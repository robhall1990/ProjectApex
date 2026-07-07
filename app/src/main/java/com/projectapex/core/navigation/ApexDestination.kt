package com.projectapex.core.navigation

sealed class ApexDestination(val route: String) {
    data object Splash : ApexDestination("splash")
    data object Home : ApexDestination("home")
    data object Race : ApexDestination("race")
    data object Settings : ApexDestination("settings")
}
