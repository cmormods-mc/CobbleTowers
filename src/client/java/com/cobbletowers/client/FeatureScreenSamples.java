package com.cobbletowers.client;

import com.cobbletowers.network.TowerFeatureState;
import com.cobbletowers.network.TowerFeatureState.Action;
import com.cobbletowers.network.TowerFeatureState.Entry;
import java.util.List;

/** Synthetic screenshot fixtures, used only by the explicitly enabled development harness. */
final class FeatureScreenSamples {
    static List<TowerFeatureState> all(){return List.of(
        state("cosmetics","Cosmetics & titles",List.of(
            entry("collection","Your collection","Titles decorate your name. Other cosmetics remain earned collectibles."),
            new Entry("s1:title_champion","Rising Tide / Champion",List.of("Owned","Equippable title"),List.of(action("title-wear","Wear title","s1:title_champion","",""))),
            entry("s1:badge_1","Rising Tide / Challenger badge","Collection cosmetic")),List.of()),
        state("season","Season & rewards",List.of(entry("status","The Rising Tide","Week 2 of the season / sample calendar"),entry("progress","Your season progress","620 points / 8 milestones reached"),entry("step9","Step 9 / 675 points","Milestone grants are automatic. No manual claim is required.")),List.of()),
        state("echoes","Echo gallery",List.of(entry("recording","Recording enabled","Qualifying top-ten teams may become Echo opponents.")),List.of(action("echo-off","Disable recording","","This removes your recorded Echoes from the pool immediately. Continue?",""))),
        state("club","Club lodge",List.of(entry("club","[TIDE] Stormwatch","Weekly goal: 10 / 10 regional clears","Banner: blue / 4 members"),entry("member","Ari","Club member")),List.of(action("club-claim","Claim weekly reward","","Claim your weekly club reward?",""),action("club-invite","Invite player","","Send this club invitation?","Player name"))),
        state("modifiers","Modifier codex",List.of(entry("cobbletowers:sharpened_claws","Sharpened Claws","[+] Moderate risk  *  +8% to your final payout","[-] Foes rise 3 levels above the tower.","[+] Loot swells to x1.25 on every floor."),entry("cobbletowers:downpour","Downpour","[+] Moderate risk  *  +8% to your final payout","The sky turns: Rain in every battle.")),List.of()),
        state("watch","Watch an expedition",List.of(entry("watch","Spectating","Follow an online player's active Tower run. Eligibility is checked before moving you.")),List.of(action("watch-start","Watch player","","Move into spectator mode and watch this player?","Player name"))),
        state("codes","Run code reference",List.of(entry("codes","Replay a shared setup","CT1 codes describe a tower, mode, Ascension and seed. They are not lobby invitations.")),List.of(action("code-inspect","Inspect code","","","CT1 run code"))),
        state("run","Current run",List.of(entry("run","This run","Battle Tower, floor 6","[+] Risk carried: +16% to your final payout.","2 modifiers, 1 relic (room for 6)."),
                entry("mod:cobbletowers:sharpened_claws","Modifier: Sharpened Claws x2 (locked in)","[+] Moderate risk  *  +8% to your final payout","[-] Foes rise 3 levels above the tower.","[+] Loot swells to x1.25 on every floor."),
                entry("relic:cobbletowers:war_banner","Relic: War Banner","[+] Start battles with +1 Attack and Sp. Atk.","Held for this run; not permanent equipment.")),List.of()),
        state("hall","Hall of Fame",List.of(entry("season1","Season 1 / The Rising Tide","Completed season archive / sample standings","1. Ari / 2. Rowan / 3. Sol")),List.of())
    );}
    private static Action action(String id,String label,String arg,String confirmation,String input){return new Action(id,label,arg,confirmation,input);}
    private static Entry entry(String id,String title,String... lines){return new Entry(id,title,List.of(lines),List.of());}
    private static TowerFeatureState state(String section,String title,List<Entry> entries,List<Action> actions){return new TowerFeatureState(0,section,title,entries,actions,"Screenshot sample / no server actions");}
}
