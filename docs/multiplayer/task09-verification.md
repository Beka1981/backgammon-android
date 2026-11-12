# TASK09 verification and change inventory

## Scope

Repository inspected at 2244c9d (TASK08), with TASK07 baseline d4958e6 and the existing regression fix in history. Initial git status was clean. AGENTS.md, actual Auth/navigation/container/Gradle code and multiplayer schema/profile/security documents were reviewed before implementation.

No remote SQL, Auth-user mutation, function deployment or Storage change was performed. A standalone local PostgreSQL cluster at /private/tmp/backgammon-task09-pg, port 55439, Unix socket only (no TCP listener), hosts disposable synthetic Auth fixtures. No live Supabase account was created. No client credential values were printed.

## Current results

- First compile exposed a wrong existing string resource name; corrected to auth_local.
- ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest: BUILD SUCCESSFUL in 29s.
- First complete ./gradlew build test lint --max-workers=2 --console=plain: BUILD SUCCESSFUL in 4m14s; 145 tasks, 53 executed, 92 up-to-date.
- JVM report: **537 tests, 0 failures, 0 skipped**, 4.270s in the final Android run. Includes 475 existing + 62 new profile tests (23 parameterized validation, 3 model, 19 ViewModel, 17 mocked-HTTP repository).
- Lint: **0 errors, 31 warnings**. Existing update notices/resources/intent-filter formatting/style warnings remain; no suppression or baseline was introduced.
- SQL migration compiled/applied on the disposable local PostgreSQL instance. An initial CASE syntax error was corrected before success. Final profiles-security.sql: **36 passing assertions**, exit 0, including actual client RPC/write denials. A test's escaped newline fixture was corrected to contain a real JSON control escape. Tests roll back synthetic rows.
- Local SQL concurrency: pgbench with 8 clients/2 threads, 1 transaction each, processed **8/8**, 0 failed transactions. Three post-run assertions passed: one claimed username/profile, one stats + IDLE row, and all eight calls across two identities. The temporary server was stopped afterward. These are local SQL tests, not live HTTP/Supabase concurrency tests.
- TypeScript validation: **31 tests passed, 0 failed**, node --test supabase/functions/player-profile/validation.test.ts.
- Deno strict check with supabase/functions/deno.json passed, including the certificate-verification-enabled TLS configuration (no remote TLS handshake was tested). Final strict check passed using Postgres.js 4.1.1 and hosted publishable-key dictionary handling. The integrity lock contains only that pinned driver; the final --frozen strict check passed (exit 0).
- First connected-test run was intentionally stopped after 1m50s to add an account-binding UI guard found during review. It is not counted as a completed test result. No existing test was removed/weakened.
- Intermediate Android build after the owner-binding guard passed in 2m58s. Final ./gradlew build test lint including preview corrections: **BUILD SUCCESSFUL in 3m07s**, exit 0; 145 tasks, 28 executed, 117 up-to-date. JVM **537 passed, 0 failed, 0 skipped**; lint **0 errors, 31 warnings**. git diff --check passed; git check-ignore confirms local.properties and backend .env/.env.production are excluded.
- First full emulator run after that guard aborted in **10m15s**: XML contains 7 entries/2 failures, comprising five completed passes, one interrupted existing AuthInteractionTest and a synthetic process-crash failure. Captured Logcat explicitly shows lowmemorykiller terminating the app (275712kB RSS; system minimum watermark breached) amid Play Store downloads and Google Services compilation. No profile test had run. Evidence is retained in app/build/verification/task09-aborted-instrumentation.xml and task09-lowmemory-evidence.txt. Final unfiltered retry after cold start/network isolation: **41 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESSFUL in **18m16s**. Includes all 32 existing tests plus 9 ProfileInteractionTest cases. Current XML and app/build/verification/task09-final-instrumentation.xml record the complete passing suite; this is not an aggregate of partial runs.
- IDE Layoutlib previews were actually rendered/inspected: Classic setup, Royal eight-avatar collection and Georgian Ocean setup. Preview-only Surface wrappers and locale=ka make them reflect the app's dark theme/localization. These renders are not emulator/device verification. The final rebuild includes these preview-only corrections.
- Emulator recovery: adb reboot left the AVD offline; CLI stop hit a 300s MCP timeout, then adb emu kill succeeded. Android CLI --cold restarted the same AVD successfully in 84s without data wipe. Original airplane_mode_on=0 and wifi_on=1 were recorded; airplane mode was enabled and Wi-Fi disabled temporarily for the fake-repository suite to prevent background downloads. Both settings were restored after automated and manual offline checks (airplane mode off, Wi-Fi on).
- Manual final-debug-APK checks while genuinely offline: installed successfully, opened directly to Classic Luxury local board without an account, rolled 4/4 opening tie and rerolled 4/6 for Black. Selected point 17, saw legal point 21/23 rings, tapped 21 and observed the checker arrive after animation. Point 17 became two checkers, point 21 one; die 4 consumed, die 6 remained.
- Opened Account welcome and Sign in; empty submission displayed valid-email validation without a server request. Local Play returned to the same mid-turn board without login/profile setup.
- Force-stop/relaunch restored that same Black turn/board/dice. The active_match.bin SHA-256 was identical before and after: 182ba4ef198b62d5cd0ec45a0114e38ffee3319cfcaebba7ca58157cf77534e0. The final launcher-style cold restart returned Status ok, TotalTime 2972 ms. Initial launch after APK installation took 33558 ms; these are emulator observations, not production startup benchmarks.
- All three skins, both languages/large text, rotation, pixel-level legal movement and saved-file reopening were verified by the complete instrumentation suite. Manual live-board inspection in this pass used Classic; it is not claimed that all three skins or a genuine server profile were manually tested.
- Profile setup/creation/edit/avatar/logout UI passed on the emulator with fake repositories. Authentication login/logout also passed fakes; no live Supabase registration/login/profile mutation was performed. Screenshots and IDE renders were visually inspected through tools but not exported as standalone PNG files. Instrumentation runner cleanup requires reinstalling the target APK; restart/checksum claims concern the controlled post-test match, not data persistence across uninstall.

