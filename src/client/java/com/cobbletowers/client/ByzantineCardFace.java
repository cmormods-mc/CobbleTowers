package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The rental card in the Pixelated Byzantine style (P33): mostly baked art ({@code textures/gui/byzantine}, from
 * {@code docs/design/concepts/byzantine/rental_card.py}, whose layout constants this mirrors); the sprite, lettering
 * and gems are drawn live. Gem colour is the move's type and shape its category.
 */
public final class ByzantineCardFace implements CardFace {

    public static final ByzantineCardFace INSTANCE = new ByzantineCardFace();

    static final int WIDTH = 112;
    static final int HEIGHT = 160;

    /** The order of the gem atlas's columns and rows. */
    static final List<String> TYPES = List.of("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy");
    static final List<String> SHAPES = List.of("physical", "special", "status");
    private static final int GEM = 7;

    private static final ResourceLocation GEMS = art("gems");
    private static final ResourceLocation SEAL = art("seal");
    private static final ResourceLocation BACK = art("back");
    private static final List<String> RARITIES = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");

    private static final int INK = 0xFF40291E;
    private static final int BROWN = 0xFF6B4A33;
    private static final int GOLD_L = 0xFFF6DC7A;
    private static final int GOLD_D = 0xFFA8741E;

    private ByzantineCardFace() {}

    private static ResourceLocation art(String name) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/byzantine/" + name + ".png");
    }

    private static String rarity(String rarity) {
        String key = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        return RARITIES.contains(key) ? key : "common";
    }

    @Override
    public void drawFace(GuiGraphics graphics, Font font, RentalDraftPayload.Card card, int x, int y, int width, int height,
                         boolean selected, long ms) {
        String rarity = rarity(card.rarity());
        RentalDraftPayload.Details d = card.details();
        graphics.blit(art("field_" + rarity), x + 8, y + 8, 96, 62, 0f, 0f, 96, 62, 96, 62);
        PartnerSprites.draw(graphics, card.species(), x + 56, y + 39);     // the nimbus is centred at 48,31 in the field
        graphics.blit(art("frame_" + rarity), x, y, WIDTH, HEIGHT, 0f, 0f, WIDTH, HEIGHT, WIDTH, HEIGHT);

        fitCentered(graphics, font, Component.literal(card.name()), x + 56, y + 63, 100, INK);
        fitCentered(graphics, font, Component.literal("Lv " + d.level() + "  ").append(translated("cobblemon.nature.", d.nature())),
                x + 56, y + 72, 100, 0xFF5A4326);
        fit(graphics, font, translated("cobblemon.ability.", d.ability()), x + 22, y + 86, 80, INK, 1f);
        if (!d.item().isEmpty()) {
            fit(graphics, font, Component.translatableWithFallback("item.cobblemon." + d.item(), tidy(d.item())), x + 22, y + 95, 80, INK, 1f);
        }
        int line = y + 113;
        for (RentalDraftPayload.Move move : d.moves()) {
            drawGem(graphics, x + 14, line - 1, move.type(), move.category());
            fit(graphics, font, translated("cobblemon.move.", move.id()), x + 25, line, 79, INK, 1f);
            line += 10;
        }
        if (selected) {
            graphics.blit(SEAL, x + WIDTH - 27, y + 13, 15, 15, 0f, 0f, 15, 15, 15, 15);
            graphics.renderOutline(x - 2, y - 2, WIDTH + 4, HEIGHT + 4, GOLD_L);
            graphics.renderOutline(x - 1, y - 1, WIDTH + 2, HEIGHT + 2, GOLD_D);
        }
    }

    @Override
    public void drawBack(GuiGraphics graphics, Font font, int x, int y, int width, int height, long ms) {
        graphics.blit(BACK, x, y, WIDTH, HEIGHT, 0f, 0f, WIDTH, HEIGHT, WIDTH, HEIGHT);
    }

    /**
     * One 7x7 gem from the atlas: the column is the type, the row the category. An unknown type or category is a
     * plain round normal gem.
     */
    static void drawGem(GuiGraphics graphics, int x, int y, String type, String category) {
        int column = Math.max(0, TYPES.indexOf(type == null ? "" : type.toLowerCase(Locale.ROOT)));
        int row = SHAPES.indexOf(category == null ? "" : category.toLowerCase(Locale.ROOT));
        if (row < 0) row = 1;
        graphics.blit(GEMS, x, y, GEM, GEM, column * GEM, row * GEM, GEM, GEM, GEM * TYPES.size(), GEM * SHAPES.size());
    }

    static Component translated(String prefix, String id) {
        return Component.translatableWithFallback(prefix + id, tidy(id));
    }

    /**
     * {@code swiftswim} is as good as the id gets, so only the first letter is raised: a missing translation still
     * reads.
     */
    static String tidy(String id) {
        if (id == null || id.isEmpty()) return "";
        String spaced = id.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
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

    private static void fitCentered(GuiGraphics graphics, Font font, Component text, int centreX, int y, int maxWidth, int color) {
        int natural = font.width(text);
        float use = natural > maxWidth ? maxWidth / (float) natural : 1f;
        fit(graphics, font, text, Math.round(centreX - natural * use / 2f), y, maxWidth, color, 1f);
    }
}
