#!/usr/bin/env python3
"""Proves a checkpointed run survives the server being killed before its next autosave.

TowerRunStore has two write paths, and this is the one that makes the difference real: put() marks
the data dirty and Minecraft writes it at the next autosave, minutes away; checkpoint() writes it
now. Only killing a real server tells them apart. A clean stop saves everything and would pass on a
build with no checkpointing at all.

Two runs are driven, and the point is the contrast between them:

  A  create, then advance to INSTANCE_ALLOCATED, which the transition table checkpoints.
  B  create, then one move the table does NOT checkpoint.

Then the process is killed outright -- no stop, no save-all, no logout -- and the server restarted.
Recovery logs the state each run was recovered FROM, which is the observable that tells the two
apart: A must be recovered from PREPARING, because its checkpoint reached disk, and B from CREATED,
because its dirty move did not. B failing that way is not a bug: it is the documented cost of not
checkpointing, and it is what proves the flush is doing the work on A rather than an autosave that
happened to fire.

    python validation/smoke/run_durability_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
"""

from __future__ import annotations

import argparse
import os
import re
import socket
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402

# At most 16 characters. The login packet caps the name there, so a longer one is rejected by the
# server's decoder -- reported as "Failed to decode packet serverbound/minecraft:hello", which
# reads like a protocol or mod-version problem and is not one.
BOT = "TowerDuraBot"
TOWER = "cobbletowers:neutral"
BOOT_MARKER = re.compile(r'Done \([\d.]+s\)! For help')
BOOT_TIMEOUT_SECONDS = 420
RCON_PORT = 25575


@dataclass
class Result:
    name: str
    passed: bool
    detail: str = ""


class Server:
    def __init__(self, directory: Path, java: Path):
        self.directory = directory
        self.java = java
        self.log = directory / "logs" / "towers-durability.log"
        self.process: subprocess.Popen | None = None

    def start(self) -> None:
        # Back-to-back boots race the previous socket out of TIME_WAIT, and a bind failure reads
        # like a mod fault when it is not one. Probing bind rather than connect is the only probe
        # that sees a port held as the local end of somebody else's outbound connection.
        wait_for_free_port(server_port(self.directory))
        self.log.parent.mkdir(parents=True, exist_ok=True)
        if self.log.exists():
            self.log.unlink()
        launcher = self.directory / "fabric-server-launch.jar"
        if not launcher.exists():
            raise RuntimeError(f"no fabric-server-launch.jar in {self.directory}")
        handle = open(self.log, "w", encoding="utf-8", errors="replace")
        self.process = subprocess.Popen(
            [str(self.java), "-Xms2G", "-Xmx4G", "-jar", launcher.name, "nogui"],
            cwd=self.directory, stdout=handle, stderr=subprocess.STDOUT, stdin=subprocess.PIPE)

    def wait_until_ready(self) -> None:
        deadline = time.time() + BOOT_TIMEOUT_SECONDS
        while time.time() < deadline:
            if self.process and self.process.poll() is not None:
                raise RuntimeError(f"server exited with code {self.process.returncode} during boot; see {self.log}")
            if BOOT_MARKER.search(self.read_log()):
                return
            time.sleep(2)
        raise TimeoutError(f"server did not boot within {BOOT_TIMEOUT_SECONDS}s; see {self.log}")

    def read_log(self) -> str:
        try:
            return self.log.read_text(encoding="utf-8", errors="replace")
        except FileNotFoundError:
            return ""

    def stop(self) -> None:
        if not self.process or self.process.poll() is not None:
            return
        try:
            with Rcon("127.0.0.1", RCON_PORT, read_password(self.directory), timeout=15) as rcon:
                rcon.command("stop")
            self.process.wait(timeout=120)
        except Exception:  # noqa: BLE001
            self.process.kill()


def server_port(directory: Path) -> int:
    for line in (directory / "server.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("server-port="):
            return int(line.split("=", 1)[1])
    return 25565


def read_password(directory: Path) -> str:
    for line in (directory / "server.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("rcon.password="):
            return line.split("=", 1)[1]
    raise RuntimeError("no rcon.password in server.properties")


def wait_for_free_port(port: int, seconds: int = 120) -> None:
    deadline = time.time() + seconds
    while time.time() < deadline:
        # Dual-stack, because Minecraft binds "*" with V6ONLY off: an IPv4-only probe reports free
        # while an IPv6 conflict is exactly what stops the server.
        probe = socket.socket(socket.AF_INET6, socket.SOCK_STREAM)
        try:
            probe.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 0)
            probe.bind(("::", port))
            return
        except OSError:
            time.sleep(2)
        finally:
            probe.close()
    raise TimeoutError(f"port {port} never became bindable")


def install_jar(server_dir: Path, jar: Path) -> None:
    mods = server_dir / "mods"
    # Matched on this mod's own jar name, and only ours: a looser CobbleRaids-*.jar glob once
    # deleted the add-on data pack beside it in the raids rig.
    for existing in mods.glob("CobbleTowers-*.jar"):
        existing.unlink()
    (mods / jar.name).write_bytes(jar.read_bytes())
    print(f"Installed {jar.name}")


def start_bot(port: int, node_modules: Path, log: Path) -> subprocess.Popen:
    """Starts the bot with its output kept.

    It was discarded at first, and the one time it mattered the harness could only report that the
    bot never joined -- which is the least useful thing it knows. Whatever node says, say it.
    """
    env = dict(os.environ, NODE_PATH=str(node_modules))
    handle = open(log, "w", encoding="utf-8", errors="replace")
    return subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(port)],
                            stdout=handle, stderr=subprocess.STDOUT, env=env)


