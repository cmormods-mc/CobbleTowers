# P37 - Battle-track UI, claimable rewards, modder-extensible tracks

Status: design (2026-10-07). Decisions below are the owner's; "Open" at the end lists what is still unsettled.

## Goal

The Progress tab shows a sideways-scrolling, battle-pass-style **track** of reward nodes: a **mastery track** (levels) always, and a
**season track** while a season runs, with a "days left" box. Rewards are **claimed** by the player. Both tracks are **data**: a datapack
or a server-owner config file can add to them, and the mastery track can run past level 30. Menu icons are redrawn as pixel art.

## Owner decisions

- Datapack **and** a server-owner config file.
- Mastery can go beyond level 30.
- Rewards become claimable (mastery, and the season track with it, for one consistent model).
- Rental-draft art and its sprites are untouched. Modifier-card sprites are deferred; **menu icons** change now.
- No premium lane: everything is earned in play.

## What exists (verified in code)

- Season track: `season_tracks/*.json`, `SeasonTrackDefinition`, `SeasonTrackRegistry` (datapack reload; one track, the first by id). Steps cost
  `step_cost` points each; grants are items/currencies/cosmetics; today they are **granted automatically** in `SeasonProgressService.settle`
  through the pending-reward store (deduplicated by a deterministic grant id). `Progress.steps()` counts steps already granted.
- Mastery: level = achievements unlocked in a tower (`TowerMasteryStore`). `MasteryPerks` hard-codes the rank names and thresholds
  (1, 5, 10, 15, 20, 25, 30) and three perk percentages (vendor discount, CobbleDollar bonus, Raid Points bonus).
- Seasons: `Seasons.phase()` gives the active number; `SeasonSchedule` knows the dates; seasons are configured in
  `config/cobbletowers-seasons.json`.

## Data formats

### Mastery track: `data/<ns>/cobbletowers/mastery_tracks/<name>.json`

```json
{
  "schema_version": 1,
  "tower": "cobbletowers:tideforge",
  "ranks":  [ { "level": 1, "name": "Bronze" } ],
  "levels": [
    { "level": 5,
      "label": "Silver cache",
      "perks": { "vendor_discount_percent": 3 },
      "grants": [ { "item": "cobbletowers:cobble_dollar", "amount": 100 } ],
      "cosmetics": [ "title_silver" ] }
  ]
}
```

- `tower` is optional; absent or `"*"` means every tower. A track for one tower merges on top of the `"*"` tracks.
- `perks` **set** a value from that level up (the value at level L is the one set at the highest level <= L), which is exactly how the
  built-in table reads: vendor 3 at 5, 6 at 15, 10 at 30; CobbleDollars 5 at 10, 10 at 20, 15 at 30; Raid Points 10 at 25. Perks stay
  **automatic** (they are rates applied at the point of use); the track only displays them.
- `grants` / `cosmetics` use the season track's grant format (item id, amount, optional `components`, `label`). They are **claimed**.
- Levels may be any number >= 1. A tower's displayed track length is the highest level any file defines or the number of achievements it has,
  whichever is greater. A level above the count of achievements a tower has is simply not reachable until more achievements are defined.
- **Merge:** files load in id order; for each level, `grants` and `cosmetics` concatenate, `perks` and `label` last-wins, ranks last-wins by
  level. The shipped `cobbletowers:default` track reproduces today's ranks and perks exactly (a test pins every level 0..30), plus modest
  CobbleDollar grants at the rank thresholds (untuned, to be set by the owner).
- Unknown items (an absent optional mod) are skipped with one log line and shown as "unavailable"; never fatal.

### Season track: existing `season_tracks/*.json`, plus merging

- The first file (by id) that has `step_cost` is the base. Any file may carry `"add_steps": [ { "step": 12, "grants": [...], "cosmetics": [...] } ]`
  which **append** to step N (a step past the end extends the track with empty steps first). The existing positional `steps` array is unchanged.

### Server-owner config: `config/cobbletowers-tracks.json`

```json
{ "auto_claim": false, "mastery": { ...same shape as one mastery track file... }, "season": { "add_steps": [ ... ] } }
```

