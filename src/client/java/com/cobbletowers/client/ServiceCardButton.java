package com.cobbletowers.client;
import com.cobbletowers.network.VendorCatalogPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
final class ServiceCardButton extends Button {
    private final VendorCatalogPayload.Entry entry;
    ServiceCardButton(int x,int y,int w,VendorCatalogPayload.Entry entry,Runnable select){super(x,y,w,43,Component.literal(entry.displayName()),b->select.run(),DEFAULT_NARRATION);this.entry=entry;setTooltip(Tooltip.create(Component.literal(entry.displayName()+"\n"+entry.priceCobbleDollars()+" CobbleDollars\n"+(entry.remainingPurchases()<0?"Unlimited stock":entry.remainingPurchases()+" remaining"))));}
    @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
        int x=getX(),y=getY(),w=getWidth();PixelUi.panel(g,x,y,w,43,TowerUi.BRONZE,isHoveredOrFocused()?1:0);
        var font=Minecraft.getInstance().font;var lines=font.split(getMessage(),w-45);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),x+8,y+6+i*10,TowerUi.TEXT,false);
        PixelUi.tag(g,font,entry.remainingPurchases()==0?"Sold out":entry.priceCobbleDollars()+" dollars",x+6,y+28,entry.remainingPurchases()==0);
        String id=entry.id().getPath();var icon=id.contains("heal")?Items.POTION:id.contains("revive")?Items.TOTEM_OF_UNDYING:id.contains("reroll")?Items.AMETHYST_SHARD:Items.PAPER;
        g.pose().pushPose();g.pose().translate(x+w-31,y+7,0);g.pose().scale(1.5f,1.5f,1);g.renderItem(new ItemStack(icon),0,0);g.pose().popPose();
    }
    @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
}
