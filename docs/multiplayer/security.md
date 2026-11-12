# Multiplayer security specification

Design only; none of these policies is deployed. [Protocol](protocol.md) and
[database-schema](database-schema.md) define the associated transactions and wire limits.

## Trust boundaries

Android, client RNG, SDK objects, local files, client clock, user_metadata, Presence,
socket messages and all submitted IDs are untrusted. A publishable project key is public.
Only server TypeScript may evaluate online actions/generate dice; only the transaction may
publish accepted state. A compromised privileged backend is outside the protection offered
by client anti-cheat; audit key use and reducer releases.

| Threat | Required control |
|---|---|
| Forged board/dice/result or turn | Strict intent-only schema; server whole-turn reducer and participant/phase checks |
| Duplicate roll / rapid taps / timeout replay | Receipt before version check/RNG; row locks; one client pending command |
| Two joins or multiple games/user | Sorted persistent activity locks; slot uniqueness; deferred cross-table constraints |
| Cross-account read/write | Verified session-to-profile mapping and per-resource membership on every endpoint |
| Forged authoritative Realtime event | Private receive-only channels, no client INSERT, snapshot via authenticated HTTPS |
| Lost/reordered events | Resource versions, subscribe-then-fetch, recovery/safety polls |
| Hash extraction / brute-force room join | Separate private secret table, Argon2id, rate reservations, redacted responses/logs |
| Arbitrary avatar upload/SSRF | No remote URLs or client object writes, byte/pixel/CPU limits, full decode/re-encode |
| Privileged function/RLS escape | Non-exposed app schema, deny-by-default grants, hardened helpers and dedicated roles |
| Deleted/revoked account still using JWT | Live profile/Auth-session check for HTTPS; minimal invalidations for cached channels |
| Cross-user response after logout | Android session epoch, observer cancellation, namespaced caches and late-response rejection |

## Authentication and keys

Validate signature with the project's trusted issuer/JWKS (or maintained Auth API),
issuer, audience, expiry and subject. Reject anonymous/unconfirmed users for online actions.
Verify current Auth user/session existence plus ACTIVE profile; do not treat a decoded JWT,
client-selected actor or JWT user_metadata as authority. Recent reauthentication is required
for deletion/account-sensitive operations. A fresh refresh-token JWT iat is not sufficient.

Separate clients/credentials:
- Android: project URL + publishable key, own short-lived access/refresh credentials only.
- Edge SQL driver: dedicated game_backend DB role, restricted schemas/tables, no superuser/
  BYPASSRLS, TLS verification. No password interpolation into SQL/logs.
- Auth/Storage admin: server-only secret key, isolated from user-session clients.
- Maintenance: separate scheduler authorization; player tokens cannot invoke cleanup/admin.
Prefer current publishable/secret key types supported by the selected runtime; legacy
anon/service_role credentials, if required by tooling, have the same public/privileged split.
Do not confuse project API keys with player JWTs or configure a secret key as a bearer user.
Pin SDKs/lockfiles in implementation milestones and test the selected gateway JWT mode;
never disable authentication to make an SDK example work.

Revoke default EXECUTE grants on privileged functions; private app schema is not in the
Data API's exposed list. Authenticated clients cannot call game commit/join/matchmaker SQL
helpers directly. SQL backend authority is powerful: handler authorization and transaction
tests are still mandatory even when its policies permit writes.

