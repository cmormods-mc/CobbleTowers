package com.cobbletowers.client;

import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.TowerHallActionPayload;
import com.cobbletowers.network.TowerHallStatePayload;
import com.cobbletowers.network.TrackActionPayload;
import com.cobbletowers.network.TrackStatePayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Live server data in the approved Tower Hall composition; no preview progress is shipped. */
public final class TowerHallScreen extends TowerMenuScreen {
    private TowerHallStatePayload state;
    private PlayStatePayload play;
    private String destination="", trial="";
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private int page, detailPage;
    private long requested;
    private String notice="";
    // The Progress tab's battle tracks (P37): arrive from the server after opening the tab; without them the old
    // cards show.
    private TrackStatePayload tracks;
    private long tracksAt;
    private boolean records;
    private String lane="mastery";
    private TrackStrip strip;
    private Button claimButton, claimAllButton;
    private final java.util.Map<String,double[]> kept=new java.util.HashMap<>();

    public TowerHallScreen(TowerHallStatePayload state) { this.state=state;this.play=state.play();notice=play.message(); }
    public void update(TowerHallStatePayload next) {state=next;play=next.play();notice=play.message();requested=0;rebuild();}
    public void updateLobby(PlayStatePayload next) {play=next;notice=next.message();if(next.open())requested=0;rebuild();}
    @Override protected void init(){rebuild();}
    private void rebuild(){if(strip!=null)kept.put(strip.kind(),new double[]{strip.selectedIndex(),strip.scrollTarget()});strip=null;claimButton=null;claimAllButton=null;clearWidgets();layoutShell();buildPage();}

    public void updateTracks(TrackStatePayload next){tracks=next;tracksAt=System.currentTimeMillis();if(!next.message().isEmpty())notice=next.message();rebuild();}
    private void requestTracks(String tower){if(ClientPlayNetworking.canSend(TrackActionPayload.TYPE))ClientPlayNetworking.send(new TrackActionPayload("refresh","",tower,0));}
    private boolean trackView(){return section.equals("Progress")&&tracks!=null&&!records;}
    private TrackStatePayload.Lane laneData(){return lane.equals("season")&&tracks.season().present()?tracks.season():tracks.mastery();}
    private String laneKind(){return lane.equals("season")&&tracks.season().present()?"season":"mastery";}
    private void claim(String action,int number){if(ClientPlayNetworking.canSend(TrackActionPayload.TYPE))ClientPlayNetworking.send(new TrackActionPayload(action,laneKind(),tracks.selectedTower(),number));}

