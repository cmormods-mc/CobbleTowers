package com.cobbletowers.network;

import com.cobbletowers.webgui.WebGuiBridge;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place the server decides how a screen payload reaches a player: through CobbleTowers' own client screens, or
 * through the WebGUI page. Used wherever the code used to ask {@code ServerPlayNetworking.canSend} and then
 * {@code send}, because "can this player see the screen" now has two honest answers.
 *
 * <p>With WebGUI absent or the bridge off this is exactly {@code canSend}/{@code send}.
 */
public final class ClientUi {

    private ClientUi() {}

    /** Whether this player has any UI for {@code type}: a native client channel for it, or a WebGUI page that shows it. */
    public static boolean canShow(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return WebGuiBridge.takesOver(player, type) || ServerPlayNetworking.canSend(player, type);
    }

    /** Sends a state payload by whichever route applies; silently nothing for a player with neither, as before. */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (WebGuiBridge.deliver(player, payload)) return;
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }
}
