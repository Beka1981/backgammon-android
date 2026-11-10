package ge.gogichaishvili.backgammon.engine

import kotlin.random.Random

fun interface DieSource {
    /** Return exactly one value in 1..6. Invalid sources fail fast through DiceRoll validation. */
    fun nextDie(): Int
}

/** Local convenience only. Server-authoritative clients supply GameAction dice directly. */
class LocalDice(private val source: DieSource = DieSource { Random.Default.nextInt(1, 7) }) {
    fun roll(): DiceRoll = DiceRoll(source.nextDie(), source.nextDie())
    fun openingAction(): GameAction.OpeningRoll = GameAction.OpeningRoll(roll())
}
