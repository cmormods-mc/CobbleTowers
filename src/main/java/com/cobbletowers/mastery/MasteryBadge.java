package com.cobbletowers.mastery;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The mastery badge (P47): which tower icon, and which level, a name shows. The tower with the highest mastery level
 * wins; a tie goes to the tower whose latest achievement is newest, then to the lower id so the answer never depends on
 * map order. Pure.
 */
public final class MasteryBadge {

    private MasteryBadge() {}

    /** A standing in one tower: the mastery level and when the latest achievement was earned (0 if none). */
    public record Standing(int level, long lastAt) {}

    /** What the name shows: the tower whose icon to draw and the level beside it. */
    public record Badge(ResourceLocation tower, int level) {}

    /** The badge for these standings, or empty when there is no level anywhere. */
    public static Optional<Badge> of(Map<ResourceLocation, Standing> standings) {
        return standings.entrySet().stream()
                .filter(entry -> entry.getValue().level() > 0)
                .max(Comparator.<Map.Entry<ResourceLocation, Standing>>comparingInt(entry -> entry.getValue().level())
                        .thenComparingLong(entry -> entry.getValue().lastAt())
                        .thenComparing(Map.Entry::getKey, Comparator.comparing(ResourceLocation::toString).reversed()))
                .map(entry -> new Badge(entry.getKey(), entry.getValue().level()));
    }

    /** The vanilla chat colour a rank name is shown in; an unknown rank (an addon's) is white. */
    public static String colorOf(String rank) {
        return switch (rank.toLowerCase(Locale.ROOT)) {
            case "bronze" -> "red";
            case "silver" -> "gray";
            case "gold" -> "gold";
            case "platinum" -> "aqua";
            case "diamond" -> "dark_aqua";
            case "master" -> "dark_purple";
            case "champion" -> "yellow";
            default -> "white";
        };
    }
}
