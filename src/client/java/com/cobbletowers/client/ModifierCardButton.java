package com.cobbletowers.client;

import com.cobbletowers.network.IntermissionStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * One modifier card (oak frame, bronze fittings, a themed diorama, a parchment nameplate), with the wooden shutters that open
 * over it. The hitbox is the card's fixed rectangle at all times; the shutters are only paint, clipped inside it. Choosing a
 * card only marks it (the screen sends nothing until Confirm), so hover, focus and click are all safe to repeat.
 */
final class ModifierCardButton extends Button {

    /** What the screen knows about this card right now. */
    interface View {
        /** 0 = shutters shut, 1 = fully open. */
        float reveal(int index);

        boolean selected(int index);

        /** The card the settled draft picked, or false while the vote is open. */
        boolean chosen(int index);

        boolean settled();
    }

    private final IntermissionStatePayload.Card card;
    private final int index;
    private final View view;
    private final long opened = System.nanoTime();
    private long hoverAt;

    ModifierCardButton(int x, int y, int w, int h, int index, IntermissionStatePayload.Card card, View view, Runnable choose) {
        super(x, y, w, h, Component.literal(card.displayName()), b -> choose.run(), DEFAULT_NARRATION);
        this.index = index;
        this.card = card;
        this.view = view;
    }

    private static float ease(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float reveal = view.reveal(index);
        boolean hot = active && isHoveredOrFocused();
        long now = System.nanoTime();
        if (hot) {
            if (hoverAt == 0) hoverAt = now;
        } else {
            hoverAt = 0;
        }
        boolean selected = view.selected(index);
        boolean dimmed = view.settled() && !view.chosen(index);

        PixelUi.frame(g, PixelUi.Frame.DARK, x, y, w, h);
        int inset = 6, plate = 26;
        int sceneX = x + inset, sceneY = y + inset, sceneW = w - inset * 2, sceneH = h - inset * 2;
        // The scene fills the card; the nameplate lies over its bottom edge, and the sprite stands above it.
        ModifierScene.draw(g, sceneX, sceneY, sceneW, sceneH, card.theme(), (now - opened) / 1_000_000L, TowerUiSettings.motion && reveal >= 1f, plate);
        if (dimmed) g.fill(sceneX, sceneY, sceneX + sceneW, sceneY + sceneH, 0x88000000);

        // Votes are small bronze pips, one each, so they never fight the Selected tag for room on a narrow card.
        for (int v = 0; v < Math.min(card.votes(), 8); v++) {
            int px = sceneX + 2 + v * 7;
            g.fill(px, sceneY + 2, px + 6, sceneY + 8, 0xFF211510);
            g.fill(px + 1, sceneY + 3, px + 5, sceneY + 7, TowerUi.BRONZE_LIGHT);
        }
        if (selected || view.chosen(index)) {
            String label = view.chosen(index) ? "Chosen" : "Selected";
            int lw = font.width(label) + 8;
            int lx = sceneX + sceneW - lw;
            g.fill(lx, sceneY, lx + lw, sceneY + 11, 0xEE211510);
            g.fill(lx, sceneY + 10, lx + lw, sceneY + 11, TowerUi.BRONZE);
            g.drawString(font, label, lx + 4, sceneY + 2, TowerUi.BRONZE_LIGHT, false);
        }

        int plateY = sceneY + sceneH - plate + 2;
        PixelUi.sheet(g, sceneX - 2, plateY, sceneW + 4, plate);
        TowerUi.label(g, font, card.displayName(), sceneX + 7, plateY + 6, sceneW - 12, TowerUi.INK);
        TowerUi.label(g, font, riskLabel(card.risk()), sceneX + 7, plateY + 15, sceneW - 12, 0xFF6B4A33);

        if (selected || view.chosen(index)) {
            PixelUi.brackets(g, x + 2, y + 2, w - 4, h - 4, TowerUi.BRONZE_LIGHT);
            PixelUi.brackets(g, x + 4, y + 4, w - 8, h - 8, TowerUi.BRONZE);
        } else if (hot) {
            // The same two-step highlight for the pointer and for keyboard focus: dim bronze, then bright after a beat.
            boolean second = !TowerUiSettings.motion || (now - hoverAt) > 100_000_000L;
            PixelUi.brackets(g, x + 2, y + 2, w - 4, h - 4, second ? TowerUi.BRONZE_LIGHT : TowerUi.BRONZE);
        }
        if (reveal < 1f) shutters(g, x + 3, y + 3, w - 6, h - 6, reveal);
    }

    static String riskLabel(int risk) {
        return switch (risk) {
            case 0 -> "Risk: minor";
            case 1 -> "Risk: moderate";
            case 2 -> "Risk: severe";
            default -> "Special offer";
        };
    }

    /** Two oak shutters with bronze bands that slide apart; clipped to the card, so nothing paints outside its box. */
    private static void shutters(GuiGraphics g, int x, int y, int w, int h, float reveal) {
        int half = w / 2;
        int slide = (int) (half * ease(reveal));
        g.enableScissor(x, y, x + w, y + h);
        shutter(g, x - slide, y, half, h);
        shutter(g, x + w - half + slide, y, half, h);
        if (slide == 0) {
            g.fill(x + half - 4, y + h / 2 - 7, x + half + 4, y + h / 2 + 7, 0xFF211510);
            g.fill(x + half - 3, y + h / 2 - 6, x + half + 3, y + h / 2 + 6, TowerUi.BRONZE);
            g.fill(x + half - 1, y + h / 2 - 2, x + half + 1, y + h / 2 + 2, 0xFF40291E);
        }
        g.disableScissor();
    }

    private static void shutter(GuiGraphics g, int x, int y, int w, int h) {
        PixelUi.tile(g, PixelUi.Tile.OAK, x, y, w, h);
        g.fill(x, y, x + w, y + h, 0x33000000);
        for (int band : new int[] {h * 22 / 100, h * 68 / 100}) {
            g.fill(x, y + band, x + w, y + band + 5, 0xFF211510);
            g.fill(x, y + band + 1, x + w, y + band + 4, TowerUi.BRONZE);
        }
        g.fill(x, y, x + 1, y + h, 0xFF211510);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF211510);
    }

    @Override
    public void playDownSound(SoundManager manager) {
        if (TowerUiSettings.sounds) super.playDownSound(manager);
    }
}
