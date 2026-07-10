package dev.jbiffis.caddie.ble

import java.io.ByteArrayOutputStream

/**
 * COBS (Consistent Overhead Byte Stuffing). Garmin's BLE GFDI transport sends
 * COBS-encoded packets delimited by 0x00 bytes.
 */
object Cobs {

    fun encode(data: ByteArray): ByteArray {
        val out = ByteArray(data.size + data.size / 254 + 2)
        var codePos = 0 // where the current block's code byte will be written
        var outPos = 1
        var code = 1
        for (b in data) {
            if (b.toInt() == 0) {
                out[codePos] = code.toByte()
                codePos = outPos++
                code = 1
            } else {
                out[outPos++] = b
                code++
                if (code == 255) {
                    out[codePos] = code.toByte()
                    codePos = outPos++
                    code = 1
                }
            }
        }
        out[codePos] = code.toByte()
        return out.copyOf(outPos)
    }

    /** Decode one COBS packet (without the 0x00 delimiters). Returns null on framing error. */
    fun decode(data: ByteArray): ByteArray? {
        val out = ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            val code = data[i].toInt() and 0xFF
            if (code == 0 || i + code > data.size) return null
            for (j in 1 until code) out.write(data[i + j].toInt())
            i += code
            if (code < 255 && i < data.size) out.write(0)
        }
        return out.toByteArray()
    }
}
