# Quick Play matchmaking

Design only. Quick Play is a separate queue, not an automatic lobby join mechanism.
It never inspects manual rooms to find opponents, never enters a protected room, and has
no room-name/password field. Shared components are game creation and the occupancy guard.

## Player experience and state

Quick Play requires a confirmed, ACTIVE onboarded account with IDLE player_activity.
Tapping Quick Play sends one enqueue command and displays “Searching for opponent...”.
UI states: Enqueuing, Searching(ticket,leaseUntil), Cancelling, Matched(gameId),
Expired, Recovering, Error. Cancellation remains available while searching.
No opponent -> remain queued, renew/poll while foreground, no fabricated countdown.

The enqueue transaction may return an immediate match. Otherwise receive own private
activity.updated notifications and poll ticket every5s. Renew every20s; server lease is60s.
Background stops renewals; a brief disconnect can recover until lease expiry. Expired tickets
are terminal; prompt for a fresh explicit enqueue. A stale ticket cannot silently revive.
On login/foreground call me/current FIRST, so a committed match is resumed instead of
creating a second queue entry. Navigation alone does not cancel queue membership.

## Initial selection policy

Single rules pool: standard short-v1, two players, no cube, one game.
FIFO by enqueued_at,id among nonexpired QUEUED ACTIVE profiles and matching QUEUED activities.
No rating filter in v1. Exclude same profile and any user with conflicting room/game activity.
Earlier ticket receives WHITE, later BLACK; secure opening dice still determine the starter.

A persistent player_activity row per user and partial unique QUEUED(profile_id) protect
against multiple requests/devices. A manual create/join and enqueue race on the same activity
row: only one can acquire a non-IDLE state. No independent in-memory matchmaking authority.

## Pairing transaction (one SQL connection)

Use one transaction-scoped advisory queue gate for ALL enqueue, pair, renew/pair, cancel,
queue expiry and account-deletion operations. This serializes short queue selection in v1
and prevents two simultaneous empty-queue enqueues from missing each other forever.
The gate is an engineering simplification at this scale, not a scalable global game lock.
Ordinary checker actions/manual-room flows never acquire it. Measure gate wait before
sharding pools in a later version.

1. Validate JWT/session/profile and bounded request; BEGIN; acquire queue gate.
2. Read tentative actor/ticket and the oldest eligible pair (including a prospective new
   caller ticket where appropriate) without taking queue row locks.
   Determine every affected profile ID. Lock activity rows in sorted ID order, then any
   related resources/queue tickets according to database-schema lock order; revalidate.
3. Handle matching command receipt before changing anything. New enqueue requires IDLE;
   atomically insert ticket and QUEUED activity. A duplicate existing queued request returns
   its own ticket; an already ACTIVE account returns its current match/busy response.
4. Select the oldest two distinct eligible tickets (including the caller when appropriate).
   If another older pair is ahead, process a bounded pair and return caller's actual state;
   never skip eligibility checks for the caller. Avoid arbitrary opponent IDs from requests.
5. Lock/recheck their activities/tickets under the gate, including lease_until > now(),
   statuses, the stored owner Auth sessions' validity, profile/session availability and no current game. If participant set differs
   from the already-held set, rollback/retry with the full sorted set; do not add lower-order
   locks later. Implementation can simplify to one pair/transaction.
6. Insert one QUICK game with no room_id, canonical version0 OPENING state and fixed seats.
   Mark both tickets MATCHED/version+1 with the same gameId and closed_at; set both activities
   ACTIVE/version+1 with that gameId. Write receipt; commit.
7. Committed user invalidations notify both. Enqueue response and subsequent ticket snapshots
   contain the same gameId; navigation deduplicates by it.

Do not implement the above as enqueue REST call + unlocked SELECT + separate game INSERT.
The future driver must retain the same transaction connection throughout. No passwords,
manual room lookups or local dice are part of pairing.

Each successful enqueue/renew can attempt a bounded pair while the gate is held, so existing
queue entries do not depend on another client event or a fast background worker. A scheduled
maintenance sweep (initially every minute) removes stale leases and may attempt bounded pairs.
It uses the same gate/order; no unbounded scan or indefinite transaction.
Do not hold the gate during TypeScript legal-sequence enumeration for an active game.

## Race semantics

| Race | Required result |
|---|---|
| Same user's two enqueue requests | One queued ticket or one active game; no duplicate occupancy |
| Two workers pair same candidates | One game; loser rechecks and observes MATCHED |
| Cancel commits before pairing | Ticket CANCELLED, activity IDLE, ticket ineligible |
| Pairing commits before cancel | Cancel returns ALREADY_MATCHED + own gameId; game remains active |
| Lease expires before locked recheck | EXPIRED/ineligible; another candidate or no match |
| HTTP result lost after pair commit | Same command retry or me/current recovers same assignment |
| Create/join manual room races enqueue | One non-IDLE claim; no manual-room fallback |
| Logout/delete races pairing | Same activity locks/session checks; cleanup reconciles committed result |

Cancel has its own command ID and expected ticket version. It cannot destroy a match that
already committed, even if the user pressed Cancel before receiving the match event.
Client remains in Cancelling until outcome is known, then navigates to the game if matched.
Disconnect after matching never frees the active reservation to start another game.
No heartbeat may turn MATCHED/CANCELLED/EXPIRED back into QUEUED.

## Database defenses

matchmaking_queue partial UNIQUE(profile_id) WHERE status='QUEUED';
player_activity PK(profile_id) with exclusive mode/target CHECK; games seats distinct;
MATCHED ticket has one matched_game_id; deferred consistency validates two matched tickets
per QUICK game, both seats/activity rows, and no room_id. Foreign keys retain membership
for recovery. A profile cannot self-match through duplicate sessions or malformed identity.
Never derive participant ID from client user_metadata.

## Load and security tests before release

- 100 concurrent enqueue/cancel calls with few distinct users: no duplicate active assignment.
- Two enqueues into an empty queue pair once without requiring an unrelated third user.
- N eligible users -> floor(N/2) matches and at most one unpaired user (after bounded work).
- Oldest eligible FIFO, stale lease exclusion, same-user multi-device exclusion.
- Cancel/expiry/logout/deletion barriers at each transaction boundary; exact legal terminal state.
- Kill worker before/after COMMIT, duplicate requests, network timeout and delayed notifications.
- An attacker cannot query/cancel another ticket or receive another user's activity channel.
- No manual-room query/join path used by the matchmaking handler, including locked rooms.
- Bounded memory/SQL work, advisory-gate wait/timeout, reconnect recovery and lease costs measured.
- Account/session switch discards old responses; UI cancel/back/recreation accessible in both languages.

Quick Play is implemented in TASK13 after normal online authority/recovery exists; schema
foundations may be designed in TASK10 without exposing an unfinished queue to players.
