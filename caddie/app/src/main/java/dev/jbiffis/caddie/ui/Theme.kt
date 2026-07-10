package dev.jbiffis.caddie.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Fairway = Color(0xFF2E7D32)
private val FairwayDark = Color(0xFF81C784)
private val Sand = Color(0xFFD7CCA1)

private val LightColors = lightColorScheme(
    primary = Fairway,
    secondary = Color(0xFF558B2F),
    tertiary = Color(0xFF00695C),
    surfaceVariant = Color(0xFFEDF2E6),
)

private val DarkColors = darkColorScheme(
    primary = FairwayDark,
    secondary = Color(0xFFAED581),
    tertiary = Color(0xFF80CBC4),
)

// Score colours (Garmin-style: red = under par, blue = over par)
val EagleColor = Color(0xFFF9A825)
val BirdieColor = Color(0xFFD32F2F)
val BogeyColor = Color(0xFF1976D2)
val DoubleColor = Color(0xFF0D47A1)
val SandColor = Sand

@Composable
fun CaddieTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

fun scoreColor(strokes: Int, par: Int): Color? = when {
    par <= 0 || strokes <= 0 -> null
    strokes <= par - 2 -> EagleColor
    strokes == par - 1 -> BirdieColor
    strokes == par -> null
    strokes == par + 1 -> BogeyColor
    else -> DoubleColor
}
