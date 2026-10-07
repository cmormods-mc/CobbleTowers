package com.cobbletowers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * A wooden/bronze button. Primary is burgundy, dark() is the chocolate secondary (navigation), and a selected button
 * shows the active tab face. Pressing draws one GUI pixel lower; the hitbox does not move.
 */
public final class TowerButton extends Button {
    private boolean dark;
    /** A {@link MenuIcons} key drawn at the left of the label, or null. */
    private String icon;
    /** Draws the active tab face while staying a normal widget; set with active=false for "you are here". */
    public boolean selected;
    private long pressedAt;
    private TowerButton(int x, int y, int w, int h, Component label, OnPress action) {
        super(x, y, w, h, label, action, DEFAULT_NARRATION);
    }
    public static Builder builder(Component label, OnPress action) { return new Builder(label, action); }

    @Override public void onPress() {
        pressedAt = System.nanoTime();
        super.onPress();
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        var font = TowerFonts.get();
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        boolean pressed = active && pressedAt != 0 && (System.nanoTime() - pressedAt) < 100_000_000L;
        PixelUi.Frame frame = selected ? PixelUi.Frame.TAB
                : !active ? PixelUi.Frame.BUTTON_DISABLED
                : pressed ? PixelUi.Frame.BUTTON_PRESSED
                : dark ? PixelUi.Frame.DARK
                : hover ? PixelUi.Frame.BUTTON_HOVER : PixelUi.Frame.BUTTON;
        PixelUi.frame(g, frame, x, y, w, h);
        if (hover && dark) PixelUi.brackets(g, x + 2, y + 2, w - 4, h - 4, TowerUi.BRONZE_LIGHT);
        int color = selected || (active && !dark) || hover ? TowerUi.TEXT : active ? TowerUi.MUTED : 0xFF8F7A5E;
        int left = x, span = w;
        String full = getMessage().getString();
        // The label comes first: the icon is drawn only when the whole label still fits beside it.
        int pad = w < 30 ? 4 : 12;
        if (icon != null && h >= 18 && font.width(full) <= w - 20 - 6) {
            MenuIcons.draw(g, icon, x + 4, y + (h - 16) / 2 + (pressed ? 1 : 0), 16);
            left = x + 20;
            span = w - 20;
            pad = 6;
        }
        String text = font.plainSubstrByWidth(full, Math.max(1, span - pad));
        g.drawString(font, text, left + (span - font.width(text)) / 2, y + (h - 8) / 2 + (pressed ? 1 : 0), color, false);
    }
    @Override public void playDownSound(SoundManager manager) {
        if (TowerUiSettings.sounds) super.playDownSound(manager);
    }
    public static final class Builder extends Button.Builder {
        private final Component label; private final OnPress action;
        private int x,y,w=150,h=20; private Tooltip tooltip; private boolean dark; private String icon;
        public Builder dark(){this.dark=true;return this;}
        public Builder icon(String icon){this.icon=icon;return this;}
        Builder(Component label, OnPress action) { super(label, action); this.label=label;this.action=action; }
        @Override public Builder pos(int x,int y) {this.x=x;this.y=y;return this;}
        @Override public Builder size(int w,int h) {this.w=w;this.h=h;return this;}
        @Override public Builder width(int w) {this.w=w;return this;}
        @Override public Builder bounds(int x,int y,int w,int h) {return pos(x,y).size(w,h);}
        @Override public Builder tooltip(Tooltip tip) {this.tooltip=tip;return this;}
        @Override public TowerButton build() {var button=new TowerButton(x,y,w,h,label,action);button.dark=dark;button.icon=icon;if(tooltip!=null)button.setTooltip(tooltip);return button;}
    }
}
