package dev.jbiffis.caddie.ble

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * GFDI (Garmin device interface) message layer.
 *
 * Wire format (little-endian):
 *
 *   [ length u16 ][ messageId u16 ][ payload ... ][ crc16 u16 ]
 *
 * `length` covers the whole packet including itself and the CRC (FIT CRC-16 over
 * everything before it). On BLE each packet is COBS-encoded and 0x00-delimited.
 *
 * Message ids and payload layouts follow the publicly documented protocol that
 * Garmin Connect Mobile speaks (the same one Gadgetbridge implements for
 * devices like the vivoactive 5). Where the docs are thin the parsers are
 * written defensively and everything unexpected is surfaced in the sync log.
 */
object Gfdi {

    // Message IDs
    const val MSG_RESPONSE = 5000
    const val MSG_DOWNLOAD_REQUEST = 5002
    const val MSG_UPLOAD_REQUEST = 5003
    const val MSG_FILE_TRANSFER_DATA = 5004
    const val MSG_CREATE_FILE = 5005
    const val MSG_DIRECTORY_FILE_FILTER = 5007
    const val MSG_FILE_READY = 5009
    const val MSG_FIT_DEFINITION = 5011
    const val MSG_FIT_DATA = 5012
    const val MSG_WEATHER_REQUEST = 5014
    const val MSG_BATTERY_STATUS = 5023
    const val MSG_DEVICE_INFORMATION = 5024
    const val MSG_DEVICE_SETTINGS = 5026
    const val MSG_SYSTEM_EVENT = 5030
    const val MSG_SUPPORTED_FILE_TYPES = 5031
    const val MSG_NOTIFICATION_SOURCE = 5033
    const val MSG_SYNC_REQUEST = 5037
    const val MSG_PROTOBUF_REQUEST = 5043
    const val MSG_PROTOBUF_RESPONSE = 5044
    const val MSG_CONFIGURATION = 5050
    const val MSG_CURRENT_TIME_REQUEST = 5052
    const val MSG_AUTH_NEGOTIATION = 5101

    const val STATUS_ACK = 0
    const val STATUS_NAK = 1
    const val STATUS_UNSUPPORTED = 2
    const val STATUS_DECODE_ERROR = 3

    // System event types (SYSTEM_EVENT payload byte)
    const val EVENT_SYNC_COMPLETE = 0
    const val EVENT_SYNC_FAIL = 1
    const val EVENT_PAIR_START = 3
    const val EVENT_PAIR_COMPLETE = 4
    const val EVENT_PAIR_FAIL = 5
    const val EVENT_HOST_FOREGROUND = 6
    const val EVENT_SYNC_READY = 8

    const val FIT_EPOCH_OFFSET_S = 631065600L

    /**
     * @param id       decoded message type (e.g. 5004)
     * @param rawType  the on-wire type field; for device-initiated messages this
     *                 carries a sequence number and must be echoed back in the ACK
     * @param seq      device message sequence (0-127), or -1 if not sequenced
     */
    class Message(val id: Int, val payload: ByteArray, val rawType: Int = id, val seq: Int = -1) {
        val sequenced: Boolean get() = seq >= 0
        override fun toString() = "GFDI(id=$id, seq=$seq, ${payload.size}b)"
    }

    /** Frame a GFDI message: length + id + payload + crc. */
    fun frame(id: Int, payload: ByteArray): ByteArray {
        val total = 2 + 2 + payload.size + 2
        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(total.toShort())
        buf.putShort(id.toShort())
        buf.put(payload)
        buf.putShort(Crc16.compute(buf.array(), 0, total - 2).toShort())
        return buf.array()
    }

