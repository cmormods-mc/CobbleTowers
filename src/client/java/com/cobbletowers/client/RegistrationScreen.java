package com.cobbletowers.client;

import com.cobbletowers.network.PlayActionPayload;
import com.cobbletowers.network.RegistrationStatePayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The party chooser (P18): everything the player owns, party and boxes, with the ones they have chosen to
 * register marked. Holds nothing of its own -- each click sends a {@link PlayActionPayload} and the
 * server answers with a fresh {@link RegistrationStatePayload}.
 */
public final class RegistrationScreen extends Screen {

    private static final int PER_PAGE = 8;

    private RegistrationStatePayload state;
    private int page;

    public RegistrationScreen(RegistrationStatePayload state) {
        super(Component.literal("Choose your party"));
        this.state = state;
    }

    public void update(RegistrationStatePayload next) {
        this.state = next;
        clearWidgets();
        buildWidgets();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private int pages() {
        return Math.max(1, (state.pokemon().size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void buildWidgets() {
        page = Math.min(page, pages() - 1);
        int left = width / 2 - 120;
        int y = 40;
        List<RegistrationStatePayload.Entry> all = state.pokemon();
        for (int i = page * PER_PAGE; i < Math.min(all.size(), (page + 1) * PER_PAGE); i++) {
            RegistrationStatePayload.Entry entry = all.get(i);
            int order = state.chosen().indexOf(entry.id());
            String mark = order >= 0 ? "[" + (order + 1) + "] " : "[ ] ";
            Button button = Button.builder(Component.literal(mark + entry.name() + "  Lv " + entry.level()
                            + (entry.fainted() ? "  (fainted)" : "") + "  - " + entry.where()),
                            b -> send(PlayActionPayload.Action.TOGGLE_POKEMON, entry.id().toString()))
                    .pos(left, y).size(240, 20).build();
            addRenderableWidget(button);
            y += 22;
        }

        int bottom = Math.max(y + 6, 40 + PER_PAGE * 22 + 6);
        Button previous = Button.builder(Component.literal("<"), b -> turn(-1)).pos(left, bottom).size(30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), b -> turn(1)).pos(left + 210, bottom).size(30, 20).build();
        next.active = page < pages() - 1;
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(Component.literal("Clear"), b -> send(PlayActionPayload.Action.CLEAR_CHOICE, ""))
                .pos(left + 36, bottom).size(80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> done())
                .pos(left + 124, bottom).size(80, 20).build());
    }

    private void turn(int by) {
        page = Math.max(0, Math.min(pages() - 1, page + by));
        clearWidgets();
        buildWidgets();
    }

    private void done() {
        // Back to the play screen: REFRESH makes the server answer with the lobby state, opened, which
        // replaces this screen.
        send(PlayActionPayload.Action.REFRESH, "");
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
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        String hint = state.chosen().isEmpty()
                ? "Nothing chosen: your current party will be used"
                : state.chosen().size() + " of " + state.max() + " registered";
        graphics.drawCenteredString(font, hint, width / 2, 26, 0xAAAAAA);
        graphics.drawCenteredString(font, "Page " + (page + 1) + "/" + pages(), width / 2, height - 28, 0x888888);
        if (!state.message().isEmpty()) {
            graphics.drawCenteredString(font, state.message(), width / 2, height - 14, 0xFFFFFF);
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
