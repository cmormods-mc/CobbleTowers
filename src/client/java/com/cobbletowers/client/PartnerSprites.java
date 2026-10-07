package com.cobbletowers.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The bundled Pokemon icons (48x32, CC0, from Cobblemon Cards; see {@code textures/gui/partners/SOURCES.md}).
 * Canvases have uneven empty margins, so a sprite is centred by the box of pixels it draws, measured once on first
 * use.
 */
final class PartnerSprites {
    private PartnerSprites() {}

    /** The texture and the box its pixels occupy. */
    private record Info(ResourceLocation texture, int width, int height, int minX, int maxX, int minY, int maxY) {}

    private static final Map<String, Optional<Info>> CACHE = new HashMap<>();
    private static ResourceManager cachedFor;

    /**
     * The species id a file is named by: letters and digits only, lower case ({@code mr_mime} and {@code mrmime} are
     * one file).
     */
    static String key(String species) {
        int colon = species.indexOf(':');
        String path = colon >= 0 ? species.substring(colon + 1) : species;
        StringBuilder out = new StringBuilder();
        for (char c : path.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) out.append(c);
        }
        return out.toString();
    }

    private static Optional<Info> info(String species) {
        ResourceManager manager = Minecraft.getInstance().getResourceManager();
        if (manager != cachedFor) {
            CACHE.clear();     // a resource reload may have changed the icons
            cachedFor = manager;
        }
        return CACHE.computeIfAbsent(key(species), id -> load(manager, id));
    }

    private static Optional<Info> load(ResourceManager manager, String id) {
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/partners/" + id + ".png");
        var resource = manager.getResource(texture);
        if (resource.isEmpty()) return Optional.empty();
        try (var in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int minX = Integer.MAX_VALUE, maxX = -1, minY = Integer.MAX_VALUE, maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getPixelRGBA(x, y) >>> 24) > 127) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            if (maxX < 0) return Optional.empty();
            return Optional.of(new Info(texture, image.getWidth(), image.getHeight(), minX, maxX, minY, maxY));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /**
     * Draws the species so that its pixels are centred on ({@code cx}, {@code cy}); a bronze crest if there is no
     * icon for it.
     */
    static void draw(GuiGraphics g, String species, int cx, int cy) {
        Optional<Info> found = info(species);
        if (found.isEmpty()) {
            crest(g, cx, cy);
            return;
        }
        Info i = found.get();
        int x = Math.round(cx - (i.minX() + i.maxX() + 1) / 2f);
        int y = Math.round(cy - (i.minY() + i.maxY() + 1) / 2f);
        g.blit(i.texture(), x, y, i.width(), i.height(), 0f, 0f, i.width(), i.height(), i.width(), i.height());
    }

    /** What stands in for a species with no icon: a bronze shield with a question mark. */
    private static void crest(GuiGraphics g, int cx, int cy) {
        g.fill(cx - 10, cy - 13, cx + 10, cy + 13, TowerUi.OUTLINE);
        g.fill(cx - 8, cy - 11, cx + 8, cy + 11, TowerUi.BRONZE);
        g.fill(cx - 6, cy - 9, cx + 6, cy + 9, 0xFF4A3426);
        g.drawCenteredString(TowerFonts.get(), "?", cx, cy - 4, TowerUi.BRONZE_LIGHT);
    }
}
