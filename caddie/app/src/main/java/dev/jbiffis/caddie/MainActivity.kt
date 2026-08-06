package dev.jbiffis.caddie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.jbiffis.caddie.ui.BagScreen
import dev.jbiffis.caddie.ui.CaddieTheme
import dev.jbiffis.caddie.ui.ClubDetailScreen
import dev.jbiffis.caddie.ui.ClubsScreen
import dev.jbiffis.caddie.ui.HoleScreen
import dev.jbiffis.caddie.ui.RoundsScreen
import dev.jbiffis.caddie.ui.ScorecardScreen
import dev.jbiffis.caddie.ui.ShotMapScreen
import dev.jbiffis.caddie.ui.SyncScreen
import dev.jbiffis.caddie.ui.design.C
import dev.jbiffis.caddie.ui.design.T

private const val ROUTE_ROUNDS = "rounds"
private const val ROUTE_CLUBS = "clubs"
private const val ROUTE_BAG = "bag"
private const val ROUTE_WATCH = "sync"
private const val ROUTE_CLUB_NAMES = "clubnames"

private class Tab(val route: String, val label: String, val icon: Int, val owns: (String?) -> Boolean)

/**
 * The four tabs. Rounds owns the scorecard, the hole map and the satellite view —
 * they are children of a round, not peers of it, so the tab stays lit while you
 * are inside one.
 */
private val TABS = listOf(
    Tab(ROUTE_ROUNDS, "Rounds", R.drawable.ic_nav_rounds) { r ->
        r == ROUTE_ROUNDS || r?.startsWith("scorecard/") == true ||
            r?.startsWith("shotmap/") == true || r?.startsWith("hole/") == true
    },
    Tab(ROUTE_CLUBS, "Clubs", R.drawable.ic_nav_clubs) { r ->
        r == ROUTE_CLUBS || r?.startsWith("club/") == true
    },
    Tab(ROUTE_BAG, "Bag", R.drawable.ic_nav_bag) { r -> r == ROUTE_BAG || r == ROUTE_CLUB_NAMES },
    Tab(ROUTE_WATCH, "Watch", R.drawable.ic_nav_watch) { r -> r == ROUTE_WATCH },
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CaddieApp

        setContent {
            CaddieTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                // The shot map is the one full-bleed screen: in-round, the map is
                // the app, and it is left by its own back button.
                val showTabs = currentRoute?.startsWith("shotmap/") != true

                Column(Modifier.fillMaxSize().background(C.Canvas)) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        CaddieNavHost(app, nav)
                    }
                    if (showTabs) CaddieTabBar(currentRoute) { route -> nav.switchTab(route) }
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun CaddieNavHost(app: CaddieApp, nav: NavHostController) {
    NavHost(navController = nav, startDestination = ROUTE_ROUNDS) {
        composable(ROUTE_ROUNDS) {
            RoundsScreen(app) { roundId -> nav.navigate("scorecard/$roundId") }
        }
        composable(
            "scorecard/{roundId}",
            arguments = listOf(navArgument("roundId") { type = NavType.LongType }),
        ) { entry ->
            val roundId = entry.arguments!!.getLong("roundId")
            // The drawn shot-by-shot view is the default hole view.
            ScorecardScreen(app, roundId) { hole -> nav.navigate("shotmap/$roundId/$hole") }
        }
        composable(
            "hole/{roundId}/{hole}",
            arguments = listOf(
                navArgument("roundId") { type = NavType.LongType },
                navArgument("hole") { type = NavType.IntType },
            ),
        ) { entry ->
            val roundId = entry.arguments!!.getLong("roundId")
            HoleScreen(
                app,
                roundId,
                entry.arguments!!.getInt("hole"),
                onNavigateHole = { h ->
                    nav.navigate("hole/$roundId/$h") { popUpTo("scorecard/{roundId}") }
                },
                onOpenShotView = { h, shot -> nav.navigate("shotmap/$roundId/$h?shot=$shot") },
            )
        }
        composable(
            "shotmap/{roundId}/{hole}?shot={shot}",
            arguments = listOf(
                navArgument("roundId") { type = NavType.LongType },
                navArgument("hole") { type = NavType.IntType },
                navArgument("shot") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) { entry ->
            val roundId = entry.arguments!!.getLong("roundId")
            ShotMapScreen(
                app,
                roundId,
                entry.arguments!!.getInt("hole"),
                entry.arguments!!.getInt("shot"),
                onNavigateHole = { h ->
                    nav.navigate("shotmap/$roundId/$h") { popUpTo("scorecard/{roundId}") }
                },
                onBack = {
                    if (!nav.popBackStack()) nav.navigate("scorecard/$roundId")
                },
            )
        }
        composable(ROUTE_CLUBS) {
            ClubDetailScreen(app, clubId = null)
        }
        composable(
            "club/{clubId}",
            arguments = listOf(navArgument("clubId") { type = NavType.LongType }),
        ) { entry ->
            ClubDetailScreen(app, entry.arguments!!.getLong("clubId"), onBack = { nav.popBackStack() })
        }
        composable(ROUTE_BAG) {
            BagScreen(
                app,
                onOpenClub = { clubId -> nav.navigate("club/$clubId") },
                onRenameClubs = { nav.navigate(ROUTE_CLUB_NAMES) },
            )
        }
        composable(ROUTE_CLUB_NAMES) { ClubsScreen(app, onBack = { nav.popBackStack() }) }
        composable(ROUTE_WATCH) { SyncScreen(app) }
    }
}

@Composable
private fun CaddieTabBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.TabBar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(C.Hairline))
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 11.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                val active = tab.owns(currentRoute)
                val tint = if (active) C.Green else C.TextSecondary
                Column(
                    Modifier.clickable { onNavigate(tab.route) }.padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(tab.label, style = T.microLabel, color = tint)
                }
            }
        }
    }
}
