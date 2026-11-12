# Task 06 — performance, accessibility and release readiness

Status: implementation and final verification passed: 433 JVM tests, 20 emulator tests,
Gradle build/test/lint, native ELF/ZIP alignment audits, IDE sync and emulator launch.
Physical-device performance and manual accessibility/audio validation remain release gates.

## Small changes, preserved boundaries

The engine, deterministic reducer, durable-before-publication ViewModel, match schema,
feedback lifecycle, navigation architecture, three skins and working toolchain are preserved.
No new production dependency, architecture layer, networking, authentication or Supabase code.

- Cache four local-coordinate checker gradient brushes per renderer (two colors, point/tray
  radii). Other animation radii are computed without retaining a growing cache. No bitmap or
  additional offscreen graphics layer is introduced. Shadows, arcs, gradients, lift and
  easing remain present. A pixel reference retains the original checker drawing in test code.
- Reuse dice pip coordinates instead of building Pair/list combinations while drawing.
- Compute distinct destinations and hit presence outside the per-frame overlay loop, and
  read progress once per frame. Animated state remains in Draw/graphicsLayer phases.
- Preserve the existing material/checker layer isolation; current evidence does not justify
  removing effects or broadly rewriting crossfades to reduce a cache high-water mark.
- Add a dark under-stroke to gold/silver selection and destination rings. Classic gold/field
  contrast alone was 1.64:1; its dark edge is 4.85:1. Raise the neutral control outline from
  2.46:1 to 3.49:1 against the surface. Skin materials and accent colors are unchanged.
- Increase consumed-dice opacity from 35% to 55% using shared emphasis tokens. The minimum
  modeled pip/face contrast across both styles and all skins rises from 2.12:1 to 3.41:1.
  Used/unplayable text remains visible; colors are not the sole status cue.
- Add localized semantic action labels and Button/Image roles, disabled/selected state,
  contextual labels for equivalent source/destination controls, a game heading, and
  accessible image descriptions for settings thumbnails.
- Wrap dice and full status labels with the existing stable FlowRow API. Screenshot review
  exposed four-die overflow at 200% text after an otherwise green run; the strengthened test
  now checks all four 48dp faces and label bounds in English and Georgian. Completed games
  show a localized game-complete hint instead of a stale checker-selection instruction.
- Correct the outdated settings text: sound/haptics work now; music remains reserved.

## Measurements and device status

The prior Task 05 final report was inspected before changes. It measured gameplay total
p50/p95 385.758/508.701 ms, draw p50/p95 1.446/12.252 ms, RSS peak 166.82 MiB,
HWUI peak 60.30 MiB and 19 board compositions versus 49 overlay draws. All total frames
missed 16.67 ms. These are historical emulator observations, not this task's before sample.

A fresh unchanged-APK baseline was captured before the new renderer was installed.
Non-debuggable/R8 profile package, Android 17/API 37, x86_64 16 KB emulator, 60.000004 Hz,
real Choreographer, deterministic six-move workload, three skin requests, two rotations.
Sound/haptics are disabled for paired rendering capture. Baseline target PID 9025.

| Baseline stage | Frames | Total p50 / p95 ms | Draw p50 / p95 ms | ART allocated bytes / GC |
|---|---:|---:|---:|---:|
| Gameplay | 44 | 2992.646 / 4145.051 | 40.410 / 155.580 | 14,254,080 / 4 |
| Themes | 3 | 2331.595 / 2331.595 | 8.267 / 8.267 | 1,179,648 / 0 |
| Rotations | 8 | 6098.124 / 7421.641 | 54.587 / 206.365 | 2,620,512 / 0 |

All baseline total frames exceed 16.67 ms. Callback drops reported zero. The very different
timings from Task 05 demonstrate environment variability; do not infer app regression or
improvement solely from wall-duration differences. ART deltas cover the instrumented process,
including test overhead; they do not identify individual allocation sites.

