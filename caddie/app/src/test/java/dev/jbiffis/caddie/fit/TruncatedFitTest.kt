package dev.jbiffis.caddie.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Truncation tolerance for in-progress (still-being-written) FIT files.
 *
 * A watch that is mid-round exposes a golf file whose declared data size runs past
 * what has actually been flushed, and whose final record may be cut off. Reading it
 * with [FitReader.decode] `lenient = true` must yield every complete message written
 * so far — that is what powers the live/partial-round import — while a strict decode
 * of the same bytes still fails loudly.
 */
class TruncatedFitTest {

    private fun sample(name: String): ByteArray {
        val f = File("src/main/assets/samples/$name")
        assertTrue("missing sample ${f.absolutePath}", f.exists())
        return f.readBytes()
    }

    /** Lenient decoding of a complete file must be identical to strict decoding. */
    @Test
    fun lenientMatchesStrictOnCompleteFile() {
        for (name in listOf("SCORE_20260708_205206.fit", "ACTIVITY_20260708_154325.fit")) {
            val bytes = sample(name)
            val strict = FitReader.decode(bytes, lenient = false)
            val lenient = FitReader.decode(bytes, lenient = true)
            assertEquals("$name message count", strict.size, lenient.size)
            assertEquals(
                "$name inventory",
                GolfFit.messageInventory(strict),
                GolfFit.messageInventory(lenient),
            )
        }
    }

    /** A truncated file is a hard error under strict decoding. */
    @Test
    fun strictDecodeRejectsTruncation() {
        val full = sample("SCORE_20260708_205206.fit")
        val cut = full.copyOf(full.size - 200)
        var threw = false
        try {
            FitReader.decode(cut, lenient = false)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue("strict decode should reject a truncated file", threw)
    }

    /**
     * As the file grows byte by byte, the set of parsed messages only ever grows and
     * always stays a prefix of the finished file — never invents or drops a message.
     */
    @Test
    fun partialDecodeIsAMonotonicPrefix() {
        val full = sample("SCORE_20260708_205206.fit")
        val complete = FitReader.decode(full, lenient = false)

        var prevCount = 0
        // Sample a spread of truncation points across the whole file.
        for (len in (full.size / 12) until full.size step (full.size / 12)) {
            val partial = FitReader.decode(full.copyOf(len), lenient = true)
            // Never more messages than the finished file, and monotonic in length.
            assertTrue("len=$len produced ${partial.size} > ${complete.size}", partial.size <= complete.size)
            assertTrue("len=$len went backwards (${partial.size} < $prevCount)", partial.size >= prevCount)
            prevCount = partial.size
            // Every parsed message must equal the finished file's message at that index.
            for (i in partial.indices) {
                assertEquals("len=$len msg $i globalNum", complete[i].globalNum, partial[i].globalNum)
            }
        }
    }

    /**
     * The holes recovered from a mid-round score file are exactly the holes finished
     * before the cut — a strict prefix of the full round, with matching scores.
     */
    @Test
    fun partialScoreRecoversCompletedHoles() {
        val full = sample("SCORE_20260708_205206.fit")
        val fullScore = GolfFit.parseScore(FitReader.decode(full))
        val fullPlayed = fullScore.holes.filter { it.strokes > 0 }.associateBy { it.hole }

        var sawPartial = false
        for (len in (full.size / 4)..full.size step (full.size / 8)) {
            val msgs = FitReader.decode(full.copyOf(len), lenient = true)
            if (!GolfFit.hasGolfScore(msgs)) continue // round summary not yet written
            val score = GolfFit.parseScore(msgs)
            val played = score.holes.filter { it.strokes > 0 }
            if (played.size < fullPlayed.size) sawPartial = true

            // Every hole seen so far matches the finished round's hole exactly.
            for (h in played) {
                val ref = fullPlayed[h.hole]
                    ?: error("partial round invented hole ${h.hole} (len=$len)")
                assertEquals("hole ${h.hole} strokes (len=$len)", ref.strokes, h.strokes)
                // Par arrives with the hole-info record, which may lag the score record;
                // once present it must match the finished round.
                if (h.par > 0) assertEquals("hole ${h.hole} par (len=$len)", ref.par, h.par)
            }
            // Total is the sum of holes actually recovered — a running score.
            assertEquals("len=$len running total", played.sumOf { it.strokes }, score.totalScore)
        }
        assertTrue("expected at least one genuinely partial parse", sawPartial)
        // Sanity: the full parse is not itself partial.
        assertFalse(fullPlayed.isEmpty())
    }

    /**
     * A growing activity file yields a valid, shorter GPS track that is a prefix of the
     * finished track — the basis for showing live on-course position.
     */
    @Test
    fun partialActivityRecoversTrackPrefix() {
        val full = sample("ACTIVITY_20260708_154325.fit")
        val fullTrack = GolfFit.parseActivity(FitReader.decode(full)).track

        val partialMsgs = FitReader.decode(full.copyOf(full.size / 2), lenient = true)
        val partial = GolfFit.parseActivity(partialMsgs)
        assertTrue("partial track should be non-empty", partial.track.isNotEmpty())
        assertTrue(
            "partial track ${partial.track.size} should be shorter than full ${fullTrack.size}",
            partial.track.size < fullTrack.size,
        )
        // The kept points line up with the head of the finished track.
        for (i in partial.track.indices) {
            assertEquals("track $i time", fullTrack[i].timeS, partial.track[i].timeS)
            assertEquals("track $i lat", fullTrack[i].lat, partial.track[i].lat, 1e-9)
        }
        assertTrue(partial.endTimeS >= partial.startTimeS)
    }
}
