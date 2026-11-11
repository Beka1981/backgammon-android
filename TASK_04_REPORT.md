# Task 04 — interactive local Backgammon

Implemented and verified: 347 JVM tests and all 11 emulator tests pass. Debug/release builds,
lint and Android Studio Gradle sync succeed.

## Implementation

MainActivity owns GameViewModel and SettingsViewModel and collects their StateFlows with the
activity lifecycle. An explicit ViewModel factory supplies SavedStateHandle. GameViewModel
serializes revision-stamped actions through a channel; engine work runs on an injected worker
dispatcher, while state publication and snapshot writes happen on Main. The existing engine
is unchanged and remains the only authority for dice, legal moves, bar priority, bearing off,
maximum dice use, turns and scoring. No networking or additional rules engine was added.

Retained Navigation 3 entries observe latest state inside their content, rather than capturing
the first game snapshot. Composables dispatch actions and render immutable state; they do not
apply rules or persist preferences.

The board renders all engine points, both bar stacks and both bearing-off trays. Taps select
legal sources and highlight engine-provided destinations. Equivalent 48dp buttons and semantic
actions support accessibility on narrow boards. Ambiguous die choices use a dialog. Current
player, remaining/used/unplayable dice, end turn, reset confirmation and scored victory dialogs
are localized in English and Georgian. All three existing skins remain data-driven.

Dice results are committed before their visual rotation/bounce. Checker movement uses normalized
before/after coordinates in a separate Canvas overlay. The destination checker (and hit checker)
is suppressed in the committed layer during its flight, preventing duplication. Lifecycle
stop/disposal snaps to committed state. Reduced motion, device animation scale and a 15-second
visual watchdog prevent animation from determining outcomes or indefinitely locking input.

A bounded, versioned SavedStateHandle snapshot preserves committed game data, remaining dice,
selection and dialog flags. It validates restored data and falls back safely. Transient animation
is intentionally not replayed after restoration.

## Modified and added files

Paths below are relative to the project root. No engine source, production dependency,
Gradle wrapper, AGP, compiler, SDK level or Compose BOM was changed.

Updated:

- AGENTS.md
- ARCHITECTURE.md
- app/src/main/java/ge/gogichaishvili/backgammon/MainActivity.kt
- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/BoardCanvas.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/BackgammonApp.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/BoardScreen.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/Strings.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/NavigationTest.kt (environment rule only; existing assertions preserved)
- gradle/libs.versions.toml (Android test libraries only)

Added:

- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/CheckerVisuals.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameUiState.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameSnapshot.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameViewModel.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/InteractiveBoard.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameScreen.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GamePreviews.kt
- app/src/main/res/values/game.xml
- app/src/main/res/values-ka/game.xml
- app/src/debug/AndroidManifest.xml
- app/src/test/java/ge/gogichaishvili/backgammon/presentation/game/GameViewModelTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/GameInteractionTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/GameActivityTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/PageSizeCompatibilityRule.kt
- TASK_04_REPORT.md

The pre-existing MainActivity Greeting/GreetingPreview helpers were retained. Compiler session
files under .kotlin/sessions and build outputs are generated artifacts, not source changes.

## Verification

- `./gradlew build test lint --max-workers=2`: BUILD SUCCESSFUL. The full execution took 55s;
  the latest check after the final test-selector edit took 5s (95 tasks: 3 executed, 92 up-to-date).
  Debug and release assembly are included.
- JVM tests: 347 passed, zero skipped/failures/errors. This includes all 323 existing tests and
  24 new ViewModel/snapshot/visual-state tests.
- Lint: zero errors, 24 warnings. Warnings concern pinned dependency update suggestions,
  existing unused starter resources and the debug-only pageSizeCompat attribute on older APIs.
  No lint baseline or disabled error checks were added.
- `git diff --check`: passed.
- Full emulator run before the final selector correction: 10/11 passed. The remaining assertion
  matched both the board heading and dialog title; it now targets the dialog specifically.
