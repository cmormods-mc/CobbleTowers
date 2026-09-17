package com.cobbletowers.command;

import com.cobbletowers.api.registry.TowerSummary;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.runtime.RunTransitions;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * {@code /cobbletowers definitions} and {@code /cobbletowers transitions}: what the server loaded,
 * and the state machine it will run.
 *
 * <p>Read-only, and the only way to see a datapack problem in game rather than in the log. P1 has no
 * runtime, so this is also the live proof that the content reached the server.
 */
public final class DefinitionsCommand {

    private DefinitionsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("definitions").executes(DefinitionsCommand::definitions))
                .then(Commands.literal("transitions").executes(DefinitionsCommand::transitions)));
    }

    private static int definitions(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        TowerContent content = TowerDefinitionRegistry.content();

        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "%d tower(s), %d definition file(s) loaded", content.towers().size(), content.definitionCount()))
                .withStyle(ChatFormatting.GOLD), false);

        for (ResourceLocation towerId : content.sortedTowerIds()) {
            TowerSummary summary = content.summary(towerId).orElseThrow();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  \"%s\"  rev %d  %s  %d floor(s), milestones %s",
                    summary.id(), summary.displayName(), summary.revision(),
                    // The first eight hex characters are enough to see that content changed.
                    summary.contentDigest().isEmpty() ? "no digest" : summary.contentDigest().substring(0, 8),
                    summary.floorCount(), summary.milestoneFloors())), false);
        }

        if (content.problems().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  every reference resolves")
                    .withStyle(ChatFormatting.GREEN), false);
        } else {
            source.sendSuccess(() -> Component.literal("  " + content.problems().size() + " problem(s):")
                    .withStyle(ChatFormatting.RED), false);
            for (String problem : content.problems()) {
                source.sendSuccess(() -> Component.literal("    " + problem).withStyle(ChatFormatting.RED), false);
            }
        }
        return content.towers().size();
    }

    private static int transitions(CommandContext<CommandSourceStack> context) {
        for (String line : RunTransitions.describe()) {
            context.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        }
        return RunTransitions.describe().size();
    }
}
