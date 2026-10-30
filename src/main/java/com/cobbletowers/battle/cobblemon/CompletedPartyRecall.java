package com.cobbletowers.battle.cobblemon;

import java.util.Objects;
import java.util.UUID;

/** A completed fight may recall its own idle entity, never an entity reused by a later fight. */
final class CompletedPartyRecall {
    enum Action { WAIT, FORGET, RECALL }

    private CompletedPartyRecall() {}

    static Action action(UUID completedBattle, UUID currentBattle, UUID expectedEntity,
                         UUID currentEntity, UUID entityBattle, boolean busy) {
        if (!Objects.equals(expectedEntity, currentEntity)) return Action.FORGET;
        if (currentBattle != null) return completedBattle.equals(currentBattle) ? Action.WAIT : Action.FORGET;
        if (entityBattle != null) return completedBattle.equals(entityBattle) ? Action.WAIT : Action.FORGET;
        return busy ? Action.WAIT : Action.RECALL;
    }
}
