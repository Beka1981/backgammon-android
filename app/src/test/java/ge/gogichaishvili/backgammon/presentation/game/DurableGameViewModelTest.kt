package ge.gogichaishvili.backgammon.presentation.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import ge.gogichaishvili.backgammon.animation.MotionPolicy
import ge.gogichaishvili.backgammon.domain.feedback.FeedbackCue
import ge.gogichaishvili.backgammon.domain.match.*
import ge.gogichaishvili.backgammon.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DurableGameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val models = ViewModelStore()
    private var id = 0
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun close() { models.clear(); Dispatchers.resetMain() }

    private class Repository(var load: MatchLoad = MatchLoad(
        MatchRecord(BackgammonEngine.fromPosition(BoardState.initial(), Player.WHITE)))) : MatchRepository {
        var failRead = false
        var failSave = false
        var gate: CompletableDeferred<Unit>? = null
        var saves = 0
        override suspend fun load(): MatchLoad {
            if (failRead) throw IOException("read")
            return load
        }
        override suspend fun save(record: MatchRecord) {
            saves++
            gate?.await()
            if (failSave) throw IOException("write")
            load = MatchLoad(record.validated())
        }
    }
    private fun model(repo: Repository, handle: SavedStateHandle = SavedStateHandle(), dice: DieSource = DieSource { 2 }): GameViewModel =
        GameViewModel(handle, LocalDice(dice), dispatcher, visualTimeoutMillis = Long.MAX_VALUE, matches = repo)
            .also { models.put("durable_" + id++, it) }
    private fun GameViewModel.roll() = dispatch(GameUiAction.Roll(state.value.revision))
    private fun GameViewModel.tap(n: Int) = dispatch(GameUiAction.Tap(BoardTarget.Point(n), state.value.revision))
    private fun GameViewModel.finish() {
        val s = state.value
        dispatch(GameUiAction.AnimationFinished(s.checkerTransition?.id ?: s.rollTransition?.id ?: -1))
    }

    @Test fun rollIsNotPublishedBeforeDurableWriteAndDuplicateQueueCannotRollAgain() = runTest {
        val repo = Repository()
        var generated = 0
        val vm = model(repo, dice = DieSource { generated++; 2 })
        advanceUntilIdle()
        val previous = vm.state.value.game
        val revision = vm.state.value.revision
        repo.gate = CompletableDeferred()
        vm.dispatch(GameUiAction.Roll(revision)); vm.dispatch(GameUiAction.Roll(revision))
        runCurrent()
        assertEquals(previous, vm.state.value.game)
        assertEquals(1, repo.saves)
        repo.gate!!.complete(Unit); advanceUntilIdle()
        assertEquals(2, generated)
        assertEquals(repo.load.record!!.game, vm.state.value.game)
        assertEquals(1, repo.saves)
        assertTrue(vm.state.value.busy)
    }

    @Test fun failedSaveLocksInputAndRetryRetainsTheExactDiceWithoutGeneratingAgain() = runTest {
        val repo = Repository().apply { failSave = true }
        var generated = 0
        val vm = model(repo, dice = DieSource { generated++; if (generated == 1) 6 else 1 })
        advanceUntilIdle()
        val previous = vm.state.value.game
        vm.roll(); advanceUntilIdle()
        assertEquals(previous, vm.state.value.game)
        assertEquals(GameStorageIssue.SAVE_FAILED, vm.state.value.storageIssue)
        assertFalse(vm.state.value.canRoll)
        vm.roll(); vm.tap(8); vm.dispatch(GameUiAction.RequestNewGame); advanceUntilIdle()
        assertFalse(vm.state.value.confirmNewGame)
        assertEquals(2, generated)
        repo.failSave = false
        vm.dispatch(GameUiAction.RetryPersistence); advanceUntilIdle()
        assertNull(vm.state.value.storageIssue)
        assertEquals(DiceRoll(6, 1), vm.state.value.lastRoll)
        assertEquals(2, generated)
        assertEquals(repo.load.record!!.game, vm.state.value.game)
    }

    @Test fun failedReadNeverStartsAnUnrelatedGameAndCanRetry() = runTest {
        val repo = Repository().apply { failRead = true }
        val vm = model(repo)
        advanceUntilIdle()
        assertEquals(GameStorageIssue.LOAD_FAILED, vm.state.value.storageIssue)
        assertFalse(vm.state.value.canRoll)
        vm.roll(); advanceUntilIdle()
        assertEquals(0, repo.saves)
        repo.failRead = false
        vm.dispatch(GameUiAction.RetryPersistence); advanceUntilIdle()
        assertNull(vm.state.value.storageIssue)
        assertEquals(repo.load.record!!.game, vm.state.value.game)
    }

    @Test fun freshProcessRestoresMidMoveDiceAndBoardWithoutAnAnimationOrReplay() = runTest {
        val repo = Repository()
        val vm = model(repo)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        val source = vm.state.value.legalMoves.first().from as MoveSource.OnPoint
        vm.tap(source.number); advanceUntilIdle()
        val move = vm.state.value.selectedMoves.first()
        vm.dispatch(GameUiAction.Play(move, vm.state.value.revision)); advanceUntilIdle()
        val record = requireNotNull(repo.load.record)
        val restored = model(repo) // Empty SavedStateHandle, independent ViewModel.
        advanceUntilIdle()
        assertEquals(record.game, restored.state.value.game)
        assertEquals(vm.state.value.dice, restored.state.value.dice)
        assertFalse(restored.state.value.busy)
        assertNull(restored.state.value.selected)
    }

    @Test fun durableRecordWinsOverAnOlderActivitySnapshot() = runTest {
        val repo = Repository()
        val handle = SavedStateHandle()
        val vm = model(repo, handle)
        advanceUntilIdle()
        val stale = requireNotNull(handle.get<IntArray>(GameSnapshot.KEY)).copyOf()
        vm.roll(); advanceUntilIdle()
        val restored = model(repo, SavedStateHandle(mapOf(GameSnapshot.KEY to stale)))
        advanceUntilIdle()
        assertEquals(vm.state.value.game, restored.state.value.game)
        assertEquals(vm.state.value.lastRoll, restored.state.value.lastRoll)
        assertFalse(restored.state.value.busy)
    }

    @Test fun legacyInstancePayloadIsValidatedBeforeImportingIntoAnEmptyMatchFile() = runTest {
        val invalid = GameUiState(
            game = BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK),
            lastRoll = DiceRoll(1, 2), unspentDice = listOf(1, 2), rolledBy = Player.WHITE)
        // A structurally valid v1 snapshot can still claim a false automatic pass.
        val payloads = listOf<Any>("wrong saved-instance type", GameSnapshot.encode(invalid))
        payloads.forEach { payload ->
            val repo = Repository(MatchLoad())
            val vm = model(repo, SavedStateHandle(mapOf(GameSnapshot.KEY to payload)))
            advanceUntilIdle()
            assertEquals(TurnState.Opening, vm.state.value.game.turn)
            assertEquals(GameNotice.RESTORE_FAILED, vm.state.value.notice)
            assertEquals(vm.state.value.game, repo.load.record!!.game)
        }
    }

    @Test fun newerSchemaNeverAllowsRollResetOrOverwrite() = runTest {
        val repo = Repository(MatchLoad(unsupportedVersion = 99))
        val vm = model(repo)
        advanceUntilIdle()
        assertEquals(GameStorageIssue.NEWER_SCHEMA, vm.state.value.storageIssue)
        vm.roll(); vm.dispatch(GameUiAction.RequestNewGame); vm.dispatch(GameUiAction.ConfirmNewGame)
        vm.dispatch(GameUiAction.RetryPersistence); advanceUntilIdle()
        assertEquals(0, repo.saves)
        assertFalse(vm.state.value.canRoll)
    }

    @Test fun corruptedSaveCreatesAndDurablyCommitsANewGameWithVisibleNotice() = runTest {
        val repo = Repository(MatchLoad(recoveredCorruption = true))
        val vm = model(repo)
        advanceUntilIdle()
        assertEquals(TurnState.Opening, vm.state.value.game.turn)
        assertEquals(GameNotice.RESTORE_FAILED, vm.state.value.notice)
        assertEquals(vm.state.value.game, repo.load.record!!.game)
    }

    @Test fun selectionMotionAndVisualCancellationDoNotWriteTheMatch() = runTest {
        val repo = Repository()
        val vm = model(repo)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        val writes = repo.saves
        vm.tap(8); vm.dispatch(GameUiAction.SetMotion(MotionPolicy(reducedMotion = true)))
        vm.dispatch(GameUiAction.CancelAnimations); advanceUntilIdle()
        assertEquals(writes, repo.saves)
        assertTrue(vm.state.value.motion.reducedMotion)
    }

    @Test fun confirmedResetSurvivesACompletelyFreshViewModel() = runTest {
        val repo = Repository()
        val vm = model(repo)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle()
        vm.dispatch(GameUiAction.RequestNewGame); vm.dispatch(GameUiAction.ConfirmNewGame); advanceUntilIdle()
        val restored = model(repo)
        advanceUntilIdle()
        assertEquals(TurnState.Opening, restored.state.value.game.turn)
        assertNull(restored.state.value.lastRoll)
        assertFalse(restored.state.value.busy)
    }

    @Test fun visualLandingProducesOneCueAndStaleAcknowledgementsDoNotRepeatIt() = runTest {
        val repo = Repository()
        val vm = model(repo)
        val cues = mutableListOf<FeedbackCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { cues += it } }
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        val move = vm.state.value.selectedMoves.first()
        vm.dispatch(GameUiAction.Play(move, vm.state.value.revision)); advanceUntilIdle()
        val effect = requireNotNull(vm.state.value.checkerTransition)
        vm.finish(); vm.dispatch(GameUiAction.AnimationFinished(effect.id)); advanceUntilIdle()
        assertEquals(listOf(FeedbackCue.DICE, FeedbackCue.PICKUP, FeedbackCue.PLACE), cues)
    }

    @Test fun lifecycleInterruptionDoesNotProducePlacementOrReplayFeedbackOnRestore() = runTest {
        val repo = Repository()
        val vm = model(repo)
        val cues = mutableListOf<FeedbackCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { cues += it } }
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        vm.dispatch(GameUiAction.Play(vm.state.value.selectedMoves.first(), vm.state.value.revision)); advanceUntilIdle()
        val committed = vm.state.value.game
        vm.dispatch(GameUiAction.CancelAnimations); advanceUntilIdle()
        assertEquals(committed, vm.state.value.game)
        assertFalse(cues.contains(FeedbackCue.PLACE))
        val restored = model(repo)
        val restoredCues = mutableListOf<FeedbackCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { restored.feedback.collect { restoredCues += it } }
        advanceUntilIdle()
        assertTrue(restoredCues.isEmpty())
        assertEquals(committed, restored.state.value.game)
    }

    @Test fun interruptedEffectRetainsCommittedGameRejectsQueuedInputAndNeverSoundsALanding() = runTest {
        val repo = Repository()
        val vm = model(repo)
        val cues = mutableListOf<FeedbackCue>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.feedback.collect { cues += it } }
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        vm.tap(8); advanceUntilIdle()
        val move = vm.state.value.selectedMoves.first()
        val revision = vm.state.value.revision
        repeat(10) { vm.dispatch(GameUiAction.Play(move, revision)) }
        advanceUntilIdle()
        val committed = vm.state.value.game
        val id = requireNotNull(vm.state.value.checkerTransition).id
        val writes = repo.saves
        vm.dispatch(GameUiAction.AnimationInterrupted(id)); advanceUntilIdle()
        vm.dispatch(GameUiAction.AnimationFinished(id)); advanceUntilIdle()
        assertEquals(committed, vm.state.value.game)
        assertEquals(committed, repo.load.record!!.game)
        assertFalse(vm.state.value.busy)
        assertEquals(writes, repo.saves)
        assertFalse(cues.contains(FeedbackCue.PLACE))
    }

    @Test fun failedResetSaveCanBeRetriedWithoutAcknowledgingAnUnpersistedNewGame() = runTest {
        val repo = Repository()
        val vm = model(repo)
        advanceUntilIdle(); vm.roll(); advanceUntilIdle(); vm.finish(); advanceUntilIdle()
        val original = vm.state.value.game
        repo.failSave = true
        vm.dispatch(GameUiAction.RequestNewGame); vm.dispatch(GameUiAction.ConfirmNewGame); advanceUntilIdle()
        assertEquals(original, vm.state.value.game)
        repo.failSave = false
        vm.dispatch(GameUiAction.RetryPersistence); advanceUntilIdle()
        assertEquals(TurnState.Opening, vm.state.value.game.turn)
        assertEquals(vm.state.value.game, repo.load.record!!.game)
    }
}
