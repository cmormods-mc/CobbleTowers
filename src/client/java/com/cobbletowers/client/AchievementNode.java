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
        int x=getX(),y=getY(),w=getWidth(),accent=earned?TowerUi.SAGE:0xFF7A6A55;
        // A trophy cabinet: every trophy stands on a shelf plank. Earned ones are bronze-and-gold cups with a sage
        // trim; the
        // rest are a brown padlock on an empty stand.
        PixelUi.plank(g,x-2,y+36,w+4,5);
        PixelUi.panel(g,x,y,w,36,accent,isHoveredOrFocused()?1:earned?.38f:0);
        if(!earned){g.fill(x+8,y+15,x+14,y+22,0xFF7A6A55);g.fill(x+9,y+12,x+13,y+16,0xFF7A6A55);g.fill(x+10,y+13,x+12,y+16,TowerUi.OUTLINE);}
        else {
            int gold=0xFFE3BD7F,dark=0xFF6B4A33;
            g.fill(x+6,y+9,x+16,y+10,dark);g.fill(x+7,y+10,x+15,y+16,gold);g.fill(x+8,y+16,x+14,y+18,gold);   // the cup
            g.fill(x+4,y+11,x+6,y+14,gold);g.fill(x+16,y+11,x+18,y+14,gold);                                    // handles
            g.fill(x+10,y+18,x+12,y+23,dark);g.fill(x+7,y+23,x+15,y+25,dark);                                   // stem and base
            g.fill(x+9,y+11,x+10,y+15,0xFFF0DFBF);
        }
        TowerUi.label(g,TowerFonts.get(),getMessage().getString(),x+20,y+14,w-26,earned?TowerUi.TEXT:TowerUi.MUTED);
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
