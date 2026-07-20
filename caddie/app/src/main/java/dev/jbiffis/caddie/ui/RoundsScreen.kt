package dev.jbiffis.caddie.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import dev.jbiffis.caddie.usb.UsbMtpImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RoundsScreen(app: CaddieApp, onOpenRound: (Long) -> Unit) {
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
                else app.repository.importFit(bytes)
            }
            when (result) {
                is ImportResult.NewRound -> { imported++; snackbar.showSnackbar("Imported ${result.courseName} (${result.totalScore})") }
                is ImportResult.ActivityAttached -> snackbar.showSnackbar("GPS track attached to round")
                is ImportResult.ActivityStored -> snackbar.showSnackbar(result.reason)
                is ImportResult.Duplicate -> snackbar.showSnackbar("Already imported: ${result.what}")
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
    fun importFromWatch() {
        if (usbBusy) return
        usbBusy = true
        scope.launch {
            try {
                snackbar.showSnackbar("Reading FIT files off the watch over USB…")
                val result = withContext(Dispatchers.IO) {
                    usbImporter.importWatchFitFiles(
                        onFit = { _, bytes -> app.repository.importFit(bytes) is ImportResult.NewRound },
                        log = {},
                    )
                }
                when (result) {
                    is UsbMtpImporter.Result.NoDevice -> snackbar.showSnackbar(
                        "No watch found. Plug it into the phone with the USB-C/OTG cable and unlock it.")
                    is UsbMtpImporter.Result.PermissionDenied -> snackbar.showSnackbar(
                        "USB permission denied — tap Allow when the prompt appears.")
                    is UsbMtpImporter.Result.OpenFailed -> snackbar.showSnackbar(
                        "Couldn't open the watch. On the watch, set USB mode to file transfer (MTP).")
                    is UsbMtpImporter.Result.Error -> snackbar.showSnackbar("USB import error: ${result.message}")
                    is UsbMtpImporter.Result.Ok -> snackbar.showSnackbar(
                        "Watch USB: read ${result.filesRead} FIT file(s), ${result.newRounds} new round(s).")
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
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No rounds yet", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.padding(8.dp))
                Text(
                    "Import SCORE and ACTIVITY .fit files from your watch " +
                        "(GARMIN/Scorecards and GARMIN/Activity folders), or sync over Bluetooth from the Watch tab.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
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
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
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
}

@Composable
private fun RoundCard(round: RoundEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(round.courseName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(formatDate(round.startedAtS), style = MaterialTheme.typography.bodySmall)
                val details = buildList {
                    round.teeName?.let { add("$it tees") }
                    round.totalPutts?.let { add("$it putts") }
                    round.distanceWalkedM?.let { add("${"%.1f".format(it / 1000 * 0.621371)} mi walked") }
                    round.avgHeartRate?.let { add("$it bpm avg") }
                }
                if (details.isNotEmpty()) {
                    Text(details.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${round.totalScore}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(toParString(round.totalScore, round.totalPar), style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
        }
    }
}
