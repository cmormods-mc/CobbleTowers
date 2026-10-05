package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared Hall chrome. Controls never move independently of their hitboxes. */
public abstract class TowerMenuScreen extends TowerScreen {
    protected int contentX, contentY, contentWidth, contentHeight;
    protected String section = "Tower Hall";
    protected TowerMenuScreen() { super(Component.literal("CobbleTowers"), TowerUi.Theme.LOBBY); }

    protected void layoutShell() {
        int rail = width < 400 ? 72 : 86;
        contentX=rail+12; contentY=43; contentWidth=width-contentX-12; contentHeight=height-contentY-58;
        String[] labels={"Tower Hall","Trials","Codex","Progress","Echoes","Social","Collection"};
        for(int i=0;i<labels.length;i++) {
            String label=labels[i];
            var button=TowerButton.builder(Component.literal(label),b->navigate(label)).pos(9,43+i*22).size(rail-10,20).dark().build();
            button.active=!section.equals(label);
            addRenderableWidget(button);
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Settings"),b->minecraft.setScreen(new TowerOptionsScreen(this)))
                .pos(9,height-30).size(rail-10,20).dark().build());
        addRenderableWidget(TowerButton.builder(Component.literal("Back"),b->onClose()).pos(width-54,10).size(42,20).dark().build());
    }

    protected abstract void navigate(String section);
    protected abstract void drawContent(GuiGraphics g);

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {
        super.renderBackground(g,mx,my,delta);
        TowerUi.panel(g,6,6,width-12,31,theme.accent);
        TowerUi.panel(g,6,40,contentX-14,height-46,theme.accent);
        g.drawString(font,"COBBLE TOWERS",15,12,TowerUi.TEXT,false);
        g.drawString(font,section,15,25,TowerUi.MUTED,false);
        drawContent(g);
    }

    @Override protected boolean showUiHint() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
