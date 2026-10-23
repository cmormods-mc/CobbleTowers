package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared Hall chrome: dark oak header and tab rail around a parchment canvas. Text on the canvas is {@link
 * TowerUi#INK}; cards and panels on it use cream.
 */
public abstract class TowerMenuScreen extends TowerScreen {
    protected int contentX, contentY, contentWidth, contentHeight;
    protected String section = "Tower Hall";
    protected TowerMenuScreen() { super(Component.literal("CobbleTowers"), TowerUi.Theme.LOBBY); }

    protected void layoutShell() {
        int rail = width < 400 ? 80 : 96;
        contentX=rail+12; contentY=43; contentWidth=width-contentX-12; contentHeight=height-contentY-58;
        String[] labels={"Tower Hall","Trials","Codex","Progress","Echoes","Social","Collection"};
        String[] icons={"tower","hourglass","book","stairs","echo","group","cards"};
        for(int i=0;i<labels.length;i++) {
            String label=labels[i];
            var button=TowerButton.builder(Component.literal(label),b->navigate(label)).pos(9,43+i*22).size(rail-10,20).dark().icon(icons[i]).build();
            if(section.equals(label)){button.active=false;button.selected=true;}
            addRenderableWidget(button);
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Settings"),b->minecraft.setScreen(new TowerOptionsScreen(this)))
                .pos(9,height-30).size(rail-10,20).dark().icon("gear").build());
        addRenderableWidget(TowerButton.builder(Component.literal("Back"),b->onClose()).pos(width-100,10).size(42,20).dark().build());
        addRenderableWidget(TowerButton.builder(Component.literal("Close"),b->closeAll()).pos(width-54,10).size(42,20).dark().build());
    }

    protected abstract void navigate(String section);
    protected abstract void drawContent(GuiGraphics g);

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {
        super.renderBackground(g,mx,my,delta);
        TowerUi.panel(g,6,6,width-12,31,theme.accent);
        TowerUi.panel(g,6,40,contentX-14,height-46,theme.accent);
        PixelUi.sheet(g,contentX-5,contentY-4,contentWidth+10,height-contentY-2);
        g.drawString(font,"COBBLE TOWERS",15,12,TowerUi.BRONZE_LIGHT,false);
        g.drawString(font,section,15,25,TowerUi.MUTED,false);
        drawContent(g);
    }

    @Override protected boolean showUiHint() { return false; }
    @Override protected boolean cornerClose() { return false; }
    @Override public boolean isPauseScreen() { return false; }
}
