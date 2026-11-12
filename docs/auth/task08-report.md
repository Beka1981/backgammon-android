# Task 08 — Supabase email/password authentication

## Result

Email/password registration, login, logout, confirmation handling, reset request/password update, session restoration and optional account navigation are implemented. Existing local gameplay remains the launch destination and requires no account.

No live test account was created: the developer explicitly declined account creation. Automated authentication uses fakes and MockEngine. Real email delivery, confirmation and password-reset links remain an end-to-end acceptance gate.

The restarted-emulator verification is complete: **32 instrumentation tests and 475 JVM tests passed; build and lint passed**. See [final-verification.md](final-verification.md) for exact commands, discovered launch-identity fix, manual evidence, all 31 lint warnings and remaining live-service gates.

## Architecture

The existing single app module and constructor-injected AppContainer are retained.

- Domain: AuthRepository, immutable authentication/result/error models, validation and strict callback parsing; no Supabase/Compose dependency.
- Data: SupabaseClientProvider installs only Auth with PKCE and OkHttp. SupabaseAuthRepository serializes operations, maps safe errors and observes SDK sessions.
- Storage: SDK-supported SessionManager/CodeVerifierCache adapters use AES-GCM ciphertext in an isolated SharedPreferences file and Android Keystore. Sessions are refreshed at startup; failed refresh is explicit rather than presenting cached identity as freshly authenticated. Corruption fails closed. Project changes clear old tokens before any refresh.
- Presentation: AuthViewModel exposes immutable StateFlow UI state and rejects duplicate submissions immediately. Passwords/tokens are absent from SavedStateHandle. Password fields clear on backgrounding and are not saveable.
- Navigation: an Account destination is added alongside the original board/settings routes. Signed-out/recovery states cannot retain an authenticated account page. Recovery sessions can only update the password or sign out. Local Play remains available even during loading, configuration failure or network failure.
- Logout destroys local session/verifier/recovery data independently of remote revocation and never clears local settings/matches.
- Intent callbacks share cold/warm validation, require a local verifier/matching flow, accept only PKCE codes and clear one-time Activity intent data.

No game engine, board renderer, geometry, checker interactions, animation implementation or local match persistence files were changed.

## Dependencies and build configuration

Added supabase-kt Auth 3.2.6, Ktor OkHttp 3.3.1, explicit Serialization JSON 1.9.0 and test-only Ktor MockEngine 3.3.1. The existing Serialization version and all Kotlin/AGP/Gradle/Compose/SDK versions are preserved.

Upstream 3.9.0 uses a newer compiler/dependency line. The compatible stable Kotlin 2.2 line is documented with upstream catalogs in [configuration.md](configuration.md).

Only SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY are read from root local.properties or same-named CI environment variables. Missing/malformed/privileged configuration produces a safe offline-capable build. local.properties and generated BuildConfig are verified Git-ignored. The developer corrected an initially path-containing URL; the generated debug flag is now SUPABASE_CONFIGURED=true. Actual values were not logged or copied into documentation.

Dashboard setup, exact redirect allowlist, SMTP/policy considerations, CI instructions and limitations of custom-scheme PKCE are in [configuration.md](configuration.md). No dashboard, database, RLS or infrastructure changes were made.

## Changed file inventory

Paths are relative to the repository root.

Modified:

- `.gitignore` — ignore generated Kotlin compiler cache.
- `gradle/libs.versions.toml` — compatible Auth/HTTP/JSON dependencies.
- `app/build.gradle.kts` — validated local/CI configuration, BuildConfig and dependencies.
- `app/src/main/AndroidManifest.xml` — INTERNET and bounded callback routes, singleTop.
- `app/src/main/java/ge/gogichaishvili/backgammon/BackgammonApplication.kt` — application auth provider/scope.
- `app/src/main/java/ge/gogichaishvili/backgammon/MainActivity.kt` — lifecycle collection and cold/warm callback delivery.
- `app/src/main/java/ge/gogichaishvili/backgammon/presentation/BackgammonApp.kt` — optional Account navigation.
- `app/src/main/res/xml/backup_rules.xml` — exclude encrypted auth storage.
- `app/src/main/res/xml/data_extraction_rules.xml` — exclude auth cloud/device-transfer data.

Added:

- `app/src/main/java/ge/gogichaishvili/backgammon/domain/auth/AuthRepository.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/domain/auth/AuthCallback.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/data/auth/AuthStorage.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/data/auth/EncryptedAuthStorage.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/data/auth/SupabaseClientProvider.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/data/auth/SupabaseAuthRepository.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/presentation/auth/AuthViewModel.kt`
- `app/src/main/java/ge/gogichaishvili/backgammon/presentation/auth/AuthScreen.kt`
- `app/src/main/res/values/auth.xml`
- `app/src/main/res/values-ka/auth.xml`
- `app/src/test/java/ge/gogichaishvili/backgammon/auth/AuthValidationTest.kt`
- `app/src/test/java/ge/gogichaishvili/backgammon/auth/AuthViewModelTest.kt`
- `app/src/test/java/ge/gogichaishvili/backgammon/auth/SupabaseAuthRepositoryTest.kt`
- `app/src/androidTest/java/ge/gogichaishvili/backgammon/AuthInteractionTest.kt`
- `docs/auth/configuration.md`
- `docs/auth/security-alignment.md`
- `docs/auth/security-integration.diff`
- `docs/auth/task08-report.md`
- `docs/auth/final-verification.md`

