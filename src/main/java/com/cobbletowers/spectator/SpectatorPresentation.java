package com.cobbletowers.spectator;

import com.cobbletowers.ServerState;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.network.SpectatorPanelPayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who a spectator is following and the camera that follows them (TDS #25). A camera choice, not run state (TDS #7):
 * held in memory for the spectator's stay, never in {@link PersistedRun}. Callers validate how a player came to be
 * spectating.
 */
public final class SpectatorPresentation {

    private static final Map<UUID, UUID> FOLLOWING = new ConcurrentHashMap<>();

    static {
        ServerState.onStop(FOLLOWING::clear);
    }

    private SpectatorPresentation() {}

    /** A player just lost their fight: follow the first teammate still able to fight, if any. */
    public static void startSpectating(MinecraftServer server, PersistedRun run, ServerPlayer spectator) {
        List<UUID> teammates = activeTeammates(run, spectator.getUUID());
        if (!teammates.isEmpty()) follow(server, run, spectator, teammates.get(0));
    }

    /** The keybind's request: whoever is next or previous among the still-active teammates. */
    public static void cycle(PersistedRun run, ServerPlayer spectator, boolean next) {
        MinecraftServer server = spectator.getServer();
        if (server == null) return;
        List<UUID> teammates = activeTeammates(run, spectator.getUUID());
        if (teammates.isEmpty()) return;

        UUID current = FOLLOWING.get(spectator.getUUID());
        int index = current == null ? -1 : teammates.indexOf(current);
        int nextIndex = index < 0 ? 0 : Math.floorMod(index + (next ? 1 : -1), teammates.size());
        follow(server, run, spectator, teammates.get(nextIndex));
    }

    /** A followed teammate's own fight just resolved: refresh every spectator watching them. */
    public static void refreshFollowersOf(MinecraftServer server, PersistedRun run, UUID teammateId) {
        ServerPlayer teammate = server.getPlayerList().getPlayer(teammateId);
        if (teammate == null) return;
        SpectatorPanelPayload payload = panelFor(run, teammate);
        for (Map.Entry<UUID, UUID> entry : FOLLOWING.entrySet()) {
            if (!entry.getValue().equals(teammateId)) continue;
            ServerPlayer spectator = server.getPlayerList().getPlayer(entry.getKey());
            if (spectator != null) TowerNetworking.sendSpectatorPanel(spectator, payload);
        }
    }

    /** The revival call site: stop overriding the camera. The next floor's own entry teleport does the rest. */
    public static void stopSpectating(ServerPlayer spectator) {
        if (FOLLOWING.remove(spectator.getUUID()) == null) return;
        spectator.setCamera(spectator);
    }

    private static void follow(MinecraftServer server, PersistedRun run, ServerPlayer spectator, UUID teammateId) {
        ServerPlayer teammate = server.getPlayerList().getPlayer(teammateId);
        if (teammate == null) return;
        FOLLOWING.put(spectator.getUUID(), teammateId);
        spectator.setCamera(teammate);
        TowerNetworking.sendSpectatorPanel(spectator, panelFor(run, teammate));
    }

    private static List<UUID> activeTeammates(PersistedRun run, UUID exceptPlayerId) {
        List<UUID> ids = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.playerId().equals(exceptPlayerId) && participant.state().canFight()) {
                ids.add(participant.playerId());
            }
        }
        return List.copyOf(ids);
    }

    static SpectatorPanelPayload panelFor(PersistedRun run, ServerPlayer teammate) {
        int total = 0;
        int remaining = 0;
        for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(teammate)) {
            total++;
            if (!pokemon.isFainted()) remaining++;
        }
        return new SpectatorPanelPayload(teammate.getGameProfile().getName(), remaining, total,
                run.floorIndex(), run.state().name());
    }
}
