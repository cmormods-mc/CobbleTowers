# Extending the CobbleTowers tracks

The Progress tab shows two sideways-scrolling reward tracks. Both are data, so a datapack (an addon mod's, or yours) or the server owner's config
file can add rewards without replacing anything.

- **Mastery track**: one level per achievement unlocked in a tower. A level can carry perks (rates applied automatically), a rank name, and
  rewards the player **claims**.
- **Season track**: points earned during a season buy steps; each step has rewards the player claims. Shown only while a season runs, with a
  "days left" box.

Rewards are claimed from the track (Claim / Claim all). A server owner can switch on `auto_claim` to grant them the moment they are reached.

## Mastery track files

`data/<namespace>/cobbletowers/mastery_tracks/<name>.json`

```json
{
  "schema_version": 1,
  "tower": "cobbletowers:tideforge",
  "ranks":  [ { "level": 40, "name": "Mythic" } ],
  "levels": [
    {
      "level": 40,
      "label": "Mythic cache",
      "perks": { "vendor_discount_percent": 12 },
      "grants": [ { "item": "minecraft:diamond", "amount": 4 } ],
      "cosmetics": [ "title_mythic" ]
    }
  ]
}
```

- `tower` is optional. Leave it out (or write `"*"`) for every tower. A file for one tower merges on top of the ones for every tower.
- A level can be any number from 1 up. A level is reached when the player has that many achievements in the tower, so levels past 30 need
  more than 30 achievements (add them with `achievements/*.json`); until then they simply show as locked.
- `perks` **set** a value from that level upward: `vendor_discount_percent`, `cobble_dollar_bonus_percent`, `raid_points_bonus_percent` (0 to
  100). The value at level L is the one set at the highest level at or below L. Perks are never claimed.
- `grants` use the season track's format: `item` (an item id, or `cobbletowers:cobble_dollar` / `cobbleraids:raid_points`), `amount`, and
  optional `components` (item tag text) and `label`. An item that does not exist on the server is skipped with one log line.
- `ranks` name a level and everything above it until the next rank.
- Cosmetics from a mastery level are recorded for the player under `m:<name>`; showing them in the menus is not built yet.

**Merging.** Files load in id order, then the owner's config. Per level: `grants` and `cosmetics` add up; `perks`, `label` and a rank's name are
last-wins. The shipped `cobbletowers:default` file holds today's ranks and perks.

## Season track files

`data/<namespace>/cobbletowers/season_tracks/<name>.json`. The first file (by id) that has `steps` is the base:

```json
{ "schema_version": 1, "step_cost": 75, "steps": [ { "grants": [ { "item": "cobbletowers:cobble_dollar", "amount": 50 } ] } ] }
```

Any file may add to it with `add_steps`, which **appends** rewards to step N (a step past the end extends the track):

```json
{
  "schema_version": 1,
  "add_steps": [
    { "step": 5,  "grants": [ { "item": "mymod:trophy", "amount": 1, "label": "Addon trophy" } ] },
    { "step": 20, "cosmetics": [ "title_addon" ] }
  ]
}
```

Grants may use `{season}`, `{season_name}` and `{color}`. Cosmetics are season cosmetics (`title_*`, `banner_*`, `badge`, `club_*`).

## Server owner config

`config/cobbletowers-tracks.json` (read at server start):

```json
{
  "auto_claim": false,
  "mastery": { "levels": [ { "level": 10, "grants": [ { "item": "minecraft:emerald", "amount": 5 } ] } ] },
  "season":  { "add_steps": [ { "step": 3, "grants": [ { "item": "minecraft:emerald", "amount": 2 } ] } ] }
}
```

- `mastery` is one mastery track file (without `schema_version`); `season` holds `add_steps`. Both are merged **after** every datapack file.
- `auto_claim: true` grants a reached node immediately (the behaviour before claiming existed).
- A file that cannot be read is logged and ignored; the datapack tracks still work.

## Claiming rules

- A node can be claimed once it is reached and not yet claimed. The server checks all of it; the client only asks.
- Season nodes can be claimed while that season runs and through the off-season after it; they lapse when the next season starts. Mastery rewards never expire.
- Season steps that were granted before claiming existed count as claimed, so nobody receives them twice.

A ready-to-copy example pack is in `docs/addon-example/`.
