package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * How one rental card is drawn (P33). The pack screen asks a face to draw; the built-in {@link ByzantineCardFace}
 * uses bundled art. This seam is where a richer face could go without touching the screen's animation or the draft
 * logic.
 */
public interface CardFace {

    /**
     * Draws the face-up side of a card.
     * @param selected whether the player marked it to keep
     * @param ms a clock for shimmer, never game logic
     */
    void drawFace(GuiGraphics graphics, Font font, RentalDraftPayload.Card card, int x, int y, int width, int height,
                  boolean selected, long ms);

    /** Draws the face-down side: the same for every card, so nothing about the rarity is given away. */
    void drawBack(GuiGraphics graphics, Font font, int x, int y, int width, int height, long ms);
}
