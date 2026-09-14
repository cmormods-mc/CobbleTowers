package com.cobbletowers.run;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Owns accepted challenge history and tiered permanent modifier progression for one run.
 *
 * <p>Every fifth accepted challenge opens a promotion choice over the most recent five accepted
 * challenges. Promoting a modifier that is already permanent increments its tier instead of adding a
 * duplicate entry. A lightweight mutation callback lets persistence observe durable changes without
 * coupling this domain object to Minecraft SavedData.
 */
public final class TowerModifierProgress {
    private static final int PROMOTION_WINDOW = 5;
    private static final Runnable NO_OP = () -> {};

    private final Deque<ResourceLocation> recentAccepted = new ArrayDeque<>(PROMOTION_WINDOW);
    private final Map<ResourceLocation, Integer> permanentTiers = new LinkedHashMap<>();
    private final Runnable onMutation;
    private int acceptedChallengeCount;
    private boolean promotionPending;
    private ResourceLocation pendingTemporary;

    public TowerModifierProgress() {
        this(NO_OP);
    }

    TowerModifierProgress(Runnable onMutation) {
        this.onMutation = Objects.requireNonNull(onMutation, "onMutation");
    }

    public void acceptTemporary(ResourceLocation modifierId) {
        Objects.requireNonNull(modifierId, "modifierId");
        if (promotionPending) {
            throw new IllegalStateException("Resolve the pending permanent promotion before accepting another challenge");
        }
        if (pendingTemporary != null) {
            throw new IllegalStateException("A temporary challenge is already pending for the next boss");
        }

        pendingTemporary = modifierId;
        acceptedChallengeCount++;
        if (recentAccepted.size() == PROMOTION_WINDOW) recentAccepted.removeFirst();
        recentAccepted.addLast(modifierId);
        if (acceptedChallengeCount % PROMOTION_WINDOW == 0) promotionPending = true;
        onMutation.run();
    }

    public ResourceLocation consumeTemporary() {
        ResourceLocation consumed = pendingTemporary;
        if (consumed != null) {
            pendingTemporary = null;
            onMutation.run();
        }
        return consumed;
    }

    public void promote(ResourceLocation modifierId) {
        Objects.requireNonNull(modifierId, "modifierId");
        if (!promotionPending) throw new IllegalStateException("No permanent modifier promotion is pending");
        if (!recentAccepted.contains(modifierId)) {
            throw new IllegalArgumentException("Modifier is not one of the five eligible recent challenges: " + modifierId);
        }

        permanentTiers.merge(modifierId, 1, Integer::sum);
        promotionPending = false;
        recentAccepted.clear();
        onMutation.run();
    }

    public int acceptedChallengeCount() {
        return acceptedChallengeCount;
    }

    public boolean promotionPending() {
        return promotionPending;
    }

    public ResourceLocation pendingTemporary() {
        return pendingTemporary;
    }

    public List<ResourceLocation> promotionChoices() {
        return promotionPending ? List.copyOf(new ArrayList<>(recentAccepted)) : List.of();
    }

    /** Exact recent-history window for persistence; unlike promotionChoices(), this is not state-filtered. */
    public List<ResourceLocation> promotionChoicesForPersistence() {
        return List.copyOf(new ArrayList<>(recentAccepted));
    }

    public Map<ResourceLocation, Integer> permanentTiers() {
        return Map.copyOf(permanentTiers);
    }

    /** Restore persisted state without replaying challenge side effects. */
    public static TowerModifierProgress restore(
            int acceptedChallengeCount,
            boolean promotionPending,
            ResourceLocation pendingTemporary,
            List<ResourceLocation> recentAccepted,
            Map<ResourceLocation, Integer> permanentTiers
    ) {
        return restore(
                acceptedChallengeCount,
                promotionPending,
                pendingTemporary,
                recentAccepted,
                permanentTiers,
                NO_OP
        );
    }

    static TowerModifierProgress restore(
            int acceptedChallengeCount,
            boolean promotionPending,
            ResourceLocation pendingTemporary,
            List<ResourceLocation> recentAccepted,
            Map<ResourceLocation, Integer> permanentTiers,
            Runnable onMutation
    ) {
        if (acceptedChallengeCount < 0) throw new IllegalArgumentException("acceptedChallengeCount must be >= 0");
        Objects.requireNonNull(recentAccepted, "recentAccepted");
        Objects.requireNonNull(permanentTiers, "permanentTiers");
        if (recentAccepted.size() > PROMOTION_WINDOW) {
            throw new IllegalArgumentException("recentAccepted may contain at most five modifiers");
        }

        TowerModifierProgress restored = new TowerModifierProgress(onMutation);
        restored.acceptedChallengeCount = acceptedChallengeCount;
        restored.promotionPending = promotionPending;
        restored.pendingTemporary = pendingTemporary;
        for (ResourceLocation id : recentAccepted) restored.recentAccepted.addLast(Objects.requireNonNull(id));
        for (Map.Entry<ResourceLocation, Integer> entry : permanentTiers.entrySet()) {
            ResourceLocation id = Objects.requireNonNull(entry.getKey());
            int tier = Objects.requireNonNull(entry.getValue());
            if (tier < 1) throw new IllegalArgumentException("Permanent modifier tiers must be >= 1");
            restored.permanentTiers.put(id, tier);
        }
        return restored;
    }
}
