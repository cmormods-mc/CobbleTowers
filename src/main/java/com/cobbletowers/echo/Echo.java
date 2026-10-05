package com.cobbletowers.echo;

import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * A recorded winning team (P35, roadmap D1): what a top-ten regional run fielded, kept so other players can meet one of its
 * Pokemon as an opponent.
 *
 * <p>The team is a list of Cobblemon property strings ({@code species level=n nature=.. ability=.. moves=a,b,c held_item=..}),
 * the same text rental sets use, so building an opponent from it needs no new parser and a store holds no Cobblemon object.
 *
 * @param id       this Echo
 * @param owner    the player whose team it was
 * @param name     the name the leaderboard shows for them (an Echo is named by the board, never by hidden data)
 * @param runId    the run that earned it; the Echo leaves the pool when that run leaves the top ten
 * @param team     up to six property strings
 * @param faced    how many challengers have met one of its Pokemon in a duel
 * @param beat     how many of those it beat
 * @param season   the season it was earned in (P36c), 0 for one earned with seasons off or before they began
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
