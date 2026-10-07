package com.cobbletowers.command;

import com.cobbletowers.diagnostics.TowerMetrics;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.instance.CellTickets;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.persistence.TowerDiagnosticsStore;
import com.cobbletowers.runtime.TowerRuns;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code /cobbletowers diagnostics[ run <run>]}: the counts, timings and adapter status of TDS #60 and section 11.
 * State, modifiers and seed are in {@code runs show}. Read-only.
 */
public final class DiagnosticsCommand {

    private DiagnosticsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("diagnostics")
                        .requires(source -> source.hasPermission(2))
                        .executes(DiagnosticsCommand::overview)
                        .then(Commands.literal("run")
                                .then(Commands.argument("run", UuidArgument.uuid())
                                        .executes(DiagnosticsCommand::forRun)))));
    }

    private static int overview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int towerEntities = countTowerEntities(source);

        // Not TowerRuns.all().size(): finished runs stay indexed for history, and TDS section 11 asks for active
        // runs.
        long activeRuns = TowerRuns.all().stream().filter(run -> !run.isRetired()).count();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%d active run(s), %d active encounter(s), %d tower chunk(s), %d tower entities",
                activeRuns, TowerEncounters.activeRounds().size(),
                CellTickets.heldCount() * CellTickets.chunksPerCell(), towerEntities))
                .withStyle(ChatFormatting.GOLD), false);

        TowerDiagnosticsStore store = TowerDiagnosticsStore.get(source.getServer());
        source.sendSuccess(() -> Component.literal("  persistence backlog: " + store.nonCheckpointedWrites()
                + " write(s) since the last checkpoint"), false);

        for (String category : new String[] {TowerMetrics.ALLOCATION, TowerMetrics.ENCOUNTER_CONSTRUCTION,
                TowerMetrics.TRANSITION, TowerMetrics.CLEANUP, TowerMetrics.TICK}) {
            TowerDiagnosticsStore.CategoryStats stats = store.categoryStats(category);
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %-22s %d sample(s), avg %dms, min %dms, max %dms, %d over budget",
                    category, stats.count(), stats.averageMillis(),
                    stats.count() == 0 ? 0 : stats.min(), stats.max(), stats.budgetExceededCount())), false);
        }
        return 1;
    }

    private static int forRun(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var runId = UuidArgument.getUuid(context, "run");
        TowerDiagnosticsStore.RunDiagnostics diagnostics =
                TowerDiagnosticsStore.get(source.getServer()).runDiagnostics(runId);

        source.sendSuccess(() -> Component.literal(runId.toString()).withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  last transition %dms, last encounter construction %dms, updated %d",
                diagnostics.lastTransitionMillis(), diagnostics.lastEncounterConstructionMillis(),
                diagnostics.updatedAt())), false);
        return 1;
    }

    /** Bounded by what the tower dimension actually holds -- players and this mod's own Pokemon. */
    private static int countTowerEntities(CommandSourceStack source) {
        ServerLevel level = TowerDimension.level(source.getServer());
        if (level == null) return 0;
        int count = 0;
        for (var ignored : level.getAllEntities()) count++;
        return count;
    }
}
