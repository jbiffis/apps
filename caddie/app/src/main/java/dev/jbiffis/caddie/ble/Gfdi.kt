package dev.jbiffis.caddie.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GFDI (Garmin device interface) message layer.
 *
 * Wire format (all little-endian), the same protocol Garmin Connect and
 * Gadgetbridge speak over BLE:
 *
 *   [ length u16 ][ messageId u16 ][ payload ... ][ crc16 u16 ]
 *
 * `length` covers the whole packet including itself and the CRC. The CRC is the
 * FIT CRC-16 computed over everything before it. On the BLE transport each packet
 * is COBS-encoded and delimited with 0x00 bytes.
 *
 * NOTE: message IDs and payload layouts here were implemented from public
 * protocol documentation and packet captures of similar devices — treat this
 * layer as experimental until verified against your watch (the Sync screen
 * logs every frame to make that iteration easy).
 */
object Gfdi {

    // Message IDs
    const val MSG_RESPONSE = 5000
    const val MSG_DOWNLOAD_REQUEST = 5002
    const val MSG_UPLOAD_REQUEST = 5003
    const val MSG_DATA_TRANSFER = 5004
    const val MSG_DEVICE_INFORMATION = 5024
    const val MSG_SYSTEM_EVENT = 5030
    const val MSG_SUPPORTED_FILE_TYPES = 5031
    const val MSG_SYNC_REQUEST = 5037
    const val MSG_CONFIGURATION = 5050
    const val MSG_AUTH_NEGOTIATION = 5051

    const val STATUS_ACK = 0
    const val STATUS_NAK = 1
    const val STATUS_UNSUPPORTED = 2

    class Message(val id: Int, val payload: ByteArray) {
        override fun toString() = "GFDI(id=$id, ${payload.size} bytes)"
    }

    /** Frame a GFDI message: length + id + payload + crc. */
    fun frame(id: Int, payload: ByteArray): ByteArray {
        val total = 2 + 2 + payload.size + 2
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(total.toShort())
        buf.putShort(id.toShort())
        buf.put(payload)
        val crc = Crc16.compute(buf.array(), 0, total - 2)
        buf.putShort(crc.toShort())
        return buf.array()
    }

