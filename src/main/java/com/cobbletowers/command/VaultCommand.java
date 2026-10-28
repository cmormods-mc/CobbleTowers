package com.cobbletowers.command;

import com.cobbletowers.halloween.vault.VaultFacing;
import com.cobbletowers.halloween.vault.VaultMachine;
import com.cobbletowers.halloween.vault.VaultService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Operator tools for the Jack-o'-Lantern Vault: {@code /cobbletowers vault spawn [facing]} (at the sender's feet), {@code start <id> <treat|trick>},
 * {@code close <id>}, {@code reset <id>}, {@code remove <id>}, {@code list}. A tool for placing and testing, not a player command.
 */
public final class VaultCommand {

    private VaultCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers").then(Commands.literal("vault")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("spawn")
                        .executes(context -> spawn(context, "north"))
                        .then(Commands.argument("facing", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"north", "east", "south", "west"}, b))
                                .executes(context -> spawn(context, StringArgumentType.getString(context, "facing")))))
                .then(Commands.literal("start")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .then(Commands.argument("outcome", StringArgumentType.word())
                                        .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[] {"treat", "trick"}, b))
                                        .executes(context -> {
                                            UUID id = UuidArgument.getUuid(context, "id");
                                            VaultMachine.Outcome outcome = VaultMachine.Outcome.valueOf(StringArgumentType.getString(context, "outcome").toUpperCase(java.util.Locale.ROOT));
                                            return reply(context, VaultService.start(id, outcome), "started", "not closed, or no such vault");
                                        }))))
                .then(Commands.literal("close").then(Commands.argument("id", UuidArgument.uuid()).executes(context ->
                        reply(context, VaultService.close(UuidArgument.getUuid(context, "id")), "closing", "not open, or no such vault"))))
                .then(Commands.literal("reset").then(Commands.argument("id", UuidArgument.uuid()).executes(context ->
                        reply(context, VaultService.reset(UuidArgument.getUuid(context, "id")), "reset", "no such vault"))))
                .then(Commands.literal("remove").then(Commands.argument("id", UuidArgument.uuid()).executes(context ->
                        reply(context, VaultService.remove(UuidArgument.getUuid(context, "id")), "removed", "no such vault"))))
                .then(Commands.literal("list").executes(context -> {
                    var lines = VaultService.describe();
                    context.getSource().sendSuccess(() -> Component.literal(lines.isEmpty() ? "No vaults." : String.join("\n", lines)), false);
                    return lines.size();
                }))));
    }

    private static int spawn(CommandContext<CommandSourceStack> context, String facing) {
        CommandSourceStack source = context.getSource();
        VaultFacing f;
        try {
            f = VaultFacing.parse(facing);
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("facing must be north, east, south or west"));
            return 0;
        }
        UUID id = VaultService.spawn(source.getLevel(), BlockPos.containing(source.getPosition()), f);
        source.sendSuccess(() -> Component.literal("Vault " + id + " spawned facing " + facing), true);
        return 1;
    }

    private static int reply(CommandContext<CommandSourceStack> context, boolean ok, String yes, String no) {
        if (ok) context.getSource().sendSuccess(() -> Component.literal("Vault " + yes), true);
        else context.getSource().sendFailure(Component.literal("Vault: " + no));
        return ok ? 1 : 0;
    }
}
