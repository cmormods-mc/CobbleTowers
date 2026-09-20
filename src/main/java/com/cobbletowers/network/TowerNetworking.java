package com.cobbletowers.network;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.ParticipantService;
import com.cobbletowers.spectator.SpectatorPresentation;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * CobbleTowers' first networking channel (P11): three payloads, registered once for the life of the
 * JVM the way every other one-time install in this mod already is.
 */
public final class TowerNetworking {

    private TowerNetworking() {}

    /**
     * Common to both physical sides. A payload has to be registered wherever it is encoded or
     * decoded, so this runs from {@code CobbleTowers.onInitialize()}, which Fabric Loader calls on a
     * dedicated server and on an integrated client's own server alike.
     */
    public static void registerPayloadTypes() {
        PayloadTypeRegistry.playS2C().register(SpectatorPanelPayload.TYPE, SpectatorPanelPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RewardRevealPayload.TYPE, RewardRevealPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CycleTeammatePayload.TYPE, CycleTeammatePayload.STREAM_CODEC);
    }

    /** The server-side half: the one payload the server ever receives. */
    public static void installServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTeammatePayload.TYPE, (payload, context) -> {
            ServerPlayer spectator = context.player();
            context.server().execute(() -> handleCycle(spectator, payload.next()));
        });
    }

    private static void handleCycle(ServerPlayer spectator, boolean next) {
        UUID spectatorId = spectator.getUUID();
        Optional<PersistedRun> run = ParticipantService.runOf(spectatorId);
        if (run.isEmpty()) return;
        Optional<ParticipantState> state = ParticipantService.stateOf(run.get(), spectatorId);
        if (state.isEmpty() || !state.get().isSpectating()) {
            TowerLog.warn("Player {} asked to cycle teammates while not spectating; ignored", spectatorId);
            return;
        }
        SpectatorPresentation.cycle(run.get(), spectator, next);
    }

    /** Never sent to a client that has not registered the channel -- see {@code RewardDelivery}'s chat fallback. */
    public static void sendRewardReveal(ServerPlayer player, RewardRevealPayload payload) {
        if (ServerPlayNetworking.canSend(player, RewardRevealPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Silently does nothing for a client that never registered the channel. */
    public static void sendSpectatorPanel(ServerPlayer player, SpectatorPanelPayload payload) {
        if (ServerPlayNetworking.canSend(player, SpectatorPanelPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
