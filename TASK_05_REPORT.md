# Task 05 — gameplay polish, persistence and performance

Status: implementation, JVM/build/lint, full emulator suite, process restart and native checks pass.
Final real-clock workload and trace analysis also passed; performance limits are documented below.

## Changes and decisions

- The pure Kotlin rules engine is unchanged. The existing one-module architecture, skins,
  settings, navigation, wrapper, AGP, SDK levels, compiler and Compose BOM are preserved.
- One typed DataStore stores the active match. A small aggregate needs atomic replacement,
  not relational queries or a Room database. Settings remain in their separate singleton
  Preferences DataStore.
- MatchRecord contains committed board/bar/off counts, phase, player, roll, remaining dice,
  dice owner and result dismissal. Schema 2 uses a bounded binary envelope with CRC32 and
  stable IDs; version 1 is read-compatible. Engine/phase validation rejects invalid states.
  Corruption recovers to a durably saved fresh game with a visible notice. Unknown future
  schemas are preserved/read-only, including oversized future payloads that fail without
  invoking corruption replacement.
- GameViewModel saves accepted changes before publishing them. Failed saves preserve the old
  board and retain the exact candidate for retry, including its dice. Read failures lock input
  and offer retry. Legacy SavedStateHandle payloads are validated before import; durable data
  wins over older activity snapshots. Animations and queued input are never persisted.
- Checker flights include compressed stack reflow, lift/scale/shadow, delayed hit-to-bar motion
  and exact tray endpoints. A single animation owner sits outside skin crossfades. Completion
  and interruption are separate actions; cancellation/watchdog expiry snaps to committed data
  without landing sounds. Revision stamps reject repeated/stale input.
- Dice visualize engine-selected faces with two/three rotations, diminishing bounce and
  settling. Normal duration is 520 ms; motion speed, reduced motion and Compose's system scale
  remain effective. No animation generates a game result.
- An Activity-owned feedback implementation provides six original synthesized PCM cues and
  native View haptics. It observes preferences, foreground/focus, output capability, media
  mute/volume and ringer/communication mode. Audio preparation runs on IO, unloaded cues are
  skipped, events are not replayed after restoration, and lifecycle stop/destroy stops/releases
  playback. No permission or external sound asset was added. Music remains a reserved setting.
- Pixel validation exposed a retained board-content snapshot during a simultaneous skin
  change and interrupted hit. The board now receives explicit composable inputs; crossfade
  content observes latest game/settings/progress references. The regression test checks the
  painted palette as well as selection, committed state, interruption and repeated input.
- Trace-confirmed redundant material/checker redraws motivated isolated graphics layers.
  Performance measurements and their memory tradeoffs are documented separately; no engine
  rewrite or speculative allocation optimization was performed.

## Verification

Android Studio Gradle task invocations, shown as wrapper-command equivalents:

| Check | Actual result |
|---|---|
| ./gradlew build test lint | Passed on current source |
| JVM debug tests | 422 passed, 0 skipped, 0 failures/errors; 75 added, original 347 retained |
| Lint | 0 errors, 23 warnings: 15 version notices and 8 unused resources |
| Standard emulator suite | Final post-fix run: 14 passed, 0 failed, 0 skipped; exit 0 |
| Rendered-palette regression after fix | 1 passed |
| Actual process restart | 2 methods passed on current build; seed PID 30416, restored PID 30467 |
| Real-clock profile workload | Final non-debuggable workload passed: 6 moves, 3 skins, 2 orientation changes |
| Gradle sync / IDE Run | Both passed; deployed ordinary app to emulator-5554 |
| git diff --check | Passed, exit 0 |
| Debug/release native checks | Current APKs passed LOAD/RELRO audits and SDK zipalign; 0 errors, exit 0 |

JVM results are summarized in verification/task05/jvm-results.json.
The preserved Gradle instrumentation XML is verification/task05/instrumentation-results.xml.
The final standalone connectedDebugAndroidTest run passed; preserved XML timestamp is
2026-11-11T10:49:05. Earlier device-discovery timeouts and aborted SDK attempts are not counted.
Process-restart logs are preserved in verification/task05/process-restore.txt.

