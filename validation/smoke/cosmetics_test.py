#!/usr/bin/env python3
"""Proves cosmetics, titles and the chat tag (P36d): the season banners arrive as real banner items, the first title earned is worn
automatically, /tower title changes it, the club tag and worn title are put in front of the player's display name and tab-list name (and so
appear in a chat line), the configured earn commands run once per newly earned cosmetic, and, with Placeholder API installed, the
%cobbletowers:...% placeholders are registered and resolve.

The calendar is pinned inside season 1 (Tideforge in the spotlight, so its banners are blue) and the track is crossed with the operator's
points command. The earn-command config is written before the server starts and removed afterwards.

    python validation/smoke/cosmetics_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>] [--placeholder-jar <jar>]
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, install_jar, reset_tower_world, read_password, server_port,
)

BOT = f"TCs{int(time.time()) % 100000}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--server-dir", required=True, type=Path)
    parser.add_argument("--java", type=Path, default=None)
    parser.add_argument("--jar", type=Path, default=None)
    parser.add_argument("--node-modules", type=Path, default=None)
    parser.add_argument("--placeholder-jar", type=Path, default=None, help="Placeholder API; installed for the run and removed after")
    args = parser.parse_args()

    server_dir = args.server_dir.resolve()
    java = args.java or Path(os.environ.get("SMOKE_JAVA", "java"))
    node_modules = args.node_modules or (server_dir.parent / "bot" / "node_modules")
    if args.jar:
        install_jar(server_dir, args.jar.resolve())
    reset_tower_world(server_dir)

    config_file = server_dir / "config" / "cobbletowers-cosmetics.json"
    config_backup = config_file.with_suffix(".json.bak")
    if config_file.exists():
        shutil.copy2(config_file, config_backup)
    config_file.parent.mkdir(parents=True, exist_ok=True)
    config_file.write_text(json.dumps({"chat_tags": True, "on_earn_commands": [
        "say EARNED {player} {id} {kind} {season}"]}), encoding="utf-8")
    placeholder_target = server_dir / "mods" / "placeholder-api-test.jar"
    if args.placeholder_jar:
        shutil.copy2(args.placeholder_jar, placeholder_target)

    results: list[Result] = []
    server = Server(server_dir, java)
    bot = None
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        env = dict(os.environ, NODE_PATH=str(node_modules))
        handle = open(server_dir / "logs" / "towers-cosmetics-bot.log", "w", encoding="utf-8", errors="replace")
        bot = subprocess.Popen(["node", str(HERE / "joinbot.js"), BOT, str(server_port(server_dir))],
                               stdout=handle, stderr=subprocess.STDOUT, env=env)

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for _ in range(90):
                if BOT in rcon.command("list"):
                    break
                time.sleep(1)
            else:
                raise RuntimeError("the bot never joined")

            def say(command: str) -> str:
                return rcon.command(f"execute as {BOT} run cobbletowers play {command}")

            def admin(command: str) -> str:
                return rcon.command(f"cobbletowers {command}")

            def names() -> str:
                return admin(f"cosmeticsadmin names {BOT}")

            def blue_banners() -> int:
                """How many blue banner items the bot carries (each distinct banner is one stack)."""
                return rcon.command(f"data get entity {BOT} Inventory").count('id: "minecraft:blue_banner"')

            def earned(cosmetic: str) -> int:
                """How many times the earn command's `say` came out for this cosmetic (its echo in the log is not counted)."""
                return server.read_log().count(f"[Server] EARNED {BOT} {cosmetic} ")

            admin("seasonadmin clear")
            admin("clubs clear")
            admin("trialadmin day 2026-10-12")
            log = server.read_log()
            results.append(Result("the earn-command config was read", "Cosmetics config: chat tags true, 1 earn command(s)" in log,
                                  "no 'Cosmetics config' line"))

            plain = names()
            results.append(Result("a player with no club and no title has an unchanged name",
                                  f"display=[{BOT}]" in plain and "decoration=[]" in plain, plain.strip()[:200]))

            made = say("club create Tidal_Crew tc")
            decorated = names()
            results.append(Result("a club tag appears in front of the display name", f"display=[[TC] {BOT}]" in decorated,
                                  decorated.strip()[:200]))
            results.append(Result("and in front of the tab-list name", f"tab=[[TC] {BOT}]" in decorated, decorated.strip()[:200]))

            # --- the track: 24 steps = three banners and the Challenger title -----------------------------------------------
            admin(f"seasonadmin points {BOT} 1800")
            time.sleep(2)
            log = server.read_log()
            results.append(Result("the three season banners were granted as real, named banner items",
                                  blue_banners() == 3 and "The Rising Tide Banner I" in rcon.command(f"data get entity {BOT} Inventory"),
                                  f"{blue_banners()} blue banner(s)"))
            inventory = rcon.command(f"data get entity {BOT} Inventory")
            results.append(Result("with their patterns: a bottom stripe, a border, a roundel and a flower",
                                  "minecraft:stripe_bottom" in inventory and "minecraft:border" in inventory
                                  and "minecraft:circle" in inventory and "minecraft:flower" in inventory, "a pattern is missing"))
            results.append(Result("the earn command ran for each new cosmetic, once",
                                  all(earned(f"s1_{name}") == 1 for name in ("banner_1", "banner_2", "banner_3", "title_challenger")),
                                  str({n: earned(f"s1_{n}") for n in ("banner_1", "banner_2", "banner_3", "title_challenger")})))
            worn = names()
            results.append(Result("the first title earned is worn automatically",
                                  f"display=[Challenger S1 [TC] {BOT}]" in worn, worn.strip()[:200]))
            results.append(Result("and shows in the tab list too", f"tab=[Challenger S1 [TC] {BOT}]" in worn, worn.strip()[:200]))

            listing = say("cosmetics")
            results.append(Result("/tower cosmetics lists the banners, the title and which is worn",
                                  "Banners:" in listing and "The Rising Tide Banner III" in listing and "Challenger of The Rising Tide (worn)" in listing,
                                  listing.strip()[:300]))

            # --- the finale, then changing the worn title -------------------------------------------------------------------
            admin(f"seasonadmin points {BOT} 600")
            time.sleep(2)
            titles = say("title")
            results.append(Result("step 30 adds the Champion title to the list, listed first, with the Challenger still worn",
                                  "1. Champion of The Rising Tide" in titles and "2. Challenger of The Rising Tide (worn)" in titles,
                                  titles.strip()[:300]))
            wore = say("title 1")
            changed = names()
            results.append(Result("/tower title 1 wears the Champion title",
                                  "Champion of The Rising Tide" in wore and f"display=[Champion S1 [TC] {BOT}]" in changed, changed.strip()[:200]))
            log = server.read_log()
            results.append(Result("earning the Champion title ran the earn command once and the badge too",
                                  earned("s1_title_champion") == 1 and earned("s1_badge") == 1,
                                  f"champion x{earned('s1_title_champion')}, badge x{earned('s1_badge')}"))

            rcon.command(f"execute as {BOT} run say hello from the tower")
            time.sleep(1)
            log = server.read_log()
            results.append(Result("a chat line carries the title and the club tag in front of the name",
                                  f"Champion S1 [TC] {BOT}] hello from the tower" in log or f"Champion S1 [TC] {BOT}> hello" in log,
                                  "no decorated chat line in the log"))

            # --- an award repeated does nothing --------------------------------------------------------------------------------
            admin(f"cosmeticsadmin grant {BOT} s2:badge")
            admin(f"cosmeticsadmin grant {BOT} s2:badge")
            time.sleep(1)
            log = server.read_log()
            results.append(Result("granting the same cosmetic twice runs the earn command once",
                                  earned("s2_badge") == 1, f"{earned('s2_badge')} runs"))
            bad = admin(f"cosmeticsadmin grant {BOT} nonsense")
            results.append(Result("a name that is not a cosmetic is refused", "not a cosmetic name" in bad, bad.strip()[:160]))

            off = say("title off")
            bare = names()
            results.append(Result("/tower title off takes the title off but keeps the club tag",
                                  "not wearing a title" in off and f"display=[[TC] {BOT}]" in bare, bare.strip()[:200]))
            say("club leave")
            alone = names()
            results.append(Result("leaving the club removes the tag", f"display=[{BOT}]" in alone and "decoration=[]" in alone, alone.strip()[:200]))

            # --- Placeholder API ----------------------------------------------------------------------------------------------
            if args.placeholder_jar:
                log = server.read_log()
                results.append(Result("with Placeholder API installed the placeholders are registered",
                                      "Registered the %cobbletowers:...% placeholders" in log, "no registration line"))
                say("club create Tidal_Crew tc")
                say("title 1")
                tag = admin(f"cosmeticsadmin parse {BOT} %cobbletowers:club_tag%")
                title = admin(f"cosmeticsadmin parse {BOT} %cobbletowers:title%")
                club_name = admin(f"cosmeticsadmin parse {BOT} %cobbletowers:club_name%")
                prefix = admin(f"cosmeticsadmin parse {BOT} %cobbletowers:prefix%")
                results.append(Result("Placeholder API resolves %cobbletowers:club_tag% and %cobbletowers:club_name% for a club member",
                                      "parsed=[TC]" in tag and "parsed=[Tidal_Crew]" in club_name, (tag + club_name).strip()[:200]))
                results.append(Result("%cobbletowers:title% is the worn title", "parsed=[Champion S1]" in title, title.strip()[:200]))
                results.append(Result("%cobbletowers:prefix% is the whole decoration", "parsed=[Champion S1 [TC] ]" in prefix, prefix.strip()[:200]))
                say("club leave")
                none = admin(f"cosmeticsadmin parse {BOT} %cobbletowers:club_tag%")
                results.append(Result("and a placeholder is empty for a player who has none", "parsed=[]" in none, none.strip()[:200]))
            else:
                log = server.read_log()
                results.append(Result("without Placeholder API the server says so and carries on",
                                      "Placeholder API is not installed" in log, "no 'not installed' line"))

            log = server.read_log()
            results.append(Result("no CobbleTowers exception during any of it",
                                  "\tat com.cobbletowers" not in log and "Mixin apply" not in log, "a CobbleTowers or mixin error is in the log"))
            admin("trialadmin day off")
    except Exception as exc:  # noqa: BLE001
        results.append(Result("cosmetics run", False, repr(exc)))
    finally:
        if bot is not None:
            bot.kill()
        print("Stopping server")
        server.stop()
        config_file.unlink(missing_ok=True)
        if config_backup.exists():
            shutil.move(config_backup, config_file)
        placeholder_target.unlink(missing_ok=True)

    print()
    width = max(len(result.name) for result in results) if results else 0
    failed = sum(1 for result in results if not result.passed)
    for result in results:
        print(f"  [{'PASS' if result.passed else 'FAIL'}] {result.name:<{width}}  {result.detail if not result.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
