#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def require(path: str, needles: list[str]) -> None:
    text = (ROOT / path).read_text(encoding="utf-8")
    missing = [needle for needle in needles if needle not in text]
    if missing:
        print(f"[FAIL] {path} missing required persistence contract markers:")
        for needle in missing:
            print(f"  - {needle}")
        sys.exit(1)


require(
    "src/main/java/com/cobbletowers/persistence/TowerSavedData.java",
    [
        "CURRENT_SCHEMA_VERSION = 1",
        "DataFixTypes.SAVED_DATA_COMMAND_STORAGE",
        "TowerRunNbtCodec.decode",
        "incompatibleNewerSchema()",
        "preservedUnknownRoot.copy()",
        "setDirty()",
        "Duplicate persisted run UUID",
    ],
)

require(
    "src/main/java/com/cobbletowers/persistence/TowerPersistenceRuntime.java",
    [
        "server.overworld()",
        "computeIfAbsent(TowerSavedData.factory(), TowerSavedData.STORAGE_ID)",
        "manager.restore(snapshot)",
        "TowerRunState.BOSS_BATTLE",
        "recoverInterruptedBossBattle()",
        "retained for recovery",
    ],
)

require(
    "src/main/java/com/cobbletowers/run/TowerRunManager.java",
    [
        "allocator.reserve(snapshot.runId(), snapshot.slotIndex())",
        "persistenceSink.accept(run.snapshot())",
        "persistenceRemoveSink.accept(runId)",
    ],
)

require(
    "src/main/java/com/cobbletowers/run/TowerParticipant.java",
    [
        "DEFAULT_RECONNECT_GRACE_TICKS = 5 * 60 * 20",
        "reconnectGraceTicksRemaining",
        "disconnect()",
        "reconnect()",
    ],
)

require(
    "src/main/java/com/cobbletowers/run/TowerRunState.java",
    ["BOSS_BATTLE", "PREPARING_NEXT_FLOOR"],
)

print("[PASS] CobbleTowers persistence/recovery contract")
