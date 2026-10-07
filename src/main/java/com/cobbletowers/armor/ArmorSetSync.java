package com.cobbletowers.armor;

import com.cobbletowers.TowerLog;
import com.cobbletowers.network.ArmorSetsPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps clients' tooltip data current (P25): the set list on join and after each reload. Clients without the channel
 * are skipped.
 */
public final class ArmorSetSync {

    private static boolean installed;

    private ArmorSetSync() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.getPlayer()));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) sendToAll(server);
        });
    }

    static ArmorSetsPayload payload() {
        List<ArmorSetView> views = new ArrayList<>();
        for (ArmorSetDefinition set : ArmorSetRegistry.sets()) views.add(ArmorSetViews.of(set));
        return new ArmorSetsPayload(views);
    }

    public static void sendToAll(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) send(player);
    }

    public static void send(ServerPlayer player) {
        try {
            if (!ServerPlayNetworking.canSend(player, ArmorSetsPayload.TYPE)) return;
            ServerPlayNetworking.send(player, payload());
            TowerLog.info("Sent the armor set tooltips to {}", player.getGameProfile().getName());
        } catch (RuntimeException ex) {
            // Tooltips are decoration. A failure here must never cost a player their join.
            TowerLog.error("Could not send the armor set tooltips to " + player.getGameProfile().getName(), ex);
        }
    }
}
