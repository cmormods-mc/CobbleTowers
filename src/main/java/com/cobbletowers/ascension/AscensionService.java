package com.cobbletowers.ascension;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ForcedModifiers;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerAscensionStore;
import com.cobbletowers.runtime.TowerRuns;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What happens when a run crosses into a new Ascension (P30): the team's records move up, one modifier is forced on the
 * run, and everybody is told. Called once, right after the transition that confirms the first floor of a cycle.
 *
 * <p>A direct start does not come through here: {@code RunFactory} gives that run its forced modifiers at creation, and the
 * players already hold the record that let them start.
 */
public final class AscensionService {

    private AscensionService() {}

    /** The run has just confirmed its next floor; if that floor begins an Ascension, apply it. */
    public static void onFloorConfirmed(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return;
        PersistedRun run = found.get();
        TowerContent content = TowerDefinitionRegistry.content();
        TowerDefinition tower = content.towers().get(run.towerId());
        if (tower == null || !tower.ascension()) return;
        int ascension = tower.ascensionOf(run.floorIndex());
        if (ascension < 1 || tower.contentFloor(run.floorIndex()) != 1) return;

        TowerAscensionStore records = TowerAscensionStore.get(server);
        boolean moved = false;
        for (PersistedParticipant participant : run.participants()) {
            moved |= records.record(participant.playerId(), run.towerId(), ascension);
        }
        if (moved) records.checkpoint(server);

        Optional<ModifierDefinition> forced = ForcedModifiers.draw(
                content.draftablePool(run.towerId(), run.floorIndex()),
                DraftService.held(content, run.modifiers()), run.seed(), ascension);
        forced.ifPresent(modifier -> TowerRuns.save(server,
                run.withModifiers(run.modifiers().accumulating(modifier.id()), now), true));
        TowerLog.info("Run {} ascends to Ascension {}{}", runId, ascension,
                forced.map(modifier -> ", forced " + modifier.id()).orElse(", with nothing left to force"));

        PersistedRun current = TowerRuns.get(runId).orElse(run);
        for (PersistedParticipant participant : current.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null) continue;
            player.sendSystemMessage(Component.literal("Ascension " + ascension + "! The tower grows harder.")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            forced.ifPresent(modifier -> player.sendSystemMessage(Component.literal("  Forced on your run: "
                    + modifier.displayName()).withStyle(ChatFormatting.RED)));
        }
    }
}
