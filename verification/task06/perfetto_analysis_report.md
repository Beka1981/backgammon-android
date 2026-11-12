# Task 06 — measured performance analysis

## Scope and capture conditions

Before target PID 9025; final target PID 27083. Pixel_10_Pro AVD, Android 17/API 37,
sdk_gphone16k_x86_64, 16,384-byte pages, ro.kernel.qemu=1, 60.000004 Hz. Only this emulator
was connected. No physical ARM64 device, physical audio/haptic feel or 60 FPS result is claimed.

Both accepted captures use the non-debuggable, R8-enabled .profile target, package compile
-m speed -f, a real Choreographer clock, one warm-up turn and the same deterministic workload:
six moves (Black double 2, White 3/4), three skin requests, landscape then portrait.
Sound/haptics are disabled. Theme requests wait 500ms; under slow rendering some requests
can coalesce. Do not interpret small theme sample counts as three complete, smooth fades.

The profile installer now assembles the target APK as well as the instrumentation APK.
A profile-only DrawingProbe exposes JSON across APK boundaries; production release rules are
unchanged. These harness fixes prevent stale-APK/class-loader measurement failures.
Failed setup attempts and the intermediate recorder-timeout capture are not accepted results.

The final capture includes the large-font dice-wrap correction and the localized completed-game
hint. It uses the separate 300-second config in verification/task06/gameplay.pftxt; the before
config remains 180 seconds. This duration difference also limits whole-trace memory comparisons.
Pre-wrap after-data are preserved separately in performance-after-pre-wrap.json and
perfetto-after-pre-wrap.txt; their 429.135ms total / 1.226ms draw medians are not the final result.

Raw accepted traces:
- app/build/reports/task05/task06_before/gameplay.perfetto-trace
- app/build/reports/task05/task06_wrapped/gameplay.perfetto-trace

Durable FrameMetrics: performance-before.json and performance-after.json.
Raw validated SQL results: perfetto-before.txt and perfetto-after.txt. The before export can
contain wrapper timing logs; the final export separates stderr. Multiple query result sets are
included, so these are diagnostic text rather than one CSV table.

## Frame durations

Milliseconds, before / after. Draw duration is callback wall time, not running CPU.

| Stage | Frames | Total p50 | Total p95 | Draw p50 | Draw p95 |
|---|---:|---:|---:|---:|---:|
| gameplay | 44 / 50 | 2992.646 / 777.429 | 4145.051 / 1174.766 | 40.410 / 2.519 | 155.580 / 17.364 |
| theme | 3 / 4 | 2331.595 / 1054.760 | 2331.595 / 1080.823 | 8.267 / 1.247 | 8.267 / 8.442 |
| orientation | 8 / 7 | 6098.124 / 1311.858 | 7421.641 / 1851.128 | 54.587 / 16.235 | 206.365 / 18.278 |

All recorded total frames exceeded 16.67ms. Metrics callback drops were zero; that does
not mean zero missed display deadlines. FrameTimeline tags the sampled frames with app
deadline misses and widespread SurfaceFlinger CPU/scheduling misses and resync jitter.

The final rotation max total duration was 2,240.204ms (before 10,778.439ms); final max
layout duration was 843.226ms. The earlier pre-wrap after-capture had a 16,949.105ms
rotation outlier; both after-captures are retained rather than selecting the faster result. There is no general smoothness or across-the-board win here.
Few rotation/theme samples make percentiles weak estimates of a population distribution.

Task 05's final gameplay draw p50 was already 1.446ms, whereas this task's fresh unchanged
baseline was 40.410ms. This large variability predates the cache change. Do not attribute the
entire before/after difference to the implementation or publish a percentage FPS speedup.

## Trace coverage and UI-thread findings

The before trace spans 177.055s and ends while BG.Stage.orientation is still open (dur=-1).
Its gameplay (63.800s) and theme (4.003s) markers are complete. The final trace spans 299.319s
and includes all three completed stages: 18.075s / 1.641s / 9.386s. FrameMetrics output covers
the complete orientation workload in both runs, but a paired Perfetto orientation CPU
comparison is unsupported. No nonzero data_loss/error stats were returned; that alone
does not establish complete workload coverage.

Main UI frames are selected using distinct android_frames.do_frame_id values. Prefix-matching
every Choreographer#doFrame slice would also count nested callbacks and overcount time.

