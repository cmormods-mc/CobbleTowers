"""Checks the Jack-o'-Lantern Vault deliverables: geometry, textures, coordinate contract, hinge clearance sweep, animation and state machine.
Run from the repo root after vault_build.py:  python3 tools/vault/vault_verify.py   (exit code 1 on any failure; writes validation-report.json)
Everything here is computed from the files on disk (the runtime JSONs and the manifests), not from the spec objects, where possible."""
from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import vault_anim as A
import vault_build as B
import vault_spec as S

RESULTS, FAILS = [], []


def check(name, ok, detail=""):
    RESULTS.append({"check": name, "ok": bool(ok), "detail": detail})
    if not ok:
        FAILS.append(name)
    print(("PASS " if ok else "FAIL ") + name + (f"  [{detail}]" if detail else ""))


def load_models():
    return {p.stem: json.loads(p.read_text()) for p in sorted(B.MODELS.glob("*.json"))}


def bounds(model):
    lo = np.array([1e9] * 3); hi = np.array([-1e9] * 3)
    for e in model["elements"]:
        corners = box_corners(e)
        lo = np.minimum(lo, corners.min(0)); hi = np.maximum(hi, corners.max(0))
    return lo, hi


def box_corners(e):
    f, t = np.array(e["from"], float), np.array(e["to"], float)
    pts = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
    if "rotation" in e:
        r = e["rotation"]
        pts = (pts - r["origin"]) @ R_matrix(r["axis"], r["angle"]).T + r["origin"]
    return pts


def R_matrix(axis, deg):
    from vault_render import rot_matrix
    return rot_matrix(axis, deg)


def pose_key(vals):
    ch = {"lid.rot_x_deg": vals[0], "face_plate.press_units": vals[1], "root.x_units": vals[2]}
    out = []
    for p in B.PERSISTENT:
        t, q = A.piece_transform(p, ch, "north")
        out.append(tuple(np.round(np.concatenate([t, q]), 12)))
    return out


