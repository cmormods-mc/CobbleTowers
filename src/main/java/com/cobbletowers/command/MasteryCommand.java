package com.cobbletowers.command;

import com.cobbletowers.definition.AchievementDefinition;
import com.cobbletowers.definition.AchievementRegistry;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.LeaderboardRules;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.mastery.MasteryPerks;
import com.cobbletowers.mastery.MasteryView;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerMasteryStore;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Mastery and leaderboards (P31), in chat. {@code /tower mastery [tower]} and {@code /tower leaderboard <board> [tower]} are
 * attached to the player's {@code play} command by {@link PlayCommand}; the operator tools live under
 * {@code /cobbletowers masteryadmin}. The screens say the same things from the same words ({@link MasteryView}).
 */
public final class MasteryCommand {

    private MasteryCommand() {}

    /** The subcommands players use, to be added under {@code /cobbletowers play}. */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        LiteralArgumentBuilder<CommandSourceStack> mastery = Commands.literal("mastery")
                .executes(context -> overview(context))
                .then(Commands.argument("tower", ResourceLocationArgument.id()).executes(context -> detail(context,
                        ResourceLocationArgument.getId(context, "tower"))));
        LiteralArgumentBuilder<CommandSourceStack> boards = Commands.literal("leaderboard");
        for (Board board : Board.values()) {
            String name = board.name().toLowerCase(java.util.Locale.ROOT);
            boards.then(Commands.literal(name)
                    .executes(context -> leaderboard(context, board, defaultTower(), "", false))
                    .then(Commands.literal("alltime")
                            .executes(context -> leaderboard(context, board, defaultTower(), "", true))
                            .then(Commands.argument("tower", ResourceLocationArgument.id())
                                    .executes(context -> leaderboard(context, board, ResourceLocationArgument.getId(context, "tower"), "", true))
                                    .then(Commands.argument("playlist", StringArgumentType.word())
                                            .executes(context -> leaderboard(context, board,
                                                    ResourceLocationArgument.getId(context, "tower"),
                                                    StringArgumentType.getString(context, "playlist"), true)))))
                    .then(Commands.argument("tower", ResourceLocationArgument.id())
                            .executes(context -> leaderboard(context, board, ResourceLocationArgument.getId(context, "tower"), "", false))
                            .then(Commands.argument("playlist", StringArgumentType.word())
                                    .executes(context -> leaderboard(context, board,
                                            ResourceLocationArgument.getId(context, "tower"),
                                            StringArgumentType.getString(context, "playlist"), false)))));
        }
        return List.of(mastery, boards);
    }

    public static void registerAdmin(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(Commands.literal("masteryadmin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("tower", ResourceLocationArgument.id())
                                                .then(Commands.argument("achievement", StringArgumentType.word())
                                                        .executes(MasteryCommand::grant)))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("tower", ResourceLocationArgument.id())
                                                .executes(MasteryCommand::reset))))
                        .then(Commands.literal("report").executes(MasteryCommand::report))
                        .then(Commands.literal("clearboards").executes(MasteryCommand::clearBoards))));
    }

    // ---- players ---------------------------------------------------------------------------------------------------

    private static ResourceLocation defaultTower() {
        return TowerDefinitionRegistry.content().sortedTowerIds().stream().findFirst()
                .orElse(ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral"));
    }

    /** Opens the screen for a client that has it; false means the caller should answer in chat instead. */
    private static boolean screen(CommandContext<CommandSourceStack> context, String tower, String tab)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!com.cobbletowers.network.MasteryScreens.canShow(player)) return false;
        com.cobbletowers.network.MasteryScreens.send(player, com.cobbletowers.network.MasteryScreens.build(
                context.getSource().getServer(), player, tower, tab, true));
        return true;
    }

    private static int overview(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (screen(context, "", "mastery")) return 1;
        TowerMasteryStore store = TowerMasteryStore.get(source.getServer());
        source.sendSuccess(() -> Component.literal("Your mastery").withStyle(ChatFormatting.GOLD), false);
        for (ResourceLocation id : TowerDefinitionRegistry.content().sortedTowerIds()) {
            TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(id);
            TowerMasteryStore.Progress progress = store.progressOf(player.getUUID(), id);
            source.sendSuccess(() -> Component.literal("  " + tower.displayName() + ": " + MasteryView.progressLine(progress.level())
                    + "; " + progress.cyclesCleared() + " cycle(s) cleared, deepest Ascension " + progress.ascensionReached()), false);
        }
        source.sendSuccess(() -> Component.literal("  /tower mastery <tower> lists the achievements.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int detail(CommandContext<CommandSourceStack> context, ResourceLocation towerId) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (screen(context, towerId.toString(), "mastery")) return 1;
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(towerId);
        if (tower == null) {
            source.sendFailure(Component.literal("No tower " + towerId + " is loaded."));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        TowerMasteryStore.Progress progress = TowerMasteryStore.get(source.getServer()).progressOf(player.getUUID(), towerId);
        MasteryPerks.Perks perks = MasteryPerks.at(progress.level());
        source.sendSuccess(() -> Component.literal(tower.displayName() + " mastery: " + MasteryView.progressLine(progress.level()))
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("  perks: " + MasteryView.perksLine(perks)), false);
        for (AchievementDefinition achievement : AchievementRegistry.all()) {
            boolean held = progress.unlocked().containsKey(achievement.id());
            source.sendSuccess(() -> Component.literal((held ? "  [x] " : "  [ ] ") + achievement.displayName() + " - "
                    + achievement.description()).withStyle(held ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }
        return progress.level();
    }

    private static int leaderboard(CommandContext<CommandSourceStack> context, Board board, ResourceLocation towerId,
                                   String playlist, boolean allTime) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        // The default view is the current season's board (P36a); only a per-run board is ever seasonal, and "alltime" asks for the lifetime one.
        java.util.Optional<Integer> viewing = allTime || !com.cobbletowers.mastery.Boards.seasonal(board)
                ? java.util.Optional.empty() : com.cobbletowers.season.Seasons.viewNumber();
        String season = viewing.map(com.cobbletowers.season.SeasonSchedule::idOf).orElse("");
        // The screen shows the Standard all-time boards; a season's and a playlist's boards are read in chat.
        if (playlist.isEmpty() && season.isEmpty() && screen(context, towerId.toString(), board.name().toLowerCase(java.util.Locale.ROOT))) return 1;
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(towerId);
        if (tower == null) {
            source.sendFailure(Component.literal("No tower " + towerId + " is loaded."));
            return 0;
        }
        TowerLeaderboardStore store = TowerLeaderboardStore.get(source.getServer());
        source.sendSuccess(() -> Component.literal(board.title() + " - " + tower.displayName()
                + (playlist.isEmpty() ? "" : " (" + playlist + ")")
                + (viewing.isPresent() ? " - Season " + viewing.get() + ": " + com.cobbletowers.season.Seasons.definition(viewing.get()).name()
                        : (com.cobbletowers.season.Seasons.enabled() && com.cobbletowers.mastery.Boards.seasonal(board) ? " - all-time" : "")))
                .withStyle(ChatFormatting.GOLD), false);
        int shown = 0;
        List<Mode> modes = board.hasMode() ? List.of(Mode.SOLO, Mode.TEAM) : List.of(Mode.ANY);
        for (Mode mode : modes) {
            List<Entry> entries = store.top(new Key(board, towerId, mode, playlist, season), 10);
            if (board.hasMode()) {
                source.sendSuccess(() -> Component.literal("  " + (mode == Mode.SOLO ? "Solo" : "Team")).withStyle(ChatFormatting.AQUA), false);
            }
            if (entries.isEmpty()) source.sendSuccess(() -> Component.literal("    no entries yet").withStyle(ChatFormatting.GRAY), false);
            for (int i = 0; i < entries.size(); i++) {
                String row = MasteryView.row(board, i + 1, entries.get(i));
                source.sendSuccess(() -> Component.literal("    " + row), false);
                shown++;
            }
        }
        return shown;
    }

    // ---- operators -------------------------------------------------------------------------------------------------

    private static int grant(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation tower = ResourceLocationArgument.getId(context, "tower");
        String which = StringArgumentType.getString(context, "achievement");
        TowerMasteryStore store = TowerMasteryStore.get(source.getServer());
        int granted = 0;
        for (AchievementDefinition achievement : AchievementRegistry.all()) {
            boolean match = which.equals("all") || achievement.id().getPath().equals(which);
            if (match && store.unlock(player.getUUID(), tower, achievement.id(), System.currentTimeMillis())) granted++;
        }
        store.checkpoint(source.getServer());
        int level = store.progressOf(player.getUUID(), tower).level();
        int count = granted;
        source.sendSuccess(() -> Component.literal("Granted " + count + " achievement(s); " + player.getGameProfile().getName()
                + " is now level " + level + " in " + tower), true);
        return granted;
    }

    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ResourceLocation tower = ResourceLocation.parse(ResourceLocationArgument.getId(context, "tower").toString());
        TowerMasteryStore store = TowerMasteryStore.get(source.getServer());
        store.reset(player.getUUID(), tower);
        store.checkpoint(source.getServer());
        source.sendSuccess(() -> Component.literal("Reset " + player.getGameProfile().getName() + "'s mastery of " + tower), true);
        return 1;
    }

    /** The tuning report: what the collected numbers say about the thresholds we guessed (see {@code TuningReport}). */
    private static int report(CommandContext<CommandSourceStack> context) {
        net.minecraft.server.MinecraftServer server = context.getSource().getServer();
        List<com.cobbletowers.mastery.TuningReport.Standing> standings = new java.util.ArrayList<>();
        TowerMasteryStore.get(server).all().forEach((player, byTower) -> byTower.forEach((tower, progress) ->
                standings.add(new com.cobbletowers.mastery.TuningReport.Standing(player, tower, progress.cyclesCleared(),
                        progress.ascensionReached(), progress.unlocked().keySet()))));
        List<com.cobbletowers.mastery.TuningReport.Achievement> achievements = new java.util.ArrayList<>();
        for (com.cobbletowers.definition.AchievementDefinition achievement : com.cobbletowers.definition.AchievementRegistry.all()) {
            achievements.add(new com.cobbletowers.mastery.TuningReport.Achievement(achievement.id(), achievement.displayName()));
        }
        List<com.cobbletowers.mastery.TuningReport.TrialAttempt> attempts = new java.util.ArrayList<>();
        for (com.cobbletowers.persistence.TowerTrialStore.Attempt attempt : com.cobbletowers.persistence.TowerTrialStore.get(server).allAttempts()) {
            attempts.add(new com.cobbletowers.mastery.TuningReport.TrialAttempt(attempt.instanceId(), attempt.finished(), attempt.score(),
                    attempt.floorsCleared()));
        }
        List<com.cobbletowers.mastery.TuningReport.Board> boards = new java.util.ArrayList<>();
        com.cobbletowers.persistence.TowerLeaderboardStore.get(server).all().forEach((key, entries) -> { if (!key.allTime()) return; boards.add(
                new com.cobbletowers.mastery.TuningReport.Board(key.board().title() + " / " + key.tower().getPath() + " / " + key.mode()
                        + (key.playlist().isEmpty() ? "" : " / " + key.playlist()), key.board().lowerIsBetter(),
                        entries.stream().map(com.cobbletowers.mastery.LeaderboardRules.Entry::value).toList())); });
        for (String line : com.cobbletowers.mastery.TuningReport.build(standings, achievements, attempts, boards)) {
            context.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int clearBoards(CommandContext<CommandSourceStack> context) {
        TowerLeaderboardStore store = TowerLeaderboardStore.get(context.getSource().getServer());
        store.clear();
        store.checkpoint(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("All leaderboards cleared."), true);
        return 1;
    }
}
