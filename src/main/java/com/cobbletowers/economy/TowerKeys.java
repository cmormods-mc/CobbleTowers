package com.cobbletowers.economy;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.TowerLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.server.level.ServerPlayer;

/**
 * The entry keys: the tower key ({@code cobbletowers:tower_key}, decided 2026-10-05) for an ordinary run and the rental
 * key ({@code cobbletowers:rental_key}) for a rental-draft run, one per player. Off until {@code
 * config/cobbletowers-keys.json} sets {@code {"required": true}}. {@link TowerKeyPolicy} says which runs cost which
 * key; the lobby takes one only once the run has started.
 */
public final class TowerKeys {

    /** The two keys. */
    public enum Kind {
        TOWER("tower_key", Rarity.UNCOMMON, "Tower Key"),
        RENTAL("rental_key", Rarity.RARE, "Rental Key");

        private final String path;
        private final Rarity rarity;
        private final String label;
        private Item item;

        Kind(String path, Rarity rarity, String label) {
            this.path = path;
            this.rarity = rarity;
            this.label = label;
        }

        public ResourceLocation id() {
            return CobbleTowers.id(path);
        }

        /** The name players see in messages. */
        public String label() {
            return label;
        }
    }

    public static final ResourceLocation ID = CobbleTowers.id("tower_key");

    private static volatile boolean required;

    private TowerKeys() {}

    /** Server-only item (P43): clients see a trial key with a custom model. */
    private static final class KeyItem extends Item implements PolymerItem {
        private final ResourceLocation id;
        private final PolymerModelData model;

        KeyItem(Item.Properties properties, ResourceLocation id) {
            super(properties);
            this.id = id;
            this.model = PolymerResourcePackUtils.requestModel(Items.TRIAL_KEY, id.withPrefix("item/"));
        }

        @Override
        public Item getPolymerItem(ItemStack stack, ServerPlayer player) {
            return model.item();
        }

        @Override
        public net.minecraft.network.chat.Component getName(ItemStack stack) {
            return com.cobbletowers.ServerLang.itemName(id);
        }

        @Override
        public int getPolymerCustomModelData(ItemStack stack, ServerPlayer player) {
            return model.value();
        }
    }

    /** Registers the item and reads the setting at each server start. Called once at startup. */
    public static synchronized void install() {
        if (Kind.TOWER.item == null) {
            for (Kind kind : Kind.values()) {
                kind.item = Registry.register(BuiltInRegistries.ITEM, kind.id(),
                        new KeyItem(new Item.Properties().rarity(kind.rarity), kind.id()));
            }
            ServerLifecycleEvents.SERVER_STARTING.register(server -> required = load());
            ServerPlayerEvents.AFTER_RESPAWN.register((old, player, alive) -> giveBack(player));
            ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveBack(handler.getPlayer()));
        }
    }

    public static boolean required() {
        return required;
    }

    private static boolean load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-keys.json");
        if (!Files.exists(file)) return false;
        try {
            boolean on = TowerKeyPolicy.parseRequired(Files.readString(file));
            TowerLog.info("Tower keys are {} ({})", on ? "required for ordinary runs" : "not required", file);
            return on;
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); tower keys are not required", file, ex.toString());
            return false;
        }
    }

    /** Tower keys set aside from a death drop until the player is back. Memory only: a restart in between loses them. */
    private static final java.util.Map<java.util.UUID, Integer> SET_ASIDE = new java.util.concurrent.ConcurrentHashMap<>();

    /** A dying player does not drop a tower key: it is held here and returned on respawn. */
    public static void setAside(java.util.UUID player, int count) {
        SET_ASIDE.merge(player, count, Integer::sum);
    }

    private static void giveBack(ServerPlayer player) {
        Integer count = SET_ASIDE.remove(player.getUUID());
        if (count == null || Kind.TOWER.item == null) return;
        ItemStack keys = new ItemStack(Kind.TOWER.item, count);
        if (!player.getInventory().add(keys)) player.drop(keys, false);
    }

    /** Whether this stack is a tower key, which stays with its holder (the rental key can be sold and traded). */
    public static boolean isTradeLocked(ItemStack stack) {
        return Kind.TOWER.item != null && stack.is(Kind.TOWER.item);
    }

    public static int count(Player player) {
        return count(player, Kind.TOWER);
    }

    public static int count(Player player, Kind kind) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(kind.item)) total += stack.getCount();
        }
        return total;
    }

    /** Takes one key from the player's inventory. @return whether there was one to take */
    public static boolean take(Player player) {
        return take(player, Kind.TOWER);
    }

    public static boolean take(Player player, Kind kind) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(kind.item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
