package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Illustrated destinations with fixed hitboxes and pixel-aligned focus feedback. */
final class TrainerCardButton extends Button {
    private final String caption, icon;
    private final int accent;
    private final long opened = System.nanoTime();
    TrainerCardButton(int x,int y,int w,int h,String title,String caption,String icon,int accent,Runnable action) {
        super(x,y,w,h,Component.literal(title),b->action.run(),DEFAULT_NARRATION);
        this.caption=caption;this.icon=icon;this.accent=accent;
    }
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
        int x=getX(),y=getY(),w=getWidth(),h=getHeight();
        boolean focus=active&&isHoveredOrFocused();
        var font=TowerFonts.get();
        PixelUi.panel(g,x,y,w,h,accent,focus?1f:.12f);
        g.enableScissor(x+5,y+2,x+w-2,y+h-2);
        int artSize=h>=38?32:16;
        int bob=focus&&TowerUiSettings.motion?(int)Math.round(Math.sin((System.nanoTime()-opened)/180_000_000.0)):0;
        MenuIcons.draw(g,icon,x+w-artSize-6,y+(h-artSize)/2+bob,artSize);
        g.disableScissor();
        int textWidth=w-artSize-18;
        TowerUi.label(g,font,getMessage().getString(),x+10,y+(h>=34?7:6),textWidth,active?TowerUi.TEXT:0xFF8F7A5E);
        if(h>=34)TowerUi.label(g,font,caption,x+10,y+21,textWidth,TowerUi.MUTED);
        if(focus)PixelUi.brackets(g,x+2,y+2,w-4,h-4,TowerUi.BRONZE_LIGHT);
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
