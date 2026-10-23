package com.cobbletowers.client;

import com.cobbletowers.CobbleTowers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The battle dioramas on modifier cards: stepped, integer-aligned fills with a Pokemon sprite from {@code
 * textures/gui/partners}. The server picks a theme key ({@code ModifierArt}); an unknown key gets a neutral scene.
 * Motion is off under reduced motion.
 */
final class ModifierScene {
    private ModifierScene() {}

    private static final int SPRITE_W = 48, SPRITE_H = 32;
    private static final int RAIN = 0xAA9DB8D0, SNOW = 0xCCF0DFBF, SAND = 0x88D8B778, GOLD = 0xFFE3BD7F, WOOD = 0xFF6B4A33,
            WOOD_DARK = 0xFF40291E, BARK = 0xFF4A3426, CLAW = 0xFFE08A78, STONE = 0xFF7C705B, STONE_DARK = 0xFF5A5246;

    /** Sky, lower sky, ground, and which sprite stands in the scene (null for none). */
    private record Look(int sky, int skyLow, int ground, String sprite) {}

    private static Look look(String theme) {
        String key = theme.contains(":") ? theme.substring(0, theme.indexOf(':')) : theme;
        String detail = theme.contains(":") ? theme.substring(theme.indexOf(':') + 1) : "";
        return switch (key) {
            case "weather" -> switch (detail) {
                case "sunnyday", "desolateland" -> new Look(0xFFB8793A, 0xFFE3A95E, 0xFF7C5A33, "charizard");
                case "sandstorm" -> new Look(0xFF9C7F4F, 0xFFC9A66B, 0xFF8A7048, "tyranitar");
                case "hail", "snowscape" -> new Look(0xFF6F8CA3, 0xFFB5C9D6, 0xFFD6E0E6, "glalie");
                default -> new Look(0xFF3E5468, 0xFF6B7F8C, 0xFF2F4A5C, "politoed");
            };
            case "terrain" -> switch (detail) {
                case "electricterrain" -> new Look(0xFF4A4A3A, 0xFF8C8A4A, 0xFF6E6A2E, "raichu");
                case "psychicterrain" -> new Look(0xFF4B3A5C, 0xFF8C6AA0, 0xFF6A4A80, "alakazam");
                case "mistyterrain" -> new Look(0xFF5C4350, 0xFFB98FA0, 0xFF8A6678, "gardevoir");
                default -> new Look(0xFF47603F, 0xFF7C9A5E, 0xFF4F6B3A, "rillaboom");
            };
            case "enemy" -> new Look(0xFF4A2A2A, 0xFF8A4A3C, 0xFF3A2A24, "garchomp");
            case "encounter" -> new Look(0xFF4F4A52, 0xFF8B8470, 0xFF5A5246, "arcanine");
            case "constraint" -> new Look(0xFF3C3A44, 0xFF7C7568, 0xFF4F4A42, "registeel");
            case "scouting" -> new Look(0xFF2E3A44, 0xFF5E7080, 0xFF3A4650, "noctowl");
            case "reward_up" -> new Look(0xFF5A4630, 0xFFB8935A, 0xFF6B4A33, "persian");
            case "reward_down" -> new Look(0xFF3A3A40, 0xFF6A6860, 0xFF48443C, "slowpoke");
            case "custom" -> switch (detail) {
                case "glass_cannon" -> new Look(0xFF4A3E52, 0xFF8A7C98, 0xFF574C60, "gallade");
                case "fortunes_wheel" -> new Look(0xFF4A3A2A, 0xFFB8935A, 0xFF6B4A33, "meowth");
                case "field_hospital" -> new Look(0xFF4A5A4A, 0xFF9CB596, 0xFF6A7C64, "chansey");
                case "black_market" -> new Look(0xFF2E2A36, 0xFF5E566C, 0xFF3C3646, "sableye");
                case "swift_start" -> new Look(0xFF3E5468, 0xFF8CB0C8, 0xFF4F6670, "jolteon");
                case "iron_hide" -> new Look(0xFF44464C, 0xFF868A94, 0xFF5A5E66, "aggron");
                case "war_banner" -> new Look(0xFF4A2E32, 0xFF9C5A52, 0xFF4A3A34, "conkeldurr");
                default -> unknown();
            };
            default -> unknown();
        };
    }

