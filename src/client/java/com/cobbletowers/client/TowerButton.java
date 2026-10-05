package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

public final class TowerButton extends Button {
    private boolean dark;
    private float glow;private long frame=System.nanoTime();
    private TowerButton(int x, int y, int w, int h, Component label, OnPress action) {
        super(x, y, w, h, label, action, DEFAULT_NARRATION);
    }
    public static Builder builder(Component label, OnPress action) { return new Builder(label, action); }
    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        var font = Minecraft.getInstance().font;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        long now=System.nanoTime();float blend=TowerUiSettings.motion?1-(float)Math.exp(-Math.min(.1,(now-frame)/1e9)*16):1;frame=now;glow+=((hover?1:0)-glow)*blend;
        TowerShader.panel(g,x,y,w,h,0xFFFF8C00,glow);
        g.fill(x+3,y+4,x+4,y+h-4,active?(hover?0xFFFFB64C:0xFF806039):0xFF39414A);
        String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,w-12));
        g.drawString(font,text,x+(w-font.width(text))/2,y+(h-8)/2,active?TowerUi.TEXT:0xFF788494,false);
    }
    @Override public void playDownSound(SoundManager manager) {
        if (TowerUiSettings.sounds) super.playDownSound(manager);
    }
    public static final class Builder extends Button.Builder {
        private final Component label; private final OnPress action;
        private int x,y,w=150,h=20; private Tooltip tooltip; private boolean dark;
        public Builder dark(){this.dark=true;return this;}
        Builder(Component label, OnPress action) { super(label, action); this.label=label;this.action=action; }
        @Override public Builder pos(int x,int y) {this.x=x;this.y=y;return this;}
        @Override public Builder size(int w,int h) {this.w=w;this.h=h;return this;}
        @Override public Builder width(int w) {this.w=w;return this;}
        @Override public Builder bounds(int x,int y,int w,int h) {return pos(x,y).size(w,h);}
        @Override public Builder tooltip(Tooltip tip) {this.tooltip=tip;return this;}
        @Override public TowerButton build() {var button=new TowerButton(x,y,w,h,label,action);button.dark=dark;button.setTooltip(tooltip == null ? Tooltip.create(label) : tooltip);return button;}
    }
}
