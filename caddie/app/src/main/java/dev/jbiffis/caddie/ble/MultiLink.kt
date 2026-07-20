package dev.jbiffis.caddie.ble

import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/**
 * Garmin BLE "Multi-Link" (ML) transport used by newer watches (vívoactive 5,
 * Forerunner 2xx/9xx, Fenix 7+ …). Instead of one dedicated GFDI characteristic,
 * these devices multiplex several services over a single write/notify pair,
 * tagging every packet with a one-byte *handle*.
 *
 * Wire format of one BLE packet:  [handle:1][data…]
 *
 *   - handle 0 is the control channel. Its data is a raw ML control message:
 *       [type:1][ …type-specific… ]
 *     REGISTER_ML_REQ (0):  [type=0][clientId:8 LE][service:2 LE][reliability:1]
 *     REGISTER_ML_RESP (1): [type=1][clientId:8][service:2][status:1][handle:1][reliability:1]
 *     CLOSE_ALL_REQ (5):    [type=5]
 *   - once GFDI (service 1) is registered, the device returns a handle; every
 *     GFDI packet is then [handle][ COBS-framed GFDI message ], fragmented to
 *     the MTU. The COBS frame is 0x00-delimited on both ends (see [Cobs]).
 *
 * Layout reverse-engineered/ported from Gadgetbridge's CommunicatorV2 and the
 * garmin-ble project.
 */
object MultiLink {

    // Exact characteristic UUIDs (channel 1 write/notify). base = 6A4Exxxx-667B-…
    val WRITE_CHAR: java.util.UUID = uuid(0x2820)
    val NOTIFY_CHAR: java.util.UUID = uuid(0x2810)

    const val CONTROL_HANDLE = 0x00

    // ML control message types
    const val REGISTER_REQ = 0x00
    const val REGISTER_RESP = 0x01
    const val CLOSE_HANDLE_REQ = 0x02
    const val CLOSE_HANDLE_RESP = 0x03
    const val CLOSE_ALL_REQ = 0x05

    // Service codes
    const val SERVICE_GFDI = 0x0001
    // File-transfer (V2) services — the first free one is registered per transfer.
    val FILE_TRANSFER_SERVICES = intArrayOf(0x2018, 0x4018, 0x6018, 0xa018, 0xc018, 0xe018)
    const val CLIENT_ID = 2L

    const val CLOSE_ALL_REQ_TYPE = 0x05

    private fun uuid(short: Int): java.util.UUID =
        java.util.UUID.fromString(String.format("6a4e%04x-667b-11e3-949a-0800200c9a66", short))

    fun base(short: Int): java.util.UUID = uuid(short)

    /** Control-channel frame to reset any handles left over from a prior session. */
    fun closeAllRequest(): ByteArray = byteArrayOf(CONTROL_HANDLE.toByte(), CLOSE_ALL_REQ.toByte())

