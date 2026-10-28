"""Builds every Jack-o'-Lantern Vault deliverable from the spec. Run from the repo root:  python3 tools/vault/vault_build.py

Writes (all deterministic, a clean checkout reproduces the same bytes):
  src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault/*.json   runtime models (7 pieces + vault_full)
  src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png
  src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault/{animation,events}.json
  src/test/resources/vault/animation_golden.json                                         Python reference samples the Java tests compare
  art/jack_o_lantern_vault/{jack_o_lantern_vault.bbmodel,vault.png,previews/*}
"""
from __future__ import annotations

import io
import json
import math
import sys
import uuid
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import vault_anim as A
import vault_events as E
import vault_export as X
import vault_paint as P
import vault_render as R
import vault_spec as S

ROOT = HERE.parent.parent
MODELS = ROOT / "src/main/resources/assets/cobbletowers/models/halloween/jack_o_lantern_vault"
TEXTURE = ROOT / "src/main/resources/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png"
DATA = ROOT / "src/main/resources/data/cobbletowers/halloween/jack_o_lantern_vault"
GOLDEN = ROOT / "src/test/resources/vault/animation_golden.json"
ART = ROOT / "art/jack_o_lantern_vault"
REFERENCE = Path("/root/.claude/uploads/2df97ef2-20a0-5a98-ab01-d5146231f20a/3c5274f3-image.png")
ID = "cobbletowers:halloween/jack_o_lantern_vault"
PERSISTENT = ("body", "lid", "face_plate", "candy_fill")


class ContractPose:
    """Renders through the SAME maths the runtime uses (vault_anim.piece_transform): this is the visual test of the coordinate contract."""

    def __init__(self, ch, facing="north"):
        self.ch, self.facing = ch, facing

    def apply(self, piece, pts):
        pts = np.asarray(pts, float)
        if piece not in PERSISTENT:
            return pts
        t, q = A.piece_transform(piece, self.ch, self.facing)
        out = np.array([A.qrot(q, A.display_flip(p - 8.0)) for p in pts])      # the client draws T + q (F v)
        return out + t * 16.0 + np.array([8.0, 0.0, 8.0])        # root-local units -> vault units, root at (8, 0, 8)

    def normal(self, piece, n):
        if piece not in PERSISTENT:
            return np.asarray(n, float)
        _, q = A.piece_transform(piece, self.ch, self.facing)
        return A.qrot(q, A.display_flip(n))


def reveal_jobs(model, tick_since_open, facing="north"):
    jobs = []
    for i, name in enumerate(A.REVEAL["models"]):
        st = A.reveal_state(i, tick_since_open)
        if st is None:
            continue
        blocks, yaw, scale = st
        fq = A.q_axis("y", A.FACING_YAW[facing])
        q = A.qmul(A.qmul(fq, A.q_axis("y", yaw)), A.FLIP_Q)
        pos = A.qrot(fq, blocks) * 16.0 + np.array([8.0, 0.0, 8.0])

        def fn(piece, pts, q=q, pos=pos, scale=scale):
            return np.array([A.qrot(q, A.display_flip((p - 8.0) * scale)) for p in np.asarray(pts, float)]) + pos

        def nfn(piece, n, q=q):
            return A.qrot(q, A.display_flip(n))
        jobs.append((model.piece(name), fn, nfn))
    return jobs


def label(im, text, xy=(10, 8)):
    d = ImageDraw.Draw(im)
    d.rectangle([xy[0] - 4, xy[1] - 3, xy[0] + 7 * len(text) + 4, xy[1] + 13], fill=(0, 0, 0, 170))
    d.text(xy, text, fill=(255, 220, 160, 255))
    return im


def write_json(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=1) + "\n")


