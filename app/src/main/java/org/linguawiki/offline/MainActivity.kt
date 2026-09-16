package org.linguawiki.offline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.linguawiki.offline.ui.LinguaWikiApp
import org.linguawiki.offline.ui.MainViewModel
import org.linguawiki.offline.ui.theme.LinguaWikiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val state = viewModel.state.collectAsStateWithLifecycle().value
            LinguaWikiTheme(
                themeMode = state.themeMode,
                colorPalette = state.colorPalette,
                fontScale = state.fontScale,
                lineSpacing = state.lineSpacing,
            ) {
                LinguaWikiApp(viewModel = viewModel, state = state)
            }
        }
    }
}