Tests cover real file reopen, all 36 dice pairs, schema/checksum/truncation/corruption,
future-version protection, concurrent writes, IO retry, process-style empty-handle restoration,
legacy instance validation, interrupted effects, duplicate input, feedback policy and PCM data,
stack geometry and final endpoints. Device cases exercise file reopen, visible corruption
recovery and theme changes during an interrupted hit. Existing gameplay tests are preserved.

## Native 16 KB findings

| Original dependency / binary | Root problem | Compatible selected version |
|---|---|---|
| androidx.datastore:datastore-core-android:1.2.0 — libdatastore_shared_counter.so | 64-bit RELRO ended at 0xd000 with writable data following | 1.2.1: end 0xc000, safe containing-LOAD suffix |
| androidx.graphics:graphics-path:1.0.1 — libandroidx.graphics.path.so | RELRO ended at 0x6000 ARM64 / 0x7000 x86_64 with writable data following | 1.1.0: safe containing-LOAD suffix on both 64-bit ABIs |

The original LOAD segment alignment and APK ZIP offsets were already 16 KB compatible.
The root cause was ELF RELRO layout. The baseline's eight failed checks count four affected
64-bit objects twice, in dependency AARs and APKs; they are not eight different libraries.

Updated native files pass the header audit in dependency AARs and APKs. SDK Build-Tools 36.0.0
zipalign -c -P 16 -v 4 independently passes debug and unsigned release packaging with exit 0.
The debug pageSizeCompat override and warning-dismissal thread were removed. The device guard
records 16,384-byte kernel pages and asserts that no compatibility warning is present.

