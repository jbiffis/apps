package dev.jbiffis.caddie.ui

import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.R
import dev.jbiffis.caddie.data.CourseFeature
import dev.jbiffis.caddie.data.HoleHistory
import dev.jbiffis.caddie.data.Lie
import dev.jbiffis.caddie.data.LocalFrame
import dev.jbiffis.caddie.data.ShotEntity
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.GlassCircleButton
import dev.jbiffis.caddie.ui.design.GlassPill
import dev.jbiffis.caddie.ui.design.PillDivider
import dev.jbiffis.caddie.ui.design.R as Radii
import dev.jbiffis.caddie.ui.design.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// Garmin-style flat course colours
// Rich, saturated turf — striped fairway, deep putting green, dense trees.
private val RoughColor = Color(0xFF57892F)        // base rough (mostly under trees)
private val FairwayColor = Color(0xFF7FB449)      // striped fairway corridor
private val GreenColor = Color(0xFF357E30)        // putting surface: deep green
private val TeeColor = Color(0xFF8CC152)          // tee box
private val BunkerColor = Color(0xFFE3D4A2)
private val WaterColor = Color(0xFF5BA9D6)
private val WoodsColor = Color(0xFF4E8A3A)
private val ExplicitRoughColor = Color(0xFF6BA043)
private val PathColor = Color(0xFFD2CBBA)          // cart path (pale concrete)
private val PathEdgeColor = Color(0x55000000)

/** How much of the screen the history sheet covers, and how much of it peeks when closed. */
private const val SHEET_FRACTION = 0.82f
private val SHEET_PEEK = 108.dp
/** Release past this fraction of the travel and the sheet completes the move. */
private const val SHEET_SNAP = 0.536f
private val SheetEasing = CubicBezierEasing(0.22f, 0.61f, 0.36f, 1f)

