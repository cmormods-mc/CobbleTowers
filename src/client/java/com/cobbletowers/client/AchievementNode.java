package com.cobbletowers.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
final class AchievementNode extends Button {
    private final boolean earned;
    AchievementNode(int x,int y,int w,String name,String description,boolean earned,Runnable action){super(x,y,w,36,Component.literal(name),b->action.run(),DEFAULT_NARRATION);this.earned=earned;setTooltip(Tooltip.create(Component.literal((earned?"Earned: ":"Not yet earned: ")+name+"\n"+description)));}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        int x=getX(),y=getY(),w=getWidth(),accent=earned?0xFF90DBBE:0xFF78849B;
        TowerShader.panel(g,x,y,w,36,accent,isHoveredOrFocused()?1:earned?.38f:0);
        if(!earned){g.fill(x+8,y+15,x+14,y+22,0xFF78849B);g.fill(x+9,y+12,x+13,y+16,0xFF78849B);g.fill(x+10,y+13,x+12,y+16,0xFF121218);}
        else {g.fill(x+8,y+17,x+11,y+20,accent);g.fill(x+11,y+14,x+14,y+18,accent);}
        TowerUi.label(g,Minecraft.getInstance().font,getMessage().getString(),x+20,y+14,w-26,earned?TowerUi.TEXT:TowerUi.MUTED);
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
