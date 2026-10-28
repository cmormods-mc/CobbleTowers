"""Geometry of the Jack-o'-Lantern Vault (Halloween reward chest), organic pumpkin edition, in model units (16 per block).

One shared "vault space": x right (east), y up, z south, the vault's FRONT is the north face (-z), as in vanilla block models. Every
runtime piece is authored in this one space, so a piece's rest transform is the identity and the full static model is just the union.
The vault is a real pumpkin: about 22 units across (1.4 blocks), ribbed in 8 lobes, 23 units tall to the lid top and 28 with the stem, centred
on (x 8, z 8), floor y = 0. The footprint therefore leaves the one-block cell; model coordinates stay inside vanilla's -16..32.

How the round shape is made from vanilla cuboids (one axis, 0 / +-22.5 / +-45 degrees only): a RING is 16 STAVES, flat boxes laid tangent to
a circle every 22.5 degrees. A stave facing direction phi = 90 m + a is a box turned by a in {0, +-22.5, +-45} about its own centre, with its
long side along x (m even) or z (m odd), so all 16 directions need no rotation outside vanilla's limits. Rings are stacked with a bulging
radius profile (the pumpkin belly); every other stave stands 0.45 proud, which cuts the 8 vertical lobes and their grooves.

  body        ribbed bowl, charcoal rim band, straps, feet, body vines     (never moves)
  lid         domed lid, lip band, straps, stem and leaves, lid vines      (rotates about the hinge)
  face_plate  the carved jack-o'-lantern plate, three layers deep          (attached; presses in a hair)
  candy_fill  the stationary pile of wrapped candy inside                  (never moves)
  reveal_*    one floating wrapped candy, authored centred at (8, 8, 8) so a display entity spins it about its own middle

HINGE: axis parallel to x through (y = 13.5, z = 19.0), just behind the rim at the lid's farthest back point, so no part of the lid is ever behind it; two
charcoal hinge leaves bridge the gap; positive rotation about +x lifts the front.
"""

from __future__ import annotations

import math
import random
from dataclasses import dataclass, field

CX, CZ = 8.0, 8.0
RIM_TOP = 12.5                                 # the seam: the pumpkin is split horizontally here into two equal halves
LIP_R = 11.1                                   # outer radius of the iron hoop round the seam (both halves)
HINGE_Z = 19.5                                 # the farthest back the lid reaches (the hoop, 11.1, + chord overhang)
HINGE = (8.0, RIM_TOP, HINGE_Z)                # a point on the hinge axis (x is the middle; the axis runs along x)
LID_MAX_DEG = 115.0                            # final open angle, checked clear of the body over the whole sweep
PIECES = ("body", "lid", "face_plate", "candy_fill", "reveal_a", "reveal_b", "reveal_c")
FACES = ("north", "east", "south", "west", "up", "down")
FACES_ORDER = FACES
FRONT_PHI = 180.0                              # outward direction angle of the front (north, -z); phi = 0 is south (+z), 90 east


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


# ---------------------------------------------------------------------------------------------------------------------------------------
OUT_FACE = {0: "south", 1: "east", 2: "north", 3: "west"}
IN_FACE = {0: "north", 1: "west", 2: "south", 3: "east"}


def phi_parts(phi):
    """Direction angle (a multiple of 22.5) -> (m, a): quarter turns and the vanilla-legal residual rotation in {0, +-22.5, +-45}."""
    phi = phi % 360.0
    m = int(round(phi / 90.0)) % 4
    a = phi - 90.0 * round(phi / 90.0)
    if a > 45.0 or a < -45.0:
        raise ValueError(phi)
    return m, a


def stave(m: Model, piece, group, name, phi, r_out, y0, y1, thick, width, mat_out, mat_in, skip=(), mat_up=None, mat_down=None, mats_side=None):
    """One flat board whose outer face is at radius r_out in direction phi, `thick` deep (inward), `width` wide, y0..y1 tall."""
    mq, a = phi_parts(phi)
    rc = r_out - thick / 2.0
    cx = CX + rc * math.sin(math.radians(phi))
    cz = CZ + rc * math.cos(math.radians(phi))
    wx, wz = (width, thick) if mq % 2 == 0 else (thick, width)
    frm = (cx - wx / 2, y0, cz - wz / 2)
    to = (cx + wx / 2, y1, cz + wz / 2)
    mats = {OUT_FACE[mq]: mat_out, IN_FACE[mq]: mat_in}
    if mat_up:
        mats["up"] = mat_up
    if mat_down:
        mats["down"] = mat_down
    for f in ("north", "east", "south", "west"):
        if f not in mats:
            mats[f] = (mats_side or mat_out)
    rot = ("y", a, (cx, (y0 + y1) / 2, cz)) if a else None
    return m.box(piece, group, name, frm, to, mat_out, skip=tuple(skip), mats=mats, rot=rot)