| Gameplay UI frame-root time | Before ms (45 frames) | After ms (50 frames) |
|---|---:|---:|
| Total wall | 45,739.847 | 13,715.069 |
| Running CPU | 4,807.518 | 1,821.825 |
| Runnable / preempted (R + R+) | 10,994.296 | 801.867 |
| Sleeping (S) | 29,935.795 | 11,091.377 |
| Uninterruptible (D) | 2.237 | 0 |

Most wall time is outside running CPU. The main thread's postAndWait slices total
30,651.468ms wall / 49.251ms CPU before and 11,276.131ms wall / 24.225ms CPU after.
This identifies render-pipeline waiting alongside scheduler delay; it does not prove that
all jank comes from the host. After-frame running CPU averages about 36.4ms, exceeding
a 16.67ms budget, and includes application/framework work.

Inclusive gameplay CPU totals:
- Recomposer:recompose: 2590.277ms before / 1028.395ms after.
- AndroidOwner:onMeasure: 708.489 / 223.604ms.
- Record View#draw(): 933.584 / 295.427ms.

These nested totals are not additive. No Java method/allocation callstack profile was
recorded, so there is insufficient evidence to rewrite the ViewModel, rules or layout tree.

## Drawing, recomposition and bounded optimization

| Gameplay draw section | Before samples / CPU ms | After samples / CPU ms |
|---|---:|---:|
| Materials | 0 / 0 | 0 / 0 |
| Committed checkers | 23 / 421.803 | 20 / 117.680 |
| Board dice | 30 / 11.223 | 24 / 9.173 |
| Control dice | 12 / 4.727 | 12 / 5.664 |
| Moving overlay | 40 / 14.597 | 44 / 28.618 |

Materials were already isolated from moving overlays; no new full-board bitmap/offscreen
cache was added. Fixed-size checker brushes are reused (two colors, 26/18-unit radii);
intermediate flight radii are not retained in a growing cache. Pip coordinates are fixed
read-only data. Distinct destinations/hit detection move out of the per-frame loop; progress
is read once per draw. Shadows, gradients, easing, hit/bar/tray motion and layers remain.

Owned composition markers each record 23 executions before / 20 after for GameScreen,
InteractiveBoard, BoardCanvas and DiceDisplay, versus 40 / 44 overlay draws. This supports
existing draw-phase state isolation. Different frame counts and runtime conditions prevent
claiming a causal recomposition reduction; framework recomposer callbacks are not equivalent
to these four owned-marker counts.

The cache was motivated by measured checker recording cost and draw-time brush construction.
Pixel-reference checks cover all skins/colors/styles/radii; the final regression suite also
checks reuse across repeated canvas-size changes. See TASK_06_REPORT.md for final test status.

## Paired software drawing probe

One AOT/non-debuggable target-side probe, three alternating pairs of 2,000 software draws:

| Pair | Original allocated bytes / CPU ms | Cached allocated bytes / CPU ms |
|---|---:|---:|
| 1 | 983,040 / 3260.018 | 1,638,400 / 2596.317 |
| 2 | 983,040 / 2626.821 | 294,912 / 2425.616 |
| 3 | 1,048,576 / 2329.447 | 196,608 / 2078.388 |
| Median | 983,040 / 2626.821 | 294,912 / 2425.616 |

Allocation counters include the entire process, not only the calling thread; the first
cached outlier is retained, not discarded. Warm-up/order/runtime noise is visible.
This confirms reusable fixed-size brushes and provides allocation diagnostics; it does
not prove a guaranteed application-wide reduction, GPU improvement or frame rate.
The probe exercises Classic ivory at the normal radius; other colors/radii have pixel
correctness coverage, not separate performance measurements. Intermediate flight brushes
still allocate. Raw data: drawing-profile-first-accepted.json (also drawing-profile.json).

## Memory and textures

| Sampled counter | Before peak MiB | After peak MiB |
|---|---:|---:|
| Process RSS, available whole trace | 170.60 | 150.61 |
| Gameplay RSS | 166.75 | 149.92 |
| Gameplay HWUI All Memory | 16.44 | 16.44 |
| HWUI All Memory, available whole trace | 60.30 | 60.30 |
| Purgeable HWUI Misc Memory | 35.77 | 35.77 |