- `auto_claim: true` grants a reached node at once (today's season behaviour), for servers that do not want a claim step.
- The `mastery` and `season` blocks are the same shapes as the datapack files and are merged **last**, so an owner can add or override.
- A bad file is logged and ignored (the datapack tracks still work), like `cobbletowers-seasons.json`.

## Claiming (server)

- A claim key per node: `s<season>:<step>` and `m:<tower>:<level>`, kept in `TowerSeasonProgressStore` (new `claims` map, per player).
  **Migration:** a season step <= `Progress.steps()` counts as claimed, so nobody loses or re-receives a prize.
- `settle()` no longer grants; it only records points (unless `auto_claim`). The claim path queues the node's grants through the pending-reward
  store with the **same deterministic grant id** the auto path used (`addIfAbsent`), saves the claim key **after queueing** (replay-safe: a crash
  between them re-queues harmlessly), then delivers.
- Server revalidates every claim: node exists, reached (points / mastery level), not yet claimed, season still active for season nodes
  **As written in code (2026-10-07 review): season nodes can be claimed only while that season is running; unclaimed steps are lost when it
  ends.** The earlier idea (claimable through the off-season, until the next season's first point replaces the stored progress) is NOT built;
  it needs the season lane and `SeasonProgressService.refusal/claim` to use `Seasons.viewNumber()` and an off-season display. Owner decision
  (see the release checklist).
- Mastery grants are claimable at any time after the level is reached (they do not expire).
- Claim all is one action that claims every reached, unclaimed node in order.
- Main-thread rule: the action handler hops to the server thread; nothing keeps a `ServerPlayer`, only the UUID.

## Network

- `TrackStatePayload` (server -> client): for the Progress tab, one request returns **both** tracks compactly: per node a number, a short label,
  an icon key (item id or cosmetic), a state (locked / reached / claimed / unavailable) and for the current node progress points.
  Season block carries season number, name, **end instant (epoch millis)** and points; the client counts down from it ("Ends in 12 days", "in 5 h").
  Sent on opening Progress and after every claim; never per frame.
- `TrackActionPayload` (client -> server): `claim <track> <key>` / `claim_all <track>` / `refresh`.
- Both versioned (`_v1`), codec-bounded like the Hall payloads.

## Client: the track view

- Horizontal strip of node tiles (nine-slice frame, batched), one **big milestone tile** at ranks / every 5th season step, dimmed future nodes,
  glowing current node with a progress bar into the next. Scroll with the wheel, drag, Left/Right, and "Jump to current". Opens centred on the
  current node. Only visible nodes are drawn; node frames are one draw call each.
- Under the strip, a detail panel for the selected node: what it gives, what it needs, and a Claim button; "Claim all" in the header.
- The season lane sits under the mastery lane while a season runs, with the "days left" box in its header; absent otherwise.
- Must lay out at GUI 320x240.
- Fallback when the server has no track payload (an older server): the current Progress cards remain.

## Icons

A small pixel icon sheet drawn in the mod's style (bronze/oak/parchment, 16x16) replaces the Cobblemon sprites in the **menu**: Tower Hall,
Trials, Codex, Progress, Echoes, Social, Collection, Settings, Refresh, Lobby, Vendor, Cash out and the Progress/Social cards. Pokemon
sprites stay where a Pokemon is the subject (partner inspection, rental cards). Shipped as one texture sheet plus a code table, so adding an
icon is one cell.

## Stages (each built, tested, committed)

1. Data: mastery track definition, registry (merge), config, `MasteryPerks` reading it (built-in default equal to today's). Tests.
2. Claims: store, service (season + mastery), migration, payloads and action handler. Tests, then a live check on the rig.
3. Icons.
4. Track UI on the Progress tab (mastery lane, then season lane with days left). Screenshot rig coverage at scale 2 and 3.
5. Docs: datapack/config guide and a sample addon pack.

## Open

- Values of the shipped mastery grants (modest, untuned).
- Whether an ended season's unclaimed nodes should lapse at once or stay claimable a grace period (proposed: until the next season starts).
