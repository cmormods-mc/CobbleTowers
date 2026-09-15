package com.cobbletowers.spike;

import com.cobbleraids.api.encounter.EncounterListener;
import com.cobbleraids.api.encounter.EncounterResult;
import com.cobbleraids.api.encounter.LeaveReason;
import com.cobbletowers.TowerLog;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The spike's active encounters and what it does when CobbleRaids reports on them.
 *
 * <p>Dev-only. It exists to prove an owned boss fight works end to end for one to four players,
 * and to put numbers on it: the log line at the end carries wall-clock and combat duration so the
 * 24-Pokemon question (TDS #42) is answered by measurement.
 */
public final class SpikeEncounters {

    record Spike(UUID id, ResourceLocation definition, int bossLevel, List<UUID> players, long startedAtMillis) {}

    private static final Map<UUID, Spike> ACTIVE = new ConcurrentHashMap<>();

    private SpikeEncounters() {}

    static void track(Spike spike) {
        ACTIVE.put(spike.id(), spike);
    }

    static Set<UUID> activeIds() {
        return Set.copyOf(ACTIVE.keySet());
    }

    public static void onServerStopped() {
        ACTIVE.clear();
    }

    static EncounterListener listenerFor(MinecraftServer server) {
        return new EncounterListener() {
            @Override
            public void onParticipantLeft(UUID encounterId, UUID playerId, LeaveReason reason) {
                TowerLog.info("Spike {}: player {} left ({})", encounterId, playerId, reason);
                Spike spike = ACTIVE.get(encounterId);
                if (spike != null) tell(server, spike, "A participant left the spike encounter: " + reason, ChatFormatting.YELLOW);
            }

            @Override
            public void onEnded(EncounterResult result) {
                Spike spike = ACTIVE.remove(result.encounterId());
                long wallMillis = spike == null ? -1L : System.currentTimeMillis() - spike.startedAtMillis();
                int started = spike == null ? 0 : spike.players().size();
                TowerLog.info("Spike {} ended {}: {} player(s) started, {} remaining, {} combat ticks, {} ms wall clock, contribution {}",
                        result.encounterId(), result.outcome(), started, result.remainingParticipants().size(),
                        result.elapsedCombatTicks(), wallMillis, result.contribution());
                if (spike != null) {
                    tell(server, spike, "Spike encounter ended: " + result.outcome() + " after "
                            + (result.elapsedCombatTicks() / 20) + "s of combat.",
                            switch (result.outcome()) {
                                case VICTORY -> ChatFormatting.GREEN;
                                case DEFEAT -> ChatFormatting.RED;
                                case ABORTED -> ChatFormatting.GRAY;
                            });
                }
            }
        };
    }

    private static void tell(MinecraftServer server, Spike spike, String message, ChatFormatting colour) {
        for (UUID playerId : spike.players()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) player.sendSystemMessage(Component.literal(message).withStyle(colour));
        }
    }
}