    /** Parse and CRC-check a de-COBSed packet. Returns null if malformed. */
    fun parse(packet: ByteArray): Message? {
        if (packet.size < 6) return null
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val length = buf.short.toInt() and 0xFFFF
        if (length != packet.size) return null
        val id = buf.short.toInt() and 0xFFFF
        val crc = ByteBuffer.wrap(packet, packet.size - 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (crc != Crc16.compute(packet, 0, packet.size - 2)) return null
        return Message(id, packet.copyOfRange(4, packet.size - 2))
    }

    // ---- Outgoing messages ----------------------------------------------------

    /** Generic ACK/NAK for a received message. */
    fun response(requestId: Int, status: Int, extra: ByteArray = ByteArray(0)): ByteArray {
        val payload = ByteBuffer.allocate(3 + extra.size).order(ByteOrder.LITTLE_ENDIAN)
        payload.putShort(requestId.toShort())
        payload.put(status.toByte())
        payload.put(extra)
        return frame(MSG_RESPONSE, payload.array())
    }

    /**
     * Device-information exchange. The watch sends its info right after connect;
     * we answer with a response carrying our own identity so it accepts the link.
     */
    fun deviceInformationResponse(): ByteArray {
        val name = "Caddie"
        val manufacturer = "Caddie"
        val model = "Android"
        val payload = ByteBuffer.allocate(2 + 1 + 2 + 2 + 4 + 2 + 2 +
            1 + name.length + 1 + manufacturer.length + 1 + model.length + 3)
            .order(ByteOrder.LITTLE_ENDIAN)
        payload.putShort(MSG_DEVICE_INFORMATION.toShort()) // request being answered
        payload.put(STATUS_ACK.toByte())
        payload.putShort(112)                  // protocol version
        payload.putShort((-1).toShort())       // product number (unknown host)
        payload.putInt(1)                      // unit number
        payload.putShort(100)                  // software version
        payload.putShort(16384)                // max GFDI packet size we accept
        payload.put(name.length.toByte());  payload.put(name.toByteArray(Charsets.US_ASCII))
        payload.put(manufacturer.length.toByte()); payload.put(manufacturer.toByteArray(Charsets.US_ASCII))
        payload.put(model.length.toByte()); payload.put(model.toByteArray(Charsets.US_ASCII))
        payload.put(1) // protocol flags
        payload.put(0)
        payload.put(0)
        return frame(MSG_RESPONSE, payload.array().copyOf(payload.position()))
    }

    /**
     * Request a download of file [index] (index 0 = the ANT-FS style directory
     * listing all files on the device).
     */
    fun downloadRequest(index: Int, offset: Long): ByteArray {
        val payload = ByteBuffer.allocate(2 + 4 + 1 + 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        payload.putShort(index.toShort())
        payload.putInt(offset.toInt())
        payload.put(0)          // initial request (1 = crc verify continuation)
        payload.putShort(0)     // crc seed
        payload.putInt(0)       // max size (0 = whole file)
        return frame(MSG_DOWNLOAD_REQUEST, payload.array())
    }

    /** ACK a DATA_TRANSFER chunk and tell the device the next offset we expect. */
    fun dataTransferAck(nextOffset: Long): ByteArray {
        val extra = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        extra.putInt(nextOffset.toInt())
        return response(MSG_DATA_TRANSFER, STATUS_ACK, extra.array())
    }

    fun syncRequest(): ByteArray {
        // 0 = sync everything the device has flagged for upload
        return frame(MSG_SYNC_REQUEST, byteArrayOf(0, 1))
    }

    // ---- Incoming payload parsers ---------------------------------------------

    class DataTransfer(val offset: Long, val data: ByteArray)

    fun parseDataTransfer(payload: ByteArray): DataTransfer? {
        if (payload.size < 4) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val offset = buf.int.toLong() and 0xFFFFFFFFL
        return DataTransfer(offset, payload.copyOfRange(4, payload.size))
    }

    class ResponseMsg(val requestId: Int, val status: Int, val extra: ByteArray)

    fun parseResponse(payload: ByteArray): ResponseMsg? {
        if (payload.size < 3) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val reqId = buf.short.toInt() and 0xFFFF
        val status = buf.get().toInt() and 0xFF
        return ResponseMsg(reqId, status, payload.copyOfRange(3, payload.size))
    }

    // ---- ANT-FS directory ------------------------------------------------------

    /**
     * Directory file (download index 0): 16-byte header followed by 16-byte
     * entries — index u16, dataType u8, subType u8, number u16, specificFlags u8,
     * generalFlags u8, size u32, timestamp u32 (FIT epoch).
     */
    class DirectoryEntry(
        val index: Int,
        val dataType: Int,   // 128 = FIT file
        val subType: Int,    // FIT file_id.type: 4 = activity, 38 = golf score
        val number: Int,
        val size: Long,
        val fitTimestamp: Long,
    )

    fun parseDirectory(bytes: ByteArray): List<DirectoryEntry> {
        if (bytes.size < 16) return emptyList()
        val out = ArrayList<DirectoryEntry>()
        var pos = 16
        while (pos + 16 <= bytes.size) {
            val buf = ByteBuffer.wrap(bytes, pos, 16).order(ByteOrder.LITTLE_ENDIAN)
            val index = buf.short.toInt() and 0xFFFF
            val dataType = buf.get().toInt() and 0xFF
            val subType = buf.get().toInt() and 0xFF
            val number = buf.short.toInt() and 0xFFFF
            buf.get() // specific flags
            buf.get() // general flags
            val size = buf.int.toLong() and 0xFFFFFFFFL
            val ts = buf.int.toLong() and 0xFFFFFFFFL
            if (index != 0 && dataType != 0) {
                out.add(DirectoryEntry(index, dataType, subType, number, size, ts))
            }
            pos += 16
        }
        return out
    }
}
