package com.doculens.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.doculens.app.ui.components.BusyOverlay
import com.doculens.app.ui.screens.DocumentScreen
import com.doculens.app.ui.screens.EditScreen
import com.doculens.app.ui.screens.HistoryScreen
import com.doculens.app.ui.screens.HomeScreen
import com.doculens.app.ui.screens.PdfPreviewScreen
import com.doculens.app.ui.screens.ScannerScreen
import com.doculens.app.ui.screens.SettingsScreen
import com.doculens.app.ui.viewmodel.AppViewModel

@Composable
fun DocuLensRoot(vm: AppViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val current = vm.message
        if (current != null) {
            snackbarHostState.showSnackbar(current)
            vm.consumeMessage()
        }
    }

    BackHandler(enabled = vm.navigation.size > 1) {
        when (val screen = vm.navigation.last()) {
            is Screen.Scanner -> vm.finishScan(screen.docId)
            is Screen.Editor -> {
                vm.closeEditor()
                vm.pop()
            }
            else -> vm.pop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            AnimatedContent(
                targetState = vm.navigation.lastOrNull() ?: Screen.Home,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 14 })
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "screen"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.History -> HistoryScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.Scanner -> ScannerScreen(vm, screen.docId)
                    is Screen.Document -> DocumentScreen(vm, screen.docId)
                    is Screen.Editor -> EditScreen(vm)
                    is Screen.PdfPreview -> PdfPreviewScreen(vm, screen.docId)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        )

        vm.busyMessage?.let { BusyOverlay(it) }
    }
}
