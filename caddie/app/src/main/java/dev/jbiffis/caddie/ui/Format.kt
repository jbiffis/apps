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