    private static Look unknown() {
        return new Look(0xFF4A4650, 0xFF857D70, 0xFF5A5246, null);
    }

    /**
     * @param reserve pixels along the bottom covered by a nameplate; the horizon, sprite and props are placed above
     *     it
     */
    static void draw(GuiGraphics g, int x, int y, int w, int h, String theme, long ms, boolean animate, int reserve) {
        if (w < 16 || h < 16) return;
        Look look = look(theme);
        String key = theme.contains(":") ? theme.substring(0, theme.indexOf(':')) : theme;
        String detail = theme.contains(":") ? theme.substring(theme.indexOf(':') + 1) : "";
        int step = animate ? (int) (ms / 120L) : 0;
        g.enableScissor(x, y, x + w, y + h);
        int visible = h - reserve;
        int horizon = y + visible * 64 / 100;
        g.fill(x, y, x + w, y + (horizon - y) / 2, look.sky);
        g.fillGradient(x, y + (horizon - y) / 2, x + w, horizon, look.sky, look.skyLow);
        g.fill(x, horizon, x + w, y + h, look.ground);
        for (int fx = x; fx < x + w; fx += 6) g.fill(fx, horizon, fx + 3, horizon + 1, 0x33000000);   // a worn ground line

        int spriteX = x + (w - SPRITE_W) / 2;
        int spriteY = y + visible - SPRITE_H;
        boolean room = visible >= SPRITE_H && w >= SPRITE_W;
        if (key.equals("constraint") && room) sprite(g, look.sprite, spriteX, spriteY);

        switch (key) {
            case "weather" -> weather(g, detail, x, y, w, visible, horizon, step);
            case "terrain" -> terrain(g, detail, x, y, w, visible, horizon, step);
            case "enemy" -> enemy(g, detail, x, y, w, visible, step);
            case "encounter" -> { if (detail.equals("crowd")) crowd(g, x, y, w, horizon, step); }
            case "constraint" -> constraint(g, detail, x, y, w, visible, step);
            case "scouting" -> lantern(g, x, y, w, step);
            case "reward_up" -> { coffer(g, x, y, w, visible, true, step); if (detail.equals("hoard")) coins(g, x + w - 30, y + visible - 14, step); }
            case "reward_down" -> coffer(g, x, y, w, visible, false, step);
            case "custom" -> custom(g, detail, x, y, w, visible, step);
            default -> {
            }
        }
        if (look.sprite != null && room && !key.equals("constraint")) {
            int sx = key.equals("encounter") ? x + 2 : key.startsWith("reward") ? x + w - SPRITE_W - 2 : spriteX;
            sprite(g, look.sprite, sx, spriteY);
            if (key.equals("encounter")) sprite(g, "salamence", x + w - SPRITE_W - 2, spriteY);
        }
        if (look.sprite == null) crest(g, x, y, w, visible);
        g.disableScissor();
    }

    private static void sprite(GuiGraphics g, String name, int x, int y) {
        g.blit(CobbleTowers.id("textures/gui/partners/" + name + ".png"),
                x, y, SPRITE_W, SPRITE_H, 0, 0, SPRITE_W, SPRITE_H, SPRITE_W, SPRITE_H);
    }