    /** Control-channel frame to close one registered service handle. */
    fun closeHandle(service: Int, handle: Int): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 1 + 8 + 2 + 1).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(CONTROL_HANDLE.toByte())
        buf.put(CLOSE_HANDLE_REQ.toByte())
        buf.putLong(CLIENT_ID)
        buf.putShort(service.toShort())
        buf.put(handle.toByte())
        return buf.array()
    }

    /** Control-channel frame registering a service, e.g. GFDI. */
    fun registerRequest(service: Int, reliability: Int = 0, clientId: Long = CLIENT_ID): ByteArray {
        val buf = java.nio.ByteBuffer.allocate(1 + 1 + 8 + 2 + 1).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(CONTROL_HANDLE.toByte())
        buf.put(REGISTER_REQ.toByte())
        buf.putLong(clientId)
        buf.putShort(service.toShort())
        buf.put(reliability.toByte())
        return buf.array()
    }

    class RegisterResponse(val service: Int, val status: Int, val handle: Int, val reliability: Int)

    /** Parse a control-channel packet (handle byte already confirmed to be 0). */
    fun parseControl(packet: ByteArray): RegisterResponse? {
        if (packet.size < 2 || packet[0].toInt() != CONTROL_HANDLE) return null
        if ((packet[1].toInt() and 0xFF) != REGISTER_RESP) return null
        // [handle][type][clientId:8][service:2][status:1][handle:1][reliability:1]
        if (packet.size < 2 + 8 + 2 + 1 + 1 + 1) return null
        val buf = java.nio.ByteBuffer.wrap(packet, 2, packet.size - 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.long // clientId (echoed)
        val service = buf.short.toInt() and 0xFFFF
        val status = buf.get().toInt() and 0xFF
        val handle = buf.get().toInt() and 0xFF
        val reliability = buf.get().toInt() and 0xFF
        return RegisterResponse(service, status, handle, reliability)
    }

    class CloseResponse(val service: Int, val handle: Int, val status: Int)

    /**
     * Parse a control-channel CLOSE_HANDLE_RESP. The watch sends this (unsolicited)
     * once a file-transfer service has delivered a whole file — it is the reliable
     * end-of-transfer signal. Layout: [handle=0][type=3][clientId:8][service:2][handle:1][status:1].
     */
    fun parseCloseResponse(packet: ByteArray): CloseResponse? {
        if (packet.size < 2 || packet[0].toInt() != CONTROL_HANDLE) return null
        if ((packet[1].toInt() and 0xFF) != CLOSE_HANDLE_RESP) return null
        if (packet.size < 2 + 8 + 2 + 1 + 1) return null
        val buf = java.nio.ByteBuffer.wrap(packet, 2, packet.size - 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.long // clientId (echoed)
        val service = buf.short.toInt() and 0xFFFF
        val handle = buf.get().toInt() and 0xFF
        val status = buf.get().toInt() and 0xFF
        return CloseResponse(service, handle, status)
    }

    fun isControl(packet: ByteArray): Boolean = packet.isNotEmpty() && packet[0].toInt() == CONTROL_HANDLE

    /**
     * Fragment an already-COBS-framed GFDI packet for a service [handle], one
     * BLE-sized chunk at a time. Each chunk is prefixed with the handle byte.
     */
    fun fragment(handle: Int, cobsFramed: ByteArray, mtuPayload: Int): List<ByteArray> {
        val perChunk = (mtuPayload - 1).coerceAtLeast(1) // room for the handle byte
        val out = ArrayList<ByteArray>()
        var off = 0
        while (off < cobsFramed.size) {
            val len = minOf(perChunk, cobsFramed.size - off)
            val chunk = ByteArray(len + 1)
            chunk[0] = handle.toByte()
            System.arraycopy(cobsFramed, off, chunk, 1, len)
            out.add(chunk)
            off += len
        }
        if (out.isEmpty()) out.add(byteArrayOf(handle.toByte()))
        return out
    }

    /** Strip the handle byte from an inbound notification; returns handle + payload. */
    fun stripHandle(packet: ByteArray): Pair<Int, ByteArray>? {
        if (packet.isEmpty()) return null
        return (packet[0].toInt() and 0xFF) to packet.copyOfRange(1, packet.size)
    }

    // ---- Multi-Link Reliable (MLR) --------------------------------------------
    // Reliable framing used for file transfer. 2-byte big-endian header:
    //   byte0 = 1 HHH RRRR   (bit7 always set, HHH = handle, high 4 bits of reqNum)
    //   byte1 = RR SSSSSS    (low 2 bits of reqNum, SSSSSS = 6-bit sequence)
    // Data payload follows. An ACK is a header with the receiver's next expected
    // sequence in reqNum and no payload.

    const val MLR_SEQ_MODULO = 0x40

    fun isReliable(packet: ByteArray): Boolean =
        packet.isNotEmpty() && (packet[0].toInt() and 0x80) != 0

    class MlrPacket(val handle: Int, val reqNum: Int, val seq: Int, val payload: ByteArray)

    fun parseReliable(packet: ByteArray): MlrPacket? {
        if (packet.size < 2 || (packet[0].toInt() and 0x80) == 0) return null
        val b0 = packet[0].toInt() and 0xFF
        val b1 = packet[1].toInt() and 0xFF
        val handle = (b0 shr 4) and 0x07
        val reqNum = ((b0 and 0x0F) shl 2) or (b1 shr 6)
        val seq = b1 and 0x3F
        return MlrPacket(handle, reqNum, seq, packet.copyOfRange(2, packet.size))
    }

    /** Build an MLR header (+optional payload). reqNum piggybacks our receive ack. */
    fun buildReliable(handle: Int, reqNum: Int, seq: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val b0 = 0x80 or ((handle and 0x07) shl 4) or ((reqNum shr 2) and 0x0F)
        val b1 = ((reqNum and 0x03) shl 6) or (seq and 0x3F)
        val out = ByteArray(2 + payload.size)
        out[0] = b0.toByte()
        out[1] = b1.toByte()
        System.arraycopy(payload, 0, out, 2, payload.size)
        return out
    }

    /** ACK all reliable packets received so far: header with reqNum = next expected seq, no data. */
    fun reliableAck(handle: Int, nextExpectedSeq: Int): ByteArray =
        buildReliable(handle, nextExpectedSeq, 0)
}