The final accepted capture (PID 27083) passed. Its raw trace is under
app/build/reports/task05/task06_wrapped; the before trace is under task06_before.
The intermediate task06_after capture timed out waiting for the recorder and is not accepted.
Detailed metrics, validated SQL and caveats: verification/task06/perfetto_analysis_report.md.

| Gameplay measurement | Before | Final after |
|---|---:|---:|
| Frames / over 16.67ms | 44 / 44 | 50 / 50 |
| Total frame p50 / p95 ms | 2992.646 / 4145.051 | 777.429 / 1174.766 |
| Draw duration p50 / p95 ms | 40.410 / 155.580 | 2.519 / 17.364 |
| Whole-process ART allocations / GC | 14,254,080 / 4 | 13,762,560 / 3 |
| Available-trace RSS peak MiB | 170.60 | 150.61 |
| HWUI All Memory peak MiB | 60.30 | 60.30 |
| Purgeable HWUI Misc peak MiB | 35.77 | 35.77 |

The final trace uses a separate 300-second configuration and contains all completed stages.
The earlier after-capture is retained in performance-after-pre-wrap.json and
perfetto-after-pre-wrap.txt; its lower 429.135ms total / 1.226ms draw medians are not the
current result. These observations do not establish a causal percentage speedup or general
memory improvement.
The before trace ends during orientation (open marker); its complete orientation FrameMetrics
remain available, but a paired Perfetto orientation CPU comparison is unsupported. The final
rotation max frame was 2,240.204ms, so the result is not uniformly smooth. Trace loss/error
stats returned no nonzero entries; that does not repair the baseline coverage limit.

Distinct UI-frame roots show substantial sleeping/runnable time. Final gameplay main-frame
wall time was 13715.069ms versus 1821.825ms running CPU; postAndWait alone accumulated
11276.131ms wall / 24.225ms CPU. Both app work and render/scheduling delay remain relevant.
Material redraws stayed at zero during gameplay; owned screen/board/dice composition markers
were 23 before / 20 after, versus 40 / 44 overlay draws. No runaway per-frame owned
recomposition was established. Graphics-cache peaks were unchanged; no effects were removed.

ADB lists only emulator-5554, model sdk_gphone16k_x86_64, ro.kernel.qemu=1,
16,384-byte pages. Its ABI list includes ARM64 translation support; it is still an emulator.
No accessibility service is enabled (enabled_accessibility_services=null). No physical ARM64 phone
was available; no physical sound, haptic, animation or frame-timing verification is claimed.
Exact physical profiling and TalkBack instructions: verification/task06/MANUAL_DEVICE_TESTS.md.

The AOT/non-debuggable paired drawing probe passed. For 2,000 software draws per sample,
original-path allocations were 983,040 / 983,040 / 1,048,576 bytes; cached-path allocations
were 1,638,400 / 294,912 / 196,608 bytes. Medians are 983,040 / 294,912 bytes. The high first
cached sample exposes whole-process noise; do not infer guaranteed application-wide savings.
Calling-thread CPU medians were 2626.821 / 2425.616 ms, with large warm-up/order variation.
This is a software-Canvas component diagnostic, not GPU timing or a frame-rate benchmark.
All raw samples are in drawing-profile-first-accepted.json.

Actual process restart passed both methods: seed PID 17919, restored PID 17963, exact match
and remaining dice restored without effects. Logs: process-restore.txt.

The profile installer now assembles both target and test APKs. Earlier harness failures used
a stale target APK; accepted diagnostics use matched APKs and Java stream output. Drawing
measurement lives only in src/profile and returns JSON to instrumentation, with one exact
profile-only entry-point keep rule. Production release shrinking is unchanged. A fast
workload also exposed insufficient recorder wait margin; the final trace passed using the corrected
recorder wait.

## Accessibility and regression

Completed host checks:
- 433 JVM tests passed, 0 failed/skipped; all original 422 tests preserved.
- Nine parameterized skin checks verify enabled text contrast >=4.5:1, control boundaries
  >=3:1, a contrasting edge for move markers on each point/field material, and readable dice pips
  in available, consumed and unplayable states for both dice styles.
