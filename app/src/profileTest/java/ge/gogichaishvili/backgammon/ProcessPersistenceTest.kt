package ge.gogichaishvili.backgammon

import android.os.Process
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ge.gogichaishvili.backgammon.domain.match.MatchRecord
import ge.gogichaishvili.backgammon.engine.*
import ge.gogichaishvili.backgammon.presentation.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.*

/** Host runs seed, force-stops only the isolated profile app, then runs restore in a new process. */
class ProcessPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun expected(): MatchRecord {
        val roll = DiceRoll(6, 1)
        val opening = (BackgammonEngine.transition(BackgammonEngine.newGame(), GameAction.OpeningRoll(roll)) as ActionResult.Accepted).state
        val move = CheckerMove(MoveSource.OnPoint(8), MoveDestination.OnPoint(7), 1)
        val game = (BackgammonEngine.transition(opening, GameAction.Move(move)) as ActionResult.Accepted).state
        return MatchRecord(game, roll, listOf(6), Player.WHITE)
    }
    @Test fun seedBeforeProcessDeath() {
        assertTrue(context.packageName.endsWith(".profile"))
        val app = context.applicationContext as BackgammonApplication
        runBlocking { app.container.matches.save(expected()) }
        DataOutputStream(FileOutputStream(File(context.filesDir, "task05-seed-pid"))).use { it.writeInt(Process.myPid()) }
    }
    @Test fun restoreAfterProcessDeath() {
        assertTrue(context.packageName.endsWith(".profile"))
        val previousPid = DataInputStream(FileInputStream(File(context.filesDir, "task05-seed-pid"))).use { it.readInt() }
        assertNotEquals("A new Android process must restore the file", previousPid, Process.myPid())
        val expected = expected()
        val app = context.applicationContext as BackgammonApplication
        assertEquals(expected, runBlocking { app.container.matches.load().record })
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            var model: GameViewModel? = null
            val until = SystemClock.uptimeMillis() + 60000
            while (model?.state?.value?.ready != true) {
                scenario.onActivity {
                    model = it.viewModelStore["androidx.lifecycle.ViewModelProvider.DefaultKey:" +
                        GameViewModel::class.java.canonicalName] as? GameViewModel
                }
                check(SystemClock.uptimeMillis() < until)
                SystemClock.sleep(25)
            }
            val restored = requireNotNull(model).state.value
            assertEquals(expected.game, restored.game)
            assertEquals(expected.roll, restored.lastRoll)
            assertEquals(listOf(6), (restored.game.turn as TurnState.Moving).remainingDice)
            assertEquals(Player.WHITE, restored.rolledBy)
            assertFalse(restored.busy)
            assertNull(restored.selected)
            assertNull(restored.storageIssue)
            instrumentation.sendStatus(0, android.os.Bundle().apply {
                putString("process_restore", "PASS seedPid=" + previousPid + " restoredPid=" + Process.myPid())
            })
        } finally { scenario.close() }
    }
}
