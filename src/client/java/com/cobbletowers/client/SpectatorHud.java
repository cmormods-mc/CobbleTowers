package com.cobbletowers.client;

import com.cobbletowers.network.SpectatorPanelPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The spectator information panel TDS #25 asks for (P11): a small dark framed slip with cream text.
 *
 * <p>Shown only while the client's camera is riding a teammate rather than the local player -- the
 * same fact {@code SpectatorPresentation} tracks server-side to know a player is spectating, read back
 * here from the one place the client already has it, with no packet of its own needed to say so. It draws the same
 * nine-slice frame as every other panel and never captures a framebuffer.
 */
public final class SpectatorHud implements HudRenderCallback {

    private static volatile SpectatorPanelPayload latest;

    private SpectatorHud() {}

    public static final SpectatorHud INSTANCE = new SpectatorHud();

    /** The most recent panel data from the server; overwritten by every enter, cycle and refresh. */
    public static void updatePanel(SpectatorPanelPayload payload) {
        latest = payload;
    }

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        SpectatorPanelPayload payload = latest;
        if (payload == null || client.player == null || client.getCameraEntity() == client.player) return;

        Font font = client.font;
        String[] lines = {
                "Following " + payload.teammateName(),
                payload.remainingCount() + "/" + payload.totalCount() + " Pokemon remaining",
                "Floor " + payload.floorIndex() + " -- " + payload.runStateLabel(),
        };

        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        int pad = 7;
        int x = 6;
        int y = 6;
        int lineStep = font.lineHeight + 1;
        PixelUi.frame(graphics, PixelUi.Frame.DARK, x, y, width + pad * 2, lines.length * lineStep + pad * 2 - 1);
        int textY = y + pad;
        for (int i = 0; i < lines.length; i++) {
            graphics.drawString(font, lines[i], x + pad, textY, i == 0 ? TowerUi.BRONZE_LIGHT : TowerUi.TEXT, false);
            textY += lineStep;
        }
    }
}
