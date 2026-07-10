package dev.jbiffis.caddie.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ClubEntity
import kotlinx.coroutines.launch

/**
 * The watch only records an opaque numeric club ID with each shot. Name your
 * clubs here once (Driver, 7 Iron, …) and every screen picks the names up.
 */
@Composable
fun ClubsScreen(app: CaddieApp) {
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val distances by app.db.dao().clubDistances().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ClubEntity?>(null) }

    if (clubs.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No clubs yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Clubs appear automatically when you import a round. " +
                    "Then name them here to match your bag.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val avgById = distances.associateBy { it.clubId }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        item {
            Text(
                "Tap a club to name it (the watch only reports a number). " +
                    "Check your Garmin Golf app bag order if unsure — longest average first is usually the driver.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        items(clubs.sortedByDescending { avgById[it.clubId]?.avgM ?: 0.0 }, key = { it.clubId }) { club ->
            val d = avgById[club.clubId]
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { editing = club }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(club.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("ID ${club.clubId}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (d != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${(d.avgM * M_TO_YD).toInt()} yd avg", fontWeight = FontWeight.Bold)
                            Text("${d.shots} shots", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Icon(Icons.Filled.Edit, contentDescription = "Rename", modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
    }

    editing?.let { club ->
        var name by remember(club) { mutableStateOf(club.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Name this club") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("e.g. Driver, 7 Iron, PW") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        app.db.dao().upsertClub(club.copy(name = name.trim().ifEmpty { club.name }))
                        editing = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
