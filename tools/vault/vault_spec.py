"""Geometry of the Jack-o'-Lantern Vault (Halloween reward chest), in model units (16 per block).

One shared "vault space": x right (east), y up, z south, the vault's FRONT is the north face (-z), as in vanilla block models. Every
runtime piece is authored in this one space, so a piece's rest transform is the identity and the full static model is just the union.
The footprint is x 0..16, z 1..15 (13 deep at the posts), y 0..17.6 with the stem; the floor is y = 0.

  body        shell, posts, feet, rim band, body vines          (never moves)
  lid         stepped lid, straps, stem, lid vines               (rotates about the hinge)
  face_plate  the carved jack-o'-lantern plate                   (attached; presses in a hair)
  candy_fill  the stationary pile of wrapped candy inside        (never moves)
  reveal_*    one floating wrapped candy, authored centred at (8, 8, 8) so a display entity spins it about its own middle

HINGE: axis parallel to x through (y = 9, z = 14), the back-top edge of the body rim; positive rotation about +x lifts the front.
"""

from __future__ import annotations

import random
from dataclasses import dataclass, field

HINGE = (8.0, 9.0, 14.0)        # a point on the hinge axis (x is the middle; the axis runs along x)
LID_MAX_DEG = 115.0              # final open angle, see docs: clear of every post and the rim over the whole sweep
PIECES = ("body", "lid", "face_plate", "candy_fill", "reveal_a", "reveal_b", "reveal_c")
FACES = ("north", "east", "south", "west", "up", "down")
FACES_ORDER = FACES


@dataclass
class El:
    name: str
    frm: tuple
    to: tuple
    mat: str = "pumpkin"
    mats: dict = field(default_factory=dict)     # per-face material overrides
    skip: tuple = ()                             # faces not emitted (buried against another element)
    rot: tuple | None = None                     # (axis, angle, origin); vanilla: one axis, multiples of 22.5, |angle| <= 45
    group: str = ""
    piece: str = ""

    def size(self):
        return tuple(self.to[i] - self.frm[i] for i in range(3))

    def material(self, face):
        return self.mats.get(face, self.mat)


class Model:
    def __init__(self):
        self.els: list[El] = []

    def box(self, piece, group, name, frm, to, mat="pumpkin", skip=(), mats=None, rot=None):
        e = El(f"{group}.{name}" if group else name, tuple(float(v) for v in frm), tuple(float(v) for v in to), mat, dict(mats or {}),
               tuple(skip), rot, group, piece)
        self.els.append(e)
        return e

    def piece(self, name):
        return [e for e in self.els if e.piece == name]


RIM_TOP = 9.0        # top of the body rim band: the lid sits here and the hinge axis runs along its back edge
POST_TOP = 13.0      # corner posts rise 4 above the rim; the lid, between them, has its flat top level with the post caps


