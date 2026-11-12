# Task 08 — Final authentication verification

## Environment and install

- Restarted emulator: Pixel_10_Pro, serial emulator-5554, Android 17, x86_64 16 KB image. No physical-device testing.
- `adb devices -l`: online `device`, not offline/unauthorized.
- `adb -s emulator-5554 shell getprop sys.boot_completed`: 1.
- `./gradlew assembleDebug --max-workers=2 --console=plain`: BUILD SUCCESSFUL in 30s (38 tasks: 9 executed, 29 up-to-date).
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`: Success; app data retained.
- Explicit MainActivity launch: Status ok, COLD, TotalTime 7998 ms. This first debug launch is not a production startup benchmark.
- Visual startup inspection showed the existing saved local match, Classic Luxury skin, White to move, dice 6/5 with 5 already consumed. No login was required.

## Instrumentation

First full run: **32 tests, 29 passed, 3 failed, 0 skipped**, command failed in **16m27s**. The third failure was the unchanged GameActivityTest: ActivityScenario timed out in PRE_ON_CREATE before reaching a requested lifecycle state. The complete suite was rerun after the two auth test corrections. Initial XML evidence is retained in `app/build/verification/task08-initial-instrumentation.xml`.

The corrected-auth full retry completed **31 passed, 1 failed, 0 skipped** in **14m50s**. All 9 auth tests passed. The remaining ActivityScenario case also failed in isolation (1 test, 1 failed; command 2m35s), establishing a reproducible launch-identity issue rather than an ADB-only failure. Evidence is retained in `app/build/verification/task08-retry-instrumentation.xml` and `task08-isolated-activity.xml`.

The unchanged recreation/landscape test passed after the production fix (1 test, 0 failures; command **2m08s**). The final unfiltered `./gradlew connectedDebugAndroidTest --max-workers=2 --console=plain` then passed: **32 tests, 0 failures, 0 errors, 0 skipped**, **BUILD SUCCESSFUL in 11m18s**. This is a complete passing run, not an aggregate of earlier partial runs. Final XML is retained in `app/build/verification/task08-final-instrumentation.xml`.

Final class totals: AuthInteractionTest 9, CheckerDrawingTest 2, CheckerMovementRegressionTest 3, ExampleInstrumentedTest 1, GameActivityTest 1, GameInteractionTest 7, GameplayAccessibilityTest 4, GameplayPolishTest 3, NavigationTest 2. All passed.

The first full run discovered two defects in the new AuthInteractionTest, not application changes:
1. Unconfigured AuthScreen was mounted without LocalizedContent; the isolated test failed with Missing app resources.
2. The visibility test incorrectly expected Compose to remove Password semantics when showing text. KeyboardType.Password retains that privacy marker. The corrected test checks the actual text-layout output while hidden, visible and hidden again, plus button labels and 48dp height.

A narrow production correction was also required in MainActivity.handleAuthIntent: cleanup had unconditionally replaced ordinary launcher Intents, removing ACTION_MAIN and CATEGORY_LAUNCHER. Logcat explicitly showed ActivityScenario rejecting lifecycle events because the start Intent and retained Intent no longer matched. AndroidX [ActivityScenario source](https://github.com/android/android-test/blob/main/core/java/androidx/test/core/app/ActivityScenario.java) confirms that action/data/categories are compared. Cleanup now returns immediately for non-ACTION_VIEW launches; one-time callback data is still cleared for VIEW callbacks, with the same strict PKCE validation. The original recreation/landscape test is retained unchanged as the regression guard.

No new features, rules-engine, geometry, renderer, checker interaction, animations or local match-persistence changes were introduced. No test was removed, disabled or weakened.

## Manual emulator checks

Verified on the installed debug app without signing in:

- English Account welcome, Sign in and Create account navigation; empty login/registration rejected with the valid-email message.
- Registration using reserved `validation@example.test` and `short` rejected with the 12–128 character policy message. A sufficiently long password with empty confirmation was rejected with the mismatch message. No valid registration was submitted. Password masking, keyboard entry and dismissal worked.
- Local Play returned to the real board without authentication. The first opening roll was 4/4 and correctly required a reroll; 2/1 then selected White.
- Tapped the point 8 checker: selection and legal destination rings appeared. Tapped point 7: after the animation, point 8 contained two White checkers and point 7 one; die 1 was consumed and die 2 remained available.
- Visually inspected that same live mid-turn position in Classic Luxury, Royal Black & Gold and Modern Ocean. These were actual game-board pixels, not just settings thumbnail labels.
- Switched to Georgian: settings, game controls/current-player labels and the registration fields, password hint, validation error and Local Play were localized. English was inspected earlier. Automated tests additionally cover localized UI at enlarged font scale.
- Force-stopped and cold-launched with ACTION_MAIN/CATEGORY_LAUNCHER: Status ok, TotalTime 2373 ms, WaitTime 2375 ms. Ocean, Georgian, the moved checker, White turn and consumed/remaining dice were restored.
- The SHA-256 of `files/datastore/active_match.bin` before skin/language changes and after process restart was identical: `707600511e81407d869b171fa102b0828af914fad18b371f6a734d3568a1f9d7`. Only the checksum was read, not session tokens.
- Emulator is left in the restored Ocean/Georgian local match. Instrumentation installation cleanup removed the earlier app installation; restoration claims concern the subsequent controlled manual match, not preservation across test-runner uninstall.

Authenticated account/logout UI and state transitions passed fake-repository instrumentation; no genuine authenticated Supabase session was available for manual logout verification. Screenshots were inspected through the IDE screenshot tool; standalone screenshot files were not exported.

No live Supabase account is created by these checks. Login/logout account-state testing uses a fake repository in instrumentation. A production session, email delivery, confirmation links and password-reset delivery require the separately approved dedicated-account test.

## Review of all 31 lint warnings

| Group | Count | Assessment |
|---|---:|---|
| Gradle/declared dependency version notices | 19 | Maintenance notices, not CVE findings. Includes Auth, Ktor, Serialization and toolchain updates. Some catalogue entries may resolve to newer transitive versions. Compatibility pinning is documented; this is not a complete dependency vulnerability audit. |
| Unused resources | 8 | Seven legacy colors and auth_continue. Size/cleanup only; no security finding. |
| UseKtx | 2 | EncryptedAuthStorage checked synchronous commits. Style suggestions; keeping commit's boolean result detects storage failure. Work runs on Dispatchers.IO. No plaintext storage or main-thread commit was identified here. |
| IntentFilterUniqueDataAttributes | 2 | Security-relevant configuration clarity: both data declarations use the same app scheme/host and only /confirm or /recovery. No unintended scheme/host combinations were identified. Runtime callback parsing additionally validates the exact URI and requires a pending matching flow plus PKCE verifier. |

Lint reports zero errors and no Security-category warning. That does not establish that the app is vulnerability-free. A limited upstream advisory check found [Ktor GHSA-xrr9-rh8p-433v](https://github.com/ktorio/ktor/security/advisories/GHSA-xrr9-rh8p-433v), which affects CIO client/server versions below 1.3.0. Inference from the documented affected range and this project’s dependencies: it does not match the app’s OkHttp 3.3.1 client. A complete transitive dependency/SBOM vulnerability scan was not performed.

The material release limitation is **custom-scheme ownership**: another installed app can register the same scheme and interfere with callback delivery. PKCE requires the originating installation's verifier to redeem the code, but it does not guarantee routing. Verified HTTPS App Links using an actually owned domain remain a production release gate.

Source review also confirmed client-public key validation, SDK Auth logging disabled, Android Keystore AES-GCM session storage, and auth backup exclusions from the implementation report. No secret/service_role key, permissive RLS, or production database change was introduced.

Primary references: [Android intent matching](https://developer.android.com/guide/components/intents-filters), [verified App Links](https://developer.android.com/training/app-links/verify-applinks), [Supabase PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow).

## Logs, limitations and live-test readiness

Final `./gradlew build test lint --max-workers=2 --console=plain`: **BUILD SUCCESSFUL in 4m06s**, exit 0; 145 tasks, 24 executed, 121 up-to-date. **475 JVM tests passed, 0 failed, 0 skipped**; debug/profile/release builds and R8 succeeded. Lint: **0 errors, 31 warnings**.

Reports: `app/build/reports/tests/testDebugUnitTest/index.html`, `app/build/reports/androidTests/connected/debug/index.html`, `app/build/reports/lint-results-debug.xml`. The final instrumentation XML also remains in `app/build/verification/task08-final-instrumentation.xml`.

Bounded Logcat review found no AndroidRuntime fatal exception or app crash in the inspected launches and no unexpected authentication error. The final restored process (PID 15925) warning/error query contained x86 CPU-variant, virtual GPU/EGL, codec/resource and platform Binder callback warnings, plus the SDK's SLF4J no-provider notice. Auth logging is disabled, so absence of logged backend errors does not prove server health. The Binder warnings are a platform/media forward-compatibility observation, not an observed abort.

Tooling was not completely reliable: one intermediate component-only launch reported Status timeout/WaitTime 78303 ms and a system launch wake-lock timeout, although the UI subsequently responded. Some IDE hierarchy dumps failed to create their temporary XML; screenshots and direct taps were used instead. The final launcher-style cold launch succeeded in 2373 ms. These are functional checks, not startup/performance benchmarks.

Android-side verification is ready for a **controlled, separately approved live registration test**. The local build reports SUPABASE_CONFIGURED=true without exposing its values. Before that test, verify dashboard email confirmation remains enabled, password policy matches the documented floor, redirect allowlist and SMTP are configured, and use an approved dedicated account on the originating installation. No live account, email, password-reset request, backend login/logout, confirmation delivery or real-session refresh was performed. No physical device or production-release verification is claimed.

Verification-specific changes: `MainActivity.kt` (ordinary-launch identity guard), `AuthInteractionTest.kt` (two test corrections), this report, `task08-report.md`, `security-alignment.md` and `security-integration.diff`. No dependencies, game rules or renderer changes were made in this verification pass.

The complete dashboard/redirect/SMTP checklist remains in [configuration.md](configuration.md). No commit or push is performed.