- Final full emulator rerun: 11/11 passed, zero failures/errors/skips; BUILD SUCCESSFUL in
  13m 42s (68 tasks: 5 executed, 63 up-to-date). Command:
  `./gradlew connectedDebugAndroidTest --max-workers=2 -Pandroid.enableAdditionalTestOutput=true -Pandroid.testInstrumentationRunnerArguments.additionalTestOutputDir=/sdcard/Android/media/ge.gogichaishvili.backgammon/additional_test_output`
- Android Studio Gradle sync: finished successfully; default MainActivity deployment succeeded.

The new JVM tests cover opening ties/ownership, repeat rolls/taps, selection and legal targets,
illegal actions, bar entry, both movement directions, bearing off and victory, doubles, passing,
turn switching, hit-overlay slots, confirmation/reset, every phase snapshot, corrupt snapshots,
mid-animation recreation, reduced motion, unspent/blocked dice and stalled visual recovery.

Eight new instrumentation cases cover touch selection/movement, bar entry, hitting, doubles,
die-choice dialogs, bearing-off/game-over, new-game confirmation and actual MainActivity
recreation/landscape. The three existing device tests are retained. Fixtures and deterministic
dice exist only in test sources; the production activity has no debug-position entry point.

## Emulator and screenshots

Device: Pixel_10_Pro(AVD), emulator-5554, Android 17 / API 37, 16 KB kernel.
After the passing suite and successful IDE sync/deployment, MainActivity was manually exercised
with device-coordinate taps: an actual local opening roll produced 3 and 6 for Black; selecting
point 1 highlighted points 4 and 7; tapping point 4 moved one Black checker from 1 to 4, cleared
selection and marked the 3 die used. This was visually verified in device screenshots returned
by the IDE. The app is left open in that local game.

The device suite separately exercised the same production Compose root, ViewModel and engine
with deterministic dice. Coordinate touch injection is used for the select/move flow; other
cases also exercise semantic actions.

Screenshots are original emulator PNGs exported by Gradle, not generated mockups. They are
captured only when the application window is foreground. The game-over image uses a reproducible
test-only near-win position, not a manually played complete match.

Output directory:
`app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/Pixel_10_Pro(AVD) - 17/task04-screenshots/`

- 01-initial.png
- 02-dice.png
- 03-selected.png
- 04-after-move.png
- 05-game-over.png

All five files exist after the passing full run and were visually inspected. These are build outputs; a clean build removes them and a connected
test rerun regenerates them.

Reports:
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_10_Pro(AVD) - 17.xml`

## Compatibility and limitations

Old Espresso 3.5.1 failed on Android 17 with InputManager.getInstance reflection errors.
Only test dependencies were updated to stable Espresso 3.7.0 / AndroidX JUnit 1.3.0.
See [AndroidX Test release notes](https://developer.android.com/jetpack/androidx/releases/test).

The emulator reports native alignment warnings for libdatastore_shared_counter.so and
libandroidx.graphics.path.so. The debug manifest requests page-size compatibility; this emulator
still shows its startup warning. The test-only watcher dismisses only that specific Android
system dialog. This does not repair native ELF/RELRO alignment or demonstrate release 16 KB
compliance. The release manifest is unchanged. See
[Android page-size compatibility](https://source.android.com/docs/core/architecture/16kb-page-size/16kb-backcompat-option).

The emulator is very slow and logged skipped frames. No 60 FPS or performance measurement is
claimed. Portrait and landscape activity behavior are exercised; tablet, large-font and skin
previews were added but are not a substitute for device-wide visual/TalkBack verification.

Saved-instance restoration is supported; durable save/resume after force-stop or intentional
activity finish is not implemented. Dragging, AI opponents, audio/haptic playback, networking,
Supabase, matchmaking and 3D dice are outside this task.

## Recommended next step

Resolve the bundled native-library alignment issue and profile checker/dice animation on a
representative handset, with TalkBack and large-font checks, before adding drag-and-drop or
additional game modes.
