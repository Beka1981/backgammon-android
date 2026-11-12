# Accounts, profiles and avatars

Design only. No Auth project/provider, Android SDK or Storage bucket is installed here.
See [security](security.md), [schema](database-schema.md) and [protocol](protocol.md).

## Account and session flows

Local Play is always available without login, network or anonymous Auth registration.
Online requires a Supabase Auth account, verified identity and completed profile onboarding.

Email:
1. Register with email/password through Supabase Auth; configure minimum password length12,
   password checks, email confirmation, safe reset and rate/CAPTCHA controls in TASK08.
2. Show a generic confirmation response; do not expose whether another player owns an email.
3. Confirm email through an allowlisted verified HTTPS Android App Link (PKCE where applicable).
   No arbitrary redirect URL, embedded WebView credential handling or tokens in logs.
4. Email/password login establishes the Supabase session. Recovery/email change use Auth's
   supported flows and reauthentication, not custom account-password tables.

Production email needs configured custom SMTP; Supabase's default sender is restricted and
intended for non-production use. [Official SMTP guidance](https://supabase.com/docs/guides/auth/auth-smtp).

Google:
- Native Credential Manager Google ID-token flow, with correct Android signing/package and
  web OAuth client IDs, Supabase provider/audience configuration and least scopes.
- Exchange the provider ID token through Supabase Auth; the verified Supabase session is the
  app identity, not an email string or a client-decoded provider token.
- Generate a cryptographic per-attempt nonce, bind it to the token exchange and verify the
  selected native SDK's exact nonce encoding contract with integration tests. Do not copy
  web-only hashed-nonce examples into Android without checking provider behavior.
  Never enable skip_nonce_check to work around mismatches.
- Provider cancellation is normal; expired/wrong-audience/replayed credentials fail closed.
  Never blindly merge accounts by a submitted email. Use Supabase's verified identity-linking
  flow with reauthentication when necessary, and ensure linked login resolves one Auth UID.
[Native provider guidance](https://supabase.com/docs/guides/auth/social-sign-in/auth-google?platform=android)
and [Kotlin ID-token reference](https://supabase.com/docs/reference/kotlin/v2/auth-signinwithidtoken).

AuthRepository exposes immutable Initializing/SignedOut/SignedIn/RefreshFailed session state.
Serialize refresh; retry one request after refresh, not an infinite login loop. Store refresh
credentials using reviewed SDK storage integrated with Android Keystore-backed encryption;
exclude session files from Android backup and logs. Keystore invalidation signs out safely
without deleting the offline match. Access tokens remain in memory where practical.
No account passwords or Google tokens in DataStore game/settings files or SavedStateHandle.

Logout (including before profile onboarding) first attempts authoritative queue/room cleanup
bound to its stored owner session and session revocation, then clears
client credentials/channels/caches even if network fails. Do not declare an active match
resigned; it remains recoverable by later login. Waiting/search leases expire if cleanup
cannot reach the server. Present that distinction. Offer current-device logout initially;
a later “all devices” operation must use Auth's supported revocation scopes.

Revoked JWTs may remain cryptographically valid until expiry. Every HTTPS online operation
therefore checks current profile status and Auth session ID, not only JWT signature.
Reconnect updates Realtime's JWT; its cached authorization limitations remain documented.
[Sessions](https://supabase.com/docs/guides/auth/sessions),
[sign-out behavior](https://supabase.com/docs/guides/auth/signout).

## Profile identity and onboarding

Supabase Auth owns auth.users.id. An internal unique auth_user_id mapping connects it to
a random public profile ID; only safe profile IDs are returned in lobby/game DTOs.
No public email field or Auth-user object serialization, including errors and Realtime.

After Auth success, choose a unique username and display name before enabling online actions.
Create profile/stats/IDLE activity atomically. Auth signup itself need not fail because a
username is unavailable: Auth identity and username claim are separate steps with recovery.

Username key: lowercase ASCII letters/digits/underscore, 3..20 chars, unique database key.
Trim outer whitespace; reject non-ASCII after validation (avoid silent confusable folding).
Reserve administrator/support/system names and enforce the same server rule on edits.
Display name: NFC, 1..40 code points, <=160 UTF-8 bytes, allow Georgian/English, reject controls.
Render both as plain text. Username conflicts return409 without disclosing private identity.
Display names need not be unique. Edits require expected profileVersion and server ownership;
throttle username changes (initially1/day) and other edits (10/minute).

Safe PublicProfile DTO:
id, profileVersion, statsVersion, username, displayName, avatar descriptor, gamesPlayed/won/lost, derived winPercentage,
rating nullable, optional coarse lastActiveDay. Never email, auth_user_id, session info,
last exact connection time or user_metadata. Self settings may display the current user's
email through AuthRepository; it is not copied into lobby/profile caches.
For a deleted/deleting participant in retained game recovery, return a neutral tombstone:
status=DELETED, username/displayName=null, default avatar, no displayed statistics/rating.
The Android client renders its localized Deleted player label; never retain an old display
name/photo/email in a denormalized game snapshot. Active profiles return normal fields.
Profile and statistics versions are separate from game version. Refresh seat/profile metadata
by profileVersion/statsVersion without interpreting it as a new board or replaying effects.

Last active updates are server-throttled to once/minute from authenticated requests or
POST me/active, not a continuous publicly trusted Presence feed. Exact time stays private;
day-level public display is an initial privacy default. It does not determine game turn,
membership or room availability. Stats are read-only; normal rules completion updates both
players exactly once transactionally. Aborted/unplayed games do not affect counters.

## Avatar UX and data

Use Android Photo Picker for optional user-selected images without broad media/storage
permissions. Send bounded raw image bytes; command ID/profile version use the protocol headers.
Support stable built-in catalogue IDs and a bundled default, preview before applying,
cancel preview, upload, replace and remove-to-default. Built-in assets must be original or
licensed with provenance. Do not adopt a Google/external photo URL automatically or let
clients submit an arbitrary remote URL (SSRF/tracking risk).

Avatar selection is independent from board skins. A safe descriptor is DEFAULT,
BUILTIN {catalogueVersion,id}, or CUSTOM {avatarId,version,approvedObjectKey,url?,expiresAt?}.
An immutable server-generated custom path is associated with its owner, not a client filename.
UI caches by avatarId/version and falls back to default when URL expires/download fails.
Room list, waiting room, online seats and profile all use the same PublicProfile projection.
A profile refresh invalidates old avatar references; do not cache private account data with it.
Show the current committed avatar while a new upload is only being previewed/processed.

## Server-enforced upload pipeline

Initial limits are product policy, stricter than Storage's platform limits:
source <=2 MiB, JPEG or PNG only; dimensions <=2048 on each side and <=4 megapixels.
Reject SVG/HTML, GIF/animation, PDFs, unknown/polyglot formats, malformed/truncated files,
decompression bombs, excessive metadata and remote fetch URLs.
The server validates actual bytes and fully decodes with a pinned audited resource-limited
decoder; Content-Type, filename and magic bytes alone are insufficient. Select an audited
single-threaded Edge-compatible codec (e.g. reviewed WASM); do not assume sharp/libvips or
worker-thread support, which the hosted runtime restricts.
Apply orientation, crop/resize to at most512x512, strip EXIF/GPS/other metadata, re-encode
as a single-frame WebP <=256 KiB. Never publish raw input.

Prefer authenticated upload-through-Edge, streaming hard byte cap before buffering,
with per-user/IP/global rate reservations before expensive work. Do not expose client
Storage INSERT/UPDATE permissions or a broad signed upload capability. Selected decoder,
Argon runtime and concurrency need profiling against Edge CPU/memory limits; if safe
sanitation cannot fit, block custom uploads until a reviewed worker is authorized.
Built-ins must still work; no client-only validation fallback.

Storage object writes and PostgreSQL profile writes are a saga:
1. Reserve owner+commandId PENDING avatar operation with body fingerprint, immutable random
   storage key and expected profileVersion under short ordered locks. Duplicate pending
   request returns202/operationId; same-body retry may resume, different body409.
2. Validate/sanitize outside DB locks. Upload only sanitized bytes to the private avatars
   bucket with overwrite/upsert disabled. Record final digest/size/dimensions.
3. Publish in a transaction: lock own activity/profile, recheck ACTIVE status and expected
   version, set avatar PUBLISHED, swap profile reference/version, retire old object, receipt.
   Never point a profile at a not-yet-created object. No game-state changes.
4. Return the new committed profile. On stale version/deletion, retire/delete new object;
   keep the old committed reference. Crash-after-upload can be finalized using the reserved
   operation and stored digest or removed by an orphan sweep. No success before publish.
5. Retire/delete old objects asynchronously with bounded retries; cleanup checks current
   references again to avoid deleting an in-use avatar. PENDING/orphan >24h is cleaned.
   A lost original payload before sanitation may require re-upload, not unsafe publication.

GET /profiles/me/avatar-uploads/{operationId} is owner-only status recovery for a202 response.
A pending operation has a bounded processing lease; duplicate workers cannot both publish.
Reserve and finalize are separate commits; PENDING is not a successful profile mutation.

## Storage policies and delivery

Private avatars bucket, stored MIME image/webp only, bucket limit256 KiB (global >=that).
Client preprocessing improves UX only. Bucket MIME/size settings are additional controls,
not a byte-level sanitizer. [Bucket restrictions](https://supabase.com/docs/guides/storage/uploads/file-limits).

- anon: no object read/write.
- authenticated: no INSERT/UPDATE/DELETE/list; SELECT/download only an object whose exact
  key matches an ACTIVE profile's current PUBLISHED approved avatar, via narrow helper
  checking BOTH the live caller/session and the target profile, not just the target object.
- backend sanitizer: upload server-selected immutable key only; no user-provided bucket/path.
- URL signer: authenticate caller, resolve approved active avatar IDs with a <=50 batch,
  issue5-minute download URLs; no arbitrary key signing. Public bucket bypass is not used.
- deletion worker: server-only removal through Storage API; never delete storage.objects
  metadata directly or assume SQL deletion removes actual bytes.

Signed download URLs are bearer capabilities until TTL expiry; do not log them.
Deletion/removal cannot retroactively erase downloaded/cached images. Immutable path versions
prevent cache overwrites; small approved avatar cache may use bounded TTL and image sizes.
No free Storage image-transformation service is assumed.
[Private/public behavior](https://supabase.com/docs/guides/storage/buckets/fundamentals),
[Storage RLS](https://supabase.com/docs/guides/storage/security/access-control).

## Account deletion

Require explicit confirmation and recent reauthentication (server-verified challenge <=5min;
JWT iat from refresh alone is not proof of recent user authentication). Deletion is an
idempotent, durable job across database, Storage and Auth. It is keyed to verified Auth
identity even if no profile was ever created; owner-only deletion/status routes allow that
case and DELETING profiles. If there is no profile, skip occupancy/avatar/profile steps.
Onboarding and deletion start use the same Auth-subject advisory guard; onboarding fails
while a deletion job exists. This prevents an Auth account being deleted just as a profile
is created. The queue gate, when needed, precedes that guard and all activity/resource locks.
1. Under queue gate + sorted activity/resource locks mark DELETING, block all online calls,
   cancel a queued/waiting resource or ABORT an active game without scored result; release
   both players where needed. Remove public username/display/avatar and erase own stats.
2. Cancel/fence pending avatar publication, stop new uploads, and drain processing leases.
   Remove all owned avatar objects via Storage API, including pending/retired/orphans;
   retry cleanup durably. Keep known immutable keys for reconciliation, so a late in-flight
   upload is removed rather than orphaned after a crash. No late worker can publish for a
   DELETING profile. Verify cleanup/quiescence before marking DONE; a bounded post-delete
   orphan sweep remains defense in depth. Remove other user-owned files before Auth deletion.
3. Delete Auth user through server Admin API and revoke sessions; anonymize profile to a
   stable minimal DELETED tombstone for game FKs, mapping auth_user_id null.
4. Mark job DONE; clear Android tokens/online caches. After Auth removal the old session
   cannot poll status; an expired/deleted Auth identity is handled by local sign-out, not
   by making deletion-job details public. Offline match/settings remain unless
   the player explicitly asks to erase local data.

Supabase documents that Storage ownership can prevent Auth deletion and that existing JWTs
may outlive it. [User deletion guidance](https://supabase.com/docs/guides/auth/managing-user-data).
Failure at any stage remains blocked/DELETING with retry; no partial public reactivation.
History/backup retention disclosure and tombstone cleanup need product/privacy review.
Retain no email in profile tombstones or game denormalized seat snapshots.

## Acceptance and security tests

Unique username simultaneous claim/case conflict, onboarding retry after Auth success,
Google/email identity linking without duplicate profiles, correct nonce/audience/replay checks,
generic reset/registration responses, token refresh/logout/restart and multi-device handling.
Public response snapshots must fail tests if any email/auth/session field appears.
Avatar forged MIME/extension, malformed/image-bomb/polyglot/oversize input, cross-owner keys,
signed URL misuse, lost uploads, concurrent replace/remove/version conflict and cleanup races.
Direct Storage writes must fail even if a client bypasses the UI. Verify deletion resumes
after Storage/Auth failures and immediately denies game/profile reads with an old JWT.
