package dev.jbiffis.caddie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.HoleEntity
import dev.jbiffis.caddie.data.Repository
import dev.jbiffis.caddie.data.ShotEntity
import kotlin.math.abs

private class ShotSample(
    val distanceM: Double,
    val lateralM: Double?,  // + right / - left of the start→pin line
    val depthM: Double?,    // + long / - short (approach shots only)
)

private class ClubStat(
    val clubId: Long,
    val name: String,
    val samples: List<ShotSample>,
    /** Tee shots on par 4/5 holes, classified against the actual fairway polygons. */
    val driving: Map<dev.jbiffis.caddie.data.Lie.Miss, Int> = emptyMap(),
) {
    val count get() = samples.size
    val avgYd get() = samples.map { it.distanceM }.average() * M_TO_YD
    val maxYd get() = (samples.maxOfOrNull { it.distanceM } ?: 0.0) * M_TO_YD
    val medianYd: Double get() {
        val sorted = samples.map { it.distanceM }.sorted()
        return sorted[sorted.size / 2] * M_TO_YD
    }
    val lefts get() = samples.count { (it.lateralM ?: 0.0) < -LATERAL_TOLERANCE_M }
    val rights get() = samples.count { (it.lateralM ?: 0.0) > LATERAL_TOLERANCE_M }
    val straight get() = count - lefts - rights

    companion object { const val LATERAL_TOLERANCE_M = 8.0 }
}

private const val MIN_SHOT_M = 15.0       // ignore chips/mis-detections in stats
private const val APPROACH_MAX_M = 210.0  // start→pin distance where aiming at the pin is plausible