def ring(m: Model, piece, group, k, n, y0, y1, r_out, thick, mat_out, mat_in, r_below, r_above, lobe=0.45, mat_top=None, mat_bottom=None,
          band=False):
    """Layer k of n: 16 staves at the given outer radius; even staves (every 45 degrees) stand `lobe` proud, odd ones sit `lobe` back."""
    chord = 2.0 * r_out * math.tan(math.radians(11.25)) + 0.3          # a little over, so the 22.5 degree turns leave no gap
    for i in range(16):
        phi = 22.5 * i
        bulge = (i % 2 == 0)
        d = (lobe if bulge else -lobe) if lobe else 0.0
        mo = mat_out if band else f"{mat_out}:{'o' if bulge else 'g'}:{k}:{n}"
        mq, _ = phi_parts(phi)
        keep = {OUT_FACE[mq], IN_FACE[mq]}
        if k == n - 1 or abs(r_above - r_out) > 0.05:
            keep.add("up")
        if k == 0 or abs(r_below - r_out) > 0.05:
            keep.add("down")
        if bulge and not band:                         # the two ends of a proud stave show against its recessed neighbours
            keep |= {"east", "west"} if mq % 2 == 0 else {"north", "south"}
        skip = [f for f in FACES if f not in keep]
        stave(m, piece, group, f"l{k}_{i}", phi, r_out + d, y0, y1, thick, chord, mo, mat_in, skip=skip,
              mat_up=mat_top, mat_down=mat_bottom, mats_side=mo)


def disc(m: Model, piece, group, name, y0, y1, radius, mat, skip=()):
    """A solid 16-gon plate from four squares turned 0 / 22.5 / 45 / -22.5 (= 67.5) degrees: the union is a round-ish disc."""
    h = radius / 1.2
    for i, a in enumerate((0.0, 22.5, 45.0, -22.5)):
        off = i * 0.02
        rot = ("y", a, (CX, (y0 + y1) / 2, CZ)) if a else None
        m.box(piece, group, f"{name}{i}", (CX - h, y0 + off, CZ - h), (CX + h, y1 + off, CZ + h), mat, skip=tuple(skip), rot=rot)


# body -----------------------------------------------------------------------------------------------------------------------------------
def _lower(t):
    """Outer radius of the lower half at height fraction t (0 floor ring, 1 equator): a quarter sine, so the belly meets the seam vertically."""
    return 6.3 + 4.6 * math.sin(math.pi / 2 * t)


def _upper(t):
    """Outer radius of the upper half (0 equator, 1 top): a quarter cosine, the mirror of the lower half but closing to the stem."""
    return 3.4 + 7.5 * math.cos(math.pi / 2 * t ** 1.25)


BODY_Y0, LAYER_H, WALL = 1.5, 0.5, 1.6
N_BODY, N_BAND = 22, 2                                        # 22 half-unit layers from y 1.5 to the seam; the top two are the iron hoop
BODY_R = [round(_lower((k + 0.5) / N_BODY), 2) if k < N_BODY - N_BAND else LIP_R for k in range(N_BODY)]
N_LID = 22
LID_H = [0.5] * N_LID
LID_R = [LIP_R if k < N_BAND else round(_upper((k + 0.5) / N_LID), 2) for k in range(N_LID)]
LID_TOP = RIM_TOP + sum(LID_H)                               # a cap plate closes the dome
LOBE = 0.3


