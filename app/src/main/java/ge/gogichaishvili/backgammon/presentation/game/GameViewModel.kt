package ge.gogichaishvili.backgammon.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.domain.match.*
import ge.gogichaishvili.backgammon.domain.feedback.FeedbackCue
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameViewModel(
    private val savedState: SavedStateHandle,
    private val dice: LocalDice = LocalDice(),
    private val worker: CoroutineDispatcher = Dispatchers.Default,
    private val initialGame: GameState = BackgammonEngine.newGame(),
    private val visualTimeoutMillis: Long = 15000,
    private val matches: MatchRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()
    private val actions = Channel<GameUiAction>(Channel.UNLIMITED)
    private var visualWatchdog: Job? = null
    private var watchedAnimation: Long? = null
    private var pendingCommit: GameUiState? = null
    private val feedbackEvents = MutableSharedFlow<FeedbackCue>(extraBufferCapacity = 16)
    val feedback: SharedFlow<FeedbackCue> = feedbackEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            publish(restore())
            for (action in actions) {
                val previous = mutableState.value
                if (action == GameUiAction.RetryPersistence) {
                    val pending = pendingCommit
                    if (pending == null) {
                        if (previous.storageIssue == GameStorageIssue.LOAD_FAILED)
                            publish(restore().copy(motion = previous.motion))
                    } else {
                        val revision = previous.revision + 1
                        accept(previous, pending.copy(revision = revision, storageIssue = null, motion = previous.motion,
                            checkerTransition = pending.checkerTransition?.copy(id = revision),
                            rollTransition = pending.rollTransition?.copy(id = revision)))
                    }
                    continue
                }
                if (action == GameUiAction.CancelAnimations)
                    pendingCommit = pendingCommit?.copy(checkerTransition = null, rollTransition = null)
                val next = withContext(worker) { reduce(previous, action) }
                if (next != previous) accept(previous, next)
            }
        }
    }

    fun dispatch(action: GameUiAction) { actions.trySend(action) }

    private fun checkpoint(s: GameUiState): MatchRecord {
        val turn = s.game.turn
        val roll = when (turn) {
            is TurnState.Moving -> turn.roll
            is TurnState.Complete -> turn.roll
            else -> s.lastRoll
        }
        return MatchRecord(s.game, roll, (turn as? TurnState.Moving)?.remainingDice ?: s.unspentDice,
            s.rolledBy, s.resultDismissed)
    }

    private suspend fun restore(): GameUiState {
        val raw = savedState.get<Any?>(GameSnapshot.KEY)
        val baseline = withContext(worker) {
            val decoded = (raw as? IntArray)?.let(GameSnapshot::decode)
            val valid = decoded?.takeIf { runCatching { checkpoint(it).validated() }.isSuccess }
            valid ?: forGame(GameUiState(), initialGame).copy(
                ready = true, notice = if (raw == null) GameNotice.NONE else GameNotice.RESTORE_FAILED)
        }
        val repository = matches ?: return baseline
        return try {
            val loaded = repository.load()
            if (loaded.unsupportedVersion != null)
                return baseline.copy(storageIssue = GameStorageIssue.NEWER_SCHEMA, selected = null)
            val record = loaded.record
            if (record == null) {
                val fresh = if (loaded.recoveredCorruption)
                    forGame(GameUiState(ready = true), BackgammonEngine.newGame()).copy(notice = GameNotice.RESTORE_FAILED)
                    else baseline
                repository.save(checkpoint(fresh))
                fresh
            } else withContext(worker) {
                val same = baseline.game == record.game && baseline.lastRoll == record.roll
                val restored = forGame(GameUiState(ready = true), record.game).copy(
                    lastRoll = record.roll, unspentDice = record.remainingDice, rolledBy = record.rolledBy,
                    resultDismissed = record.resultDismissed)
                restored.copy(selected = baseline.selected.takeIf { same }, confirmNewGame = same && baseline.confirmNewGame)
            }
        } catch (_: IOException) {
            baseline.copy(storageIssue = GameStorageIssue.LOAD_FAILED, selected = null)
        }
    }

    private suspend fun accept(previous: GameUiState, next: GameUiState) {
        val changed = matches != null && checkpoint(previous) != checkpoint(next)
        if (changed) {
            try { requireNotNull(matches).save(checkpoint(next)) }
            catch (_: IOException) {
                pendingCommit = next
                publish(previous.copy(storageIssue = GameStorageIssue.SAVE_FAILED, selected = null,
                    checkerTransition = null, rollTransition = null, revision = previous.revision + 1))
                return
            }
        }
        if (next.storageIssue == null) pendingCommit = null
        publish(next)
        // No replay: collectors exist only while the activity is resumed. Restoration emits nothing.
        when {
            next.rollTransition != null && previous.rollTransition?.id != next.rollTransition.id ->
                feedbackEvents.tryEmit(FeedbackCue.DICE)
            next.selected != null && next.selected != previous.selected ->
                feedbackEvents.tryEmit(FeedbackCue.PICKUP)
        }
        val finished = previous.checkerTransition
        if (finished != null && next.checkerTransition == null && previous.game == next.game &&
            previous.storageIssue == null && next.storageIssue == null) {
            // Lifecycle cancellation must not make sound in a new/paused screen.
            // Only an acknowledged landing sets this marker in reduce().
            if (landingAcknowledged) {
                feedbackEvents.tryEmit(when {
                    next.game.board.bar[finished.player.opponent] > finished.before.bar[finished.player.opponent] -> FeedbackCue.HIT
                    finished.move.to == MoveDestination.Off -> FeedbackCue.BEAR_OFF
                    else -> FeedbackCue.PLACE
                })
                if (next.game.result != null) feedbackEvents.tryEmit(FeedbackCue.VICTORY)
            }
        }
        landingAcknowledged = false
    }

    private var landingAcknowledged = false

    private fun publish(state: GameUiState) {
        // Persist only committed engine data; restoration intentionally snaps any transient animation.
        savedState[GameSnapshot.KEY] = GameSnapshot.encode(state)
        mutableState.value = state
        val id = state.checkerTransition?.id ?: state.rollTransition?.id
        if (id != watchedAnimation) {
            visualWatchdog?.cancel()
            watchedAnimation = id
            visualWatchdog = if (id == null) null else viewModelScope.launch {
                // A paused/lost frame clock must never lock input indefinitely.
                // Expiration only discards visual state; the engine was committed beforehand.
                delay(visualTimeoutMillis)
                dispatch(GameUiAction.AnimationInterrupted(id))
            }
        }
    }

    private fun forGame(s: GameUiState, game: GameState): GameUiState = s.copy(
        game = game, legalMoves = BackgammonEngine.legalMoves(game),
        selected = null, choices = emptyList(), revision = s.revision + 1,
    )

    private fun reduce(s: GameUiState, a: GameUiAction): GameUiState {
        landingAcknowledged = a is GameUiAction.AnimationFinished && s.checkerTransition?.id == a.id
        if (s.storageIssue != null && a !is GameUiAction.SetMotion && a !is GameUiAction.AnimationFinished && a !is GameUiAction.AnimationInterrupted &&
            a != GameUiAction.CancelAnimations) return s
        when (a) {
            is GameUiAction.SetMotion -> return s.copy(motion = a.policy)
            is GameUiAction.AnimationFinished -> return s.copy(
                checkerTransition = s.checkerTransition?.takeUnless { it.id == a.id },
                rollTransition = s.rollTransition?.takeUnless { it.id == a.id })
            is GameUiAction.AnimationInterrupted -> return s.copy(
                checkerTransition = s.checkerTransition?.takeUnless { it.id == a.id },
                rollTransition = s.rollTransition?.takeUnless { it.id == a.id })
            GameUiAction.CancelAnimations -> return s.copy(checkerTransition = null, rollTransition = null)
            GameUiAction.RequestNewGame -> return s.copy(confirmNewGame = true, choices = emptyList())
            GameUiAction.CancelNewGame -> return s.copy(confirmNewGame = false)
            GameUiAction.ConfirmNewGame -> return if (!s.confirmNewGame) s else
                forGame(GameUiState(motion = s.motion, revision = s.revision, ready = true), BackgammonEngine.newGame())
            GameUiAction.DismissResult -> return s.copy(resultDismissed = true)
            GameUiAction.CancelChoice -> return s.copy(choices = emptyList(), revision = s.revision + 1)
            else -> Unit
        }
        val revision = when (a) {
            is GameUiAction.Roll -> a.revision
            is GameUiAction.Tap -> a.revision
            is GameUiAction.Play -> a.revision
            is GameUiAction.EndTurn -> a.revision
            else -> return s
        }
        if (!s.ready || s.busy || s.confirmNewGame || revision != s.revision) return s
        return when (a) {
            is GameUiAction.Roll -> {
                if (!s.canRoll) s else {
                    val roll = dice.roll()
                    val opening = s.game.turn == TurnState.Opening
                    val action = if (opening) GameAction.OpeningRoll(roll) else GameAction.ApplyDice(roll)
                    val outcome = BackgammonEngine.transition(s.game, action)
                    if (outcome !is ActionResult.Accepted) s else {
                        val next = forGame(s, outcome.state)
                        next.copy(lastRoll = roll, unspentDice = roll.uses, rolledBy = if (opening) outcome.state.currentPlayer else s.game.currentPlayer,
                            rollTransition = RollTransition(next.revision), notice = when {
                                outcome.state.turn == TurnState.Opening -> GameNotice.OPENING_TIE
                                outcome.state.turn is TurnState.AwaitingRoll -> GameNotice.PASSED
                                else -> GameNotice.NONE
                            })
                    }
                }
            }
            is GameUiAction.Tap -> tap(s, a.target)
            is GameUiAction.Play -> play(s, a.move)
            is GameUiAction.EndTurn -> {
                val result = BackgammonEngine.transition(s.game, GameAction.EndTurn)
                if (result is ActionResult.Accepted) forGame(s, result.state).copy(
                    lastRoll = null, unspentDice = emptyList(), rolledBy = null, notice = GameNotice.NONE) else s
            }
            else -> s
        }
    }

    private fun tap(s: GameUiState, target: BoardTarget): GameUiState {
        if (target is BoardTarget.Point && target.number !in 1..24) return s.copy(notice = GameNotice.ILLEGAL_MOVE)
        val destination = when (target) {
            is BoardTarget.Point -> MoveDestination.OnPoint(target.number)
            BoardTarget.WhiteTray -> if (s.game.currentPlayer == Player.WHITE) MoveDestination.Off else null
            BoardTarget.BlackTray -> if (s.game.currentPlayer == Player.BLACK) MoveDestination.Off else null
            BoardTarget.Bar -> null
        }
        val choices = s.selectedMoves.filter { it.to == destination }
        if (choices.size == 1) return play(s, choices.single())
        if (choices.size > 1) return s.copy(choices = frozenList(choices), revision = s.revision + 1)
        val source = when (target) {
            is BoardTarget.Point -> MoveSource.OnPoint(target.number)
            BoardTarget.Bar -> MoveSource.Bar
            else -> null
        }
        return if (source != null && s.legalMoves.any { it.from == source }) s.copy(
            selected = source.takeUnless { it == s.selected }, choices = emptyList(),
            revision = s.revision + 1, notice = GameNotice.NONE)
        else s.copy(notice = GameNotice.ILLEGAL_MOVE)
    }

    private fun play(s: GameUiState, move: CheckerMove): GameUiState {
        if (move.from != s.selected || move !in s.legalMoves) return s.copy(notice = GameNotice.ILLEGAL_MOVE)
        val result = BackgammonEngine.transition(s.game, GameAction.Move(move))
        if (result !is ActionResult.Accepted) return s.copy(notice = GameNotice.ILLEGAL_MOVE)
        val remaining = (s.game.turn as TurnState.Moving).remainingDice.toMutableList().apply { remove(move.die) }
        val next = forGame(s, result.state)
        return next.copy(unspentDice = frozenList(remaining), checkerTransition = CheckerTransition(next.revision, s.game.board, move,
            requireNotNull(s.game.currentPlayer)), notice =
            if (result.state.turn is TurnState.Complete) GameNotice.TURN_COMPLETE else GameNotice.NONE)
    }

    override fun onCleared() { actions.close(); super.onCleared() }
}
