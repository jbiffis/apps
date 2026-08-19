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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.R as Radii
import dev.jbiffis.caddie.ui.design.S
import dev.jbiffis.caddie.ui.design.T
import kotlin.math.roundToInt

/** Live-tunable drawn-map rendering settings. Changes apply to the map immediately. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(C.Canvas).verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(Radii.pill)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ChevronLeft, "Back", tint = C.TextPrimary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.size(4.dp))
            Text("Map settings", style = T.screenTitle, color = C.TextPrimary)
        }
        Spacer(Modifier.height(6.dp))

        SettingSlider(
            title = "Total objects",
            subtitle = "Overall tree/tuft budget for the whole hole view.",
            value = MapSettings.maxObjects.toFloat(),
            valueLabel = "${MapSettings.maxObjects}",
            range = 2000f..30000f,
            steps = 27, // 1000 apart
            onChange = { MapSettings.updateMaxObjects((it / 1000f).roundToInt() * 1000) },
        )
        SettingSlider(
            title = "Per-wood tuft limit",
            subtitle = "Density inside each wood area — higher fills big forests more.",
            value = MapSettings.perWood.toFloat(),
            valueLabel = "${MapSettings.perWood}",
            range = 500f..10000f,
            steps = 18, // 500 apart
            onChange = { MapSettings.updatePerWood((it / 500f).roundToInt() * 500) },
        )
        SettingSlider(
            title = "Tree size",
            subtitle = "10 steps. Shipped default is step 3.",
            value = MapSettings.treeSizeStop.toFloat(),
            valueLabel = "Step ${MapSettings.treeSizeStop}  ·  ×${"%.2f".format(MapSettings.treeScale)}",
            range = 1f..MapSettings.TREE_STOPS.toFloat(),
            steps = MapSettings.TREE_STOPS - 2,
            onChange = { MapSettings.updateTreeSize(it.roundToInt()) },
        )

        Text(
            "Changes apply to the drawn hole map right away. Defaults: " +
                "${MapSettings.DEFAULT_MAX} objects, ${MapSettings.DEFAULT_PER_WOOD} per wood, tree step ${MapSettings.DEFAULT_TREE_STOP}.",
            style = T.bodySmall,
            color = C.TextSecondary,
            modifier = Modifier.padding(horizontal = S.gutter, vertical = 10.dp),
        )
    }
}

@Composable
private fun SettingSlider(
    title: String,
    subtitle: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    CaddieCard(Modifier.padding(horizontal = S.gutter, vertical = 6.dp), padding = PaddingValues(S.cardWide)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = T.cardTitle, color = C.TextPrimary)
            Text(valueLabel, style = T.rowTitleBold, color = C.Green, fontWeight = FontWeight.Bold)
        }
        Text(subtitle, style = T.metaSmall, color = C.TextSecondary)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = C.Green,
                activeTrackColor = C.Green,
                inactiveTrackColor = C.HairlineStrong,
            ),
        )
    }
}
