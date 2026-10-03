package com.cobbletowers.lobby;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.battle.cobblemon.PartyReader;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.PartyValidation;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import com.cobbletowers.runtime.RunFactory;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything a player does to get into a run: pick a tower, invite a team, answer an invite, start.
 *
 * <p>Both the commands and the play screen call the same methods here, and each returns the sentence the
 * caller should show -- so the two doors cannot drift apart. Lobbies live only in memory (see
 * {@link TowerLobby}); the first persisted fact is the run, created when the countdown ends.
 *
 * <p>Starting takes the team through party validation, instance allocation and preparation to the
 * opening of floor 1, and the lobby lives until that works: a rejected party or a failed allocation
 * leaves the team together to try again. What happens <em>between</em> floors is not driven from here.
 */
public final class LobbyService {

    /** A short pause after the host confirms, so nobody is moved mid-action. */
    public static final long COUNTDOWN_MILLIS = 5_000L;

    private static final Map<UUID, TowerLobby> BY_HOST = new LinkedHashMap<>();

    private LobbyService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (BY_HOST.isEmpty()) return;
            try {
                tick(server, System.currentTimeMillis());
            } catch (RuntimeException ex) {
                TowerLog.error("A tower lobby tick failed", ex);
            }
        });
    }

    /** Memory hygiene at shutdown, like {@code TowerRuns.onServerStopped}. */
    public static void clear() {
        BY_HOST.clear();
    }

    /** One line per forming team: host, tower, how many are ready and pending, and whether it is counting down. */
    public static List<String> describeAll() {
        return BY_HOST.values().stream().map(lobby -> lobby.host() + " " + lobby.tower() + " "
                + lobby.team().size() + " ready, " + lobby.pending().size() + " pending"
                + (lobby.counting() ? ", counting down" : "")).toList();
    }

    public static Optional<TowerLobby> lobbyOf(UUID player) {
        return BY_HOST.values().stream().filter(lobby -> lobby.contains(player)).findFirst();
    }

    // ---- actions -----------------------------------------------------------------------------

    public static String select(MinecraftServer server, ServerPlayer player, ResourceLocation towerId) {
        if (!TowerDefinitionRegistry.content().towers().containsKey(towerId)) {
            return "No tower " + towerId + " is loaded.";
        }
        if (inRun(player.getUUID())) return "You are already in a tower run.";

        Optional<TowerLobby> existing = lobbyOf(player.getUUID());
        if (existing.isPresent() && !existing.get().host().equals(player.getUUID())) {
            return "Only the host can change the tower. Leave first to host your own.";
        }
        if (existing.isPresent() && existing.get().counting()) return "The run is already starting.";

        TowerLobby lobby = existing.orElseGet(() -> {
            TowerLobby created = new TowerLobby(player.getUUID(), towerId);
            BY_HOST.put(player.getUUID(), created);
            return created;
        });
        lobby.selectTower(towerId);
        broadcast(server, lobby, "");
        return "Tower set to " + towerName(towerId) + ".";
    }

    public static String invite(MinecraftServer server, ServerPlayer host, ServerPlayer target) {
        TowerLobby lobby = BY_HOST.get(host.getUUID());
        if (lobby == null) return "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        if (target.getUUID().equals(host.getUUID())) return "You are already on your own team.";
        if (inRun(target.getUUID())) return name(target) + " is already in a tower run.";
        if (lobbyOf(target.getUUID()).isPresent()) return name(target) + " is already in a team.";

        TowerLobby.Result result = lobby.invite(target.getUUID(), System.currentTimeMillis());
        if (result == TowerLobby.Result.FULL) return "A team is at most " + TowerLobby.MAX_PLAYERS + " players.";
        if (result != TowerLobby.Result.OK) return name(target) + " is already invited.";

        target.sendSystemMessage(Component.literal(name(host) + " invited you to " + towerName(lobby.tower())
                + ". Use /cobbletowers play accept " + name(host) + " (expires in 3 minutes)."));
        broadcast(server, lobby, "");
        return "Invited " + name(target) + ".";
    }

    public static String accept(MinecraftServer server, ServerPlayer player, UUID hostId) {
        TowerLobby lobby = BY_HOST.get(hostId);
        if (lobby == null) return "That team no longer exists.";
        if (inRun(player.getUUID())) return "You are already in a tower run.";
        TowerLobby.Result result = lobby.accept(player.getUUID(), System.currentTimeMillis());
        if (result != TowerLobby.Result.OK) return "That invite has lapsed.";
        broadcast(server, lobby, name(player) + " joined the team.");
        return "You joined the team for " + towerName(lobby.tower()) + ".";
    }

    public static String decline(MinecraftServer server, ServerPlayer player, UUID hostId) {
        TowerLobby lobby = BY_HOST.get(hostId);
        if (lobby == null || lobby.decline(player.getUUID()) != TowerLobby.Result.OK) return "You have no such invite.";
        broadcast(server, lobby, name(player) + " declined.");
        sendState(server, player, null, "");
        return "Declined.";
    }

    /** A host leaving ends the team; anyone else just steps out of it. */
    public static String leave(MinecraftServer server, ServerPlayer player) {
        Optional<TowerLobby> found = lobbyOf(player.getUUID());
        if (found.isEmpty()) return "You are not in a team.";
        TowerLobby lobby = found.get();
        if (lobby.host().equals(player.getUUID())) {
            dissolve(server, lobby, name(player) + " ended the team.");
            return "You ended the team.";
        }
        lobby.remove(player.getUUID());
        sendState(server, player, null, "");
        broadcast(server, lobby, name(player) + " left the team.");
        return "You left the team.";
    }

    /** The host confirms; the countdown runs and {@link #tick} launches the run when it ends. */
    public static String start(MinecraftServer server, ServerPlayer host) {
        TowerLobby lobby = BY_HOST.get(host.getUUID());
        if (lobby == null) return "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        lobby.beginCountdown(System.currentTimeMillis(), COUNTDOWN_MILLIS);
        broadcast(server, lobby, "Starting " + towerName(lobby.tower()) + " in "
                + COUNTDOWN_MILLIS / 1000 + " seconds. Anyone may /cobbletowers play leave to drop out.");
        return "Starting.";
    }

    // ---- the tick ----------------------------------------------------------------------------

    static void tick(MinecraftServer server, long now) {
        for (TowerLobby lobby : List.copyOf(BY_HOST.values())) {
            for (UUID lapsed : lobby.expire(now)) {
                ServerPlayer player = server.getPlayerList().getPlayer(lapsed);
                if (player != null) {
                    player.sendSystemMessage(Component.literal("Your invite to the team lapsed."));
                    sendState(server, player, null, "");
                }
                broadcast(server, lobby, "An invite lapsed.");
            }
            if (!lobby.counting()) continue;
            if (lobby.countdownDue(now)) {
                launch(server, lobby, now);
            } else {
                int left = lobby.secondsLeft(now);
                for (UUID id : lobby.team()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) player.displayClientMessage(Component.literal("Starting in " + left + "..."), true);
                }
            }
        }
    }

    /**
     * The countdown ended. Everything that can be checked without touching a player's world is checked
     * first, so a team that cannot start finds out before a run record exists.
     */
    private static void launch(MinecraftServer server, TowerLobby lobby, long now) {
        lobby.cancelCountdown();
        TowerContent content = TowerDefinitionRegistry.content();
        TowerDefinition tower = content.towers().get(lobby.tower());
        RulesetDefinition ruleset = tower == null ? null : content.rulesets().get(tower.rulesetId());
        if (ruleset == null) {
            broadcast(server, lobby, "That tower is no longer available.");
            return;
        }

        // Starting early: the unanswered are dropped, and so is anyone who has gone offline.
        for (UUID id : lobby.pending()) lobby.remove(id);
        List<UUID> players = new ArrayList<>();
        Map<UUID, List<UUID>> parties = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (UUID id : lobby.team()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                if (!id.equals(lobby.host())) lobby.remove(id);
                else problems.add("The host is offline.");
                continue;
            }
            if (inRun(id)) {
                problems.add(name(player) + " is already in a tower run.");
                continue;
            }
            List<PartyMember> party = PartyReader.members(player);
            PartyValidation.Result checked = PartyValidation.validate(party, ruleset);
            checked.problems().forEach(problem -> problems.add(name(player) + " " + problem));
            players.add(id);
            parties.put(id, checked.registered());
        }
        if (!problems.isEmpty()) {
            broadcast(server, lobby, "Cannot start: " + String.join("; ", problems));
            return;
        }

        long seed = server.overworld().getRandom().nextLong();
        Optional<PersistedRun> created = RunFactory.create(content, lobby.tower(), players, parties, seed, now);
        if (created.isEmpty()) {
            broadcast(server, lobby, "That tower is no longer available.");
            return;
        }
        UUID runId = created.get().runId();
        TowerRuns.save(server, created.get(), true);

        RunTransitionService.Outcome validated = RunLifecycle.validateParty(server, runId, now,
                id -> Optional.ofNullable(server.getPlayerList().getPlayer(id)).map(PartyReader::members));
        if (!(validated instanceof RunTransitionService.Move move) || move.next().state() != RunState.ALLOCATING_INSTANCE) {
            // Pre-checked above, so this is a party that changed in the gap. The run is abandoned by
            // the rejection; the team is left to try again.
            broadcast(server, lobby, "Cannot start: a party changed while the run was being set up. Try again.");
            return;
        }

        RunTransitionService.Outcome allocated = RunLifecycle.allocateInstance(server, runId, now);
        if (!(allocated instanceof RunTransitionService.Move)) {
            abandonParked(server, runId, now);
            broadcast(server, lobby, "Cannot start: no tower space is free right now. Try again in a moment.");
            return;
        }

        if (!openFloor(server, runId, now)) {
            // The run is parked by openFloor; it holds the team until recovery or an operator settles it.
            dissolve(server, lobby, "The floor could not be opened; the run is parked for recovery.");
            return;
        }
        dissolve(server, lobby, "");
        TowerLog.info("Lobby of {} started run {} on {}", players.size(), runId, lobby.tower());
    }

    /** PREPARING -> FLOOR_READY -> ENCOUNTER_ACTIVE and the floor's first round; parks the run on failure. */
    private static boolean openFloor(MinecraftServer server, UUID runId, long now) {
        if (!(RunTransitionService.apply(server, runId, RunEvent.PREPARATION_COMPLETE, now)
                instanceof RunTransitionService.Move)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return false;
        }
        if (!(RunTransitionService.apply(server, runId, RunEvent.ENCOUNTER_STARTED, now)
                instanceof RunTransitionService.Move)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return false;
        }
        try {
            if (TowerEncounters.begin(server, runId).isPresent()) return true;
        } catch (RuntimeException ex) {
            TowerLog.error("Opening floor 1 of run {} threw", runId, ex);
        }
        RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
        return false;
    }

    /** Frees a team held by a run that never got a cell, so they can retry (a parked run still holds them). */
    private static void abandonParked(MinecraftServer server, UUID runId, long now) {
        RunTransitionService.apply(server, runId, RunEvent.RECOVERY_ABANDONED, now);
    }

    // ---- state shown to the screen -------------------------------------------------------------

    public static void openScreen(MinecraftServer server, ServerPlayer player) {
        sendState(server, player, lobbyOf(player.getUUID()).orElse(null), "", true);
    }

    /** Same as {@link #openScreen}, with a sentence for the screen's message line. */
    public static void openScreenWithMessage(MinecraftServer server, ServerPlayer player, String message) {
        sendState(server, player, lobbyOf(player.getUUID()).orElse(null), message, true);
    }

    private static void dissolve(MinecraftServer server, TowerLobby lobby, String message) {
        BY_HOST.remove(lobby.host());
        List<UUID> everyone = new ArrayList<>(lobby.team());
        everyone.addAll(lobby.pending());
        for (UUID id : everyone) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            if (!message.isEmpty()) player.sendSystemMessage(Component.literal(message));
            sendState(server, player, null, message);
        }
    }

    private static void broadcast(MinecraftServer server, TowerLobby lobby, String message) {
        List<UUID> everyone = new ArrayList<>(lobby.team());
        everyone.addAll(lobby.pending());
        for (UUID id : everyone) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) continue;
            if (!message.isEmpty()) player.sendSystemMessage(Component.literal(message));
            sendState(server, player, lobby, message);
        }
    }

    private static void sendState(MinecraftServer server, ServerPlayer player, TowerLobby lobby, String message) {
        sendState(server, player, lobby, message, false);
    }

    private static void sendState(MinecraftServer server, ServerPlayer player, TowerLobby lobby, String message,
                                  boolean open) {
        TowerContent content = TowerDefinitionRegistry.content();
        List<PlayStatePayload.Tower> towers = content.towers().values().stream()
                .map(tower -> new PlayStatePayload.Tower(tower.id(), tower.displayName())).toList();
        List<Integer> levels = PartyReader.members(player).stream().map(PartyMember::level).toList();

        int role = 0;
        String selected = "";
        String hostName = "";
        List<PlayStatePayload.Member> members = new ArrayList<>();
        int countdown = -1;
        if (lobby != null) {
            long now = System.currentTimeMillis();
            selected = lobby.tower().toString();
            ServerPlayer host = server.getPlayerList().getPlayer(lobby.host());
            hostName = host == null ? lobby.host().toString().substring(0, 8) : name(host);
            UUID me = player.getUUID();
            role = me.equals(lobby.host()) ? 1
                    : lobby.responseOf(me).orElse(null) == TowerLobby.Response.ACCEPTED ? 3 : 2;
            for (UUID id : lobby.invitees()) {
                ServerPlayer other = server.getPlayerList().getPlayer(id);
                String label = other == null ? id.toString().substring(0, 8) : name(other);
                members.add(new PlayStatePayload.Member(label, lobby.responseOf(id).orElse(null) == TowerLobby.Response.ACCEPTED));
            }
            countdown = lobby.secondsLeft(now);
        }
        TowerNetworking.sendPlayState(player, new PlayStatePayload(towers,
                new PlayStatePayload.Lobby(role, selected, hostName, members, countdown), levels, message, open));
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static boolean inRun(UUID player) {
        return TowerRuns.forPlayer(player).filter(run -> !run.isRetired()).isPresent();
    }

    private static String towerName(ResourceLocation id) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(id);
        return tower == null ? id.toString() : tower.displayName();
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