local.properties was corrected by the developer and remains ignored; no credentials are part of this change.

## Automated verification

- Final `./gradlew build test lint --max-workers=2 --console=plain`: **BUILD SUCCESSFUL**, **4m06s**, exit 0 (145 tasks: 24 executed, 121 up-to-date). This includes the final background-storage fix. Debug, profile and release variants build; release/profile R8 processing succeeds. Earlier complete runs also passed.
- JVM suite: **475 passed, 0 failed, 0 skipped** (433 existing plus 42 new: 5 validation, 23 ViewModel, 14 actual-SDK mocked-HTTP tests). The final added case verifies that background secure-storage failure blocks authentication instead of killing the session observer.
- Earlier emulator verification was blocked by ADB timeouts. After restart, completed runs exposed two auth-test fixture/assertion issues and a real MainActivity launcher-intent identity regression. These were corrected without changing gameplay or weakening tests; details and intermediate failing results are retained in final-verification.md.
- Final unfiltered `./gradlew connectedDebugAndroidTest --max-workers=2 --console=plain`: **32 passed, 0 failed, 0 skipped**, BUILD SUCCESSFUL in **11m18s**. Includes all 9 auth tests and the unchanged Activity recreation/landscape regression.
- Lint: **0 errors, 31 warnings** in the generated debug XML. Warnings include dependency-update notices, legacy unused colors, deep-link data formatting, one unused auth string and SharedPreferences style suggestions. Checked synchronous commits run on Dispatchers.IO for durable session storage. No lint baseline or suppression was added.
- `git diff --check`: passed. Git confirms local.properties and generated BuildConfig are ignored and untracked.

New tests cover form validation, success/failure, duplicates, confirmation, reset, expiration, restoration, offline logout, project-token isolation, corruption, PKCE recovery gating/idempotence, password update, local-play access, localization parity and Android Keystore tampering. Existing tests were not removed or weakened.

## Emulator verification

Pixel_10_Pro / emulator-5554 is connected and responsive, Android 17/API 37, x86_64 16 KB image. The latest debug APK was built, installed and launched.

Manual verification covered English sign-in/registration navigation, email/password/confirmation validation, keyboard entry, offline access, actual checker selection/legal highlights and a completed point 8 → 7 move. The live mid-turn board was visually inspected in all three skins. Georgian settings, gameplay and registration UI were inspected. A force-stop/restart restored the identical match checksum, player, remaining dice, board, Ocean skin and Georgian language; the final cold launch returned Status ok in 2373 ms.

All 32 current emulator tests passed, including pixel-based movement across skins, saved-game reopening, activity recreation/landscape, auth navigation, visibility/accessibility and fake-repository login/logout. Screenshots were inspected in the IDE but not exported as standalone files. No live account was created. Bounded app-process Logcat checks found no crash or unexpected auth error; emulator graphics/media warnings and intermittent launch/hierarchy-tool timeouts are documented in [final-verification.md](final-verification.md).

Remaining acceptance: with separate approval for a dedicated account, perform the actual Supabase registration/email confirmation/login/logout/reset checklist in configuration.md.

## Limitations and next milestone

- No live account, email delivery or production session lifecycle was tested. Confirm the documented dashboard policies/redirects/SMTP configuration with an approved dedicated test account.
- Custom schemes lack verified-domain ownership; migrate to verified HTTPS App Links after obtaining a real owned domain before production distribution.
- Client new-password validation applies the documented 12-character floor; additional dashboard requirements are enforced by Supabase. No admin policy/secret API is exposed.
- PKCE links require the originating installation and latest verifier. Other-device email confirmation can be followed by manual sign-in; reset must be requested on the installation that opens the link.
- Offline logout cannot guarantee immediate remote token revocation; issued JWTs can remain valid until expiry.
- Account form values deliberately do not survive process death; encrypted sessions and reset-required flags do. Session corruption requires local logout/re-login.
- Physical-device verification was not performed. No production signing/release claim is made.
- Google Sign-In, profiles/avatars, rooms, matchmaking and online game data remain unimplemented.

Next: obtain separate approval for a dedicated-account live Auth acceptance check, verify dashboard redirect/email/SMTP policies, then proceed to Task 09 profiles and avatars under the existing multiplayer specification. Backend data authorization/RLS is still required; a public client key never grants trusted gameplay authority.

Nothing was committed or pushed.
