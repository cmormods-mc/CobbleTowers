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
    @DisplayName("the level is the mean of the two highest, rounded half up")
    void topTwoRoundedHalfUp() {
        assertEquals(OptionalInt.of(51), TowerLevelSnapshot.of(List.of(50, 51)));
        assertEquals(OptionalInt.of(51), TowerLevelSnapshot.of(List.of(50, 51, 10)));
        assertEquals(OptionalInt.of(75), TowerLevelSnapshot.of(List.of(10, 50, 100, 20)));
    }

    @Test
    @DisplayName("low-level padding, on the bench or in the team, cannot lower the enemy")
    void paddingDoesNotLowerIt() {
        List<Integer> levels = new ArrayList<>(Collections.nCopies(4, 10));  // a padded bench
        levels.add(100);
        levels.add(100);
        assertEquals(OptionalInt.of(100), TowerLevelSnapshot.of(levels));
        // a strong player teamed with weak ones: the strong player's two best still set it
        List<Integer> team = new ArrayList<>(Collections.nCopies(12, 20));
        team.add(100);
        team.add(98);
        assertEquals(OptionalInt.of(99), TowerLevelSnapshot.of(team));
    }

    @Test
    @DisplayName("a single Pokemon sets the level alone, order does not matter")
    void singleAndOrder() {
        assertEquals(OptionalInt.of(64), TowerLevelSnapshot.of(List.of(64)));
        assertEquals(TowerLevelSnapshot.of(List.of(30, 90, 60)), TowerLevelSnapshot.of(List.of(90, 60, 30)));
        assertEquals(OptionalInt.of(75), TowerLevelSnapshot.of(List.of(30, 90, 60)));
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
