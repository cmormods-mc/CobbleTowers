package com.cobbletowers.command;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Set;

/**
 * Operator-gates a command tree's subcommands, except the named few. The screens do everything the typed commands do,
 * so only the entry points stay open to players; the rest remain for operators (support, testing).
 */
final class CommandGate {

    private CommandGate() {}

    /** A copy of {@code root} whose direct literal children need operator level 2 unless named in {@code open}. */
    static <S> LiteralCommandNode<S> opOnlyExcept(
            LiteralCommandNode<S> root, Set<String> open, java.util.function.Predicate<S> isOp) {
        LiteralCommandNode<S> copy = new LiteralCommandNode<>(root.getLiteral(), root.getCommand(), root.getRequirement(),
                root.getRedirect(), root.getRedirectModifier(), root.isFork());
        for (CommandNode<S> child : root.getChildren()) {
            copy.addChild(child instanceof LiteralCommandNode<S> literal && !open.contains(literal.getLiteral())
                    ? opOnly(literal, isOp) : child);
        }
        return copy;
    }

    private static <S> LiteralCommandNode<S> opOnly(LiteralCommandNode<S> node, java.util.function.Predicate<S> isOp) {
        LiteralCommandNode<S> copy = new LiteralCommandNode<>(node.getLiteral(), node.getCommand(),
                source -> isOp.test(source) && node.getRequirement().test(source),
                node.getRedirect(), node.getRedirectModifier(), node.isFork());
        for (CommandNode<S> child : node.getChildren()) copy.addChild(child);
        return copy;
    }
}