Final manual process Logcat (PID 6431, warning/error query) contained x86/virtual GPU/EGL, codec/Binder and SLF4J-provider warnings, with no fatal exception or unexpected Auth/profile error in the inspected process. The earlier instrumentation low-memory kill remains documented above; this is not a claim that no process ever terminated. Auth logging is disabled and no live backend call was tested, so Logcat alone does not establish server health.

Final Git inventory: **36 files (9 modified, 27 added)**, listed below. No production credential, local.properties, generated BuildConfig, build report or screenshot artifact is tracked.

## Changed files

Modified existing files:

1. app/src/main/java/ge/gogichaishvili/backgammon/BackgammonApplication.kt
2. app/src/main/java/ge/gogichaishvili/backgammon/MainActivity.kt
3. app/src/main/java/ge/gogichaishvili/backgammon/data/auth/SupabaseClientProvider.kt
4. app/src/main/java/ge/gogichaishvili/backgammon/presentation/BackgammonApp.kt
5. docs/multiplayer/database-schema.md
6. docs/multiplayer/profiles-and-avatars.md
7. docs/multiplayer/protocol.md
8. ARCHITECTURE.md
9. .gitignore (local backend environment files/CLI state)

Added:

- app/src/main/java/ge/gogichaishvili/backgammon/domain/profile/ProfileRepository.kt
- app/src/main/java/ge/gogichaishvili/backgammon/data/profile/SupabaseProfileRepository.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/profile/ProfileViewModel.kt
- app/src/main/java/ge/gogichaishvili/backgammon/presentation/profile/ProfileScreen.kt
- app/src/main/java/ge/gogichaishvili/backgammon/designsystem/PlayerAvatar.kt
- app/src/main/res/values/profile.xml
- app/src/main/res/values-ka/profile.xml
- app/src/test/java/ge/gogichaishvili/backgammon/profile/ProfileValidationTest.kt
- app/src/test/java/ge/gogichaishvili/backgammon/profile/ProfileViewModelTest.kt
- app/src/test/java/ge/gogichaishvili/backgammon/profile/SupabaseProfileRepositoryTest.kt
- app/src/androidTest/java/ge/gogichaishvili/backgammon/ProfileInteractionTest.kt
- supabase/config.toml
- supabase/migrations/202611120001_profiles.sql
- supabase/functions/deno.json
- supabase/functions/deno.lock
- supabase/functions/player-profile/index.ts
- supabase/functions/player-profile/validation.ts
- supabase/functions/player-profile/validation.test.ts
- supabase/tests/local-bootstrap.sql
- supabase/tests/profiles-security.sql
- supabase/tests/remote-preflight.sql
- supabase/tests/local-race-setup.sql
- supabase/tests/local-race.pgbench
- supabase/tests/local-race-verify.sql
- docs/multiplayer/task09-profiles.md
- docs/multiplayer/task09-security-tests.md
- docs/multiplayer/task09-verification.md

No Gradle/dependency catalogue, engine, renderer, checker interaction, animation, feedback, settings storage or match persistence file is modified. Avatar artwork is original procedural Canvas code, not externally sourced image files.

## Deployment limitations and next step

Android fake-repository tests, mocked HTTP, local PostgreSQL and TypeScript validation/type checking are distinct from a deployed Edge/Auth/Supavisor integration. Remote schema inspection and two-account concurrency/revocation tests remain required. No claim of production profile persistence, Storage sanitation, physical-device verification or release readiness.

Custom uploads stay disabled until the documented private-bucket server sanitation/publish pipeline is implemented and tested. TASK10 rooms/RLS must not start from an unreviewed live schema; first inspect metadata, approve the profile migration/restricted-role provisioning/function deployment, then smoke-test the existing approved account. See task09-profiles.md and task09-security-tests.md for the concrete approval package and gates.

Nothing was committed or pushed.
