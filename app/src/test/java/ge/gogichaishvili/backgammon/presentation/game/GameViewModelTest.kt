package ge.gogichaishvili.backgammon.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.designsystem.checkerFlights
import ge.gogichaishvili.backgammon.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var count = 0
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }

    private fun model(
        board: BoardState = position(mapOf(8 to 1)), values: List<Int> = listOf(1, 2),
        handle: SavedStateHandle = SavedStateHandle(), game: GameState = BackgammonEngine.fromPosition(board, Player.WHITE),
    ): GameViewModel {
        val queue = ArrayDeque(values)
        return GameViewModel(handle, LocalDice(DieSource { queue.removeFirst() }), dispatcher, game,
            visualTimeoutMillis = Long.MAX_VALUE)
            .also { models.put("model_" + count++, it) }
    }
    private fun GameViewModel.finish() {
        val s = state.value
        val id = s.checkerTransition?.id ?: s.rollTransition?.id ?: return
        dispatch(GameUiAction.AnimationFinished(id))
    }
    private fun GameViewModel.roll() { dispatch(GameUiAction.Roll(state.value.revision)) }
    private fun GameViewModel.tap(n: Int) { dispatch(GameUiAction.Tap(BoardTarget.Point(n), state.value.revision)) }

    @Test fun initializationAndOpeningRollUseEngineOwnership() = runTest {
        val vm = model(values = listOf(6, 1), game = BackgammonEngine.newGame())
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        assertEquals(TurnState.Opening, vm.state.value.game.turn)
        vm.roll(); advanceUntilIdle()
        assertEquals(Player.WHITE, vm.state.value.game.currentPlayer)
        assertEquals(DiceRoll(6, 1), vm.state.value.lastRoll)
        assertEquals(listOf(6, 1), vm.state.value.dice.map { it.value })
        assertTrue(vm.state.value.busy)
    }

    @Test fun openingTieRequiresAnotherExplicitRoll() = runTest {
        val vm = model(values = listOf(2, 2, 1, 5), game = BackgammonEngine.newGame())
        advanceUntilIdle(); vm.roll(); advanceUntilIdle()
        assertEquals(GameNotice.OPENING_TIE, vm.state.value.notice)
        assertEquals(2, vm.state.value.dice.size)
        assertFalse(vm.state.value.canRoll)
        vm.finish(); advanceUntilIdle(); vm.roll(); advanceUntilIdle()
        assertEquals(Player.BLACK, vm.state.value.game.currentPlayer)
    }

    @Test fun repeatedRollsAndTapsAreDeduplicatedByRevision() = runTest {
        val vm = model()
        advanceUntilIdle()
        val revision = vm.state.value.revision
        vm.dispatch(GameUiAction.Roll(revision)); vm.dispatch(GameUiAction.Roll(revision))
        advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        val selectedRevision = vm.state.value.revision
        vm.dispatch(GameUiAction.Tap(BoardTarget.Point(8), selectedRevision))
        vm.dispatch(GameUiAction.Tap(BoardTarget.Point(8), selectedRevision))
        advanceUntilIdle()
        assertEquals(MoveSource.OnPoint(8), vm.state.value.selected)
        val destinationRevision = vm.state.value.revision
        vm.dispatch(GameUiAction.Tap(BoardTarget.Point(7), destinationRevision))
        vm.dispatch(GameUiAction.Tap(BoardTarget.Point(7), destinationRevision))
        advanceUntilIdle()
        assertEquals(Point(Player.WHITE, 1), vm.state.value.game.board.point(7))
        assertEquals(listOf(2), (vm.state.value.game.turn as TurnState.Moving).remainingDice)
    }

    @Test fun selectionAndHighlightsComeExactlyFromEngine() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        assertEquals(MoveSource.OnPoint(8), vm.state.value.selected)
        assertEquals(setOf(MoveDestination.OnPoint(7), MoveDestination.OnPoint(6)),
            vm.state.value.selectedMoves.map { it.to }.toSet())
        assertEquals(BackgammonEngine.legalMoves(vm.state.value.game), vm.state.value.legalMoves)
    }

    @Test fun selectionCanChangeOrBeCleared() = runTest {
        val vm = model(board = BoardState.initial())
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(13); advanceUntilIdle()
        assertEquals(MoveSource.OnPoint(13), vm.state.value.selected)
        vm.tap(13); advanceUntilIdle()
        assertNull(vm.state.value.selected)
    }

    @Test fun illegalTargetsAndIllegalMoveActionsLeaveEngineStateUntouched() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        val game = vm.state.value.game
        listOf(0, 25, 12).forEach { vm.tap(it); advanceUntilIdle() }
        vm.dispatch(GameUiAction.Play(move(8, 5, 3), vm.state.value.revision)); advanceUntilIdle()
        assertEquals(game, vm.state.value.game)
        assertEquals(GameNotice.ILLEGAL_MOVE, vm.state.value.notice)
        assertEquals(MoveSource.OnPoint(8), vm.state.value.selected)
    }

    @Test fun committedMoveClearsSelectionAndMarksConsumedDice() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(7); advanceUntilIdle()
        assertNull(vm.state.value.selected)
        assertEquals(listOf(DisplayDie(1, true), DisplayDie(2, false)), vm.state.value.dice)
        assertTrue(vm.state.value.busy)
        assertEquals(Point(Player.WHITE, 1), vm.state.value.game.board.point(7))
    }

    @Test fun movementGateRejectsInputUntilMatchingAnimationCompletes() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        assertNull(vm.state.value.selected)
        vm.dispatch(GameUiAction.AnimationFinished(-1)); advanceUntilIdle()
        assertTrue(vm.state.value.busy)
        vm.finish(); advanceUntilIdle(); vm.tap(8); advanceUntilIdle()
        assertNotNull(vm.state.value.selected)
    }

    @Test fun cancellationSnapsToCommittedPositionWithoutReplayingTheMove() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(7); advanceUntilIdle()
        val committed = vm.state.value.game
        vm.dispatch(GameUiAction.CancelAnimations); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
        assertEquals(committed, vm.state.value.game)
        vm.tap(7); advanceUntilIdle()
        assertEquals(MoveSource.OnPoint(7), vm.state.value.selected)
    }

    @Test fun barSelectionAndEntryUseTheEngine() = runTest {
        val vm = model(board = position(mapOf(8 to 1), whiteBar = 1))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        assertNull(vm.state.value.selected)
        vm.dispatch(GameUiAction.Tap(BoardTarget.Bar, vm.state.value.revision)); advanceUntilIdle()
        assertEquals(MoveSource.Bar, vm.state.value.selected)
        vm.tap(24); advanceUntilIdle()
        assertEquals(0, vm.state.value.game.board.bar.white)
        assertEquals(Point(Player.WHITE, 1), vm.state.value.game.board.point(24))
    }

    @Test fun ambiguousBearingOffAsksWhichDieAndFinishesWithCorrectScore() = runTest {
        val vm = model(board = position(mapOf(1 to 2)))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(1); advanceUntilIdle()
        vm.dispatch(GameUiAction.Tap(BoardTarget.WhiteTray, vm.state.value.revision)); advanceUntilIdle()
        assertEquals(setOf(1, 2), vm.state.value.choices.map { it.die }.toSet())
        val choice = vm.state.value.choices.first { it.die == 2 }
        vm.dispatch(GameUiAction.Play(choice, vm.state.value.revision)); advanceUntilIdle()
        assertEquals(14, vm.state.value.game.board.borneOff.white)
        vm.finish(); advanceUntilIdle(); vm.tap(1); advanceUntilIdle()
        vm.dispatch(GameUiAction.Tap(BoardTarget.WhiteTray, vm.state.value.revision)); advanceUntilIdle()
        assertEquals(GameResult(Player.WHITE, WinKind.GAMMON), vm.state.value.game.result)
        assertTrue(vm.state.value.busy)
        vm.finish(); advanceUntilIdle()
        assertFalse(vm.state.value.busy)
    }

    @Test fun doublesExposeFourDiceAndRequireFourMoves() = runTest {
        val vm = model(board = position(mapOf(20 to 1), mapOf(1 to 15)), values = listOf(2, 2))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        assertEquals(4, vm.state.value.dice.size)
        listOf(20 to 18, 18 to 16, 16 to 14, 14 to 12).forEach { (from, to) ->
            vm.tap(from); advanceUntilIdle(); vm.tap(to); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        }
        assertTrue(vm.state.value.canEndTurn)
        assertTrue(vm.state.value.dice.all { it.consumed })
        assertFalse(vm.state.value.canRoll)
    }

    @Test fun blockedRollPassesAndNormalCompletedTurnRequiresEndTurn() = runTest {
        val blocked = model(board = position(emptyMap(), (19..24).associateWith { 2 } + (12 to 3), whiteBar = 1))
        advanceUntilIdle(); blocked.roll(); advanceUntilIdle()
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), blocked.state.value.game.turn)
        assertEquals(GameNotice.PASSED, blocked.state.value.notice)
        blocked.finish(); advanceUntilIdle()
        assertTrue(blocked.state.value.canRoll)
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(7); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(7); advanceUntilIdle(); vm.tap(5); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.dispatch(GameUiAction.EndTurn(vm.state.value.revision)); advanceUntilIdle()
        assertEquals(TurnState.AwaitingRoll(Player.BLACK), vm.state.value.game.turn)
        assertTrue(vm.state.value.canRoll)
        assertTrue(vm.state.value.dice.isEmpty())
    }

    @Test fun hitOverlayHidesBothFinalArrivalSlotsWithoutDuplicatingCheckers() = runTest {
        val vm = model(board = position(mapOf(8 to 1), mapOf(5 to 1, 12 to 14)), values = listOf(3, 1))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(5); advanceUntilIdle()
        val s = vm.state.value
        val effect = requireNotNull(s.checkerTransition)
        val flights = checkerFlights(effect.before, s.game.board, effect.move, effect.player)
        assertEquals(2, flights.size)
        assertEquals(2, flights.map { it.hidden }.distinct().size)
        assertTrue(flights.first().hit)
        assertEquals(1, s.game.board.bar.black)
    }

    @Test fun newGameNeedsConfirmationAndClearsEffects() = runTest {
        val vm = model()
        advanceUntilIdle(); vm.roll(); advanceUntilIdle()
        val original = vm.state.value.game
        vm.dispatch(GameUiAction.ConfirmNewGame); advanceUntilIdle()
        assertEquals(original, vm.state.value.game)
        vm.dispatch(GameUiAction.RequestNewGame); advanceUntilIdle()
        vm.dispatch(GameUiAction.CancelNewGame); advanceUntilIdle()
        assertEquals(original, vm.state.value.game)
        vm.dispatch(GameUiAction.RequestNewGame); vm.dispatch(GameUiAction.ConfirmNewGame); advanceUntilIdle()
        assertEquals(TurnState.Opening, vm.state.value.game.turn)
        assertEquals(BoardState.initial(), vm.state.value.game.board)
        assertFalse(vm.state.value.busy)
        assertTrue(vm.state.value.dice.isEmpty())
    }

    @Test fun savedStateRestoresSelectionBoardRollAndRemainingDice() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        val restored = model(handle = SavedStateHandle(mapOf(GameSnapshot.KEY to handle.get<IntArray>(GameSnapshot.KEY))))
        advanceUntilIdle()
        assertEquals(vm.state.value.game, restored.state.value.game)
        assertEquals(vm.state.value.selected, restored.state.value.selected)
        assertEquals(vm.state.value.dice, restored.state.value.dice)
        assertFalse(restored.state.value.busy)
    }

    @Test fun restorationDuringMoveUsesCommittedBoardAndUnlocksInput() = runTest {
        val handle = SavedStateHandle()
        val vm = model(handle = handle)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle(); vm.tap(7); advanceUntilIdle()
        val restored = model(handle = SavedStateHandle(mapOf(GameSnapshot.KEY to handle.get<IntArray>(GameSnapshot.KEY))))
        advanceUntilIdle()
        assertEquals(vm.state.value.game, restored.state.value.game)
        assertFalse(restored.state.value.busy)
        restored.tap(7); advanceUntilIdle()
        assertNotNull(restored.state.value.selected)
    }

    @Test fun malformedSavedStateFallsBackSafely() = runTest {
        val vm = model(handle = SavedStateHandle(mapOf(GameSnapshot.KEY to intArrayOf(99))))
        advanceUntilIdle()
        assertTrue(vm.state.value.ready)
        assertEquals(GameNotice.RESTORE_FAILED, vm.state.value.notice)
    }

    @Test fun reducedMotionChangesVisualPolicyWithoutChangingGameRules() = runTest {
        val vm = model()
        advanceUntilIdle()
        val game = vm.state.value.game
        vm.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = true))); advanceUntilIdle()
        assertEquals(game, vm.state.value.game)
        assertTrue(vm.state.value.motion.reducedMotion)
    }

    @Test fun blockedLeftoverDieIsUnplayableRatherThanFalselyMarkedUsed() = runTest {
        val vm = model(board = position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.dispatch(GameUiAction.Tap(BoardTarget.Bar, vm.state.value.revision)); advanceUntilIdle()
        vm.tap(23); advanceUntilIdle()
        assertEquals(listOf(DisplayDie(1, false, false), DisplayDie(2, true, false)), vm.state.value.dice)
        val restored = GameSnapshot.decode(GameSnapshot.encode(vm.state.value))
        assertEquals(vm.state.value.dice, requireNotNull(restored).dice)
    }

    @Test fun unusedDiceAtVictoryAreNotMarkedConsumed() = runTest {
        val vm = model(board = position(mapOf(1 to 1)))
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(1); advanceUntilIdle()
        vm.dispatch(GameUiAction.Tap(BoardTarget.WhiteTray, vm.state.value.revision)); advanceUntilIdle()
        assertNotNull(vm.state.value.game.result)
        assertEquals(listOf(DisplayDie(1, false, false), DisplayDie(2, true, false)), vm.state.value.dice)
    }

    @Test fun blackCanMoveAndBearOffUsingItsOwnTray() = runTest {
        val initial = BoardState.initial()
        val moving = model(board = initial, game = BackgammonEngine.fromPosition(initial, Player.BLACK))
        advanceUntilIdle(); moving.roll(); advanceUntilIdle(); moving.finish(); advanceUntilIdle()
        moving.tap(1); advanceUntilIdle(); moving.tap(2); advanceUntilIdle()
        assertEquals(Point(Player.BLACK, 1), moving.state.value.game.board.point(2))
        val home = position(mapOf(13 to 15), mapOf(24 to 1))
        val bearing = model(board = home, game = BackgammonEngine.fromPosition(home, Player.BLACK))
        advanceUntilIdle(); bearing.roll(); advanceUntilIdle(); bearing.finish(); advanceUntilIdle()
        bearing.tap(24); advanceUntilIdle()
        bearing.dispatch(GameUiAction.Tap(BoardTarget.BlackTray, bearing.state.value.revision)); advanceUntilIdle()
        assertEquals(GameResult(Player.BLACK, WinKind.GAMMON), bearing.state.value.game.result)
    }

    @Test fun lostAnimationCallbackRecoversWithoutChangingCommittedGameState() = runTest {
        val vm = GameViewModel(SavedStateHandle(), LocalDice(DieSource { 2 }), dispatcher,
            BackgammonEngine.fromPosition(BoardState.initial(), Player.WHITE), visualTimeoutMillis = 1000)
        models.put("watchdog", vm)
        advanceUntilIdle()
        vm.roll(); runCurrent()
        assertTrue(vm.state.value.busy)
        val committed = vm.state.value.game
        advanceTimeBy(1001); runCurrent()
        assertFalse(vm.state.value.busy)
        assertEquals(committed, vm.state.value.game)
        assertEquals(4, vm.state.value.dice.size)
    }

    @Test fun allTurnPhasesRoundTripThroughSnapshot() {
        val initial = BackgammonEngine.newGame()
        val moving = accepted(BackgammonEngine.fromPosition(position(mapOf(8 to 1)), Player.WHITE), GameAction.ApplyDice(DiceRoll(1, 2)))
        val complete = accepted(rolled(position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1), 1, 2), GameAction.Move(enter(23, 2)))
        val finished = accepted(rolled(position(mapOf(1 to 1)), 1, 2), GameAction.Move(off(1, 2)))
        listOf(initial, BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK), moving, complete, finished).forEach {
            val decoded = GameSnapshot.decode(GameSnapshot.encode(GameUiState(game = it, ready = true)))
            assertEquals(it, requireNotNull(decoded).game)
        }
    }
}
