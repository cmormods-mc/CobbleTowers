package com.cobbletowers.network;

import com.cobbletowers.region.RegionService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Answers the Ranks tab: builds the duel it asked for and sends it back by whichever route the player has. */
public final class RanksScreens {

    private RanksScreens() {}

    public static void handleRequest(MinecraftServer server, ServerPlayer player, RanksRequestPayload request) {
        ClientUi.send(player, RegionService.view(server, player, request.left(), request.right()));
    }
}
