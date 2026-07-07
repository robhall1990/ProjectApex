package com.projectapex.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.projectapex.feature.home.HomeScreen
import com.projectapex.feature.race.RaceScreen
import com.projectapex.feature.settings.SettingsScreen
import com.projectapex.feature.splash.SplashScreen

@Composable
fun ApexNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = ApexDestination.Splash.route
    ) {
        composable(ApexDestination.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(ApexDestination.Home.route) {
                        popUpTo(ApexDestination.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ApexDestination.Home.route) {
            HomeScreen(
                onEnterGarage = { navController.navigate(ApexDestination.Race.route) },
                onOpenSettings = { navController.navigate(ApexDestination.Settings.route) }
            )
        }
        composable(ApexDestination.Race.route) {
            RaceScreen(onBack = { navController.popBackStack() })
        }
        composable(ApexDestination.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
