"""Animation spec, state machine and transform maths of the Jack-o'-Lantern Vault: the Python reference implementation.

The runtime (Java, com.cobbletowers.halloween.vault) reads the manifest this module writes and must reproduce these numbers; the
Java tests load the golden table written next to the manifest and compare every tick. Blockbench animations are generated from the same
keyframes purely as a preview.

Time is in server ticks (20 per second); a track is a list of (tick, value, ease) keys, `ease` shapes the segment that STARTS at that key.
"""
from __future__ import annotations

import math

import numpy as np

import vault_spec as S

TPS = 20
LID_OPEN = S.LID_MAX_DEG          # the angle the lid settles at (peak overshoot is OVERSHOOT_DEG above it)
OVERSHOOT_DEG = 3.0
PRESS_UNITS = 0.6                 # the face plate's latch press, model units (0.0375 block)
HOP = 1.5                         # the few degrees the latch pops the lid
ORIGIN_UNITS = (8.0, 0.0, 8.0)    # vault root: the middle of the footprint on the floor

EASES = {
    "linear": lambda t: t,
    "in_quad": lambda t: t * t,
    "out_quad": lambda t: 1 - (1 - t) * (1 - t),
    "in_cubic": lambda t: t * t * t,
    "out_cubic": lambda t: 1 - (1 - t) ** 3,
    "in_out_sine": lambda t: -(math.cos(math.pi * t) - 1) / 2,
}


def _shake(amp, n):
    """Alternating +-amp decaying to 0 over n keys, one per tick: the vault trembling."""
    keys, sign = [], 1
    for i in range(n + 1):
        a = amp * (1 - i / n) ** 0.7 if 0 < i < n else 0.0
        keys.append([i, round(sign * a, 4), "linear"])
        sign = -sign
    return keys


SEQUENCES = {
    "closed_idle": {
        "ticks": 40, "loop": True,
        "tracks": {"lid.rot_x_deg": [[0, 0.0, "in_out_sine"], [20, 0.35, "in_out_sine"], [40, 0.0, "linear"]]},
        "note": "A barely visible breath: the lid lifts 0.35 degrees and settles. First and last frame are the closed pose.",
    },
    "anticipation": {
        "ticks": 10, "loop": False,
        "tracks": {
            "lid.rot_x_deg": [[0, 0.0, "out_quad"], [2, 1.6, "in_quad"], [4, 0.2, "out_quad"], [6, 2.2, "in_quad"], [8, 0.3, "out_quad"],
                              [9, 1.0, "in_quad"], [10, 0.0, "linear"]],
            "root.x_units": _shake(0.22, 10),
        },
        "note": "The whole vault trembles (root x shake) while the lid rattles. Ends exactly closed.",
    },
    "unlock": {
        "ticks": 5, "loop": False,
        "tracks": {
            "face_plate.press_units": [[0, 0.0, "out_quad"], [2, PRESS_UNITS, "linear"], [3, PRESS_UNITS, "in_out_sine"], [5, 0.0, "linear"]],
            "lid.rot_x_deg": [[0, 0.0, "linear"], [3, 0.0, "out_quad"], [5, HOP, "linear"]],
        },
        "note": "The plate clicks in 0.6 unit and springs back; the latch pops the lid up 1.5 degrees.",
    },
    "open": {
        "ticks": 14, "loop": False,
        "tracks": {"lid.rot_x_deg": [[0, HOP, "in_out_sine"], [10, LID_OPEN + OVERSHOOT_DEG, "in_out_sine"], [14, LID_OPEN, "linear"]]},
        "note": "The lid swings to the final angle, overshoots it by 3 degrees at tick 10 and settles at tick 14.",
    },
    "open_hold": {
        "ticks": 40, "loop": True,
        "tracks": {"lid.rot_x_deg": [[0, LID_OPEN, "in_out_sine"], [20, LID_OPEN + 0.4, "in_out_sine"], [40, LID_OPEN, "linear"]]},
        "note": "The lid rests open; a 0.4 degree sway keeps it alive. Loops until close is requested.",
    },
    "close": {
        "ticks": 14, "loop": False,
        "tracks": {
            "lid.rot_x_deg": [[0, LID_OPEN, "in_quad"], [11, 0.0, "out_quad"], [12, 1.8, "in_quad"], [14, 0.0, "linear"]],
            "face_plate.press_units": [[0, 0.0, "linear"], [10, 0.0, "out_quad"], [12, 0.5, "in_out_sine"], [14, 0.0, "linear"]],
        },
        "note": "The lid falls, shuts at tick 11 with a 1.8 degree bounce, the plate clicks, all settled closed at tick 14.",
    },
    "reset": {
        "ticks": 0, "loop": False, "snap": True,
        "tracks": {"lid.rot_x_deg": [[0, 0.0, "linear"]], "face_plate.press_units": [[0, 0.0, "linear"]], "root.x_units": [[0, 0.0, "linear"]]},
        "note": "Instant: every channel to its rest value, reveal candy removed. Allowed from every state.",
    },
}

