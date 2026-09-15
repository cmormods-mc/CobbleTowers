package com.cobbletowers.encounter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TowerLevelSnapshotTest {

    @Test
    @DisplayName("the level is the mean, rounded half up")
    void roundsHalfUp() {
        assertEquals(OptionalInt.of(51), TowerLevelSnapshot.of(List.of(50, 51)));
        assertEquals(OptionalInt.of(50), TowerLevelSnapshot.of(List.of(50, 50, 51)));
    }

    @Test
    @DisplayName("every Pokemon counts once, so a full party outweighs a single strong one")
    void flatAcrossPokemonNotPlayers() {
        List<Integer> levels = new ArrayList<>(Collections.nCopies(6, 10));  // one player's six
        levels.add(100);                                                  // another player's one
        // (60 + 100) / 7 = 22.86 -- a per-player mean would have said 55.
        assertEquals(OptionalInt.of(23), TowerLevelSnapshot.of(levels));
    }

    @Test
    @DisplayName("the result stays within levels a Pokemon can have")
    void clamps() {
        assertEquals(OptionalInt.of(TowerLevelSnapshot.MAX_LEVEL), TowerLevelSnapshot.of(List.of(250, 250)));
        assertEquals(OptionalInt.of(TowerLevelSnapshot.MIN_LEVEL), TowerLevelSnapshot.of(List.of(0, 0)));
    }

    @Test
    @DisplayName("nothing to average gives no level, and missing entries are skipped")
    void emptyAndNulls() {
        assertTrue(TowerLevelSnapshot.of(List.of()).isEmpty());
        assertTrue(TowerLevelSnapshot.of(null).isEmpty());
        assertTrue(TowerLevelSnapshot.of(Arrays.asList(null, null)).isEmpty());
        assertEquals(OptionalInt.of(40), TowerLevelSnapshot.of(Arrays.asList(40, null)));
    }
}
