package com.projectapex.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.projectapex.desktop.ui.DesktopApp

fun main() = application {
    val container = remember { AppContainer() }
    val windowState = rememberWindowState(
        position = WindowPosition(alignment = Alignment.Center),
        size = DpSize(1100.dp, 720.dp),
    )

    Window(
        onCloseRequest = {
            container.stopAll()
            exitApplication()
        },
        state = windowState,
        title = "Project Apex — Engine Bench",
    ) {
        DesktopApp(container)
    }
}
