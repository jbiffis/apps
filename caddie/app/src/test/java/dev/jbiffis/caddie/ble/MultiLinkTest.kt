package dev.jbiffis.caddie.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MultiLinkTest {

    @Test
    fun characteristicUuids() {
        assertEquals("6a4e2820-667b-11e3-949a-0800200c9a66", MultiLink.WRITE_CHAR.toString())
        assertEquals("6a4e2810-667b-11e3-949a-0800200c9a66", MultiLink.NOTIFY_CHAR.toString())
        assertEquals("6a4e2800-667b-11e3-949a-0800200c9a66", MultiLink.base(0x2800).toString())
    }

    @Test
    fun registerRequestMatchesDocumentedLayout() {
        // handle=0, type=0, clientId(8 LE), service=1 (GFDI), reliability=0
        val req = MultiLink.registerRequest(MultiLink.SERVICE_GFDI, reliability = 0, clientId = 1)
        val expected = byteArrayOf(
            0x00, 0x00,
            0x01, 0, 0, 0, 0, 0, 0, 0,   // clientId = 1 as u64 LE
            0x01, 0x00,                  // service = 1
            0x00,                        // reliability
        )
        assertArrayEquals(expected, req)
    }

    @Test
    fun closeAllRequest() {
        assertArrayEquals(byteArrayOf(0x00, 0x05), MultiLink.closeAllRequest())
    }

    @Test
    fun parsesRegisterResponse() {
        // handle=0, type=1, clientId(8), service=1, status=0, handle=3, reliability=0
        val buf = ByteBuffer.allocate(2 + 8 + 2 + 3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0); buf.put(1)
        buf.putLong(2)
        buf.putShort(1)
        buf.put(0)   // status ok
        buf.put(3)   // assigned handle
        buf.put(0)   // reliability
        val resp = MultiLink.parseControl(buf.array())!!
        assertEquals(1, resp.service)
        assertEquals(0, resp.status)
        assertEquals(3, resp.handle)
    }

    @Test
    fun nonControlPacketNotParsedAsControl() {
        // A GFDI packet on handle 3 must not be mistaken for a control frame
        assertNull(MultiLink.parseControl(byteArrayOf(3, 0, 0, 0)))
    }

    @Test
    fun fragmentPrefixesHandleAndFitsMtu() {
        val data = ByteArray(500) { (it and 0xFF).toByte() }
        val chunks = MultiLink.fragment(handle = 3, cobsFramed = data, mtuPayload = 100)
        // every chunk starts with the handle and is <= mtuPayload
        assertTrue(chunks.all { it[0].toInt() == 3 && it.size <= 100 })
        // reassembling (minus handle bytes) reproduces the input
        val rejoined = chunks.fold(ByteArray(0)) { acc, c -> acc + c.copyOfRange(1, c.size) }
        assertArrayEquals(data, rejoined)
    }

    @Test
    fun stripHandleSplitsFirstByte() {
        val (handle, payload) = MultiLink.stripHandle(byteArrayOf(3, 9, 8, 7))!!
        assertEquals(3, handle)
        assertArrayEquals(byteArrayOf(9, 8, 7), payload)
    }

    @Test
    fun reliableHeaderRoundTrips() {
        // handle=5, reqNum=42, seq=17, some payload
        val payload = byteArrayOf(1, 2, 3)
        val packet = MultiLink.buildReliable(handle = 5, reqNum = 42, seq = 17, payload = payload)
        assertTrue("bit7 must be set for MLR", MultiLink.isReliable(packet))
        val parsed = MultiLink.parseReliable(packet)!!
        assertEquals(5, parsed.handle)
        assertEquals(42, parsed.reqNum)
        assertEquals(17, parsed.seq)
        assertArrayEquals(payload, parsed.payload)
    }

    @Test
    fun reliableFieldsCoverFullRange() {
        // reqNum and seq are each 6 bits (0..63); handle 0..7
        for (h in 0..7) for (req in intArrayOf(0, 1, 42, 63)) for (seq in intArrayOf(0, 1, 63)) {
            val p = MultiLink.parseReliable(MultiLink.buildReliable(h, req, seq, byteArrayOf(0xAB.toByte())))!!
            assertEquals(h, p.handle); assertEquals(req, p.reqNum); assertEquals(seq, p.seq)
        }
    }

    @Test
    fun ackHasNoPayloadAndCarriesNextSeq() {
        val ack = MultiLink.reliableAck(handle = 2, nextExpectedSeq = 7)
        val p = MultiLink.parseReliable(ack)!!
        assertEquals(2, p.handle)
        assertEquals(7, p.reqNum)
        assertEquals(0, p.payload.size)
    }

    @Test
    fun plainMlNotMistakenForReliable() {
        // Handle 5 plain-ML packet starts with 0x05 (bit7 clear)
        assertFalse(MultiLink.isReliable(byteArrayOf(5, 0, 1, 2)))
    }

    @Test
    fun gfdiFrameSurvivesCobsAndFragmentReassembly() {
        // End-to-end: GFDI message -> COBS frame -> ML fragments -> reassemble -> decode
        val gfdi = Gfdi.systemEvent(Gfdi.EVENT_SYNC_READY)
        val cobs = Cobs.encode(gfdi)
        val framed = ByteArray(cobs.size + 2)
        System.arraycopy(cobs, 0, framed, 1, cobs.size) // 0x00 .. 0x00
        val chunks = MultiLink.fragment(handle = 5, cobsFramed = framed, mtuPayload = 20)

        // Receiver: strip handles, feed the COBS stream, split on 0x00
        val stream = chunks.fold(ByteArray(0)) { acc, c -> acc + MultiLink.stripHandle(c)!!.second }
        val buffer = ArrayList<Byte>()
        var decoded: ByteArray? = null
        for (b in stream) {
            if (b.toInt() == 0) {
                if (buffer.isNotEmpty()) { decoded = Cobs.decode(buffer.toByteArray()); buffer.clear() }
            } else buffer.add(b)
        }
        val msg = Gfdi.parse(decoded!!)!!
        assertEquals(Gfdi.MSG_SYSTEM_EVENT, msg.id)
        assertArrayEquals(byteArrayOf(Gfdi.EVENT_SYNC_READY.toByte(), 0), msg.payload)
    }
}
