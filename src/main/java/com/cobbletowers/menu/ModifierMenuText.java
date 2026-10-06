package com.cobbletowers.menu;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Static codex rules only; never turns risk into an invented loot percentage. */
public final class ModifierMenuText {
    private ModifierMenuText() {}
    public static List<String> lines(ModifierDefinition m) {
        List<String> lines=new ArrayList<>();var e=m.effect();
        lines.add("Risk: "+m.risk().name().toLowerCase(Locale.ROOT)+" / Stack limit: "+m.stackLimit());
        if(e.levelOffset()!=0)lines.add("Enemy level offset: "+e.levelOffset());
        if(e.bossLevelOffset()!=0)lines.add("Boss level offset: "+e.bossLevelOffset());
        if(e.extraOpponents()!=0)lines.add("Additional opponents: "+e.extraOpponents());
        if(e.bossHealthPercent()!=100)lines.add("Boss health: "+e.bossHealthPercent()+"% of baseline");
        lines.add(e.rewardPercent()==100?"No direct reward amount multiplier.":String.format(Locale.ROOT,"Eligible reward amounts: x%.2f",e.rewardPercent()/100d));
        if(!e.allowSwitching())lines.add("Switching is disabled.");
        if(!e.allowItems())lines.add("Items are disabled in battle.");
        if(!e.bannedMoves().isEmpty())lines.add("Banned moves: "+String.join(", ",e.bannedMoves()));
        e.weather().ifPresent(v->lines.add("Battle weather: "+v));e.terrain().ifPresent(v->lines.add("Battle terrain: "+v));
        if(e.scoutingBonus()!=0)lines.add("Scouting threshold bonus: "+e.scoutingBonus()+" floors");
        e.custom().ifPresent(v->lines.add(switch(v) {
            case "glass_cannon" -> "Start battles at 60% HP with +2 Attack and Sp. Atk.";
            case "field_hospital" -> "The party heals on arrival at each intermission.";
            case "fortunes_wheel" -> "Reward gamble: 30% chance of x4, otherwise x0.5.";
            case "black_market" -> "Vendor prices are halved for the rest of the run.";
            case "swift_start" -> "Start battles with +1 Speed.";
            case "iron_hide" -> "Start battles with +1 Defense and Sp. Def.";
            case "war_banner" -> "Start battles with +1 Attack and Sp. Atk.";
            default -> "Custom behavior: "+v;
        }));
        if(!m.requires().isEmpty())lines.add("Requires: "+String.join(", ",m.requires().stream().map(Object::toString).toList()));
        if(!m.excludes().isEmpty())lines.add("Conflicts: "+String.join(", ",m.excludes().stream().map(Object::toString).toList()));
        if(!m.tags().isEmpty())lines.add("Tags: "+String.join(", ",m.tags()));
        lines.add("Risk is separate from reward amount. Conditional offers are not guaranteed.");
        if(m.relic())lines.add("Held for this run; not permanent equipment.");
        return lines;
    }
}
