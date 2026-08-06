package dev.jbiffis.caddie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.R
import dev.jbiffis.caddie.data.HoleHistory
import dev.jbiffis.caddie.data.Lie
import dev.jbiffis.caddie.data.ShotEntity
import dev.jbiffis.caddie.data.StrokesGained
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.CardHeader
import dev.jbiffis.caddie.ui.design.CountBadge
import dev.jbiffis.caddie.ui.design.InsetNote
import dev.jbiffis.caddie.ui.design.RowDivider
import dev.jbiffis.caddie.ui.design.StatColumn
import dev.jbiffis.caddie.ui.design.T
import dev.jbiffis.caddie.ui.design.ValueWithUnit
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The four cards behind the shot map's swipe-up sheet. Every number here is
 * computed from imported rounds — where a card has nothing to show yet, it says
 * so rather than rendering an empty chart.
 */

// --- Card A: this hole over time ----------------------------------------

@Composable
fun HoleOverTimeCard(history: HoleHistory) {
    val played = history.visits.filter { it.strokes > 0 }
    CaddieCard {
        CardHeader("This hole over time") {
            CountBadge(if (played.size == 1) "1 round" else "${played.size} rounds")
        }
        if (played.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("No scores recorded for this hole yet.", style = T.bodySmall, color = C.TextSecondary)
            return@CaddieCard
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatColumn(
                value = "${history.best ?: 0}",
                label = "Best",
                modifier = Modifier.weight(1f),
                valueColor = C.Green,
            )
            StatColumn(
                value = history.average?.let { "%.1f".format(it) } ?: "–",
                label = "Average",
                modifier = Modifier.weight(1f),
            )
            StatColumn(
                value = history.thisRound?.takeIf { it > 0 }?.toString() ?: "–",
                label = "This round",
                modifier = Modifier.weight(1f),
            )
        }

        if (played.size >= 2) {
            Spacer(Modifier.height(10.dp))
            ScoreSparkline(
                scores = played.map { it.strokes },
                currentIndex = played.indexOfFirst { it.roundId == history.currentRoundId },
                modifier = Modifier.fillMaxWidth().height(58.dp),
            )
        }

        val delta = history.strokesSinceFirst
        if (delta != null && played.size >= 2) {
            Spacer(Modifier.height(10.dp))
            InsetNote {
                TrendArrow(improving = delta <= 0)
                Text(
                    buildAnnotatedString {
                        when {
                            delta < 0 -> {
                                append("Trending better — ")
                                bold("${-delta} ${strokesWord(-delta)}")
                                append(" since your first visit")
                            }
                            delta > 0 -> {
                                append("Costing you ")
                                bold("$delta ${strokesWord(delta)}")
                                append(" more than your first visit")
                            }
                            else -> {
                                append("Level with your first visit — ")
                                bold("${played.first().strokes}")
                                append(" both times")
                            }
                        }
                    },
                    style = T.bodySmall,
                    color = C.TextSecondary,
                )
            }
        }
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.bold(text: String) {
    withStyle(SpanStyle(color = C.TextPrimary, fontWeight = FontWeight.Bold)) { append(text) }
}

private fun strokesWord(n: Int) = if (n == 1) "stroke" else "strokes"

@Composable
private fun TrendArrow(improving: Boolean) {
    val color = if (improving) C.Green else C.Orange
    Canvas(Modifier.size(14.dp)) {
        val w = size.width
        val stroke = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val top = if (improving) w * 0.15f else w * 0.85f
        val bottom = if (improving) w * 0.85f else w * 0.15f
        drawLine(color, Offset(w / 2, top), Offset(w / 2, bottom), strokeWidth = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.22f, if (improving) w * 0.55f else w * 0.45f)
            lineTo(w / 2, bottom)
            lineTo(w * 0.78f, if (improving) w * 0.55f else w * 0.45f)
        }
        drawPath(head, color, style = stroke)
    }
}

/** Score history, lower is better — so the line rises as the golfer improves. */
@Composable
private fun ScoreSparkline(scores: List<Int>, currentIndex: Int, modifier: Modifier = Modifier) {
    val best = scores.min()
    val worst = scores.max()
    Canvas(modifier) {
        val padX = 10.dp.toPx()
        val padY = 6.dp.toPx()
        val w = size.width - padX * 2
        val h = size.height - padY * 2
        val span = (worst - best).coerceAtLeast(1)
        fun point(i: Int): Offset {
            val x = padX + if (scores.size == 1) w / 2 else w * i / (scores.size - 1f)
            // Best score at the top, worst at the bottom.
            val y = padY + h * (scores[i] - best) / span
            return Offset(x, y)
        }
        val pts = scores.indices.map(::point)

        val area = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(
            area,
            Brush.verticalGradient(
                0f to C.Green.copy(alpha = 0.26f),
                1f to C.Green.copy(alpha = 0f),
            ),
        )
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            line, C.Green,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Solid dot on the best round, hollow ring on the round being viewed.
        val bestIdx = scores.indexOf(best)
        drawCircle(C.Green, radius = 4.dp.toPx(), center = pts[bestIdx])
        if (currentIndex in pts.indices) {
            drawCircle(C.Surface, radius = 4.5.dp.toPx(), center = pts[currentIndex])
            drawCircle(
                C.Green, radius = 4.5.dp.toPx(), center = pts[currentIndex],
                style = Stroke(width = 2.5.dp.toPx()),
            )
        }
    }
}

// --- Card B: strokes gained ---------------------------------------------

@Composable
fun StrokesGainedCard(history: HoleHistory) {
    val values = history.strokesGainedRelative
    val net = values.values.sum()
    val scale = max(values.values.maxOfOrNull { abs(it) } ?: 0.0, 0.2)
    val measurable = history.strokesGained.isNotEmpty()

    CaddieCard {
        CardHeader("Strokes gained here") {
            if (measurable) {
                Text(
                    signedSg(net),
                    style = T.stat14.copy(fontWeight = FontWeight.SemiBold),
                    color = if (net >= 0) C.Green else C.Orange,
                )
            }
        }
        if (!measurable) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Needs a pin position on this hole — import the round's SCORE file, " +
                    "or set the pin, and this fills in.",
                style = T.bodySmall,
                color = C.TextSecondary,
            )
            return@CaddieCard
        }

        StrokesGained.Category.entries.forEachIndexed { i, category ->
            Spacer(Modifier.height(if (i == 0) 14.dp else 11.dp))
            SgRow(category.label, values[category] ?: 0.0, scale)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (history.strokesGainedApproximate) {
                "Against your own average hole. Lies are estimated — this course isn't fully mapped on OpenStreetMap."
            } else {
                "Against your own average hole, not a tour pro's."
            },
            style = T.micro,
            color = C.TextTertiary,
        )
    }
}

