package com.cobbletowers.season;

import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * One finished season in the Hall of Fame (P36a): who won what, frozen at the season's end and never edited.
 *
 * @param spotlight the season's spotlight region as an id string, empty if it had none
 * @param boards    every board that had entries, with its top ten
 */
public record HallSeason(int number, String name, Optional<String> spotlight, LocalDate endedOn, List<Board> boards) {

    /** A board's frozen top entries; {@code key} is the board's identity with its season part empty. */
    public record Board(Key key, List<Entry> entries) {
        public Board {
            entries = List.copyOf(entries);
        }
    }

    public HallSeason {
        boards = List.copyOf(boards);
    }
}
