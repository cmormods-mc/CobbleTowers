#!/usr/bin/env python3
"""CobbleTowers may use CobbleRaids only through its public API package.

The README's rule is that CobbleTowers never depends on CobbleRaids internals: a CobbleRaids change
behind com.cobbleraids.api must not be able to break this mod. An import check would miss a fully
qualified name, and a source check would miss reflection, so this reads the compiled classes and
fails on any constant-pool string that names a CobbleRaids package other than the API:

    com/cobbleraids/<anything but api/>      class references, descriptors, generic signatures
    com.cobbleraids.<anything but api.>      string constants, the form reflection uses

It fails, too, if it finds no classes, so a build that produced nothing cannot pass it.

    python validation/validate_architecture.py                          # compiled classes
    python validation/validate_architecture.py build/libs/CobbleTowers-x.jar
"""

from __future__ import annotations

import re
import struct
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FORBIDDEN = re.compile(r"com[/.]cobbleraids[/.](?!api[/.])[\w$/.]*")


def utf8_constants(data: bytes) -> list[str]:
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("not a class file")
    pos = 8
    (count,) = struct.unpack_from(">H", data, pos)
    pos += 2
    strings = []
    index = 1
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            (length,) = struct.unpack_from(">H", data, pos)
            pos += 2
            strings.append(data[pos:pos + length].decode("utf-8", "replace"))
            pos += length
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        else:
            raise ValueError(f"unknown constant pool tag {tag}")
        index += 1
    return strings


def classes(source: Path | None) -> list[tuple[str, bytes]]:
    if source is None:
        found = []
        # "main" and "client" (P11's split environment source set) are two separate compile outputs of
        # the same mod; a client-only violation must not go unchecked just because it compiled into a
        # directory this script did not yet know to look in.
        for env in ("main", "client"):
            base = ROOT / "build" / "classes" / "java" / env
            if not base.is_dir():
                continue
            found.extend((str(p.relative_to(base)).replace("\\", "/"), p.read_bytes())
                         for p in sorted(base.rglob("*.class")))
        return found
    with zipfile.ZipFile(source) as jar:
        return [(name, jar.read(name)) for name in sorted(jar.namelist()) if name.endswith(".class")]


def main() -> None:
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    found = classes(source)
    label = f" ({source.name})" if source else ""
    if not found:
        print(f"Architecture boundary validation{label}: FAIL -- no classes found; nothing was checked")
        sys.exit(1)

    violations = []
    for path, data in found:
        for constant in utf8_constants(data):
            for match in FORBIDDEN.finditer(constant):
                violations.append(f"{path} references {match.group(0)}")

    if violations:
        print(f"Architecture boundary validation{label}: FAIL")
        for violation in sorted(set(violations)):
            print("  " + violation)
        sys.exit(1)
    print(f"Architecture boundary validation{label}: PASS -- {len(found)} class(es), "
          f"CobbleRaids used only through com.cobbleraids.api")


if __name__ == "__main__":
    main()