    private static void weather(GuiGraphics g, String kind, int x, int y, int w, int h, int horizon, int step) {
        switch (kind) {
            case "sunnyday", "desolateland" -> {
                int sx = x + w - 22, sy = y + 5;
                g.fill(sx + 3, sy, sx + 11, sy + 14, GOLD);
                g.fill(sx, sy + 3, sx + 14, sy + 11, GOLD);
                g.fill(sx + 1, sy + 1, sx + 13, sy + 13, GOLD);
            }
            case "sandstorm" -> {
                for (int i = 0; i < w / 4; i++) g.fill(x + (i * 29 + step * 3) % w, y + (i * 13) % h, x + (i * 29 + step * 3) % w + 3, y + (i * 13) % h + 1, SAND);
            }
            case "hail", "snowscape" -> {
                for (int i = 0; i < w / 6; i++) {
                    int px = x + (i * 23) % w, py = y + (i * 11 + step) % h;
                    g.fill(px, py, px + 2, py + 2, SNOW);
                }
            }
            default -> {
                for (int i = 0; i < w / 5; i++) {
                    int px = x + (i * 17) % w, py = y + (i * 7 + step * 2) % h;
                    g.fill(px, py, px + 1, py + 4, RAIN);
                }
            }
        }
    }

    private static void terrain(GuiGraphics g, String kind, int x, int y, int w, int h, int horizon, int step) {
        switch (kind) {
            case "electricterrain" -> {
                for (int zx = x + 4; zx < x + w - 8; zx += 18) {
                    int zy = horizon + 4 + (zx / 18 % 2) * 3;
                    g.fill(zx, zy, zx + 6, zy + 1, GOLD);
                    g.fill(zx + 5, zy, zx + 6, zy + 4, GOLD);
                    g.fill(zx + 5, zy + 4, zx + 11, zy + 5, GOLD);
                }
            }
            case "psychicterrain" -> {
                for (int i = 0; i < w / 10; i++) g.fill(x + 3 + i * 10, horizon + 3 + (i * 5 + step) % 8, x + 5 + i * 10, horizon + 5 + (i * 5 + step) % 8, 0xFFD9B6E8);
            }
            case "mistyterrain" -> {
                for (int row = horizon; row < y + h; row += 4) g.fill(x, row, x + w, row + 2, 0x44F0DFBF);
            }
            default -> {
                for (int tx = x + 2; tx < x + w; tx += 5) g.fill(tx, horizon - 2 + tx % 3, tx + 1, horizon + 3, 0xFF2C5237);
            }
        }
    }

    private static void claws(GuiGraphics g, int x, int y, int w, int h) {
        for (int k = 0; k < 3; k++) {
            int sx = x + w * 55 / 100 + k * 7, sy = y + 3;
            for (int s = 0; s < h * 55 / 100; s++) g.fill(sx + s * 2 / 3, sy + s, sx + s * 2 / 3 + 2, sy + s + 1, CLAW);
        }
    }

    /** An enemy modifier, painted for what it does to the opposition. */
    private static void enemy(GuiGraphics g, String kind, int x, int y, int w, int h, int step) {
        int px = x + w - 26, py = y + 5;
        switch (kind) {
            case "tough" -> { shield(g, px, py, false); bar(g, x + 5, y + 5, 28, 100); }
            case "fragile" -> { shield(g, px, py, true); bar(g, x + 5, y + 5, 28, 35); }
            case "champion" -> { crown(g, px - 1, py + 2); chevrons(g, x + 6, y + 6, 2, true, GOLD); }
            case "veteran" -> { claws(g, x, y, w, h); chevrons(g, x + 6, y + 6, 3, true, GOLD); }
            case "novice" -> chevrons(g, x + 6, y + 6, 2, false, 0xFFB8B09C);
            default -> claws(g, x, y, w, h);
        }
    }

