package com.cobbletowers.client;

import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.RewardRevealPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The reward reveal the design doc replaces P9's chat-only stopgap with (TDS #9's grants shown, not
 * chosen).
 *
 * <p>Read-only: a grant is never a player choice ("No player choice in what is granted",
 * {@code docs/design/P9-economy.md}), so there is nothing here to drag, click or confirm beyond
 * dismissing the screen once it has been read.
 */
public final class RewardRevealScreen extends TowerScreen {

    private int page;
    private long revealStart=System.nanoTime();
    private void turn(int step){page=Math.floorMod(page+step,grants.size());revealStart=System.nanoTime();}
    private final int floorIndex;
    private final List<RewardRevealPayload.Grant> grants;

    public RewardRevealScreen(RewardRevealPayload payload) {
        super(Component.literal("Tower Rewards"), TowerUi.Theme.REWARDS);
        this.floorIndex = payload.floorIndex();
        this.grants = payload.grants();
    }

    @Override protected void init(){
        addRenderableWidget(TowerButton.builder(Component.literal("Continue"),b->onClose()).pos(width/2-50,height-34).size(100,22).build());
        if(grants.size()>1){addRenderableWidget(TowerButton.builder(Component.literal("<"),b->turn(-1)).pos(width/2-86,height-34).size(28,22).build());addRenderableWidget(TowerButton.builder(Component.literal(">"),b->turn(1)).pos(width/2+58,height-34).size(28,22).build());}
    }
    private int accent(){if(grants.isEmpty())return theme.accent;String id=grants.get(page).item().getPath();if(id.contains("duskvale"))return TowerUi.ECHO;if(id.contains("rootvale"))return TowerUi.SAGE;return TowerUi.BRONZE_LIGHT;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        super.render(g,mx,my,dt);int w=Math.min(250,width-40),h=Math.min(145,height-84),x=(width-w)/2,y=(height-h)/2-8;
        float t=TowerUiSettings.motion?Math.min(1,(System.nanoTime()-revealStart)/1_000_000_000f):1;
        // The grant label: parchment inside a wooden coffer. The grant is already made server-side; this only shows it.
        PixelUi.frame(g,PixelUi.Frame.DARK,x-6,y-6,w+12,h+12);
        PixelUi.sheet(g,x,y,w,h);
        g.drawString(font,"REWARDS SECURED",width/2-font.width("REWARDS SECURED")/2,y+12,TowerUi.BURGUNDY,false);
        g.drawString(font,"Through floor "+floorIndex,width/2-font.width("Through floor "+floorIndex)/2,y+27,TowerUi.INK,false);
        if(grants.isEmpty())g.drawString(font,"No rewards in this grant",width/2-font.width("No rewards in this grant")/2,y+60,TowerUi.INK,false);
        else {
            var grant=grants.get(page);var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(grant.item());
            g.pose().pushPose();g.pose().translate(width/2f-16,y+45,0);g.pose().scale(2,2,1);g.renderItem(new net.minecraft.world.item.ItemStack(item),0,0);g.pose().popPose();
            String label=grant.label().isEmpty()?item.getDescription().getString():grant.label();TowerUi.label(g,font,label+" x"+grant.amount(),x+12,y+h-34,w-24,TowerUi.INK);
            g.drawString(font,(page+1)+" / "+grants.size(),width/2-font.width((page+1)+" / "+grants.size())/2,y+h-16,TowerUi.INK,false);
        }
        if(t<1){
            // Coffer lid: two oak halves retract after a brass seal releases (first 20% of the reveal).
            float open=Math.max(0,Math.min(1,(t-.2f)/.8f));
            open=1-(float)Math.pow(1-open,3);
            int door=(int)((w/2f)*(1-open));
            if(door>0){
                for(int side=0;side<2;side++){
                    int dx=side==0?x:x+w-door;
                    PixelUi.tile(g,PixelUi.Tile.OAK,dx,y,door,h);
                    PixelUi.frame(g,PixelUi.Frame.DARK,dx,y,door,h);
                    for(int sy=y+10;sy<y+h-10;sy+=24)g.fill(dx+2,sy,dx+door-2,sy+4,TowerUi.BRONZE);
                }
                if(t<.2f){int c=width/2;g.fill(c-6,y+h/2-6,c+6,y+h/2+6,TowerUi.OUTLINE);g.fill(c-5,y+h/2-5,c+5,y+h/2+5,TowerUi.BRONZE);g.fill(c-5,y+h/2-5,c+5,y+h/2-4,TowerUi.BRONZE_LIGHT);}
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void closeAll() { onClose(); }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
        // A floor that banks sends this on top of the intermission screen; ask for that one back so
        // closing the reveal returns the player to the menu rather than to an empty arena. The server
        // answers only if the player is actually at an intermission.
        if (ClientPlayNetworking.canSend(IntermissionActionPayload.TYPE)) {
            ClientPlayNetworking.send(new IntermissionActionPayload(IntermissionActionPayload.Action.REFRESH, 0));
        }
    }
}
