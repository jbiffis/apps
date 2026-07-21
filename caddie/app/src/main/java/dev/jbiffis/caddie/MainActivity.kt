package dev.jbiffis.caddie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import dev.jbiffis.caddie.ui.CaddieTheme
import dev.jbiffis.caddie.ui.ClubDetailScreen
import dev.jbiffis.caddie.ui.ClubsScreen
import dev.jbiffis.caddie.ui.HoleScreen
import dev.jbiffis.caddie.ui.RoundsScreen
import dev.jbiffis.caddie.ui.ScorecardScreen
import dev.jbiffis.caddie.ui.ShotMapScreen
import dev.jbiffis.caddie.ui.StatsScreen
import dev.jbiffis.caddie.ui.SyncScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as CaddieApp

        setContent {
            CaddieTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route

                val tabs = listOf(
                    Triple("rounds", "Rounds", Icons.Filled.GolfCourse),
                    Triple("stats", "Clubs", Icons.Filled.Insights),
                    Triple("clubs", "Bag", Icons.Filled.SportsGolf),
                    Triple("sync", "Watch", Icons.Filled.Bluetooth),
                )

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEach { (route, label, icon) ->
                                NavigationBarItem(
                                    selected = currentRoute == route,
                                    onClick = {
                                        nav.navigate(route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                ) { padding ->
                    NavHost(
                        navController = nav,
                        startDestination = "rounds",
                        modifier = Modifier.padding(padding),
                    ) {
                        composable("rounds") {
                            RoundsScreen(app) { roundId -> nav.navigate("scorecard/$roundId") }
                        }
                        composable(
                            "scorecard/{roundId}",
                            arguments = listOf(navArgument("roundId") { type = NavType.LongType }),
                        ) { entry ->
                            val roundId = entry.arguments!!.getLong("roundId")
                            // Drawn shot-by-shot view is the default hole view
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
                                    nav.navigate("hole/$roundId/$h") {
                                        popUpTo("scorecard/{roundId}")
                                    }
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
                                    nav.navigate("shotmap/$roundId/$h") {
                                        popUpTo("scorecard/{roundId}")
                                    }
                                },
                                onOpenSatellite = { h -> nav.navigate("hole/$roundId/$h") },
                            )
                        }
                        composable("stats") {
                            StatsScreen(app, onOpenClub = { clubId -> nav.navigate("club/$clubId") })
                        }
                        composable(
                            "club/{clubId}",
                            arguments = listOf(navArgument("clubId") { type = NavType.LongType }),
                        ) { entry ->
                            ClubDetailScreen(app, entry.arguments!!.getLong("clubId"), onBack = { nav.popBackStack() })
                        }
                        composable("clubs") { ClubsScreen(app) }
                        composable("sync") { SyncScreen(app) }
                    }
                }
            }
        }
    }
}
