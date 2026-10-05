package com.cobbletowers.season;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Season points (P36b): where they come from, how much each is worth, and the caps that keep them from being ground. Pure: no
 * server, no clock; the caller passes the day and week it is awarding on.
 *
 * <p>Points are a tally of play that already exists, never a way to earn anything new. Every source already has its own anti-farm rule
 * (a cycle must be cleared, a trial attempt is once, a contract needs depth); the limits here are a second belt. Only regional
 * towers earn points for clears (owner decision): Neutral and the Test tower earn none.
 */
public final class SeasonPoints {

    /** The most a player can earn from the daily sources in one day. */
    public static final int DAILY_CAP = 80;

    public enum Source {
        /** A regional cycle clear: 20, or 30 in the season's spotlight region; at most three count a day. */
        REGIONAL_CLEAR(20, 3, true),
        DAILY_TRIAL(15, 1, true),
        DAILY_CONTRACT(5, 3, true),
        ECHO_DUEL(5, 1, true),
        /** Weekly sources are outside the daily cap and happen once a week by their own rules. */
        WEEKLY_TRIAL(40, 0, false),
        WEEKLY_CONTRACT(20, 0, false),
        CLUB_CLAIM(20, 0, false),
        /** A streak milestone: once each, outside the daily cap. The value depends on the milestone. */
        STREAK_MILESTONE(0, 0, false);

        private final int points;
        private final int dailyLimit;
        private final boolean daily;

        Source(int points, int dailyLimit, boolean daily) {
            this.points = points;
            this.dailyLimit = dailyLimit;
            this.daily = daily;
        }

        public int points() { return points; }

        /** How many times a day this source counts, 0 for a source with no daily count (a weekly or one-off one). */
        public int dailyLimit() { return dailyLimit; }

        /** Whether this source counts against the daily cap. */
        public boolean daily() { return daily; }
    }

    /** Points for a regional clear in the spotlight region. */
    public static final int SPOTLIGHT_CLEAR = 30;

    /** Points for reaching a streak milestone of {@code days}: 10, 20, 30, 50 at 3, 7, 14, 30, and 50 for any later one. */
    public static int streakPoints(int days) {
        return switch (days) {
            case 3 -> 10;
            case 7 -> 20;
            case 14 -> 30;
            default -> days >= 30 ? 50 : 0;
        };
    }

    /**
     * One player's tally for one season.
     *
     * @param total       season points so far
     * @param steps       track steps already granted
     * @param day         the day {@code dayTotal} and {@code dayCounts} are for ({@code ""} for none yet)
     * @param dayTotal    points earned from the daily sources on {@code day}
     * @param dayCounts   how many times each daily source has counted on {@code day}
     * @param once        what has already counted once in this season, keyed by source and period (a week, a streak milestone)
     */
    public record Progress(int total, int steps, String day, int dayTotal, Map<Source, Integer> dayCounts, Set<String> once) {
        public Progress {
            dayCounts = Map.copyOf(dayCounts);
            once = Set.copyOf(once);
        }

        public static final Progress EMPTY = new Progress(0, 0, "", 0, Map.of(), Set.of());

        public Progress withSteps(int next) {
            return new Progress(total, next, day, dayTotal, dayCounts, once);
        }
    }

    /** What an award did: the new tally and the points it actually added (0 when capped, or already counted). */
    public record Result(Progress next, int granted) {}

    /**
     * Awards one source.
     *
     * @param param    the streak milestone for {@link Source#STREAK_MILESTONE}; ignored otherwise
     * @param spotlight whether a {@link Source#REGIONAL_CLEAR} was in the season's spotlight region
     * @param dayKey   today's trial day, {@code 2026-10-12}
     * @param weekKey  this trial week, {@code 2026-w42}
     */
    public static Result award(Progress progress, Source source, int param, boolean spotlight, String dayKey, String weekKey) {
        // A new day starts the daily tally over.
        String day = progress.day();
        int dayTotal = progress.dayTotal();
        Map<Source, Integer> counts = new EnumMap<>(Source.class);
        if (dayKey.equals(day)) counts.putAll(progress.dayCounts());
        else dayTotal = 0;

        Set<String> once = new HashSet<>(progress.once());
        int points = source == Source.STREAK_MILESTONE ? streakPoints(param)
                : source == Source.REGIONAL_CLEAR && spotlight ? SPOTLIGHT_CLEAR : source.points();
        int granted;
        if (source.daily()) {
            int used = counts.getOrDefault(source, 0);
            if (used >= source.dailyLimit()) return new Result(progress, 0);
            granted = Math.max(0, Math.min(points, DAILY_CAP - dayTotal));
            counts.put(source, used + 1);
            dayTotal += granted;
        } else {
            // Once per period: a week for the weekly sources, ever (this season) for each streak milestone.
            String key = source == Source.STREAK_MILESTONE ? "streak:" + param : source.name() + ":" + weekKey;
            if (!once.add(key)) return new Result(progress, 0);
            granted = points;
        }
        return new Result(new Progress(progress.total() + granted, progress.steps(), dayKey, dayTotal, counts, once), granted);
    }
}
