package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/**
 * The font every menu draws with: Pixelify Sans (SIL OFL, licence beside the file), looked up in {@code
 * cobbletowers:ui} with vanilla glyphs as fallback. Tooltips and chat stay vanilla.
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
