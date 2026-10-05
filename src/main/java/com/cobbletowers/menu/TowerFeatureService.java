package com.cobbletowers.menu;

import com.cobbletowers.club.ClubBook;
import com.cobbletowers.club.ClubService;
import com.cobbletowers.definition.*;
import com.cobbletowers.echo.EchoPolicy;
import com.cobbletowers.mastery.RunSummaries;
import com.cobbletowers.network.TowerFeatureRequest;
import com.cobbletowers.network.TowerFeatureState;
import com.cobbletowers.network.TowerFeatureState.Action;
import com.cobbletowers.network.TowerFeatureState.Entry;
import com.cobbletowers.persistence.*;
import com.cobbletowers.season.*;
import com.cobbletowers.spectator.Watching;
import java.util.*;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Menu adapters for the existing gameplay services. No chat parsing or arbitrary command execution. */
public final class TowerFeatureService {
    private static final Set<String> SECTIONS=Set.of("cosmetics","season","hall","echoes","club","club-top","club-alltime",
            "watch","codes","contracts","report","modifiers","relics");
    private TowerFeatureService() {}
    static Action action(String id,String label,String argument,String confirmation,String input) {return new Action(id,label,argument,confirmation,input);}
    static Action link(String label,String section) {return action("page",label,section,"","");}
    private static Entry entry(String id,String title,List<String> lines,Action... actions) {
        return new Entry(id,limit(title,256),lines.stream().map(s->limit(s,512)).limit(32).toList(),List.of(actions));
    }
    private static String limit(String s,int max){return s.length()>max?s.substring(0,max-3)+"...":s;}

    public static void handle(MinecraftServer server,ServerPlayer player,TowerFeatureRequest request) {
        if(!SECTIONS.contains(request.section())||!ServerPlayNetworking.canSend(player,TowerFeatureState.TYPE))return;
        if(com.cobbletowers.battle.cobblemon.PartyStorage.inBattle(player)) {
            ServerPlayNetworking.send(player,new TowerFeatureState(request.requestId(),request.section(),"Battle in progress",List.of(),List.of(),"Finish your battle before using these menus."));
            return;
        }
        TowerFeatureState before=view(server,player,request,"");
        if(request.action().equals("open")){ServerPlayNetworking.send(player,before);return;}
        String message="";
        if(!request.action().equals("open")) {
            boolean offered=isOffered(before,request);
            if(!offered)message="That action is no longer available. The menu has been refreshed.";
            else message=apply(server,player,request);
        }
        ServerPlayNetworking.send(player,view(server,player,request,limit(message,512)));
    }

    static boolean isOffered(TowerFeatureState state,TowerFeatureRequest request) {
        return state.section().equals(request.section()) && Stream.concat(state.actions().stream(),state.entries().stream().flatMap(e->e.actions().stream()))
                .anyMatch(a->a.id().equals(request.action())&&!Set.of("page","lobby").contains(a.id())
                        &&(!a.inputHint().isEmpty()||a.argument().equals(request.argument())));
    }

    private static String apply(MinecraftServer server,ServerPlayer player,TowerFeatureRequest r) {
        String arg=r.argument().trim();UUID id=player.getUUID();
        return switch(r.action()) {
            case "title-wear" -> {
                var owned=Cosmetics.titlesOf(TowerSeasonProgressStore.get(server).cosmeticsOf(id));
                int index=owned.indexOf(arg);
                yield index<0?"You no longer own that title.":CosmeticsService.wear(server,player,index+1);
            }
            case "title-off" -> CosmeticsService.takeOff(server,player);
            case "echo-on","echo-off" -> {
                var store=TowerEchoStore.get(server);boolean off=r.action().equals("echo-off");
                int removed=store.setOptedOut(id,off);store.checkpoint(server);
                yield off?"Recording disabled. "+removed+" recorded Echoes removed.":"Recording enabled for future qualifying runs.";
            }
            case "club-create" -> {
                String[] parts=arg.split("\\s+");
                yield arg.isEmpty()||parts.length>2?"Enter a club name and optional tag.":ClubService.create(server,player,parts[0],parts.length==2?parts[1]:null);
            }
            case "club-accept" -> ClubService.accept(server,player,arg);
            case "club-invite" -> {
                var target=server.getPlayerList().getPlayerByName(arg);
                yield target==null?"That player is not online.":ClubService.invite(server,player,target);
            }
            case "club-leave" -> ClubService.leave(server,player);
            case "club-disband" -> ClubService.disband(server,player);
            case "club-kick" -> ClubService.kick(server,player,arg);
            case "club-banner" -> ClubService.banner(server,player,arg);
            case "club-claim" -> ClubService.claim(server,player);
            case "watch-start" -> {
                var target=server.getPlayerList().getPlayerByName(arg);
                yield target==null?"That player is not online.":Watching.start(server,player,target);
            }
            case "watch-stop" -> {String reply=Watching.stop(server,player);yield reply.isEmpty()?"Returned from spectating.":reply;}
            case "code-apply" -> com.cobbletowers.lobby.LobbyService.useCode(server,player,arg);
            case "code-inspect" -> com.cobbletowers.runcode.RunCode.decode(arg)
                    .map(code->"Tower: "+code.tower()+" / Mode: "+code.playlist().map(Object::toString).orElse("Standard")+" / Ascension: "+code.ascension())
                    .orElse("Invalid run code. Check the complete CT1 code.");
            case "contract-reroll" -> {
                String[] pair=arg.split(":");
                yield com.cobbletowers.contract.ContractService.reroll(server,id,pair[0].equals("daily")?ContractTemplateDefinition.Period.DAILY:ContractTemplateDefinition.Period.WEEKLY,Integer.parseInt(pair[1]));
            }
            default -> "Unsupported action.";
        };
    }