    private void buildTracks(int x,int y,int w){
        var data=laneData();String kind=laneKind();
        int rowA=y+32,bw=Math.min(64,(w-8)/3);
        var mastery=TowerButton.builder(Component.literal("Mastery"),b->{lane="mastery";rebuild();}).pos(x,rowA).size(bw,16).dark().icon("trophy").build();
        mastery.selected=kind.equals("mastery");mastery.active=!mastery.selected;addRenderableWidget(mastery);
        if(tracks.season().present()){
            var season=TowerButton.builder(Component.literal("Season"),b->{lane="season";rebuild();}).pos(x+bw+4,rowA).size(bw,16).dark().icon("star").build();
            season.selected=kind.equals("season");season.active=!season.selected;addRenderableWidget(season);
        }
        addRenderableWidget(TowerButton.builder(Component.literal("Records"),b->{records=true;rebuild();}).pos(x+w-bw,rowA).size(bw,16).dark().icon("book").build());
        if(kind.equals("mastery")&&tracks.towers().size()>1){
            int index=0;for(int i=0;i<tracks.towers().size();i++)if(tracks.towers().get(i).id().toString().equals(tracks.selectedTower()))index=i;
            final int at=index,count=tracks.towers().size();
            addRenderableWidget(TowerButton.builder(Component.literal("<"),b->{kept.remove("mastery");requestTracks(tracks.towers().get(Math.floorMod(at-1,count)).id().toString());}).pos(x+w-38,y+6).size(16,16).dark().iconOnly("left").build());
            addRenderableWidget(TowerButton.builder(Component.literal(">"),b->{kept.remove("mastery");requestTracks(tracks.towers().get((at+1)%count).id().toString());}).pos(x+w-20,y+6).size(16,16).dark().iconOnly("right").build());
        }
        double[] saved=kept.get(kind);
        strip=new TrackStrip(x,y+51,w,50,kind,data,saved==null?-1:(int)saved[0],saved==null?Double.NaN:saved[1],()->{});
        addRenderableWidget(strip);
        int dy=y+105,dh=Math.max(36,height-46-dy);
        claimButton=TowerButton.builder(Component.literal("Claim"),b->{var n=strip.selectedNode();if(n!=null)claim("claim",n.number());}).pos(x+w-64,dy+4).size(58,16).icon("gift").build();
        claimAllButton=TowerButton.builder(Component.literal("Claim all"),b->claim("claim_all",0)).pos(x+w-64,dy+Math.min(24,dh-20)).size(58,16).dark().build();
        addRenderableWidget(claimButton);addRenderableWidget(claimAllButton);
        addRenderableWidget(TowerButton.builder(Component.literal("Now"),b->{if(strip!=null)strip.jumpToCurrent();}).pos(x+w-64,dy+Math.min(44,dh-1)).size(58,16).dark().build()).visible=dh>=60;
    }
    @Override protected void navigate(String next){if(next.equals("Progress")){records=false;requestTracks("");}if(next.equals("Collection")){minecraft.setScreen(new PartnerInspectionScreen(this,null));return;}if(next.equals("Codex")||next.equals("Echoes")){TowerFeatureScreen.open(this,next.equals("Codex")?"modifiers":"echoes");return;}if(!section.equals(next))history.push(section);section=next;page=0;detailPage=0;requested=0;rebuild();}
    @Override public void onClose(){if(!history.isEmpty()){section=history.pop();page=0;detailPage=0;rebuild();}else minecraft.setScreen(null);}

    private void request(String action,String argument,String revision) {
        if(requested!=0)return;
        if(!ClientPlayNetworking.canSend(TowerHallActionPayload.TYPE)){notice="Tower Hall is unavailable on this server.";return;}
        requested=System.nanoTime();notice="Waiting for the server...";
        ClientPlayNetworking.send(new TowerHallActionPayload(action,argument,revision));
        rebuild();
    }
    @Override public void tick(){if(requested!=0&&(System.nanoTime()-requested)>8_000_000_000L){requested=0;notice="No response. Refresh to check the latest state.";rebuild();}}
    private Button control(String label,int x,int y,int w,Runnable callback){var b=TowerButton.builder(Component.literal(label),ignored->callback.run()).pos(x,y).size(w,20).dark().icon(label.equals("Lobby")||label.startsWith("Return")?"door":label.equals("Refresh")?"refresh":label.startsWith("Daily")?"sun":label.startsWith("Weekly")?"calendar":null).build();b.active=requested==0;addRenderableWidget(b);return b;}
    private void tile(String label,String subtitle,String art,int x,int y,int w,int h,Runnable callback){addRenderableWidget(new DestinationButton(x,y,w,h,label,subtitle,art,callback));}
    private int rows(){return Math.max(1,Math.min(2,(contentHeight-75)/52));}
    private int perPage(){return rows()*2;}

