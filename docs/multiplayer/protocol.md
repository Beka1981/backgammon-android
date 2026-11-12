# Online protocol v1

Normative design only; endpoints/types below are future contracts, not implemented APIs.
See [architecture](architecture.md) for authority and [database](database-schema.md) for atomicity.

## Transport, identity and compatibility

Use HTTPS Edge endpoints under logical /v1 paths (a router or multiple functions is an
implementation choice). User Authorization is a Supabase access JWT, never a user-supplied
profile/actor ID. The project publishable key identifies the project, not the player.
Require a live verified Auth session and ACTIVE onboarded profile for all online operations
except self-onboarding and owner-only Auth/session/deletion management. Those account
management paths also work before onboarding and permit deletion-status recovery while
DELETING; they never grant room/game access. No anonymous online users; offline play has
no such requirement.

JSON is UTF-8. UUIDs are strings. Versions are nonnegative decimal strings backed by
Postgres bigint/Kotlin Long; never parse them through a lossy JavaScript Number. TS uses
BigInt or decimal comparison. Other SQL bigint counters (including player statistics) also use decimal strings.
Bounded checker counts/dice remain JSON numbers; derived winPercentage may be a bounded
0..100 decimal outside canonical game state.
Timestamps are server UTC RFC3339. Server time controls all leases.
Initial encoded limits: game command4 KiB, room create/join1 KiB, profile edit4 KiB;
avatar upload is the separately bounded2 MiB binary route. Snapshot response<=32 KiB,
lobby/history page<=128 KiB; no full legal-sequence tree in normal responses.
Reject oversized bodies, invalid fields, unknown action variants, unsupported protocol/
state schema/rules versions and arbitrary client board/dice/result fields.

Three distinct versions:
- protocolVersion=1: request/event envelope and DTO contract.
- stateSchemaVersion=1: canonical snapshot shape.
- rulesVersion="short-v1": pinned TypeScript reducer/fixture suite for a game's lifetime.
A deployed reducer must retain support for active games on older rules versions; incompatible
clients become read-only with an update message. Do not silently reset or downgrade a game.

## Command envelope and exactly-once effect window

Every non-idempotent mutation has commandId (client UUIDv4), expectedVersion for an existing
room/queue/game/profile and an allowlisted payload. Scope/resource are in the route.
Creates have no existing expectedVersion. Profile/delete/queue details are below.
Client retains one command ID/body until response or reconciliation; an HTTP timeout is not
permission to generate another roll.

Example intent:
```json
{
  "protocolVersion": 1,
  "commandId": "5e41007d-c257-49eb-af88-2235f3a4e702",
  "expectedVersion": "12",
  "action": {
    "type": "MOVE",
    "from": {"kind": "POINT", "point": 8},
    "to": {"kind": "POINT", "point": 7},
    "die": 1
  }
}
```

The die here chooses a value from the server's existing remaining pool; it never rolls dice.
BAR is a source without point; OFF is a destination without point. No fictitious point 0/25.
No client can submit player, board, newState, random seed, rolled values, result or legal list.

Profile-operation receipt key is (actorProfileId, commandId), bound to route/resource and
canonical body HMAC. Pre-profile account deletion uses its Auth-subject-keyed deletion job;
session revocation/cleanup is intrinsically idempotent and scoped to that Auth session.
Under relevant locks, check the receipt before expectedVersion/phase. A matching duplicate
returns the original acceptance metadata; a reused ID with different content returns 409
COMMAND_ID_REUSED. Password-bearing request fingerprints are keyed HMAC, not plaintext or
an offline-guessable naked SHA digest. Never store the submitted password in receipts.
The HMAC key/version is server-only.

