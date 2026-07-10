package dev.jbiffis.caddie.ble

/**
 * CRC-16 as used by the FIT file format and Garmin's GFDI messages
 * (reflected polynomial 0xA001, init 0x0000).
 */
object Crc16 {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0
        for (i in offset until offset + length) {
            val b = data[i].toInt() and 0xFF
            var tmp = TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor TABLE[b and 0xF]
            tmp = TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor TABLE[(b shr 4) and 0xF]
        }
        return crc and 0xFFFF
    }
}
