#!/usr/bin/env python3
"""A party of two drafts a Rental Draft team together and the tower starts: the flow a real group plays.

A host and a guest (headless players, through the same commands the play screen's buttons send) form a team, the host chooses the
rental mode and confirms it, both draft three packs, both ready up, and the host starts. What this proves live:

  * the guest's draft opens only after the host confirms (the mode is not decided before);
  * the host cannot start until both are ready, and can once they are;
  * after the countdown a run with both players reaches ENCOUNTER_ACTIVE, each with a rental team of six;
  * the lobby is gone once the run owns the team, so /tower puts nobody into a fresh lobby.

    python validation/smoke/rental_party_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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
from run_durability_test import Result, Server, clear_tower, install_jar, read_password, reset_tower_world, server_port  # noqa: E402

STAMP = int(time.time()) % 100000
HOST = f"TPh{STAMP}"
GUEST = f"TPg{STAMP}"
TOWER = "cobbletowers:test"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"


def start_bot(name: str, port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), name, str(port)], stdout=handle, stderr=subprocess.STDOUT, env=env)


def play(rcon: Rcon, name: str, command: str) -> str:
    return rcon.command(f"execute as {name} run cobbletowers play {command}")


def draft(rcon: Rcon, name: str) -> bool:
    """Three packs in chat, never keeping a legendary or mythic."""
    for _ in range(3):
        cards = re.findall(r"(\d)\. \[(\w+)\]", play(rcon, name, "draft"))
        picks = [n for n, rarity in cards if rarity not in ("legendary", "mythic")][:2]
        if len(cards) != 5 or len(picks) != 2:
            return False
        play(rcon, name, f"draft pick {picks[0]} {picks[1]}")
    return "Your team is drafted" in play(rcon, name, "draft")


def live_runs(rcon: Rcon) -> list[str]:
    return [line.strip() for line in rcon.command("cobbletowers runs list").splitlines()
            if re.search(UUID_RE, line) and "ENCOUNTER_ACTIVE" in line]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
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
        server.start()
        server.wait_until_ready()
        port = server_port(server_dir)
        for name in (HOST, GUEST):
            bots.append(start_bot(name, port, node_modules, server_dir / "logs" / f"towers-party-{name}.log"))
        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as r:
            for name in (HOST, GUEST):
                for _ in range(90):
                    if name in r.command("list"):
                        break
                    time.sleep(1)
                else:
                    raise RuntimeError(f"{name} never joined")
            time.sleep(3)
            clear_tower(r)
            for name in (HOST, GUEST):
                r.command(f"pokegiveother {name} glaceon level=100")

            play(r, HOST, f"tower {TOWER}")
            play(r, HOST, f"invite {GUEST}")
            play(r, GUEST, f"accept {HOST}")
            play(r, HOST, "playlist rental")
            before = play(r, GUEST, "draft")
            results.append(Result("the guest's draft does not open before the host confirms the mode", "confirm the mode" in before, before.strip()[:160]))
            play(r, HOST, "confirm")
            results.append(Result("both players draft a team", draft(r, HOST) and draft(r, GUEST), "a draft failed"))
            again = play(r, HOST, f"tower {TOWER}")
            results.append(Result("choosing the same tower again does not reset the team (drafts, mode and invitations stay)",
                                  "already your tower" in again and "Your team is drafted" in play(r, GUEST, "draft")
                                  and "Your team is drafted" in play(r, HOST, "draft") and "confirm the mode" not in play(r, GUEST, "draft"),
                                  again.strip()[:160]))
            early = play(r, HOST, "start")
            results.append(Result("the host cannot start before everyone is ready", "ready up" in early and not live_runs(r), early.strip()[:200]))
            play(r, HOST, "ready")
            play(r, GUEST, "ready")
            started = play(r, HOST, "start")
            print("start says:", started.strip()[:120])
            ok = False
            for _ in range(60):
                runs = live_runs(r)
                if runs and "2 player" in runs[0]:
                    ok = True
                    break
                time.sleep(1)
            results.append(Result("after the countdown a run with both players is live", ok,
                                  "; ".join(rcon_line.strip() for rcon_line in r.command("cobbletowers runs list").splitlines())[:300]))
            lobbies = r.command("cobbletowers play lobbies")
            results.append(Result("the lobby is gone once the run owns the team", "0 lobby" in lobbies, lobbies.strip()[:160]))
            for name in (HOST, GUEST):
                text = r.command(f"cobbletowers play rentals {name}")
                results.append(Result(f"{name[2]} holds a rental team of six", len(re.findall(r"RENTAL " + UUID_RE, text)) == 6, text[:200]))
    except Exception as exc:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        results.append(Result("rental party run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        log = server.read_log()
        server.stop()
    print()
    for line in log.splitlines():
        if re.search(r"Cannot start|could not start|lobby tick failed|Lent |tower lobby", line):
            print("  S>", line[:220])
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<70} {'' if result.passed else result.detail}")
    failed = [x for x in results if not x.passed]
    print(f"\n{len(results) - len(failed)}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
