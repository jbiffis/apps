package dev.jbiffis.caddie.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jbiffis.caddie.data.HoleEntity
import dev.jbiffis.caddie.data.ShotEntity
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Esri World Imagery — free satellite tiles, no API key. */
val EsriSatelliteTiles: OnlineTileSourceBase = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 20, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Tiles © Esri",
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex)
}

/**
 * Satellite basemap for a single hole with the shots and pin overlaid. Works for
 * ANY course (aerial imagery covers everywhere), so it's the fallback when a
 * course has no OpenStreetMap golf geometry to draw. Tapping a shot marker
 * selects it via [onSelectShot].
 */
@Composable
fun SatelliteHoleMap(
    shots: List<ShotEntity>,
    holeInfo: HoleEntity?,
    clubNames: Map<Long, String>,
    modifier: Modifier = Modifier,
    onSelectShot: (Int) -> Unit = {},
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(EsriSatelliteTiles)
                setMultiTouchControls(true)
                overlays.add(CopyrightOverlay(ctx))
            }
        },
        update = { map ->
            map.overlays.removeAll { it !is CopyrightOverlay }

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
                    icon = BitmapDrawable(map.resources, numberedDotBmp(map, i + 1, isPutt))
                    title = "Shot ${i + 1}: ${clubNames[shot.clubId] ?: if (isPutt) "Putt / no club" else "Club ${shot.clubId}"}"
                    snippet = "${shot.distanceM.toYards()} yds"
                    setOnMarkerClickListener { _, _ -> onSelectShot(i); true }
                })
            }

            holeInfo?.let { h ->
                if (h.pinLat != null && h.pinLon != null) {
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(h.pinLat, h.pinLon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Pin"
                    })
                }
            }

            val points = buildList {
                shots.forEach {
                    add(GeoPoint(it.startLat, it.startLon)); add(GeoPoint(it.endLat, it.endLon))
                }
                holeInfo?.let { h -> if (h.pinLat != null && h.pinLon != null) add(GeoPoint(h.pinLat, h.pinLon)) }
            }
            if (points.isNotEmpty()) {
                val box = BoundingBox.fromGeoPointsSafe(points)
                map.post { map.zoomToBoundingBox(box.increaseByScale(1.4f), false) }
            }
            map.invalidate()
        },
    )
}

/** Small numbered circle bitmap used as a shot marker. */
private fun numberedDotBmp(map: MapView, number: Int, isPutt: Boolean): Bitmap {
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
