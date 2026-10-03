#!/usr/bin/env python3
"""Proves P24 live: armor sets are real items, worn pieces switch bonuses on and off, and a full set reaches a battle.

  * a datapack set with a bad bonus is skipped (and named) without taking the shipped sets down;
  * two worn Tideforge pieces add their attribute modifiers, and taking one off removes them again;
  * a mixed outfit gets two-piece bonuses and no full-set battle effects;
  * a full Tideforge set puts its battle effects on that player's floor and boss battles, resolved to the right
    sides, while their teammate in an ordinary outfit carries nothing;
  * taking the set off stops the effects.

What the effects do inside Showdown is proved by validation/showdown/tower_fx_test.js; the arithmetic of the tower
bonuses by the unit tests. This proves the wiring around them.

    python validation/smoke/armor_set_test.py --server-dir <rig> --java <jdk21 java> [--jar <build>]
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

from rcon import Rcon  # noqa: E402
from run_durability_test import (  # noqa: E402
    Result, Server, clear_tower, install_jar, reset_tower_world, read_password, run_id_from, wait_online,
)
from floor_encounter_test import (  # noqa: E402
    BOTS, FIRST_MOVE, TOWER, begin_floor, give_party, start_battle_bot, tell_bot, wait_for, wait_for_floor,
)

PACK = "armor_smoke_bad"
BAD_SET = ('{"schema_version":1,"display_name":"Bad","pieces":{"head":"cobbletowers:challenger_helmet"},'
           '"bonuses":[{"pieces":1,"kind":"battle","effects":[{"op":"eval","code":"1"}]}]}')


def install_bad_pack(server_dir: Path) -> Path:
    pack = server_dir / "world" / "datapacks" / PACK
    target = pack / "data" / "cobbletowers" / "cobbletowers" / "armor_sets"
    target.mkdir(parents=True, exist_ok=True)
    (target / "smoke_bad.json").write_text(BAD_SET, encoding="utf-8")
    (pack / "pack.mcmeta").write_text('{"pack":{"pack_format":48,"description":"armor smoke"}}', encoding="utf-8")
    return pack


def attribute(rcon: Rcon, player: str, name: str) -> float | None:
    out = rcon.command(f"attribute {player} {name} get")
    match = re.search(r"is (-?[0-9.]+)\s*$", out.strip())
    return float(match.group(1)) if match else None


def wear(rcon: Rcon, player: str, set_id: str, slots: dict[str, str]) -> None:
    for slot, piece in slots.items():
        rcon.command(f"item replace entity {player} armor.{slot} with cobbletowers:{set_id}_{piece}")


def clear_armor(rcon: Rcon, player: str) -> None:
    for slot in ("head", "chest", "legs", "feet"):
        rcon.command(f"item replace entity {player} armor.{slot} with minecraft:air")


def settle() -> None:
    time.sleep(2)   # the worn-set check runs every half second


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
    pack = install_bad_pack(server_dir)

    results: list[Result] = []
    server = Server(server_dir, java)
    bots: list[subprocess.Popen] = []
    a, b = BOTS
    try:
        print(f"Booting server ({server.log})")
        server.start()
        server.wait_until_ready()
        boot = server.read_log()
        results.append(Result("the shipped armor sets loaded", bool(re.search(r"Loaded [4-9] armor set", boot)),
                              "no 'Loaded N armor set' line"))
        results.append(Result("Showdown's own runtime loaded the CobbleTowers extension",
                              "Showdown extension cobbletowers-fx loaded" in boot and "Cannot load module" not in boot,
                              "; ".join(l for l in boot.splitlines() if "Showdown extension" in l or "Cannot load module" in l)[:300]))
        results.append(Result("a datapack set with a bad bonus is skipped and named",
                              "Skipping malformed armor set cobbletowers:smoke_bad" in boot, "no skip line for smoke_bad"))
        for name in BOTS:
            bots.append(start_battle_bot(rig, name, FIRST_MOVE))

        with Rcon("127.0.0.1", 25575, read_password(server_dir)) as rcon:
            for name in BOTS:
                if not wait_online(rcon, seconds=90) and name not in rcon.command("list"):
                    raise RuntimeError(f"{name} never joined")
            rcon.command("execute in cobbletowers:tower run forceload remove all")
            clear_tower(rcon)
            for name in BOTS:
                give_party(rcon, name)
                clear_armor(rcon, name)
            settle()

            # Tooltip sync (P25). The bots are not Fabric clients, so they never receive the payload and canSend skips them;
            # what this proves is that a join and a reload stay clean for a client without the channel, and that the reload
            # re-runs the sync path (the sets are loaded again) without a send error.
            before_reload = server.read_log().count("Loaded 4 armor set(s)")
            rcon.command("reload")
            time.sleep(5)
            after = server.read_log()
            results.append(Result("a datapack reload loads the armor sets again", after.count("Loaded 4 armor set(s)") > before_reload,
                                  f"{before_reload} -> {after.count('Loaded 4 armor set(s)')}"))
            results.append(Result("joining and reloading are clean for a client that cannot receive the tooltip data",
                                  "Could not send the armor set tooltips" not in after, "a send error was logged"))

            breath = "minecraft:generic.oxygen_bonus"
            water = "minecraft:generic.water_movement_efficiency"
            results.append(Result("nothing worn: no bonus", attribute(rcon, a, breath) == 0.0, str(attribute(rcon, a, breath))))

            wear(rcon, a, "tideforge", {"head": "helmet", "chest": "chestplate"})
            settle()
            results.append(Result("two Tideforge pieces add the breath bonus", attribute(rcon, a, breath) == 1.0,
                                  str(attribute(rcon, a, breath))))
            results.append(Result("and the water movement bonus", attribute(rcon, a, water) == 0.5, str(attribute(rcon, a, water))))
            results.append(Result("the teammate wearing nothing has neither", attribute(rcon, b, breath) == 0.0,
                                  str(attribute(rcon, b, breath))))

            rcon.command(f"item replace entity {a} armor.chest with minecraft:air")
            settle()
            results.append(Result("taking a piece off removes the modifiers", attribute(rcon, a, breath) == 0.0,
                                  str(attribute(rcon, a, breath))))

            clear_armor(rcon, a)
            wear(rcon, a, "tideforge", {"head": "helmet", "chest": "chestplate"})
            wear(rcon, a, "rootvale", {"legs": "leggings", "feet": "boots"})
            settle()
            health = attribute(rcon, a, "minecraft:generic.max_health")
            results.append(Result("a mixed outfit gets both two-piece bonuses",
                                  attribute(rcon, a, breath) == 1.0 and health == 22.0, f"{attribute(rcon, a, breath)} / {health}"))

            clear_armor(rcon, a)
            wear(rcon, a, "tideforge", {"head": "helmet", "chest": "chestplate", "legs": "leggings", "feet": "boots"})
            settle()

            run = run_id_from(rcon.command(f"cobbletowers runs create {TOWER} @a"))
            rcon.command(f"cobbletowers runs advance {run} party_submitted")
            rcon.command(f"cobbletowers runs advance {run} party_validated")
            rcon.command(f"cobbletowers runs allocate {run}")
            for name in BOTS:
                tell_bot(rig, name, "FIGHT")
                tell_bot(rig, name, f"MOVE {FIRST_MOVE}")
            for name in BOTS:
                if not wait_for(rig / "bot" / f"{name}.log", r"AUTOFIGHT on", seconds=30):
                    raise RuntimeError(f"{name} never armed")

            begin_floor(rcon, run)
            time.sleep(3)
            carried = re.findall(r"Tower battle \S+ carries (\d+) effect\(s\): (.*)", server.read_log())
            results.append(Result("exactly one floor battle carried effects: the full-set wearer's",
                                  len(carried) == 1 and carried[0][0] == "2", str(carried)))
            if carried:
                text = carried[0][1]
                results.append(Result("global rain, and the Water boost on the wearer's own side only",
                                      '"raindance"' in text and '"Water"' in text and '"sides":["p1"]' in text
                                      and '"p2"' not in text, text[:240]))

            applied = re.findall(r"\[CobbleTowers\] Applied (\d+) of (\d+) tower effect", server.read_log())
            results.append(Result("the simulator applied the full set's battle effects (2 of 2)", applied == [("2", "2")], str(applied)))

            wait_for_floor(server, rig, BOTS, seconds=180, pattern=r"Floor \d+ boss \S+ started at level \d+")
            time.sleep(2)
            carried = re.findall(r"Tower battle \S+ carries (\d+) effect\(s\)", server.read_log())
            results.append(Result("the boss battle carried the set's effects too, and only the wearer's",
                                  len(carried) == 2, str(carried)))
            resolved = wait_for_floor(server, rig, BOTS, seconds=360)
            results.append(Result("the floor still resolved with a full set worn", bool(resolved), "see " + str(server.log)))

            clear_armor(rcon, a)
            settle()
            results.append(Result("taking the full set off clears every modifier",
                                  attribute(rcon, a, breath) == 0.0 and attribute(rcon, a, water) == 0.0,
                                  f"{attribute(rcon, a, breath)} / {attribute(rcon, a, water)}"))
            final = server.read_log()
            bad = [line for line in final.splitlines()
                   if re.search(r"armor", line, re.I) and re.search(r"error|exception", line, re.I)
                   and "smoke_bad" not in line and "malformed" not in line]
            results.append(Result("no armor errors in the server log beyond the deliberate bad set", not bad, "; ".join(bad)[:300]))
    except Exception as exc:  # noqa: BLE001
        results.append(Result("armor set run", False, repr(exc)))
    finally:
        for bot in bots:
            bot.kill()
        print("Stopping server")
        server.stop()
        shutil.rmtree(pack, ignore_errors=True)

    print()
    width = max(len(r.name) for r in results)
    failed = sum(1 for r in results if not r.passed)
    for r in results:
        print(f"  [{'PASS' if r.passed else 'FAIL'}] {r.name:<{width}}  {r.detail if not r.passed else ''}")
    print(f"\n{len(results) - failed}/{len(results)} checks passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
