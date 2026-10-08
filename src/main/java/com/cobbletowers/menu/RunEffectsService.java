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

    /**
     * Tells every online participant what the run now carries, or that it carries nothing because it is over. Called when a
     * run is saved with a change to its modifiers, relics or floor, so the overlay is never behind the run.
     */
    public static void pushToParticipants(net.minecraft.server.MinecraftServer server, PersistedRun run) {
        try {
            RunEffectsPayload payload = run.state().isLive() ? build(run) : CLEARED;
            for (var participant : run.participants()) {
                ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player != null && ServerPlayNetworking.canSend(player, RunEffectsPayload.TYPE)) ServerPlayNetworking.send(player, payload);
            }
        } catch (RuntimeException ex) {
            com.cobbletowers.TowerLog.errorOnce("run-effects-push", "Could not push a run's effects to its players", ex);
        }
    }

    /** Whether saving {@code next} over {@code previous} changed anything the overlay shows. */
    public static boolean changed(PersistedRun previous, PersistedRun next) {
        if (previous == null) return true;
        var before = previous.modifiers();
        var after = next.modifiers();
        return previous.floorIndex() != next.floorIndex() || previous.state().isLive() != next.state().isLive()
                || !before.accumulated().equals(after.accumulated()) || !before.lockedIn().equals(after.lockedIn())
                || !before.relics().equals(after.relics());
    }

    /** Floor 0 means no run: the client forgets what it had. */
    private static final RunEffectsPayload CLEARED = new RunEffectsPayload(0, 0, List.of());

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
