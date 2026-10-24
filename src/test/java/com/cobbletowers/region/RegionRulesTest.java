package com.cobbletowers.region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.region.RegionRules.Stat;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which region a team or tower stands for, and what counts as away. */
class RegionRulesTest {

    @Test
    @DisplayName("a CobbleTeams team name maps to a region, ignoring case, and anything else to none")
    void teams() {
        assertEquals(Optional.of("tideforge"), RegionRules.regionOfTeam("Tideforge"));
        assertEquals(Optional.of("duskvale"), RegionRules.regionOfTeam(" duskvale "));
        assertEquals(Optional.empty(), RegionRules.regionOfTeam("ember"));
        assertEquals(Optional.empty(), RegionRules.regionOfTeam(null));
    }

    @Test
    @DisplayName("a tower's region is its theme, then its own id; neutral towers have none")
    void towers() {
        assertEquals(Optional.of("rootvale"), RegionRules.regionOfTower("rootvale", Optional.empty()));
        assertEquals(Optional.of("duskvale"), RegionRules.regionOfTower("custom", Optional.of("duskvale")));
        assertEquals(Optional.empty(), RegionRules.regionOfTower("neutral", Optional.empty()));
    }

    @Test
    @DisplayName("away needs two different known regions")
    void away() {
        assertTrue(RegionRules.isAway(Optional.of("tideforge"), Optional.of("rootvale")));
        assertFalse(RegionRules.isAway(Optional.of("tideforge"), Optional.of("tideforge")));
        assertFalse(RegionRules.isAway(Optional.of("tideforge"), Optional.empty()), "a neutral tower is nobody's");
        assertFalse(RegionRules.isAway(Optional.empty(), Optional.of("rootvale")), "no region, no conquest");
    }

    @Test
    @DisplayName("totals are judged per member, so the bigger region does not win by size alone")
    void totalsPerMember() {
        // 1311 clears over 41 members (32.0 each) against 1204 over 29 (41.5 each)
        assertEquals(1, RegionRules.leader(Stat.CLEARS, 1311, 1204, 41, 29));
        assertEquals(-1, RegionRules.leader(Stat.CLEARS, 1204, 1311, 29, 41));
        assertEquals(0, RegionRules.leader(Stat.AWAY, 100, 50, 20, 10), "100 over 20 and 50 over 10 are both 5 each");
        assertEquals(-1, RegionRules.leader(Stat.TRIALS, 9, 4, 0, 5), "with no members on a side the raw totals decide");
    }

    @Test
    @DisplayName("a depth record is one player's feat, so size does not matter")
    void depthRecord() {
        assertEquals(-1, RegionRules.leader(Stat.ASCENSION, 5, 3, 1, 100));
        assertEquals(1, RegionRules.leader(Stat.ASCENSION, 3, 5, 100, 1));
        assertEquals(0, RegionRules.leader(Stat.ASCENSION, 4, 4, 10, 20));
    }

    @Test
    @DisplayName("a time record with none set loses to any real time")
    void fastest() {
        assertEquals(-1, RegionRules.leader(Stat.FASTEST, 600_000, 700_000, 10, 10));
        assertEquals(1, RegionRules.leader(Stat.FASTEST, 0, 700_000, 10, 10));
        assertEquals(0, RegionRules.leader(Stat.FASTEST, 0, 0, 10, 10));
    }
}
