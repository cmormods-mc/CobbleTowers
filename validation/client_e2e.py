#!/usr/bin/env python3
"""A real client against a real server, end to end: the Rental Draft as a player would do it (P33), with pictures.

Boots the rig's server with this build, starts a real Fabric client (see client_launch.py) that joins it, and drives the client through
ClientRemote (a command file the client polls): the player types /tower commands, the server sends the real payloads, the real
RentalPackScreen opens, the script clicks the pack and cards and presses Keep, and the finished team starts a run. Pictures are saved
at each step, and what the server did is checked over RCON.

  * the client receives the playlist state and the rental draft payload and opens the pack screen (not the chat fallback);
  * a pick made on the screen reaches the server and moves the draft on, three times;
  * the finished draft starts a run whose party is six rentals, built from what the player kept;
  * ending the run puts the player's own Pokemon back.

    python validation/client_e2e.py --server-dir <rig>/testserver-181 --java <jdk21 java> --out <dir> [--gui-scale 3]

The window opens on the desktop for a few minutes (sound is muted). Needs a display and a GPU.
"""

from __future__ import annotations

import argparse
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "smoke"))

import client_launch  # noqa: E402
from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, server_port,
)

PLAYER = "ScreenShots"
TOWER = "cobbletowers:test"
UUID_RE = r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})"
LIVE_STATES = ("ENCOUNTER_ACTIVE", "RECOVERY_REQUIRED", "ALLOCATING_INSTANCE", "PREPARING", "FLOOR_READY")


