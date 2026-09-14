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
        "CURRENT_SCHEMA_VERSION = 2",
        "DataFixTypes.SAVED_DATA_COMMAND_STORAGE",
        "Migrating CobbleTowers persistence schema 1 -> 2",
        "TowerRunNbtCodec.decode",
        "TowerStructureWorkNbtCodec.decode",
        "structure_work",
        "incompatibleNewerSchema()",
        "preservedUnknownRoot.copy()",
        "setDirty()",
        "Duplicate persisted run UUID",
        "Duplicate persisted structure work UUID",
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
        "recoverParticipantConnectionsAfterServerRestart()",
        "retained for recovery",
    ],
)

require(
    "src/main/java/com/cobbletowers/runtime/TowerServerRuntime.java",
    [
        "savedData.incompatibleNewerSchema()",
        "Saved Tower data is preserved untouched",
        "rejected run(s)",
        "snapshots remain on disk for recovery",
        "runManager.clearRuntimeState()",
    ],
)

require(
    "src/main/java/com/cobbletowers/CobbleTowers.java",
    [
        "ServerLifecycleEvents.SERVER_STARTED.register(SERVER_RUNTIME::start)",
        "ServerLifecycleEvents.SERVER_STOPPED.register",
        "public static TowerServerRuntime runtime()",
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
        "recoverAfterServerRestart()",
        "disconnect()",
        "reconnect()",
    ],
)

require(
    "src/main/java/com/cobbletowers/run/TowerRunState.java",
    ["BOSS_BATTLE", "PREPARING_NEXT_FLOOR"],
)

require(
    "src/main/java/com/cobbletowers/structure/TowerStructureScheduler.java",
    [
        "MAX_BUILD_ATTEMPTS = 3",
        "return; // hard global budget: at most one operation per tick",
        "TowerStructureOperationResult.ASSET_FAULT",
        "safePersist",
        "saturatingAdd",
        "Runtime retry deadlines intentionally restart from now",
    ],
)

print("[PASS] CobbleTowers persistence/recovery/structure-work contract")