Success envelope:
```json
{
  "protocolVersion": 1,
  "commandId": "5e41007d-c257-49eb-af88-2235f3a4e702",
  "outcome": "ACCEPTED",
  "acceptedVersion": "13",
  "replayed": false,
  "snapshot": {"gameId": "8ed29019-285c-49ea-a1bd-a1398815d51b", "version": "13", "stateSchemaVersion": 1}
}
```
The abbreviated snapshot above stands for the full schema below. On duplicate retry,
acceptedVersion remains original but snapshot may be newer. Never animate the old acceptance
over a newer snapshot. Room/queue success returns the same original room/ticket/game IDs.
Receipts retain seven days; automatic retry is limited to 24 hours. There is no indefinite
exactly-once promise after retention. A stale game version cannot mutate a later turn even
after its receipt expires; clients recover current assignment instead of replaying old creates.

Stable errors: 400 INVALID_REQUEST, 401 SESSION_REQUIRED, 403 NOT_ALLOWED,
404 NOT_FOUND (also unauthorized resource enumeration), 409 VERSION_CONFLICT,
409 PLAYER_BUSY, 409 COMMAND_ID_REUSED, 409 ILLEGAL_ACTION,
409 ALREADY_MATCHED (with own authorized game ID), 410 EXPIRED, 426 CLIENT_UPDATE_REQUIRED,
429 RATE_LIMITED with retryAfterSeconds, 503 RETRYABLE_UNAVAILABLE.
Room joining uses generic ROOM_UNAVAILABLE_OR_PASSWORD_INVALID for wrong/stale/unavailable
credentials; never return secret material. VERSION_CONFLICT may include a participant-only
latest snapshot. Log correlation IDs and safe error codes, not bodies or credentials.

Network timeout/503 -> retry identical command with jittered exponential backoff after session
refresh if needed. Version conflict -> recover and ask for a fresh user intent, never silently
apply a move to a different board. After process death, recover snapshot/assignment first.
Persist at most a small encrypted own pending-command envelope if necessary; no offline
online-command backlog, no password persistence. Tokens are separate from local match data.

## Endpoints and bounded responses

All mutating endpoints except lease/last-active renewals require commandId.

| Logical endpoint | Request / result |
|---|---|
| POST /profiles/onboard | username, displayName, builtInAvatarId -> self/public profile; creates activity/stats atomically |
| GET /profiles/me; GET /profiles/{id} | Self detail vs safe public DTO; no email in public response |
| PATCH /profiles/me | expectedVersion, editable username/displayName only -> updated profile |
| POST /profiles/me/avatar | Raw JPEG/PNG body <=2 MiB; X-Command-Id and X-Expected-Profile-Version headers; sanitized publish pipeline |
| PUT /profiles/me/avatar | Built-in avatar ID, expectedVersion; built-ins/default selection |
| DELETE /profiles/me/avatar | expectedVersion -> default; old custom object queued for deletion |
| GET /profiles/me/avatar-uploads/{operationId} | Owner-only PENDING/PUBLISHED/RETIRED upload status recovery |
| POST /account/delete | commandId + recent reauthentication proof; deletion job status |
| GET /account/delete/{jobId} | Verified Auth owner only, including DELETING/pre-profile state; no public job lookup |
| POST /session/logout | best-effort lifecycle cleanup + current Auth-session revocation; then SDK local clearing |
| GET /me/current | Own activity version, IDLE/WAITING_ROOM/QUEUED/ACTIVE, room/ticket/game IDs; optional most-recent terminal gameId when IDLE |
| POST /me/active | Server-throttled last-active update <=1/minute; not a gameplay heartbeat |
| GET /rooms?q=&cursor=&limit= | 20 default, 50 max; literal name prefix search; opaque stable cursor |
| POST /rooms | name required, password optional; creator occupies slot 1 |
| GET /rooms/{id} | Creator/participant snapshot, version/status, authorized gameId |
| POST /rooms/{id}/join | commandId, expectedVersion from list, optional password -> game snapshot + room |
| POST /rooms/{id}/cancel | Creator only, expectedVersion -> CANCELLED or conflict/current game |
| PUT /rooms/{id}/lease | Creator only; monotonic renewal; no game creation; returns new leaseUntil |
| POST /matchmaking/enqueue | commandId; no room ID/password -> ticket and optionally matched game |
| GET /matchmaking/{ticketId} | Owner-only QUEUED/MATCHED/CANCELLED/EXPIRED and assignment |
| PUT /matchmaking/{ticketId}/lease | Owner-only idempotent server-time renewal; cannot revive terminal ticket |
| POST /matchmaking/{ticketId}/cancel | expectedVersion + commandId -> cancellation or ALREADY_MATCHED |
| GET /games/{id} | Participant-only full current snapshot + legal first moves |
| POST /games/{id}/actions | Command envelope; REQUEST_ROLL, MOVE or END_TURN only |
| GET /games/{id}/actions?afterVersion=&limit= | Participant-only committed history, limit <=64, or SNAPSHOT_REQUIRED |

