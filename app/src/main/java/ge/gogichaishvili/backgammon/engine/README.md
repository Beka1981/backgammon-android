# Standard short-backgammon engine

Pure Kotlin package in the existing app module. No Android, Compose, ViewModel, persistence,
network or coroutines dependencies. The preview UI is deliberately unchanged.

## Coordinates

All points use one absolute numbering scheme, identical to BoardGeometry. Do not reverse
point IDs when a player changes; rotate/mirror pixels only in presentation if desired.

| Player | Preview color | Direction | Home | Bar entry for die d | Bearing off |
| --- | --- | --- | --- | --- | --- |
| WHITE | Ivory | 24 -> 1 | 1..6 | 25 - d | Below point 1 |
| BLACK | Dark | 1 -> 24 | 19..24 | d | Above point 24 |

BoardState.points[0] is point 1; use board.point(1) for checked one-based lookup.
A Point has one owner and a count, or is empty (null owner, zero count).
Bar and borne-off counts are separate from numbered points. The typed MoveSource.Bar and
MoveDestination.Off are not invented point numbers.

Initial White: 24×2, 13×5, 8×3, 6×5.
Initial Black: 1×2, 12×5, 17×3, 19×5.
These match the existing static PreviewPosition, checked by a regression test.

## Public API

- BackgammonEngine.newGame(): initial board in TurnState.Opening.
- BackgammonEngine.fromPosition(board, player): validated board at a turn boundary, or
  Finished if the board already has a winner. This validates structural invariants, not
  historical reachability, and does not restore a partially played roll.
- BackgammonEngine.transition(state, action): Accepted(newState) or Rejected(originalState, reason).
- BackgammonEngine.legalSequences(state): every permitted ordered complete continuation.
- BackgammonEngine.legalMoves(state): distinct first moves of those sequences.
- BackgammonEngine.isLegalMove(state, move): validates against whole-turn requirements.
- BackgammonRules.legalSequences(board, player, roll): analyze a fresh roll directly.
- BackgammonRules.canBearOff / result: position queries.
- LocalDice(DieSource): optional injectable local dice; never called by the reducer.

Malformed values (out-of-range dice, invalid point IDs, incorrect checker totals) fail fast
with IllegalArgumentException. Well-formed but forbidden user actions return typed rejections.
Construct game states through the engine; internal constructors are not a persistence schema.

Example integration, without introducing a ViewModel in this milestone:

```kotlin
var state = BackgammonEngine.newGame()
val opening = GameAction.OpeningRoll(DiceRoll(first = 5, second = 2))
state = BackgammonEngine.transition(state, opening).state
val choice = BackgammonEngine.legalMoves(state).first()
val outcome = BackgammonEngine.transition(state, GameAction.Move(choice))
if (outcome is ActionResult.Accepted) state = outcome.state
```

OpeningRoll.first is White's die and second is Black's. Unequal dice determine the starter
and become the opening move's two dice; the winner does not roll again. Equal dice leave
the same Opening state. Make another explicit attempt, rather than an unbounded RNG loop.
ApplyDice is permitted only in AwaitingRoll. Future authoritative dice go through exactly
the same action as local dice; authentication and server validation belong outside this package.

## Turn lifecycle

Opening -> Moving -> Complete -> AwaitingRoll(other player) -> Moving ...
Moving -> Finished on the fifteenth borne-off checker.
Opening ties -> Opening.
A fully blocked roll -> AwaitingRoll(other player), automatically and without a Move/EndTurn.

After all usable dice have been played, Complete requires an explicit EndTurn. There is no
skip action while any compulsory move remains. Unusable leftover dice end the played turn;
doubles do not grant an extra roll. Reset is valid in every phase and returns a fresh Opening.

## Move generation

Each die is a separate step. Doubles expand into four equal uses. A depth-first search
enumerates every legal source and both distinct die orders, applying each step to an immutable
board. Every MoveStep includes boardAfter; LegalMoveSequence exposes initialBoard and finalBoard.

Only longest playable sequences survive. If exactly one die can be used and either value
could be played, only the larger playable die survives. This filtering happens before exposing
moves, so a locally legal checker move that wastes the second die is rejected. After a move,
continuations are recomputed from the actual remaining dice. Identical dice have no artificial
identity, while distinct ordered plays are retained even when final boards coincide.

Bar checkers must enter before ordinary moves. Empty/own/blot destinations are open;
opposing stacks of two or more are blocked. Hitting transfers one opposing checker to its bar.
Bearing off requires no bar checker and all remaining pieces in home. Exact dice may remove
any matching checker; oversized dice may remove only a farthest checker. Conditions are
re-evaluated after every step, including entry into home or removal of the highest checker.
Search stops at victory; unused dice are never played after the last checker is removed.

Every constructed board enforces exactly 15 checkers per player across points + bar + borneOff.
Inputs and returned collections are defensively copied and read-only, not aliased mutable lists.
No mutable cache, global game state, RNG or animation callback affects deterministic transitions.
Search depth is at most four. Complete enumeration can produce many sequences: future ViewModels
should run generation/reduction on Dispatchers.Default and serialize actions, not run it during
Compose drawing. Performance has not been profiled on-device in this task.

## Single-game scoring

Cube value is fixed at one: SINGLE=1, GAMMON=2, BACKGAMMON=3.
A loser with any checker off loses a single. With none off, a checker on the bar or in the
winner's home board causes backgammon; otherwise it is gammon. The loser's own home is
irrelevant. There is no doubling cube, Crawford/Jacoby rule, match score, resignation,
clock, undo workflow, persistence schema, AI, network transport or animation integration.

## Rule sources and verification

Rules were checked against the [U.S. Backgammon Federation beginner guide](https://usbgf.org/beginners-guide-to-backgammon/),
including starting rolls, compulsory dice usage, bar entry, bear-off and 1/2/3-point results.
Tests include every ordered dice pair for both players, specific dice-order traps, invalid
transitions, immutable snapshots, and three seeded games through completion.

See the root TASK_03_REPORT.md for exact commands, test counts and modified-file inventory.

## Next integration step

Add an activity/game-scoped GameViewModel with a single immutable StateFlow<GameState>.
Serialize actions in that ViewModel and run transitions on a worker dispatcher. Inject LocalDice
for local play; keep supplied-dice actions available for future authority. Convert Player to
checker visuals in presentation, and pass board points/bar/off data into a state-driven renderer
while preserving the existing selected theme. Drive selection/highlights from legalMoves and
resolve a destination that can use different dice through an explicit move choice.
First connect tap-based moves and turn controls with accessibility semantics and UI tests;
add dragging and animations only in later authorized tasks.