    static TowerFeatureState view(MinecraftServer server,ServerPlayer player,TowerFeatureRequest r,String message) {
        List<Entry> entries=new ArrayList<>();List<Action> actions=new ArrayList<>();UUID id=player.getUUID();String title=r.section();
        switch(r.section()) {
            case "cosmetics" -> {
                title="Cosmetics & titles";var store=TowerSeasonProgressStore.get(server);var owned=store.cosmeticsOf(id);
                entries.add(entry("overview","Your collection",List.of("Titles decorate your name. Badges, banners and club marks remain earned collectibles.","Earn cosmetics through season milestones and club finishes.")));
                for(String cosmetic:new TreeSet<>(owned)) {
                    boolean wearable=Cosmetics.titlesOf(List.of(cosmetic)).size()==1;
                    entries.add(entry(cosmetic,CosmeticsService.nameOf(cosmetic),List.of(store.selectedTitle(id).equals(cosmetic)?"Currently worn":"Owned",wearable?"Equippable title":"Collection cosmetic"),
                            wearable?new Action[]{action("title-wear","Wear title",cosmetic,"","")}:new Action[0]));
                }
                if(!store.selectedTitle(id).isEmpty())actions.add(action("title-off","Remove title","","",""));
                actions.add(link("Season rewards","season"));
            }
            case "season" -> {
                title="Season & rewards";entries.add(entry("status","Current season",SeasonService.status()));
                entries.add(entry("progress","Your season progress",SeasonProgressService.trackLines(server,id)));
                var current=Seasons.activeNumber();var track=SeasonTrackRegistry.current();
                if(current.isPresent()&&track.isPresent()) {
                    int reached=track.get().stepsFor(TowerSeasonProgressStore.get(server).of(id,current.get()).total());
                    for(var step:track.get().steps())entries.add(entry("step:"+step.number(),"Step "+step.number()+ (step.number()<=reached?" / Reached":" / "+step.number()*track.get().stepCost()+" points"),
                            List.of(SeasonProgressService.describeStep(step,current.get()),"Milestone rewards are granted automatically. There is no manual season claim.")));
                }
                actions.add(link("Cosmetics","cosmetics"));actions.add(link("Hall of Fame","hall"));
            }
            case "hall" -> {
                title="Hall of Fame";entries.add(entry("latest","Latest completed season",SeasonService.hall(server,Optional.empty())));
                // Completed seasons only; selecting an archive never fabricates a current champion.
                var latest=TowerHallStore.get(server).latest();
                if(latest.isPresent())for(int n=latest.get().number();n>Math.max(0,latest.get().number()-64);n--)
                    if(TowerHallStore.get(server).has(n))entries.add(entry("season:"+n,"Season "+n,SeasonService.hall(server,Optional.of(n))));
            }
            case "echoes" -> {
                title="Echo gallery";var store=TowerEchoStore.get(server);boolean off=store.isOptedOut(id);
                entries.add(entry("recording","Recording "+(off?"disabled":"enabled"),List.of("Qualifying top-ten regional teams can become Echo opponents.","Echo duels appear through run events; this gallery does not launch a duel.")));
                for(var echo:store.ownedBy(id))entries.add(entry(echo.id().toString(),echo.tower().getPath()+" / "+(echo.season()>0?"Season "+echo.season():"All-time"),List.of(
                        "Team: "+String.join(", ",echo.team().stream().map(EchoPolicy::speciesOf).toList()),"Challengers faced: "+echo.faced(),"Duels won: "+echo.beat())));
                actions.add(action(off?"echo-on":"echo-off",off?"Enable recording":"Disable recording","",off?"Future qualifying runs may be recorded; removed Echoes are not restored.":"This removes your recorded Echoes from the pool immediately. Continue?",""));
            }
            case "club" -> {
                title="Club lodge";entries.add(entry("club","Your club",ClubService.info(server,player)));
                var book=TowerClubStore.get(server).book();var club=book.clubOf(id);
                if(club.isEmpty()) {
                    actions.add(action("club-create","Create club","","Create this club?","Name [TAG]"));
                    actions.add(action("club-accept","Accept invitation","","Join this club?","Club name"));
                } else {
                    var c=club.get();boolean owner=c.owner().equals(id);
                    if(book.weekClears(c,ClubService.weekKey())>=ClubBook.WEEKLY_GOAL&&!c.claimed().contains(id))actions.add(action("club-claim","Claim weekly reward","","Claim your weekly club reward?",""));
                    if(owner) {
                        actions.add(action("club-invite","Invite player","","Send this club invitation?","Player name"));
                        actions.add(action("club-banner","Set banner","","Change the club banner?","Banner color"));
                        actions.add(action("club-disband","Disband club","","Disband this club and remove its memberships? This cannot be undone.",""));
                    } else actions.add(action("club-leave","Leave club","","Leave your current club?",""));
                    for(var member:c.members().entrySet())entries.add(entry("member:"+member.getKey(),member.getValue(),List.of(member.getKey().equals(c.owner())?"Club owner":"Club member"),owner&&!member.getKey().equals(id)?new Action[]{action("club-kick","Remove member",member.getValue(),"Remove "+member.getValue()+" from the club?","")}:new Action[0]));
                    entries.add(entry("banners","Available banners",List.of(String.join(", ",ClubBook.BANNERS),"Earned: "+String.join(", ",new TreeSet<>(c.unlockedBanners())))));
                }
                actions.add(link("Season standings","club-top"));actions.add(link("All-time standings","club-alltime"));
            }
            case "club-top","club-alltime" -> {title=r.section().equals("club-top")?"Season club standings":"All-time club standings";int n=0;for(String line:ClubService.top(server,r.section().equals("club-alltime")))entries.add(entry("row:"+n++,line,List.of(line)));actions.add(link("Your club","club"));}
            case "watch" -> {
                title="Watch an expedition";entries.add(entry("watch","Spectating",List.of("Follow an online player's active Tower run. Eligibility is checked before moving you.","Stop watching returns you from spectator mode.")));
                actions.add(action("watch-start","Watch player","","Move into spectator mode and watch this player?","Player name"));
                if(Watching.isWatching(id))actions.add(action("watch-stop","Stop watching","","Return from spectating?",""));
            }
            case "codes" -> {
                title="Run code reference";entries.add(entry("codes","Inspect a run code",List.of("CT1 codes describe a tower, mode, Ascension and run seed.","They are not lobby invitation codes. Inspecting one does not join or launch a run.","Prepare replay lobby applies the code through the existing host validation.")));
                actions.add(action("code-inspect","Inspect code","","","CT1 run code"));
                var lobby=com.cobbletowers.lobby.LobbyService.lobbyOf(id);
                if(com.cobbletowers.runtime.TowerRuns.forPlayer(id).filter(run->!run.isRetired()).isEmpty()
                        && (lobby.isEmpty() || lobby.get().host().equals(id) && !lobby.get().counting()))
                    actions.add(action("code-apply","Prepare replay lobby","","Apply this run code? It can replace the staged tower, mode and rental draft. It does not start a run.","CT1 run code"));
                actions.add(action("lobby","Open lobby","","",""));
            }
            case "contracts" -> {
                title="Contracts";int n=0;for(String line:com.cobbletowers.contract.ContractService.describe(server,id))entries.add(entry("contract:"+n++,line,List.of(line)));
                for(String period:List.of("daily","weekly"))for(int slot=0;slot<(period.equals("daily")?3:2);slot++)actions.add(action("contract-reroll","Reroll "+period+" "+(slot+1),period+":"+slot,"Replace this contract using the period's reroll? Completed contracts cannot be rerolled.",""));
            }
            case "report" -> {title="Last run report";RunSummaries.lastReportOf(id).ifPresentOrElse(report->entries.add(entry("report",report.tower(),report.lines())),()->entries.add(entry("empty","No completed run",List.of("Finish a run to see its report here."))));}
            case "modifiers","relics" -> {
                boolean relic=r.section().equals("relics");title=relic?"Relic codex":"Modifier codex";
                TowerDefinitionRegistry.content().modifiers().values().stream().filter(m->m.relic()==relic).sorted(Comparator.comparing(ModifierDefinition::displayName))
                        .forEach(m->entries.add(entry(m.id().toString(),m.displayName(),ModifierMenuText.lines(m))));
                actions.add(link(relic?"Modifiers":"Relics",relic?"modifiers":"relics"));
            }
        }
        if(!message.isEmpty())entries.addFirst(entry("result","Latest result",List.of(message)));
        return new TowerFeatureState(r.requestId(),r.section(),title,entries.stream().limit(128).toList(),actions,message);
    }
}