- Two resource checks verify English/Georgian key and format-argument parity and Georgian
  gameplay action strings. This is not a professional translation or pronunciation review.
- Build/test/lint passed; lint has 0 errors, 22 warnings (15 version notices, 7 unused resources).

The first full device run had 19 passes and one new-test selector failure: the victory
assertion matched both the page status and dialog title. It is now scoped to the dialog;
all original 14 tests and the 48 checker pixel-reference combinations passed. The failed
attempt is preserved as instrumentation-first-attempt.xml. The next full run passed all 20, but visual inspection then exposed the large-font dice
layout bug. That pass is archived as instrumentation-before-dice-wrap.xml. The wrapping
fix passed the four focused accessibility cases. A final full run then stalled for about 21 minutes in the activity-recreation case and was explicitly stopped; its incomplete/crashed result is preserved in instrumentation-interrupted.xml. That unchanged recreation/orientation case passed on an isolated retry. The full post-fix retry passed all 20 tests, zero failures/errors/skips, at 2026-11-11T14:45:25Z. The interrupted run is not counted as a pass.

The final passing device suite includes all existing local-game, repeated input, turn/dice,
persistence, theme/hit cancellation, restart and completion cases, plus four accessibility flows
and two pixel-rendering checks (48 skin/color/style/radius combinations and 24 repeated-resize cases).
The paired timing diagnostic runs separately in the non-debuggable profile variant. New accessibility cases exercise semantic equivalent controls, minimum
48dp bounds, Georgian gameplay at 200% Compose font scale, selected/switch/radio state,
language switching and a readable bear-off victory dialog, followed by all four dice/labels fitting at full
48dp face size in both languages. The large-text dialog and settings images were visually
inspected, followed by both final English/Georgian post-wrap dice screenshots. All four faces
and complete status labels fit; localized game-complete guidance appears correctly.

Actual TalkBack speech/focus traversal, physical keyboard behavior, largest system display size,
and physical audio/haptics require manual validation. Semantic tests do not replace them.
Narrow geometric board points retain their real coordinates; equivalent 48dp buttons provide
accessible activation without enlarging/overlapping point geometry. Motion policy retains
reduced motion and Compose system animation scale support.

## Release and multiplayer assessment

Do not claim release-ready 60 FPS from this emulator. Physical ARM64 frame/memory budgets,
graphics-cache retention after repeated transitions, TalkBack traversal, low-memory behavior,
and tactile/audio feel remain release-validation work. No heap-allocation callstack profile,
GPU timing study or leak proof is claimed. Regression coverage uses representative move/turn,
bar/hit/bear-off and completion fixtures; a complete human-played match from opening to victory
was not performed in this task.

The stalled-run investigation used a debuggable-app SIGQUIT request. An attempted adb root
was rejected by automatic approval review because it changes the emulator security boundary;
it was not executed or retried. Non-root test restart and diagnostics remain available.
The optional readiness-diagnostic init task requests SIGQUIT as the app UID only; it does not
root adbd. The first helper attempt was queued behind instrumentation and found no process
after that run was stopped; no Java stack analysis is claimed.

The existing pure engine, repository boundaries and serialized immutable UI state provide a
foundation for planning a server-authoritative Supabase multiplayer milestone. Local persistence
is not a synchronization or anti-cheat protocol. Define server dice authority, action sequencing,
reconnection/conflict behavior and schema/security contracts before adding connectivity.
No multiplayer code was added in this task.

## Task 06 file inventory

Existing uncommitted Task 04/05 work is preserved. Task 06 changes/additions:

- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/BoardCanvas.kt
- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/CheckerBrushes.kt
- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/DiePips.kt
- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/Tokens.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/InteractiveBoard.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/game/GameScreen.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/SettingsScreen.kt
- app/src/main/res/values/game.xml
- app/src/main/res/values-ka/game.xml
- app/src/main/res/values/strings.xml
- app/src/main/res/values-ka/strings.xml
- app/src/test/java/ge/gogichaishvili/backgammon/AccessibilityContrastTest.kt
- app/src/test/java/ge/gogichaishvili/backgammon/LocalizedResourcesTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/ReferenceCheckerDrawing.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/CheckerDrawingTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/GameplayAccessibilityTest.kt
- app/profile-target.pro
- app/profile-test.pro
- app/src/profile/java/ge/gogichaishvili/backgammon/DrawingProbe.kt
- app/src/profileTest/java/ge/gogichaishvili/backgammon/ProfileDrawingMeasurementTest.kt
- tools/emulator-checks.init.gradle.kts
- tools/game-profile.init.gradle.kts
- tools/readiness-checks.init.gradle.kts
- TASK_06_REPORT.md
- verification/task06/MANUAL_DEVICE_TESTS.md
- verification/task06/devices.txt
- verification/task06/performance-before.json

Additional evidence files:
- verification/task06/drawing-profile.json
- verification/task06/drawing-profile-first-accepted.json
- verification/task06/gameplay.pftxt
- verification/task06/performance-after.json
- verification/task06/performance-after-pre-wrap.json
- verification/task06/perfetto-after-pre-wrap.txt
- verification/task06/instrumentation-before-dice-wrap.xml
- verification/task06/perfetto-before.txt
- verification/task06/perfetto-after.txt
- verification/task06/perfetto-schema.txt
- verification/task06/perfetto_analysis_report.md
- verification/task06/process-restore.txt
- verification/task06/instrumentation-first-attempt.xml
- verification/task06/connected-task.txt

- tools/readiness-diagnostic.init.gradle.kts
- verification/task06/instrumentation-interrupted.xml
- verification/task06/instrumentation-results.xml
- verification/task06/instrumentation-results.txt
- verification/task06/jvm-results.txt
- verification/task06/host-verification.txt
- verification/task06/native-debug.txt
- verification/task06/native-release.txt
- verification/task06/packaging.txt

## Final verification and captured images

| Check | Actual result |
|---|---|
| Gradle build test lint :app:assembleDebugAndroidTest --max-workers=2 | Passed |
| JVM tests | 433 passed, 0 failed/errors/skipped |
| Final connectedDebugAndroidTest | 20 passed, 0 failed/errors/skipped |
| Focused accessibility / isolated recreation retry | 4 passed / 1 passed |
| Separate process-restart verification | 2 methods passed; PID changed |
| Non-debuggable drawing diagnostic / real-clock workload | 1 passed each |
| Lint | 0 errors; 22 warnings (15 version notices, 7 unused resources) |
| Native debug/release ELF and stored-ZIP audit | TOTAL_ALIGNMENT_ERRORS=0 for both |
| SDK zipalign -c -P 16 -v 4 | Debug and unsigned release passed, exit 0 |
| Final IDE Gradle sync / Run deployment | Passed; MainActivity visible on emulator-5554 |
| git diff --check | Passed, exit 0 |

Original tests remain present. No physical device was connected. The interrupted run and
initial selector failure are recorded above; only the final clean run is the regression result.
No production source changed after the final host checks. No commit or push was performed.

Screenshots generated by the final suite are under:
app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/Pixel_10_Pro(AVD) - 17/task06-screenshots/

- 01-georgian-large-dialog.png
- 02-large-victory.png
- 03-georgian-large-settings.png
- 04-large-dice.png — final English full-size dice and completion hint; inspected
- 05-georgian-large-dice.png — final Georgian full-size dice and completion hint; inspected

Existing Task 04/05 interaction tests also regenerate initial/selected/moved/victory and
three-skin evidence under their additional-output directories. The final normal app launch
was viewed with the Studio screenshot tool; that tool returned an image without a local
artifact path, so no saved launch-image path is claimed. No recording was captured.

Recommended next step: validate frame/memory budgets, TalkBack and audio/haptics on physical
ARM64 hardware using MANUAL_DEVICE_TESTS.md. In parallel, plan the multiplayer server-authority,
action revision/idempotency, reconnect and security contracts; add Supabase only in its
explicit implementation milestone.