def _body(m: Model):
    P = "body"
    flesh = "flesh"
    # shell: four walls 1 unit thick, y 2..9, x 2..14, z 2.5..13.5; the cavity is x 3..13, z 3.5..12.5, y 3..9
    m.box(P, "shell", "front", (2, 2, 2.5), (14, 9, 3.5), "pumpkin", skip=("up",), mats={"south": flesh})
    m.box(P, "shell", "back", (2, 2, 12.5), (14, 9, 13.5), "pumpkin", skip=("up",), mats={"north": flesh})
    m.box(P, "shell", "left", (2, 2, 3.5), (3, 9, 12.5), "pumpkin", skip=("up", "north", "south"), mats={"east": flesh})
    m.box(P, "shell", "right", (13, 2, 3.5), (14, 9, 12.5), "pumpkin", skip=("up", "north", "south"), mats={"west": flesh})
    m.box(P, "shell", "floor", (3, 2, 3.5), (13, 3, 12.5), flesh, skip=("north", "south", "east", "west", "down"))
    # base plate: the dark plinth the shell stands on, 0.5 proud of the walls
    m.box(P, "base", "plinth", (1.5, 1, 2), (14.5, 2.25, 14), "charcoal")
    # rim band: the dark strap round the top of the body (the lid sits on it)
    m.box(P, "rim", "front", (1.5, 7.5, 2), (14.5, 9, 3.5), "charcoal")
    m.box(P, "rim", "back", (1.5, 7.5, 12.5), (14.5, 9, 14), "charcoal")
    m.box(P, "rim", "left", (1.5, 7.5, 3.5), (3, 9, 12.5), "charcoal", skip=("north", "south"))
    m.box(P, "rim", "right", (13, 7.5, 3.5), (14.5, 9, 12.5), "charcoal", skip=("north", "south"))
    # corner posts 2.5 x 2.5, rising 4 above the rim, a 1.5 x 1.5 cap nub, a flat foot 3.5 x 3.5 x 2.25, rivet studs on the outer faces
    for tag, x0, z0 in (("fl", 0, 1.5), ("fr", 13.5, 1.5), ("bl", 0, 12), ("br", 13.5, 12)):
        m.box(P, f"post_{tag}", "post", (x0, 2.25, z0), (x0 + 2.5, POST_TOP, z0 + 2.5), "charcoal", skip=("down",))
        m.box(P, f"post_{tag}", "cap", (x0 + 0.5, POST_TOP, z0 + 0.5), (x0 + 2.0, POST_TOP + 0.5, z0 + 2.0), "charcoal_lit", skip=("down",))
        m.box(P, f"post_{tag}", "foot", (0 if x0 == 0 else 12.5, 0, z0 - 0.5), (3.5 if x0 == 0 else 16, 2.25, z0 + 3), "charcoal_lit")
    m.box(P, "post_fl", "stud_front", (0.75, 9.75, 1.0), (1.75, 10.75, 1.5), "charcoal_lit", skip=("south",))
    m.box(P, "post_fr", "stud_front", (14.25, 9.75, 1.0), (15.25, 10.75, 1.5), "charcoal_lit", skip=("south",))
    m.box(P, "post_bl", "stud_back", (0.75, 9.75, 14.5), (1.75, 10.75, 15.0), "charcoal_lit", skip=("north",))
    m.box(P, "post_br", "stud_back", (14.25, 9.75, 14.5), (15.25, 10.75, 15.0), "charcoal_lit", skip=("north",))
    m.box(P, "post_fl", "stud_side", (-0.5, 9.75, 2.25), (0, 10.75, 3.25), "charcoal_lit", skip=("east",))
    m.box(P, "post_bl", "stud_side", (-0.5, 9.75, 12.75), (0, 10.75, 13.75), "charcoal_lit", skip=("east",))
    m.box(P, "post_fr", "stud_side", (16, 9.75, 2.25), (16.5, 10.75, 3.25), "charcoal_lit", skip=("west",))
    m.box(P, "post_br", "stud_side", (16, 9.75, 12.75), (16.5, 10.75, 13.75), "charcoal_lit", skip=("west",))
    _body_vines(m)


def _vine_run(m, piece, group, prefix, pts, axis_face, width=1.0):
    """A meandering vine laid on a flat face as short boxes `width` wide and 0.25 proud. `pts` are (a, b) pairs on the face plane;
    consecutive points differ in one axis only (a staircase), which keeps every box axis-aligned."""
    face, depth = axis_face
    for i, ((a0, b0), (a1, b1)) in enumerate(zip(pts, pts[1:])):
        lo_a, hi_a = min(a0, a1), max(a0, a1) + width
        lo_b, hi_b = min(b0, b1), max(b0, b1) + width
        mat = "vine" if i % 3 else "vine_lit"
        if face == "east":                                      # a = z, b = y on the x = depth plane (outward +x)
            m.box(piece, group, f"{prefix}{i}", (depth, lo_b, lo_a), (depth + 0.25, hi_b, hi_a), mat)
        elif face == "north":                                   # a = x, b = y on the z = depth plane (outward -z)
            m.box(piece, group, f"{prefix}{i}", (lo_a, lo_b, depth - 0.25), (hi_a, hi_b, depth), mat)
        elif face == "up":                                      # a = x, b = z on the y = depth plane
            m.box(piece, group, f"{prefix}{i}", (lo_a, depth, lo_b), (hi_a, depth + 0.25, hi_b), mat)


