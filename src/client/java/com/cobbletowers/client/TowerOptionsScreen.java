package com.cobbletowers.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TowerOptionsScreen extends TowerScreen {
    private final Screen parent;
    private String tab="General";
    private int content;
    private boolean renderModes;
    public TowerOptionsScreen(Screen parent){super(Component.literal("Presentation settings"),TowerUi.Theme.LOBBY);this.parent=parent;}
    @Override protected void init(){
        content=Math.max(132,width/4);int y=45;
        for(String name:new String[]{"General","Audio","Graphics","Shader Controls","Keybinds"}){
            var b=TowerButton.builder(Component.literal(name),ignored->{tab=name;rebuild();}).pos(12,y).size(content-24,22).build();b.active=!tab.equals(name);addRenderableWidget(b);y+=27;
        }
        int x=content+12,w=width-x-20;
        switch(tab){
            case "General" -> toggle("Animations",()->TowerUiSettings.motion,x,55,w,()->TowerUiSettings.motion=!TowerUiSettings.motion);
            case "Audio" -> toggle("UI sounds",()->TowerUiSettings.sounds,x,55,w,()->TowerUiSettings.sounds=!TowerUiSettings.sounds);
            case "Graphics" -> {
                addRenderableWidget(TowerButton.builder(Component.literal("Renderer: "+(TowerUiSettings.shaders?"Industrial CRT":"Flat steel")+"  v"),b->{renderModes=!renderModes;rebuild();}).pos(x,55).size(w,24).build());
                if(renderModes)for(int i=0;i<2;i++){boolean glass=i==0;addRenderableWidget(TowerButton.builder(Component.literal(glass?"Industrial / CRT / indicators":"Steel / compatibility"),b->{TowerUiSettings.shaders=glass;renderModes=false;TowerUiSettings.save();rebuild();}).pos(x,83+i*25).size(w,23).build());}
            }
            case "Keybinds" -> addRenderableWidget(TowerButton.builder(Component.literal("Edit Minecraft keybinds"),b->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options))).pos(x,101).size(w,24).build());
            case "Shader Controls" -> {
                addRenderableWidget(new SettingSlider(x,55,w,"CRT strength",TowerUiSettings.blur/3.0,true));
                addRenderableWidget(new SettingSlider(x,85,w,"Metal shading",(TowerUiSettings.opacity-.65)/.33,false));
            }
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Done"),b->onClose()).pos(width-100,height-32).size(80,22).build());
    }
    private void toggle(String label,java.util.function.BooleanSupplier value,int x,int y,int w,Runnable action){addRenderableWidget(new GlassToggle(x,y,w,label,value,action));}
    private void rebuild(){clearWidgets();init();}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float dt){
        super.renderBackground(g,mx,my,dt);TowerUi.panel(g,7,36,content-9,height-76,theme.accent);TowerUi.panel(g,content+4,36,width-content-12,height-76,theme.accent);
        g.drawString(font,title,14,17,TowerUi.TEXT,false);
        String note=switch(tab){case "General"->"Reduced motion keeps every choice available.";case "Audio"->"Game volume remains in Minecraft sound settings.";case "Graphics"->"Disable shaders for the flat presentation fallback.";case "Shader Controls"->"Changes apply immediately to these panels.";default->CobbleTowersClient.PRESENTATION.getTranslatedKeyMessage().getString()+": presentation settings. Tab: focus. Enter: select. Esc: back.";};
        TowerUi.wrapped(g,font,note,content+12,tab.equals("Keybinds")?55:tab.equals("Graphics")?145:120,width-content-32,TowerUi.MUTED);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    private static final class SettingSlider extends AbstractSliderButton {
        private final String label;private final boolean blur;
        SettingSlider(int x,int y,int w,String label,double value,boolean blur){super(x,y,w,22,Component.empty(),Math.max(0,Math.min(1,value)));this.label=label;this.blur=blur;updateMessage();}
        @Override protected void updateMessage(){setMessage(Component.literal(label+": "+Math.round((blur?value*3:.65+value*.33)*100)+(blur?"%":"%")));}
        @Override protected void applyValue(){if(blur)TowerUiSettings.blur=(float)(value*3);else TowerUiSettings.opacity=(float)(.65+value*.33);TowerUiSettings.save();}
        @Override public void renderWidget(GuiGraphics g,int mx,int my,float dt){TowerShader.panel(g,getX(),getY(),getWidth(),getHeight(),0xFFFF8C00,isHoveredOrFocused()?1:0);int x=getX()+5+(int)((getWidth()-14)*value);g.fill(x,getY()+4,x+4,getY()+getHeight()-4,0xFFFF8C00);g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font,getMessage(),getX()+getWidth()/2,getY()+7,TowerUi.TEXT);}
    }
}


