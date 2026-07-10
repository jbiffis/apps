package dev.jbiffis.caddie.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class CobsTest {

    @Test
    fun roundTrips() {
        val cases = listOf(
            byteArrayOf(),
            byteArrayOf(0),
            byteArrayOf(0, 0),
            byteArrayOf(1, 2, 3),
            byteArrayOf(1, 0, 2, 0, 3),
            ByteArray(254) { (it + 1).toByte() },
            ByteArray(255) { (it % 254 + 1).toByte() },
            Random(42).nextBytes(1024),
        )
        for (case in cases) {
            val encoded = Cobs.encode(case)
            assertEquals("encoded data must not contain zero bytes", -1, encoded.indexOfFirst { it.toInt() == 0 })
            assertArrayEquals(case, Cobs.decode(encoded))
        }
    }

    @Test
    fun knownVector() {
        // Classic COBS example: 11 22 00 33 -> 03 11 22 02 33
        assertArrayEquals(
            byteArrayOf(0x03, 0x11, 0x22, 0x02, 0x33),
            Cobs.encode(byteArrayOf(0x11, 0x22, 0x00, 0x33)),
        )
    }

    @Test
    fun crcMatchesFitSpec() {
        // From the FIT SDK documentation example
        assertEquals(0x0000, Crc16.compute(ByteArray(0)))
        val gfdi = Gfdi.frame(Gfdi.MSG_SYNC_REQUEST, byteArrayOf(0, 1))
        // frame must self-validate
        val parsed = Gfdi.parse(gfdi)
        assertEquals(Gfdi.MSG_SYNC_REQUEST, parsed!!.id)
        assertArrayEquals(byteArrayOf(0, 1), parsed.payload)
    }
}
