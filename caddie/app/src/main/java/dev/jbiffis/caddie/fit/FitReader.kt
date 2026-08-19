package dev.jbiffis.caddie.fit

import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal FIT file decoder.
 *
 * Decodes every data message into a [FitMessage] keyed by global message number and
 * raw field number, without applying the Garmin profile. This keeps the decoder tiny
 * and lets us read the undocumented golf messages (190-194) that Garmin Golf uses.
 */
class FitMessage(
    val globalNum: Int,
    val fields: Map<Int, Any>,
) {
    fun long(num: Int): Long? = when (val v = fields[num]) {
        is Long -> v
        is Double -> v.toLong()
        else -> null
    }

    fun int(num: Int): Int? = long(num)?.toInt()
    fun double(num: Int): Double? = when (val v = fields[num]) {
        is Long -> v.toDouble()
        is Double -> v
        else -> null
    }

    fun string(num: Int): String? = fields[num] as? String
}

object FitReader {

    const val FIT_EPOCH_OFFSET_S = 631065600L // 1989-12-31T00:00:00Z

    /**
     * Decode all data messages in a FIT file (including chained files).
     *
     * With [lenient] = true, a truncated file (one still being written — the header's
     * declared data size exceeds what's present, or the last record is cut off) is
     * decoded up to the last complete message instead of throwing. This is what lets
     * an in-progress round be read mid-play.
     */
    fun decode(bytes: ByteArray, lenient: Boolean = false): List<FitMessage> {
        val out = ArrayList<FitMessage>()
        var pos = 0
        while (pos + 12 <= bytes.size) {
            pos = try {
                decodeOne(bytes, pos, out, lenient)
            } catch (e: Exception) {
                if (lenient) break else throw e
            }
            // Skip trailing garbage that isn't another chained FIT header.
            if (pos + 12 > bytes.size || !hasFitSignature(bytes, pos)) break
        }
        if (out.isEmpty() && !lenient) throw IllegalArgumentException("Not a FIT file")
        return out
    }

    private fun hasFitSignature(b: ByteArray, pos: Int): Boolean {
        val headerSize = b[pos].toInt() and 0xFF
        if (headerSize < 12 || pos + headerSize > b.size) return false
        return b[pos + 8].toInt() == '.'.code && b[pos + 9].toInt() == 'F'.code &&
            b[pos + 10].toInt() == 'I'.code && b[pos + 11].toInt() == 'T'.code
    }

    private class FieldDef(val num: Int, val size: Int, val baseType: Int)
    private class MesgDef(
        val globalNum: Int,
        val littleEndian: Boolean,
        val fields: List<FieldDef>,
        val devFieldBytes: Int,
    )

