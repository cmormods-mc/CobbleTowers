package com.cobbletowers.client;

import com.cobbletowers.armor.ArmorSetView;
import com.cobbletowers.armor.ArmorTooltipBuilder;
import com.cobbletowers.network.ArmorSetsPayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Armor set tooltips, the client half of P25: remembers the sets the server last described and adds set lines under
 * pieces that belong to one, lit or locked by what the viewer wears. With no payload a piece keeps its ordinary
 * tooltip.
 */
public final class ArmorTooltips {

    /** The set each piece belongs to, by item id; replaced wholesale by each payload. */
    private static volatile Map<ResourceLocation, ArmorSetView> BY_ITEM = Map.of();

    private ArmorTooltips() {}

    public static void install() {
        ClientPlayNetworking.registerGlobalReceiver(ArmorSetsPayload.TYPE,
                (payload, context) -> context.client().execute(() -> update(payload.sets())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BY_ITEM = Map.of());
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            try {
                append(stack, lines);
            } catch (RuntimeException ex) {
                // A tooltip is never worth a crash.
            }
        });
    }

    static void update(List<ArmorSetView> sets) {
        Map<ResourceLocation, ArmorSetView> byItem = new HashMap<>();
        for (ArmorSetView set : sets) {
            for (ArmorSetView.Piece piece : set.pieces()) byItem.put(piece.item(), set);
        }
        BY_ITEM = Map.copyOf(byItem);
    }

    private static void append(ItemStack stack, List<Component> lines) {
        Map<ResourceLocation, ArmorSetView> sets = BY_ITEM;
        if (sets.isEmpty() || stack.isEmpty()) return;
        ArmorSetView set = sets.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (set == null) return;
        lines.addAll(ArmorTooltipBuilder.build(set, wornBy(Minecraft.getInstance().player),
                item -> Optional.ofNullable(BuiltInRegistries.ITEM.get(item)).map(i -> i.getDescription()).orElse(Component.literal(item.toString())),
                Screen.hasShiftDown()));
    }

    /** The viewer's own armor, by slot name. Empty when there is no player (the main menu, a creative tab). */
    private static Map<String, ResourceLocation> wornBy(Player player) {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        if (player == null) return worn;
        worn(worn, "head", player.getItemBySlot(EquipmentSlot.HEAD));
        worn(worn, "chest", player.getItemBySlot(EquipmentSlot.CHEST));
        worn(worn, "legs", player.getItemBySlot(EquipmentSlot.LEGS));
        worn(worn, "feet", player.getItemBySlot(EquipmentSlot.FEET));
        return worn;
    }

    private static void worn(Map<String, ResourceLocation> into, String slot, ItemStack stack) {
        if (!stack.isEmpty()) into.put(slot, BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }
}
