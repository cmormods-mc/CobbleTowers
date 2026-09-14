#!/usr/bin/env python3
"""
Validation notes
================
Exact functionality:
- Scans every CobbleTowers Java source file.
- Allows imports from `com.cobbleraids.api` only.
- Fails CI if any source imports a CobbleRaids implementation package.

Architectural role:
- Enforces the stable API/internal boundary continuously instead of relying on code-review memory.

Performance impact:
- CI-only source scan with negligible cost and zero runtime server overhead.

Assumptions and constraints:
- CobbleTowers may depend on CobbleRaids at runtime, but all compile-time interaction must flow
  through the public `com.cobbleraids.api` package.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE_ROOT = ROOT / "src/main/java"
IMPORT_PATTERN = re.compile(r"^import\s+(com\.cobbleraids(?:\.[\w.*]+)?);", re.MULTILINE)
ALLOWED_PREFIX = "com.cobbleraids.api"

violations: list[str] = []
checked = 0

for path in sorted(SOURCE_ROOT.rglob("*.java")):
    checked += 1
    text = path.read_text(encoding="utf-8")
    for imported in IMPORT_PATTERN.findall(text):
        if imported == ALLOWED_PREFIX or imported.startswith(ALLOWED_PREFIX + "."):
            continue
        violations.append(f"{path.relative_to(ROOT)} imports forbidden CobbleRaids implementation: {imported}")

if violations:
    print("CobbleTowers architecture validation failed:", file=sys.stderr)
    for violation in violations:
        print(f" - {violation}", file=sys.stderr)
    raise SystemExit(1)

print(f"CobbleTowers architecture boundary OK ({checked} Java source files checked)")
