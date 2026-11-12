# TASK09 security and deployment acceptance matrix

Run remote checks only after read-only schema inspection and explicit approval. Never run local-bootstrap.sql on Supabase: its Auth tables are deliberately minimal synthetic fixtures for a disposable local PostgreSQL cluster.

## Locally executable tests

- SQL: apply 202611120001_profiles.sql to an empty disposable UTF-8 PostgreSQL database after local-bootstrap.sql, then run profiles-security.sql with psql -X -v ON_ERROR_STOP=1. All data tests roll back.
- SQL assertions cover forced RLS/grants, direct RPC/write/statistics denial, private mapping protection, confirmed/live session binding, field allowlisting, canonical names, atomic creation, case conflict, expected versions, retry receipt mismatch and throttling.
- Concurrency (same disposable cluster only): run local-race-setup.sql, then pgbench -n -c 8 -j 2 -t 1 -f supabase/tests/local-race.pgbench against that cluster, then local-race-verify.sql. Eight calls across two synthetic identities must yield one profile/statistics/activity set. These fixtures remain in the temporary cluster, which should be stopped after verification; never run them remotely.
- TypeScript: node --test supabase/functions/player-profile/validation.test.ts.
- Edge type check: npx --yes deno check --config supabase/functions/deno.json supabase/functions/player-profile/index.ts. Review/commit the generated dependency lock with the source; deployment remains separately approved.
- Android: ./gradlew build test lint and ./gradlew connectedDebugAndroidTest. Authentication/profile tests use fakes or MockEngine; existing movement/skin/persistence tests are retained.

## Staging/remote gates NOT established by local fixtures

| Scenario | Required outcome |
|---|---|
| Publishable key only / anon JWT / expired JWT / wrong issuer or audience | 401, no profile or private data |
| Valid account A with client-submitted authUserId/profileId/statistics/status | 400; cannot choose owner or edit counters |
| Account A sends profile B ID in URL/body or calls private SQL helper directly | Reject; own endpoint only, direct schema/function access denied |
| Confirmed A logs out or Auth session is revoked/deleted | New profile calls fail current-session check; stale Android responses cannot reopen account UI |
| Unconfirmed/anonymous/banned Auth user | Denied even with syntactically valid token |
| Two different accounts claim Same_Name/same_name concurrently | Exactly one profile claim, loser gets USERNAME_UNAVAILABLE; no orphan statistics/activity |
| Same account concurrently creates from two devices | Same public profile ID, exactly one stats and IDLE activity row |
| Two edits from same expectedVersion | One accepted increment, other VERSION_CONFLICT; no partial edit |
| Lost response retried with same commandId/payload | Current safe projection, no second increment |
| Same commandId with altered payload or owner | Conflict/denial, no overwrite or cross-owner receipt disclosure |
| Crash/rollback between profile/stat/activity writes | No partial onboarding; retry succeeds |
| UTF-8 malformed, NUL/C1/bidi/hidden controls, lone surrogate, 41 code points | Rejected consistently; no raw SQL error in response/log |
| Display-name normalization / ASCII trim / reserved-name casing | Matches shared Android/TypeScript/SQL fixtures |
| Username brute force / oversized/streamed body / rapid edits | Bounded body, atomic rate limit, safe 400/429; no logging of payload/tokens |
| Database unavailable or timeout | 503; local play remains usable; no false committed profile |
| Auth schema/role capability mismatch on managed Supabase | Fail closed in staging, revise reviewed migration before production |
| TLS invalid certificate or wrong hostname | Database connection fails, no rejectUnauthorized:false workaround |
| Public DTO/error/analytics snapshot | Contains no email, Auth UUID, JWT, session ID, private metadata or DB connection details |
| Encrypted session restore/account switch + profile response race | Old owner's draft/profile never appears in new account |

A genuine Supabase Auth server + restricted connection role + Edge gateway round-trip must be tested. Local Auth fixture tables do not emulate GoTrue, RLS gateway, token signing, SMTP, Supavisor or managed Auth privileges. Review public schema/default grants and api.exposed_schemas before deployment. app and profile_api must not be exposed.

## Avatar upload release gates — upload remains disabled

No bucket/policy/upload handler is included in TASK09. Do not treat the absence of an upload button as proof that an existing remote bucket is secure; inspect remote Storage policies separately.

Before enabling Photo Picker/custom avatars, test JPEG/PNG byte sniffing vs forged MIME/extensions, malformed/decompression-bomb/oversized/polyglot images, bounded streaming/decode memory/CPU, metadata stripping, normalized dimensions/output limits, private bucket enforcement, cross-owner read/write/list denial, arbitrary external URLs and key signing refusal, approved-object-only download, short-lived signed URL scope, concurrent replace/remove and version fencing, interrupted processing, PENDING orphan cleanup and deletion races.

All authenticated/anonymous direct Storage INSERT/UPDATE/DELETE/list attempts must fail. Only a tested server sanitizer can publish an immutable approved object; old/current object replacement cannot delete a newer reference. No production Storage policy should be deployed before these tests pass.