    private void buildPage(){
        int x=contentX,y=contentY,w=contentWidth,bottom=height-64;
        if(section.equals("Tower Hall")) {
            int count=perPage(),pages=Math.max(1,(state.towers().size()+count-1)/count);page=Math.min(page,pages-1);
            int h=Math.max(38,(contentHeight-75)/rows()),cw=(w-6)/2;
            for(int i=0;i<count&&page*count+i<state.towers().size();i++) {
                var t=state.towers().get(page*count+i);
                tile(t.name(),t.floors()+" floors / cycle",t.id().getPath(),x+(i%2)*(cw+6),y+38+(i/2)*h,cw,h-4,()->{destination=t.id().toString();navigate("Tower briefing");});
            }
            int ty=y+38+rows()*h;
            control("Daily Tower",x,ty,cw,()->openTrial("daily"));control("Weekly Tower",x+cw+6,ty,cw,()->openTrial("weekly"));
            if(pages>1){control("<",x,bottom,20,()->{page=Math.floorMod(page-1,pages);rebuild();});control(">",x+24,bottom,20,()->{page=(page+1)%pages;rebuild();});}
        } else if(section.equals("Trials")) {
            tile("Daily Tower","A new daily challenge","tideforge",x,y+34,w,49,()->openTrial("daily"));
            tile("Weekly Tower","The week's expedition","duskvale",x,y+88,w,49,()->openTrial("weekly"));
        } else if(section.equals("Tower briefing")) {
            var selected=state.towers().stream().filter(t->t.id().toString().equals(destination)).findFirst();
            if(selected.isPresent())control("Select tower & open lobby",x,bottom,w,()->request("tower",destination,"")).active=canSelect();
        } else if(section.equals("Trial briefing")) {
            var selected=state.trials().stream().filter(t->t.kind().equals(trial)).findFirst();
            if(selected.isPresent())control("Prepare trial lobby",x,bottom,w,()->request("trial",trial,selected.get().revision())).active=canSelect();
        } else if(trackView()) {
            buildTracks(x,y,w);
        } else if(section.equals("Social")||section.equals("Progress")) {
            boolean social=section.equals("Social");
            if(!social&&tracks!=null)addRenderableWidget(TowerButton.builder(Component.literal("Tracks"),b->{records=false;rebuild();}).pos(x+w-64,y+5).size(58,18).dark().icon("stairs").build());
            String[] labels=social?new String[]{"Party & invites","Club lodge","Spectate","Run codes"}:new String[]{"Season & rewards","Cosmetics & titles","Contracts","Hall of Fame","Last run report","Mastery & boards"};
            String[] captions=social?new String[]{"Gather your team","Your community","Watch challengers","Scout an expedition"}:new String[]{"Your next milestone","Wear your victories","Accept a challenge","Tower legends","Review your run","Measure your growth"};
            String[] keys=social?new String[]{"lobby","club","watch","codes"}:new String[]{"season","cosmetics","contracts","hall","report","mastery"};
            String[] icons=social?new String[]{"group","flag","eye","scroll"}:new String[]{"star","crown","scroll","trophy","book","stairs"};
            int[] colors={TowerUi.SAGE,TowerUi.BURGUNDY,TowerUi.BRONZE_LIGHT};
            int cw=(w-6)/2, cardH=Math.min(57,(height-47-(y+34))/(social?2:3));
            for(int i=0;i<labels.length;i++){
                String key=keys[i];
                var card=new TrainerCardButton(x+(i%2)*(cw+6),y+34+(i/2)*cardH,cw,cardH-4,labels[i],captions[i],icons[i],colors[i%3],()->{
                    if(key.equals("lobby"))request("lobby","","");
                    else if(key.equals("mastery")){if(minecraft.player!=null){minecraft.setScreen(null);minecraft.player.connection.sendCommand("tower mastery");}}
                    else TowerFeatureScreen.open(this,key);
                });
                card.active=requested==0;addRenderableWidget(card);
            }
        }

        if(section.endsWith("briefing")) {
            int max=detailPages();detailPage=Math.min(detailPage,max-1);
            if(max>1){control("Previous",x,bottom-23,(w-6)/2,()->{detailPage=Math.floorMod(detailPage-1,max);rebuild();});control("More",x+(w+6)/2,bottom-23,(w-6)/2,()->{detailPage=(detailPage+1)%max;rebuild();});}
        }
        int fw=Math.min(95,w/2);
        control(state.canResume()?"Return to run":"Lobby",x,height-29,fw,()->request(state.canResume()?"resume":"lobby","",""));
        control("Refresh",x+w-58,height-29,58,()->request("refresh","",""));
    }

