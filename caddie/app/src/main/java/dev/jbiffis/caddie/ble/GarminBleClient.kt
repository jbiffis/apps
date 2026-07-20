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
        /** Bumped every BLE change so the log unambiguously identifies the running build. */
        const val BLE_BUILD = "ble-12 dir-size"
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

    // Multi-link state
    private var gfdiHandle = -1
    private val controlResponses = Channel<MultiLink.RegisterResponse>(Channel.BUFFERED)

    // COBS reassembly buffer per multi-link handle (streams must not interleave)
    private val rxBuffers = HashMap<Int, ByteArrayOutputStream>()
    // Next expected receive sequence per reliable (MLR) handle
    private val mlrRcvSeq = HashMap<Int, Int>()
    private var verboseRx = false
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
        log("Caddie BLE build: $BLE_BUILD")
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
        gfdiHandle = -1
        rxBuffers.clear()
        mlrRcvSeq.clear()
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
            val mlService = g.services.firstOrNull {
                it.uuid == MultiLink.base(0x2800)
            }
            g.services.forEach { s ->
                val mark = if (s.uuid.toString().lowercase().endsWith(GARMIN_BASE_UUID_SUFFIX)) "  [Garmin]" else ""
                log("Service ${s.uuid}$mark")
            }
            if (mlService == null) {
                log("No Garmin multi-link service (6a4e2800). Is the watch still paired to " +
                    "Garmin Connect? Remove it there and forget it in Android Bluetooth settings.")
                return
            }
            // Log every characteristic with its FULL uuid so channels are identifiable
            for (ch in mlService.characteristics) {
                val p = ch.properties
                val w = p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                val n = p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                log("  char ${ch.uuid} props=0x${p.toString(16)}${if (w) " W" else ""}${if (n) " N" else ""}")
            }
            // Channel 1: write 6a4e2820, notify 6a4e2810 (fall back to first W / first N)
            writeChar = mlService.getCharacteristic(MultiLink.WRITE_CHAR)
                ?: mlService.characteristics.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                }
            notifyChar = mlService.getCharacteristic(MultiLink.NOTIFY_CHAR)
                ?: mlService.characteristics.firstOrNull {
                    it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }
            if (writeChar == null || notifyChar == null) {
                log("Multi-link service has no usable write/notify characteristics")
                return
            }
            log("Using write=${writeChar!!.uuid} notify=${notifyChar!!.uuid}")
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
        log("Link up — registering GFDI over multi-link…")
        scope.launch { registerGfdi() }
    }

    /** Multi-link handshake: reset handles, register GFDI, learn its handle. */
    private suspend fun registerGfdi() {
        // Clear stale handles from a previous session (best effort)
        sendRaw(MultiLink.closeAllRequest())
        kotlinx.coroutines.delay(150)

        while (controlResponses.tryReceive().getOrNull() != null) { /* drain */ }
        sendRaw(MultiLink.registerRequest(MultiLink.SERVICE_GFDI))
        val resp = withTimeoutOrNull(5000) { controlResponses.receive() }
        if (resp == null) {
            log("No multi-link register response — the watch may use a different transport. " +
                "Share this log so it can be added.")
            return
        }
        if (resp.status != 0 || resp.handle == 0) {
            log("GFDI registration refused (status=${resp.status}, handle=${resp.handle})")
            return
        }
        gfdiHandle = resp.handle
        log("GFDI registered on handle ${gfdiHandle} (reliability=${resp.reliability}). " +
            "Waiting for the watch's device info…")

        // First-connect pairing courtesy event; the watch then sends DEVICE_INFORMATION.
        val address = device?.address
        if (address != null && !isPaired(address)) {
            send(Gfdi.systemEvent(Gfdi.EVENT_PAIR_START))
        }
    }

    // ---- Frame RX --------------------------------------------------------------

    private fun onBytes(raw: ByteArray) {
        if (raw.isEmpty()) return
        if (verboseRx) log("  raw← ${Gfdi.hex(raw, 40)}")

        // Reliable (MLR) framing: 2-byte header with bit7 set (used for file transfer)
        if (MultiLink.isReliable(raw)) {
            val m = MultiLink.parseReliable(raw) ?: return
            val expected = mlrRcvSeq.getOrPut(m.handle) { 0 }
            if (m.payload.isEmpty()) {
                // Pure ACK (no data) — nothing to reassemble
                if (verboseRx) log("  MLR ack handle=${m.handle} req=${m.reqNum}")
                return
            }
            if (m.seq != expected) {
                log("MLR handle=${m.handle} out-of-seq: got ${m.seq}, expected $expected — re-acking")
                scope.launch { sendRaw(MultiLink.reliableAck(m.handle, expected)) }
                return
            }
            val next = (expected + 1) % MultiLink.MLR_SEQ_MODULO
            mlrRcvSeq[m.handle] = next
            scope.launch { sendRaw(MultiLink.reliableAck(m.handle, next)) }
            feedCobs(m.handle, m.payload)
            return
        }

        // Plain ML: first byte is the handle
        val (handle, payload) = MultiLink.stripHandle(raw) ?: return
        if (handle == MultiLink.CONTROL_HANDLE) {
            MultiLink.parseControl(raw)?.let {
                log("RX ML register-resp: service=${it.service} status=${it.status} handle=${it.handle}")
                controlResponses.trySend(it)
                return
            }
            // Other control-channel traffic — log, don't feed to COBS
            if (verboseRx) log("  control handle=0: ${Gfdi.hex(payload, 24)}")
            return
        }
        feedCobs(handle, payload)
    }

    /** Feed reassembled service bytes for [handle] into its COBS/GFDI decoder. */
    private fun feedCobs(handle: Int, payload: ByteArray) {
        val buffer = rxBuffers.getOrPut(handle) { ByteArrayOutputStream() }
        for (b in payload) {
            if (b.toInt() == 0) {
                val packet = buffer.toByteArray()
                buffer.reset()
                if (packet.isNotEmpty()) {
                    val decoded = Cobs.decode(packet)
                    if (decoded == null) { log("RX framing error (h$handle): ${Gfdi.hex(packet)}"); continue }
                    val msg = Gfdi.parse(decoded)
                    if (msg == null) { log("RX bad GFDI (h$handle): ${Gfdi.hex(decoded, 40)}"); continue }
                    handleMessage(msg)
                }
            } else {
                buffer.write(b.toInt())
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
                // downloadFile() sends the acknowledgement (it advances the sequence)
                Gfdi.parseDataTransfer(msg.payload, msg.rawType)?.let { dataChunks.trySend(it) }
            }
            Gfdi.MSG_PROTOBUF_REQUEST -> {
                val req = Gfdi.parseProtobufRequest(msg.payload)
                log("RX protobuf request id=${req?.requestId} offset=${req?.dataOffset} " +
                    "len=${req?.totalLength} seq=${msg.seq}")
                if (req != null) scope.launch { send(Gfdi.protobufAck(req.requestId, req.dataOffset)) }
                else scope.launch { send(Gfdi.ack(msg.id)) }
            }
            Gfdi.MSG_DEVICE_INFORMATION -> {
                val info = Gfdi.parseDeviceInformation(msg.payload)
                log("RX device info: ${info?.name ?: "?"} sw=${info?.softwareVersion} seq=${msg.seq}")
                scope.launch {
                    if (msg.sequenced) {
                        // Re-sent during sync; just acknowledge and keep the session moving
                        send(Gfdi.ack(msg.id))
                    } else {
                        send(Gfdi.deviceInformationResponse(unitNumber))
                        completeHandshake()
                    }
                }
            }
            Gfdi.MSG_CURRENT_TIME_REQUEST -> {
                log("RX current-time request — answering")
                val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000
                scope.launch { send(Gfdi.currentTimeResponse(System.currentTimeMillis(), tz)) }
            }
            Gfdi.MSG_SYSTEM_EVENT -> {
                log("RX system event: ${Gfdi.hex(msg.payload, 8)}")
                scope.launch { send(Gfdi.ack(msg.id)) }
            }
            Gfdi.MSG_FILE_READY -> {
                log("RX file ready — the watch has new files")
                scope.launch {
                    send(Gfdi.ack(msg.id))
                    if (_state.value == State.READY) startSync()
                }
            }
            Gfdi.MSG_SYNC_REQUEST -> {
                log("RX sync request from watch")
                scope.launch {
                    send(Gfdi.ack(msg.id))
                    if (_state.value == State.READY) startSync()
                }
            }
            Gfdi.MSG_AUTH_NEGOTIATION -> {
                log("RX ⚠ AUTH NEGOTIATION (${Gfdi.hex(msg.payload)}) — encrypted auth not " +
                    "implemented yet. Export this log so support for it can be added.")
                scope.launch { send(Gfdi.response(msg.id, Gfdi.STATUS_UNSUPPORTED)) }
            }
            else -> {
                // Configuration, protobuf request, fit definition, etc. — acknowledge
                // so the watch advances its send sequence and proceeds to file data.
                log("RX id=${msg.id} seq=${msg.seq} (${msg.payload.size}b) — ACK: ${Gfdi.hex(msg.payload, 32)}")
                scope.launch { send(Gfdi.ack(msg.id)) }
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

    /** Wrap a GFDI message in COBS and send it over the registered GFDI handle. */
    private suspend fun send(gfdiPacket: ByteArray): Boolean {
        if (gfdiHandle < 0) { log("TX before GFDI handle assigned"); return false }
        if (verboseRx && gfdiPacket.size >= 4) {
            val type = (gfdiPacket[2].toInt() and 0xFF) or ((gfdiPacket[3].toInt() and 0xFF) shl 8)
            val ref = if (type == Gfdi.MSG_RESPONSE && gfdiPacket.size >= 7)
                " ref=0x${((gfdiPacket[4].toInt() and 0xFF) or ((gfdiPacket[5].toInt() and 0xFF) shl 8)).toString(16)}" else ""
            log("  TX→ type=$type$ref (${gfdiPacket.size}b)")
        }
        val encoded = Cobs.encode(gfdiPacket)
        val framed = ByteArray(encoded.size + 2)
        System.arraycopy(encoded, 0, framed, 1, encoded.size) // 0x00 … 0x00 delimiters
        val chunks = MultiLink.fragment(gfdiHandle, framed, mtu - 3)
        return writeMutex.withLock {
            chunks.all { writeChunkUnlocked(it) }
        }
    }

    /** Send a control-channel (handle 0) frame verbatim — no COBS, no fragmentation. */
    private suspend fun sendRaw(packet: ByteArray): Boolean = writeMutex.withLock {
        writeChunkUnlocked(packet)
    }

    private suspend fun writeChunkUnlocked(bytes: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        @Suppress("DEPRECATION")
        ch.value = bytes
        @Suppress("DEPRECATION")
        if (!g.writeCharacteristic(ch)) { log("TX write failed"); return false }
        val ok = withTimeoutOrNull(5000) { writeDone.receive() } ?: false
        if (!ok) { log("TX write not confirmed"); return false }
        return true
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
            verboseRx = true // capture the raw file-transfer framing for diagnosis
            try {
                send(Gfdi.directoryFilter())
                awaitResponse(Gfdi.MSG_DIRECTORY_FILE_FILTER, 3000)

                log("Downloading file directory…")
                val dir = downloadFile(0) ?: run { log("Directory download failed"); return@launch }
                log("Directory downloaded (${dir.size}b): ${Gfdi.hex(dir, 48)}")
                val entries = Gfdi.parseDirectory(dir)
                entries.forEach {
                    log("  file #${it.index} dataType=${it.dataType} subType=${it.subType} " +
                        "num=${it.number} ${it.size}b")
                }
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
        // DownloadRequestStatusMessage extra = [downloadStatus:1][maxFileSize:4]
        val downloadStatus = ack.extra.getOrNull(0)?.toInt() ?: 0
        if (downloadStatus != 0) { log("Download refused, downloadStatus=$downloadStatus"); return null }
        val fileSize = if (ack.extra.size >= 5)
            java.nio.ByteBuffer.wrap(ack.extra, 1, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        else -1L
        if (fileSize >= 0) log("Transfer accepted, size=$fileSize")

        val out = ByteArrayOutputStream()
        var lastProgressLog = 0
        while (true) {
            // Generous: the watch interleaves other sequenced messages (which we
            // ack elsewhere) between file chunks, retransmitting every ~5s.
            val chunk = withTimeoutOrNull(30_000) { dataChunks.receive() } ?: run {
                log("Timed out at ${out.size()}b")
                return if (out.size() > 0 && fileSize <= 0) out.toByteArray() else null
            }
            if (chunk.offset != out.size().toLong()) {
                // Duplicate/old chunk (e.g. a retransmit) — re-ack our real position
                log("Skipping chunk at ${chunk.offset} (have ${out.size()})")
                send(Gfdi.dataTransferAck(out.size().toLong()))
                continue
            }
            out.write(chunk.data)
            // Ack with the logical type + next offset to advance the transfer
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
