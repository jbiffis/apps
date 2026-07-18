package dev.jbiffis.caddie.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Direct BLE link to a Garmin watch speaking GFDI — no Garmin Connect.
 *
 * Requirements on the watch side: it must NOT be paired with Garmin Connect
 * (remove it from the Connect app and forget it in Android Bluetooth settings
 * first — a watch keeps one BLE host). Then pair from Caddie's Watch tab; the
 * watch shows its usual pairing confirmation.
 *
 * Flow: bond → GATT connect → MTU → discover Garmin service (any service on
 * the 667B-11E3-949A-0800200C9A66 base; write + notify characteristics chosen
 * by property) → handshake (device info ↔, configuration, pair events on first
 * connect, SYNC_READY) → ANT-FS directory download → download new golf files →
 * import → SYNC_COMPLETE.
 *
 * Known gap: some firmware asks for encrypted auth (MSG_AUTH_NEGOTIATION).
 * That handshake isn't implemented; if your watch requires it the log will
 * show "AUTH NEGOTIATION" prominently — export the log and we iterate.
 */
@SuppressLint("MissingPermission") // callers gate on runtime permissions
class GarminBleClient(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val onFileDownloaded: suspend (name: String, bytes: ByteArray) -> Unit,
) {
    companion object {
        const val GARMIN_BASE_UUID_SUFFIX = "-667b-11e3-949a-0800200c9a66"
        val CCCD: java.util.UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val FILE_TYPE_FIT = 128
        const val SUBTYPE_GOLF_SCORE = 38
        const val SUBTYPE_ACTIVITY = 4
    }

    enum class State { DISCONNECTED, BONDING, CONNECTING, DISCOVERING, HANDSHAKE, READY, SYNCING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _directory = MutableStateFlow<List<Gfdi.DirectoryEntry>>(emptyList())
    val directory: StateFlow<List<Gfdi.DirectoryEntry>> = _directory.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var mtu = 23
    private var connectRetried = false

    private val rxBuffer = ByteArrayOutputStream()
    private val writeDone = Channel<Boolean>(Channel.CONFLATED)
    private val writeMutex = Mutex()
    private val responses = Channel<Gfdi.ResponseMsg>(Channel.BUFFERED)
    private val dataChunks = Channel<Gfdi.DataTransfer>(Channel.BUFFERED)
    private var bondReceiver: BroadcastReceiver? = null

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
        _log.value = (_log.value + "$ts  $msg").takeLast(600)
    }

    fun exportLog(): String = _log.value.joinToString("\n")

    private val unitNumber: Long
        get() {
            val existing = prefs.getLong("unit_number", 0)
            if (existing != 0L) return existing
            val fresh = 2_000_000_000L + (System.currentTimeMillis() % 100_000_000L)
            prefs.edit().putLong("unit_number", fresh).apply()
            return fresh
        }

    private fun isPaired(address: String) = prefs.getBoolean("paired_$address", false)
    private fun markPaired(address: String) = prefs.edit().putBoolean("paired_$address", true).apply()

    private fun syncedKey(e: Gfdi.DirectoryEntry) = "${device?.address}|${e.index}|${e.fitTimestamp}"
    private fun isSynced(e: Gfdi.DirectoryEntry) =
        prefs.getStringSet("synced_files", emptySet())!!.contains(syncedKey(e))
    private fun markSynced(e: Gfdi.DirectoryEntry) {
        val set = HashSet(prefs.getStringSet("synced_files", emptySet())!!)
        set.add(syncedKey(e))
        prefs.edit().putStringSet("synced_files", set).apply()
    }

    // ---- Connection ------------------------------------------------------------

    fun connect(target: BluetoothDevice) {
        disconnect()
        device = target
        connectRetried = false
        if (target.bondState == BluetoothDevice.BOND_NONE) {
            log("Not bonded — starting Bluetooth pairing (watch will ask to confirm)…")
            _state.value = State.BONDING
            val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            bondReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val d = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    if (d.address != target.address) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)) {
                        BluetoothDevice.BOND_BONDED -> {
                            log("Bonded.")
                            unregisterBondReceiver()
                            gattConnect(target)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            log("Bonding failed or rejected — trying unbonded anyway")
                            unregisterBondReceiver()
                            gattConnect(target)
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(context, bondReceiver!!, filter, ContextCompat.RECEIVER_EXPORTED)
            if (!target.createBond()) {
                log("createBond() refused — trying direct connect")
                unregisterBondReceiver()
                gattConnect(target)
            }
        } else {
            gattConnect(target)
        }
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        bondReceiver = null
    }

    private fun gattConnect(target: BluetoothDevice) {
        _state.value = State.CONNECTING
        log("Connecting to ${target.name ?: target.address}…")
        gatt = target.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        unregisterBondReceiver()
        gatt?.close()
        gatt = null
        writeChar = null
        notifyChar = null
        rxBuffer.reset()
        _state.value = State.DISCONNECTED
        _directory.value = emptyList()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected (status=$status). Requesting MTU 512…")
                _state.value = State.DISCOVERING
                g.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log("Disconnected (status=$status)")
                if (status == 133 && !connectRetried && device != null) {
                    connectRetried = true
                    log("GATT 133 — retrying once…")
                    g.close()
                    scope.launch {
                        kotlinx.coroutines.delay(1200)
                        device?.let { gattConnect(it) }
                    }
                } else {
                    _state.value = State.DISCONNECTED
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = newMtu
            log("MTU = $newMtu. Discovering services…")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            // Prefer the dedicated GFDI service (6a4e2800…) when several Garmin
            // services are advertised; otherwise take any Garmin-base service
            // that has both a writable and a notifiable characteristic.
            val garminServices = g.services
                .filter { it.uuid.toString().lowercase().endsWith(GARMIN_BASE_UUID_SUFFIX) }
                .sortedByDescending { it.uuid.toString().lowercase().startsWith("6a4e2800") }
            g.services.forEach { s ->
                val mark = if (s in garminServices) "  [Garmin]" else ""
                log("Service ${s.uuid}$mark")
            }
            for (service in garminServices) {
                var w: BluetoothGattCharacteristic? = null
                var n: BluetoothGattCharacteristic? = null
                for (ch in service.characteristics) {
                    val props = ch.properties
                    val writable = props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    val notifiable = props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    log("  char …${ch.uuid.toString().takeLast(17)} props=0x${props.toString(16)}" +
                        (if (writable) " W" else "") + (if (notifiable) " N" else ""))
                    if (writable && w == null) w = ch
                    if (notifiable && n == null && ch != w) n = ch
                }
                if (w != null && n != null) {
                    writeChar = w; notifyChar = n; break
                }
            }
            if (writeChar == null || notifyChar == null) {
                log("No usable Garmin service. Is the watch still paired to Garmin Connect? " +
                    "Remove it there and forget it in Android Bluetooth settings, then retry.")
                return
            }
            log("Using write=…${writeChar!!.uuid.toString().takeLast(17)} notify=…${notifyChar!!.uuid.toString().takeLast(17)}")
            gatt?.setCharacteristicNotification(notifyChar, true)
            val cccd = notifyChar!!.getDescriptor(CCCD)
            if (cccd != null) {
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            } else {
                onLinkReady()
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            log("Notifications enabled (status=$status)")
            onLinkReady()
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            writeDone.trySend(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (android.os.Build.VERSION.SDK_INT < 33) onBytes(ch.value ?: return)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            onBytes(value)
        }
    }

    private fun onLinkReady() {
        _state.value = State.HANDSHAKE
        val address = device?.address ?: return
        log("Link up — waiting for the watch to start the GFDI handshake…")
        if (!isPaired(address)) {
            scope.launch { send(Gfdi.systemEvent(Gfdi.EVENT_PAIR_START)) }
        }
    }

    // ---- Frame RX --------------------------------------------------------------

    private fun onBytes(bytes: ByteArray) {
        for (b in bytes) {
            if (b.toInt() == 0) {
                val packet = rxBuffer.toByteArray()
                rxBuffer.reset()
                if (packet.isNotEmpty()) {
                    val decoded = Cobs.decode(packet)
                    if (decoded == null) { log("RX framing error: ${Gfdi.hex(packet)}"); continue }
                    val msg = Gfdi.parse(decoded)
                    if (msg == null) { log("RX bad GFDI packet: ${Gfdi.hex(decoded)}"); continue }
                    handleMessage(msg)
                }
            } else {
                rxBuffer.write(b.toInt())
            }
        }
    }

    private fun handleMessage(msg: Gfdi.Message) {
        when (msg.id) {
            Gfdi.MSG_RESPONSE -> {
                val r = Gfdi.parseResponse(msg.payload) ?: return
                log("RX ack[${r.requestId}] status=${r.status}" +
                    (if (r.extra.isNotEmpty()) " extra=${Gfdi.hex(r.extra, 12)}" else ""))
                responses.trySend(r)
            }
            Gfdi.MSG_FILE_TRANSFER_DATA -> {
                Gfdi.parseDataTransfer(msg.payload)?.let { dataChunks.trySend(it) }
            }
            Gfdi.MSG_DEVICE_INFORMATION -> {
                val info = Gfdi.parseDeviceInformation(msg.payload)
                log("RX device info: ${info?.name ?: "?"} sw=${info?.softwareVersion} " +
                    "proto=${info?.protocolVersion} maxPacket=${info?.maxPacketSize}")
                scope.launch {
                    send(Gfdi.deviceInformationResponse(unitNumber))
                    completeHandshake()
                }
            }
            Gfdi.MSG_CURRENT_TIME_REQUEST -> {
                log("RX current-time request — answering")
                val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
                scope.launch { send(Gfdi.currentTimeResponse(System.currentTimeMillis(), tz)) }
            }
            Gfdi.MSG_SYSTEM_EVENT -> {
                log("RX system event: ${Gfdi.hex(msg.payload, 8)}")
                scope.launch { send(Gfdi.response(Gfdi.MSG_SYSTEM_EVENT, Gfdi.STATUS_ACK)) }
            }
            Gfdi.MSG_FILE_READY -> {
                log("RX file ready — the watch has new files")
                scope.launch {
                    send(Gfdi.response(Gfdi.MSG_FILE_READY, Gfdi.STATUS_ACK))
                    if (_state.value == State.READY) startSync()
                }
            }
            Gfdi.MSG_SYNC_REQUEST -> {
                log("RX sync request from watch")
                scope.launch {
                    send(Gfdi.response(Gfdi.MSG_SYNC_REQUEST, Gfdi.STATUS_ACK))
                    if (_state.value == State.READY) startSync()
                }
            }
            Gfdi.MSG_AUTH_NEGOTIATION -> {
                log("RX ⚠ AUTH NEGOTIATION (${Gfdi.hex(msg.payload)}) — encrypted auth not " +
                    "implemented yet. Export this log so support for it can be added.")
                scope.launch { send(Gfdi.response(Gfdi.MSG_AUTH_NEGOTIATION, Gfdi.STATUS_UNSUPPORTED)) }
            }
            Gfdi.MSG_BATTERY_STATUS, Gfdi.MSG_DEVICE_SETTINGS, Gfdi.MSG_NOTIFICATION_SOURCE,
            Gfdi.MSG_PROTOBUF_REQUEST, Gfdi.MSG_FIT_DEFINITION, Gfdi.MSG_FIT_DATA,
            Gfdi.MSG_WEATHER_REQUEST -> {
                log("RX id=${msg.id} (${msg.payload.size}b) — ACK: ${Gfdi.hex(msg.payload)}")
                scope.launch { send(Gfdi.response(msg.id, Gfdi.STATUS_ACK)) }
            }
            else -> {
                log("RX unknown id=${msg.id}: ${Gfdi.hex(msg.payload)} — ACK")
                scope.launch { send(Gfdi.response(msg.id, Gfdi.STATUS_ACK)) }
            }
        }
    }

    private suspend fun completeHandshake() {
        val address = device?.address ?: return
        send(Gfdi.configuration())
        awaitResponse(Gfdi.MSG_CONFIGURATION, 5000)  // tolerate silence
        if (!isPaired(address)) {
            send(Gfdi.systemEvent(Gfdi.EVENT_PAIR_COMPLETE))
            markPaired(address)
            log("Pairing complete.")
        }
        send(Gfdi.systemEvent(Gfdi.EVENT_SYNC_READY))
        _state.value = State.READY
        log("Handshake complete — syncing golf files")
        startSync()
    }

    // ---- TX --------------------------------------------------------------------

    private suspend fun send(gfdiPacket: ByteArray): Boolean = writeMutex.withLock {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        val encoded = Cobs.encode(gfdiPacket)
        val framed = ByteArray(encoded.size + 2)
        System.arraycopy(encoded, 0, framed, 1, encoded.size)
        framed[framed.size - 1] = 0
        val chunkSize = mtu - 3
        var off = 0
        while (off < framed.size) {
            val len = minOf(chunkSize, framed.size - off)
            @Suppress("DEPRECATION")
            ch.value = framed.copyOfRange(off, off + len)
            @Suppress("DEPRECATION")
            if (!g.writeCharacteristic(ch)) { log("TX write failed"); return false }
            val ok = withTimeoutOrNull(5000) { writeDone.receive() } ?: false
            if (!ok) { log("TX write not confirmed"); return false }
            off += len
        }
        true
    }

    private suspend fun awaitResponse(requestId: Int, timeoutMs: Long): Gfdi.ResponseMsg? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            val r = withTimeoutOrNull(remaining) { responses.receive() } ?: return null
            if (r.requestId == requestId) return r
            log("(out-of-band ack[${r.requestId}] while waiting for $requestId)")
        }
    }

    // ---- Sync ------------------------------------------------------------------

    fun startSync() {
        scope.launch {
            if (_state.value == State.SYNCING) return@launch
            _state.value = State.SYNCING
            try {
                send(Gfdi.directoryFilter())
                awaitResponse(Gfdi.MSG_DIRECTORY_FILE_FILTER, 3000)

                log("Downloading file directory…")
                val dir = downloadFile(0) ?: run { log("Directory download failed"); return@launch }
                val entries = Gfdi.parseDirectory(dir)
                val golf = entries.filter {
                    it.dataType == FILE_TYPE_FIT &&
                        (it.subType == SUBTYPE_GOLF_SCORE || it.subType == SUBTYPE_ACTIVITY)
                }
                _directory.value = golf
                log("Directory: ${entries.size} files, ${golf.size} golf-related")

                // SCORE files first so activities can attach to their rounds
                val fresh = golf.filter { !isSynced(it) }
                    .sortedBy { if (it.subType == SUBTYPE_GOLF_SCORE) 0 else 1 }
                if (fresh.isEmpty()) {
                    log("No new golf files.")
                } else {
                    log("${fresh.size} new file(s) to download")
                    var ok = 0
                    for (entry in fresh) {
                        val kind = if (entry.subType == SUBTYPE_GOLF_SCORE) "SCORE" else "ACTIVITY"
                        log("Downloading $kind #${entry.index} (${entry.size}b)…")
                        val bytes = downloadFile(entry.index)
                        if (bytes == null) { log("  failed — will retry next sync"); continue }
                        try {
                            onFileDownloaded("${kind}_${entry.index}.fit", bytes)
                            markSynced(entry)
                            ok++
                            log("  imported ✓")
                        } catch (e: Exception) {
                            log("  import failed: ${e.message}")
                        }
                    }
                    log("Sync done: $ok/${fresh.size} imported")
                }
                send(Gfdi.systemEvent(Gfdi.EVENT_SYNC_COMPLETE))
            } catch (e: Exception) {
                log("Sync error: $e")
            } finally {
                _state.value = State.READY
            }
        }
    }

    fun downloadEntry(entry: Gfdi.DirectoryEntry) {
        scope.launch {
            if (_state.value == State.SYNCING) return@launch
            _state.value = State.SYNCING
            try {
                val kind = if (entry.subType == SUBTYPE_GOLF_SCORE) "SCORE" else "ACTIVITY"
                log("Downloading $kind #${entry.index} (${entry.size}b)…")
                val bytes = downloadFile(entry.index) ?: run { log("Download failed"); return@launch }
                onFileDownloaded("${kind}_${entry.index}.fit", bytes)
                markSynced(entry)
                log("Imported ✓")
            } catch (e: Exception) {
                log("Download error: $e")
            } finally {
                _state.value = State.READY
            }
        }
    }

    private suspend fun downloadFile(index: Int): ByteArray? {
        // Drain any stale chunks from a previous aborted transfer
        while (true) { if (dataChunks.tryReceive().getOrNull() == null) break }

        if (!send(Gfdi.downloadRequest(index, 0))) return null
        val ack = awaitResponse(Gfdi.MSG_DOWNLOAD_REQUEST, 10_000) ?: run {
            log("No response to download request"); return null
        }
        if (ack.status != Gfdi.STATUS_ACK) { log("Download refused, status=${ack.status}"); return null }
        val fileSize = if (ack.extra.size >= 4)
            java.nio.ByteBuffer.wrap(ack.extra, 0, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        else -1L
        if (fileSize >= 0) log("Transfer accepted, size=$fileSize")

        val out = ByteArrayOutputStream()
        var lastProgressLog = 0
        while (true) {
            val chunk = withTimeoutOrNull(15_000) { dataChunks.receive() } ?: run {
                log("Timed out at ${out.size()}b")
                return if (out.size() > 0 && fileSize <= 0) out.toByteArray() else null
            }
            if (chunk.offset != out.size().toLong()) {
                log("Offset mismatch: got ${chunk.offset}, have ${out.size()} — requesting resend")
                send(Gfdi.dataTransferAck(out.size().toLong()))
                continue
            }
            out.write(chunk.data)
            send(Gfdi.dataTransferAck(out.size().toLong()))
            if (out.size() - lastProgressLog >= 8192) {
                lastProgressLog = out.size()
                log("  …${out.size()}b")
            }
            if (fileSize in 1..out.size().toLong()) break
            if (chunk.data.isEmpty()) break
        }
        return out.toByteArray()
    }
}