def manifest():
    seqs = {}
    for name, s in A.SEQUENCES.items():
        seqs[name] = {"ticks": s["ticks"], "seconds": round(s["ticks"] / A.TPS, 3), "loop": s["loop"], **({"snap": True} if s.get("snap") else {}),
                      "tracks": s["tracks"], "note": s["note"]}
    return {
        "schema": 1,
        "id": ID,
        "ticks_per_second": A.TPS,
        "units_per_block": 16,
        "coordinate_contract": {
            "space": "vault space: model units, x east, y up, z south; the model FRONT is the north face (-z); the floor is y = 0",
            "root": "the vault block's floor centre, i.e. model point (8, 0, 8); every display entity is spawned at the root with yaw 0 and "
                    "transform-only facing (see composition)",
            "display_origin": "an item display draws its model centred on the entity, so model point (8, 8, 8) sits at root + (0, 0.5, 0) blocks: "
                              "the rest translation of every persistent piece is (0, 0.5, 0); its rest rotation SENT is rotY(180) = (0, 1, 0, 0) xyzw "
                              "(the client's own half turn then cancels it, so the model stands in vault space: front north)",
            "scale": [1, 1, 1],
            "display_transform": "none (the item display default, no display override in the model JSON)",
            "hinge": {"axis": "x", "point_units": list(S.HINGE), "point_root_local_blocks": [round(float(v), 6) for v in A.HINGE_LOCAL],
                      "positive_direction": "positive rotation about +x lifts the lid's FRONT edge (opens); angle = lid.rot_x_deg",
                      "max_open_deg": A.LID_OPEN, "peak_overshoot_deg": A.LID_OPEN + A.OVERSHOOT_DEG},
            "facing_yaw_deg": A.FACING_YAW,
            "client_item_display_flip": "Minecraft's item display renderer turns the item MODEL a half circle about y inside its own frame, after the "
                                        "transformation: the client draws T + q (F v) with F = rotY(180). At identity the model's north face looks south "
                                        "(verified in a real 1.21.1 client). The flip acts on the model only, not on the translation.",
            "composition": "Intended pose = Rnet (Tc + R v). For the lid Tc = H + R(c - H), R = rotX(lid.rot_x_deg), H = hinge (root-local blocks), "
                           "c = (0, 0.5, 0); that is root x translate(H) x R x translate(-H) x rest, no second offset anywhere. Other pieces: Tc = c "
                           "(+ (0, 0, press/16) for face_plate), R = identity. All get + (root.x_units/16, 0, 0) first. Rnet = rotY(facing_yaw_deg) "
                           "(north 0, west 90, south 180, east -90). SENT to the client: translation T = Rnet Tc, left rotation q = Rnet R F with "
                           "F = rotY(180) (so the client's own F cancels); entity yaw stays 0.",
            "interpolation": "every tick the server sends the sampled transformation with interpolation_duration = 1 tick (one sample per "
                             "tick, so rotation about the hinge follows the arc instead of cutting the chord)",
        },
        "pieces": {
            "body": {"model": f"{ID}/body", "rest": {"translation_blocks": [0, 0.5, 0], "rotation_xyzw": [0, 1, 0, 0]}},
            "lid": {"model": f"{ID}/lid", "rest": {"translation_blocks": [0, 0.5, 0], "rotation_xyzw": [0, 1, 0, 0]}, "pivot": "hinge"},
            "face_plate": {"model": f"{ID}/face_plate", "rest": {"translation_blocks": [0, 0.5, 0], "rotation_xyzw": [0, 1, 0, 0]}},
            "candy_fill": {"model": f"{ID}/candy_fill", "rest": {"translation_blocks": [0, 0.5, 0], "rotation_xyzw": [0, 1, 0, 0]}},
            "reveal_a": {"model": f"{ID}/reveal_a", "origin": "authored centred at (8, 8, 8): a display entity rotates it about its own middle"},
            "reveal_b": {"model": f"{ID}/reveal_b", "origin": "as reveal_a"},
            "reveal_c": {"model": f"{ID}/reveal_c", "origin": "as reveal_a"},
            "vault_full": {"model": f"{ID}/vault_full", "note": "static assembled closed model (body + lid + face_plate + candy_fill); NOT used at runtime"},
        },
        "channels": {
            "lid.rot_x_deg": {"unit": "degrees", "rest": 0.0, "drives": "lid hinge rotation"},
            "face_plate.press_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "face_plate translation along +z (into the body)"},
            "root.x_units": {"unit": "model units (1/16 block)", "rest": 0.0, "drives": "every persistent piece, x (in vault space, before facing)"},
        },
        "ease": "the ease on a key shapes the segment that starts at that key: " + ", ".join(A.EASES),
        "sequences": seqs,
        "state_machine": {
            "states": {k: {"sequence": v["sequence"], "next": v["next"], "ticks": A.SEQUENCES[v["sequence"]]["ticks"]} for k, v in A.STATES.items()},
            "start": "CLOSED -> ANTICIPATING (outcome treat or trick is fixed here and does not change the pose timeline)",
            "close": "OPEN -> CLOSING (requested by the controller: the close_request event, or an API call)",
            "reset": "any state -> CLOSED, all channels to rest, reveal candy removed (sequence 'reset')",
            "total_ticks_closed_to_open": sum(A.SEQUENCES[x]["ticks"] for x in ("anticipation", "unlock", "open")),
            "total_ticks_open_to_closed": A.SEQUENCES["close"]["ticks"],
        },
        "reveal": A.REVEAL,
    }


