package com.cobbletowers.network;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.definition.VendorServiceDefinition;
import com.cobbletowers.economy.VendorPurchaseService;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerWalletStore;
import com.cobbletowers.runtime.ParticipantService;
import com.cobbletowers.spectator.SpectatorPresentation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * CobbleTowers' networking channel (P11, extended in P12): registered once for the life of the JVM
 * the way every other one-time install in this mod already is.
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
        PayloadTypeRegistry.playS2C().register(VendorCatalogPayload.TYPE, VendorCatalogPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(VendorPurchasePayload.TYPE, VendorPurchasePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ScoutingRevealPayload.TYPE, ScoutingRevealPayload.STREAM_CODEC);
    }

    /** The server-side half. */
    public static void installServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTeammatePayload.TYPE, (payload, context) -> {
            ServerPlayer spectator = context.player();
            context.server().execute(() -> handleCycle(spectator, payload.next()));
        });
        ServerPlayNetworking.registerGlobalReceiver(VendorPurchasePayload.TYPE, (payload, context) -> {
            ServerPlayer buyer = context.player();
            context.server().execute(() -> handlePurchase(context.server(), buyer, payload));
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

    private static void handlePurchase(MinecraftServer server, ServerPlayer buyer, VendorPurchasePayload payload) {
        Optional<PersistedRun> run = ParticipantService.runOf(buyer.getUUID());
        if (run.isEmpty()) {
            TowerLog.warn("Player {} asked to buy {} while not in a run; ignored", buyer.getUUID(), payload.serviceId());
            return;
        }
        VendorPurchaseService.Result result = VendorPurchaseService.purchase(
                server, run.get().runId(), buyer.getUUID(), payload.targetPlayerId(), payload.serviceId());
        if (result != VendorPurchaseService.Result.SUCCESS) {
            TowerLog.info("Player {} could not buy {}: {}", buyer.getUUID(), payload.serviceId(), result);
        }
        // Refreshed either way: a failed purchase (sold out, insufficient funds) still needs the
        // caller's screen to show the balance and remaining-purchase count it actually has now.
        ParticipantService.runOf(buyer.getUUID()).ifPresent(current -> sendVendorCatalog(server, buyer, current));
    }

    /** The catalog for one player's own run, sent on request (the vendor command) or after a purchase. */
    public static void sendVendorCatalog(MinecraftServer server, ServerPlayer player, PersistedRun run) {
        TowerContent content = TowerDefinitionRegistry.content();
        long balance = TowerWalletStore.get(server).balanceOf(player.getUUID());
        List<VendorCatalogPayload.Entry> entries = content.vendorCatalog().stream()
                .map(service -> catalogEntry(service, run))
                .toList();
        sendVendorCatalog(player, new VendorCatalogPayload(balance, entries));
    }

    private static VendorCatalogPayload.Entry catalogEntry(VendorServiceDefinition service, PersistedRun run) {
        int cap = service.maxPurchasesPerRun();
        int remaining = cap <= 0 ? -1 : Math.max(0, cap - run.purchasesOf(service.id()));
        return new VendorCatalogPayload.Entry(service.id(), service.displayName(), service.priceCobbleDollars(), remaining);
    }

    private static void sendVendorCatalog(ServerPlayer player, VendorCatalogPayload payload) {
        if (ServerPlayNetworking.canSend(player, VendorCatalogPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
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

    /** Silently does nothing for a client that never registered the channel. */
    public static void sendScoutingReveal(ServerPlayer player, ScoutingRevealPayload payload) {
        if (ServerPlayNetworking.canSend(player, ScoutingRevealPayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
