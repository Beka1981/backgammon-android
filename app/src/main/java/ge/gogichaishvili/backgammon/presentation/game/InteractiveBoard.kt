package ge.gogichaishvili.backgammon.presentation.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.animation.MotionKind
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.domain.settings.AppSettings
import ge.gogichaishvili.backgammon.domain.theme.BoardTheme
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.tr
import ge.gogichaishvili.backgammon.animation.GameMotion

@Composable
fun playerLabel(player: Player?): String = tr(when (player) {
    Player.WHITE -> R.string.game_white
    Player.BLACK -> R.string.game_black
    null -> R.string.game_empty
})

@Composable
fun InteractiveBoard(
    state: GameUiState, theme: BoardTheme, settings: AppSettings,
    dispatch: (GameUiAction) -> Unit, modifier: Modifier = Modifier,
    checkerProgress: State<Float>,
) {
    CompositionTrace("BG.Compose.InteractiveBoard")
    val effect = state.checkerTransition
    val flights = remember(effect, state.game.board) {
        effect?.let { checkerFlights(it.before, state.game.board, it.move, it.player) } ?: emptyList()
    }
    val send by rememberUpdatedState(dispatch)
    val selection = remember { Animatable(0f) }
    val selectionDuration = state.motion.duration(MotionKind.SELECTION)
    LaunchedEffect(state.selected, selectionDuration) {
        selection.snapTo(0f)
        if (state.selected != null) {
            if (selectionDuration == 0) selection.snapTo(1f)
            else selection.animateTo(1f, tween(selectionDuration))
        }
    }
    val selectedTargets = state.selectedMoves.map { it.to }
    val boardLabel = tr(R.string.game_board)
    BoardCanvas(theme, modifier.testTag("game_board").semantics { contentDescription = boardLabel },
        settings.checkerStyle, settings.diceStyle,
        board = state.game.board,
        diceValues = state.dice.filterNot { it.consumed }.map { it.value },
        hidden = flights.map { it.hidden }) {
        // Only this overlay samples progress per frame; materials and committed checkers do not.
        Canvas(Modifier.fillMaxSize()) {
            renderTrace("BG.Draw.Overlay") {
            scale(size.width / BoardGeometry.width, size.height / BoardGeometry.height, Offset.Zero) {
                val p = theme.palette
                state.selected?.let { source ->
                    val target = when (source) {
                        MoveSource.Bar -> BoardTarget.Bar
                        is MoveSource.OnPoint -> BoardTarget.Point(source.number)
                    }
                    val count = if (source == MoveSource.Bar) state.game.board.bar[requireNotNull(state.game.currentPlayer)]
                        else state.game.board.point((source as MoveSource.OnPoint).number).count
                    val c = checkerVisualCenter(state.game.board, CheckerVisualSlot(
                        requireNotNull(state.game.currentPlayer), target, count - 1))
                    val emphasis = selection.value
                    drawCircle(Color(p.accent).copy(alpha = .12f * emphasis), 36f, Offset(c.x, c.y))
                    drawCircle(Color(p.accent).copy(alpha = emphasis), 29f + 2f * emphasis,
                        Offset(c.x, c.y), style = Stroke(2f + 2f * emphasis))
                }
                selectedTargets.distinct().forEach { target ->
                    val c = when (target) {
                        is MoveDestination.OnPoint -> {
                            val point = BoardGeometry.pointCenter(target.number)
                            BoardCoordinate(point.x, if (target.number >= 13) 286f else 414f)
                        }
                        MoveDestination.Off -> BoardCoordinate(
                            if (state.game.currentPlayer == Player.WHITE) 955f else 45f, 350f)
                    }
                    drawCircle(Color(p.accent).copy(alpha = 0.3f), 23f, Offset(c.x, c.y))
                    drawCircle(Color(p.accent), 18f, Offset(c.x, c.y), style = Stroke(3f))
                }
                flights.forEach { flight ->
                    val t = if (flight.restacking) checkerProgress.value else
                        GameMotion.flight(checkerProgress.value, flight.hit, flights.any { it.hit })
                    val lift = if (flight.restacking) 0f else GameMotion.lift(t)
                    val x = flight.from.x + (flight.to.x - flight.from.x) * t
                    val y = flight.from.y + (flight.to.y - flight.from.y) * t - lift * if (flight.hit) 22f else 36f
                    val radius = if (flight.bearingOff) 26f - 8f * t else 26f
                    drawChecker(Offset(x, y), Color(if (flight.player == Player.WHITE) p.ivory else p.dark),
                        p, settings.checkerStyle, radius * (1f + .06f * lift), elevation = lift)
                }
            }
        }
        }
        BoxWithConstraints(Modifier.fillMaxSize().pointerInput(state.revision, state.busy, state.storageIssue) {
            detectTapGestures { tap ->
                if (!state.busy && state.storageIssue == null) BoardGeometry.hitTest(
                    tap.x / size.width * BoardGeometry.width, tap.y / size.height * BoardGeometry.height
                )?.let { send(GameUiAction.Tap(it, state.revision)) }
            }
        }) {
            val boardWidth = maxWidth
            val boardHeight = maxHeight
            @Composable
            fun targetNode(target: BoardTarget, bounds: BoardRect, label: String, source: MoveSource?, destination: MoveDestination?) {
                val selected = source != null && source == state.selected
                val legal = destination != null && destination in selectedTargets
                val selectable = source != null && state.legalMoves.any { it.from == source }
                val status = when {
                    selected -> tr(R.string.game_selected)
                    legal -> tr(R.string.game_legal)
                    selectable -> tr(R.string.game_source)
                    else -> ""
                }
                val tag = when (target) {
                    is BoardTarget.Point -> "point_" + target.number
                    BoardTarget.Bar -> "bar"
                    BoardTarget.WhiteTray -> "white_tray"
                    BoardTarget.BlackTray -> "black_tray"
                }
                Box(Modifier.offset(boardWidth * (bounds.left / 1000f), boardHeight * (bounds.top / 700f))
                    .size(boardWidth * ((bounds.right - bounds.left) / 1000f), boardHeight * ((bounds.bottom - bounds.top) / 700f))
                    .testTag(tag).semantics {
                        contentDescription = label
                        this.selected = selected
                        stateDescription = status
                        if (!state.busy && state.storageIssue == null && (legal || selectable)) onClick {
                            send(GameUiAction.Tap(target, state.revision)); true
                        }
                    })
            }
            (1..24).forEach { number ->
                val point = state.game.board.point(number)
                targetNode(BoardTarget.Point(number), BoardGeometry.pointBounds(number),
                    tr(R.string.game_point, number, playerLabel(point.owner), point.count),
                    MoveSource.OnPoint(number), MoveDestination.OnPoint(number))
            }
            targetNode(BoardTarget.Bar, BoardGeometry.bar,
                tr(R.string.game_bar, state.game.board.bar.white, state.game.board.bar.black), MoveSource.Bar, null)
            targetNode(BoardTarget.WhiteTray, BoardGeometry.whiteTray,
                tr(R.string.game_off, playerLabel(Player.WHITE), state.game.board.borneOff.white), null,
                if (state.game.currentPlayer == Player.WHITE) MoveDestination.Off else null)
            targetNode(BoardTarget.BlackTray, BoardGeometry.blackTray,
                tr(R.string.game_off, playerLabel(Player.BLACK), state.game.board.borneOff.black), null,
                if (state.game.currentPlayer == Player.BLACK) MoveDestination.Off else null)
        }
    }
}
