# Manual rooms, public lobby and waiting room

Design only. [Protocol](protocol.md) owns request fields; [database](database-schema.md)
owns constraints and locks. These rooms are never Quick Play opponents.

## Initial creation UI: exactly two fields

1. **Room Name** — required, custom creator text.
2. **Password** — optional, obscured text with an accessible reveal control.

Create/Cancel buttons are actions, not configuration. Do not add visibility, rating, timer,
color, stake, cube, game length or tournament controls. No separate private-room toggle.
Room name NFC 1..60 code points, <=240 UTF-8 bytes after trimming leading/trailing whitespace;
reject controls/newlines, render as plain text. Names are not unique.
Password omitted or zero-length -> open. Do not silently trim a nonempty password: whitespace
is significant. Allow 1..128 UTF-8 bytes, reject controls; recommend a longer phrase without
making account-password rules apply. Do not Unicode-normalize passwords on one side only.

A nonempty password creates a protected but PUBLICLY LISTED room with a lock icon.
The creator occupies slot1 immediately. Any eligible authenticated player may join an open
room; a protected room additionally requires its password. Exactly two distinct profiles.

## Public means discoverable to online players

The online lobby is authenticated (confirmed, onboarded ACTIVE users), not an anonymous
internet directory. It shows all WAITING, unexpired rooms regardless of is_locked. It excludes
IN_PROGRESS, FINISHED, CANCELLED and EXPIRED. Expired lease/expiry filters apply even when
the cleanup sweep has not yet run. Also require an ACTIVE creator, valid stored creator Auth
session, and matching WAITING_ROOM activity. A revoked-session room is unavailable even if its
lease timestamp has not expired. Never filter out locked rooms.

Each item includes:
- id, roomVersion, room name, creator safe profile ID/username/display name/avatar.
- creator rating when non-null; no fabricated rating/rank.
- occupancy 1/2, lock boolean/icon and Join action.
- no email, password, hash, salt, session identifiers or internal abuse counters.

RoomListUiState: Loading, Content(items,nextCursor,query,refreshing), Empty, RecoverableError;
items keyed by roomId. Use stable pagination ordered created_at DESC,id DESC. Cursor encodes
that pair and query fingerprint, is opaque/signed and cannot select arbitrary filters/SQL.
Default20/max50; q is a literal case-normalized name PREFIX (document this in search hints),
bounded60 chars, escape LIKE wildcard characters. Parameterized SQL and execution limits.

Refresh replaces the first-page result. Search change discards cursor/old results. Page merge
deduplicates by roomId and keeps only the newest roomVersion; a terminal/removed item cannot
be resurrected by an old page. Live invalidation schedules a debounced first-page refresh and
marks older pages potentially stale; clicking Join always checks server authority.
An item disappearing after join by someone else is normal: remove it, show a safe message,
and remain in the lobby. Do not decrement occupancy locally to fabricate availability.

Subscribe to lobby:v1 only while viewing lobby; close on game/background. Broadcast only
lobby.invalidated, no table rows. Refresh on foreground, reconnect and every30s as safety.
Bound invalidation/fetch frequency (e.g. one fetch/sec); this saves queries but not Broadcast
fanout billing. Measure churn before adding server coalescing.

## Create and wait

Submit one command ID/body; disable duplicate create while pending. Server checks own IDLE
activity, rate limits and room limits, hashes a nonempty password before entering the create
transaction, then atomically inserts room, optional secret, creator slot, activity and receipt.
On success navigate to Waiting(roomId). On timeout retry identical command; do not create
another room with a new key. GET me/current can recover the existing room.

Waiting screen: room name, creator username/avatar, open/locked status,
“Waiting for opponent...”, Cancel button and visible connection/cancellation state.
No room password is redisplayed or persisted. Subscribe private room:<id>:v1 and own
user:<profileId>:v1, then fetch. Show completed/expired status even after app recreation.
Foreground renewal every30s extends the creator lease to server now+90s, capped by the
original15-minute expiry. Renewal cannot change roomVersion or restore a terminal room.
Poll status every5s while waiting to repair a lost join notification.

