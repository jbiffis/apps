package dev.jbiffis.caddie.data

import kotlin.math.cos
import kotlin.math.sin

/**
 * Lie detection and miss classification from OpenStreetMap course polygons
 * (golf=fairway / green / bunker / tee / water_hazard, natural=water / wood).
 */
object Lie {

    enum class Type(val label: String) {
        GREEN("Green"),
        TEE("Tee box"),
        BUNKER("Bunker"),
        WATER("Water"),
        FAIRWAY("Fairway"),
        WOODS("Trees"),
        ROUGH("Rough"),
        TREE("Tree"), // a single OSM tree node (a point, not a polygon)
        UNKNOWN("Unknown"),
    }

    /** Where a shot finished relative to the fairway — feeds driving accuracy stats. */
    enum class Miss(val label: String) {
        FAIRWAY("Fairway"),
        GREEN("On the green"),
        LEFT("Missed left"),
        RIGHT("Missed right"),
        SHORT("Short"),
        BUNKER("Bunker"),
        WATER("Water"),
        OTHER("Rough"),
    }

    fun typeFromOsm(golf: String?, natural: String?, landuse: String? = null): Type? = when {
        golf == "green" -> Type.GREEN
        golf == "tee" -> Type.TEE
        golf == "bunker" || natural == "sand" -> Type.BUNKER
        golf == "water_hazard" || golf == "lateral_water_hazard" || natural == "water" -> Type.WATER
        golf == "fairway" -> Type.FAIRWAY
        golf == "rough" -> Type.ROUGH
        natural == "tree" -> Type.TREE
        natural == "wood" || natural == "scrub" || landuse == "forest" -> Type.WOODS
        else -> null
    }

    /** Ray-casting point-in-polygon test directly on lat/lon (fine at golf-hole scale). */
    fun pointInPolygon(lat: Double, lon: Double, points: List<Pair<Double, Double>>): Boolean {
        if (points.size < 3) return false
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val (latI, lonI) = points[i]
            val (latJ, lonJ) = points[j]
            if ((latI > lat) != (latJ > lat) &&
                lon < (lonJ - lonI) * (lat - latI) / (latJ - latI) + lonI
            ) inside = !inside
            j = i
        }
        return inside
    }

    private val PRIORITY = listOf(Type.GREEN, Type.TEE, Type.BUNKER, Type.WATER, Type.FAIRWAY, Type.WOODS, Type.ROUGH)

    /** The most specific feature the point sits in, or ROUGH when the course is mapped but nothing matches. */
    fun lieAt(lat: Double, lon: Double, features: List<CourseFeature>): Type {
        for (wanted in PRIORITY) {
            for (f in features) {
                if (f.type == wanted && pointInPolygon(lat, lon, f.points)) return wanted
            }
        }
        return if (features.isEmpty()) Type.UNKNOWN else Type.ROUGH
    }

    /**
     * Classify where a shot finished relative to the fairway it was aimed down.
     * [startLat]/[startLon] is where the shot was hit from, the target line runs
     * to [pinLat]/[pinLon].
     */
    fun classifyMiss(
        startLat: Double, startLon: Double,
        endLat: Double, endLon: Double,
        pinLat: Double, pinLon: Double,
        features: List<CourseFeature>,
    ): Miss {
        when (lieAt(endLat, endLon, features)) {
            Type.GREEN -> return Miss.GREEN
            Type.FAIRWAY, Type.TEE -> return Miss.FAIRWAY
            Type.BUNKER -> return Miss.BUNKER
            Type.WATER -> return Miss.WATER
            else -> {}
        }
        // In the rough (or unmapped): work out which side of the fairway we are on.
        val frame = LocalFrame(startLat, startLon, bearingDeg(startLat, startLon, pinLat, pinLon))
        val (endRight, endAlong) = frame.project(endLat, endLon)

        val fairways = features.filter { it.type == Type.FAIRWAY }
            .filter { f -> f.points.any { frame.project(it.first, it.second).let { (r, a) -> a > -50 && kotlin.math.abs(r) < 150 } } }
        if (fairways.isEmpty()) {
            // No mapped fairway on this line — fall back to the pin-line heuristic.
            return when {
                kotlin.math.abs(endRight) <= 12 -> Miss.SHORT
                endRight < 0 -> Miss.LEFT
                else -> Miss.RIGHT
            }
        }

        // The fairway's lateral extent at the ball's depth: intersect each polygon
        // edge with the line along = endAlong (robust even for sparse polygons).
        var minRight = Double.MAX_VALUE
        var maxRight = -Double.MAX_VALUE
        var minAlong = Double.MAX_VALUE
        for (f in fairways) {
            val proj = f.points.map { frame.project(it.first, it.second) }
            for (p in proj) minAlong = minOf(minAlong, p.second)
            for (i in proj.indices) {
                val (r1, a1) = proj[i]
                val (r2, a2) = proj[(i + 1) % proj.size]
                if (endAlong in minOf(a1, a2)..maxOf(a1, a2) && a1 != a2) {
                    val r = r1 + (endAlong - a1) / (a2 - a1) * (r2 - r1)
                    minRight = minOf(minRight, r)
                    maxRight = maxOf(maxRight, r)
                }
            }
        }
        return when {
            minRight == Double.MAX_VALUE && endAlong < minAlong -> Miss.SHORT
            // Beyond the fairway with no width at this depth — judge by side of the line
            minRight == Double.MAX_VALUE -> if (endRight < 0) Miss.LEFT else Miss.RIGHT
            endRight < minRight -> Miss.LEFT
            endRight > maxRight -> Miss.RIGHT
            endAlong < minAlong -> Miss.SHORT
            else -> Miss.OTHER
        }
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val ex = (lon2 - lon1) * cos(Math.toRadians(lat1))
        val ny = lat2 - lat1
        return (Math.toDegrees(kotlin.math.atan2(ex, ny)) + 360.0) % 360.0
    }
}

/** A course polygon in memory (decoded from [CourseFeatureEntity]). */
data class CourseFeature(
    val type: Lie.Type,
    val holeRef: Int?,
    /** lat,lon vertex list */
    val points: List<Pair<Double, Double>>,
)

/**
 * Local metric frame anchored at an origin, rotated so the target line points "up".
 * project() returns (right, along): metres right of the line, metres toward the target.
 */
class LocalFrame(private val originLat: Double, private val originLon: Double, bearingDeg: Double) {
    private val cosLat = cos(Math.toRadians(originLat))
    private val sinB = sin(Math.toRadians(bearingDeg))
    private val cosB = cos(Math.toRadians(bearingDeg))

    fun project(lat: Double, lon: Double): Pair<Double, Double> {
        val ex = (lon - originLon) * 111320.0 * cosLat
        val ny = (lat - originLat) * 111320.0
        val along = ex * sinB + ny * cosB
        val right = ex * cosB - ny * sinB
        return right to along
    }

    /** Inverse of [project]: (right, along) metres back to (lat, lon). */
    fun unproject(right: Double, along: Double): Pair<Double, Double> {
        val ex = right * cosB + along * sinB
        val ny = -right * sinB + along * cosB
        val lat = originLat + ny / 111320.0
        val lon = originLon + ex / (111320.0 * cosLat)
        return lat to lon
    }
}
