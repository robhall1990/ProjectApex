package com.projectapex.core.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.projectapex.feature.analysis.AnalysisScreen
import com.projectapex.feature.race.RaceScreen
import com.projectapex.feature.settings.SettingsScreen

/**
 * Hosts the three bottom-nav tabs (Race, Analysis, Settings) behind their own
 * nested [NavHost], nested inside the top-level graph declared in [ApexNavHost].
 */
@Composable
fun ApexMainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { ApexBottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ApexBottomDestination.Race.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ApexBottomDestination.Race.route) { RaceScreen() }
            composable(ApexBottomDestination.Analysis.route) { AnalysisScreen() }
            composable(ApexBottomDestination.Settings.route) { SettingsScreen() }
        }
    }
}
