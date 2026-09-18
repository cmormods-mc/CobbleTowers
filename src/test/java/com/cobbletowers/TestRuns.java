package com.cobbletowers;

import com.cobbletowers.api.tower.MilestoneKind;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.api.tower.participant.ParticipantState;
import com.cobbletowers.definition.DefinitionKey;
import com.cobbletowers.definition.EncounterPoolDefinition;
import com.cobbletowers.definition.FloorDefinition;
import com.cobbletowers.definition.MilestoneDefinition;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.RunFactory;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Runs and content for tests, so each test says what it is about rather than how to build one. */
public final class TestRuns {

    public static final ResourceLocation TOWER = id("neutral");
    public static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final long NOW = 1_726_000_000_000L;

    private TestRuns() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    /** A run at its opening state, on floor 1, with one player and nothing committed. */
    public static PersistedRun fresh(UUID runId) {
        return fresh(runId, PLAYER, NOW);
    }

    public static PersistedRun fresh(UUID runId, UUID playerId, long updatedAt) {
        return new PersistedRun(runId, PersistedRun.SCHEMA_VERSION, TOWER, 3, "digest-abc", 2, 0, 42L,
                RunFactory.FIRST_FLOOR, RunState.CREATED,
                List.of(new PersistedParticipant(playerId, ParticipantState.joined(), List.of())),
                Optional.empty(), List.of(), updatedAt, OptionalInt.empty());
    }

    /** The same run moved to a state directly, for tests about storage rather than transitions. */
    public static PersistedRun at(UUID runId, RunState state, long updatedAt) {
        PersistedRun run = fresh(runId, PLAYER, updatedAt);
        return new PersistedRun(run.runId(), run.schemaVersion(), run.towerId(), run.towerRevision(),
                run.towerDigest(), run.rulesetRevision(), run.structureRevision(), run.seed(), run.floorIndex(),
                state, run.participants(), run.lastCheckpoint(), run.committedTransactions(), updatedAt,
                run.cell());
    }

    /** One tower, two floors, a ruleset and a boss milestone -- enough for every reference to resolve. */
    public static TowerContent content() {
        RulesetDefinition ruleset = RulesetDefinition.fromJson(id("standard"),
                JsonParser.parseString("{\"schema_version\":1,\"revision\":5}").getAsJsonObject());
        EncounterPoolDefinition pool = EncounterPoolDefinition.fromJson(id("pool"),
                JsonParser.parseString("{\"schema_version\":1,\"entries\":[{\"species\":\"cobblemon:machoke\"}]}")
                        .getAsJsonObject());

        FloorDefinition one = floor(1, Optional.empty());
        FloorDefinition two = floor(2, Optional.of(MilestoneKind.BOSS));
        // A boss milestone must name the CobbleRaids boss it reuses; the record refuses one without.
        MilestoneDefinition boss = new MilestoneDefinition(id("boss"), 2, MilestoneKind.BOSS,
                Optional.of(ResourceLocation.fromNamespaceAndPath("cobbleraids", "lucario")), true);
        TowerDefinition tower = new TowerDefinition(TOWER, "Neutral", 1, 3, id("standard"),
                List.of(one.id(), two.id()), List.of(boss.id()), Optional.empty());

        return TowerContent.of(Map.of(TOWER, tower), Map.of(one.id(), one, two.id(), two), Map.of(pool.id(), pool),
                Map.of(ruleset.id(), ruleset), Map.of(boss.id(), boss),
                Map.of(DefinitionKey.tower(TOWER), "digest-abc"));
    }

    private static FloorDefinition floor(int index, Optional<MilestoneKind> milestone) {
        return new FloorDefinition(id("floor_" + index), index, id("pool"), milestone, Optional.empty(), List.of());
    }
}