CHANNEL_REST = {"lid.rot_x_deg": 0.0, "face_plate.press_units": 0.0, "root.x_units": 0.0}

# state -> (sequence, duration policy)
STATES = {
    "CLOSED": {"sequence": "closed_idle", "next": None},
    "ANTICIPATING": {"sequence": "anticipation", "next": "UNLOCKING"},
    "UNLOCKING": {"sequence": "unlock", "next": "OPENING"},
    "OPENING": {"sequence": "open", "next": "OPEN"},
    "OPEN": {"sequence": "open_hold", "next": None},
    "CLOSING": {"sequence": "close", "next": "CLOSED"},
}

REVEAL = {
    "count": 3,
    "models": ["reveal_a", "reveal_b", "reveal_c"],
    "start_units": [[8.0, 9.5, 8.0], [6.8, 9.5, 8.6], [9.2, 9.5, 7.4]],         # inside the pile, model units
    "rise_units": [20.0, 24.0, 22.0],
    "rise_ticks": 24, "hold_ticks": 40, "collect_ticks": 8,
    "scale_keys": [[0, 0.3, "out_cubic"], [12, 1.25, "in_out_sine"], [24, 1.0, "linear"]],
    "spin_deg_per_tick": [18.0, -14.0, 22.0],
    "stagger_ticks": [0, 3, 6],
    "bob_units": 0.6, "bob_period_ticks": 40,
    "note": "Floating reveal candy: each is its own display entity, spawned only after the lid is fully OPEN (after OPENING), so the "
            "trick and treat timelines are identical until then. Position = root + start + rise; rotation yaw spin; scale from scale_keys.",
}


def sample_track(keys, tick):
    """Value of a keyframe track at a (possibly fractional) tick: ease applies from each key to the next, clamped outside the range."""
    if tick <= keys[0][0]:
        return float(keys[0][1])
    for (t0, v0, e), (t1, v1, _) in zip(keys, keys[1:]):
        if tick <= t1:
            f = (tick - t0) / (t1 - t0)
            return v0 + (v1 - v0) * EASES[e](f)
    return float(keys[-1][1])


def sample(seq_name, tick):
    seq = SEQUENCES[seq_name]
    out = dict(CHANNEL_REST)
    t = tick
    if seq["loop"]:
        t = tick % seq["ticks"]
    else:
        t = min(tick, seq["ticks"])
    for ch, keys in seq["tracks"].items():
        out[ch] = sample_track(keys, t)
    return out


# state machine -----------------------------------------------------------------------------------------------------------------------
class Machine:
    """Deterministic: advances only by tick(); start(outcome) from CLOSED; close() from OPEN; reset() from any state."""

    def __init__(self):
        self.state, self.t, self.outcome = "CLOSED", 0, None

    def start(self, outcome):
        assert outcome in ("treat", "trick")
        if self.state != "CLOSED":
            return False
        self.state, self.t, self.outcome = "ANTICIPATING", 0, outcome
        return True

    def close(self):
        if self.state != "OPEN":
            return False
        self.state, self.t = "CLOSING", 0
        return True

    def reset(self):
        self.state, self.t, self.outcome = "CLOSED", 0, None

    def tick(self):
        """Advance one tick; returns the channel values to show for this tick."""
        spec = STATES[self.state]
        seq = SEQUENCES[spec["sequence"]]
        vals = sample(spec["sequence"], self.t)
        self.t += 1
        if spec["next"] and self.t >= seq["ticks"]:
            nxt = spec["next"]
            self.state, self.t = nxt, 0
            if nxt == "CLOSED":
                self.outcome = None
        return vals