def wait_online(rcon: Rcon, seconds: int = 90) -> bool:
    for _ in range(seconds):
        if BOT in rcon.command("list"):
            return True
        time.sleep(1)
    return False


def run_id_from(created: str) -> str:
    match = re.search(r"Created run ([0-9a-f-]{36})", created)
    if not match:
        raise RuntimeError("could not read a run id out of: " + created.strip()[:200])
    return match.group(1)


def recovered_from(log: str, run_id: str) -> str:
    """The state recovery moved this run out of, as the transition service logged it."""
    match = re.search(rf"Run {re.escape(run_id)} (\w+) -> RECOVERY_REQUIRED", log)
    return match.group(1) if match else "<no recovery line>"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None, help="build to install into mods/ first")
    parser.add_argument("--node-modules", type=Path, default=None,
                        help="node_modules holding mineflayer (default: the CobbleRaids rig's, beside the server)")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())

    results: list[Result] = []
    server = Server(server_dir, java)
    bot: subprocess.Popen | None = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        bot_log = server_dir / "logs" / "towers-durability-bot.log"
        bot = start_bot(server_port(server_dir), node_modules, bot_log)

        with Rcon("127.0.0.1", RCON_PORT, read_password(server_dir)) as rcon:
            if not wait_online(rcon):
                said = bot_log.read_text(encoding="utf-8", errors="replace").strip() if bot_log.exists() else ""
                raise RuntimeError("the bot never joined; the run commands need a player selector. "
                                   f"node said: {said[-400:] or '<nothing>'}")

            loaded = rcon.command("cobbletowers definitions")
            results.append(Result("the bundled tower content loaded", TOWER.split(":")[1] in loaded,
                                  loaded.strip()[:200]))

            # A: create, then a move the table checkpoints.
            run_a = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            rcon.command(f"cobbletowers runs advance {run_a} party_submitted")
            rcon.command(f"cobbletowers runs advance {run_a} party_validated")
            allocated = rcon.command(f"cobbletowers runs advance {run_a} instance_allocated")
            results.append(Result("allocating an instance is checkpointed", "checkpoint" in allocated,
                                  allocated.strip()[:200]))

            # B: create, then a move it does not.
            run_b = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} {BOT}"))
            submitted = rcon.command(f"cobbletowers runs advance {run_b} party_submitted")
            results.append(Result("submitting a party is not checkpointed", "checkpoint" not in submitted,
                                  submitted.strip()[:200]))
            print(f"  run A {run_a} at PREPARING, run B {run_b} at VALIDATING_PARTY")

        print("Killing the server with no stop and no save")
        server.process.kill()
        server.process.wait(timeout=60)
        if bot is not None:
            bot.kill()
            bot = None

        print("Restarting")
        server.start()
        server.wait_until_ready()
        restart_log = server.read_log()
        with Rcon("127.0.0.1", RCON_PORT, read_password(server_dir)) as rcon:
            listing = rcon.command("cobbletowers runs list")
            show_a = rcon.command(f"cobbletowers runs show {run_a}")
            show_b = rcon.command(f"cobbletowers runs show {run_b}")
        print("  after restart: " + listing.strip()[:300])

        results.append(Result("both runs came back at all", run_a in listing and run_b in listing,
                              listing.strip()[:200]))
        results.append(Result("a checkpointed move survived the kill",
                              recovered_from(restart_log, run_a) == "PREPARING",
                              f"run A was recovered from {recovered_from(restart_log, run_a)}, expected PREPARING"))
        results.append(Result("the checkpoint key survived with it",
                              f"run:{run_a}:allocated" in show_a, show_a.strip()[:200]))
        results.append(Result("a move that did not checkpoint did not survive, which is what proves the flush",
                              recovered_from(restart_log, run_b) == "CREATED",
                              f"run B was recovered from {recovered_from(restart_log, run_b)}, expected CREATED"
                              " (VALIDATING_PARTY would mean an autosave fired and this test proved nothing)"))
        results.append(Result("an interrupted run is parked, not lost",
                              "RECOVERY_REQUIRED" in show_a and "RECOVERY_REQUIRED" in show_b,
                              show_a.strip()[:120] + " / " + show_b.strip()[:120]))
        results.append(Result("no CobbleTowers exception during boot or recovery",
                              "com.cobbletowers" not in restart_log.replace("com.cobbletowers.CobbleTowers", ""),
                              "see " + str(server.log)))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("durability run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()

    print()
    width = max(len(result.name) for result in results)
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  "
              f"{result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