def _leaf(m, piece, group, name, frm, to):
    m.box(piece, group, name, frm, to, "leaf")


def _body_vines(m: Model):
    # authored on the +x side and mirrored to the viewer's right (-x) in build()
    P, G = "body", "vines_body"
    # side panel (x = 13.5 wall, between the posts): a vine climbing from the floor to just under the rim band
    pts = [(7.5, 3.0), (7.5, 4.0), (8.5, 4.0), (8.5, 5.0), (7.5, 5.0), (7.5, 6.0), (8.5, 6.0)]
    _vine_run(m, P, G, "side", pts, ("east", 14.0))
    _leaf(m, P, G, "side_leaf_a", (14.0, 4.0, 9.5), (14.25, 5.0, 10.5))
    _leaf(m, P, G, "side_leaf_b", (14.0, 5.5, 5.75), (14.25, 6.5, 6.75))
    # front-right post, outer face (x = 16): a shorter vine, kept clear of the stud
    pts = [(2.25, 3.0), (2.25, 4.0), (3.25, 4.0), (3.25, 5.5), (2.25, 5.5), (2.25, 6.5)]
    _vine_run(m, P, G, "post", pts, ("east", 16.0))
    _leaf(m, P, G, "post_leaf", (16.0, 3.5, 3.75), (16.25, 4.5, 4.5))
    # a short drape on the front panel, beside the plate
    pts = [(11.75, 3.0), (11.75, 4.0), (12.25, 4.0), (12.25, 5.25)]
    _vine_run(m, P, G, "front", pts, ("north", 2.5))
    _leaf(m, P, G, "front_leaf", (11.0, 3.0, 2.25), (11.75, 3.75, 2.5))


# face plate -------------------------------------------------------------------------------------------------------------------------
PLATE_MASK = [          # 11 columns x 12 rows, one cell = 0.5 unit; '#' orange, '.' open (shows the dark backing)
    " ######### ",
    "###########",
    "#...###...#",
    "#...###...#",
    "##.#####.##",
    "###########",
    "###########",
    "#..#...#..#",
    "#.........#",
    "##..#.#..##",
    "###########",
    " ######### ",
]
PLATE_X0, PLATE_TOP, CELL = 5.25, 8.75, 0.5
PLATE_Z_FRONT, PLATE_Z_MID, PLATE_Z_BACK = 1.2, 1.8, 2.5


def _plate(m: Model):
    P = "face_plate"
    rows = [[c == "#" for c in r] for r in PLATE_MASK]
    h, w = len(rows), len(rows[0])
    m.box(P, "plate", "backing", (PLATE_X0, PLATE_TOP - h * CELL, PLATE_Z_MID), (PLATE_X0 + w * CELL, PLATE_TOP, PLATE_Z_BACK), "plate_back",
          skip=("south", "up", "down", "east", "west"))
    for r, row in enumerate(rows):
        c = 0
        while c < w:
            if not row[c]:
                c += 1
                continue
            c0 = c
            while c < w and row[c]:
                c += 1
            y1 = PLATE_TOP - r * CELL
            skip = ["south"]
            if r > 0 and all(rows[r - 1][c0:c]):
                skip.append("up")
            if r < h - 1 and all(rows[r + 1][c0:c]):
                skip.append("down")
            m.box(P, "plate", f"r{r}_{c0}", (PLATE_X0 + c0 * CELL, y1 - CELL, PLATE_Z_FRONT), (PLATE_X0 + c * CELL, y1, PLATE_Z_MID), "plate",
                  skip=tuple(skip), mats={"north": "plate_face"})


