# PostgreSQL schema and transaction specification

TASK07 design contract. The profile/stats/IDLE activity subset now has an unapplied TASK09 migration; see [task09-profiles.md](task09-profiles.md) for exact scope and approval gates. Other objects remain future work.
Use UUID primary keys, timestamptz server timestamps and bigint revision counters.
The private app schema is NOT exposed through PostgREST. Grant anon/authenticated no table
DML or private schema usage. Enable and FORCE RLS on all application tables; backend and
narrow authorization helpers have explicit reviewed policies/grants. Never use RLS alone
as a substitute for constraints or transaction locking.

## Tables and required constraints

Unless noted, PK=UUID id, created_at/updated_at=server timestamps. Every FK/index is explicit
in the future migrations; PostgreSQL does not automatically index referencing FK columns.
All text and JSON payload sizes are bounded. No email/account password is copied from Auth.

### profiles
- id UUID PK: random public profile ID; auth_user_id UUID UNIQUE NULL FK auth.users(id)
  ON DELETE SET NULL. Supabase Auth still owns the user's identity; the mapping separates
  public identifiers/history from private Auth lookup.
- status ACTIVE/DELETING/DELETED, username nullable, username_key nullable UNIQUE,
  display_name, avatar_kind DEFAULT/BUILTIN/CUSTOM, built_in_avatar_id nullable,
  avatar_object_id nullable FK avatar_objects(id), version bigint >=0.
- last_active_at nullable is private; public DTO may expose a coarse lastActiveDay.
- Active profiles require an Auth ID, valid normalized username key and display name.
  Deleted tombstones require auth_user_id/username/key/custom avatar null and neutral name.
- Username key: lowercase ASCII [a-z0-9_], 3..20 chars, reserved names denied by server.
  Display name NFC 1..40 code points and <=160 UTF-8 bytes; no controls.
- Avatar discriminator CHECK ensures exactly its selected representation.
- Onboarding atomically creates profile, player_stats and persistent player_activity IDLE.
  Auth users without onboarding cannot create/join/queue.
- Auth deletion is an application workflow first; direct administrator deletion must trigger
  blocking/anonymization reconciliation, not leave an ACTIVE profile with null auth mapping.

### player_stats
- profile_id UUID PK/FK profiles(id), games_played/won/lost bigint >=0, rating nullable,
  version bigint >=0; CHECK games_played=games_won+games_lost.
- Only normally FINISHED games update counters. ABORTED/cancelled rooms do not.
  Win percentage is computed with zero denominator -> 0, not an independently mutable value.
  Rating remains null in v1; no rating updates or leaderboard implied.
- Profile deletion clears its public stats; tombstone has no displayed statistics.

### player_activity (justified cross-workflow occupancy guard)
- profile_id UUID PK/FK profiles(id), mode IDLE/WAITING_ROOM/QUEUED/ACTIVE,
  room_id nullable FK game_rooms, queue_id nullable FK matchmaking_queue,
  game_id nullable FK games, version bigint >=0, last_active_at.
- CHECK exact target by mode: IDLE all null; WAITING_ROOM only room_id; QUEUED only queue_id;
  ACTIVE only game_id. One persistent row/player provides a lock even when currently idle.
- This single guard prevents joining a manual room while queued/active or multiple concurrent
  creates/joins. Unique indexes on target IDs are NOT appropriate: two users share one game.
- Deferred constraint triggers verify resource membership matches the claimed mode and
  a nonterminal resource cannot exist without the matching activity rows. All writers,
  maintenance and deletion use the same transaction protocol.

### game_rooms
- id PK, creator_profile_id FK profiles NOT NULL, name NFC 1..60 code points <=240 bytes,
  name_search normalized lowercase, status WAITING/IN_PROGRESS/FINISHED/CANCELLED/EXPIRED,
  is_locked boolean NOT NULL, version bigint>=0, creator_session_id UUID (private), lease_until, expires_at,
  closed_at nullable, close_reason nullable.
- No password or hash column in this public-list entity. The private creator session must
  still be valid at join; a successful creator renewal may rebind it to that user's current
  session while the stored session is still valid. A revoked stored session expires the
  resource instead of being silently revived. Revoked-session rooms are unavailable even
  before cleanup. Names need not be unique.
- Hard expiry initially created_at +15 minutes; creator lease +90s (renew every30s) only while
  WAITING, bounded by hard expiry. Server time, never client timestamps.
- CHECK terminal status iff closed_at set. Waiting has no game; active/finished has exactly
  one linked game via games.room_id; deferred consistency triggers enforce cross-table facts.
- Index (status,created_at DESC,id DESC), creator FK index and partial
  (name_search text_pattern_ops) WHERE status='WAITING'. No volatile now() partial predicate.
  Query always filters lease_until/expires_at > server now() as well as WAITING.
