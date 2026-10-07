package com.cobbletowers.intermission;

import com.cobbletowers.battle.cobblemon.PartyReader;
import com.cobbletowers.definition.PlaylistDefinition;
import com.cobbletowers.definition.PlaylistRegistry;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import com.cobbletowers.runtime.PlaylistRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps a playlist's party clauses true for the whole run (P32): before a floor opens each participant's live party
 * is rechecked, and the floor waits, with the reason, until it complies.
 */
public final class PlaylistGuard {

    private PlaylistGuard() {}

    /**
     * Why the run's party no longer satisfies its playlist; empty when it does, or when the run has no clauses to
     * keep.
     */
    public static List<String> problems(MinecraftServer server, PersistedRun run) {
        Optional<PlaylistDefinition> playlist = run.options().playlist().flatMap(PlaylistRegistry::get);
        if (playlist.isEmpty() || !playlist.get().party().any()) return List.of();
        List<String> problems = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (!participant.state().isInRun()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null) continue;
            // The whole live party, not only what was registered: anything in the party can be sent out to fight.
            List<PartyMember> party = PartyReader.members(player);
            for (String problem : PlaylistRules.problems(party, playlist.get())) {
                problems.add(player.getGameProfile().getName() + ": " + problem);
            }
        }
        return List.copyOf(problems);
    }
}
