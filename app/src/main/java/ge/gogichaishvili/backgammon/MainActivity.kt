package ge.gogichaishvili.backgammon

import android.os.Bundle
import android.content.Intent
import androidx.lifecycle.ViewModelProvider
import ge.gogichaishvili.backgammon.presentation.auth.AuthAction
import ge.gogichaishvili.backgammon.presentation.auth.AuthViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.lifecycleScope
import ge.gogichaishvili.backgammon.feedback.AndroidGameFeedback
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.presentation.game.*
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme

class MainActivity : ComponentActivity() {
    private lateinit var feedback: AndroidGameFeedback
    private lateinit var authModel: AuthViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val container = (application as BackgammonApplication).container
        feedback = AndroidGameFeedback(applicationContext, window.decorView, lifecycleScope)
        authModel = ViewModelProvider(this, viewModelFactory {
            initializer { AuthViewModel(container.auth) }
        })[AuthViewModel::class.java]
        handleAuthIntent(intent)
        val settingsFactory = viewModelFactory {
            initializer { SettingsViewModel(container.settings, container.themes) }
        }
        val gameFactory = viewModelFactory {
            initializer { GameViewModel(createSavedStateHandle(), matches = container.matches) }
        }
        setContent {
            val settingsModel: SettingsViewModel = viewModel(factory = settingsFactory)
            val gameModel: GameViewModel = viewModel(factory = gameFactory)
            val settings by settingsModel.state.collectAsStateWithLifecycle()
            val game by gameModel.state.collectAsStateWithLifecycle()
            val auth by authModel.state.collectAsStateWithLifecycle()
            SideEffect { if (settings.loaded) feedback.configure(settings.settings) }
            LaunchedEffect(gameModel) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    gameModel.feedback.collect { cue -> feedback.play(cue, settingsModel.state.value.settings) }
                }
            }
            LaunchedEffect(settings.settings.animationSpeed, settings.settings.reducedMotion) {
                gameModel.dispatch(GameUiAction.SetMotion(MotionPolicy(
                    settings.settings.animationSpeed, settings.settings.reducedMotion)))
            }
            BackgammonApp(settings, settingsModel.availableThemes, settingsModel::selectTheme,
                settingsModel::update, settingsModel::retryRead, settingsModel::dismissSaveError,
                game, gameModel::dispatch, auth, authModel::dispatch)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(incoming: Intent) {
        // Preserve normal launch identity for lifecycle/restoration observers.
        if (incoming.action != Intent.ACTION_VIEW) return
        // Never forward nested intents/extras/URI permissions or import supplied bearer tokens.
        val callback = incoming.dataString
        setIntent(Intent(this, MainActivity::class.java)) // Remove one-time code from retained activity intent.
        if (callback != null && callback.length <= 4096)
            authModel.dispatch(AuthAction.Callback(callback))
    }

    override fun onStop() { if (::feedback.isInitialized) feedback.stop(); super.onStop() }
    override fun onDestroy() { if (::feedback.isInitialized) feedback.close(); super.onDestroy() }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BackgammonTheme {
        Greeting("Android")
    }
}