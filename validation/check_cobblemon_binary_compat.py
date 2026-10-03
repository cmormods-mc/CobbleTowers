#!/usr/bin/env python3
"""Does a mod jar still link against a different Cobblemon build?

Compiling against a new Cobblemon proves SOURCE compatibility. A jar built against 1.7.3 and dropped onto 1.8.1 also
needs BINARY compatibility: every Cobblemon class, method and field its bytecode names must exist with the same
descriptor, or the first call dies with NoSuchMethodError at runtime -- in a tower battle, not at startup. Kotlin makes
this easy to break without touching a signature a caller wrote (a parameter gains a default, so the synthetic
`$default` bridge changes; a property becomes a field).

    python validation/check_cobblemon_binary_compat.py <mod jar> <cobblemon jar> [--also <more cobblemon-referencing jar>]

Exits 1 on any unresolved reference. Needs `javap` on PATH (any JDK). It reads the REMAPPED release jar, because the
Cobblemon jar uses intermediary names for Minecraft types and so must the thing compared against it.
"""

from __future__ import annotations

import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

REF = re.compile(r"//\s*(InterfaceMethod|Method|Field)\s+(com/cobblemon/[^.\s]+)\.([^:\s]+):(\S+)")
HEAD = re.compile(r"^(?:public |protected |private |abstract |final |static |synthetic )*(?:class|interface|enum) (\S+)(?: extends (\S+))?(?: implements (.+?))?\s*\{?$")
MEMBER_NAME = re.compile(r"^\s{2}(?:[\w<>\[\]$.,?\s]+?\s)?([\w$<>]+)\(.*\)|^\s{2}(?:[\w<>\[\]$.,?\s]+?\s)([\w$]+);")
DESC = re.compile(r"^\s+descriptor: (\S+)")


def strip_generics(text: str) -> str:
    out, depth = [], 0
    for ch in text:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth = max(0, depth - 1)
        elif depth == 0:
            out.append(ch)
    return "".join(out)


def javap(args: list[str]) -> str:
    return subprocess.run(["javap", *args], capture_output=True, text=True, errors="replace").stdout


def references(mod_jar: Path) -> dict[tuple[str, str, str], set[str]]:
    """(owner, name, descriptor) -> the mod classes that use it."""
    refs: dict[tuple[str, str, str], set[str]] = {}
    with zipfile.ZipFile(mod_jar) as z:
        classes = [n[:-6] for n in z.namelist() if n.endswith(".class") and n.startswith("com/")]
    for start in range(0, len(classes), 40):
        chunk = [c.replace("/", ".") for c in classes[start:start + 40]]
        out = javap(["-c", "-p", "-cp", str(mod_jar), *chunk])
        current = ""
        for line in out.splitlines():
            head = re.match(r"^(?:public |final |abstract |synthetic )*(?:class|interface|enum) (\S+)", line)
            if head:
                current = head.group(1)
            m = REF.search(line)
            if m:
                refs.setdefault((m.group(2), m.group(3).strip('"'), m.group(4)), set()).add(current)
    return refs


class Cobblemon:
    def __init__(self, jar: Path):
        self.jar = jar
        with zipfile.ZipFile(jar) as z:
            self.names = {n[:-6] for n in z.namelist() if n.endswith(".class")}
        self.cache: dict[str, tuple[set[tuple[str, str]], list[str]] | None] = {}

    def load(self, owner: str):
        if owner in self.cache:
            return self.cache[owner]
        if owner not in self.names:
            self.cache[owner] = None
            return None
        out = javap(["-s", "-p", "-cp", str(self.jar), owner.replace("/", ".")])
        members: set[tuple[str, str]] = set()
        supers: list[str] = []
        simple = owner.rsplit("/", 1)[-1]   # javap prints a nested class constructor as Outer$Inner
        lines = out.splitlines()
        for i, line in enumerate(lines):
            if re.match(r"^(?:\w+ )*(?:class|interface|enum) ", line):
                header = strip_generics(line)
                m = re.search(r" extends ([^\s{]+(?:,\s*[^\s{]+)*)", header)
                n = re.search(r" implements ([^{]+)", header)
                for group in (m.group(1) if m else None, n.group(1) if n else None):
                    if group:
                        supers += [x.strip().replace(".", "/") for x in group.split(",") if x.strip()]
            d = DESC.match(line)
            if d and i > 0:
                decl = strip_generics(lines[i - 1].strip().rstrip(";"))
                call = re.search(r"([\w$.]+)\(", decl)
                if call:
                    name = call.group(1).rsplit(".", 1)[-1]
                    member = "<init>" if name == simple else name
                else:
                    member = decl.split()[-1]
                members.add((member, d.group(1)))
        self.cache[owner] = (members, supers)
        return self.cache[owner]

    def has(self, owner: str, name: str, desc: str, seen: set[str] | None = None) -> bool | None:
        """True/False if decided inside Cobblemon; None if the lookup leaves it (a Minecraft or JDK supertype)."""
        seen = seen if seen is not None else set()
        if owner in seen:
            return False
        seen.add(owner)
        loaded = self.load(owner)
        if loaded is None:
            return None
        members, supers = loaded
        if (name, desc) in members:
            return True
        outside = False
        for parent in supers:
            found = self.has(parent, name, desc, seen)
            if found:
                return True
            if found is None:
                outside = True
        return None if outside else False


def main() -> int:
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    mod_jar, cobblemon_jar = Path(sys.argv[1]), Path(sys.argv[2])
    refs = references(mod_jar)
    cobblemon = Cobblemon(cobblemon_jar)
    owners = {owner for owner, _, _ in refs}
    missing_classes = sorted(o for o in owners if o not in cobblemon.names)
    problems = []
    undecided = 0
    for (owner, name, desc), users in sorted(refs.items()):
        if owner in missing_classes:
            continue
        found = cobblemon.has(owner, name, desc)
        if found is False:
            problems.append((owner, name, desc, sorted(users)[:3]))
        elif found is None:
            undecided += 1
    print(f"{mod_jar.name} against {cobblemon_jar.name}: {len(refs)} Cobblemon reference(s) across {len(owners)} class(es)")
    for owner in missing_classes:
        print(f"  MISSING CLASS  {owner}")
    for owner, name, desc, users in problems:
        print(f"  MISSING MEMBER {owner}.{name}{desc}   (used by {', '.join(users)})")
    print(f"  ({undecided} reference(s) resolve through a non-Cobblemon supertype and were not judged)")
    if missing_classes or problems:
        print("BINARY INCOMPATIBLE")
        return 1
    print("BINARY COMPATIBLE: every Cobblemon reference resolves")
    return 0


if __name__ == "__main__":
    sys.exit(main())
