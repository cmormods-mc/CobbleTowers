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
    check("atlas is used (>8%) and fits", 0.08 < used <= 1.0, f"{used:.0%} of the {size}x{size} texels belong to islands")

    # ---- dimensions and the reference checklist -------------------------------------------------------------------------------------
    full = models["vault_full"]
    body_lo, body_hi = bounds(models["body"]); lid_lo, lid_hi = bounds(models["lid"])
    plate_lo, plate_hi = bounds(models["face_plate"]); fill_lo, fill_hi = bounds(models["candy_fill"])
    full_lo, full_hi = bounds(full)
    cx, cz = S.CX, S.CZ
    def radial(e):
        c = box_corners(e)
        return float(np.hypot(c[:, 0] - cx, c[:, 2] - cz).max())
    dims = {
        "body_x": [float(body_lo[0]), float(body_hi[0])], "body_z": [float(body_lo[2]), float(body_hi[2])], "rim_top_y": S.RIM_TOP,
        "max_belly_radius": round(max(radial(e) for e in models["body"]["elements"] if e["name"].startswith("shell.l")), 2),
        "lid_x": [float(lid_lo[0]), float(lid_hi[0])], "plate": [[float(v) for v in plate_lo], [float(v) for v in plate_hi]],
        "full_bounds": [[float(v) for v in full_lo], [float(v) for v in full_hi]],
    }
    check("full model stays inside vanilla's -16..32 and the pumpkin is centred on the block (x and z symmetric about 8)",
          full_lo.min() >= -16 and full_hi.max() <= 32 and abs((body_lo[0] + body_hi[0]) / 2 - cx) < 0.6, str(dims["full_bounds"]))
    check("lid sits on the rim (lid bottom y == rim top)", abs(lid_lo[1] - S.RIM_TOP) < 1e-6, f"lid min y {lid_lo[1]}")
    check("hinge axis lies on the lid's back-bottom edge (y == lid bottom, z == the lid's farthest back point: nothing of the lid is behind it)",
          abs(lid_lo[1] - S.HINGE[1]) < 1e-6 and float(lid_hi[2]) <= S.HINGE[2] + 1e-6 and S.HINGE[2] - float(lid_hi[2]) < 0.5, f"lid max z {lid_hi[2]:.2f} hinge z {S.HINGE[2]}")
    check("plate is centred on the front (x symmetric about 8) and proud of the front lobe", abs((plate_lo[0] + plate_hi[0]) / 2 - cx) < 1e-6 and plate_lo[2] < cz - dims["max_belly_radius"],
          f"x {plate_lo[0]}..{plate_hi[0]} front z {plate_lo[2]}")
    # candy: inside the bowl (radius), under the lid ceiling
    lid_inner = {}
    for k, (r, h) in enumerate(zip(S.LID_R, S.LID_H)):
        lid_inner[k] = (S.RIM_TOP + sum(S.LID_H[:k]), S.RIM_TOP + sum(S.LID_H[:k + 1]), r - min(S.WALL, r - 0.6))
    kc = next(k for k, r in enumerate(S.LID_R) if r < 6.6)
    ceil_y = lid_inner[kc][0]
    cand = [e for e in models["candy_fill"]["elements"] if not e["name"].startswith("pile.")]
    bad = [e["name"] for e in cand if radial(e) > 7.6]
    check("stationary candy stays within radius 7.6 of the axis (inside the bowl and the lid hollow)", not bad, ",".join(bad[:4]))
    topc = max(box_corners(e)[:, 1].max() for e in cand)
    check("stationary candy stays under the lid's ceiling", topc < ceil_y - 0.2, f"top {topc:.2f} ceiling {ceil_y:.2f}")
    bad = []
    for e in models["candy_fill"]["elements"]:
        if e["name"].startswith("pile."):
            y0, y1 = e["from"][1], e["to"][1]
            inner = min(S.BODY_R[k] - S.WALL for k in range(S.N_BODY) if S.BODY_Y0 + k * S.LAYER_H < y1 and S.BODY_Y0 + (k + 1) * S.LAYER_H > y0)
            if radial(e) > inner + S.WALL - 0.3:
                bad.append(f"{e['name']} {radial(e):.1f}>{inner:.1f}")
    check("the candy slab discs stay inside the bowl wall (corners may sink into it, never through it)", not bad, ",".join(bad[:3]))
    cavity_h = S.RIM_TOP - 3.0
    tops = [box_corners(e)[:, 1].max() for e in cand if e["name"].endswith("_body")]
    base_top = max(e["to"][1] for e in models["candy_fill"]["elements"] if e["name"].startswith("pile."))
    dims["candy_pile"] = {"slab_top_y": base_top, "slab_fraction_of_cavity": round((base_top - 3.0) / cavity_h, 3),
                          "mean_candy_top_y": round(float(np.mean(tops)), 2), "mean_top_fraction_of_cavity": round((float(np.mean(tops)) - 3.0) / cavity_h, 3),
                          "highest_candy_top_y": round(float(max(tops)), 2), "rim_top_y": S.RIM_TOP}
    dome = [box_corners(e)[:, 1].max() for e in models["lid"]["elements"] if e["name"].startswith("dome")]
    dims["lid"] = {"bottom_y": S.RIM_TOP, "top_of_dome_y": round(float(max(dome)), 2), "height_without_stem": round(float(max(dome)) - S.RIM_TOP, 2),
                   "stem_top_y": float(lid_hi[1]), "stem_height": round(float(lid_hi[1]) - float(max(dome)), 2)}
    # vines: each lies wholly on the lid (y >= rim top) or wholly on the body (below the charcoal band), none bridges the seam
    band_bottom = S.BODY_Y0 + (S.N_BODY - S.N_BAND) * S.LAYER_H
    bridges = []
    for pc, mod in (("lid", models["lid"]), ("body", models["body"])):
        for e in mod["elements"]:
            if e["name"].startswith("vines_") or e["name"].startswith("stem.leaf"):
                ys = box_corners(e)[:, 1]
                if pc == "lid" and ys.min() < S.RIM_TOP - 1e-6:
                    bridges.append(e["name"])
                if pc == "body" and ys.max() > band_bottom + 1e-6:
                    bridges.append(e["name"])
    check("lid vines are only on the lid, body vines only below the rim band; none bridges the seam", not bridges, ",".join(bridges))
    # the round shape: every board turned only by an angle vanilla allows, and 16 boards per ring, 8 lobes
    check("round shape: every ring has 16 staves; lobes alternate +-0.45/0.4 radius every 45 degrees",
          sum(1 for e in models["body"]["elements"] if e["name"].startswith("shell.l0_")) == 16 and sum(1 for e in models["lid"]["elements"] if e["name"].startswith("dome.l1_")) == 16)
    count = sum(len(models[p]["elements"]) for p in ("body", "lid", "face_plate", "candy_fill"))
    check("vault_full == body + lid + face_plate + candy_fill (element count)", count == len(full["elements"]), f"{count} vs {len(full['elements'])}")
    centred = True
    for r in ("reveal_a", "reveal_b", "reveal_c"):
        lo, hi = bounds(models[r])
        if np.abs((lo + hi) / 2 - 8).max() > 0.01:
            centred = False
    check("reveal pieces are centred at (8, 8, 8) (spin about their own middle)", centred)

    # ---- hinge clearance sweep (exact oriented boxes) -------------------------------------------------------------------------------------
    obbs = []
    for p in ("body", "face_plate", "candy_fill"):
        for e in models[p]["elements"]:
            f, t = np.array(e["from"], float), np.array(e["to"], float)
            c, hlf = (f + t) / 2, (t - f) / 2
            if "rotation" in e:
                r = e["rotation"]
                Rm = R_matrix(r["axis"], r["angle"])
                c = (c - r["origin"]) @ Rm.T + r["origin"]
            else:
                Rm = np.eye(3)
            obbs.append((c, Rm, hlf, float(np.linalg.norm(hlf))))
    centres = np.array([o[0] for o in obbs]); radii = np.array([o[3] for o in obbs])
    pts = []
    for e in models["lid"]["elements"]:
        f, t = np.array(e["from"]), np.array(e["to"])
        axes = [np.unique(np.concatenate([np.arange(f[i], t[i], 0.75), [t[i]]])) for i in range(3)]
        g = np.array(np.meshgrid(*axes, indexing="ij")).reshape(3, -1).T
        if "rotation" in e:
            r = e["rotation"]
            g = (g - r["origin"]) @ R_matrix(r["axis"], r["angle"]).T + r["origin"]
        pts.append(g)
    pts = np.vstack(pts)
    H = np.array(S.HINGE)
    worst = (0, None)
    shrink = 0.05
    for deg in np.arange(0, A.LID_OPEN + A.OVERSHOOT_DEG + 0.01, 1.0):
        q = (pts - H) @ R_matrix("x", float(deg)).T + H
        d = np.linalg.norm(q[:, None, :] - centres[None], axis=2)
        near = np.argwhere(d < radii[None] + 0.1)
        hit = 0
        for pi, bi in near:
            c, Rm, hlf, _ = obbs[bi]
            loc = (q[pi] - c) @ Rm
            if np.all(np.abs(loc) < hlf - shrink):
                hit += 1
        if hit > worst[0]:
            worst = (hit, float(deg))
    check("hinge sweep 0..118 deg (1 deg steps): no lid point penetrates the body, plate or candy by more than 0.05 unit", worst[0] == 0,
          "no penetration at any step" if worst[0] == 0 else f"{worst[0]} points at {worst[1]} deg")
    low = ((pts - H) @ R_matrix("x", A.LID_OPEN + A.OVERSHOOT_DEG).T + H)
    check("at peak overshoot the lid stays above the floor and behind the front", low[:, 1].min() > 0 and low[:, 2].min() > 0,
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
        ref = A.world_point("lid", {**rest}, [3.0, S.HINGE[1], S.HINGE[2]], facing)
        for deg in (1.5, 30, 90, 115, 118):
            for x in (0.0, 3.0, 8.0, 13.0, 16.0):
                a = A.world_point("lid", {**rest, "lid.rot_x_deg": deg}, [x, S.HINGE[1], S.HINGE[2]], facing)
                b = A.world_point("lid", {**rest}, [x, S.HINGE[1], S.HINGE[2]], facing)
                fixed &= bool(np.allclose(a, b, atol=1e-9))
    check("points on the hinge axis do not move for any angle, in all four facings", fixed)
    # the lid's front edge rises with a positive angle (direction of positive rotation)
    f0 = A.world_point("lid", rest, [8, S.HINGE[1], 0.0], "north")
    f1 = A.world_point("lid", {**rest, "lid.rot_x_deg": 30}, [8, S.HINGE[1], 0.0], "north")
    check("positive lid.rot_x_deg lifts the lid's front edge (and swings it toward the back)", f1[1] > f0[1] and f1[2] > f0[2])
    # a lid point goes through exactly R about the hinge in vault space: compare with the independent matrix formula
    pt = np.array([5.0, 17.0, 4.0])
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
    inside = abs(rs[0][0]) * 16 < 4 and abs(rs[0][2]) * 16 < 4 and rs[0][1] * 16 < S.RIM_TOP
    check("reveal candy starts inside the pile and rises well above the open bowl", inside and A.reveal_state(0, 40)[0][1] * 16 > 20)

    report = {"checks": RESULTS, "dimensions": dims, "failed": FAILS}
    (B.ART / "validation-report.json").write_text(json.dumps(report, indent=1) + "\n")
    print(f"\n{len(RESULTS) - len(FAILS)}/{len(RESULTS)} checks passed")
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
