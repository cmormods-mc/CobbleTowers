package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.rental.CardStacks;
import com.cobbletowers.rental.PackReveal;
import com.cobbletowers.rental.RentalCards;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A rental card drawn as a real CobblemonCards card (P33b), when that mod is installed on this client: the card item itself, made
 * from the same data a reward card is made from, drawn by the mod's own renderer (its frame, its art, its holographic shimmer), with
 * our information (name, level, role, ability, item, moves) set beneath it.
 *
 * <p>There is no compile-time dependency on the mod: the card is an ordinary item built from plain names, so this class loads and works
 * with or without it. {@link #available()} says whether the mod is loaded; the screen uses {@link FallbackCardFace} when it is not,
 * and this face itself falls back to it for any card the item cannot be made for.
 */
public final class CobblemonCardFace implements CardFace {

    public static final CobblemonCardFace INSTANCE = new CobblemonCardFace();
    private static final Map<String, Optional<ItemStack>> STACKS = new HashMap<>();
    private static HolderLookup.Provider fallbackLookup;

    private CobblemonCardFace() {}

    /** Whether CobblemonCards is loaded on this client. */
    public static boolean available() {
        return FabricLoader.getInstance().isModLoaded("cobblemon-cards");
    }

    private static HolderLookup.Provider registries() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) return minecraft.getConnection().registryAccess();
        if (fallbackLookup == null) fallbackLookup = VanillaRegistries.createLookup();
        return fallbackLookup;
    }

    /** The card item for this card, built once and kept; empty if the item cannot be made (the mod is gone, or its data changed). */
    private static Optional<ItemStack> stackOf(RentalDraftPayload.Card card) {
        RentalDraftPayload.Look look = card.look();
        String key = card.species() + "|" + look.shiny() + "|" + look.rarity() + "|" + look.background() + "|" + look.effect();
        return STACKS.computeIfAbsent(key, ignored -> CardStacks.of(registries(), new RentalCards.Spec(card.species(), look.shiny(),
                look.rarity(), "luck", 0f, 0, look.background().isEmpty() ? Optional.empty() : Optional.of(look.background()),
                look.effect().isEmpty() ? Optional.empty() : Optional.of(look.effect()))));
    }

    @Override
    public void drawFace(GuiGraphics graphics, Font font, RentalDraftPayload.Card card, int x, int y, int width, int height,
                         boolean selected, long ms) {
        Optional<ItemStack> stack = stackOf(card);
        if (stack.isEmpty()) {
            FallbackCardFace.INSTANCE.drawFace(graphics, font, card, x, y, width, height, selected, ms);
            return;
        }
        int frame = PackReveal.colorAt(card.rarity(), ms);
        PixelUi.panel(graphics,x,y,width,height,frame,selected?1f:.2f);



        // The card itself: the item's 16-pixel box scaled up to fill the top of the face.
        int art = width - 8;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 4, y + 3, 0f);
        float scale = art / 16f;
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack.get(), 0, 0);
        graphics.pose().popPose();

        // Our information beneath it.
        RentalDraftPayload.Details d = card.details();
        int left = x + 5;
        int inner = width - 10;
        int line = y + 3 + art + 3;
        FallbackCardFace.fit(graphics, font, Component.literal(card.name() + "  Lv " + d.level()), left, line, inner, TowerUi.TEXT, 1.0f);
        line += 11;
        FallbackCardFace.fit(graphics, font, Component.translatableWithFallback("cobblemon.ability." + d.ability(),
                FallbackCardFace.tidy(d.ability())).append(" / ").append(Component.translatableWithFallback("item.cobblemon." + d.item(),
                FallbackCardFace.tidy(d.item()))), left, line, inner, TowerUi.MUTED, 0.8f);
        line += 9;
        for (int i = 0; i < d.moves().size(); i += 2) {
            Component pair = move(d.moves().get(i));
            if (i + 1 < d.moves().size()) pair = pair.copy().append("  -  ").append(move(d.moves().get(i + 1)));
            FallbackCardFace.fit(graphics, font, pair, left, line, inner, TowerUi.TEXT, 0.8f);
            line += 9;
        }
    }

    /** A move by the name Cobblemon's own language file gives it, or a tidied id if there is none. */
    private static net.minecraft.network.chat.MutableComponent move(String id) {
        return Component.translatableWithFallback("cobblemon.move." + id, FallbackCardFace.tidy(id));
    }

    @Override
    public void drawBack(GuiGraphics graphics, Font font, int x, int y, int width, int height, long ms) {
        FallbackCardFace.INSTANCE.drawBack(graphics, font, x, y, width, height, ms);
    }
}
