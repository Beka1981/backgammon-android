package ge.gogichaishvili.backgammon.presentation.game

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ge.gogichaishvili.backgammon.R
import ge.gogichaishvili.backgammon.animation.MotionKind
import ge.gogichaishvili.backgammon.animation.GameMotion
import ge.gogichaishvili.backgammon.designsystem.*
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.*

@Composable
fun GameScreen(
    settings: SettingsUiState, state: GameUiState,
    dispatch: (GameUiAction) -> Unit, modifier: Modifier = Modifier,
) {
    CompositionTrace("BG.Compose.GameScreen")
    val send by rememberUpdatedState(dispatch)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { send(GameUiAction.CancelAnimations) }
    DisposableEffect(Unit) { onDispose { send(GameUiAction.CancelAnimations) } }
    if (!state.ready) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(R.string.game_wait)) }
        return
    }
    val checkerProgress = rememberCheckerProgress(state, dispatch)
    BoxWithConstraints(modifier.fillMaxSize().padding(Space.medium), contentAlignment = Alignment.TopCenter) {
        if (maxWidth >= 700.dp && maxWidth > maxHeight) {
            Row(Modifier.widthIn(max = Space.maxContent).fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Space.large),
                verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    ThemedGameBoard(settings, state, checkerProgress, dispatch,
                        Modifier.width(minOf(maxWidth, maxHeight * BoardGeometry.aspectRatio)))
                }
                Column(Modifier.width(260.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.small)) {
                    GameHeading(settings, state)
                    GameControls(settings, state, dispatch)
                }
            }
        } else {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.medium)) {
                GameHeading(settings, state)
                ThemedGameBoard(settings, state, checkerProgress, dispatch, Modifier.fillMaxWidth())
                GameControls(settings, state, dispatch)
            }
        }
    }
    GameDialogs(state, dispatch)
}

/** Explicit inputs prevent a local, retained content closure from freezing the table.
 * Crossfade's per-skin content also observes the latest board and shared animation owner.
 */
@Composable
private fun ThemedGameBoard(
    settings: SettingsUiState, state: GameUiState, checkerProgress: State<Float>,
    dispatch: (GameUiAction) -> Unit, modifier: Modifier,
) {
    val currentGame by rememberUpdatedState(state)
    val currentSettings by rememberUpdatedState(settings.settings)
    val currentProgress by rememberUpdatedState(checkerProgress)
    val send by rememberUpdatedState(dispatch)
    Crossfade(settings.theme, modifier,
        animationSpec = tween(state.motion.duration(MotionKind.THEME)), label = "Game skin") { theme ->
        InteractiveBoard(currentGame, theme, currentSettings, send, Modifier.fillMaxWidth(), currentProgress)
    }
}

/** One animation owner outside the skin Crossfade: both renderers sample the same frame. */
@Composable
private fun rememberCheckerProgress(state: GameUiState, dispatch: (GameUiAction) -> Unit): State<Float> {
    val effect = state.checkerTransition
    val progress = remember(effect?.id) { Animatable(0f) }
    val send by rememberUpdatedState(dispatch)
    val hit = effect != null && state.game.board.bar[effect.player.opponent] > effect.before.bar[effect.player.opponent]
    val duration = state.motion.duration(when {
        effect?.move?.to == MoveDestination.Off -> MotionKind.BEAR_OFF
        hit -> MotionKind.HIT_TO_BAR
        else -> MotionKind.CHECKER_MOVE
    })
    LaunchedEffect(effect?.id, duration) {
        val active = effect ?: return@LaunchedEffect
        var completed = false
        try {
            if (duration == 0) progress.snapTo(1f) else progress.animateTo(1f, tween(duration))
            completed = true
        } finally {
            send(if (completed) GameUiAction.AnimationFinished(active.id) else GameUiAction.AnimationInterrupted(active.id))
        }
    }
    return progress.asState()
}