    /** Parse and CRC-check a de-COBSed packet. Returns null if malformed. */
    fun parse(packet: ByteArray): Message? {
        if (packet.size < 6) return null
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        val length = buf.short.toInt() and 0xFFFF
        if (length != packet.size) return null
        val rawType = buf.short.toInt() and 0xFFFF
        val crc = ByteBuffer.wrap(packet, packet.size - 2, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        if (crc != Crc16.compute(packet, 0, packet.size - 2)) return null
        // Device-initiated messages set bit15 of the type field: the low byte is
        // (type - 5000) and the high byte's low 7 bits are a message sequence
        // number. The ACK must echo the exact rawType (sequence included).
        val payload = packet.copyOfRange(4, packet.size - 2)
        return if (rawType and 0x8000 != 0) {
            val seq = (rawType shr 8) and 0x7F
            Message(id = 5000 + (rawType and 0xFF), payload = payload, rawType = rawType, seq = seq)
        } else {
            Message(id = rawType, payload = payload, rawType = rawType, seq = -1)
        }
    }

    // ---- Outgoing messages -----------------------------------------------------

    /** Generic ACK/NAK for a received message. */
    fun response(requestId: Int, status: Int, extra: ByteArray = ByteArray(0)): ByteArray {
        val payload = ByteBuffer.allocate(3 + extra.size).order(ByteOrder.LITTLE_ENDIAN)
        payload.putShort(requestId.toShort())
        payload.put(status.toByte())
        payload.put(extra)
        return frame(MSG_RESPONSE, payload.array())
    }

    /**
     * Our half of the device-information exchange. The watch opens the session
     * by sending its own MSG_DEVICE_INFORMATION; this is the required response.
     */
    fun deviceInformationResponse(unitNumber: Long): ByteArray {
        val friendlyName = "Caddie"
        val deviceName = "Android"
        val deviceModel = "Phone"
        val buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(MSG_DEVICE_INFORMATION.toShort())     // request being answered
        buf.put(STATUS_ACK.toByte())
        buf.putShort(200)                                   // protocol version (2.0)
        buf.putShort((-1).toShort())                        // product number (generic host)
        buf.putInt(unitNumber.toInt())                      // our stable unit id
        buf.putShort(100)                                   // software version (1.00)
        buf.putShort(16384)                                 // max GFDI packet size we accept
        for (s in listOf(friendlyName, deviceName, deviceModel)) {
            buf.put(s.length.toByte())
            buf.put(s.toByteArray(Charsets.US_ASCII))
        }
        return frame(MSG_RESPONSE, buf.array().copyOf(buf.position()))
    }

    /** Parse the watch's device information request payload (best effort). */
    class DeviceInfo(val protocolVersion: Int, val productNumber: Int, val unitNumber: Long, val softwareVersion: Int, val maxPacketSize: Int, val name: String?)

    fun parseDeviceInformation(payload: ByteArray): DeviceInfo? {
        if (payload.size < 12) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val protocol = buf.short.toInt() and 0xFFFF
        val product = buf.short.toInt() and 0xFFFF
        val unit = buf.int.toLong() and 0xFFFFFFFFL
        val sw = buf.short.toInt() and 0xFFFF
        val maxPacket = buf.short.toInt() and 0xFFFF
        val name = if (buf.remaining() > 1) {
            val len = buf.get().toInt() and 0xFF
            if (len in 1..buf.remaining()) {
                val bytes = ByteArray(len); buf.get(bytes); String(bytes, Charsets.US_ASCII)
            } else null
        } else null
        return DeviceInfo(protocol, product, unit, sw, maxPacket, name)
    }

    /** Host capability flags. An empty/zero set keeps the session plain (no GC features). */
    fun configuration(): ByteArray {
        val capabilities = byteArrayOf(0x00)
        val payload = ByteBuffer.allocate(1 + capabilities.size).order(ByteOrder.LITTLE_ENDIAN)
        payload.put(capabilities.size.toByte())
        payload.put(capabilities)
        return frame(MSG_CONFIGURATION, payload.array())
    }

    /** SYSTEM_EVENT payload is [eventType:u8][value:u8]. */
    fun systemEvent(event: Int, value: Int = 0): ByteArray =
        frame(MSG_SYSTEM_EVENT, byteArrayOf(event.toByte(), value.toByte()))

    /**
     * Answer the watch's current-time request. Layout (as a 5000 status message):
     * ref(u16) status(u8) referenceId(u32) garminTs(u32) tzOffset(i32) dstEnd(i32) dstStart(i32).
     */
    fun currentTimeResponse(nowMs: Long, tzOffsetS: Int): ByteArray {
        val garminTs = nowMs / 1000 - FIT_EPOCH_OFFSET_S
        val extra = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        extra.putInt(0)                   // referenceId
        extra.putInt(garminTs.toInt())    // current time, garmin epoch
        extra.putInt(tzOffsetS)           // timezone offset seconds
        extra.putInt(0)                   // next DST transition end
        extra.putInt(0)                   // next DST transition start
        return response(MSG_CURRENT_TIME_REQUEST, STATUS_ACK, extra.array())
    }

    /** Ask the watch to include every file kind in the directory. */
    fun directoryFilter(): ByteArray = frame(MSG_DIRECTORY_FILE_FILTER, byteArrayOf(0x00))

    /**
     * Request a download of file [index] (index 0 = ANT-FS style directory).
     * requestType 0 opens a new transfer; 1 continues from [offset] with [crcSeed].
     */
    fun downloadRequest(index: Int, offset: Long, requestType: Int = 0, crcSeed: Int = 0): ByteArray {
        val payload = ByteBuffer.allocate(2 + 4 + 1 + 2 + 4).order(ByteOrder.LITTLE_ENDIAN)
        payload.putShort(index.toShort())
        payload.putInt(offset.toInt())
        payload.put(requestType.toByte())
        payload.putShort(crcSeed.toShort())
        payload.putInt(0) // max size, 0 = whole file
        return frame(MSG_DOWNLOAD_REQUEST, payload.array())
    }

    /**
     * ACK a FILE_TRANSFER_DATA chunk, telling the watch the next offset we expect.
     * [ackType] must be the exact type field the watch sent (sequence included).
     */
    fun dataTransferAck(ackType: Int, nextOffset: Long): ByteArray {
        val extra = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        extra.putInt(nextOffset.toInt())
        return response(ackType, STATUS_ACK, extra.array())
    }

    /** Simple ACK echoing the exact type field the watch sent. */
    fun ack(rawType: Int): ByteArray = response(rawType, STATUS_ACK)

    // ---- Incoming payload parsers ----------------------------------------------

    class DataTransfer(val flags: Int, val crc: Int, val offset: Long, val data: ByteArray, val ackType: Int)

    /**
     * FILE_TRANSFER_DATA payload: flags u8, crc16 u16 (running CRC), offset u32, data.
     * [ackType] is the raw type field to echo when acknowledging this chunk.
     */
    fun parseDataTransfer(payload: ByteArray, ackType: Int): DataTransfer? {
        if (payload.size < 7) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val flags = buf.get().toInt() and 0xFF
        val crc = buf.short.toInt() and 0xFFFF
        val offset = buf.int.toLong() and 0xFFFFFFFFL
        return DataTransfer(flags, crc, offset, payload.copyOfRange(7, payload.size), ackType)
    }

    class ResponseMsg(val requestId: Int, val status: Int, val extra: ByteArray)

    fun parseResponse(payload: ByteArray): ResponseMsg? {
        if (payload.size < 3) return null
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val reqId = buf.short.toInt() and 0xFFFF
        val status = buf.get().toInt() and 0xFF
        return ResponseMsg(reqId, status, payload.copyOfRange(3, payload.size))
    }

    // ---- ANT-FS directory --------------------------------------------------------

    /**
     * Directory file (download index 0): 16-byte header then 16-byte entries —
     * index u16, dataType u8, subType u8, number u16, specificFlags u8,
     * generalFlags u8, size u32, timestamp u32 (garmin epoch).
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

    fun hex(bytes: ByteArray, max: Int = 24): String =
        bytes.take(max).joinToString(" ") { "%02x".format(it) } + if (bytes.size > max) " …(${bytes.size}b)" else ""
}
