package com.cobbletowers.client;

import com.cobbletowers.CobbleTowers;
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
        super.render(g,mx,my,dt);int right=width-140,vw=right-24,vh=card!=null?height-96:height-132,wellBottom=card!=null?60+vh-8:height-74;
        labDisplay(g,12,60,vw,vh);
        PixelUi.sheet(g,right,60,128,vh);
        String plate=gear.isEmpty()?species.toUpperCase():gear.toUpperCase()+" SET";
        PixelUi.tag(g,font,font.plainSubstrByWidth(plate,vw-44),22,66,false);
        g.enableScissor(16,80,right-16,wellBottom);g.flush();
        if(gear.isEmpty()&&PokemonSpecies.getByName(species)==null){
            // No species data or model yet (not in a world): show the bundled icon, at twice its size, on the
            // pedestal.
            g.pose().pushPose();g.pose().translate(12+vw/2f,60+vh*.4f,0);g.pose().scale(2,2,1);PartnerSprites.draw(g,species,0,0);g.pose().popPose();
        }else if(gear.isEmpty()&&!modelFailed){
            g.pose().pushPose();
            try {
                g.pose().translate(12+vw/2f,65+vh*.62f,150);pose.updateAge(TowerUiSettings.motion?(int)(uiAge()/50):0);
                PokemonGuiUtilsKt.drawProfilePokemon(ResourceLocation.fromNamespaceAndPath("cobblemon",species),g.pose(),new Quaternionf().rotationXYZ(.08f,(float)Math.toRadians(yaw),0),PoseType.PROFILE,pose,TowerUiSettings.motion?dt:0,Math.min(vw,vh)*.55f*zoom,ProfileTransformType.PROFILE,false,false,1,1,1,1,0,0,15);
            }catch(RuntimeException e){modelFailed=true;org.slf4j.LoggerFactory.getLogger("cobbletowers-ui").warn("Partner model unavailable: {}",species,e);}finally{g.pose().popPose();}
        }else if(!gear.isEmpty()){
            if(minecraft.level!=null){
                if(mannequin==null){mannequin=new ArmorStand(minecraft.level,0,0,0);String[] parts={"helmet","chestplate","leggings","boots"};EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};for(int i=0;i<parts.length;i++)mannequin.setItemSlot(slots[i],new ItemStack(BuiltInRegistries.ITEM.get(CobbleTowers.id(gear+"_"+parts[i]))));}
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,16,80,right-16,wellBottom,(int)(vh*.4f*zoom),0,mx,my,mannequin);
            }else {g.pose().pushPose();g.pose().translate(12+vw/2f-24,80+vh*.2f,0);g.pose().scale(3,3,1);g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(CobbleTowers.id(gear+"_chestplate"))),0,0);g.pose().popPose();}
        }
        g.disableScissor();
        int y=70;
        int brown=0xFF6B4A33;
        if(!gear.isEmpty()){TowerUi.wrapped(g,font,minecraft.level==null?"Join a world for the equipped armor viewport.":"Move the cursor to turn the equipped set.",right+8,y,112,brown);}
        else if(card!=null){
            var d=card.details();
            y=TowerUi.wrapped(g,font,"Lv "+d.level()+"  "+ByzantineCardFace.translated("cobblemon.nature.",d.nature()).getString(),right+8,y,112,TowerUi.INK);
            if(!d.role().isEmpty())y=TowerUi.wrapped(g,font,d.role(),right+8,y,112,brown);
            y+=3;
            y=TowerUi.wrapped(g,font,"Ability: "+ByzantineCardFace.translated("cobblemon.ability.",d.ability()).getString(),right+8,y,112,TowerUi.INK);
            if(!d.item().isEmpty())y=TowerUi.wrapped(g,font,"Item: "+Component.translatableWithFallback("item.cobblemon."+d.item(),ByzantineCardFace.tidy(d.item())).getString(),right+8,y,112,TowerUi.INK);
            y+=4;
            // Each move: a gem (colour is the type, shape the category) and, spelled out, the type and the category.
            for(var move:d.moves()){
                ByzantineCardFace.drawGem(g,right+8,y,move.type(),move.category());
                g.drawString(font,font.plainSubstrByWidth(ByzantineCardFace.translated("cobblemon.move.",move.id()).getString(),100),right+19,y,TowerUi.INK,false);
                String kind=move.type().isEmpty()?"Type unknown":ByzantineCardFace.tidy(move.type())+" - "+ByzantineCardFace.tidy(move.category().isEmpty()?"unknown":move.category());
                g.drawString(font,kind,right+19,y+9,brown,false);
                y+=20;
            }
        }
        else {var data=PokemonSpecies.getByName(species);g.drawString(font,"Species base stats",right+8,y,brown,false);y+=15;if(data==null)TowerUi.wrapped(g,font,"Join a world to load species data and its 3D model.",right+8,y,112,brown);if(data!=null)for(var e:data.getBaseStats().entrySet().stream().sorted(java.util.Comparator.comparing(e->e.getKey().getShowdownId())).toList()){TowerUi.label(g,font,e.getKey().getShowdownId()+"  "+e.getValue(),right+8,y,112,TowerUi.INK);y+=12;}}
        g.drawString(font,card==null?"REFERENCE COLLECTION":"RENTAL SET INSPECTION",12,16,TowerUi.TEXT,false);
        if(modelFailed)TowerUi.label(g,font,"Model unavailable",20,height-85,vw-16,TowerUi.MUTED);
    }
    /**
     * The display case: oak frame with bronze rim and rivets, dark well with a faint grid and a bronze pedestal,
     * drawn behind the clipped model.
     */
    private static void labDisplay(GuiGraphics g,int x,int y,int w,int h){
        PixelUi.frame(g,PixelUi.Frame.DARK,x,y,w,h);
        int wx=x+6,wy=y+6,ww=w-12,wh=h-12;
        g.fill(wx-1,wy-1,wx+ww+1,wy+wh+1,TowerUi.BRONZE);
        g.fill(wx,wy,wx+ww,wy+wh,0xFF1E1410);
        g.fillGradient(wx,wy,wx+ww,wy+wh,0x00000000,0x55000000);
        int floor=wy+wh*72/100;
        for(int gy=floor;gy<wy+wh;gy+=6)g.fill(wx,gy,wx+ww,gy+1,0x33A77B46);
        for(int gx=wx+(ww/2)%12;gx<wx+ww;gx+=12)g.fill(gx,floor,gx+1,wy+wh,0x22A77B46);
        int cx=wx+ww/2,pw=Math.min(ww-16,Math.max(40,ww*40/100));
        g.fill(cx-pw/2,floor-3,cx+pw/2,floor+3,TowerUi.OUTLINE);
        g.fill(cx-pw/2+1,floor-2,cx+pw/2-1,floor+2,TowerUi.BRONZE);
        g.fill(cx-pw/2+1,floor-2,cx+pw/2-1,floor-1,TowerUi.BRONZE_LIGHT);
        for(int[] r:new int[][]{{x+3,y+3},{x+w-6,y+3},{x+3,y+h-6},{x+w-6,y+h-6}}){g.fill(r[0],r[1],r[0]+3,r[1]+3,TowerUi.OUTLINE);g.fill(r[0]+1,r[1]+1,r[0]+2,r[1]+2,TowerUi.BRONZE_LIGHT);}
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
