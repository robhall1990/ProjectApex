package com.projectapex.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
                    navController.navigate(ApexDestination.Main.route) {
                        popUpTo(ApexDestination.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        composable(ApexDestination.Main.route) {
            ApexMainScreen()
        }
    }
}
