package com.cobbletowers.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cobbletowers.network.ActionRate.Verdict;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ActionRateTest {

    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long T0 = 1_000_000L;

    @Test
    @DisplayName("a hand clicking about five times a second is never held back")
    void humanRate() {
        ActionRate rate = new ActionRate();
        for (int i = 0; i < 600; i++) assertEquals(Verdict.ALLOW, rate.check(PLAYER, T0 + i * 200L));
    }

    @Test
    @DisplayName("a burst beyond the allowance is dropped, and said once")
    void flood() {
        ActionRate rate = new ActionRate();
        for (int i = 0; i < ActionRate.BURST; i++) assertEquals(Verdict.ALLOW, rate.check(PLAYER, T0));
        assertEquals(Verdict.DROP_AND_SAY, rate.check(PLAYER, T0));
        assertEquals(Verdict.DROP, rate.check(PLAYER, T0 + 1));
        assertEquals(Verdict.DROP, rate.check(PLAYER, T0 + 2));
    }

    @Test
    @DisplayName("tokens come back, so a flood that stops is forgiven")
    void recovers() {
        ActionRate rate = new ActionRate();
        for (int i = 0; i < ActionRate.BURST; i++) rate.check(PLAYER, T0);
        assertEquals(Verdict.DROP_AND_SAY, rate.check(PLAYER, T0));
        assertEquals(Verdict.ALLOW, rate.check(PLAYER, T0 + 100));
    }

    @Test
    @DisplayName("one player's flood does not slow another")
    void independent() {
        ActionRate rate = new ActionRate();
        for (int i = 0; i < 100; i++) rate.check(PLAYER, T0);
        assertEquals(Verdict.ALLOW, rate.check(OTHER, T0));
    }

    @Test
    @DisplayName("a clock that steps backwards cannot mint tokens")
    void clockBack() {
        ActionRate rate = new ActionRate();
        for (int i = 0; i < ActionRate.BURST; i++) rate.check(PLAYER, T0);
        assertEquals(Verdict.DROP_AND_SAY, rate.check(PLAYER, T0 - 60_000));
    }

    @Test
    @DisplayName("forgetting a player frees their bucket")
    void forgets() {
        ActionRate rate = new ActionRate();
        rate.check(PLAYER, T0);
        rate.forget(PLAYER);
        assertEquals(0, rate.size());
    }
}