Google's current requirement covers Android 15+ targets on 64-bit Play devices; the current
documentation gives February 1, 2027 for unsupported updates. RELRO may either end on a 16 KB
boundary or occupy the remainder of its containing LOAD segment.
[Official page-size guidance](https://developer.android.com/guide/practices/page-sizes).
Stable release references: [DataStore](https://developer.android.com/jetpack/androidx/releases/datastore)
and [Graphics](https://developer.android.com/jetpack/androidx/releases/graphics).

Reports: verification/task05/native-baseline.txt, native-debug.txt, native-release.txt and packaging.txt.
No ARM64 physical device, signed distribution, or Play-generated split APK was tested.

## Performance and screenshots

Final post-fix capture is recorded in verification/task05/performance-polished.json and
perfetto-polished.txt. Detailed analysis: verification/task05/perfetto_analysis_report.md.
Gameplay: 60 measured frames, total p50 385.758 ms / p95 508.701 ms;
draw-duration p50 1.446 ms / p95 12.252 ms. All total frames exceeded 16.67 ms.
Material redraws fell from 37 to 0 during measured gameplay. Graphics memory peaked at
60.30 MiB versus baseline 16.49 MiB; this cache tradeoff needs physical-device validation.
Emulator noise and changed frame counts prevent a causal percentage-speedup claim.
Raw traces remain under app/build/reports/task05/{baseline,final}/ and are not committed.
Measurements use an isolated non-debuggable .profile package and real Choreographer time;
fixtures do not overwrite the ordinary application's match. No 60 FPS claim is made.

Screenshot output is produced by the instrumentation runner under
app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/Pixel_10_Pro(AVD) - 17/.
Fresh captures in that directory:
- task04-screenshots/01-initial.png, 02-dice.png, 03-selected.png, 04-after-move.png,
  05-game-over.png (existing critical flow tests rerun successfully).
- task05-screenshots/01-selected-classic.png, 02-hit-ocean.png, 03-hit-royal.png.

Ocean and Royal captures were visually inspected: correct painted skin, White on point 5,
Black bar count 1, White off count 14, consumed die 3 and remaining die 1. The theme regression
also asserts sampled Canvas palette pixels, not only screen labels. No recording was captured.

Manual ordinary-app smoke check: IDE Run focused MainActivity, then a real tap on Roll dice
produced engine faces 1/1 and the expected opening-tie message with Roll dice still available.
The UI hierarchy dump failed to produce its file; direct device screenshots confirmed the
screen and response. Saved fixture screenshots above are the reproducible file artifacts.

## Remaining validation and next milestone

Physical-device frame timing, graphics-cache retention, audio/haptic feel, low-memory behavior,
TalkBack and large font scales still need representative-device validation. Optional cues can
be skipped until SoundPool is ready or when system policy prevents playback. No heap-allocation
stack profile or leak proof is claimed. The soundtrack, networking, Supabase, matchmaking and
3D physics are outside this task.

Recommended next milestone: establish release frame/memory budgets on physical ARM64 16 KB
and ordinary 4 KB phones, then tune feedback and accessibility using those measurements.

## Files changed or added by Task 05

Paths are relative to the project root. Existing uncommitted Task 04 work is preserved.

```text
AGENTS.md
ARCHITECTURE.md
TASK_05_REPORT.md
gradle/libs.versions.toml
app/build.gradle.kts
app/profile-target.pro
app/profile-test.pro
app/src/debug/AndroidManifest.xml
app/src/profile/AndroidManifest.xml
app/src/main/java/ge/gogichaishvili/backgammon/BackgammonApplication.kt
app/src/main/java/ge/gogichaishvili/backgammon/MainActivity.kt
app/src/main/java/ge/gogichaishvili/backgammon/animation/GameMotion.kt
app/src/main/java/ge/gogichaishvili/backgammon/animation/MotionPolicy.kt
app/src/main/java/ge/gogichaishvili/backgammon/data/MatchStore.kt
app/src/main/java/ge/gogichaishvili/backgammon/domain/match/MatchRepository.kt
app/src/main/java/ge/gogichaishvili/backgammon/domain/feedback/GameFeedback.kt
app/src/main/java/ge/gogichaishvili/backgammon/feedback/AndroidGameFeedback.kt
app/src/main/java/ge/gogichaishvili/backgammon/feedback/ProceduralAudio.kt
app/src/main/java/ge/gogichaishvili/backgammon/designsystem/BoardCanvas.kt
app/src/main/java/ge/gogichaishvili/backgammon/designsystem/CheckerVisuals.kt
app/src/main/java/ge/gogichaishvili/backgammon/designsystem/RenderTrace.kt
app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameUiState.kt
app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameViewModel.kt
app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameScreen.kt
app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/InteractiveBoard.kt
app/src/main/res/values/game.xml
app/src/main/res/values-ka/game.xml
app/src/test/java/ge/gogichaishvili/backgammon/data/MatchPersistenceTest.kt
app/src/test/java/ge/gogichaishvili/backgammon/data/MatchDiceMatrixTest.kt
app/src/test/java/ge/gogichaishvili/backgammon/feedback/FeedbackTest.kt
app/src/test/java/ge/gogichaishvili/backgammon/presentation/game/DurableGameViewModelTest.kt
app/src/test/java/ge/gogichaishvili/backgammon/designsystem/GameplayMotionTest.kt
app/src/androidTest/java/ge/gogichaishvili/backgammon/GameplayPolishTest.kt
app/src/androidTest/java/ge/gogichaishvili/backgammon/PageSizeCompatibilityRule.kt
app/src/profileTest/java/ge/gogichaishvili/backgammon/GamePerformanceTest.kt
app/src/profileTest/java/ge/gogichaishvili/backgammon/ProcessPersistenceTest.kt
tools/native-audit.init.gradle.kts
tools/game-profile.init.gradle.kts
tools/process-restore.init.gradle.kts
tools/device-verification.init.gradle.kts
tools/emulator-checks.init.gradle.kts
verification/task05/gameplay.pftxt
verification/task05/native-baseline.txt
verification/task05/native-debug.txt
verification/task05/native-release.txt
verification/task05/packaging.txt
verification/task05/performance-baseline.json
verification/task05/performance-polished.json
verification/task05/perfetto-baseline.txt
verification/task05/perfetto-polished.txt
verification/task05/perfetto_analysis_report.md
verification/task05/jvm-results.json
verification/task05/instrumentation-results.xml
verification/task05/process-restore.txt
```
