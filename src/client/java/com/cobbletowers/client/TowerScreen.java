package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public abstract class TowerScreen extends Screen {
    private final long opened = System.nanoTime();
    protected final TowerUi.Theme theme;
    protected TowerScreen(Component title, TowerUi.Theme theme) { super(title); this.theme = theme; }
    protected long uiAge() { return (System.nanoTime() - opened) / 1_000_000L; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        TowerShader.draw(g, width, height, theme, uiAge());
        int edge = Math.min(12, width / 30);
        g.fill(edge, 4, width - edge, 5, 0xFF3A4D69);
        g.fill(edge, height - 4, width - edge, height - 3, 0xFF33445F);
        if (TowerUiSettings.motion && uiAge() < 450) {
            int w = (int)((width - edge * 2) * Math.min(1, uiAge() / 450f));
            g.fill(edge, 4, edge + w, 5, theme.accent);
        }
        if (showUiHint()) g.drawString(font, CobbleTowersClient.PRESENTATION.getTranslatedKeyMessage().getString()+" UI", 6, height - 11, TowerUi.MUTED, false);
    }
    protected boolean showUiHint() { return true; }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (CobbleTowersClient.PRESENTATION.matches(key,scan)) { Minecraft.getInstance().setScreen(new TowerOptionsScreen(this)); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