Background/disconnection stops renewal, not immediate cancellation. Creator has up to90s
from last successful renewal, but hard expiry is never extended beyond15min. On return,
recover server status before resuming renewals or allowing another create.
These are server defaults, not extra creation UI options.

## Join flows and atomic start

Open: Join -> submit commandId, expected roomVersion -> server transaction -> online game.
Protected: Join -> password dialog -> submit same room metadata + entered password over HTTPS
-> server verifies -> transaction -> online game. On failure keep the dialog usable with
generic safe error and rate-limit retry delay. Never log/save password or include it in URLs.

Password hashing/verification and rate-limit reservation are outside resource row locks;
the hash is immutable for this room in v1. The final transaction locks creator/joiner activity
rows sorted, then room; rechecks lease, hard expiry, WAITING/version, active identities,
creator's matching occupancy, joiner's IDLE status, distinct players and verified credential.
Insert slot2; create version0 OPENING game; mark room IN_PROGRESS/version+1; put both activity
rows ACTIVE; write receipt. Commit as one transaction or roll back everything.
At most two is enforced by UNIQUE(room_id,slot) with slot CHECK1/2, not client UI or COUNT.

No gameplay starts before this commit. Neither player chooses authoritative initial board,
dice, starter or color. Creator=WHITE and guest=BLACK; opening dice decide who moves first.
Both get server assignment via own response/private room or activity invalidation and fetch.
Navigate once by gameId, even if duplicate events arrive. A second concurrent joiner fails;
it cannot overwrite slot2 or create another game. A creator cannot join their own second slot.

## Lifecycle and race outcomes

| State / event | Authoritative outcome |
|---|---|
| WAITING + join wins transaction | IN_PROGRESS, exactly two participants + one game |
| WAITING + creator cancel wins | CANCELLED, creator activity IDLE, later join fails |
| WAITING + lease/hard expiry | EXPIRED, creator IDLE, join checks reject immediately |
| IN_PROGRESS + normal rules victory | FINISHED; game FINISHED; stats updated once |
| IN_PROGRESS + operational abort | CANCELLED with reason; game ABORTED, no scoring |
| Any terminal room | Never becomes WAITING again; new room requires new intent |

Cancel against a committed join returns conflict with the creator's authorized gameId;
it does not eject the guest or reset the game. Waiting-screen Back triggers explicit cancel
confirmation. If cancel acknowledgement is lost, show “Checking room...” and reconcile:
never promise cancellation before a server result.

No active-match win/forfeit solely from transport disconnect in v1. Reconnect recovers the
same game. A game with no accepted rules action for24h is operationally ABORTED with no
win/loss; this is retention housekeeping, not a per-turn timer or gammon rule. Display that
policy before joining and warn on recovery near expiry. Activity leases cannot fabricate
game moves or extend the last-action abandonment deadline.
Deleted/disabled account: cancel WAITING rooms; abort active games without a fabricated
rules result; release both reservations transactionally. See profiles-and-avatars.

## Required acceptance tests

- Creation form has exactly the required name and optional password settings.
- Open and locked WAITING rooms appear together with 1/2 and the correct creator avatar.
- Empty/invalid names, Unicode length/control limits and literal wildcard search handled.
- No API/event/error/projection contains plaintext or hash.
- Wrong password, invalid/expired room and rate-limit denial reveal no secret detail.
- Barrier test with100 concurrent joiners yields exactly one slot2, one game, two active users.
- Same profile cannot create/queue/join in simultaneous requests; creator cannot take slot2.
- Join/cancel/expiry races yield one committed legal outcome and both clients recover it.
- Response lost after commit + same-ID retry returns the same game, not a duplicate.
- Duplicate/out-of-order notifications, page refresh and late pages never duplicate/revive rooms.
- Creator background/restart, lost final join event and expired lease recover by snapshot.
- Username/avatar/lock descriptions, keyboard/TalkBack dialogs, Georgian/English strings and
  portrait/landscape layouts preserve the existing design system.
