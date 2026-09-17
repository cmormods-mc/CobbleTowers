package com.cobbletowers.api.rules;

/**
 * The rules a run is played under, as an addon may read them.
 *
 * <p>Level bounds live here and nowhere else (TDS #45: do not scatter level formulas through
 * encounter code). The enemy level is computed once per encounter from the registered parties and
 * then clamped by {@link #minEnemyLevel()} and {@link #maxEnemyLevel()}.
 */
public interface RulesetView {

    int minEnemyLevel();

    int maxEnemyLevel();

    /** How many Pokemon each player registers. Six in v1 (TDS #42, test-gated). */
    int registeredPartySize();

    /** Standard mode requires every registered Pokemon to be battle-ready at entry (TDS #46). */
    boolean requiresBattleReadyParty();

    /** How many battle items may be used per encounter (TDS #47). */
    int itemActionBudget();

    /** Whether battle damage is carried back to the real party between floors (TDS #16). */
    boolean carriesHealthBetweenFloors();

    /** Whether spent PP is carried back to the real party between floors. */
    boolean carriesPpBetweenFloors();
}
