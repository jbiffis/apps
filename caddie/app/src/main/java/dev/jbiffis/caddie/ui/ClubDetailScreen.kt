package dev.jbiffis.caddie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ClubStats
import dev.jbiffis.caddie.data.Lie
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.CardHeader
import dev.jbiffis.caddie.ui.design.InsetNote
import dev.jbiffis.caddie.ui.design.R as Radii
import dev.jbiffis.caddie.ui.design.RowDivider
import dev.jbiffis.caddie.ui.design.S
import dev.jbiffis.caddie.ui.design.SelectChip
import dev.jbiffis.caddie.ui.design.StatColumn
import dev.jbiffis.caddie.ui.design.T
import dev.jbiffis.caddie.ui.design.ValueWithUnit
import kotlin.math.roundToInt

/**
 * Everything known about one club, from the player's own shots.
 *
 * Reached either from the Bag (with a club already chosen) or from the tab bar, in
 * which case it opens on the longest club and the chip strip switches between them.
 */
@Composable
fun ClubDetailScreen(
    app: CaddieApp,
    clubId: Long?,
    onBack: (() -> Unit)? = null,
) {
    val shots by app.db.dao().allShots().collectAsState(initial = emptyList())
    val holes by app.db.dao().allHoles().collectAsState(initial = emptyList())
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val featureEntities by app.db.dao().allFeatures().collectAsState(initial = emptyList())

    val stats = remember(shots, holes, clubs, featureEntities) {
        val featuresByRound = featureEntities.groupBy({ it.roundId }, { it.decode() })
            .mapValues { (_, v) -> v.filterNotNull() }
        ClubStats.computeAll(shots, holes, clubs.associate { it.clubId to it.name }, featuresByRound)
    }

    var selected by remember(clubId) { mutableStateOf(clubId) }
    val club = stats.firstOrNull { it.clubId == selected } ?: stats.firstOrNull()

    if (club == null) {
        Column(
            Modifier.fillMaxSize().background(C.Canvas).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No club data yet", style = T.screenTitle, color = C.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Import a round with tracked shots to see distances, dispersion and shot history.",
                style = T.bodySmall,
                color = C.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().background(C.Canvas),
        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(S.gap),
    ) {
        if (onBack != null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(Radii.pill)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ChevronLeft, "Back", tint = C.TextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = S.gutter, end = S.gutter, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stats.forEach { s ->
                    SelectChip(
                        text = shortClubName(s.name, s.clubId),
                        selected = s.clubId == club.clubId,
                        onClick = { selected = s.clubId },
                    )
                }
            }
        }

        item { SummaryCard(club) }
        item { DispersionCard(club) }
        item { HistogramCard(club) }
        item { ShotHistoryCard(club) }
    }
}

@Composable
private fun SummaryCard(club: ClubStats) {
    CaddieCard(
        Modifier.padding(horizontal = S.gutter),
        padding = PaddingValues(S.cardWide),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(club.name, Modifier.weight(1f), style = T.cardTitle, color = C.TextPrimary)
            Text("${club.count} tracked shots", style = T.metaSmall, color = C.TextSecondary)
        }
        Spacer(Modifier.height(15.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatColumn(
                "${club.averageYd.roundToInt()}", "Average", Modifier.weight(1f),
                valueStyle = T.stat24, align = Alignment.CenterHorizontally, labelTop = 5.dp,
            )
            StatColumn(
                "${club.medianYd.roundToInt()}", "Median", Modifier.weight(1f),
                valueStyle = T.stat24, align = Alignment.CenterHorizontally, labelTop = 5.dp,
            )
            StatColumn(
                "${club.solidYd.roundToInt()}", "Solid", Modifier.weight(1f),
                valueStyle = T.stat24, valueColor = C.Green, labelColor = C.Green,
                align = Alignment.CenterHorizontally, labelTop = 5.dp,
            )
            StatColumn(
                "${club.longestYd.roundToInt()}", "Longest", Modifier.weight(1f),
                valueStyle = T.stat24, align = Alignment.CenterHorizontally, labelTop = 5.dp,
            )
        }
        Spacer(Modifier.height(14.dp))
        InsetNote {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = C.Green, fontWeight = FontWeight.Bold)) { append("Solid") }
                    append(
                        " = your better-half average — expect this from a well-struck one. " +
                            "Range ${club.shortestYd.roundToInt()}–${club.longestYd.roundToInt()} yd.",
                    )
                },
                style = T.meta,
                color = C.TextSecondary,
            )
        }
    }
}

