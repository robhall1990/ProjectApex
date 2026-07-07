package com.projectapex.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class ApexBottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Race : ApexBottomDestination("race", "Race", Icons.Filled.Flag)
    data object Analysis : ApexBottomDestination("analysis", "Analysis", Icons.Filled.Insights)
    data object Settings : ApexBottomDestination("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val items: List<ApexBottomDestination> = listOf(Race, Analysis, Settings)
    }
}
