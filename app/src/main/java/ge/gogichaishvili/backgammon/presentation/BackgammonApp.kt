package ge.gogichaishvili.backgammon.presentation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import ge.gogichaishvili.backgammon.presentation.auth.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
import ge.gogichaishvili.backgammon.presentation.game.*

@Serializable
private data object BoardRoute : NavKey
@Serializable
private data object SettingsRoute : NavKey
@Serializable
private data object AccountRoute : NavKey

@Composable
fun BackgammonApp(
    state: SettingsUiState,
    themes: List<BoardTheme>,
    onTheme: (String) -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    gameState: GameUiState? = null,
    onGameAction: (GameUiAction) -> Unit = {},
    authUi: AuthUiState? = null,
    onAuthAction: (AuthAction) -> Unit = {},
) {
    // NavEntry content can outlive its entry-provider invocation. Read observable latest
    // values inside the retained entry instead of capturing its initial snapshot.
    val currentState by rememberUpdatedState(state)
    val currentGame by rememberUpdatedState(gameState)
    val currentGameAction by rememberUpdatedState(onGameAction)
    val currentAuth by rememberUpdatedState(authUi)
    val currentAuthAction by rememberUpdatedState(onAuthAction)
    LocalizedContent(state.settings.language) {
        BackgammonTheme(state.theme) {
            val stack = rememberNavBackStack(BoardRoute)
            val inSettings = stack.lastOrNull() == SettingsRoute
            val inAccount = stack.lastOrNull() == AccountRoute
            val openAccount = { if (stack.lastOrNull() != AccountRoute) stack.add(AccountRoute); Unit }
            LaunchedEffect(authUi?.openRequested) {
                if (authUi?.openRequested == true) {
                    openAccount()
                    currentAuthAction(AuthAction.NavigationHandled)
                }
            }
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
                            if (inSettings || inAccount) {
                                TextButton(onClick = back, modifier = Modifier.heightIn(min = Space.minTouch)) {
                                    Text(tr(R.string.back))
                                }
                            }
                            Text(tr(if (inSettings) R.string.settings else if (inAccount) R.string.auth_account else R.string.app_name),
                                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            if (!inSettings && !inAccount && currentAuth != null) {
                                val accountLabel = tr(R.string.auth_account)
                                IconButton(onClick = openAccount, modifier = Modifier.testTag("open_account")
                                    .semantics { contentDescription = accountLabel }) {
                                    val ink = MaterialTheme.colorScheme.primary
                                    Canvas(Modifier.size(24.dp)) {
                                        drawCircle(ink, size.width * .18f, center.copy(y = size.height * .3f))
                                        drawArc(ink, 180f, 180f, useCenter = true,
                                            topLeft = androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .58f),
                                            size = androidx.compose.ui.geometry.Size(size.width * .76f, size.height * .64f))
                                    }
                                }
                            }
                            if (!inSettings && !inAccount && state.loaded) {
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
                                    BoardRoute -> NavEntry(key) { BoardScreen(currentState, openSettings, gameState = currentGame, onGameAction = currentGameAction) }
                                    SettingsRoute -> NavEntry(key) {
                                        SettingsScreen(currentState.settings, themes, onTheme, onUpdate)
                                    }
                                    AccountRoute -> NavEntry(key) {
                                        currentAuth?.let { AuthScreen(it, currentAuthAction, back) }
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
