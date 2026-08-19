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
    /**
     * Import a still-growing (partial) file pulled during live-round polling and
     * return a short human summary. Null disables live polling.
     */
    private val onPartialFile: (suspend (name: String, bytes: ByteArray) -> String)? = null,
    /** Import the downloaded FIT and return a short human summary for the sync log. */
    private val onFileDownloaded: suspend (name: String, bytes: ByteArray) -> String,
) {
    companion object {
        /** Bumped every BLE change so the log unambiguously identifies the running build. */
        const val BLE_BUILD = "ble-72 golf-newfile"
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

    // Protobuf (Smart / FileSyncService) reassembly by requestId
    private val protobufBuffers = HashMap<Int, ByteArrayOutputStream>()
    private var protobufRequestId = 1000
    private val remoteFiles = ArrayList<FileSync.RemoteFile>()
    // File-list paging state. The watch caps each page (~100 files) and some firmware
    // reports nextPageId=0 even when more pages exist, so we fall back to the max pageId
    // seen (Gadgetbridge #5461) and keep going until a page adds nothing new.
    private var lastFileListCursor = -1
    private var prevRemoteCount = 0
    private var emptyPageStreak = 0
    private var filePagesFetched = 0
    // Diagnostic: list files without downloading them (safe to run during a round).
    private var diagnosticListOnly = false
    // Realtime-capture diagnostic: log every inbound message to reverse the live stream.
    @Volatile private var captureAll = false

    // ---- Live golf polling -----------------------------------------------------
    // While a round is in progress, poll the golf service (Smart field 7) for the
    // current scorecard; the watch answers with the whole scorecard as a golf FIT.
    @Volatile private var golfLiveOn = false
    @Volatile private var lastAnnouncedSeq = 0
    private var golfJob: kotlinx.coroutines.Job? = null
    // Scorecard files the watch announces mid-round (NewFileNotification). Downloads are
    // serialized through this queue because only one V2 transfer can be in flight.
    private val liveDownloadQueue = Channel<FileSync.RemoteFile>(Channel.UNLIMITED)
    private val liveSeenFiles = java.util.Collections.synchronizedSet(HashSet<String>())
    private var liveDownloadJob: kotlinx.coroutines.Job? = null

    // V2 file transfer: FileResponse handle + a raw data channel for the transfer handle
    private val fileResponseHandles = Channel<Int>(Channel.BUFFERED)
    private var fileXferHandle = -1
    private var fileXferService = -1
    private val fileXferChunks = Channel<ByteArray>(Channel.BUFFERED)
    // Signalled when the watch closes the transfer handle — the true end-of-file marker.
    private val fileXferDone = Channel<Unit>(Channel.CONFLATED)
    // Cycle through the FILE_TRANSFER services so a just-closed handle is never reused immediately.
    private var fileXferServiceIdx = 0

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(System.currentTimeMillis())
        _log.value = (_log.value + "$ts  $msg").takeLast(3000)
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
        stopGolfLive()
        unregisterBondReceiver()
        gatt?.close()
        gatt = null
        writeChar = null
        notifyChar = null
        gfdiHandle = -1
        fileXferHandle = -1
        fileXferService = -1
        fileXferServiceIdx = 0
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
            MultiLink.parseCloseResponse(raw)?.let {
                if (verboseRx) log("  control close: service=${it.service} handle=${it.handle} status=${it.status}")
                // The watch closes the transfer handle to mark end-of-file.
                if (it.handle == fileXferHandle || it.service == fileXferService) fileXferDone.trySend(Unit)
                return
            }
            // Other control-channel traffic — log, don't feed to COBS
            if (verboseRx) log("  control handle=0: ${Gfdi.hex(payload, 24)}")
            return
        }
        // A registered V2 file-transfer handle delivers RAW (deflate) data, not COBS
        if (handle == fileXferHandle) {
            fileXferChunks.trySend(payload)
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
        // Realtime-capture diagnostic: log EVERY inbound message verbatim so we can see
        // whatever the watch pushes during a round (the raw material for reversing the
        // live-golf stream). Kept separate from normal handling below.
        if (captureAll) {
            log("CAP id=${msg.id} seq=${msg.seq} (${msg.payload.size}b): ${Gfdi.hex(msg.payload, 64)}")
        }
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
            Gfdi.MSG_PROTOBUF_REQUEST, Gfdi.MSG_PROTOBUF_RESPONSE -> {
                val req = Gfdi.parseProtobufRequest(msg.payload)
                if (req == null) { scope.launch { send(Gfdi.ack(msg.id)) }; return }
                scope.launch { handleProtobuf(req, msg.id) }
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
            5042 -> {
                // Capability query in the golf handshake — Connect answers with this extra.
                log("RX id=5042 — answering as Garmin Golf")
                scope.launch { send(Gfdi.response(5042, Gfdi.STATUS_ACK, hex("050001020304"))) }
            }
            else -> {
                // Configuration, fit definition, etc. — acknowledge so the watch
                // advances its send sequence and proceeds to file data.
                log("RX id=${msg.id} seq=${msg.seq} (${msg.payload.size}b) — ACK: ${Gfdi.hex(msg.payload, 32)}")
                scope.launch { send(Gfdi.ack(msg.id)) }
            }
        }
    }

    // ---- Protobuf / FileSyncService (V2 file enumeration) ----------------------

    private fun nextProtobufRequestId(): Int {
        protobufRequestId = (protobufRequestId + 1) and 0xFFFF
        return protobufRequestId
    }

    /** Send a Smart-message protobuf as a PROTOBUF_REQUEST and return its requestId. */
    private suspend fun sendProtobuf(smart: ByteArray): Int {
        val id = nextProtobufRequestId()
        send(Gfdi.protobufRequest(id, smart))
        return id
    }

    /** Reassemble protobuf chunks by requestId, ack each, and handle complete Smart messages. */
    private suspend fun handleProtobuf(req: Gfdi.ProtobufRequest, logicalType: Int) {
        val buf = protobufBuffers.getOrPut(req.requestId) { ByteArrayOutputStream() }
        // Ack echoes the RECEIVED offset + logical type; only then does the watch
        // send the next chunk.
        if (req.dataOffset.toInt() == buf.size()) {
            buf.write(req.data)
        } else if (req.dataOffset.toInt() > buf.size()) {
            log("protobuf offset gap: got ${req.dataOffset}, have ${buf.size()} — re-acking")
        }
        send(Gfdi.protobufAck(logicalType, req.requestId, req.dataOffset))
        if (buf.size().toLong() >= req.totalLength && req.totalLength > 0) {
            val smart = buf.toByteArray()
            protobufBuffers.remove(req.requestId)
            log("protobuf #${req.requestId} complete (${smart.size}b)")
            onSmartMessage(smart)
        }
    }

    private fun onSmartMessage(smart: ByteArray) {
        if (GolfLive.isGolf(smart)) { handleGolfSmart(smart); return }
        if (GolfLive.isNotify(smart)) {
            val seq = GolfLive.parseAnnouncedSeq(smart)
            if (seq != null) {
                lastAnnouncedSeq = seq
                log("Live golf: watch announced scorecard seq=$seq — polling it")
                scope.launch { runCatching { sendProtobuf(GolfLive.buildPoll(seq)) } }
            } else {
                log("Live golf: notify ${Gfdi.hex(smart, 16)}")
            }
            return
        }
        if (GolfLive.isAppReg(smart)) {
            if (GolfLive.isAppRegConfigRequest(smart)) {
                log("Live golf: completing app-reg handshake")
                scope.launch { runCatching { sendProtobuf(GolfLive.buildAppRegAck()) } }
            } else {
                log("Live golf: app-registration response ${Gfdi.hex(smart, 24)}")
            }
            return
        }
        // Onboarding handshake the watch runs before it will announce scorecards.
        if (golfLiveOn) {
            if (GolfLive.isTokenRequest(smart)) {
                log("Live golf: watch requested tokens — replying with captured credentials")
                scope.launch { runCatching { sendProtobuf(GolfLive.buildTokenReply()) } }
                return
            }
            when (GolfLive.topField(smart)) {
                GolfLive.SMART_S16 -> {
                    log("Live golf: answering s16")
                    scope.launch { runCatching { sendProtobuf(GolfLive.build16Ack()) } }
                    return
                }
                GolfLive.SMART_S10 -> {
                    log("Live golf: answering s10")
                    scope.launch { runCatching { sendProtobuf(GolfLive.build10Ack()) } }
                    return
                }
                24, 42, 21, 8, 30 -> {
                    log("Live golf: watch svc${GolfLive.topField(smart)} ${Gfdi.hex(smart, 16)} (noted)")
                    return
                }
            }
        }
        val fss = FileSync.fileSyncServiceOf(smart)
        if (fss == null) {
            log("Smart msg (non-filesync): ${Gfdi.hex(smart, 32)}")
            return
        }
        FileSync.parseFileListResponse(fss)?.let { list ->
            var added = 0
            list.files.forEach {
                if (remoteFiles.none { r -> r.id == it.id }) { remoteFiles.add(it); added++ }
            }
            log("File-list page: ${list.files.size} file(s) (+$added new, ${remoteFiles.size} total), " +
                "nextPage=${list.nextPageId}")
            scope.launch { onFileListPage(list) }
            return
        }
        FileSync.parseNewFileNotification(fss)?.let { files ->
            log("New-file notification: ${files.size} file(s)")
            files.forEach { log("  • ${it.typeName ?: "?"} ${it.size}b") }
            // While live golf is on, the watch announces updated scorecards this way.
            // Queue them for download+import (deduped by file id + size, since the
            // scorecard grows as holes are played).
            if (golfLiveOn && onPartialFile != null) {
                for (f in files) {
                    val key = "${f.id?.id1}:${f.size}"
                    if (liveSeenFiles.add(key)) liveDownloadQueue.trySend(f)
                }
            }
            return
        }
        if (Protobuf.firstBytes(Protobuf.decode(fss), FileSync.FS_FILE_RESPONSE) != null) {
            val handle = FileSync.parseFileResponseHandle(fss)
            log("File response: transfer handle=$handle")
            fileResponseHandles.trySend(handle ?: -1)
            return
        }
        log("FileSyncService msg (unhandled): ${Gfdi.hex(fss, 32)}")
    }

    /** Page through the file list; when the last page arrives, download golf files. */
    private suspend fun onFileListPage(list: FileSync.FileList) {
        // Page through the whole list. The watch caps each page (~100 files) and on the
        // first page reports nextPageId=0 (Gadgetbridge #5461), so we bootstrap the cursor
        // from the max pageId seen. After that, FOLLOW the watch's explicit nextPageId even
        // across empty boundary pages — the watch returned an empty page but a real
        // nextPage=1468, meaning more files exist beyond our initial guess. Only fall back
        // to "added new files" when there's no explicit cursor, and guard against runaway.
        val explicitNext = list.nextPageId?.takeIf { it != 0 }
        val maxPageId = list.files.mapNotNull { it.pageId }.maxOrNull()
        val next = explicitNext ?: maxPageId
        val addedNew = remoteFiles.size > prevRemoteCount
        prevRemoteCount = remoteFiles.size
        emptyPageStreak = if (list.files.isEmpty()) emptyPageStreak + 1 else 0
        log("  paging: explicitNext=$explicitNext maxPageId=$maxPageId addedNew=$addedNew " +
            "empty=$emptyPageStreak (${remoteFiles.size} files so far)")
        val worthContinuing = explicitNext != null || addedNew
        if (next != null && next != 0 && next != lastFileListCursor && worthContinuing &&
            emptyPageStreak < 20 && filePagesFetched < 100) {
            lastFileListCursor = next
            filePagesFetched++
            log("  → requesting next page at cursor $next")
            sendProtobuf(FileSync.buildFileListRequest(next))
            return
        }
        log("  → paging done (${remoteFiles.size} files total)")
        if (diagnosticListOnly) {
            log("── File list (V2, no download) — ${remoteFiles.size} file(s), largest first:")
            remoteFiles.sortedByDescending { it.size }.take(60).forEach {
                log("  id=${it.id?.id1} type=${it.typeCode}/${it.typeName ?: "?"} ${it.size}b")
            }
            log("── Re-run 'List files' after a hole or two — a file whose size GROWS is the live round.")
            log("Heads-up: this list only shows UNSYNCED files, so an in-progress round may not appear until you save it.")
            diagnosticListOnly = false
            send(Gfdi.systemEvent(Gfdi.EVENT_SYNC_COMPLETE))
            _state.value = State.READY
            return
        }
        // The golf round's file_id.type is unknown and NOT the "sports" code (code 9
        // turned out to be monitoring). So scan every distinct file and let importFit
        // decide by content. Cap each identical (typeCode,size) bucket at two files so
        // the ~50 uniform settings/monitoring files don't dominate the transfer — real
        // golf files have distinctive sizes and are always kept.
        val bucket = HashMap<String, Int>()
        val candidates = remoteFiles.filter { f ->
            val k = "${f.typeCode}:${f.size}"
            val n = bucket.getOrDefault(k, 0)
            bucket[k] = n + 1
            n < 2
        }
        log("Enumeration complete: ${remoteFiles.size} files (${candidates.size} distinct to scan).")
        var newRounds = 0
        var golfFiles = 0
        for (file in candidates) {
            val bytes = downloadFileV2(file)
            if (bytes == null) { log("  download failed for ${file.id?.id1}"); continue }
            try {
                val summary = onFileDownloaded("v2_${file.id?.id1}.fit", bytes)
                // Only surface files that actually carry golf/activity data; the rest
                // (settings, monitoring, …) are skipped silently to keep the log usable.
                if (!summary.startsWith("skipped")) {
                    golfFiles++
                    if (summary.startsWith("NEW round")) newRounds++
                    log("  ★ code ${file.typeCode} ${file.typeName ?: "type?"} (${bytes.size}b) → $summary")
                }
            } catch (e: Exception) {
                log("  import error: ${e.message}")
            }
        }
        if (golfFiles == 0) {
            log("Scan done: the watch is not currently offering any golf rounds over " +
                "Bluetooth — its ${remoteFiles.size} files are all monitoring/wellness/settings. " +
                "Garmin only advertises UNSYNCED activities here, so a round already pulled by " +
                "Garmin Connect/Gadgetbridge is hidden. Play & save a new round, then sync again " +
                "and it will appear.")
        } else {
            log("Scan done: $golfFiles golf/activity file(s), $newRounds new round(s) imported.")
        }
        send(Gfdi.systemEvent(Gfdi.EVENT_SYNC_COMPLETE))
    }

    private fun hex(s: String): ByteArray =
        ByteArray(s.length / 2) { ((s[it * 2].digitToInt(16) shl 4) or s[it * 2 + 1].digitToInt(16)).toByte() }

    /**
     * Replicate the capability handshake Garmin Connect performs before golf. Caddie
     * normally sends an EMPTY configuration; the watch appears to gate the golf service
     * on a host that declares these capability bits (captured verbatim from Connect).
     */
    private suspend fun declareGolfCapabilities() {
        log("Live golf: declaring Garmin Golf capabilities…")
        // Two CONFIGURATION messages carrying Connect's capability flags.
        send(Gfdi.frame(Gfdi.MSG_CONFIGURATION, hex("0ffa07080006001e00823402150a0021")))
        send(Gfdi.frame(Gfdi.MSG_CONFIGURATION, hex("11f9ffdffffffffeccdfa50a172b02230002")))
        // Ask for supported file types, then declare device settings, as Connect does.
        send(Gfdi.frame(Gfdi.MSG_SUPPORTED_FILE_TYPES, ByteArray(0)))
        send(Gfdi.frame(Gfdi.MSG_DEVICE_SETTINGS, hex("03060101070101080100")))
    }

    private suspend fun completeHandshake() {
        val address = device?.address ?: return
        if (isGolfArmed()) declareGolfCapabilities() else send(Gfdi.configuration())
        awaitResponse(Gfdi.MSG_CONFIGURATION, 5000)  // tolerate silence
        if (!isPaired(address)) {
            send(Gfdi.systemEvent(Gfdi.EVENT_PAIR_COMPLETE))
            markPaired(address)
            log("Pairing complete.")
        }
        send(Gfdi.systemEvent(Gfdi.EVENT_SYNC_READY))
        _state.value = State.READY
        if (isGolfArmed()) {
            // Mirror the real Garmin Golf app: register as the golf app immediately,
            // before any bulk file sync, so the watch starts announcing scorecards.
            log("Handshake complete — live golf armed; registering as golf app (skipping bulk sync).")
            startGolfLive()
        } else {
            log("Handshake complete — syncing golf files")
            startSync()
        }
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
            if (verboseRx) log("(out-of-band ack[${r.requestId}] while waiting for $requestId)")
        }
    }

    /**
     * V2 file download: request the file over FileSyncService, register a
     * FILE_TRANSFER multi-link service, pull the raw deflate stream on its
     * handle, and inflate to the FIT file.
     */
    private suspend fun downloadFileV2(file: FileSync.RemoteFile): ByteArray? {
        log("Requesting ${file.typeName ?: "file"} ${file.size}b (id=${file.id?.id1})…")
        // Drain stale state
        while (fileResponseHandles.tryReceive().getOrNull() != null) {}
        while (fileXferChunks.tryReceive().getOrNull() != null) {}
        while (fileXferDone.tryReceive().getOrNull() != null) {}

        // 1. FileRequest → FileResponse(handle)
        sendProtobuf(FileSync.buildFileRequest(file))
        val fileHandle = withTimeoutOrNull(10_000) { fileResponseHandles.receive() }
        if (fileHandle == null || fileHandle < 0) { log("  no/failed FileResponse"); return null }

        // 2. Register a FILE_TRANSFER multi-link service. Cycle through the pool so a
        //    just-closed service handle is never immediately re-registered.
        val serviceCode = MultiLink.FILE_TRANSFER_SERVICES[
            fileXferServiceIdx % MultiLink.FILE_TRANSFER_SERVICES.size]
        fileXferServiceIdx++
        while (controlResponses.tryReceive().getOrNull() != null) {}
        sendRaw(MultiLink.registerRequest(serviceCode))
        val reg = withTimeoutOrNull(5000) { controlResponses.receive() }
        if (reg == null || reg.handle == 0) { log("  file-transfer service registration failed"); return null }
        fileXferHandle = reg.handle
        fileXferService = serviceCode
        log("  transfer service=0x${serviceCode.toString(16)} handle=${fileXferHandle}")

        var closedByWatch = false
        try {
            // 3. Ask for the file on that handle: [00 00][fileHandle:2 LE][00 00]
            val req = java.nio.ByteBuffer.allocate(6).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            req.put(0); req.put(0); req.putShort(fileHandle.toShort()); req.put(0); req.put(0)
            sendRaw(MultiLink.fragment(fileXferHandle, req.array(), mtu - 3).first())

            // 4. The transfer handle delivers, in order:
            //      • a 3-byte [00 00 00] status marker (the response to our request) — dropped
            //      • the raw deflate file bytes, one BLE packet at a time
            //      • (out of band) a control-channel CLOSE for this service = end of file
            //    Completion is the watch's close, NOT a zlib/idle heuristic. Wait on
            //    BOTH the data channel and the close signal at once via select() so the
            //    close ends the transfer immediately (data and close arrive on separate
            //    channels — polling one blocks on the other).
            val out = ByteArrayOutputStream()
            var started = false
            fun consume(chunk: ByteArray) {
                if (!started) {
                    started = true // drop the [00 00 00] status marker packet
                    if (chunk.size != 3) log("  unexpected transfer header ${Gfdi.hex(chunk, 8)}")
                } else {
                    out.write(chunk)
                }
            }
            loop@ while (true) {
                val closed = withTimeoutOrNull(8000) {
                    kotlinx.coroutines.selects.select<Boolean> {
                        fileXferChunks.onReceive { consume(it); false }
                        fileXferDone.onReceive { true }
                    }
                }
                when (closed) {
                    null -> { log("  transfer idle at ${out.size()}b"); break@loop } // fallback only
                    true -> {
                        // Watch closed the handle: drain any data still buffered, then finish.
                        while (true) { consume(fileXferChunks.tryReceive().getOrNull() ?: break) }
                        closedByWatch = true
                        break@loop
                    }
                    false -> { /* consumed a data chunk; keep going */ }
                }
            }
            if (closedByWatch) log("  transfer complete (${out.size()}b, watch closed handle)")

            val raw = out.toByteArray()
            if (raw.isEmpty()) return null
            val inflated = inflate(raw)
            if (inflated == null) { log("  inflate failed (${raw.size}b raw)"); return null }
            log("  inflated ${raw.size}b → ${inflated.size}b")
            return inflated
        } finally {
            // Only send our own close if the watch didn't already close the handle.
            if (!closedByWatch) runCatching { sendRaw(MultiLink.closeHandle(serviceCode, fileXferHandle)) }
            fileXferHandle = -1
            fileXferService = -1
        }
    }

    private fun inflate(data: ByteArray): ByteArray? {
        for (nowrap in listOf(false, true)) {
            try {
                val inf = java.util.zip.Inflater(nowrap)
                inf.setInput(data)
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0 && inf.needsInput()) break
                    out.write(buf, 0, n)
                }
                inf.end()
                if (out.size() > 0) return out.toByteArray()
            } catch (_: Exception) {}
        }
        return null
    }

    // ---- Sync ------------------------------------------------------------------

    /**
     * Forget which files were already imported so the next sync re-downloads and
     * re-imports every golf file on the watch. Useful to repair a bad import or
     * just to confirm the transfer end-to-end.
     */
    fun clearSyncHistory() {
        prefs.edit().remove("synced_files").apply()
        log("Sync history cleared — next sync re-downloads all golf files.")
    }

    /** Clear the sync history and immediately re-sync (when connected). */
    fun resyncAll() {
        clearSyncHistory()
        if (_state.value == State.READY) startSync()
        else log("Connect first, then Sync to re-download.")
    }

    /**
     * Diagnostic: enumerate the watch's files and log each with its size, WITHOUT
     * downloading anything. Safe to run mid-round — re-run it every hole or two and
     * watch for a file whose size grows (that would be the live round, if the watch
     * exposes its in-progress activity).
     */
    fun listFilesOnly() {
        scope.launch {
            if (_state.value != State.READY) { log("Connect to the watch first, then List files."); return@launch }
            _state.value = State.SYNCING
            diagnosticListOnly = true
            try {
                log("── Diagnostic: listing files (no download) ──")
                send(Gfdi.directoryFilter())
                awaitResponse(Gfdi.MSG_DIRECTORY_FILE_FILTER, 3000)
                val dir = downloadFile(0)
                if (dir == null) {
                    log("Directory download failed")
                    diagnosticListOnly = false
                    _state.value = State.READY
                    return@launch
                }
                val entries = Gfdi.parseDirectory(dir)
                if (entries.isNotEmpty()) {
                    log("── ANT-FS directory — ${entries.size} file(s), newest first:")
                    entries.sortedByDescending { it.fitTimestamp }.forEach {
                        log("  #${it.index} type=${it.dataType} sub=${it.subType} num=${it.number} ${it.size}b ts=${it.fitTimestamp}")
                    }
                    _directory.value = entries
                    log("── Re-run 'List files' after a hole — a growing size means live round data is available.")
                    diagnosticListOnly = false
                    _state.value = State.READY
                } else {
                    log("Empty ANT-FS directory — enumerating via FileSyncService (V2)…")
                    remoteFiles.clear(); lastFileListCursor = -1; prevRemoteCount = 0; emptyPageStreak = 0; filePagesFetched = 0
                    sendProtobuf(FileSync.buildFileListRequest(null))
                    // onFileListPage finishes the diagnostic (no download); leave the session open.
                }
            } catch (e: Exception) {
                log("List files failed: ${e.message}")
                diagnosticListOnly = false
                if (_state.value == State.SYNCING) _state.value = State.READY
            }
        }
    }

    /**
     * Start watching for an in-progress round: every [intervalMs] (while connected and
     * idle) re-enumerate the watch's files and import any that have grown since the last
     * look as a partial round. Safe no-op if no partial importer was wired in.
     */
    /**
     * Realtime-capture diagnostic. Declares the app foregrounded and logs EVERY message
     * the watch pushes, so a round played while this is on reveals whatever live stream
     * (if any) the watch emits. Share the log afterward to reverse the golf realtime feed.
     */
    fun startRealtimeCapture() {
        scope.launch {
            if (_state.value != State.READY && _state.value != State.SYNCING) {
                log("Connect to the watch first, then start capture."); return@launch
            }
            captureAll = true
            verboseRx = true
            log("── REALTIME CAPTURE ON. Play a hole or two, then Share the log (as a file).")
            log("   Declaring host foreground so the watch may begin streaming…")
            runCatching { send(Gfdi.systemEvent(Gfdi.EVENT_HOST_FOREGROUND)) }
        }
    }

    fun stopRealtimeCapture() {
        captureAll = false
        verboseRx = false
        log("── Realtime capture OFF.")
    }

    val isCapturing: Boolean get() = captureAll

    /**
     * Live golf: while a round is in progress on the watch, poll the golf service
     * (Smart field 7) for the current scorecard every [intervalMs]. The watch answers
     * with the whole scorecard as a golf FIT, which we import as a live round. See
     * [GolfLive]. Safe no-op if no partial importer was wired in.
     */
    /** Persisted: register as the golf app on connect so the watch streams scorecards. */
    fun armGolfLive(on: Boolean) {
        prefs.edit().putBoolean("golf_armed", on).apply()
        if (on) {
            log("Live golf armed — reconnecting so registration happens first…")
            val d = device
            if (d != null) connect(d) else log("Now connect to the watch to begin live scoring.")
        } else {
            stopGolfLive()
            log("Live golf disarmed.")
        }
    }

    private fun isGolfArmed() = prefs.getBoolean("golf_armed", false)

    fun startGolfLive(fallbackMs: Long = 60_000) {
        if (onPartialFile == null) { log("Live golf unavailable (no partial importer)."); return }
        if (golfLiveOn) return
        golfLiveOn = true
        lastAnnouncedSeq = 0
        log("── LIVE GOLF ON. Registered as the golf app; waiting for the watch to announce " +
            "scorecard updates (play a hole).")
        // Consume announced scorecard files one at a time and import each as a partial round.
        liveDownloadJob = scope.launch {
            for (file in liveDownloadQueue) {
                if (!golfLiveOn) break
                val importer = onPartialFile ?: continue
                log("Live golf: fetching announced ${file.typeName ?: "file"} ${file.size}b…")
                val bytes = runCatching { downloadFileV2(file) }.getOrNull()
                if (bytes == null) { log("  fetch failed"); continue }
                val summary = runCatching { importer("golf_live_${file.id?.id1}.fit", bytes) }
                    .getOrElse { "import error: ${it.message}" }
                log("  → $summary")
            }
        }
        golfJob = scope.launch {
            registerGolfApp()
            // The watch drives us: it announces 5:{7:{1:seq}} when the scorecard changes and
            // we poll that seq. A 15s heartbeat makes the log self-documenting — it shows
            // unambiguously whether the watch is streaming or silent, no matter when the log
            // is captured — and re-polls the last announced seq as a safety net.
            var elapsed = 0
            while (golfLiveOn) {
                kotlinx.coroutines.delay(15_000)
                elapsed += 15
                if (lastAnnouncedSeq > 0) {
                    log("Live golf: ${elapsed}s alive — last announced seq=$lastAnnouncedSeq, re-polling")
                    if (_state.value == State.READY) {
                        runCatching { sendProtobuf(GolfLive.buildPoll(lastAnnouncedSeq)) }
                    }
                } else {
                    log("Live golf: ${elapsed}s — no announcement yet; re-declaring foreground. " +
                        "Score a hole on the watch and keep waiting.")
                    if (_state.value == State.READY) {
                        runCatching { send(Gfdi.systemEvent(Gfdi.EVENT_HOST_FOREGROUND)) }
                    }
                }
            }
        }
    }

    /** Send the captured Garmin Golf app-registration Smart messages (service 13). */
    private suspend fun registerGolfApp() {
        log("Live golf: registering as Garmin Golf app…")
        for (reg in GolfLive.registrationMessages) {
            runCatching { sendProtobuf(reg) }
            kotlinx.coroutines.delay(300)
        }
        // The s30 "hello" is what makes the watch begin its onboarding burst (token
        // request, s16/s10 pokes) that precedes scorecard announcements.
        kotlinx.coroutines.delay(200)
        log("Live golf: sending s30 hello to start onboarding…")
        runCatching { sendProtobuf(GolfLive.buildS30Hello()) }
        // Connect then declares the app foregrounded and nudges golf active before the
        // watch starts streaming. Mirror that.
        kotlinx.coroutines.delay(600)
        log("Live golf: declaring app foreground + activating golf…")
        runCatching { send(Gfdi.systemEvent(Gfdi.EVENT_HOST_FOREGROUND)) }
        runCatching { sendProtobuf(GolfLive.buildS42Activate()) }
        runCatching { sendProtobuf(GolfLive.buildFileSyncActivate()) }
    }

    fun stopGolfLive() {
        if (!golfLiveOn) return
        golfLiveOn = false
        golfJob?.cancel(); golfJob = null
        liveDownloadJob?.cancel(); liveDownloadJob = null
        liveSeenFiles.clear()
        while (liveDownloadQueue.tryReceive().getOrNull() != null) { /* drain */ }
        log("── Live golf OFF.")
    }

    val isGolfLive: Boolean get() = golfLiveOn

    /** Handle an inbound golf-service (field 7) Smart message: scorecard push or descriptor. */
    private fun handleGolfSmart(smart: ByteArray) {
        GolfLive.parsePush(smart)?.let { push ->
            log("Live golf: scorecard push seq=${push.seq} (${push.fit.size}b FIT)")
            scope.launch {
                runCatching { sendProtobuf(GolfLive.buildReceiveAck(push.seq)) }
                val importer = onPartialFile
                if (importer != null) {
                    val summary = runCatching { importer("golf_live_${push.seq}.fit", push.fit) }
                        .getOrElse { "import error: ${it.message}" }
                    log("  → $summary")
                }
            }
            return
        }
        GolfLive.parseXfer(smart)?.let { n ->
            // Secondary transfer descriptor — acknowledge so the watch stays happy.
            scope.launch { runCatching { sendProtobuf(GolfLive.buildXferAck(n, n, 0L)) } }
            return
        }
        log("Live golf: other service-7 msg ${Gfdi.hex(smart, 24)}")
    }

    fun startSync() {
        scope.launch {
            if (_state.value == State.SYNCING) return@launch
            _state.value = State.SYNCING
            verboseRx = false // transport is proven; keep the log readable (no raw byte spam)
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

                // Newer watches (vívoactive 5) leave the ANT-FS directory empty and
                // enumerate files over the protobuf FileSyncService instead.
                if (entries.isEmpty()) {
                    log("Empty directory — requesting file list via FileSyncService (V2)…")
                    remoteFiles.clear()
                    lastFileListCursor = -1
                    prevRemoteCount = 0
                    emptyPageStreak = 0
                    filePagesFetched = 0
                    sendProtobuf(FileSync.buildFileListRequest(null))
                    // Responses arrive asynchronously; leave the session open to receive them.
                    return@launch
                }

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
