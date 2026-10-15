package com.cobbletowers.mastery;

import com.cobbletowers.season.Cosmetics;
import java.util.Comparator;
import java.util.List;
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

    /** The font that holds the tower icons (a bitmap font in the resource pack, {@code font/tower_icons.json}). */
    public static final String FONT = "cobbletowers:tower_icons";

    /** The icon character of a tower: fixed for the shipped towers so it never moves, the generic one for any other. */
    public static String glyph(ResourceLocation tower) {
        if (tower.getNamespace().equals("cobbletowers")) {
            switch (tower.getPath()) {
                case "neutral": return "";
                case "tideforge": return "";
                case "rootvale": return "";
                case "duskvale": return "";
                default: break;
            }
        }
        return "";
    }

    /** The pieces in front of a name: the icon in the icon font, then the level in the rank's colour. */
    public static List<Cosmetics.Segment> segments(Badge badge, String rank) {
        return List.of(new Cosmetics.Segment(glyph(badge.tower()), "white", Optional.of(FONT)),
                new Cosmetics.Segment(badge.level() + " ", colorOf(rank)));
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
