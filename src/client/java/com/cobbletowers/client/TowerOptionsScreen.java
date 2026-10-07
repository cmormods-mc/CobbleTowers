package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TowerOptionsScreen extends TowerScreen {
    private final Screen parent;
    private String tab="General";
    private int content;
    public TowerOptionsScreen(Screen parent){super(Component.literal("Presentation settings"),TowerUi.Theme.LOBBY);this.parent=parent;}
    @Override protected void init(){
        content=Math.max(132,width/4);int y=45;
        for(String name:new String[]{"General","Audio","Graphics","Keybinds"}){
            var b=TowerButton.builder(Component.literal(name),ignored->{tab=name;rebuild();}).pos(12,y).size(content-24,22).dark().build();
            if(tab.equals(name)){b.active=false;b.selected=true;}addRenderableWidget(b);y+=27;
        }
        int x=content+12,w=width-x-20;
        switch(tab){
            case "General" -> {toggle("Animations",()->TowerUiSettings.motion,x,55,w,()->TowerUiSettings.motion=!TowerUiSettings.motion);toggle("Pixel font",()->TowerUiSettings.pixelFont,x,85,w,()->{TowerUiSettings.pixelFont=!TowerUiSettings.pixelFont;font=TowerFonts.get();});}
            case "Audio" -> toggle("UI sounds",()->TowerUiSettings.sounds,x,55,w,()->TowerUiSettings.sounds=!TowerUiSettings.sounds);
            case "Graphics" -> toggle("Torch glow",()->TowerUiSettings.glow,x,55,w,()->TowerUiSettings.glow=!TowerUiSettings.glow);
            case "Keybinds" -> addRenderableWidget(TowerButton.builder(Component.literal("Edit Minecraft keybinds"),b->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).pos(x,101).size(w,24).build());
            default -> {}
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Done"),b->onClose()).pos(width-100,height-32).size(80,22).build());
    }
    private void toggle(String label,java.util.function.BooleanSupplier value,int x,int y,int w,Runnable action){addRenderableWidget(new BronzeSwitch(x,y,w,label,value,action));}
    private void rebuild(){clearWidgets();init();}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);TowerUi.panel(g,7,36,content-9,height-76,theme.accent);PixelUi.sheet(g,content+4,36,width-content-12,height-76);
        g.drawString(font,title,14,17,TowerUi.BRONZE_LIGHT,false);
        String note=switch(tab){case "General"->"Reduced motion keeps every choice available.";case "Audio"->"Game volume remains in Minecraft sound settings.";case "Graphics"->"The torch glow is a soft light behind headers. Turning it off keeps every material and control.";default->CobbleTowersClient.PRESENTATION.getTranslatedKeyMessage().getString()+": presentation settings. Tab: focus. Enter: select. Esc: back.";};
        TowerUi.wrapped(g,font,note,content+12,tab.equals("Keybinds")?55:tab.equals("General")?120:90,width-content-32,TowerUi.INK);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
