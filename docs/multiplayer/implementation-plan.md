# Implementation roadmap and TASK07 verification

Specification date 2026-11-12. Documentation only; no SDK, migration, infrastructure,
function deployment, game rule, UI, animation, skin or local persistence change is authorized
by this task. No commit or push.

## Delivery dependencies and release gates

TASK08 Auth -> TASK09 profiles/avatar -> TASK10 schema/manual room workflow ->
TASK11 server engine -> TASK12 Android online gameplay -> TASK13 Quick Play ->
TASK14 rating/history/advanced features.

TASK10 can test atomic initial-match creation using fixture state in local/staging, but
production join/match-start must remain disabled until TASK11 has real rules/dice authority
and TASK12 can recover/render it correctly. “Schema exists” is not permission to launch an
unplayable or client-authoritative match. Custom avatars may be gated independently until
the image-processing security/performance criteria pass. Local Play remains usable throughout.

All new paths below are proposed, not files created in TASK07. Keep one Android module and
the current toolchain. Extract packages/modules only when actual dependencies require it.

## TASK08 — Supabase project setup and Authentication

Files/boundaries:
- Future supabase/config.toml and environment documentation; separate local/staging/prod.
- app domain/auth/AuthRepository.kt, data/network/auth adapter/session storage,
  presentation/auth UI/ViewModel, Home route and AppContainer.
- Future Android credential/App Link/backup configuration and pinned version-catalog entries.
  Inspect the selected community supabase-kt APIs/support before adding a version.

Backend:
- Create/configure project only with explicit implementation authorization; Auth email
  confirmation/password/reset, Google audiences/redirects, custom SMTP, rate/CAPTCHA controls.
- Key separation, JWT verification/session-check contract, minimal protected health/me endpoint.
- Do not create an unprotected game API or Storage bucket as a shortcut.

Android:
- Email register/login/reset, Credential Manager Google ID-token exchange, onboarding-required
  routing placeholder, session StateFlow, refresh serialization and logout.
- Reviewed Keystore-backed session storage excluded from backup; offline route independent.

Security tests:
Wrong audience/nonce/replay/expired token, reset enumeration, malicious redirect, provider
cancel, linked identity, token refresh contention, old-account responses after logout.
Inspect logs and APK for privileged keys; verify offline match survives sign-in/out.

Acceptance:
Both providers work in staging with verified identities; no online action without session;
restart/refresh/logout safe; no private email in public DTO; offline build/tests stay green.
Custom SMTP and deep links verified with release signing, not just debug emulators.

## TASK09 — Profiles, built-in avatars and secure uploads

Files/boundaries:
- Future profile/stats/activity/avatars/deletion migrations and RLS tests.
- Edge profile/avatar/account-delete functions and shared auth/validation/rate-limit helpers.
- Android domain/profile/ProfileRepository, profile/avatar ViewModels/screens and safe cache.

Backend:
- Unique username onboarding creates stats + IDLE activity transactionally; public projection.
- Private avatars bucket, catalogue/default IDs, sanitation/upload/publish saga, short URL
  signing, cleanup/deletion job. Storage limits and byte-level decoder controls enforced.
- Account deletion job may initially have no rooms/games; TASK10/11 extend its cleanup under
  the already-specified lock order before those resources can be created.

Android:
Profile editing, preview/cancel/apply, replace/remove/built-in selection, default fallback.
Refresh public summaries consistently and clear online caches on account change.

Security tests:
Concurrent username claims; cross-user profile edits; MIME/extension spoof, image bombs,
malformed/oversize data, URL signing, stale profile-version replace/remove, deletion crash
at each Storage/Auth stage, old JWT after DELETING and direct Storage permission denials.

Acceptance:
No email/Auth UUID leaks; stats are server-owned; only sanitized approved avatars readable;
pending upload never appears public; no arbitrary remote photo URL; all UI in Georgian/English.
Custom-upload decoder meets Edge CPU/memory limits or custom upload stays disabled.

## TASK10 — Database schema, RLS and manual rooms

