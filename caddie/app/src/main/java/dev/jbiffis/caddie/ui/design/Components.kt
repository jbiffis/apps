package dev.jbiffis.caddie.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The signature card: flat fill, 22.dp radius, one hairline border and no shadow.
 * Every screen is built out of these — do not swap in Material's [androidx.compose.material3.Card],
 * whose default elevation breaks the flat elevation model.
 */
@Composable
fun CaddieCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(S.card),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(R.card)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Surface)
            .border(1.dp, C.Hairline, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Card title on the left, an optional quiet label or badge on the right. */
@Composable
fun CardHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = T.cardTitle, color = C.TextPrimary)
        trailing?.invoke(this)
    }
}

/** Quiet count/label chip, e.g. "7 rounds". */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(R.pill))
            .background(C.SurfaceRaised)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        style = T.metaSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        color = C.TextSecondary,
    )
}

/** The inset note strip that closes several cards: raised fill, 12.dp radius. */
@Composable
fun InsetNote(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R.note))
            .background(C.SurfaceRaised)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        content = content,
    )
}

/** A value over its label. Used in every stat row; the value is always Space Grotesk. */
@Composable
fun StatColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = T.stat22,
    valueColor: Color = C.TextPrimary,
    labelColor: Color = C.TextSecondary,
    align: Alignment.Horizontal = Alignment.Start,
    labelTop: Dp = 4.dp,
) {
    Column(modifier, horizontalAlignment = align) {
        Text(value, style = valueStyle, color = valueColor)
        Spacer(Modifier.height(labelTop))
        Text(label, style = T.metaSmall, color = labelColor)
    }
}

/** A stat on a raised tile — the scorecard summary's Score / To par / Thru. */
@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = C.TextPrimary,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(R.tile))
            .background(C.SurfaceRaised)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = T.stat26, color = valueColor)
        Spacer(Modifier.height(5.dp))
        Text(label, style = T.micro, color = C.TextSecondary)
    }
}

/**
 * Score relative to par, as a filled pill. Widens for two-digit scores rather than
 * shrinking the numeral, so a 12 stays as legible as a 4.
 */
@Composable
fun ScoreBadge(
    text: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .clip(RoundedCornerShape(R.tile))
            .background(background)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = T.stat14, color = foreground, textAlign = TextAlign.Center)
    }
}

/** Selectable chip — the club tab strip. */
@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(R.pill))
            .background(if (selected) C.Green else C.SurfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 8.dp),
        style = T.chip,
        color = if (selected) C.OnAccent else C.TextSecondary,
        maxLines = 1,
    )
}

/**
 * Translucent "glass" chrome floating over the course map. A real blur behind the
 * fill would cost a render pass per element on a screen that must stay at 60fps
 * while panning, so this uses a heavy translucent fill plus a light border, which
 * reads the same against the map's mid-tones.
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    fill: Color = C.GlassFill,
    border: Color = C.HairlineStrong,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Glass pill — the hole label and the wind readout. */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    fill: Color = C.GlassFillPill,
    horizontal: Dp = 14.dp,
    vertical: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(R.pill)
    Row(
        modifier
            .clip(shape)
            .background(fill)
            .border(1.dp, C.HairlineStrong, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = horizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Vertical hairline divider used inside pills. */
@Composable
fun PillDivider(height: Dp = 14.dp) {
    Box(Modifier.width(1.dp).height(height).background(C.HairlineStrong))
}

/** A number with a smaller trailing unit, e.g. "198 yd" — keeps the numeral dominant. */
@Composable
fun ValueWithUnit(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = T.stat17,
    valueColor: Color = C.TextPrimary,
    unitColor: Color = C.TextSecondary,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = valueStyle, color = valueColor)
        Text(" $unit", style = T.unit, color = unitColor)
    }
}

/** Screen title, with an optional subtitle beneath it. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 2.dp)) {
        Text(title, style = T.screenTitle, color = C.TextPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = T.bodySmall, color = C.TextSecondary)
        }
    }
}

/** Centred footnote that closes a list screen. */
@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = T.metaSmall,
        color = C.TextTertiary,
        textAlign = TextAlign.Center,
    )
}

/** Row separator matching the card hairline. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, color: Color = C.Hairline) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}
