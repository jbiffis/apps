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

fun toParString(score: Int, par: Int): String = when {
    score == 0 -> "–"
    score == par -> "E"
    score > par -> "+${score - par}"
    else -> "${score - par}"
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

private val CARDINALS = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

/** 8-point compass label for a bearing in degrees (the direction wind comes FROM). */
fun windCardinal(dirDeg: Int): String = CARDINALS[(((dirDeg % 360) + 360) % 360 + 22) / 45 % 8]

/** e.g. "SW 15 km/h". [dirDeg] is the direction the wind blows FROM. */
fun formatWind(speedKmh: Double?, dirDeg: Int?): String? {
    if (speedKmh == null) return null
    val s = "${speedKmh.roundToInt()} km/h"
    return if (dirDeg == null) s else "${windCardinal(dirDeg)} $s"
}
