package com.cobbletowers.command;

import com.cobbletowers.definition.TrialPoolDefinition.Kind;
import com.cobbletowers.lobby.LobbyService;
import com.cobbletowers.mastery.LeaderboardRules;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerTrialStore;
import com.cobbletowers.trial.TrialSchedule;
import com.cobbletowers.trial.TrialService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The trials in chat (P32): {@code /tower trial} for what today's trials are and where you stand, {@code /tower trial play
 * daily|weekly} to take one into your lobby, {@code /tower trial board daily|weekly [date]} for the results. Operator tools live
 * under {@code /cobbletowers trialadmin}.
 */
public final class TrialCommand {

    private TrialCommand() {}

    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        LiteralArgumentBuilder<CommandSourceStack> trial = Commands.literal("trial")
                .executes(context -> info(context, null))
                .then(Commands.literal("daily").executes(context -> info(context, Kind.DAILY)))
                .then(Commands.literal("weekly").executes(context -> info(context, Kind.WEEKLY)))
                .then(Commands.literal("play")
                        .then(Commands.literal("daily").executes(context -> play(context, Kind.DAILY)))
                        .then(Commands.literal("weekly").executes(context -> play(context, Kind.WEEKLY))))
                .then(Commands.literal("board")
                        .then(boardFor("daily", Kind.DAILY))
                        .then(boardFor("weekly", Kind.WEEKLY)));
        LiteralArgumentBuilder<CommandSourceStack> contracts = Commands.literal("contracts")
                .executes(TrialCommand::contracts)
                .then(Commands.literal("reroll")
                        .then(Commands.literal("daily").then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                                .executes(context -> reroll(context, com.cobbletowers.definition.ContractTemplateDefinition.Period.DAILY))))
                        .then(Commands.literal("weekly").then(Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 2))
                                .executes(context -> reroll(context, com.cobbletowers.definition.ContractTemplateDefinition.Period.WEEKLY)))));
        LiteralArgumentBuilder<CommandSourceStack> report = Commands.literal("report")
                .executes(context -> report(context, false))
                .then(Commands.literal("share").executes(context -> report(context, true)));
        LiteralArgumentBuilder<CommandSourceStack> summary = Commands.literal("summary")
                .then(Commands.literal("on").executes(context -> summary(context, false)))
                .then(Commands.literal("off").executes(context -> summary(context, true)));
        return List.of(trial, contracts, report, summary);
    }

    private static int report(CommandContext<CommandSourceStack> context, boolean share) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<com.cobbletowers.mastery.RunReport> last = com.cobbletowers.mastery.RunSummaries.lastReportOf(player.getUUID());
        if (last.isEmpty()) {
            source.sendFailure(Component.literal("You have no finished run to report yet."));
            return 0;
        }
        if (share) {
            String line = last.get().shareLine(com.cobbletowers.mastery.RunSummaries.lastPlayersOf(player.getUUID()));
            source.getServer().getPlayerList().broadcastSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD), false);
            return 1;
        }
        for (String line : last.get().lines()) source.sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    private static int summary(CommandContext<CommandSourceStack> context, boolean quiet) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TowerTrialStore store = TowerTrialStore.get(context.getSource().getServer());
        store.setQuiet(player.getUUID(), quiet);
        store.checkpoint(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal(quiet
                ? "The login summary is off. /tower summary on turns it back on."
                : "The login summary is on."), false);
        return 1;
    }

    private static int contracts(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        for (String line : com.cobbletowers.contract.ContractService.describe(source.getServer(), source.getPlayerOrException().getUUID())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int reroll(CommandContext<CommandSourceStack> context,
                              com.cobbletowers.definition.ContractTemplateDefinition.Period period) throws CommandSyntaxException {
        int slot = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "slot") - 1;
        String reply = com.cobbletowers.contract.ContractService.reroll(context.getSource().getServer(),
                context.getSource().getPlayerOrException().getUUID(), period, slot);
        context.getSource().sendSuccess(() -> Component.literal(reply), false);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boardFor(String word, Kind kind) {
        return Commands.literal(word)
                .executes(context -> board(context, kind, ""))
                .then(Commands.argument("period", StringArgumentType.word()).executes(context ->
                        board(context, kind, StringArgumentType.getString(context, "period"))));
    }

    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("trialadmin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("day")
                                .then(Commands.argument("date", StringArgumentType.word()).executes(TrialCommand::day)))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player()).executes(TrialCommand::reset)))));
    }

    // ---- players ---------------------------------------------------------------------------------------------------

    private static int info(CommandContext<CommandSourceStack> context, Kind only) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        for (Kind kind : only == null ? List.of(Kind.DAILY, Kind.WEEKLY) : List.of(only)) {
            for (String line : TrialService.describe(source.getServer(), player.getUUID(), kind)) {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return 1;
    }

    private static int play(CommandContext<CommandSourceStack> context, Kind kind) throws CommandSyntaxException {
        String reply = LobbyService.selectTrial(context.getSource().getServer(), context.getSource().getPlayerOrException(), kind);
        context.getSource().sendSuccess(() -> Component.literal(reply), false);
        return 1;
    }

    private static int board(CommandContext<CommandSourceStack> context, Kind kind, String period) {
        CommandSourceStack source = context.getSource();
        Optional<TrialSchedule.Instance> current = TrialService.current(kind);
        String boardPath;
        if (period.isEmpty()) {
            if (current.isEmpty()) {
                source.sendFailure(Component.literal("There is no " + kind.name().toLowerCase(Locale.ROOT) + " trial on this server."));
                return 0;
            }
            boardPath = kind.name().toLowerCase(Locale.ROOT) + "/" + current.get().periodKey();
        } else {
            if (kind == Kind.DAILY) {
                try {
                    LocalDate.parse(period);
                } catch (DateTimeParseException ex) {
                    source.sendFailure(Component.literal("A daily board is named by date, like 2026-10-05."));
                    return 0;
                }
            }
            boardPath = kind.name().toLowerCase(Locale.ROOT) + "/" + period.toLowerCase(Locale.ROOT);
        }
        ResourceLocation id = ResourceLocation.tryParse("cobbletowers:" + boardPath);
        if (id == null) {
            source.sendFailure(Component.literal("That is not a board."));
            return 0;
        }
        TowerLeaderboardStore store = TowerLeaderboardStore.get(source.getServer());
        source.sendSuccess(() -> Component.literal((kind == Kind.DAILY ? "Daily Trial" : "Weekly Trial") + " " + boardPath.substring(boardPath.indexOf('/') + 1))
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        for (Mode mode : List.of(Mode.SOLO, Mode.TEAM)) {
            List<Entry> entries = store.top(new Key(Board.TRIAL, id, mode), 10);
            source.sendSuccess(() -> Component.literal("  " + (mode == Mode.SOLO ? "Solo" : "Team")).withStyle(ChatFormatting.AQUA), false);
            if (entries.isEmpty()) source.sendSuccess(() -> Component.literal("    no entries yet").withStyle(ChatFormatting.GRAY), false);
            for (int i = 0; i < entries.size(); i++) {
                String row = TrialService.boardRow(i + 1, entries.get(i));
                source.sendSuccess(() -> Component.literal("    " + row), false);
                shown++;
            }
        }
        return shown;
    }

    // ---- operators -------------------------------------------------------------------------------------------------

    private static int day(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "date");
        if (raw.equalsIgnoreCase("off")) {
            TrialService.overrideDay(Optional.empty());
            context.getSource().sendSuccess(() -> Component.literal("The trial day follows the clock again."), true);
            return 1;
        }
        try {
            LocalDate date = LocalDate.parse(raw);
            TrialService.overrideDay(Optional.of(date));
            context.getSource().sendSuccess(() -> Component.literal("The trial day is pinned to " + date), true);
            return 1;
        } catch (DateTimeParseException ex) {
            context.getSource().sendFailure(Component.literal("Use a date like 2026-10-05, or off."));
            return 0;
        }
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        TowerTrialStore store = TowerTrialStore.get(context.getSource().getServer());
        store.reset(player.getUUID());
        store.checkpoint(context.getSource().getServer());
        com.cobbletowers.persistence.TowerContractStore contracts = com.cobbletowers.persistence.TowerContractStore.get(context.getSource().getServer());
        contracts.reset(player.getUUID());
        contracts.checkpoint(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Reset " + player.getGameProfile().getName() + "'s trial history and streak"), true);
        return 1;
    }
}
