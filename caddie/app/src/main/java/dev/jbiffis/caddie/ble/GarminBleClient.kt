package dev.jbiffis.caddie.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Direct BLE link to a Garmin watch, Gadgetbridge-style (no Garmin Connect).
 *
 * Garmin's GATT services all share the base UUID xxxxxxxx-667B-11E3-949A-0800200C9A66.
 * Rather than hard-coding service UUIDs that differ between watch generations, we
 * discover any Garmin-base service and pick its writable + notifiable characteristics.
 *
 * Transport: GFDI packets, COBS-encoded, 0x00-delimited (see [Gfdi]).
 *
 * Status: EXPERIMENTAL. The link, framing and directory download are implemented;
 * newer firmware may additionally require Garmin's encrypted-auth handshake, which
 * is not implemented yet. Every frame is surfaced in the log so the protocol can be
 * iterated against a real watch.
 */
@SuppressLint("MissingPermission") // callers gate on runtime permissions
class GarminBleClient(
    private val context: Context,
    private val onFileDownloaded: suspend (name: String, bytes: ByteArray) -> Unit,
) {
    companion object {
        const val GARMIN_BASE_UUID_SUFFIX = "-667b-11e3-949a-0800200c9a66"
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val FILE_TYPE_FIT = 128
    }

    enum class State { DISCONNECTED, CONNECTING, DISCOVERING, READY, SYNCING }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val _directory = MutableStateFlow<List<Gfdi.DirectoryEntry>>(emptyList())
    val directory: StateFlow<List<Gfdi.DirectoryEntry>> = _directory.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var mtu = 23

    private val rxBuffer = ByteArrayOutputStream()
    private val writeDone = Channel<Boolean>(Channel.CONFLATED)
    private val responses = Channel<Gfdi.ResponseMsg>(Channel.BUFFERED)
    private val dataChunks = Channel<Gfdi.DataTransfer>(Channel.BUFFERED)

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
        _log.value = (_log.value + "$ts  $msg").takeLast(400)
    }

    fun connect(device: BluetoothDevice) {
        disconnect()
        _state.value = State.CONNECTING
        log("Connecting to ${device.name ?: device.address}…")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
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
                _state.value = State.DISCONNECTED
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {
            mtu = newMtu
            log("MTU = $newMtu. Discovering services…")
            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            var found = false
            for (service in g.services) {
                val uuid = service.uuid.toString().lowercase()
                val isGarmin = uuid.endsWith(GARMIN_BASE_UUID_SUFFIX)
                log("Service $uuid${if (isGarmin) "  [Garmin]" else ""}")
                if (!isGarmin) continue
                for (ch in service.characteristics) {
                    val props = ch.properties
                    val writable = props and (BluetoothGattCharacteristic.PROPERTY_WRITE or
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                    val notifiable = props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                        BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    log("  char ${ch.uuid.toString().takeLast(17)} props=0x${props.toString(16)}" +
                        (if (writable) " W" else "") + (if (notifiable) " N" else ""))
                    if (writable && writeChar == null) writeChar = ch
                    if (notifiable && notifyChar == null) notifyChar = ch
                }
                if (writeChar != null && notifyChar != null) { found = true; break }
            }
            if (!found) {
                log("No usable Garmin service found — is the watch paired in Android Bluetooth settings?")
                return
            }
            log("Using write=${writeChar!!.uuid.toString().takeLast(17)} notify=${notifyChar!!.uuid.toString().takeLast(17)}")
            g.setCharacteristicNotification(notifyChar, true)
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

        // API 33+ delivers notifications through this overload instead
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            onBytes(value)
        }
    }

    private fun onLinkReady() {
        _state.value = State.READY
        log("Link ready — waiting for the watch to talk, or start a sync.")
    }

    /** Reassemble the 0x00-delimited COBS stream arriving in MTU-sized notifications. */
    private fun onBytes(bytes: ByteArray) {
        for (b in bytes) {
            if (b.toInt() == 0) {
                val packet = rxBuffer.toByteArray()
                rxBuffer.reset()
                if (packet.isNotEmpty()) {
                    val decoded = Cobs.decode(packet)
                    if (decoded == null) { log("RX framing error (${packet.size} bytes)"); continue }
                    val msg = Gfdi.parse(decoded)
                    if (msg == null) { log("RX bad GFDI packet (${decoded.size} bytes)"); continue }
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
                log("RX response to ${r.requestId} status=${r.status} (+${r.extra.size} bytes)")
                responses.trySend(r)
            }
            Gfdi.MSG_DATA_TRANSFER -> {
                val d = Gfdi.parseDataTransfer(msg.payload) ?: return
                dataChunks.trySend(d)
            }
            Gfdi.MSG_DEVICE_INFORMATION -> {
                log("RX device information (${msg.payload.size} bytes) — sending ours")
                scope.launch { send(Gfdi.deviceInformationResponse()) }
            }
            Gfdi.MSG_SYSTEM_EVENT -> {
                log("RX system event: ${msg.payload.joinToString(" ") { "%02x".format(it) }}")
                scope.launch { send(Gfdi.response(Gfdi.MSG_SYSTEM_EVENT, Gfdi.STATUS_ACK)) }
            }
            Gfdi.MSG_AUTH_NEGOTIATION -> {
                log("RX auth negotiation — encrypted auth NOT implemented yet; replying unsupported")
                scope.launch { send(Gfdi.response(Gfdi.MSG_AUTH_NEGOTIATION, Gfdi.STATUS_UNSUPPORTED)) }
            }
            else -> {
                log("RX unhandled GFDI id=${msg.id} (${msg.payload.size} bytes) — ACKing")
                scope.launch { send(Gfdi.response(msg.id, Gfdi.STATUS_ACK)) }
            }
        }
    }

    private suspend fun send(gfdiPacket: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = writeChar ?: return false
        val encoded = Cobs.encode(gfdiPacket)
        val framed = ByteArray(encoded.size + 2)
        framed[0] = 0
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
        return true
    }

    /** Download the file directory and publish golf-relevant entries. */
    fun startSync() {
        scope.launch {
            _state.value = State.SYNCING
            try {
                log("Requesting file directory (index 0)…")
                val dir = downloadFile(0) ?: run {
                    log("Directory download failed"); return@launch
                }
                val entries = Gfdi.parseDirectory(dir)
                log("Directory: ${entries.size} files")
                val golf = entries.filter {
                    it.dataType == FILE_TYPE_FIT &&
                        (it.subType == 38 /* golf score */ || it.subType == 4 /* activity */)
                }
                _directory.value = golf
                golf.forEach {
                    log("  #${it.index} type=${it.subType} ${it.size} bytes")
                }
                log("Tap a file to download it, or Download All.")
            } finally {
                if (_state.value == State.SYNCING) _state.value = State.READY
            }
        }
    }

    fun downloadEntry(entry: Gfdi.DirectoryEntry) {
        scope.launch {
            _state.value = State.SYNCING
            try {
                log("Downloading file #${entry.index} (${entry.size} bytes)…")
                val bytes = downloadFile(entry.index)
                if (bytes == null) { log("Download failed"); return@launch }
                val kind = if (entry.subType == 38) "SCORE" else "ACTIVITY"
                log("Downloaded $kind file (${bytes.size} bytes) — importing")
                onFileDownloaded("${kind}_${entry.index}.fit", bytes)
            } finally {
                _state.value = State.READY
            }
        }
    }

    private suspend fun downloadFile(index: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        if (!send(Gfdi.downloadRequest(index, 0))) return null
        // Expect an ACK to the download request, then DATA_TRANSFER chunks we ACK
        // one by one until the file size is reached.
        val ack = withTimeoutOrNull(10_000) { responses.receive() } ?: run {
            log("No response to download request"); return null
        }
        if (ack.status != Gfdi.STATUS_ACK) { log("Download refused, status=${ack.status}"); return null }
        val fileSize = if (ack.extra.size >= 8)
            java.nio.ByteBuffer.wrap(ack.extra, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        else -1L
        log("Download accepted${if (fileSize >= 0) ", size=$fileSize" else ""}")
        while (true) {
            val chunk = withTimeoutOrNull(15_000) { dataChunks.receive() } ?: run {
                log("Timed out waiting for data (got ${out.size()} bytes)")
                return if (out.size() > 0) out.toByteArray() else null
            }
            if (chunk.offset != out.size().toLong()) {
                log("Unexpected offset ${chunk.offset} (have ${out.size()})")
            }
            out.write(chunk.data)
            send(Gfdi.dataTransferAck(out.size().toLong()))
            if (fileSize in 1..out.size().toLong()) break
            if (chunk.data.isEmpty()) break
        }
        return out.toByteArray()
    }
}