- Partial UNIQUE(creator_profile_id) WHERE status='WAITING' is defense in depth for one
  waiting room; player_activity enforces exclusivity across resource types.

### room_secrets
- room_id UUID PK/FK game_rooms ON DELETE CASCADE, password_hash text bounded <=512 bytes,
  hash_algorithm/version and created_at. Encoded Argon2id hash includes salt/parameters.
- Private backend-only access; never join this into list/public-profile queries or Realtime.
- Deferred constraint trigger enforces room.is_locked iff exactly one secret row exists.
  Room password/lock changes are not supported in v1; protected rooms remain listed.

### room_participants
- room_id FK game_rooms, slot smallint CHECK IN(1,2), profile_id FK profiles,
  joined_at; PRIMARY KEY(room_id,slot), UNIQUE(room_id,profile_id), index(profile_id,room_id).
- Deferred constraints: slot1=room creator; slot2 distinct from creator; WAITING exactly one
  slot1; IN_PROGRESS/FINISHED exactly two. Only join transaction inserts slot2.
  Retain membership on terminal rooms for authorized recovery/history until purge.
- At most two is a database uniqueness/check guarantee, not a client count or SELECT COUNT race.

### games
- id PK, origin MANUAL/QUICK, room_id nullable UNIQUE FK game_rooms,
  white_profile_id/black_profile_id NOT NULL FK profiles, CHECK distinct.
  CHECK (origin='MANUAL')=(room_id IS NOT NULL).
- status ACTIVE/FINISHED/ABORTED, state jsonb <=16 KiB, state_schema_version=1,
  rules_version='short-v1', version bigint>=0, state_hash, last_action_at,
  abandon_after (initially last accepted action +24h), finished_at nullable,
  result jsonb nullable, abort_reason nullable, stats_applied_at nullable.
- CHECK ACTIVE: no result/finish/abort; FINISHED: valid winner/1/2/3-point result and finish time;
  ABORTED: abort reason/time, no scored result. Full board/phase invariants validated server-side
  plus a defensive DB validation function for shape/ranges/totals. DB does not duplicate legal
  sequence generation. ACTIVE phase may be OPENING/AWAITING_ROLL/MOVING/COMPLETE.
- Manual seats must match room slots; QUICK has no room or room_participants.
- Index each participant FK with (status,updated_at DESC,id), room_id unique, ACTIVE abandon_after.
- Seat profile FKs remain stable via deleted-profile tombstones, not auth.users cascade.
- Creation sets version0 and validated canonical initial OPENING state. No dice are pre-rolled.
  Initial state bytes/hash must match shared Kotlin/TS fixtures.

### game_actions
- (game_id FK games ON DELETE CASCADE, version bigint>0) PRIMARY KEY,
  actor_profile_id FK profiles, command_id UUID, type REQUEST_ROLL/MOVE/END_TURN,
  validated_payload jsonb bounded, resolved_roll nullable, state_hash_before/after,
  created_at. UNIQUE(actor_profile_id,command_id).
- Store server-generated resolved dice for audit; no RNG seeds. Payload contains action
  data, not a full duplicated board snapshot at each version.
- Rejected requests do not increment versions or insert actions. An accepted opening tie
  DOES insert an action/version even though board/phase remain OPENING.
- Append-only to application clients. Keep seven days of terminal-game actions; never
  delete active-game history. Future history can retain summaries separately.

### matchmaking_queue
- id PK ticket, profile_id FK profiles, status QUEUED/MATCHED/CANCELLED/EXPIRED,
  version bigint>=0, owner_session_id UUID (private), enqueued_at, lease_until (+60s, renew20s), matched_game_id nullable FK games,
  closed_at nullable.
- Partial UNIQUE(profile_id) WHERE status='QUEUED'; index(status,enqueued_at,id),
  index(status,lease_until), matched_game_id FK index.
- owner_session_id is server-bound from verified JWT on enqueue; pairing excludes revoked
  sessions even within lease. An authorized owner renewal may rebind a still-live ticket to
  its current session while the old stored session is still valid. Revoked-session tickets
  expire; a fresh queue requires explicit enqueue. Session IDs are checked with read helpers,
  never accepted from clients.
- MATCHED iff matched_game_id NOT NULL; terminal iff closed_at set.
  Exactly two MATCHED tickets per QUICK game validated by a deferred constraint trigger.
  No room ID, password, manual-room fallback or arbitrary client opponent ID.

### command_receipts
- (actor_profile_id FK profiles, command_id UUID) PRIMARY KEY, scope/resource_id,
  request_hmac, hmac_key_version, accepted_version nullable, outcome_code,
  result_ids bounded JSON (room/ticket/game ID), created_at.
- Receipts are inserted atomically with accepted mutations. Store fixed acceptance metadata,
  not a whole board or room password. A retry may attach a newer authorized snapshot.
  Final deterministic errors may be receipted without state mutation; 503 is not final.
