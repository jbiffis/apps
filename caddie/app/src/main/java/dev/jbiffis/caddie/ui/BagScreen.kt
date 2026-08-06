package dev.jbiffis.caddie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.jbiffis.caddie.CaddieApp
import dev.jbiffis.caddie.data.ClubStats
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.CaddieCard
import dev.jbiffis.caddie.ui.design.Footnote
import dev.jbiffis.caddie.ui.design.R as Radii
import dev.jbiffis.caddie.ui.design.S
import dev.jbiffis.caddie.ui.design.ScreenHeader
import dev.jbiffis.caddie.ui.design.T
import kotlin.math.roundToInt

/**
 * The distance ladder: every club you actually carry, longest first, each bar
 * measured against the longest.
 *
 * The gaps are the point. Real tracked data has clubs that overlap and clubs the
 * watch never named, and seeing an 8 iron sitting above a 6 iron is what tells a
 * golfer which part of the bag needs work — so nothing here is sorted into an
 * idealised ladder or quietly hidden.
 */
@Composable
fun BagScreen(
    app: CaddieApp,
    onOpenClub: (Long) -> Unit,
    onRenameClubs: () -> Unit,
) {
    val shots by app.db.dao().allShots().collectAsState(initial = emptyList())
    val holes by app.db.dao().allHoles().collectAsState(initial = emptyList())
    val clubs by app.db.dao().clubs().collectAsState(initial = emptyList())
    val featureEntities by app.db.dao().allFeatures().collectAsState(initial = emptyList())

    val stats = remember(shots, holes, clubs, featureEntities) {
        val featuresByRound = featureEntities.groupBy({ it.roundId }, { it.decode() })
            .mapValues { (_, v) -> v.filterNotNull() }
        ClubStats.computeAll(shots, holes, clubs.associate { it.clubId to it.name }, featuresByRound)
    }

    if (stats.isEmpty()) {
        EmptyBag()
        return
    }

    val longest = stats.first().averageYd.coerceAtLeast(1.0)

    LazyColumn(
        Modifier.fillMaxSize().background(C.Canvas),
        contentPadding = PaddingValues(start = S.gutter, end = S.gutter, top = 6.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ScreenHeader(
                "Your bag",
                "Smart distances · ${shots.count { it.clubId != 0L }} tracked shots",
            )
        }
        item {
            CaddieCard(padding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
                stats.forEach { club ->
                    ClubLadderRow(club, longest, onClick = { onOpenClub(club.clubId) })
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Footnote("Tap a club for full distance stats")
                Text(
                    "Rename clubs",
                    Modifier.fillMaxWidth().clickable(onClick = onRenameClubs).padding(vertical = 2.dp),
                    style = T.metaSmall,
                    color = C.Green,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ClubLadderRow(club: ClubStats, longestYd: Double, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            clubAbbrev(club.name, club.clubId),
            Modifier.width(34.dp),
            style = T.stat15.copy(fontStyle = FontStyle.Italic),
            color = C.TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(Radii.bar))
                .background(Color(0x0DFFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((club.averageYd / longestYd).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(Radii.bar))
                    .background(Brush.horizontalGradient(listOf(C.GreenDark, C.GreenLight))),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${club.averageYd.roundToInt()}",
            Modifier.width(44.dp),
            style = T.stat16,
            color = C.TextPrimary,
            textAlign = TextAlign.End,
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = C.TextTertiary,
            modifier = Modifier.padding(start = 4.dp).size(14.dp),
        )
    }
}

@Composable
private fun EmptyBag() {
    Column(
        Modifier.fillMaxSize().background(C.Canvas).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No club data yet", style = T.screenTitle, color = C.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Import a round with tracked shots and every club you hit shows up here, " +
                "measured from your own swings.",
            style = T.bodySmall,
            color = C.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
