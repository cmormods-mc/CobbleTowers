package com.cobbletowers.armor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The sixteen armor items and four materials (P24), registered in code since they must exist before datapacks. Named
 * {@code cobbletowers:<set>_<slot>}; what a set does is data ({@link ArmorSetDefinition}). Protection is diamond's
 * (3/8/6/3, toughness 2, durability factor 33, enchantability 10); textures are generated placeholders in {@code
 * textures/models/armor}.
 */
public final class ArmorSetItems {

    public static final String NAMESPACE = "cobbletowers";
    /** The four sets, in the order they are registered and listed. */
    public static final List<String> SETS = List.of("challenger", "tideforge", "rootvale", "duskvale");

    private static final int DURABILITY_FACTOR = 33;
    private static final int ENCHANTABILITY = 10;

    private static final Map<ArmorItem.Type, String> SLOT_NAMES = new EnumMap<>(Map.of(
            ArmorItem.Type.HELMET, "helmet",
            ArmorItem.Type.CHESTPLATE, "chestplate",
            ArmorItem.Type.LEGGINGS, "leggings",
            ArmorItem.Type.BOOTS, "boots"));

    private static boolean registered;

    private ArmorSetItems() {}

    /** The id of one piece: {@code cobbletowers:tideforge_helmet}. */
    public static ResourceLocation pieceId(String set, ArmorItem.Type type) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, set + "_" + SLOT_NAMES.get(type));
    }

    /** Every item id this class registers, for the validators and the drop tables. */
    public static List<ResourceLocation> allPieceIds() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (String set : SETS) {
            for (ArmorItem.Type type : SLOT_NAMES.keySet()) ids.add(pieceId(set, type));
        }
        return ids;
    }

    /** Registers every material and piece. Idempotent: a second call (a test, a reload) does nothing. */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        for (String set : SETS) {
            Holder<ArmorMaterial> material = registerMaterial(set);
            for (ArmorItem.Type type : SLOT_NAMES.keySet()) {
                Item item = new ArmorItem(material, type,
                        new Item.Properties().durability(type.getDurability(DURABILITY_FACTOR)));
                Registry.register(BuiltInRegistries.ITEM, pieceId(set, type), item);
            }
        }
    }

    private static Holder<ArmorMaterial> registerMaterial(String set) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, 3);
        defense.put(ArmorItem.Type.CHESTPLATE, 8);
        defense.put(ArmorItem.Type.LEGGINGS, 6);
        defense.put(ArmorItem.Type.BOOTS, 3);
        defense.put(ArmorItem.Type.BODY, 11);
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NAMESPACE, set);
        ArmorMaterial material = new ArmorMaterial(defense, ENCHANTABILITY, SoundEvents.ARMOR_EQUIP_DIAMOND,
                () -> Ingredient.of(Items.DIAMOND), List.of(new ArmorMaterial.Layer(id)), 2.0F, 0.0F);
        return Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, id, material);
    }
}
