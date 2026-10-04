package com.cobbletowers.intermission;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.lobby.LobbyService;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PersistedDraft;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Everything a team does between floors (P17): vote on the modifier, buy from the vendor, say they are
 * ready, and vote on cashing out. Once everybody is ready a short countdown runs and the next floor opens.
 *
 * <p>Both the commands and the intermission screen call the same methods, and each returns the sentence
 * to show. Rounds live only in memory (see {@link IntermissionRound}); a restart costs a click, never
 * progress.
 */
public final class IntermissionService {

    private static final Map<UUID, IntermissionRound> ROUNDS = new HashMap<>();

    private IntermissionService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ROUNDS.isEmpty()) return;
            try {
                tick(server, System.currentTimeMillis());
            } catch (RuntimeException ex) {
                TowerLog.error("An intermission tick failed", ex);
            }
        });
    }

    public static void clear() {
        ROUNDS.clear();
    }

    /**
     * Arrival at INTERMISSION. Called from the same place the transition service revives people and opens
     * the draft, so every road in behaves alike, and <b>before</b> banking so that a reward reveal sent
     * when a floor banks lands on top of this screen rather than under it.
     */
    public static void onArrival(MinecraftServer server, UUID runId) {
        ROUNDS.put(runId, new IntermissionRound());
        TowerRuns.get(runId).ifPresent(run -> broadcast(server, run, "", true));
    }

    // ---- actions -----------------------------------------------------------------------------

    public static String pick(MinecraftServer server, ServerPlayer player, int cardIndex) {
        Optional<PersistedRun> run = intermissionRunOf(player);
        if (run.isEmpty()) return "You are not at an intermission.";
        if (run.get().modifiers().draft().filter(draft -> !draft.resolved()).isEmpty()) return "There is nothing to vote on.";
        DraftService.vote(server, run.get().runId(), player.getUUID(), cardIndex, System.currentTimeMillis());
        return afterChange(server, run.get().runId(), "Vote recorded.");
    }

    public static String ready(MinecraftServer server, ServerPlayer player, boolean value) {
        Optional<PersistedRun> run = intermissionRunOf(player);
        if (run.isEmpty()) return "You are not at an intermission.";
        if (value && run.get().modifiers().hasOpenDraft()) return "Vote on the modifier before you ready up.";
        IntermissionRound round = ROUNDS.computeIfAbsent(run.get().runId(), id -> new IntermissionRound());
        round.setReady(player.getUUID(), value);
        return afterChange(server, run.get().runId(), value ? "You are ready." : "You are not ready.");
    }

    /** {@code true} votes to cash out, {@code false} to stay. */
    public static String cashOut(MinecraftServer server, ServerPlayer player, boolean value) {
        Optional<PersistedRun> run = intermissionRunOf(player);
        if (run.isEmpty()) return "You can only cash out at an intermission.";
        IntermissionRound round = ROUNDS.computeIfAbsent(run.get().runId(), id -> new IntermissionRound());
        round.voteCashOut(player.getUUID(), value);
        return afterChange(server, run.get().runId(), value ? "You voted to cash out." : "You voted to keep going.");
    }

    public static String vendor(MinecraftServer server, ServerPlayer player) {
        Optional<PersistedRun> run = intermissionRunOf(player);
        if (run.isEmpty()) return "The vendor is only open at an intermission.";
        TowerNetworking.sendVendorCatalog(server, player, run.get());
        return "";
    }

    public static void openScreen(MinecraftServer server, ServerPlayer player, String message) {
        intermissionRunOf(player).ifPresent(run -> send(server, run, player, message, true));
    }

    /** One line for {@code runs show}: who is ready, who is cashing out, and whether the next floor is counting down. */
    public static String describe(PersistedRun run) {
        IntermissionRound round = ROUNDS.get(run.runId());
        if (round == null) return "no intermission round";
        List<UUID> electorate = DraftService.voters(run);
        long ready = electorate.stream().filter(round::isReady).count();
        long cashing = electorate.stream().filter(round::votedCashOut).count();
        return ready + "/" + electorate.size() + " ready, " + cashing + " cashing out"
                + (round.counting() ? ", counting down" : "");
    }

    /** A plain-words status for a player at an intermission, or null when they are not at one. */
    public static String statusOf(ServerPlayer player) {
        Optional<PersistedRun> found = intermissionRunOf(player);
        if (found.isEmpty()) return null;
        PersistedRun run = found.get();
        String vote = run.modifiers().hasOpenDraft()
                ? "Modifier vote still open: /tower vote <1-3>. " : "";
        return "Floor " + run.floorIndex() + " cleared. " + vote + describe(run)
                + ". Reopen the menu any time with /tower (or the Tower Menu key).";
    }

    /** Clickable shortcuts, so a closed menu is one click from open again. */
    private static Component shortcuts(PersistedRun run) {
        MutableComponent line = Component.literal("Tower: ").withStyle(ChatFormatting.GOLD);
        line.append(button("[Open menu]", "/tower menu"));
        int cards = run.modifiers().draft().filter(d -> !d.resolved()).map(d -> d.cards().size()).orElse(0);
        for (int i = 1; i <= cards; i++) line.append(" ").append(button("[Vote " + i + "]", "/tower vote " + i));
        line.append(" ").append(button("[Ready]", "/tower ready"));
        return line;
    }

    private static Component button(String label, String command) {
        return Component.literal(label).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    /** True when {@code player} is in a run that is at an intermission. */
    public static boolean isAtIntermission(ServerPlayer player) {
        return intermissionRunOf(player).isPresent();
    }

    // ---- the decision ------------------------------------------------------------------------

    private static String afterChange(MinecraftServer server, UUID runId, String reply) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty() || found.get().state() != RunState.INTERMISSION) return reply;
        PersistedRun run = found.get();
        IntermissionRound round = ROUNDS.computeIfAbsent(runId, id -> new IntermissionRound());
        List<UUID> electorate = DraftService.voters(run);

        if (round.cashOutPasses(electorate)) {
            ROUNDS.remove(runId);
            RunTransitionService.apply(server, runId, RunEvent.CASH_OUT_CHOSEN, System.currentTimeMillis());
            notifyAll(server, run, "The team cashed out.");
            return "The team cashed out.";
        }
        String message = "";
        if (!round.counting() && !run.modifiers().hasOpenDraft() && round.allReady(electorate)) {
            round.beginCountdown(System.currentTimeMillis(), LobbyService.COUNTDOWN_MILLIS);
            message = "Everyone is ready. The next floor opens in " + LobbyService.COUNTDOWN_MILLIS / 1000 + " seconds.";
        }
        broadcast(server, run, message, false);
        return reply;
    }

    static void tick(MinecraftServer server, long now) {
        for (UUID runId : List.copyOf(ROUNDS.keySet())) {
            Optional<PersistedRun> found = TowerRuns.get(runId);
            if (found.isEmpty() || found.get().state() != RunState.INTERMISSION) {
                ROUNDS.remove(runId);
                continue;
            }
            IntermissionRound round = ROUNDS.get(runId);
            if (!round.counting()) continue;
            if (!round.countdownDue(now)) {
                int left = round.secondsLeft(now);
                if (!round.announce(left)) continue;
                for (PersistedParticipant participant : found.get().participants()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                    if (player != null) player.displayClientMessage(Component.literal("Next floor in " + left + "..."), true);
                }
                continue;
            }
            round.cancelCountdown();
            RunLifecycle.Opened result = RunLifecycle.openNextFloor(server, runId, now);
            switch (result) {
                case OPENED -> ROUNDS.remove(runId);
                case BLOCKED -> broadcast(server, found.get(), "The next floor cannot open yet.", false);
                case FAILED -> {
                    ROUNDS.remove(runId);
                    notifyAll(server, found.get(), "The next floor could not be opened; the run is parked for recovery.");
                }
            }
        }
    }

    // ---- state shown to the screen -------------------------------------------------------------

    private static Optional<PersistedRun> intermissionRunOf(ServerPlayer player) {
        return TowerRuns.forPlayer(player.getUUID()).filter(run -> run.state() == RunState.INTERMISSION);
    }

    private static void notifyAll(MinecraftServer server, PersistedRun run, String message) {
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) player.sendSystemMessage(Component.literal(message));
        }
    }

    private static void broadcast(MinecraftServer server, PersistedRun run, String message, boolean open) {
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player == null) continue;
            if (!message.isEmpty()) player.sendSystemMessage(Component.literal(message));
            if (open) player.sendSystemMessage(shortcuts(run));
            send(server, run, player, message, open);
        }
    }

    private static void send(MinecraftServer server, PersistedRun run, ServerPlayer viewer, String message, boolean open) {
        IntermissionRound round = ROUNDS.computeIfAbsent(run.runId(), id -> new IntermissionRound());
        IntermissionStatePayload.Draft draft = draftOf(run, viewer.getUUID());

        List<IntermissionStatePayload.Member> members = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer other = server.getPlayerList().getPlayer(participant.playerId());
            String name = other == null ? participant.playerId().toString().substring(0, 8)
                    : other.getGameProfile().getName();
            members.add(new IntermissionStatePayload.Member(name, round.isReady(participant.playerId()),
                    round.votedCashOut(participant.playerId())));
        }
        TowerNetworking.sendIntermissionState(viewer, new IntermissionStatePayload(run.floorIndex(), draft, members,
                round.secondsLeft(System.currentTimeMillis()), message, open));
    }

    private static IntermissionStatePayload.Draft draftOf(PersistedRun run, UUID viewer) {
        Optional<PersistedDraft> found = run.modifiers().draft();
        if (found.isEmpty()) return new IntermissionStatePayload.Draft(0, List.of(), -1, -1);
        PersistedDraft draft = found.get();
        List<IntermissionStatePayload.Card> cards = new ArrayList<>();
        for (int i = 0; i < draft.cards().size(); i++) {
            ModifierDefinition modifier = TowerDefinitionRegistry.content().modifiers().get(draft.cards().get(i));
            int index = i;
            int votes = (int) draft.votes().values().stream().filter(vote -> vote == index).count();
            cards.add(new IntermissionStatePayload.Card(draft.cards().get(i),
                    modifier == null ? draft.cards().get(i).toString() : modifier.displayName(), votes));
        }
        return new IntermissionStatePayload.Draft(draft.resolved() ? 2 : 1, cards,
                draft.chosen().orElse(-1), draft.votes().getOrDefault(viewer, -1));
    }
}
