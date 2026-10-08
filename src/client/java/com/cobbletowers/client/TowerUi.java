package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/** Shared pixel geometry and palette; no game state or network decisions live in the renderer. */
public final class TowerUi {
    public static final int OUTLINE = 0xFF211510;
    public static final int OAK = 0xFF38271F;
    public static final int BRONZE = 0xFFA77B46;
    public static final int BRONZE_LIGHT = 0xFFE3BD7F;
    public static final int PARCHMENT = 0xFFE5CCA1;
    public static final int INK = 0xFF40291E;
    public static final int TEXT = 0xFFF0DFBF;
    public static final int MUTED = 0xFFC6AC87;
    public static final int BURGUNDY = 0xFF773C38;
    public static final int SAGE = 0xFFA9B781;
    public static final int ECHO = 0xFFB6A1C5;
    /** Warning/refusal text on a dark surface (readable on chocolate, unlike pure red). */
    public static final int DANGER = 0xFFE08A78;

    public enum Theme {
        LOBBY(BRONZE, 0), PARTY(SAGE, 1), MODIFIER(BURGUNDY, 2),
        VENDOR(BRONZE_LIGHT, 3), SCOUT(SAGE, 4), REWARDS(BRONZE_LIGHT, 5),
        MASTERY(SAGE, 6), RENTAL(BURGUNDY, 7);
        public final int accent;
        public final int effect;
        Theme(int accent, int effect) { this.accent = accent; this.effect = effect; }
    }

    private TowerUi() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent) {
        PixelUi.panel(g, x, y, w, h, accent, 0);
    }

    /** Green for a benefit and red for a cost, readable on the parchment sheets (the server marks the lines, see ModifierMenuText). */
    public static final int GOOD_INK = 0x2F7A2F;
    public static final int BAD_INK = 0xB02424;

    /** A card line as drawn: the server's benefit or cost marker is dropped and becomes the colour. */
    public static net.minecraft.network.chat.Component styled(String line) {
        if (line.startsWith("[+] ")) return net.minecraft.network.chat.Component.literal(line.substring(4)).withColor(GOOD_INK);
        if (line.startsWith("[-] ")) return net.minecraft.network.chat.Component.literal(line.substring(4)).withColor(BAD_INK);
        return net.minecraft.network.chat.Component.literal(line);
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
