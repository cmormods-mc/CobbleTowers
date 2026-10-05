package com.cobbletowers.club;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Every club and every player's best regional clear (P35, roadmap D2), as plain data with no server in sight.
 *
 * <p>A club is a persistent named crew of up to {@value #MAX_MEMBERS}. Its score is the sum of its members' best regional cycle
 * clear (the same difficulty score the Difficulty board ranks); a weekly goal counts every member's regional cycle clears
 * and pays each member once when it is met. Only regional towers count: the caller decides that, this class only keeps the
 * numbers.
 *
 * <p>A player's best is kept whether or not they are in a club, so joining a crew brings their record with them.
 */
public final class ClubBook {

    public static final int MAX_MEMBERS = 12;
    public static final int MIN_NAME = 3;
    public static final int MAX_NAME = 16;
    public static final int MIN_TAG = 2;
    public static final int MAX_TAG = 4;
    /** Regional cycle clears, across all members, that meet a club's weekly goal. */
    public static final int WEEKLY_GOAL = 12;
    /** CobbleDollars each member may claim once per week after the goal is met. */
    public static final int WEEKLY_REWARD = 300;

    /** Banner colours only a club that has finished a season in the top three can set (P36c): first, second and third. */
    public static final List<String> PRESTIGE_BANNERS = List.of("gold", "silver", "bronze");
    /** How many clubs a season's podium has. */
    public static final int PODIUM = 3;

    /** The sixteen dye names a banner may be. */
    public static final List<String> BANNERS = List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
            "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    public enum Result {
        OK, BAD_NAME, BAD_TAG, NAME_TAKEN, ALREADY_IN_CLUB, NOT_IN_CLUB, NOT_OWNER, FULL, NO_SUCH_CLUB, BAD_BANNER, NOT_A_MEMBER,
        GOAL_NOT_MET, ALREADY_CLAIMED
    }

    /** One club. Mutable, owned by a {@link ClubBook}. */
    public static final class Club {
        private final String name;
        private String tag;
        private String banner = "white";
        private UUID owner;
        private final Map<UUID, String> members = new LinkedHashMap<>();
        private final long createdAt;
        private String weekKey = "";
        private int weekClears;
        private final Set<UUID> claimed = new HashSet<>();
        private final Set<String> unlockedBanners = new HashSet<>();
        private final List<String> honors = new ArrayList<>();

        public Club(String name, String tag, UUID owner, String ownerName, long createdAt) {
            this.name = name;
            this.tag = tag;
            this.owner = owner;
            this.createdAt = createdAt;
            members.put(owner, ownerName);
        }

        public String name() { return name; }

        public String tag() { return tag; }

        public String banner() { return banner; }

        public UUID owner() { return owner; }

        public Map<UUID, String> members() { return Map.copyOf(members); }

        public List<UUID> memberIds() { return List.copyOf(members.keySet()); }

        public long createdAt() { return createdAt; }

        public String weekKey() { return weekKey; }

        public int weekClears() { return weekClears; }

        public Set<UUID> claimed() { return Set.copyOf(claimed); }

        /** The prestige banner colours this club has earned. */
        public Set<String> unlockedBanners() { return Set.copyOf(unlockedBanners); }

        /** What the club has been honoured for, oldest first, such as {@code Season 1: champion}. */
        public List<String> honors() { return List.copyOf(honors); }

        /** Restores the stored honours; used by the store when loading. */
        public void restoreHonors(Set<String> unlocked, List<String> earned) {
            unlockedBanners.clear();
            unlockedBanners.addAll(unlocked);
            honors.clear();
            honors.addAll(earned);
        }

        /** Restores stored state; used by the store when loading. */
        public void restore(String banner, String weekKey, int weekClears, Set<UUID> claimed, Map<UUID, String> members) {
            this.banner = banner;
            this.weekKey = weekKey;
            this.weekClears = weekClears;
            this.claimed.clear();
            this.claimed.addAll(claimed);
            this.members.clear();
            this.members.putAll(members);
        }

        void roll(String currentWeek) {
            if (!currentWeek.equals(weekKey)) {
                weekKey = currentWeek;
                weekClears = 0;
                claimed.clear();
            }
        }
    }

    private final Map<String, Club> clubs = new LinkedHashMap<>();
    private final Map<UUID, Integer> best = new LinkedHashMap<>();
    /** Each player's best regional clear within a season (P36c), kept for the seasons not yet finalised. */
    private final Map<Integer, Map<UUID, Integer>> seasonBests = new LinkedHashMap<>();

    // ---- naming ------------------------------------------------------------------------------------

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public static boolean validName(String name) {
        return name != null && name.length() >= MIN_NAME && name.length() <= MAX_NAME && name.matches("[A-Za-z0-9_]+");
    }

    public static boolean validTag(String tag) {
        return tag != null && tag.length() >= MIN_TAG && tag.length() <= MAX_TAG && tag.matches("[A-Za-z0-9]+");
    }

    // ---- membership --------------------------------------------------------------------------------

    public Optional<Club> clubOf(UUID player) {
        return clubs.values().stream().filter(club -> club.members.containsKey(player)).findFirst();
    }

    public Optional<Club> find(String name) {
        return Optional.ofNullable(name == null ? null : clubs.get(key(name)));
    }

    public List<Club> all() {
        return List.copyOf(clubs.values());
    }

    public Result create(UUID owner, String ownerName, String name, String tag, long now) {
        if (!validName(name)) return Result.BAD_NAME;
        if (!validTag(tag)) return Result.BAD_TAG;
        if (clubOf(owner).isPresent()) return Result.ALREADY_IN_CLUB;
        if (clubs.containsKey(key(name))) return Result.NAME_TAKEN;
        clubs.put(key(name), new Club(name, tag.toUpperCase(Locale.ROOT), owner, ownerName, now));
        return Result.OK;
    }

    /** Adds a player (the caller has checked they were invited). */
    public Result join(UUID player, String playerName, String clubName) {
        Optional<Club> club = find(clubName);
        if (club.isEmpty()) return Result.NO_SUCH_CLUB;
        if (clubOf(player).isPresent()) return Result.ALREADY_IN_CLUB;
        if (club.get().members.size() >= MAX_MEMBERS) return Result.FULL;
        club.get().members.put(player, playerName);
        return Result.OK;
    }

    /** Leaving: the owner's leaving passes the club to its longest-standing member, or ends it if they were alone. */
    public Result leave(UUID player) {
        Optional<Club> found = clubOf(player);
        if (found.isEmpty()) return Result.NOT_IN_CLUB;
        Club club = found.get();
        // The claim record stays: leaving and rejoining (or joining another club) must not pay the week twice.
        club.members.remove(player);
        if (club.members.isEmpty()) {
            clubs.remove(key(club.name));
        } else if (club.owner.equals(player)) {
            club.owner = club.members.keySet().iterator().next();
        }
        return Result.OK;
    }

    public Result disband(UUID player) {
        Optional<Club> found = clubOf(player);
        if (found.isEmpty()) return Result.NOT_IN_CLUB;
        if (!found.get().owner.equals(player)) return Result.NOT_OWNER;
        clubs.remove(key(found.get().name));
        return Result.OK;
    }

    public Result kick(UUID owner, UUID target) {
        Optional<Club> found = clubOf(owner);
        if (found.isEmpty()) return Result.NOT_IN_CLUB;
        if (!found.get().owner.equals(owner)) return Result.NOT_OWNER;
        if (!found.get().members.containsKey(target) || owner.equals(target)) return Result.NOT_A_MEMBER;
        found.get().members.remove(target);
        return Result.OK;
    }

    public Result setBanner(UUID owner, String banner) {
        Optional<Club> found = clubOf(owner);
        if (found.isEmpty()) return Result.NOT_IN_CLUB;
        if (!found.get().owner.equals(owner)) return Result.NOT_OWNER;
        String color = banner == null ? "" : banner.toLowerCase(Locale.ROOT);
        boolean prestige = PRESTIGE_BANNERS.contains(color) && found.get().unlockedBanners.contains(color);
        if (!BANNERS.contains(color) && !prestige) return Result.BAD_BANNER;
        found.get().banner = color;
        return Result.OK;
    }

    // ---- clears and the board ------------------------------------------------------------------------

    /** The player's best regional clear score so far (0 for none). */
    public int bestOf(UUID player) {
        return best.getOrDefault(player, 0);
    }

    /**
     * A regional cycle clear by {@code player}: raises their best, and, if they are in a club, counts toward its weekly goal.
     * Returns whether this clear met the goal (true only on the clear that reaches it).
     */
    public boolean recordClear(UUID player, int score, String currentWeek) {
        return recordClear(player, score, currentWeek, 0);
    }

    /**
     * As above, in a season ({@code 0} for none): the clear also raises the player's best for that season, which the season's club board
     * is made of. A clear outside any season (the off-season, or seasons off) counts for the all-time score and the week only.
     */
    public boolean recordClear(UUID player, int score, String currentWeek, int season) {
        if (season > 0 && score > seasonBestOf(season, player)) {
            seasonBests.computeIfAbsent(season, key -> new LinkedHashMap<>()).put(player, score);
        }
        if (score > bestOf(player)) best.put(player, score);
        Optional<Club> found = clubOf(player);
        if (found.isEmpty()) return false;
        Club club = found.get();
        club.roll(currentWeek);
        boolean before = club.weekClears >= WEEKLY_GOAL;
        club.weekClears++;
        return !before && club.weekClears >= WEEKLY_GOAL;
    }

    /** A club's score: the sum of its members' best regional clears. */
    public int score(Club club) {
        int total = 0;
        for (UUID member : club.members.keySet()) total += bestOf(member);
        return total;
    }

    /** The player's best regional clear within {@code season} (0 for none). */
    public int seasonBestOf(int season, UUID player) {
        return seasonBests.getOrDefault(season, Map.of()).getOrDefault(player, 0);
    }

    /** A club's score for one season: the sum of its current members' bests in that season. */
    public int seasonScore(Club club, int season) {
        int total = 0;
        for (UUID member : club.members.keySet()) total += seasonBestOf(season, member);
        return total;
    }

    /** The season's clubs best first (highest season score, then name), only those that scored, at most {@code limit}. */
    public List<Club> topForSeason(int season, int limit) {
        List<Club> ranked = new ArrayList<>();
        for (Club club : clubs.values()) if (seasonScore(club, season) > 0) ranked.add(club);
        ranked.sort(Comparator.comparingInt((Club club) -> -seasonScore(club, season)).thenComparing(club -> key(club.name)));
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    /** Forgets the per-player bests of seasons older than {@code keepFrom}; the just-finalised season stays until the next one is. */
    public void pruneSeasonBests(int keepFrom) {
        seasonBests.keySet().removeIf(season -> season < keepFrom);
    }

    /** Gives a club a prestige banner colour and a line of honour (both idempotent, so a replayed finalisation changes nothing). */
    public void honor(Club club, String prestigeBanner, String honor) {
        if (prestigeBanner != null && PRESTIGE_BANNERS.contains(prestigeBanner)) club.unlockedBanners.add(prestigeBanner);
        if (honor != null && !club.honors.contains(honor)) club.honors.add(honor);
    }

    /** The clubs best first (highest score, then name), at most {@code limit}. */
    public List<Club> top(int limit) {
        List<Club> ranked = new ArrayList<>(clubs.values());
        ranked.sort(Comparator.comparingInt((Club club) -> -score(club)).thenComparing(club -> key(club.name)));
        return ranked.subList(0, Math.min(limit, ranked.size()));
    }

    /** The week's progress with the week rolled forward first, so a new week reads as nothing done. */
    public int weekClears(Club club, String currentWeek) {
        club.roll(currentWeek);
        return club.weekClears;
    }

    /** One member claims the weekly reward, once a week, once the goal is met. */
    public Result claim(UUID player, String currentWeek) {
        Optional<Club> found = clubOf(player);
        if (found.isEmpty()) return Result.NOT_IN_CLUB;
        Club club = found.get();
        club.roll(currentWeek);
        if (club.weekClears < WEEKLY_GOAL) return Result.GOAL_NOT_MET;
        // One claim a week per player, wherever they were a member when they made it.
        for (Club other : clubs.values()) {
            if (other.weekKey.equals(currentWeek) && other.claimed.contains(player)) return Result.ALREADY_CLAIMED;
        }
        return club.claimed.add(player) ? Result.OK : Result.ALREADY_CLAIMED;
    }

    /** Adds to a club's week directly (an operator seam for tests). */
    public boolean addWeekClears(Club club, int count, String currentWeek) {
        club.roll(currentWeek);
        boolean before = club.weekClears >= WEEKLY_GOAL;
        club.weekClears += count;
        return !before && club.weekClears >= WEEKLY_GOAL;
    }

    // ---- storage -----------------------------------------------------------------------------------

    /** Adds a loaded club (the store's way in). */
    public void restoreClub(Club club) {
        clubs.put(key(club.name), club);
    }

    public void restoreBest(UUID player, int score) {
        best.put(player, score);
    }

    public Map<UUID, Integer> bests() {
        return Map.copyOf(best);
    }

    public Map<Integer, Map<UUID, Integer>> seasonBests() {
        Map<Integer, Map<UUID, Integer>> copy = new LinkedHashMap<>();
        seasonBests.forEach((season, bests) -> copy.put(season, Map.copyOf(bests)));
        return copy;
    }

    public void restoreSeasonBest(int season, UUID player, int score) {
        seasonBests.computeIfAbsent(season, key -> new LinkedHashMap<>()).put(player, score);
    }

    public void clear() {
        clubs.clear();
        best.clear();
        seasonBests.clear();
    }
}
