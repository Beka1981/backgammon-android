# TASK09 — profiles and original avatars

## Status and boundary

Android/domain/UI, an allowlisted Edge endpoint, and a versioned SQL migration are implemented locally. **Nothing is deployed or migrated remotely.** A remote Supabase management connector is not available in this session, so the existing remote schema/policies have not been inspected. The actual test account is untouched. Live profile persistence is an acceptance gate after inspection, review and explicit approval, not a result of fake-repository tests.

The existing module, constructor injection, Auth SDK versions and local game code remain intact. No game rules, board renderer, animations, local match storage, rooms, matchmaking, Realtime or Google Sign-In changes.

## Flow and Android architecture

- ProfileRepository is a pure-domain contract. PublicProfile contains a random public UUID, username, display name, stable built-in avatar ID, independent profile/stat versions, counters, nullable rating and creation time. No Auth UUID, email, metadata, token, session or Storage URL.
- SupabaseProfileRepository calls only /functions/v1/player-profile using the existing authenticated SDK session. It never supplies an owner ID or writes tables. Responses are bounded to 16 KiB and strictly decoded; invalid counters/private unexpected fields fail closed. Future unknown avatar IDs render the bundled default.
- AppContainer creates Auth and profile services once. No new Android dependency or toolchain upgrade. A process-lifetime Ktor OkHttp client uses timeouts and no HTTP logging.
- ProfileViewModel observes valid non-recovery Auth sessions, loads the server profile, requests onboarding if missing and restores returning players. Generation fencing, cancellation and immediate busy admission prevent late account-switch responses and duplicate saves. A private UI-only owner binding also prevents a stale profile appearing for even one composition when Auth updates before the profile observer; that binding is never a public profile field or network payload.
- A draft in SavedStateHandle restores the same owner's interrupted form/selected avatar. No authoritative profile or session is restored from that draft: the server is reloaded. Edits retain their original expected version, including across recreation; unknown-result retries retain a UUID command ID. Conflict reload discards stale edits explicitly.
- Local Play and logout remain available in setup, loading, error and profile screens. Only a server-accepted mutation changes the displayed committed profile. Sign-out/account changes clear profile state/drafts. Existing Auth recovery/password flows still take precedence over profile UI.
- Navigation retains the existing board/settings/account routes and reads latest observable profile state inside retained NavEntry content. Profile setup, My Profile, editing and avatar preview are feature-local states.

## Username and display-name contract

All layers trim **ASCII U+0020 only**, not arbitrary Unicode whitespace. Username normalization lowercases ASCII A–Z only, then validates [a-z0-9_]{3,20}. The database UNIQUE username_key is authoritative. Reserved catalogue: admin, administrator, support, system, moderator, backgammon, official, supabase, postgres, api, root, null, deleted.

Names are NFC, 1–40 Unicode code points, at most 160 UTF-8 bytes. Reject C0/C1 controls, lone UTF-16 surrogates, U+200B–200F, U+2028–202E, U+2060–206F and U+FEFF (hidden/bidirectional formatting). PostgreSQL rejects U+0000 at its JSON/text boundary; Edge and Android reject it explicitly. Georgian, Latin and valid supplementary characters are supported. Plain text only.

The SQL CHECKs independently enforce canonical stored values and the reserved catalogue. Username changes are limited to once/day, including the day after initial claim; writes to 10/minute and reads to 60/minute per live identity. No username-availability query leaks private identity. A 409 USERNAME_UNAVAILABLE is sufficient.

## Transaction and RLS design

Migration: supabase/migrations/202611120001_profiles.sql.

- Private app schema: profiles, player_stats, player_activity (IDLE-only foundation), profile_commands and bounded per-account profile_limits. The two justified support tables implement mutation retry receipts and database-atomic rate limiting.
- Separate profile_api schema, also **not exposed** through PostgREST. No anon/authenticated table or function access, no public view, no broad SELECT policy.
- FORCE RLS on every table. The non-login/non-BYPASSRLS function owner has narrowly granted table operations with explicit policies. It has no UPDATE permission on statistics/activity. The non-login backend role has only execute permission on the one validated entry point.
- A separately provisioned restricted LOGIN, not postgres/service_role, becomes a member of bg_profile_backend. Edge connects using transaction pooling/TLS and SET LOCAL ROLE. No database credential is put in Android, local.properties or Git.
- Edge validates the bearer token through the configured project's Auth user endpoint; verifies subject, issuer, audience, expiry, role and session claim. Only then are verified actor/session IDs passed as SQL parameters. SQL checks a current confirmed/nonanonymous/unbanned Auth user and live matching session again.
- GET returns an allowlisted projection or PROFILE_NOT_FOUND. POST locks the Auth subject, returns its existing profile on retry, or atomically inserts profile + zero stats + IDLE activity. Database uniqueness handles simultaneous claims; failed claims leave no partial stats/activity.
- PATCH takes expectedVersion + commandId, checks ownership from the verified actor mapping and performs a version increment atomically. Matching retry receipts return the current safe projection without replaying writes; altered payload/revision conflicts fail.
- Receipts retain bounded request fields (public profile choices, not secrets). Proposed seven-day receipt cleanup must be installed/reviewed with deployment; automatic client retries must not exceed 24h. Expected versions remain mandatory after receipt expiry. No cleanup scheduler is deployed here.
- Auth signup has no new trigger. ACTIVE profiles require a non-null Auth mapping; ON DELETE SET NULL therefore deliberately blocks out-of-band Auth deletion of an ACTIVE profile. The documented future deletion workflow must anonymize/deactivate first. Do not manually delete a profiled Auth user to work around this check.
- player_activity currently only permits IDLE. TASK10 must explicitly expand its targets/constraints and align profile/deletion operations with the documented activity/resource lock order; no partial room/game infrastructure is added now.

