package com.cobbletowers.menu;

import com.cobbletowers.definition.ModifierDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Codex and card text, written to be read: what the modifier does to the fight, and what the risk pays at the end. */
public final class ModifierMenuText {
    private ModifierMenuText() {}
    private static String fieldName(String id){
        return switch(id.toLowerCase(Locale.ROOT)){
            case "raindance"->"Rain";case "sunnyday"->"Harsh sun";case "sandstorm"->"Sandstorm";case "hail"->"Hail";case "snowscape"->"Snowfall";
            case "electricterrain"->"Electric terrain";case "grassyterrain"->"Grassy terrain";case "mistyterrain"->"Misty terrain";case "psychicterrain"->"Psychic terrain";
            default->id;
        };
    }
    public static List<String> lines(ModifierDefinition m) {
        List<String> lines=new ArrayList<>();var e=m.effect();
        String risk=m.risk().name().toLowerCase(Locale.ROOT);
        lines.add(Character.toUpperCase(risk.charAt(0))+risk.substring(1)+" risk  *  +"+com.cobbletowers.reward.RiskReward.percentOf(m.risk())+"% to your final payout"+(m.stackLimit()>1?"  *  stacks "+m.stackLimit()+"x":""));
        if(e.levelOffset()>0)lines.add("Foes rise "+e.levelOffset()+(e.levelOffset()==1?" level":" levels")+" above the tower.");
        if(e.levelOffset()<0)lines.add("Foes fall "+(-e.levelOffset())+(e.levelOffset()==-1?" level":" levels")+" below the tower.");
        if(e.bossLevelOffset()>0)lines.add("The champion stands "+e.bossLevelOffset()+" levels taller.");
        if(e.bossLevelOffset()<0)lines.add("The champion is weakened by "+(-e.bossLevelOffset())+" levels.");
        if(e.extraOpponents()>0)lines.add(e.extraOpponents()+" more challengers storm every floor.");
        if(e.bossHealthPercent()>100)lines.add("The boss endures: "+e.bossHealthPercent()+"% health.");
        if(e.bossHealthPercent()<100)lines.add("The boss is frail: only "+e.bossHealthPercent()+"% health.");
        if(e.rewardPercent()>100)lines.add(String.format(Locale.ROOT,"Loot swells to x%.2f on every floor.",e.rewardPercent()/100d));
        if(e.rewardPercent()<100)lines.add(String.format(Locale.ROOT,"Loot shrinks to x%.2f on every floor.",e.rewardPercent()/100d));
        if(!e.allowSwitching())lines.add("No switching: your lead stands or falls.");
        if(!e.allowItems())lines.add("No items in battle: trust your team.");
        if(!e.bannedMoves().isEmpty())lines.add("Forbidden moves: "+String.join(", ",e.bannedMoves())+".");
        e.weather().ifPresent(v->lines.add("The sky turns: "+fieldName(v)+" in every battle."));
        e.terrain().ifPresent(v->lines.add("The ground shifts: "+fieldName(v)+" in every battle."));
        if(e.scoutingBonus()!=0)lines.add("Your scouts see "+e.scoutingBonus()+" floors further ahead.");
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
        lines.add("The risk you carry is paid out when the tower ends.");
        if(m.relic())lines.add("Held for this run; not permanent equipment.");
        return lines;
    }
}
