package ge.gogichaishvili.backgammon.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import ge.gogichaishvili.backgammon.domain.match.*
import ge.gogichaishvili.backgammon.engine.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.CRC32

internal sealed interface StoredMatch {
    data object Missing : StoredMatch
    data object Recovered : StoredMatch
    data class Ready(val record: MatchRecord) : StoredMatch
    class Future(val version: Int, val original: ByteArray) : StoredMatch
}

/** Bounded binary envelope: magic, schema, payload size, payload, CRC32 (v2).
 * v1 used the same payload without CRC; reads migrate it, subsequent writes always use v2.
 * Unknown future schemas are preserved and lock gameplay instead of being overwritten.
 */
internal object MatchWire {
    const val VERSION = 2
    private const val MAGIC = 0x42474D4E
    const val MAX_BYTES = 1024

    fun hasFutureHeader(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        DataInputStream(ByteArrayInputStream(bytes)).use { it.readInt() == MAGIC && it.readInt() > VERSION }

    fun encode(value: StoredMatch, version: Int = VERSION): ByteArray {
        if (value is StoredMatch.Future) return value.original.copyOf()
        require(version in 1..VERSION)
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                when (value) {
                    StoredMatch.Missing -> out.writeByte(0)
                    StoredMatch.Recovered -> out.writeByte(1)
                    is StoredMatch.Ready -> {
                        val record = value.record.validated()
                        out.writeByte(2)
                        record.game.board.points.forEach {
                            out.writeInt(if (it.owner == Player.BLACK) -it.count else it.count)
                        }
                        val board = record.game.board
                        listOf(board.bar.white, board.bar.black, board.borneOff.white, board.borneOff.black).forEach(out::writeInt)
                        out.writeInt(when (record.game.turn) {
                            TurnState.Opening -> 0
                            is TurnState.AwaitingRoll -> 1
                            is TurnState.Moving -> 2
                            is TurnState.Complete -> 3
                            is TurnState.Finished -> 4
                        })
                        out.writeInt(playerId(record.game.currentPlayer))
                        out.writeInt(record.roll?.first ?: 0)
                        out.writeInt(record.roll?.second ?: 0)
                        out.writeInt(record.remainingDice.size)
                        record.remainingDice.forEach(out::writeInt)
                        out.writeInt(playerId(record.rolledBy))
                        out.writeBoolean(record.resultDismissed)
                    }
                    is StoredMatch.Future -> error("Handled above")
                }
            }
        }.toByteArray()
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeInt(MAGIC); it.writeInt(version); it.writeInt(payload.size); it.write(payload)
                if (version >= 2) it.writeLong(CRC32().apply { update(payload) }.value)
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): StoredMatch {
        require(bytes.size <= MAX_BYTES)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            val version = input.readInt()
            if (version > VERSION) return StoredMatch.Future(version, bytes.copyOf())
            require(version in 1..VERSION)
            val size = input.readInt()
            require(size in 1..512)
            val payload = ByteArray(size).also(input::readFully)
            if (version >= 2) require(input.readLong() == CRC32().apply { update(payload) }.value)
            require(input.available() == 0)
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                val value = when (data.readUnsignedByte()) {
                    0 -> StoredMatch.Missing
                    1 -> StoredMatch.Recovered
                    2 -> {
                        val points = List(24) {
                            val count = data.readInt()
                            require(count in -15..15)
                            when {
                                count < 0 -> Point(Player.BLACK, -count)
                                count > 0 -> Point(Player.WHITE, count)
                                else -> Point()
                            }
                        }
                        val board = BoardState(points, CheckerCounts(data.readInt(), data.readInt()),
                            CheckerCounts(data.readInt(), data.readInt()))
                        val phase = data.readInt()
                        val player = player(data.readInt())
                        val first = data.readInt()
                        val second = data.readInt()
                        val roll = if (first == 0 && second == 0) null else DiceRoll(first, second)
                        val count = data.readInt().also { require(it in 0..4) }
                        val remaining = List(count) { data.readInt() }
                        val owner = player(data.readInt())
                        val dismissed = data.readUnsignedByte().also { require(it in 0..1) } == 1
                        val turn = when (phase) {
                            0 -> TurnState.Opening
                            1 -> TurnState.AwaitingRoll(requireNotNull(player))
                            2 -> TurnState.Moving(requireNotNull(player), requireNotNull(roll), remaining)
                            3 -> TurnState.Complete(requireNotNull(player), requireNotNull(roll))
                            4 -> TurnState.Finished(requireNotNull(BackgammonRules.result(board)))
                            else -> error("Unknown match phase")
                        }
                        val game = GameState(board, turn)
                        require(game.currentPlayer == player)
                        StoredMatch.Ready(MatchRecord(game, roll, remaining, owner, dismissed).validated())
                    }
                    else -> error("Unknown match payload")
                }
                require(data.available() == 0)
                value
            }
        }
    }

    private fun playerId(player: Player?): Int = when (player) { null -> 0; Player.WHITE -> 1; Player.BLACK -> 2 }
    private fun player(id: Int): Player? = when (id) { 0 -> null; 1 -> Player.WHITE; 2 -> Player.BLACK; else -> error("Unknown player") }
}

internal object MatchSerializer : Serializer<StoredMatch> {
    override val defaultValue: StoredMatch = StoredMatch.Missing
    override suspend fun readFrom(input: InputStream): StoredMatch {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(256)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (bytes.size() + count > MatchWire.MAX_BYTES) {
                // A future schema may legitimately be larger. Fail read-only, never invoke replacement.
                if (MatchWire.hasFutureHeader(bytes.toByteArray())) throw IOException("Newer oversized match schema")
                throw CorruptionException("Oversized match")
            }
            bytes.write(buffer, 0, count)
        }
        return try { MatchWire.decode(bytes.toByteArray()) }
        catch (e: EOFException) { throw CorruptionException("Truncated match", e) }
        catch (e: IllegalArgumentException) { throw CorruptionException("Invalid match", e) }
        catch (e: IllegalStateException) { throw CorruptionException("Invalid match", e) }
    }
    override suspend fun writeTo(t: StoredMatch, output: OutputStream) { output.write(MatchWire.encode(t)) }
}

internal class DataStoreMatchRepository(private val store: DataStore<StoredMatch>) : MatchRepository {
    override suspend fun load(): MatchLoad = withContext(Dispatchers.IO) { when (val value = store.data.first()) {
        StoredMatch.Missing -> MatchLoad()
        StoredMatch.Recovered -> MatchLoad(recoveredCorruption = true)
        is StoredMatch.Future -> MatchLoad(unsupportedVersion = value.version)
        is StoredMatch.Ready -> MatchLoad(value.record.validated())
    } }
    override suspend fun save(record: MatchRecord): Unit = withContext(Dispatchers.IO) {
        val valid = record.validated()
        store.updateData {
            if (it is StoredMatch.Future) throw IOException("Newer match schema must not be overwritten")
            StoredMatch.Ready(valid)
        }
        Unit
    }
}