/**
 * The in-round view: the hole drawn from OpenStreetMap geometry, this round's shot
 * path over it, and — behind a swipe-up sheet — what every previous round on this
 * hole says about how it should be played.
 *
 * Deliberately full-bleed with no tab bar. It is reached from the scorecard and
 * left by its back button; while you are standing over a shot the map is the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShotMapScreen(
    app: CaddieApp,
    roundId: Long,
    hole: Int,
    initialShot: Int,
    onNavigateHole: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val dao = app.db.dao()
    val round by dao.round(roundId).collectAsState(initial = null)
    val holes by dao.holes(roundId).collectAsState(initial = emptyList())
    val allShots by dao.shots(roundId).collectAsState(initial = emptyList())
    val clubs by dao.clubs().collectAsState(initial = emptyList())
    val featureEntities by dao.features(roundId).collectAsState(initial = emptyList())
    // Every round, for this hole's history across visits.
    val allRounds by dao.rounds().collectAsState(initial = emptyList())
    val everyHole by dao.allHoles().collectAsState(initial = emptyList())
    val everyShot by dao.allShots().collectAsState(initial = emptyList())
    val everyFeature by dao.allFeatures().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val holeInfo = holes.firstOrNull { it.hole == hole }
    val shots = allShots.filter { it.hole == hole }
    val features = remember(featureEntities) { featureEntities.mapNotNull { it.decode() } }
    val clubNames = clubs.associate { it.clubId to it.name }

    val history = remember(roundId, hole, allRounds, everyHole, everyShot, everyFeature) {
        val byRound = everyFeature.groupBy({ it.roundId }, { it.decode() })
            .mapValues { (_, v) -> v.filterNotNull() }
        HoleHistory.build(roundId, hole, allRounds, everyHole, everyShot, byRound)
    }

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

    // Map transform, hoisted so the zoom buttons and the gestures drive the same state.
    var zoom by remember(hole) { mutableFloatStateOf(1f) }
    var pan by remember(hole) { mutableStateOf(Offset.Zero) }

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
                "Course map download failed — tap to retry"
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

    // Lie transitions for the shot list: where each shot started, and what it found.
    val lieTransitions = remember(shots, features, holeInfo) {
        val pinLat = holeInfo?.pinLat
        val pinLon = holeInfo?.pinLon
        shots.mapIndexed { i, s ->
            val start = if (i == 0) Lie.Type.TEE else Lie.lieAt(s.startLat, s.startLon, features)
            val startLabel = if (s.clubId == 0L && start == Lie.Type.UNKNOWN) "Green" else start.label
            val end = when {
                i == shots.lastIndex -> "holed"
                pinLat != null && pinLon != null -> Lie.classifyMiss(
                    s.startLat, s.startLon, s.endLat, s.endLon, pinLat, pinLon, features, hole,
                ).label
                else -> Lie.lieAt(s.endLat, s.endLon, features).label
            }
            "$startLabel → $end"
        }
    }

    // Show satellite whenever there's no OSM vector geometry yet (during the
    // download or when the course isn't mapped), so the user always sees a real
    // map instead of blank turf. Flips to the drawn view once features arrive.
    val useSatellite = satelliteOverride ?: (features.isEmpty() && fetchState != null)

    BoxWithConstraints(Modifier.fillMaxSize().background(C.Canvas)) {
        val density = LocalDensity.current
        val sheetHeight = maxHeight * SHEET_FRACTION
        val collapsedPx = with(density) { (sheetHeight - SHEET_PEEK).toPx() }
        val sheetOffset = remember(collapsedPx) { Animatable(collapsedPx) }
        // derivedStateOf, and an offset read in the layout phase, keep the map from
        // recomposing on every frame the sheet moves.
        val expanded by remember(collapsedPx) {
            derivedStateOf { sheetOffset.value < collapsedPx * SHEET_SNAP }
        }

        // --- map ---------------------------------------------------------
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
                zoom = zoom,
                pan = pan,
                onTransform = { z, p -> zoom = z; pan = p },
                onSelectShot = { shotIdx = it; if (!editMode) sheetOpen = true },
                onMoveNode = { node, lat, lon -> moveNode(node, lat, lon) },
                modifier = Modifier.matchParentSize(),
            )
        }

        // --- floating chrome ---------------------------------------------
        GlassCircleButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp),
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back to scorecard", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        // Hole pill, with the hole stepper built into it so paging holes mid-round
        // is one tap and the top of the screen stays uncluttered.
        GlassPill(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            horizontal = 6.dp,
        ) {
            PillStep(
                icon = Icons.Filled.ChevronLeft,
                description = "Previous hole",
                enabled = hole > 1,
                onClick = { onNavigateHole(hole - 1) },
            )
            Text("HOLE $hole", style = T.overlineWide, color = C.Green)
            Spacer(Modifier.width(10.dp))
            PillDivider()
            Spacer(Modifier.width(10.dp))
            Text(
                holeInfo?.let { h ->
                    "Par ${h.par}" + (h.lengthM?.let { " · ${it.toYards()} yd" } ?: "")
                } ?: "Par –",
                style = T.stat13.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
            )
            PillStep(
                icon = Icons.Filled.ChevronRight,
                description = "Next hole",
                enabled = hole < holes.size,
                onClick = { onNavigateHole(hole + 1) },
            )
        }

        // Wind — tap to set it (the arrow points where it blows, relative to the hole).
        WindPill(
            speedKmh = holeInfo?.windSpeedKmh,
            dirDeg = holeInfo?.windDirDeg,
            holeBearingDeg = holeBearing,
            loading = windLoading,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 60.dp),
            onClick = { editWind = true },
        )

        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 68.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            GlassCircleButton(onClick = { satelliteOverride = !useSatellite }) {
                Icon(
                    Icons.Filled.Layers,
                    contentDescription = if (useSatellite) "Show the drawn hole" else "Show satellite imagery",
                    tint = if (useSatellite) C.Green else Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            GlassCircleButton(
                onClick = { editMode = !editMode },
                fill = if (editMode) C.Green else C.GlassFill,
                border = if (editMode) C.Green else C.HairlineStrong,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = if (editMode) "Done editing shots" else "Edit shots",
                    tint = if (editMode) C.OnAccent else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Zoom stack — only meaningful over the drawn map; the satellite view has
        // its own gestures.
        if (!useSatellite) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = SHEET_PEEK + 42.dp)
                    .width(42.dp) // keep the pill button-sized; the divider fills this, not the screen
                    .clip(RoundedCornerShape(Radii.card))
                    .background(C.GlassFill)
                    .border(1.dp, C.HairlineStrong, RoundedCornerShape(Radii.card)),
            ) {
                ZoomButton(Icons.Filled.Add, "Zoom in") { zoom = (zoom * 1.3f).coerceIn(1f, 5f) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.HairlineStrong))
                ZoomButton(Icons.Filled.Remove, "Zoom out") { zoom = (zoom * 0.75f).coerceIn(1f, 5f) }
            }

            // ODbL requires the credit wherever the geometry is shown.
            Text(
                "© OpenStreetMap",
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = SHEET_PEEK + 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x800A140F))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                style = T.mono9,
                color = Color(0x9EFFFFFF),
            )
        }

        if (features.isEmpty()) {
            OutlinedButton(
                onClick = { loadCourse(force = true) },
                enabled = !fetching,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp),
                shape = RoundedCornerShape(Radii.pill),
                border = BorderStroke(1.dp, C.HairlineStrong),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = C.GlassFill,
                    contentColor = Color.White,
                ),
            ) {
                Text(
                    when {
                        fetching -> "Downloading course map…"
                        useSatellite -> "Satellite — tap to retry the OSM outline"
                        else -> fetchState ?: "Download course map"
                    },
                    style = T.metaSmall,
                )
            }
        }

        if (editMode) {
            EditBanner(
                shotLabel = current?.let { "Shot ${shotIdx + 1}/${shots.size}" },
                onDelete = if (current != null) ({ confirmDelete = true }) else null,
                onDone = { editMode = false },
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 114.dp),
            )
        }

        // --- history sheet -----------------------------------------------
        HistorySheet(
            history = history,
            holeInfo = holeInfo,
            shots = shots,
            clubNames = clubNames,
            lieTransitions = lieTransitions,
            height = sheetHeight,
            offsetProvider = { sheetOffset.value },
            collapsedOffset = collapsedPx,
            expanded = expanded,
            onDrag = { delta ->
                scope.launch { sheetOffset.snapTo((sheetOffset.value + delta).coerceIn(0f, collapsedPx)) }
            },
            onSettle = { toExpanded ->
                scope.launch {
                    sheetOffset.animateTo(
                        if (toExpanded) 0f else collapsedPx,
                        tween(320, easing = SheetEasing),
                    )
                }
            },
            onSelectShot = { i -> shotIdx = i; sheetOpen = true },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
                }) { Text("Delete", color = C.Orange) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (editClub && current != null) {
        AlertDialog(
            onDismissRequest = { editClub = false },
            title = { Text("Club for shot ${shotIdx + 1}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
                    h.pinLat, h.pinLon, features, hole,
                ).label
            } else null
        } ?: Lie.lieAt(current.endLat, current.endLon, features).label
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = C.Sheet) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { if (shotIdx > 0) shotIdx-- }, enabled = shotIdx > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous shot")
                    }
                    Text(
                        "Shot ${shotIdx + 1} of ${shots.size}",
                        Modifier.weight(1f),
                        style = T.rowTitleBold,
                        color = C.TextPrimary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { if (shotIdx < shots.size - 1) shotIdx++ },
                        enabled = shotIdx < shots.size - 1,
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next shot")
                    }
                }
                Spacer(Modifier.height(6.dp))
                DetailRow("Lie", if (isPutt && startLie == Lie.Type.UNKNOWN) "Green" else startLie.label)
                DetailRow(
                    "Club",
                    clubNames[current.clubId] ?: if (isPutt) "Putt / no club" else "Club ${current.clubId}",
                    onClick = { editClub = true },
                )
                DetailRow("Distance", shotDistance(current.distanceM, isPutt).let { "${it.first} ${it.second}" })
                DetailRow("Result", result)
                DetailRow(
                    "Wind",
                    formatWind(holeInfo?.windSpeedKmh, holeInfo?.windDirDeg) ?: "Tap to set",
                    onClick = { editWind = true },
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { insertShotAt(shotIdx) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 2.dp))
                        Text("Before")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { insertShotAt(shotIdx + 1) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 2.dp))
                        Text("After")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = C.Orange) }
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

// --- chrome pieces --------------------------------------------------------

@Composable
private fun PillStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (enabled) Color.White else Color(0x40FFFFFF),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(Modifier.size(42.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Wind chip. The arrow points where the wind blows, rotated so "up" is the green. */
@Composable
private fun WindPill(
    speedKmh: Double?,
    dirDeg: Int?,
    holeBearingDeg: Double,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassPill(modifier = modifier, fill = C.GlassFillSoft, horizontal = 12.dp, vertical = 7.dp, onClick = onClick) {
        when {
            loading -> {
                CircularProgressIndicator(Modifier.size(13.dp), color = C.Blue, strokeWidth = 2.dp)
                Spacer(Modifier.width(7.dp))
                Text("Weather…", style = T.stat13, color = Color.White)
            }
            speedKmh != null && dirDeg != null -> {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = "Wind direction",
                    tint = C.Blue,
                    modifier = Modifier.size(13.dp).rotate(((dirDeg + 180).toDouble() - holeBearingDeg).toFloat()),
                )
                Spacer(Modifier.width(7.dp))
                Text(formatWind(speedKmh, dirDeg) ?: "", style = T.stat13, color = Color.White)
            }
            else -> {
                Icon(Icons.Filled.Air, contentDescription = "Set wind", tint = Color.White, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(7.dp))
                Text("Wind", style = T.stat13, color = Color.White)
            }
        }
    }
}

