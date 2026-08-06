package dev.jbiffis.caddie.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The design tokens for Caddie's dark visual system.
 *
 * Depth is flat: separation comes from three background steps
 * ([Canvas] -> [Surface] -> [SurfaceRaised]) plus a [Hairline] border. Only the
 * shot-map sheet and the floating map chrome cast shadows — do not add elevation
 * to cards.
 */
object C {
    // Backgrounds
    val Canvas = Color(0xFF0E1613)         // screen background
    val Surface = Color(0xFF16211D)        // card background
    val SurfaceRaised = Color(0xFF1C2823)  // inset tiles, chips, unselected tabs
    val Sheet = Color(0xFF131E19)          // bottom sheet
    val TabBar = Color(0xFF101B16)         // bottom nav

    // Text
    val TextPrimary = Color(0xFFF1F6F3)
    val TextSecondary = Color(0xFF90A49B)
    val TextTertiary = Color(0xFF5A6B63)   // footnotes, chevrons

    // Accents
    val Green = Color(0xFF5EC98A)          // primary, active, positive
    val GreenDark = Color(0xFF2F7D4A)      // gradient start
    val GreenLight = Color(0xFF6FD39A)     // gradient end, holed pin
    val Blue = Color(0xFF52B6D6)           // bogey, miss right, secondary data
    val Orange = Color(0xFFEC8A5C)         // over par, strokes lost
    val Red = Color(0xFFEC5C6E)            // heart rate
    val FlagRed = Color(0xFFE5573C)
    val Sand = Color(0xFFE7D9A6)
    val OnAccent = Color(0xFF08160E)       // text on green/blue/orange fills

    // Lines and glass
    val Hairline = Color(0x12FFFFFF)          // rgba(255,255,255,.07)
    val HairlineStrong = Color(0x24FFFFFF)    // rgba(255,255,255,.14)
    val GlassFill = Color(0x990A140F)         // rgba(10,20,15,.6)
    val GlassFillSoft = Color(0x8C0A140F)     // rgba(10,20,15,.55)
    val GlassFillPill = Color(0x9E0A140F)     // rgba(10,20,15,.62)
}

/**
 * Radii. 22.dp is the signature card radius — the one value that most defines the
 * look, so it is shared by cards and the map's zoom stack.
 */
object R {
    val bar = 6.dp
    val pinLabel = 9.dp
    val note = 12.dp
    val tile = 14.dp
    val button = 16.dp
    val card = 22.dp
    val sheet = 28.dp
    val pill = 999.dp
}

/** Screen gutter, card padding and the gap between cards. */
object S {
    val gutter = 14.dp
    val card = 16.dp
    val cardWide = 17.dp
    val gap = 12.dp
}
