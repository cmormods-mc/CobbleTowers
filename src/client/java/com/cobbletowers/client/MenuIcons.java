package com.cobbletowers.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The menu icons (P37): 16x16 pixel pictures in the mod's own oak, bronze, parchment and burgundy, drawn once into small textures and shown
 * as one quad each. They replace the Cobblemon sprites the menus used as stand-ins; a Pokemon sprite stays only where a Pokemon is the subject.
 * Adding an icon is one entry in {@link #paint}. An unknown key draws a bronze diamond, never nothing.
 */
final class MenuIcons {
    private static final int O = 0xFF211510, B = 0xFFB8935A, BL = 0xFFE3BD7F, W = 0xFF6B4A33, WL = 0xFF9A7448, C = 0xFFF0DFBF,
            S = 0xFF8C95A0, SD = 0xFF5A5E66, R = 0xFF9A3A3A, RL = 0xFFC0453A, G = 0xFF6FA55A, U = 0xFF4F7FA0, UL = 0xFF8CB0C8, Y = 0xFFE3A95E;

    private static final Map<String, ResourceLocation> CACHE = new HashMap<>();
    private static int serial;

    private MenuIcons() {}

    /** Draws an icon at a whole-number scale of 16 GUI pixels ({@code size} 16 or 32). */
    static void draw(GuiGraphics g, String key, int x, int y, int size) {
        ResourceLocation texture = CACHE.computeIfAbsent(key, MenuIcons::build);
        g.blit(texture, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    private static ResourceLocation build(String key) {
        NativeImage image = new NativeImage(16, 16, true);
        image.fillRect(0, 0, 16, 16, 0);
        paint(new Pen(image), key);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "dynamic/icon/" + serial++);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    /** Frees the icon textures (a resource reload may want this). */
    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation texture : CACHE.values()) textures.release(texture);
        CACHE.clear();
    }

    private static final class Pen {
        private final NativeImage image;

        Pen(NativeImage image) {
            this.image = image;
        }

        void px(int x, int y, int argb) {
            if (x < 0 || y < 0 || x > 15 || y > 15) return;
            image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16));
        }

        void r(int x1, int y1, int x2, int y2, int argb) {
            for (int y = y1; y <= y2; y++) for (int x = x1; x <= x2; x++) px(x, y, argb);
        }

        void clr(int x1, int y1, int x2, int y2) {
            r(x1, y1, x2, y2, 0);
        }

        void disc(int cx, int cy, double radius, int argb) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (Math.hypot(x - cx, y - cy) <= radius) px(x, y, argb);
        }

        void ring(int cx, int cy, double radius, double thick, int argb) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) if (Math.abs(Math.hypot(x - cx, y - cy) - radius) <= thick) px(x, y, argb);
        }

        void line(int x1, int y1, int x2, int y2, int argb) {
            int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1, err = dx - dy;
            while (true) {
                px(x1, y1, argb);
                if (x1 == x2 && y1 == y2) return;
                int e2 = 2 * err;
                if (e2 > -dy) { err -= dy; x1 += sx; }
                if (e2 < dx) { err += dx; y1 += sy; }
            }
        }

        /** A dark outline around every non-clear pixel, drawn into the clear ones that touch them. */
        void outline() {
            boolean[][] filled = new boolean[16][16];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) filled[x][y] = (image.getPixelRGBA(x, y) >>> 24) != 0;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (filled[x][y]) continue;
                    boolean near = (x > 0 && filled[x - 1][y]) || (x < 15 && filled[x + 1][y]) || (y > 0 && filled[x][y - 1]) || (y < 15 && filled[x][y + 1]);
                    if (near) px(x, y, O);
                }
            }
        }
    }

    private static void paint(Pen p, String key) {
        switch (key) {
            case "tower" -> {
                p.r(3, 3, 5, 4, S); p.r(7, 3, 8, 4, S); p.r(10, 3, 12, 4, S);
                p.r(3, 5, 12, 14, S); p.r(10, 5, 12, 14, SD);
                p.r(6, 7, 7, 9, O); p.r(6, 7, 6, 8, BL);
                p.r(6, 11, 9, 14, O); p.r(7, 12, 8, 14, W);
                p.outline();
            }
            case "hourglass" -> {
                p.r(3, 1, 12, 2, B); p.r(3, 13, 12, 14, B);
                p.r(4, 3, 11, 4, C); p.r(5, 5, 10, 5, C); p.r(6, 6, 9, 6, C); p.r(7, 7, 8, 8, C);
                p.r(6, 9, 9, 9, C); p.r(5, 10, 10, 10, C); p.r(4, 11, 11, 12, C);
                p.r(5, 3, 10, 4, Y); p.r(6, 5, 9, 5, Y); p.r(7, 7, 8, 7, Y); p.r(5, 11, 10, 12, Y); p.r(6, 10, 9, 10, Y);
                p.outline();
            }
            case "book" -> {
                p.r(2, 3, 7, 13, C); p.r(8, 3, 13, 13, C); p.r(7, 2, 8, 14, B);
                p.r(3, 5, 6, 5, SD); p.r(3, 7, 6, 7, SD); p.r(3, 9, 6, 9, SD); p.r(9, 5, 12, 5, SD); p.r(9, 7, 12, 7, SD); p.r(9, 9, 11, 9, SD);
                p.r(11, 11, 12, 15, R);
                p.outline();
            }
            case "stairs" -> {
                p.r(2, 12, 5, 14, B); p.r(6, 9, 9, 14, B); p.r(10, 6, 13, 14, B);
                p.r(2, 12, 5, 12, BL); p.r(6, 9, 9, 9, BL); p.r(10, 6, 13, 6, BL);
                p.r(11, 1, 12, 4, Y); p.r(10, 2, 13, 3, Y);
                p.outline();
            }
            case "echo" -> {
                p.ring(8, 8, 6, 0.6, UL); p.ring(8, 8, 4, 0.6, U); p.ring(8, 8, 2, 0.5, UL); p.disc(8, 8, 1, Y);
                p.outline();
            }
            case "group" -> {
                p.disc(5, 5, 2, BL); p.r(2, 8, 8, 14, B);
                p.disc(11, 6, 2, C); p.r(8, 9, 14, 14, R);
                p.outline();
            }
            case "cards" -> {
                p.r(2, 4, 8, 14, R); p.r(5, 3, 11, 13, B); p.r(8, 2, 14, 12, C);
                p.r(10, 4, 12, 6, Y); p.r(9, 8, 13, 8, SD); p.r(9, 10, 12, 10, SD);
                p.outline();
            }
            case "gear" -> {
                p.disc(8, 8, 5, B);
                p.r(7, 1, 8, 3, B); p.r(7, 13, 8, 15, B); p.r(1, 7, 3, 8, B); p.r(13, 7, 15, 8, B);
                p.r(3, 3, 4, 4, B); p.r(11, 3, 12, 4, B); p.r(3, 11, 4, 12, B); p.r(11, 11, 12, 12, B);
                p.disc(8, 8, 2, 0); p.ring(8, 8, 4, 0.5, BL);
                p.outline();
            }
            case "refresh" -> {
                for (int a = 40; a < 340; a += 6) {
                    double rad = Math.toRadians(a);
                    p.px((int) Math.round(8 + 5 * Math.cos(rad)), (int) Math.round(8 - 5 * Math.sin(rad)), BL);
                    p.px((int) Math.round(8 + 4 * Math.cos(rad)), (int) Math.round(8 - 4 * Math.sin(rad)), B);
                }
                p.r(11, 1, 14, 2, BL); p.r(12, 3, 14, 4, BL); p.r(13, 5, 14, 6, BL);
                p.outline();
            }
            case "door" -> {
                p.r(4, 2, 11, 14, W); p.r(5, 3, 10, 13, WL); p.r(5, 7, 10, 7, W); p.r(8, 3, 8, 13, W);
                p.r(9, 8, 10, 9, Y);
                p.r(2, 14, 13, 15, SD);
                p.outline();
            }
            case "pouch" -> {
                p.r(5, 3, 10, 5, W); p.r(4, 6, 11, 14, W); p.r(3, 8, 12, 13, W); p.r(5, 6, 6, 13, WL);
                p.r(5, 5, 10, 6, B);
                p.r(7, 9, 8, 11, Y); p.r(6, 10, 9, 10, Y);
                p.outline();
            }
            case "coins" -> {
                p.r(3, 10, 12, 13, B); p.r(3, 10, 12, 10, BL); p.r(4, 6, 13, 9, Y); p.r(4, 6, 13, 6, BL); p.r(6, 2, 11, 5, BL);
                p.r(8, 3, 9, 4, B);
                p.outline();
            }
            case "trophy" -> {
                p.r(5, 2, 10, 8, Y); p.r(6, 8, 9, 9, Y); p.r(2, 3, 4, 5, B); p.r(11, 3, 13, 5, B);
                p.r(7, 10, 8, 11, B); p.r(5, 12, 10, 13, B); p.r(6, 3, 6, 6, BL);
                p.outline();
            }
            case "lock" -> {
                p.ring(8, 6, 3.4, 0.9, S); p.clr(7, 5, 8, 7);
                p.r(4, 8, 11, 14, B); p.r(4, 8, 11, 8, BL); p.r(7, 10, 8, 12, O);
                p.outline();
            }
            case "check" -> {
                p.line(3, 8, 6, 11, G); p.line(3, 9, 6, 12, G); p.line(6, 11, 12, 4, G); p.line(6, 12, 12, 5, G);
                p.outline();
            }
            case "star" -> {
                p.r(7, 1, 8, 15, Y); p.r(1, 6, 14, 9, Y); p.r(4, 4, 11, 11, Y); p.r(3, 12, 5, 14, Y); p.r(10, 12, 12, 14, Y);
                p.r(6, 5, 9, 8, BL);
                p.outline();
            }
            case "gift" -> {
                p.r(3, 7, 12, 14, R); p.r(2, 5, 13, 7, RL); p.r(7, 5, 8, 14, B);
                p.r(5, 2, 7, 4, B); p.r(8, 2, 10, 4, B);
                p.outline();
            }
            case "sun" -> {
                p.disc(8, 8, 3.5, Y);
                p.r(7, 1, 8, 3, BL); p.r(7, 12, 8, 14, BL); p.r(1, 7, 3, 8, BL); p.r(12, 7, 14, 8, BL);
                p.r(3, 3, 4, 4, BL); p.r(11, 3, 12, 4, BL); p.r(3, 11, 4, 12, BL); p.r(11, 11, 12, 12, BL);
                p.outline();
            }
            case "calendar" -> {
                p.r(2, 3, 13, 14, C); p.r(2, 3, 13, 6, R); p.r(4, 1, 5, 4, SD); p.r(10, 1, 11, 4, SD);
                p.r(4, 8, 5, 9, SD); p.r(7, 8, 8, 9, SD); p.r(10, 8, 11, 9, SD); p.r(4, 11, 5, 12, SD); p.r(7, 11, 8, 12, B);
                p.outline();
            }
            case "flag" -> {
                p.r(3, 2, 4, 14, W); p.r(5, 3, 13, 9, R); p.r(5, 3, 13, 4, RL); p.r(5, 9, 9, 10, R);
                p.outline();
            }
            case "eye" -> {
                p.r(1, 6, 14, 10, C); p.r(3, 4, 12, 12, C); p.r(5, 3, 10, 13, C);
                p.disc(8, 8, 3, U); p.disc(8, 8, 1, O); p.px(7, 7, C);
                p.outline();
            }
            case "scroll" -> {
                p.r(3, 3, 12, 13, C); p.r(2, 2, 13, 4, WL); p.r(2, 12, 13, 14, WL);
                p.r(5, 6, 10, 6, SD); p.r(5, 8, 10, 8, SD); p.r(5, 10, 8, 10, SD);
                p.outline();
            }
            case "crown" -> {
                p.r(2, 5, 4, 12, Y); p.r(6, 3, 9, 12, Y); p.r(11, 5, 13, 12, Y); p.r(2, 9, 13, 13, Y); p.r(2, 12, 13, 13, B);
                p.r(7, 5, 8, 6, RL); p.r(4, 10, 4, 10, R); p.r(11, 10, 11, 10, R);
                p.outline();
            }
            case "left" -> {
                p.line(10, 3, 5, 8, BL); p.line(10, 4, 5, 9, BL); p.line(5, 8, 10, 13, BL); p.line(5, 7, 10, 12, BL);
                p.outline();
            }
            case "right" -> {
                p.line(5, 3, 10, 8, BL); p.line(5, 4, 10, 9, BL); p.line(10, 8, 5, 13, BL); p.line(10, 7, 5, 12, BL);
                p.outline();
            }
            case "coin" -> {
                p.disc(8, 8, 5.5, Y); p.ring(8, 8, 4, 0.5, BL); p.r(7, 5, 8, 11, B);
                p.outline();
            }
            default -> {
                p.r(7, 2, 8, 13, B); p.r(2, 7, 13, 8, B); p.r(5, 5, 10, 10, BL);
                p.outline();
            }
        }
    }
}
