package com.cobbletowers.armor;

import com.cobbletowers.TowerLog;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * What each online player's worn armor switches on, and the one place that applies it (P24). Every {@value
 * #CHECK_INTERVAL_TICKS} ticks the slots are resolved against the loaded sets ({@link SetBonusResolver}) and cached;
 * consumers read {@link #of(UUID)}. Attribute modifiers are transient, so a crash leaves no bonus behind.
 */
public final class WornSets {

    /** Half a second: quick enough that taking a piece off feels immediate, cheap enough to run for everyone. */
    static final int CHECK_INTERVAL_TICKS = 10;

    private static final Map<UUID, ActiveBonuses> CACHE = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> WARNED_ATTRIBUTES = ConcurrentHashMap.newKeySet();
    private static boolean installed;
    private static int ticks;

    private WornSets() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++ticks % CHECK_INTERVAL_TICKS != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    refresh(player);
                } catch (RuntimeException ex) {
                    TowerLog.error("Could not refresh the armor set bonuses of {}", player.getGameProfile().getName(), ex);
                }
            }
        });
        // The cache starts empty and the modifiers are transient, so a fresh login needs no cleanup; dropping the
        // entry on disconnect just stops the map growing.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CACHE.remove(handler.getPlayer().getUUID()));
    }

    /**
     * The player's bonuses now, re-resolved first so a consumer reads an exact value. Cheap, and consumers are
     * occasional events.
     */
    public static ActiveBonuses current(ServerPlayer player) {
        refresh(player);
        return of(player.getUUID());
    }

    /**
     * The player's active bonuses as of the last check; {@link ActiveBonuses#NONE} for anyone not wearing a set (or
     * not online).
     */
    public static ActiveBonuses of(UUID player) {
        return CACHE.getOrDefault(player, ActiveBonuses.NONE);
    }

    /** Re-resolves one player now. Public so a test (and a join) need not wait for the next interval. */
    public static void refresh(ServerPlayer player) {
        ActiveBonuses wanted = SetBonusResolver.resolve(wornItems(player), ArmorSetRegistry.sets());
        ActiveBonuses before = CACHE.getOrDefault(player.getUUID(), ActiveBonuses.NONE);
        if (wanted.equals(before)) return;
        applyAttributes(player, before, wanted);
        if (wanted.isNone()) CACHE.remove(player.getUUID());
        else CACHE.put(player.getUUID(), wanted);
    }

    /** Test seam: the cache is bypassed and the resolved result returned without being applied. */
    public static ActiveBonuses resolveNow(ServerPlayer player) {
        return SetBonusResolver.resolve(wornItems(player), ArmorSetRegistry.sets());
    }

    private static Map<String, ResourceLocation> wornItems(ServerPlayer player) {
        Map<EquipmentSlot, String> slots = new EnumMap<>(EquipmentSlot.class);
        slots.put(EquipmentSlot.HEAD, "head");
        slots.put(EquipmentSlot.CHEST, "chest");
        slots.put(EquipmentSlot.LEGS, "legs");
        slots.put(EquipmentSlot.FEET, "feet");
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        for (Map.Entry<EquipmentSlot, String> slot : slots.entrySet()) {
            ItemStack stack = player.getItemBySlot(slot.getKey());
            if (!stack.isEmpty()) worn.put(slot.getValue(), BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return worn;
    }

    private static void applyAttributes(ServerPlayer player, ActiveBonuses before, ActiveBonuses wanted) {
        Set<ResourceLocation> keep = new HashSet<>();
        for (ActiveBonuses.Attribute attribute : wanted.attributes()) keep.add(attribute.key());

        for (ActiveBonuses.Attribute old : before.attributes()) {
            if (keep.contains(old.key())) continue;
            AttributeInstance instance = instanceOf(player, old.attribute());
            if (instance != null) instance.removeModifier(old.key());
        }
        for (ActiveBonuses.Attribute attribute : wanted.attributes()) {
            AttributeInstance instance = instanceOf(player, attribute.attribute());
            if (instance == null) continue;
            // Remove first: an amount that changed (a reload) must replace the old modifier, not be refused as a
            // duplicate.
            instance.removeModifier(attribute.key());
            instance.addTransientModifier(new AttributeModifier(attribute.key(), attribute.amount(),
                    operationOf(attribute.operation())));
        }
    }

    private static AttributeInstance instanceOf(ServerPlayer player, ResourceLocation attribute) {
        Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(attribute).orElse(null);
        AttributeInstance instance = holder == null ? null : player.getAttribute(holder);
        if (instance == null && WARNED_ATTRIBUTES.add(attribute)) {
            TowerLog.warn("An armor set names attribute {}, which this player or game version does not have; skipped.",
                    attribute);
        }
        return instance;
    }

    private static AttributeModifier.Operation operationOf(String operation) {
        return switch (operation) {
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> AttributeModifier.Operation.ADD_VALUE;
        };
    }
}
