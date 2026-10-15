#!/usr/bin/env python3
"""Proves the mastery badge (P47) against a real server (dev jar; Polymer and WebGUI in mods/).

The badge is the icon of the tower with the highest mastery level, then that level, in front of the name:

  * a player with no level has no badge;
  * a few achievements in one tower give that tower's icon and level;
  * a higher level in another tower takes over, and a tie goes to the tower whose achievement is newest;
  * /tower badge off hides it (and survives nothing else), on brings it back;
  * the display name and the tab-list name carry it, and a reset removes it;
  * "mastery_badge": false in config/cobbletowers-cosmetics.json switches it off after a restart.

    python validation/smoke/mastery_badge_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from run_durability_test import Result, Server, install_jar, reset_tower_world, read_password, server_port  # noqa: E402

BOT = f"MBa{int(time.time()) % 100000}"
NEUTRAL, ROOTVALE = "cobbletowers:neutral", "cobbletowers:rootvale"
ICON = {"neutral": "", "rootvale": ""}


def decoration(rcon: Rcon) -> str:
    text = rcon.command(f"cobbletowers cosmeticsadmin names {BOT}")
    match = re.search(r"decoration=\[(.*)\]", text, re.S)
    return match.group(1) if match else "?" + text


def level_of(rcon: Rcon, tower: str) -> int:
    match = re.search(r"is now level (\d+)", tower)
    return int(match.group(1)) if match else -1


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    config = server_dir / "config" / "cobbletowers-cosmetics.json"
    if config.exists():
        config.unlink()
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bots: list[subprocess.Popen] = []
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(server_dir.parent / "bot" / "node_modules"))
        handle = open(server_dir / "logs" / f"towers-badge-{BOT}.log", "w", encoding="utf-8", errors="replace")
        bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                                     stdout=handle, stderr=subprocess.STDOUT, env=env))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError(f"{BOT} never joined")
            rcon.command(f"cobbletowers masteryadmin reset {BOT} {NEUTRAL}")
            rcon.command(f"cobbletowers masteryadmin reset {BOT} {ROOTVALE}")

            results.append(Result("no level anywhere: no badge", ICON["neutral"] not in decoration(rcon) and ICON["rootvale"] not in decoration(rcon),
                                  repr(decoration(rcon))))

            one = rcon.command(f"cobbletowers masteryadmin grant {BOT} {NEUTRAL} challenger_2")
            deco = decoration(rcon)
            results.append(Result("one achievement: the neutral icon and level 1", deco.startswith(ICON["neutral"] + "1 "), f"{one.strip()} -> {deco!r}"))

            allr = rcon.command(f"cobbletowers masteryadmin grant {BOT} {ROOTVALE} all")
            top = level_of(rcon, allr)
            deco = decoration(rcon)
            results.append(Result("a higher level in another tower takes over", deco.startswith(ICON["rootvale"] + f"{top} "), f"{allr.strip()} -> {deco!r}"))

            alln = rcon.command(f"cobbletowers masteryadmin grant {BOT} {NEUTRAL} all")
            deco = decoration(rcon)
            results.append(Result("equal levels go to the tower whose achievement is newest (neutral, just granted)",
                                  level_of(rcon, alln) == top and deco.startswith(ICON["neutral"] + f"{top} "), f"{alln.strip()} -> {deco!r}"))

            names = rcon.command(f"cobbletowers cosmeticsadmin names {BOT}")
            results.append(Result("the display name and tab-list name carry the badge",
                                  names.count(ICON["neutral"]) >= 0 and re.search(r"display=\[[^\]]*" + ICON["neutral"], names) is not None
                                  and re.search(r"tab=\[[^\]]*" + ICON["neutral"], names) is not None, names.strip()[:200]))

            off = rcon.command(f"execute as {BOT} run cobbletowers play badge off")
            results.append(Result("/tower badge off hides it", ICON["neutral"] not in decoration(rcon) and "hidden" in off, f"{off.strip()} -> {decoration(rcon)!r}"))
            on = rcon.command(f"execute as {BOT} run cobbletowers play badge on")
            results.append(Result("/tower badge on brings it back", decoration(rcon).startswith(ICON["neutral"]) and "shown" in on, f"{on.strip()} -> {decoration(rcon)!r}"))

            rcon.command(f"cobbletowers masteryadmin reset {BOT} {NEUTRAL}")
            deco = decoration(rcon)
            results.append(Result("after a reset the next tower's badge shows", deco.startswith(ICON["rootvale"] + f"{top} "), repr(deco)))
            rcon.command(f"cobbletowers masteryadmin reset {BOT} {ROOTVALE}")
            results.append(Result("and with no levels left there is none", ICON["rootvale"] not in decoration(rcon) and ICON["neutral"] not in decoration(rcon),
                                  repr(decoration(rcon))))
            rcon.command(f"cobbletowers masteryadmin grant {BOT} {ROOTVALE} challenger_2")
        for bot in bots:
            bot.kill()
        bots.clear()

        # ---- the server switch -------------------------------------------------------------------------------
        print("Restarting with mastery_badge off")
        server.stop()
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text('{"mastery_badge": false}\n', encoding="utf-8")
        server = Server(server_dir, java)
        server.start()
        server.wait_until_ready()
        handle = open(server_dir / "logs" / f"towers-badge2-{BOT}.log", "w", encoding="utf-8", errors="replace")
        bots.append(subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                                     stdout=handle, stderr=subprocess.STDOUT, env=env))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            deco = decoration(rcon)
            results.append(Result("with the switch off a level shows no badge", ICON["rootvale"] not in deco, repr(deco)))
            reply = rcon.command(f"execute as {BOT} run cobbletowers play badge")
            results.append(Result("and /tower badge says the server has it off", "switched off" in reply, reply.strip()[:200]))
        bad = [l for l in server.read_log().splitlines() if "ERROR" in l and "cobbletowers" in l.lower()]
        results.append(Result("no CobbleTowers error was logged", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("mastery badge", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        if config.exists():
            config.unlink()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  {'' if result.passed else result.detail}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
