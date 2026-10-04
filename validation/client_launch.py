"""Shared by client_screens.py and client_e2e.py: how to start a real, production-form Fabric client offline.

The vanilla client jar, Fabric Loader and its libraries (taken from the list Loom writes when it configures a launch, minus anything
development-only), and the release jars of Fabric API, Cobblemon, CobbleRaids and CobbleTowers in a client directory of their own.
Why not `gradlew runClient`: Cobblemon is Kotlin and Loom's remapping to Mojang names does not rewrite Kotlin metadata, so Cobblemon
cannot start there ("ClassNotFoundException: net.minecraft.class_2960" from kotlin-reflect).
"""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
GRADLE_CACHE = Path.home() / ".gradle" / "caches"
SKIP_PREFIXES = ("kotlin", "kotlinx", "atomicfu", "mappings", "dev-launch-injector", "annotations-")


def card_mods(rig: Path) -> list[Path]:
    """CobblemonCards and the two mods it needs at runtime (Accessories, which needs owo-lib), from the rig's disabled-mods folder."""
    folder = rig.parent / "testserver-full" / "mods-disabled-for-testing"
    return [first(folder, pattern) for pattern in ("cobblemon-cards-fabric-*.jar", "accessories-fabric-*.jar", "owo-lib-*.jar")]


def newer_loader(rig: Path | None, keep: list[str]) -> list[str]:
    """Swaps Fabric Loader and its own libraries for the rig's (the server runs a newer Loader than Gradle caches), because a mod
    such as CobblemonCards needs Loader 0.18.6 or later and the Gradle cache holds 0.17.2."""
    if rig is None:
        return keep
    libs = rig / "libraries"
    loader = sorted(libs.glob("net/fabricmc/fabric-loader/*/fabric-loader-*.jar"))
    if not loader:
        return keep
    replacements = {
        "fabric-loader-": loader[-1],
        "sponge-mixin-": next(iter(sorted(libs.glob("net/fabricmc/sponge-mixin/*/sponge-mixin-*.jar"))), None),
    }
    for asm in ("asm", "asm-analysis", "asm-commons", "asm-tree", "asm-util"):
        found = sorted(libs.glob(f"org/ow2/asm/{asm}/*/{asm}-*.jar"))
        if found:
            replacements[f"{asm}-9"] = found[-1]
    out = []
    for entry in keep:
        name = Path(entry).name
        swapped = None
        for prefix, jar in replacements.items():
            if jar is not None and name.startswith(prefix):
                swapped = str(jar)
                break
        out.append(swapped or entry)
    return out


def libraries() -> list[str]:
    """The Minecraft and Fabric Loader libraries the client source set runs with, without anything development-only (named jars,
    remapped mods, Kotlin, which Cobblemon brings in its own jar). Asked of Gradle through a small init script."""
    result = subprocess.run([str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "-q", "-I",
                             str(ROOT / "validation" / "print_client_classpath.gradle"), "printClientLibs", "--console=plain"],
                            cwd=ROOT, check=True, capture_output=True, text=True, encoding="utf-8", errors="replace")
    line = next((l for l in result.stdout.splitlines() if l.startswith("CLIENTLIBS=")), None)
    if line is None:
        raise RuntimeError("Gradle did not print the client classpath: " + result.stdout[-500:] + result.stderr[-500:])
    keep = []
    for entry in line[len("CLIENTLIBS="):].split(";"):
        name = Path(entry).name
        if not entry or "loom-cache" in entry or f"{os.sep}build{os.sep}" in entry or name.startswith(SKIP_PREFIXES):
            continue
        keep.append(entry)
    return keep


def first(root: Path, pattern: str) -> Path:
    found = sorted(root.glob(pattern))
    if not found:
        sys.exit(f"cannot find {pattern} under {root}")
    return found[-1]


def prepare(game: Path, rig: Path, tower_jar: Path | None, gui_scale: int, extra_mods: list[Path] | None = None) -> None:
    """Fills a client directory with the release mods, this build, and options that keep the window quiet and predictable."""
    shutil.rmtree(game / "mods", ignore_errors=True)
    (game / "mods").mkdir(parents=True)
    for pattern in ("fabric-api-*.jar", "Cobblemon-*.jar", "CobbleRaids-*.jar"):
        jar = first(rig / "mods", pattern)
        shutil.copy2(jar, game / "mods" / jar.name)
    tower = tower_jar or first(ROOT / "build" / "libs", "CobbleTowers-*[!s].jar")
    shutil.copy2(tower, game / "mods" / tower.name)
    for extra in extra_mods or []:
        shutil.copy2(extra, game / "mods" / extra.name)
    (game / "options.txt").write_text(
        f"guiScale:{gui_scale}\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n"
        "soundCategory_master:0.0\nlang:en_us\ntutorialStep:none\n", encoding="utf-8")


def command(java: Path, game: Path, extra: list[str], rig: Path | None = None) -> list[str]:
    vanilla = GRADLE_CACHE / "fabric-loom" / "1.21.1" / "minecraft-client.jar"
    intermediary = first(GRADLE_CACHE / "modules-2" / "files-2.1" / "net.fabricmc" / "intermediary", "1.21.1/*/intermediary-1.21.1-v2.jar")
    classpath = newer_loader(rig, libraries()) + [str(vanilla), str(intermediary)]
    return [str(java), "-Xmx3G", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(classpath),
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--version", "1.21.1", "--gameDir", str(game), "--assetsDir", str(GRADLE_CACHE / "fabric-loom" / "assets"),
            "--assetIndex", "1.21.1-17", "--accessToken", "0", "--username", "ScreenShots",
            "--uuid", "00000000-0000-0000-0000-000000000001", "--userType", "legacy", "--width", "1280", "--height", "720", *extra]