# lid ----------------------------------------------------------------------------------------------------------------------------------
def _lid(m: Model):
    P = "lid"
    flesh = "flesh_lit"
    # tier A: a hollow skirt, x 2.5..13.5 (between the posts), z 2..14, y 9..12, walls 1 thick, a ceiling at y 11..12
    m.box(P, "skirt", "front", (2.5, 9, 2), (13.5, 12, 3), "pumpkin", mats={"south": flesh, "down": "charcoal"})
    m.box(P, "skirt", "back", (2.5, 9, 13), (13.5, 12, 14), "pumpkin", mats={"north": flesh, "down": "charcoal"})
    m.box(P, "skirt", "left", (2.5, 9, 3), (3.5, 12, 13), "pumpkin", skip=("north", "south"), mats={"east": flesh, "down": "charcoal"})
    m.box(P, "skirt", "right", (12.5, 9, 3), (13.5, 12, 13), "pumpkin", skip=("north", "south"), mats={"west": flesh, "down": "charcoal"})
    m.box(P, "skirt", "ceiling", (3.5, 11, 3), (12.5, 12, 13), flesh, skip=("north", "south", "east", "west", "up"))
    # tiers B and C step in; the skirt's bottom edge is a dark charcoal frame round the flesh-coloured ceiling
    m.box(P, "tiers", "b", (3.25, 12, 2.75), (12.75, 13.25, 13.25), "pumpkin", skip=("down",))
    m.box(P, "tiers", "c", (4.5, 13.25, 4), (11.5, 14.25, 12), "pumpkin_lit", skip=("down",))
    # straps: two dark bands front to back over tiers C and B, 2 wide, 0.25 proud (they do not wrap the front face of the skirt)
    for tag, x0 in (("a", 5.0), ("b", 9.0)):
        g = f"strap_{tag}"
        m.box(P, g, "top", (x0, 14.25, 4), (x0 + 2, 14.5, 12), "charcoal", skip=("down",))
        m.box(P, g, "front_riser", (x0, 13.25, 3.75), (x0 + 2, 14.5, 4), "charcoal", skip=("down", "south"))
        m.box(P, g, "back_riser", (x0, 13.25, 12), (x0 + 2, 14.5, 12.25), "charcoal", skip=("down", "north"))
        m.box(P, g, "front_flat", (x0, 13.25, 2.75), (x0 + 2, 13.5, 3.75), "charcoal", skip=("down", "south"))
        m.box(P, g, "back_flat", (x0, 13.25, 12.25), (x0 + 2, 13.5, 13.25), "charcoal", skip=("down", "north"))
    # stem: a small blocky green stem with a bent tip and two leaf pads
    m.box(P, "stem", "base", (6.5, 14.25, 6.5), (9.5, 16.25, 9.5), "stem")
    m.box(P, "stem", "mid", (7.0, 16.25, 7.0), (9.0, 17.25, 9.0), "stem")
    m.box(P, "stem", "tip", (8.0, 17.25, 7.5), (10.0, 18.0, 9.25), "stem_lit")
    m.box(P, "stem", "leaf_a", (9.5, 14.25, 5.0), (11.5, 15.0, 7.0), "leaf")
    m.box(P, "stem", "leaf_b", (4.5, 14.25, 8.5), (6.5, 15.0, 10.25), "leaf")
    # lid vines: on the lid only (nothing reaches below y = 9, and none crosses the seam onto the body)
    G = "vines_lid"
    pts = [(3.5, 2.75), (4.0, 2.75), (4.0, 3.75), (4.75, 3.75)]
    _vine_run(m, P, G, "top_front", pts, ("up", 13.25))
    pts = [(10.75, 5.0), (11.5, 5.0), (11.5, 6.5), (10.75, 6.5), (10.75, 8.0)]
    _vine_run(m, P, G, "top_right", pts, ("up", 13.25))
    pts = [(3.5, 11.5), (3.5, 10.5), (4.25, 10.5), (4.25, 9.75)]
    _vine_run(m, P, G, "front_drape", pts, ("north", 2.0))
    pts = [(4.5, 11.5), (4.5, 10.5), (5.5, 10.5), (5.5, 9.75)]
    _vine_run(m, P, G, "right_drape", pts, ("east", 13.5))
    _leaf(m, P, G, "leaf_a", (3.0, 13.25, 3.25), (4.0, 13.5, 4.25))
    _leaf(m, P, G, "leaf_b", (10.0, 13.25, 6.75), (11.0, 13.5, 7.75))


# candy ----------------------------------------------------------------------------------------------------------------------------
CANDY_KINDS = ("candy_red", "candy_pink", "candy_purple", "candy_green", "candy_yellow", "candy_orange", "candy_blue")


