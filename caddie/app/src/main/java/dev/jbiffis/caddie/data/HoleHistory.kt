package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.GolfFit

/**
 * Everything the shot map's history sheet knows about one hole, accumulated across
 * every round imported for the same course.
 *
 * This is the payoff of playing the same course often: a single round tells you
 * what you scored, but seven rounds tell you that the hole is trending better,
 * that you miss it right more than half the time, and which club left you closest.
 */
data class HoleHistory(
    /** Every visit to this hole, oldest first. */
    val visits: List<Visit>,
    val currentRoundId: Long,
    /** Approach shots into this green, across all visits. */
    val approaches: List<Approach>,
    /** Strokes gained on this hole, averaged over the visits that could be measured. */
    val strokesGained: Map<StrokesGained.Category, Double>,
    /** The player's own per-hole average, over every hole of every round. */
    val strokesGainedBaseline: Map<StrokesGained.Category, Double>,
    /** True when at least one measured hole had to infer lies from an unmapped course. */
    val strokesGainedApproximate: Boolean,
) {
    data class Visit(val roundId: Long, val startedAtS: Long, val strokes: Int, val par: Int)

    data class Approach(
        val roundId: Long,
        val timeS: Long,
        val clubId: Long,
        /** Where the ball finished, relative to the pin: metres right and beyond it. */
        val rightM: Double,
        val alongM: Double,
        val distanceToPinM: Double,
        val result: Lie.Miss,
    )

    val best: Int? get() = visits.mapNotNull { it.strokes.takeIf { s -> s > 0 } }.minOrNull()
    val average: Double? get() = visits.map { it.strokes }.filter { it > 0 }
        .takeIf { it.isNotEmpty() }?.average()
    val thisRound: Int? get() = visits.firstOrNull { it.roundId == currentRoundId }?.strokes
    val par: Int? get() = visits.firstOrNull()?.par
    val roundCount: Int get() = visits.size

    /** Strokes since the first visit — negative is an improvement. */
    val strokesSinceFirst: Int?
        get() {
            val played = visits.filter { it.strokes > 0 }
            if (played.size < 2) return null
            return played.last().strokes - played.first().strokes
        }

    /** Net strokes gained on this hole against the player's own norm. */
    val strokesGainedRelative: Map<StrokesGained.Category, Double>
        get() = StrokesGained.relativeTo(strokesGained, strokesGainedBaseline)

    val greensHit: Int get() = approaches.count { it.result == Lie.Miss.GREEN }
    val missRight: Int get() = approaches.count { it.result == Lie.Miss.RIGHT }
    val missLeft: Int get() = approaches.count { it.result == Lie.Miss.LEFT }
    val missShort: Int get() = approaches.count { it.result == Lie.Miss.SHORT }

    /** The approach that finished closest to the pin. */
    val bestApproach: Approach? get() = approaches.minByOrNull { it.distanceToPinM }

    companion object {

        /**
         * Build the history for [hole] at the course played in [currentRoundId].
         *
         * Rounds are matched by course name — the watch does not record a stable
         * course id, and matching on name is what the rest of the app already does
         * when it groups a course's geometry.
         */
        fun build(
            currentRoundId: Long,
            hole: Int,
            rounds: List<RoundEntity>,
            allHoles: List<HoleEntity>,
            allShots: List<ShotEntity>,
            featuresByRound: Map<Long, List<CourseFeature>>,
        ): HoleHistory {
            // One pass over the shot table — the per-hole lookups below would
            // otherwise rescan every shot of every round.
            val shotsByHole = allShots.groupBy { it.roundId to it.hole }

            val current = rounds.firstOrNull { it.id == currentRoundId }
            val courseRounds = rounds
                .filter { current == null || it.courseName.equals(current.courseName, ignoreCase = true) }
                .sortedBy { it.startedAtS }
            val courseRoundIds = courseRounds.map { it.id }.toSet()

            val holesByRound = allHoles.filter { it.hole == hole && it.roundId in courseRoundIds }
                .associateBy { it.roundId }

            val visits = courseRounds.mapNotNull { r ->
                holesByRound[r.id]?.let { h -> Visit(r.id, r.startedAtS, h.strokes, h.par) }
            }

            // Approaches: the last shot with a club on each visit — the one that was
            // meant to finish on the green. Putts are excluded (clubId 0).
            val approaches = ArrayList<Approach>()
            for (r in courseRounds) {
                val h = holesByRound[r.id] ?: continue
                val pinLat = h.pinLat ?: continue
                val pinLon = h.pinLon ?: continue
                val shots = shotsByHole[r.id to hole].orEmpty().sortedBy { it.timeS }
                val approach = shots.lastOrNull { it.clubId != 0L } ?: continue
                val features = featuresByRound[r.id].orEmpty()
                val frame = LocalFrame(
                    pinLat, pinLon,
                    Lie.bearingDeg(approach.startLat, approach.startLon, pinLat, pinLon),
                )
                val (right, along) = frame.project(approach.endLat, approach.endLon)
                approaches += Approach(
                    roundId = r.id,
                    timeS = approach.timeS,
                    clubId = approach.clubId,
                    rightM = right,
                    alongM = along,
                    distanceToPinM = GolfFit.haversineM(approach.endLat, approach.endLon, pinLat, pinLon),
                    result = Lie.classifyMiss(
                        approach.startLat, approach.startLon,
                        approach.endLat, approach.endLon,
                        pinLat, pinLon, features,
                    ),
                )
            }

            // Strokes gained: this hole's visits against every hole the player has played.
            val holeSg = courseRounds.mapNotNull { r ->
                val h = holesByRound[r.id] ?: return@mapNotNull null
                StrokesGained.forHole(shotsByHole[r.id to hole].orEmpty(), h, featuresByRound[r.id].orEmpty())
            }
            val allSg = allHoles.mapNotNull { h ->
                StrokesGained.forHole(
                    shotsByHole[h.roundId to h.hole].orEmpty(), h, featuresByRound[h.roundId].orEmpty(),
                )
            }

            return HoleHistory(
                visits = visits,
                currentRoundId = currentRoundId,
                approaches = approaches,
                strokesGained = StrokesGained.average(holeSg),
                strokesGainedBaseline = StrokesGained.average(allSg),
                strokesGainedApproximate = holeSg.any { it.approximate },
            )
        }
    }
}
