package com.cobbletowers.command;

import com.cobbletowers.persistence.TowerHallStore;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerSeasonStore;
import com.cobbletowers.season.SeasonService;
import com.cobbletowers.season.Seasons;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Seasons (P36a): {@code /tower season} and {@code /tower hall [season]} for players, {@code /cobbletowers seasonadmin} for operators. */
public final class SeasonCommand {

    private SeasonCommand() {}

    /** The subcommands players use, to be added under {@code /cobbletowers play}. */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(
                Commands.literal("season").executes(context -> lines(context, SeasonService.status())),
                Commands.literal("hall")
                        .executes(context -> lines(context, SeasonService.hall(context.getSource().getServer(), Optional.empty())))
                        .then(Commands.argument("season", IntegerArgumentType.integer(1, 999999))
                                .executes(context -> lines(context, SeasonService.hall(context.getSource().getServer(),
                                        Optional.of(IntegerArgumentType.getInteger(context, "season")))))));
    }

    /** {@code /cobbletowers seasonadmin ...}: operator only. */
    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("seasonadmin")
                .requires(source -> source.hasPermission(2))
                .executes(context -> lines(context, SeasonService.status()))
                .then(Commands.literal("status").executes(context -> lines(context, SeasonService.status())))
                .then(Commands.literal("check").executes(context -> {
                    SeasonService.check(context.getSource().getServer());
                    return one(context, "Checked the calendar.");
                }))
                .then(Commands.literal("finalize")
                        .executes(context -> lines(context, SeasonService.finalizePending(context.getSource().getServer(), false)))
                        .then(Commands.literal("dry").executes(context -> lines(context,
                                SeasonService.finalizePending(context.getSource().getServer(), true)))))
                .then(Commands.literal("enable").executes(context -> {
                    Seasons.setEnabled(true);
                    return one(context, "Seasons are on.");
                }))
                .then(Commands.literal("disable").executes(context -> {
                    Seasons.setEnabled(false);
                    return one(context, "Seasons are off until the next restart (the config file decides after that).");
                }))
                .then(Commands.literal("clear").executes(context -> {
                    var server = context.getSource().getServer();
                    TowerSeasonStore.get(server).clear();
                    TowerHallStore.get(server).clear();
                    TowerLeaderboardStore.get(server).prune(key -> !key.allTime());
                    return one(context, "Season state, the Hall and every seasonal board cleared (all-time boards kept).");
                }))));
    }

    private static int lines(CommandContext<CommandSourceStack> context, List<String> lines) {
        for (String line : lines) one(context, line);
        return 1;
    }

    private static int one(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
