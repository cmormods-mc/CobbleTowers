package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Shared pixel geometry; no game state or network decisions live in the renderer. */
public final class TowerUi {
    public static final int INK = 0xFF0D0E12;
    public static final int PAPER = 0xFFDCE4EF;
    public static final int TEXT = 0xFFE3DDD0;
    public static final int MUTED = 0xFF9BA7AF;

    public enum Theme {
        LOBBY(0xFFFF8C00, 0), PARTY(0xFF61C6CD, 1), MODIFIER(0xFFFF8C00, 2),
        VENDOR(0xFFDCA45C, 3), SCOUT(0xFF61C6CD, 4), REWARDS(0xFFFFB64C, 5),
        MASTERY(0xFF61C6CD, 6), RENTAL(0xFFFF8C00, 7);
        public final int accent;
        public final int effect;
        Theme(int accent, int effect) { this.accent = accent; this.effect = effect; }
    }

    private TowerUi() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        TowerShader.panel(g,x,y,w,h,accent,0);
    }

    public static void label(GuiGraphics g, Font font, String text, int x, int y, int max, int color) {
        g.drawString(font, font.plainSubstrByWidth(text, Math.max(1, max)), x, y, color, false);
    }

    public static int wrapped(GuiGraphics g, Font font, String text, int x, int y, int width, int color) {
        for (var line : font.split(Component.literal(text), Math.max(24, width))) {
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return y;
    }
}
