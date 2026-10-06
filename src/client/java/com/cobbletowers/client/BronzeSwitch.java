package com.cobbletowers.client;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
/** A normal keyboard-accessible button drawn as a bronze lever switch; the knob slides on elapsed time. */
final class BronzeSwitch extends Button {
    private final String label;private final BooleanSupplier value;private float position;private long last=System.nanoTime();
    BronzeSwitch(int x,int y,int w,String label,BooleanSupplier value,Runnable action){super(x,y,w,26,Component.literal(label),b->{action.run();TowerUiSettings.save();},DEFAULT_NARRATION);this.label=label;this.value=value;position=value.getAsBoolean()?1:0;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        boolean on=value.getAsBoolean();float target=on?1:0;long now=System.nanoTime();float blend=TowerUiSettings.motion?Math.min(1,(now-last)/1e9f*8):1;last=now;position+=(target-position)*blend;
        setMessage(Component.literal(label+": "+(on?"On":"Off")));
        PixelUi.panel(g,getX(),getY(),getWidth(),getHeight(),TowerUi.BRONZE,isHoveredOrFocused()?.8f:0);
        var font=Minecraft.getInstance().font;TowerUi.label(g,font,label,getX()+9,getY()+9,getWidth()-62,TowerUi.TEXT);
        int x=getX()+getWidth()-43,y=getY()+7;
        g.fill(x,y,x+32,y+13,TowerUi.OUTLINE);g.fill(x+1,y+1,x+31,y+12,on?0xFF4F5C33:0xFF2B1D17);
        int knob=x+2+Math.round(position*17);
        g.fill(knob,y+2,knob+11,y+11,TowerUi.BRONZE);g.fill(knob,y+2,knob+11,y+3,TowerUi.BRONZE_LIGHT);g.fill(knob,y+10,knob+11,y+11,0xFF67452C);
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
