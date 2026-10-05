package com.cobbletowers.economy;

import com.cobbletowers.TowerLog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * The tower key (decided 2026-10-05): the entry item for an ordinary run, one per player. It is an ordinary item, so
 * anything can hand it out by id ({@code cobbletowers:tower_key}): the daily vote crate, a Raids shop entry, a command.
 *
 * <p>Off until an operator turns it on with {@code config/cobbletowers-keys.json} ({@code {"required": true}}), so that
 * updating the mod before the key has a source cannot lock every player out. Which runs cost a key is
 * {@link TowerKeyPolicy}; the lobby checks everyone has one before the countdown ends and takes it only once the run
 * has really started, so a launch that fails costs nothing.
 */
public final class TowerKeys {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower_key");

    private static Item item;
    private static volatile boolean required;

    private TowerKeys() {}

    /** Registers the item and reads the setting at each server start. Called once at startup. */
    public static synchronized void install() {
        if (item == null) {
            item = Registry.register(BuiltInRegistries.ITEM, ID, new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
            ServerLifecycleEvents.SERVER_STARTING.register(server -> required = load());
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

    public static int count(Player player) {
        int total = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** Takes one key from the player's inventory. @return whether there was one to take */
    public static boolean take(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                stack.shrink(1);
                inventory.setChanged();
                return true;
            }
        }
        return false;
    }
}
