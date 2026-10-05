package com.cobbletowers.season;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * What an earned cosmetic is called and how it is shown (P36d), with no server in sight.
 *
 * <p>A cosmetic is a plain name the season track and the club podium already record: {@code s1:title_champion}, {@code s1:badge},
 * {@code s1:banner_2}, {@code s1:club_gold}. Only a <b>title</b> can be worn (owner decision: banners and badges are collectables);
 * a worn title and the player's club tag are shown in front of their name, and this class decides the words and colours.
 */
public final class Cosmetics {

    public enum Kind { TITLE, BADGE, BANNER, CLUB }

    /** A cosmetic taken apart: its season, its own name ({@code title_champion}) and what kind it is. */
    public record Parsed(int season, String name, Kind kind) {}

    /** One piece of the text in front of a name, with the colour it is shown in (a vanilla formatting name such as {@code gold}). */
    public record Segment(String text, String color) {}

    private Cosmetics() {}

    /** Takes {@code s3:title_champion} apart, or empty for anything that is not a cosmetic name. */
    public static Optional<Parsed> parse(String id) {
        if (id == null) return Optional.empty();
        int colon = id.indexOf(':');
        if (colon < 2 || id.charAt(0) != 's') return Optional.empty();
        int season;
        try {
            season = Integer.parseInt(id.substring(1, colon));
        } catch (NumberFormatException invalid) {
            return Optional.empty();
        }
        String name = id.substring(colon + 1);
        Kind kind;
        if (name.startsWith("title_")) kind = Kind.TITLE;
        else if (name.equals("badge")) kind = Kind.BADGE;
        else if (name.startsWith("banner_")) kind = Kind.BANNER;
        else if (name.startsWith("club_")) kind = Kind.CLUB;
        else return Optional.empty();
        return season < 1 ? Optional.empty() : Optional.of(new Parsed(season, name, kind));
    }

    /** The full name, for lists: {@code Champion of The Rising Tide}. */
    public static String fullName(Parsed cosmetic, String seasonName) {
        String rest = cosmetic.name().substring(cosmetic.name().indexOf('_') + 1);
        return switch (cosmetic.kind()) {
            case TITLE -> capitalize(rest) + " of " + seasonName;
            case BADGE -> seasonName + " Badge";
            case BANNER -> seasonName + " Banner " + roman(parseIndex(rest));
            case CLUB -> switch (rest) {
                case "gold" -> seasonName + " Club Champion";
                case "silver" -> seasonName + " Club, Second Place";
                case "bronze" -> seasonName + " Club, Third Place";
                default -> seasonName + " Club (" + rest + ")";
            };
        };
    }

    /** The short form shown in chat: {@code Champion S1}. Only titles have one. */
    public static Optional<String> shortTitle(Parsed cosmetic) {
        if (cosmetic.kind() != Kind.TITLE) return Optional.empty();
        String rest = cosmetic.name().substring("title_".length());
        return Optional.of(capitalize(rest) + " S" + cosmetic.season());
    }

    /** The titles among {@code owned}, newest season first and, within a season, the higher title first (Champion before Challenger). */
    public static List<String> titlesOf(Collection<String> owned) {
        List<String> titles = new ArrayList<>();
        for (String id : owned) if (parse(id).map(p -> p.kind() == Kind.TITLE).orElse(false)) titles.add(id);
        titles.sort(Comparator.<String>comparingInt(id -> -parse(id).orElseThrow().season())
                .thenComparingInt(id -> titleRank(parse(id).orElseThrow().name())).thenComparing(id -> id));
        return titles;
    }

    private static int titleRank(String name) {
        return name.equals("title_champion") ? 0 : name.equals("title_challenger") ? 1 : 2;
    }

    /** The selected title if the player still owns it, else empty: a stale selection never shows. */
    public static Optional<String> validSelection(String selected, Collection<String> owned) {
        return selected != null && !selected.isEmpty() && owned.contains(selected) && titlesOf(List.of(selected)).size() == 1
                ? Optional.of(selected) : Optional.empty();
    }

    /**
     * The text in front of a name: the worn title (gold), then the club tag in brackets in the club's banner colour. Empty when the
     * player has neither, so such a player's name is not touched at all.
     */
    public static List<Segment> decoration(Optional<String> shortTitle, Optional<String> clubTag, Optional<String> clubBanner) {
        List<Segment> segments = new ArrayList<>();
        shortTitle.ifPresent(title -> segments.add(new Segment(title + " ", "gold")));
        clubTag.ifPresent(tag -> segments.add(new Segment("[" + tag + "] ", chatColor(clubBanner.orElse("white")))));
        return List.copyOf(segments);
    }

    /** The plain text of a decoration, for placeholders and logs: {@code Champion S1 [TC] }. */
    public static String plain(List<Segment> segments) {
        StringBuilder out = new StringBuilder();
        for (Segment segment : segments) out.append(segment.text());
        return out.toString();
    }

    /** The vanilla chat colour a banner colour is shown in. Dye colours map to the nearest, the prestige banners to metals. */
    public static String chatColor(String banner) {
        return switch (banner.toLowerCase(Locale.ROOT)) {
            case "white" -> "white";
            case "orange", "yellow", "gold", "brown" -> "gold";
            case "magenta", "pink" -> "light_purple";
            case "light_blue" -> "aqua";
            case "lime" -> "green";
            case "gray" -> "dark_gray";
            case "light_gray", "silver" -> "gray";
            case "cyan" -> "dark_aqua";
            case "purple" -> "dark_purple";
            case "blue" -> "blue";
            case "green" -> "dark_green";
            case "red", "bronze" -> "red";
            case "black" -> "black";
            default -> "white";
        };
    }

    /** Fills {@code {name}} tokens in {@code template} from {@code values}; an unknown token is left as written. */
    public static String expand(String template, Map<String, String> values) {
        String out = template;
        for (Map.Entry<String, String> value : values.entrySet()) out = out.replace("{" + value.getKey() + "}", value.getValue());
        return out;
    }

    static String roman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
