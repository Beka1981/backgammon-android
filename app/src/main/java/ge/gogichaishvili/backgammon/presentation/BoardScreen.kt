package ge.gogichaishvili.backgammon.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.animation.*
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.engine.BoardGeometry
import ge.gogichaishvili.backgammon.presentation.game.*

@Composable
fun BoardScreen(state: SettingsUiState, onSettings: () -> Unit, modifier: Modifier = Modifier,
    gameState: GameUiState? = null, onGameAction: (GameUiAction) -> Unit = {}) {
    if (gameState != null) {
        GameScreen(state, gameState, onGameAction, modifier)
        return
    }
    val motion = MotionPolicy(state.settings.animationSpeed, state.settings.reducedMotion)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.medium), contentAlignment = Alignment.Center) {
        if (maxWidth >= 700.dp && maxWidth > maxHeight) {
            Row(Modifier.widthIn(max = Space.maxContent).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Space.large),
                verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    val boardWidth = minOf(maxWidth, maxHeight * BoardGeometry.aspectRatio)
                    ThemedBoard(state.theme, state.settings, motion, Modifier.width(boardWidth))
                }
                Column(Modifier.width(240.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.medium)) {
                    BoardHeading(state.theme)
                    BoardDetails(onSettings)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.large),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    Text(tr(R.string.collection), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(tr(R.string.board_title), style = MaterialTheme.typography.displaySmall)
                    Text(tr(R.string.board_subtitle), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ThemedBoard(state.theme, state.settings, motion, Modifier.fillMaxWidth())
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    BoardHeading(state.theme)
                    BoardDetails(onSettings)
                }
            }
        }
    }
}

@Composable
private fun ThemedBoard(theme: BoardTheme, settings: AppSettings, motion: MotionPolicy, modifier: Modifier) {
    Crossfade(theme, modifier, animationSpec = tween(motion.duration(MotionKind.THEME)), label = "Board skin") { skin ->
        BoardCanvas(skin, Modifier.fillMaxWidth(), settings.checkerStyle, settings.diceStyle,
            tr(R.string.board_accessibility))
    }
}

@Composable
private fun BoardHeading(theme: BoardTheme) {
    Text(tr(themeName(theme.id)), style = MaterialTheme.typography.headlineMedium)
    Text(tr(themeDetail(theme.id)), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary)
    Text(tr(R.string.static_notice), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BoardDetails(onSettings: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(Space.medium), verticalArrangement = Arrangement.spacedBy(Space.small)) {
            Text(tr(R.string.materials), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)
            Text(tr(R.string.materials_body), style = MaterialTheme.typography.bodyMedium)
        }
    }
    Button(onClick = onSettings, modifier = Modifier.heightIn(min = Space.minTouch)) {
        Text(tr(R.string.edit_table))
    }
}
