# Task 05 performance evidence — 2026-11-11

Status: final post-fix capture and workload passed. Final target PID 30611.
The polished metric artifacts below contain this final capture, not the earlier provisional run.

## Capture conditions

Pixel_10_Pro AVD, Android 17/API 37, x86_64, 16,384-byte kernel pages,
60.000004 Hz. This is an emulator, not physical-device evidence.
Both successful captures used the non-debuggable, R8-enabled profile package
ge.gogichaishvili.backgammon.profile and the real Choreographer clock.
Profile-only API keep rules support the in-process test harness; production release
shrinking is unchanged. The target was compiled with package compile -m speed -f.

Each workload warms one opening turn, then measures two deterministic turns:
Black double 2, White 3/4, six checker moves total. It then switches royal/ocean/classic
and rotates landscape/portrait, asserting unchanged engine state. Sounds/haptics are disabled.
The baseline still initialized the audio pool despite mute; the polished controller does not.
This and host scheduling variability prevent attributing every timing difference solely to layers.

Raw traces and runner logs:
- app/build/reports/task05/baseline/gameplay.perfetto-trace
- app/build/reports/task05/final/gameplay.perfetto-trace
- corresponding instrumentation.log, recording.log and query-*.txt files.

Durable metric copies: performance-baseline.json and performance-polished.json beside this report.
The initial polished attempt failed a fixed one-second orientation wait; it produced no accepted
measurement set. The successful harness waits for the actual orientation before asserting/settling.

## FrameMetrics results

Times in ms. Draw/layout are callback wall durations, not scheduling-derived running CPU.
The baseline JSON's old drawCpu/layoutCpu keys mean those durations; the polished keys clarify this.

| Stage | Frames baseline / polished | Total p50 baseline / polished | Total p95 baseline / polished | Draw p50 baseline / polished | Draw p95 baseline / polished |
|---|---:|---:|---:|---:|---:|
| Six moves | 47 / 60 | 479.383 / 385.758 | 3641.525 / 508.701 | 6.336 / 1.446 | 48.127 / 12.252 |
| Three skins | 3 / 11 | 3384.202 / 449.177 | 3384.202 / 553.456 | 4.068 / 0.768 | 4.068 / 13.527 |
| Two rotations | 3 / 6 | 5539.310 / 590.748 | 5539.310 / 982.788 | 194.610 / 0.926 | 194.610 / 10.352 |

All recorded frames in both captures exceed 16.67 ms. Callback drops reported zero.
FrameTimeline identifies app deadline misses plus substantial SurfaceFlinger CPU/scheduling
misses. This evidence does **not** demonstrate 60 FPS, nor isolate all causes to the app or host.
Sample counts for themes/rotation are too small for robust percentile conclusions; the
orientation harness now waits explicitly for configuration completion. These are diagnostics,
not a statistically controlled performance claim.

## Confirmed redraw issue and bounded change

Baseline gameplay traced 37 material draws and 37 static checker draws alongside 37 overlay draws.
The material and committed-checker Canvases now have independent graphics layers/display lists.
Polished gameplay shows zero material draws, 19 checker draws and 49 overlay draws. Changed
boards still render after commits; unchanged materials are reused. No bitmap cache or blur added.

Scheduling-derived CPU sums from slices.cpu_time for gameplay:

| Trace section | Baseline samples / CPU ms | Polished samples / CPU ms |
|---|---:|---:|
| Materials | 37 / 86.569 | 0 / 0 |
| Committed checkers | 37 / 163.657 | 19 / 50.992 |
| Board dice | 42 / 49.171 | 24 / 3.446 |
| Control dice | 12 / 39.988 | 12 / 1.711 |
| Moving overlay | 37 / 13.819 | 49 / 10.978 |

The zero material redraw count is direct evidence that layer isolation worked. CPU timing
differences remain subject to emulator noise and different frame counts; no percentage
speedup or guaranteed FPS is claimed. No speculative engine/allocation rewrite was performed.

Composition markers in the polished gameplay stage record 19 compositions each for
GameScreen, InteractiveBoard, BoardCanvas and DiceDisplay, versus 49 overlay draws.
Per-frame motion reads stay in draw/graphicsLayer scopes. The baseline marker helper could
itself be skipped; its counts are not a valid recomposition baseline. It was corrected with
NonRestartableComposable, so no before/after recomposition reduction is claimed.

