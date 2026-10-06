package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * The regional tower landscapes from the approved preview, painted with integer-aligned fills and clipped to their
 * box: sea walls for Tideforge, forest canopy for Rootvale, a moonlit sky for Duskvale and plain stone otherwise.
 * No texture, shader or allocation; the only motion is a two-step window lamp and water glint, both off under
 * reduced motion.
 */
final class TowerPanorama {
    private TowerPanorama() {}

    private record Palette(int sky, int skyLow, int far, int near, int ground, int stone, int stoneShade, int window) {}

    private static Palette palette(String region) {
        if (region.contains("tideforge")) return new Palette(0xFF3E5C6E, 0xFF7C8E94, 0xFF6F7F82, 0xFF4F6670, 0xFF2F5878, 0xFFC9B58F, 0xFF8A7656, 0xFF2D3A44);
        if (region.contains("rootvale")) return new Palette(0xFF47603F, 0xFF7C9A5E, 0xFF6A8A4C, 0xFF40683F, 0xFF4F6B3A, 0xFFC4B48A, 0xFF857553, 0xFF2D3A2A);
        if (region.contains("duskvale")) return new Palette(0xFF3B3348, 0xFF6E5B7E, 0xFF6C5A7A, 0xFF4E4160, 0xFF3F3752, 0xFFB8A98F, 0xFF7C6F66, 0xFF2A2433);
        return new Palette(0xFF5A5A62, 0xFFAB9F89, 0xFF978B73, 0xFF7C705B, 0xFF6B6455, 0xFFC9BA96, 0xFF8A7B5C, 0xFF3A322A);
    }

    static void draw(GuiGraphics g, int x, int y, int w, int h, String region, long age, boolean animate) {
        if (w < 8 || h < 8) return;
        Palette p = palette(region);
        boolean moving = animate && TowerUiSettings.motion;
        g.enableScissor(x, y, x + w, y + h);
        int horizon = y + h * 62 / 100;
        g.fill(x, y, x + w, y + (horizon - y) / 2, p.sky);
        g.fillGradient(x, y + (horizon - y) / 2, x + w, horizon, p.sky, p.skyLow);
        if (region.contains("duskvale")) {
            for (int i = 0; i < Math.max(4, w / 18); i++) {
                int sx = x + (i * 37 + 11) % Math.max(1, w), sy = y + 3 + (i * 13) % Math.max(1, (horizon - y) / 2);
                g.fill(sx, sy, sx + 1, sy + 1, 0xFFF0DFBF);
            }
        }
        // Two mountain ridges, stepped so every edge is a pixel edge.
        for (int mx = 0; mx < w; mx += 2) {
            int peak = 6 + Math.abs(((mx / 2) * 5 % 26) - 13);
            int ridge = Math.max(1, (horizon - y) * peak / 28);
            g.fill(x + mx, horizon - ridge, x + mx + 2, horizon, p.far);
        }
        for (int mx = 0; mx < w; mx += 2) {
            int peak = 3 + Math.abs((((mx + 20) / 2) * 7 % 18) - 9);
            int ridge = Math.max(1, (horizon - y) * peak / 40);
            g.fill(x + mx, horizon - ridge, x + mx + 2, horizon, p.near);
        }
        // Ground band: water, canopy floor or flagstones.
        g.fill(x, horizon, x + w, y + h, p.ground);
        if (region.contains("rootvale")) {
            for (int tx = x + 3; tx < x + w; tx += 11) {
                int th = Math.max(8, h / 3 + (tx * 3 % 7));
                int top = horizon - th + 6;
                for (int row = 0; row < th / 3; row++) g.fill(tx - 1 - row, top + row * 3, tx + 2 + row, top + row * 3 + 3, 0xFF2C5237);
                g.fill(tx, horizon - 5, tx + 1, horizon + 2, 0xFF3A2A1F);
            }
        } else if (region.contains("tideforge")) {
            int glint = moving ? (int) ((age / 700L) % 3) : 0;
            for (int ry = horizon + 3; ry < y + h - 1; ry += 4)
                for (int rx = x + ((ry - horizon) / 4 % 2) * 6; rx < x + w; rx += 12) g.fill(rx + glint, ry, rx + glint + 4, ry + 1, 0xFF6C9CB6);
        } else {
            for (int ry = horizon + 3; ry < y + h; ry += 5) g.fill(x, ry, x + w, ry + 1, 0x33000000);
        }
        // Three stone towers: tall centre, two flanks, with crenellations and lit windows.
        int cx = x + w / 2;
        tower(g, p, cx - 7 * Math.max(1, w / 60) - Math.max(10, w / 9), horizon, Math.max(10, w / 9), Math.max(14, (horizon - y) * 52 / 100), moving, age, 0);
        tower(g, p, cx + 7 * Math.max(1, w / 60), horizon, Math.max(10, w / 9), Math.max(14, (horizon - y) * 52 / 100), moving, age, 1);
        tower(g, p, cx - Math.max(7, w / 14), horizon, Math.max(14, w / 7), Math.max(20, (horizon - y) * 85 / 100), moving, age, 2);
        g.disableScissor();
    }

    private static void tower(GuiGraphics g, Palette p, int x, int base, int w, int h, boolean moving, long age, int seed) {
        int top = base - h;
        g.fill(x, top, x + w, base, p.stone);
        g.fill(x + w - 3, top, x + w, base, p.stoneShade);
        g.fill(x, top, x + w, top + 1, 0xFFF0DFBF);
        for (int cx = x; cx < x + w; cx += 4) g.fill(cx, top - 2, Math.min(cx + 2, x + w), top, p.stone);
        int lampPhase = moving ? (int) ((age / 1100L + seed) % 3) : 0;
        int n = 0;
        for (int wy = top + 5; wy < base - 8; wy += 7) {
            for (int wx = x + 3; wx < x + w - 5; wx += 6) {
                boolean lit = ((n++ + seed) % 4 == 0) && (lampPhase != 2);
                g.fill(wx, wy, wx + 3, wy + 4, lit ? 0xFFE3BD7F : p.window);
            }
        }
        g.fill(x + w / 2 - 2, base - 6, x + w / 2 + 2, base, p.window);
    }
}
