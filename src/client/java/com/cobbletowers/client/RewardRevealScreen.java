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
    private int accent(){if(grants.isEmpty())return theme.accent;String id=grants.get(page).item().getPath();if(id.contains("duskvale"))return 0xFFBC9BEF;if(id.contains("rootvale"))return 0xFF8ED8AE;if(id.contains("tideforge"))return 0xFF7FCBE6;return 0xFFE8CB8D;}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        super.render(g,mx,my,dt);int w=Math.min(250,width-40),h=Math.min(145,height-84),x=(width-w)/2,y=(height-h)/2-8;
        float t=TowerUiSettings.motion?Math.min(1,(System.nanoTime()-revealStart)/1_200_000_000f):1,scale=.92f+.08f*(1-(float)Math.pow(1-t,3));
        g.pose().pushPose();g.pose().translate(width/2f,y+h/2f,0);g.pose().scale(scale,scale,1);g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(TowerUiSettings.motion?(1-t)*-3:0));g.pose().translate(-width/2f,-y-h/2f,0);
        TowerShader.panel(g,x,y,w,h,accent(),.8f);
        g.drawCenteredString(font,"REWARDS SECURED",width/2,y+12,accent());
        g.drawCenteredString(font,"Through floor "+floorIndex,width/2,y+27,TowerUi.MUTED);
        if(grants.isEmpty())g.drawCenteredString(font,"No rewards in this grant",width/2,y+60,TowerUi.TEXT);
        else {
            var grant=grants.get(page);var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(grant.item());
            g.pose().pushPose();g.pose().translate(width/2f-16,y+45,0);g.pose().scale(2,2,1);g.renderItem(new net.minecraft.world.item.ItemStack(item),0,0);g.pose().popPose();
            String label=grant.label().isEmpty()?item.getDescription().getString():grant.label();TowerUi.label(g,font,label+" x"+grant.amount(),x+12,y+h-34,w-24,TowerUi.TEXT);
            g.drawCenteredString(font,(page+1)+" / "+grants.size(),width/2,y+h-16,TowerUi.MUTED);
        }
        g.pose().popPose();
        if(t<1){
            float open=Math.max(0,Math.min(1,(t-.2f)/.8f));
            open=1-(float)Math.pow(1-open,3);
            int door=(int)((w/2f)*(1-open));
            if(door>0){
                TowerUi.panel(g,x,y,door,h,accent());
                TowerUi.panel(g,x+w-door,y,door,h,accent());
                for(int sy=y+8;sy<y+h-8;sy+=12){
                    g.fill(x+door-4,sy,x+door-1,sy+6,0xFFB77B2F);
                    g.fill(x+w-door+1,sy,x+w-door+4,sy+6,0xFFB77B2F);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

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