def write_runtime(m, atlas, rects, size):
    TEXTURE.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(TEXTURE, optimize=True)
    names = X.write_models(m, rects, size, MODELS)
    write_json(DATA / "animation.json", manifest())
    write_json(DATA / "events.json", {"schema": 1, "id": ID, "ticks_per_second": A.TPS, "anchors": E.ANCHORS, "events": E.EVENTS,
                                       "note": "Not animation data. state/tick = the state's own tick counter; see vault_events.py for the contract."})
    # golden samples: every tick of every sequence, all channels, plus the transforms of every persistent piece in the 4 facings
    gold = {"schema": 1, "sequences": {}, "transforms": []}
    for name, s in A.SEQUENCES.items():
        gold["sequences"][name] = [{k: round(v, 6) for k, v in A.sample(name, t).items()} for t in range(max(1, s["ticks"] + 1))]
    for facing in A.FACING_YAW:
        for lid in (0.0, 1.5, 45.0, 90.0, 115.0, 118.0):
            ch = {"lid.rot_x_deg": lid, "face_plate.press_units": 0.4, "root.x_units": 0.1}
            for piece in PERSISTENT:
                t, q = A.piece_transform(piece, ch, facing)
                gold["transforms"].append({"facing": facing, "piece": piece, "lid": lid, "press": 0.4, "root_x": 0.1,
                                           "t": [round(float(v), 6) for v in t], "q": [round(float(v), 6) for v in q]})
    write_json(GOLDEN, gold)
    return names


def anim_cycle():
    """One full cycle sampled per tick: [(state, channels, ticks_since_open or None)]."""
    mach = A.Machine()
    frames = [("CLOSED", mach.tick(), None) for _ in range(10)]
    mach.start("treat")
    open_ticks, closing = 0, False
    while True:
        st = mach.state
        ch = mach.tick()
        since = None
        if st == "OPEN":
            since = open_ticks
            open_ticks += 1
        frames.append((st, ch, since))
        if st == "OPEN" and open_ticks == 60:
            mach.close()
        if st == "CLOSING":
            closing = True
        if closing and mach.state == "CLOSED":
            break
    frames += [("CLOSED", mach.tick(), None) for _ in range(6)]
    return frames


