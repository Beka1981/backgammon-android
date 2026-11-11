package ge.gogichaishvili.backgammon.data

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import ge.gogichaishvili.backgammon.domain.match.*
import ge.gogichaishvili.backgammon.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class MatchPersistenceTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun record(first: Int = 3, second: Int = 1): MatchRecord {
        val roll = DiceRoll(first, second)
        val game = accepted(BackgammonEngine.fromPosition(BoardState.initial(), Player.WHITE), GameAction.ApplyDice(roll))
        return MatchRecord(game, roll, (game.turn as TurnState.Moving).remainingDice, Player.WHITE)
    }
    private fun decoded(bytes: ByteArray) = (MatchWire.decode(bytes) as StoredMatch.Ready).record
    private suspend fun <T> opened(file: File, block: suspend (MatchRepository) -> T): T {
        val job = SupervisorJob()
        val store = DataStoreFactory.create(serializer = MatchSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { StoredMatch.Recovered },
            scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
        return try { block(DataStoreMatchRepository(store)) }
        finally { job.cancelAndJoin() }
    }

    @Test fun fileReopenPreservesCommittedBoardAndUnusedDice() = runTest {
        val initial = record()
        val move = BackgammonEngine.legalMoves(initial.game).first()
        val game = accepted(initial.game, GameAction.Move(move))
        val remaining = initial.remainingDice.toMutableList().apply { remove(move.die) }
        val saved = MatchRecord(game, initial.roll, remaining, Player.WHITE)
        val file = File(temporary.root, "match.bin")
        opened(file) { it.save(saved) }
        opened(file) { assertEquals(saved, it.load().record) }
    }

    @Test fun missingFileIsNotReportedAsCorruption() = runTest {
        opened(File(temporary.root, "missing.bin")) { assertEquals(MatchLoad(), it.load()) }
    }

    @Test fun corruptedFileRecoversAndCanBeReplacedAtomically() = runTest {
        val file = temporary.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        opened(file) {
            assertTrue(it.load().recoveredCorruption)
            it.save(record())
            assertEquals(record(), it.load().record)
        }
        opened(file) { assertEquals(record(), it.load().record) }
    }

    @Test fun futureSchemaIsPreservedAndCannotBeOverwritten() = runTest {
        val raw = MatchWire.encode(StoredMatch.Ready(record())).also { ByteBuffer.wrap(it).putInt(4, 99) }
        val file = temporary.newFile().apply { writeBytes(raw) }
        opened(file) {
            assertEquals(99, it.load().unsupportedVersion)
            try { it.save(record()); fail("Must preserve future schema") } catch (_: IOException) { }
        }
        assertArrayEquals(raw, file.readBytes())
    }

    @Test fun oversizedFutureSchemaFailsReadOnlyWithoutCorruptionReplacement() = runTest {
        val raw = ByteArray(4096)
        ByteBuffer.wrap(raw).putInt(0x42474D4E).putInt(99)
        val file = temporary.newFile().apply { writeBytes(raw) }
        opened(file) {
            try { it.load(); fail("Unknown large future schema must not be replaced") }
            catch (e: IOException) { assertFalse(e is CorruptionException) }
        }
        assertArrayEquals(raw, file.readBytes())
    }

    @Test fun falseAutomaticPassIsRejectedUsingTheEngine() {
        val roll = DiceRoll(1, 2)
        val invalid = MatchRecord(BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK),
            roll, roll.uses, Player.WHITE)
        assertTrue(runCatching { invalid.validated() }.isFailure)
    }

    @Test fun legacyV1PayloadMigratesToChecksummedV2OnNextWrite() {
        val old = MatchWire.encode(StoredMatch.Ready(record()), version = 1)
        val restored = decoded(old)
        assertEquals(record(), restored)
        val rewritten = MatchWire.encode(StoredMatch.Ready(restored))
        assertEquals(2, ByteBuffer.wrap(rewritten).getInt(4))
        assertEquals(record(), decoded(rewritten))
    }

    @Test fun everyTruncatedPrefixIsRejected() {
        val bytes = MatchWire.encode(StoredMatch.Ready(record()))
        (0 until bytes.size).forEach { length ->
            assertTrue("Prefix " + length, runCatching { MatchWire.decode(bytes.copyOf(length)) }.isFailure)
        }
    }

    @Test fun checksumDetectsPayloadBitFlip() {
        val bytes = MatchWire.encode(StoredMatch.Ready(record()))
        bytes[28] = (bytes[28].toInt() xor 1).toByte()
        assertTrue(runCatching { MatchWire.decode(bytes) }.isFailure)
    }

    @Test fun trailingBytesAndOversizedLengthAreRejected() {
        val bytes = MatchWire.encode(StoredMatch.Ready(record()))
        assertTrue(runCatching { MatchWire.decode(bytes + 0.toByte()) }.isFailure)
        ByteBuffer.wrap(bytes).putInt(8, Int.MAX_VALUE)
        assertTrue(runCatching { MatchWire.decode(bytes) }.isFailure)
    }

    @Test fun oversizedStreamBecomesDataStoreCorruption() = runTest {
        try { MatchSerializer.readFrom(ByteArrayInputStream(ByteArray(2048))); fail() }
        catch (_: CorruptionException) { }
    }

    @Test fun allPhasesIncludingTiePassCompletionAndVictoryRoundTrip() {
        val tie = DiceRoll(2, 2)
        val moving = record()
        val blocked = rolled(position(emptyMap(), mapOf(22 to 2, 12 to 13), whiteBar = 1), 1, 2)
        val complete = accepted(blocked, GameAction.Move(enter(23, 2)))
        val won = accepted(rolled(position(mapOf(1 to 1)), 1, 2), GameAction.Move(off(1, 2)))
        val passed = accepted(BackgammonEngine.fromPosition(
            position(emptyMap(), (19..24).associateWith { 2 } + (12 to 3), whiteBar = 1), Player.WHITE),
            GameAction.ApplyDice(DiceRoll(1, 2)))
        val records = listOf(
            MatchRecord(BackgammonEngine.newGame()),
            MatchRecord(BackgammonEngine.newGame(), tie, tie.uses),
            MatchRecord(BackgammonEngine.fromPosition(BoardState.initial(), Player.BLACK)),
            moving,
            MatchRecord(complete, DiceRoll(1, 2), listOf(1), Player.WHITE),
            MatchRecord(passed, DiceRoll(1, 2), listOf(1, 2), Player.WHITE),
            MatchRecord(won, DiceRoll(1, 2), listOf(1), Player.WHITE, true))
        records.forEach { assertEquals(it, decoded(MatchWire.encode(StoredMatch.Ready(it)))) }
    }

    @Test fun mismatchedOwnerRollAndPrematureCompleteAreRejected() {
        val valid = record()
        assertTrue(runCatching { valid.copy(rolledBy = Player.BLACK).validated() }.isFailure)
        assertTrue(runCatching { valid.copy(roll = DiceRoll(6, 6)).validated() }.isFailure)
        val premature = GameState(valid.game.board, TurnState.Complete(Player.WHITE, requireNotNull(valid.roll)))
        assertTrue(runCatching { valid.copy(game = premature).validated() }.isFailure)
        assertTrue(runCatching { valid.copy(resultDismissed = true).validated() }.isFailure)
    }

    @Test fun savedRecordCannotBeMutatedThroughOriginalDiceList() = runTest {
        val file = File(temporary.root, "immutable.bin")
        val dice = record().remainingDice.toMutableList()
        opened(file) {
            it.save(record().copy(remainingDice = dice))
            dice.clear()
            assertEquals(record(), it.load().record)
        }
    }

    @Test fun concurrentAtomicWritesNeverProduceATornRecord() = runTest {
        val file = File(temporary.root, "atomic.bin")
        opened(file) { repo ->
            coroutineScope { (1..6).map { n -> async(Dispatchers.Default) { repo.save(record(n, n)) } }.awaitAll() }
            repo.save(record(6, 1))
        }
        opened(file) { assertEquals(record(6, 1), it.load().record) }
    }
}
