package com.cobbletowers.command;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.battle.cobbleraids.TowerBossAdapter;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.definition.VendorServiceDefinition;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.modifier.ModifierResolver;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.PersistedDraft;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.persistence.TowerWalletStore;
import com.cobbletowers.runtime.RunFactory;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerPresence;
import com.cobbletowers.runtime.TowerRuns;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobbletowers runs list|show|create|advance}: the run machine, visible and drivable.
 *
 * <p>{@code list} and {@code show} are read-only. {@code create} and {@code advance} are dev tools at
 * the same permission level as the battle spike -- P2 has no floors, no battles and no GUI, so
 * driving the machine by hand is the only way to exercise persistence, checkpoints and recovery
 * against a real server. The crash-durability test drives exactly these over RCON.
 */
public final class RunsCommand {

    private RunsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("runs")
                        // The one thing here a player is meant to do for themselves. Everything else
                        // under "runs" drives the machine by hand and stays at permission 2.
                        .then(Commands.literal("leave").executes(RunsCommand::leave))
                        // Also player-facing, and also bare of a `.requires(...)` for the same reason:
                        // cashing out ends the run, which is a decision only the party makes for
                        // itself. Not blocked by an open draft -- a party that is leaving for good has
                        // no reason to be forced through a vote on a challenge it will never fight.
                        .then(Commands.literal("cashout").executes(RunsCommand::cashout))
                        .then(Commands.literal("reward")
                                .then(Commands.literal("show").executes(RunsCommand::rewardShow)))
                        // Player-facing, like leave/cashout: opens the caller's own shop screen
                        // (P12). INTERMISSION-only is enforced by VendorPurchaseService itself, not
                        // here -- the catalog can be requested any time; buying cannot.
                        .then(Commands.literal("vendor").executes(RunsCommand::vendor))
                        // Drafting is the other thing a player does for themselves, so `vote` and
                        // `show` set no permission while `force` does. P7's trap is why each
                        // subcommand carries its own: Brigadier keeps the FIRST registration's
                        // `requires` on a merged literal, so one put higher up would silently apply
                        // to every player-facing thing beneath it.
                        // Puts a modifier on a run without waiting for the draw to offer it.
                        // An operator's tool and the only way a live test can pin down which
                        // modifier a boss is fought under -- the cards are the seed's business.
                        .then(Commands.literal("grant")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .then(Commands.argument("modifier", ResourceLocationArgument.id())
                                                .executes(RunsCommand::grant))))
                        .then(Commands.literal("draft")
                                .then(Commands.literal("show")
                                        .executes(RunsCommand::draftShow))
                                .then(Commands.literal("vote")
                                        .then(Commands.argument("card", IntegerArgumentType.integer(1, 9))
                                                .executes(RunsCommand::draftVote)))
                                .then(Commands.literal("force")
                                        .requires(source -> source.hasPermission(2))
                                        .then(Commands.argument("run", UuidArgument.uuid())
                                                .executes(RunsCommand::draftForce))))
                        .then(Commands.literal("list")
                                .requires(source -> source.hasPermission(2))
                                .executes(RunsCommand::list))
                        .then(Commands.literal("show")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("run", UuidArgument.uuid()).executes(RunsCommand::show)))
                        .then(Commands.literal("create")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(RunsCommand::create))))
                        .then(Commands.literal("allocate")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .executes(RunsCommand::allocate)))
                        .then(Commands.literal("encounter")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .executes(RunsCommand::encounter)))
                        .then(Commands.literal("watchdog")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("cap", StringArgumentType.word())
                                        .suggests((context, builder) -> builder.suggest("player")
                                                .suggest("floor").buildFuture())
                                        .executes(RunsCommand::watchdog)))
                        .then(Commands.literal("advance")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .then(Commands.argument("event", StringArgumentType.word())
                                                .suggests((context, builder) -> {
                                                    for (RunEvent event : RunEvent.values()) {
                                                        builder.suggest(event.name().toLowerCase(Locale.ROOT));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(RunsCommand::advance))))));
    }

    /**
     * {@code /cobbletowers runs watchdog player|floor}: the real sweep, with the clock wound forward.
     *
     * <p>The watchdog's caps are ten and thirty minutes, which is right for a server and impossible
     * for a test -- and a test nobody runs proves nothing. This runs the sweep that the tick runs,
     * over the floors that are really open, with {@code now} advanced past one cap. No test-only
     * threshold, no second code path: the only thing that differs from the real thing is the clock.
     *
     * <p>Useful to an operator for the same reason: it answers "what would the watchdog do about
     * this floor" without waiting out the cap to find out.
     */
    private static int watchdog(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cap = StringArgumentType.getString(context, "cap");
        long ahead = cap.equalsIgnoreCase("floor")
                ? TowerPresence.FLOOR_STALL_MILLIS : TowerPresence.PLAYER_STALL_MILLIS;

        int floors = TowerEncounters.active();
        TowerPresence.sweep(source.getServer(), System.currentTimeMillis() + ahead + 1);
        source.sendSuccess(() -> Component.literal("Swept " + floors + " floor(s) as if the " + cap
                + " cap had passed").withStyle(ChatFormatting.GOLD), false);
        return floors;
    }

    /**
     * {@code /cobbletowers runs leave}: the player is done, and says so.
     *
     * <p>Terminal for them -- {@code ParticipantState} freezes a participant who has left, so no
     * later transition can quietly put them back in -- and the end of the run if they were the last
     * one in it (TDS #39).
     */
    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!TowerPresence.leave(source.getServer(), player, System.currentTimeMillis())) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You have left the tower run.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /**
     * {@code /cobbletowers runs cashout}: the party stops here, banking whatever it has earned.
     *
     * <p>Fires {@code CASH_OUT_CHOSEN} for the caller's own run, the same way {@code leave} resolves
     * its run from the player rather than taking one as an argument. {@code RewardBankService} grants
     * everything remaining the moment the run reaches {@code CASHED_OUT} (docs/design/P9-economy.md
     * §4a) -- this command only asks for the transition.
     */
    private static int cashout(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        RunTransitionService.Outcome outcome = RunTransitionService.apply(source.getServer(), found.get().runId(),
                RunEvent.CASH_OUT_CHOSEN, System.currentTimeMillis());
        if (outcome instanceof RunTransitionService.Move) {
            source.sendSuccess(() -> Component.literal("You have cashed out.").withStyle(ChatFormatting.GOLD), true);
            return 1;
        }
        RunTransitionService.Refusal refusal = (RunTransitionService.Refusal) outcome;
        source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
        return 0;
    }

    /**
     * {@code /cobbletowers runs reward show}: what has been banked, what is still at risk, and what
     * is queued for the caller. Diagnostic (TDS #60), the same role {@code draft show} plays for the
     * modifier system.
     */
    private static int rewardShow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        PersistedRun run = found.get();
        source.sendSuccess(() -> Component.literal("Banked through floor " + run.lastBankedFloor())
                .withStyle(ChatFormatting.GOLD), false);
        for (var entry : run.ledger()) {
            boolean banked = entry.floorIndex() <= run.lastBankedFloor();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s floor %d %s  [%s]",
                    entry.kind(), entry.floorIndex(), entry.what(), banked ? "banked" : "at risk")), false);
        }
        List<PendingTowerReward> pending = TowerPendingRewardStore.get(source.getServer())
                .queueFor(player.getUUID());
        source.sendSuccess(() -> Component.literal("Queued for you: " + pending.size() + " grant(s)"), false);
        for (PendingTowerReward reward : pending) {
            source.sendSuccess(() -> Component.literal("  " + reward.item() + " x" + reward.amount()
                    + " (floor " + reward.floorIndex() + ")"), false);
        }
        return pending.size();
    }

    /**
     * {@code /cobbletowers runs vendor}: sends the caller {@link VendorCatalogPayload} and, on a
     * modded client, opens the shop screen -- a client without the channel registered gets a plain
     * chat listing instead, the same graceful-degradation posture P11's reward reveal already takes.
     */
    private static int vendor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        PersistedRun run = found.get();
        TowerNetworking.sendVendorCatalog(source.getServer(), player, run);

        long balance = TowerWalletStore.get(source.getServer()).balanceOf(player.getUUID());
        source.sendSuccess(() -> Component.literal("CobbleDollars: " + balance).withStyle(ChatFormatting.GOLD), false);
        for (VendorServiceDefinition service : TowerDefinitionRegistry.content().vendorCatalog()) {
            int cap = service.maxPurchasesPerRun();
            String remaining = cap <= 0 ? "unlimited" : (cap - run.purchasesOf(service.id())) + "/" + cap + " left";
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s -- %d CobbleDollars (%s)",
                    service.displayName(), service.priceCobbleDollars(), remaining)), false);
        }
        return TowerDefinitionRegistry.content().vendorCatalog().size();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<PersistedRun> runs = TowerRuns.all();
        source.sendSuccess(() -> Component.literal(runs.size() + " run(s)").withStyle(ChatFormatting.GOLD), false);
        for (PersistedRun run : runs) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  %s  %s  floor %d  %d player(s)", run.runId(), run.towerId(), run.state(),
                    run.floorIndex(), run.participants().size())), false);
        }
        return runs.size();
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No run " + runId));
            return 0;
        }
        PersistedRun run = found.get();
        source.sendSuccess(() -> Component.literal(run.runId() + "  " + run.state())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  tower %s rev %d  digest %s  ruleset rev %d  structure rev %d",
                run.towerId(), run.towerRevision(),
                // The first eight hex characters are enough to see that content changed.
                run.towerDigest().isEmpty() ? "none" : run.towerDigest().substring(0, 8),
                run.rulesetRevision(), run.structureRevision())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  floor %d  seed %d  updated %d",
                run.floorIndex(), run.seed(), run.updatedAt())), false);
        source.sendSuccess(() -> Component.literal("  cell " + (run.cell().isPresent()
                ? String.valueOf(run.cell().getAsInt()) : "none")), false);
        source.sendSuccess(() -> Component.literal("  checkpoint " + run.lastCheckpoint()
                .map(checkpoint -> checkpoint.key() + " @ " + checkpoint.state()).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("  committed " + run.committedTransactions()), false);
        source.sendSuccess(() -> Component.literal("  unclaimed pool: " + run.ledger().size() + " entry(s)"), false);
        for (var entry : run.ledger()) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "    %s floor %d %s",
                    entry.kind(), entry.floorIndex(), entry.what())), false);
        }
        // TDS #60: state, modifiers, seed and last transition, all keyed by run.
        ModifierEffects effects = DraftService.effects(run);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  modifiers: %d drafted (%d locked in)  ->  level %+d, +%d opponent(s), boss level %+d,"
                        + " boss hp %d%%, reward %d%%",
                run.modifiers().accumulated().size(), run.modifiers().lockedIn().size(),
                effects.levelOffset(), effects.extraOpponents(), effects.bossLevelOffset(),
                effects.bossHealthPercent(), effects.rewardPercent())), false);
        for (ResourceLocation modifier : run.modifiers().accumulated()) {
            source.sendSuccess(() -> Component.literal("    " + modifier
                    + (run.modifiers().lockedIn().contains(modifier) ? "  [LOCKED IN]" : "")), false);
        }
        run.modifiers().draft().ifPresent(draft -> source.sendSuccess(() -> Component.literal(
                "  draft at floor " + draft.floorIndex() + (draft.lockIn() ? " (LOCK-IN)" : "")
                        + ": " + draft.cards() + (draft.resolved()
                        ? " -> " + draft.chosenModifier().orElse(null)
                        + (draft.decidedByTieBreak() ? " (tie-break)" : "")
                        : " OPEN, " + draft.votes().size() + " vote(s)")), false));
        TowerEncounters.of(runId).ifPresent(round -> source.sendSuccess(() -> Component.literal(
                "  fighting now: " + round.phase() + " " + round.byPlayer()
                        + (round.waves().values().stream().anyMatch(wave -> wave.remaining() > 0)
                        ? "  waves " + round.waves() : "")), false));
        TowerBossAdapter.of(runId).ifPresent(boss -> source.sendSuccess(() -> Component.literal(
                "  boss: " + boss.definition() + " at level " + boss.level()), false));
        for (PersistedParticipant participant : run.participants()) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  %s/%s/%s  %d registered", participant.playerId(), participant.state().connection(),
                    participant.state().combat(), participant.state().membership(),
                    participant.registeredPokemon().size())), false);
        }
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation towerId = ResourceLocationArgument.getId(context, "tower");
        List<UUID> players = new ArrayList<>();
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            players.add(player.getUUID());
        }

        Optional<PersistedRun> created = RunFactory.create(TowerDefinitionRegistry.content(), towerId, players,
                source.getServer().overworld().getRandom().nextLong(), System.currentTimeMillis());
        if (created.isEmpty()) {
            source.sendFailure(Component.literal("No tower " + towerId + " is loaded"));
            return 0;
        }
        PersistedRun run = created.get();
        // Checkpointed on creation: a run nobody can find after a crash is worse than no run at all.
        TowerRuns.save(source.getServer(), run, true);
        source.sendSuccess(() -> Component.literal("Created run " + run.runId() + " on " + towerId
                + " with " + players.size() + " player(s)").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int allocate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        RunTransitionService.Outcome outcome =
                RunLifecycle.allocateInstance(source.getServer(), runId, System.currentTimeMillis());
        if (outcome instanceof RunTransitionService.Move move) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s -> %s, cell %s",
                            move.from(), move.next().state(), move.next().cell().isPresent()
                                    ? String.valueOf(move.next().cell().getAsInt()) : "none"))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        RunTransitionService.Refusal refusal = (RunTransitionService.Refusal) outcome;
        source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
        return 0;
    }

    /**
     * Starts the floor's prerequisite round, the thing a GUI will do in P11.
     *
     * <p>Dev-gated like the rest of these: there is no preparation screen yet, so a floor is begun by
     * hand. The durability and floor tests drive exactly this over RCON.
     */
    private static int encounter(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");

        RunTransitionService.Outcome started =
                RunTransitionService.apply(source.getServer(), runId, RunEvent.ENCOUNTER_STARTED,
                        System.currentTimeMillis());
        if (started instanceof RunTransitionService.Refusal refusal) {
            source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
            return 0;
        }

        Optional<TowerEncounters.Round> round;
        try {
            round = TowerEncounters.begin(source.getServer(), runId);
        } catch (RuntimeException ex) {
            // Minecraft only prints a command's stack trace when it is running in an IDE; on a real
            // server the throwable is swallowed and the caller gets "An unexpected error occurred".
            // A dev command whose failures are invisible is worse than no command, so it says so
            // itself before anything else gets a chance to hide it.
            TowerLog.error("Starting the floor for run {} threw", runId, ex);
            source.sendFailure(Component.literal("Starting the floor threw: " + ex));
            return 0;
        }
        if (round.isEmpty()) {
            // The floor could not be put up at all: unknown content, nobody able to fight, no cell.
            // None of those are the party's doing, so it is a technical fault and the run parks.
            source.sendFailure(Component.literal("The floor could not be started; parking the run"));
            RunTransitionService.apply(source.getServer(), runId, RunEvent.TECHNICAL_FAILURE,
                    System.currentTimeMillis());
            return 0;
        }
        TowerEncounters.Round begun = round.get();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Floor %d begun: %d opponent(s)", begun.floorIndex(), begun.byPlayer().size()))
                .withStyle(ChatFormatting.GREEN), true);
        return begun.byPlayer().size();
    }

    private static int advance(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        String raw = StringArgumentType.getString(context, "event");
        RunEvent event;
        try {
            event = RunEvent.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown event '" + raw + "'"));
            return 0;
        }

        RunTransitionService.Outcome outcome =
                RunTransitionService.apply(source.getServer(), runId, event, System.currentTimeMillis());
        if (outcome instanceof RunTransitionService.Move move) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s -> %s%s",
                            move.from(), move.next().state(),
                            move.checkpoint() ? " [checkpoint " + move.key() + "]" : ""))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        RunTransitionService.Refusal refusal = (RunTransitionService.Refusal) outcome;
        source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
        return 0;
    }

    /**
     * Grants a modifier straight onto a run, bypassing the draft.
     *
     * <p>Refused if the resolver would not allow it, so this cannot be used to build an accumulation
     * the game itself could never reach -- an operator tool that could produce illegal state would
     * make every later bug report ambiguous.
     */
    private static int grant(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        ResourceLocation modifierId = ResourceLocationArgument.getId(context, "modifier");

        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No run " + runId));
            return 0;
        }
        PersistedRun run = found.get();
        TowerContent content = TowerDefinitionRegistry.content();
        Optional<ModifierDefinition> modifier = content.modifier(modifierId);
        if (modifier.isEmpty()) {
            source.sendFailure(Component.literal("No modifier " + modifierId + " is loaded"));
            return 0;
        }
        if (!ModifierResolver.eligible(modifier.get(), DraftService.held(content, run.modifiers()))) {
            source.sendFailure(Component.literal(modifierId + " cannot be held alongside what this run"
                    + " already has: " + run.modifiers().accumulated()));
            return 0;
        }

        TowerRuns.save(source.getServer(),
                run.withModifiers(run.modifiers().accumulating(modifierId), System.currentTimeMillis()), true);
        source.sendSuccess(() -> Component.literal("Granted " + modifierId + " to run " + runId), true);
        return 1;
    }

    /**
     * Prints the draft in front of the player, with its cards numbered from 1.
     *
     * <p>One-based for the player, zero-based inside: a card list that starts at zero is a thing
     * only programmers vote on. The conversion happens here, at the edge, exactly once.
     */
    private static int draftShow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        Optional<PersistedDraft> draft = found.get().modifiers().draft();
        if (draft.isEmpty()) {
            source.sendFailure(Component.literal("There is no draft open."));
            return 0;
        }
        PersistedDraft open = draft.get();
        TowerContent content = TowerDefinitionRegistry.content();
        source.sendSuccess(() -> Component.literal((open.lockIn() ? "LOCK-IN draft" : "Draft")
                + " at floor " + open.floorIndex()).withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < open.cards().size(); index++) {
            int card = index;
            ResourceLocation id = open.cards().get(index);
            String name = content.modifier(id).map(ModifierDefinition::displayName).orElse(id.toString());
            String risk = content.modifier(id).map(modifier -> modifier.risk().name()).orElse("?");
            long votes = open.votes().values().stream().filter(choice -> choice == card).count();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  [%d] %s (%s) - %d vote(s)%s", card + 1, name, risk, votes,
                    open.chosen().isPresent() && open.chosen().getAsInt() == card ? "  <- CHOSEN" : "")), false);
        }
        if (open.resolved()) {
            source.sendSuccess(() -> Component.literal("  settled"
                    + (open.decidedByTieBreak() ? " on a seed tie-break" : " by majority")), false);
        }
        return open.cards().size();
    }

    /** Votes for a card, by its 1-based number. */
    private static int draftVote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        int card = IntegerArgumentType.getInteger(context, "card") - 1;
        Optional<PersistedDraft> after = DraftService.vote(source.getServer(), found.get().runId(),
                player.getUUID(), card, System.currentTimeMillis());
        if (after.isEmpty()) {
            source.sendFailure(Component.literal("There is no draft open to vote on."));
            return 0;
        }
        boolean settled = after.get().resolved();
        source.sendSuccess(() -> Component.literal(settled
                ? "Vote recorded; the draft is settled."
                : "Vote recorded."), false);
        return 1;
    }

    /** Settles an open draft without waiting for the rest of the party. */
    private static int draftForce(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty() || found.get().modifiers().draft().isEmpty()) {
            source.sendFailure(Component.literal("Run " + runId + " has no draft open."));
            return 0;
        }
        PersistedDraft settled = DraftService.settle(source.getServer(), runId, System.currentTimeMillis());
        source.sendSuccess(() -> Component.literal("Draft settled on "
                + settled.chosenModifier().map(ResourceLocation::toString).orElse("nothing")
                + (settled.decidedByTieBreak() ? " (seed tie-break)" : " (majority)")), true);
        return 1;
    }
}