@Composable
private fun EditBanner(
    shotLabel: String?,
    onDelete: (() -> Unit)?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(Radii.pill))
            .background(C.Green)
            .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            shotLabel?.let { "$it · drag to move" } ?: "Tap a shot to select",
            style = T.body.copy(fontWeight = FontWeight.Bold),
            color = C.OnAccent,
        )
        Spacer(Modifier.width(12.dp))
        if (onDelete != null) {
            Text(
                "Delete",
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(C.OnAccent)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 13.dp, vertical = 5.dp),
                style = T.body.copy(fontWeight = FontWeight.Bold),
                color = C.Orange,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            "Done",
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(C.OnAccent)
                .clickable(onClick = onDone)
                .padding(horizontal = 13.dp, vertical = 5.dp),
            style = T.body.copy(fontWeight = FontWeight.Bold),
            color = C.Green,
        )
    }
}

// --- the sheet ------------------------------------------------------------

/**
 * The swipe-up history sheet. Collapsed it is a score summary; dragged (or tapped)
 * up it becomes this hole's record across every round played here.
 */
@Composable
private fun HistorySheet(
    history: HoleHistory,
    holeInfo: dev.jbiffis.caddie.data.HoleEntity?,
    shots: List<ShotEntity>,
    clubNames: Map<Long, String>,
    lieTransitions: List<String>,
    height: androidx.compose.ui.unit.Dp,
    offsetProvider: () -> Float,
    collapsedOffset: Float,
    expanded: Boolean,
    onDrag: (Float) -> Unit,
    onSettle: (Boolean) -> Unit,
    onSelectShot: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The gesture block below outlives recomposition, so anything it reads has to
    // come through a holder rather than being captured by value.
    val currentOffset by rememberUpdatedState(offsetProvider)
    val currentExpanded by rememberUpdatedState(expanded)
    Column(
        modifier
            .fillMaxWidth()
            .height(height)
            .offset { androidx.compose.ui.unit.IntOffset(0, offsetProvider().roundToInt()) }
            .clip(RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet))
            .background(C.Sheet),
    ) {
        // Handle: the drag target. Restricting dragging to the handle keeps the
        // card list's own scrolling unambiguous.
        var dragged by remember { mutableFloatStateOf(0f) }
        Column(
            Modifier
                .fillMaxWidth()
                .pointerInput(collapsedOffset) {
                    detectVerticalDragGestures(
                        onDragStart = { dragged = 0f },
                        onVerticalDrag = { change, delta ->
                            change.consume()
                            dragged += delta
                            onDrag(delta)
                        },
                        onDragEnd = {
                            val tapThreshold = with(density) { 6.dp.toPx() }
                            if (kotlin.math.abs(dragged) < tapThreshold) {
                                onSettle(!currentExpanded) // barely moved — treat it as a tap
                            } else {
                                onSettle(currentOffset() < collapsedOffset * SHEET_SNAP)
                            }
                        },
                        onDragCancel = { onSettle(currentExpanded) },
                    )
                }
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(Color(0x47FFFFFF)),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val par = holeInfo?.par ?: 0
                val strokes = holeInfo?.strokes ?: 0
                val (badgeBg, badgeFg) = scoreBadgeColors(strokes, par)
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(Radii.tile)).background(badgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (strokes > 0) "$strokes" else "–", style = T.stat19, color = badgeFg)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (strokes > 0) "${scoreName(strokes, par)} · ${toParString(strokes, par)} to par"
                        else "Not scored yet",
                        style = T.rowTitleBold,
                        color = C.TextPrimary,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        holeSummaryLine(holeInfo?.putts, shots, clubNames, lieTransitions),
                        style = T.meta,
                        color = C.TextSecondary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("History", style = T.metaSmall.copy(fontWeight = FontWeight.SemiBold), color = C.Green)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) "Collapse history" else "Expand history",
                    tint = C.Green,
                    modifier = Modifier.size(18.dp).rotate(if (expanded) 180f else 0f),
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HoleOverTimeCard(history)
            StrokesGainedCard(history)
            WhereYouMissCard(history, clubNames)
            ThisRoundCard(shots, clubNames, lieTransitions, onSelectShot)
        }
    }
}

