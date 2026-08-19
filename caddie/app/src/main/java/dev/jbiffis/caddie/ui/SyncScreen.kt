package dev.jbiffis.caddie.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.ble.GarminBleClient

private fun blePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

@SuppressLint("MissingPermission")
@Composable
fun SyncScreen(app: CaddieApp) {
    val context = LocalContext.current
    val client = app.bleClient
    val state by client.state.collectAsState()
    val log by client.log.collectAsState()
    val directory by client.directory.collectAsState()

    var hasPermission by remember {
        mutableStateOf(blePermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants -> hasPermission = grants.values.all { it } }

    val devices = remember { mutableStateListOf<BluetoothDevice>() }
    var scanning by remember { mutableStateOf(false) }
    var livePolling by remember { mutableStateOf(client.isLivePolling) }

    val bluetoothManager = remember { context.getSystemService(BluetoothManager::class.java) }
    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                if (device.name != null && devices.none { it.address == device.address }) {
                    devices.add(device)
                }
            }
        }
    }

    fun stopScan() {
        if (scanning) {
            bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            scanning = false
        }
    }

    fun startScan() {
        devices.clear()
        val scanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: return
        scanner.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        )
        scanning = true
    }

    DisposableEffect(Unit) { onDispose { stopScan() } }
    LaunchedEffect(scanning) {
        if (scanning) {
            kotlinx.coroutines.delay(12_000)
            stopScan()
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Watch sync",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "build ${GarminBleClient.BLE_BUILD}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = {
                // Share the log as a FILE, not inline text — receiving apps clip long
                // EXTRA_TEXT and drop the tail (exactly the lines that matter most).
                val logText = client.exportLog()
                val uri = runCatching {
                    val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
                    val f = java.io.File(dir, "caddie-sync-log.txt")
                    f.writeText(logText)
                    androidx.core.content.FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", f,
                    )
                }.getOrNull()
                val share = Intent(Intent.ACTION_SEND).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Caddie sync log")
                    if (uri != null) {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, logText)
                    }
                }
                context.startActivity(Intent.createChooser(share, "Share sync log"))
            }) { Icon(Icons.Filled.Share, contentDescription = "Share log") }
        }
        Text(
            "One-time setup: the watch can hold ONE phone pairing. Remove it from the " +
                "Garmin Connect app, forget it in Android Bluetooth settings, then put the " +
                "watch in pairing mode (Settings → Phone) and scan here. After pairing once, " +
                "connect + sync pulls new golf files automatically.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(blePermissions()) }) {
                Text("Grant Bluetooth permission")
            }
            return@Column
        }

        // Controls stack vertically so no button is ever pushed off-screen on a
        // narrow phone (the READY row has three actions).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val statusText = when (state) {
                GarminBleClient.State.BONDING -> "pairing…"
                GarminBleClient.State.HANDSHAKE -> "handshake…"
                else -> state.name.lowercase()
            }
            when (state) {
                GarminBleClient.State.DISCONNECTED -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { if (scanning) stopScan() else startScan() }) {
                            Text(if (scanning) "Stop scan" else "Scan for watch")
                        }
                        Text(statusText, style = MaterialTheme.typography.labelMedium)
                    }
                }
                GarminBleClient.State.READY, GarminBleClient.State.SYNCING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { client.startSync() }, enabled = state == GarminBleClient.State.READY) {
                            Text("Sync new files")
                        }
                        OutlinedButton(
                            onClick = { client.resyncAll() },
                            enabled = state == GarminBleClient.State.READY,
                        ) { Text("Re-sync all") }
                        Text(statusText, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { client.listFilesOnly() },
                            enabled = state == GarminBleClient.State.READY,
                        ) { Text("List files (no download)") }
                        OutlinedButton(onClick = { client.disconnect() }) { Text("Disconnect") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (livePolling) {
                            Button(onClick = { client.stopLivePolling(); livePolling = false }) {
                                Text("Stop live round")
                            }
                            Text("watching for updates…", style = MaterialTheme.typography.labelMedium)
                        } else {
                            OutlinedButton(
                                onClick = { client.startLivePolling(); livePolling = true },
                                enabled = state == GarminBleClient.State.READY,
                            ) { Text("Watch live round") }
                            Text("experimental", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { client.disconnect() }) { Text("Cancel") }
                        Text(statusText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (state == GarminBleClient.State.DISCONNECTED && devices.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 180.dp).padding(top = 8.dp)) {
                items(devices, key = { it.address }) { device ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { stopScan(); client.connect(device) }
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text(device.name ?: "?", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Text(device.address, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        if (directory.isNotEmpty()) {
            Text(
                "Golf files on watch (tap to re-download)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            LazyColumn(Modifier.heightIn(max = 160.dp)) {
                items(directory, key = { it.index }) { entry ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { client.downloadEntry(entry) }
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text(
                                if (entry.subType == GarminBleClient.SUBTYPE_GOLF_SCORE)
                                    "Scorecard #${entry.number}" else "Activity #${entry.number}",
                                Modifier.weight(1f),
                            )
                            Text("${entry.size / 1024} KB", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Protocol log — share it if a sync stalls so the handshake can be fixed
        val listState = rememberLazyListState()
        LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }
        Card(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                items(log.size) { i ->
                    Text(log[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
