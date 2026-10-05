package com.cobbletowers.client;

import com.cobbletowers.network.TowerFeatureRequest;
import com.cobbletowers.network.TowerFeatureState;
import com.cobbletowers.network.TowerFeatureState.Action;
import com.cobbletowers.network.TowerFeatureState.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Paginated ledger with explicit review forms. Late responses only update their requesting screen. */
public final class TowerFeatureScreen extends TowerMenuScreen {
    private static final AtomicInteger IDS=new AtomicInteger();
    private final TowerHallScreen parent;
    private String feature;
    private TowerFeatureState state;
    private int requestId,page,linePage,selected=-1;
    private long pending;
    private Action review;
    private EditBox input;
    private String typed="",status="";
    private boolean uncertain;

    private TowerFeatureScreen(TowerHallScreen parent,String feature){this.parent=parent;this.feature=feature;section=feature;}
    static void open(TowerHallScreen parent,String feature){var screen=new TowerFeatureScreen(parent,feature);Minecraft.getInstance().setScreen(screen);screen.send("open","");}
    static TowerFeatureScreen preview(TowerHallScreen parent,TowerFeatureState sample){var s=new TowerFeatureScreen(parent,sample.section());s.state=sample;s.section=sample.title();s.status=sample.message();return s;}
    @Override protected void init(){rebuild();}
    private void send(String action,String argument){
        if(pending!=0)return;
        if(!ClientPlayNetworking.canSend(TowerFeatureRequest.TYPE)){status="This server does not support these feature menus.";rebuild();return;}
        requestId=IDS.incrementAndGet();pending=System.nanoTime();status="Waiting for the server...";uncertain=false;
        ClientPlayNetworking.send(new TowerFeatureRequest(requestId,feature,action,argument));rebuild();
    }
    public void accept(TowerFeatureState next){
        if(next.requestId()!=requestId||!next.section().equals(feature))return;
        String previousId=state!=null&&selected>=0&&selected<state.entries().size()?state.entries().get(selected).id():null;
        state=next;section=next.title();pending=0;uncertain=false;status=next.message();review=null;typed="";selected=-1;
        if(!next.message().isEmpty()&&!state.entries().isEmpty())selected=0;
        else if(previousId!=null)for(int i=0;i<state.entries().size();i++)if(state.entries().get(i).id().equals(previousId)){selected=i;break;}
        rebuild();
    }
    @Override public void tick(){if(pending!=0&&System.nanoTime()-pending>8_000_000_000L){pending=0;uncertain=true;status="Response delayed. Refresh before another action.";rebuild();}}
    @Override protected void navigate(String next){minecraft.setScreen(parent);parent.navigate(next);}
    @Override public void onClose(){
        if(review!=null){review=null;typed="";linePage=0;rebuild();}
        else if(selected>=0){selected=-1;linePage=0;rebuild();}
        else minecraft.setScreen(parent);
    }
    private int rows(){return Math.max(1,(height-64-contentY-34)/24);}
    private int lineCount(){return Math.max(2,(height-(review!=null&&!review.inputHint().isEmpty()?110:89)-contentY-34)/11);}
    private List<FormattedCharSequence> body(){
        List<String> lines=review!=null?List.of(review.confirmation().isEmpty()?review.label():review.confirmation()):state!=null&&selected>=0?state.entries().get(selected).lines():List.of();
        List<FormattedCharSequence> result=new ArrayList<>();for(String line:lines)result.addAll(font.split(Component.literal(line),Math.max(32,contentWidth-14)));return result;
    }
    private void button(String label,int x,int y,int w,Runnable run,boolean enabled){var b=TowerButton.builder(Component.literal(label),ignored->run.run()).pos(x,y).size(w,20).dark().build();b.active=enabled;addRenderableWidget(b);}
    TowerHallScreen hall(){return parent;}
    private void choose(Action a){
        if(a.id().equals("lobby")){
            if(ClientPlayNetworking.canSend(com.cobbletowers.network.TowerHallActionPayload.TYPE))
                ClientPlayNetworking.send(new com.cobbletowers.network.TowerHallActionPayload("lobby","",""));
            return;
        }
        if(a.id().equals("page")){feature=a.argument();selected=-1;page=0;linePage=0;state=null;send("open","");return;}
        if(!a.confirmation().isEmpty()||!a.inputHint().isEmpty()){review=a;typed="";linePage=0;rebuild();}
        else send(a.id(),a.argument());
    }
    private void rebuild(){
        if(input!=null&&review!=null)typed=input.getValue();input=null;
        clearWidgets();layoutShell();int x=contentX,y=contentY,w=contentWidth;
        boolean ready=pending==0&&!uncertain;
        if(state!=null&&review==null&&selected<0){
            int total=state.entries().size()+state.actions().size(),pages=Math.max(1,(total+rows()-1)/rows());page=Math.min(page,pages-1);
            for(int slot=0;slot<rows()&&page*rows()+slot<total;slot++){
                int index=page*rows()+slot;String label=index<state.entries().size()?state.entries().get(index).title():state.actions().get(index-state.entries().size()).label();
                var card=new TrainerCardButton(x,y+34+slot*24,w,22,label,"",feature.equals("echoes")?"scizor":feature.equals("club")?"machamp":"blastoise",0xFF65C7D5,()->{if(index<state.entries().size()){selected=index;linePage=0;rebuild();}else choose(state.actions().get(index-state.entries().size()));});card.active=ready;addRenderableWidget(card);
            }
            if(pages>1){button("<",x,height-62,24,()->{page=Math.floorMod(page-1,pages);rebuild();},ready);button(">",x+28,height-62,24,()->{page=(page+1)%pages;rebuild();},ready);}
        }else if(state!=null){
            var body=body();int pages=Math.max(1,(body.size()+lineCount()-1)/lineCount());linePage=Math.min(linePage,pages-1);
            int pagingY=review!=null&&!review.inputHint().isEmpty()?height-111:height-86;
            if(pages>1){button("Previous",x,pagingY,(w-6)/2,()->{linePage=Math.floorMod(linePage-1,pages);rebuild();},ready);button("More",x+(w+6)/2,pagingY,(w-6)/2,()->{linePage=(linePage+1)%pages;rebuild();},ready);}
            if(review!=null){
                if(!review.inputHint().isEmpty()){
                    input=new EditBox(font,x+2,height-87,w-4,20,Component.literal(review.inputHint()));input.setMaxLength(256);input.setHint(Component.literal(review.inputHint()));input.setValue(typed);input.setEditable(ready);addRenderableWidget(input);
                }
                button("Confirm",x,height-62,(w-6)/2,()->send(review.id(),input==null?review.argument():input.getValue().trim()),ready&&linePage==pages-1);
                button("Cancel",x+(w+6)/2,height-62,(w-6)/2,this::onClose,pending==0);
            }else{
                Entry e=state.entries().get(selected);
                if(!e.actions().isEmpty())button(e.actions().getFirst().label(),x,height-62,w,()->choose(e.actions().getFirst()),ready);
            }
        }
        button("Overview",x,height-29,Math.min(90,w/2),()->{selected=-1;review=null;linePage=0;rebuild();},pending==0);
        button("Refresh",x+w-58,height-29,58,()->send("open",""),pending==0);
    }
    @Override protected void drawContent(GuiGraphics g){
        int x=contentX,y=contentY,w=contentWidth;
        String art=switch(feature){case "echoes"->"echoes";case "club","club-top","club-alltime"->"rootvale";case "season","cosmetics","hall"->"neutral";default->"tideforge";};
        TowerUi.panel(g,x,y,w,26,theme.accent);
        String heading=review!=null?review.label():state!=null&&selected>=0?state.entries().get(selected).title():section;
        TowerUi.label(g,font,heading,x+7,y+9,w-14,TowerUi.TEXT);
        if(review!=null||selected>=0){var lines=body();for(int i=0;i<lineCount()&&linePage*lineCount()+i<lines.size();i++)g.drawString(font,lines.get(linePage*lineCount()+i),x+7,y+34+i*11,TowerUi.TEXT,false);}
        else if(state!=null){int pages=Math.max(1,(state.entries().size()+state.actions().size()+rows()-1)/rows());if(pages>1)g.drawString(font,(page+1)+" / "+pages,x+60,height-56,TowerUi.MUTED,false);}
        TowerUi.label(g,font,status,x,height-40,w,TowerUi.MUTED);
    }
}