/** "2 putts · driver into the right rough" — the one-line story of the hole. */
private fun holeSummaryLine(
    putts: Int?,
    shots: List<ShotEntity>,
    clubNames: Map<Long, String>,
    lieTransitions: List<String>,
): String {
    val parts = ArrayList<String>(2)
    if (putts != null && putts > 0) parts += if (putts == 1) "1 putt" else "$putts putts"
    val tee = shots.firstOrNull()
    if (tee != null) {
        val club = clubNames[tee.clubId]?.substringBefore(" (") ?: clubAbbrev(null, tee.clubId)
        val landed = lieTransitions.firstOrNull()?.substringAfter("→ ")?.lowercase()
        parts += if (landed != null) "$club into the $landed" else club
    }
    return if (parts.isEmpty()) "No shots tracked" else parts.joinToString(" · ")
}

@Composable
private fun DetailRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = T.body, color = C.TextSecondary)
        Text(value, style = T.rowTitle, color = C.TextPrimary)
        if (onClick != null) {
            Icon(
                Icons.Filled.ChevronRight, contentDescription = null,
                tint = C.TextTertiary, modifier = Modifier.padding(start = 4.dp).size(18.dp),
            )
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
        containerColor = C.Surface,
        title = { Text("Wind") },
        text = {
            Column {
                OutlinedButton(onClick = onAutoFill, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Air, contentDescription = null, Modifier.padding(end = 6.dp))
                    Text("Auto-fill whole round from weather")
                }
                Spacer(Modifier.height(12.dp))
                Text("Speed: ${speed.roundToInt()} km/h", fontWeight = FontWeight.Bold)
                Slider(value = speed.toFloat(), onValueChange = { speed = it.toDouble() }, valueRange = 0f..60f, steps = 11)
                Spacer(Modifier.height(8.dp))
                Text("Coming from: ${windCardinal(dir)}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
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
                            ) { Text(lbl, style = T.micro) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
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
    zoom: Float,
    pan: Offset,
    onTransform: (Float, Offset) -> Unit,
    onSelectShot: (Int) -> Unit,
    onMoveNode: (nodeIndex: Int, lat: Double, lon: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (shots.isEmpty() && (pinLat == null || features.isEmpty())) {
        Box(modifier.background(RoughColor))
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

    // Cache tuft positions (world coords) once so panning doesn't recompute the grid.
    val tufts = remember(features) { computeTufts(features) }

    // Shot labels are set in the design's numeral face.
    val context = LocalContext.current
    val labelTypeface = remember {
        runCatching { ResourcesCompat.getFont(context, R.font.space_grotesk_semibold) }.getOrNull()
    }

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

    // Always draw from a clamped pan, so a zoom driven by the buttons (which know
    // nothing about the map's extent) can never leave the hole off screen.
    val livePan = clampPan(pan, zoom)

    // User zoom/pan applied about the canvas centre, on top of the fit transform.
    val transform: (Double, Double) -> Offset = { lat, lon ->
        val b = baseTransform(lat, lon)
        val cx = canvasSize.width / 2f
        val cy = canvasSize.height / 2f
        Offset(cx + (b.x - cx) * zoom + livePan.x, cy + (b.y - cy) * zoom + livePan.y)
    }
    // Inverse: screen point -> (lat, lon), for dragging shots
    val screenToLatLon: (Offset) -> Pair<Double, Double> = { p ->
        val w = canvasSize.width.toFloat()
        val h = canvasSize.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val bx = (p.x - cx - livePan.x) / zoom + cx
        val by = (p.y - cy - livePan.y) / zoom + cy
        val dR = (bounds[1] - bounds[0]).toFloat()
        val dA = (bounds[3] - bounds[2]).toFloat()
        val scale = if (dR > 0 && dA > 0) minOf(w / dR, h / dA) else 1f
        val offX = (w - dR * scale) / 2f
        val offY = (h - dA * scale) / 2f
        val r = (bx - offX) / scale + bounds[0]
        val a = (h - offY - by) / scale + bounds[2]
        frame.unproject(r.toDouble(), a.toDouble())
    }

    // Node k is shots[k].start (k < n) or the final resting point (k == n).
    val nodeCount = if (shots.isEmpty()) 0 else shots.size + 1
    fun nodeLatLon(k: Int): Pair<Double, Double> =
        if (k < shots.size) shots[k].startLat to shots[k].startLon
        else shots.last().endLat to shots.last().endLon

    var dragNode by remember { mutableIntStateOf(-1) }
    var dragPos by remember { mutableStateOf<Offset?>(null) }

    // Edit mode marks every draggable pin with a ring that pulses outward.
    val pulse = rememberInfiniteTransition(label = "pin-pulse")
    val pulseT by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Restart),
        label = "pin-pulse-phase",
    )

    // The pointerInput blocks below survive recomposition, so everything they read
    // has to be reached through a holder. The map transform is a parameter now (the
    // zoom buttons drive it), and capturing it by value would freeze the gestures at
    // whatever zoom was current when the block was created.
    val liveZoom by rememberUpdatedState(zoom)
    val livePanState by rememberUpdatedState(livePan)
    val liveTransform by rememberUpdatedState(transform)
    val liveScreenToLatLon by rememberUpdatedState(screenToLatLon)
    val liveClamp by rememberUpdatedState({ p: Offset, z: Float -> clampPan(p, z) })
    val liveNodeLatLon by rememberUpdatedState({ k: Int -> nodeLatLon(k) })
    val liveNodeCount by rememberUpdatedState(nodeCount)
    // onMoveNode closes over the shot list as it was when it was created, so it has
    // to be refreshed too — otherwise a second drag writes back a stale shot row.
    val liveMoveNode by rememberUpdatedState(onMoveNode)
    val liveSelectShot by rememberUpdatedState(onSelectShot)
    val liveOnTransform by rememberUpdatedState(onTransform)

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(editMode) {
                // Pinch to zoom, drag to pan (view mode). Pan is clamped so the
                // current shot stays visible; double-tap resets.
                if (!editMode) {
                    detectTransformGestures { _, panChange, zoomChange, _ ->
                        val z = (liveZoom * zoomChange).coerceIn(1f, 5f)
                        liveOnTransform(z, liveClamp(livePanState + panChange, z))
                    }
                }
            }
            .pointerInput(editMode) {
                if (editMode) {
                    detectDragGestures(
                        onDragStart = { start ->
                            var best = -1
                            var bestDist = 52.dp.toPx()
                            for (k in 0 until liveNodeCount) {
                                val (la, lo) = liveNodeLatLon(k)
                                val p = liveTransform(la, lo)
                                val d = hypot(p.x - start.x, p.y - start.y)
                                if (d < bestDist) { best = k; bestDist = d }
                            }
                            dragNode = best
                            dragPos = start
                            val lastShot = liveNodeCount - 2
                            if (best in 0..lastShot) liveSelectShot(best)
                            else if (best == liveNodeCount - 1 && lastShot >= 0) liveSelectShot(lastShot)
                        },
                        onDrag = { change, delta ->
                            change.consume()
                            dragPos = dragPos?.plus(delta)
                        },
                        onDragEnd = {
                            val node = dragNode
                            val pos = dragPos
                            if (node >= 0 && pos != null) {
                                val (lat, lon) = liveScreenToLatLon(pos)
                                liveMoveNode(node, lat, lon)
                            }
                            dragNode = -1; dragPos = null
                        },
                        onDragCancel = { dragNode = -1; dragPos = null },
                    )
                }
            }
            .pointerInput(editMode) {
                // Tap selects the nearest shot in both modes (view mode also opens the
                // editor sheet); double-tap resets the zoom in view mode only.
                detectTapGestures(
                    onDoubleTap = { if (!editMode) liveOnTransform(1f, Offset.Zero) },
                    onTap = { tap ->
                        var best = -1
                        var bestDist = 48.dp.toPx()
                        for (i in 0 until liveNodeCount - 1) {
                            val (la, lo) = liveNodeLatLon(i)
                            val p = liveTransform(la, lo)
                            val d = hypot(p.x - tap.x, p.y - tap.y)
                            if (d < bestDist) { best = i; bestDist = d }
                        }
                        if (best >= 0) liveSelectShot(best)
                    },
                )
            }
    ) {
        // Live screen position of a node, honouring an in-progress drag
        fun nodeScreen(k: Int): Offset =
            if (k == dragNode && dragPos != null) dragPos!!
            else nodeLatLon(k).let { (la, lo) -> transform(la, lo) }
        drawRect(RoughColor)
        if (canvasSize == IntSize.Zero) return@Canvas
        val zf = zoom.coerceIn(1f, 2.5f) // trees/tufts grow a little as you zoom in

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
                // Diagonal mowing stripes on the mown surfaces, like the design.
                when (type) {
                    Lie.Type.FAIRWAY -> mowingStripes(path, 20f * zf, Color(0x22FFFFFF))
                    Lie.Type.GREEN -> mowingStripes(path, 13f * zf, Color(0x30FFFFFF))
                    Lie.Type.TEE -> mowingStripes(path, 14f * zf, Color(0x22FFFFFF))
                    else -> {}
                }
                if (type == Lie.Type.GREEN || type == Lie.Type.BUNKER || type == Lie.Type.WATER) {
                    drawPath(path, Color(0x2A000000), style = Stroke(width = 2f))
                }
            }
        }

        // Cart paths (open polylines) — pale paved line with a soft edge.
        for (f in features) {
            if (f.type != Lie.Type.PATH) continue
            val pts = f.points.map { transform(it.first, it.second) }
            if (pts.none { it.x in -60f..(size.width + 60f) && it.y in -60f..(size.height + 60f) }) continue
            val path = Path()
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, PathEdgeColor, style = Stroke(width = 8f * zf, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, PathColor, style = Stroke(width = 5f * zf, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Trees: draw cached tuft instances. Positions are precomputed in world
        // coords (no per-frame grid work — that was the pan lag), and only the ones
        // on screen are drawn.
        for (t in tufts) {
            val p = transform(t.lat, t.lon)
            if (p.x > -30f && p.x < size.width + 30f && p.y > -30f && p.y < size.height + 30f) {
                tree(p, t.baseR * zf, t.seed)
            }
        }

        // Shot chain — gently arced (like a ball flight), dark outline under white.
        // Putts stay straight. Shot i runs from node i to node i+1. The stroke width
        // is in screen pixels, so the path stays the same weight at every zoom.
        shots.forEachIndexed { i, s ->
            val a = nodeScreen(i)
            val b = nodeScreen(i + 1)
            val isCurrent = i == currentIdx
            val path = Path().apply {
                moveTo(a.x, a.y)
                val dx = b.x - a.x; val dy = b.y - a.y
                val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                if (s.clubId == 0L || len < 24f) {
                    lineTo(b.x, b.y)
                } else {
                    // Control point bulged perpendicular to the shot — arcs the flight.
                    val bulge = (len * 0.13f).coerceAtMost(70f)
                    quadraticBezierTo(
                        (a.x + b.x) / 2f - dy / len * bulge,
                        (a.y + b.y) / 2f + dx / len * bulge,
                        b.x, b.y,
                    )
                }
            }
            drawPath(path, Color(0x66000000), style = Stroke(width = if (isCurrent) 5.dp.toPx() else 3.6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(
                path,
                if (isCurrent) Color.White else Color(0xF5FFFFFF),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // Pins. The selected shot is green, the ball's final resting place is the
        // lighter green, everything else white.
        shots.forEachIndexed { i, _ ->
            val p = nodeScreen(i)
            drawPin(
                centre = p,
                diameter = if (i == currentIdx) 16.dp.toPx() else 13.dp.toPx(),
                fill = if (i == currentIdx) C.Green else Color.White,
            )
        }
        if (shots.isNotEmpty()) {
            drawPin(centre = nodeScreen(shots.size), diameter = 16.dp.toPx(), fill = C.GreenLight)
        }
        if (editMode) {
            for (k in 0 until nodeCount) {
                drawPulseRing(nodeScreen(k), pulseT)
            }
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
            drawPath(flag, C.FlagRed)
        }

        // Per-shot info bubble at each shot's start node: "club · distance".
        shots.forEachIndexed { i, _ ->
            val p = nodeScreen(i)
            val label = bubbleLabels.getOrNull(i) ?: return@forEachIndexed
            bubble(label, Offset(p.x + 13.dp.toPx(), p.y - 11.dp.toPx()), labelTypeface)
        }
    }
}

/** A shot marker: a filled dot ringed in translucent black so it reads on any turf. */
private fun DrawScope.drawPin(centre: Offset, diameter: Float, fill: Color) {
    val r = diameter / 2f
    drawCircle(Color(0x4D000000), radius = r + 2.5.dp.toPx() / 2f, center = centre)
    drawCircle(fill, radius = r, center = centre)
}

/** The edit-mode ring: expands from 0.75x to 1.5x and fades out, once every 1.5s. */
private fun DrawScope.drawPulseRing(centre: Offset, phase: Float) {
    val scale = 0.75f + phase * 0.75f
    val alpha = (0.85f * (1f - phase)).coerceAtLeast(0f)
    drawCircle(
        Color.White.copy(alpha = alpha),
        radius = 18.dp.toPx() * scale,
        center = centre,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private fun DrawScope.bubble(text: String, at: Offset, typeface: android.graphics.Typeface?) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.dp.toPx()
            typeface?.let { this.typeface = it }
            color = android.graphics.Color.rgb(0x12, 0x21, 0x1A)
        }
        val tw = paint.measureText(text)
        val th = paint.descent() - paint.ascent()
        val padX = 9.dp.toPx()
        val padY = 4.dp.toPx()
        val rect = RectF(at.x, at.y - th / 2 - padY, at.x + tw + 2 * padX, at.y + th / 2 + padY)
        val radius = 9.dp.toPx()
        // A drawn drop shadow rather than Paint.setShadowLayer, which a hardware
        // canvas ignores for shapes.
        val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x40000000 }
        val shadowRect = RectF(rect).apply { offset(0f, 2.dp.toPx()) }
        canvas.nativeCanvas.drawRoundRect(shadowRect, radius, radius, shadow)
        val bg = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFFFFF.toInt()
        }
        canvas.nativeCanvas.drawRoundRect(rect, radius, radius, bg)
        canvas.nativeCanvas.drawText(text, at.x + padX, at.y - (paint.descent() + paint.ascent()) / 2, paint)
    }
}

/** Diagonal mowing stripes clipped to a mown surface (fairway / green / tee). */
private fun DrawScope.mowingStripes(path: Path, band: Float, color: Color) {
    val b = path.getBounds()
    if (b.isEmpty) return
    val cx = b.center.x
    val cy = b.center.y
    val reach = hypot(b.width.toDouble(), b.height.toDouble()).toFloat() / 2f + band
    // Rotate so the lighter bands run diagonally across the mown surface.
    clipPath(path) {
        rotate(degrees = -32f, pivot = Offset(cx, cy)) {
            var x = cx - reach
            while (x < cx + reach) {
                drawRect(color, topLeft = Offset(x, cy - reach), size = androidx.compose.ui.geometry.Size(band, reach * 2f))
                x += band * 2f
            }
        }
    }
}

/** One cached tuft/tree instance in world coordinates. */
private class Tuft(val lat: Double, val lon: Double, val seed: Int, val baseR: Float)

private const val MAX_TUFTS = 3000

/**
 * Precompute tree/tuft positions once (independent of pan/zoom): tree nodes plus a
 * jittered world grid inside each woods polygon. Doing this once — instead of every
 * frame — is what keeps panning smooth.
 */
private fun computeTufts(features: List<CourseFeature>): List<Tuft> {
    val out = ArrayList<Tuft>()
    for (f in features) {
        when (f.type) {
            Lie.Type.TREE -> {
                val seed = (f.points[0].first * 1e5).toInt() * 31 + (f.points[0].second * 1e5).toInt()
                out.add(Tuft(f.points[0].first, f.points[0].second, seed, 12f))
            }
            Lie.Type.WOODS -> {
                val lats = f.points.map { it.first }; val lons = f.points.map { it.second }
                val midLat = (lats.min() + lats.max()) / 2
                val latStep = 7.0 / 111320.0
                val lonStep = 7.0 / (111320.0 * cos(Math.toRadians(midLat)))
                var la = lats.min(); var row = 0
                while (la <= lats.max() && out.size < MAX_TUFTS) {
                    var lo = lons.min() + if (row % 2 == 0) 0.0 else lonStep / 2
                    while (lo <= lons.max() && out.size < MAX_TUFTS) {
                        var sd = ((la * 1e5).toInt() * 73856093) xor ((lo * 1e5).toInt() * 19349663) xor 0x7A17
                        sd = sd * 1103515245 + 12345; val jla = la + (((sd ushr 16) and 0x7fff) / 32768.0 - 0.5) * latStep * 0.8
                        sd = sd * 1103515245 + 12345; val jlo = lo + (((sd ushr 16) and 0x7fff) / 32768.0 - 0.5) * lonStep * 0.8
                        if (Lie.pointInPolygon(jla, jlo, f.points)) out.add(Tuft(jla, jlo, sd, 9f))
                        lo += lonStep
                    }
                    la += latStep; row++
                }
            }
            else -> {}
        }
    }
    return out
}

/** A stylised bushy tree, varied per [seed]: size, lobe count, rotation, tint. */
private fun DrawScope.tree(center: Offset, baseR: Float, seed: Int) {
    var s = seed * 374761393 xor 0x632BE5AB
    fun rnd(): Float { s = s * 1103515245 + 12345; return ((s ushr 16) and 0x7fff) / 32768f }
    val r = baseR * (0.8f + rnd() * 0.7f)
    val lobes = 5 + (rnd() * 3).toInt()
    val rot = rnd() * 6.2832f
    val darkTint = -0.06f + rnd() * 0.12f
    fun g(base: Long): Color {
        val c = Color(base)
        val f = (1f + darkTint).coerceIn(0.85f, 1.15f)
        return Color((c.red * f).coerceIn(0f, 1f), (c.green * f).coerceIn(0f, 1f), (c.blue * f).coerceIn(0f, 1f))
    }
    val twoPi = 2f * Math.PI.toFloat()
    drawCircle(Color(0x30000000), radius = r * 1.18f, center = center + Offset(r * 0.4f, r * 0.55f))
    for (i in 0 until lobes) {
        val a = rot + i * twoPi / lobes + (rnd() - 0.5f) * 0.4f
        val dist = r * (0.48f + rnd() * 0.2f)
        drawCircle(g(0xFF2C561F), radius = r * (0.52f + rnd() * 0.22f), center = center + Offset(cos(a) * dist, sin(a) * dist))
    }
    drawCircle(g(0xFF34692B), radius = r * 0.88f, center = center)
    for (i in 0 until lobes) {
        val a = rot + i * twoPi / lobes + 0.5f
        val dist = r * (0.4f + rnd() * 0.18f)
        drawCircle(g(0xFF4C8B3A), radius = r * (0.3f + rnd() * 0.14f), center = center + Offset(cos(a) * dist, sin(a) * dist - r * 0.1f))
    }
    drawCircle(g(0xFF74B84C), radius = r * 0.4f, center = center + Offset(-r * 0.28f, -r * 0.34f))
    drawCircle(g(0xFF9AD268), radius = r * (0.14f + rnd() * 0.1f), center = center + Offset(-r * 0.36f, -r * 0.42f))
}
