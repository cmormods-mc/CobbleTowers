#!/usr/bin/env python3
"""Commands that undo a tower are refused inside it, and /tower leave is the way out (P27).

A real (bot) player types the commands as chat, because the guard only looks at commands a player sends: RCON and the
console are exempt by design. The bot is not an operator.

  * outside the tower /pokeheal and /home are not refused by the guard (the control);
  * inside the tower dimension they are, with the sentence that says so;
  * /tower and /cobbletowers commands still work in there;
  * /tower leave during a live run ends the player's part in it and takes them home.

    python validation/smoke/command_guard_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, read_password, reset_tower_world, run_id_from, wait_online,
)
from floor_encounter_test import FIRST_MOVE, begin_floor, give_party, start_battle_bot, tell_bot  # noqa: E402

BOT = f"CG{int(time.time()) % 100000}"


def blocked_lines(rig: Path) -> list[str]:
    try:
        text = (rig / "bot" / f"{BOT}.log").read_text(encoding="utf-8", errors="replace")
    except FileNotFoundError:
        return []
    return [line for line in text.splitlines() if "BLOCKED" in line]


def say(rig: Path, text: str) -> None:
    tell_bot(rig, BOT, "SAY " + text)
    time.sleep(2.5)


def dimension(rcon: Rcon) -> str:
    return rcon.command(f"data get entity {BOT} Dimension")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    args = parser.parse_args()
    server_dir = args.server_dir.resolve()
    rig = server_dir.parent
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot: subprocess.Popen | None = None
    try:
        server.start()
        server.wait_until_ready()
        bot = start_battle_bot(rig, BOT, FIRST_MOVE)
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            if not wait_online(rcon, seconds=90) and BOT not in rcon.command("list"):
                raise RuntimeError(f"{BOT} never joined")
            clear_tower(rcon)
            give_party(rcon, BOT)
            rcon.command(f"deop {BOT}")

            say(rig, "/pokeheal")
            say(rig, "/home")
            results.append(Result("outside the tower the guard refuses nothing", not blocked_lines(rig),
                                  "; ".join(blocked_lines(rig))[:200]))

            run = run_id_from(rcon.command(f"cobbletowers runs create cobbletowers:neutral {BOT}"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            begin_floor(rcon, run)
            time.sleep(3)
            in_tower = "cobbletowers:tower" in dimension(rcon)
            results.append(Result("the player is inside the tower dimension", in_tower, dimension(rcon).strip()[:80]))

            for command in ("/pokeheal", "/home", "/tp @s 0 100 0", "/cobblemon:pokeheal"):
                before = len(blocked_lines(rig))
                say(rig, command)
                lines = blocked_lines(rig)
                results.append(Result(f"inside the tower {command} is refused, with the reason",
                                      len(lines) > before and "turned off inside the tower" in lines[-1],
                                      lines[-1] if lines else "no refusal"))

            before = len(blocked_lines(rig))
            say(rig, "/tower status")
            say(rig, "/cobbletowers play status")
            results.append(Result("/tower and /cobbletowers still work inside it", len(blocked_lines(rig)) == before,
                                  "; ".join(blocked_lines(rig)[before:])[:200]))

            say(rig, "/tower leave")
            time.sleep(8)
            home = "minecraft:overworld" in dimension(rcon)
            shown = rcon.command(f"cobbletowers runs show {run}")
            results.append(Result("/tower leave takes the player out of the tower", home, dimension(rcon).strip()[:80]))
            results.append(Result("and the run is over for them", any(s in shown for s in ("ABANDONED", "FAILED", "CASHED_OUT", "COMPLETED")),
                                  shown.strip()[:160]))
            bad = [line for line in server.read_log().splitlines() if "com.cobbletowers" in line and "ERROR" in line]
            results.append(Result("no CobbleTowers error", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("command guard run", False, repr(exc)))
    finally:
        if bot:
            bot.kill()
        server.stop()

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
