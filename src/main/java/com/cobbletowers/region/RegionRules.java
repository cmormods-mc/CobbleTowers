package com.cobbletowers.region;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The region rivalry's rules, pure. A player's region is their CobbleTeams team (a scoreboard team named after the
 * region); a tower's region is its regional theme or, failing that, its own id. Neutral towers belong to no region.
 */
public final class RegionRules {

    /** The three rival regions, in the order the page lists them. */
    public static final List<String> REGIONS = List.of("tideforge", "rootvale", "duskvale");

    /** One row of the duel. {@code HIGH} and {@code LOW} are records; {@code TOTAL} adds up over players. */
    public enum Stat {
        CLEARS("Total clears", Kind.TOTAL),
        AWAY("Away conquests", Kind.TOTAL),
        TRIALS("Trial clears", Kind.TOTAL),
        ASCENSION("Deepest Ascension", Kind.HIGH),
        FASTEST("Fastest cycle", Kind.LOW);

        public enum Kind { TOTAL, HIGH, LOW }

        private final String label;
        private final Kind kind;

        Stat(String label, Kind kind) {
            this.label = label;
            this.kind = kind;
        }

        public String label() {
            return label;
        }

        public Kind kind() {
            return kind;
        }
    }

    private RegionRules() {}

    /** The region a CobbleTeams team name stands for, matched without regard to case. */
    public static Optional<String> regionOfTeam(String teamName) {
        if (teamName == null) return Optional.empty();
        String name = teamName.trim().toLowerCase(Locale.ROOT);
        return REGIONS.contains(name) ? Optional.of(name) : Optional.empty();
    }

    /** The region a tower stands for: its theme's path if that is a region, else its own path. Empty for neutral towers. */
    public static Optional<String> regionOfTower(String towerPath, Optional<String> themePath) {
        Optional<String> theme = themePath.flatMap(RegionRules::regionOfTeam);
        return theme.isPresent() ? theme : regionOfTeam(towerPath);
    }

    /** An away conquest: a player of one region clearing the tower of another. Unknown or neutral on either side is not away. */
    public static boolean isAway(Optional<String> playerRegion, Optional<String> towerRegion) {
        return playerRegion.isPresent() && towerRegion.isPresent() && !playerRegion.get().equals(towerRegion.get());
    }

    public static String displayName(String region) {
        return region.isEmpty() ? region : Character.toUpperCase(region.charAt(0)) + region.substring(1);
    }

    /**
     * Which side leads on a stat: {@code -1} the left, {@code 1} the right, {@code 0} level. A {@code LOW} stat of 0
     * means nobody has set one yet, so it loses to any real value.
     *
     * <p>TODO(human): decide how the regions are compared. Regions differ in size, so raw totals favour the one with
     * the most players. See the reply for the trade-offs. Until then this compares the raw numbers.
     */
    public static int leader(Stat stat, long left, long right, int leftPlayers, int rightPlayers) {
        return switch (stat.kind()) {
            case TOTAL, HIGH -> Long.compare(left, right) == 0 ? 0 : left > right ? -1 : 1;
            case LOW -> {
                if (left == right) yield 0;
                if (left == 0) yield 1;
                if (right == 0) yield -1;
                yield left < right ? -1 : 1;
            }
        };
    }
}