@Composable
private fun GameHeading(settings: SettingsUiState, state: GameUiState) {
    Text(tr(R.string.game_local), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary)
    Text(tr(themeName(settings.theme.id)), Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    val status = if (state.game.turn == TurnState.Opening) tr(R.string.game_opening)
        else state.game.result?.let { tr(R.string.game_wins, playerLabel(it.winner)) }
            ?: tr(R.string.game_turn, playerLabel(state.game.currentPlayer))
    Text(status, Modifier.testTag("turn_status").semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun GameControls(settings: SettingsUiState, state: GameUiState, dispatch: (GameUiAction) -> Unit) {
    state.storageIssue?.let { issue ->
        Text(tr(when (issue) {
            GameStorageIssue.LOAD_FAILED -> R.string.match_load_failed
            GameStorageIssue.SAVE_FAILED -> R.string.match_save_failed
            GameStorageIssue.NEWER_SCHEMA -> R.string.match_newer_schema
        }), Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, color = MaterialTheme.colorScheme.error)
        if (issue != GameStorageIssue.NEWER_SCHEMA)
            Button(onClick = { dispatch(GameUiAction.RetryPersistence) }, modifier = Modifier.testTag("retry_match")) {
                Text(tr(R.string.match_retry))
            }
    }
    val notice = when (state.notice) {
        GameNotice.OPENING_TIE -> R.string.game_tie
        GameNotice.PASSED -> R.string.game_passed
        GameNotice.TURN_COMPLETE -> R.string.game_complete
        GameNotice.ILLEGAL_MOVE -> R.string.game_illegal
        GameNotice.RESTORE_FAILED -> R.string.game_restore_failed
        GameNotice.NONE -> R.string.game_select
    }
    val noticeText = tr(if (state.game.result != null) R.string.game_finished else notice)
    Text(noticeText, Modifier.testTag("game_notice").semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    DiceDisplay(settings, state, dispatch)
    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
        if (state.game.turn is TurnState.Complete) {
            Button(onClick = { dispatch(GameUiAction.EndTurn(state.revision)) }, enabled = state.canEndTurn,
                modifier = Modifier.weight(1f).heightIn(min = Space.minTouch).testTag("end_turn")) {
                Text(tr(R.string.game_end_turn))
            }
        } else {
            Button(onClick = { dispatch(GameUiAction.Roll(state.revision)) }, enabled = state.canRoll,
                modifier = Modifier.weight(1f).heightIn(min = Space.minTouch).testTag("roll_dice")) {
                Text(tr(if (state.rollTransition != null) R.string.game_rolling else R.string.game_roll))
            }
        }
        OutlinedButton(onClick = { dispatch(GameUiAction.RequestNewGame) }, enabled = state.storageIssue == null,
            modifier = Modifier.weight(1f).heightIn(min = Space.minTouch).testTag("new_game")) {
            Text(tr(R.string.game_new))
        }
    }
    Player.entries.forEach { player ->
        Text(tr(R.string.game_status, playerLabel(player), state.game.board.bar[player], state.game.board.borneOff[player]),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // Equivalent 48dp+ keyboard/TalkBack controls for narrow physical points on a phone.
    if (state.legalMoves.isNotEmpty()) {
        Text(tr(R.string.game_sources), style = MaterialTheme.typography.labelLarge)
        ControlRows(state.legalMoves.map { it.from }.distinct()) { source ->
            val label = when (source) {
                MoveSource.Bar -> tr(R.string.game_bar_short)
                is MoveSource.OnPoint -> tr(R.string.game_point_short, source.number)
            }
            val actionDescription = tr(R.string.game_select_control, label)
            val sourceTag = when (source) {
                MoveSource.Bar -> "source_bar"
                is MoveSource.OnPoint -> "source_" + source.number
            }
            OutlinedButton(onClick = { dispatch(GameUiAction.Tap(when (source) {
                MoveSource.Bar -> BoardTarget.Bar
                is MoveSource.OnPoint -> BoardTarget.Point(source.number)
            }, state.revision)) }, enabled = !state.busy && state.storageIssue == null, modifier = Modifier.heightIn(min = Space.minTouch)
                .testTag(sourceTag).semantics {
                    selected = state.selected == source
                    contentDescription = actionDescription
                }) { Text(label) }
        }
    }
    if (state.selectedMoves.isNotEmpty()) {
        Text(tr(R.string.game_destinations), style = MaterialTheme.typography.labelLarge)
        ControlRows(state.selectedMoves.map { it.to }.distinct()) { target ->
            val label = when (target) {
                MoveDestination.Off -> tr(R.string.game_off_short)
                is MoveDestination.OnPoint -> tr(R.string.game_point_short, target.number)
            }
            val actionDescription = tr(R.string.game_move_control, label)
            val destinationTag = when (target) {
                MoveDestination.Off -> "destination_off"
                is MoveDestination.OnPoint -> "destination_" + target.number
            }
            Button(onClick = { dispatch(GameUiAction.Tap(when (target) {
                MoveDestination.Off -> if (state.game.currentPlayer == Player.WHITE) BoardTarget.WhiteTray else BoardTarget.BlackTray
                is MoveDestination.OnPoint -> BoardTarget.Point(target.number)
            }, state.revision)) }, enabled = !state.busy && state.storageIssue == null, modifier = Modifier.heightIn(min = Space.minTouch)
                .testTag(destinationTag).semantics { contentDescription = actionDescription }) { Text(label) }
        }
    }
}

@Composable
private fun <T> ControlRows(values: List<T>, item: @Composable (T) -> Unit) {
    // Fixed small rows wrap through chunking, using stable APIs and no experimental Flow/Grid.
    values.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            row.forEach { Box(Modifier.weight(1f)) { item(it) } }
        }
    }
}

@Composable
private fun DiceDisplay(settings: SettingsUiState, state: GameUiState, dispatch: (GameUiAction) -> Unit) {
    CompositionTrace("BG.Compose.DiceDisplay")
    val transition = state.rollTransition
    val progress = remember(transition?.id) { Animatable(0f) }
    val send by rememberUpdatedState(dispatch)
    val duration = state.motion.duration(MotionKind.DICE_ROLL)
    LaunchedEffect(transition?.id, duration) {
        val active = transition ?: return@LaunchedEffect
        var completed = false
        try {
            if (duration == 0) progress.snapTo(1f) else progress.animateTo(1f, tween(duration))
            completed = true
        } finally {
            send(if (completed) GameUiAction.AnimationFinished(active.id) else GameUiAction.AnimationInterrupted(active.id))
        }
    }
    if (state.dice.isEmpty()) return
    Text(if (state.rolledBy == null) tr(R.string.game_opening_dice)
        else tr(R.string.game_dice_owner, playerLabel(state.rolledBy)), style = MaterialTheme.typography.labelLarge)
    FlowRow(modifier = Modifier.fillMaxWidth().testTag("dice_display"),
        horizontalArrangement = Arrangement.spacedBy(Space.small),
        verticalArrangement = Arrangement.spacedBy(Space.small)) {
        state.dice.forEachIndexed { index, die ->
            val status = tr(if (die.consumed) R.string.game_consumed else if (die.available) R.string.game_unused else R.string.game_unplayable)
            val description = tr(R.string.game_die, die.value, status)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.size(48.dp).testTag("die_$index").semantics { contentDescription = description; role = Role.Image }
                    .graphicsLayer {
                        alpha = if (die.consumed) DiceEmphasis.consumed else if (die.available) 1f else DiceEmphasis.unplayable
                        val t = if (transition == null) 1f else progress.value
                        val bounce = GameMotion.diceBounce(t)
                        rotationZ = GameMotion.diceRotation(t, index)
                        translationY = -bounce * 20.dp.toPx()
                        scaleX = 1f + .04f * bounce
                        scaleY = 1f - .03f * bounce
                        shadowElevation = bounce * 6.dp.toPx()
                    }) {
                    scale(size.width / 58f, size.height / 58f, Offset.Zero) {
                        renderTrace("BG.Draw.Dice") { drawDie(Offset(2f, 1f), die.value, settings.theme.palette, settings.settings.diceStyle) }
                    }
                }
                if (die.consumed || !die.available) Text(status, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun GameDialogs(state: GameUiState, dispatch: (GameUiAction) -> Unit) {
    if (state.storageIssue != null) return
    if (state.confirmNewGame) AlertDialog(
        onDismissRequest = { dispatch(GameUiAction.CancelNewGame) },
        title = { Text(tr(R.string.game_new_title)) }, text = { Text(tr(R.string.game_new_body)) },
        confirmButton = { TextButton(onClick = { dispatch(GameUiAction.ConfirmNewGame) },
            modifier = Modifier.testTag("confirm_new_game")) { Text(tr(R.string.game_confirm)) } },
        dismissButton = { TextButton(onClick = { dispatch(GameUiAction.CancelNewGame) }) { Text(tr(R.string.game_cancel)) } })
    else if (state.choices.isNotEmpty()) AlertDialog(
        onDismissRequest = { dispatch(GameUiAction.CancelChoice) }, title = { Text(tr(R.string.game_choose_die)) },
        text = { Column { state.choices.forEach { move ->
            TextButton(onClick = { dispatch(GameUiAction.Play(move, state.revision)) }) { Text(tr(R.string.game_use_die, move.die)) }
        } } }, confirmButton = { TextButton(onClick = { dispatch(GameUiAction.CancelChoice) }) { Text(tr(R.string.game_cancel)) } })
    else if (state.game.result != null && !state.resultDismissed && !state.busy) {
        val result = requireNotNull(state.game.result)
        val kind = tr(when (result.kind) {
            WinKind.SINGLE -> R.string.game_single
            WinKind.GAMMON -> R.string.game_gammon
            WinKind.BACKGAMMON -> R.string.game_backgammon
        })
        AlertDialog(onDismissRequest = { dispatch(GameUiAction.DismissResult) },
            modifier = Modifier.testTag("game_over"),
            title = { Text(tr(R.string.game_wins, playerLabel(result.winner))) },
            text = { Text(trQuantity(R.plurals.game_score, result.points, kind, result.points)) },
            confirmButton = { TextButton(onClick = { dispatch(GameUiAction.RequestNewGame) }) { Text(tr(R.string.game_new)) } },
            dismissButton = { TextButton(onClick = { dispatch(GameUiAction.DismissResult) }) { Text(tr(R.string.game_continue)) } })
    }
}
