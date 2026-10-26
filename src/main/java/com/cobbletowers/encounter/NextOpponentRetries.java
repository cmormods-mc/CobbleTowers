package com.cobbletowers.encounter;

import com.cobbletowers.TowerLog;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * Opponents that could not be started the moment the last battle ended (Cobblemon still counts the player as in it), tried
 * again every half second for a few seconds. Server thread only; an empty list costs one comparison per tick.
 */
final class NextOpponentRetries {

    private static final int EVERY_TICKS = 10;
    private static final int MAX_ATTEMPTS = 10;

    record Pending(UUID runId, UUID playerId, TowerEncounters.Wave wave, int attempt) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    private NextOpponentRetries() {}

    static void install() {
        ServerTickEvents.END_SERVER_TICK.register(NextOpponentRetries::tick);
    }

    static void add(UUID runId, UUID playerId, TowerEncounters.Wave wave, int attempt) {
        QUEUE.add(new Pending(runId, playerId, wave, attempt));
    }

    static boolean exhausted(int attempt) {
        return attempt >= MAX_ATTEMPTS;
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty() || server.getTickCount() % EVERY_TICKS != 0) return;
        List<Pending> due = List.copyOf(QUEUE);
        QUEUE.clear();
        for (Pending pending : due) {
            // A throw here would reach the server tick; one bad retry must not cost the others.
            try {
                TowerEncounters.retryNextOpponent(server, pending);
            } catch (RuntimeException ex) {
                TowerLog.error("Retrying the next opponent of run " + pending.runId() + " failed", ex);
            }
        }
    }
}
