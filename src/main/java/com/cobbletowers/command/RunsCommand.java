package com.cobbletowers.command;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.RunFactory;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerRuns;
import com.mojang.brigadier.CommandDispatcher;
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
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("runs")
                        .then(Commands.literal("list").executes(RunsCommand::list))
                        .then(Commands.literal("show")
                                .then(Commands.argument("run", UuidArgument.uuid()).executes(RunsCommand::show)))
                        .then(Commands.literal("create")
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .then(Commands.argument("players", EntityArgument.players())
                                                .executes(RunsCommand::create))))
                        .then(Commands.literal("allocate")
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .executes(RunsCommand::allocate)))
                        .then(Commands.literal("encounter")
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .executes(RunsCommand::encounter)))
                        .then(Commands.literal("advance")
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
        TowerEncounters.of(runId).ifPresent(round -> source.sendSuccess(() -> Component.literal(
                "  fighting now: " + round.byPlayer()), false));
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
}
