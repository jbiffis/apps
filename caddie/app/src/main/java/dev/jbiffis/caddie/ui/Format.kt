package dev.jbiffis.caddie.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

const val M_TO_YD = 1.0936133

fun Double.toYards(): Int = (this * M_TO_YD).roundToInt()

fun formatDate(unixS: Long): String =
    SimpleDateFormat("EEE, MMM d yyyy", Locale.getDefault()).format(Date(unixS * 1000))

fun formatTime(unixS: Long): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(unixS * 1000))

/** Compact date for inline notes, e.g. "Jun 2". */
fun formatShortDate(unixS: Long): String =
    SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(unixS * 1000))

/** Weekday and day, e.g. "Wed, Jul 22" — used in shot history rows. */
fun formatDayDate(unixS: Long): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(unixS * 1000))

fun toParString(score: Int, par: Int): String = when {
    score == 0 -> "–"
    score == par -> "E"
    score > par -> "+${score - par}"
    else -> "${score - par}"
}

/** Golf's name for a score, e.g. 6 on a par 5 is a "Bogey". */
fun scoreName(strokes: Int, par: Int): String = when {
    strokes <= 0 || par <= 0 -> "Not played"
    strokes == 1 -> "Hole in one"
    else -> when (strokes - par) {
        -3 -> "Albatross"
        -2 -> "Eagle"
        -1 -> "Birdie"
        0 -> "Par"
        1 -> "Bogey"
        2 -> "Double bogey"
        3 -> "Triple bogey"
        else -> if (strokes < par) "${par - strokes} under" else "${strokes - par} over"
    }
}

/** Feet, for putts and anything measured around the hole. */
fun Double.toFeet(): Int = (this * 3.280839895).roundToInt()

/**
 * A shot's distance the way a golfer says it: putts in feet, everything else in
 * yards. Returns the number and its unit separately so the unit can be set smaller.
 */
fun shotDistance(distanceM: Double, isPutt: Boolean): Pair<String, String> =
    if (isPutt) "${distanceM.toFeet()}" to "ft" else "${distanceM.toYards()}" to "yd"

/** Signed strokes-gained value, e.g. 0.42 -> "+0.4" and -0.25 -> "−0.3". */
fun signedSg(value: Double): String {
    val rounded = Math.round(value * 10.0) / 10.0
    // U+2212 minus, not a hyphen — it aligns with the digits.
    return if (rounded < 0) "−${"%.1f".format(-rounded)}" else "+${"%.1f".format(rounded)}"
}

/** Short label for a club, e.g. "Driver (10.5°)" -> "Dr", "6 Iron (30.5°)" -> "6i". */
fun clubAbbrev(name: String?, clubId: Long): String {
    if (clubId == 0L) return "Putt"
    val n = name ?: return "?"
    Regex("^(\\d+)\\s*Iron").find(n)?.let { return "${it.groupValues[1]}i" }
    Regex("^(\\d+)\\s*Hybrid").find(n)?.let { return "${it.groupValues[1]}H" }
    Regex("^(\\d+)\\s*Wood").find(n)?.let { return "${it.groupValues[1]}W" }
    return when {
        n.startsWith("Driver") -> "Dr"
        n.startsWith("Pitching") -> "PW"
        n.startsWith("Gap") -> "GW"
        n.startsWith("Sand") -> "SW"
        n.startsWith("Lob") -> "LW"
        n.startsWith("Putter") -> "Pt"
        else -> n.take(3)
    }
}

/** Loft in degrees parsed from a club name like "6 Iron (30.5°)", or null. */
fun clubLoft(name: String?): Double? =
    name?.let { Regex("\\(([0-9.]+)\\s*°\\)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** 8-point compass label for a bearing in degrees (the direction wind comes FROM). */
fun windCardinal(dirDeg: Int): String = CARDINALS[(((dirDeg % 360) + 360) % 360 + 22) / 45 % 8]

/** e.g. "SW 15 km/h". [dirDeg] is the direction the wind blows FROM. */
fun formatWind(speedKmh: Double?, dirDeg: Int?): String? {
    if (speedKmh == null) return null
    val s = "${speedKmh.roundToInt()} km/h"
    return if (dirDeg == null) s else "${windCardinal(dirDeg)} $s"
}
