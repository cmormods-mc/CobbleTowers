package com.cobbletowers.trial;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.definition.TrialPoolDefinition;
import com.cobbletowers.definition.TrialPoolDefinition.Kind;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Which trial a day or week holds (P32): a pure function of the pool and date, so every server computes the same
 * trial, seed included. The pick walks the pool in seeded shuffled cycles ({@link #indexFor}).
 */
public final class TrialSchedule {

    private TrialSchedule() {}

    /**
     * One trial.
     * @param id {@code daily:2026-10-05} or {@code weekly:2026-w41}, stored on a run and used as a board key
     * @param periodKey the date or week
     * @param seed the run seed every attempt shares
     */
    public record Instance(String id, Kind kind, String periodKey, ResourceLocation pool, TrialPoolDefinition.Entry entry,
                           int floors, int streakMinFloors, long seed) {

        /** The board this trial's results live on, as a tower-style id: {@code cobbletowers:daily/2026-10-05}. */
        public ResourceLocation boardId() {
            return CobbleTowers.id(kind.name().toLowerCase(Locale.ROOT) + "/" + periodKey);
        }

        /** A name for messages. */
        public String title() {
            return (kind == Kind.DAILY ? "Daily" : "Weekly") + " Trial" + (entry.label().isEmpty() ? "" : ": " + entry.label());
        }
    }

    /** The trial a pool holds on a trial day. */
    public static Instance of(TrialPoolDefinition pool, LocalDate day) {
        String period = periodKey(pool.kind(), day);
        long number = pool.kind() == Kind.DAILY ? day.toEpochDay() : TrialClock.weekStart(day).toEpochDay() / 7;
        int index = indexFor(pool.id().toString(), pool.entries().size(), number);
        String id = pool.kind().name().toLowerCase(Locale.ROOT) + ":" + period;
        return new Instance(id, pool.kind(), period, pool.id(), pool.entries().get(index), pool.floors(),
                pool.streakMinFloors(), TrialSeed.of(id + "|" + pool.id() + "|seed"));
    }

    /**
     * Which entry period {@code number} holds: cycles as long as the pool, each a seeded shuffle that never begins
     * with the previous cycle's last entry. Depends only on the number.
     */
    public static int indexFor(String poolKey, int count, long number) {
        if (count <= 1) return 0;
        if (count == 2) return (int) Math.floorMod(number, 2L);
        long cycle = Math.floorDiv(number, (long) count);
        int position = (int) Math.floorMod(number, (long) count);
        int[] order = shuffled(poolKey, count, cycle);
        // The shuffle may open with what the previous cycle closed with; swapping the first two never touches the
        // last place.
        if (order[0] == shuffled(poolKey, count, cycle - 1)[count - 1]) {
            int swap = order[0];
            order[0] = order[1];
            order[1] = swap;
        }
        return order[position];
    }

    private static int[] shuffled(String poolKey, int count, long cycle) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) order[i] = i;
        for (int i = count - 1; i > 0; i--) {
            int j = (int) Math.floorMod(TrialSeed.of(poolKey + "|" + cycle + "|" + i), (long) (i + 1));
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    public static String periodKey(Kind kind, LocalDate day) {
        return kind == Kind.DAILY ? TrialClock.dayKey(day) : TrialClock.weekKey(day);
    }

    /**
     * The board a trial's results live on, from its instance id: {@code daily:2026-10-05} is {@code
     * cobbletowers:daily/2026-10-05}.
     */
    public static Optional<ResourceLocation> boardIdOf(String instanceId) {
        int colon = instanceId.indexOf(':');
        if (colon < 0 || kindOf(instanceId).isEmpty()) return Optional.empty();
        return Optional.ofNullable(ResourceLocation.tryParse("cobbletowers:" + instanceId.substring(0, colon) + "/"
                + instanceId.substring(colon + 1)));
    }

    /** The kind an instance id names, or empty for anything that is not a trial id. */
    public static Optional<Kind> kindOf(String instanceId) {
        if (instanceId.startsWith("daily:")) return Optional.of(Kind.DAILY);
        if (instanceId.startsWith("weekly:")) return Optional.of(Kind.WEEKLY);
        return Optional.empty();
    }

    /** The date a daily instance id names, for working out the day of a finished run. */
    public static Optional<LocalDate> dayOf(String instanceId) {
        if (!instanceId.startsWith("daily:")) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(instanceId.substring("daily:".length())));
        } catch (java.time.format.DateTimeParseException ex) {
            return Optional.empty();
        }
    }
}