No graphics-cache peak reduction is demonstrated. Transitions/rotation still reach 60.30MiB;
graphics/cache retention after repeated cycles and low-memory pressure needs representative
hardware. RSS samples have different stage durations and trace coverage; a lower observed
peak is not proof of general memory improvement or leak freedom. HWUI and RSS are separate
counters and must not simply be added as total memory.

Whole-process ART allocations / GC:

| Stage | Before bytes / GC | After bytes / GC |
|---|---:|---:|
| Gameplay | 14,254,080 / 4 | 13,762,560 / 3 |
| Theme requests | 1,179,648 / 0 | 1,146,880 / 0 |
| Orientation | 2,620,512 / 0 | 2,784,352 / 1 |

Texture-upload slices are present: largest observed before event 360x58 at 193.061ms wall,
and final upload event 492x72 at 21.642ms wall. These are different events across whole traces,
not a controlled texture-speed comparison. Source uses procedural Canvas materials and
bounded circle/hardware-elevation shadows, without bitmap artwork or blur textures.
GPU timing and per-allocation native stacks were not measured. There is no evidence here
justifying removal of premium effects.

## Reproduce

```text
./gradlew -PprofileTests=true -I tools/emulator-checks.init.gradle.kts :app:installProfileMeasurement --no-configuration-cache
./gradlew -PprofileTests=true -I tools/readiness-checks.init.gradle.kts :app:measureReadinessDrawing --no-configuration-cache
./gradlew -PprofileTests=true -I tools/game-profile.init.gradle.kts :app:captureGameProfile --no-configuration-cache -PprofileLabel=repeat_run -PprofileConfig=verification/task06/gameplay.pftxt
```

The final 300-second window includes every completed stage. The recorder wait derives from
configuration duration plus transport margin, and query stderr is separate from final raw results.
Use a longer trace window for slow environments when repeating the full rotation workload.
Physical-device installation/profiling, TalkBack, audio/haptics and system-scale steps are
in MANUAL_DEVICE_TESTS.md. Repeat under comparable thermal/power conditions before setting
release frame/memory budgets. The query helper uses the Android Profiler skill's bundled
trace_processor; its absolute path is environment-specific.

All three warm analysis sessions (before, pre-wrap and final) were stopped. No temporary SQL files were created.
Schemas were validated with LIMIT 0 and stdlib metadata, including slices.cpu_time,
slices.time_in_state and android.frames.timeline.

## Validated SQL

