package com.cobbletowers.menu;

import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.network.RunEffectsPayload;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.reward.RewardBankService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Pushes what a run carries to a player's in-fight overlay (P41). Pure apart from the send. */
public final class RunEffectsService {

    private RunEffectsService() {}

    /** Sends the player their run's modifiers and relics; silent when the client has no overlay or the run is gone. */
    public static void send(ServerPlayer player, UUID runId) {
        try {
            if (!ServerPlayNetworking.canSend(player, RunEffectsPayload.TYPE)) return;
            TowerRuns.get(runId).map(RunEffectsService::build).ifPresent(payload -> ServerPlayNetworking.send(player, payload));
        } catch (RuntimeException ex) {
            com.cobbletowers.TowerLog.errorOnce("run-effects", "Could not send a run's effects to a player", ex);
        }
    }

    /** The payload for a run: grouped modifiers then relics, each with only its benefit and cost lines. */
    public static RunEffectsPayload build(PersistedRun run) {
        var content = TowerDefinitionRegistry.content();
        var state = run.modifiers();
        List<RunEffectsPayload.Item> items = new ArrayList<>();
        Map<ResourceLocation, List<Integer>> floors = new LinkedHashMap<>();
        for (int i = 0; i < state.accumulated().size(); i++) {
            floors.computeIfAbsent(state.accumulated().get(i), k -> new ArrayList<>()).add(state.modifierFloor(i));
        }
        for (var entry : floors.entrySet()) {
            ModifierDefinition m = content.modifier(entry.getKey()).orElse(null);
            if (m == null) continue;
            items.add(item(m, false, entry.getValue().size(), entry.getValue().stream().filter(f -> f > 0).findFirst().orElse(0)));
        }
        for (int i = 0; i < state.relics().size(); i++) {
            ModifierDefinition relic = content.modifier(state.relics().get(i)).orElse(null);
            if (relic != null) items.add(item(relic, true, 1, state.relicFloor(i)));
        }
        if (items.size() > RunEffectsPayload.MAX_ITEMS) items = new ArrayList<>(items.subList(0, RunEffectsPayload.MAX_ITEMS));
        return new RunEffectsPayload(run.floorIndex(), RewardBankService.riskBonusPercent(content, run), items);
    }

    private static RunEffectsPayload.Item item(ModifierDefinition m, boolean relic, int count, int floor) {
        List<String> lines = new ArrayList<>();
        boolean first = true;
        for (String line : ModifierMenuText.lines(m)) {
            // The first marked line is the risk payout, which the overlay's header already totals.
            boolean marked = line.startsWith(ModifierMenuText.GOOD) || line.startsWith(ModifierMenuText.BAD);
            if (marked && first) {
                first = false;
                continue;
            }
            if (marked && lines.size() < RunEffectsPayload.MAX_LINES) lines.add(cut(line));
        }
        return new RunEffectsPayload.Item(cut(m.displayName()), relic, count, floor, lines);
    }

    private static String cut(String text) {
        return text.length() <= RunEffectsPayload.MAX_TEXT ? text : text.substring(0, RunEffectsPayload.MAX_TEXT - 3) + "...";
    }
}
