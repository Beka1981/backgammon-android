# Backgammon architecture

## Boundaries

One Android app module with extractable packages; explicit constructor injection in AppContainer.
Presentation -> domain <- data. Design system consumes domain and engine models.
The pure Kotlin engine owns legal sequences, compulsory dice usage, deterministic transitions,
turns and scoring. It has no Android/Compose/storage/network dependency. LocalDice accepts an
injectable DieSource; supplied server dice can use the same reducer in a future authorized task.

- domain/settings and domain/theme: immutable preferences, skins, repository contracts.
- domain/match: committed MatchRecord, phase validation and MatchRepository contract.
- domain/feedback: pure preference/foreground feedback policy and playback interface.
- engine: rules, immutable state, normalized 1000 × 700 geometry; see engine/README.md.
- data: one Preferences DataStore for settings and one typed DataStore for the active match.
- presentation: activity-scoped StateFlow ViewModels, serialized commands, Navigation 3,
  lifecycle-aware collection and stateless screens.
- designsystem: original procedural materials, skin palettes, tokens, board and checker geometry.
- animation: finite motion categories and pure visual curves, never game outcomes.
- feedback: Activity-owned SoundPool/View haptics; original synthesized PCM cues prepared on IO.
- network: documentation only. No Supabase, endpoint, client or secret.

Hilt, Room and extra modules are deferred until real scopes, relational history or multiple
features justify them. Existing wrapper, AGP, SDK, compiler and Compose BOM remain unchanged.
DataStore 1.2.1 and the explicit graphics-path 1.1.0 override resolve the native RELRO
compatibility issue in older transitive libraries; see TASK_05_REPORT.md.

## Authoritative game data and durability

UI action -> single GameViewModel channel -> revision/busy validation -> engine on worker
dispatcher -> MatchRepository atomic write on IO -> immutable UI publication on Main.
Both dice and moves are saved before the visible action is acknowledged. A failed write keeps
the previous committed board, locks gameplay and retains the exact candidate for retry;
retry never rolls again. Failed reads offer retry. A newer schema locks input and is never
overwritten. Save failures do not disappear behind optimistic UI.

Typed DataStore is appropriate for one small aggregate, without queries, relationships or
a match-history database. The bounded BGMN binary envelope stores stable IDs, schema, payload
length and CRC32. Version 2 reads the version 1 payload and rewrites it on the next write.
CRC detects damage, not malicious tampering. Validation covers fifteen checkers per player,
phase, roll, remaining dice, player ownership and legal remaining moves. Corruption recovery
creates and durably saves a fresh game with a visible notice. Future schemas preserve their
bytes. A single application-owned DataStore instance owns each production file.

The active match includes board, bar, borne-off counts, phase, current player, roll, unused
dice, dice owner and result-dialog dismissal. It excludes animations, selection, pending input
and random generator state. Process restart restores exact committed rules data without
replaying animations, audio or haptics. SavedStateHandle can preserve activity-only selection
and confirmation when its engine snapshot matches the durable record. Rotation retains the
activity ViewModel. Disk always wins over an older instance snapshot.

Settings use atomic edits of the latest preferences, stable IDs and safe unknown-value
fallbacks. UI reflects the persisted flow; read/write failures are visible. English and
Georgian resources are bundled for the offline app-local switcher. Navigation 3 back stack
and scroll states use saveable state. No orientation locks or configChanges overrides.

## Rendering, input and motion

BoardCanvas takes immutable engine state. Logical coordinates remain independent of pixels.
Point, bar and tray semantics expose counts and legal actions; equivalent 48dp source/target
buttons support narrow phones, keyboard and TalkBack. Ambiguous bearing-off dice require an
explicit choice. No composable decides legal moves.

Each checker transition captures the before board while the authoritative state is already
committed. Overlay flights suppress their corresponding final slots. Existing compressed
stack members animate between before/after geometry; hit checkers travel below the arriving
checker. Bearing-off ends at the tray's actual slot/radius. Lift, scale and shadow are visual
only. A single checker animation owner outside the skin Crossfade prevents duplicate lifecycle
callbacks while both theme layers sample the same progress. The board composable takes
explicit inputs; retained Crossfade content reads observable latest game/settings/progress
references. This prevents stale palettes and checker snapshots during simultaneous skin changes
and interrupted movement. Integration tests validate rendered palette pixels, not just titles.

Selection, checker and dice motion are finite. Dice use predetermined engine faces with
rotation, diminishing bounce and settling. MotionPolicy respects speed, reduced motion and
Compose's system duration scale. Only Canvas draw/graphicsLayer scopes read per-frame progress.
Static materials and checker layers have isolated graphics layers, motivated by baseline
trace evidence of redundant redraws. No heavyweight graphics engine, particle system or blur.

Successful completion and interruption are different actions. Only a matching successful
landing emits placement/hit/off/victory cues. Lifecycle stop, disposal and the visual watchdog
discard effects and snap to committed state without landing sounds or rule changes.
Revision stamps and busy state reject repeated/stale input.

## Feedback lifecycle

MainActivity collects non-replayed feedback events only while RESUMED and uses current
preferences. Sounds are short original synthesized WAV/PCM cues, prepared on IO and loaded
asynchronously. They never use game randomness. Audio initializes only when enabled on an
audio-output-capable device. Missing/unloaded audio is skipped rather than replayed late.
Ringer mode, communication mode, media mute/volume and window focus gate playback. No audio
focus theft, background loops or extra permissions. Haptics use View.performHapticFeedback
without override flags, allowing Android settings/hardware to decide availability.
Reduced motion also shortens feedback. Stop on activity stop and release on destroy.
The music preference remains reserved; this milestone adds no music soundtrack.

## Verification and measurement

Preserve engine and UI tests. Unit coverage includes file-backed reopen, all dice combinations,
schema migration, checksum/truncation/corruption, future-schema preservation, IO retry and
visual interruption. Instrumentation adds theme changes during a hit, repeated input,
file reopen and visible corruption recovery. Profile-only tests use an isolated application
ID so deterministic fixtures never overwrite the ordinary app's match.

The non-debuggable profile variant inherits release shrinking, uses debug signing solely for
local installation and keeps the few APIs referenced by the in-process measurement harness.
Its manifest is shell-profileable. Opt-in profileTests includes Kotlin measurement sources.
Real Choreographer/FrameMetrics and Perfetto captures cover gameplay, themes and rotation;
whole-process allocation deltas include instrumentation overhead. See verification/task05,
tools/game-profile.init.gradle.kts and TASK_05_REPORT.md. These emulator measurements are
diagnostic, not physical-device benchmarks or proof of 60 FPS. Profile-specific rules do
not alter release shrinking.

Native audits inspect runtime AAR and APK ELF LOAD/RELRO boundaries and ZIP offsets; SDK
zipalign independently checks APK packaging. There is no pageSizeCompat override or warning
dismissal. The instrumentation guard asserts that the compatibility warning is absent.
