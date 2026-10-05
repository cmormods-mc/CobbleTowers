package com.cobbletowers.echo;

import com.cobbletowers.encounter.EncounterSeed;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * When an Echo is made and when one is met (P35), with no server in sight.
 *
 * <p>An Echo is made from a top-{@value #TOP_N} regional run and met in an optional Echo Duel room at a regional milestone
 * intermission: an exhibition battle on a cloned, healed party, so it costs nothing and the run's own path (and so a run code)
 * is untouched. It never replaces a boss, never appears in a trial, and never shows a player their own team.
 */
public final class EchoPolicy {

    public static final int TOP_N = 10;
    public static final int MAX_TEAM = 6;
    /** CobbleDollars a duel winner earns: bounded by there being one duel room per regional milestone. */
    public static final int DUEL_REWARD = 100;

    /** Its own ordinal space, like every other draw. */
    static final int ORDINAL_BASE = 6_000_019;

    private EchoPolicy() {}

    /** The boards an Echo can be earned on: per-run ones that carry a run id and a place on a tower. */
    private static boolean counts(Board board) {
        return board == Board.ASCENSION || board == Board.SPEED || board == Board.DIFFICULTY;
    }

    /** Every run in the top {@value #TOP_N} of any counting board of {@code tower}. */
    public static Set<UUID> topRuns(Map<Key, List<Entry>> boards, ResourceLocation tower) {
        Set<UUID> runs = new HashSet<>();
        for (Map.Entry<Key, List<Entry>> board : boards.entrySet()) {
            if (!board.getKey().tower().equals(tower) || !counts(board.getKey().board()) || !board.getKey().allTime()) continue;
            List<Entry> entries = board.getValue();
            for (int i = 0; i < Math.min(TOP_N, entries.size()); i++) {
                if (entries.get(i).runId() != null) runs.add(entries.get(i).runId());
            }
        }
        return runs;
    }

    /** One Pokemon of an Echo. */
    public record Pick(Echo echo, String properties) {}

    /** The Echo and Pokemon for this slot, from a pool sorted by id, never one owned by someone in {@code present}. */
    public static Optional<Pick> pick(List<Echo> pool, Set<UUID> present, long runSeed, int floorIndex, int ordinal) {
        List<Echo> eligible = new ArrayList<>();
        for (Echo echo : pool) {
            if (!present.contains(echo.owner()) && !echo.team().isEmpty()) eligible.add(echo);
        }
        if (eligible.isEmpty()) return Optional.empty();
        eligible.sort(Comparator.comparing(echo -> echo.id().toString()));
        long seed = EncounterSeed.of(runSeed, floorIndex, ORDINAL_BASE + 2 * ordinal + 1);
        Echo echo = eligible.get((int) Math.floorMod(seed, eligible.size()));
        return Optional.of(new Pick(echo, echo.team().get((int) Math.floorMod(seed >> 8, echo.team().size()))));
    }

    /** The property string with its level replaced: an Echo fights at the floor's level, never its own (TDS #45). */
    public static String atLevel(String properties, int level) {
        if (properties.matches(".*(^|\\s)level=\\d+.*")) {
            return properties.replaceFirst("(^|\\s)level=\\d+", "$1level=" + level);
        }
        return properties + " level=" + level;
    }

    /** The species at the start of a property string, for a message. */
    public static String speciesOf(String properties) {
        int space = properties.indexOf(' ');
        return space < 0 ? properties : properties.substring(0, space);
    }

    /** Whether a run records and meets Echoes at all: a regional tower, outside a trial. */
    public static boolean applies(boolean regionalTower, boolean trial) {
        return regionalTower && !trial;
    }
}
