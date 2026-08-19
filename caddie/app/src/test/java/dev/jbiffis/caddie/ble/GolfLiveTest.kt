package dev.jbiffis.caddie.ble

import dev.jbiffis.caddie.fit.FitReader
import dev.jbiffis.caddie.fit.GolfFit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Live-golf service (Smart field 7), verified against a real Garmin Golf HCI capture.
 * The captured scorecard FIT blobs live in test resources.
 */
class GolfLiveTest {

    private fun resource(name: String): ByteArray =
        javaClass.getResourceAsStream("/golf_live/$name")!!.readBytes()

    /** The poll and ack builders must match the exact bytes Garmin Golf sent. */
    @Test
    fun buildersMatchCapturedBytes() {
        // phone->watch  7:{ 3:{ 1:16 } }
        assertEquals("3a041a020810", GolfLive.buildPoll(16).toHex())
        // phone->watch  7:{ 6:{ 1:16, 2:1 } }
        assertEquals("3a06320408101001", GolfLive.buildReceiveAck(16).toHex())
        // phone->watch  13:{ 6:{ 1:1, 2:{ 1:0, 2:1 } } }  (app-reg handshake completion)
        assertEquals("6a0a32080801120408001001", GolfLive.buildAppRegAck().toHex())
    }

    /** Handshake builders must match the exact bytes the real app sent. */
    @Test
    fun handshakeBuildersMatchCapture() {
        assertEquals("f201060a040a020813", GolfLive.buildS30Hello().toHex())
        assertEquals("82010c2a0a08001202080012020801", GolfLive.build16Ack().toHex())
        assertEquals("52046a020801", GolfLive.build10Ack().toHex())
        // Token reply: the captured service-27 credential message, replayed verbatim.
        val token = GolfLive.buildTokenReply()
        assertEquals(27, GolfLive.topField(token))
        assertEquals(162, token.size)
        assertTrue(String(token, Charsets.US_ASCII).contains("VS8HWafs7wZ"))
        assertEquals(30, GolfLive.topField(GolfLive.buildS30Hello()))
    }

    /**
     * Replies to watch-initiated requests must go out as PROTOBUF_RESPONSE echoing the
     * watch's request id — sent as a REQUEST the watch never sees its question answered.
     */
    @Test
    fun repliesAreResponsesEchoingRequestId() {
        val body = GolfLive.buildTokenReply()
        val resp = Gfdi.protobufResponse(0x1234, body)
        val req = Gfdi.protobufRequest(0x1234, body)

        // type field (bytes 2..3, little endian) distinguishes them
        fun typeOf(f: ByteArray) = (f[2].toInt() and 0xFF) or ((f[3].toInt() and 0xFF) shl 8)
        assertEquals(Gfdi.MSG_PROTOBUF_RESPONSE, typeOf(resp))
        assertEquals(Gfdi.MSG_PROTOBUF_REQUEST, typeOf(req))

        // both carry the same request id and payload, so only the type differs
        val parsed = Gfdi.parseProtobufRequest(resp.copyOfRange(4, resp.size - 2))!!
        assertEquals(0x1234, parsed.requestId)
        assertArrayEquals(body, parsed.data)
    }

    /** The watch's scorecard announcement 5:{7:{1:seq,2:size}} yields the seq to poll. */
    @Test
    fun parsesAnnouncedSeq() {
        val inner = Protobuf.Writer().varint(1, 16).varint(2, 938)
        val notify = Protobuf.Writer()
            .message(GolfLive.SMART_NOTIFY, Protobuf.Writer().message(7, inner))
            .toByteArray()
        assertTrue(GolfLive.isNotify(notify))
        assertEquals(16, GolfLive.parseAnnouncedSeq(notify))
        // A golf push (service 7) is not a notify.
        val golf = Protobuf.Writer()
            .message(GolfLive.SMART_GOLF, Protobuf.Writer().message(5, Protobuf.Writer().varint(1, 1)))
            .toByteArray()
        assertEquals(null, GolfLive.parseAnnouncedSeq(golf))
    }

    /** A service-7 push round-trips: wrap a real FIT, parse it back out unchanged. */
    @Test
    fun parsesScorecardPush() {
        val fit = resource("live_scorecard.fit")
        // Rebuild the watch's push: Smart{ 7:{ 5:{ 1:seq, 2:0, 3:FIT } } }
        val inner = Protobuf.Writer().varint(1, 42).varint(2, 0).bytes(3, fit)
        val smart = Protobuf.Writer()
            .message(GolfLive.SMART_GOLF, Protobuf.Writer().message(5, inner))
            .toByteArray()

        assertTrue(GolfLive.isGolf(smart))
        val push = GolfLive.parsePush(smart)!!
        assertEquals(42L, push.seq)
        assertArrayEquals(fit, push.fit)
    }

    /** The pushed FIT is a real golf scorecard our parser reads end-to-end. */
    @Test
    fun pushedFitParsesAsScorecard() {
        val fit = resource("live_scorecard.fit")
        val messages = FitReader.decode(fit)
        assertTrue("has golf score", GolfFit.hasGolfScore(messages))
        val score = GolfFit.parseScore(messages)
        // A round that was mid-play when captured: some holes have strokes, start time set.
        assertTrue("start time present", score.startedAtS > 0)
        assertTrue("at least one hole scored", score.holes.any { it.strokes > 0 })
    }

    /**
     * Two pushes of the SAME round carry different file_id timestamps but the same
     * start time — the key the live importer must de-dupe on.
     */
    @Test
    fun sameRoundKeepsStableStartAcrossPushes() {
        val early = GolfFit.parseScore(FitReader.decode(resource("live_early.fit")))
        val later = GolfFit.parseScore(FitReader.decode(resource("live_scorecard.fit")))
        assertEquals("stable round start", early.startedAtS, later.startedAtS)
        // The later push has strictly more scoring than the early one.
        assertTrue(later.holes.sumOf { it.strokes } >= early.holes.sumOf { it.strokes })
    }

    /** The replayed app-registration blobs must be transcribed exactly and declare golf. */
    @Test
    fun registrationBytesAreValid() {
        val regs = GolfLive.registrationMessages
        assertEquals(2, regs.size)
        assertEquals(174, regs[0].size)
        assertEquals(178, regs[1].size)
        for (r in regs) {
            assertTrue("is service 13", GolfLive.isAppReg(r))
            assertTrue("carries app UUID",
                String(r, Charsets.US_ASCII).contains("8cd46041-06fe-4d04-b4be-776042af7e75"))
        }
        // Only the first registration carries the app name string.
        assertTrue(String(regs[0], Charsets.US_ASCII).contains("Garmin Golf"))
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
