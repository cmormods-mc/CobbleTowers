package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/**
 * The font every CobbleTowers menu draws with: Pixelify Sans (SIL OFL, licence beside the file), a pixel face that stays legible at
 * small sizes. It is a {@link Font} that always looks glyphs up in {@code cobbletowers:ui}, which falls back to Minecraft's own
 * glyphs for anything the face lacks. Tooltips and chat stay in the vanilla font.
 */
final class TowerFonts {
    private static final ResourceLocation UI = ResourceLocation.fromNamespaceAndPath("cobbletowers", "ui");
    private static Font ui;

    private TowerFonts() {}

    static Font get() {
        Font vanilla = Minecraft.getInstance().font;
        if (!TowerUiSettings.pixelFont) return vanilla;
        if (ui == null) ui = new Font(id -> Minecraft.getInstance().font.getFontSet(UI), false);
        return ui;
    }
}
