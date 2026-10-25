package com.aura.core

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.aura.core.ui.AuraMainScreen
import com.aura.core.ui.AuraMainViewModel

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "AURA Engine v1.0",
        state = WindowState(width = 800.dp, height = 600.dp)
    ) {
        AuraMainScreen(AuraMainViewModel())
    }
}
