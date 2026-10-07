package com.cobbletowers.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * The regional tower landscapes from the approved preview, painted with integer-aligned fills and clipped to their
 * box: sea walls for Tideforge, forest canopy for Rootvale, a moonlit sky for Duskvale and plain stone otherwise.
 * No shader and no per-frame allocation; the only motion is a two-step window lamp and water glint, both off under
 * reduced motion.
 *
 * <p>A landscape is a couple of hundred fills, so it is painted once into a small texture and drawn as a single quad; the
 * picture only changes when the box, the region or one of the (at most nine) animation steps does. Painting goes through
 * {@link Painter}, so the same code draws into the texture's pixels.
 */
final class TowerPanorama {
    private TowerPanorama() {}

    /** Where a landscape is painted: filled boxes and a top-to-bottom gradient, in the box's own pixels. */
    private interface Painter {
        void fill(int x1, int y1, int x2, int y2, int argb);

        void gradient(int x1, int y1, int x2, int y2, int top, int bottom);
    }

    private record Palette(int sky, int skyLow, int far, int near, int ground, int stone, int stoneShade, int window) {}

    private static Palette palette(String region) {
        if (region.contains("tideforge")) return new Palette(0xFF3E5C6E, 0xFF7C8E94, 0xFF6F7F82, 0xFF4F6670, 0xFF2F5878, 0xFFC9B58F, 0xFF8A7656, 0xFF2D3A44);
        if (region.contains("rootvale")) return new Palette(0xFF47603F, 0xFF7C9A5E, 0xFF6A8A4C, 0xFF40683F, 0xFF4F6B3A, 0xFFC4B48A, 0xFF857553, 0xFF2D3A2A);
        if (region.contains("duskvale")) return new Palette(0xFF3B3348, 0xFF6E5B7E, 0xFF6C5A7A, 0xFF4E4160, 0xFF3F3752, 0xFFB8A98F, 0xFF7C6F66, 0xFF2A2433);
        return new Palette(0xFF5A5A62, 0xFFAB9F89, 0xFF978B73, 0xFF7C705B, 0xFF6B6455, 0xFFC9BA96, 0xFF8A7B5C, 0xFF3A322A);
    }

    // ---- the cache ---------------------------------------------------------------------------------------------------

    private static final int MAX_CACHED = 48;
    private static int serial;
    /** Access-ordered, so the least recently drawn landscape is the one let go of. */
    private static final Map<String, ResourceLocation> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    static void draw(GuiGraphics g, int x, int y, int w, int h, String region, long age, boolean animate) {
        if (w < 8 || h < 8) return;
        boolean moving = animate && TowerUiSettings.motion;
        int glint = moving ? (int) ((age / 700L) % 3) : 0;
        int lamp = moving ? (int) ((age / 1100L) % 3) : 0;
        String key = region + '|' + w + 'x' + h + '|' + moving + glint + lamp;
        ResourceLocation texture = CACHE.get(key);
        if (texture == null) {
            texture = paintTexture(region, w, h, moving, glint, lamp);
            CACHE.put(key, texture);
            trim();
        }
        g.blit(texture, x, y, 0, 0, w, h, w, h);
    }

