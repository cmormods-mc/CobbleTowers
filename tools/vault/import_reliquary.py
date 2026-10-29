#!/usr/bin/env python3
"""Imports the Hallowbound Reliquary (v3.1) as the Jack-o'-Lantern Vault's art and motion.

    python tools/vault/import_reliquary.py <extracted hallowbound-reliquary-v3.1 folder>

Writes (all deterministic):
  src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault/*.json   7 pieces + vault_full
  src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png
  src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault/{animation,events}.json
  src/test/resources/vault/animation_golden.json   samples taken straight from the source clips, not from the manifest

The reliquary's models carry UVs in atlas pixels (0..1024) and no texture_size; vanilla reads UVs as 0..16, so they are rescaled here.
Its clips are linear Blockbench keys in seconds; the manifest keeps them as fractional ticks so sampling stays exact.
The reveal candy is not keyed per tick: the reward_reveal clip is summarised by the manifest's `reveal` block (see REVEAL below).
"""
import json
import math
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault"
TEXTURE = ROOT / "src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png"
DATA = ROOT / "src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault"
GOLDEN = ROOT / "src/test/resources/vault/animation_golden.json"
TEX_ID = "cobbletowers:item/halloween/jack_o_lantern_vault/vault"
ATLAS = 1024
PIECES = ["body", "lid", "face_plate", "candy_fill", "reveal_a", "reveal_b", "reveal_c"]
PERSISTENT = PIECES[:4]
TPS = 20

# channel name -> (animator, keyframe channel, axis)
CHANNELS = {
    "lid.rot_x_deg": ("lid", "rotation", "x"),
    "face_plate.press_units": ("face_plate", "position", "z"),
    "face_plate.y_units": ("face_plate", "position", "y"),
    "root.x_units": ("vault_root", "position", "x"),
    "root.y_units": ("vault_root", "position", "y"),
}
SEQUENCES = ["closed_idle", "anticipation", "unlock", "open", "open_hold", "close", "reset"]
STATES = {   # state -> (sequence, next)
    "CLOSED": ("closed_idle", None), "ANTICIPATING": ("anticipation", "UNLOCKING"), "UNLOCKING": ("unlock", "OPENING"),
    "OPENING": ("open", "OPEN"), "OPEN": ("open_hold", None), "CLOSING": ("close", "CLOSED"),
}
HINGE = [8.0, 15.65, 17.8]
# The reward_reveal clip, as the procedural reveal block: candy rises from inside the pile to y 13/14/15 above its rest, three across.
REVEAL = {
    "count": 3, "models": ["reveal_a", "reveal_b", "reveal_c"],
    "start_units": [[12.0, 8.0, 8.0], [8.0, 8.0, 8.0], [4.0, 8.0, 8.0]], "rise_units": [13.0, 14.0, 15.0],
    "rise_ticks": 14, "hold_ticks": 40, "collect_ticks": 8,
    "scale_keys": [[0, 0.0, "linear"], [5, 1.15, "linear"], [8, 1.0, "linear"]],
    "spin_deg_per_tick": [-6.0, -6.0, -6.0], "stagger_ticks": [0, 2, 5], "bob_units": 0.4, "bob_period_ticks": 60,
    "note": "Floating reveal candy from the reliquary's reward_reveal and open_hold clips: each is its own display entity, spawned only after the lid is "
            "fully OPEN. Position = root + start + rise; rotation yaw spin; scale from scale_keys (0 to 1.15 to 1).",
}
ANCHORS = {"plate": [8.0, 12.7, -5.2], "pile": [8.0, 15.0, 8.0], "seam": [8.0, 15.6, -3.1], "above": [8.0, 30.0, 8.0], "floor": [8.0, 0.2, 8.0]}


def keys_of(clip, animator, channel, axis):
    """[(seconds, value)] of one axis of one animator's keyframes, or [] when that animator does not key it."""
    for an in clip["animators"].values():
        if an["name"] != animator:
            continue
        out = [(k["time"], k["data_points"][0][axis]) for k in an["keyframes"] if k["channel"] == channel]
        assert all(k.get("interpolation", "linear") == "linear" for k in an["keyframes"]), "only linear keys are supported"
        return sorted(out)
    return []


