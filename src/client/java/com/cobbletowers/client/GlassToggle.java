package com.cobbletowers.client;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
/** A normal keyboard-accessible button with a time-based visual switch. */
final class GlassToggle extends Button {
    private final String label;private final BooleanSupplier value;private float position;private long last=System.nanoTime();
    GlassToggle(int x,int y,int w,String label,BooleanSupplier value,Runnable action){super(x,y,w,26,Component.literal(label),b->{action.run();TowerUiSettings.save();},DEFAULT_NARRATION);this.label=label;this.value=value;position=value.getAsBoolean()?1:0;}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        boolean on=value.getAsBoolean();float target=on?1:0;long now=System.nanoTime();float blend=TowerUiSettings.motion?1-(float)Math.exp(-Math.min(.1,(now-last)/1e9)*18):1;last=now;position+=(target-position)*blend;
        setMessage(Component.literal(label+": "+(on?"On":"Off")));
        TowerShader.panel(g,getX(),getY(),getWidth(),getHeight(),0xFF8ABBE8,isHoveredOrFocused()?.8f:0);
        var font=Minecraft.getInstance().font;TowerUi.label(g,font,label,getX()+9,getY()+9,getWidth()-62,TowerUi.TEXT);
        int x=getX()+getWidth()-43,y=getY()+7;TowerShader.panel(g,x,y,32,13,0xFF8ABBE8,on?.9f:0);
        TowerShader.panel(g,x+2+(int)(position*17),y+2,10,9,0xFFCCE8FF,1);
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