Files/boundaries:
- Future supabase/migrations/* rooms/secrets/participants/games/receipts/abuse guards,
  constraint helpers, authorization helpers and local SQL security/concurrency tests.
- Edge rooms/lobby/lifecycle functions, shared transaction/normalization/password helpers.
- Android domain/online room/lobby contracts, data/network adapters, lobby/create/wait/password UI.

Backend:
- Implement actual constraints, private schema/grants and RLS exactly as specified.
- One retained SQL transaction creates/joins/cancels/expires rooms; atomic slot2 + initial game.
- Argon2id library/cost benchmark, abuse reservations, generic errors, no secret projections.
- Private receive-only lobby/room/user invalidations with subscribe/fetch recovery.
- Deletion and expiry housekeeping integrated before room launch.

Android:
Only room name(required) + password(optional) creation inputs. Locked rooms remain listed
with lock icon,1/2 and creator username/avatar/rating-if-present. Bounded prefix search/pages,
refresh/empty/error states, safe password dialog, waiting/cancel/recovery UI.

Security tests:
100-way join barrier, self join, queue/room/active guard conflicts, cancel/expiry/response-loss
races, injection/wildcard search, RLS/RPC/channel denials and exhaustive no-hash/no-email snapshots.
Argon CPU saturation and rate-limit/NAT behavior measured.

Acceptance:
Exactly one slot2/game from concurrent joins; no more than two distinct participants; same-ID
retry stable; terminal/stale rooms absent; blocked/active user cannot join; creating password
room never hides it. Production joins remain feature-gated until TASK11/12 readiness.

## TASK11 — Server Backgammon engine and secure game actions

Files/boundaries:
- Future supabase/functions/_shared/rules/ pure TypeScript reducer + encoder/fixtures,
  game-actions function and transaction/crypto helpers.
- Proposed rules/fixtures/short-v1/ consumed by Kotlin and TS CI; minimal fixture loader tests
  added to current engine tests, not a replacement rules engine on Android.
- Game action/stat/version/hash/receipt triggers and security tests.

Backend:
- Port current deterministic phases/coordinate scheme/whole-turn legal-sequence requirements.
- Server CSPRNG, explicit opening roll including ties, blocked passes, explicit EndTurn,
  bar/hit/doubles/off/single/gammon/backgammon; no cube, reset or client outcome action.
- Lock participants/resources, dedupe, validate, reduce, persist and commit as one transaction.
- Finish/stats/release exactly once. ABORTED is operational, not a fabricated rules result.
- Read snapshot/action history endpoints and version/rules compatibility checks.

Android:
No online gameplay launch yet; DTO codec/contract tests may consume server fixtures.
Keep existing local reducer and all passing tests unchanged.

Security tests:
All existing dice matrices/order traps and complete-game fixtures parity; invalid payloads,
wrong seat/version/phase, replay/commit-unknown, duplicate rolls, race at victory and
tampered snapshots. Secure RNG distribution tests are diagnostic, not proof of unpredictability.

Acceptance:
Kotlin/TS canonical fixtures identical, max dice/higher-die rules preserved, no client dice
or board accepted, normal/edge state transitions deterministic. Benchmark worst-case
double/search CPU and transaction duration against hosted limits before removing gates.
No truncated legal lists or skipped dice allowed as a performance workaround.

## TASK12 — Android online gameplay and Realtime synchronization

Files/boundaries:
- domain/online snapshots/OnlineGameRepository, data/network DTO/HTTPS/Realtime adapters.
- presentation/onlinegame/OnlineGameViewModel and screens, navigation/recovery.
- Minimal reusable rendering/effect input seam in current GameScreen/InteractiveBoard if
  needed; existing BoardCanvas/CheckerVisuals/motion/skins/feedback remain reused.

Backend:
- Participant-only snapshot and bounded action recovery, private commit-driven invalidations,
  live session/authorization checks; telemetry for Broadcast warnings and recovery delay.
- Production room joining enabled only after complete end-to-end acceptance.

Android:
- Serialized one-pending-command UDF, version monotonicity, own seat/current player/dice,
  server legal highlighting and explicit die choices.
- Retry same command after unknown result, resync on foreground/reconnect/gap/30s safety poll.
- Animate contiguous committed state only; cancel/snap on gaps/newer snapshots. Avatar/name
  headers, game completion, offline fallback, reduced motion and lifecycle-safe feedback.

Security/regression tests:
Two accounts plus nonparticipant; forged messages, duplicate/out-of-order/missing-last events,
connection loss before/after commit, stale command after turn change, account swap/process
death, skin changes/rotation/rapid taps during remote updates and saved local-game isolation.

Acceptance:
Both devices converge to exact server board/dice/version; no authoritative optimistic local
move; reconnect requires no room rejoin/new match; six render/movement skin/restore paths
and all original local tests remain green. Measure real devices; no unmeasured60FPS claim.

## TASK13 — Quick Play matchmaking and reconnection

Files/boundaries:
- Future queue/activity constraints/migrations, queue handler/sweeper and concurrency tests.
- MatchmakingRepository/adapters, Quick Play search/cancel/recovery ViewModel/screen.
- Integration with existing user-private activity stream and me/current recovery.

Backend:
FIFO Quick-only eligible tickets, advisory queue gate then sorted row locks, atomic pair+
one initial game, lease/cancel/expire races, no manual-room query fallback.
Extend deletion/logout cleanup and housekeeping. Reconnection protocol already exists in
TASK12; TASK13 extends it to queue assignments and match/cancel races rather than deferring
ordinary game recovery until this milestone.

Android:
Searching, lease renewal, explicit cancellation, matched navigation deduplication,
foreground/process recovery before enqueue, ALREADY_MATCHED handling.

Security tests:
N-user and duplicate-device pairing, two empty-queue enqueues, cancellation barriers,
stale lease, malicious other-user ticket, manual-room exclusion, worker crashes and
lost assignment events. Measure queue-gate contention and waiting-poll costs.

Acceptance:
No self-match or multiple active games, no manual/protected room joining, cancellation
does not delete a committed game, stale users not paired, both participants recover the
same game after lost notifications and restarts.

## TASK14 — Ratings, history and advanced features

Files/boundaries:
Future rating/history migrations/projections, background aggregation and Android screens;
timer/match-length/tournament/cube protocols only through separately reviewed rule versions.

Backend:
Choose rating algorithm/abuse policy, exactly-once result application, bounded privacy-aware
history/pagination/retention and deletion treatment. Load-test and upgrade capacity before
expanded rollout. Do not reinterpret old short-v1 games.

Android:
Rating/history read models, optional advanced UX without bloating the first room form.
Keep offline play and basic two-field room creation discoverable.

Security tests:
Duplicate rating updates, collusion/boosting, aborted match exclusion, history membership,
deleted-account privacy, migrations/backfills and rule-version compatibility.

Acceptance:
Published product rules for scoring/rating/retention, deterministic audited aggregates,
backfill safe to rerun, budgets and load/abuse testing pass. Advanced features remain
explicitly gated, not implied by adding nullable rating columns now.

## Architecture decisions and unresolved release risks

- Trusted TS authority + one real SQL transaction per command; client Kotlin is advisory
  online and authoritative only offline.
- Persistent per-profile occupancy guard across rooms/queue/game, not independent table checks.
- Database commit-driven minimal private invalidations; snapshots/polls are the reliability
  contract. No exactly-once delivery or instant channel revocation promise.
- Default-deny private tables; Edge safe projections and narrowly scoped authorization helpers.
- Argon2id/byte-level avatar sanitation outside resource locks; cross-service deletion saga.
- No paid-plan capacity claim from nominal Free quotas, no unmeasured load-test performance.
- One Android module/explicit DI initially; separate online ViewModel, preserved local file.

Risks/gates: native Google nonce/identity linking integration; community Kotlin SDK/session
storage review; Edge image/hash/rules CPU/memory budget; adversarial queue/join ordering;
role grants/helper search paths; Auth/Storage deletion failures; stale channel metadata;
room/lobby fanout and waiting-poll costs; database growth/vacuum/egress; operational24h abort
policy and retention disclosures; physical-device accessibility/performance validation.
All numbers are initial policy/budget choices. No live backend security or TS parity test
was executed in this documentation milestone.

## TASK07 files and current verification

Exactly these specification files are created:
1. docs/multiplayer/architecture.md
2. docs/multiplayer/protocol.md
3. docs/multiplayer/database-schema.md
4. docs/multiplayer/rooms-and-lobby.md
5. docs/multiplayer/matchmaking.md
6. docs/multiplayer/profiles-and-avatars.md
7. docs/multiplayer/security.md
8. docs/multiplayer/implementation-plan.md

Existing production source, Gradle/SDK/compiler, engine, three skins, animation and local
match formats are unchanged. No live Supabase operation was performed.

Actual verification on 2026-11-12 (fresh report parsing, not prior milestone counts):

| Check | Result |
|---|---|
| Gradle build test lint --max-workers=2 | Build finished successfully |
| JVM JUnit XML totals | 433 passed, 0 failures/errors/skips |
| Debug lint XML | 0 errors, 22 warnings |
| First connectedDebugAndroidTest | 22 passed, 1 failed; exit1 |
| Failed existing case | GameActivityTest.activityRecreationAndLandscapeKeepGameAndConfirmationState, line27: No compose hierarchies found |
| Isolated unchanged GameActivityTest retry | 1 passed |
| Clean full connectedDebugAndroidTest retry | 23 passed, 0 failures/errors/skips |
| Final emulator XML timestamp | 2026-11-12T07:39:26 |
| git diff --check | Passed; tracked production files unchanged |
| Git status | Only new docs/ directory |

The first instrumentation RPC timed out after300s, but its completed XML/exit artifact was
read afterwards; it was NOT counted as passed. Its existing recreation/landscape assertion
failed, while all three checker-movement cases passed. No test or source was changed.
The isolated retry and one full retry passed. Treat this observed run variability as a
test-environment/lifecycle reliability risk, not evidence that online multiplayer was tested.
JVM reports: app/build/test-results/testDebugUnitTest/.
Lint: app/build/reports/lint-results-debug.xml.
Final emulator XML: app/build/outputs/androidTest-results/connected/debug/TEST-Pixel_10_Pro(AVD) - 17.xml.
Initial failure details were read before the retry replaced that build-output XML.
No physical-device or online/backend verification was performed.

All eight Markdown files were reviewed for internal relative links and closed code fences;
the specification's lock order, phases, policies, lease values and endpoint names were
cross-checked. No runnable schema/migration/TypeScript/Kotlin implementation was added.

An IDE terminal inspection transiently returned OS error24 (too many open files); later
Git status/diff checks succeeded. No IDE/JVM restart, environment limit change or build
configuration modification was made. Performance/security/load measurements for the proposed
backend remain implementation gates, not results of this documentation milestone.
