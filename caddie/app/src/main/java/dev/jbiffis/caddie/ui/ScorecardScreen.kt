package dev.jbiffis.caddie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.HoleEntity
import dev.jbiffis.caddie.data.RoundEntity
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.Footnote
import dev.jbiffis.caddie.ui.design.RowDivider
import dev.jbiffis.caddie.ui.design.S
import dev.jbiffis.caddie.ui.design.ScoreBadge
import dev.jbiffis.caddie.ui.design.ScreenHeader
import dev.jbiffis.caddie.ui.design.StatTile
import dev.jbiffis.caddie.ui.design.T

/**
 * The round at a glance, and the way in to any hole's map. Scores are colour-coded
 * against par so the shape of the round is visible before a single number is read.
 */
@Composable
fun ScorecardScreen(app: CaddieApp, roundId: Long, onOpenHole: (Int) -> Unit) {
    val round by app.db.dao().round(roundId).collectAsState(initial = null)
    val holes by app.db.dao().holes(roundId).collectAsState(initial = emptyList())
    val r = round ?: return

    val front = holes.filter { it.hole <= 9 }
    val back = holes.filter { it.hole > 9 }

    LazyColumn(
        Modifier.fillMaxSize().background(C.Canvas),
        contentPadding = PaddingValues(start = S.gutter, end = S.gutter, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(S.gap),
    ) {
        item { ScreenHeader(r.courseName) }

        item { RoundSummary(r, holes) }

        if (front.isNotEmpty()) {
            item {
                HoleTable(
                    holes = front,
                    label = "OUT",
                    par = r.frontPar,
                    score = r.frontScore,
                    onOpenHole = onOpenHole,
                )
            }
        }
        if (back.isNotEmpty()) {
            item {
                HoleTable(
                    holes = back,
                    label = "IN",
                    par = r.backPar,
                    score = r.backScore,
                    onOpenHole = onOpenHole,
                )
            }
        }

        item {
            Footnote(
                when {
                    front.isNotEmpty() && back.isEmpty() -> "Back nine — not yet played"
                    holes.isEmpty() -> "No holes recorded in this round"
                    else -> "Tap a hole to see the map and your shots"
                },
                Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun RoundSummary(r: RoundEntity, holes: List<HoleEntity>) {
    CaddieCard {
        Text(
            listOfNotNull(
                formatDate(r.startedAtS),
                r.teeName?.let { "$it tees" },
                r.slope?.let { "slope $it" },
                r.rating?.let { "rating ${"%.1f".format(it)}" },
            ).joinToString(" · "),
            style = T.meta,
            color = C.TextSecondary,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("${r.totalScore}", "Score", Modifier.weight(1f))
            StatTile(
                toParString(r.totalScore, r.totalPar),
                "To par",
                Modifier.weight(1f),
                valueColor = when {
                    r.totalScore == 0 -> C.TextSecondary
                    r.totalScore > r.totalPar -> C.Orange
                    r.totalScore < r.totalPar -> C.Green
                    else -> C.TextPrimary
                },
            )
            // "Thru" is the honest count of holes actually scored, not the card size.
            StatTile(
                "${holes.count { it.strokes > 0 }}",
                "Thru",
                Modifier.weight(1f),
                valueColor = C.Blue,
            )
        }
    }
}

@Composable
private fun HoleTable(
    holes: List<HoleEntity>,
    label: String,
    par: Int,
    score: Int,
    onOpenHole: (Int) -> Unit,
) {
    CaddieCard(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCell("HOLE", Modifier.width(30.dp))
            HeaderCell("YARDS", Modifier.weight(1f).padding(start = 10.dp))
            HeaderCell("PAR", Modifier.width(44.dp), TextAlign.Center)
            HeaderCell("SCORE", Modifier.width(50.dp), TextAlign.Center)
            Spacer(Modifier.width(16.dp))
        }
        holes.forEach { hole ->
            HoleRow(hole, onOpenHole)
        }
        Spacer(Modifier.height(12.dp))
        RowDivider(color = C.HairlineStrong)
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.width(30.dp), style = T.overline, color = C.TextSecondary)
            Text(
                holes.sumOf { it.lengthM ?: 0.0 }.takeIf { it > 0 }?.let { "${it.toYards()} yd" } ?: "–",
                Modifier.weight(1f).padding(start = 10.dp),
                style = T.body,
                color = C.TextSecondary,
            )
            Text("$par", Modifier.width(44.dp), style = T.body, color = C.TextSecondary, textAlign = TextAlign.Center)
            Text("$score", Modifier.width(50.dp), style = T.stat15, color = C.TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.width(16.dp))
        }
    }
}

@Composable
private fun HoleRow(hole: HoleEntity, onOpenHole: (Int) -> Unit) {
    RowDivider(color = C.Hairline)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenHole(hole.hole) }
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${hole.hole}", Modifier.width(30.dp), style = T.stat15, color = C.TextPrimary)
        Text(
            hole.lengthM?.let { "${it.toYards()} yd" } ?: "–",
            Modifier.weight(1f).padding(start = 10.dp),
            style = T.body,
            color = C.TextSecondary,
        )
        Text("${hole.par}", Modifier.width(44.dp), style = T.body, color = C.TextSecondary, textAlign = TextAlign.Center)
        Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) {
            val (bg, fg) = scoreBadgeColors(hole.strokes, hole.par)
            ScoreBadge(if (hole.strokes > 0) "${hole.strokes}" else "–", bg, fg)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = C.TextTertiary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, modifier, style = T.overline, color = C.TextSecondary, textAlign = align)
}
