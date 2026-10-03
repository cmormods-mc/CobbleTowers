package com.cobbletowers.client;

import com.cobbletowers.network.PlayActionPayload;
import com.cobbletowers.network.PlayStatePayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The way into a run: pick a tower, invite a team, answer an invite, start.
 *
 * <p>Holds no state of its own beyond what the server last sent. Every button sends a
 * {@link PlayActionPayload} and the server answers with a fresh {@link PlayStatePayload}, so what is on
 * screen is always the server's view of the lobby rather than a guess at it.
 */
public final class PlayScreen extends Screen {

    private PlayStatePayload state;
    private EditBox inviteName;

    public PlayScreen(PlayStatePayload state) {
        super(Component.literal("Battle Tower"));
        this.state = state;
    }

    /** A fresh state arrived while this screen is open. */
    public void update(PlayStatePayload next) {
        String typed = inviteName == null ? "" : inviteName.getValue();
        this.state = next;
        clearWidgets();
        buildWidgets();
        if (inviteName != null) inviteName.setValue(typed);
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        PlayStatePayload.Lobby lobby = state.lobby();
        int left = width / 2 - 100;
        int y = 40;

        boolean canChoose = lobby.role() == 0 || lobby.role() == 1;
        for (PlayStatePayload.Tower tower : state.towers()) {
            boolean selected = tower.id().toString().equals(lobby.selected());
            Button button = Button.builder(Component.literal((selected ? "> " : "") + tower.displayName()),
                            b -> send(PlayActionPayload.Action.SELECT_TOWER, tower.id().toString()))
                    .pos(left, y).size(200, 20).build();
            button.active = canChoose && lobby.countdown() < 0;
            addRenderableWidget(button);
            y += 22;
        }

        y += 8;
        if (lobby.role() == 1 && lobby.countdown() < 0) {
            inviteName = new EditBox(font, left, y, 130, 20, Component.literal("Player name"));
            inviteName.setMaxLength(16);
            inviteName.setHint(Component.literal("Player name"));
            addRenderableWidget(inviteName);
            addRenderableWidget(Button.builder(Component.literal("Invite"), b -> invite())
                    .pos(left + 134, y).size(66, 20).build());
            y += 24;
        } else {
            inviteName = null;
        }

        if (lobby.role() == 2) {
            addRenderableWidget(Button.builder(Component.literal("Accept"),
                            b -> send(PlayActionPayload.Action.ACCEPT, lobby.hostName()))
                    .pos(left, y).size(98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Decline"),
                            b -> send(PlayActionPayload.Action.DECLINE, lobby.hostName()))
                    .pos(left + 102, y).size(98, 20).build());
            y += 24;
        }
        if (lobby.role() == 1) {
            Button start = Button.builder(Component.literal(lobby.countdown() >= 0
                            ? "Starting in " + lobby.countdown() + "..." : "Start"),
                    b -> send(PlayActionPayload.Action.START, "")).pos(left, y).size(98, 20).build();
            start.active = !lobby.selected().isEmpty() && lobby.countdown() < 0;
            addRenderableWidget(start);
        }
        if (lobby.role() != 0) {
            addRenderableWidget(Button.builder(Component.literal(lobby.role() == 1 ? "End team" : "Leave"),
                            b -> send(PlayActionPayload.Action.LEAVE, ""))
                    .pos(left + 102, y).size(98, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                    .pos(left + 102, y).size(98, 20).build());
        }
    }

    private void invite() {
        if (inviteName == null || inviteName.getValue().isBlank()) return;
        send(PlayActionPayload.Action.INVITE, inviteName.getValue().trim());
        inviteName.setValue("");
    }

    private void send(PlayActionPayload.Action action, String argument) {
        if (ClientPlayNetworking.canSend(PlayActionPayload.TYPE)) {
            ClientPlayNetworking.send(new PlayActionPayload(action, argument));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, "Your party: " + partyLine(state.partyLevels()), width / 2, 26, 0xAAAAAA);

        PlayStatePayload.Lobby lobby = state.lobby();
        int y = height - 20 - 10 * (lobby.members().size() + 3);
        if (lobby.role() != 0) {
            graphics.drawCenteredString(font, "Host: " + lobby.hostName(), width / 2, y, 0xFFFF55);
            y += 10;
            for (PlayStatePayload.Member member : lobby.members()) {
                graphics.drawCenteredString(font, member.name() + (member.accepted() ? "  ready" : "  invited"),
                        width / 2, y, member.accepted() ? 0x55FF55 : 0xAAAAAA);
                y += 10;
            }
        }
        if (!state.message().isEmpty()) {
            graphics.drawCenteredString(font, state.message(), width / 2, height - 14, 0xFFFFFF);
        }
    }

    private static String partyLine(List<Integer> levels) {
        if (levels.isEmpty()) return "no Pokemon";
        StringBuilder line = new StringBuilder();
        for (int level : levels) {
            if (line.length() > 0) line.append(", ");
            line.append("Lv ").append(level);
        }
        return line.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
