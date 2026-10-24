package com.cobbletowers.encounter;

import com.cobbletowers.persistence.TowerDrawLogStore;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** What a run has already met, read before a draw and written after it, so no Pokemon appears twice in one run. */
final class RunDraws {

    private RunDraws() {}

    static Set<String> seen(MinecraftServer server, UUID run, int floor, int ordinal) {
        return TowerDrawLogStore.get(server).seenBefore(run, floor, ordinal);
    }

    static void record(MinecraftServer server, UUID run, int floor, int ordinal, String name) {
        TowerDrawLogStore.get(server).record(run, floor, ordinal, name);
    }
}
