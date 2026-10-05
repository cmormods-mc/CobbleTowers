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
public record HallSeason(int number, String name, Optional<String> spotlight, LocalDate endedOn, List<Board> boards,
                         List<Club> clubs) {

    /** A club on the season's frozen club board (P36c): who they were and what they scored that season. */
    public record Club(String name, String tag, String banner, int score, List<String> members) {
        public Club {
            members = List.copyOf(members);
        }
    }

    /** A season recorded before clubs were part of the Hall, or one with no club that scored. */
    public HallSeason(int number, String name, Optional<String> spotlight, LocalDate endedOn, List<Board> boards) {
        this(number, name, spotlight, endedOn, boards, List.of());
    }

    /** A board's frozen top entries; {@code key} is the board's identity with its season part empty. */
    public record Board(Key key, List<Entry> entries) {
        public Board {
            entries = List.copyOf(entries);
        }
    }

    public HallSeason {
        boards = List.copyOf(boards);
        clubs = List.copyOf(clubs);
    }
}
