package dev.jbiffis.caddie.ui.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.jbiffis.caddie.R

/**
 * Space Grotesk — the "instrument" face. Every numeral, screen title and uppercase
 * overline uses it; its consistency on numbers is what makes the app read like a
 * device rather than a web page.
 */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** Hanken Grotesk — body copy, card titles, list rows, buttons. */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk_bold, FontWeight.Bold),
    Font(R.font.hanken_grotesk_extrabold, FontWeight.ExtraBold),
)

/**
 * Named text styles from the design. Sizes are in sp so they still respect the
 * user's font-scale setting; the mock's px values map 1:1.
 */
object T {
    // Space Grotesk — numerals and titles
    val screenTitle = TextStyle(fontFamily = SpaceGrotesk, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.26).sp)
    val heroStat = TextStyle(fontFamily = SpaceGrotesk, fontSize = 62.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.86).sp)
    val bigStat = TextStyle(fontFamily = SpaceGrotesk, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    val stat26 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val stat24 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    val stat22 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val stat20 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val stat19 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    val stat17 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val stat16 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    val stat16Bold = TextStyle(fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val stat15 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    val stat14 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val stat13 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val mono11 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 11.sp)
    val mono9 = TextStyle(fontFamily = SpaceGrotesk, fontSize = 9.sp)

    /** Uppercase overline, e.g. the scorecard's column headers and "HOLE 7". */
    val overline = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.88.sp,
    )
    val overlineWide = TextStyle(
        fontFamily = SpaceGrotesk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.44.sp,
    )

    // Hanken Grotesk — copy
    val cardTitle = TextStyle(fontFamily = HankenGrotesk, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
    val rowTitle = TextStyle(fontFamily = HankenGrotesk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val rowTitleBold = TextStyle(fontFamily = HankenGrotesk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val body = TextStyle(fontFamily = HankenGrotesk, fontSize = 13.sp)
    val bodySmall = TextStyle(fontFamily = HankenGrotesk, fontSize = 12.5.sp)
    val meta = TextStyle(fontFamily = HankenGrotesk, fontSize = 12.sp)
    val metaSmall = TextStyle(fontFamily = HankenGrotesk, fontSize = 11.5.sp)
    val micro = TextStyle(fontFamily = HankenGrotesk, fontSize = 11.sp)
    val microLabel = TextStyle(fontFamily = HankenGrotesk, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    val chip = TextStyle(fontFamily = HankenGrotesk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    val button = TextStyle(fontFamily = HankenGrotesk, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val unit = TextStyle(fontFamily = HankenGrotesk, fontSize = 11.sp)
}
