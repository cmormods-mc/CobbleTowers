package com.cobbletowers.battle.cobblemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter.Binding;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The adapter's battle indexes stay in step: one register, one forget, nothing left behind. */
class BattleIndexTest {

    private static final UUID RUN = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PLAYER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    private static Binding binding(boolean exhibition) {
        return new Binding(RUN, PLAYER, 3, ResourceLocation.parse("cobblemon:eevee"), UUID.randomUUID(),
                1_000L, exhibition);
    }

    private final BattleIndex index = new BattleIndex();

    private UUID add(Binding bound) {
        UUID battle = UUID.randomUUID();
        index.register(battle, bound, 20_000);
        return battle;
    }

    @Test
    @DisplayName("forget removes a battle from every index and returns its binding")
    void forgetClearsEverything() {
        Binding bound = binding(false);
        UUID battle = add(bound);
        index.turns.advanced(battle, 1);
        index.stalls.observe(battle, 1, true, 0);
        assertEquals(1, index.byBattle.size());
        assertEquals(1, index.byRun.get(RUN).size());
        assertEquals(1, index.lastActivity.size());
        assertEquals(1, index.probeAt.size());

        assertEquals(bound, index.forget(battle));

        assertTrue(index.byBattle.isEmpty());
        assertTrue(index.byRun.isEmpty());
        assertTrue(index.lastActivity.isEmpty());
        assertTrue(index.probeAt.isEmpty());
        assertEquals(0, index.turns.size());
        assertEquals(0, index.stalls.size());
    }

    @Test
    @DisplayName("forgetting twice is harmless: the second caller gets nothing to resolve")
    void forgetIsIdempotent() {
        UUID battle = add(binding(false));
        assertTrue(index.forget(battle) != null);
        assertNull(index.forget(battle));
    }

    @Test
    @DisplayName("forgetting one of a run's battles keeps the others")
    void siblingsSurvive() {
        UUID first = add(binding(false));
        add(binding(true));
        index.forget(first);
        assertEquals(1, index.byRun.get(RUN).size());
        assertEquals(1, index.byBattle.size());
        assertTrue(index.byBattle.values().iterator().next().exhibition());
        assertEquals(0, index.probeAt.size());
    }

    @Test
    @DisplayName("a turn that moves is progress; the first reading and a repeat are not")
    void turnTracker() {
        TurnTracker turns = new TurnTracker();
        UUID battle = UUID.randomUUID();
        assertFalse(turns.advanced(battle, 1));
        assertFalse(turns.advanced(battle, 1));
        assertTrue(turns.advanced(battle, 2));
        assertFalse(turns.advanced(battle, 2));
        turns.forget(battle);
        assertEquals(0, turns.size());
    }
}
