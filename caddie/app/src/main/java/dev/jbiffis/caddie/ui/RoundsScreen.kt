package dev.jbiffis.caddie.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ImportResult
import dev.jbiffis.caddie.data.RoundEntity
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.ScreenHeader
import dev.jbiffis.caddie.ui.design.T
import dev.jbiffis.caddie.usb.UsbMtpImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RoundsScreen(app: CaddieApp, onOpenRound: (Long) -> Unit, onOpenSettings: () -> Unit = {}) {
    val rounds by app.db.dao().rounds().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<RoundEntity?>(null) }

    suspend fun importUris(uris: List<android.net.Uri>) {
        var imported = 0
        for (uri in uris) {
            val result = withContext(Dispatchers.IO) {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) ImportResult.Failed("Could not read file")
                else app.repository.importFile(bytes)
            }
            when (result) {
                is ImportResult.NewRound -> { imported++; snackbar.showSnackbar("Imported ${result.courseName} (${result.totalScore})") }
                is ImportResult.UpdatedRound -> { imported++; snackbar.showSnackbar(
                    "${if (result.finalized) "Finalized" else "Updated"} ${result.courseName} (${result.totalScore})") }
                is ImportResult.ActivityAttached -> snackbar.showSnackbar("GPS track attached to round")
                is ImportResult.ActivityStored -> snackbar.showSnackbar(result.reason)
                is ImportResult.Duplicate -> snackbar.showSnackbar("Already imported: ${result.what}")
                is ImportResult.ClubsImported -> snackbar.showSnackbar("Imported ${result.count} clubs")
                is ImportResult.CourseDatImported -> snackbar.showSnackbar("Course map: ${result.greens} green outlines")
                is ImportResult.Failed -> snackbar.showSnackbar(result.reason)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch { importUris(uris) }
    }

    val context = LocalContext.current
    val usbImporter = remember { UsbMtpImporter(context) }
    var usbBusy by remember { mutableStateOf(false) }
    var usbReport by remember { mutableStateOf<List<String>?>(null) }
    fun importFromWatch() {
        if (usbBusy) return
        usbBusy = true
        scope.launch {
            try {
                snackbar.showSnackbar("Reading FIT files off the watch over USB…")
                val result = withContext(Dispatchers.IO) {
                    usbImporter.importWatchFitFiles(onFit = { _, bytes ->
                        when (val r = app.repository.importFile(bytes)) {
                            is ImportResult.NewRound -> "NEW round: ${r.courseName} (${r.totalScore})"
                            is ImportResult.UpdatedRound ->
                                "${if (r.finalized) "finalized" else "updated"} round: ${r.courseName} (${r.totalScore})"
                            is ImportResult.ActivityAttached -> "activity attached"
                            is ImportResult.ActivityStored -> "activity held: ${r.reason}"
                            is ImportResult.Duplicate -> "already have: ${r.what}"
                            is ImportResult.ClubsImported -> "clubs: ${r.count} imported"
                            is ImportResult.CourseDatImported -> "course ${r.courseId}: ${r.greens} outlines"
                            is ImportResult.Failed -> "skipped: ${r.reason}"
                        }
                    })
                }
                when (result) {
                    is UsbMtpImporter.Result.NoDevice -> snackbar.showSnackbar(
                        "No watch found. Plug it into the phone with the USB-C/OTG cable and unlock it.")
                    is UsbMtpImporter.Result.PermissionDenied -> snackbar.showSnackbar(
                        "USB permission denied — tap Allow when the prompt appears.")
                    is UsbMtpImporter.Result.OpenFailed -> snackbar.showSnackbar(
                        "Couldn't open the watch. On the watch, set USB mode to file transfer (MTP).")
                    is UsbMtpImporter.Result.Error -> {
                        usbReport = listOf("USB import error: ${result.message}")
                        snackbar.showSnackbar("USB import error: ${result.message}")
                    }
                    is UsbMtpImporter.Result.Ok -> {
                        usbReport = result.report
                        snackbar.showSnackbar(
                            "Watch USB: read ${result.filesRead} FIT file(s), ${result.newRounds} new round(s).")
                    }
                }
            } finally {
                usbBusy = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmallFloatingActionButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Map settings")
                }
                SmallFloatingActionButton(onClick = { importFromWatch() }) {
                    Icon(Icons.Filled.Usb, contentDescription = "Import from watch over USB")
                }
                ExtendedFloatingActionButton(
                    onClick = { picker.launch(arrayOf("application/octet-stream", "application/vnd.ant.fit", "*/*")) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Import FIT") },
                )
            }
        },
    ) { padding ->
        if (rounds.isEmpty()) {
            Column(
                Modifier.fillMaxSize().background(C.Canvas).padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No rounds yet", style = T.screenTitle, color = C.TextPrimary)
                Spacer(Modifier.padding(8.dp))
                Text(
                    "Import SCORE and ACTIVITY .fit files from your watch " +
                        "(GARMIN/Scorecards and GARMIN/Activity folders), or sync over Bluetooth from the Watch tab.",
                    textAlign = TextAlign.Center,
                    style = T.bodySmall,
                    color = C.TextSecondary,
                )
                Spacer(Modifier.padding(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val names = withContext(Dispatchers.IO) { app.assets.list("samples")?.toList() ?: emptyList() }
                        for (name in names.filter { it.endsWith(".fit") }.sorted()) {
                            val result = withContext(Dispatchers.IO) {
                                app.assets.open("samples/$name").use { app.repository.importFit(it.readBytes()) }
                            }
                            if (result is ImportResult.NewRound) snackbar.showSnackbar("Imported ${result.courseName}")
                        }
                    }
                }) { Text("Load sample round (The Marshes)") }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(C.Canvas).padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ScreenHeader(
                        "Your rounds",
                        if (rounds.size == 1) "1 round imported" else "${rounds.size} rounds imported",
                    )
                }
                items(rounds, key = { it.id }) { round ->
                    RoundCard(round, onClick = { onOpenRound(round.id) }, onDelete = { confirmDelete = round })
                }
            }
        }
    }

    confirmDelete?.let { round ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete round?") },
            text = { Text("${round.courseName} on ${formatDate(round.startedAtS)} will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { app.db.dao().deleteRoundCascade(round.id); confirmDelete = null }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }

    usbReport?.let { lines ->
        AlertDialog(
            onDismissRequest = { usbReport = null },
            title = { Text("Watch USB import") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(lines.size) { i ->
                        Text(
                            lines[i],
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Caddie USB import report")
                        putExtra(android.content.Intent.EXTRA_TEXT, lines.joinToString("\n"))
                    }
                    context.startActivity(android.content.Intent.createChooser(share, "Share report"))
                }) { Text("Share") }
            },
            dismissButton = { TextButton(onClick = { usbReport = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun LiveBadge() {
    Box(
        Modifier
            .background(C.Orange, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text("LIVE", style = T.metaSmall, color = C.Canvas)
    }
}

@Composable
private fun RoundCard(round: RoundEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    CaddieCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(round.courseName, style = T.rowTitleBold, color = C.TextPrimary)
                    if (round.live) {
                        Spacer(Modifier.padding(start = 4.dp))
                        LiveBadge()
                    }
                }
                Spacer(Modifier.padding(top = 2.dp))
                Text(
                    if (round.live) "In progress · ${formatDate(round.startedAtS)}"
                    else formatDate(round.startedAtS),
                    style = T.meta, color = C.TextSecondary,
                )
                val details = buildList {
                    round.teeName?.let { add("$it tees") }
                    round.totalPutts?.let { add("$it putts") }
                    round.distanceWalkedM?.let { add("${"%.1f".format(it / 1000 * 0.621371)} mi walked") }
                    round.avgHeartRate?.let { add("$it bpm avg") }
                }
                if (details.isNotEmpty()) {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(details.joinToString(" · "), style = T.metaSmall, color = C.TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${round.totalScore}", style = T.stat26, color = C.TextPrimary)
                Spacer(Modifier.padding(top = 3.dp))
                Text(
                    toParString(round.totalScore, round.totalPar),
                    style = T.metaSmall,
                    color = when {
                        round.totalScore == 0 -> C.TextSecondary
                        round.totalScore > round.totalPar -> C.Orange
                        round.totalScore < round.totalPar -> C.Green
                        else -> C.TextPrimary
                    },
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete round", tint = C.TextTertiary)
            }
        }
    }
}
