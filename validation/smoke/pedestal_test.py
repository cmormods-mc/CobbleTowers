#!/usr/bin/env python3
"""Proves P55's modifier pedestals against a real server: holograms stand near the vendor for an intermission on a
tower with pedestal_votes, a real right-click on the click box casts the vote, an outsider's click does not, and they
are gone once the next floor opens.

Needs the Halloween tower open outside its window: set SMOKE_JAVA_OPTS=-Dcobbletowers.ignoreWindows=true.

    python validation/smoke/pedestal_test.py --server-dir <rig>/testserver-181 --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import math
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)
from floor_encounter_test import give_party  # noqa: E402
from intermission_test import play, run_line, wait_state, clear_floor, UUID_RE  # noqa: E402

TOWER = "cobbletowers:halloween"
STAMP = int(time.time()) % 100000
A = f"TPa{STAMP}"
B = f"TPb{STAMP}"
C = f"TPc{STAMP}"
IN_TOWER = "execute in cobbletowers:tower"
VENDOR = "@e[type=minecraft:villager,tag=cobbletowers_vendor,limit=1]"


def count(rcon: Rcon, selector: str) -> int:
    found = re.search(r"count:\s*(\d+)", rcon.command(f"{IN_TOWER} if entity {selector}"))
    return int(found.group(1)) if found else 0


def wait_count(rcon: Rcon, selector: str, want: int, seconds: int = 15) -> int:
    deadline = time.time() + seconds
    value = count(rcon, selector)
    while value != want and time.time() < deadline:
        time.sleep(1)
        value = count(rcon, selector)
    return value


def card(index: int, part: str) -> str:
    # the details block is the third text piece of a card (line 2); the other parts have one entity each
    extra = ",tag=cobbletowers_pedestal_line_2" if part == "text" else ""
    return f"@e[tag=cobbletowers_pedestal_{part},tag=cobbletowers_pedestal_card_{index}{extra},limit=1]"


def casts(text: str) -> int:
    """How many filled vote pips the details block shows (the RCON reply may carry the glyph as a literal or an escape)."""
    return text.count("●") + text.count("\\u25cf")


def voted(text: str) -> bool:
    return casts(text) > 0


def waiting(text: str) -> bool:
    return casts(text) == 0


def data(rcon: Rcon, selector: str, path: str) -> str:
    return rcon.command(f"{IN_TOWER} run data get entity {selector} {path}")


def position(rcon: Rcon, selector: str) -> tuple[float, float, float] | None:
    found = re.findall(r"(-?\d+\.?\d*)d", data(rcon, selector, "Pos"))
    return (float(found[0]), float(found[1]), float(found[2])) if len(found) == 3 else None


def stand_by_card(rcon: Rcon, name: str, index: int) -> None:
    rcon.command(f"{IN_TOWER} as {card(index, 'hitbox')} at @s run tp {name} ~ ~ ~1.5")


def click(bot: subprocess.Popen) -> None:
    bot.stdin.write("use\n")
    bot.stdin.flush()


def log_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if "ignoreWindows" not in os.environ.get("SMOKE_JAVA_OPTS", ""):
        os.environ["SMOKE_JAVA_OPTS"] = (os.environ.get("SMOKE_JAVA_OPTS", "") + " -Dcobbletowers.ignoreWindows=true").strip()
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    node_modules = rig / "bot" / "node_modules"
    bots: dict[str, subprocess.Popen] = {}
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        for name in (A, B, C):
            handle = open(server_dir / "logs" / f"towers-pedestal-{name}.log", "w", encoding="utf-8", errors="replace")
            bots[name] = subprocess.Popen(["node", str(HERE / "pedestalbot.js"), name, str(server_port(server_dir))],
                                          stdin=subprocess.PIPE, stdout=handle, stderr=subprocess.STDOUT,
                                          env=env, text=True)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (A, B, C):
                for _ in range(90):
                    if name in rcon.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            rcon.command(f"op {C}")
            rcon.command(f"gamemode creative {C}")
            for name in (A, B):
                give_party(rcon, name)

            play(rcon, A, f"tower {TOWER}")
            play(rcon, A, f"invite {B}")
            play(rcon, B, f"accept {A}")
            play(rcon, A, "confirm")
            play(rcon, A, "start")

            if not wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=40, floor=1):
                raise RuntimeError("floor 1 never opened: " + run_line(rcon))
            run = re.search(UUID_RE, run_line(rcon)).group(1)
            results.append(Result("no pedestals stand during a floor", count(rcon, "@e[tag=cobbletowers_pedestal]") == 0,
                                  str(count(rcon, "@e[tag=cobbletowers_pedestal]"))))

            clear_floor(rcon, run)
            if not wait_state(rcon, "INTERMISSION", seconds=20, floor=1):
                raise RuntimeError("never reached the intermission: " + run_line(rcon))

            boxes = wait_count(rcon, "@e[tag=cobbletowers_pedestal_hitbox]", 3)
            results.append(Result("three click boxes stand", boxes == 3, str(boxes)))
            # per card: 3 text pieces (name, payout, details), 4 stand parts, 1 gem
            for part, each in (("text", 3), ("stand", 4), ("gem", 1)):
                n = count(rcon, f"@e[tag=cobbletowers_pedestal_{part}]")
                results.append(Result(f"{each} {part} entities per card stand", n == each * boxes, str(n)))
            results.append(Result("a pedestal is tagged with its run",
                                  run in data(rcon, card(0, "hitbox"), "Tags"), data(rcon, card(0, "hitbox"), "Tags")))

            # Where they stand: next to the vendor, on the same floor (also proves the vendor is in the hall).
            vendor_at = position(rcon, VENDOR)
            box_at = position(rcon, card(1, "hitbox"))
            near = (vendor_at is not None and box_at is not None
                    and math.dist((vendor_at[0], vendor_at[2]), (box_at[0], box_at[2])) < 6.0
                    and abs(vendor_at[1] - box_at[1]) < 1.5)
            results.append(Result("the middle pedestal stands within 6 blocks of the vendor", near, f"{vendor_at} {box_at}"))

            text0 = data(rcon, card(0, "text"), "text")
            results.append(Result("the hologram offers a right-click", "Right-click" in text0, text0[:200]))
            results.append(Result("the hologram starts at no votes", waiting(text0), text0[:200]))

            # --- a participant's click votes -------------------------------------------------------
            stand_by_card(rcon, A, 0)
            time.sleep(1)
            click(bots[A])
            time.sleep(3)
            text = log_text(server.log)
            results.append(Result("a participant's click reaches the pedestal",
                                  re.search(rf"Pedestal of run {run}: {A} clicked card 0", text) is not None, text[-300:]))
            text0 = data(rcon, card(0, "text"), "text")
            results.append(Result("the hologram counts the vote", voted(text0), text0[:200]))

            # --- an outsider's click does not -------------------------------------------------------
            stand_by_card(rcon, C, 0)
            time.sleep(1)
            click(bots[C])
            time.sleep(3)
            text0 = data(rcon, card(0, "text"), "text")
            results.append(Result("an outsider's click adds no vote", casts(text0) == 1, text0[:200]))

            # --- the second vote settles the draft --------------------------------------------------
            stand_by_card(rcon, B, 0)
            time.sleep(1)
            click(bots[B])
            time.sleep(3)
            # Both voted: the draft settles, and an event room (P34b) may open a second round on the same pedestals.
            text0 = data(rcon, card(0, "text"), "text")
            results.append(Result("the hologram moves on once the draft settles (CHOSEN, or a fresh round)",
                                  "CHOSEN" in text0 or waiting(text0), text0[:200]))

            # --- the pedestals leave with the intermission ------------------------------------------
            for _ in range(2):
                play(rcon, A, "pick 1")
                play(rcon, B, "pick 1")
            play(rcon, A, "ready")
            play(rcon, B, "ready")
            opened = wait_state(rcon, "ENCOUNTER_ACTIVE", seconds=60, floor=2)
            results.append(Result("floor 2 opens", opened, run_line(rcon)))
            left = wait_count(rcon, "@e[tag=cobbletowers_pedestal]", 0)
            results.append(Result("the pedestals are gone once the next floor opens", left == 0, str(left)))
            text = log_text(server.log)
            results.append(Result("no pedestal fault was logged", "pedestal tick failed" not in text.lower()
                                  and "pedestals could not be spawned" not in text,
                                  "; ".join(l for l in text.splitlines() if "edestal" in l and "WARN" in l)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("pedestal run", False, repr(exc)))
    finally:
        for bot in bots.values():
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