- Seven-day cleanup index(created_at); client automatic retry capped at24h. Do not advertise
  unlimited exactly-once semantics. Game version/phase checks remain mandatory after expiry.

### avatar_objects
- id PK, owner_profile_id FK profiles, command_id UUID, expected_profile_version bigint,
  request_hmac, processing_lease_until, failure_code nullable; UNIQUE(owner_profile_id,command_id).
  storage_key UNIQUE immutable server-generated path,
  status PENDING/PUBLISHED/RETIRED, content_sha256, mime='image/webp', byte_size<=262144,
  width/height in1..512, created_at, retired_at nullable.
- Published references require matching owner and successful sanitation; NOT NULL dimensions/
  digest for PUBLISHED. Index(owner_profile_id,status), (status,created_at) for cleanup.
- Object upload and SQL are not one transaction: use the publish saga in profiles-and-avatars.

### account_deletion_jobs / abuse_limits
- Deletion job: id PK, owner_auth_id nullable UNIQUE FK auth.users ON DELETE SET NULL,
  owner_subject_hmac UNIQUE (server-only), command_id UUID, profile_id nullable UNIQUE FK
  profiles, state REQUESTED/CLEANING/DONE/RETRY, stage/error_code (no token/email),
  attempts/next_attempt_at. It also supports Auth accounts with no profile yet. Durably resumes cross-service
  Storage/Auth deletion; never claim one SQL transaction deletes external objects.
- abuse_limits: (bucket,subject_digest,window_start) PK, count>=0, expires_at index.
  Database-atomic reservations, HMAC IP keys with short retention; not raw IP/password logs.
  Bounds and cleanup mandatory. Initially used for room hash attempts, upload and lifecycle
  abuse. No new Redis service required until measured demand justifies it.

## Reference constraints (illustrative only)

```sql
-- These illustrate invariants; full ordered migrations belong to TASK 10.
CHECK (slot IN (1,2));
PRIMARY KEY (room_id,slot);
UNIQUE (room_id,profile_id);
CHECK (white_profile_id <> black_profile_id);
CREATE UNIQUE INDEX one_queued_ticket
  ON app.matchmaking_queue(profile_id) WHERE status='QUEUED';
CREATE UNIQUE INDEX one_waiting_room
  ON app.game_rooms(creator_profile_id) WHERE status='WAITING';
```

CHECK cannot reference other rows/tables. The cross-table invariants above therefore need
deferred constraint triggers (checking final transaction state), FKs/unique constraints,
and restricted write paths; do not pretend a CHECK can count participants or prevent all races.

## Lock order and transaction ownership

Backend TypeScript holds one database transaction/connection for every mutation, including
game reduction. No SELECT and subsequent independent REST INSERT/UPDATE join sequence.

Order:
1. For Quick Play enqueue/pair/cancel/expiry and account deletion only, acquire the same
   transaction-scoped advisory queue gate first. This initially serializes queue selection.
   Manual rooms and ordinary game actions never acquire that gate.
2. Lock all affected persistent player_activity rows in ascending profile UUID order.
3. Lock affected rooms in ID order, then games in ID order, then queue tickets in ID order.
4. Lock any profile/stat/receipt rows in deterministic key order where needed. Read/check a same-command
   receipt under the resource/activity locks before any RNG/state evaluation.
No code holding activity/game/room locks may subsequently request the queue gate.
Game completion releases both activity rows under their already-held locks without acquiring
a queue gate. Profile/avatar publication edits also lock their own activity before profile/receipt
rows, so account deletion cannot deadlock against an inverse profile->activity lock order. Maintenance/deletion follow the same order.

Find tentative immutable resource participants first without locks, then acquire the full
ordered activity set and resource locks, and recheck every assumption/status/version/lease.
If the required participant set changed, rollback/retry with the new sorted set, not an
out-of-order extra lock. Immutable seat/creator IDs simplify this. Bound deadlock/serialization
retries with the same command ID; unique constraints remain the last defense.

### Create and join manual room
Create locks creator activity, verifies IDLE, inserts room/secret/slot1 and updates
WAITING_ROOM activity + receipt, then commits.

Join:
- Verify password with backend-only hash and rate-limit reservation outside row locks.
  The secret is immutable in v1. Never return it to the caller.
- Begin; lock creator and joiner activity sorted, then room; verify WAITING, expectedVersion,
  future lease/expiry, creator WAITING_ROOM for this room, joiner IDLE, distinct profiles,
  active sessions/profiles (including the stored creator session) and successful verification
  for this immutable secret.
- Insert slot2, create initial game, set room IN_PROGRESS/version+1, both activity ACTIVE
  pointing to the same game, write receipt. Deferred constraints run; commit all or nothing.
