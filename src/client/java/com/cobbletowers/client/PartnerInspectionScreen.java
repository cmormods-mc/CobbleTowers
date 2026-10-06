package com.cobbletowers.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.PokemonGuiUtilsKt;
import com.cobblemon.mod.common.client.gui.ProfileTransformType;
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState;
import com.cobblemon.mod.common.entity.PoseType;
import com.cobbletowers.network.RentalDraftPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EquipmentSlot;
import org.joml.Quaternionf;

/** Read-only species reference or an actual server-provided rental set. Never represents ownership. */
public final class PartnerInspectionScreen extends TowerScreen {
    private final Screen parent;private final RentalDraftPayload.Card card;
    private String species="blastoise",gear="";private FloatingState pose=new FloatingState();
    private float yaw=25,zoom=1;private boolean modelFailed;private ArmorStand mannequin;
    public PartnerInspectionScreen(Screen parent,RentalDraftPayload.Card card){super(Component.literal("Partner & gear inspection"),TowerUi.Theme.PARTY);this.parent=parent;this.card=card;if(card!=null)species=card.species();}
    @Override protected void init(){
        int x=12;
        if(card==null)for(String name:new String[]{"blastoise","machamp","scizor"}){addRenderableWidget(TowerButton.builder(Component.literal(name),b->{species=name;gear="";pose=new FloatingState();modelFailed=false;}).pos(x,36).size(72,20).build());x+=76;}
        if(card==null){x=12;for(String set:new String[]{"challenger","duskvale","rootvale","tideforge"}){addRenderableWidget(TowerButton.builder(Component.literal(set),b->{gear=set;mannequin=null;}).pos(x,height-57).size(76,20).build());x+=80;}}
        addRenderableWidget(TowerButton.builder(Component.literal("Back"),b->onClose()).pos(width-84,height-30).size(72,20).build());
        addRenderableWidget(TowerButton.builder(Component.literal("Rotate"),b->yaw+=30).pos(12,height-30).size(66,20).build());
        addRenderableWidget(TowerButton.builder(Component.literal("Zoom"),b->zoom=zoom>=1.4f?.8f:zoom+.2f).pos(82,height-30).size(60,20).build());
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&x>12&&x<width-146&&y>60&&y<height-64){yaw+=(float)dx;return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        super.render(g,mx,my,dt);int right=width-140,vw=right-24,vh=height-132;
        PixelUi.panel(g,12,60,vw,vh,gear.equals("duskvale")?TowerUi.ECHO:species.equals("scizor")?TowerUi.BURGUNDY:TowerUi.BRONZE,.25f);
        TowerUi.panel(g,right,60,128,vh,theme.accent);
        TowerUi.label(g,font,gear.isEmpty()?species.toUpperCase():gear.toUpperCase()+" SET",20,66,vw-16,TowerUi.TEXT);
        g.enableScissor(16,80,right-16,height-74);g.flush();
        if(gear.isEmpty()&&PokemonSpecies.getByName(species)==null){
            if(java.util.Set.of("blastoise","machamp","scizor").contains(species))g.blit(ResourceLocation.fromNamespaceAndPath("cobbletowers","textures/gui/partners/"+species+".png"),12+vw/2-48,76,96,64,0,0,48,32,48,32);
        }else if(gear.isEmpty()&&!modelFailed){
            g.pose().pushPose();
            try {
                g.pose().translate(12+vw/2f,65+vh*.62f,150);pose.updateAge(TowerUiSettings.motion?(int)(uiAge()/50):0);
                PokemonGuiUtilsKt.drawProfilePokemon(ResourceLocation.fromNamespaceAndPath("cobblemon",species),g.pose(),new Quaternionf().rotationXYZ(.08f,(float)Math.toRadians(yaw),0),PoseType.PROFILE,pose,TowerUiSettings.motion?dt:0,Math.min(vw,vh)*.55f*zoom,ProfileTransformType.PROFILE,false,false,1,1,1,1,0,0,15);
            }catch(RuntimeException e){modelFailed=true;org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Partner model unavailable: {}",species,e);}finally{g.pose().popPose();}
        }else if(!gear.isEmpty()){
            if(minecraft.level!=null){
                if(mannequin==null){mannequin=new ArmorStand(minecraft.level,0,0,0);String[] parts={"helmet","chestplate","leggings","boots"};EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};for(int i=0;i<parts.length;i++)mannequin.setItemSlot(slots[i],new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobbletowers",gear+"_"+parts[i]))));}
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,16,80,right-16,height-74,(int)(vh*.4f*zoom),0,mx,my,mannequin);
            }else {g.pose().pushPose();g.pose().translate(12+vw/2f-24,80+vh*.2f,0);g.pose().scale(3,3,1);g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobbletowers",gear+"_chestplate"))),0,0);g.pose().popPose();}
        }
        g.disableScissor();
        int y=70;
        if(!gear.isEmpty()){TowerUi.wrapped(g,font,minecraft.level==null?"Join a world for the equipped armor viewport.":"Move the cursor to turn the equipped set.",right+8,y,112,TowerUi.MUTED);}
        else if(card!=null){y=TowerUi.wrapped(g,font,"Lv "+card.details().level()+" / "+card.details().ability(),right+8,y,112,TowerUi.TEXT)+8;for(String move:card.details().moves())y=TowerUi.wrapped(g,font,Component.translatable("cobblemon.move."+move).getString(),right+8,y,112,TowerUi.MUTED)+3;}
        else {var data=PokemonSpecies.getByName(species);g.drawString(font,"Species base stats",right+8,y,TowerUi.MUTED,false);y+=15;if(data==null)TowerUi.wrapped(g,font,"Join a world to load species data and its 3D model.",right+8,y,112,TowerUi.MUTED);if(data!=null)for(var e:data.getBaseStats().entrySet().stream().sorted(java.util.Comparator.comparing(e->e.getKey().getShowdownId())).toList()){TowerUi.label(g,font,e.getKey().getShowdownId()+"  "+e.getValue(),right+8,y,112,TowerUi.TEXT);y+=12;}}
        g.drawString(font,card==null?"REFERENCE COLLECTION":"RENTAL SET INSPECTION",12,16,TowerUi.TEXT,false);
        if(modelFailed)TowerUi.label(g,font,"Model unavailable",20,height-85,vw-16,TowerUi.MUTED);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
