package dev.jbiffis.caddie.ui

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// Garmin-style flat course colours
private val RoughColor = Color(0xFF6E9E43)
private val FairwayColor = Color(0xFF90BF57)
private val GreenColor = Color(0xFF2E7D32)   // putting surface: solid dark green
private val TeeColor = Color(0xFFB7DD7C)      // tee box: solid light green
private val BunkerColor = Color(0xFFE7DBA8)
private val WaterColor = Color(0xFF64B5F6)
private val WoodsColor = Color(0xFF4A7A33)
private val ExplicitRoughColor = Color(0xFF7FAB4C)

@OptIn(ExperimentalMaterial3Api::class)
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

    // Per-shot map bubble labels ("6i · 127") and the hole's play bearing (tee→green).
    val bubbleLabels = shots.map { "${clubAbbrev(clubNames[it.clubId], it.clubId)} · ${it.distanceM.toYards()}" }
    val holeBearing = remember(shots, holeInfo) {
        val tLat = shots.firstOrNull()?.startLat; val tLon = shots.firstOrNull()?.startLon
        val gLat = holeInfo?.pinLat ?: shots.lastOrNull()?.endLat
        val gLon = holeInfo?.pinLon ?: shots.lastOrNull()?.endLon
        if (tLat != null && tLon != null && gLat != null && gLon != null)
            Lie.bearingDeg(tLat, tLon, gLat, gLon) else 0.0
    }

    var shotIdx by rememberSaveable(hole) { mutableIntStateOf(initialShot) }
    if (shots.isNotEmpty()) shotIdx = shotIdx.coerceIn(0, shots.size - 1)
    val current = shots.getOrNull(shotIdx)

    var confirmDelete by remember { mutableStateOf(false) }
    var editClub by remember { mutableStateOf(false) }
    var editWind by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var windLoading by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(false) }
    // null = auto (drawn if mapped, satellite if not); true/false = user override.
    var satelliteOverride by remember { mutableStateOf<Boolean?>(null) }
    var fetchState by remember { mutableStateOf<String?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var greens by remember { mutableStateOf<List<List<Pair<Double, Double>>>>(emptyList()) }

    // Pull any Garmin CourseView green outlines (.DAT) that fall inside this hole's
    // view. Matched by location, since rounds don't record Garmin's course id.
    LaunchedEffect(shots, holeInfo) {
        val lats = shots.flatMap { listOf(it.startLat, it.endLat) } + listOfNotNull(holeInfo?.pinLat)
        val lons = shots.flatMap { listOf(it.startLon, it.endLon) } + listOfNotNull(holeInfo?.pinLon)
        greens = if (lats.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            val pad = 0.0025 // ~275 m, enough to catch the green serving this hole
            dao.greensInBounds(lats.min() - pad, lons.min() - pad, lats.max() + pad, lons.max() + pad)
                .map { it.polygon() }
        }
    }

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

    // Insert a shot at position [index] (0 = before the first shot, shots.size = append).
    // The new shot bridges the previous resting point and the next shot's start.
    fun insertShotAt(index: Int) {
        scope.launch {
            val idx = index.coerceIn(0, shots.size)
            val prev = shots.getOrNull(idx - 1)
            val next = shots.getOrNull(idx)
            val startLat = prev?.endLat ?: next?.startLat?.let { it + 0.0002 }
                ?: holeInfo?.pinLat ?: features.firstOrNull()?.points?.firstOrNull()?.first ?: return@launch
            val startLon = prev?.endLon ?: next?.startLon
                ?: holeInfo?.pinLon ?: features.firstOrNull()?.points?.firstOrNull()?.second ?: return@launch
            // End a short way toward the pin (or the next shot) so it's visible and draggable.
            val endLat = next?.startLat ?: holeInfo?.pinLat?.takeIf { it != startLat } ?: (startLat + 0.0002)
            val endLon = next?.startLon ?: holeInfo?.pinLon?.takeIf { it != startLon } ?: startLon
            // Pick a timeS strictly between neighbours so the list orders correctly;
            // if the gap is too small, nudge the later shots to make room.
            val prevT = prev?.timeS
            val nextT = next?.timeS
            val newT: Long = when {
                nextT == null -> (prevT ?: holeInfo?.finishedAtS ?: 0L) + 1
                prevT == null -> nextT - 1
                nextT - prevT > 1 -> (prevT + nextT) / 2
                else -> {
                    var t = prevT + 2
                    for (j in idx until shots.size) { dao.updateShot(shots[j].copy(timeS = t)); t++ }
                    prevT + 1
                }
            }
            dao.insertShot(
                ShotEntity(
                    roundId = roundId, hole = hole, timeS = newT,
                    startLat = startLat, startLon = startLon,
                    endLat = endLat, endLon = endLon,
                    clubId = 0L,
                    distanceM = dev.jbiffis.caddie.fit.GolfFit.haversineM(startLat, startLon, endLat, endLon),
                    windSpeedKmh = holeInfo?.windSpeedKmh, windDirDeg = holeInfo?.windDirDeg,
                )
            )
            shotIdx = idx // select the newly inserted shot
        }
    }

    fun loadCourse(force: Boolean) {
        if (fetching) return
        fetching = true
        fetchState = "Downloading course map…"
        scope.launch {
            fetchState = try {
                val n = withContext(Dispatchers.IO) {
                    // Manual retry forces a fresh fetch; auto-load re-fetches once per
                    // app session so OSM edits show up on the next restart.
                    if (force) app.repository.downloadCourseFeatures(roundId)
                    else app.repository.refreshCourseFeaturesForSession(roundId)
                }
                if (n == 0) "This course isn't mapped on OpenStreetMap yet" else null
            } catch (e: Exception) {
                "Course map download failed — tap to retry\n(${e.message?.take(90) ?: e.javaClass.simpleName})"
            }
            fetching = false
        }
    }

    // Re-check OpenStreetMap for this course once per app session (restart the app
    // after editing OSM to pull the new geometry). Within a session, repeated hole
    // views reuse the cached map instead of re-hitting Overpass.
    LaunchedEffect(roundId) { loadCourse(force = false) }

    fun autoFillWind() {
        if (windLoading) return
        windLoading = true
        editWind = false
        scope.launch {
            try { withContext(Dispatchers.IO) { app.repository.fetchWindForRound(roundId) } }
            finally { windLoading = false }
        }
    }

    // The first time a round with no wind is opened, back-fill it from the weather
    // automatically (once per round per session — never re-hits the network while
    // paging holes). Runs only once the holes have loaded and none carry wind.
    val roundHasWind = holes.any { it.windSpeedKmh != null }
    LaunchedEffect(roundId, holes.isNotEmpty(), roundHasWind) {
        if (holes.isNotEmpty() && !roundHasWind && !windLoading) {
            windLoading = true
            try { withContext(Dispatchers.IO) { app.repository.autoFillWindIfMissing(roundId) } }
            finally { windLoading = false }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            // Satellite when the user toggles it on, or (in auto mode) as a fallback
            // when there's no OpenStreetMap golf geometry (unmapped course / failed
            // Overpass fetch) so every course still shows a real map with the shots.
            val autoSatellite = features.isEmpty() && fetchState != null && !fetching
            val useSatellite = satelliteOverride ?: autoSatellite
            if (useSatellite) {
                SatelliteHoleMap(
                    shots = shots,
                    holeInfo = holeInfo,
                    clubNames = clubNames,
                    modifier = Modifier.matchParentSize(),
                    greens = greens,
                    onSelectShot = { shotIdx = it; sheetOpen = true },
                )
            } else {
                HoleCanvas(
                    shots = shots,
                    features = features,
                    pinLat = holeInfo?.pinLat,
                    pinLon = holeInfo?.pinLon,
                    currentIdx = shotIdx,
                    editMode = editMode,
                    bubbleLabels = bubbleLabels,
                    onSelectShot = { shotIdx = it; if (!editMode) sheetOpen = true },
                    onMoveNode = { node, lat, lon -> moveNode(node, lat, lon) },
                )
            }
            // Wind badge — tap to set the hole's wind (arrow points where it blows,
            // relative to the hole so up = toward the green).
            WindBadge(
                speedKmh = holeInfo?.windSpeedKmh,
                dirDeg = holeInfo?.windDirDeg,
                holeBearingDeg = holeBearing,
                loading = windLoading,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                onClick = { editWind = true },
            )
            if (features.isEmpty()) {
                OutlinedButton(
                    onClick = { loadCourse(force = true) },
                    enabled = !fetching,
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                ) {
                    Text(
                        when {
                            fetching -> "Downloading course map…"
                            useSatellite -> "Satellite view — tap to retry OSM outline"
                            else -> fetchState ?: "Download course map (OpenStreetMap)"
                        }
                    )
                }
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
                IconButton(onClick = { satelliteOverride = !useSatellite }) {
                    Icon(
                        Icons.Filled.Layers,
                        contentDescription = if (useSatellite) "Show drawn view" else "Show satellite view",
                        tint = if (useSatellite) Color(0xFFFFD54F) else Color.White,
                    )
                }
            }
            if (editMode && !useSatellite) {
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

        // Slim shot bar — tap to open the editor sheet (or add the first shot).
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                .clickable { if (current != null) sheetOpen = true else insertShotAt(0) },
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (current == null) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 6.dp))
                    Text("Add a shot", fontWeight = FontWeight.Bold)
                } else {
                    Text("Shot ${shotIdx + 1}/${shots.size}", fontWeight = FontWeight.Bold)
                    Text(
                        "   ${clubNames[current.clubId] ?: if (current.clubId == 0L) "Putt" else "Club"} · ${current.distanceM.toYards()} yds",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.Edit, contentDescription = "Edit shot")
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

    // Slide-up editor for the tapped shot.
    if (sheetOpen && current != null) {
        val startLie = if (shotIdx == 0) Lie.Type.TEE else Lie.lieAt(current.startLat, current.startLon, features)
        val isPutt = current.clubId == 0L
        val result: String = holeInfo?.let { h ->
            if (h.pinLat != null && h.pinLon != null) {
                Lie.classifyMiss(
                    current.startLat, current.startLon, current.endLat, current.endLon,
                    h.pinLat, h.pinLon, features,
                ).label
            } else null
        } ?: Lie.lieAt(current.endLat, current.endLon, features).label
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (shotIdx > 0) shotIdx-- }, enabled = shotIdx > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous shot")
                    }
                    Text(
                        "Shot ${shotIdx + 1} of ${shots.size}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
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
                DetailRow("Wind", formatWind(holeInfo?.windSpeedKmh, holeInfo?.windDirDeg) ?: "Tap to set", onClick = { editWind = true })
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { insertShotAt(shotIdx) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 2.dp))
                        Text("Before")
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { insertShotAt(shotIdx + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 2.dp))
                        Text("After")
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (editWind) {
        WindEditorDialog(
            initialSpeed = holeInfo?.windSpeedKmh,
            initialDir = holeInfo?.windDirDeg,
            onDismiss = { editWind = false },
            onAutoFill = { autoFillWind() },
            onClear = { scope.launch { dao.applyHoleWind(roundId, hole, null, null); editWind = false } },
            onSave = { speed, dir, allHoles ->
                scope.launch {
                    if (allHoles) dao.applyRoundWind(roundId, speed, dir)
                    else dao.applyHoleWind(roundId, hole, speed, dir)
                    editWind = false
                }
            },
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

/** Wind chip overlaid on the map. The arrow points where the wind blows, rotated
 *  so "up" is toward the green (matching the tee-down hole orientation). */
@Composable
private fun WindBadge(
    speedKmh: Double?,
    dirDeg: Int?,
    holeBearingDeg: Double,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .background(Color(0xB3000000), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp), color = Color(0xFF80D8FF), strokeWidth = 2.dp,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text("Weather…", color = Color.White, style = MaterialTheme.typography.labelLarge)
        } else if (speedKmh != null && dirDeg != null) {
            // Wind blows toward (dir + 180); show it relative to the hole bearing.
            val angle = ((dirDeg + 180).toDouble() - holeBearingDeg).toFloat()
            Icon(
                Icons.Filled.Navigation, contentDescription = "Wind direction",
                tint = Color(0xFF80D8FF),
                modifier = Modifier.size(18.dp).rotate(angle),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text(formatWind(speedKmh, dirDeg) ?: "", color = Color.White, style = MaterialTheme.typography.labelLarge)
        } else {
            Icon(Icons.Filled.Air, contentDescription = "Set wind", tint = Color.White, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            Text("Wind", color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun WindEditorDialog(
    initialSpeed: Double?,
    initialDir: Int?,
    onDismiss: () -> Unit,
    onAutoFill: () -> Unit,
    onClear: () -> Unit,
    onSave: (speed: Double, dir: Int, allHoles: Boolean) -> Unit,
) {
    var speed by remember { mutableStateOf(initialSpeed ?: 10.0) }
    var dir by remember { mutableIntStateOf(initialDir ?: 0) }
    var allHoles by remember { mutableStateOf(false) }
    val cardinals = listOf("N" to 0, "NE" to 45, "E" to 90, "SE" to 135, "S" to 180, "SW" to 225, "W" to 270, "NW" to 315)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wind") },
        text = {
            Column {
                OutlinedButton(onClick = onAutoFill, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Air, contentDescription = null, Modifier.padding(end = 6.dp))
                    Text("Auto-fill whole round from weather")
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                Text("Speed: ${speed.roundToInt()} km/h", fontWeight = FontWeight.Bold)
                Slider(value = speed.toFloat(), onValueChange = { speed = it.toDouble() }, valueRange = 0f..60f, steps = 11)
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                Text("Coming from: ${windCardinal(dir)}", fontWeight = FontWeight.Bold)
                androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                cardinals.chunked(4).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        rowItems.forEach { (lbl, deg) ->
                            val sel = dir == deg
                            OutlinedButton(
                                onClick = { dir = deg },
                                modifier = Modifier.weight(1f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp),
                                colors = if (sel) androidx.compose.material3.ButtonDefaults.buttonColors()
                                    else androidx.compose.material3.ButtonDefaults.outlinedButtonColors(),
                            ) { Text(lbl, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allHoles, onCheckedChange = { allHoles = it })
                    Text("Apply to all holes in this round")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(speed, dir, allHoles) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
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
    bubbleLabels: List<String>,
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
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Base fit transform: lat/lon -> screen with the hole framed to the canvas.
    val baseTransform: (Double, Double) -> Offset = remember(bounds, canvasSize) {
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
    // User zoom/pan applied about the canvas centre, on top of the fit transform.
    val transform: (Double, Double) -> Offset = { lat, lon ->
        val b = baseTransform(lat, lon)
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        Offset(cx + (b.x - cx) * zoom + pan.x, cy + (b.y - cy) * zoom + pan.y)
    }
    // Inverse: screen point -> (lat, lon), for dragging shots
    val screenToLatLon: (Offset) -> Pair<Double, Double> = { p ->
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val bx = (p.x - cx - pan.x) / zoom + cx
        val by = (p.y - cy - pan.y) / zoom + cy
        val dR = (bounds[1] - bounds[0]).toFloat()
        val dA = (bounds[3] - bounds[2]).toFloat()
        val scale = if (dR > 0 && dA > 0) minOf(w / dR, h / dA) else 1f
        val offX = (w - dR * scale) / 2f
        val offY = (h - dA * scale) / 2f
        val r = (bx - offX) / scale + bounds[0]
        val a = (h - offY - by) / scale + bounds[2]
        frame.unproject(r.toDouble(), a.toDouble())
    }
    // Clamp the pan so the current shot always stays on screen — no panning it away.
    fun clampPan(p: Offset, z: Float): Offset {
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        if (w == 0f || h == 0f) return p
        val cx = w / 2f
        val cy = h / 2f
        val anchor = shots.getOrNull(currentIdx)?.let {
            val a = baseTransform(it.startLat, it.startLon)
            val b = baseTransform(it.endLat, it.endLon)
            Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        } ?: if (pinLat != null && pinLon != null) baseTransform(pinLat, pinLon) else Offset(cx, cy)
        val baseX = cx + (anchor.x - cx) * z
        val baseY = cy + (anchor.y - cy) * z
        val m = 48f
        return Offset(
            p.x.coerceIn(m - baseX, (w - m) - baseX),
            p.y.coerceIn(m - baseY, (h - m) - baseY),
        )
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
            .pointerInput(editMode, shots.size) {
                // Pinch to zoom, drag to pan (view mode). Pan is clamped so the
                // current shot stays visible; double-tap resets.
                if (!editMode) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val z = (zoom * zoomChange).coerceIn(1f, 5f)
                        zoom = z
                        pan = clampPan(pan + panChange, z)
                    }
                }
            }
            .pointerInput(editMode, shots.size) {
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
                }
            }
            .pointerInput(editMode, shots.size) {
                if (!editMode) {
                    detectTapGestures(
                        onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                        onTap = { tap ->
                            var best = -1
                            var bestDist = 48.dp.toPx()
                            shots.forEachIndexed { i, s ->
                                val p = transform(s.startLat, s.startLon)
                                val d = hypot(p.x - tap.x, p.y - tap.y)
                                if (d < bestDist) { best = i; bestDist = d }
                            }
                            if (best >= 0) onSelectShot(best)
                        },
                    )
                }
            }
    ) {
        // Live screen position of a node, honouring an in-progress drag
        fun nodeScreen(k: Int): Offset =
            if (k == dragNode && dragPos != null) dragPos!!
            else nodeLatLon(k).let { (la, lo) -> transform(la, lo) }
        drawRect(RoughColor)
        if (canvasSize == IntSize.Zero) return@Canvas
        val zf = zoom.coerceIn(1f, 2.5f) // trees/tufts grow a little as you zoom in
        turfTexture(0x9F0210) // subtle mottle over the rough (covered where polygons draw)

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
                // Mowing stripes + mottle on the fairway; tee and green stay solid.
                if (type == Lie.Type.FAIRWAY) {
                    mowingStripes(path, 26f, Color(0x1EFFFFFF))
                    clipPath(path) { turfTexture(0x5A17) }
                }
                if (type == Lie.Type.GREEN || type == Lie.Type.BUNKER || type == Lie.Type.WATER) {
                    drawPath(path, Color(0x33000000), style = Stroke(width = 2f))
                }
            }
        }

        // Trees on top of everything but the shot chain: OSM tree nodes as canopy
        // glyphs, plus a scatter of tufts inside wood/forest polygons.
        for (f in features) {
            if (f.type != Lie.Type.WOODS) continue
            val proj = f.points.map { transform(it.first, it.second) }
            val minX = proj.minOf { it.x }; val maxX = proj.maxOf { it.x }
            val minY = proj.minOf { it.y }; val maxY = proj.maxOf { it.y }
            val step = 24f * zf
            var y = minY
            var row = 0
            while (y <= maxY) {
                var x = minX + if (row % 2 == 0) 0f else step / 2
                while (x <= maxX) {
                    // Jitter each tuft off the grid so the woods don't look regular.
                    var sd = (x.toInt() * 73856093) xor (y.toInt() * 19349663) xor 0x7A17
                    sd = sd * 1103515245 + 12345; val jx = x + (((sd ushr 16) and 0x7fff) / 32768f - 0.5f) * step * 0.7f
                    sd = sd * 1103515245 + 12345; val jy = y + (((sd ushr 16) and 0x7fff) / 32768f - 0.5f) * step * 0.7f
                    if (jx in -20f..(size.width + 20f) && jy in -20f..(size.height + 20f) &&
                        pointInScreenPolygon(jx, jy, proj)
                    ) tree(Offset(jx, jy), 9f * zf, sd)
                    x += step
                }
                y += step * 0.82f; row++
            }
        }
        for (f in features) {
            if (f.type != Lie.Type.TREE) continue
            val p = transform(f.points[0].first, f.points[0].second)
            if (p.x in -20f..(size.width + 20f) && p.y in -20f..(size.height + 20f)) {
                val seed = (f.points[0].first * 1e5).toInt() * 31 + (f.points[0].second * 1e5).toInt()
                tree(p, 12f * zf, seed)
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

        // Per-shot info bubble at each shot's start node: "club · distance".
        shots.forEachIndexed { i, _ ->
            val p = nodeScreen(i)
            val label = bubbleLabels.getOrNull(i) ?: return@forEachIndexed
            bubble(label, Offset(p.x + 16f, p.y), emphasised = i == currentIdx)
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

/** Faint, jittered dark/light blobs to break up flat turf — subtle grass texture. */
private fun DrawScope.turfTexture(seedBase: Int) {
    val w = size.width
    val h = size.height
    val step = 56f
    var yy = -step
    var row = 0
    while (yy < h + step) {
        var xx = if (row % 2 == 0) -step else -step / 2
        while (xx < w + step) {
            var s = seedBase xor (xx.toInt() * 73856093) xor (yy.toInt() * 19349663)
            s = s * 1103515245 + 12345; val jx = xx + (((s ushr 16) and 0x7fff) / 32768f - 0.5f) * step
            s = s * 1103515245 + 12345; val jy = yy + (((s ushr 16) and 0x7fff) / 32768f - 0.5f) * step
            s = s * 1103515245 + 12345; val t = ((s ushr 16) and 0x7fff) / 32768f
            s = s * 1103515245 + 12345; val rad = step * (0.28f + ((s ushr 16) and 0x7fff) / 32768f * 0.45f)
            drawCircle(Color(if (t < 0.5f) 0x16000000 else 0x14FFFFFF), radius = rad, center = Offset(jx, jy))
            xx += step
        }
        yy += step * 0.82f; row++
    }
}

/** Vertical mowing stripes clipped to a turf polygon (Garmin-golf look). */
private fun DrawScope.mowingStripes(path: Path, bandPx: Float, color: Color) {
    clipPath(path) {
        var x = 0f
        while (x < size.width) {
            drawRect(color, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(bandPx, size.height))
            x += bandPx * 2
        }
    }
}

/**
 * A stylised bushy tree, varied per [seed] so no two look identical: the size,
 * lobe count, rotation, tint and highlight all wobble a little. Each lobe is drawn
 * with its own jitter for a lumpy, textured canopy.
 */
private fun DrawScope.tree(center: Offset, baseR: Float, seed: Int) {
    var s = seed * 374761393 xor 0x632BE5AB
    fun rnd(): Float { s = s * 1103515245 + 12345; return ((s ushr 16) and 0x7fff) / 32768f }

    val r = baseR * (0.8f + rnd() * 0.7f)          // size varies ±
    val lobes = 5 + (rnd() * 3).toInt()             // 5..7 lobes
    val rot = rnd() * 6.2832f                        // random orientation
    val darkTint = -0.06f + rnd() * 0.12f            // shift greens a touch
    fun g(base: Long): Color {
        val c = Color(base)
        val f = (1f + darkTint).coerceIn(0.85f, 1.15f)
        return Color((c.red * f).coerceIn(0f, 1f), (c.green * f).coerceIn(0f, 1f), (c.blue * f).coerceIn(0f, 1f))
    }
    val twoPi = 2f * Math.PI.toFloat()

    // soft ground shadow
    drawCircle(Color(0x30000000), radius = r * 1.18f, center = center + Offset(r * 0.4f, r * 0.55f))
    // dark base canopy — a ring of jittered lobes → bumpy, bushy outline
    for (i in 0 until lobes) {
        val a = rot + i * twoPi / lobes + (rnd() - 0.5f) * 0.4f
        val dist = r * (0.48f + rnd() * 0.2f)
        drawCircle(g(0xFF2C561F), radius = r * (0.52f + rnd() * 0.22f), center = center + Offset(cos(a) * dist, sin(a) * dist))
    }
    drawCircle(g(0xFF34692B), radius = r * 0.88f, center = center)
    // mid-tone clumps between the base lobes
    for (i in 0 until lobes) {
        val a = rot + i * twoPi / lobes + 0.5f
        val dist = r * (0.4f + rnd() * 0.18f)
        drawCircle(g(0xFF4C8B3A), radius = r * (0.3f + rnd() * 0.14f), center = center + Offset(cos(a) * dist, sin(a) * dist - r * 0.1f))
    }
    // sunlit highlights, upper-left
    drawCircle(g(0xFF74B84C), radius = r * 0.4f, center = center + Offset(-r * 0.28f, -r * 0.34f))
    drawCircle(g(0xFF9AD268), radius = r * (0.14f + rnd() * 0.1f), center = center + Offset(-r * 0.36f, -r * 0.42f))
}

/** Ray-cast point-in-polygon on screen-space points, for scattering tufts in woods. */
private fun pointInScreenPolygon(x: Float, y: Float, poly: List<Offset>): Boolean {
    if (poly.size < 3) return false
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val pi = poly[i]; val pj = poly[j]
        if ((pi.y > y) != (pj.y > y) && x < (pj.x - pi.x) * (y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
        j = i
    }
    return inside
}