class Remote:
    """Writes commands for the client and waits for it to say it has done them."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self.commands = directory / "commands.txt"
        self.ack = directory / "ack.txt"
        self.sent = 0
        directory.mkdir(parents=True, exist_ok=True)
        self.commands.write_text("", encoding="utf-8")

    def send(self, line: str, wait: bool = True, seconds: int = 60) -> None:
        with self.commands.open("a", encoding="utf-8") as handle:
            handle.write(line + "\n")
        self.sent += 1
        if not wait:
            return
        deadline = time.time() + seconds
        while time.time() < deadline:
            try:
                done = len([x for x in self.ack.read_text(encoding="utf-8").splitlines() if x.strip()])
            except OSError:
                done = 0
            if done >= self.sent:
                return
            time.sleep(0.25)
        raise TimeoutError(f"the client never did '{line}'")

    def shot(self, name: str) -> bool:
        self.send(f"shot {name}")
        return (self.directory / f"{name}.png").exists()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=Path(os.environ.get("SMOKE_JAVA", "java")))
    parser.add_argument("--jar", type=Path, default=None, help="the CobbleTowers jar (default: the one in build/libs)")
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--gui-scale", type=int, default=3)
    parser.add_argument("--cards", action="store_true",
                        help="install CobblemonCards (and the Accessories and owo-lib mods it needs) on the server and the client: "
                             "the pack screen draws real cards, and completing the run grants real ones")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    tower_jar = args.jar or client_launch.first(client_launch.ROOT / "build" / "libs", "CobbleTowers-*[!s].jar")
    install_jar(server_dir, tower_jar.resolve())
    reset_tower_world(server_dir)

    results: list[Result] = []
    server = Server(server_dir, args.java)
    card_jars = client_launch.card_mods(server_dir) if args.cards else []
    on_server = [server_dir / "mods" / jar.name for jar in card_jars]
    for jar in on_server:
        jar.unlink(missing_ok=True)
    client: subprocess.Popen | None = None
    password = read_password(server_dir)
    port = server_port(server_dir)

    def rcon_run(command: str) -> str:
        with Rcon("127.0.0.1", 25575, password) as rcon:
            return rcon.command(command)

    try:
        print("Booting the server" + (" with CobblemonCards" if args.cards else ""))
        for jar in card_jars:
            shutil.copy2(jar, server_dir / "mods" / jar.name)
        server.start()
        server.wait_until_ready()

        game = out.parent / "clientrig"
        client_launch.prepare(game, server_dir, tower_jar, args.gui_scale, card_jars)
        env = dict(os.environ, COBBLETOWERS_REMOTE=str(out))
        remote = Remote(out)
        print("Launching the client")
        client = subprocess.Popen(client_launch.command(args.java, game, ["--quickPlayMultiplayer", f"127.0.0.1:{port}"],
                                                                        server_dir if args.cards else None), cwd=game, env=env,
                                  stdout=open(game / "client_output.log", "w", encoding="utf-8", errors="replace"),
                                  stderr=subprocess.STDOUT)
        joined = False
        for _ in range(240):
            if PLAYER in rcon_run("list"):
                joined = True
                break
            time.sleep(1)
        results.append(Result("the real client joins the real server", joined, "never joined"))
        if not joined:
            raise RuntimeError("the client never joined")
        time.sleep(6)

        with Rcon("127.0.0.1", 25575, password) as rcon:
            clear_tower(rcon)
            if args.cards:
                rcon.command(f"clear {PLAYER} cobblemon-cards:card")   # a player keeps their inventory between runs of this script
            rcon.command(f"pokegiveother {PLAYER} glaceon level=100")
            for _ in range(5):
                rcon.command(f"pokegiveother {PLAYER} magikarp level=1")
        time.sleep(2)

        # ---- the lobby, and the draft opening on the real screen -----------------------------------------------
        remote.send(f"cmd tower tower {TOWER}")
        remote.send("wait 1200")
        remote.send("cmd tower playlist rental")
        remote.send("wait 1200")
        remote.send("cmd tower")                      # the Hall (the warm-pixel menu) ...
        remote.send("wait 1500")
        remote.send("press Lobby")                    # ... and its Lobby button: the play screen, with the real lobby state
        remote.send("wait 1200")
        results.append(Result("the play screen shows the lobby from the real server", remote.shot("e2e_01_play_screen"), ""))
        # Nothing opens until the host confirms the mode; the play screen's Confirm mode button then opens the pack screen itself.
        remote.send("press Draft your team")          # not there yet: refused, nothing opens
        remote.send("wait 500")
        remote.shot("e2e_01b_before_confirm")
        client_log = game / "logs" / "latest.log"
        refused = "No active button labelled 'Draft your team'" in client_log.read_text(encoding="utf-8", errors="replace")
        results.append(Result("there is no Draft button, and no draft screen, before the mode is confirmed", refused, "the button was there"))
        remote.send("press Confirm mode")
        remote.send("wait 1500")
        results.append(Result("'Confirm mode' opens the pack screen from a real payload", remote.shot("e2e_02_pack_table"), ""))

        for pack in range(3):
            remote.send("center")                     # tear the pack open
            remote.send("wait 5600")                  # the tear and the five-card reveal
            remote.shot(f"e2e_{3 + pack * 2:02d}_pack{pack + 1}_cards")
            if args.cards and pack == 0:
                remote.send("press Cards:")           # the plain text cards, for comparison
                remote.send("wait 400")
                remote.shot("e2e_04_pack1_plain_look")
                remote.send("press Cards:")           # and back to the collection look
                remote.send("wait 400")
            remote.send("card 0")
            remote.send("card 1")
            remote.send("wait 300")
            remote.send("press Keep these two")
            remote.send("wait 1500")
        results.append(Result("three packs of picks reach the server and the team is drafted", remote.shot("e2e_09_team"), ""))
        remote.send("press Done")
        remote.send("wait 500")
        remote.send("cmd tower ready")                # a drafted player readies up; the host cannot start before
        remote.send("wait 800")

        # ---- the run ---------------------------------------------------------------------------------------------
        remote.send("cmd tower confirm")
        remote.send("cmd tower start")
        started = ""
        for _ in range(60):
            listing = rcon_run("cobbletowers runs list")
            for match in re.finditer(UUID_RE + r"\s+\S+\s+(\w+)\s+floor", listing):
                if match.group(2) in LIVE_STATES:
                    started = match.group(1)
            if started:
                break
            time.sleep(1)
        results.append(Result("a team drafted on the real screen starts a run", bool(started), rcon_run("cobbletowers runs list")[:200]))
        time.sleep(4)
        remote.shot("e2e_10_in_the_tower")
        text = rcon_run(f"cobbletowers play rentals {PLAYER}")
        rentals = len(re.findall(r"RENTAL " + UUID_RE, text))
        results.append(Result("the run's party is the six Pokemon the player kept", rentals == 6, text[:300]))
        # ---- the intermission as the real client shows it, and the vendor ---------------------------------------
        if started:
            rcon_run(f"cobbletowers runs advance {started} encounter_resolved_cleared")
            rcon_run(f"cobbletowers runs advance {started} rewards_banked")
            time.sleep(5)
            results.append(Result("the intermission screen opens on the real client", remote.shot("e2e_11_intermission_screen"), ""))
            # The modifier draft: choosing a card only marks it; Confirm is what sends the vote, and the server records it.
            show = f"cobbletowers runs show {started}"
            before = rcon_run(show)
            if "OPEN, 0 vote(s)" in before:
                remote.send("wait 1800")                  # the shutters
                remote.send("widget ModifierCardButton")  # chooses the first card
                remote.send("wait 500")
                remote.shot("e2e_11b_card_selected")
                chosen = rcon_run(show)
                results.append(Result("choosing a card sends nothing to the server", "OPEN, 0 vote(s)" in chosen, chosen[-200:]))
                remote.send("press Confirm")
                remote.send("wait 1500")
                # With one player the first vote settles the draft (and an event draft may chain behind it), so the proof is the
                # server's own "drafted <card>" line for the card that was chosen, not the open count of whatever opened next.
                first = re.search(r"\[(cobbletowers:[^,\]]+)", before)
                drafted = bool(first) and f"drafted {first.group(1)} at floor" in server.read_log()
                results.append(Result("Confirm sends the vote and the server settles on the chosen card", drafted, rcon_run(show)[-200:]))
                remote.shot("e2e_11c_after_confirm")
            else:
                results.append(Result("a modifier draft is open at the intermission", False, before[-300:]))
            remote.send("press Close")
            remote.send("wait 1500")
            remote.shot("e2e_12_intermission_world")
            remote.send("cmd tower")                    # back to the intermission menu
            remote.send("wait 1200")
            remote.send("press Vendor")
            remote.send("wait 1500")
            results.append(Result("the vendor screen opens from the real catalogue", remote.shot("e2e_13_vendor_screen"), ""))
            remote.send("press Close")
            remote.send("wait 800")
        if started and args.cards:
            # Finish the run: floors 2 to 4 by operator event, so the run COMPLETES and the cards are granted.
            for floor in range(2, 5):
                rcon_run(f"execute as {PLAYER} run cobbletowers play pick 1")
                rcon_run(f"execute as {PLAYER} run cobbletowers play ready")
                for _ in range(90):
                    if re.search(re.escape(started) + r"\s+\S+\s+ENCOUNTER_ACTIVE", rcon_run("cobbletowers runs list")):
                        break
                    time.sleep(1)
                rcon_run(f"cobbletowers runs advance {started} encounter_resolved_cleared")
                if floor == 4:
                    rcon_run(f"cobbletowers runs advance {started} final_floor_cleared")
                else:
                    rcon_run(f"cobbletowers runs advance {started} rewards_banked")
                    time.sleep(3)
            time.sleep(1.2)   # the reveal opens as the run completes, and the exit home closes it a few seconds later
            results.append(Result("the real reward reveal names each real card", remote.shot("e2e_15_reward_reveal"), ""))
            time.sleep(4)
            remote.send("close")
            remote.send("wait 600")
            remote.send("inventory")
            remote.send("wait 1500")
            results.append(Result("the real cards are in the player's inventory, drawn by the mod", remote.shot("e2e_16_inventory_cards"), ""))
            remote.send("close")
            held = rcon_run(f"data get entity {PLAYER} Inventory")
            cards = len(re.findall(r'cobblemon-cards:card"', held))
            results.append(Result("six real cards were granted for completing the run", cards == 6, f"{cards} card item(s)"))
        elif started:
            rcon_run(f"cobbletowers runs advance {started} abandon_requested")
        time.sleep(8)
        left = rcon_run(f"cobbletowers play rentals {PLAYER}")
        results.append(Result("ending the run deletes the rentals", "0 rentals" in left, left[:200]))
        remote.shot("e2e_14_after")
        remote.send("quit", wait=False)
    except Exception as exc:  # noqa: BLE001
        import traceback
        traceback.print_exc()
        results.append(Result("client end-to-end run", False, repr(exc)))
    finally:
        if client is not None:
            try:
                client.wait(timeout=20)
            except subprocess.TimeoutExpired:
                client.kill()
        server.stop()
        for jar in on_server:
            jar.unlink(missing_ok=True)

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name.ljust(width)}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed; pictures are in {out}")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