## Allocation and memory observations

ART cumulative allocation deltas include the entire instrumented process and test overhead:

| Stage | Allocated bytes baseline / polished | GC baseline / polished |
|---|---:|---:|
| Gameplay | 13,565,952 / 13,795,328 | 1 / 5 |
| Themes | 950,272 / 1,835,008 | 0 / 1 |
| Rotation | 3,472,480 / 2,620,512 | 1 / 2 |

Movement allocations are essentially unchanged; these measurements do not identify allocation
sites or prove leak freedom. No heap profiler was run. Across each whole trace, sampled process
RSS peaks were 194.41 / 166.82 MiB. HWUI All Memory counter peaks were 16.49 / 60.30 MiB;
Purgeable HWUI Misc Memory peaks were 8.24 / 35.77 MiB. The higher graphics/cache peak is a
tradeoff to verify on representative hardware, especially during skin crossfades and rotation.
Do not treat a lower RSS peak as proof of a general memory improvement.

No nonzero data_loss or error-severity trace-processor stats were returned. Baseline process
metadata remained named zygote64 at PID 16321; unique BG.Stage markers identify its app process.
The polished config adds task/task_rename and correctly identifies target PID 30611.

## Reproduce and query

Install profile target and test APKs:

```text
./gradlew -PprofileTests=true -I tools/emulator-checks.init.gradle.kts :app:installProfileMeasurement --no-configuration-cache
./gradlew -PprofileTests=true -I tools/game-profile.init.gradle.kts :app:captureGameProfile --no-configuration-cache -PprofileLabel=final
```

The init script uses the official Perfetto record_android_trace helper. The query task uses
the Android Profiler skill's bundled trace_processor wrapper; its path is environment-specific.
Start one warm session per trace, run queries, then stop it with perfettoAction=stop.
All analysis sessions used here were stopped. No temporary SQL files remain.

CPU/draw query (same query on both traces):

```sql
INCLUDE PERFETTO MODULE slices.cpu_time;
INCLUDE PERFETTO MODULE android.frames.timeline;
SELECT s.name AS stage, d.name AS section, count(*) AS samples,
       round(avg(d.dur)/1e6,3) AS avg_wall_ms,
       round(sum(c.cpu_time)/1e6,3) AS total_cpu_ms
FROM thread_or_process_slice s
JOIN thread_or_process_slice d
  ON d.upid=s.upid AND d.ts>=s.ts AND d.ts<s.ts+s.dur
JOIN thread_slice_cpu_time c ON c.id=d.id
WHERE s.name GLOB 'BG.Stage.*' AND d.name GLOB 'BG.Draw.*'
GROUP BY s.name,d.name;
```

Frame classification:

```sql
SELECT s.name AS stage, a.jank_type, count(*) AS frames
FROM thread_or_process_slice s
JOIN android_frames f
  ON f.upid=s.upid AND f.ts>=s.ts AND f.ts<s.ts+s.dur
JOIN actual_frame_timeline_slice a ON a.id=f.actual_frame_timeline_id
WHERE s.name GLOB 'BG.Stage.*'
GROUP BY s.name,a.jank_type;
```

Memory query should filter named byte-valued counters, not divide arbitrary counters by MiB:

```sql
SELECT pct.name, count(*) AS samples,
       min(c.value)/1048576.0 AS min_mib, max(c.value)/1048576.0 AS max_mib
FROM process_counter_track pct JOIN counter c ON c.track_id=pct.id
WHERE pct.upid IN (
  SELECT upid FROM thread_or_process_slice WHERE name='BG.Stage.gameplay'
) AND pct.name IN ('mem.rss','HWUI All Memory','Purgeable HWUI Misc Memory')
GROUP BY pct.name;
SELECT name,value FROM stats WHERE severity IN ('data_loss','error') AND value>0;
```

## Next measurement

Use physical ARM64 16 KB and ordinary 4 KB devices, plus a constrained-memory phone.
Repeat release/profile workloads several times under comparable thermal conditions; include
actual touch latency, audio-enabled startup, graphics-cache retention after rotation/themes,
TalkBack and large text. Establish frame/memory budgets before further optimization.