    private boolean canSelect(){return requested==0 && state.runStatus().isEmpty() && play.lobby().countdown()<0 && (play.lobby().role()==0||play.lobby().role()==1);}
    private void openTrial(String kind){trial=kind;navigate("Trial briefing");}
    private List<FormattedCharSequence> details(){
        List<String> lines=new ArrayList<>();
        if(section.equals("Tower briefing")) state.towers().stream().filter(t->t.id().toString().equals(destination)).findFirst().ifPresentOrElse(t->{
            lines.add(t.name());lines.add(t.floors()+" floors per cycle / "+t.milestones()+" milestones");
            lines.add(t.ascends()?"Ascension continues into harder cycles.":"The tower ends after its final floor.");
            lines.add("Select this tower to create or update your lobby. Review mode, party registration and invitations before starting.");
        },()->lines.add("This tower is no longer available. Refresh the Hall."));
        else state.trials().stream().filter(t->t.kind().equals(trial)).findFirst().ifPresentOrElse(t->{lines.add(t.title());lines.addAll(t.rules());lines.add("Preparing does not launch a run. Trial rules will be checked again by the server.");},()->lines.add("No "+trial+" trial is currently available."));
        List<FormattedCharSequence> result=new ArrayList<>();for(String line:lines){result.addAll(font.split(Component.literal(line),Math.max(20,contentWidth-14)));}return result;
    }
    private int detailLines(){return Math.max(1,(contentHeight-76)/11);}
    private int detailPages(){return Math.max(1,(details().size()+detailLines()-1)/detailLines());}

    /** "12 days", "5 h" or "40 min": how long is left, rounded to the unit that matters. */
    static String timeLeft(long millis){
        if(millis<=0)return "ending now";
        long days=millis/86_400_000L,hours=millis/3_600_000L;
        if(days>=2)return days+" days";
        if(hours>=1)return hours+(hours==1?" hour":" hours");
        return Math.max(1,millis/60_000L)+" min";
    }

    /**
     * The lane's header (title, subtitle, progress, and the season's days-left box) and the selected tile's detail
     * panel.
     */
    private void drawTracks(GuiGraphics g,int x,int y,int w){
        var data=laneData();boolean season=laneKind().equals("season");
        TowerUi.panel(g,x,y,w,28,theme.accent);
        int right=season?96:(tracks.towers().size()>1?40:0);
        TowerUi.label(g,font,data.title(),x+8,y+5,w-16-right,TowerUi.BRONZE_LIGHT);
        String subtitle=data.subtitle();
        if(!season&&tracks.towers().size()>1)for(var t:tracks.towers())if(t.id().toString().equals(tracks.selectedTower()))subtitle=t.name()+": "+subtitle;
        TowerUi.label(g,font,subtitle,x+8,y+16,w-16-right,TowerUi.MUTED);
        if(season){
            long left=tracksAt+data.endsInMillis()-System.currentTimeMillis();
            int bx=x+w-92;
            PixelUi.frame(g,PixelUi.Frame.BUTTON,bx,y+3,88,22);
            TowerUi.label(g,font,data.timerLabel().isEmpty()?"ENDS IN":data.timerLabel(),bx+6,y+5,78,TowerUi.MUTED);
            TowerUi.label(g,font,timeLeft(left),bx+6,y+14,78,TowerUi.TEXT);
            if(data.need()>0){
                int bar=w-16-right;g.fill(x+8,y+24,x+8+bar,y+26,0xFF211510);
                g.fill(x+8,y+24,x+8+bar*Math.min(data.into(),data.need())/data.need(),y+26,TowerUi.BRONZE_LIGHT);
            }
        }
        int dy=y+105,dh=Math.max(36,height-46-dy);
        TowerUi.panel(g,x,dy,w,dh,theme.accent);
        var node=strip==null?null:strip.selectedNode();
        int textW=w-76;
        if(node==null){
            TowerUi.wrapped(g,font,"Select a tile to see its reward. Scroll with the wheel, drag, or use the Left and Right keys.",x+8,dy+6,textW,TowerUi.MUTED);
        }else{
            String what=(season?"Step ":"Level ")+node.number();
            String stateText=switch(node.state()){case TrackStatePayload.CLAIMABLE->"ready to claim";case TrackStatePayload.CLAIMED->"claimed";case TrackStatePayload.LOCKED->"locked";case TrackStatePayload.UNAVAILABLE->"unavailable here";default->"";};
            TowerUi.label(g,font,what+(stateText.isEmpty()?"":"  -  "+stateText),x+8,dy+5,textW,node.state()==TrackStatePayload.CLAIMABLE?TowerUi.BRONZE_LIGHT:TowerUi.TEXT);
            int ty=dy+16;
            if(!node.reward().isEmpty())ty=TowerUi.wrapped(g,font,node.reward(),x+8,ty,textW,TowerUi.TEXT);
            else if(node.note().isEmpty())ty=TowerUi.wrapped(g,font,season?"No reward on this step.":"No reward at this level. Keep unlocking achievements to climb.",x+8,ty,textW,TowerUi.MUTED);
            if(!node.note().isEmpty()&&ty<dy+dh-9)TowerUi.label(g,font,node.note(),x+8,ty,textW,TowerUi.MUTED);
        }
        if(claimButton!=null)claimButton.active=node!=null&&node.state()==TrackStatePayload.CLAIMABLE;
        if(claimAllButton!=null)claimAllButton.active=strip!=null&&strip.anyClaimable();
    }

