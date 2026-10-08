package com.cobbletowers.client;

import com.cobbletowers.network.CycleTeammatePayload;
import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.RegistrationStatePayload;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.ScoutingRevealPayload;
import com.cobbletowers.network.SpectatorPanelPayload;
import com.cobbletowers.network.VendorCatalogPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The client half of P11/P12: HUD panel, reward reveal, vendor shop, scouting report and the spectator-cycling
 * keybind. Payload types are registered from the common entrypoint; only receivers, HUD callback and keybinds live
 * here.
 */
public final class CobbleTowersClient implements ClientModInitializer {

    static final KeyMapping PRESENTATION=new KeyMapping("key.cobbletowers.presentation",InputConstants.Type.KEYSYM,org.lwjgl.glfw.GLFW.GLFW_KEY_F8,KeyMapping.CATEGORY_MISC);
    private static final KeyMapping CYCLE_NEXT = new KeyMapping("key.cobbletowers.cycle_next",
            InputConstants.Type.KEYSYM, InputConstants.KEY_PERIOD, KeyMapping.CATEGORY_MISC);
    private static final KeyMapping CYCLE_PREVIOUS = new KeyMapping("key.cobbletowers.cycle_previous",
            InputConstants.Type.KEYSYM, InputConstants.KEY_COMMA, KeyMapping.CATEGORY_MISC);

    private static final KeyMapping OPEN_MENU = new KeyMapping("key.cobbletowers.open_menu",
            InputConstants.Type.KEYSYM, InputConstants.KEY_J, KeyMapping.CATEGORY_MISC);

    @Override
    public void onInitializeClient() {
        TowerUiSettings.load();
        KeyBindingHelper.registerKeyBinding(OPEN_MENU);
        KeyBindingHelper.registerKeyBinding(PRESENTATION);
        KeyBindingHelper.registerKeyBinding(CYCLE_NEXT);
        KeyBindingHelper.registerKeyBinding(CYCLE_PREVIOUS);

        // Development only: pictures of the screens, when COBBLETOWERS_SCREENSHOTS names a directory.
        ScreenshotHarness.installIfRequested();
        ClientRemote.installIfRequested();

        // Armor set tooltips (P25): the sets the server describes, shown on their pieces.
        ArmorTooltips.install();

        ClientPlayNetworking.registerGlobalReceiver(SpectatorPanelPayload.TYPE,
                (payload, context) -> context.client().execute(() -> SpectatorHud.updatePanel(payload)));
        ClientPlayNetworking.registerGlobalReceiver(RewardRevealPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Minecraft.getInstance().setScreen(new RewardRevealScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(ScoutingRevealPayload.TYPE, (payload, context) ->
                context.client().execute(() -> Minecraft.getInstance().setScreen(new ScoutingScreen(payload))));
        ClientPlayNetworking.registerGlobalReceiver(VendorCatalogPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof VendorScreen open) {
                        // A refresh after a purchase: updated in place rather than replaced, so the
                        // screen does not flicker closed-and-reopened under the player's own click.
                        open.updateCatalog(payload);
                    } else {
                        Minecraft.getInstance().setScreen(new VendorScreen(payload));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(com.cobbletowers.network.TowerFeatureState.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof TowerFeatureScreen feature) feature.accept(payload);
                }));

        ClientPlayNetworking.registerGlobalReceiver(com.cobbletowers.network.TowerHallStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof TowerHallScreen hall) hall.update(payload);
                    else context.client().setScreen(new TowerHallScreen(payload));
                }));

        ClientPlayNetworking.registerGlobalReceiver(com.cobbletowers.network.TrackStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().screen instanceof TowerHallScreen hall) hall.updateTracks(payload);
                }));

        ClientPlayNetworking.registerGlobalReceiver(PlayStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof TowerHallScreen hall && !payload.open()) {
                        hall.updateLobby(payload);
                    } else if (current instanceof TowerFeatureScreen feature && !payload.open()) {
                        if (feature.hall() != null) feature.hall().updateLobby(payload);
                    } else if (current instanceof PlayScreen open) {
                        // A change to the lobby: redrawn in place, as the vendor screen is.
                        open.update(payload);
                    } else if (payload.open()) {
                        Minecraft.getInstance().setScreen(new PlayScreen(payload, current instanceof TowerHallScreen hall ? hall : current instanceof TowerFeatureScreen feature ? feature.hall() : null));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(IntermissionStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof IntermissionScreen open) {
                        open.update(payload);
                    } else if (payload.open() && !(current instanceof RewardRevealScreen)) {
                        // Never over a reveal the player is reading: that screen asks for this one back
                        // when it closes.
                        Minecraft.getInstance().setScreen(new IntermissionScreen(payload));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(com.cobbletowers.network.MasteryScreenPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof MasteryScreen open) {
                        open.update(payload);
                    } else if (payload.open()) {
                        Minecraft.getInstance().setScreen(new MasteryScreen(payload));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(com.cobbletowers.network.RentalDraftPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof RentalPackScreen open) {
                        open.accept(payload);
                    } else {
                        Minecraft.getInstance().setScreen(new RentalPackScreen(payload));
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(RegistrationStatePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof RegistrationScreen open) {
                        open.update(payload);
                    } else if (payload.open()) {
                        Minecraft.getInstance().setScreen(new RegistrationScreen(payload));
                    }
                }));

        HudRenderCallback.EVENT.register(SpectatorHud.INSTANCE);

        // Polled once a tick: {@code consumeClick()} fires once per press however long the frame gap.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.player != null) client.player.connection.sendCommand("tower menu");
            }
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
