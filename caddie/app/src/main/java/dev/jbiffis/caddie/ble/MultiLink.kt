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
    const val CLOSE_ALL_REQ = 0x05

    // Service codes
    const val SERVICE_GFDI = 0x0001
    const val CLIENT_ID = 2L

    private fun uuid(short: Int): java.util.UUID =
        java.util.UUID.fromString(String.format("6a4e%04x-667b-11e3-949a-0800200c9a66", short))

    fun base(short: Int): java.util.UUID = uuid(short)

    /** Control-channel frame to reset any handles left over from a prior session. */
    fun closeAllRequest(): ByteArray = byteArrayOf(CONTROL_HANDLE.toByte(), CLOSE_ALL_REQ.toByte())

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
}
