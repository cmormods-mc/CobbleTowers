package com.cobbletowers.contract;

import com.cobbletowers.definition.ContractTemplateDefinition;
import com.cobbletowers.definition.ContractTemplateDefinition.Period;
import com.cobbletowers.trial.TrialClock;
import com.cobbletowers.trial.TrialSchedule;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Which contracts a day or week holds (P32c): a pure function of the templates and the date, so every player is handed the same
 * ones with no coordination (and so the day's contracts are something the community can talk about). Three daily and two weekly,
 * never the same template twice at once. A reroll moves one slot to another template for that player alone.
 */
public final class ContractSchedule {

    public static final int DAILY_SLOTS = 3;
    public static final int WEEKLY_SLOTS = 2;
    /** How far a reroll jumps through the draw; any number that is not a small multiple of the pool works. */
    private static final long REROLL_STRIDE = 7919;

    private ContractSchedule() {}

    public static int slotsOf(Period period) {
        return period == Period.DAILY ? DAILY_SLOTS : WEEKLY_SLOTS;
    }

    /** The number a day or week is known by: the epoch day, or the epoch week of its Monday. */
    public static long periodNumber(Period period, LocalDate day) {
        return period == Period.DAILY ? day.toEpochDay() : TrialClock.weekStart(day).toEpochDay() / 7;
    }

    /** The key a period's progress is stored under: {@code daily:2026-10-05} or {@code weekly:2026-w41}. */
    public static String periodKey(Period period, LocalDate day) {
        return period == Period.DAILY ? "daily:" + TrialClock.dayKey(day) : "weekly:" + TrialClock.weekKey(day);
    }

    /**
     * The contracts for a period, one per slot.
     *
     * @param templates the templates of this period in a stable order
     * @param rerolls   for each slot, how many times it was rerolled (0 or 1), so a reroll gives a different pick
     */
    public static List<ContractTemplateDefinition> pick(List<ContractTemplateDefinition> templates, Period period, long number,
                                                        int[] rerolls) {
        int slots = slotsOf(period);
        if (templates.size() <= slots) return List.copyOf(templates);
        // The plain draw first: every slot, never the same template twice.
        List<Integer> chosen = new ArrayList<>();
        for (int slot = 0; slot < slots; slot++) {
            int index = TrialSchedule.indexFor("contracts:" + period.name(), templates.size(), number * slots + slot);
            while (chosen.contains(index)) index = (index + 1) % templates.size();
            chosen.add(index);
        }
        // A reroll then moves only its own slot, to something not already in the set, so the other slots (and any
        // progress on them) stay exactly as they were.
        for (int slot = 0; slot < slots && slot < rerolls.length; slot++) {
            if (rerolls[slot] <= 0) continue;
            int index = TrialSchedule.indexFor("contracts:" + period.name(), templates.size(),
                    number * slots + slot + rerolls[slot] * REROLL_STRIDE);
            while (chosen.contains(index)) index = (index + 1) % templates.size();
            chosen.set(slot, index);
        }
        List<ContractTemplateDefinition> picked = new ArrayList<>();
        for (int index : chosen) picked.add(templates.get(index));
        return List.copyOf(picked);
    }
}
