package dev.jbiffis.caddie.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.jbiffis.caddie.data.HoleEntity
import dev.jbiffis.caddie.data.Lie
import dev.jbiffis.caddie.data.ShotEntity
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
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
    greens: List<List<Pair<Double, Double>>> = emptyList(),
    onSelectShot: (Int) -> Unit = {},
) {
    // Only auto-fit the view when the hole or shot count changes — not on every
    // recomposition — so the user's own pan/zoom sticks while they tap shots.
    val framed = remember { mutableStateOf(-1) }
    val frameSig = (holeInfo?.hole ?: 0) * 100 + shots.size
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

            // Garmin CourseView green/hole outlines (from .DAT), drawn under the shots.
            greens.forEach { poly ->
                if (poly.size >= 3) map.overlays.add(Polygon(map).apply {
                    points = poly.map { GeoPoint(it.first, it.second) }
                    fillPaint.color = 0x55A9D468
                    outlinePaint.color = 0xFF66BB6A.toInt()
                    outlinePaint.strokeWidth = 3f
                })
            }

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
                        icon = BitmapDrawable(map.resources, flagPinBmp(map))
                        setAnchor(FLAG_ANCHOR_X, Marker.ANCHOR_BOTTOM)
                        title = "Pin"
                    })
                }
            }

            // Orient the hole like Garmin: tee at the bottom, green at the top. Rotate
            // the map so the tee→green bearing points straight up.
            val teeLat = shots.firstOrNull()?.startLat
            val teeLon = shots.firstOrNull()?.startLon
            val greenLat = holeInfo?.pinLat ?: shots.lastOrNull()?.endLat
            val greenLon = holeInfo?.pinLon ?: shots.lastOrNull()?.endLon
            if (teeLat != null && teeLon != null && greenLat != null && greenLon != null) {
                val bearing = Lie.bearingDeg(teeLat, teeLon, greenLat, greenLon)
                map.mapOrientation = -bearing.toFloat() // osmdroid: negative bearing = that heading points up
            }

            val points = buildList {
                shots.forEach {
                    add(GeoPoint(it.startLat, it.startLon)); add(GeoPoint(it.endLat, it.endLon))
                }
                holeInfo?.let { h -> if (h.pinLat != null && h.pinLon != null) add(GeoPoint(h.pinLat, h.pinLon)) }
                greens.forEach { poly -> poly.forEach { add(GeoPoint(it.first, it.second)) } }
            }
            if (points.isNotEmpty() && framed.value != frameSig) {
                framed.value = frameSig
                val box = BoundingBox.fromGeoPointsSafe(points)
                map.post { map.zoomToBoundingBox(box.increaseByScale(1.4f), false) }
            }
            map.invalidate()
        },
    )
}

// Horizontal anchor of the flag bitmap: the pole sits at 30% of the width.
internal const val FLAG_ANCHOR_X = 0.3f

/** Golf-flag bitmap for the pin: red pennant on a dark pole with a white base dot. */
internal fun flagPinBmp(map: MapView): Bitmap {
    val s = map.resources.displayMetrics.density
    val w = (20 * s).toInt()
    val h = (28 * s).toInt()
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val poleX = w * FLAG_ANCHOR_X
    val baseY = h - 3 * s
    // base dot
    p.color = android.graphics.Color.WHITE
    c.drawCircle(poleX, baseY, 4 * s, p)
    p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * s; p.color = 0x55000000
    c.drawCircle(poleX, baseY, 4 * s, p)
    p.style = Paint.Style.FILL
    // pole
    p.color = 0xFF303030.toInt()
    c.drawRect(poleX - 0.9f * s, 2 * s, poleX + 0.9f * s, baseY, p)
    // pennant
    p.color = 0xFFD32F2F.toInt()
    c.drawPath(android.graphics.Path().apply {
        moveTo(poleX, 2 * s); lineTo(poleX + 12 * s, 6 * s); lineTo(poleX, 10 * s); close()
    }, p)
    return bmp
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
