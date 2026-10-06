package com.cobbletowers.client;

import com.cobbletowers.network.VendorCatalogPayload;
import com.cobbletowers.network.VendorPurchasePayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Tower Supply Vendor's shop (TDS #16), reached by {@code /cobbletowers runs vendor} or the
 * intermission screen's Vendor button rather than a physical NPC (see the design doc's scope decision).
 *
 * <p>P19: a row of teammate buttons chooses who a purchase is for (TDS #18: "may pay for recovery
 * targeted at teammates"). The default is the buyer; the choice survives the refresh that follows every
 * purchase, so buying two services for the same teammate takes no re-selecting. The server re-checks the
 * target (in the run, online) and answers with a message that is shown at the bottom.
 */
public final class VendorScreen extends TowerScreen {

    private VendorCatalogPayload catalog;
    /** Who a purchase is for. Null until the first catalog arrives, then the buyer. */
    private UUID target;

    public VendorScreen(VendorCatalogPayload catalog) {
        super(Component.literal("Tower Supply Vendor"), TowerUi.Theme.VENDOR);
        this.catalog = catalog;
        this.target = defaultTarget(catalog);
    }

    /** Called when a fresh catalog arrives (e.g. right after a purchase) while this screen is open. */
    public void updateCatalog(VendorCatalogPayload catalog) {
        this.catalog = catalog;
        // Keep the choice unless that teammate has left the run.
        boolean stillThere = catalog.team().stream().anyMatch(member -> member.id().equals(target));
        if (!stillThere) target = defaultTarget(catalog);
        clearWidgets();
        buildWidgets();
    }

    private static UUID defaultTarget(VendorCatalogPayload catalog) {
        return catalog.team().isEmpty() ? null : catalog.team().get(0).id();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private int selected,page;
    private int catalogWidth(){return width-Math.max(108,width/4)-30;}
    private int pageSize(){return Math.max(1,(height-110)/48)*2;}
    private void buildWidgets(){
        int cw=catalogWidth(),sx=cw+22,sw=width-sx-10;
        int count=pageSize();page=Math.min(page,Math.max(0,(catalog.services().size()-1)/count));
        selected=Math.min(selected,Math.max(0,catalog.services().size()-1));
        for(int i=0;i<count&&page*count+i<catalog.services().size();i++){
            int index=page*count+i;var e=catalog.services().get(index);int tile=(cw-6)/2;
            var card=new ServiceCardButton(12+i%2*(tile+6),64+i/2*48,tile,e,()->{selected=index;clearWidgets();buildWidgets();});
            addRenderableWidget(card);
        }
        if(!catalog.team().isEmpty()){
            String name=catalog.team().stream().filter(t->t.id().equals(target)).map(VendorCatalogPayload.Teammate::name).findFirst().orElse("Choose recipient");
            addRenderableWidget(TowerButton.builder(Component.literal("For: "+name),b->{int i=0;for(int n=0;n<catalog.team().size();n++)if(catalog.team().get(n).id().equals(target))i=n;choose(catalog.team().get((i+1)%catalog.team().size()).id());}).pos(12,35).size(cw,22).build());
        }
        if(!catalog.services().isEmpty()){
            var e=catalog.services().get(selected);
            var buy=TowerButton.builder(Component.literal("Purchase"),b->buy(e.id())).pos(sx+6,height-62).size(sw-12,24).build();
            buy.active=target!=null&&catalog.team().stream().anyMatch(t->t.id().equals(target)&&t.online())&&catalog.cobbleDollars()>=e.priceCobbleDollars()&&e.remainingPurchases()!=0;addRenderableWidget(buy);
        }
        if(catalog.services().size()>count){addRenderableWidget(TowerButton.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);clearWidgets();buildWidgets();}).pos(12,height-36).size(70,22).build());addRenderableWidget(TowerButton.builder(Component.literal("Next"),b->{page=Math.min((catalog.services().size()-1)/count,page+1);clearWidgets();buildWidgets();}).pos(86,height-36).size(60,22).build());}
        addRenderableWidget(TowerButton.builder(Component.literal("Close"),b->onClose()).pos(sx+6,height-34).size(sw-12,22).build());
    }

    private static String label(VendorCatalogPayload.Entry entry) {
        String stock = entry.remainingPurchases() < 0 ? "" : " (" + entry.remainingPurchases() + " left)";
        return entry.displayName() + " -- " + entry.priceCobbleDollars() + stock;
    }

    private void choose(UUID id) {
        target = id;
        clearWidgets();
        buildWidgets();
    }

    private void buy(ResourceLocation serviceId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || target == null) return;
        if (ClientPlayNetworking.canSend(VendorPurchasePayload.TYPE)) {
            ClientPlayNetworking.send(new VendorPurchasePayload(serviceId, target));
        }
    }

    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);int sx=catalogWidth()+22,sw=width-sx-10;
        PixelUi.sheet(g,sx,35,sw,height-73);
        // The quartermaster's counter: each row of goods stands on its own plank, under a bronze sign.
        int count=pageSize();int onPage=Math.max(0,Math.min(count,catalog.services().size()-page*count));
        for(int row=0;row<(onPage+1)/2;row++)PixelUi.plank(g,8,64+row*48+42,catalogWidth()+8,6);
        int signW=font.width("TOWER SUPPLIES")+16;
        g.fill(8,6,8+signW,26,TowerUi.OUTLINE);
        g.fill(9,7,7+signW,25,TowerUi.BRONZE);
        g.fill(9,7,7+signW,8,TowerUi.BRONZE_LIGHT);
        g.fill(12,14,14,18,TowerUi.OUTLINE);g.fill(2+signW,14,4+signW,18,TowerUi.OUTLINE);
        g.drawString(font,"TOWER SUPPLIES",17,13,TowerUi.INK,false);
        TowerUi.label(g,font,"Balance: "+catalog.cobbleDollars(),sx,15,sw,TowerUi.TEXT);
        if(!catalog.services().isEmpty()){
            var e=catalog.services().get(selected);int y=TowerUi.wrapped(g,font,e.displayName(),sx+8,46,sw-16,TowerUi.INK)+12;
            y=TowerUi.wrapped(g,font,e.priceCobbleDollars()+" CobbleDollars",sx+8,y,sw-16,TowerUi.BURGUNDY)+10;
            TowerUi.wrapped(g,font,e.remainingPurchases()<0?"Stock: unlimited":"Stock: "+e.remainingPurchases(),sx+8,y,sw-16,0xFF6B4A33);
        }
        TowerUi.label(g,font,catalog.message(),12,height-48,catalogWidth(),TowerUi.TEXT);
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
