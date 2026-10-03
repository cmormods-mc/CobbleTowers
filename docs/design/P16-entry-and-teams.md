# P16: a player-facing way into a run, and forming a team

Written before the code it describes, as the TDS gate requires. **Draft: the questions at the end need
answers.**

Every run today is created by an operator (`/cobbletowers runs create <tower> <players>`); the TDS is
silent on how a player gets into one (it fixes only that up to four players share a run and that a run
is "owned by its own UUID, not by a leader or initiator"). P15 made party validation real, so what is
missing between a player and a validated run is entry: choosing a tower, forming a team, and starting.

## Decisions already taken (user, 2026-10-02)

| Question | Answer |
|---|---|
| How does a player start a run? | `/cobbletowers play` opens a client **screen**: pick a tower, see your party, confirm. No world objects. |
| How do teams of up to four form? | **Invite + accept.** The host invites by name; each invitee accepts and registers their own party; the run starts when all are ready. |
| What does registering a party mean? | **Pick from party + PC** -- a chooser over the whole collection. |
| A registered Pokemon sitting in the PC? | **Swap into the party at lock**: the mod moves registered Pokemon into the party (displaced ones go to the PC) and restores the original layout when the run ends. |
| A registered Pokemon moved out mid-run? | **Allowed**; the level policy skips it (what P15 already does). "Locked" is a soft rule. |

## Phasing: this splits in two

The chooser and the swap/restore are the riskiest part of the whole project: a bug in restore **loses or
duplicates a player's Pokemon**, which is not recoverable by rerunning a run. So:

- **P16 (this doc): entry and teams.** `/cobbletowers play`, the tower picker screen, invites,
  accept/decline, a lobby state, and starting the run via the existing `RunFactory` + P15
  `validateParty`. Registration here is **the current party as-is** -- exactly what P15 validates.
- **P17: the registration chooser and the swap/restore.** The PC picker, the swap at lock, and the
  restore path, designed on their own with a crash-safety argument (original layout persisted before
  any move; restore idempotent; a startup sweep for runs that ended while the server was down).

Nothing in P16 touches a player's Pokemon storage.

## 1. Where a lobby lives

A run needs a UUID and a persisted record before anyone has registered; but a lobby is not a run. The
proposal: a **`TowerLobby`**, in-memory only (never persisted -- a lobby that outlives a restart would
need its invitees' consent re-asked, and a half-formed team is worth nothing after a crash). A lobby
holds: host id, tower id, an ordered invite list with a state each (`INVITED`, `ACCEPTED`, `DECLINED`),
and an expiry. The first persisted fact is the run itself, created when the host starts it.

## 2. The flow

1. `/cobbletowers play` -> `PlayScreenPayload` to the client (tower list, from the loaded definitions,
   plus the player's current party for display).
2. Host picks a tower. A lobby exists as soon as the first invite is sent, or immediately for a solo
   start.
3. `/cobbletowers play invite <player>` (or an invite button) -> the invitee gets a chat message with
   clickable accept/decline (the same click-event style vanilla uses), and the screen shows state.
4. Everyone accepted -> host confirms Start -> `RunFactory.create(content, tower, players, parties, ...)`
   then `RunLifecycle.validateParty`. A rejected party reports its reasons to the player(s) at fault, and
   the lobby stays open so they can fix it and retry rather than being dropped.
5. A validated run proceeds through the existing allocation path.

## 3. Rules

- A player may be in **one** lobby or run at a time (`TowerRuns.forPlayer` already says who is in a run).
- Max team size four (TDS); the lobby refuses a fifth invite.
- Invites expire (proposed: 5 minutes) and lapse if the host leaves.
- Declining or leaving before start removes you; it never affects anyone's Pokemon.
- Permissions: the player-facing commands need no operator level; the existing `runs ...` operator
  commands are unchanged.

## 4. Not in P16

- The PC chooser and swap/restore (P17).
- A physical entrance (declined by the user in favour of a screen).
- Leaderboards (TDS #90), spectating a lobby, matchmaking with strangers.

## 5. Tests

- Pure: `TowerLobby` state machine (invite, accept, decline, expiry, one-lobby-at-a-time, size cap).
- Live: bots can run commands but not open a screen, so the live test drives the same server-side
  entry points over RCON (`execute as <bot> run cobbletowers play ...`): invite, accept, start, and a
  validated run reaching a cell; plus a decline, and a fifth invite refused. The screen itself is
  untestable on this rig, as with the vendor and reward screens.

## Open questions

1. Invite expiry: five minutes, or none until the host cancels?
2. Should the host be able to start with some invitees still pending (they are dropped), or must all
   accept?
3. Tower picker: show every loaded tower, or only ones the player has unlocked? (Nothing in the code
   gates towers today.)
4. If a validated run's allocation fails (no cell), does the lobby survive so the team can retry?
