package com.cobbletowers.mastery;

import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import java.util.Optional;

/**
 * Where a result is posted (P36a): the all-time board always, and the current season's board too while a season is
 * active.
 */
public final class Boards {

    private Boards() {}

    /**
     * Whether a board is ever seasonal: the per-run boards. Clears is lifetime and a trial board has its own period.
     */
    public static boolean seasonal(Board board) {
        return board == Board.ASCENSION || board == Board.SPEED || board == Board.DIFFICULTY;
    }

    /**
     * Offers {@code entry} to the all-time key and, for a seasonal board in an active season, to that season's key.
     * @param seasonId the active season's id ({@code s3}), empty when none
     * @return the all-time rank (as {@link TowerLeaderboardStore#offer})
     */
    public static int offerBoth(TowerLeaderboardStore store, Key key, Entry entry, Optional<String> seasonId) {
        int rank = store.offer(key.inSeason(""), entry);
        if (seasonal(key.board()) && seasonId.isPresent()) store.offer(key.inSeason(seasonId.get()), entry);
        return rank;
    }
}
