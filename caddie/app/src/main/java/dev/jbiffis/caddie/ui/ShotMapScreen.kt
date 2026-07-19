package dev.jbiffis.caddie.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.CourseFeature
import dev.jbiffis.caddie.data.Lie
import dev.jbiffis.caddie.data.LocalFrame
import dev.jbiffis.caddie.data.ShotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

// Garmin-style flat course colours
private val RoughColor = Color(0xFF6E9E43)
private val FairwayColor = Color(0xFF90BF57)
private val GreenColor = Color(0xFFA9D468)
private val TeeColor = Color(0xFF9CCB61)
private val BunkerColor = Color(0xFFE7DBA8)
private val WaterColor = Color(0xFF64B5F6)
private val WoodsColor = Color(0xFF4A7A33)
private val ExplicitRoughColor = Color(0xFF7FAB4C)

@Composable
fun ShotMapScreen(
    app: CaddieApp,
    roundId: Long,
    hole: Int,
    initialShot: Int,
    onNavigateHole: (Int) -> Unit,
    onOpenSatellite: (Int) -> Unit,
) {
    val dao = app.db.dao()
    val round by dao.round(roundId).collectAsState(initial = null)
    val holes by dao.holes(roundId).collectAsState(initial = emptyList())
    val allShots by dao.shots(roundId).collectAsState(initial = emptyList())
    val clubs by dao.clubs().collectAsState(initial = emptyList())
    val featureEntities by dao.features(roundId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val holeInfo = holes.firstOrNull { it.hole == hole }
    val shots = allShots.filter { it.hole == hole }
    val features = remember(featureEntities) { featureEntities.mapNotNull { it.decode() } }
    val clubNames = clubs.associate { it.clubId to it.name }

    var shotIdx by rememberSaveable(hole) { mutableIntStateOf(initialShot) }
    if (shots.isNotEmpty()) shotIdx = shotIdx.coerceIn(0, shots.size - 1)
    val current = shots.getOrNull(shotIdx)

    var confirmDelete by remember { mutableStateOf(false) }
    var editClub by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    var fetchState by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }

    fun recomputeDistance(s: ShotEntity) =
        s.copy(distanceM = dev.jbiffis.caddie.fit.GolfFit.haversineM(s.startLat, s.startLon, s.endLat, s.endLon))

    // Drag a chain node: node k is shots[k-1].end and/or shots[k].start.
    fun moveNode(nodeIndex: Int, lat: Double, lon: Double) {
        scope.launch {
            val before = shots.getOrNull(nodeIndex - 1)
            val after = shots.getOrNull(nodeIndex)
            before?.let { dao.updateShot(recomputeDistance(it.copy(endLat = lat, endLon = lon))) }
            after?.let { dao.updateShot(recomputeDistance(it.copy(startLat = lat, startLon = lon))) }
        }
    }

    fun addShot() {
        scope.launch {
            val last = shots.lastOrNull()
            // New shot starts where the last one finished (or the pin, or hole centre).
            val startLat = last?.endLat ?: holeInfo?.pinLat ?: features.firstOrNull()?.points?.firstOrNull()?.first
            val startLon = last?.endLon ?: holeInfo?.pinLon ?: features.firstOrNull()?.points?.firstOrNull()?.second
            if (startLat == null || startLon == null) return@launch
            // End a short way toward the pin so the new shot is visible and draggable.
            val endLat = holeInfo?.pinLat?.takeIf { it != startLat } ?: (startLat + 0.0002)
            val endLon = holeInfo?.pinLon?.takeIf { it != startLon } ?: startLon
            val newShot = ShotEntity(
                roundId = roundId,
                hole = hole,
                timeS = (last?.timeS ?: holeInfo?.finishedAtS ?: 0L) + 1,
                startLat = startLat, startLon = startLon,
                endLat = endLat, endLon = endLon,
                clubId = 0L,
                distanceM = dev.jbiffis.caddie.fit.GolfFit.haversineM(startLat, startLon, endLat, endLon),
            )
            dao.insertShot(newShot)
            shotIdx = shots.size // select the newly added shot
        }
    }

    fun fetchCourse() {
        if (fetching) return
        fetching = true
        fetchState = "Downloading course map…"
        scope.launch {
            fetchState = try {
                val n = withContext(Dispatchers.IO) { app.repository.downloadCourseFeatures(roundId) }
                if (n == 0) "This course isn't mapped on OpenStreetMap yet" else null
            } catch (e: Exception) {
                "Course map download failed — tap to retry\n(${e.message?.take(90) ?: e.javaClass.simpleName})"
            }
            fetching = false
        }
    }

    // Fetch the course polygons automatically the first time this round's
    // shot view is opened (import may have failed offline / rate-limited).
    LaunchedEffect(roundId) {
        val stored = withContext(Dispatchers.IO) { dao.featureCount(roundId) }
        if (stored == 0) fetchCourse()
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            HoleCanvas(
                shots = shots,
                features = features,
                pinLat = holeInfo?.pinLat,
                pinLon = holeInfo?.pinLon,
                currentIdx = shotIdx,
                editMode = editMode,
                onSelectShot = { shotIdx = it },
                onMoveNode = { node, lat, lon -> moveNode(node, lat, lon) },
            )
            if (features.isEmpty()) {
                OutlinedButton(
                    onClick = { fetchCourse() },
                    enabled = !fetching,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                ) { Text(fetchState ?: "Download course map (OpenStreetMap)") }
            }
            Row(
                Modifier.align(Alignment.TopEnd).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { editMode = !editMode }) {
                    Icon(
                        if (editMode) Icons.Filled.Check else Icons.Filled.Edit,
                        contentDescription = if (editMode) "Done editing" else "Edit shots",
                        tint = if (editMode) Color(0xFFFFD54F) else Color.White,
                    )
                }
                IconButton(onClick = { onOpenSatellite(hole) }) {
                    Icon(Icons.Filled.Layers, contentDescription = "Satellite view", tint = Color.White)
                }
            }
            if (editMode) {
                Text(
                    "Edit mode: drag the balls to move shots. Add or delete below.",
                    Modifier.align(Alignment.BottomCenter).padding(8.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Shot detail sheet
        Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            if (current == null) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "No tracked shots on this hole.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { addShot() }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 4.dp))
                        Text("Add a shot")
                    }
                }
            } else {
                val startLie = when {
                    shotIdx == 0 -> Lie.Type.TEE
                    else -> Lie.lieAt(current.startLat, current.startLon, features)
                }
                val isPutt = current.clubId == 0L
                val result: String = holeInfo?.let { h ->
                    if (h.pinLat != null && h.pinLon != null) {
                        Lie.classifyMiss(
                            current.startLat, current.startLon,
                            current.endLat, current.endLon,
                            h.pinLat, h.pinLon, features,
                        ).label
                    } else null
                } ?: Lie.lieAt(current.endLat, current.endLon, features).label

                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (shotIdx > 0) shotIdx-- }, enabled = shotIdx > 0) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous shot")
                        }
                        Text(
                            "Shot ${shotIdx + 1} of ${shots.size}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = { if (shotIdx < shots.size - 1) shotIdx++ }, enabled = shotIdx < shots.size - 1) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Next shot")
                        }
                    }
                    HorizontalDivider()
                    DetailRow("Lie", if (isPutt && startLie == Lie.Type.UNKNOWN) "Green" else startLie.label)
                    DetailRow(
                        "Club",
                        clubNames[current.clubId] ?: if (isPutt) "Putt / no club" else "Club ${current.clubId}",
                        onClick = { editClub = true },
                    )
                    DetailRow("Distance", "${current.distanceM.toYards()} yds")
                    DetailRow("Result", result)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { addShot() }) {
                            Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 4.dp))
                            Text("Add shot")
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("Delete shot", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Hole navigation bar
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (hole > 1) onNavigateHole(hole - 1) }, enabled = hole > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous hole")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Hole $hole", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                holeInfo?.let { h ->
                    Text(
                        listOfNotNull(
                            "Par ${h.par}",
                            h.strokeIndex?.let { "Hdcp $it" },
                            h.lengthM?.let { "${it.toYards()} yds" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            holeInfo?.let { h ->
                val color = scoreColor(h.strokes, h.par)
                Box(
                    Modifier.size(40.dp)
                        .background(color ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${h.strokes}",
                        fontWeight = FontWeight.Bold,
                        color = if (color != null) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            IconButton(onClick = { if (hole < holes.size) onNavigateHole(hole + 1) }, enabled = hole < holes.size) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next hole")
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete shot ${shotIdx + 1}?") },
            text = { Text("Removes this tracked shot from the map and club stats. The scorecard is not changed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        dao.deleteShot(current.id)
                        confirmDelete = false
                        if (shotIdx > 0) shotIdx--
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (editClub && current != null) {
        AlertDialog(
            onDismissRequest = { editClub = false },
            title = { Text("Club for shot ${shotIdx + 1}") },
            text = {
                Column {
                    (clubs.sortedBy { it.name } + dev.jbiffis.caddie.data.ClubEntity(0L, "Putt / no club")).forEach { club ->
                        Text(
                            club.name,
                            Modifier.fillMaxWidth()
                                .clickable {
                                    scope.launch { dao.updateShotClub(current.id, club.clubId); editClub = false }
                                }
                                .padding(vertical = 10.dp),
                            fontWeight = if (club.clubId == current.clubId) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editClub = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, fontWeight = FontWeight.Bold)
        if (onClick != null) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, Modifier.padding(start = 4.dp))
        }
    }
}

/** Flat, Garmin-style drawn hole with the shot chain. Tee is at the bottom, pin at the top. */
@Composable
private fun HoleCanvas(
    shots: List<ShotEntity>,
    features: List<CourseFeature>,
    pinLat: Double?,
    pinLon: Double?,
    currentIdx: Int,
    editMode: Boolean,
    onSelectShot: (Int) -> Unit,
    onMoveNode: (nodeIndex: Int, lat: Double, lon: Double) -> Unit,
) {
    if (shots.isEmpty() && (pinLat == null || features.isEmpty())) {
        Box(Modifier.fillMaxSize().background(RoughColor))
        return
    }

    // Anchor the frame at the tee, pointing at the pin
    val originLat = shots.firstOrNull()?.startLat ?: pinLat!!
    val originLon = shots.firstOrNull()?.startLon ?: pinLon!!
    val targetLat = pinLat ?: shots.last().endLat
    val targetLon = pinLon ?: shots.last().endLon
    val frame = remember(originLat, originLon, targetLat, targetLon) {
        LocalFrame(originLat, originLon, Lie.bearingDeg(originLat, originLon, targetLat, targetLon))
    }

    // Content bounds in metres: shots + pin, padded
    val bounds = remember(shots, pinLat, pinLon, frame) {
        var minR = -20.0; var maxR = 20.0; var minA = -15.0; var maxA = 30.0
        fun include(lat: Double, lon: Double, pad: Double) {
            val (r, a) = frame.project(lat, lon)
            minR = minOf(minR, r - pad); maxR = maxOf(maxR, r + pad)
            minA = minOf(minA, a - pad); maxA = maxOf(maxA, a + pad)
        }
        shots.forEach { include(it.startLat, it.startLon, 25.0); include(it.endLat, it.endLon, 25.0) }
        if (pinLat != null && pinLon != null) include(pinLat, pinLon, 35.0)
        doubleArrayOf(minR, maxR, minA, maxA)
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // Screen transform shared by drawing and gesture handling
    val transform: (Double, Double) -> Offset = remember(bounds, canvasSize) {
        { lat, lon ->
            val (r, a) = frame.project(lat, lon)
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            val dR = (bounds[1] - bounds[0]).toFloat()
            val dA = (bounds[3] - bounds[2]).toFloat()
            val scale = if (dR > 0 && dA > 0) minOf(w / dR, h / dA) else 1f
            val offX = (w - dR * scale) / 2f
            val offY = (h - dA * scale) / 2f
            Offset(
                offX + (r.toFloat() - bounds[0].toFloat()) * scale,
                h - offY - (a.toFloat() - bounds[2].toFloat()) * scale,
            )
        }
    }
    // Inverse: screen point -> (lat, lon), for dragging shots
    val screenToLatLon: (Offset) -> Pair<Double, Double> = remember(bounds, canvasSize) {
        { p ->
            val w = canvasSize.width.toFloat()
            val h = canvasSize.height.toFloat()
            val dR = (bounds[1] - bounds[0]).toFloat()
            val dA = (bounds[3] - bounds[2]).toFloat()
            val scale = if (dR > 0 && dA > 0) minOf(w / dR, h / dA) else 1f
            val offX = (w - dR * scale) / 2f
            val offY = (h - dA * scale) / 2f
            val r = (p.x - offX) / scale + bounds[0]
            val a = (h - offY - p.y) / scale + bounds[2]
            frame.unproject(r.toDouble(), a.toDouble())
        }
    }

    // Node k is shots[k].start (k < n) or the final resting point (k == n).
    val nodeCount = if (shots.isEmpty()) 0 else shots.size + 1
    fun nodeLatLon(k: Int): Pair<Double, Double> =
        if (k < shots.size) shots[k].startLat to shots[k].startLon
        else shots.last().endLat to shots.last().endLon

    var dragNode by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { canvasSize = it }
            .pointerInput(editMode, shots, transform) {
                if (editMode) {
                    detectDragGestures(
                        onDragStart = { start ->
                            var best = -1
                            var bestDist = 52.dp.toPx()
                            for (k in 0 until nodeCount) {
                                val (la, lo) = nodeLatLon(k)
                                val p = transform(la, lo)
                                val d = hypot(p.x - start.x, p.y - start.y)
                                if (d < bestDist) { best = k; bestDist = d }
                            }
                            dragNode = best
                            dragPos = start
                            if (best in 0 until shots.size) onSelectShot(best)
                            else if (best == shots.size && shots.isNotEmpty()) onSelectShot(shots.size - 1)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragPos = dragPos?.plus(delta)
                        },
                        onDragEnd = {
                            val node = dragNode
                            val pos = dragPos
                            if (node >= 0 && pos != null) {
                                val (lat, lon) = screenToLatLon(pos)
                                onMoveNode(node, lat, lon)
                            }
                            dragNode = -1; dragPos = null
                        },
                        onDragCancel = { dragNode = -1; dragPos = null },
                    )
                } else {
                    detectTapGestures { tap ->
                        var best = -1
                        var bestDist = 48.dp.toPx()
                        shots.forEachIndexed { i, s ->
                            val p = transform(s.startLat, s.startLon)
                            val d = hypot(p.x - tap.x, p.y - tap.y)
                            if (d < bestDist) { best = i; bestDist = d }
                        }
                        if (best >= 0) onSelectShot(best)
                    }
                }
            }
    ) {
        // Live screen position of a node, honouring an in-progress drag
        fun nodeScreen(k: Int): Offset =
            if (k == dragNode && dragPos != null) dragPos!!
            else nodeLatLon(k).let { (la, lo) -> transform(la, lo) }
        drawRect(RoughColor)
        if (canvasSize == IntSize.Zero) return@Canvas

        // Course polygons, least → most specific
        val order = listOf(
            Lie.Type.WOODS to WoodsColor,
            Lie.Type.ROUGH to ExplicitRoughColor,
            Lie.Type.WATER to WaterColor,
            Lie.Type.FAIRWAY to FairwayColor,
            Lie.Type.BUNKER to BunkerColor,
            Lie.Type.TEE to TeeColor,
            Lie.Type.GREEN to GreenColor,
        )
        for ((type, color) in order) {
            for (f in features) {
                if (f.type != type) continue
                // Broad phase: skip polygons entirely off screen
                val anyVisible = f.points.any {
                    val p = transform(it.first, it.second)
                    p.x > -300 && p.x < size.width + 300 && p.y > -300 && p.y < size.height + 300
                }
                if (!anyVisible) continue
                val path = Path()
                f.points.forEachIndexed { i, pt ->
                    val p = transform(pt.first, pt.second)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, color)
                if (type == Lie.Type.GREEN || type == Lie.Type.BUNKER) {
                    drawPath(path, Color(0x33000000), style = Stroke(width = 2f))
                }
            }
        }

        // Shot chain — dark outline under white line, like Garmin.
        // Shot i runs from node i to node i+1, so drags preview live and connected.
        shots.forEachIndexed { i, _ ->
            val a = nodeScreen(i)
            val b = nodeScreen(i + 1)
            val isCurrent = i == currentIdx
            drawLine(Color(0x66000000), a, b, strokeWidth = if (isCurrent) 14f else 9f)
            drawLine(
                if (isCurrent) Color.White else Color(0xCCFFFFFF),
                a, b,
                strokeWidth = if (isCurrent) 9f else 5f,
            )
        }
        // Shot start balls
        shots.forEachIndexed { i, _ ->
            val p = nodeScreen(i)
            val isCurrent = i == currentIdx
            val enlarged = editMode || isCurrent
            drawCircle(Color(0x66000000), radius = if (enlarged) 15f else 10f, center = p)
            drawCircle(if (isCurrent) Color(0xFFFFD54F) else Color.White, radius = if (enlarged) 12f else 8f, center = p)
        }
        // Final resting point of the last shot
        if (shots.isNotEmpty()) {
            val p = nodeScreen(shots.size)
            drawCircle(Color(0x66000000), radius = if (editMode) 12f else 9f, center = p)
            drawCircle(Color.White, radius = if (editMode) 9f else 6f, center = p)
        }

        // Pin flag
        if (pinLat != null && pinLon != null) {
            val p = transform(pinLat, pinLon)
            drawCircle(Color.White, radius = 13f, center = p)
            drawCircle(Color(0x55000000), radius = 13f, center = p, style = Stroke(width = 2f))
            drawLine(Color(0xFF444444), Offset(p.x - 1, p.y + 6), Offset(p.x - 1, p.y - 8), strokeWidth = 3f)
            val flag = Path().apply {
                moveTo(p.x - 1, p.y - 8); lineTo(p.x + 9, p.y - 5); lineTo(p.x - 1, p.y - 2); close()
            }
            drawPath(flag, Color(0xFFD32F2F))
        }

        // Distance bubbles on each segment (current shot emphasised)
        shots.forEachIndexed { i, s ->
            val a = nodeScreen(i)
            val b = nodeScreen(i + 1)
            if (hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()) < 40) return@forEachIndexed
            // While dragging, show the live distance instead of the stored one
            val yards = if (dragNode == i || dragNode == i + 1) {
                val (la1, lo1) = screenToLatLon(a)
                val (la2, lo2) = screenToLatLon(b)
                dev.jbiffis.caddie.fit.GolfFit.haversineM(la1, lo1, la2, lo2).toYards()
            } else s.distanceM.toYards()
            bubble(
                "$yards yds",
                Offset((a.x + b.x) / 2 + 14f, (a.y + b.y) / 2),
                emphasised = i == currentIdx,
            )
        }
    }
}

private fun DrawScope.bubble(text: String, at: Offset, emphasised: Boolean) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (emphasised) 34f else 28f
            isFakeBoldText = emphasised
            color = android.graphics.Color.BLACK
        }
        val tw = paint.measureText(text)
        val th = paint.descent() - paint.ascent()
        val padX = 16f
        val padY = 8f
        val rect = RectF(at.x, at.y - th / 2 - padY, at.x + tw + 2 * padX, at.y + th / 2 + padY)
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (emphasised) android.graphics.Color.WHITE else 0xE6FFFFFF.toInt()
        }
        canvas.nativeCanvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, bg)
        canvas.nativeCanvas.drawText(text, at.x + padX, at.y - (paint.descent() + paint.ascent()) / 2, paint)
    }
}
