package dev.jbiffis.caddie.data

import dev.jbiffis.caddie.fit.GolfFit
import kotlin.math.abs

/**
 * Strokes gained, computed from the tracked shots of a round.
 *
 * The model is Broadie's: every position on the course (a lie plus a distance to
 * the hole) has an *expected strokes to hole out* value, and a shot's value is
 *
 *     SG = E(where it started) − E(where it finished) − 1
 *
 * A shot that leaves you exactly where an average player would have left it
 * scores 0; a shot that beats that expectation scores positive.
 *
 * ### Two baselines, and why the UI uses the second one
 *
 * [BASELINE_SOURCE] is the PGA Tour expectation. Measuring an amateur against it
 * produces a large negative number on every shot — true, but useless: it says
 * "you are not a tour pro" over and over and never says which part of *your* game
 * cost you strokes today.
 *
 * So the screens compare a hole against **your own** average instead. Both halves
 * use the same absolute scale, and one is subtracted from the other
 * ([relativeTo]), so what the card shows is: on this hole, you play this category
 * this much better or worse than you usually do. That is the question a golfer
 * who plays the same course every week actually has.
 */
object StrokesGained {

    const val BASELINE_SOURCE = "PGA Tour expected-strokes baseline (Broadie)"

    /** Which part of the game a shot belongs to. */
    enum class Category(val label: String) {
        OFF_THE_TEE("Off the tee"),
        APPROACH("Approach"),
        SHORT_GAME("Short game"),
        PUTTING("Putting"),
    }

    /** The starting lie of a shot, as far as expected strokes are concerned. */
    enum class From { TEE, FAIRWAY, ROUGH, SAND, RECOVERY, GREEN }

    // Expected strokes to hole out, keyed by distance in YARDS (feet on the green).
    // Interpolated linearly between points and clamped at the ends.
    private val TEE = tableOf(
        100.0 to 2.92, 150.0 to 2.99, 200.0 to 3.12, 250.0 to 3.35, 300.0 to 3.71,
        350.0 to 3.88, 400.0 to 4.17, 450.0 to 4.40, 500.0 to 4.65, 550.0 to 4.89, 600.0 to 5.17,
    )
    private val FAIRWAY = tableOf(
        20.0 to 2.40, 40.0 to 2.60, 60.0 to 2.70, 80.0 to 2.75, 100.0 to 2.80, 120.0 to 2.85,
        140.0 to 2.91, 160.0 to 2.98, 180.0 to 3.08, 200.0 to 3.19, 220.0 to 3.32, 240.0 to 3.45,
        260.0 to 3.58, 280.0 to 3.69, 300.0 to 3.78,
    )
    private val ROUGH = tableOf(
        20.0 to 2.59, 40.0 to 2.78, 60.0 to 2.91, 80.0 to 2.96, 100.0 to 3.02, 120.0 to 3.08,
        140.0 to 3.15, 160.0 to 3.23, 180.0 to 3.33, 200.0 to 3.42, 220.0 to 3.53, 240.0 to 3.64,
        260.0 to 3.74, 280.0 to 3.83, 300.0 to 3.90,
    )
    private val SAND = tableOf(
        20.0 to 2.53, 40.0 to 2.82, 60.0 to 3.15, 80.0 to 3.24, 100.0 to 3.23, 120.0 to 3.21,
        140.0 to 3.22, 160.0 to 3.28, 180.0 to 3.40, 200.0 to 3.55, 220.0 to 3.70, 240.0 to 3.84,
        260.0 to 3.93, 280.0 to 4.00, 300.0 to 4.04,
    )
    private val RECOVERY = tableOf(
        100.0 to 3.45, 120.0 to 3.48, 140.0 to 3.52, 160.0 to 3.57, 180.0 to 3.65, 200.0 to 3.71,
        220.0 to 3.79, 240.0 to 3.86, 260.0 to 3.92, 280.0 to 3.96, 300.0 to 4.02,
    )
    /** Putting, in FEET. */
    private val GREEN = tableOf(
        1.0 to 1.001, 2.0 to 1.009, 3.0 to 1.053, 4.0 to 1.147, 5.0 to 1.256, 6.0 to 1.357,
        7.0 to 1.443, 8.0 to 1.515, 9.0 to 1.575, 10.0 to 1.626, 15.0 to 1.799, 20.0 to 1.888,
        25.0 to 1.941, 30.0 to 1.978, 40.0 to 2.037, 50.0 to 2.087, 60.0 to 2.136, 90.0 to 2.290,
    )

