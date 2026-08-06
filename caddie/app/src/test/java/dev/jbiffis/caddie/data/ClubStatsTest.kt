package dev.jbiffis.caddie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubStatsTest {

    private val YD_M = 0.9144 // yards -> metres

    @Test
    fun `solid is the average of the better half, and beats the plain average`() {
        val club = clubOf(yards = listOf(100.0, 110.0, 120.0, 130.0, 200.0))
        assertEquals(132.0, club.averageYd, 0.5)
        assertEquals(120.0, club.medianYd, 0.5)
        // The better half is 120, 130 and 200.
        assertEquals(150.0, club.solidYd, 0.5)
        assertEquals(200.0, club.longestYd, 0.5)
        assertEquals(100.0, club.shortestYd, 0.5)
        assertTrue("solid must not be dragged down by mishits", club.solidYd > club.averageYd)
    }

    @Test
    fun `a single tracked shot still produces every stat`() {
        val club = clubOf(yards = listOf(180.0))
        assertEquals(180.0, club.averageYd, 0.5)
        assertEquals(180.0, club.medianYd, 0.5)
        assertEquals(180.0, club.solidYd, 0.5)
        assertEquals(1, club.count)
    }

    @Test
    fun `dispersion counts use an eight metre tolerance either side`() {
        val club = ClubStats(
            clubId = 1, name = "Driver",
            shots = listOf(
                sample(200.0, lateralM = -20.0), // left
                sample(200.0, lateralM = -3.0),  // straight
                sample(200.0, lateralM = 0.0),   // straight
                sample(200.0, lateralM = 12.0),  // right
                sample(200.0, lateralM = 30.0),  // right
            ),
        )
        assertEquals(1, club.lefts)
        assertEquals(2, club.straight)
        assertEquals(2, club.rights)
        assertEquals(40, club.pct(club.rights))
        assertEquals(100, club.pct(club.lefts) + club.pct(club.straight) + club.pct(club.rights))
    }

    @Test
    fun `the histogram spans the club's range and every shot lands in a bucket`() {
        val club = clubOf(yards = listOf(70.0, 120.0, 180.0, 200.0, 286.0))
        val histogram = club.histogram(bins = 18)
        assertEquals(18, histogram.counts.size)
        assertEquals(5, histogram.counts.sum())
        assertEquals(70.0, histogram.lowYd, 0.5)
        assertEquals(286.0, histogram.highYd, 0.5)
        // The longest shot belongs to the last bucket, the shortest to the first.
        assertEquals(0, histogram.binOf(club.shortestYd))
        assertEquals(17, histogram.binOf(club.longestYd))
    }

    @Test
    fun `computeAll orders the bag longest first and skips putts and chips`() {
        val shots = listOf(
            shot(id = 1, clubId = 1, metres = 180.0),  // driver
            shot(id = 2, clubId = 1, metres = 170.0),
            shot(id = 3, clubId = 2, metres = 100.0),  // an iron
            shot(id = 4, clubId = 0, metres = 5.0),    // a putt — no club
            shot(id = 5, clubId = 2, metres = 3.0),    // a chip, under the 15 m floor
        )
        val stats = ClubStats.computeAll(
            shots = shots,
            holes = emptyList(),
            clubNames = mapOf(1L to "Driver", 2L to "7 Iron"),
            featuresByRound = emptyMap(),
        )
        assertEquals(listOf("Driver", "7 Iron"), stats.map { it.name })
        assertEquals(2, stats[0].count)
        assertEquals(1, stats[1].count) // the 3 m chip is not a club distance
    }

    @Test
    fun `a club the watch never named keeps its id so the gap stays visible`() {
        val stats = ClubStats.computeAll(
            shots = listOf(shot(id = 1, clubId = 398, metres = 105.0)),
            holes = emptyList(),
            clubNames = emptyMap(),
            featuresByRound = emptyMap(),
        )
        assertEquals("Club 398", stats.single().name)
    }

    // --- fixtures --------------------------------------------------------

    private fun clubOf(yards: List<Double>) =
        ClubStats(clubId = 1, name = "Driver", shots = yards.map { sample(it, null) })

    private fun sample(yards: Double, lateralM: Double?) =
        ClubShot(shot(id = 1, clubId = 1, metres = yards * YD_M), lateralM, null)

    private fun shot(id: Long, clubId: Long, metres: Double) = ShotEntity(
        id = id, roundId = 1, hole = 1, timeS = id,
        startLat = 0.0, startLon = 0.0, endLat = 0.0, endLon = 0.0,
        clubId = clubId, distanceM = metres,
    )
}
