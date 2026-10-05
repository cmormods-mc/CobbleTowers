package com.cobbletowers.season;

import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.definition.SeasonRegistry;
import com.cobbletowers.trial.TrialService;
import java.time.LocalDate;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * What season it is right now (P36a): the configured anchor and master switch, and the calendar read at today's trial day, so the
 * day-pinning seam an operator or a test uses moves seasons too.
 */
public final class Seasons {

    private static volatile SeasonConfig CONFIG = SeasonConfig.standard();

    private Seasons() {}

    public static void install() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CONFIG = SeasonConfig.load());
    }

    public static SeasonConfig config() {
        return CONFIG;
    }

    /** An operator's switch; in memory until the config file says otherwise at the next start. */
    public static void setEnabled(boolean enabled) {
        CONFIG = new SeasonConfig(CONFIG.anchor(), enabled);
    }

    public static boolean enabled() {
        return CONFIG.enabled();
    }

    public static LocalDate today() {
        return TrialService.today();
    }

    /** The calendar phase today, or empty when seasons are off. */
    public static Optional<SeasonSchedule.Phase> phase() {
        return CONFIG.enabled() ? Optional.of(SeasonSchedule.at(CONFIG.anchor(), today())) : Optional.empty();
    }

    /** The id of the season that is running now ({@code s3}), empty before the first, in an off-season, or when seasons are off. */
    public static Optional<String> activeId() {
        return phase().filter(phase -> phase instanceof SeasonSchedule.Active)
                .map(phase -> SeasonSchedule.idOf(((SeasonSchedule.Active) phase).number()));
    }

    /**
     * The season a default view shows: the running one, or during the off-season the one that has just ended (its boards are
     * frozen but still live). Empty before the first season and when seasons are off, so the view falls back to all-time.
     */
    public static Optional<Integer> viewNumber() {
        return phase().flatMap(phase -> {
            if (phase instanceof SeasonSchedule.Active active) return Optional.of(active.number());
            if (phase instanceof SeasonSchedule.OffSeason off) return Optional.of(off.endedNumber());
            return Optional.empty();
        });
    }

    /** The newest season that has ended by today (0 if none, and when seasons are off). */
    public static int lastEnded() {
        return CONFIG.enabled() ? SeasonSchedule.lastEnded(CONFIG.anchor(), today()) : 0;
    }

    public static SeasonDefinition definition(int number) {
        return SeasonRegistry.of(number);
    }
}
