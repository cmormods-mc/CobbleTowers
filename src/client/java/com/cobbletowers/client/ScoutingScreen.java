package com.cobbletowers.client;
import com.cobbletowers.network.ScoutingRevealPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
/** Inspectable information nodes, not purchasable scouting upgrades. */
public final class ScoutingScreen extends TowerScreen {
    private final ScoutingRevealPayload payload;private int selected,page;private long inspectedAt=System.nanoTime();
    public ScoutingScreen(ScoutingRevealPayload payload){super(Component.literal("Scouting report"),TowerUi.Theme.SCOUT);this.payload=payload;}
    private int count(){return Math.max(1,(height-88)/38)*2;}
    @Override protected void init(){
        int cw=(width-150)/2;
        for(int i=0;i<count()&&page*count()+i<payload.categories().size();i++){int index=page*count()+i;var c=payload.categories().get(index);addRenderableWidget(TowerButton.builder(Component.literal(c.name()),b->{selected=index;inspectedAt=System.nanoTime();}).pos(12+i%2*(cw+4),50+i/2*38).size(cw-4,30).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(c.name()+": "+c.value()))).build());}
        if(payload.categories().size()>count())addRenderableWidget(TowerButton.builder(Component.literal("Next page"),b->{page=(page+1)%((payload.categories().size()+count()-1)/count());clearWidgets();init();}).pos(12,height-32).size(100,22).build());
        addRenderableWidget(TowerButton.builder(Component.literal("Close"),b->onClose()).pos(width-110,height-32).size(98,22).build());
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);g.drawString(font,"SCOUTING / FLOOR "+payload.floorIndex(),12,18,TowerUi.TEXT,false);
        for(int x=12;x<width-132;x+=10)for(int y=43;y<height-38;y+=10)g.fill(x,y,x+1,y+1,0x304B5D73);
        float t=TowerUiSettings.motion?Math.min(1,(System.nanoTime()-inspectedAt)/220_000_000f):1;
        g.enableScissor(width-126,43,width-12,height-40);g.pose().pushPose();g.pose().translate(12*Math.pow(1-t,3),0,0);
        TowerUi.panel(g,width-126,43,114,height-83,theme.accent);
        if(payload.categories().isEmpty())TowerUi.wrapped(g,font,"Nothing revealed",width-116,55,94,TowerUi.MUTED);
        else {var c=payload.categories().get(selected);int y=TowerUi.wrapped(g,font,c.name(),width-116,55,94,TowerUi.TEXT)+12;TowerUi.wrapped(g,font,c.value(),width-116,y,94,TowerUi.MUTED);}
        g.pose().popPose();g.disableScissor();
    }
    @Override public boolean isPauseScreen(){return false;}
}
