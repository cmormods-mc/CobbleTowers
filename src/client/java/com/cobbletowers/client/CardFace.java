package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * How one rental card is drawn (P33). The pack screen asks a face to draw a card and knows nothing about how: the built-in
 * {@link ByzantineCardFace} draws the Pixelated Byzantine card from bundled art, so it works on every client with no other mod.
 *
 * <p>This seam is where a richer face goes (a Pokemon model, or CobblemonCards' own frame and holographic art when that mod is
 * installed) without touching the screen's animation or the draft logic.
 */
public interface CardFace {

    /**
     * Draws the face-up side of a card.
     *
     * @param selected whether the player has marked this card to keep
     * @param ms       a clock in milliseconds for anything that shimmers; never used for game logic
     */
    void drawFace(GuiGraphics graphics, Font font, RentalDraftPayload.Card card, int x, int y, int width, int height,
                  boolean selected, long ms);

    /** Draws the face-down side: the same for every card, so nothing about the rarity is given away. */
    void drawBack(GuiGraphics graphics, Font font, int x, int y, int width, int height, long ms);
}
