package dev.jbiffis.caddie.fit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Parses the real vivoactive 5 files shipped in assets/samples
 * (round at The Marshes Golf Club, 2026-07-08).
 */
class GolfFitTest {

    private fun sample(name: String): ByteArray {
        // Gradle unit tests run with the module directory as the working dir
        val f = File("src/main/assets/samples/$name")
        assertTrue("missing sample ${f.absolutePath}", f.exists())
        return f.readBytes()
    }

    @Test
    fun parsesScoreFile() {
        val messages = FitReader.decode(sample("SCORE_20260708_205206.fit"))
        assertEquals(GolfFit.FILE_TYPE_GOLF_SCORE, GolfFit.fileType(messages))

        val score = GolfFit.parseScore(messages)
        assertEquals("The Marshes Golf Club at Brookstreet", score.courseName)
        assertEquals("White", score.teeName)
        assertEquals(36, score.frontPar)
        assertEquals(36, score.backPar)
        assertEquals(72, score.totalPar)
        assertEquals(103, score.totalScore)
        assertEquals(52, score.frontScore)
        assertEquals(51, score.backScore)
        assertEquals(42, score.totalPutts)
        assertEquals(118, score.slope)
        assertEquals(68.0, score.rating!!, 0.01)

        assertEquals(18, score.holes.size)
        assertEquals(36, score.holes.filter { it.hole <= 9 }.sumOf { it.par })
        assertEquals(103, score.holes.sumOf { it.strokes })
        assertEquals(42, score.holes.sumOf { it.putts ?: 0 })

        val hole1 = score.holes.first { it.hole == 1 }
        assertEquals(4, hole1.par)
        assertEquals(4, hole1.strokes)
        assertEquals(2, hole1.putts)
        assertEquals(13, hole1.strokeIndex)
        // Pin should be on the course near Ottawa
        assertEquals(45.35, hole1.pinLat!!, 0.05)
        assertEquals(-75.92, hole1.pinLon!!, 0.05)

        assertEquals(62, score.shots.size)
        // 9 real clubs + putts (clubId 0)
        assertEquals(10, score.shots.map { it.clubId }.distinct().size)
        // Longest tracked shot in this round was ~248 m
        val longest = score.shots.maxOf { it.distanceM }
        assertTrue("longest=$longest", longest in 230.0..260.0)
    }

    @Test
    fun parsesActivityFile() {
        val messages = FitReader.decode(sample("ACTIVITY_20260708_154325.fit"))
        assertEquals(GolfFit.FILE_TYPE_ACTIVITY, GolfFit.fileType(messages))

        val activity = GolfFit.parseActivity(messages)
        assertNotNull(activity)
        assertEquals(22546.24, activity.totalDistanceM!!, 1.0)
        assertEquals(1853, activity.totalCalories)
        assertEquals(100, activity.avgHeartRate)
        assertEquals(146, activity.maxHeartRate)
        assertTrue("track ${activity.track.size}", activity.track.size > 2000)
        assertTrue(activity.endTimeS > activity.startTimeS)
        // 5h08m round
        assertEquals(18520L, activity.endTimeS - activity.startTimeS)
    }

    @Test
    fun scoreAndActivityOverlapInTime() {
        val score = GolfFit.parseScore(FitReader.decode(sample("SCORE_20260708_205206.fit")))
        val activity = GolfFit.parseActivity(FitReader.decode(sample("ACTIVITY_20260708_154325.fit")))
        // The round start in the SCORE file can differ from the session start by a
        // second or two, so match with the same slack the importer uses (±1 h).
        assertTrue(score.startedAtS in (activity.startTimeS - 3600)..(activity.endTimeS + 3600))
    }
}
