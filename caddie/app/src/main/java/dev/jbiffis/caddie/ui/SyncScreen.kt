package dev.jbiffis.caddie.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

    // Stop scanning when leaving the screen
    DisposableEffect(Unit) { onDispose { stopScan() } }
    LaunchedEffect(scanning) {
        if (scanning) {
            kotlinx.coroutines.delay(12_000)
            stopScan()
        }
    }

    val gcLog = remember { mutableStateListOf<String>() }
    val appendLog: (String) -> Unit = { line ->
        gcLog.add(line)
        if (gcLog.size > 300) gcLog.removeAt(0)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        GarminConnectSection(app, appendLog)

        Text(
            "Direct Bluetooth (experimental)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            "Talks straight to the watch — only works if the watch is NOT paired with " +
                "Garmin Connect. Prefer the cloud sync above.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 6.dp),
        )

        if (!hasPermission) {
            Button(onClick = { permissionLauncher.launch(blePermissions()) }) {
                Text("Grant Bluetooth permission")
            }
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            when (state) {
                GarminBleClient.State.DISCONNECTED -> {
                    Button(onClick = { if (scanning) stopScan() else startScan() }) {
                        Text(if (scanning) "Stop scan" else "Scan for watch")
                    }
                }
                GarminBleClient.State.READY, GarminBleClient.State.SYNCING -> {
                    Button(onClick = { client.startSync() }, enabled = state == GarminBleClient.State.READY) {
                        Text("List golf files")
                    }
                    OutlinedButton(onClick = { client.disconnect() }) { Text("Disconnect") }
                }
                else -> OutlinedButton(onClick = { client.disconnect() }) { Text("Cancel") }
            }
            Text(state.name.lowercase(), style = MaterialTheme.typography.labelMedium)
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
            Text("Golf files on watch", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.heightIn(max = 160.dp)) {
                items(directory, key = { it.index }) { entry ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { client.downloadEntry(entry) }
                    ) {
                        Row(Modifier.padding(12.dp)) {
                            Text(
                                if (entry.subType == 38) "Scorecard #${entry.number}" else "Activity #${entry.number}",
                                Modifier.weight(1f),
                            )
                            Text("${entry.size / 1024} KB", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // Combined sync + BLE protocol log
        val combined = gcLog.toList() + log
        val listState = rememberLazyListState()
        LaunchedEffect(combined.size) { if (combined.isNotEmpty()) listState.animateScrollToItem(combined.size - 1) }
        Card(Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp)) {
            LazyColumn(state = listState, modifier = Modifier.padding(8.dp)) {
                items(combined.size) { i ->
                    Text(combined[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
        }
    }
}
