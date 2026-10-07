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
 * The party chooser (P18): everything the player owns, with the chosen Pokemon marked. Each click sends a {@link
 * PlayActionPayload} and the server answers with a fresh {@link RegistrationStatePayload}.
 */
public final class RegistrationScreen extends TowerScreen {

    private static final int MAX_PER_PAGE = 8;

    private RegistrationStatePayload state;
    private int page;

    public RegistrationScreen(RegistrationStatePayload state) {
        super(Component.literal("Choose your party"), TowerUi.Theme.PARTY);
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

    /**
     * How many rows fit above the controls: eight on a tall window, fewer on a short one, so the buttons are never
     * off the screen.
     */
    private int perPage() {
        return Math.max(3, Math.min(MAX_PER_PAGE, (height - 98) / 22));
    }

    private int pages() {
        return Math.max(1, (state.pokemon().size() + perPage() - 1) / perPage());
    }

    private void buildWidgets() {
        page = Math.min(page, pages() - 1);
        int left = width / 2 - 120;
        int y = 40;
        List<RegistrationStatePayload.Entry> all = state.pokemon();
        for (int i = page * perPage(); i < Math.min(all.size(), (page + 1) * perPage()); i++) {
            RegistrationStatePayload.Entry entry = all.get(i);
            int order = state.chosen().indexOf(entry.id());
            String mark = order >= 0 ? "[" + (order + 1) + "] " : "[ ] ";
            Button button = TowerButton.builder(Component.literal(mark + entry.name() + "  Lv " + entry.level()
                            + (entry.fainted() ? "  (fainted)" : "") + "  - " + entry.where()),
                            b -> send(PlayActionPayload.Action.TOGGLE_POKEMON, entry.id().toString()))
                    .pos(left, y).size(240, 20).build();
            addRenderableWidget(button);
            y += 22;
        }

        int bottom = Math.max(y + 6, 40 + perPage() * 22 + 6);
        Button previous = TowerButton.builder(Component.literal("<"), b -> turn(-1)).pos(left, bottom).size(30, 20).build();
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = TowerButton.builder(Component.literal(">"), b -> turn(1)).pos(left + 210, bottom).size(30, 20).build();
        next.active = page < pages() - 1;
        addRenderableWidget(next);
        addRenderableWidget(TowerButton.builder(Component.literal("Clear"), b -> send(PlayActionPayload.Action.CLEAR_CHOICE, ""))
                .pos(left + 36, bottom).size(80, 20).build());
        addRenderableWidget(TowerButton.builder(Component.literal("Done"), b -> done())
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

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);TowerUi.panel(g,width/2-127,35,254,height-73,theme.accent);
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, TowerUi.TEXT);
        String hint = state.chosen().isEmpty()
                ? "Nothing chosen: your current party will be used"
                : state.chosen().size() + " of " + state.max() + " registered";
        graphics.drawCenteredString(font, hint, width / 2, 26, TowerUi.MUTED);
        graphics.drawCenteredString(font, "Page " + (page + 1) + "/" + pages(), width / 2, height - 28, 0xFF8F7A5E);
        if (!state.message().isEmpty()) {
            graphics.drawCenteredString(font, state.message(), width / 2, height - 14, TowerUi.TEXT);
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