/**
 * One diverging bar. Strokes lost grow left of the centre tick, strokes gained
 * grow right, so a glance down the card shows which side of zero the hole sits on
 * without reading a single number.
 */
@Composable
private fun SgRow(label: String, value: Double, scale: Double) {
    val positive = value >= 0
    val color = if (positive) C.Green else C.Orange
    // The largest value on the card fills 85% of its half of the track.
    val fraction = (abs(value) / scale * 0.85).coerceIn(0.0, 1.0).toFloat()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(82.dp), style = T.bodySmall, color = C.TextSecondary)
        Spacer(Modifier.width(10.dp))
        // 22.dp tall so the centre tick can overhang the 14.dp bar track.
        Box(Modifier.weight(1f).height(22.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().height(14.dp)) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (!positive && fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp))
                                .background(color),
                        )
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (positive && fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topEnd = 7.dp, bottomEnd = 7.dp))
                                .background(color),
                        )
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(C.HairlineStrong))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            signedSg(value),
            Modifier.width(40.dp),
            style = T.stat13,
            color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

// --- Card C: where you miss ---------------------------------------------

@Composable
fun WhereYouMissCard(history: HoleHistory, clubNames: Map<Long, String>) {
    val approaches = history.approaches
    CaddieCard {
        CardHeader("Where you miss") {
            Text(
                if (history.roundCount == 1) "this round" else "last ${history.roundCount} rounds",
                style = T.metaSmall,
                color = C.TextSecondary,
            )
        }
        if (approaches.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "No approach shots tracked into this green yet.",
                style = T.bodySmall,
                color = C.TextSecondary,
            )
            return@CaddieCard
        }

        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MissTarget(approaches, Modifier.size(128.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val total = approaches.size
                MissLegendRow("Hit green", history.greensHit, total, C.Green, highlight = true)
                MissLegendRow("Miss right", history.missRight, total, C.Blue)
                MissLegendRow("Miss left", history.missLeft, total, C.Orange)
                if (history.missShort > 0) MissLegendRow("Short", history.missShort, total, C.Sand)
            }
        }

        history.bestApproach?.let { best ->
            Spacer(Modifier.height(14.dp))
            InsetNote {
                Icon(
                    painterResource(R.drawable.ic_flag_pin),
                    contentDescription = null,
                    tint = C.Green,
                    modifier = Modifier.size(14.dp),
                )
                val club = clubNames[best.clubId] ?: clubAbbrev(null, best.clubId)
                val dist = if (best.distanceToPinM < 27.0) "${best.distanceToPinM.toFeet()} ft"
                else "${best.distanceToPinM.toYards()} yd"
                Text(
                    buildAnnotatedString {
                        append("Best approach here: ")
                        bold("$club → $dist")
                        append(" · ${formatShortDate(best.timeS)}")
                    },
                    style = T.bodySmall,
                    color = C.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun MissLegendRow(label: String, count: Int, total: Int, color: Color, highlight: Boolean = false) {
    val pct = if (total > 0) (100.0 * count / total).roundToInt() else 0
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(9.dp))
        Text(label, Modifier.weight(1f), style = T.body, color = C.TextSecondary)
        Text(
            "$pct%",
            style = T.stat16Bold,
            color = if (highlight) C.TextPrimary else color,
        )
    }
}

/**
 * Where approach shots finished, plotted around the pin. Up is past the pin, right
 * is right of the line the shot was played on — so the cluster shows a miss pattern
 * the way the golfer experienced it, not a compass bearing.
 */
@Composable
private fun MissTarget(approaches: List<HoleHistory.Approach>, modifier: Modifier = Modifier) {
    // Scale so the widest miss sits just inside the ring, with a 20 m floor to stop
    // one tight round from exaggerating a two-metre spread.
    val reach = max(approaches.maxOfOrNull { hypot(it.rightM, it.alongM) } ?: 20.0, 20.0)
    Box(modifier) {
        Box(Modifier.matchParentSize().clip(CircleShape).background(C.SurfaceRaised))
        Box(
            Modifier
                .matchParentSize()
                .padding(24.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(Color(0xFF2A3A33), Color(0xFF223029))),
                )
                .border(1.dp, Color(0x0FFFFFFF), CircleShape),
        )
        Canvas(Modifier.matchParentSize()) {
            val centre = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2 - 6.dp.toPx()
            approaches.forEach { a ->
                val dx = (a.rightM / reach).coerceIn(-1.0, 1.0) * radius
                // Beyond the pin draws above it.
                val dy = -(a.alongM / reach).coerceIn(-1.0, 1.0) * radius
                val colour = when (a.result) {
                    Lie.Miss.GREEN -> C.Green
                    Lie.Miss.RIGHT -> C.Blue
                    Lie.Miss.LEFT -> C.Orange
                    Lie.Miss.SHORT -> C.Sand
                    Lie.Miss.BUNKER -> C.Sand
                    Lie.Miss.WATER -> C.Blue
                    else -> C.TextTertiary
                }
                drawCircle(colour, radius = 4.dp.toPx(), center = centre + Offset(dx.toFloat(), dy.toFloat()))
            }
            // The pin itself.
            drawCircle(C.TextPrimary, radius = 3.5.dp.toPx(), center = centre)
        }
    }
}

// --- Card D: this round --------------------------------------------------

@Composable
fun ThisRoundCard(
    shots: List<ShotEntity>,
    clubNames: Map<Long, String>,
    lieTransitions: List<String>,
    onSelectShot: (Int) -> Unit,
) {
    CaddieCard {
        CardHeader("This round") {
            Text(
                if (shots.size == 1) "1 shot" else "${shots.size} shots",
                style = T.metaSmall,
                color = C.TextSecondary,
            )
        }
        if (shots.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "No shots tracked on this hole. Tap the pencil to add one.",
                style = T.bodySmall,
                color = C.TextSecondary,
            )
            return@CaddieCard
        }
        shots.forEachIndexed { i, shot ->
            if (i > 0) RowDivider()
            ShotRow(
                index = i + 1,
                club = clubNames[shot.clubId] ?: if (shot.clubId == 0L) "Putt" else "Club ${shot.clubId}",
                transition = lieTransitions.getOrElse(i) { "" },
                distanceM = shot.distanceM,
                isPutt = shot.clubId == 0L,
                onClick = { onSelectShot(i) },
            )
        }
    }
}

@Composable
private fun ShotRow(
    index: Int,
    club: String,
    transition: String,
    distanceM: Double,
    isPutt: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(C.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text("$index", style = T.stat13.copy(fontWeight = FontWeight.SemiBold), color = C.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(club, style = T.rowTitle, color = C.TextPrimary, maxLines = 1)
            if (transition.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(transition, style = T.metaSmall, color = C.TextSecondary)
            }
        }
        val (value, unit) = shotDistance(distanceM, isPutt)
        ValueWithUnit(value, unit)
    }
}