def check_unkeyed_axes(clip):
    """Axes the runtime does not play must be zero in every clip, or the import would silently drop motion."""
    for an in clip["animators"].values():
        for k in an["keyframes"]:
            p = k["data_points"][0]
            if an["name"] == "lid" and k["channel"] == "rotation":
                assert float(p["y"]) == 0 and float(p["z"]) == 0, (clip["name"], "lid rotation off the x axis")
            if an["name"] == "face_plate" and k["channel"] == "position":
                assert float(p["x"]) == 0, (clip["name"], "face_plate x")
            if an["name"] == "vault_root" and k["channel"] == "position":
                assert float(p["z"]) == 0, (clip["name"], "root z")


def sample(keys, t):
    if not keys:
        return 0.0
    if t <= keys[0][0]:
        return float(keys[0][1])
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            return float(v0) + (float(v1) - float(v0)) * (t - t0) / (t1 - t0)
    return float(keys[-1][1])


def ticks_of(clip):
    ticks = round(clip["length"] * TPS)
    assert abs(ticks - clip["length"] * TPS) < 1e-9, (clip["name"], "length is not a whole number of ticks")
    return ticks


def models(src):
    out = {}
    for name in PIECES:
        model = json.loads((src / f"resourcepack/assets/harvestvault/models/{name}.json").read_text())
        for el in model["elements"]:
            for face in el["faces"].values():
                assert all(0 <= u <= ATLAS for u in face["uv"]), (name, el["name"])
                face["uv"] = [round(u * 16 / ATLAS, 5) for u in face["uv"]]
                face["texture"] = "#0"
        out[name] = {"credit": "Hallowbound Reliquary v3.1, imported by tools/vault/import_reliquary.py", "texture_size": [ATLAS, ATLAS],
                     "textures": {"0": TEX_ID, "particle": TEX_ID}, "elements": model["elements"]}
    out["vault_full"] = dict(out["body"], elements=[e for n in PERSISTENT for e in out[n]["elements"]])
    return out


def manifest(clips):
    m = json.loads((DATA / "animation.json").read_text())
    m["coordinate_contract"]["hinge"].update(
        point_units=HINGE, point_root_local_blocks=[0.0, HINGE[1] / 16, (HINGE[2] - 8) / 16], max_open_deg=112.0, peak_overshoot_deg=116.0)
    m["channels"] = {
        "lid.rot_x_deg": {"unit": "degrees", "rest": 0.0, "drives": "lid hinge rotation"},
        "face_plate.press_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "face_plate translation along +z (into the body)"},
        "face_plate.y_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "face_plate translation along y (the latch drops)"},
        "root.x_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "every persistent piece, x (in vault space, before facing)"},
        "root.y_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "every persistent piece, y"},
    }
    m["ease"] = "every key is linear (the reliquary's clips are linear Blockbench keys); other eases stay valid for the reveal block"
    m["sequences"] = {}
    for name in SEQUENCES:
        clip = clips[name]
        ticks = ticks_of(clip)
        tracks = {}
        for channel, (animator, kind, axis) in CHANNELS.items():
            keys = keys_of(clip, animator, kind, axis)
            if keys:
                tracks[channel] = [[round(t * TPS, 6), float(v), "linear"] for t, v in keys]
        m["sequences"][name] = {"ticks": ticks, "seconds": ticks / TPS, "loop": clip["loop"] == "loop", "tracks": tracks}
    m["state_machine"]["states"] = {
        s: {"sequence": seq, "next": nxt, "ticks": m["sequences"][seq]["ticks"]} for s, (seq, nxt) in STATES.items()}
    sm = m["state_machine"]["states"]
    m["state_machine"]["total_ticks_closed_to_open"] = sm["ANTICIPATING"]["ticks"] + sm["UNLOCKING"]["ticks"] + sm["OPENING"]["ticks"]
    m["state_machine"]["total_ticks_open_to_closed"] = sm["CLOSING"]["ticks"]
    m["reveal"] = REVEAL
    m["pieces"]["vault_full"]["note"] = "static assembled closed model (body + lid + face_plate + candy_fill); NOT used at runtime"
    return m


