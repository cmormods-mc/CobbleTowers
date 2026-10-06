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
public final class PlayScreen extends TowerScreen {

    private PlayStatePayload state;
    private EditBox inviteName;
    private final TowerHallScreen hall;

    public PlayScreen(PlayStatePayload state) { this(state, null); }

    public PlayScreen(PlayStatePayload state, TowerHallScreen hall) {
        super(Component.literal("Battle Tower"), TowerUi.Theme.LOBBY);
        this.state = state;
        this.hall = hall;
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

    /** Where the right-hand column controls end, so the team list can sit just under them. */
    private int controlsBottom;
    private int statusWidth(){return Math.max(104,(int)(width*.30));}

    /**
     * Two columns, so everything fits a short window (a 1080p screen at the automatic GUI scale is only 270 tall): the towers on the
     * left, and on the right whatever the player can do right now (invite, depth, mode, draft, start, leave), with the team under it.
     */
    private void buildWidgets() {
        PlayStatePayload.Lobby lobby = state.lobby();
        int column = Math.max(96, (width - statusWidth() - 32) / 2);
        int half = (column - 4) / 2;
        int leftX = 12;
        int rightX = 18 + column;

        // The towers: 22 apart when there is room, packed closer when there are many.
        boolean canChoose = lobby.role() == 0 || lobby.role() == 1;
        int towers = Math.max(1, state.towers().size());
        int step = Math.max(18, Math.min(40, (height - 66) / towers));
        int y = 40;
        for (PlayStatePayload.Tower tower : state.towers()) {
            boolean selected = tower.id().toString().equals(lobby.selected());
            String art=tower.id().getPath().contains("rootvale")?"machamp":tower.id().getPath().contains("duskvale")?"scizor":"blastoise";
            Button button=new TrainerCardButton(leftX,y,column,step-4,tower.displayName(),selected?"Selected destination":"Open expedition",art,theme.accent,()->send(PlayActionPayload.Action.SELECT_TOWER,tower.id().toString()));
            button.active = canChoose && lobby.countdown() < 0;
            addRenderableWidget(button);
            y += step;
        }

        int ry = 40;
        if (lobby.role() == 1 && lobby.countdown() < 0) {
            inviteName = new EditBox(font, rightX, ry, column - 70, 20, Component.literal("Player name"));
            inviteName.setMaxLength(16);
            inviteName.setHint(Component.literal("Name"));
            addRenderableWidget(inviteName);
            addRenderableWidget(TowerButton.builder(Component.literal("Invite"), b -> invite())
                    .pos(rightX + column - 66, ry).size(66, 20).build());
            ry += 22;
        } else {
            inviteName = null;
        }

        // Ascension (P30): the host cycles through the depths the whole team has reached; 0 is the ordinary start.
        PlayStatePayload.Depth depth = lobby.options().depth();
        if (lobby.role() == 1 && depth.offered()) {
            int next = depth.chosen() >= depth.max() ? 0 : depth.chosen() + 1;
            Button ascend = TowerButton.builder(Component.literal("Start at: " + (depth.chosen() == 0
                            ? "Floor 1" : "Ascension " + depth.chosen()) + (depth.max() > 0 ? "" : " (none reached)")),
                    button -> send(PlayActionPayload.Action.SET_ASCENSION, Integer.toString(next)))
                    .pos(rightX, ry).size(column, 20).build();
            ascend.active = depth.max() > 0 && lobby.countdown() < 0;
            addRenderableWidget(ascend);
            ry += 22;
        }

        // Mode (P32): the host cycles Standard and the playlists; the house rules apply to the whole team.
        PlayStatePayload.Modes modes = lobby.options().modes();
        if (lobby.role() == 1 && !modes.ids().isEmpty()) {
            int chosenIndex = modes.ids().indexOf(modes.chosen());
            int nextIndex = chosenIndex + 1 >= modes.ids().size() ? -1 : chosenIndex + 1;
            String nextId = nextIndex < 0 ? "standard" : modes.ids().get(nextIndex);
            String label = chosenIndex < 0 ? "Standard" : modes.names().get(chosenIndex);
            Button mode = TowerButton.builder(Component.literal("Mode: " + label),
                    button -> send(PlayActionPayload.Action.SET_PLAYLIST, nextId))
                    .pos(rightX, ry).size(column, 20).build();
            mode.active = lobby.countdown() < 0;
            addRenderableWidget(mode);
            ry += 22;
        }

        // The Rental Draft (P33): every member opens their own packs; the host cannot start until all are done.
        PlayStatePayload.Readiness readiness = modes.readiness();
        if (lobby.role() == 1 && !lobby.selected().isEmpty() && !readiness.confirmed() && lobby.countdown() < 0) {
            // The host settles on the mode first, whatever it is; a rental mode opens nobody's draft until they do.
            addRenderableWidget(TowerButton.builder(Component.literal("Confirm mode"),
                            button -> send(PlayActionPayload.Action.CONFIRM_MODE, ""))
                    .pos(rightX, ry).size(column, 20).build());
            ry += 22;
        }
        if (modes.rental() && (lobby.role() == 1 || lobby.role() == 3) && readiness.confirmed() && lobby.countdown() < 0) {
            addRenderableWidget(TowerButton.builder(Component.literal("Draft your team"),
                            button -> {
                                if (ClientPlayNetworking.canSend(com.cobbletowers.network.RentalDraftActionPayload.TYPE)) {
                                    ClientPlayNetworking.send(new com.cobbletowers.network.RentalDraftActionPayload(
                                            com.cobbletowers.network.RentalDraftActionPayload.Action.OPEN, 0, 0));
                                }
                            })
                    .pos(rightX, ry).size(column, 20).build());
            ry += 22;
            // Ready-up: only a finished draft can be readied, and the host starts once every member is.
            Button ready = TowerButton.builder(Component.literal(readiness.mine() ? "Ready (click to undo)" : "Ready up"),
                            button -> send(readiness.mine() ? PlayActionPayload.Action.UNREADY : PlayActionPayload.Action.READY, ""))
                    .pos(rightX, ry).size(column, 20).build();
            ready.active = readiness.drafted();
            addRenderableWidget(ready);
            ry += 22;
        }

        if (lobby.role() == 2) {
            addRenderableWidget(TowerButton.builder(Component.literal("Accept"),
                            b -> send(PlayActionPayload.Action.ACCEPT, lobby.hostName()))
                    .pos(rightX, ry).size(half, 20).build());
            addRenderableWidget(TowerButton.builder(Component.literal("Decline"),
                            b -> send(PlayActionPayload.Action.DECLINE, lobby.hostName()))
                    .pos(rightX + half + 4, ry).size(half, 20).build());
            ry += 22;
        }
        if (lobby.role() == 1) {
            Button start = TowerButton.builder(Component.literal(lobby.countdown() >= 0
                            ? "Starting in " + lobby.countdown() + "..." : "Start"),
                    b -> send(PlayActionPayload.Action.START, "")).pos(width-statusWidth()+6, height-48).size(statusWidth()-20, 22).build();
            start.active = !lobby.selected().isEmpty() && lobby.countdown() < 0 && lobby.options().modes().readiness().confirmed();
            addRenderableWidget(start);
            addRenderableWidget(TowerButton.builder(Component.literal("End team"), b -> send(PlayActionPayload.Action.LEAVE, ""))
                    .pos(rightX + half + 4, ry).size(half, 20).build());
            ry += 22;
        } else if (lobby.role() != 0) {
            addRenderableWidget(TowerButton.builder(Component.literal("Leave"), b -> send(PlayActionPayload.Action.LEAVE, ""))
                    .pos(rightX, ry).size(column, 20).build());
            ry += 22;
        } else {
            addRenderableWidget(TowerButton.builder(Component.literal("Close"), b -> onClose())
                    .pos(rightX, ry).size(column, 20).build());
            ry += 22;
        }
        if (lobby.role() == 1 || lobby.role() == 3) {
            addRenderableWidget(TowerButton.builder(Component.literal("Choose party"),
                            b -> send(PlayActionPayload.Action.OPEN_CHOOSER, ""))
                    .pos(rightX, ry).size(column, 20).build());
            ry += 22;
        }
        controlsBottom = ry;
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, TowerUi.TEXT);
        TowerUi.label(graphics,font,"Your party: " + partyLine(state.partyLevels()),12,26,width-statusWidth()-24,TowerUi.MUTED);

        PlayStatePayload.Lobby lobby = state.lobby();
        int column = Math.max(96, (width - statusWidth() - 32) / 2);
        int rightX = width-statusWidth()+4;
        column=statusWidth()-16;
        int y = 55;
        if (lobby.role() != 0) {
            boolean rental = lobby.options().modes().rental();
            graphics.drawString(font, font.plainSubstrByWidth("Host: " + lobby.hostName()
                    + (rental && lobby.options().modes().readiness().host() ? "  ready" : ""), column), rightX, y, TowerUi.BRONZE_LIGHT);
            y += 10;
            for (PlayStatePayload.Member member : lobby.members()) {
                graphics.drawString(font, font.plainSubstrByWidth(member.name() + (rental ? (member.accepted() ? (member.ready() ? "  ready" : "  drafting") : "  invited")
                        : (member.accepted() ? "  ready" : "  invited")), column),
                        rightX, y, (rental ? member.ready() : member.accepted()) ? TowerUi.SAGE : TowerUi.MUTED);
                y += 10;
            }
        }
        if (lobby.role() == 3 && !lobby.options().modes().readiness().confirmed()) {
            graphics.drawString(font, font.plainSubstrByWidth("Waiting for the host to confirm the mode", column), rightX, y + 4, TowerUi.MUTED);
        }
        if (!state.message().isEmpty()) {
            graphics.drawCenteredString(font, state.message(), width / 2, height - 14, TowerUi.TEXT);
        }
    }

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);
        TowerUi.panel(g,6,34,width-statusWidth()-10,height-56,theme.accent);
        TowerUi.panel(g,width-statusWidth(),34,statusWidth()-8,height-56,theme.accent);
        g.drawString(font,"TEAM STATUS",width-statusWidth()+7,42,TowerUi.MUTED,false);
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
        if (hall != null) hall.updateLobby(state);
        Minecraft.getInstance().setScreen(hall);
    }
}
