package com.cobbletowers.client;

import com.cobbletowers.CobbleTowers;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import net.minecraft.resources.ResourceLocation;

/**
 * The warm pixel renderer: oak, bronze, parchment, chocolate and burgundy from the tiles and nine-slice frames under
 * {@code textures/gui/pixel}. Integer-positioned and nearest-sampled; no shader or blur. Corners are drawn at {@link
 * #CORNER} GUI pixels and never stretch.
 */
public final class PixelUi {
    public static final int CORNER = 8;
    private static final int SRC = 48, SRC_CORNER = 16;

    public enum Frame {
        DARK("frame_dark"), PARCHMENT("frame_parchment"), BUTTON("button_normal"), BUTTON_HOVER("button_hover"),
        BUTTON_PRESSED("button_pressed"), BUTTON_DISABLED("button_disabled"), SECONDARY("button_secondary"), TAB("tab_active");
        final ResourceLocation texture;
        Frame(String name) { texture = CobbleTowers.id("textures/gui/pixel/" + name + ".png"); }
    }

    public enum Tile {
        OAK("tile_oak"), PARCHMENT("tile_parchment"), BRONZE("tile_bronze"), BURGUNDY("tile_cloth_burgundy");
        final ResourceLocation texture;
        Tile(String name) { texture = CobbleTowers.id("textures/gui/pixel/" + name + ".png"); }
    }

    private PixelUi() {}

    /**
     * A nine-slice frame drawn as one batched draw call with the state a vanilla {@code blit} sets. Tiny boxes shrink
     * the corners instead of overlapping them.
     */
    public static void frame(GuiGraphics g, Frame frame, int x, int y, int w, int h) {
        if (w < 2 || h < 2) return;
        int c = Math.max(1, Math.min(CORNER, Math.min(w, h) / 2));
        int mw = w - 2 * c, mh = h - 2 * c;
        int m = SRC - 2 * SRC_CORNER;
        RenderSystem.setShaderTexture(0, frame.texture);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        int far = SRC - SRC_CORNER;
        quad(b, matrix, x, y, c, c, 0, 0, SRC_CORNER, SRC_CORNER);
        quad(b, matrix, x + w - c, y, c, c, far, 0, SRC_CORNER, SRC_CORNER);
        quad(b, matrix, x, y + h - c, c, c, 0, far, SRC_CORNER, SRC_CORNER);
        quad(b, matrix, x + w - c, y + h - c, c, c, far, far, SRC_CORNER, SRC_CORNER);
        if (mw > 0) {
            quad(b, matrix, x + c, y, mw, c, SRC_CORNER, 0, m, SRC_CORNER);
            quad(b, matrix, x + c, y + h - c, mw, c, SRC_CORNER, far, m, SRC_CORNER);
        }
        if (mh > 0) {
            quad(b, matrix, x, y + c, c, mh, 0, SRC_CORNER, SRC_CORNER, m);
            quad(b, matrix, x + w - c, y + c, c, mh, far, SRC_CORNER, SRC_CORNER, m);
        }
        if (mw > 0 && mh > 0) quad(b, matrix, x + c, y + c, mw, mh, SRC_CORNER, SRC_CORNER, m, m);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /**
     * One textured quad: a box of {@code w}x{@code h} GUI pixels showing the {@code uw}x{@code vh} texel region at
     * ({@code u},{@code v}).
     */
    private static void quad(BufferBuilder b, Matrix4f matrix, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        float u1 = u / (float) SRC, u2 = (u + uw) / (float) SRC, v1 = v / (float) SRC, v2 = (v + vh) / (float) SRC;
        b.addVertex(matrix, x, y, 0).setUv(u1, v1);
        b.addVertex(matrix, x, y + h, 0).setUv(u1, v2);
        b.addVertex(matrix, x + w, y + h, 0).setUv(u2, v2);
        b.addVertex(matrix, x + w, y, 0).setUv(u2, v1);
    }

    /** A 16x16 material tiled across a box at integer positions (never stretched). */
    public static void tile(GuiGraphics g, Tile tile, int x, int y, int w, int h) {
        if (w < 1 || h < 1) return;
        // One quad, with the texture repeating across it (as vanilla's own menu background does), not a quad per
        // 16x16 tile.
        g.blit(tile.texture, x, y, w, h, 0f, 0f, w, h, 16, 16);
    }

    /**
     * A recessed chocolate panel with a bronze edge. {@code accent} marks the selected state; {@code hover} (0..1)
     * lights the brackets.
     */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int accent, float hover) {
        frame(g, Frame.DARK, x, y, w, h);
        if (hover > .1f && w > 20 && h > 14) brackets(g, x + 2, y + 2, w - 4, h - 4, TowerUi.BRONZE_LIGHT);
    }

    /** A parchment content sheet; text on it is {@link TowerUi#INK}. */
    public static void sheet(GuiGraphics g, int x, int y, int w, int h) {
        frame(g, Frame.PARCHMENT, x, y, w, h);
    }

    /** A shelf or counter plank: oak with a bronze lip on top and a dark underside, for things to stand on. */
    public static void plank(GuiGraphics g, int x, int y, int w, int h) {
        if (w < 4 || h < 3) return;
        tile(g, Tile.OAK, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0x44000000);
        g.fill(x, y, x + w, y + 1, TowerUi.BRONZE);
        g.fill(x, y + h - 1, x + w, y + h, TowerUi.OUTLINE);
    }

    /** A small bronze tag (a price, a label) with a punched hole at its left edge; the text is dark ink. */
    public static void tag(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int x, int y, boolean dim) {
        int w = font.width(text) + 14;
        int face = dim ? 0xFF7A6A55 : TowerUi.BRONZE;
        g.fill(x, y, x + w, y + 12, TowerUi.OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + 11, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, dim ? 0xFF8F7A5E : TowerUi.BRONZE_LIGHT);
        g.fill(x + 3, y + 5, x + 5, y + 7, TowerUi.OUTLINE);
        g.drawString(font, text, x + 8, y + 2, TowerUi.INK, false);
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
