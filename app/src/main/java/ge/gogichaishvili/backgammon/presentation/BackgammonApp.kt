package ge.gogichaishvili.backgammon.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.animation.*
import ge.gogichaishvili.backgammon.designsystem.Space
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme
import kotlinx.serialization.Serializable

@Serializable
private data object BoardRoute : NavKey
@Serializable
private data object SettingsRoute : NavKey

@Composable
fun BackgammonApp(
    state: SettingsUiState,
    themes: List<BoardTheme>,
    onTheme: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
) {
    LocalizedContent(state.settings.language) {
        BackgammonTheme(state.theme) {
            val stack = rememberNavBackStack(BoardRoute)
            val inSettings = stack.lastOrNull() == SettingsRoute
            val back = { if (stack.size > 1) stack.removeAt(stack.lastIndex); Unit }
            val openSettings = { if (stack.lastOrNull() != SettingsRoute) stack.add(SettingsRoute); Unit }
            val duration = MotionPolicy(state.settings.animationSpeed, state.settings.reducedMotion)
                .duration(MotionKind.NAVIGATION)
            Scaffold(
                topBar = {
                    Surface {
                        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Space.medium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                            if (inSettings) {
                                TextButton(onClick = back, modifier = Modifier.heightIn(min = Space.minTouch)) {
                                    Text(tr(R.string.back))
                                }
                            }
                            Text(tr(if (inSettings) R.string.settings else R.string.app_name),
                                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            if (!inSettings && state.loaded) {
                                TextButton(onClick = openSettings, modifier = Modifier.heightIn(min = Space.minTouch)) {
                                    Text(tr(R.string.settings))
                                }
                            }
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing,
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (state.saveFailed) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.fillMaxWidth().padding(Space.medium),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(tr(R.string.save_error), Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                                TextButton(onClick = onDismissError) { Text(tr(R.string.dismiss)) }
                            }
                        }
                    }
                    if (!state.loaded || state.readFailed) {
                        Column(Modifier.fillMaxSize().padding(Space.large),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.medium, Alignment.CenterVertically)) {
                            Text(tr(if (state.readFailed) R.string.read_error else R.string.loading))
                            if (state.readFailed) Button(onClick = onRetry) { Text(tr(R.string.retry)) }
                            else CircularProgressIndicator()
                        }
                    } else {
                        NavDisplay(
                            backStack = stack,
                            onBack = back,
                            modifier = Modifier.weight(1f),
                            transitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                            popTransitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                            predictivePopTransitionSpec = { fadeIn(tween(duration)) togetherWith fadeOut(tween(duration)) },
                            entryProvider = { key ->
                                when (key) {
                                    BoardRoute -> NavEntry(key) { BoardScreen(state, openSettings) }
                                    SettingsRoute -> NavEntry(key) {
                                        SettingsScreen(state.settings, themes, onTheme, onUpdate)
                                    }
                                    else -> error("Unknown destination")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
