package com.cobbletowers.client;

import com.cobbletowers.network.CycleTeammatePayload;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.SpectatorPanelPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The client half of P11: a HUD panel, a reward reveal screen, and the keybind that drives cycling.
 *
 * <p>CobbleTowers' first client entrypoint. Payload <em>types</em> are registered from the common
 * {@code main} entrypoint ({@code TowerNetworking.registerPayloadTypes}), which Fabric Loader also
 * runs on the client -- only the client's own receivers, HUD callback and keybinds live here.
 */
public final class CobbleTowersClient implements ClientModInitializer {

    private static final KeyMapping CYCLE_NEXT = new KeyMapping("key.cobbletowers.cycle_next",
            InputConstants.Type.KEYSYM, InputConstants.KEY_PERIOD, KeyMapping.CATEGORY_MISC);
    private static final KeyMapping CYCLE_PREVIOUS = new KeyMapping("key.cobbletowers.cycle_previous",
            InputConstants.Type.KEYSYM, InputConstants.KEY_COMMA, KeyMapping.CATEGORY_MISC);

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(CYCLE_NEXT);
        KeyBindingHelper.registerKeyBinding(CYCLE_PREVIOUS);

        ClientPlayNetworking.registerGlobalReceiver(SpectatorPanelPayload.TYPE,
                (payload, context) -> context.client().execute(() -> SpectatorHud.updatePanel(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RewardRevealPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Minecraft.getInstance().setScreen(new RewardRevealScreen(payload))));

        HudRenderCallback.EVENT.register(SpectatorHud.INSTANCE);

        // Polled once a tick rather than handled from the key-press event itself, the standard Fabric
        // shape for a held-key-safe binding: consumeClick() only fires once per press, however long
        // the frame gap, and never double-fires a press the event system already delivered.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (CYCLE_NEXT.consumeClick()) sendCycle(true);
            while (CYCLE_PREVIOUS.consumeClick()) sendCycle(false);
        });
    }

    private static void sendCycle(boolean next) {
        if (ClientPlayNetworking.canSend(CycleTeammatePayload.TYPE)) {
            ClientPlayNetworking.send(new CycleTeammatePayload(next));
        }
    }
}