@Composable
fun StatsScreen(app: CaddieApp) {
    val shots by app.db.dao().allShots().collectAsState(initial = emptyList())
    val holes by app.db.dao().allHoles().collectAsState(initial = emptyList())
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val featureEntities by app.db.dao().allFeatures().collectAsState(initial = emptyList())

    val stats = remember(shots, holes, clubs, featureEntities) {
        val featuresByRound = featureEntities.groupBy({ it.roundId }, { it.decode() })
            .mapValues { (_, v) -> v.filterNotNull() }
        computeStats(shots, holes, clubs.associate { it.clubId to it.name }, featuresByRound)
    }

    if (stats.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No club data yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Import a round with tracked shots to see per-club distances and misses.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        items(stats, key = { it.clubId }) { stat -> ClubCard(stat) }
        item {
            Text(
                "Distances use every tracked shot ≥ ${MIN_SHOT_M.toInt()} m with a club. " +
                    "Left/right miss is measured against the line from the shot to the pin. " +
                    "Rename clubs in the Bag tab.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

private fun computeStats(
    shots: List<ShotEntity>,
    holes: List<HoleEntity>,
    clubNames: Map<Long, String>,
    featuresByRound: Map<Long, List<dev.jbiffis.caddie.data.CourseFeature>>,
): List<ClubStat> {
    val pinByRoundHole = holes.associateBy({ it.roundId to it.hole }, { it })
    val samplesByClub = HashMap<Long, MutableList<ShotSample>>()
    val drivingByClub = HashMap<Long, MutableMap<dev.jbiffis.caddie.data.Lie.Miss, Int>>()

    // First tracked shot on each hole = the tee shot
    val teeShotIds = shots.groupBy { it.roundId to it.hole }
        .mapNotNull { (_, holeShots) -> holeShots.minByOrNull { it.timeS }?.id }
        .toHashSet()

    for (shot in shots) {
        if (shot.clubId == 0L || shot.distanceM < MIN_SHOT_M) continue
        val hole = pinByRoundHole[shot.roundId to shot.hole]
        var lateral: Double? = null
        var depth: Double? = null
        if (hole?.pinLat != null && hole.pinLon != null) {
            val toPin = dev.jbiffis.caddie.fit.GolfFit.haversineM(shot.startLat, shot.startLon, hole.pinLat, hole.pinLon)
            lateral = Repository.lateralMissM(
                shot.startLat, shot.startLon, shot.endLat, shot.endLon, hole.pinLat, hole.pinLon,
            )
            if (toPin <= APPROACH_MAX_M) {
                depth = Repository.depthMissM(
                    shot.startLat, shot.startLon, shot.endLat, shot.endLon, hole.pinLat, hole.pinLon,
                )
            }
            // Driving accuracy vs the mapped fairway (par 4/5 tee shots only)
            val features = featuresByRound[shot.roundId].orEmpty()
            if (shot.id in teeShotIds && (hole.par >= 4) && features.isNotEmpty()) {
                val miss = dev.jbiffis.caddie.data.Lie.classifyMiss(
                    shot.startLat, shot.startLon, shot.endLat, shot.endLon,
                    hole.pinLat, hole.pinLon, features,
                )
                val bucket = drivingByClub.getOrPut(shot.clubId) { HashMap() }
                bucket[miss] = (bucket[miss] ?: 0) + 1
            }
        }
        samplesByClub.getOrPut(shot.clubId) { ArrayList() }
            .add(ShotSample(shot.distanceM, lateral, depth))
    }
    return samplesByClub.map { (clubId, samples) ->
        ClubStat(clubId, clubNames[clubId] ?: "Club $clubId", samples, drivingByClub[clubId] ?: emptyMap())
    }.sortedByDescending { it.avgYd }
}

@Composable
private fun ClubCard(stat: ClubStat) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stat.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${stat.count} shots", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCol("Avg", "${stat.avgYd.toInt()} yd")
                StatCol("Median", "${stat.medianYd.toInt()} yd")
                StatCol("Longest", "${stat.maxYd.toInt()} yd")
            }
            // Miss direction bar
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                MissCol("Left", stat.lefts, stat.count, Color(0xFFE57373))
                MissCol("Straight", stat.straight, stat.count, Color(0xFF81C784))
                MissCol("Right", stat.rights, stat.count, Color(0xFF64B5F6))
            }
            // Driving accuracy from the actual fairway polygons (OpenStreetMap)
            val drives = stat.driving.values.sum()
            if (drives > 0) {
                val fw = stat.driving.filterKeys {
                    it == dev.jbiffis.caddie.data.Lie.Miss.FAIRWAY || it == dev.jbiffis.caddie.data.Lie.Miss.GREEN
                }.values.sum()
                val parts = stat.driving.entries
                    .filter { it.key != dev.jbiffis.caddie.data.Lie.Miss.FAIRWAY && it.key != dev.jbiffis.caddie.data.Lie.Miss.GREEN }
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { "${it.value}× ${it.key.label.lowercase()}" }
                Text(
                    "Off the tee: ${100 * fw / drives}% fairway ($fw/$drives)" +
                        (if (parts.isNotEmpty()) " — $parts" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            val depthSamples = stat.samples.mapNotNull { it.depthM }
            if (depthSamples.isNotEmpty()) {
                val short = depthSamples.count { it < -8 }
                val long = depthSamples.count { it > 8 }
                Text(
                    "Approach shots: ${depthSamples.size} at the pin — " +
                        "$short short, ${depthSamples.size - short - long} pin high, $long long",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            DispersionPlot(stat)
        }
    }
}

@Composable
private fun StatCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MissCol(label: String, n: Int, total: Int, color: Color) {
    val pct = if (total > 0) (100.0 * n / total).toInt() else 0
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$pct%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Down-range scatter: x = lateral miss (left/right), y = carry distance. */
@Composable
private fun DispersionPlot(stat: ClubStat) {
    val withLateral = stat.samples.filter { it.lateralM != null }
    if (withLateral.size < 3) return
    val maxDist = withLateral.maxOf { it.distanceM } * 1.1
    val maxLat = maxOf(withLateral.maxOf { abs(it.lateralM!!) } * 1.2, 15.0)
    val dotColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.outline

    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp)) {
        val w = size.width
        val h = size.height
        // Center line = target line, shots fan out from the bottom center
        drawLine(lineColor, Offset(w / 2, 0f), Offset(w / 2, h), strokeWidth = 2f)
        for (s in withLateral) {
            val x = (w / 2 + (s.lateralM!! / maxLat) * (w / 2)).toFloat().coerceIn(0f, w)
            val y = (h - (s.distanceM / maxDist) * h).toFloat().coerceIn(0f, h)
            drawCircle(dotColor, radius = 5f, center = Offset(x, y))
        }
        drawCircle(lineColor, radius = 7f, center = Offset(w / 2, h), style = Stroke(width = 3f))
    }
}
