package dev.jbiffis.caddie.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ShotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Esri World Imagery — free satellite tiles, no API key. */
private val SatelliteTiles = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Tiles © Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

@Composable
fun HoleScreen(app: CaddieApp, roundId: Long, hole: Int, onNavigateHole: (Int) -> Unit) {
    val dao = app.db.dao()
    val round by dao.round(roundId).collectAsState(initial = null)
    val holes by dao.holes(roundId).collectAsState(initial = emptyList())
    val allShots by dao.shots(roundId).collectAsState(initial = emptyList())
    val clubs by dao.clubs().collectAsState(initial = emptyList())

    val holeInfo = holes.firstOrNull { it.hole == hole }
    val shots = allShots.filter { it.hole == hole }
    val clubNames = clubs.associate { it.clubId to it.name }

    // Walked GPS track for this hole: between the previous hole's finish and this hole's finish
    val track by produceState(initialValue = emptyList<dev.jbiffis.caddie.data.TrackPointEntity>(), holeInfo, holes, round) {
        val r = round ?: return@produceState
        val h = holeInfo ?: return@produceState
        val fromS = holes.firstOrNull { it.hole == hole - 1 }?.finishedAtS ?: r.startedAtS
        val toS = h.finishedAtS ?: (fromS + 3600)
        value = withContext(Dispatchers.IO) { dao.trackBetween(roundId, fromS, toS) }
    }

    Column(Modifier.fillMaxSize()) {
        // Header with prev/next hole navigation
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (hole > 1) onNavigateHole(hole - 1) }, enabled = hole > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous hole")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hole $hole", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                holeInfo?.let { h ->
                    Text(
                        listOfNotNull(
                            "Par ${h.par}",
                            h.lengthM?.let { "${it.toYards()} yds" },
                            h.strokeIndex?.let { "HCP $it" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { if (hole < 18) onNavigateHole(hole + 1) }, enabled = hole < holes.size) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next hole")
            }
        }
        holeInfo?.let { h ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    "Score ${h.strokes} (${toParString(h.strokes, h.par)})" +
                        (h.putts?.let { "  ·  $it putts" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    color = scoreColor(h.strokes, h.par) ?: MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Map
        val mapView = remember { mutableStateOf<MapView?>(null) }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(SatelliteTiles)
                    setMultiTouchControls(true)
                    overlays.add(CopyrightOverlay(ctx))
                    mapView.value = this
                }
            },
            update = { map ->
                map.overlays.removeAll { it !is CopyrightOverlay }

                // Walked path (subtle)
                if (track.size > 1) {
                    map.overlays.add(Polyline(map).apply {
                        setPoints(track.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = 0x66FFFFFF
                        outlinePaint.strokeWidth = 4f
                    })
                }

                // Shots
                shots.forEachIndexed { i, shot ->
                    val isPutt = shot.clubId == 0L
                    map.overlays.add(Polyline(map).apply {
                        setPoints(listOf(GeoPoint(shot.startLat, shot.startLon), GeoPoint(shot.endLat, shot.endLon)))
                        outlinePaint.color = if (isPutt) 0xFF90CAF9.toInt() else 0xFFFFD54F.toInt()
                        outlinePaint.strokeWidth = 6f
                    })
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(shot.startLat, shot.startLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = BitmapDrawable(map.resources, numberedDot(map, i + 1, isPutt))
                        title = "Shot ${i + 1}: ${clubNames[shot.clubId] ?: if (isPutt) "Putt / no club" else "Club ${shot.clubId}"}"
                        snippet = "${shot.distanceM.toYards()} yds"
                    })
                }

                // Pin
                holeInfo?.let { h ->
                    if (h.pinLat != null && h.pinLon != null) {
                        map.overlays.add(Marker(map).apply {
                            position = GeoPoint(h.pinLat, h.pinLon)
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = "Pin"
                        })
                    }
                }

                // Zoom to fit everything on this hole
                val points = buildList {
                    shots.forEach {
                        add(GeoPoint(it.startLat, it.startLon)); add(GeoPoint(it.endLat, it.endLon))
                    }
                    holeInfo?.let { h ->
                        if (h.pinLat != null && h.pinLon != null) add(GeoPoint(h.pinLat, h.pinLon))
                    }
                    if (isEmpty()) track.forEach { add(GeoPoint(it.lat, it.lon)) }
                }
                if (points.isNotEmpty()) {
                    val box = BoundingBox.fromGeoPointsSafe(points)
                    map.post { map.zoomToBoundingBox(box.increaseByScale(1.35f), false) }
                }
                map.invalidate()
            },
        )

        // Shot list
        LazyColumn(Modifier.fillMaxWidth().height(140.dp)) {
            items(shots.size) { i ->
                val shot = shots[i]
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Text("${i + 1}.", Modifier.padding(end = 8.dp), fontWeight = FontWeight.Bold)
                    Text(
                        clubNames[shot.clubId] ?: if (shot.clubId == 0L) "Putt / no club" else "Club ${shot.clubId}",
                        Modifier.weight(1f),
                    )
                    Text("${shot.distanceM.toYards()} yds", fontWeight = FontWeight.Bold)
                }
            }
            if (shots.isEmpty()) {
                item {
                    Text(
                        "No tracked shots on this hole.",
                        Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Small numbered circle bitmap used as a shot marker. */
private fun numberedDot(map: MapView, number: Int, isPutt: Boolean): Bitmap {
    val d = (22 * map.resources.displayMetrics.density).toInt()
    val bmp = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = if (isPutt) 0xFF1976D2.toInt() else 0xFFF57F17.toInt()
    canvas.drawCircle(d / 2f, d / 2f, d / 2f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = d * 0.55f
    paint.textAlign = Paint.Align.CENTER
    paint.isFakeBoldText = true
    val y = d / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText("$number", d / 2f, y, paint)
    return bmp
}
