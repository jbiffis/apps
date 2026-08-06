package dev.jbiffis.caddie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoleHistoryTest {

    private val hole = 7

    @Test
    fun `visits come from every round at the same course, oldest first`() {
        val history = build(
            rounds = listOf(
                round(1, "Richmond Centennial", startedAtS = 300),
                round(2, "Richmond Centennial", startedAtS = 100),
                round(3, "Somewhere Else", startedAtS = 200),
            ),
            holes = listOf(
                holeRow(1, strokes = 6), holeRow(2, strokes = 8), holeRow(3, strokes = 4),
            ),
            shots = emptyList(),
            currentRoundId = 1,
        )
        assertEquals(listOf(2L, 1L), history.visits.map { it.roundId })
        assertEquals(2, history.roundCount)
        assertEquals(6, history.best)
        assertEquals(7.0, history.average!!, 1e-9)
        assertEquals(6, history.thisRound)
    }

    @Test
    fun `course matching ignores case`() {
        val history = build(
            rounds = listOf(round(1, "Richmond Centennial"), round(2, "RICHMOND CENTENNIAL", startedAtS = 50)),
            holes = listOf(holeRow(1, strokes = 5), holeRow(2, strokes = 7)),
            shots = emptyList(),
            currentRoundId = 1,
        )
        assertEquals(2, history.roundCount)
    }

    @Test
    fun `improvement is measured from the first visit to the last`() {
        val history = build(
            rounds = listOf(round(1, "A", startedAtS = 100), round(2, "A", startedAtS = 200)),
            holes = listOf(holeRow(1, strokes = 8), holeRow(2, strokes = 6)),
            shots = emptyList(),
            currentRoundId = 2,
        )
        assertEquals(-2, history.strokesSinceFirst)
    }

    @Test
    fun `a single visit has no trend`() {
        val history = build(
            rounds = listOf(round(1, "A")),
            holes = listOf(holeRow(1, strokes = 5)),
            shots = emptyList(),
            currentRoundId = 1,
        )
        assertNull(history.strokesSinceFirst)
    }

    @Test
    fun `holes that were not played do not drag the average down`() {
        val history = build(
            rounds = listOf(round(1, "A", startedAtS = 100), round(2, "A", startedAtS = 200)),
            holes = listOf(holeRow(1, strokes = 6), holeRow(2, strokes = 0)),
            shots = emptyList(),
            currentRoundId = 1,
        )
        assertEquals(6.0, history.average!!, 1e-9)
        assertEquals(6, history.best)
    }

    @Test
    fun `the approach is the last shot played with a club`() {
        // Pin at the origin, played from the south (so north is down the target line
        // and east of the pin is a miss to the player's right). The approach finishes
        // ~10 m east of the pin, then two putts.
        val shots = listOf(
            shot(roundId = 1, timeS = 1, clubId = 1, startLat = -0.0015, endLat = -0.0004),
            shot(roundId = 1, timeS = 2, clubId = 2, startLat = -0.0004, endLat = 0.0, endLon = 0.00009),
            shot(roundId = 1, timeS = 3, clubId = 0, startLat = 0.0, startLon = 0.00009, endLat = 0.0),
        )
        val history = build(
            rounds = listOf(round(1, "A")),
            holes = listOf(holeRow(1, strokes = 5)),
            shots = shots,
            currentRoundId = 1,
        )
        assertEquals(1, history.approaches.size)
        val approach = history.approaches.single()
        assertEquals(2L, approach.clubId)
        assertTrue("expected a miss to the right, got ${approach.rightM}", approach.rightM > 5.0)
        // Level with the pin, not past it.
        assertEquals(0.0, approach.alongM, 1.5)
        assertEquals(10.0, approach.distanceToPinM, 1.5)
        assertEquals(approach, history.bestApproach)
    }

    @Test
    fun `right and left are measured from the player's point of view, not the compass`() {
        // The same ball position, approached from opposite ends of the hole, has to
        // come out as opposite misses — this is what makes the dispersion plot mean
        // anything when a hole runs in an unusual direction.
        fun rightMOf(fromSouth: Boolean): Double {
            val startLat = if (fromSouth) -0.0004 else 0.0004
            return build(
                rounds = listOf(round(1, "A")),
                holes = listOf(holeRow(1, strokes = 4)),
                shots = listOf(
                    shot(roundId = 1, timeS = 1, clubId = 2, startLat = startLat, endLat = 0.0, endLon = 0.00009),
                ),
                currentRoundId = 1,
            ).approaches.single().rightM
        }
        assertTrue(rightMOf(fromSouth = true) > 5.0)
        assertTrue(rightMOf(fromSouth = false) < -5.0)
    }

    @Test
    fun `a hole with no pin contributes no approach`() {
        val history = build(
            rounds = listOf(round(1, "A")),
            holes = listOf(holeRow(1, strokes = 5, pin = false)),
            shots = listOf(shot(roundId = 1, timeS = 1, clubId = 1)),
            currentRoundId = 1,
        )
        assertTrue(history.approaches.isEmpty())
        assertNull(history.bestApproach)
    }

    @Test
    fun `strokes gained is measured against the player's own average hole`() {
        // Two holes in one round: hole 7 (measured) and hole 8 (part of the baseline).
        val rounds = listOf(round(1, "A"))
        val holes = listOf(
            holeRow(1, strokes = 5),
            HoleEntity(
                id = 99, roundId = 1, hole = 8, par = 4, strokeIndex = null, lengthM = 300.0,
                pinLat = 0.0, pinLon = 0.0, strokes = 4, putts = 2, finishedAtS = null,
            ),
        )
        val shots = listOf(
            shot(roundId = 1, timeS = 1, clubId = 1, startLat = 0.0018, endLat = 0.0004),
            shot(roundId = 1, timeS = 2, clubId = 0, startLat = 0.0004, endLat = 0.0),
            shot(roundId = 1, timeS = 3, clubId = 1, hole = 8, startLat = 0.0027, endLat = 0.0006),
            shot(roundId = 1, timeS = 4, clubId = 0, hole = 8, startLat = 0.0006, endLat = 0.0),
        )
        val history = build(rounds, holes, shots, currentRoundId = 1)

        assertTrue(history.strokesGained.isNotEmpty())
        assertTrue(history.strokesGainedBaseline.isNotEmpty())
        // Relative = this hole minus the baseline, category by category.
        StrokesGained.Category.entries.forEach { c ->
            assertEquals(
                (history.strokesGained[c] ?: 0.0) - (history.strokesGainedBaseline[c] ?: 0.0),
                history.strokesGainedRelative[c]!!,
                1e-9,
            )
        }
        assertTrue("no mapped polygons, so lies are inferred", history.strokesGainedApproximate)
    }

    @Test
    fun `an unplayed course produces an empty but usable history`() {
        val history = build(emptyList(), emptyList(), emptyList(), currentRoundId = 1)
        assertNotNull(history)
        assertEquals(0, history.roundCount)
        assertNull(history.best)
        assertNull(history.average)
        assertNull(history.thisRound)
        assertEquals(0, history.greensHit)
    }

    // --- fixtures --------------------------------------------------------

    private fun build(
        rounds: List<RoundEntity>,
        holes: List<HoleEntity>,
        shots: List<ShotEntity>,
        currentRoundId: Long,
    ) = HoleHistory.build(currentRoundId, hole, rounds, holes, shots, emptyMap())

    private fun round(id: Long, course: String, startedAtS: Long = 100) = RoundEntity(
        id = id, scoreFileTimeS = startedAtS, deviceSerial = 0, startedAtS = startedAtS,
        courseName = course, teeName = null, playerName = null,
        frontPar = 36, backPar = 36, totalPar = 72,
        frontScore = 45, backScore = 0, totalScore = 45,
        totalPutts = null, slope = null, rating = null, distanceWalkedM = null,
    )

    private fun holeRow(roundId: Long, strokes: Int, pin: Boolean = true) = HoleEntity(
        id = roundId, roundId = roundId, hole = hole, par = 5, strokeIndex = null, lengthM = 350.0,
        pinLat = if (pin) 0.0 else null, pinLon = if (pin) 0.0 else null,
        strokes = strokes, putts = 2, finishedAtS = null,
    )

    private fun shot(
        roundId: Long,
        timeS: Long,
        clubId: Long,
        hole: Int = this.hole,
        startLat: Double = 0.001,
        startLon: Double = 0.0,
        endLat: Double = 0.0005,
        endLon: Double = 0.0,
    ) = ShotEntity(
        id = timeS, roundId = roundId, hole = hole, timeS = timeS,
        startLat = startLat, startLon = startLon, endLat = endLat, endLon = endLon,
        clubId = clubId,
        distanceM = dev.jbiffis.caddie.fit.GolfFit.haversineM(startLat, startLon, endLat, endLon),
    )
}
