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
public final class MasteryScreen extends TowerScreen {

    private static final String[] TABS = {"mastery", "speed", "ascension", "difficulty", "clears"};
    private static final String[] TAB_NAMES = {"Mastery", "Speed", "Ascension", "Difficulty", "Clears"};
    private static final int LINE = 11;

    private MasteryScreenPayload state;
    private int scroll,nodePage,selectedNode;
    private long inspectedAt=System.nanoTime();
    private int nodesPerPage(){return Math.max(1,(height-90)/45)*2;}

    public MasteryScreen(MasteryScreenPayload state) {
        super(Component.literal("Tower Mastery"), TowerUi.Theme.MASTERY);
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
            Button button = TowerButton.builder(Component.literal((chosen ? "> " : "") + tower.displayName() + " L" + tower.level()),
                            b -> request(tower.id().toString(), state.tab()))
                    .pos(left, y).size(94, 20).build();
            button.active = !chosen;
            addRenderableWidget(button);
            y += 22;
        }
        int x = 112;
        // Each tab is as wide as its word needs, with the same small margin, so none is clipped on a narrow window.
        int tabX = x;
        for (int i = 0; i < TABS.length; i++) {
            String tab = TABS[i];
            int wide = Math.min(font.width(TAB_NAMES[i]) + 12,(width-122)/5-2);
            Button button = TowerButton.builder(Component.literal(TAB_NAMES[i]), b -> request(state.selected(), tab))
                    .pos(tabX, 8).size(wide, 18).build();
            tabX += wide + 2;
            button.active = !tab.equals(state.tab());
            addRenderableWidget(button);
        }
        if("mastery".equals(state.tab())){
            var nodes=state.mastery().achievements();int count=nodesPerPage();nodePage=Math.min(nodePage,Math.max(0,(nodes.size()-1)/count));
            int nw=(width-234)/2;
            for(int i=0;i<count&&nodePage*count+i<nodes.size();i++){
                int index=nodePage*count+i;var node=nodes.get(index);
                addRenderableWidget(new AchievementNode(112+(i%2)*(nw+4),66+(i/2)*45,nw,node.name(),node.description(),node.held(),()->{selectedNode=index;inspectedAt=System.nanoTime();}));
            }
            if(nodes.size()>count){addRenderableWidget(TowerButton.builder(Component.literal("<"),b->{nodePage=Math.max(0,nodePage-1);clearWidgets();buildWidgets();}).pos(112,height-27).size(26,18).build());addRenderableWidget(TowerButton.builder(Component.literal(">"),b->{nodePage=Math.min((nodes.size()-1)/count,nodePage+1);clearWidgets();buildWidgets();}).pos(142,height-27).size(26,18).build());}
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Close"), b -> onClose())
                .pos(left, height - 28).size(94, 20).build());
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
            lines.add(new Line("Perks: " + mastery.perks(), TowerUi.BRONZE_LIGHT));
            lines.add(new Line("", TowerUi.TEXT));
            for (MasteryScreenPayload.Achievement achievement : mastery.achievements()) {
                lines.add(new Line((achievement.held() ? "[x] " : "[ ] ") + achievement.name() + " - " + achievement.description(),
                        achievement.held() ? TowerUi.SAGE : TowerUi.MUTED));
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
        if (heading != null) lines.add(new Line(heading, TowerUi.BRONZE_LIGHT));
        if (rows.isEmpty()) lines.add(new Line("  no entries yet", TowerUi.MUTED));
        for (String row : rows) lines.add(new Line("  " + row, TowerUi.TEXT));
    }

    private record Line(String text, int color) {}

    /** A line of the view as drawn: one row of a wrapped line, indented when it continues the one above. */
    private record Visual(net.minecraft.util.FormattedCharSequence text, int color, int indent) {}

    /** The view wrapped to the width of the panel: a long achievement description runs on to a second row rather than being cut off. */
    private List<Visual> visuals() {
        int wrap = Math.max(60, width - 112 - 10);
        List<Visual> out = new ArrayList<>();
        for (Line line : lines()) {
            if (line.text().isEmpty()) {
                out.add(new Visual(net.minecraft.util.FormattedCharSequence.EMPTY, line.color(), 0));
                continue;
            }
            boolean first = true;
            for (net.minecraft.util.FormattedCharSequence part : font.split(Component.literal(line.text()), wrap - 12)) {
                out.add(new Visual(part, line.color(), first ? 0 : 12));
                first = false;
            }
        }
        return out;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if("mastery".equals(state.tab())){int max=Math.max(0,(state.mastery().achievements().size()-1)/nodesPerPage());nodePage=Math.max(0,Math.min(max,nodePage-(int)Math.signum(vertical)));clearWidgets();buildWidgets();return true;}
        int visible = Math.max(1, (height - 60) / LINE);
        int max = Math.max(0, visuals().size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(vertical) * 3));
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if("mastery".equals(state.tab())){
            TowerUi.label(graphics,font,state.mastery().progress(),112,34,width-124,TowerUi.BRONZE_LIGHT);
            TowerUi.label(graphics,font,"Perks: "+state.mastery().perks(),112,47,width-124,TowerUi.MUTED);
            var nodes=state.mastery().achievements();
            if(!nodes.isEmpty()){
                selectedNode=Math.min(selectedNode,nodes.size()-1);var node=nodes.get(selectedNode);int x=width-112;
                float t=TowerUiSettings.motion?Math.min(1,(System.nanoTime()-inspectedAt)/220_000_000f):1;
                graphics.enableScissor(x,63,width-8,height-34);graphics.pose().pushPose();graphics.pose().translate(12*Math.pow(1-t,3),0,0);
                TowerUi.panel(graphics,x,63,104,height-97,theme.accent);
                int y=TowerUi.wrapped(graphics,font,node.name(),x+7,72,91,TowerUi.TEXT)+10;
                y=TowerUi.wrapped(graphics,font,node.held()?"EARNED":"NOT YET EARNED",x+7,y,91,node.held()?TowerUi.SAGE:TowerUi.MUTED)+10;
                TowerUi.wrapped(graphics,font,node.description(),x+7,y,91,TowerUi.TEXT);graphics.pose().popPose();graphics.disableScissor();
            }return;
        }
        int x = 112;
        int y = 36;
        int visible = Math.max(1, (height - 60) / LINE);
        List<Visual> lines = visuals();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            Visual line = lines.get(i);
            graphics.drawString(font, line.text(), x + line.indent(), y, line.color());
            y += LINE;
        }
        if (lines.size() > visible) {
            graphics.drawString(font, "scroll for more (" + (scroll + 1) + "-" + Math.min(lines.size(), scroll + visible)
                    + " of " + lines.size() + ")", x, height - 18, 0xFF8F7A5E);
        }
    }

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);
        TowerUi.panel(g,6,27,101,height-62,theme.accent);
        if("mastery".equals(state.tab())){for(int x=112;x<width-116;x+=10)for(int y=62;y<height-35;y+=10)g.fill(x,y,x+1,y+1,0x304B5D73);}
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
