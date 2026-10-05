package com.cobbletowers.client;

import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.IntermissionStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The between-floors menu (P17): vote on the modifier, visit the vendor, ready up, and vote on cashing out.
 *
 * <p>Holds nothing of its own. Every button sends an {@link IntermissionActionPayload} and the server
 * answers with a fresh {@link IntermissionStatePayload}, so what is shown is the server's view of the team.
 */
public final class IntermissionScreen extends TowerScreen {

    private IntermissionStatePayload state;

    public IntermissionScreen(IntermissionStatePayload state) {
        super(Component.literal("Intermission"), TowerUi.Theme.MODIFIER);
        this.state = state;
    }

    /** A fresh state arrived while this screen is open. */
    public void update(IntermissionStatePayload next) {
        this.state = next;
        clearWidgets();
        buildWidgets();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private boolean mine(boolean ready) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return false;
        String name = me.getGameProfile().getName();
        for (IntermissionStatePayload.Member member : state.members()) {
            if (member.name().equals(name)) return ready ? member.ready() : member.cashOut();
        }
        return false;
    }

    /** Where the buttons end, so the team list can sit just below them whatever the window height. */
    private int buttonsBottom;

    private void buildWidgets() {
        int left = width / 2 - 110;
        int y = 52;
        IntermissionStatePayload.Draft draft = state.draft();
        boolean open = draft.state() == 1;

        for (int i = 0; i < draft.cards().size(); i++) {
            IntermissionStatePayload.Card card = draft.cards().get(i);
            int index = i;
            String mark = draft.state() == 2 && draft.chosen() == i ? "[chosen] "
                    : draft.myVote() == i ? "> " : "";
            Button button = TowerButton.builder(
                            Component.literal(mark + card.displayName() + "  (" + card.votes() + ")"),
                            b -> send(IntermissionActionPayload.Action.PICK_CARD, index))
                    .pos(left, y).size(220, 18).build();
            button.active = open && state.countdown() < 0;
            addRenderableWidget(button);
            y += 20;
        }

        // Two buttons to a row, so a draft of four cards and a team of four still fit a short window.
        y += 4;
        addRenderableWidget(TowerButton.builder(Component.literal("Vendor"),
                        b -> send(IntermissionActionPayload.Action.VENDOR, 0))
                .pos(left, y).size(106, 18).build());
        boolean ready = mine(true);
        Button readyButton = TowerButton.builder(Component.literal(ready ? "Not ready" : "Ready"),
                        b -> send(ready ? IntermissionActionPayload.Action.UNREADY : IntermissionActionPayload.Action.READY, 0))
                .pos(left + 114, y).size(106, 18).build();
        readyButton.active = ready || !open;
        addRenderableWidget(readyButton);
        y += 22;

        boolean cashing = mine(false);
        addRenderableWidget(TowerButton.builder(Component.literal(cashing ? "Keep going" : "Cash out"),
                        b -> send(cashing ? IntermissionActionPayload.Action.STAY : IntermissionActionPayload.Action.CASH_OUT, 0))
                .pos(left, y).size(106, 18).build());
        addRenderableWidget(TowerButton.builder(Component.literal("Close"), b -> onClose())
                .pos(left + 114, y).size(106, 18).build());
        buttonsBottom = y + 18;
    }

    private void send(IntermissionActionPayload.Action action, int argument) {
        if (ClientPlayNetworking.canSend(IntermissionActionPayload.TYPE)) {
            ClientPlayNetworking.send(new IntermissionActionPayload(action, argument));
        }
    }

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){super.renderBackground(g,mx,my,dt);TowerUi.panel(g,width/2-119,43,238,height-70,theme.accent);}
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Floor " + state.floor() + " cleared", width / 2, 12, 0xFFFFFF);
        String hint = switch (state.draft().state()) {
            case 1 -> "Vote on a modifier for the next floor";
            case 2 -> "Modifier chosen";
            default -> "No modifier this floor";
        };
        graphics.drawCenteredString(font, hint, width / 2, 26, 0xAAAAAA);

        int y = buttonsBottom + 6;
        for (IntermissionStatePayload.Member member : state.members()) {
            String status = (member.ready() ? "ready" : "not ready") + (member.cashOut() ? ", cashing out" : "");
            graphics.drawCenteredString(font, member.name() + "  " + status, width / 2, y,
                    member.ready() ? 0x55FF55 : 0xAAAAAA);
            y += 10;
        }
        if (state.countdown() >= 0) {
            graphics.drawCenteredString(font, "Next floor in " + state.countdown() + "...", width / 2, 38, 0xFFFF55);
        }
        if (!state.message().isEmpty()) {
            graphics.drawCenteredString(font, state.message(), width / 2, Math.max(y + 2, height - 12), 0xFFFFFF);
        }
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