    private fun decodeOne(bytes: ByteArray, start: Int, out: MutableList<FitMessage>, lenient: Boolean): Int {
        if (!hasFitSignature(bytes, start)) throw IllegalArgumentException("Missing .FIT header")
        val headerSize = bytes[start].toInt() and 0xFF
        val dataSize = ByteBuffer.wrap(bytes, start + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        var pos = start + headerSize
        var end = pos + dataSize.toInt()
        val truncated = end > bytes.size
        if (truncated) {
            if (!lenient) throw EOFException("Truncated FIT file")
            // In-progress file: the declared data size runs past what's been written.
            // Decode every complete record that is present.
            end = bytes.size
        }

        val defs = HashMap<Int, MesgDef>()
        var lastTimestamp: Long? = null

        while (pos < end) {
            // A record can be cut off mid-way in a still-growing file. Snapshot the
            // position before reading so we can rewind to the last complete record.
            val recordStart = pos
            try {
                val header = bytes[pos].toInt() and 0xFF
                pos++
                if (header and 0x80 != 0) {
                    // Compressed timestamp data message
                    val localType = (header shr 5) and 0x03
                    val timeOffset = header and 0x1F
                    val def = defs[localType] ?: throw IllegalArgumentException("Undefined local type $localType")
                    val (msg, newPos) = readData(bytes, pos, def)
                    if (newPos > end) throw EOFException("Truncated record")
                    pos = newPos
                    lastTimestamp?.let { last ->
                        var ts = (last and 0x1F.inv().toLong()) or timeOffset.toLong()
                        if (ts < last) ts += 0x20
                        lastTimestamp = ts
                        out.add(FitMessage(msg.globalNum, msg.fields + (253 to ts)))
                    } ?: out.add(msg)
                } else if (header and 0x40 != 0) {
                    // Definition message
                    val localType = header and 0x0F
                    val hasDev = header and 0x20 != 0
                    pos++ // reserved
                    val littleEndian = bytes[pos].toInt() == 0
                    pos++
                    val globalNum = ByteBuffer.wrap(bytes, pos, 2)
                        .order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
                        .short.toInt() and 0xFFFF
                    pos += 2
                    val numFields = bytes[pos].toInt() and 0xFF
                    pos++
                    val fields = ArrayList<FieldDef>(numFields)
                    repeat(numFields) {
                        fields.add(
                            FieldDef(
                                bytes[pos].toInt() and 0xFF,
                                bytes[pos + 1].toInt() and 0xFF,
                                bytes[pos + 2].toInt() and 0xFF,
                            )
                        )
                        pos += 3
                    }
                    var devBytes = 0
                    if (hasDev) {
                        val numDev = bytes[pos].toInt() and 0xFF
                        pos++
                        repeat(numDev) {
                            devBytes += bytes[pos + 1].toInt() and 0xFF
                            pos += 3
                        }
                    }
                    if (pos > end) throw EOFException("Truncated definition")
                    defs[localType] = MesgDef(globalNum, littleEndian, fields, devBytes)
                } else {
                    // Normal data message
                    val localType = header and 0x0F
                    val def = defs[localType] ?: throw IllegalArgumentException("Undefined local type $localType")
                    val (msg, newPos) = readData(bytes, pos, def)
                    if (newPos > end) throw EOFException("Truncated record")
                    pos = newPos
                    msg.long(253)?.let { lastTimestamp = it }
                    out.add(msg)
                }
            } catch (e: Exception) {
                // A record ran past the available bytes (or referenced an as-yet-unseen
                // definition). In a growing file that's the write frontier — stop cleanly
                // at the last complete record. Otherwise the file is genuinely corrupt.
                if (lenient) {
                    pos = recordStart
                    return bytes.size
                }
                throw e
            }
        }
        return if (truncated || end + 2 > bytes.size) bytes.size else end + 2 // skip file CRC
    }

    private fun readData(bytes: ByteArray, start: Int, def: MesgDef): Pair<FitMessage, Int> {
        var pos = start
        val order = if (def.littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val fields = HashMap<Int, Any>()
        for (f in def.fields) {
            val value = readValue(bytes, pos, f.size, f.baseType, order)
            pos += f.size
            if (value != null) fields[f.num] = value
        }
        pos += def.devFieldBytes
        return FitMessage(def.globalNum, fields) to pos
    }

    private fun readValue(bytes: ByteArray, pos: Int, size: Int, baseType: Int, order: ByteOrder): Any? {
        val buf = ByteBuffer.wrap(bytes, pos, size).order(order)
        return when (baseType) {
            0x07 -> { // string (null terminated)
                val sb = StringBuilder()
                var i = pos
                while (i < pos + size && bytes[i].toInt() != 0) {
                    sb.append(bytes[i].toInt().toChar()); i++
                }
                sb.toString().ifEmpty { null }
            }
            else -> {
                val baseSize = baseTypeSize(baseType)
                if (baseSize == 0 || size % baseSize != 0) return null
                val count = size / baseSize
                if (count == 1) {
                    readScalar(buf, baseType)
                } else {
                    // Arrays: keep the first valid element (golf files don't use arrays we care about)
                    var first: Any? = null
                    repeat(count) {
                        val v = readScalar(buf, baseType)
                        if (first == null && v != null) first = v
                    }
                    first
                }
            }
        }
    }

    private fun baseTypeSize(baseType: Int): Int = when (baseType) {
        0x00, 0x01, 0x02, 0x0A, 0x0D -> 1
        0x83, 0x84, 0x8B -> 2
        0x85, 0x86, 0x88, 0x8C -> 4
        0x89, 0x8E, 0x8F, 0x90 -> 8
        else -> 0
    }

    /** Returns Long, Double or null (invalid sentinel). */
    private fun readScalar(buf: ByteBuffer, baseType: Int): Any? = when (baseType) {
        0x00, 0x02 -> (buf.get().toLong() and 0xFF).takeIf { it != 0xFFL } // enum / uint8
        0x01 -> buf.get().toLong().takeIf { it != 0x7FL } // sint8
        0x0A -> (buf.get().toLong() and 0xFF).takeIf { it != 0L } // uint8z
        0x0D -> buf.get().toLong() and 0xFF // byte
        0x83 -> buf.short.toLong().takeIf { it != 0x7FFFL } // sint16
        0x84 -> (buf.short.toLong() and 0xFFFF).takeIf { it != 0xFFFFL } // uint16
        0x8B -> (buf.short.toLong() and 0xFFFF).takeIf { it != 0L } // uint16z
        0x85 -> buf.int.toLong().takeIf { it != 0x7FFFFFFFL } // sint32
        0x86 -> (buf.int.toLong() and 0xFFFFFFFFL).takeIf { it != 0xFFFFFFFFL } // uint32
        0x8C -> (buf.int.toLong() and 0xFFFFFFFFL).takeIf { it != 0L } // uint32z
        0x88 -> buf.float.toDouble().takeIf { !it.isNaN() } // float32
        0x89 -> buf.double.takeIf { !it.isNaN() } // float64
        0x8E -> buf.long.takeIf { it != 0x7FFFFFFFFFFFFFFFL } // sint64
        0x8F -> buf.long.takeIf { it != -1L } // uint64
        0x90 -> buf.long.takeIf { it != 0L } // uint64z
        else -> null
    }
}