def previews(m, atlas, rects, size):
    out = ART / "previews"
    out.mkdir(parents=True, exist_ok=True)
    az, el = -23, 22
    rest = {"lid.rot_x_deg": 0.0, "face_plate.press_units": 0.0, "root.x_units": 0.0}
    opened = dict(rest, **{"lid.rot_x_deg": A.LID_OPEN})
    render = lambda ch, facing="north", az=az, el=el, px=30, size_=(700, 640), centre=(8, 8, 8), extra=None: R.render(
        m, atlas, rects, size, PERSISTENT, ContractPose(ch, facing), az=az, el=el, px=px, out=size_, centre=centre, extra=extra)
    closed = render(rest, size_=(700, 720), centre=(8, 13, 8), px=19)
    open_ = render(opened, el=el + 12, size_=(700, 800), centre=(8, 17, 12), px=19)
    closed.save(out / "closed.png")
    open_.save(out / "open.png")
    # side by side with the concept (crops of the supplied reference; the stone pedestal is excluded)
    if REFERENCE.exists():
        ref = Image.open(REFERENCE).convert("RGBA")
        rc = ref.crop((60, 180, 760, 740)).resize((700, 560))
        ro = ref.crop((800, 90, 1500, 740)).resize((700, 650))
        W = Image.new("RGBA", (1400, 1250), (20, 20, 24, 255))
        W.paste(label(rc, "concept - closed"), (0, 0)); W.paste(label(closed.crop((0, 80, 700, 640)), "model - closed"), (700, 0))
        W.paste(label(ro, "concept - open"), (0, 580)); W.paste(label(open_.crop((0, 60, 700, 710)), "model - open"), (700, 580))
        W.save(out / "compare_reference.png")
    # four facings through the runtime contract
    F = Image.new("RGBA", (1400, 1280), (20, 20, 24, 255))
    for i, facing in enumerate(("north", "east", "south", "west")):
        im = render(opened if i % 2 else rest, facing, az=-23, el=24, px=14, size_=(700, 640), centre=(8, 14, 8))
        F.paste(label(im, f"facing {facing}  ({'open' if i % 2 else 'closed'})  front -> {facing}"), ((i % 2) * 700, (i // 2) * 640))
    F.save(out / "facings.png")
    # pieces sheet
    sheet = Image.new("RGBA", (1400, 700), (20, 20, 24, 255))
    for i, pc in enumerate(("body", "lid", "face_plate", "candy_fill")):
        im = R.render(m, atlas, rects, size, (pc,), R.Pose(0), az=-23, el=26, px=11, out=(350, 350), centre=(8, 12, 8))
        sheet.paste(label(im, pc), (i * 350, 0))
    for i, pc in enumerate(("reveal_a", "reveal_b", "reveal_c")):
        im = R.render(m, atlas, rects, size, (pc,), R.Pose(0), az=-23, el=26, px=60, out=(350, 350), centre=(8, 8, 8))
        sheet.paste(label(im, pc), (i * 350, 350))
    sheet.save(out / "pieces.png")
    atlas.resize((size * 3, size * 3), Image.NEAREST).save(out / "atlas_x3.png")
    # animation strip + gif
    frames = anim_cycle()
    imgs = []
    for st, ch, since in frames:
        extra = reveal_jobs(m, since) if since is not None else None
        im = render(ch, "north", az=-30, el=28, px=9, size_=(380, 360), centre=(8, 15, 8), extra=extra)
        imgs.append(label(im, f"{st} lid {ch['lid.rot_x_deg']:6.1f}"))
    imgs[0].save(out / "animation_preview.gif", save_all=True, append_images=imgs[1:], duration=50, loop=0, disposal=2)
    want = [("ANTICIPATING", 2), ("UNLOCKING", 2), ("OPENING", 5), ("OPENING", 10), ("OPEN", 6), ("OPEN", 30), ("OPEN", 59), ("CLOSING", 5), ("CLOSING", 11), ("CLOSED", None)]
    seen, count = {}, {}
    pick = []
    for i, (st, ch, since) in enumerate(frames):
        count[st] = count.get(st, 0)
        seen[(st, count[st])] = i
        count[st] += 1
    for st, k in want:
        pick.append(seen.get((st, k), seen[(st, 0)]) if k is not None else len(frames) - 1)
    strip = Image.new("RGBA", (380 * 5, 360 * 2), (20, 20, 24, 255))
    for i, k in enumerate(pick):
        strip.paste(imgs[k], ((i % 5) * 380, (i // 5) * 360))
    strip.save(out / "animation_strip.png")
    return len(frames)


def blockbench_animations(m):
    """Preview-only Blockbench animations, baked one keyframe per tick (linear) from the manifest sequences. NOT a runtime animation."""
    res = []
    guid = lambda n: str(uuid.uuid5(X.NS_UUID, "anim:" + n))
    for name, s in A.SEQUENCES.items():
        n = max(1, s["ticks"])
        animators = {}
        def add(group, channel, pts):
            animators.setdefault(X._u(group), {"name": group, "type": "bone", "keyframes": []})["keyframes"] += [
                {"channel": channel, "data_points": [{"x": p[0], "y": p[1], "z": p[2]}], "uuid": guid(f"{name}:{group}:{channel}:{i}"),
                 "time": round(i / A.TPS, 4), "color": -1, "interpolation": "linear"} for i, p in enumerate(pts)]
        samples = [A.sample(name, t) for t in range(n + 1)]
        # the lid group's uuid is the piece group; the Blockbench outliner uses piece uuids for the 'bone' animator keys
        animators.clear()
        def add2(piece, channel, pts):
            key = X._u("piece:" + piece)
            animators.setdefault(key, {"name": piece, "type": "bone", "keyframes": []})["keyframes"] += [
                {"channel": channel, "data_points": [{"x": p[0], "y": p[1], "z": p[2]}], "uuid": guid(f"{name}:{piece}:{channel}:{i}"),
                 "time": round(i / A.TPS, 4), "color": -1, "interpolation": "linear"} for i, p in enumerate(pts)]
        add2("lid", "rotation", [(c["lid.rot_x_deg"], 0, 0) for c in samples])
        if any(abs(c["face_plate.press_units"]) > 0 for c in samples):
            add2("face_plate", "position", [(0, 0, c["face_plate.press_units"]) for c in samples])
        if any(abs(c["root.x_units"]) > 0 for c in samples):
            animators.setdefault(X._u("root"), {"name": "vault_root", "type": "bone", "keyframes": []})["keyframes"] += [
                {"channel": "position", "data_points": [{"x": c["root.x_units"], "y": 0, "z": 0}], "uuid": guid(f"{name}:root:{i}"),
                 "time": round(i / A.TPS, 4), "color": -1, "interpolation": "linear"} for i, c in enumerate(samples)]
        res.append({"uuid": guid(name), "name": name, "loop": "loop" if s["loop"] else "once", "override": False, "length": max(0.05, round(s["ticks"] / A.TPS, 4)),
                    "snapping": 20, "selected": False, "anim_time_update": "", "blend_weight": "", "start_delay": "", "loop_delay": "",
                    "animators": animators})
    return res


def main():
    m = S.build()
    atlas, rects, size = P.build_atlas(m)
    names = write_runtime(m, atlas, rects, size)
    buf = io.BytesIO()
    atlas.save(buf, "PNG", optimize=True)
    ART.mkdir(parents=True, exist_ok=True)
    (ART / "vault.png").write_bytes(buf.getvalue())
    bb = X.bbmodel(m, rects, size, buf.getvalue(), blockbench_animations(m))
    (ART / "jack_o_lantern_vault.bbmodel").write_text(json.dumps(bb, indent=1) + "\n")
    n = previews(m, atlas, rects, size)
    print("models", names)
    print("atlas", size, "elements", len(m.els), {p: len(m.piece(p)) for p in S.PIECES}, "preview frames", n)


if __name__ == "__main__":
    main()
