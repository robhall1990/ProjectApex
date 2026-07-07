package com.projectapex.core.navigation

sealed class ApexDestination(val route: String) {
    data object Splash : ApexDestination("splash")
    data object Main : ApexDestination("main")
}