Session logout/deletion behavior and token expiry are documented in
[Supabase signout](https://supabase.com/docs/guides/auth/signout) and
[session management](https://supabase.com/docs/guides/auth/sessions).
Do not claim JWT signature validity gives immediate revocation.

## RLS and Realtime authorization

Enable/FORCE RLS and explicit grants in migrations, not just Dashboard assumptions.
All app tables default-deny anon/authenticated. Safe profiles/lobby/game snapshots are
allowlisted Edge projections; arbitrary SELECT * must never reach responses.
A non-exposed helper schema may grant function usage/execution without granting access to
the private data schema. Helper owner has only required read privileges and cannot mutate
games/secrets.

Illustrative policy predicate (not a migration):
```text
realtime.messages SELECT TO authenticated
  extension == broadcast
  AND auth.uid() is not null
  AND authz.can_receive_exact_topic(realtime.topic()) -- internally reads auth.uid()/JWT session_id
```
The helper internally reads the verified auth.uid()/JWT session_id, resolves exact topic
types/IDs and current ACTIVE membership; default false,
never substring matching or trusting a user-selected profile ID. Owner/read-only privileges,
empty search_path and schema-qualified object names. No client INSERT policy for Broadcast
or Presence. Disable public channel access and set private=true on BOTH server send and
client subscription. Random topic UUIDs are not an authorization mechanism.
[Official authorization](https://supabase.com/docs/guides/realtime/authorization).

Policies are cached at channel join/JWT update, not rechecked for every received message.
A removed/deleted subscriber may observe resource IDs/version metadata until expiry.
Game boards, dice, actions, profile private fields and room credentials therefore stay out
of Broadcast payloads. HTTPS snapshots verify live membership/session again.
Explicitly test an old open channel after deletion instead of promising instant revocation.
Use a short tested token lifetime (initial target15min) with reliable refresh/rejoin.
This is an expiry exposure bound, not a substitute for HTTP authorization.

Database triggers must emit a constructed minimal payload, not a whole NEW/OLD record.
No user-controlled event name/topic, public base-table publication or Realtime access to
room_secrets/receipts. Authenticated users receive no authority to send a server event.
If an untrusted message somehow arrives, it can at most request a guarded fetch, never
replace game state. Test malformed/oversized notification envelopes too.

## Optional room passwords

These are room-access secrets, not Auth credentials. Nonempty passwords are encoded exactly
as supplied (no silent trimming/normalization), <=128 UTF-8 bytes, controls rejected.
Empty/omitted means open room. Show lock status publicly; never hash or password itself.

Use Argon2id with independent CSPRNG salt >=16 bytes per room, encoded parameters and version.
Initial minimum m=19 MiB, t=2, p=1, per
[OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).
Benchmark an audited maintained Edge-compatible implementation; increase cost if affordable.
Do not substitute fast SHA/MD5, encrypt a plaintext password, reuse a global salt or lower
cost to hide CPU failures. If the selected runtime cannot hash safely within limits, block
protected-room release until a reviewed hashing execution path is available.
At most one concurrent hash task per isolate initially; project-wide limits bound aggregate
work. Neither salts nor hashes enter public DTOs, logs, client storage or Realtime.

Reserve rate-limit budget atomically BEFORE expensive hashing. Initial tunable limits:
- Wrong/protected join attempts:5/minute per account+room,30/hour per account,
  100/hour per HMAC IP bucket; project hash budget initially10/sec.
- Room create3/minute and12/hour/account; one non-IDLE reservation independently.
- Game commands30/minute/account with small burst8; repeated invalid commands monitored.
- Avatar replacements5/hour/account, byte/global CPU budget and IP abuse limits.
- Profile edits10/minute; username changes1/day. Read/snapshot/search requests initially
  60/minute/profile with burst10, separate from mutation buckets; tune poll/search UX in staging.
These are proposed security defaults, not measured service limits. Tune against normal
double/rapid input behavior in staging without allowing unlimited retries. Use short retry
delays/429, not permanent room-wide lockout an attacker could exploit. NAT/shared IP
collateral and distributed-account abuse need telemetry/CAPTCHA/escalation review.

Limit storage is DB-atomic and bounded; if unavailable, hash/upload paths fail closed with503.
Edge in-memory counters alone cannot coordinate multiple instances. Count attempts even
when a client disconnects after expensive work. Do not refund abuse budget on network retry
unless its command receipt proves it is the same already-completed attempt.
Generic ROOM_UNAVAILABLE_OR_PASSWORD_INVALID response, coarse timing differences assessed;
the lock flag is intentionally public, but secret validity must not disclose internal state.
Never log password, hash, submitted body or signed URLs. Password changes/removal are future,
so the final join transaction can recheck the same immutable credential identity.

## Server action integrity

Use Web Crypto unbiased die sampling; only after locking/checking current game and receipts.
A roll and its committed snapshot/action/receipt are one atomic effect. Commit before any
response/event, including ties and blocked passes. Canceled HTTP does not undo committed roll.
Expected-version conflicts never consume a new roll or expose unused sampled values.

Validation uses all whole-turn continuations, maximum dice use and higher-die priority,
not only empty destination checks. Check source ownership, bar precedence, dice pool and
home-board/off rules by the server reducer. No admin “set board” endpoint exposed to players.
Enforce fifteen checkers/player before and after; pin rulesVersion and run fixture parity
for every reducer release. Test high-branching doubles within CPU/memory budgets; equivalent
memoization may optimize search, but truncating legal choices is forbidden.

SQL transaction boundaries are real BEGIN/COMMIT on one retained driver connection.
Use parameterized SQL, query/lock deadlines, bounded result sizes and deterministic lock
order. No network I/O/hash work under game locks. Retry serialization/deadlock outcomes
with the same command ID; never emit success after a rollback. Log transaction version and
safe rejection reason, not secrets or full profile data.

## Storage and deletion controls

Private bucket; no raw upload publication, no client writes/listing. Only current approved
sanitized avatars are readable by eligible accounts. Image content is fully decoded and
re-encoded under strict resource limits; arbitrary file metadata/MIME is not proof.
Server URL signing is bound to approved avatar IDs, never caller-provided paths.
See [avatar saga](profiles-and-avatars.md) for crashes, publication and object cleanup.

Direct dashboard Auth deletion is not the normal application workflow. DELETING status
first denies requests, closes reservations and removes profile data, then Storage cleanup,
then Auth Admin deletion, then tombstone completion. ACTIVE-with-null-auth mapping violates
a DB constraint, so an out-of-band delete must fail or run reviewed deactivation hooks;
never leave such a profile eligible. Alert/reconcile unexpected admin changes.
Signed download URLs/cached images may live until expiry; disclose this limited revocation.

## Logging, operations and abuse

Allowlist correlation ID, action type, resource IDs, versions, latency, lock wait, safe outcome,
counts and hashed short-lived abuse subjects. Redact Authorization, refresh/provider tokens,
room passwords/hashes, email, image bytes, signed URLs, body dumps and database connection URLs
at Edge, HTTP proxy, SQL exception and Android layers. Do not put secrets in analytics/crashes.
Rotate server keys, salts are per-room (not secret), HMAC keys carry versions; deletion cleanup
and retention policy include backups/logs. No public diagnostic endpoint or embedded test seeds.

Monitor authorization denials, stale-version/replay volume, slot/claim invariant violations,
game reducer CPU, queue gate wait, snapshots behind version, Broadcast warnings, recovery polls,
uploads/orphans and quota trends. Stop creating online games if authority/commit fails; offline
mode remains available. Never weaken RLS, sanitization or mandatory dice usage to recover uptime.

## Security test matrix (future implementation, not executed in TASK07)

Use local/staging Supabase with anon, two participants, unrelated third user, revoked/deleted
user, expired/wrong-audience JWT, hostile metadata, publishable key only and backend roles.
Verify direct Data API/RPC/Storage/channel permission DENIAL as well as normal endpoint success.

Room: concurrent slot2 joins, self join, correct/wrong/missing password, stale lease/status,
cancel/expiry race, response timeout replay, no secrets in any response/event/log.
Game: wrong seat/phase/version, supplied dice/board/winner fields, impossible/bar/bear-off/
higher-die/doubles moves, stale retries after turn change, duplicate roll, rolled-back action,
exactly-once result counters and abort with no score.
Queue: self/multi-device/cross-room enrollment, simultaneous matchers, stale tickets,
cancel/match races and no manual-room fallback.
Realtime: spoofed send, nonmember join, old subscriber after revoke, duplicates/gaps/lost-last
event, reconnect during commit and subscription before/after snapshot race.
Profile/avatar: case-conflict username claim, email leak assertions, cross-owner object access,
oversize/MIME spoof/bomb/polyglot, URL signing, concurrent replace/remove and deletion retries.
Android: process death with unknown command result, account switch, rapid taps, skin/rotation/
animation cancellation during remote snapshots; local game remains unchanged and available.
