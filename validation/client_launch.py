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


def libraries() -> list[str]:
    """The Minecraft and Fabric Loader libraries a development client uses, without anything development-only."""
    subprocess.run([str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")), "configureClientLaunch", "--console=plain", "-q"],
                   cwd=ROOT, check=True)
    argfile = ROOT / "build" / "loom-cache" / "argFiles" / "runClient"
    text = argfile.read_text(encoding="utf-8", errors="replace")
    classpath = text.split("-classpath", 1)[1].strip().splitlines()[0].strip().strip('"')
    keep = []
    for entry in classpath.split(";"):
        name = Path(entry).name
        if "loom-cache" in entry or f"{os.sep}build{os.sep}" in entry or name.startswith(SKIP_PREFIXES):
            continue
        keep.append(entry)
    return keep


def first(root: Path, pattern: str) -> Path:
    found = sorted(root.glob(pattern))
    if not found:
        sys.exit(f"cannot find {pattern} under {root}")
    return found[-1]


def prepare(game: Path, rig: Path, tower_jar: Path | None, gui_scale: int) -> None:
    """Fills a client directory with the release mods, this build, and options that keep the window quiet and predictable."""
    shutil.rmtree(game / "mods", ignore_errors=True)
    (game / "mods").mkdir(parents=True)
    for pattern in ("fabric-api-*.jar", "Cobblemon-*.jar", "CobbleRaids-*.jar"):
        jar = first(rig / "mods", pattern)
        shutil.copy2(jar, game / "mods" / jar.name)
    tower = tower_jar or first(ROOT / "build" / "libs", "CobbleTowers-*[!s].jar")
    shutil.copy2(tower, game / "mods" / tower.name)
    (game / "options.txt").write_text(
        f"guiScale:{gui_scale}\nonboardAccessibility:false\nskipMultiplayerWarning:true\npauseOnLostFocus:false\nfullscreen:false\n"
        "soundCategory_master:0.0\nlang:en_us\ntutorialStep:none\n", encoding="utf-8")


def command(java: Path, game: Path, extra: list[str]) -> list[str]:
    vanilla = GRADLE_CACHE / "fabric-loom" / "1.21.1" / "minecraft-client.jar"
    intermediary = first(GRADLE_CACHE / "modules-2" / "files-2.1" / "net.fabricmc" / "intermediary", "1.21.1/*/intermediary-1.21.1-v2.jar")
    classpath = libraries() + [str(vanilla), str(intermediary)]
    return [str(java), "-Xmx3G", "-Dfile.encoding=UTF-8", "-cp", os.pathsep.join(classpath),
            "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "--version", "1.21.1", "--gameDir", str(game), "--assetsDir", str(GRADLE_CACHE / "fabric-loom" / "assets"),
            "--assetIndex", "1.21.1-17", "--accessToken", "0", "--username", "ScreenShots",
            "--uuid", "00000000-0000-0000-0000-000000000001", "--userType", "legacy", "--width", "1280", "--height", "720", *extra]