    /** A modifier that takes something from the party; a bar-slashed icon says which. */
    private static void constraint(GuiGraphics g, String kind, int x, int y, int w, int h, int step) {
        int ix = x + w - 25, iy = y + 5;
        switch (kind) {
            case "no_heal" -> { plus(g, ix, iy, 0xFFF0DFBF); slash(g, ix - 2, iy - 2, 18); }
            case "no_setup" -> { chevrons(g, ix, iy, 3, true, 0xFFF0DFBF); slash(g, ix - 2, iy - 2, 18); }
            case "no_switch" -> {
                g.fill(ix, iy + 3, ix + 14, iy + 5, 0xFFF0DFBF);
                g.fill(ix + 10, iy, ix + 12, iy + 8, 0xFFF0DFBF);
                g.fill(ix, iy + 11, ix + 14, iy + 13, 0xFFF0DFBF);
                g.fill(ix + 2, iy + 8, ix + 4, iy + 16, 0xFFF0DFBF);
                slash(g, ix - 2, iy - 2, 18);
            }
            case "no_items" -> {
                g.fill(ix + 1, iy + 5, ix + 15, iy + 17, WOOD);
                g.fill(ix + 3, iy + 1, ix + 13, iy + 5, TowerUi.BRONZE);
                g.fill(ix + 6, iy + 8, ix + 10, iy + 11, GOLD);
                slash(g, ix - 2, iy - 1, 20);
            }
            default -> bars(g, x, y, w, h);
        }
    }

    /** Many small dark figures: a modifier that adds opponents. */
    private static void crowd(GuiGraphics g, int x, int y, int w, int horizon, int step) {
        for (int i = 0; i < 6; i++) {
            int fx = x + 3 + i * Math.max(8, (w - 12) / 6), fy = horizon - 8 - (i % 2) * 3 + (step + i) % 2;
            g.fill(fx, fy, fx + 6, fy + 9, 0xCC2A1A12);
            g.fill(fx + 1, fy - 3, fx + 5, fy, 0xCC2A1A12);
            g.fill(fx + 1, fy - 2, fx + 2, fy - 1, 0xFFC0453A);
            g.fill(fx + 4, fy - 2, fx + 5, fy - 1, 0xFFC0453A);
        }
    }

    private static void shield(GuiGraphics g, int x, int y, boolean cracked) {
        g.fill(x - 1, y - 1, x + 19, y + 15, 0xFF211510);
        g.fill(x, y, x + 18, y + 14, TowerUi.BRONZE_LIGHT);
        g.fill(x + 2, y + 14, x + 16, y + 18, TowerUi.BRONZE_LIGHT);
        g.fill(x + 5, y + 18, x + 13, y + 21, TowerUi.BRONZE_LIGHT);
        g.fill(x + 2, y + 2, x + 16, y + 14, 0xFF8C95A0);
        g.fill(x + 4, y + 14, x + 14, y + 17, 0xFF8C95A0);
        g.fill(x + 7, y + 6, x + 11, y + 10, TowerUi.BRONZE);
        if (cracked) {
            for (int i = 0; i < 12; i++) g.fill(x + 9 + (i % 4 < 2 ? -1 : 1), y + 1 + i, x + 10 + (i % 4 < 2 ? -1 : 1), y + 2 + i, 0xFF211510);
        }
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int percent) {
        g.fill(x, y, x + w, y + 5, 0xFF2A1A12);
        g.fill(x + 1, y + 1, x + w - 1, y + 4, 0xFF4A2A2A);
        g.fill(x + 1, y + 1, x + 1 + (w - 2) * percent / 100, y + 4, percent > 60 ? 0xFF6FA55A : 0xFFC0453A);
    }

    private static void crown(GuiGraphics g, int x, int y) {
        g.fill(x, y + 6, x + 20, y + 14, GOLD);
        for (int i = 0; i < 3; i++) g.fill(x + i * 8, y, x + i * 8 + 4, y + 6, GOLD);
        g.fill(x + 2, y + 9, x + 18, y + 11, TowerUi.BURGUNDY);
    }

    /** Stacked V marks, pointing up or down: more, or fewer, levels. */
    private static void chevrons(GuiGraphics g, int x, int y, int count, boolean up, int color) {
        for (int c = 0; c < count; c++) {
            int cy = y + c * 6;
            for (int i = 0; i < 6; i++) {
                int dy = up ? i : 5 - i;
                g.fill(x + i * 2, cy + dy, x + i * 2 + 2, cy + dy + 2, color);
                g.fill(x + 22 - i * 2 - 2, cy + dy, x + 22 - i * 2, cy + dy + 2, color);
            }
        }
    }

