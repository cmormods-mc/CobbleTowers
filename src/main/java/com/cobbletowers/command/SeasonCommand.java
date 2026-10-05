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
                Commands.literal("season").executes(context -> lines(context, SeasonService.status()))
                        .then(Commands.literal("track").executes(context -> lines(context,
                                com.cobbletowers.season.SeasonProgressService.trackLines(context.getSource().getServer(),
                                        context.getSource().getPlayerOrException().getUUID())))),
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
                .then(Commands.literal("points")
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                        .executes(context -> {
                                            var player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
                                            int added = com.cobbletowers.season.SeasonProgressService.addPoints(
                                                    context.getSource().getServer(), player.getUUID(),
                                                    IntegerArgumentType.getInteger(context, "amount"));
                                            return one(context, added == 0 ? "No season is running, so nothing was added."
                                                    : "Added " + added + " season points (outside every cap) to " + player.getGameProfile().getName() + ".");
                                        }))))
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
                    com.cobbletowers.persistence.TowerSeasonProgressStore.get(server).clear();
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
