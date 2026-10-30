# P57: the Trick or Treat door

Status: owner decisions taken 2026-10-30; built in the same session, see "As built" at the end.

## What and why

The Halloween tower's route doors are three 5 x 6 openings of iron bars (P55). A lane whose stop is **Trick or Treat** (`RouteGraph.Kind.TRICK_OR_TREAT`, the Jack-o'-Lantern Vault) gets the
owner's **Hallowbound trick-or-treat door** instead: a dark-oak door with forged ironwork, a brass skull knocker, pumpkin vines and a lit hanging sign, whose wing swings, overshoots and
rebounds. The art and motion come from the owner's separate project `L:\Codex\CobbleRaids GUIs\outputs\hallowbound-halloween-door` (MIT, Polymer 0.9.19, 6 model JSONs, a 1024 atlas).

## Owner decisions (2026-10-30)

1. **Merge into CobbleTowers**, not a second jar: one install. The standalone mod stays as its own project and is not touched.
2. **A wall with the door in it; the server opens it.** The 5 x 6 opening is filled with the tower's wall block (polished blackstone bricks) except a 1 x 2 gap in the middle, where the door
   stands. It opens by itself when the party's vote settles on that lane, exactly when the iron bars of the other lanes clear, and it plays the swing.

## Design

* **No registered block.** The standalone mod registers a real `DoorBlock` (placeable, redstone, four reserved Polymer door states). Here the door is a *prop*, drawn as Polymer virtual item
  displays like the vault, so nothing is added to the registries (a server-only mod stays server-only; no `polymer-blocks` dependency). The gap is closed by two `barrier` blocks (collision,
  invisible without the item) and opened by turning them to air. A player cannot place or break it; that is the point.
* **Three display parts** per door (`frame`, `wing`, `glow`) on `paper` with Polymer-allocated custom model data (`requestModel`), the same way the vault does it. Frame is still, wing and glow
  rotate together about the hinge, glow is brightness 15/15. Right hinge only (the left-hinge models are not carried).
* **Motion**: the standalone controller's 12-tick Hermite opening and 10-tick smoothstep closing, ported unchanged (`DoorMotion`), with its tests. Matrix maths ported unchanged (`DoorPose`).
* **Pack gate**: a player without the Polymer pack is not shown the door parts (as with the vault); the wall and the gap still work, so the room is passable.
* **Wiring** (`com.cobbletowers.halloween.door`):
  * `TrickDoors.install(level, door, origin, facing)` is called by `RoomDresser` when it dresses a lane as TRICK_OR_TREAT: fill the opening with wall, barrier in the gap, door display closed.
    When a later dressing changes that lane to another kind it calls `uninstall`: bars are put back, the display is destroyed.
  * `TowerDoors.chosen` opens a trick door (barrier to air, swing, creak sound) instead of clearing bars; `resetAll` closes it (barrier back, swing shut, never with a player in the gap)
    instead of painting bars into the open gap; `ensureOpen` asks `TrickDoors.isOpen`.
  * Displays are Polymer virtual entities, never saved: a restart ends the run (existing policy), and `RoomDresser.forget` / run end destroys the run's doors.
* **The event room too** (owner, 2026-10-30: "every Trick or Treat room"): when the vault vote opens (`TrickOrTreatService.tend`), `TowerDoors.standTrickDoor` makes the doorway of the lane
  the vault is shown behind (the middle one when unknown) a trick door **standing open**, so a party already walking in is never trapped; floor start shuts it with the others.
  Not carried over: a placeable door item and the left-hinge models.

## Checks

* Unit: the opening/closing curve and mid-motion reversal (ported tests), the pose matrix for the four facings, the wall/gap geometry of an opening.
* Live (rig, real client, owner's Samsung): the wall and door closed in a TRICK_OR_TREAT lane, the vote opening it, the swing, the gap passable, closing at the next floor, other lanes unchanged.

## As built (2026-10-30)

* Code: `com.cobbletowers.halloween.door` (`DoorMotion`, `DoorPose`, `DoorGeometry`, `DoorModels`, `DoorDisplay`, `TrickDoors`), wiring in `TowerDoors` (`chosen`, `resetAll`, `ensureOpen`,
  `standTrickDoor`), `RoomDresser` (install on a TRICK_OR_TREAT lane, uninstall otherwise), `TrickOrTreatService.tend`, `RunTransitionService` (`dropRun` on release), `CobbleTowers` (bootstrap),
  operator tool `/cobbletowers trickdoor list|open|close` (`TrickDoorCommand`). Art: `assets/cobbletowers/models/halloween/trick_door/` and `textures/item/halloween/trick_door/door.png`
  (the Hallowbound atlas, MIT, from the owner's standalone project).
* Tests: `DoorMotionTest`, `DoorGeometryTest`; the full suite and `ci_local.sh` pass.
* Live on the rig (real client, dev jar, `validation/trick_door_look.py`): the event room installs the door standing open, `trickdoor close/open` shut and open it, and the door shows in the wall gap
  with its sign. **Not yet judged**: the art close up and head-on, the swing as a sequence (the first frames were taken edge-on), the facing (sign toward the hall), the route-stop path (door
  starts shut, opens on the vote: no seed gives a TRICK_OR_TREAT lane on demand), a player standing in the gap when the floor starts.
* Capture lesson: a run whose window sat behind the owner's terminal photographed the terminal; `capture_window.ps1` and `capture_burst.ps1` now refuse unless the client is the foreground window.

## Grand door (2026-10-30)

The owner meant the **Grand 5 x 7** door (`hallowbound-grand-door-1.1.0.zip`), not the 1 x 2. A route opening (5 wide, 6 high) now gets it: the clear gap is 3 x 5 (`DoorGeometry.grand`), six displays (frame, frame glow, left, left glow, right, right glow, `DoorPose.grand`, models under `models/halloween/grand_door/`, 2048 atlas). The jambs and header are invisible **barrier**, not brick, so the frame model shows (brick hid it); the crown is a 7th row above the opening and is display only. A smaller opening keeps the 1 x 2 door in a brick wall. Looked at on the real client: shut, open, frame, sign facing (`trick-door-grand2`).