- Concurrent joiners share the creator lock and slot2 uniqueness. Only one can start a game.
  A failed transaction never consumes a slot; a response timeout retries the same command.

Creator cancel/expiry locks creator then room and rechecks WAITING. Cancel vs join winner
is the first committed transaction, not the first button press. Terminal rooms never reopen.
Expiry sweep and read filtering both use server time; delayed sweep cannot admit a stale room.

### Game action and terminal statistics
Lock both activities, the associated manual room if present, then game; verify own membership
and expected state. TS reducer runs
under that lock. A stale action never rolls again. Insert action/version/receipt and state.
On normal victory, ACTIVE -> FINISHED, update both stats once, room -> FINISHED if manual,
release activities to IDLE, emit versions. Terminal transition guarded by locked old status
and stats_applied_at. Duplicate requests cannot count results twice.
On operational abort, release occupancy/close manual room CANCELLED without statistics.
No opponent disconnect is converted into a fabricated single/gammon/backgammon.
Accepted game mutations use conditional version=expected plus increment exactly1; assert one
updated row and a contiguous action version. Creation alone uses version0. A DB write guard
rejects state/status changes without that increment; lease/last-active metadata is separate.
For IDLE result recovery, me/current may query the latest terminal game via bounded participant
indexes; it is not an unbounded history listing and still enforces caller membership.

Onboarding and deletion start additionally serialize on a transaction-scoped Auth-subject
advisory lock (a separate namespace). If used with queue gate, queue gate comes first, then
Auth-subject lock, then activities/resources. No path acquires either advisory lock after
activity locks. This prevents profile creation racing deletion of a not-yet-onboarded Auth
account. Onboarding has no queue/resource locks; it rejects an existing deletion job.

Profile edits use own activity lock then optimistic version update plus actor profile-row lock;
never acquire activity locks after a profile lock. Account deletion takes the queue gate, relevant activity
rows and resources before updating profile status. Auth/session lookup helpers are reads,
not locks acquired in a conflicting order.

## RLS, grants and views

| Relation | anon/authenticated direct access | Backend / helper authority |
|---|---|---|
| All app tables | Deny SELECT/INSERT/UPDATE/DELETE; no exposed schema | Explicit game_backend policies + least DML grants |
| room_secrets, receipts, abuse, deletion jobs | No client access at all | Endpoint-specific backend access |
| Safe profile/lobby/game DTOs | HTTPS endpoint only; projection allowlist | Verify active caller + membership where required |
| realtime.messages | Receive-only SELECT policy scoped by topic/membership | Server emits minimal payloads |
| storage.objects in avatars | No client writes; private read only approved active-avatar path | Sanitizer uploads, approved URL signer/cleanup |

No broad authenticated INSERT policy on realtime.messages and no arbitrary client broadcast/
presence publishing. Topic authorization helper returns only a boolean using auth.uid(),
the exact topic and live profile/session mapping. It cannot expose hashes/rows or accept a
caller-provided user ID. Harden SECURITY DEFINER with empty search_path, fully qualified
names, a non-superuser owner, narrow execution grants and no dynamic SQL.
[Supabase RLS guidance](https://supabase.com/docs/guides/database/postgres/row-level-security).

Backend role is trusted application authority, not user authority. It must revalidate actor
on every code path; neither a secret API key nor a valid JWT makes any requested row legal.
Storage/Auth administrator secret clients bypass RLS and are kept separate from the
normal SQL connection. Test denied grants as well as allowed endpoint behavior.

## Realtime/retention/maintenance

AFTER triggers use realtime.send with private=true and ONLY the protocol's invalidation
envelope. Never use whole-row broadcast_changes on secret/profile/game tables. WAL
commit visibility prevents rollback notifications; delivery failures are repaired by
protocol snapshot polls, not by assuming exactly-once broadcasts.
[Official database Broadcast](https://supabase.com/docs/guides/realtime/broadcast).

Server-only sweeps expire WAITING room leases/hard expiry, stale queue leases and games
past last_action_at+24h. Reads/joins enforce expiry even before a sweep. Own me/current recovery may lazily expire
invalid/revoked-session waiting/queue reservations through the same guarded transaction,
then return IDLE; a plain read must not silently revive them. Leases never prove
socket connectivity and Presence never decides membership. Store no player game timer in v1.
Initial sweep every minute, bounded batches (e.g.100), SKIP LOCKED only where compatible with
the global lock order; queue reaper takes the queue gate and rechecks after activity locks.

Terminal rooms/tickets and games retain30d for recovery/operations; actions/receipts7d,
short-lived abuse windows <=24h, unreferenced avatar objects <=24h cleanup. Active resources
are excluded from purge. Retention and deletion/backup disclosures require product review;
ratings/match history are future TASK14, not automatically exposed raw action logs.
