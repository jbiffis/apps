package dev.jbiffis.caddie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ClubEntity
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.Footnote
import dev.jbiffis.caddie.ui.design.R as Radii
import dev.jbiffis.caddie.ui.design.RowDivider
import dev.jbiffis.caddie.ui.design.S
import dev.jbiffis.caddie.ui.design.ScreenHeader
import dev.jbiffis.caddie.ui.design.T
import dev.jbiffis.caddie.ui.design.ValueWithUnit
import kotlinx.coroutines.launch

/**
 * The watch only records an opaque numeric club ID with each shot. Name your clubs
 * here once and every other screen picks the names up.
 */
@Composable
fun ClubsScreen(app: CaddieApp, onBack: (() -> Unit)? = null) {
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val distances by app.db.dao().clubDistances().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<ClubEntity?>(null) }

    if (clubs.isEmpty()) {
        Column(
            Modifier.fillMaxSize().background(C.Canvas).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No clubs yet", style = T.screenTitle, color = C.TextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Clubs appear automatically when you import a round. Name them here to match your bag.",
                style = T.bodySmall,
                color = C.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val avgById = distances.associateBy { it.clubId }
    LazyColumn(
        Modifier.fillMaxSize().background(C.Canvas),
        contentPadding = PaddingValues(start = S.gutter, end = S.gutter, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(Radii.pill)).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.ChevronLeft, "Back", tint = C.TextPrimary, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.size(4.dp))
                }
                ScreenHeader("Name your clubs", "The watch reports a number — you decide what it's called")
            }
        }
        item {
            CaddieCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)) {
                clubs.sortedByDescending { avgById[it.clubId]?.avgM ?: 0.0 }.forEachIndexed { i, club ->
                    if (i > 0) RowDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = club }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(club.name, style = T.rowTitle, color = C.TextPrimary)
                            Spacer(Modifier.height(1.dp))
                            Text("Watch ID ${club.clubId}", style = T.metaSmall, color = C.TextSecondary)
                        }
                        avgById[club.clubId]?.let { d ->
                            Column(horizontalAlignment = Alignment.End) {
                                ValueWithUnit("${(d.avgM * M_TO_YD).toInt()}", "yd avg")
                                Text("${d.shots} shots", style = T.metaSmall, color = C.TextSecondary)
                            }
                        }
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = C.TextTertiary,
                            modifier = Modifier.padding(start = 12.dp).size(16.dp),
                        )
                    }
                }
            }
        }
        item {
            Footnote(
                "Unsure which is which? Longest average first is usually the driver — " +
                    "check your bag order in Garmin Golf.",
            )
        }
    }

    editing?.let { club ->
        var name by remember(club) { mutableStateOf(club.name) }
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = C.Surface,
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
