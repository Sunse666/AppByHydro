package com.jxau.oj.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jxau.oj.core.AppContainer
import com.jxau.oj.data.net.SessionStore
import com.jxau.oj.ui.nav.AppNavHost
import com.jxau.oj.ui.theme.JxauOjTheme
import com.jxau.oj.ui.theme.LocalEditorFontId
import com.jxau.oj.ui.theme.LocalEditorFontSizeSp

@Composable
fun JxauOjRoot(container: AppContainer) {
    val appVm: AppViewModel = viewModel(initializer = { AppViewModel(container) })
    val state by appVm.state.collectAsStateWithLifecycle()

    val darkTheme = when (state.darkMode) {
        SessionStore.DARK_MODE_LIGHT -> false
        SessionStore.DARK_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }

    JxauOjTheme(themeId = state.themeId, darkTheme = darkTheme) {
        // 代码字体下发给整棵子树：编辑器与只读代码块（评测详情、评测信息）共用同一选择
        CompositionLocalProvider(
            LocalEditorFontId provides state.editorFontId,
            LocalEditorFontSizeSp provides state.editorFontSizeSp,
        ) {
            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(state.message) {
                val message = state.message
                if (message != null) {
                    snackbarHostState.showSnackbar(message)
                    appVm.consumeMessage()
                }
            }

            Box(Modifier.fillMaxSize()) {
                AppNavHost(appVm)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding(),
                )
            }
        }
    }
}
