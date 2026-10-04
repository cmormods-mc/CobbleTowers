package com.cobbletowers.trial;

import com.cobbletowers.contract.ContractService;
import com.cobbletowers.persistence.TowerTrialStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The few lines a player sees when they log in (P32d): today's daily trial and where their streak stands, and how many contracts
 * are open. Short on purpose, and a player can turn it off with {@code /tower summary off}.
 */
public final class LoginSummary {

    private LoginSummary() {}

    public static void install() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            try {
                send(server, handler.getPlayer());
            } catch (RuntimeException ex) {
                com.cobbletowers.TowerLog.error("The login summary failed", ex);
            }
        });
    }

    /** What the summary says for a player, empty when it is off or there is nothing to say. */
    public static List<String> lines(MinecraftServer server, ServerPlayer player) {
        if (TowerTrialStore.get(server).isQuiet(player.getUUID())) return List.of();
        List<String> lines = new ArrayList<>();
        TrialService.summaryLine(server, player.getUUID()).ifPresent(lines::add);
        Optional<String> contracts = ContractService.summary(server, player.getUUID());
        contracts.ifPresent(lines::add);
        if (!lines.isEmpty()) lines.add(0, "CobbleTowers today:");
        return List.copyOf(lines);
    }

    private static void send(MinecraftServer server, ServerPlayer player) {
        List<String> lines = lines(server, player);
        for (int i = 0; i < lines.size(); i++) {
            player.sendSystemMessage(Component.literal((i == 0 ? "" : "  ") + lines.get(i))
                    .withStyle(i == 0 ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }
    }
}
