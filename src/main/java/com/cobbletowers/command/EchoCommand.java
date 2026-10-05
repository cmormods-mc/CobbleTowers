package com.cobbletowers.command;

import com.cobbletowers.echo.Echo;
import com.cobbletowers.echo.EchoPolicy;
import com.cobbletowers.echo.EchoService;
import com.cobbletowers.persistence.TowerEchoStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Echoes (P35): what a player can see and switch ({@code /tower echo [off|on]}), and operator tools to record and clear them
 * (a live test has no top-ten run to earn one with).
 */
public final class EchoCommand {

    private EchoCommand() {}

    /** The subcommands players use, to be added under {@code /cobbletowers play}. */
    public static List<LiteralArgumentBuilder<CommandSourceStack>> playerCommands() {
        return List.of(Commands.literal("echo")
                .executes(EchoCommand::show)
                .then(Commands.literal("off").executes(context -> opt(context, true)))
                .then(Commands.literal("on").executes(context -> opt(context, false))));
    }

    /** {@code /cobbletowers echoes ...}: operator only. */
    public static void registerAdmin(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("echoes")
                .requires(source -> source.hasPermission(2))
                .executes(EchoCommand::list)
                .then(Commands.literal("list").executes(EchoCommand::list))
                .then(Commands.literal("clear").executes(context -> {
                    TowerEchoStore.get(context.getSource().getServer()).clear();
                    return say(context, "Every Echo and opt-out cleared.");
                }))
                .then(Commands.literal("add")
                        .then(Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .then(Commands.argument("pokemon", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                                .executes(EchoCommand::add)))))
                .then(Commands.literal("endbattle")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> say(context, com.cobbletowers.battle.cobblemon.CobblemonBattleAdapter
                                        .endBattleOf(EntityArgument.getPlayer(context, "player"))
                                        ? "Ended the player's battle." : "The player is not in a battle."))))
                .then(Commands.literal("record")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("tower", ResourceLocationArgument.id())
                                        .executes(EchoCommand::record))))));
    }

    private static int show(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TowerEchoStore store = TowerEchoStore.get(context.getSource().getServer());
        if (store.isOptedOut(player.getUUID())) {
            return say(context, "You have opted out: your teams are never recorded as Echoes. /tower echo on to allow it.");
        }
        List<Echo> mine = store.ownedBy(player.getUUID());
        if (mine.isEmpty()) {
            return say(context, "No Echo of yours yet. A team in the top " + EchoPolicy.TOP_N
                    + " of a regional tower is recorded automatically; /tower echo off opts out.");
        }
        for (Echo echo : mine) {
            say(context, echo.tower().getPath() + ": " + echo.team().stream().map(EchoPolicy::speciesOf)
                    .collect(java.util.stream.Collectors.joining(", ")) + " -- met by " + echo.faced() + " challenger(s), won " + echo.beat() + " duel(s)");
        }
        return 1;
    }

    private static int opt(CommandContext<CommandSourceStack> context, boolean out) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TowerEchoStore store = TowerEchoStore.get(context.getSource().getServer());
        int removed = store.setOptedOut(player.getUUID(), out);
        store.checkpoint(context.getSource().getServer());
        return say(context, out ? "Opted out. " + removed + " Echo(es) of yours removed from the pool now."
                : "Opted back in. Your next top-" + EchoPolicy.TOP_N + " regional run will be recorded.");
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        TowerEchoStore store = TowerEchoStore.get(context.getSource().getServer());
        say(context, store.count() + " Echo(es) recorded.");
        for (var tower : com.cobbletowers.definition.TowerDefinitionRegistry.content().towers().keySet()) {
            for (Echo echo : store.forTower(tower)) {
                say(context, tower.getPath() + " | season " + echo.season() + " | " + echo.name() + " | " + echo.team().size() + " Pokemon | faced "
                        + echo.faced() + " | run " + echo.runId());
            }
        }
        return 1;
    }

    /** Records an Echo of an invented owner from one property string: the seam a live test uses to have someone else's team to meet. */
    private static int add(CommandContext<CommandSourceStack> context) {
        String name = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "name");
        var tower = ResourceLocationArgument.getId(context, "tower");
        String pokemon = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "pokemon");
        TowerEchoStore.get(context.getSource().getServer()).add(new Echo(UUID.randomUUID(), UUID.randomUUID(), name, tower,
                UUID.randomUUID(), List.of(pokemon), System.currentTimeMillis(), 0, 0,
                com.cobbletowers.season.Seasons.viewNumber().orElse(0)));
        return say(context, "Recorded an Echo of " + name + " on " + tower + ": " + EchoPolicy.speciesOf(pokemon) + ".");
    }

    private static int record(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        var tower = ResourceLocationArgument.getId(context, "tower");
        List<String> team = EchoService.teamOf(player);
        if (team.isEmpty()) return say(context, player.getGameProfile().getName() + " has no Pokemon to record.");
        TowerEchoStore store = TowerEchoStore.get(context.getSource().getServer());
        store.add(new Echo(UUID.randomUUID(), player.getUUID(), player.getGameProfile().getName(), tower, UUID.randomUUID(),
                team, System.currentTimeMillis(), 0, 0, com.cobbletowers.season.Seasons.viewNumber().orElse(0)));
        return say(context, "Recorded an Echo of " + player.getGameProfile().getName() + " on " + tower + ": " + team.size()
                + " Pokemon. (A recorded Echo with no top-ten run leaves at the next refresh of that tower's boards.)");
    }

    private static int say(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
