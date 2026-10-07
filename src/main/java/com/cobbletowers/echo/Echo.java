package com.cobbletowers.echo;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * A recorded winning team (P35, roadmap D1): what a top-ten regional run fielded, kept so others can meet one of its
 * Pokemon. The team is Cobblemon property strings ({@code species level=n nature=.. ability=.. moves=a,b,c
 * held_item=..}) as rental sets use.
 * @param name the leaderboard's name for the owner
 * @param runId the earning run; the Echo leaves the pool when it leaves the top ten
 * @param team up to six property strings
 * @param faced challengers who met one of its Pokemon
 * @param beat how many of those it beat
 * @param season the season earned in (P36c), 0 with seasons off
 */
public record Echo(UUID id, UUID owner, String name, ResourceLocation tower, UUID runId, List<String> team, long at, int faced, int beat,
                   int season) {

    public Echo {
        team = List.copyOf(team);
    }

    public Echo(UUID id, UUID owner, String name, ResourceLocation tower, UUID runId, List<String> team, long at, int faced) {
        this(id, owner, name, tower, runId, team, at, faced, 0, 0);
    }

    public Echo(UUID id, UUID owner, String name, ResourceLocation tower, UUID runId, List<String> team, long at, int faced, int beat) {
        this(id, owner, name, tower, runId, team, at, faced, beat, 0);
    }

    public Echo withFaced(int next) {
        return new Echo(id, owner, name, tower, runId, team, at, next, beat, season);
    }

    public Echo withBeat(int next) {
        return new Echo(id, owner, name, tower, runId, team, at, faced, next, season);
    }
}
