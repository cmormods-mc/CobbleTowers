package com.cobbletowers.network;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.definition.VendorServiceDefinition;
import com.cobbletowers.economy.VendorPurchaseService;
import com.cobbletowers.intermission.IntermissionService;
import com.cobbletowers.lobby.LobbyService;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerWalletStore;
import com.cobbletowers.runtime.ParticipantService;
import com.cobbletowers.spectator.SpectatorPresentation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
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
        PayloadTypeRegistry.playS2C().register(PlayStatePayload.TYPE, PlayStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PlayActionPayload.TYPE, PlayActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RegistrationStatePayload.TYPE, RegistrationStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(IntermissionStatePayload.TYPE, IntermissionStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(IntermissionActionPayload.TYPE, IntermissionActionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ArmorSetsPayload.TYPE, ArmorSetsPayload.STREAM_CODEC);
    }

    /** The server-side half. */
    public static void installServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CycleTeammatePayload.TYPE, (payload, context) -> {
            ServerPlayer spectator = context.player();
            context.server().execute(() -> handleCycle(spectator, payload.next()));
        });
        ServerPlayNetworking.registerGlobalReceiver(IntermissionActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handleIntermissionAction(context.server(), player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(PlayActionPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> handlePlayAction(context.server(), player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(VendorPurchasePayload.TYPE, (payload, context) -> {
            ServerPlayer buyer = context.player();
            context.server().execute(() -> handlePurchase(context.server(), buyer, payload));
        });
    }

    /** The play screen's buttons: each does what the matching {@code /cobbletowers play} subcommand does. */
    private static void handlePlayAction(MinecraftServer server, ServerPlayer player, PlayActionPayload payload) {
        String reply;
        try {
            reply = switch (payload.action()) {
                case SELECT_TOWER -> LobbyService.select(server, player, ResourceLocation.parse(payload.argument()));
                case INVITE -> {
                    ServerPlayer target = server.getPlayerList().getPlayerByName(payload.argument());
                    yield target == null ? "No player named " + payload.argument() + " is online."
                            : LobbyService.invite(server, player, target);
                }
                case ACCEPT, DECLINE -> {
                    ServerPlayer host = server.getPlayerList().getPlayerByName(payload.argument());
                    if (host == null) yield "That team no longer exists.";
                    yield payload.action() == PlayActionPayload.Action.ACCEPT
                            ? LobbyService.accept(server, player, host.getUUID())
                            : LobbyService.decline(server, player, host.getUUID());
                }
                case START -> LobbyService.start(server, player);
                case LEAVE -> LobbyService.leave(server, player);
                case REFRESH -> "";
                case OPEN_CHOOSER -> {
                    LobbyService.sendRegistration(server, player, "", true);
                    yield "";
                }
                case TOGGLE_POKEMON -> LobbyService.choose(server, player, java.util.UUID.fromString(payload.argument()));
                case CLEAR_CHOICE -> LobbyService.clearChoice(server, player);
            };
        } catch (RuntimeException ex) {
            // A malformed id or similar from a modified client: refused, never trusted, never fatal.
            TowerLog.warn("Play action {} from {} was refused: {}", payload.action(), player.getUUID(), ex.toString());
            reply = "That did not work.";
        }
        LobbyService.openScreenWithMessage(server, player, reply);
    }

    /** The intermission screen's buttons: each does what the matching {@code /cobbletowers play} subcommand does. */
    private static void handleIntermissionAction(MinecraftServer server, ServerPlayer player,
                                                 IntermissionActionPayload payload) {
        String reply = switch (payload.action()) {
            case PICK_CARD -> IntermissionService.pick(server, player, payload.argument());
            case READY -> IntermissionService.ready(server, player, true);
            case UNREADY -> IntermissionService.ready(server, player, false);
            case CASH_OUT -> IntermissionService.cashOut(server, player, true);
            case STAY -> IntermissionService.cashOut(server, player, false);
            case VENDOR -> IntermissionService.vendor(server, player);
            case REFRESH -> "";
        };
        if (payload.action() != IntermissionActionPayload.Action.VENDOR) {
            IntermissionService.openScreen(server, player, reply);
        }
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
        String targetName = nameOf(server, payload.targetPlayerId());
        String message = VendorPurchaseService.describe(result, targetName, buyer.getUUID().equals(payload.targetPlayerId()));
        if (result == VendorPurchaseService.Result.SUCCESS && !buyer.getUUID().equals(payload.targetPlayerId())) {
            // The teammate is told who paid, since their party just changed under them (TDS #18).
            ServerPlayer target = server.getPlayerList().getPlayer(payload.targetPlayerId());
            if (target != null) {
                target.sendSystemMessage(Component.literal(buyer.getGameProfile().getName() + " bought a service for you."));
            }
        }
        // Refreshed either way: a failed purchase (sold out, insufficient funds) still needs the
        // caller's screen to show the balance and remaining-purchase count it actually has now.
        ParticipantService.runOf(buyer.getUUID())
                .ifPresent(current -> sendVendorCatalog(server, buyer, current, message));
    }

    private static String nameOf(MinecraftServer server, UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player == null ? playerId.toString().substring(0, 8) : player.getGameProfile().getName();
    }

    /** The catalog for one player's own run, sent on request (the vendor command) or after a purchase. */
    public static void sendVendorCatalog(MinecraftServer server, ServerPlayer player, PersistedRun run) {
        sendVendorCatalog(server, player, run, "");
    }

    public static void sendVendorCatalog(MinecraftServer server, ServerPlayer player, PersistedRun run, String message) {
        TowerContent content = TowerDefinitionRegistry.content();
        long balance = TowerWalletStore.get(server).balanceOf(player.getUUID());
        List<VendorCatalogPayload.Entry> entries = content.vendorCatalog().stream()
                .map(service -> catalogEntry(service, run, player))
                .toList();
        // Everyone still in the run, the caller first: the picker defaults to "yourself".
        List<VendorCatalogPayload.Teammate> team = new java.util.ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.state().isInRun()) continue;
            ServerPlayer member = server.getPlayerList().getPlayer(participant.playerId());
            VendorCatalogPayload.Teammate entry = new VendorCatalogPayload.Teammate(participant.playerId(),
                    nameOf(server, participant.playerId()), member != null);
            if (participant.playerId().equals(player.getUUID())) team.add(0, entry);
            else team.add(entry);
        }
        sendVendorCatalog(player, new VendorCatalogPayload(balance, entries, team, message));
    }

    private static VendorCatalogPayload.Entry catalogEntry(VendorServiceDefinition service, PersistedRun run,
                                                          ServerPlayer player) {
        int cap = service.maxPurchasesPerRun();
        int remaining = cap <= 0 ? -1 : Math.max(0, cap - run.purchasesOf(service.id()));
        return new VendorCatalogPayload.Entry(service.id(), service.displayName(),
                com.cobbletowers.armor.ArmorBonusEffects.vendorPrice(player, service.priceCobbleDollars()), remaining);
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

    /** Silently does nothing for a client that never registered the channel (the commands still work). */
    public static void sendIntermissionState(ServerPlayer player, IntermissionStatePayload payload) {
        if (ServerPlayNetworking.canSend(player, IntermissionStatePayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Silently does nothing for a client that never registered the channel (the commands still work). */
    public static void sendRegistration(ServerPlayer player, RegistrationStatePayload payload) {
        if (ServerPlayNetworking.canSend(player, RegistrationStatePayload.TYPE)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** Silently does nothing for a client that never registered the channel (the commands still work). */
    public static void sendPlayState(ServerPlayer player, PlayStatePayload payload) {
        if (ServerPlayNetworking.canSend(player, PlayStatePayload.TYPE)) {
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