# ---- independent reference for the Java transforms (matrix form; Java uses quaternions) ----
def rot(axis, deg):
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))
    return {"x": [[1, 0, 0], [0, c, -s], [0, s, c]], "y": [[c, 0, s], [0, 1, 0], [-s, 0, c]], "z": [[c, -s, 0], [s, c, 0], [0, 0, 1]]}[axis]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def quat(m):
    tr = m[0][0] + m[1][1] + m[2][2]
    if tr > 0:
        s = math.sqrt(tr + 1) * 2
        return [(m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s, s / 4]
    i = max(range(3), key=lambda k: m[k][k])
    j, k = (i + 1) % 3, (i + 2) % 3
    s = math.sqrt(m[i][i] - m[j][j] - m[k][k] + 1) * 2
    q = [0, 0, 0, 0]
    q[i] = s / 4
    q[j] = (m[j][i] + m[i][j]) / s
    q[k] = (m[k][i] + m[i][k]) / s
    q[3] = (m[k][j] - m[j][k]) / s
    return q


FACING = {"north": 0.0, "west": 90.0, "south": 180.0, "east": -90.0}


def transform(piece, ch, facing):
    centre = [0.0, 0.5, 0.0]
    r = rot("x", ch["lid"]) if piece == "lid" else rot("x", 0)
    hinge = [0.0, HINGE[1] / 16, (HINGE[2] - 8) / 16]
    if piece == "lid":
        d = [centre[i] - hinge[i] for i in range(3)]
        t = [hinge[i] + mv(r, d)[i] for i in range(3)]
    elif piece == "face_plate":
        t = [0.0, 0.5 + ch["plate_y"] / 16, ch["press"] / 16]
    else:
        t = centre[:]
    t = [t[0] + ch["root_x"] / 16, t[1] + ch["root_y"] / 16, t[2]]
    f = rot("y", FACING[facing])
    return mv(f, t), quat(mm(mm(f, r), rot("y", 180)))


def golden(clips, m):
    out = {"schema": 1, "sequences": {}, "transforms": []}
    for name in SEQUENCES:
        clip = clips[name]
        frames = []
        for tick in range(ticks_of(clip)):
            t = (tick % ticks_of(clip) if clip["loop"] == "loop" else tick) / TPS
            frame = {}
            for channel, (animator, kind, axis) in CHANNELS.items():
                frame[channel] = round(sample(keys_of(clip, animator, kind, axis), t), 6)
            frames.append(frame)
        out["sequences"][name] = frames
    for facing in FACING:
        for piece in PERSISTENT:
            for lid, press, plate_y, root_x, root_y in [(0, 0, 0, 0, 0), (30, 0.45, -0.55, 0.2, 0.06), (112, 0, -0.55, -0.15, 0), (116, 0.2, 0, 0.1, 0)]:
                ch = {"lid": lid, "press": press, "plate_y": plate_y, "root_x": root_x, "root_y": root_y}
                t, q = transform(piece, ch, facing)
                out["transforms"].append({"facing": facing, "piece": piece, "lid": lid, "press": press, "plate_y": plate_y, "root_x": root_x,
                                          "root_y": root_y, "t": [round(v, 9) for v in t], "q": [round(v, 9) for v in q]})
    return out


def main():
    src = Path(sys.argv[1])
    anim = json.loads((src / "animation_modelspace.json").read_text())
    assert anim["channel_convention"] == "model_space_right_handed" and anim["hinge"] == HINGE
    clips = {c["name"]: c for c in anim["clips"]}
    for clip in clips.values():
        check_unkeyed_axes(clip)
    MODELS.mkdir(parents=True, exist_ok=True)
    for name, model in models(src).items():
        (MODELS / f"{name}.json").write_text(json.dumps(model, indent=1) + "\n")
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src / "textures/harvest_vault.png", TEXTURE)
    m = manifest(clips)
    (DATA / "animation.json").write_text(json.dumps(m, indent=1) + "\n")
    events = json.loads((DATA / "events.json").read_text())
    events["anchors"] = ANCHORS
    (DATA / "events.json").write_text(json.dumps(events, indent=1) + "\n")
    GOLDEN.parent.mkdir(parents=True, exist_ok=True)
    GOLDEN.write_text(json.dumps(golden(clips, m), indent=1) + "\n")
    ticks = {s: m["state_machine"]["states"][s]["ticks"] for s in STATES}
    print("imported:", ticks)


if __name__ == "__main__":
    main()