## Endpoint contract

GET /functions/v1/player-profile: own active profile.
POST: {username, displayName, avatarId}; idempotency is the unique verified Auth subject.
PATCH: same fields plus nonnegative safe-integer expectedVersion and UUID commandId.

No client owner/statistics/status fields. Request <=4096 bytes; SQL payload <=1024 bytes; unknown fields rejected. Error responses contain only an allowlisted code. No raw SQL exception, connection string, submitted body, email or token logging. No caching of personal responses.

Hosted Edge configuration reads the platform-injected SUPABASE_PUBLISHABLE_KEYS dictionary; SUPABASE_PUBLISHABLE_KEY is only a local CLI fallback. Do not attempt to set custom SUPABASE_* secrets: that prefix is reserved. Secret/service-role and legacy JWT-shaped API keys are rejected. PROFILE_DATABASE_URL is a separate server-only restricted connection secret and must never enter Android configuration or Git. Malformed configuration fails closed without logging a credential-bearing URL.

The implementation uses a single private SQL transaction for each operation. The Android deployment URL uses Supabase's function-name path to fit the existing project; this is the concrete TASK09 binding of the TASK07 conceptual /profiles/me routes, not a new public table API.

## Original avatar collection

PlayerAvatar.kt draws eight original procedural medallions: crown (default), knight, sun, moon, mountain, lotus, shield and star. All path geometry/artwork was authored for this project. No downloaded/copyrighted art, external image service or image asset licence dependency. Stable IDs, scalable Canvas geometry and Material theme tokens make the component reusable in future lobby rows and game seats.

Selection provides a large preview, accessible radio choices, Save/Cancel and 48dp+ controls. All visible strings are English/Georgian resources. Layouts scroll, honor IME/insets and adapt the grid to width; previews cover portrait, landscape, tablet, Classic/Royal/Ocean and Georgian.

## Custom uploads — disabled

No Photo Picker launcher, upload endpoint, bucket or Storage policy is enabled. There is no direct client upload shortcut. Built-in selection does not depend on Storage.

Future enablement must implement and test the existing profiles-and-avatars.md publish saga: Photo Picker JPEG/PNG sources <=2 MiB, streamed server limits, actual byte validation, bounded dimensions/pixels/CPU, full safe decode, metadata stripping, resize <=512px, WebP re-encode <=256 KiB, immutable server keys, private bucket, approved-object read/signing and owner-authorized replacement/removal. Need a tested sanitizer runtime/library, pending/publish fencing, default fallback, cleanup and hostile-image tests before exposing a UI upload action. Storage MIME/size settings alone are insufficient.

## Remote safety and approval package

1. Obtain explicit permission for read-only remote inspection and run/review supabase/tests/remote-preflight.sql. It queries schema/grants/policies metadata, not account emails/tokens. Verify Auth schema compatibility, schema exposure, existing buckets and role availability. Do not assume the synthetic local Auth tables match every managed version.
2. Reconcile any existing app/profile_api objects; this migration deliberately fails on an existing schema/role/table instead of overwriting it. Review a fresh project backup and staging run.
3. Review this migration and Edge code with exact grants, live-session checks and deployment credentials. Request **separate explicit approval before remote migration, restricted-login provisioning or Edge deployment**. Storage remains untouched.
4. After approval, apply the versioned migration, provision a least-privilege login/member, set PROFILE_DATABASE_URL and confirm the platform-injected SUPABASE_PUBLISHABLE_KEYS dictionary, and deploy player-profile using the reviewed supabase/config.toml. Its legacy gateway verify_jwt=false setting supports modern signing keys; this does not disable handler authentication. Every call still requires Auth /user verification, verified JWT claims and a live matching SQL session. Explicitly test key-only/expired/forged JWT rejection through the deployed gateway before enabling Android profiles.
5. Run real two-account HTTP/DB denial/concurrency tests and the approved existing-account onboarding/edit/logout/restart smoke test. Never create/delete Auth users automatically.
6. Verify log redaction and retention, connection limits/TLS certificate verification, monitoring and receipt cleanup. Proceed to TASK10 only after these live gates pass.

No DROP/TRUNCATE/DELETE of existing data, no Auth registration trigger and no Storage migration. Security-impacting operations are schema access revocations, private objects/roles/grants and Auth-table read grants to the isolated owner. Future out-of-band Auth deletion behavior changes once a profile exists. This must be approved with the migration, not discovered after deployment.

## Verification

See task09-verification.md for actual command results, file inventory and explicit gaps. Local SQL fixtures are not real Supabase users; fake Android repositories and mocked HTTP do not prove live service behavior.

Primary sources reviewed: [Supabase Edge/Postgres](https://supabase.com/docs/guides/functions/connect-to-postgres), [Auth getUser](https://supabase.com/docs/reference/javascript/auth-getuser), [transaction pooling](https://supabase.com/docs/guides/database/connecting-to-postgres), [Storage RLS](https://supabase.com/docs/guides/storage/security/access-control), [PostgreSQL normalization](https://www.postgresql.org/docs/current/functions-string.html), [pinned Postgres.js 4.1.1 release](https://github.com/porsager/postgres/releases/tag/v4.1.1), [Edge environment variables](https://supabase.com/docs/guides/functions/secrets), [current API-key/gateway migration guidance](https://supabase.com/docs/guides/getting-started/migrating-to-new-api-keys).