    @Override protected void drawContent(GuiGraphics g){
        int x=contentX,y=contentY,w=contentWidth;
        if(section.equals("Tower Hall")) {
            TowerPanorama.draw(g,x,y,w,32,"tideforge",uiAge(),true);
            g.fill(x,y,x+w*46/100,y+32,0xA02A1A12);
            TowerUi.label(g,font,"Every ascent begins here.",x+7,y+12,w-14,TowerUi.TEXT);
        } else if(section.endsWith("briefing")) {
            TowerPanorama.draw(g,x,y,w,28,section.equals("Tower briefing")?destination:"tideforge",uiAge(),true);
            var lines=details();int limit=detailLines();
            for(int i=0;i<limit&&detailPage*limit+i<lines.size();i++)g.drawString(font,lines.get(detailPage*limit+i),x+7,y+35+i*11,TowerUi.INK,false);
        } else if(trackView()) {
            drawTracks(g,x,y,w);
        } else {
            TowerUi.panel(g,x,y,w,28,theme.accent);
            String copy=switch(section){
                case "Trials" -> "Scheduled challenges";
                case "Social" -> "";
                case "Progress" -> "";
                case "Codex" -> "The codex catalog is not connected in this build.";
                case "Echoes" -> "The Echo gallery is not connected in this build.";
                default -> "";
            };
            if(section.equals("Progress")||section.equals("Social")) {

                TowerUi.label(g,font,section.equals("Progress")?"TRAINER RECORD":"LINK LOUNGE",x+8,y+5,w-16,TowerUi.BRONZE_LIGHT);
                TowerUi.label(g,font,section.equals("Progress")?"Every victory leaves a mark.":"Great battles begin with good company.",x+8,y+17,w-16,TowerUi.MUTED);
            }
            TowerUi.wrapped(g,font,copy,x+5,y+35,w-10,TowerUi.INK);
            
            
        }
        if(section.equals("Tower Hall")&&state.towers().size()>perPage())g.drawString(font,(page+1)+" / "+((state.towers().size()+perPage()-1)/perPage()),x+51,height-58,TowerUi.INK,false);
        String status=!notice.isEmpty()?notice:!state.runStatus().isEmpty()?state.runStatus():play.lobby().role()==0?"Choose a destination to begin.":"Your expedition lobby is available.";
        TowerUi.label(g,font,status,x,height-40,w,0xFF6B4A33);
    }

    private static final class DestinationButton extends Button {
        private final String subtitle,art;
        private long hoverAt;
        DestinationButton(int x,int y,int w,int h,String label,String subtitle,String art,Runnable action){
            super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.subtitle=subtitle;this.art=art;
            setTooltip(Tooltip.create(Component.literal(label+"\n"+subtitle)));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
            boolean hover=isHoveredOrFocused();if(!hover)hoverAt=0;else if(hoverAt==0)hoverAt=System.nanoTime();
            int x=getX(),y=getY(),w=getWidth(),h=getHeight();
            PixelUi.frame(g,PixelUi.Frame.DARK,x,y,w,h);
            TowerPanorama.draw(g,x+3,y+3,w-6,Math.max(10,h-18),art,hoverAt==0?1400:(System.nanoTime()-hoverAt)/1_000_000,hover);
            var font=TowerFonts.get();
            TowerUi.label(g,font,getMessage().getString(),x+5,y+h-12,w-10,TowerUi.TEXT);
            if(hover)PixelUi.brackets(g,x+2,y+2,w-4,h-4,TowerUi.BRONZE_LIGHT);
        }
        @Override public void playDownSound(SoundManager manager){if(TowerUiSettings.sounds)super.playDownSound(manager);}
    }
}
