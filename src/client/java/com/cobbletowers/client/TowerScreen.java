package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class TowerScreen extends Screen {
    private final long opened = System.nanoTime();
    protected final TowerUi.Theme theme;
    /** Shadows the screen's vanilla font: every CobbleTowers screen draws in the bundled UI font. */
    protected net.minecraft.client.gui.Font font = TowerFonts.get();
    protected TowerScreen(Component title, TowerUi.Theme theme) { super(title); this.theme = theme; }
    protected long uiAge() { return (System.nanoTime() - opened) / 1_000_000L; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        PixelUi.backdrop(g, width, height, uiAge());
        if (showUiHint()) g.drawString(font, CobbleTowersClient.PRESENTATION.getTranslatedKeyMessage().getString()+" UI", 6, height - 11, TowerUi.MUTED, false);
    }
    protected boolean showUiHint() { return true; }

    /** Closes the whole menu from anywhere, never one step back. */
    protected void closeAll() { Minecraft.getInstance().setScreen(null); }
    /** Menu screens carry a labelled Close button in their header; every other screen gets this corner X. */
    protected boolean cornerClose() { return true; }
    private static final int X_SIZE = 13;
    private boolean overClose(double mx, double my) { return mx >= width - X_SIZE - 2 && mx < width - 2 && my >= 2 && my < 2 + X_SIZE; }
    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        if (!cornerClose()) return;
        boolean hover = overClose(mx, my);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        PixelUi.frame(g, hover ? PixelUi.Frame.BUTTON_HOVER : PixelUi.Frame.BUTTON, width - X_SIZE - 2, 2, X_SIZE, X_SIZE);
        g.drawCenteredString(font, "x", width - 2 - X_SIZE / 2, 5, TowerUi.TEXT);
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (cornerClose() && button == 0 && overClose(mx, my)) { closeAll(); return true; }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (CobbleTowersClient.PRESENTATION.matches(key,scan)) { Minecraft.getInstance().setScreen(new TowerOptionsScreen(this)); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
