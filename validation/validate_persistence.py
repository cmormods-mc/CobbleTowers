#!/usr/bin/env python3
"""Persisted state may not hold a live reference.

TDS §10: "Never persist live Minecraft/Cobblemon entity references as authoritative state. Persist
identifiers, deterministic seeds, snapshots and logical state." That rule is easy to agree with and
easy to break by accident -- a PokemonEntity field on a stored record looks convenient and quietly
turns a save file into a promise the server cannot keep, because the entity is gone after a restart
and a stale one pins the world it belonged to.

So this reads the compiled classes and fails when a FIELD of persisted state names:

    net/minecraft/world/entity/   entities
    net/minecraft/server/         the server, its levels and its players
    net/minecraft/world/level/    levels and anything reached through one
    com/cobblemon/                Cobblemon, whose Pokemon are entities too

Fields, not methods: reaching the store needs a MinecraftServer parameter, which is how a checkpoint
gets written at all. What must never happen is one being kept.

"Persisted state" is anything under com/cobbletowers/persistence/ **or** anything extending
SavedData, wherever it lives. Folder alone would be a rule about where a file sits rather than what
it does, and the day someone puts a SavedData in another package is exactly the day this should
still fire.

Reading bytecode means it cannot be fooled by a fully qualified name, an import alias or a type
reached through a generic argument, and it keeps working when classes are renamed or added.

    python validation/validate_persistence.py                            # compiled classes
    python validation/validate_persistence.py build/libs/CobbleTowers-x.jar
"""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_api_boundary import parse_class  # noqa: E402  -- same directory, shared class reader

ROOT = Path(__file__).resolve().parents[1]
MOD_PREFIX = "com/cobbletowers/"
PERSISTENCE_PREFIX = "com/cobbletowers/persistence/"
SAVED_DATA = "net/minecraft/world/level/saveddata/SavedData"
FORBIDDEN = (
    "net/minecraft/world/entity/",
    "net/minecraft/server/",
    "net/minecraft/world/level/",
    "com/cobblemon/",
)
CLASS_NAME = re.compile(r"L([^;<]+)")


def mod_classes(source: Path | None) -> list[tuple[str, bytes]]:
    if source is None:
        found = []
        # "main" and "client" (P11's split environment source set) are two separate compile outputs of
        # the same mod; persisted state has no business existing client-side, but this checks that
        # rather than assuming it.
        for env in ("main", "client"):
            base = ROOT / "build" / "classes" / "java" / env
            mod_dir = base / MOD_PREFIX
            if not mod_dir.is_dir():
                continue
            found.extend((str(p.relative_to(base)).replace("\\", "/"), p.read_bytes())
                         for p in sorted(mod_dir.rglob("*.class")))
        return found
    with zipfile.ZipFile(source) as jar:
        return [(name, jar.read(name)) for name in sorted(jar.namelist())
                if name.startswith(MOD_PREFIX) and name.endswith(".class")]


def is_persisted_state(name: str, parsed: dict) -> bool:
    return name.startswith(PERSISTENCE_PREFIX) or parsed.get("super") == SAVED_DATA


def violations_in(parsed: dict) -> list[str]:
    found = []
    owner = parsed["name"]
    # The members list holds fields first, then methods; a field descriptor never starts with "(".
    for _access, name, descriptor, signatures in parsed["members"]:
        if descriptor.startswith("("):
            continue
        for text in (descriptor, *signatures):
            for referenced in CLASS_NAME.findall(text or ""):
                if referenced.startswith(FORBIDDEN):
                    found.append(f"{owner}.{name} keeps a {referenced}")
    return found


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    classes = mod_classes(source)
    if not classes:
        print(f"Persistence validation: FAIL -- no classes under {MOD_PREFIX} in "
              f"{source or 'build/classes/java/main'}; nothing was checked")
        sys.exit(1)

    violations: list[str] = []
    checked = 0
    for _path, data in classes:
        parsed = parse_class(data)
        if not is_persisted_state(parsed["name"] + ".class", parsed):
            continue
        checked += 1
        violations.extend(violations_in(parsed))

    if checked == 0:
        print("Persistence validation: FAIL -- no persisted state found at all; nothing was checked")
        sys.exit(1)

    where = f" ({source.name})" if source else ""
    if violations:
        print(f"Persistence validation{where}: FAIL -- stored state holds live references")
        for violation in violations:
            print(f"  {violation}")
        print("  Persist an identifier and look the live object up again; see TDS section 10.")
        sys.exit(1)

    print(f"Persistence validation{where}: PASS -- {checked} persisted type(s) checked "
          f"(persistence package plus every SavedData), none holds an entity, level, server or "
          f"Cobblemon reference")


if __name__ == "__main__":
    main()