Email signup/login/reset, Google token exchange and refresh go through Supabase Auth's
maintained SDK/API rather than custom copies of password handling. Avatar URL resolution
is batched (<=50 approved avatar IDs) as part of profile/lobby responses or a bounded helper,
never an arbitrary object path signer. Account deletion status uses its own returned job ID.
Server-only maintenance endpoints require an independent secret/role and are not player APIs.

## Authoritative game snapshot

Full response fields:
- gameId, origin MANUAL/QUICK, roomId nullable, protocolVersion, stateSchemaVersion, rulesVersion,
  version, stateHash, serverTime, createdAt, updatedAt, abandonAfter.
- status ACTIVE/FINISHED/ABORTED; termination nullable {reason}; ABORTED is not a rules win.
- seats: WHITE and BLACK safe public profile summaries (id, profileVersion, statsVersion,
  username, displayName, avatar,
  nullable rating). mySeat is derived from identity, never stored from input.
- board: points array of exactly 24 {owner: WHITE/BLACK/null, count:0..15}, bar and borneOff
  each {white:0..15, black:0..15}. Exactly 15 pieces/player; empty iff owner=null/count=0.
- turn: tagged OPENING, AWAITING_ROLL {player}, MOVING {player,roll,remainingDice},
  COMPLETE {player,roll}, FINISHED {winner,winKind,points}.
- lastRoll nullable {rollId,first,second,rolledBy,opening}; displayRemainingDice multiset.
  Opening rolls: first=White's die, second=Black's; rolledBy null on a tie. Non-opening
  roll fields preserve sampled order but do not represent numbered/identifiable dice.
- legalMoves: bounded distinct first moves from all maximum-use legal continuations,
  empty outside MOVING. Cannot use a locally legal move excluded by compulsory dice rules.
- lastAcceptedAction nullable {version,type,actorProfileId,move?}; server-derived only.
  REQUEST_ROLL results are in lastRoll; client cannot supply them.
- result nullable {winner:WHITE/BLACK,winKind:SINGLE/GAMMON/BACKGAMMON,points:1/2/3},
  required only for FINISHED; ABORTED has no result.

COMPLETE can have unused but unplayable displayRemainingDice; it cannot have legalMoves.
A fully blocked roll automatically moves to AWAITING_ROLL(other), preserves the displayed
roll/unused values as unplayable, and does not require END_TURN. END_TURN clears displayed
roll/remaining values. Opening ties preserve OPENING but create a committed version/action
and visible tie result; make another explicit REQUEST_ROLL, no unbounded RNG retry loop.
White is the designated opening-roll requester (the server rolls both dice). Thereafter
only the current player can request a roll. COMPLETE requires explicit END_TURN, matching
the existing reducer. No RESET, undo, arbitrary pass, resignation or cube action online.

Coordinate contract: points[0]=point 1; WHITE moves 24 -> 1, home 1..6, bar entry=25-d.
BLACK moves 1 -> 24, home 19..24, bar entry=d. White initial 24x2,13x5,8x3,6x5;
Black 1x2,12x5,17x3,19x5. Pixel rotation/mirroring must never reverse wire point IDs.

Canonical state hash: SHA-256 of canonical UTF-8 JSON with sorted object keys, fixed enum
strings, decimal version strings and no floats. Define one fixture-tested encoder; include
gameId, versions, board, turn, lastRoll, displayRemainingDice, status/result/termination.
Exclude profile text/avatars, legalMoves ordering, serverTime and local UI/effects. Sort
remaining dice for canonical multiset encoding, preserve roll.first/second. Reject bad
schema/invariants before presenting a snapshot. Hash equality is not authentication.

