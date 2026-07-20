package dev.jbiffis.caddie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.HoleEntity

@Composable
fun ScorecardScreen(app: CaddieApp, roundId: Long, onOpenHole: (Int) -> Unit) {
    val round by app.db.dao().round(roundId).collectAsState(initial = null)
    val holes by app.db.dao().holes(roundId).collectAsState(initial = emptyList())
    val r = round ?: return

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(r.courseName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(
                            formatDate(r.startedAtS),
                            r.teeName?.let { "$it tees" },
                            r.slope?.let { "slope $it" },
                            r.rating?.let { "rating ${"%.1f".format(it)}" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Stat("Score", "${r.totalScore}")
                        Stat("To par", toParString(r.totalScore, r.totalPar))
                        // Front/Back only when both nines were played.
                        if (r.frontScore > 0 && r.backScore > 0) {
                            Stat("Front", "${r.frontScore}")
                            Stat("Back", "${r.backScore}")
                        }
                        r.totalPutts?.let { Stat("Putts", "$it") }
                    }
                }
            }
        }

        val front = holes.filter { it.hole <= 9 }
        val back = holes.filter { it.hole > 9 }
        item { HeaderRow() }
        if (front.isNotEmpty()) {
            items(front, key = { it.hole }) { HoleRow(it, onOpenHole) }
            item { TotalRow("OUT", r.frontPar, r.frontScore, front) }
        }
        if (back.isNotEmpty()) {
            items(back, key = { it.hole }) { HoleRow(it, onOpenHole) }
            item { TotalRow("IN", r.backPar, r.backScore, back) }
        }
        // Only show a combined total when both nines were played (otherwise OUT/IN is the total).
        if (front.isNotEmpty() && back.isNotEmpty()) {
            item { TotalRow("TOTAL", r.totalPar, r.totalScore, holes) }
        }
        item {
            Text(
                "Tap a hole to see the map and your shots.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Cell("HOLE", 1.2f, bold = true)
        Cell("YDS", 1f, bold = true)
        Cell("HCP", 0.8f, bold = true)
        Cell("PAR", 0.8f, bold = true)
        Cell("SCORE", 1.2f, bold = true)
        Cell("PUTTS", 1f, bold = true)
    }
    HorizontalDivider()
}

@Composable
private fun HoleRow(hole: HoleEntity, onOpenHole: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenHole(hole.hole) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell("${hole.hole}", 1.2f, bold = true)
        Cell(hole.lengthM?.let { "${it.toYards()}" } ?: "–", 1f)
        Cell(hole.strokeIndex?.toString() ?: "–", 0.8f)
        Cell("${hole.par}", 0.8f)
        Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) {
            val color = scoreColor(hole.strokes, hole.par)
            Box(
                Modifier.size(32.dp).background(color ?: Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (hole.strokes > 0) "${hole.strokes}" else "–",
                    fontWeight = FontWeight.Bold,
                    color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Cell(hole.putts?.toString() ?: "–", 1f)
    }
    HorizontalDivider()
}

@Composable
private fun TotalRow(label: String, par: Int, score: Int, holes: List<HoleEntity>) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp),
    ) {
        Cell(label, 1.2f, bold = true)
        Cell(holes.sumOf { it.lengthM ?: 0.0 }.takeIf { it > 0 }?.toYards()?.toString() ?: "–", 1f)
        Cell("", 0.8f)
        Cell("$par", 0.8f, bold = true)
        Cell("$score", 1.2f, bold = true)
        Cell(holes.mapNotNull { it.putts }.takeIf { it.isNotEmpty() }?.sum()?.toString() ?: "–", 1f)
    }
    HorizontalDivider()
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text,
        Modifier.weight(weight),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}