def _body(m: Model):
    P, G = "body", "shell"
    n = len(BODY_R)
    for k, r in enumerate(BODY_R):
        y0 = BODY_Y0 + k * LAYER_H
        below = BODY_R[k - 1] if k else r
        above = BODY_R[k + 1] if k < n - 1 else r
        band = (k >= n - N_BAND)
        ring(m, P, G, k, n, y0, y0 + LAYER_H, r, WALL, "charcoal" if band else "pumpkin", "flesh_lit" if not band else "charcoal",
             below, above, lobe=0.0 if band else LOBE, mat_top="charcoal" if band else "pumpkin_top", mat_bottom="pumpkin_bottom", band=band)
    # the floor of the cavity, three stepped discs (the bowl narrows toward the bottom): the candy sits on them
    disc(m, P, "floor", "d0", 1.5, 3.0, 4.9, "flesh", skip=("up", "north", "south", "east", "west"))
    # four stubby charcoal feet turned 45 degrees under the belly
    for i, phi in enumerate((45, 135, 225, 315)):
        cx, cz = CX + 5.2 * math.sin(math.radians(phi)), CZ + 5.2 * math.cos(math.radians(phi))
        m.box(P, "feet", f"f{i}", (cx - 1.6, 0.0, cz - 1.6), (cx + 1.6, 1.6, cz + 1.6), "charcoal_lit", skip=("up",), rot=("y", 45.0, (cx, 0.8, cz)))
    # two iron straps climb the front belly either side of the face (the lid carries the same two on over the dome)
    for sgn, tag in ((-1, "l"), (1, "r")):
        phi = FRONT_PHI + sgn * 45.0
        for k in range(1, n - N_BAND):
            y0 = BODY_Y0 + k * LAYER_H
            stave(m, P, "straps", f"{tag}{k}", phi, BODY_R[k] + LOBE + 0.8, y0, y0 + LAYER_H, 0.9, 2.2, "charcoal", "charcoal", skip=("up", "down"))
        stave(m, P, "straps", f"{tag}_stud", phi, BODY_R[10] + LOBE + 0.8 + 0.4, 6.0, 7.4, 0.4, 1.4, "charcoal_lit", "charcoal_lit", skip=("up", "down"))
    for i, x in enumerate((CX - 4.5, CX + 4.5)):          # the body halves of the two hinge leaves
        m.box(P, "hinge", f"body{i}", (x - 1.0, RIM_TOP - 1.8, 18.0), (x + 1.0, RIM_TOP, HINGE_Z + 0.4), "charcoal_lit", skip=("up",))
    _body_vines(m)


