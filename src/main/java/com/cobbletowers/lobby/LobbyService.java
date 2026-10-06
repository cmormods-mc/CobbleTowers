package com.cobbletowers.lobby;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.battle.cobblemon.PartyReader;
import com.cobbletowers.battle.cobblemon.PartyStorage;
import com.cobbletowers.definition.RulesetDefinition;
import com.cobbletowers.runtime.PlaylistRules;
import com.cobbletowers.persistence.RunOptions;
import com.cobbletowers.definition.RulesetResolver;
import com.cobbletowers.definition.PlaylistRegistry;
import com.cobbletowers.definition.PlaylistDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.RegistrationStatePayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.PartyValidation;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import com.cobbletowers.runtime.RunFactory;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import com.cobbletowers.storage.PartyArrangement;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.storage.PartyJournalService;
import com.cobbletowers.storage.RentalPartyService;
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
        // DISCONNECT fires on a Netty thread; the lobbies are plain server-thread state.
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            com.cobbletowers.runtime.ServerThread.run(server, () -> {
                try {
                    onDisconnect(server, id);
                } catch (RuntimeException ex) {
                    TowerLog.error("Could not tidy the lobby of a disconnecting player", ex);
                }
            });
        });
    }

    /**
     * A player dropped. A host going ends their team (nobody else can start it, and it would otherwise sit in memory with
     * its drafts); anyone else just steps out. Leaving a run is {@code TowerPresence}'s business, not this waiting room's.
     */
    static void onDisconnect(MinecraftServer server, UUID id) {
        TowerLobby hosted = BY_HOST.get(id);
        if (hosted != null) {
            dissolve(server, hosted, "The host left, so the team was ended.");
            return;
        }
        Optional<TowerLobby> found = lobbyOf(id);
        if (found.isEmpty()) return;
        TowerLobby lobby = found.get();
        lobby.remove(id);
        RentalDraftService.clear(id);
        ServerPlayer gone = server.getPlayerList().getPlayer(id);
        broadcast(server, lobby, (gone == null ? "A player" : name(gone)) + " left the team.");
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
        RentalDraftService.clearAll(lobby);
        lobby.selectTower(towerId);
        broadcast(server, lobby, "");
        return "Tower set to " + towerName(towerId) + ".";
    }

    public static String invite(MinecraftServer server, ServerPlayer host, ServerPlayer target) {
        TowerLobby lobby = BY_HOST.get(host.getUUID());
        if (lobby == null) return inRun(host.getUUID()) ? inRunMessage() : "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        if (target.getUUID().equals(host.getUUID())) return "You are already on your own team.";
        if (inRun(target.getUUID())) return name(target) + " is already in a tower run.";
        if (lobbyOf(target.getUUID()).isPresent()) return name(target) + " is already in a team.";

        int teamCap = lobby.playlist().flatMap(PlaylistRegistry::get).map(PlaylistDefinition::maxPlayers).orElse(0);
        if (teamCap > 0 && 1 + lobby.invitees().size() >= teamCap) {
            return playlistName(lobby.playlist()) + " allows " + teamCap + " player" + (teamCap == 1 ? "" : "s") + ".";
        }
        TowerLobby.Result result = lobby.invite(target.getUUID(), System.currentTimeMillis());
        if (result == TowerLobby.Result.FULL) return "A team is at most " + TowerLobby.MAX_PLAYERS + " players.";
        if (result != TowerLobby.Result.OK) return name(target) + " is already invited.";

        target.sendSystemMessage(Component.literal(name(host) + " invited you to " + towerName(lobby.tower())
                + ". Use /tower accept " + name(host) + " (expires in 3 minutes)."));
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
        // A rental run needs every member's own draft, so put their packs in front of them now.
        RentalDraftService.prompt(server, lobby, player);
        return "You joined the team for " + towerName(lobby.tower()) + ".";
    }

    public static String decline(MinecraftServer server, ServerPlayer player, UUID hostId) {
        TowerLobby lobby = BY_HOST.get(hostId);
        if (lobby == null || lobby.decline(player.getUUID()) != TowerLobby.Result.OK) return "You have no such invite.";
        broadcast(server, lobby, name(player) + " declined.");
        sendState(server, player, null, "");
        return "Declined.";
    }

    /**
     * What to say to a player with no team. A team is only the waiting room: it is gone the moment the run
     * starts, so a player already in a run must be told that, not that they have no team.
     */
    public static String noTeam(ServerPlayer player) {
        return inRun(player.getUUID()) ? inRunMessage() : "You are not in a team.";
    }

    private static String inRunMessage() {
        return "You are in a tower run already (the team waiting room closes once it starts). Use /tower to see where you are.";
    }

    /** A host leaving ends the team; anyone else just steps out of it. */
    public static String leave(MinecraftServer server, ServerPlayer player) {
        Optional<TowerLobby> found = lobbyOf(player.getUUID());
        if (found.isEmpty()) {
            // In a live run, leaving is leaving the run (the same as `runs leave`); the exit sweep then takes them
            // home. Only after that does the "the run is over, take me home" path apply.
            if (inRun(player.getUUID())
                    && com.cobbletowers.runtime.TowerPresence.leave(server, player, System.currentTimeMillis())) {
                return "You left the run. You will be taken home in a moment.";
            }
            String home = com.cobbletowers.runtime.RunExitService.leaveNow(server, player);
            return home != null ? home : noTeam(player);
        }
        TowerLobby lobby = found.get();
        if (lobby.host().equals(player.getUUID())) {
            dissolve(server, lobby, name(player) + " ended the team.");
            return "You ended the team.";
        }
        lobby.remove(player.getUUID());
        RentalDraftService.clear(player.getUUID());
        sendState(server, player, null, "");
        broadcast(server, lobby, name(player) + " left the team.");
        return "You left the team.";
    }

    /** The host confirms; the countdown runs and {@link #tick} launches the run when it ends. */
    /**
     * The host picks today's trial (P32): the tower, playlist, mutators, level and seed come from the trial, so the lobby is
     * fixed to it until the host chooses a tower again.
     */
    public static String selectTrial(MinecraftServer server, ServerPlayer player,
                                     com.cobbletowers.definition.TrialPoolDefinition.Kind kind) {
        if (inRun(player.getUUID())) return "You are already in a tower run.";
        Optional<TowerLobby> existing = lobbyOf(player.getUUID());
        if (existing.isPresent() && !existing.get().host().equals(player.getUUID())) {
            return "Only the host can choose the trial. Leave first to host your own.";
        }
        if (existing.isPresent() && existing.get().counting()) return "The run is already starting.";
        Optional<com.cobbletowers.trial.TrialSchedule.Instance> found = com.cobbletowers.trial.TrialService.current(kind);
        if (found.isEmpty()) return "There is no " + kind.name().toLowerCase(java.util.Locale.ROOT) + " trial on this server.";
        com.cobbletowers.trial.TrialSchedule.Instance trial = found.get();
        if (!TowerDefinitionRegistry.content().towers().containsKey(trial.entry().tower())) {
            return "Today's trial uses " + trial.entry().tower() + ", which is not loaded.";
        }
        PlaylistDefinition playlist = trial.entry().playlist().flatMap(PlaylistRegistry::get).orElse(null);
        TowerLobby lobby = existing.orElseGet(() -> {
            TowerLobby created = new TowerLobby(player.getUUID(), trial.entry().tower());
            BY_HOST.put(player.getUUID(), created);
            return created;
        });
        if (playlist != null && playlist.maxPlayers() > 0 && lobby.team().size() + lobby.pending().size() > playlist.maxPlayers()) {
            return playlist.displayName() + " allows " + playlist.maxPlayers() + " player(s); the team is larger.";
        }
        RentalDraftService.clearAll(lobby);
        lobby.selectTower(trial.entry().tower());
        lobby.setPlaylist(trial.entry().playlist());
        lobby.setTrial(Optional.of(trial));
        broadcast(server, lobby, "");
        boolean scored = com.cobbletowers.trial.TrialService.wouldBeScored(server, new ArrayList<>(lobby.team()), trial.id());
        return trial.title() + " selected: " + trial.floors() + " floors on " + towerName(trial.entry().tower())
                + (playlist == null ? "" : ", " + playlist.displayName())
                + (scored ? ". This will be your scored attempt: it cannot be retried. Use /tower confirm, then /tower start."
                        : ". Someone on the team has already used this trial's attempt, so this run is practice and will not post. Use /tower confirm, then /tower start.");
    }

    /** The code of the last run each player started, for {@code /tower play code}. In memory only. */
    private static final Map<UUID, String> LAST_CODES = new java.util.concurrent.ConcurrentHashMap<>();

    /** What {@code /tower play code} says with no argument: the code of the run you last started. */
    public static String lastCode(ServerPlayer player) {
        String code = LAST_CODES.get(player.getUUID());
        return code == null ? "You have not started a run since the server last restarted." : "Your last run code: " + code;
    }

    /**
     * The host sets the lobby to a run code (P35): its tower, mode, starting Ascension and seed. Refused when the code is
     * not valid, names something not loaded, or asks for an Ascension the team has not reached.
     */
    public static String useCode(MinecraftServer server, ServerPlayer player, String raw) {
        java.util.Optional<com.cobbletowers.runcode.RunCode.Decoded> decoded = com.cobbletowers.runcode.RunCode.decode(raw);
        if (decoded.isEmpty()) return "That is not a valid run code. Check it was copied whole (it looks like CT1-...).";
        com.cobbletowers.runcode.RunCode.Decoded code = decoded.get();
        if (!TowerDefinitionRegistry.content().towers().containsKey(code.tower())) {
            return "That code is for " + code.tower() + ", which this server does not have.";
        }
        if (code.playlist().isPresent() && PlaylistRegistry.get(code.playlist().get()).isEmpty()) {
            return "That code uses the mode " + code.playlist().get() + ", which this server does not have.";
        }
        String selected = select(server, player, code.tower());
        TowerLobby lobby = BY_HOST.get(player.getUUID());
        if (lobby == null || !lobby.tower().equals(code.tower())) return selected;
        if (lobby.counting()) return "The run is already starting.";
        lobby.setTrial(java.util.Optional.empty());
        String mode = setPlaylist(server, player, code.playlist().map(id -> id.toString()).orElse("standard"));
        if (code.playlist().isPresent() && lobby.playlist().isEmpty()) return mode;
        if (code.ascension() > 0) {
            String ascended = setAscension(server, player, code.ascension());
            if (lobby.ascension() != code.ascension()) return ascended;
        }
        lobby.setSeed(java.util.Optional.of(code.seed()));
        broadcast(server, lobby, "");
        return "Run code accepted: " + towerName(code.tower()) + ", " + playlistName(lobby.playlist())
                + (code.ascension() > 0 ? ", Ascension " + code.ascension() : "")
                + ". You will get the same opponents, bosses and draft cards as the player who shared it. Use /tower confirm, then /tower start.";
    }

    /** The host picks the playlist (P32): its house rules apply to the whole team. {@code "standard"} clears it. */
    public static String setPlaylist(MinecraftServer server, ServerPlayer player, String raw) {
        TowerLobby lobby = BY_HOST.get(player.getUUID());
        if (lobby == null) return lobbyOf(player.getUUID()).isPresent() ? "Only the host can choose the mode." : "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        if (lobby.trial().isPresent()) return "A trial fixes the mode. Choose a tower to leave the trial.";
        if (raw.equalsIgnoreCase("standard") || raw.isBlank()) {
            RentalDraftService.clearAll(lobby);
            lobby.setPlaylist(java.util.Optional.empty());
            broadcast(server, lobby, "");
            return "Mode: Standard.";
        }
        ResourceLocation id = raw.contains(":") ? ResourceLocation.tryParse(raw)
                : ResourceLocation.fromNamespaceAndPath("cobbletowers", raw.toLowerCase(java.util.Locale.ROOT));
        java.util.Optional<PlaylistDefinition> playlist = id == null ? java.util.Optional.empty() : PlaylistRegistry.get(id);
        if (playlist.isEmpty()) return "No mode called " + raw + ". Try: standard, "
                + PlaylistRegistry.all().stream().map(p -> p.id().getPath()).collect(java.util.stream.Collectors.joining(", ")) + ".";
        if (playlist.get().maxPlayers() > 0 && lobby.team().size() + lobby.pending().size() > playlist.get().maxPlayers()) {
            return playlist.get().displayName() + " allows " + playlist.get().maxPlayers() + " player(s); the team is larger.";
        }
        RentalDraftService.clearAll(lobby);
        lobby.setPlaylist(java.util.Optional.of(id));
        broadcast(server, lobby, "");
        return "Mode: " + playlist.get().displayName() + ". " + playlist.get().description()
                + (playlist.get().rental() ? " Confirm the mode (/tower confirm) when you are happy with it, and everyone's packs open."
                        : " Confirm the mode (/tower confirm) when you are happy with it.");
    }

    static String playlistName(java.util.Optional<ResourceLocation> playlist) {
        return playlist.flatMap(PlaylistRegistry::get).map(PlaylistDefinition::displayName).orElse("Standard");
    }

    /**
     * The deepest Ascension the whole team may start at (P30): the lowest record on the team, since nobody should be thrown
     * into depth they have not earned.
     */
    public static int maxAscension(MinecraftServer server, TowerLobby lobby) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(lobby.tower());
        if (tower == null || !tower.ascension()) return 0;
        com.cobbletowers.persistence.TowerAscensionStore records = com.cobbletowers.persistence.TowerAscensionStore.get(server);
        int deepest = Integer.MAX_VALUE;
        for (UUID id : lobby.team()) deepest = Math.min(deepest, records.recordOf(id, lobby.tower()));
        return deepest == Integer.MAX_VALUE ? 0 : deepest;
    }

    /** The host chooses the Ascension the team starts at, up to what every member has reached. */
    public static String setAscension(MinecraftServer server, ServerPlayer player, int level) {
        TowerLobby lobby = BY_HOST.get(player.getUUID());
        if (lobby == null) return lobbyOf(player.getUUID()).isPresent() ? "Only the host can choose the Ascension."
                : "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        if (lobby.trial().isPresent()) return "A trial always starts from the first floor. Choose a tower to leave the trial.";
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(lobby.tower());
        if (tower == null || !tower.ascension()) return towerName(lobby.tower()) + " does not ascend.";
        int max = maxAscension(server, lobby);
        if (level > max) {
            return "Your team can start at Ascension " + max + " at most (a start needs everyone to have reached it).";
        }
        lobby.setAscension(level);
        broadcast(server, lobby, "");
        return level == 0 ? "Starting from the first floor." : "Starting at Ascension " + level + ".";
    }

    public static String start(MinecraftServer server, ServerPlayer host) {
        TowerLobby lobby = BY_HOST.get(host.getUUID());
        if (lobby == null) return inRun(host.getUUID()) ? inRunMessage() : "Pick a tower first.";
        if (lobby.counting()) return "The run is already starting.";
        if (!lobby.modeConfirmed()) {
            return "Confirm the mode first (/tower confirm)" + (RentalDraftService.isRentalLobby(lobby)
                    ? ", then everyone drafts and readies up." : ".");
        }
        if (RentalDraftService.isRentalLobby(lobby)) {
            List<String> waiting = new ArrayList<>();
            for (UUID id : lobby.team()) {
                if (lobby.isReady(id)) continue;
                ServerPlayer member = server.getPlayerList().getPlayer(id);
                waiting.add(member == null ? id.toString().substring(0, 8) : name(member));
            }
            if (!waiting.isEmpty()) {
                return "Everyone must ready up (after drafting) before the run can start. Waiting on: " + String.join(", ", waiting) + ".";
            }
        }
        lobby.beginCountdown(System.currentTimeMillis(), COUNTDOWN_MILLIS);
        broadcast(server, lobby, "Starting " + towerName(lobby.tower()) + " in "
                + COUNTDOWN_MILLIS / 1000 + " seconds. Anyone may /tower leave to drop out.");
        return "Starting.";
    }

    /**
     * The host settles on the mode (P33). For a rental mode that is the moment everyone's draft opens: until then the mode may still
     * be cycled, and nobody is shown a draft for a mode that might change.
     */
    public static String confirmMode(MinecraftServer server, ServerPlayer player) {
        TowerLobby lobby = BY_HOST.get(player.getUUID());
        if (lobby == null) return lobbyOf(player.getUUID()).isPresent() ? "Only the host can confirm the mode." : noTeam(player);
        if (lobby.counting()) return "The run is already starting.";
        if (lobby.modeConfirmed()) return "The mode is already confirmed.";
        lobby.confirmMode();
        boolean rental = RentalDraftService.isRentalLobby(lobby);
        broadcast(server, lobby, name(player) + " confirmed the mode" + (rental ? ": draft your teams." : "."));
        RentalDraftService.promptAll(server, lobby);
        return "Mode confirmed.";
    }

    /**
     * Ready up or stand down (P33). Only a rental lobby has a ready-up, and only a finished draft can be readied; a countdown
     * already running is not interrupted by either.
     */
    public static String ready(MinecraftServer server, ServerPlayer player, boolean value) {
        Optional<TowerLobby> found = lobbyOf(player.getUUID());
        if (found.isEmpty()) return noTeam(player);
        TowerLobby lobby = found.get();
        if (!RentalDraftService.isRentalLobby(lobby)) return "There is nothing to ready up for in this mode.";
        if (lobby.counting()) return "The run is already starting.";
        if (!lobby.modeConfirmed()) return "Waiting for the host to confirm the mode.";
        if (value && !RentalDraftService.draftOf(player.getUUID()).filter(RentalDraft::complete).isPresent()) {
            RentalDraftService.prompt(server, lobby, player);
            return "Finish your draft first: you can only ready up once your team is drafted.";
        }
        lobby.setReady(player.getUUID(), value);
        broadcast(server, lobby, name(player) + (value ? " is ready." : " is no longer ready."));
        return value ? "You are ready." : "You are not ready.";
    }

    /** Re-sends the lobby to everyone in it (without opening anything), after something changed that it shows. */
    public static void refresh(MinecraftServer server, TowerLobby lobby) {
        broadcast(server, lobby, "");
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
                if (!lobby.announce(left)) continue;
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
        RulesetDefinition ruleset = RulesetResolver.forTower(content, tower, lobby.playlist());
        if (ruleset == null) {
            broadcast(server, lobby, "That tower is no longer available.");
            return;
        }

        // Starting early: the unanswered are dropped, and so is anyone who has gone offline.
        for (UUID id : lobby.pending()) lobby.remove(id);
        List<UUID> players = new ArrayList<>();
        Map<UUID, List<UUID>> parties = new LinkedHashMap<>();
        Map<UUID, List<UUID>> locks = new LinkedHashMap<>();
        Map<UUID, RentalDraft.Team> rentalTeams = new LinkedHashMap<>();
        boolean rentalRun = RentalDraftService.isRentalLobby(lobby);
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
            if (rentalRun) {
                // A rental run (P33): the party is the drafted team, so the player's own Pokemon are not judged at all.
                Optional<RentalDraft> draft = RentalDraftService.draftOf(id).filter(RentalDraft::complete);
                if (draft.isEmpty()) {
                    problems.add(name(player) + " has not finished their draft (/tower draft)");
                    continue;
                }
                if (!RentalPartyService.canMakeRoom(player)) {
                    problems.add(name(player) + " needs free box space for their own Pokemon while they play");
                    continue;
                }
                RentalDraft.Team team = draft.get().finish(UUID::randomUUID);
                rentalTeams.put(id, team);
                players.add(id);
                parties.put(id, team.ids());
                continue;
            }
            List<UUID> picked = lobby.chosenOf(id);
            PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
            List<PartyMember> party;
            if (picked.isEmpty()) {
                party = PartyReader.members(player);
            } else {
                party = new ArrayList<>();
                for (UUID pokemon : picked) snapshot.member(pokemon).ifPresent(party::add);
                if (party.size() != picked.size()) {
                    problems.add(name(player) + " chose a Pokemon that is no longer theirs");
                    continue;
                }
            }
            PartyValidation.Result checked = PartyValidation.validate(party, ruleset);
            checked.problems().forEach(problem -> problems.add(name(player) + " " + problem));
            // The playlist's clauses (P32) apply to what would be registered.
            lobby.playlist().flatMap(PlaylistRegistry::get).ifPresent(playlist ->
                    PlaylistRules.problems(party.stream().filter(member -> checked.registered().contains(member.id())).toList(),
                            playlist).forEach(problem -> problems.add(name(player) + ": " + problem)));
            if (!picked.isEmpty()) {
                // Dry-run before anyone is moved: a plan that cannot be carried out is refused up front.
                PartyArrangement.Plan plan = PartyJournalService.dryRun(player, checked.registered());
                if (!plan.ok()) problems.add(name(player) + " cannot register that party (" + plan.failure() + ")");
                locks.put(id, checked.registered());
            }
            players.add(id);
            parties.put(id, checked.registered());
        }
        // Re-checked at the start: someone who accepted after the host chose may not have reached it.
        int startAt = lobby.ascension();
        if (startAt > 0) {
            com.cobbletowers.persistence.TowerAscensionStore records = com.cobbletowers.persistence.TowerAscensionStore.get(server);
            for (UUID id : players) {
                if (records.recordOf(id, lobby.tower()) < startAt) {
                    ServerPlayer shallow = server.getPlayerList().getPlayer(id);
                    problems.add((shallow == null ? "A teammate" : name(shallow)) + " has not reached Ascension " + startAt);
                }
            }
        }
        // The entry item (decided 2026-10-05): one tower key per player, checked here and taken only once the run
        // has really started (below), so a launch that fails costs nothing.
        boolean keyRun = com.cobbletowers.economy.TowerKeyPolicy.costsKey(
                com.cobbletowers.economy.TowerKeys.required(), lobby.trial().isPresent(), rentalRun);
        if (keyRun) {
            for (UUID id : players) {
                ServerPlayer keyholder = server.getPlayerList().getPlayer(id);
                if (keyholder == null || com.cobbletowers.economy.TowerKeys.count(keyholder) < 1) {
                    problems.add((keyholder == null ? "A teammate" : name(keyholder)) + " needs a Tower Key");
                }
            }
        }
        if (!problems.isEmpty()) {
            TowerLog.info("The lobby of {} could not start {}: {}", lobby.host(), lobby.tower(), String.join("; ", problems));
            broadcast(server, lobby, "Cannot start: " + String.join("; ", problems));
            return;
        }

        long seed = lobby.seed().orElseGet(() -> server.overworld().getRandom().nextLong());
        RunOptions options = RunOptions.of(lobby.playlist());
        List<ResourceLocation> trialModifiers = List.of();
        if (lobby.trial().isPresent()) {
            // A trial (P32): the same seed, mutators and enemy level for everybody, limited to its floors, from floor 1.
            com.cobbletowers.trial.TrialSchedule.Instance trial = lobby.trial().get();
            boolean scored = com.cobbletowers.trial.TrialService.wouldBeScored(server, players, trial.id());
            options = new RunOptions(lobby.playlist(), Optional.of(trial.id()), trial.floors(), scored, trial.entry().enemyLevel());
            trialModifiers = trial.entry().modifiers();
            seed = trial.seed();
            startAt = 0;
        }
        Optional<PersistedRun> created = RunFactory.create(content, lobby.tower(), players, parties, startAt, options,
                trialModifiers, seed, now);
        if (created.isEmpty()) {
            broadcast(server, lobby, "That tower is no longer available.");
            return;
        }
        UUID runId = created.get().runId();
        TowerRuns.save(server, created.get(), true);
        if (lobby.trial().isEmpty()) {
            // Printed for every ordinary run (P35): a friend who enters it plays the same run.
            String code = com.cobbletowers.runcode.RunCode.encode(lobby.tower(), lobby.playlist(), startAt, seed);
            for (UUID id : players) LAST_CODES.put(id, code);
            broadcast(server, lobby, "Run code: " + code + " (share it; /tower play code <code> starts the same run)");
            TowerLog.info("Run {} started with code {}", runId, code);
        }

        // Move registered Pokemon into the party. Journaled and flushed first, per player, so a failure or
        // a crash anywhere from here on can be undone; a failure here undoes the ones already done.
        for (UUID id : players) {
            RentalDraft.Team lent = rentalTeams.get(id);
            List<UUID> target = locks.get(id);
            if (target == null && lent == null) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            // A rental run (P33) lends the drafted team through the same journal; anything else moves the player's own Pokemon.
            Object result = lent != null
                    ? (player == null ? RentalPartyService.Lock.FAILED : RentalPartyService.lock(server, runId, player, lent))
                    : (player == null ? PartyJournalService.Lock.FAILED : PartyJournalService.lockIn(server, runId, player, target));
            if (result == PartyJournalService.Lock.FAILED || result == PartyJournalService.Lock.NOT_OWNED
                    || result == PartyJournalService.Lock.NO_ROOM || result == PartyJournalService.Lock.TOO_MANY
                    || result == RentalPartyService.Lock.FAILED || result == RentalPartyService.Lock.NO_ROOM
                    || result == RentalPartyService.Lock.IN_BATTLE) {
                RunTransitionService.apply(server, runId, RunEvent.ABANDON_REQUESTED, now);
                restoreParties(server, players);
                broadcast(server, lobby, "Cannot start: " + (player == null ? "a player went offline"
                        : name(player) + " could not have their party arranged (" + result + ")") + ". Try again.");
                return;
            }
        }

        RunTransitionService.Outcome validated = RunLifecycle.validateParty(server, runId, now,
                id -> Optional.ofNullable(server.getPlayerList().getPlayer(id)).map(PartyReader::members));
        if (!(validated instanceof RunTransitionService.Move move) || move.next().state() != RunState.ALLOCATING_INSTANCE) {
            // Pre-checked above, so this is a party that changed in the gap. The run is abandoned by
            // the rejection; the team is left to try again.
            restoreParties(server, players);
            broadcast(server, lobby, "Cannot start: a party changed while the run was being set up. Try again.");
            return;
        }

        RunTransitionService.Outcome allocated = RunLifecycle.allocateInstance(server, runId, now);
        if (!(allocated instanceof RunTransitionService.Move)) {
            abandonParked(server, runId, now);
            restoreParties(server, players);
            broadcast(server, lobby, "Cannot start: no tower space is free right now. Try again in a moment.");
            return;
        }

        if (!openFloor(server, runId, now)) {
            // The run is parked by openFloor; it holds the team until recovery or an operator settles it.
            dissolve(server, lobby, "The floor could not be opened; the run is parked for recovery.");
            return;
        }
        // The attempt is spent only now that the run has really started: a launch that failed above costs nothing.
        if (keyRun) {
            for (UUID id : players) {
                ServerPlayer keyholder = server.getPlayerList().getPlayer(id);
                // The run is open and cannot be undone here, so a key that vanished in the gap is logged, not enforced.
                if (keyholder != null && com.cobbletowers.economy.TowerKeys.take(keyholder)) {
                    keyholder.sendSystemMessage(Component.literal("Used 1 Tower Key."));
                } else {
                    TowerLog.warn("Run {} started but player {} had no Tower Key left to take", runId, id);
                }
            }
        }
        TowerRuns.get(runId).ifPresent(started -> {
            com.cobbletowers.trial.TrialService.recordLaunch(server, started);
            if (started.options().isTrial()) {
                String note = started.options().scored() ? "This is your scored attempt." : "This run is practice and will not post.";
                for (UUID id : players) {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    if (player != null) player.sendSystemMessage(Component.literal(note));
                }
            }
        });
        dissolve(server, lobby, "");
        TowerLog.info("Lobby of {} started run {} on {}", players.size(), runId, lobby.tower());
    }

    /** PREPARING -> FLOOR_READY, then the floor itself; parks the run on failure. */
    private static boolean openFloor(MinecraftServer server, UUID runId, long now) {
        if (!(RunTransitionService.apply(server, runId, RunEvent.PREPARATION_COMPLETE, now)
                instanceof RunTransitionService.Move)) {
            RunTransitionService.apply(server, runId, RunEvent.TECHNICAL_FAILURE, now);
            return false;
        }
        return RunLifecycle.beginFloor(server, runId, now);
    }

    /** Puts every listed player's Pokemon back at once, after a start that did not happen. */
    private static void restoreParties(MinecraftServer server, List<UUID> players) {
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) PartyJournalService.restoreNow(server, player);
        }
    }

    /** Frees a team held by a run that never got a cell, so they can retry (a parked run still holds them). */
    private static void abandonParked(MinecraftServer server, UUID runId, long now) {
        RunTransitionService.apply(server, runId, RunEvent.RECOVERY_ABANDONED, now);
    }

    // ---- choosing what to register (P18) -----------------------------------------------------

    /** Adds the Pokemon to the player's registration, or takes it off if it is already there. */
    public static String choose(MinecraftServer server, ServerPlayer player, UUID pokemon) {
        Optional<TowerLobby> found = lobbyOf(player.getUUID());
        if (found.isEmpty()) return inRun(player.getUUID()) ? inRunMessage() : "Join or start a team first.";
        TowerLobby lobby = found.get();
        if (lobby.counting()) return "The run is already starting.";
        if (!PartyStorage.snapshot(player).entries().containsKey(pokemon)) return "That Pokemon is not yours.";

        List<UUID> now = new ArrayList<>(lobby.chosenOf(player.getUUID()));
        String reply;
        if (now.remove(pokemon)) {
            reply = "Removed from your registration.";
        } else if (now.size() >= registerLimit(lobby)) {
            return "You can register at most " + registerLimit(lobby) + " for this tower.";
        } else {
            now.add(pokemon);
            reply = "Registered.";
        }
        lobby.setChosen(player.getUUID(), now);
        broadcast(server, lobby, "");
        sendRegistration(server, player, reply, false);
        return reply;
    }

    public static String clearChoice(MinecraftServer server, ServerPlayer player) {
        Optional<TowerLobby> found = lobbyOf(player.getUUID());
        if (found.isEmpty()) return noTeam(player);
        if (found.get().counting()) return "The run is already starting.";
        found.get().setChosen(player.getUUID(), List.of());
        broadcast(server, found.get(), "");
        sendRegistration(server, player, "Registration cleared: your current party will be used.", false);
        return "Registration cleared.";
    }

    /** How many Pokemon the lobby's tower lets a player register. */
    private static int registerLimit(TowerLobby lobby) {
        TowerContent content = TowerDefinitionRegistry.content();
        TowerDefinition tower = content.towers().get(lobby.tower());
        RulesetDefinition ruleset = RulesetResolver.forTower(content, tower, lobby.playlist());
        return ruleset == null ? PartyArrangement.PARTY_SIZE : ruleset.registeredPartySize();
    }

    /** The chooser: everything the player owns, what they have chosen, and the limit. */
    public static void sendRegistration(MinecraftServer server, ServerPlayer player, String message, boolean open) {
        PartyStorage.Snapshot snapshot = PartyStorage.snapshot(player);
        List<RegistrationStatePayload.Entry> entries = new ArrayList<>();
        for (PartyStorage.Entry entry : snapshot.entries().values()) {
            String where = entry.slot().isParty() ? "Party " + (entry.slot().index() + 1) : "Box " + (entry.slot().index() + 1);
            entries.add(new RegistrationStatePayload.Entry(entry.id(), entry.name(), entry.level(), entry.fainted(), where));
        }
        Optional<TowerLobby> lobby = lobbyOf(player.getUUID());
        TowerNetworking.sendRegistration(player, new RegistrationStatePayload(entries,
                lobby.map(found -> found.chosenOf(player.getUUID())).orElse(List.of()),
                lobby.map(LobbyService::registerLimit).orElse(PartyArrangement.PARTY_SIZE), message, open));
    }

    /** One line per Pokemon the player owns, for an operator or a test: place, id, name and level. */
    public static List<String> describePokemon(ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        java.util.Set<UUID> lent = new java.util.HashSet<>(com.cobbletowers.battle.cobblemon.RentalStorage.tagged(player));
        for (PartyStorage.Entry entry : PartyStorage.snapshot(player).entries().values()) {
            lines.add((entry.slot().isParty() ? "party " + entry.slot().index() : "box " + entry.slot().index() + "/" + entry.slot().sub())
                    + " " + entry.id() + " " + entry.name() + " Lv" + entry.level() + (entry.fainted() ? " fainted" : "")
                    + (lent.contains(entry.id()) ? " RENTAL" : ""));
        }
        return lines;
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
        RentalDraftService.clearAll(lobby);
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
        TowerNetworking.sendPlayState(player, playState(server, player, lobby, message, open));
    }

    public static PlayStatePayload playState(MinecraftServer server, ServerPlayer player, TowerLobby lobby, String message, boolean open) {
        TowerContent content = TowerDefinitionRegistry.content();
        List<PlayStatePayload.Tower> towers = content.towers().values().stream()
                .map(tower -> new PlayStatePayload.Tower(tower.id(), tower.displayName())).toList();
        List<Integer> levels = PartyReader.members(player).stream().map(PartyMember::level).toList();

        int role = 0;
        String selected = "";
        String hostName = "";
        List<PlayStatePayload.Member> members = new ArrayList<>();
        int countdown = -1;
        PlayStatePayload.Depth depth = PlayStatePayload.Depth.none();
        PlayStatePayload.Modes modes = PlayStatePayload.Modes.none();
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
                members.add(new PlayStatePayload.Member(label, lobby.responseOf(id).orElse(null) == TowerLobby.Response.ACCEPTED,
                        lobby.isReady(id)));
            }
            countdown = lobby.secondsLeft(now);
            TowerDefinition chosen = content.towers().get(lobby.tower());
            depth = new PlayStatePayload.Depth(lobby.ascension(), maxAscension(server, lobby), chosen != null && chosen.ascension());
            List<String> modeIds = new ArrayList<>();
            List<String> modeNames = new ArrayList<>();
            for (PlaylistDefinition playlist : PlaylistRegistry.all()) {
                modeIds.add(playlist.id().getPath());
                modeNames.add(playlist.displayName());
            }
            modes = new PlayStatePayload.Modes(modeIds, modeNames, lobby.playlist().map(ResourceLocation::getPath).orElse(""),
                    RentalDraftService.isRentalLobby(lobby),
                    new PlayStatePayload.Readiness(lobby.modeConfirmed(), RentalDraftService.draftOf(me).filter(RentalDraft::complete).isPresent(),
                            lobby.isReady(me), lobby.isReady(lobby.host())));
        }
        return new PlayStatePayload(towers,
                new PlayStatePayload.Lobby(role, selected, hostName, members, countdown,
                        new PlayStatePayload.Options(depth, modes)), levels, message, open);
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