## Server reducer parity

TypeScript port retains the existing reducer algorithms/phase behavior, not just individual
destination checks. Generate full ordered continuations to depth four, keep maximum dice
usage, impose higher-die priority, expose only their distinct first steps, and stop on win.
Use immutable values and strict input validation; no random result inside reducer functions.

TASK 11 creates language-neutral fixtures under proposed rules/fixtures/short-v1/:
input state, supplied test dice/action, expected acceptance/rejection, expected next state,
all ordered legal sequences with intermediate boardAfter, canonical state hash and results.
Port existing Kotlin dice matrices, bar/hit/bear-off/doubles/higher-die/order traps,
opening ties/blocked rolls/explicit EndTurn and invalid-transition tests.
Compare sorted canonical move/sequence sets without depending on collection iteration order.
Add seeded complete games, randomized invariant checks, mirrored positions and malicious
wire payload tests. Kotlin and TS must consume identical fixture bytes in CI. Do not expose
a test seed or supplied-dice endpoint in production.

CSPRNG uses Web Crypto bytes with rejection sampling (accept byte <252, value=byte%6+1);
two independent accepted draws. No Math.random, modulo bias, time seed, animation RNG,
client seed or reseeding per retry. This protects against client manipulation, not a malicious
server; verifiable/fairness protocols are a future product/security decision.

## Realtime topics, recovery and visual effects

All topics private; project public-channel access disabled:

| Topic | Receive authorization | Event |
|---|---|---|
| lobby:v1 | Active onboarded authenticated profile | lobby.invalidated, no room/profile row payload |
| room:<roomId>:v1 | Creator or one of its two retained participants, ACTIVE profile/session | room.updated {roomId,version} |
| user:<profileId>:v1 | Exact own active profile/session | activity.updated {profileId,version} |
| game:<gameId>:v1 | Exact WHITE/BLACK active profile/session | game.updated {gameId,version} |

Envelope: {protocolVersion:1,eventId:UUID,type,resourceId,version,occurredAt}.
Lobby invalidation does not have a fictitious globally gap-free version; it uses eventId and
server time and requests a fresh page. Resource versions increment on accepted logical state
changes, not every heartbeat. Authoritative dice/move/turn/result updates are obtained in the
subsequent snapshot or own command response. No client Broadcast INSERT/Presence publishing
policy. Unexpected client-origin data is never interpreted as a game snapshot.

Subscribe successfully -> fetch snapshot -> compare queued notification versions -> fetch
again if newer. Keep one request/resource in flight; compare snapshots monotonically.
Ignore <=known version and duplicate event IDs; version gaps and reconnect always fetch full
snapshot. A 30s safety poll recovers a lost last event even without a later gap. Foreground
WAITING/QUEUED polls every 5s; background stops channels/leases and shows recovery on return.
Reconnect with bounded jittered backoff and a fresh JWT; never accept local cached online
state as authority while resyncing. Use /me/current before re-enqueueing or creating a room.

Realtime authorization is cached per connection/JWT; revocation is not instantaneous.
Minimal invalidations avoid leaking boards/dice to a cached former subscriber, while every
HTTPS fetch checks live authorization. Metadata can remain visible until token expiry;
document this limit rather than promising immediate channel revocation.
[Authorization behavior](https://supabase.com/docs/guides/realtime/authorization).

A snapshot exactly one version ahead with matching preceding state can produce a visual
move/roll effect. Gaps, resync, old acks, background cancellation or malformed data snap to
latest validated state without replaying sound/haptics. Do not wait for effects to accept
new authoritative data. Theme/animation state never changes a game version. Seat profile/Avatar metadata has its own
profileVersion/statsVersion and can refresh without changing an equal-version board or
replaying a game action. A deleted participant is represented by the neutral tombstone
contract in profiles-and-avatars, never an old copied name/photo.