    /** Lets go of every cached landscape (the GL textures are freed). */
    static void clear() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (ResourceLocation texture : CACHE.values()) textures.release(texture);
        CACHE.clear();
    }

    private static void trim() {
        var textures = Minecraft.getInstance().getTextureManager();
        Iterator<ResourceLocation> eldest = CACHE.values().iterator();
        while (CACHE.size() > MAX_CACHED && eldest.hasNext()) {
            textures.release(eldest.next());
            eldest.remove();
        }
    }

    private static ResourceLocation paintTexture(String region, int w, int h, boolean moving, int glint, int lamp) {
        NativeImage image = new NativeImage(w, h, true);
        image.fillRect(0, 0, w, h, 0);
        paint(new ImagePainter(image), region, w, h, moving, glint, lamp);
        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "dynamic/panorama/" + serial++);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return id;
    }

    /** Paints into the pixels of a texture; clipped to the image, which is exactly the landscape's box. */
    private static final class ImagePainter implements Painter {
        private final NativeImage image;

        ImagePainter(NativeImage image) {
            this.image = image;
        }

        @Override
        public void fill(int x1, int y1, int x2, int y2, int argb) {
            int alpha = argb >>> 24;
            for (int py = Math.max(0, y1); py < Math.min(image.getHeight(), y2); py++) {
                for (int px = Math.max(0, x1); px < Math.min(image.getWidth(), x2); px++) {
                    image.setPixelRGBA(px, py, alpha == 255 ? abgr(argb) : abgr(over(argb, image.getPixelRGBA(px, py))));
                }
            }
        }

        @Override
        public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
            int rows = Math.max(1, y2 - y1);
            for (int py = Math.max(0, y1); py < Math.min(image.getHeight(), y2); py++) {
                int color = lerp(top, bottom, (py - y1 + 0.5f) / rows);
                fill(x1, py, x2, py + 1, color);
            }
        }

        private static int abgr(int argb) {
            return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        }

        /** {@code src} (ARGB) over what is already in the image ({@code dstAbgr}, as NativeImage stores it); returns ARGB. */
        private static int over(int src, int dstAbgr) {
            int dst = (dstAbgr & 0xFF00FF00) | ((dstAbgr >> 16) & 0xFF) | ((dstAbgr & 0xFF) << 16);
            int a = src >>> 24;
            int out = 0xFF000000;
            for (int shift = 16; shift >= 0; shift -= 8) {
                int s = (src >> shift) & 0xFF;
                int d = (dst >> shift) & 0xFF;
                out |= ((s * a + d * (255 - a)) / 255) << shift;
            }
            return out;
        }

        private static int lerp(int from, int to, float t) {
            int out = 0;
            for (int shift = 24; shift >= 0; shift -= 8) {
                int a = (from >>> shift) & 0xFF;
                int b = (to >>> shift) & 0xFF;
                out |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
            }
            return out;
        }
    }

    // ---- the landscape -------------------------------------------------------------------------------------------------

    private static void paint(Painter g, String region, int w, int h, boolean moving, int glint, int lamp) {
        final int x = 0;
        final int y = 0;
        Palette p = palette(region);
        boolean banner = w >= h * 3;
        int horizon = y + h * (banner ? 80 : 62) / 100;
        g.fill(x, y, x + w, y + (horizon - y) / 2, p.sky);
        g.gradient(x, y + (horizon - y) / 2, x + w, horizon, p.sky, p.skyLow);
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
            for (int ry = horizon + 3; ry < y + h - 1; ry += 4)
                for (int rx = x + ((ry - horizon) / 4 % 2) * 6; rx < x + w; rx += 12) g.fill(rx + glint, ry, rx + glint + 4, ry + 1, 0xFF6C9CB6);
        } else {
            for (int ry = horizon + 3; ry < y + h; ry += 5) g.fill(x, ry, x + w, ry + 1, 0x33000000);
        }
        // Three stone towers: tall centre, two flanks, with crenellations and lit windows. A wide, short banner keeps them whole
        // (never cut by the top edge) and puts them right of centre, clear of the title written over the left of the strip.
        int cx = banner ? x + w * 70 / 100 : x + w / 2;
        int room = horizon - y - 4;
        int flankH = Math.min(room, Math.max(14, (horizon - y) * 52 / 100));
        int mainH = Math.min(room, Math.max(20, (horizon - y) * 85 / 100));
        int flankW = banner ? Math.max(10, Math.min(w / 9, flankH * 3 / 4)) : Math.max(10, w / 9);
        int mainW = banner ? Math.max(14, Math.min(w / 7, mainH * 3 / 4)) : Math.max(14, w / 7);
        int gap = banner ? 4 : 7 * Math.max(1, w / 60);
        tower(g, p, cx - mainW / 2 - gap - flankW, horizon, flankW, flankH, moving, lamp, 0);
        tower(g, p, cx + mainW / 2 + gap, horizon, flankW, flankH, moving, lamp, 1);
        tower(g, p, cx - mainW / 2, horizon, mainW, mainH, moving, lamp, 2);
    }

    private static void tower(Painter g, Palette p, int x, int base, int w, int h, boolean moving, int lamp, int seed) {
        int top = base - h;
        g.fill(x, top, x + w, base, p.stone);
        g.fill(x + w - 3, top, x + w, base, p.stoneShade);
        g.fill(x, top, x + w, top + 1, 0xFFF0DFBF);
        for (int cx = x; cx < x + w; cx += 4) g.fill(cx, top - 2, Math.min(cx + 2, x + w), top, p.stone);
        int lampPhase = moving ? (lamp + seed) % 3 : 0;
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