    private const val M_TO_YD = 1.0936133
    private const val M_TO_FT = 3.280839895

    /** Expected strokes to hole out from [from] at [distanceM] metres. 0 when holed. */
    fun expected(from: From, distanceM: Double): Double {
        if (distanceM <= 0.0) return 0.0
        return when (from) {
            From.GREEN -> {
                val ft = distanceM * M_TO_FT
                // Inside a foot the ball is effectively conceded to one putt.
                if (ft <= 1.0) 1.0 else interpolate(GREEN, ft)
            }
            From.TEE -> interpolate(TEE, distanceM * M_TO_YD)
            From.FAIRWAY -> interpolate(FAIRWAY, distanceM * M_TO_YD)
            From.ROUGH -> interpolate(ROUGH, distanceM * M_TO_YD)
            From.SAND -> interpolate(SAND, distanceM * M_TO_YD)
            From.RECOVERY -> interpolate(RECOVERY, distanceM * M_TO_YD)
        }
    }

    /**
     * Strokes gained by a single shot: what it saved against the expectation of the
     * position it was played from. [endDistanceM] is 0 when the shot was holed.
     */
    fun shotValue(
        from: From, startDistanceM: Double,
        endFrom: From, endDistanceM: Double,
    ): Double = expected(from, startDistanceM) - expected(endFrom, endDistanceM) - 1.0

    /** Which category a shot belongs to, from where it was played. */
    fun categoryOf(from: From, startDistanceM: Double, isTeeShotOnLongHole: Boolean): Category = when {
        isTeeShotOnLongHole -> Category.OFF_THE_TEE
        from == From.GREEN -> Category.PUTTING
        // Inside ~30 yards is a pitch/chip, not an approach — different skill.
        startDistanceM * M_TO_YD <= 30.0 -> Category.SHORT_GAME
        else -> Category.APPROACH
    }

    /** Strokes gained for one hole, split by category. All values are absolute. */
    data class HoleSg(
        val roundId: Long,
        val hole: Int,
        val byCategory: Map<Category, Double>,
        val shotsCounted: Int,
        /** True when the course had no mapped polygons, so lies were inferred. */
        val approximate: Boolean,
    ) {
        val net: Double get() = byCategory.values.sum()
        operator fun get(c: Category): Double = byCategory[c] ?: 0.0
    }

    /**
     * Strokes gained for one hole's tracked shots.
     *
     * Returns null when the hole has no pin position — without it there is no
     * distance to the hole, and therefore no expectation to measure against.
     *
     * Note that this measures the *tracked* shots. The watch can miss a shot (or
     * invent one), in which case the hole's total will not reconcile exactly with
     * the scorecard; the per-category comparison is still meaningful because the
     * same bias applies to the baseline it is compared with.
     */
    fun forHole(
        shots: List<ShotEntity>,
        hole: HoleEntity,
        features: List<CourseFeature>,
    ): HoleSg? {
        val pinLat = hole.pinLat ?: return null
        val pinLon = hole.pinLon ?: return null
        if (shots.isEmpty()) return null
        val ordered = shots.sortedBy { it.timeS }
        val approximate = features.none { it.type == Lie.Type.GREEN || it.type == Lie.Type.FAIRWAY }

        val totals = HashMap<Category, Double>()
        var counted = 0
        ordered.forEachIndexed { i, shot ->
            val startDist = GolfFit.haversineM(shot.startLat, shot.startLon, pinLat, pinLon)
            val endDist = GolfFit.haversineM(shot.endLat, shot.endLon, pinLat, pinLon)
            val isTee = i == 0 && hole.par >= 4
            val from = lieOf(shot.startLat, shot.startLon, features, isTeeShot = i == 0, isPutt = shot.clubId == 0L, distanceToPinM = startDist)
            // The last tracked shot on the hole is the one that finished it.
            val holed = i == ordered.lastIndex
            val endFrom = if (holed) From.GREEN else lieOf(
                shot.endLat, shot.endLon, features,
                isTeeShot = false,
                isPutt = ordered.getOrNull(i + 1)?.clubId == 0L,
                distanceToPinM = endDist,
            )
            val value = shotValue(from, startDist, endFrom, if (holed) 0.0 else endDist)
            val category = categoryOf(from, startDist, isTee)
            totals[category] = (totals[category] ?: 0.0) + value
            counted++
        }
        return HoleSg(hole.roundId, hole.hole, totals, counted, approximate)
    }

