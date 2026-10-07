package com.cobbletowers.persistence;

import com.cobbletowers.api.reward.PendingRewardView;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One grant a player has not been handed because they were offline. The item id and amount are stored, never an
 * {@code ItemStack}, since the roll already happened. A CobblemonCards card (P33b) is kept as plain item text and
 * turned into a stack on handover.
 */
public record PendingTowerReward(UUID runId, int floorIndex, ResourceLocation item, int amount, long grantedAt,
                                 String components, String label) implements PendingRewardView {

    /** A plain grant of {@code amount} of an item, which is every reward before the card rewards. */
    public PendingTowerReward(UUID runId, int floorIndex, ResourceLocation item, int amount, long grantedAt) {
        this(runId, floorIndex, item, amount, grantedAt, "", "");
    }

    public PendingTowerReward {
        components = components == null ? "" : components;
        label = label == null ? "" : label;
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(item, "item");
        if (floorIndex < 1) throw new IllegalArgumentException("floorIndex must be >= 1, got " + floorIndex);
        if (amount < 1) throw new IllegalArgumentException("amount must be >= 1, got " + amount);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("run", runId);
        tag.putInt("floor", floorIndex);
        tag.putString("item", item.toString());
        tag.putInt("amount", amount);
        tag.putLong("granted_at", grantedAt);
        if (!components.isEmpty()) tag.putString("components", components);
        if (!label.isEmpty()) tag.putString("label", label);
        return tag;
    }

    public static PendingTowerReward fromTag(CompoundTag tag) {
        ResourceLocation item = ResourceLocation.tryParse(tag.getString("item"));
        if (item == null) throw new IllegalArgumentException("pending reward names an invalid item id: " + tag.getString("item"));
        return new PendingTowerReward(tag.getUUID("run"), tag.getInt("floor"), item, tag.getInt("amount"),
                tag.getLong("granted_at"), tag.getString("components"), tag.getString("label"));
    }
}
