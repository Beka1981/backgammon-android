# Checker movement investigation

Status: reported no-selection regression not yet reproduced. No production fix is claimed.
The exact failing skin/player/dice/point combination has been requested.

## Compared implementation

Compared the Task 06 working tree to ac75285 (Task 05: Polish animations, add sound and durable
game persistence), the last known working commit. Inspected InteractiveBoard, BoardCanvas,
CheckerBrushes, GameScreen, GameUiState, GameViewModel, BoardGeometry, CheckerVisuals,
BackgammonApp and existing movement/persistence instrumentation.

Task 06 changes reuse four fixed-size local-coordinate gradient brushes, translate drawing
to checker-local coordinates, reuse dice pips, precompute destination/hit data, read progress
once per overlay draw, and add accessibility semantics/contrast/layout improvements.

The Task 06 diff does not change the engine, ViewModel action reducer, pointer-input keys,
tap-to-normalized-coordinate conversion, board geometry, durable persistence, checker flight
geometry, latest-state navigation/crossfade handling or animation completion logic.
Inspection alone cannot prove the cause of a user-reported failure.

## Ordinary-app reproduction

Deployed the current debug application to emulator-5554 (Pixel 10 Pro, API 37, x86_64 16 KB).
After the user confirmed the emulator was idle:

1. Launched the ordinary app at opening position.
2. Tapped Roll Dice; real engine opening roll was 4/6, Black to move.
3. Tapped the actual top checker on point 17.
4. Point 17 became selected, and points 21/23 exposed legal destination actions.
5. Tapped point 21; point 17 changed 3 -> 2, point 21 changed 0 -> 1; die 4 was consumed.
   The screenshot showed the checker at point 21.
6. Selected another point-17 checker and tapped point 23.
   Point 17 changed 2 -> 1, point 23 changed 0 -> 1; die 6 was consumed.
   The screenshot showed the second checker at point 23 and enabled End turn.

Thus touch receipt, selection, legal move generation, accepted moves and final checker
rendering worked in this particular ordinary-app session, before any production edits.
Initial inspections were affected by concurrent emulator activity; those are not evidence
of a rendering defect.

## New regression coverage

app/src/androidTest/java/ge/gogichaishvili/backgammon/CheckerMovementRegressionTest.kt

Three cases: Classic, Royal and Ocean. Each uses a deterministic real engine opening roll,
normal animations, real physical-coordinate pointer taps on the board, and a temporary
actual DataStore match file:
- Select point 8; require selected semantics and a visible selection outline.
- Require legal point-7 semantics AND a visible destination marker on the emulator surface.
- Tap the visible marker; require authoritative point counts, consumed die and durable save.
- Wait for animation completion; require the source checker pixels to disappear and
  destination checker pixels to match the source checker material.
- Clear ViewModel/store, cancel DataStore scope and reopen the file with a fresh ViewModel.
- Verify exact game and remaining die, then make and pixel-check another move (13 -> 7).

Screen sampling uses UiAutomation.takeScreenshot and crops the real board window bounds.
It does not call Compose captureToImage to force a redraw. State transitions are emitted
with tag MoveRegression and saved to transitions.txt beside the screenshots.

Diagnostic history:
- Initial Compose PixelCopy version passed Classic/Royal; Ocean PixelCopy timed out before
  the first tap. This was a capture failure, not an accepted-move failure.
- Direct screen sampling exposed a flawed marker-center probe for Ocean: the saved image
  visibly contained selection and both destination rings; its pale center differed from
  the light point by less than the threshold.
- A dark-edge-only probe was similarly too close to Royal's dark field.
- The final probe samples the fill and both contrasting rings. No move, selection, engine
  or persistence assertion was weakened. The Ocean before/selected images were inspected;
  the Royal dark-edge probe was too close to its field color by the palette values.
- Final full suite passed all 23 tests, including all three new cases. No production
  source changes were needed to obtain that pass.

## Changed files for this investigation

- app/src/androidTest/java/ge/gogichaishvili/backgammon/CheckerMovementRegressionTest.kt
- CHECKER_MOVEMENT_INVESTIGATION.md
- verification/checker-movement/jvm-results.txt
- verification/checker-movement/instrumentation-results.xml
- verification/checker-movement/verification.txt

Existing Task 06 edits remain present. No production source, engine, dependencies, settings,
networking or secrets were changed for this investigation. No commit or push was performed.

## Verification

Gradle build test lint :app:assembleDebugAndroidTest --max-workers=2: passed.
JVM: 433 passed, zero failures/errors/skips (parsed from fresh JUnit XML).
Instrumentation: 23 passed, zero failures/errors/skips, final XML timestamp
2026-11-11T15:13:49; all original 20 cases preserved and three movement cases added.
Lint: zero errors, 22 warnings.
Final Ocean before/selected/after/restored-move images were visually inspected.
Classic/Royal movement pixels are checked by the passing tests.
No physical-device result is claimed.

Screenshot and transition-log base:
app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/Pixel_10_Pro(AVD) - 17/movement-regression/

Each of classic/, royal/, ocean/ contains:
- 01-before-board.png and 01-before-screen.png
- 01-selected-presented-board.png and 01-selected-presented-screen.png
- 01-after-board.png and 01-after-screen.png
- 02-restored-board.png and 02-restored-screen.png
- 03-restored-move-before-board.png and -screen.png
- 03-restored-move-selected-presented-board.png and -screen.png
- 03-restored-move-after-board.png and -screen.png
- transitions.txt (selected -> committed/busy -> settled; restored -> next committed/settled)

These are actual emulator captures. Build-output artifacts can be removed by Gradle clean.
The ordinary-app manual screenshots were viewed through Studio but that tool did not provide
a local saved path; the reproducible saved images above are from instrumentation.

## Unresolved

There is currently no evidence identifying a specific Task 06 change as the cause.
The reported symptom is no selection/destinations. The user has been asked for the precise
skin, current player, dice values and tapped point. A recording of the failing interaction
would also distinguish a stale/blocked screen from a wrong-player, illegal-source or
animation-busy interaction. Those are possibilities to investigate, not diagnoses.

Do not treat passing regression tests as proof that this unobserved failure has been fixed.
