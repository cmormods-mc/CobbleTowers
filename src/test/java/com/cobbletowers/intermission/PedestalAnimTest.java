package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PedestalAnimTest {

    private static final UUID RUN = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    @AfterEach
    void reset() {
        PedestalAnim.clear();
    }

    @Test
    void stepsRunInDueOrderAndOnlyOnceDue() {
        List<String> log = new ArrayList<>();
        PedestalAnim.at(10, RUN, s -> log.add("b"));
        PedestalAnim.at(5, RUN, s -> log.add("a"));
        PedestalAnim.at(5, RUN, s -> log.add("a2"));
        PedestalAnim.run(null, 4);
        assertTrue(log.isEmpty());
        PedestalAnim.run(null, 5);
        assertEquals(List.of("a", "a2"), log);
        PedestalAnim.run(null, 99);
        assertEquals(List.of("a", "a2", "b"), log);
        assertTrue(PedestalAnim.idle());
    }

    @Test
    void cancelDropsOnlyThatRunsSteps() {
        List<String> log = new ArrayList<>();
        PedestalAnim.at(1, RUN, s -> log.add("run"));
        PedestalAnim.at(1, OTHER, s -> log.add("other"));
        PedestalAnim.cancel(RUN);
        PedestalAnim.run(null, 1);
        assertEquals(List.of("other"), log);
    }

    @Test
    void aFailingStepDoesNotStopTheRest() {
        List<String> log = new ArrayList<>();
        PedestalAnim.at(1, RUN, s -> {
            throw new IllegalStateException("boom");
        });
        PedestalAnim.at(1, RUN, s -> log.add("after"));
        PedestalAnim.run(null, 1);
        assertEquals(List.of("after"), log);
    }

    @Test
    void aStepMayScheduleAnotherForALaterTick() {
        List<String> log = new ArrayList<>();
        PedestalAnim.at(1, RUN, s -> {
            log.add("first");
            PedestalAnim.at(3, RUN, t -> log.add("second"));
        });
        PedestalAnim.run(null, 1);
        assertEquals(List.of("first"), log);
        PedestalAnim.run(null, 3);
        assertEquals(List.of("first", "second"), log);
    }
}
