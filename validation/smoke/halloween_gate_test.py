#!/usr/bin/env python3
"""The event tower must not show or start outside its window (P54). Run against the RELEASE jar, with no ignoreWindows flag:

1. A player cannot pick Hollow's Eve (`play tower`): it answers "not open right now".
2. A normal tower can still be picked (the control).
3. The player's chat lists of towers do not name it (mastery overview), and its mastery page is refused.
4. Nothing of it stands in the tower dimension (pedestals, vendor, cells).
5. The boot log names no Halloween content problem.

    python validation/smoke/halloween_gate_test.py --server-dir <rig>/testserver-181 --java <jdk21 java> --jar <release jar>
"""

from __future__ import annotations

import argparse
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
    RCON_PORT, Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)
from intermission_test import play  # noqa: E402

BOT = f"THg{int(time.time()) % 100000}"
EVENT = "cobbletowers:halloween"


def say(rcon: Rcon, command: str) -> str:
    return rcon.command(f"execute as {BOT} run cobbletowers {command}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = server_dir.parent / "bot" / "node_modules"
    os.environ["SMOKE_JAVA_OPTS"] = " ".join(o for o in os.environ.get("SMOKE_JAVA_OPTS", "").split() if "ignoreWindows" not in o)
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-hgate-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))], stdout=handle, stderr=subprocess.STDOUT, env=env)

        with Rcon("127.0.0.1", RCON_PORT, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("bot never joined")
            clear_tower(rcon)

            reply = say(rcon, f"play tower {EVENT}")
            results.append(Result("picking the event tower outside its window is refused", "not open" in reply.lower(), reply.strip()[:200]))
            control = say(rcon, "play tower cobbletowers:neutral")
            results.append(Result("a normal tower can still be picked", "not open" not in control.lower() and "no tower" not in control.lower(), control.strip()[:200]))
            play(rcon, BOT, "leave")

            overview = say(rcon, "play mastery")
            results.append(Result("the mastery overview does not name the event tower", "hollow" not in overview.lower() and "halloween" not in overview.lower(), overview.strip()[:300]))
            results.append(Result("the mastery overview does list the normal towers (the check is not vacuous)", "neutral" in overview.lower() or "tideforge" in overview.lower(), overview.strip()[:300]))
            detail = say(rcon, f"play mastery {EVENT}")
            results.append(Result("the event tower's mastery page is refused outside its window", "not open" in detail.lower(), detail.strip()[:200]))

            time.sleep(1)
            # what the event tower puts into the world during a run: its pedestals (text/item/block displays, an interaction box per card)
            # and the vendor; none may exist with no run. Anything else (a built structure's own decoration) is only reported.
            for selector in ("@e[tag=cobbletowers_pedestal]", "@e[type=minecraft:villager,tag=cobbletowers_vendor]", "@e[type=minecraft:text_display]",
                             "@e[type=minecraft:interaction]", "@e[type=minecraft:block_display]", "@e[type=minecraft:item_display]"):
                found = re.search(r"count:\s*(\d+)", rcon.command(f"execute in cobbletowers:tower if entity {selector}"))
                n = int(found.group(1)) if found else 0
                results.append(Result(f"nothing stands in the tower dimension for {selector}", n == 0, str(n)))
            census = {}
            for kind in ("minecraft:armor_stand", "minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting", "minecraft:villager",
                         "cobblemon:pokemon", "cobblemon:npc", "minecraft:marker", "minecraft:item", "minecraft:experience_orb"):
                found = re.search(r"count:\s*(\d+)", rcon.command(f"execute in cobbletowers:tower if entity @e[type={kind}]"))
                if found and int(found.group(1)):
                    census[kind] = int(found.group(1))
            total = re.search(r"count:\s*(\d+)", rcon.command("execute in cobbletowers:tower if entity @e[type=!minecraft:player]"))
            print("entities in the tower dimension:", total.group(1) if total else "none", "of which", census)

        text = server.log.read_text(encoding="utf-8", errors="replace")
        lines = [line for line in text.splitlines() if re.search(r"halloween|hollow", line, re.I)]
        results.append(Result("the boot log mentions Halloween only as loaded content", all("ERROR" not in line and "WARN" not in line for line in lines), "; ".join(lines)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("halloween gate run", False, repr(exc)))
    finally:
        if bot:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
