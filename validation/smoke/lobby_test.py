#!/usr/bin/env python3
"""Proves P16's player-facing entry against a real server: a team forms and a run really starts.

Before P16 only an operator's `runs create` made a run. Here two real (headless) players do it the way a
player would, through `/cobbletowers play ...` executed *as them* -- the same LobbyService methods the
play screen's buttons call. What this proves live:

  * a solo lobby with a bot that has no Pokemon does not start, and the team survives to retry;
  * an invitee who declines is dropped from the lobby;
  * a host and an accepted invitee start together, after the countdown, into a run that reaches
    ENCOUNTER_ACTIVE on floor 1 with both players in it;
  * starting early drops an invitee who never answered, and the run has one player.

The screen itself and the invite-lapse timer are not covered here: a headless bot cannot open a screen
and the lapse takes three minutes (TowerLobbyTest covers it with an injected clock).

    python validation/smoke/lobby_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)

STAMP = int(time.time()) % 100000
HOST = f"TLh{STAMP}"
GUEST = f"TLg{STAMP}"
EMPTY = f"TLe{STAMP}"
TOWER = "cobbletowers:neutral"
LEAD = "glaceon"
FILLERS = 5
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def start_bot(name: str, port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_online(rcon: Rcon, name: str, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if name in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def give_party(rcon: Rcon, name: str) -> None:
    rcon.command(f"pokegiveother {name} {LEAD} level=100")
    for _ in range(FILLERS):
        rcon.command(f"pokegiveother {name} magikarp level=1")


def as_player(rcon: Rcon, name: str, command: str) -> None:
    rcon.command(f"execute as {name} run cobbletowers play {command}")


def lobbies(rcon: Rcon) -> str:
    return rcon.command("cobbletowers play lobbies")


def runs(rcon: Rcon) -> list[str]:
    """Run lines from `runs list`: '<uuid> <tower> <STATE> floor N M player(s)'."""
    return [line.strip() for line in rcon.command("cobbletowers runs list").splitlines() if re.search(UUID_RE, line)]


def wait_for(predicate, seconds: int = 20) -> bool:
    for _ in range(seconds * 2):
        if predicate():
            return True
        time.sleep(0.5)
    return False


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
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
        port = server_port(server_dir)
        for name in (HOST, GUEST, EMPTY):
            bots.append(start_bot(name, port, node_modules, server_dir / "logs" / f"towers-lobby-{name}.log"))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in (HOST, GUEST, EMPTY):
                if not wait_online(rcon, name):
                    raise RuntimeError(f"{name} never joined")
            clear_tower(rcon)
            give_party(rcon, HOST)
            give_party(rcon, GUEST)  # EMPTY deliberately gets nothing

            # --- a player with no Pokemon cannot start, and the team survives -----------------
            as_player(rcon, EMPTY, f"tower {TOWER}")
            as_player(rcon, EMPTY, "confirm")
            as_player(rcon, EMPTY, "start")
            time.sleep(8)
            still = lobbies(rcon)
            results.append(Result("a player with no Pokemon does not start a run",
                                  not runs(rcon), "; ".join(runs(rcon))))
            results.append(Result("the lobby survives a refused start so the team can retry",
                                  "1 lobby" in still and "0 pending" in still and "counting" not in still, still.strip()[:200]))
            as_player(rcon, EMPTY, "leave")
            results.append(Result("the host ending the team clears it", "0 lobby" in lobbies(rcon), lobbies(rcon).strip()[:200]))

            # --- invite, decline, invite again, accept ------------------------------------------
            as_player(rcon, HOST, f"tower {TOWER}")
            as_player(rcon, HOST, f"invite {GUEST}")
            seen = lobbies(rcon)
            results.append(Result("an invite shows as pending", "1 ready, 1 pending" in seen, seen.strip()[:200]))
            as_player(rcon, GUEST, f"decline {HOST}")
            seen = lobbies(rcon)
            results.append(Result("declining drops the invitee", "1 ready, 0 pending" in seen, seen.strip()[:200]))
            as_player(rcon, HOST, f"invite {GUEST}")
            as_player(rcon, GUEST, f"accept {HOST}")
            seen = lobbies(rcon)
            results.append(Result("accepting puts the invitee on the team", "2 ready, 0 pending" in seen, seen.strip()[:200]))

            # --- start: countdown, then a real run on floor 1 -----------------------------------
            as_player(rcon, HOST, "confirm")
            as_player(rcon, HOST, "start")
            counting = lobbies(rcon)
            results.append(Result("starting begins a countdown first", "counting down" in counting, counting.strip()[:200]))
            started = wait_for(lambda: any("ENCOUNTER_ACTIVE" in line for line in runs(rcon)), seconds=40)
            lines = runs(rcon)
            results.append(Result("after the countdown a run reaches ENCOUNTER_ACTIVE on floor 1",
                                  started and any("floor 1" in line for line in lines), "; ".join(lines)))
            results.append(Result("both players are in the run",
                                  any("2 player(s)" in line for line in lines), "; ".join(lines)))
            results.append(Result("the lobby is gone once the run owns the team",
                                  "0 lobby" in lobbies(rcon), lobbies(rcon).strip()[:200]))
            match = re.search(UUID_RE, lines[0]) if lines else None
            shown = rcon.command(f"cobbletowers runs show {match.group(1)}") if match else ""
            results.append(Result("each player registered a six-Pokemon party",
                                  shown.count("6 registered") == 2, shown[-300:]))
            if match:
                rcon.command(f"cobbletowers runs advance {match.group(1)} abandon_requested")
            clear_tower(rcon)
            time.sleep(2)

            # --- starting early drops the unanswered ---------------------------------------------
            as_player(rcon, HOST, f"tower {TOWER}")
            as_player(rcon, HOST, f"invite {GUEST}")
            as_player(rcon, HOST, "confirm")
            as_player(rcon, HOST, "start")
            started = wait_for(lambda: any("ENCOUNTER_ACTIVE" in line for line in runs(rcon)), seconds=40)
            active = [line for line in runs(rcon) if "ENCOUNTER_ACTIVE" in line]
            results.append(Result("starting early runs without the invitee who never answered",
                                  started and any("1 player(s)" in line for line in active), "; ".join(runs(rcon))))
            for line in active:
                found = re.search(UUID_RE, line)
                if found:
                    rcon.command(f"cobbletowers runs advance {found.group(1)} abandon_requested")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("lobby run", False, repr(exc)))
    finally:
        for bot in bots:
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
