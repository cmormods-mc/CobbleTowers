package com.cobbletowers.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CommandGateTest {

    // The "source" is a boolean: true means operator.
    private static LiteralCommandNode<Boolean> gated() {
        LiteralCommandNode<Boolean> root = LiteralArgumentBuilder.<Boolean>literal("play")
                .then(LiteralArgumentBuilder.<Boolean>literal("menu").executes(c -> 1))
                .then(LiteralArgumentBuilder.<Boolean>literal("vote").executes(c -> 1)
                        .then(LiteralArgumentBuilder.<Boolean>literal("deep").executes(c -> 1)))
                .build();
        return CommandGate.opOnlyExcept(root, Set.of("menu"), Boolean::booleanValue);
    }

    @Test
    @DisplayName("a named entry point stays open to everyone")
    void openOnes() {
        assertTrue(gated().getChild("menu").canUse(false));
    }

    @Test
    @DisplayName("any other subcommand needs an operator, and keeps its own children")
    void gatedOnes() {
        var vote = gated().getChild("vote");
        assertFalse(vote.canUse(false));
        assertTrue(vote.canUse(true));
        assertTrue(vote.getChild("deep") != null);
    }

    @Test
    @DisplayName("a player reaches an open subcommand through the /tower alias even though the /cobbletowers root is op-only")
    void aliasOverOpRoot() throws Exception {
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<Boolean>();
        dispatcher.register(LiteralArgumentBuilder.<Boolean>literal("cobbletowers").requires(op -> op));
        var root = dispatcher.register(LiteralArgumentBuilder.<Boolean>literal("cobbletowers")
                .then(CommandGate.opOnlyExcept(LiteralArgumentBuilder.<Boolean>literal("play")
                        .then(LiteralArgumentBuilder.<Boolean>literal("help").executes(c -> 7))
                        .then(LiteralArgumentBuilder.<Boolean>literal("start").executes(c -> 8)).build(),
                        Set.of("help"), Boolean::booleanValue)));
        dispatcher.register(LiteralArgumentBuilder.<Boolean>literal("tower").executes(c -> 1).redirect(root.getChild("play")));

        assertTrue(dispatcher.execute("tower help", false) == 7);
        assertTrue(dispatcher.execute("tower start", true) == 8);
        org.junit.jupiter.api.Assertions.assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("tower start", false));
        org.junit.jupiter.api.Assertions.assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> dispatcher.execute("cobbletowers play help", false));
        assertFalse(dispatcher.getRoot().getChild("cobbletowers").canUse(false));
    }
}
