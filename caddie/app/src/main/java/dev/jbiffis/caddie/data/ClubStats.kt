package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.GolfFit
import kotlin.math.abs
import kotlin.math.roundToInt

private const val M_TO_YD = 1.0936133

/** One tracked shot with the geometry the club screens need. */
class ClubShot(
    val shot: ShotEntity,
    /** + right / − left of the line from where it was hit to the pin. */
    val lateralM: Double?,
    /** + long / − short. Approach shots only. */
    val depthM: Double?,
) {
    val yards: Double get() = shot.distanceM * M_TO_YD
}

/**
 * Everything the Bag and Club Detail screens know about one club, derived from the
 * player's own tracked shots. There are no manufacturer numbers anywhere in here —
 * a club is exactly as long as it has actually been hit.
 */
class ClubStats(
    val clubId: Long,
    val name: String,
    val shots: List<ClubShot>,
    /** Tee shots on par 4/5 holes, classified against the mapped fairway. */
    val driving: Map<Lie.Miss, Int> = emptyMap(),
) {
    private val sortedYards: List<Double> = shots.map { it.yards }.sorted()

    val count get() = shots.size
    val averageYd get() = sortedYards.average()
    val medianYd get() = sortedYards[sortedYards.size / 2]
    val longestYd get() = sortedYards.last()
    val shortestYd get() = sortedYards.first()

    /**
     * The better half of the player's shots, averaged.
     *
     * This is the number to club off. A plain average is dragged down by thins and
     * tops, so clubbing to it leaves you short whenever you catch one properly;
     * "solid" is what a well-struck shot with this club actually goes.
     */
    val solidYd: Double
        get() {
            val upper = sortedYards.filter { it >= medianYd }
            return if (upper.isEmpty()) averageYd else upper.average()
        }

    val lefts get() = shots.count { (it.lateralM ?: 0.0) < -LATERAL_TOLERANCE_M }
    val rights get() = shots.count { (it.lateralM ?: 0.0) > LATERAL_TOLERANCE_M }
    val straight get() = count - lefts - rights

    fun pct(n: Int): Int = if (count > 0) (100.0 * n / count).roundToInt() else 0

    /** Fairways hit off the tee, where the course is mapped. */
    val drives get() = driving.values.sum()
    val fairwaysHit
        get() = driving.filterKeys { it == Lie.Miss.FAIRWAY || it == Lie.Miss.GREEN }.values.sum()

    /**
     * Shot counts in [bins] equal buckets spanning the club's whole range — the
     * distance histogram. Returns the bucket counts plus the range they cover.
     */
    fun histogram(bins: Int = 18): Histogram {
        val lo = shortestYd
        val hi = longestYd
        val span = (hi - lo).coerceAtLeast(1.0)
        val counts = IntArray(bins)
        for (y in sortedYards) {
            val idx = (((y - lo) / span) * bins).toInt().coerceIn(0, bins - 1)
            counts[idx]++
        }
        return Histogram(counts.toList(), lo, hi)
    }

    class Histogram(val counts: List<Int>, val lowYd: Double, val highYd: Double) {
        val max get() = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
        /** Which bucket a distance falls in — used to mark median and solid. */
        fun binOf(yards: Double): Int {
            val span = (highYd - lowYd).coerceAtLeast(1.0)
            return (((yards - lowYd) / span) * counts.size).toInt().coerceIn(0, counts.size - 1)
        }
    }

    companion object {
        const val LATERAL_TOLERANCE_M = 8.0

        /** Chips and mis-detections are not club distances. */
        const val MIN_SHOT_M = 15.0

        /** Beyond this, a shot is not aimed at the pin, so depth means nothing. */
        private const val APPROACH_MAX_M = 210.0

        /**
         * Build stats for every club with tracked shots, longest first — the order
         * the Bag screen shows them in.
         */
        fun computeAll(
            shots: List<ShotEntity>,
            holes: List<HoleEntity>,
            clubNames: Map<Long, String>,
            featuresByRound: Map<Long, List<CourseFeature>>,
        ): List<ClubStats> {
            val holeByKey = holes.associateBy { it.roundId to it.hole }
            val samples = HashMap<Long, MutableList<ClubShot>>()
            val drivingByClub = HashMap<Long, MutableMap<Lie.Miss, Int>>()

            // The first tracked shot on a hole is the tee shot.
            val teeShotIds = shots.groupBy { it.roundId to it.hole }
                .mapNotNull { (_, holeShots) -> holeShots.minByOrNull { it.timeS }?.id }
                .toHashSet()

            for (shot in shots) {
                if (shot.clubId == 0L || shot.distanceM < MIN_SHOT_M) continue
                val hole = holeByKey[shot.roundId to shot.hole]
                var lateral: Double? = null
                var depth: Double? = null
                val pinLat = hole?.pinLat
                val pinLon = hole?.pinLon
                if (hole != null && pinLat != null && pinLon != null) {
                    lateral = Repository.lateralMissM(
                        shot.startLat, shot.startLon, shot.endLat, shot.endLon, pinLat, pinLon,
                    )
                    val toPin = GolfFit.haversineM(shot.startLat, shot.startLon, pinLat, pinLon)
                    if (toPin <= APPROACH_MAX_M) {
                        depth = Repository.depthMissM(
                            shot.startLat, shot.startLon, shot.endLat, shot.endLon, pinLat, pinLon,
                        )
                    }
                    val features = featuresByRound[shot.roundId].orEmpty()
                    if (shot.id in teeShotIds && hole.par >= 4 && features.isNotEmpty()) {
                        val miss = Lie.classifyMiss(
                            shot.startLat, shot.startLon, shot.endLat, shot.endLon,
                            pinLat, pinLon, features,
                        )
                        val bucket = drivingByClub.getOrPut(shot.clubId) { HashMap() }
                        bucket[miss] = (bucket[miss] ?: 0) + 1
                    }
                }
                samples.getOrPut(shot.clubId) { ArrayList() }.add(ClubShot(shot, lateral, depth))
            }

            return samples.map { (clubId, list) ->
                ClubStats(clubId, clubNames[clubId] ?: "Club $clubId", list, drivingByClub[clubId] ?: emptyMap())
            }.sortedByDescending { it.averageYd }
        }
    }
}

/** Absolute lateral miss, for the dispersion plot's scale. */
fun ClubShot.absLateral(): Double = abs(lateralM ?: 0.0)
