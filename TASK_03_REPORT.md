# Task 03 — standard short-backgammon rules engine

## Result
Implemented in the existing pure Kotlin engine package. No changes to Gradle, dependencies,
SDK/compiler versions, Compose screens, board rendering, skins or settings. No Supabase,
networking, visual animations or interactive board behavior were introduced.

## Architecture
- GameModels.kt: immutable players, points, board/bar/off counts, moves, dice, ordered
  sequence snapshots, phases, game state, results, actions and typed rejections.
- BackgammonRules.kt: legal single-step logic, full-turn sequence enumeration, mandatory
  maximum dice usage and higher-die filtering, bar entry, hits, bear-off and scoring.
  Unchecked application is private. Public callers use whole-turn-validated transitions.
- BackgammonEngine.kt: deterministic reducer for opening, supplied dice, moves, completion,
  automatic blocked-roll pass, victory and reset. Rejection preserves the original state.
- LocalDice.kt: injectable optional local source, separated from deterministic reduction.
- engine/README.md: full coordinate system, API example, lifecycle and integration notes.

White/ivory moves 24 -> 1, home 1..6, enters on 25-die.
Black/dark moves 1 -> 24, home 19..24, enters on die.
Board lists are zero-based, but public point numbers are 1..24. Bar and Off are typed
locations, not point-number sentinels. Fifteen checkers per player are enforced across
points + bar + borne-off at every board construction.

The opening uses one die per player, ties require another explicit attempt, and the
higher roller plays both original dice. Fully blocked rolls pass automatically.
Played turns enter Complete and require EndTurn; a caller cannot skip compulsory moves.
The fifteenth borne-off checker finishes immediately, with 1/2/3 points for
single/gammon/backgammon. No doubling cube is present.

Rules reference: [U.S. Backgammon Federation](https://usbgf.org/beginners-guide-to-backgammon/).

## Tests
305 new executed cases across 60 test methods (parameterized cases counted separately):
- DiceMatrixTest: 252 = 7 tests × all 36 ordered dice pairs.
- MoveRulesTest: 17.
- BearOffAndScoringTest: 15.
- GameTransitionsTest: 13.
- EngineInvariantTest: 8.

All 305 engine cases and the existing 18 JVM cases passed: **323 total, zero failures,
errors or skips**, confirmed from all ten final JUnit XML reports.

Coverage includes starting positions matching the preview; both movement directions;
all rolls and doubles; exact bar-entry coordinates for both players; hits and made points;
mandatory bar precedence; moving an entered checker; exact/oversized bear-off; entering
home mid-turn; dice-order traps; mandatory maximum dice usage; high-die priority;
partially playable doubles; intermediate boards; automatic pass; all result types for
both players; opening ties; invalid actions and values; reset; defensive collection
immutability; deterministic state/sequence equality; and three seeded complete games.

## Actual verification
| Command | Result | Actionable tasks |
| --- | --- | --- |
| ./gradlew testDebugUnitTest | exit 0, BUILD SUCCESSFUL in 6s | 25: 5 executed, 20 up-to-date |
| ./gradlew assembleDebug | exit 0, BUILD SUCCESSFUL in 6s | 36: 4 executed, 32 up-to-date |
| ./gradlew lint | exit 0, BUILD SUCCESSFUL in 23s | 27: 8 executed, 19 up-to-date |
| ./gradlew build test lint testDebugUnitTest assembleDebug | exit 0, BUILD SUCCESSFUL in 1m 2s | 95: 25 executed, 70 up-to-date |

The final combined command recompiled and retested the final source, regenerated lint,
and packaged both debug and release builds.
Lint: zero errors, 25 existing dependency-update/unused-resource warnings, no findings
in engine files. Existing DataStore protobuf emits a nonfatal sun.misc.Unsafe warning
under JDK 25 during the existing persistence tests; engine sources have no such dependency.
A source-import search confirms no Android/Compose/ViewModel/database/Supabase imports
in the engine package. No emulator or visual verification was performed in this task.

## Limitations
Single-game mode only. No cube, match score, Crawford/Jacoby variants, resignation, clocks,
AI, undo workflow, network authority, snapshot serialization, animations or UI binding.
fromPosition validates structural invariants and resumes at a turn boundary, not halfway
through a partially played roll; it does not prove historical reachability.
Full sequence enumeration is finite (at most four steps) but may allocate many results.
On-device performance profiling is not claimed. LocalDice is for local play, not a
replacement for future authoritative server dice.

## Recommended next task
Introduce a GameViewModel with immutable StateFlow<GameState>, serialized actions, and
engine work on Dispatchers.Default. Adapt engine board/bar/off data into the existing
theme-aware renderer. Expose dice, turn state and legalMoves as presentation state.
Begin with accessible tap selection, legal destination highlighting and turn controls,
with UI tests for phase changes and board/state agreement. Keep drag/animations and
backend work for separate authorized milestones.

## Complete task file inventory
- Modified: [AGENTS.md](/Users/Admin/AndroidStudioProjects/Backgammon/AGENTS.md)
- Modified: [ARCHITECTURE.md](/Users/Admin/AndroidStudioProjects/Backgammon/ARCHITECTURE.md)
- Added: [TASK_03_REPORT.md](/Users/Admin/AndroidStudioProjects/Backgammon/TASK_03_REPORT.md)
- Added: [app/src/main/java/ge/gogichaishvili/backgammon/engine/GameModels.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/main/java/ge/gogichaishvili/backgammon/engine/GameModels.kt)
- Added: [app/src/main/java/ge/gogichaishvili/backgammon/engine/BackgammonRules.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/main/java/ge/gogichaishvili/backgammon/engine/BackgammonRules.kt)
- Added: [app/src/main/java/ge/gogichaishvili/backgammon/engine/BackgammonEngine.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/main/java/ge/gogichaishvili/backgammon/engine/BackgammonEngine.kt)
- Added: [app/src/main/java/ge/gogichaishvili/backgammon/engine/LocalDice.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/main/java/ge/gogichaishvili/backgammon/engine/LocalDice.kt)
- Added: [app/src/main/java/ge/gogichaishvili/backgammon/engine/README.md](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/main/java/ge/gogichaishvili/backgammon/engine/README.md)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/EngineFixtures.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/EngineFixtures.kt)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/DiceMatrixTest.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/DiceMatrixTest.kt)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/MoveRulesTest.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/MoveRulesTest.kt)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/BearOffAndScoringTest.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/BearOffAndScoringTest.kt)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/GameTransitionsTest.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/GameTransitionsTest.kt)
- Added: [app/src/test/java/ge/gogichaishvili/backgammon/engine/EngineInvariantTest.kt](/Users/Admin/AndroidStudioProjects/Backgammon/app/src/test/java/ge/gogichaishvili/backgammon/engine/EngineInvariantTest.kt)

An additional workspace change appeared in [.idea/deploymentTargetSelector.xml](/Users/Admin/AndroidStudioProjects/Backgammon/.idea/deploymentTargetSelector.xml)
during the session. It was not edited or reverted by this task; it is listed separately
as an IDE/user-managed deployment selection change.
