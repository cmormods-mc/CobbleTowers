package com.cobbletowers.armor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerArmorModel;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.rsm.api.RegistrySyncUtils;
import com.cobbletowers.ServerLang;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The forty-eight armor items and twelve materials (P24, P38), registered in code since they must exist before datapacks. Named
 * {@code cobbletowers:<set>_<slot>}; what a set does is data ({@link ArmorSetDefinition}). Protection is diamond's
 * (3/8/6/3, toughness 2, durability factor 33, enchantability 10); textures are generated placeholders in {@code
 * textures/models/armor}.
 */
public final class ArmorSetItems {

    public static final String NAMESPACE = "cobbletowers";
    /**
     * The twelve sets, in the order they are registered and listed: tier II (the first four, one per tower), then tier I
     * (utility) and tier III (defense) of each tower (P38).
     */
    public static final List<String> SETS = List.of("challenger", "tideforge", "rootvale", "duskvale",
            "recruit", "tidewalker", "sprout", "duskwanderer",
            "paragon", "leviathan", "heartwood", "nightfall");

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
                Item item = new PieceItem(material, type,
                        new Item.Properties().durability(type.getDurability(DURABILITY_FACTOR)), set);
                Registry.register(BuiltInRegistries.ITEM, pieceId(set, type), item);
            }
        }
    }

    private static Map<String, ResourceLocation> wornBy(ServerPlayer player) {
        Map<String, ResourceLocation> worn = new java.util.LinkedHashMap<>();
        put(worn, "head", player.getItemBySlot(EquipmentSlot.HEAD));
        put(worn, "chest", player.getItemBySlot(EquipmentSlot.CHEST));
        put(worn, "legs", player.getItemBySlot(EquipmentSlot.LEGS));
        put(worn, "feet", player.getItemBySlot(EquipmentSlot.FEET));
        return worn;
    }

    private static void put(Map<String, ResourceLocation> into, String slot, ItemStack stack) {
        if (!stack.isEmpty()) into.put(slot, BuiltInRegistries.ITEM.getKey(stack.getItem()));
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
        Holder<ArmorMaterial> holder = Registry.registerForHolder(BuiltInRegistries.ARMOR_MATERIAL, id, material);
        RegistrySyncUtils.setServerEntry(BuiltInRegistries.ARMOR_MATERIAL, material);
        return holder;
    }

    /** Server-only piece (P43): clients see dyed leather armor, and the pack's armor shader draws the set's worn texture. */
    private static final class PieceItem extends ArmorItem implements PolymerItem {
        private final Item base;
        private final PolymerArmorModel worn;
        private final PolymerModelData icon;

        PieceItem(Holder<ArmorMaterial> material, Type type, Properties properties, String set) {
            super(material, type, properties);
            this.base = switch (type) {
                case HELMET -> Items.LEATHER_HELMET;
                case CHESTPLATE -> Items.LEATHER_CHESTPLATE;
                case LEGGINGS -> Items.LEATHER_LEGGINGS;
                default -> Items.LEATHER_BOOTS;
            };
            this.worn = PolymerResourcePackUtils.requestArmor(ResourceLocation.fromNamespaceAndPath(NAMESPACE, set));
            this.icon = PolymerResourcePackUtils.requestModel(base,
                    ResourceLocation.fromNamespaceAndPath(NAMESPACE, "item/" + set + "_" + SLOT_NAMES.get(type)));
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return base;
        }

        @Override
        public int getPolymerCustomModelData(ItemStack stack, ServerPlayer player) {
            return icon.value();
        }

        @Override
        public Component getName(ItemStack stack) {
            return ServerLang.itemName(BuiltInRegistries.ITEM.getKey(this));
        }

        /** Set lines under the piece, lit by what the viewer wears (the server-side half of P25; no Shift expansion here). */
        @Override
        public void modifyClientTooltip(List<Component> tooltip, ItemStack stack, ServerPlayer player) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(this);
            for (ArmorSetDefinition set : ArmorSetRegistry.sets()) {
                if (!set.pieces().containsValue(id)) continue;
                tooltip.addAll(ArmorTooltipBuilder.build(ArmorSetViews.of(set), wornBy(player), ServerLang::itemName, true));
                return;
            }
        }

        @Override
        public int getPolymerArmorColor(ItemStack stack, ServerPlayer player) {
            return worn.color();
        }
    }
}