    /**
     * The lie a shot was played from. Uses the mapped course polygons when the
     * course is on OpenStreetMap; otherwise falls back to what the shot itself
     * tells us — the first shot is off a tee, a club-less shot is a putt, and
     * anything else is assumed to be a fairway lie so the estimate stays neutral
     * rather than flattering or punishing.
     */
    fun lieOf(
        lat: Double, lon: Double,
        features: List<CourseFeature>,
        isTeeShot: Boolean,
        isPutt: Boolean,
        distanceToPinM: Double,
    ): From {
        if (isTeeShot) return From.TEE
        when (Lie.lieAt(lat, lon, features)) {
            Lie.Type.GREEN -> return From.GREEN
            Lie.Type.TEE -> return From.TEE
            Lie.Type.BUNKER -> return From.SAND
            Lie.Type.FAIRWAY -> return From.FAIRWAY
            Lie.Type.WOODS -> return From.RECOVERY
            Lie.Type.ROUGH -> return From.ROUGH
            // WATER / PATH / TREE / UNKNOWN fall through to the inference below.
            else -> {}
        }
        // Unmapped course: a club-less shot within a green's reach is a putt.
        if (isPutt && distanceToPinM <= 30.0) return From.GREEN
        return From.FAIRWAY
    }

    /**
     * Average strokes gained per hole per category over a set of holes — the
     * player's own baseline.
     */
    fun average(holes: List<HoleSg>): Map<Category, Double> {
        if (holes.isEmpty()) return emptyMap()
        return Category.entries.associateWith { c -> holes.sumOf { it[c] } / holes.size }
    }

    /**
     * This hole's average, expressed against the player's own norm. Positive means
     * the hole plays *better* for them than their typical hole.
     */
    fun relativeTo(hole: Map<Category, Double>, baseline: Map<Category, Double>): Map<Category, Double> =
        Category.entries.associateWith { c -> (hole[c] ?: 0.0) - (baseline[c] ?: 0.0) }

    // --- table machinery -------------------------------------------------

    private fun tableOf(vararg points: Pair<Double, Double>): DoubleArray {
        val flat = DoubleArray(points.size * 2)
        points.forEachIndexed { i, (x, y) -> flat[i * 2] = x; flat[i * 2 + 1] = y }
        return flat
    }

    /** Linear interpolation over a flattened (x, y) table, clamped at both ends. */
    private fun interpolate(table: DoubleArray, x: Double): Double {
        val n = table.size / 2
        if (x <= table[0]) return table[1]
        if (x >= table[(n - 1) * 2]) return table[(n - 1) * 2 + 1]
        for (i in 0 until n - 1) {
            val x0 = table[i * 2]
            val x1 = table[(i + 1) * 2]
            if (x in x0..x1) {
                val y0 = table[i * 2 + 1]
                val y1 = table[(i + 1) * 2 + 1]
                if (abs(x1 - x0) < 1e-9) return y0
                return y0 + (x - x0) / (x1 - x0) * (y1 - y0)
            }
        }
        return table[(n - 1) * 2 + 1]
    }
}