```sql
INCLUDE PERFETTO MODULE slices.cpu_time;
INCLUDE PERFETTO MODULE slices.time_in_state;
INCLUDE PERFETTO MODULE android.frames.timeline;
SELECT s.name stage,d.name section,count(*) samples,round(avg(d.dur)/1e6,3) avg_wall_ms,round(sum(c.cpu_time)/1e6,3) cpu_ms
FROM thread_or_process_slice s JOIN thread_or_process_slice d ON d.upid=s.upid AND d.ts>=s.ts AND d.ts<s.ts+s.dur
JOIN thread_slice_cpu_time c ON c.id=d.id WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND d.name GLOB 'BG.Draw.*' GROUP BY s.name,d.name;
SELECT s.name stage,d.name composition,count(*) count FROM thread_or_process_slice s JOIN thread_or_process_slice d
ON d.upid=s.upid AND d.ts>=s.ts AND d.ts<s.ts+s.dur WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND d.name GLOB 'BG.Compose.*' GROUP BY s.name,d.name;
SELECT s.name stage,CASE WHEN d.name GLOB 'Choreographer#doFrame*' THEN 'Choreographer#doFrame' ELSE d.name END section,
count(*) samples,round(sum(d.dur)/1e6,3) wall_ms,round(sum(c.cpu_time)/1e6,3) cpu_ms,round(max(d.dur)/1e6,3) max_wall_ms
FROM thread_or_process_slice s JOIN thread_or_process_slice d ON d.upid=s.upid AND d.ts>=s.ts AND d.ts<s.ts+s.dur
JOIN thread t ON t.utid=d.utid JOIN thread_slice_cpu_time c ON c.id=d.id
WHERE s.name='BG.Stage.gameplay' AND d.dur>0 AND t.is_main_thread=1 AND d.name NOT GLOB 'Choreographer#doFrame*' GROUP BY section ORDER BY cpu_ms DESC LIMIT 20;
WITH measured_frames AS (
SELECT DISTINCT s.name stage,f.do_frame_id id FROM thread_or_process_slice s JOIN android_frames f
ON f.upid=s.upid AND f.ts>=s.ts AND f.ts<s.ts+s.dur WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND f.do_frame_id IS NOT NULL)
SELECT m.stage,z.state,round(sum(z.dur)/1e6,3) time_ms FROM measured_frames m JOIN thread_slice_time_in_state z ON z.id=m.id GROUP BY m.stage,z.state;
WITH measured_frames AS (
SELECT DISTINCT s.name stage,f.do_frame_id id FROM thread_or_process_slice s JOIN android_frames f
ON f.upid=s.upid AND f.ts>=s.ts AND f.ts<s.ts+s.dur WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND f.do_frame_id IS NOT NULL)
SELECT m.stage,count(*) frames,round(sum(d.dur)/1e6,3) wall_ms,round(sum(c.cpu_time)/1e6,3) cpu_ms,
round(max(d.dur)/1e6,3) max_wall_ms FROM measured_frames m JOIN slice d ON d.id=m.id JOIN thread_slice_cpu_time c ON c.id=m.id GROUP BY m.stage;
SELECT s.name stage,round(s.dur/1e9,3) seconds FROM thread_or_process_slice s WHERE s.name GLOB 'BG.Stage.*';
SELECT round((trace_end()-trace_start())/1e9,3) trace_seconds;
SELECT s.name stage,d.name section,count(*) samples,round(sum(d.dur)/1e6,3) wall_ms,round(sum(c.cpu_time)/1e6,3) cpu_ms,
round(max(d.dur)/1e6,3) max_wall_ms FROM thread_or_process_slice s JOIN thread_or_process_slice d
ON d.upid=s.upid AND d.ts>=s.ts AND d.ts<s.ts+s.dur JOIN thread t ON t.utid=d.utid JOIN thread_slice_cpu_time c ON c.id=d.id
WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND t.is_main_thread=1
AND d.name IN('postAndWait','AndroidOwner:onMeasure','Recomposer:recompose','Record View#draw()') GROUP BY s.name,d.name;
SELECT s.name stage,a.jank_type,count(*) frames FROM thread_or_process_slice s JOIN android_frames f
ON f.upid=s.upid AND f.ts>=s.ts AND f.ts<s.ts+s.dur JOIN actual_frame_timeline_slice a ON a.id=f.actual_frame_timeline_id
WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 GROUP BY s.name,a.jank_type;
SELECT pct.name,count(*) samples,round(min(c.value)/1048576,2) min_mib,round(max(c.value)/1048576,2) max_mib
FROM process_counter_track pct JOIN counter c ON c.track_id=pct.id
WHERE pct.upid IN(SELECT upid FROM thread_or_process_slice WHERE name='BG.Stage.gameplay')
AND pct.name IN('mem.rss','HWUI All Memory','Purgeable HWUI Misc Memory') GROUP BY pct.name;
SELECT s.name stage,pct.name,count(*) samples,round(min(c.value)/1048576,2) min_mib,round(max(c.value)/1048576,2) max_mib
FROM thread_or_process_slice s JOIN process_counter_track pct ON pct.upid=s.upid JOIN counter c ON c.track_id=pct.id AND c.ts>=s.ts AND c.ts<s.ts+s.dur
WHERE s.name GLOB 'BG.Stage.*' AND s.dur>0 AND pct.name IN('mem.rss','HWUI All Memory','Purgeable HWUI Misc Memory') GROUP BY s.name,pct.name;
SELECT d.thread_name,d.name,count(*) samples,round(sum(d.dur)/1e6,3) wall_ms,round(sum(c.cpu_time)/1e6,3) cpu_ms
FROM thread_or_process_slice d JOIN thread_slice_cpu_time c ON c.id=d.id
WHERE d.upid IN(SELECT upid FROM thread_or_process_slice WHERE name='BG.Stage.gameplay') AND regexp('texture|upload|blur|shadow',d.name,'i')
GROUP BY d.thread_name,d.name ORDER BY wall_ms DESC LIMIT 10;
SELECT name,idx,severity,source,value FROM stats WHERE severity IN('data_loss','error') AND value>0;
SELECT pid,name,cmdline FROM process WHERE upid IN(SELECT upid FROM thread_or_process_slice WHERE name='BG.Stage.gameplay');
```
