package dev.jbiffis.caddie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ShotEntity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MIN_CLUB_SHOT_M = 15.0

@Composable
fun ClubDetailScreen(app: CaddieApp, clubId: Long, onBack: () -> Unit) {
    val allShots by app.db.dao().allShots().collectAsState(initial = emptyList())
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val name = clubs.firstOrNull { it.clubId == clubId }?.name ?: "Club"

    val shots = remember(allShots, clubId) {
        allShots.filter { it.clubId == clubId && it.distanceM >= MIN_CLUB_SHOT_M }
    }
    val yards = remember(shots) { shots.map { it.distanceM * M_TO_YD }.sorted() }

    var sortByDistance by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${shots.size} tracked shots", style = MaterialTheme.typography.bodySmall)
            }
        }
        HorizontalDivider()

        if (yards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tracked shots for this club yet.", style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        val mean = yards.average()
        val median = yards[yards.size / 2]
        // "Solid" = average of the upper half — what a well-struck shot looks like,
        // dropping the mishit/short outliers.
        val upper = yards.filter { it >= median }
        val solid = if (upper.isNotEmpty()) upper.average() else mean

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            BigStat("Average", mean.roundToInt())
                            BigStat("Median", median.roundToInt())
                            BigStat("Solid", solid.roundToInt(), highlight = true)
                        }
                        Text(
                            "Solid = average of your better half of shots — the distance to " +
                                "expect from a well-struck one. Range ${yards.first().roundToInt()}–${yards.last().roundToInt()} yds.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Distance histogram", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Histogram(yards, solid)
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Shot history", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (sortByDistance) "Sort: distance" else "Sort: recent",
                        Modifier.clickable { sortByDistance = !sortByDistance }.padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            val history = if (sortByDistance) shots.sortedByDescending { it.distanceM }
            else shots.sortedByDescending { it.timeS }
            items(history, key = { it.id }) { shot -> ShotHistoryRow(shot) }
        }
    }
}

@Composable
private fun BigStat(label: String, yards: Int, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$yards",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text("$label · yds", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ShotHistoryRow(shot: ShotEntity) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(shot.distanceM * M_TO_YD).roundToInt()}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(" yds", style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            Text(formatDate(shot.timeS), style = MaterialTheme.typography.bodyMedium)
            shot.windSpeedKmh?.let {
                Text("  ·  ${formatWind(it, shot.windDirDeg)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** Distance histogram: 10-yard bins, with a marker at the "solid" distance. */
@Composable
private fun Histogram(yards: List<Double>, solidMark: Double) {
    val bin = 10.0
    val lo = floor(yards.first() / bin) * bin
    val hi = ceil(yards.last() / bin) * bin
    val bins = ((hi - lo) / bin).toInt().coerceAtLeast(1)
    val counts = IntArray(bins)
    for (y in yards) {
        val idx = (((y - lo) / bin).toInt()).coerceIn(0, bins - 1)
        counts[idx]++
    }
    val maxCount = (counts.maxOrNull() ?: 1).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val markColor = Color(0xFFFF7043)

    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(top = 12.dp)) {
            val w = size.width
            val h = size.height - 4f
            val bw = w / bins
            for (i in 0 until bins) {
                val bh = (counts[i].toFloat() / maxCount) * (h - 8f)
                if (counts[i] > 0) {
                    drawRect(
                        barColor,
                        topLeft = Offset(i * bw + 3f, h - bh),
                        size = Size(bw - 6f, bh),
                    )
                }
            }
            drawLine(axisColor, Offset(0f, h), Offset(w, h), strokeWidth = 2f)
            // Marker at the "solid" distance
            val mx = (((solidMark - lo) / (hi - lo)) * w).toFloat().coerceIn(0f, w)
            drawLine(markColor, Offset(mx, 0f), Offset(mx, h), strokeWidth = 4f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${lo.roundToInt()}", style = MaterialTheme.typography.labelSmall)
            Text("yards", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("${hi.roundToInt()}", style = MaterialTheme.typography.labelSmall)
        }
    }
}