@Composable
private fun DispersionCard(club: ClubStats) {
    val measured = club.shots.count { it.lateralM != null }
    CaddieCard(
        Modifier.padding(horizontal = S.gutter),
        padding = PaddingValues(S.cardWide),
    ) {
        CardHeader(if (club.drives > 0) "Off the tee" else "Dispersion")
        if (measured == 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Left and right are measured against the line to the pin — this club's " +
                    "shots are on holes with no pin position recorded.",
                style = T.bodySmall,
                color = C.TextSecondary,
            )
            return@CaddieCard
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatColumn(
                "${club.pct(club.lefts)}%", "Left", Modifier.weight(1f),
                valueColor = C.Orange, align = Alignment.CenterHorizontally,
            )
            StatColumn(
                "${club.pct(club.straight)}%", "Straight", Modifier.weight(1f),
                valueColor = C.Green, align = Alignment.CenterHorizontally,
            )
            StatColumn(
                "${club.pct(club.rights)}%", "Right", Modifier.weight(1f),
                valueColor = C.Blue, align = Alignment.CenterHorizontally,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)),
        ) {
            Segment(club.lefts, club.count, C.Orange)
            Segment(club.straight, club.count, C.Green)
            Segment(club.rights, club.count, C.Blue)
        }

        // Driving accuracy against the real fairway polygons, where OSM has them.
        if (club.drives > 0) {
            Spacer(Modifier.height(14.dp))
            InsetNote {
                val misses = club.driving.entries
                    .filter { it.key != Lie.Miss.FAIRWAY && it.key != Lie.Miss.GREEN }
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { "${it.value}× ${it.key.label.lowercase()}" }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = C.TextPrimary, fontWeight = FontWeight.Bold)) {
                            append("${100 * club.fairwaysHit / club.drives}% fairways")
                        }
                        append(" from ${club.drives} tee shot${if (club.drives == 1) "" else "s"}")
                        if (misses.isNotEmpty()) append(" — $misses")
                    },
                    style = T.meta,
                    color = C.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Segment(n: Int, total: Int, color: Color) {
    if (n <= 0 || total <= 0) return
    Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().background(color))
}

@Composable
private fun HistogramCard(club: ClubStats) {
    val histogram = remember(club) { club.histogram() }
    val medianBin = histogram.binOf(club.medianYd)
    val solidBin = histogram.binOf(club.solidYd)

    CaddieCard(
        Modifier.padding(horizontal = S.gutter),
        padding = PaddingValues(S.cardWide),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text("Distance histogram", Modifier.weight(1f), style = T.cardTitle, color = C.TextPrimary)
            Text(
                buildAnnotatedString {
                    append("Median ${club.medianYd.roundToInt()} · ")
                    withStyle(SpanStyle(color = C.Green)) { append("Solid ${club.solidYd.roundToInt()}") }
                },
                style = T.micro,
                color = C.TextSecondary,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(118.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            histogram.counts.forEachIndexed { i, count ->
                Box(
                    Modifier
                        .weight(1f)
                        // A bucket with shots in it never collapses to nothing.
                        .fillMaxHeight((count.toFloat() / histogram.max).coerceAtLeast(if (count > 0) 0.04f else 0.008f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(
                            when (i) {
                                solidBin -> C.Green
                                medianBin -> C.Blue
                                else -> C.GreenLight.copy(alpha = 0.4f)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${histogram.lowYd.roundToInt()}", style = T.mono11, color = C.TextSecondary)
            Text(
                "${((histogram.lowYd + histogram.highYd) / 2).roundToInt()}",
                style = T.mono11,
                color = C.TextSecondary,
            )
            Text("${histogram.highYd.roundToInt()} yd", style = T.mono11, color = C.TextSecondary)
        }
    }
}

@Composable
private fun ShotHistoryCard(club: ClubStats) {
    val recent = remember(club) { club.shots.sortedByDescending { it.shot.timeS }.take(12) }
    CaddieCard(
        Modifier.padding(horizontal = S.gutter),
        padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shot history", Modifier.weight(1f), style = T.cardTitle, color = C.TextPrimary)
            Text("Recent", style = T.metaSmall.copy(fontWeight = FontWeight.SemiBold), color = C.Green)
        }
        recent.forEach { s ->
            RowDivider(color = Color(0x0FFFFFFF))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ValueWithUnit("${s.yards.roundToInt()}", "yd", valueStyle = T.stat20)
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(
                        formatDayDate(s.shot.timeS),
                        formatWind(s.shot.windSpeedKmh, s.shot.windDirDeg),
                    ).joinToString(" · "),
                    style = T.meta,
                    color = C.TextSecondary,
                )
            }
        }
    }
}

/** "Driver (10.5°)" -> "Driver" — the chip strip has no room for the loft. */
private fun shortClubName(name: String, clubId: Long): String =
    if (clubId == 0L) "Putter" else name.substringBefore(" (").trim().ifEmpty { name }
