package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The warm pixel renderer: oak, aged bronze, parchment, chocolate and burgundy, drawn from the 16x16 material tiles and
 * 48x48 nine-slice frames under {@code textures/gui/pixel}. Everything is integer-positioned and nearest-sampled by
 * the GUI atlas; there is no shader, no framebuffer capture and no blur, so nothing here can fail to compile or leak
 * render state. Corners are drawn at {@link #CORNER} GUI pixels (half the 16px source corner) and never stretch; only
 * the edge strips and the flat centre are scaled, along their own length.
 */
public final class PixelUi {
    public static final int CORNER = 8;
    private static final int SRC = 48, SRC_CORNER = 16;

    public enum Frame {
        DARK("frame_dark"), PARCHMENT("frame_parchment"), BUTTON("button_normal"), BUTTON_HOVER("button_hover"),
        BUTTON_PRESSED("button_pressed"), BUTTON_DISABLED("button_disabled"), SECONDARY("button_secondary"), TAB("tab_active");
        final ResourceLocation texture;
        Frame(String name) { texture = ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/pixel/" + name + ".png"); }
    }

    public enum Tile {
        OAK("tile_oak"), PARCHMENT("tile_parchment"), BRONZE("tile_bronze"), BURGUNDY("tile_cloth_burgundy");
        final ResourceLocation texture;
        Tile(String name) { texture = ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/pixel/" + name + ".png"); }
    }

    private PixelUi() {}

    /** A nine-slice frame. Tiny boxes shrink the corners instead of overlapping them. */
    public static void frame(GuiGraphics g, Frame frame, int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        int c = Math.max(1, Math.min(CORNER, Math.min(w, h) / 2));
        int mw = w - 2 * c, mh = h - 2 * c;
        var t = frame.texture;
        int m = SRC - 2 * SRC_CORNER;
        g.blit(t, x, y, c, c, 0, 0, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x + w - c, y, c, c, SRC - SRC_CORNER, 0, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x, y + h - c, c, c, 0, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, SRC, SRC);
        g.blit(t, x + w - c, y + h - c, c, c, SRC - SRC_CORNER, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, SRC, SRC);
        if (mw > 0) {
            g.blit(t, x + c, y, mw, c, SRC_CORNER, 0, m, SRC_CORNER, SRC, SRC);
            g.blit(t, x + c, y + h - c, mw, c, SRC_CORNER, SRC - SRC_CORNER, m, SRC_CORNER, SRC, SRC);
        }
        if (mh > 0) {
            g.blit(t, x, y + c, c, mh, 0, SRC_CORNER, SRC_CORNER, m, SRC, SRC);
            g.blit(t, x + w - c, y + c, c, mh, SRC - SRC_CORNER, SRC_CORNER, SRC_CORNER, m, SRC, SRC);
        }
        if (mw > 0 && mh > 0) g.blit(t, x + c, y + c, mw, mh, SRC_CORNER, SRC_CORNER, m, m, SRC, SRC);
    }

    /** A 16x16 material tiled across a box at integer positions (never stretched). */
    public static void tile(GuiGraphics g, Tile tile, int x, int y, int w, int h) {
        if (w < 1 || h < 1) return;
        // One quad, with the texture repeating across it (as vanilla's own menu background does), not a quad per 16x16 tile.
        g.blit(tile.texture, x, y, w, h, 0f, 0f, w, h, 16, 16);
    }

    /**
     * A recessed chocolate panel with a bronze edge. {@code accent} marks the selected state with two bronze/burgundy
     * brackets; {@code hover} (0..1) lights them. Text on it is cream.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent, float hover) {
        frame(g, Frame.DARK, x, y, w, h);
        if (hover > .1f && w > 20 && h > 14) brackets(g, x + 2, y + 2, w - 4, h - 4, TowerUi.BRONZE_LIGHT);
    }

    /** A parchment content sheet; text on it is {@link TowerUi#INK}. */
    public static void sheet(GuiGraphics g, int x, int y, int w, int h) {
        frame(g, Frame.PARCHMENT, x, y, w, h);
    }

    /** Four bronze corner brackets, the selection mark for cards. */
    public static void brackets(GuiGraphics g, int x, int y, int w, int h, int color) {
        int a = Math.min(5, Math.min(w, h) / 3);
        for (int cx : new int[] {x, x + w - a}) for (int cy : new int[] {y, y + h - 1}) g.fill(cx, cy, cx + a, cy + 1, color);
        for (int cx : new int[] {x, x + w - 1}) for (int cy : new int[] {y, y + h - a}) g.fill(cx, cy, cx + 1, cy + a, color);
    }

    /** The screen backdrop: dark oak planks with a bronze keyline, and a restrained torch glow when enabled. */
    public static void backdrop(GuiGraphics g, int width, int height, long age) {
        tile(g, Tile.OAK, 0, 0, width, height);
        g.fill(0, 0, width, height, 0x66000000);
        if (TowerUiSettings.glow) {
            // Two stepped warm pools, no flash: a slow two-step breathing when motion is allowed.
            int step = TowerUiSettings.motion ? (int) ((age / 1400L) % 2) : 0;
            g.fillGradient(0, 0, width, Math.min(height, 40), step == 0 ? 0x26B87A33 : 0x32B87A33, 0x00B87A33);
        }
    }
}