    private static void plus(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 5, y, x + 9, y + 14, color);
        g.fill(x, y + 5, x + 14, y + 9, color);
    }

    /** A red diagonal slash across an icon: "not allowed". */
    private static void slash(GuiGraphics g, int x, int y, int size) {
        for (int i = 0; i < size; i++) g.fill(x + i, y + size - 1 - i, x + i + 2, y + size - i, 0xFFC0453A);
    }

    private static void coins(GuiGraphics g, int x, int y, int step) {
        for (int row = 0; row < 3; row++) {
            for (int i = 0; i <= 2 - row; i++) {
                int cx = x + row * 4 + i * 8;
                g.fill(cx, y + 8 - row * 4, cx + 7, y + 11 - row * 4, GOLD);
                g.fill(cx, y + 10 - row * 4, cx + 7, y + 11 - row * 4, TowerUi.BRONZE);
            }
        }
        int sp = step % 3;
        g.fill(x + 4 + sp * 6, y - 4, x + 5 + sp * 6, y - 1, 0xFFF0DFBF);
    }

    private static void bars(GuiGraphics g, int x, int y, int w, int h) {
        for (int bx = x + 6; bx < x + w - 4; bx += 11) {
            g.fill(bx, y, bx + 4, y + h, WOOD_DARK);
            g.fill(bx + 1, y, bx + 3, y + h, TowerUi.BRONZE);
        }
        g.fill(x, y + h / 3, x + w, y + h / 3 + 3, TowerUi.BRONZE);
        g.fill(x, y + h * 2 / 3, x + w, y + h * 2 / 3 + 3, TowerUi.BRONZE);
    }

    private static void lantern(GuiGraphics g, int x, int y, int w, int step) {
        int lx = x + 8, ly = y + 6;
        int glow = step % 2 == 0 ? 0x33E3BD7F : 0x44E3BD7F;
        g.fill(lx - 6, ly - 6, lx + 12, ly + 14, glow);
        g.fill(lx, ly, lx + 6, ly + 8, GOLD);
        g.fill(lx - 1, ly - 2, lx + 7, ly, TowerUi.BRONZE);
        g.fill(lx - 1, ly + 8, lx + 7, ly + 10, TowerUi.BRONZE);
    }

    /** A coffer: open and gleaming when the modifier pays more, shut when it pays less. */
    private static void coffer(GuiGraphics g, int x, int y, int w, int h, boolean open, int step) {
        int cx = x + 6, cy = y + h - 22;
        g.fill(cx, cy + 8, cx + 24, cy + 20, WOOD);
        g.fill(cx, cy + 12, cx + 24, cy + 14, TowerUi.BRONZE);
        g.fill(cx + 10, cy + 10, cx + 14, cy + 16, GOLD);
        if (open) {
            g.fill(cx - 1, cy, cx + 25, cy + 8, WOOD_DARK);
            g.fill(cx + 2, cy + 6, cx + 22, cy + 9, GOLD);
            int spark = step % 3;
            g.fill(cx + 4 + spark * 6, cy - 4, cx + 5 + spark * 6, cy - 1, GOLD);
            g.fill(cx + 3 + spark * 6, cy - 3, cx + 6 + spark * 6, cy - 2, GOLD);
        } else {
            g.fill(cx, cy + 2, cx + 24, cy + 9, WOOD);
            g.fill(cx, cy + 4, cx + 24, cy + 5, TowerUi.BRONZE);
            g.fill(cx - 2, cy + 18, cx + 26, cy + 20, 0x55000000);
        }
    }

    private static void custom(GuiGraphics g, String kind, int x, int y, int w, int h, int step) {
        switch (kind) {
            case "glass_cannon" -> {
                for (int s = 0; s < h / 2; s++) g.fill(x + w / 3 + s / 2, y + 2 + s, x + w / 3 + s / 2 + 1, y + 3 + s, 0xCCF0DFBF);
                for (int s = 0; s < h / 3; s++) g.fill(x + w / 3 + 6 - s, y + h / 3 + s, x + w / 3 + 7 - s, y + h / 3 + s + 1, 0xCCF0DFBF);
            }
            case "fortunes_wheel" -> {
                int wx = x + w - 24, wy = y + 6;
                g.fill(wx, wy, wx + 18, wy + 18, WOOD_DARK);
                g.fill(wx + 2, wy + 2, wx + 16, wy + 16, WOOD);
                g.fill(wx + 8, wy + 1, wx + 10, wy + 17, GOLD);
                g.fill(wx + 1, wy + 8, wx + 17, wy + 10, GOLD);
                int mark = step % 4;
                g.fill(wx + 7 + (mark % 2) * 4 - 2, wy + 7 + (mark / 2) * 4 - 2, wx + 11 + (mark % 2) * 4 - 2, wy + 11 + (mark / 2) * 4 - 2, TowerUi.BURGUNDY);
            }
            case "field_hospital" -> {
                int cx = x + w - 22, cy = y + 6;
                g.fill(cx, cy + 5, cx + 14, cy + 9, 0xFFF0DFBF);
                g.fill(cx + 5, cy, cx + 9, cy + 14, 0xFFF0DFBF);
                g.fill(cx + 1, cy + 6, cx + 13, cy + 8, TowerUi.BURGUNDY);
                g.fill(cx + 6, cy + 1, cx + 8, cy + 13, TowerUi.BURGUNDY);
            }
            case "black_market" -> {
                for (int i = 0; i < 4; i++) g.fill(x + 6, y + h - 12 - i * 3, x + 20, y + h - 10 - i * 3, i % 2 == 0 ? GOLD : TowerUi.BRONZE);
            }
            case "swift_start" -> {
                for (int i = 0; i < 4; i++) g.fill(x + 4 + (step + i * 3) % 6, y + 8 + i * 8, x + 22 + (step + i * 3) % 6, y + 9 + i * 8, 0xCCF0DFBF);
            }
            case "iron_hide" -> {
                int sx = x + w - 22, sy = y + 6;
                g.fill(sx, sy, sx + 16, sy + 16, STONE_DARK);
                g.fill(sx + 2, sy + 2, sx + 14, sy + 14, STONE);
                g.fill(sx + 7, sy + 3, sx + 9, sy + 13, TowerUi.BRONZE);
            }
            case "war_banner" -> {
                int bx = x + w - 20, by = y + 4;
                g.fill(bx, by, bx + 2, by + 26, TowerUi.BRONZE);
                g.fill(bx + 2, by + 2, bx + 14, by + 14, TowerUi.BURGUNDY);
                g.fill(bx + 2, by + 14, bx + 8, by + 18, TowerUi.BURGUNDY);
            }
            default -> {
            }
        }
    }

    /** The fallback: a bronze crest with a question mark, for anything this file has no picture for. */
    private static void crest(GuiGraphics g, int x, int y, int w, int h) {
        int cx = x + w / 2 - 12, cy = y + h / 2 - 14;
        g.fill(cx + 4, cy, cx + 20, cy + 28, WOOD_DARK);
        g.fill(cx, cy + 4, cx + 24, cy + 24, WOOD_DARK);
        g.fill(cx + 5, cy + 2, cx + 19, cy + 26, TowerUi.BRONZE);
        g.fill(cx + 2, cy + 5, cx + 22, cy + 23, TowerUi.BRONZE);
        g.fill(cx + 6, cy + 4, cx + 18, cy + 24, BARK);
        g.drawCenteredString(TowerFonts.get(), "?", cx + 12, cy + 10, GOLD);
    }
}
