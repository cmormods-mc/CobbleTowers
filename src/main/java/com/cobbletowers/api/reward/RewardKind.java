package com.cobbletowers.api.reward;

/** What a piece of the unclaimed pool was earned for. */
public enum RewardKind {
    OPPONENT_DEFEATED,
    /** The floor's CobbleRaids boss. */
    BOSS_DEFEATED,
    FLOOR_CLEARED
}