def _body_vines(m: Model):
    P, G = "body", "vines_body"
    # a vine climbs the belly on the viewer's right of the front, from the ground bulge up to just under the rim band, turning 22.5 degrees every
    # few layers so it spirals; leaves are wider flat boards on top of it
    for j, k in enumerate(range(1, N_BODY - N_BAND)):
        phi = 270.0 - 22.5 * (j // 7)
        y0 = BODY_Y0 + k * LAYER_H
        stave(m, P, G, f"v{j}", phi, BODY_R[k] + LOBE + 0.3, y0, y0 + LAYER_H, 0.3, 1.0, "vine" if j % 3 else "vine_lit", "vine", skip=("up", "down"))
    stave(m, P, G, "leaf_a", 247.5, BODY_R[8] + 0.9, 5.4, 7.0, 0.3, 2.0, "leaf", "leaf", skip=("up", "down"))
    stave(m, P, G, "leaf_b", 270.0, BODY_R[16] + 0.9, 9.0, 10.5, 0.3, 1.8, "leaf", "leaf", skip=("up", "down"))


# face plate: an iron lock plate with the jack-o'-lantern carved into it --------------------------------------------------------------------------
PLATE_MASK = [          # 11 columns x 13 rows, one cell = CELL units; '#' solid, '.' open (shows the dark backing): two angled eyes over a keyhole
    " ######### ",
    "###########",
    "#..#####..#",
    "##..###..##",
    "###########",
    "####...####",
    "###.....###",
    "###.....###",
    "####...####",
    "####...####",
    "###.....###",
    "###########",
    " ######### ",
]
CELL = 0.6
PLATE_X0, PLATE_TOP = CX - 11 * CELL / 2, 10.6
# front is -z. Layers: the dark backing (the candle inside), the iron bezel (0.3 wider than the face, so the silhouette and every hole step down
# in two stages), the raised pumpkin face, and four rivets. Behind the backing a charcoal mount follows the belly so the plate does not float.
PLATE_Z_FRONT, PLATE_Z_STEP, PLATE_Z_BODY, PLATE_Z_BACK = -4.6, -3.8, -3.0, -2.2


def _runs(m, rows, z0, z1, mat_north, mat_all, tag, grow=0.0):
    h, w = len(rows), len(rows[0])
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
            if not grow:
                if r > 0 and all(rows[r - 1][c0:c]):
                    skip.append("up")
                if r < h - 1 and all(rows[r + 1][c0:c]):
                    skip.append("down")
            m.box("face_plate", "plate", f"{tag}{r}_{c0}", (PLATE_X0 + c0 * CELL - grow, y1 - CELL - grow, z0),
                  (PLATE_X0 + c * CELL + grow, y1 + grow, z1), mat_all, skip=tuple(skip), mats={"north": mat_north})


def _plate(m: Model):
    rows = [[c == "#" for c in r] for r in PLATE_MASK]
    h, w = len(rows), len(rows[0])
    m.box("face_plate", "plate", "backing", (PLATE_X0, PLATE_TOP - h * CELL, PLATE_Z_BODY), (PLATE_X0 + w * CELL, PLATE_TOP, PLATE_Z_BACK),
          "plate_back", skip=("south", "up", "down", "east", "west"))
    _runs(m, rows, PLATE_Z_STEP, PLATE_Z_BODY, "charcoal_lit", "charcoal", "b", grow=0.3)          # the iron bezel
    _runs(m, rows, PLATE_Z_FRONT, PLATE_Z_STEP, "plate_face", "plate", "f")                         # the raised pumpkin face
    for i, (dx, dy) in enumerate(((-1, 1), (1, 1), (-1, -1), (1, -1))):                              # four rivets on the bezel corners
        cx, cy = CX + dx * (w * CELL / 2 + 0.05), PLATE_TOP - h * CELL / 2 + dy * (h * CELL / 2 + 0.05)
        m.box("face_plate", "plate", f"rivet{i}", (cx - 0.5, cy - 0.5, PLATE_Z_STEP - 0.5), (cx + 0.5, cy + 0.5, PLATE_Z_STEP), "charcoal_lit", skip=("south",))
    # the mount: charcoal blocks from the backing back into the belly, one per half-unit row, so the lock sits on the pumpkin rather than hovering
    y = PLATE_TOP - h * CELL
    while y < PLATE_TOP - 0.01:
        k = min(N_BODY - N_BAND - 1, max(0, int((y + 0.25 - BODY_Y0) / LAYER_H)))
        z_in = CZ - (BODY_R[k] - 1.0)
        if z_in < PLATE_Z_BACK - 0.05:
            m.box("face_plate", "mount", f"m{int(round(y * 10))}", (CX - 3.0, y, z_in), (CX + 3.0, min(y + 0.6, PLATE_TOP), PLATE_Z_BACK), "charcoal",
                  skip=("south", "up", "down", "east", "west"))
        y += 0.6


# lid ----------------------------------------------------------------------------------------------------------------------------------
def _lid(m: Model):
    P, G = "lid", "dome"
    n = len(LID_R)
    y = RIM_TOP
    ys = []
    for k, r in enumerate(LID_R):
        y0, y1 = y, y + LID_H[k]
        ys.append((y0, y1))
        below = LID_R[k - 1] if k else r
        above = LID_R[k + 1] if k < n - 1 else r
        band = (k == 0)
        thick = min(WALL, r - 0.6)
        ring(m, P, G, k, n, y0, y1, r, thick, "charcoal" if band else "pumpkin_lid", "charcoal" if band else "flesh_lit", below, above,
             lobe=0.0 if band else LOBE, mat_top="pumpkin_top", mat_bottom="charcoal" if band else "flesh_lit", band=band)
        y = y1
    # a ceiling under the narrow top, and the cap that closes the dome
    kc = next(k for k, r in enumerate(LID_R) if r < 6.6)
    disc(m, P, "dome_fill", "ceil", ys[kc][0], ys[kc][0] + 0.8, LID_R[kc] - WALL + 0.5, "flesh_lit", skip=("north", "south", "east", "west", "up"))
    disc(m, P, "dome_fill", "cap", LID_TOP, LID_TOP + 0.8, 3.2, "pumpkin_top", skip=("down",))
    # the straps run on from the body straps over the dome: boards per layer (stepped like the dome)
    for sgn, tag in ((-1, "l"), (1, "r")):
        phi = FRONT_PHI + sgn * 45.0
        for k in range(N_BAND, n):
            y0, y1 = ys[k]
            stave(m, P, "straps", f"{tag}{k}", phi, LID_R[k] + LOBE + 0.7, y0, y1, 0.8, 2.0, "charcoal", "charcoal", skip=("up", "down"))
        stave(m, P, "straps", f"{tag}_stud", phi, LID_R[8] + LOBE + 0.7 + 0.4, ys[8][0] + 0.1, ys[8][1] + 0.9, 0.4, 1.2, "charcoal_lit", "charcoal_lit",
              skip=("up", "down"))
    for i, x in enumerate((CX - 4.5, CX + 4.5)):          # the lid halves of the hinge leaves: all behind the axis' front, none beyond it
        m.box(P, "hinge", f"lid{i}", (x - 1.0, RIM_TOP, 18.0), (x + 1.0, RIM_TOP + 1.8, HINGE_Z), "charcoal_lit", skip=("down",))
    # the stem: a small blocky green stem with a bent tip and two leaf pads
    s0 = LID_TOP + 0.8
    m.box(P, "stem", "base", (CX - 1.6, s0, CZ - 1.6), (CX + 1.6, s0 + 2.2, CZ + 1.6), "stem")
    m.box(P, "stem", "mid", (CX - 1.1, s0 + 2.2, CZ - 1.1), (CX + 1.1, s0 + 3.4, CZ + 1.1), "stem")
    m.box(P, "stem", "tip", (CX - 0.4, s0 + 3.4, CZ - 1.0), (CX + 2.0, s0 + 4.2, CZ + 1.4), "stem_lit")
    m.box(P, "stem", "leaf_a", (CX + 1.6, s0, CZ - 4.2), (CX + 4.4, s0 + 0.8, CZ - 0.8), "leaf", rot=("y", 22.5, (CX + 3.0, s0 + 0.4, CZ - 2.5)))
    m.box(P, "stem", "leaf_b", (CX - 4.6, s0, CZ + 0.6), (CX - 1.4, s0 + 0.8, CZ + 3.6), "leaf", rot=("y", -22.5, (CX - 3.0, s0 + 0.4, CZ + 2.1)))
    # lid vines: a spiral up the dome from the second layer (never the lip band, so none bridges the seam)
    for j, k in enumerate(range(N_BAND + 1, N_BAND + 15)):
        y0, y1 = ys[k]
        stave(m, P, "vines_lid", f"v{j}", 112.5 + 22.5 * (j // 5), LID_R[k] + LOBE + 0.3, y0, y1, 0.3, 1.0, "vine" if j % 2 else "vine_lit", "vine", skip=("up", "down"))
    stave(m, P, "vines_lid", "leaf_a", 135.0, LID_R[9] + 0.9, ys[9][0] + 0.1, ys[9][1] + 1.2, 0.3, 1.8, "leaf", "leaf", skip=("up", "down"))


# candy ----------------------------------------------------------------------------------------------------------------------------------
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
    # the pile's body: three stepped slabs following the narrowing bowl, up to y = 7.8; wrapped candies heap on it to about y = 13
    disc(m, P, "pile", "s0", 3.0, 4.5, 4.8, "candy_mix", skip=("north", "south", "east", "west", "down"))
    disc(m, P, "pile", "s1", 4.5, 6.0, 6.6, "candy_mix", skip=("north", "south", "east", "west", "down"))
    disc(m, P, "pile", "s2", 6.0, 7.8, 7.2, "candy_mix", skip=("down",))
    rnd = random.Random(1031)
    kinds = list(CANDY_KINDS)
    spots = []
    for i in range(6):                                   # outer ring, lowest
        a = math.radians(30 + 60 * i)
        spots.append((CX + 4.6 * math.sin(a), 8.8, CZ + 4.6 * math.cos(a)))
    for i in range(5):                                   # middle ring
        a = math.radians(10 + 72 * i)
        spots.append((CX + 3.0 * math.sin(a), 10.0, CZ + 3.0 * math.cos(a)))
    spots += [(CX + 0.6, 11.2, CZ - 0.4), (CX - 1.0, 12.0, CZ + 0.9)]
    for i, (x, y, z) in enumerate(spots):
        candy(m, P, "candies", f"c{i}", (x, y, z), kinds[(i * 3) % len(kinds)], rnd.choice(("x", "z")), rnd.choice((0.0, 22.5, -22.5, 45.0, -45.0)))


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
    return m
