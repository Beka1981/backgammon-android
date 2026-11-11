package ge.gogichaishvili.backgammon.presentation.game

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.*
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.domain.settings.*
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.*
import ge.gogichaishvili.backgammon.ui.theme.BackgammonTheme

@Preview(name = "Game phone", widthDp = 393, heightDp = 852)
@Preview(name = "Game landscape", widthDp = 852, heightDp = 393)
@Preview(name = "Game tablet", widthDp = 1280, heightDp = 800)
@Preview(name = "Game large text", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun InteractiveGamePreview(@PreviewParameter(BoardSkinPreviews::class) theme: BoardTheme) {
    val roll = DiceRoll(6, 1)
    val game = BackgammonEngine.transition(BackgammonEngine.newGame(), GameAction.OpeningRoll(roll)).state
    val state = GameUiState(game = game, ready = true, lastRoll = roll, rolledBy = Player.WHITE,
        legalMoves = BackgammonEngine.legalMoves(game), selected = MoveSource.OnPoint(8),
        motion = MotionPolicy(reducedMotion = true))
    LocalizedContent(AppLanguage.ENGLISH) {
        BackgammonTheme(theme) {
            Surface { GameScreen(SettingsUiState(AppSettings(themeId = theme.id), loaded = true), state, {}) }
        }
    }
}
