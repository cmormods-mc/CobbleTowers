package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.rental.PackReveal;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The built-in card face (P33): a dark card framed in its rarity colour, with the Pokemon's name, level, role, ability, held item,
 * nature and four moves. Text only, so it needs nothing from Cobblemon's renderer and cannot break on a model.
 *
 * <p>Names of moves, abilities and items are looked up in Cobblemon's own language file and fall back to a tidied version of the
 * set's id, so the card reads well whether or not a translation exists.
 */
public final class FallbackCardFace implements CardFace {

    public static final FallbackCardFace INSTANCE = new FallbackCardFace();

    private FallbackCardFace() {}

    @Override
    public void drawFace(GuiGraphics graphics, Font font, RentalDraftPayload.Card card, int x, int y, int width, int height,
                         boolean selected, long ms) {
        int frame = PackReveal.colorAt(card.rarity(), ms);
        int rank = PackReveal.rank(card.rarity());
        TowerShader.panel(graphics,x,y,width,height,frame,selected?1f:.2f);





        int left = x + 6;
        int inner = width - 12;
        int line = y + 6;
        String level = "Lv " + card.details().level();
        fit(graphics, font, Component.literal(label(card.rarity())), left, line, inner - font.width(level) - 4, frame, 1f);
        graphics.drawString(font, level, x + width - 6 - font.width(level), line, 0xFFB8BDC7, false);
        line += 13;

        fit(graphics, font, Component.literal(card.name()), left, line, inner, 0xFFFFFFFF, 1.25f);
        line += 15;
        if (!card.details().role().isEmpty()) {
            fit(graphics, font, Component.literal(card.details().role()), left, line, inner, 0xFF9AA0A6, 0.85f);
        }
        line += 11;
        graphics.fill(left, line, x + width - 6, line + 1, withAlpha(frame, 0xAA));
        line += 5;

        RentalDraftPayload.Details d = card.details();
        fit(graphics, font, Component.literal("Ability  ").append(translated("cobblemon.ability.", d.ability())), left, line, inner, 0xFFE6E8EC, 0.85f);
        line += 10;
        if (!d.item().isEmpty()) {
            fit(graphics, font, Component.literal("Item  ").append(Component.translatableWithFallback("item.cobblemon." + d.item(), tidy(d.item()))),
                    left, line, inner, 0xFFE6E8EC, 0.85f);
            line += 10;
        }
        fit(graphics, font, Component.literal("Nature  ").append(translated("cobblemon.nature.", d.nature())), left, line, inner, 0xFFE6E8EC, 0.85f);
        line += 13;
        for (String move : d.moves()) {
            fit(graphics, font, Component.literal("- ").append(translated("cobblemon.move.", move)), left, line, inner, 0xFFFFFFFF, 0.9f);
            line += 10;
        }
        if (rank >= 4) {
            // A faint shimmer along the bottom edge for the top rarities, so a legendary reads as one at a glance.
            int pulse = (int) (0x40 + 0x30 * Math.sin(TowerUiSettings.motion?ms / 250.0:0));
            graphics.fill(x + 3, y + height - 5, x + width - 3, y + height - 3, withAlpha(frame, pulse));
        }
    }

    @Override
    public void drawBack(GuiGraphics graphics, Font font, int x, int y, int width, int height, long ms) {
        graphics.fill(x, y, x + width, y + height, 0xFF10182B);
        graphics.fillGradient(x + 3, y + 3, x + width - 3, y + height - 3, 0xFF1F2E57, 0xFF0B1022);
        graphics.renderOutline(x, y, width, height, 0xFFC9A227);
        graphics.renderOutline(x + 3, y + 3, width - 6, height - 6, 0x66C9A227);
        // A ball: a ring with a bar through it, drawn from rectangles.
        int cx = x + width / 2;
        int cy = y + height / 2;
        int r = Math.max(8, Math.min(width, height) / 5);
        graphics.fill(cx - r, cy - 1, cx + r, cy + 1, 0xFFC9A227);
        graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, 0xFFC9A227);
        graphics.renderOutline(cx - r, cy - r, r * 2, r * 2, 0x88C9A227);
        graphics.drawCenteredString(font, "?", cx, y + height - 14, 0xFFC9A227);
    }

    private static String label(String rarity) {
        return rarity.isEmpty() ? "" : Character.toUpperCase(rarity.charAt(0)) + rarity.substring(1);
    }

    private static Component translated(String prefix, String id) {
        return Component.translatableWithFallback(prefix + id, tidy(id));
    }

    /** {@code swiftswim} is as good as the id gets, so only the first letter is raised: a missing translation still reads. */
    static String tidy(String id) {
        if (id == null || id.isEmpty()) return "";
        String spaced = id.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }

    /** Draws text no wider than {@code maxWidth}: at {@code scale} if it fits, smaller if it does not. */
    static void fit(GuiGraphics graphics, Font font, Component text, int x, int y, int maxWidth, int color, float scale) {
        int natural = font.width(text);
        float use = natural * scale > maxWidth ? maxWidth / (float) Math.max(1, natural) : scale;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(use, use, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
