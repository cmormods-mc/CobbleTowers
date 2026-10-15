package com.cobbletowers.trial;

import com.cobbletowers.definition.TrialRecipeRegistry;
import com.cobbletowers.persistence.PersistedRun;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What a run's rotating trial asks of the game while it plays (P46): the weekly rule and the room it leans toward. Both
 * are re-rolled from the run's trial id, so a restart changes nothing; a plain run, a pool trial and a featured day have none.
 */
public final class TrialRules {

    private TrialRules() {}

    /** The composed parts of the run's trial, if it is a rotating one. */
    public static Optional<RotatingRoll> rollOf(PersistedRun run) {
        Optional<String> trial = run.options().trial();
        if (trial.isEmpty()) return Optional.empty();
        return TrialSchedule.kindOf(trial.get()).flatMap(kind -> TrialRecipeRegistry.ofKind(kind).flatMap(recipe -> {
            Optional<Long> number = numberOf(trial.get());
            if (number.isEmpty()) return Optional.empty();
            RotatingRoll roll = RotatingRoll.of(recipe, number.get());
            return roll.featured() ? Optional.empty() : Optional.of(roll);
        }));
    }

    /** The period number a trial id names (see {@link TrialSchedule#numberOf}). */
    static Optional<Long> numberOf(String instanceId) {
        Optional<LocalDate> day = TrialSchedule.dayOf(instanceId);
        if (day.isPresent()) return Optional.of(day.get().toEpochDay());
        if (instanceId.startsWith("weekly:")) {
            return TrialClock.weekStartOf(instanceId.substring("weekly:".length())).map(start -> start.toEpochDay() / 7);
        }
        return Optional.empty();
    }

    public static Optional<String> weeklyRule(PersistedRun run) {
        return rollOf(run).flatMap(RotatingRoll::weeklyRule);
    }

    public static boolean hasRule(PersistedRun run, String rule) {
        return run.options().isTrial() && weeklyRule(run).filter(rule::equals).isPresent();
    }

    /** The room name (lower case) this run's trial leans toward, if any. */
    public static Optional<String> roomBias(PersistedRun run) {
        return rollOf(run).flatMap(RotatingRoll::roomBias);
    }
}