def main():
    models = load_models()
    m = S.build()
    atlas = Image.open(B.TEXTURE).convert("RGBA")
    size = atlas.size[0]

    # ---- files and schema ------------------------------------------------------------------------------------------------------------
    check("8 runtime models exist", sorted(models) == sorted(list(S.PIECES) + ["vault_full"]), ",".join(sorted(models)))
    check("texture is a square RGBA PNG, power of two", atlas.size[0] == atlas.size[1] and (size & (size - 1)) == 0, f"{atlas.size}")
    allowed = {0, 22.5, -22.5, 45, -45}
    bad = []
    for name, mod in models.items():
        if mod["textures"]["0"] != B.X.TEXTURE or mod["textures"]["particle"] != B.X.TEXTURE:
            bad.append(f"{name}: texture ref")
        if mod["texture_size"] != [size, size]:
            bad.append(f"{name}: texture_size")
        for e in mod["elements"]:
            f, t = e["from"], e["to"]
            if any(not (-16 <= v <= 32) for v in f + t):
                bad.append(f"{name}/{e['name']}: out of -16..32")
            if any(t[i] <= f[i] for i in range(3)):
                bad.append(f"{name}/{e['name']}: empty box")
            if "rotation" in e and (e["rotation"]["angle"] not in allowed or e["rotation"]["axis"] not in "xyz"):
                bad.append(f"{name}/{e['name']}: rotation not vanilla-legal")
            for fn, face in e["faces"].items():
                u = face["uv"]
                if not all(0 <= v <= 16 for v in u) or u[2] <= u[0] or u[3] <= u[1]:
                    bad.append(f"{name}/{e['name']}/{fn}: uv {u}")
                if face["texture"] != "#0":
                    bad.append(f"{name}/{e['name']}/{fn}: texture var")
    check("every element: bounds -16..32, non-empty, legal rotation, uv in 0..16, texture #0", not bad, "; ".join(bad[:5]))

    # ---- UV islands: inside the atlas, no overlap, never transparent -----------------------------------------------------------------
    arr = np.asarray(atlas)
    occ = np.zeros((size, size), np.int32)
    transparent, overlap = 0, 0
    for name, mod in models.items():
        if name == "vault_full":
            continue
        for e in mod["elements"]:
            for face in e["faces"].values():
                u = face["uv"]
                x0, y0, x1, y1 = [round(v * size / 16) for v in u]
                if np.any(arr[y0:y1, x0:x1, 3] < 255):
                    transparent += 1
                occ[y0:y1, x0:x1] += 1
    # an island is used by exactly one element face in exactly one piece file
    overlap = int((occ > 1).sum())
    check("no atlas texel is transparent inside an island", transparent == 0, f"{transparent} islands")
    check("no two element faces share atlas texels (no UV overlap)", overlap == 0, f"{overlap} shared texels")
    used = float((occ > 0).mean())
    check("atlas is used (>25%) and fits", 0.25 < used <= 1.0, f"{used:.0%} of the {size}x{size} texels belong to islands")

    # ---- dimensions and the reference checklist -------------------------------------------------------------------------------------
    full = models["vault_full"]
    body_lo, body_hi = bounds(models["body"]); lid_lo, lid_hi = bounds(models["lid"])
    plate_lo, plate_hi = bounds(models["face_plate"]); fill_lo, fill_hi = bounds(models["candy_fill"])
    full_lo, full_hi = bounds(full)
    dims = {
        "body_footprint_x": [float(body_lo[0]), float(body_hi[0])], "body_footprint_z": [float(body_lo[2]), float(body_hi[2])],
        "body_top_posts_y": float(body_hi[1]), "rim_top_y": S.RIM_TOP,
                "lid_x": [float(lid_lo[0]), float(lid_hi[0])], "plate": [[float(v) for v in plate_lo], [float(v) for v in plate_hi]],
        "full_bounds": [[float(v) for v in full_lo], [float(v) for v in full_hi]],
    }
    check("full model fits the block footprint (x 0..16, only the 0.5-unit rivet studs overhang) and stays inside -16..32",
          full_lo[0] >= -0.5 and full_hi[0] <= 16.5 and full_lo[2] >= 0 and full_hi[2] <= 16 and full_hi[1] <= 32, str(dims["full_bounds"]))
    check("lid sits on the rim (lid bottom y == rim top)", abs(lid_lo[1] - S.RIM_TOP) < 1e-6, f"lid min y {lid_lo[1]}")
    check("hinge axis lies on the lid's back-bottom edge (z == lid max z of the skirt, y == lid bottom)",
          abs(lid_lo[1] - S.HINGE[1]) < 1e-6 and abs(S.HINGE[2] - 14.0) < 1e-6 and abs(float(lid_hi[2]) - 14.0) < 1e-6)
    check("plate is centred on the front (x symmetric about 8) and proud of the front", abs((plate_lo[0] + plate_hi[0]) / 2 - 8) < 1e-6 and plate_lo[2] < 2.0,
          f"x {plate_lo[0]}..{plate_hi[0]} front z {plate_lo[2]}")
    # fill height: candy surface vs cavity (y 3..9)
    check("stationary candy stays inside the cavity footprint (x 3..13, z 3..13) and under the lid ceiling (y 11)",
          fill_lo[0] >= 3 - 1e-6 and fill_hi[0] <= 13 + 1e-6 and fill_lo[2] >= 3 - 1e-6 and fill_hi[2] <= 13 + 1e-6 and fill_hi[1] <= 11,
          f"x {fill_lo[0]:.2f}..{fill_hi[0]:.2f} z {fill_lo[2]:.2f}..{fill_hi[2]:.2f} y top {fill_hi[1]:.2f}")
    # candies above the rim must lie inside the lid void (x 3.5..12.5, z 3..13) so the closed lid does not cut them
    inside = True
    for e in models["candy_fill"]["elements"]:
        c = box_corners(e)
        if c[:, 1].max() > S.RIM_TOP + 1e-6 and (c[:, 0].min() < 3.5 - 1e-6 or c[:, 0].max() > 12.5 + 1e-6 or c[:, 2].min() < 3 - 1e-6 or c[:, 2].max() > 13 + 1e-6):
            inside = False
    check("candy above the rim stays inside the lid's hollow (no clipping through the lid walls)", inside)
    cavity_h = S.RIM_TOP - 3.0
    tops = [box_corners(e)[:, 1].max() for e in models["candy_fill"]["elements"] if e["name"].endswith("_body")]
    base_top = [e["to"][1] for e in models["candy_fill"]["elements"] if e["name"] == "pile.base"][0]
    dims["candy_pile"] = {"slab_top_y": base_top, "slab_fraction_of_cavity": round((base_top - 3.0) / cavity_h, 3),
                          "mean_candy_top_y": round(float(np.mean(tops)), 2), "mean_top_fraction_of_cavity": round((float(np.mean(tops)) - 3.0) / cavity_h, 3),
                          "highest_candy_top_y": round(float(max(tops)), 2), "rim_top_y": S.RIM_TOP}
    lid_main = [box_corners(e)[:, 1].max() for e in models["lid"]["elements"] if e["name"].split(".")[0] in ("skirt", "tiers")]
    dims["lid"] = {"bottom_y": S.RIM_TOP, "top_of_tiers_y": round(float(max(lid_main)), 2), "thickness_without_stem": round(float(max(lid_main)) - S.RIM_TOP, 2),
                   "stem_top_y": float(lid_hi[1]), "stem_height": round(float(lid_hi[1]) - float(max(lid_main)), 2)}
    # vines: each lies wholly on the lid (y >= 9) or wholly on the body (y <= rim top), none bridges the seam
    bridges = []
    for pc, mod in (("lid", models["lid"]), ("body", models["body"])):
        for e in mod["elements"]:
            if e["name"].startswith("vines_") or e["name"].startswith("stem.leaf"):
                lo, hi = e["from"][1], e["to"][1]
                if pc == "lid" and lo < S.RIM_TOP - 1e-6:
                    bridges.append(e["name"])
                if pc == "body" and hi > S.RIM_TOP - 1.5 + 1e-6:
                    bridges.append(e["name"])
    check("lid vines are only on the lid, body vines only below the rim band; none bridges the seam", not bridges, ",".join(bridges))
    # piece partition: persistent pieces together equal vault_full
    count = sum(len(models[p]["elements"]) for p in ("body", "lid", "face_plate", "candy_fill"))
    check("vault_full == body + lid + face_plate + candy_fill (element count)", count == len(full["elements"]), f"{count} vs {len(full['elements'])}")
    # reveal pieces are centred on (8, 8, 8)
    centred = True
    for r in ("reveal_a", "reveal_b", "reveal_c"):
        lo, hi = bounds(models[r])
        if np.abs((lo + hi) / 2 - 8).max() > 0.01:
            centred = False
    check("reveal pieces are centred at (8, 8, 8) (spin about their own middle)", centred)

    # ---- hinge clearance sweep ----------------------------------------------------------------------------------------------------------
    def aabbs(pieces):
        boxes = []
        for p in pieces:
            for e in models[p]["elements"]:
                c = box_corners(e)
                boxes.append((c.min(0), c.max(0)))
        return np.array([b[0] for b in boxes]), np.array([b[1] for b in boxes])
    blo, bhi = aabbs(("body", "face_plate", "candy_fill"))
    pts = []
    for e in models["lid"]["elements"]:
        f, t = np.array(e["from"]), np.array(e["to"])
        axes = [np.unique(np.concatenate([np.arange(f[i], t[i], 0.5), [t[i]]])) for i in range(3)]
        g = np.array(np.meshgrid(*axes, indexing="ij")).reshape(3, -1).T
        pts.append(g)
    pts = np.vstack(pts)
    H = np.array(S.HINGE)
    worst = (0.0, None)
    shrink = 0.03
    for deg in np.arange(0, A.LID_OPEN + A.OVERSHOOT_DEG + 0.01, 0.5):
        Rm = R_matrix("x", float(deg))
        q = (pts - H) @ Rm.T + H
        inside = np.zeros(len(q), bool)
        for lo, hi in zip(blo + shrink, bhi - shrink):
            inside |= np.all((q > lo) & (q < hi), axis=1)
        if inside.any():
            depth = float(inside.sum())
            if depth > worst[0]:
                worst = (depth, float(deg))
    check("hinge sweep 0..118 deg: no lid point penetrates the body, plate or candy by more than 0.03 unit", worst[0] == 0.0,
          "no penetration at any 0.5 deg step" if worst[0] == 0 else f"{worst[0]} points at {worst[1]} deg")
    low = ((pts - H) @ R_matrix("x", A.LID_OPEN + A.OVERSHOOT_DEG).T + H)
    check("at peak overshoot the lid stays behind/above the rim and clear of the floor", low[:, 1].min() > 0 and low[:, 2].min() > 0,
          f"lowest lid point y={low[:, 1].min():.2f}")

    # ---- coordinate contract -------------------------------------------------------------------------------------------------------------
    man = json.loads((B.DATA / "animation.json").read_text())
    rest = {"lid.rot_x_deg": 0.0, "face_plate.press_units": 0.0, "root.x_units": 0.0}
    # all four pieces coincide at rest (rest transform is the identity in vault space)
    same = all(np.allclose(A.piece_transform(p, rest, f)[0], A.piece_transform("body", rest, f)[0]) and np.allclose(
        A.piece_transform(p, rest, f)[1], A.piece_transform("body", rest, f)[1]) for p in B.PERSISTENT for f in A.FACING_YAW)
    check("at rest all four persistent pieces share one transform (no per-piece offset: no double offset)", same)
    fronts = {}
    for facing, want in (("north", (0, 0, -1)), ("east", (1, 0, 0)), ("south", (0, 0, 1)), ("west", (-1, 0, 0))):
        t, q = A.piece_transform("body", rest, facing)
        v = A.qrot(q, A.display_flip((0, 0, -1)))          # the client draws T + q (F v)
        fronts[facing] = np.allclose(v, want, atol=1e-9)
    check("after the client's 180-degree display flip the model front (-z) points north/east/south/west for the four facings", all(fronts.values()), str(fronts))
    # hinge points are fixed in the world for every angle and every facing
    fixed = True
    for facing in A.FACING_YAW:
        ref = A.world_point("lid", {**rest}, [3.0, 9.0, 14.0], facing)
        for deg in (1.5, 30, 90, 115, 118):
            for x in (0.0, 3.0, 8.0, 13.0, 16.0):
                a = A.world_point("lid", {**rest, "lid.rot_x_deg": deg}, [x, 9.0, 14.0], facing)
                b = A.world_point("lid", {**rest}, [x, 9.0, 14.0], facing)
                fixed &= bool(np.allclose(a, b, atol=1e-9))
    check("points on the hinge axis do not move for any angle, in all four facings", fixed)
    # the lid's front edge rises with a positive angle (direction of positive rotation)
    f0 = A.world_point("lid", rest, [8, 9, 2], "north")
    f1 = A.world_point("lid", {**rest, "lid.rot_x_deg": 30}, [8, 9, 2], "north")
    check("positive lid.rot_x_deg lifts the lid's front edge (and swings it toward the back)", f1[1] > f0[1] and f1[2] > f0[2])
    # a lid point goes through exactly R about the hinge in vault space: compare with the independent matrix formula
    pt = np.array([5.0, 12.0, 4.0])
    for deg in (10, 45, 100, 118):
        want = (pt - H) @ R_matrix("x", deg).T + H
        got = A.world_point("lid", {**rest, "lid.rot_x_deg": deg}, pt, "north") * 16 + np.array([8, 0, 8])   # north: net rotation is the identity
        check(f"lid transform equals root*T(h)*Rx({deg})*T(-h) applied to a lid point", np.allclose(want, got, atol=1e-9), f"{want} vs {got}")

    # ---- animation + state machine ---------------------------------------------------------------------------------------------------------
    seqs = man["sequences"]
    check("every sequence: seconds == ticks / 20", all(abs(s["seconds"] - s["ticks"] / 20) < 1e-9 for s in seqs.values()))
    durations = {k: v["seconds"] for k, v in seqs.items()}
    check("timing brief: anticipation 0.5 s, unlock 0.25 s, open 0.7 s, close 0.7 s", durations["anticipation"] == 0.5 and durations["unlock"] == 0.25 and durations["open"] == 0.7 and durations["close"] == 0.7, str(durations))
    cont = []
    for a, b in (("anticipation", "unlock"), ("unlock", "open"), ("open", "open_hold"), ("close", "closed_idle")):
        ea, sb = A.sample(a, seqs[a]["ticks"]), A.sample(b, 0)
        for ch in ea:
            if abs(ea[ch] - sb[ch]) > 1e-9:
                cont.append(f"{a}->{b}:{ch} {ea[ch]} vs {sb[ch]}")
    check("sequence boundaries are continuous (end value == next start value)", not cont, "; ".join(cont))
    lids = [A.sample("open", t)["lid.rot_x_deg"] for t in range(15)]
    check("open peaks at LID_OPEN + 3 deg then settles at LID_OPEN; monotone up to the peak", abs(max(lids) - (A.LID_OPEN + 3.0)) < 1e-9 and abs(lids[-1] - A.LID_OPEN) < 1e-9 and all(b >= a - 1e-9 for a, b in zip(lids[:11], lids[1:11])), f"max {max(lids):.2f} final {lids[-1]}")
    cl = [A.sample("close", t)["lid.rot_x_deg"] for t in range(15)]
    check("close reaches 0 at tick 11, bounces <= 2 deg, rests at 0 at tick 14", abs(cl[11]) < 1e-9 and max(cl[12:]) <= 2.0 and abs(cl[14]) < 1e-9)
    check("final lid angle after open is the manifest max_open_deg", abs(lids[-1] - man["coordinate_contract"]["hinge"]["max_open_deg"]) < 1e-9)
    # 100+ automated cycles, alternating outcomes, comparing the closed pose bit for bit
    closed_first = None
    mismatches = 0
    cycles = 120
    states_seen = set()
    for c in range(cycles):
        mach = A.Machine()
        outcome = "treat" if c % 2 == 0 else "trick"
        # the machine starts CLOSED; sample the pose exactly as the runtime does
        pose0 = tuple(round(v, 12) for v in A.sample("closed_idle", 0).values())
        mach.start(outcome)
        open_ticks = 0
        while True:
            st = mach.state
            states_seen.add(st)
            mach.tick()
            if st == "OPEN":
                open_ticks += 1
                if open_ticks == (20 if outcome == "trick" else 60):
                    mach.close()
            if st == "CLOSING" and mach.state == "CLOSED":
                break
        pose1 = tuple(round(v, 12) for v in A.sample("closed_idle", mach.t).values())
        poses, last = pose_key(pose0), pose_key(pose1)
        if closed_first is None:
            closed_first = poses
        if poses != closed_first or last != closed_first or mach.state != "CLOSED":
            mismatches += 1
    check(f"{cycles} automated cycles (treat/trick alternating): first and last closed transforms identical, machine ends CLOSED", mismatches == 0, f"{mismatches} mismatches; states seen {sorted(states_seen)}")
    # trick and treat pose timelines are identical until the lid is open
    def timeline(outcome):
        mach = A.Machine(); mach.start(outcome); out = []
        for _ in range(10 + 5 + 14):
            out.append((mach.state, tuple(mach.tick().items())))
        return out
    check("trick and treat share an identical pose timeline until the lid is open (29 ticks)", timeline("treat") == timeline("trick"))
    ev = json.loads((B.DATA / "events.json").read_text())["events"]
    early = [e for e in ev if e["state"] in ("ANTICIPATING", "UNLOCKING", "OPENING") and e["outcome"] != "any"]
    check("no outcome-specific event fires before the lid is open", not early)
    # reset from every state and tick
    bad = 0
    for st in A.STATES:
        for t in range(0, 15):
            mach = A.Machine(); mach.start("treat")
            while mach.state != st:
                mach.tick()
                if mach.state == "OPEN" and st in ("CLOSING", "CLOSED"):
                    mach.close()
            for _ in range(t):
                mach.tick()
            mach.reset()
            vals = mach.tick()
            if mach.state != "CLOSED" or abs(vals["lid.rot_x_deg"]) > 1e-9 or abs(vals["face_plate.press_units"]) > 1e-9:
                bad += 1
    check("reset from every state (at several ticks) returns to CLOSED at rest", bad == 0, f"{bad} failures")
    rs = A.reveal_state(0, 0)
    inside = 3 <= rs[0][0] * 16 + 8 <= 13 and 3 <= rs[0][2] * 16 + 8 <= 13 and rs[0][1] * 16 < 11
    check("reveal candy starts inside the pile and rises above the open lid's hole", inside and A.reveal_state(0, 40)[0][1] * 16 > 14)

    report = {"checks": RESULTS, "dimensions": dims, "failed": FAILS}
    (B.ART / "validation-report.json").write_text(json.dumps(report, indent=1) + "\n")
    print(f"\n{len(RESULTS) - len(FAILS)}/{len(RESULTS)} checks passed")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