# transform maths -----------------------------------------------------------------------------------------------------------------------
def qmul(a, b):
    ax, ay, az, aw = a
    bx, by, bz, bw = b
    return (aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx,
            aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz)


def qrot(q, v):
    x, y, z, w = q
    qv = np.array([x, y, z])
    v = np.asarray(v, float)
    t = 2 * np.cross(qv, v)
    return v + w * t + np.cross(qv, t)


def q_axis(axis, deg):
    h = math.radians(deg) / 2
    s, c = math.sin(h), math.cos(h)
    return {"x": (s, 0, 0, c), "y": (0, s, 0, c), "z": (0, 0, s, c)}[axis]


# Minecraft's item display renderer turns the ITEM MODEL a half circle about y inside its own frame (ItemDisplayRenderer applies a 180 degree
# yaw after the transformation), so at identity the model's north face looks SOUTH. Measured in a real 1.21.1 client. The half turn acts on the
# model vector only, not on the translation: world = T + q (F v), F = rotY(180). To land on the intended pose Rnet (T_c + R v) the rotation SENT
# must be q = Rnet R F (F is its own inverse) and the translation T = Rnet T_c.
FACING_YAW = {"north": 0.0, "west": 90.0, "south": 180.0, "east": -90.0}   # net rotation about +y that takes the model front (-z) to this side
FLIP_Q = (0.0, 1.0, 0.0, 0.0)                                              # rotY(180)


def display_flip(v):
    """The client's half turn of the model about its own centre axis: x and z change sign."""
    v = np.asarray(v, float)
    return np.array([-v[0], v[1], -v[2]])


PIECE_CENTRE = np.array([0.0, 0.5, 0.0])      # the display origin sits at the model centre (8, 8, 8), root-local (0, 0.5, 0) blocks
HINGE_LOCAL = np.array([(S.HINGE[0] - 8) / 16.0, S.HINGE[1] / 16.0, (S.HINGE[2] - 8) / 16.0])


def piece_transform(piece, ch, facing="north"):
    """Display-entity transformation to SEND for a persistent piece: (translation in blocks, left rotation quaternion xyzw). Scale is 1."""
    root = np.array([ch.get("root.x_units", 0.0) / 16.0, 0.0, 0.0])
    if piece == "lid":
        q = q_axis("x", ch["lid.rot_x_deg"])
        t = HINGE_LOCAL + qrot(q, PIECE_CENTRE - HINGE_LOCAL)        # translate-to-hinge * R * translate-from-hinge * rest
    elif piece == "face_plate":
        q = (0, 0, 0, 1.0)
        t = PIECE_CENTRE + np.array([0, 0, ch["face_plate.press_units"] / 16.0])
    else:
        q = (0, 0, 0, 1.0)
        t = PIECE_CENTRE.copy()
    t = t + root
    qf = q_axis("y", FACING_YAW[facing])
    return qrot(qf, t), qmul(qmul(qf, q), FLIP_Q)


def world_point(piece, ch, model_point, facing="north"):
    """Where a model-space point (units) of a piece ends up in root-local WORLD blocks: the client draws T + q (F v)."""
    t, q = piece_transform(piece, ch, facing)
    v = (np.asarray(model_point, float) - 8.0) / 16.0
    return t + qrot(q, display_flip(v))


def reveal_state(index, tick_since_open):
    """(translation in blocks root-local, yaw degrees, scale) of reveal candy `index`, `tick_since_open` ticks after it was spawned."""
    r = REVEAL
    t = tick_since_open - r["stagger_ticks"][index]
    if t < 0:
        return None
    rise = r["rise_ticks"]
    k = EASES["out_cubic"](min(1.0, t / rise))
    start = np.array(r["start_units"][index])
    pos = start + np.array([0, r["rise_units"][index] * k, 0])
    if t > rise:
        pos[1] += r["bob_units"] * math.sin(2 * math.pi * (t - rise) / r["bob_period_ticks"])
    scale = sample_track(r["scale_keys"], t)
    blocks = (pos - np.array([8.0, 0.0, 8.0])) / 16.0          # vault space, before facing and before the client's half turn
    return blocks, (r["spin_deg_per_tick"][index] * t) % 360.0, scale
