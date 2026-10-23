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
}
