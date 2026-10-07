package com.cobbletowers.events;

import com.cobbletowers.api.modifier.RiskTier;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * Something that happened in a tower run, as a plain value (P32c). The runtime emits these and subscribers
 * (contracts, the Run Report) read them without reaching into the run machinery. {@link #players()} is everyone the
 * event counts for: the whole team for a floor clear, the buyer for a purchase.
 */
public sealed interface TowerEvent {

    UUID runId();

    List<UUID> players();

    /** A floor was cleared. {@code flawless} means no player Pokemon fainted during that floor. */
    record FloorCleared(UUID runId, List<UUID> players, ResourceLocation tower, int floorIndex, long floorMillis,
                        boolean flawless, boolean solo) implements TowerEvent {
        public FloorCleared {
            players = List.copyOf(players);
        }
    }

    /** A milestone boss or champion was defeated. */
    record BossDefeated(UUID runId, List<UUID> players, ResourceLocation tower, int floorIndex, boolean solo) implements TowerEvent {
        public BossDefeated {
            players = List.copyOf(players);
        }
    }

    /** A vendor purchase went through. */
    record Purchased(UUID runId, List<UUID> players, ResourceLocation service) implements TowerEvent {
        public Purchased {
            players = List.copyOf(players);
        }
    }

    /** A modifier was drafted onto the run. */
    record Drafted(UUID runId, List<UUID> players, ResourceLocation modifier, RiskTier risk) implements TowerEvent {
        public Drafted {
            players = List.copyOf(players);
        }
    }

    /** A scored or practice trial run ended. {@code completed} means every floor was cleared. */
    record TrialFinished(UUID runId, List<UUID> players, String trialId, boolean daily, boolean scored, boolean completed,
                         int floorsCleared) implements TowerEvent {
        public TrialFinished {
            players = List.copyOf(players);
        }
    }
}