def candy(m: Model, piece, group, name, centre, kind, along="x", yaw=0.0, scale=1.0):
    """A wrapped candy: a chunky body with a flat twist fin at each end, length along x or z, yawed about its own centre."""
    cx, cy, cz = centre
    L, H = 3.0 * scale, 1.75 * scale
    fin = 0.875 * scale
    rot = ("y", yaw, (cx, cy, cz)) if yaw else None
    wrap = f"{kind}_wrap"
    if along == "x":
        m.box(piece, group, f"{name}_body", (cx - L / 2, cy - H / 2, cz - H / 2), (cx + L / 2, cy + H / 2, cz + H / 2), kind, rot=rot)
        for s, tag in ((-1, "a"), (1, "b")):
            x0 = cx + L / 2 if s > 0 else cx - L / 2 - fin
            m.box(piece, group, f"{name}_fin_{tag}", (x0, cy - H * 0.5, cz - H * 0.17), (x0 + fin, cy + H * 0.5, cz + H * 0.17), wrap, rot=rot)
    else:
        m.box(piece, group, f"{name}_body", (cx - H / 2, cy - H / 2, cz - L / 2), (cx + H / 2, cy + H / 2, cz + L / 2), kind, rot=rot)
        for s, tag in ((-1, "a"), (1, "b")):
            z0 = cz + L / 2 if s > 0 else cz - L / 2 - fin
            m.box(piece, group, f"{name}_fin_{tag}", (cx - H * 0.17, cy - H * 0.5, z0), (cx + H * 0.17, cy + H * 0.5, z0 + fin), wrap, rot=rot)


def _candy_fill(m: Model):
    P = "candy_fill"
    # the pile's body: a mixed-candy slab up to y = 7.2, the wrapped candies heap on top to about y = 10.3 (the rim top is 9; the heap stands proud of it, inside the lid void)
    m.box(P, "pile", "base", (3.1, 3, 3.6), (12.9, 7.2, 12.4), "candy_mix", skip=("down",))
    # (x, y, z, along, yaw): centres chosen so every rotated candy stays inside the cavity x/z 3.5..12.5 (checked in verify_vault.py)
    spots = [
        (6.2, 8.1, 5.9, "x", 0), (9.6, 8.1, 5.8, "x", 22.5),
        (5.3, 8.2, 8.9, "z", 0), (10.7, 8.1, 9.0, "z", -22.5),
        (6.9, 8.2, 11.0, "x", -22.5), (9.9, 8.1, 11.0, "x", 22.5),
        (8.0, 8.8, 8.4, "x", 22.5), (6.6, 9.0, 7.4, "z", 22.5), (9.4, 8.9, 7.2, "z", -22.5),
        (7.2, 9.2, 9.7, "x", 0), (9.0, 9.0, 9.6, "x", -22.5), (8.0, 9.5, 6.4, "x", 0),
    ]
    kinds = ["candy_red", "candy_pink", "candy_purple", "candy_green", "candy_yellow", "candy_orange", "candy_blue",
             "candy_red", "candy_green", "candy_pink", "candy_orange", "candy_purple"]
    for i, (x, y, z, along, yaw) in enumerate(spots):
        candy(m, P, "candies", f"c{i}", (x, y, z), kinds[i], along, yaw)


def _reveals(m: Model):
    for tag, kind in (("a", "candy_red"), ("b", "candy_purple"), ("c", "candy_yellow")):
        candy(m, f"reveal_{tag}", "reveal", "candy", (8, 8, 8), kind, "x", 0.0, 1.0)


def build() -> Model:
    m = Model()
    _body(m)
    _plate(m)
    _lid(m)
    _candy_fill(m)
    _reveals(m)
    # The vines were laid out on the +x (east) side; the concept art has them on the viewer's right as the front is seen, which is the
    # west (-x) side. Mirror the vine elements across x = 8 (they are symmetric boxes, so only the extents change).
    for e in m.els:
        if e.group.startswith("vines"):
            e.frm, e.to = (16 - e.to[0], e.frm[1], e.frm[2]), (16 - e.frm[0], e.to[1], e.to[2])
    return m
