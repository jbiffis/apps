package dev.jbiffis.caddie.ble

import java.io.ByteArrayOutputStream

/**
 * Minimal protobuf (proto2) wire codec — just enough for Garmin's GDI Smart
 * messages (FileSyncService). No generated classes; fields are addressed by
 * number. Supports varint, length-delimited (bytes/string/sub-message) and
 * fixed64. See https://protobuf.dev/programming-guides/encoding/.
 */
object Protobuf {

    const val WIRE_VARINT = 0
    const val WIRE_FIXED64 = 1
    const val WIRE_LEN = 2
    const val WIRE_FIXED32 = 5

    class Writer {
        private val out = ByteArrayOutputStream()

        fun varint(field: Int, value: Long): Writer {
            tag(field, WIRE_VARINT); writeVarint(value); return this
        }

        fun uint32(field: Int, value: Int): Writer = varint(field, value.toLong() and 0xFFFFFFFFL)

        fun fixed64(field: Int, value: Long): Writer {
            tag(field, WIRE_FIXED64)
            for (i in 0 until 8) out.write(((value ushr (8 * i)) and 0xFF).toInt())
            return this
        }

        fun bytes(field: Int, value: ByteArray): Writer {
            tag(field, WIRE_LEN); writeVarint(value.size.toLong()); out.write(value); return this
        }

        /** Embed a sub-message. */
        fun message(field: Int, sub: Writer): Writer = bytes(field, sub.toByteArray())

        fun toByteArray(): ByteArray = out.toByteArray()

        private fun tag(field: Int, wire: Int) = writeVarint(((field.toLong()) shl 3) or wire.toLong())

        private fun writeVarint(v: Long) {
            var value = v
            while (true) {
                val b = (value and 0x7F).toInt()
                value = value ushr 7
                if (value != 0L) out.write(b or 0x80) else { out.write(b); break }
            }
        }
    }

    /** A decoded field: its wire type and raw value (varint as Long, len as ByteArray). */
    class Field(val number: Int, val wireType: Int, val varint: Long, val bytes: ByteArray?)

    /** Decode a message into fields (repeated fields appear multiple times). */
    fun decode(data: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var pos = 0
        while (pos < data.size) {
            val (tag, p1) = readVarint(data, pos); pos = p1
            val field = (tag ushr 3).toInt()
            when (val wire = (tag and 0x7).toInt()) {
                WIRE_VARINT -> { val (v, p2) = readVarint(data, pos); pos = p2; out.add(Field(field, wire, v, null)) }
                WIRE_FIXED64 -> {
                    var v = 0L
                    for (i in 0 until 8) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    pos += 8; out.add(Field(field, wire, v, null))
                }
                WIRE_FIXED32 -> {
                    var v = 0L
                    for (i in 0 until 4) v = v or ((data[pos + i].toLong() and 0xFF) shl (8 * i))
                    pos += 4; out.add(Field(field, wire, v, null))
                }
                WIRE_LEN -> {
                    val (len, p2) = readVarint(data, pos); pos = p2
                    val end = pos + len.toInt()
                    if (end > data.size) return out
                    out.add(Field(field, wire, 0, data.copyOfRange(pos, end))); pos = end
                }
                else -> return out // unknown wire type — stop
            }
        }
        return out
    }

    fun firstBytes(fields: List<Field>, number: Int): ByteArray? =
        fields.firstOrNull { it.number == number && it.wireType == WIRE_LEN }?.bytes

    fun firstVarint(fields: List<Field>, number: Int): Long? =
        fields.firstOrNull { it.number == number }?.varint

    fun allBytes(fields: List<Field>, number: Int): List<ByteArray> =
        fields.filter { it.number == number && it.wireType == WIRE_LEN }.mapNotNull { it.bytes }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L; var shift = 0; var pos = start
        while (pos < data.size) {
            val b = data[pos].toInt() and 0xFF; pos++
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return result to pos
    }
}
