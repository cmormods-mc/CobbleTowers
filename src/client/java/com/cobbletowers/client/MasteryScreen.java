package com.cobbletowers.client;

import com.cobbletowers.network.MasteryRequestPayload;
import com.cobbletowers.network.MasteryScreenPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mastery and leaderboards (P31). Towers down the left, tabs along the top (the player's own achievements, then the four
 * boards), and a scrolling list in the middle. Every click is a request to the server, which answers with a fresh payload that
 * redraws this screen in place; the screen holds no state of its own beyond the scroll position.
 *
 * <p>Not seen in a real client at the time of writing: headless test bots cannot open a screen. Nothing here decides anything.
 */
public final class MasteryScreen extends Screen {

    private static final String[] TABS = {"mastery", "speed", "ascension", "difficulty", "clears"};
    private static final String[] TAB_NAMES = {"Mastery", "Speed", "Ascension", "Difficulty", "Clears"};
    private static final int LINE = 11;

    private MasteryScreenPayload state;
    private int scroll;

    public MasteryScreen(MasteryScreenPayload state) {
        super(Component.literal("Tower Mastery"));
        this.state = state;
    }

    /** A refreshed view arrived while the screen is open. */
    public void update(MasteryScreenPayload next) {
        boolean sameView = next.selected().equals(state.selected()) && next.tab().equals(state.tab());
        this.state = next;
        if (!sameView) scroll = 0;
        clearWidgets();
        buildWidgets();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        int left = 10;
        int y = 30;
        for (MasteryScreenPayload.Tower tower : state.towers()) {
            boolean chosen = tower.id().toString().equals(state.selected());
            Button button = Button.builder(Component.literal((chosen ? "> " : "") + tower.displayName() + " L" + tower.level()),
                            b -> request(tower.id().toString(), state.tab()))
                    .pos(left, y).size(130, 20).build();
            button.active = !chosen;
            addRenderableWidget(button);
            y += 22;
        }
        int x = 150;
        int each = Math.max(50, Math.min(80, (width - x - 10) / TABS.length));
        for (int i = 0; i < TABS.length; i++) {
            String tab = TABS[i];
            Button button = Button.builder(Component.literal(TAB_NAMES[i]), b -> request(state.selected(), tab))
                    .pos(x + i * each, 8).size(each - 2, 18).build();
            button.active = !tab.equals(state.tab());
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(left, height - 28).size(130, 20).build());
    }

    private void request(String tower, String tab) {
        if (ClientPlayNetworking.canSend(MasteryRequestPayload.TYPE)) {
            ClientPlayNetworking.send(new MasteryRequestPayload(tower, tab));
        }
    }

    /** The lines of the current view, each with its colour. */
    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        if ("mastery".equals(state.tab())) {
            MasteryScreenPayload.Mastery mastery = state.mastery();
            lines.add(new Line(mastery.progress(), 0xFFD700));
            lines.add(new Line("Perks: " + mastery.perks(), 0x55FFFF));
            lines.add(new Line("", 0xFFFFFF));
            for (MasteryScreenPayload.Achievement achievement : mastery.achievements()) {
                lines.add(new Line((achievement.held() ? "[x] " : "[ ] ") + achievement.name() + " - " + achievement.description(),
                        achievement.held() ? 0x55FF55 : 0xAAAAAA));
            }
            return lines;
        }
        MasteryScreenPayload.Board board = state.board();
        lines.add(new Line(board.title(), 0xFFD700));
        if (board.split()) {
            addRows(lines, "Solo", board.solo());
            addRows(lines, "Team", board.team());
        } else {
            addRows(lines, null, board.solo());
        }
        return lines;
    }

    private static void addRows(List<Line> lines, String heading, List<String> rows) {
        if (heading != null) lines.add(new Line(heading, 0x55FFFF));
        if (rows.isEmpty()) lines.add(new Line("  no entries yet", 0xAAAAAA));
        for (String row : rows) lines.add(new Line("  " + row, 0xFFFFFF));
    }

    private record Line(String text, int color) {}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int visible = Math.max(1, (height - 60) / LINE);
        int max = Math.max(0, lines().size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical) * 3));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = 150;
        int y = 36;
        int visible = Math.max(1, (height - 60) / LINE);
        List<Line> lines = lines();
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            Line line = lines.get(i);
            graphics.drawString(font, font.plainSubstrByWidth(line.text(), width - x - 10), x, y, line.color());
            y += LINE;
        }
        if (lines.size() > visible) {
            graphics.drawString(font, "scroll for more (" + (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible)
                    + " of " + lines.size() + ")", x, height - 18, 0x888888);
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
