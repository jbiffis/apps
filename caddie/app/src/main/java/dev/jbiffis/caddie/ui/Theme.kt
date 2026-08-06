package dev.jbiffis.caddie.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.HankenGrotesk
import dev.jbiffis.caddie.ui.design.SpaceGrotesk

/**
 * Caddie is a single dark theme — it is used outdoors in bright light where a
 * washed-out light theme reads worse, and the course map is drawn against a dark
 * canvas. There is deliberately no light variant.
 */
private val Colors = darkColorScheme(
    primary = C.Green,
    onPrimary = C.OnAccent,
    secondary = C.Blue,
    onSecondary = C.OnAccent,
    tertiary = C.Orange,
    onTertiary = C.OnAccent,
    background = C.Canvas,
    onBackground = C.TextPrimary,
    surface = C.Surface,
    onSurface = C.TextPrimary,
    surfaceVariant = C.SurfaceRaised,
    onSurfaceVariant = C.TextSecondary,
    surfaceContainer = C.Surface,
    surfaceContainerHigh = C.SurfaceRaised,
    surfaceContainerHighest = C.SurfaceRaised,
    outline = C.HairlineStrong,
    outlineVariant = C.Hairline,
    error = C.Orange,
    onError = C.OnAccent,
)

/**
 * Material's own typography, re-pointed at the two design faces. Screens use the
 * named styles in [dev.jbiffis.caddie.ui.design.T] directly; this exists so the
 * dialogs, snackbars and other Material components that remain still pick up the
 * right fonts.
 */
private val Type = Typography().let { d ->
    Typography(
        displayLarge = d.displayLarge.copy(fontFamily = SpaceGrotesk),
        displayMedium = d.displayMedium.copy(fontFamily = SpaceGrotesk),
        displaySmall = d.displaySmall.copy(fontFamily = SpaceGrotesk),
        headlineLarge = d.headlineLarge.copy(fontFamily = SpaceGrotesk),
        headlineMedium = d.headlineMedium.copy(fontFamily = SpaceGrotesk),
        headlineSmall = d.headlineSmall.copy(fontFamily = SpaceGrotesk),
        titleLarge = d.titleLarge.copy(fontFamily = SpaceGrotesk),
        titleMedium = d.titleMedium.copy(fontFamily = HankenGrotesk),
        titleSmall = d.titleSmall.copy(fontFamily = HankenGrotesk),
        bodyLarge = d.bodyLarge.copy(fontFamily = HankenGrotesk),
        bodyMedium = d.bodyMedium.copy(fontFamily = HankenGrotesk),
        bodySmall = d.bodySmall.copy(fontFamily = HankenGrotesk),
        labelLarge = d.labelLarge.copy(fontFamily = HankenGrotesk),
        labelMedium = d.labelMedium.copy(fontFamily = HankenGrotesk),
        labelSmall = d.labelSmall.copy(fontFamily = HankenGrotesk),
    )
}

@Composable
fun CaddieTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}

// Score colours, keyed to the design's accents rather than Garmin's red/blue.
val EagleColor = C.Green
val BirdieColor = C.Green
val BogeyColor = C.Blue
val DoubleColor = C.Orange
val SandColor = C.Sand

/**
 * Fill for a score badge, relative to par. Under par and par are distinguished by
 * fill weight (solid green vs. a neutral wash), bogey is blue and double-or-worse
 * orange — so a card can be read at a glance without counting numbers.
 */
fun scoreBadgeColors(strokes: Int, par: Int): Pair<Color, Color> = when {
    par <= 0 || strokes <= 0 -> C.SurfaceRaised to C.TextSecondary
    strokes < par -> C.Green to C.OnAccent
    strokes == par -> Color(0x14FFFFFF) to C.TextPrimary
    strokes == par + 1 -> C.Blue to C.OnAccent
    else -> C.Orange to C.OnAccent
}

/** Accent for a score used as plain text (no badge), or null at par. */
fun scoreColor(strokes: Int, par: Int): Color? = when {
    par <= 0 || strokes <= 0 -> null
    strokes < par -> C.Green
    strokes == par -> null
    strokes == par + 1 -> C.Blue
    else -> C.Orange
}
