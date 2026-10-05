package com.cobbletowers.season;

import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a season's end writes into the Hall (P36a), as a pure function: the boards in, the Hall season out. No server, so it is
 * fully tested, and it is exactly what the operator's dry run prints.
 */
public final class SeasonFinalizer {

    /** How many entries of each board the Hall keeps. */
    public static final int HALL_DEPTH = 10;

    private SeasonFinalizer() {}

    /**
     * The Hall season for season {@code definition.number()}: the top {@value #HALL_DEPTH} of every seasonal board of that season
     * that has entries, in a stable order (board, tower, mode, playlist), solo and team apart as the boards are.
     */
    public static HallSeason plan(SeasonDefinition definition, LocalDate endedOn, Map<Key, List<Entry>> boards) {
        return plan(definition, endedOn, boards, List.of());
    }

    /** As above, with the season's club board (already ranked, best first) frozen alongside the boards. */
    public static HallSeason plan(SeasonDefinition definition, LocalDate endedOn, Map<Key, List<Entry>> boards,
                                  List<HallSeason.Club> clubs) {
        String id = SeasonSchedule.idOf(definition.number());
        List<HallSeason.Board> frozen = new ArrayList<>();
        boards.entrySet().stream()
                .filter(board -> board.getKey().season().equals(id) && !board.getValue().isEmpty())
                .sorted(Comparator.<Map.Entry<Key, List<Entry>>, Integer>comparing(board -> board.getKey().board().ordinal())
                        .thenComparing(board -> board.getKey().tower().toString())
                        .thenComparing(board -> board.getKey().mode().ordinal())
                        .thenComparing(board -> board.getKey().playlist()))
                .forEach(board -> frozen.add(new HallSeason.Board(board.getKey().inSeason(""),
                        board.getValue().subList(0, Math.min(HALL_DEPTH, board.getValue().size())))));
        return new HallSeason(definition.number(), definition.name(), definition.spotlight().map(Object::toString), endedOn, frozen,
                clubs.subList(0, Math.min(HALL_DEPTH, clubs.size())));
    }

    /** The seasonal keys older than {@code keepFrom} (the newest finished season stays live and viewable). */
    public static boolean staleSeasonKey(Key key, int keepFrom) {
        if (key.allTime()) return false;
        Optional<Integer> number = SeasonSchedule.numberOf(key.season());
        return number.isPresent() && number.get() < keepFrom;
    }
}
