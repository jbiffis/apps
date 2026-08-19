package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.GolfFit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The live-round merge must let the watch drive the round's shape while keeping the
 * two things a player owns: wind (per hole) and club assignments they made on shots
 * the watch left unlabelled.
 */
class LiveRoundMergeTest {

    private fun hole(n: Int, par: Int = 4, strokes: Int = 4) = GolfFit.Hole(
        hole = n, par = par, strokeIndex = n, lengthM = 350.0,
        pinLat = 45.0 + n * 1e-4, pinLon = -75.0, strokes = strokes, putts = 2,
        finishedAtS = 1_000L + n,
    )

    private fun shot(hole: Int, timeS: Long, club: Long) = GolfFit.Shot(
        timeS = timeS, hole = hole,
        startLat = 45.0, startLon = -75.0, endLat = 45.001, endLon = -75.0, clubId = club,
    )

    private fun score(holes: List<GolfFit.Hole>, shots: List<GolfFit.Shot>) = GolfFit.ScoreFile(
        serialNumber = 1, createdAtS = 100, courseName = "Test", teeName = "White",
        frontPar = 36, backPar = 36, totalPar = 72, slope = 113, rating = 70.0,
        distanceWalkedM = null, totalPutts = null, startedAtS = 100, playerName = "Me",
        frontScore = holes.sumOf { it.strokes }, backScore = 0,
        totalScore = holes.sumOf { it.strokes }, holes = holes, shots = shots,
    )

    @Test
    fun freshImportHasNoEditsToPreserve() {
        val s = score(listOf(hole(1)), listOf(shot(1, 1001, club = 7)))
        val m = LiveRound.merge(roundId = 5, score = s, oldHoles = emptyList(), oldShots = emptyList())
        assertEquals(1, m.holes.size)
        assertEquals(5L, m.holes[0].roundId)
        assertNull(m.holes[0].windSpeedKmh)
        assertEquals(7L, m.shots[0].clubId)
    }

    @Test
    fun windIsPreservedByHoleAcrossUpdate() {
        // Stored: hole 1 finished with wind the user set. Hole 2 not yet played.
        val old = listOf(
            HoleEntity(roundId = 5, hole = 1, par = 4, strokeIndex = 1, lengthM = 350.0,
                pinLat = 45.0, pinLon = -75.0, strokes = 4, putts = 2, finishedAtS = 1001,
                windSpeedKmh = 18.0, windDirDeg = 270),
        )
        // New file: hole 1 unchanged, hole 2 now added by the watch.
        val s = score(listOf(hole(1), hole(2)), listOf(shot(1, 1001, 7), shot(2, 2001, 9)))
        val m = LiveRound.merge(5, s, old, emptyList())

        val h1 = m.holes.first { it.hole == 1 }
        assertEquals("wind carried over", 18.0, h1.windSpeedKmh!!, 0.001)
        assertEquals(270, h1.windDirDeg)
        // The new hole has no wind yet, and both shots inherit their hole's wind.
        assertNull(m.holes.first { it.hole == 2 }.windSpeedKmh)
        assertEquals(18.0, m.shots.first { it.hole == 1 }.windSpeedKmh!!, 0.001)
        assertNull(m.shots.first { it.hole == 2 }.windSpeedKmh)
    }

    @Test
    fun userClubOverrideSurvivesWhenWatchStillReportsNoClub() {
        // Watch recorded shot at t=1001 with no club (putt/unknown); user tagged it 7-iron.
        val old = listOf(
            ShotEntity(roundId = 5, hole = 1, timeS = 1001, startLat = 45.0, startLon = -75.0,
                endLat = 45.001, endLon = -75.0, clubId = 7, distanceM = 111.0),
        )
        // New file still reports club 0 for that same shot.
        val s = score(listOf(hole(1)), listOf(shot(1, 1001, club = 0)))
        val m = LiveRound.merge(5, s, emptyList(), old)
        assertEquals("user's club kept", 7L, m.shots.single().clubId)
    }

    @Test
    fun watchClubWinsOverStaleOverride() {
        // User had tagged the shot 7; the watch now reports a real club (12) for it.
        val old = listOf(
            ShotEntity(roundId = 5, hole = 1, timeS = 1001, startLat = 45.0, startLon = -75.0,
                endLat = 45.001, endLon = -75.0, clubId = 7, distanceM = 111.0),
        )
        val s = score(listOf(hole(1)), listOf(shot(1, 1001, club = 12)))
        val m = LiveRound.merge(5, s, emptyList(), old)
        assertEquals("watch's recorded club wins", 12L, m.shots.single().clubId)
    }

    @Test
    fun growingRoundAddsHolesAndShots() {
        // Early poll: 1 hole, 1 shot.
        val early = score(listOf(hole(1)), listOf(shot(1, 1001, 7)))
        val m1 = LiveRound.merge(5, early, emptyList(), emptyList())
        assertEquals(1, m1.holes.size)
        assertEquals(1, m1.shots.size)

        // Later poll: 3 holes, more shots. Feed the previous merge back in as "stored".
        val later = score(
            listOf(hole(1), hole(2), hole(3)),
            listOf(shot(1, 1001, 7), shot(2, 2001, 9), shot(2, 2100, 0), shot(3, 3001, 12)),
        )
        val m2 = LiveRound.merge(5, later, m1.holes, m1.shots)
        assertEquals(3, m2.holes.size)
        assertEquals(4, m2.shots.size)
        assertEquals(listOf(1, 2, 3), m2.holes.map { it.hole })
    }
}
