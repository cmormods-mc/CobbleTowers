package com.cobbletowers.reward;

import com.cobbletowers.api.modifier.RiskTier;
import java.util.ArrayList;
import java.util.List;

/**
 * The end-of-tower payout bonus for risk taken: each modifier held adds a percentage by {@link RiskTier} (they add,
 * not compound) and the total scales the final payout's rolled rewards. A milestone's guaranteed items are never
 * scaled. Pure.
 */
public final class RiskReward {

    public static final int MINOR = 3;
    public static final int MODERATE = 8;
    public static final int SEVERE = 15;

    private RiskReward() {}

    public static int percentOf(RiskTier risk) {
        return switch (risk) {
            case MINOR -> MINOR;
            case MODERATE -> MODERATE;
            case SEVERE -> SEVERE;
        };
    }

    /** The bonus, in percent, for these held modifiers (a locked-in one listed twice counts twice). */
    public static int bonusPercent(List<RiskTier> risks) {
        int total = 0;
        for (RiskTier risk : risks) total += percentOf(risk);
        return total;
    }

    /** {@code amount} with the bonus on top, rounded down; never less than {@code amount}. */
    public static int scale(int amount, int bonusPercent) {
        if (amount <= 0 || bonusPercent <= 0) return amount;
        return (int) Math.min(Integer.MAX_VALUE, (long) amount * (100 + bonusPercent) / 100);
    }

    /** The rolled grants scaled by the bonus; the per-player guaranteed ones are returned as they were. */
    public static List<RewardValuation.Grant> apply(List<RewardValuation.Grant> grants, int bonusPercent) {
        if (bonusPercent <= 0) return grants;
        List<RewardValuation.Grant> scaled = new ArrayList<>();
        for (RewardValuation.Grant grant : grants) {
            scaled.add(grant.perPlayer() ? grant : new RewardValuation.Grant(grant.item(), scale(grant.amount(), bonusPercent), false));
        }
        return List.copyOf(scaled);
    }
}
