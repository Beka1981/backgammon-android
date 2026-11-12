# Task 08: email/password authentication

## Local and CI configuration

Add these two unquoted entries to the root `local.properties`, alongside the existing SDK entry:

```properties
SUPABASE_URL=https://<project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<your sb_publishable_... key>
```

Use the project's HTTPS base URL without a path, query, fragment, username or password. Use the Publishable Key, never a secret/service_role key, database password or legacy JWT key. Invalid or missing configuration produces the **Online account unavailable** screen; Local Play remains available. BuildConfig generation is enabled. Both values are embedded only when both pass validation. Rebuild after changing them.

CI supplies the same names as environment variables; environment values take precedence over local.properties. Keep CI logs free of configuration dumps, HTTP bodies and tokens. Publishable keys are public client configuration, not authorization. Generated BuildConfig/APKs contain them intentionally; RLS and validated server-side identity remain mandatory for future data access.

local.properties, app/build and the Kotlin compiler cache are Git-ignored. No credential example contains a real project reference or key.

## Supabase dashboard checklist

1. Enable the Email provider and email/password registrations.
2. Keep **Confirm email** enabled for production.
3. Set the minimum password length to **12**. The client requires 12–128 characters for new passwords; existing-account login does not impose the new-password floor. Supabase enforces any additional dashboard policies and rejected passwords receive a safe policy message. Policy introspection/admin APIs are not exposed to Android.
4. Under Authentication → URL Configuration → Redirect URLs, allow exactly:
   - `ge.gogichaishvili.backgammon://auth/confirm`
   - `ge.gogichaishvili.backgammon://auth/recovery`
5. If deliberately testing the separate profile build, also allow its two exact equivalents:
   - `ge.gogichaishvili.backgammon.profile://auth/confirm`
   - `ge.gogichaishvili.backgammon.profile://auth/recovery`
6. Keep the existing legitimate Site URL. No production domain is invented by this implementation. Explicit redirect URLs above are sent on registration/reset.
7. Confirmation and reset email templates should retain the Supabase `{{ .ConfirmationURL }}` link so Supabase verifies the email before redirecting with the PKCE code. Do not substitute implicit access-token fragment links or an arbitrary TokenHash handler.
8. Configure a production SMTP provider and appropriate Auth rate limits before release. Supabase's default email service has delivery restrictions; dashboard settings were not changed by this task.

References: [mobile deep links](https://supabase.com/docs/guides/auth/native-mobile-deep-linking), [password authentication](https://supabase.com/docs/guides/auth/passwords), [email templates](https://supabase.com/docs/guides/auth/auth-email-templates), [PKCE](https://supabase.com/docs/guides/auth/sessions/pkce-flow).

## Session and redirect behavior

Registration may return a session or require confirmation; the app handles both. Existing accounts receive generic confirmation messaging. Password reset always acknowledges a successfully submitted request without disclosing account existence.

Email flows use PKCE. Open the latest link on the installation that requested it: its encrypted verifier is required. Starting another email flow replaces the verifier. If confirmation happens on another device, confirm the email and sign in manually on Android. A reset requested on another installation must be requested again on this installation.

Only the exact app scheme, host `auth`, paths `/confirm` and `/recovery`, and one bounded `code` query parameter are accepted. Token fragments, extra parameters, wrong hosts, duplicate codes and unsolicited flows are rejected. Both cold-start and warm-start Activity intents use the same parser. Retained Activity intents are cleared of the one-time code.

A custom scheme is suitable for the current app configuration but another installed app could claim it and prevent delivery. PKCE prevents that app redeeming the code without the verifier. Before public production release, configure verified HTTPS App Links using an actually owned domain and matching dashboard redirects.

SDK session and PKCE persistence use a custom Android Keystore AES-GCM adapter with per-entry IVs and authenticated key names. SharedPreferences contain ciphertext only. Auth storage is excluded from cloud and device-transfer backup. Changing Supabase projects clears saved credentials before refresh. Corruption fails closed. Recovery sessions persist a reset-required flag and cannot enter the account screen until the password changes or the user logs out.

Startup refreshes saved sessions before presenting SignedIn. Offline refresh failure offers retry/logout while preserving Local Play. SDK automatic refresh handles ongoing expiration. Logout removes local identity even if remote revocation fails; a previously issued access token can remain valid until expiry. Local matches/settings use separate storage and are never erased by logout.

## Dependencies

Pinned compatible line: supabase-kt Auth **3.2.6**, Ktor OkHttp **3.3.1**, existing Kotlin Serialization **1.9.0**. Tests use Ktor MockEngine **3.3.1**. Existing Kotlin/Compose compiler 2.2.10, AGP, Gradle, Compose BOM and SDK versions are preserved.

Verified newer upstream release 3.9.0 uses Kotlin 2.4.21, Ktor 3.6.0 and Serialization 1.11.0 in its [dependency catalog](https://github.com/supabase/supabase-kt/blob/3.9.0/gradle/libs.versions.toml). Those versions exceed the existing compiler line; upgrading the toolchain is a separate compatibility decision. This integration uses the stable line matching Kotlin 2.2 metadata and was compiled/tested without metadata suppression. Sources: [3.2.6 dependency catalog](https://github.com/supabase/supabase-kt/blob/3.2.6/gradle/libs.versions.toml), [upstream releases](https://github.com/supabase/supabase-kt/releases).

## Controlled live smoke test

No real account should be created automatically. With explicit developer approval, use a dedicated non-production test account, enter its password directly in the emulator, then verify registration, confirmation link, login, process restart/session refresh, logout, password reset link and new-password login. Check delivery/redirect configuration first. Never put passwords or tokens into chat, logs, screenshots, source files or test fixtures.

Automated tests use fake repositories and mocked HTTP responses with clearly artificial tokens. They require no Supabase credentials. Google Sign-In, profiles, avatars, online gameplay and database/RLS changes remain out of scope.
