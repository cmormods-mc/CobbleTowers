"""Drives a REAL vanilla Minecraft 1.21.1 client (no client mod; only the Polymer resource pack from the server) around the vault and takes
screenshots: closed, open, the four facings and an animation spot-check. Needs a running scratch server with CobbleTowers (console fed from
a file) and the client logged in as ShotCam under Xvfb :99. Paths come from the environment:  SP (scratchpad), OUT (screenshot dir)."""
import os, re, subprocess, sys, time

SP = os.environ["SP"]
OUT = os.environ["OUT"]
CONSOLE, LOG = f"{SP}/srv/console.txt", f"{SP}/live.log"
ENV = dict(os.environ, DISPLAY=":99")
os.makedirs(OUT, exist_ok=True)


def cmd(c, wait=0.35):
    with open(CONSOLE, "a") as f:
        f.write(c + "\n")
    time.sleep(wait)


def shot(name, crop="1280x720+0+0"):
    path = f"{OUT}/{name}.png"
    subprocess.run(["import", "-window", "root", "-crop", crop, "+repage", path], env=ENV, check=True)
    print("shot", name, flush=True)


def spawn(x, y, z, facing):
    n = os.path.getsize(LOG)
    cmd(f"execute in minecraft:overworld positioned {x} {y} {z} run cobbletowers vault spawn {facing}", 1.2)
    with open(LOG, "rb") as f:
        f.seek(n)
        m = re.search(r"Vault ([0-9a-f-]{36}) spawned", f.read().decode("utf8", "replace"))
    assert m, "no vault id in the log"
    return m.group(1)


def cam(x, y, z, yaw, pitch):
    cmd(f"tp ShotCam {x} {y} {z} {yaw} {pitch}", 0.9)


def setup():
    cmd("execute in minecraft:overworld run forceload add -20 -20 20 20", 1.0)
    cmd("execute in minecraft:overworld run tp ShotCam 0.5 101 8.5 180 10", 6.0)
    for c in ("op ShotCam", "gamemode creative ShotCam", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "time set 6000", "weather clear",
              "gamerule doMobSpawning false", "gamerule randomTickSpeed 0",
              "execute in minecraft:overworld run fill -16 99 -16 16 99 16 minecraft:smooth_stone",
              "execute in minecraft:overworld run fill -16 100 -16 16 114 16 minecraft:air"):
        cmd(c, 0.5)


def facing_vec(f):
    return {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}[f]


def yaw_to_face(dx, dz):
    import math
    return math.degrees(math.atan2(-dx, dz))


if __name__ == "__main__":
    setup()
    if os.environ.get("TOGGLE_HUD"):
        subprocess.run(["xdotool", "key", "F1"], env=ENV)
        time.sleep(0.5)
    vid = spawn(0, 100, 0, "south")
    cam(0.5, 101.4, 2.6, 180, 14)
    time.sleep(2.0)
    shot("01_closed_front_south")
    cam(-1.9, 101.7, 2.6, yaw_to_face(2.4, -2.1), 17)
    time.sleep(1.5)
    shot("02_closed_three_quarter")
    cmd(f"cobbletowers vault start {vid} treat", 0.2)
    t0 = time.time()
    time.sleep(2.7)
    shot("03_open_three_quarter_with_reveal")
    cam(0.5, 101.5, 3.9, 180, 24)
    time.sleep(0.6)
    shot("04_open_front")
    time.sleep(2.5)
    shot("05_after_close")
    # animation spot checks: unlock/open/close at roughly 0.15 s steps (screen grabs are not tick-exact)
    cam(-1.9, 101.7, 2.6, yaw_to_face(2.4, -2.1), 17)
    time.sleep(1.0)
    cmd(f"cobbletowers vault reset {vid}", 0.5)
    cmd(f"cobbletowers vault start {vid} trick", 0.0)
    t0 = time.time()
    for i in range(18):
        shot(f"anim/f{i:02d}_t{int((time.time() - t0) * 1000):04d}ms", crop="1280x720+0+0")
    time.sleep(1.0)
    cmd(f"cobbletowers vault remove {vid}", 0.5)
    # four facings: the camera stands in front of the vault each time, so the plate must face the camera in all four
    for f in ("north", "east", "south", "west"):
        v = spawn(0, 100, -10, f)
        dx, dz = facing_vec(f)
        cx, cz = 0.5 + dx * 3.9, -9.5 + dz * 3.9
        cam(cx, 101.5, cz, yaw_to_face(-dx, -dz), 14)
        time.sleep(1.8)
        shot(f"06_facing_{f}_closed")
        cmd(f"cobbletowers vault start {v} treat", 0.2)
        time.sleep(2.8)
        shot(f"07_facing_{f}_open")
        cmd(f"cobbletowers vault remove {v}", 0.6)
    cmd("cobbletowers vault list", 0.5)
    print("scene-done")
