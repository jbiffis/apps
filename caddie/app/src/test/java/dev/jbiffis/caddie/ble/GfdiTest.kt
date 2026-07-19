package dev.jbiffis.caddie.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GfdiTest {

    @Test
    fun frameRoundTripsThroughCobsTransport() {
        val original = Gfdi.downloadRequest(index = 0, offset = 0)
        val onWire = Cobs.encode(original)
        val parsed = Gfdi.parse(Cobs.decode(onWire)!!)!!
        assertEquals(Gfdi.MSG_DOWNLOAD_REQUEST, parsed.id)
        assertEquals(13, parsed.payload.size) // index2 + offset4 + type1 + crc2 + max4
    }

    @Test
    fun rejectsCorruptedPackets() {
        val packet = Gfdi.frame(Gfdi.MSG_SYNC_REQUEST, byteArrayOf(0, 1))
        packet[packet.size - 1] = (packet[packet.size - 1] + 1).toByte() // break CRC
        assertNull(Gfdi.parse(packet))
        assertNull(Gfdi.parse(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun parsesDataTransferModernLayout() {
        // flags u8, crc u16, offset u32, data
        val buf = ByteBuffer.allocate(7 + 3).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(0)
        buf.putShort(0x1234)
        buf.putInt(4096)
        buf.put(byteArrayOf(9, 8, 7))
        val d = Gfdi.parseDataTransfer(buf.array(), ackType = 0x8304)!!
        assertEquals(4096L, d.offset)
        assertEquals(0x1234, d.crc)
        assertArrayEquals(byteArrayOf(9, 8, 7), d.data)
        assertEquals(0x8304, d.ackType)
    }

    @Test
    fun decodesSequencedDeviceMessages() {
        // Reproduces the real vivoactive 5 framing: device-initiated messages set
        // bit15 of the type field; low byte = type-5000, high byte = 0x80|sequence.
        // Verified against captured frames: 0x8304->5004 seq3, 0x8518->5024 seq5.
        fun sequencedFrame(rawType: Int, payload: ByteArray): ByteArray {
            val total = 2 + 2 + payload.size + 2
            val b = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
            b.putShort(total.toShort()); b.putShort(rawType.toShort()); b.put(payload)
            b.putShort(dev.jbiffis.caddie.ble.Crc16.compute(b.array(), 0, total - 2).toShort())
            return b.array()
        }
        val fileData = Gfdi.parse(sequencedFrame(0x8304, ByteArray(9)))!!
        assertEquals(Gfdi.MSG_FILE_TRANSFER_DATA, fileData.id)  // 5004
        assertEquals(3, fileData.seq)
        assertEquals(0x8304, fileData.rawType)
        assertTrue(fileData.sequenced)

        val devInfo = Gfdi.parse(sequencedFrame(0x8518, ByteArray(4)))!!
        assertEquals(Gfdi.MSG_DEVICE_INFORMATION, devInfo.id)   // 5024
        assertEquals(5, devInfo.seq)

        // A normal (host-directed) response is not treated as sequenced
        val resp = Gfdi.parse(Gfdi.response(Gfdi.MSG_CONFIGURATION, Gfdi.STATUS_ACK))!!
        assertEquals(Gfdi.MSG_RESPONSE, resp.id)
        assertFalse(resp.sequenced)
    }

    @Test
    fun parsesDirectory() {
        // header + two entries: a golf score and a non-FIT entry
        val buf = ByteBuffer.allocate(16 + 32).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(16)
        buf.putShort(7); buf.put(128.toByte()); buf.put(38.toByte()); buf.putShort(120)
        buf.put(0); buf.put(0); buf.putInt(3268); buf.putInt(1152492726.toInt())
        buf.putShort(8); buf.put(1.toByte()); buf.put(0.toByte()); buf.putShort(0)
        buf.put(0); buf.put(0); buf.putInt(100); buf.putInt(0)
        val entries = Gfdi.parseDirectory(buf.array())
        assertEquals(2, entries.size)
        val score = entries.first { it.index == 7 }
        assertEquals(128, score.dataType)
        assertEquals(38, score.subType)
        assertEquals(120, score.number)
        assertEquals(3268L, score.size)
        assertEquals(1152492726L, score.fitTimestamp)
    }

    @Test
    fun deviceInformationRoundTrip() {
        // Our response payload after requestId+status matches the request layout,
        // so the parser can decode what we emit.
        val framed = Gfdi.deviceInformationResponse(unitNumber = 2_000_000_001L)
        val msg = Gfdi.parse(framed)!!
        assertEquals(Gfdi.MSG_RESPONSE, msg.id)
        val response = Gfdi.parseResponse(msg.payload)!!
        assertEquals(Gfdi.MSG_DEVICE_INFORMATION, response.requestId)
        assertEquals(Gfdi.STATUS_ACK, response.status)
        val info = Gfdi.parseDeviceInformation(response.extra)
        assertNotNull(info)
        assertEquals(200, info!!.protocolVersion)
        assertEquals(2_000_000_001L, info.unitNumber)
        assertEquals("Caddie", info.name)
    }

    @Test
    fun systemEventFrames() {
        val msg = Gfdi.parse(Gfdi.systemEvent(Gfdi.EVENT_SYNC_READY))!!
        assertEquals(Gfdi.MSG_SYSTEM_EVENT, msg.id)
        // [eventType, value]
        assertArrayEquals(byteArrayOf(8, 0), msg.payload)
    }

    @Test
    fun currentTimeResponseLayout() {
        val msg = Gfdi.parse(Gfdi.currentTimeResponse(nowMs = 0L, tzOffsetS = 0))!!
        assertEquals(Gfdi.MSG_RESPONSE, msg.id)
        val r = Gfdi.parseResponse(msg.payload)!!
        assertEquals(Gfdi.MSG_CURRENT_TIME_REQUEST, r.requestId)
        assertEquals(Gfdi.STATUS_ACK, r.status)
        // referenceId(4) + garminTs(4) + tz(4) + dstEnd(4) + dstStart(4)
        assertEquals(20, r.extra.size)
    }
}
