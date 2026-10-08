package com.cobbletowers.instance;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleFaintedEvent;
import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Nothing is given to a player when a Pokemon faints in the tower (owner decision 2026-10-08: a card dropped when a party
 * member fainted). Other mods hand out items from Cobblemon's faint event, some straight into the inventory, which the
 * ground-item guard cannot see. This runs around those handlers: it counts what each tower player carries first (highest
 * priority) and takes back anything extra afterwards (lowest). The event is dispatched in one go on the server thread,
 * so nothing legitimate can arrive in between. Server thread only.
 */
public final class FaintDropGuard {

    private static final Map<UUID, Map<String, Integer>> BEFORE = new HashMap<>();
    private static boolean installed;

    static {
        ServerState.onStop(BEFORE::clear);
    }

    private FaintDropGuard() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.HIGHEST, FaintDropGuard::before);
        CobblemonEvents.BATTLE_FAINTED.subscribe(Priority.LOWEST, FaintDropGuard::after);
    }

    private static void before(BattleFaintedEvent event) {
        try {
            for (ServerPlayer player : event.getBattle().getPlayers()) {
                if (inTower(player)) BEFORE.put(player.getUUID(), count(player));
            }
        } catch (RuntimeException ex) {
            TowerLog.errorOnce("faint-guard-before", "The faint drop guard could not count an inventory", ex);
        }
    }

    private static void after(BattleFaintedEvent event) {
        try {
            for (ServerPlayer player : event.getBattle().getPlayers()) {
                Map<String, Integer> before = BEFORE.remove(player.getUUID());
                if (before == null) continue;
                int removed = takeBackExtras(player, before);
                if (removed > 0) TowerLog.info("Took {} item(s) a faint handed to {} in the tower", removed, player.getGameProfile().getName());
            }
        } catch (RuntimeException ex) {
            TowerLog.errorOnce("faint-guard-after", "The faint drop guard could not check an inventory", ex);
        }
    }

    private static boolean inTower(ServerPlayer player) {
        return player.level().dimension().equals(TowerDimension.LEVEL);
    }

    /** What a signature of item and components counts to across the whole inventory. */
    static Map<String, Integer> count(ServerPlayer player) {
        Map<String, Integer> counts = new HashMap<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) counts.merge(signature(stack), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    static String signature(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getComponents().hashCode();
    }

    /** Removes whatever the inventory holds beyond {@code before}, newest slots first. @return how many items */
    private static int takeBackExtras(ServerPlayer player, Map<String, Integer> before) {
        Map<String, Integer> now = count(player);
        var inventory = player.getInventory();
        int removed = 0;
        for (Map.Entry<String, Integer> entry : now.entrySet()) {
            int extra = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            for (int slot = inventory.getContainerSize() - 1; slot >= 0 && extra > 0; slot--) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || !signature(stack).equals(entry.getKey())) continue;
                int take = Math.min(extra, stack.getCount());
                stack.shrink(take);
                extra -= take;
                removed += take;
            }
        }
        return removed;
    }
}
