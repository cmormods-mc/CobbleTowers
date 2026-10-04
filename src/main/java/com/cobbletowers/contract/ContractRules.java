package com.cobbletowers.contract;

import com.cobbletowers.api.modifier.RiskTier;
import com.cobbletowers.definition.ContractTemplateDefinition.Condition;
import com.cobbletowers.events.TowerEvent;
import java.util.UUID;

/** How much one event moves a contract (P32c). Pure: a condition, an event and a player in, a number out. */
public final class ContractRules {

    private ContractRules() {}

    /** Progress {@code player} makes on a contract of this condition from {@code event}; 0 when it does not count. */
    public static int delta(Condition condition, TowerEvent event, UUID player) {
        if (!event.players().contains(player)) return 0;
        return switch (condition.kind()) {
            case FLOORS_CLEARED -> event instanceof TowerEvent.FloorCleared floor && floorCounts(condition, floor) ? 1 : 0;
            case BOSSES_DEFEATED -> event instanceof TowerEvent.BossDefeated boss
                    && (!condition.solo() || boss.solo()) && boss.floorIndex() >= condition.minFloor() ? 1 : 0;
            case PURCHASES -> event instanceof TowerEvent.Purchased ? 1 : 0;
            case SEVERE_DRAFTS -> event instanceof TowerEvent.Drafted drafted && drafted.risk() == RiskTier.SEVERE ? 1 : 0;
            case TRIALS_FINISHED -> event instanceof TowerEvent.TrialFinished trial && trial.scored() && trial.completed() ? 1 : 0;
            case DAILY_TRIALS_FINISHED -> event instanceof TowerEvent.TrialFinished trial
                    && trial.scored() && trial.completed() && trial.daily() ? 1 : 0;
        };
    }

    private static boolean floorCounts(Condition condition, TowerEvent.FloorCleared floor) {
        if (condition.maxSeconds() > 0 && floor.floorMillis() > condition.maxSeconds() * 1000L) return false;
        if (condition.flawless() && !floor.flawless()) return false;
        if (condition.solo() && !floor.solo()) return false;
        return floor.floorIndex() >= condition.minFloor();
    }
}
