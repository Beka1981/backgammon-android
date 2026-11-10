package ge.gogichaishvili.backgammon

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ge.gogichaishvili.backgammon.presentation.BackgammonApp
import ge.gogichaishvili.backgammon.presentation.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val container = (application as BackgammonApplication).container
        val factory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.themes) }
        }
        setContent {
            val model: SettingsViewModel = viewModel(factory = factory)
            val state by model.state.collectAsStateWithLifecycle()
            BackgammonApp(state, model.availableThemes, model::selectTheme, model::update,
                model::retryRead, model::dismissSaveError)
        }
    }
}
