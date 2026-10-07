package com.cobbletowers.rental;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.definition.RentalSetDefinition.Rarity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * The collectible card each rental set makes (P33b): a pure function of the set, so server and client agree. Speaks
 * CobblemonCards' data ({@code cobblemon-cards:card}, component {@code cobblemon-cards:card_data}) as plain names and
 * numbers, so the mod need not be installed.
 */
public final class RentalCards {

    public static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card");
    public static final ResourceLocation COMPONENT = ResourceLocation.fromNamespaceAndPath("cobblemon-cards", "card_data");

    /**
     * What a card of one rarity carries: the middle of the stat range the mod rolls for it (its own packs, before its
     * shiny bonus).
     */
    private static final Map<Rarity, Float> STAT_VALUE = Map.of(
            Rarity.COMMON, 0.0075f, Rarity.UNCOMMON, 0.0225f, Rarity.RARE, 0.055f,
            Rarity.EPIC, 0.10f, Rarity.LEGENDARY, 0.15f, Rarity.MYTHIC, 0.225f);
    /** The mod adds this to a shiny card's stat. */
    private static final float SHINY_BONUS = 0.03f;

    /** A background from the mod's own list for each type, so a card looks like the Pokemon it shows. */
    private static final Map<String, String> BACKGROUND = Map.ofEntries(
            Map.entry("normal", "skybg2"), Map.entry("fire", "fire_embers"), Map.entry("water", "waterbg1"),
            Map.entry("grass", "forestbg1"), Map.entry("electric", "neon_grid"), Map.entry("ice", "frozen_tundra"),
            Map.entry("fighting", "ancient_ruins"), Map.entry("poison", "toxic_sludge"), Map.entry("ground", "rockbg1"),
            Map.entry("flying", "cloud_scroll"), Map.entry("psychic", "dreamscape"), Map.entry("bug", "grassbg2"),
            Map.entry("rock", "rockbg1"), Map.entry("ghost", "plasma_bg"), Map.entry("dragon", "galactic_supernova"),
            Map.entry("steel", "rockbg1"), Map.entry("fairy", "cherry_blossom_wind"), Map.entry("dark", "plasma_bg"));
    /** A holographic effect from the mod's own list for each rarity from rare up; nothing below. */
    private static final Map<Rarity, String> EFFECT = Map.of(
            Rarity.RARE, "glint", Rarity.EPIC, "holo_sparkle", Rarity.LEGENDARY, "holo_aurora", Rarity.MYTHIC, "holo_galaxy");

    private RentalCards() {}

    /**
     * One card as CobblemonCards stores it.
     * @param stat the binder stat, the Pokemon's type spawn boost such as {@code dragon_spawn}
     */
    public record Spec(String pokemonId, boolean shiny, String rarity, String stat, float statValue, int grade,
                       Optional<String> background, Optional<String> effect) {
        public Spec {
            background = background == null ? Optional.empty() : background;
            effect = effect == null ? Optional.empty() : effect;
        }

        /** The card as the component the mod reads: field names exactly as its own codec writes them. */
        public CompoundTag toComponent() {
            CompoundTag data = new CompoundTag();
            data.putString("pokemon_id", pokemonId);
            data.putBoolean("is_shiny", shiny);
            data.putString("rarity", rarity);
            data.putString("stat", stat);
            data.putFloat("stat_value", statValue);
            data.putInt("grade", grade);
            background.ifPresent(name -> data.putString("background", name));
            effect.ifPresent(name -> data.putString("effect", name));
            return data;
        }

        /** The whole item as {@code ItemStack} parses it: its id, a count of one, and the card data. */
        public CompoundTag toItemTag() {
            CompoundTag components = new CompoundTag();
            components.put(COMPONENT.toString(), toComponent());
            CompoundTag item = new CompoundTag();
            item.putString("id", ITEM.toString());
            item.putInt("count", 1);
            item.put("components", components);
            return item;
        }
    }

    /**
     * The card for a set.
     * @param godPack whether it came from a God Pack (makes it shiny)
     * @param cap the rarest card to make
     */
    public static Spec of(RentalSetDefinition set, boolean godPack, Rarity cap) {
        Rarity rarity = set.rarity().compareTo(cap) <= 0 ? set.rarity() : cap;
        float value = STAT_VALUE.get(rarity) + (godPack ? SHINY_BONUS : 0f);
        Optional<String> background = rarity.atLeast(Rarity.UNCOMMON) || godPack
                ? Optional.of(BACKGROUND.getOrDefault(set.primaryType(), "skybg2")) : Optional.empty();
        Optional<String> effect = Optional.ofNullable(EFFECT.get(rarity));
        return new Spec(set.species(), godPack, rarity.lower(), set.primaryType() + "_spawn", value, 0, background, effect);
    }

    /** What a player reads: {@code Garchomp card (epic)}, and {@code shiny} when it came from a God Pack. */
    public static String label(RentalSetDefinition set, Spec card) {
        return set.displayName() + " card (" + (card.shiny() ? "shiny " : "") + card.rarity() + ")";
    }
}
