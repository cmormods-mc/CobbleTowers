package com.cobbletowers.rental;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.rental.RentalDraw.Offer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One player's draft in progress (P33): three packs opened in order, two cards kept from each, six Pokemon in the
 * end. Pure and in memory; the client sends only pick indices, each checked here.
 */
public final class RentalDraft {

    /** How many cards are kept from each pack. */
    public static final int KEEP = 2;
    /** A team may keep at most this many legendary or mythic Pokemon (so a God Pack cannot field six). */
    public static final int MAX_TOP_RARITY = 2;

    public enum Result {
        OK, ALREADY_COMPLETE, NOT_THIS_PACK, WRONG_COUNT, DUPLICATE, OUT_OF_RANGE, TOO_MANY_LEGENDARY
    }

    private final Offer offer;
    private final List<List<Integer>> picks = new ArrayList<>();

    public RentalDraft(Offer offer) {
        this.offer = offer;
    }

    public Offer offer() {
        return offer;
    }

    /** The pack the player is choosing from now, or {@link RentalDraw#PACKS} when they are done. */
    public int currentPack() {
        return picks.size();
    }

    public boolean complete() {
        return picks.size() >= RentalDraw.PACKS;
    }

    public List<List<Integer>> picks() {
        return picks.stream().map(List::copyOf).toList();
    }

    /** Keeps {@code indices} (two distinct cards) from pack {@code pack}, which must be the current one. */
    public Result pick(int pack, List<Integer> indices) {
        if (complete()) return Result.ALREADY_COMPLETE;
        if (pack != currentPack()) return Result.NOT_THIS_PACK;
        if (indices.size() != KEEP) return Result.WRONG_COUNT;
        Set<Integer> distinct = new HashSet<>(indices);
        if (distinct.size() != indices.size()) return Result.DUPLICATE;
        List<RentalSetDefinition> cards = offer.packs().get(pack).cards();
        for (int index : indices) {
            if (index < 0 || index >= cards.size()) return Result.OUT_OF_RANGE;
        }
        long tops = team().stream().filter(set -> set.rarity().isTop()).count()
                + indices.stream().filter(index -> cards.get(index).rarity().isTop()).count();
        if (tops > MAX_TOP_RARITY) return Result.TOO_MANY_LEGENDARY;
        picks.add(List.copyOf(indices));
        return Result.OK;
    }

    /** Forgets every pick and starts the draft over from the first pack (the offer is the same). */
    public void restart() {
        picks.clear();
    }

    /** The Pokemon kept so far, in pick order. */
    public List<RentalSetDefinition> team() {
        List<RentalSetDefinition> team = new ArrayList<>();
        for (int pack = 0; pack < picks.size(); pack++) {
            for (int index : picks.get(pack)) team.add(offer.packs().get(pack).cards().get(index));
        }
        return List.copyOf(team);
    }

    /**
     * Names a finished team's Pokemon with the ids they will have, so registration can be written before they exist.
     * @param god per Pokemon, whether it came from a God Pack
     */
    public record Team(List<RentalSetDefinition> sets, List<UUID> ids, List<Boolean> god) {
        public Team {
            sets = List.copyOf(sets);
            ids = List.copyOf(ids);
            god = List.copyOf(god);
        }
    }

    /** For each kept Pokemon, in pick order, whether its pack was the God Pack. */
    public List<Boolean> godFlags() {
        List<Boolean> flags = new ArrayList<>();
        for (int pack = 0; pack < picks.size(); pack++) {
            for (int ignored : picks.get(pack)) flags.add(offer.packs().get(pack).god());
        }
        return List.copyOf(flags);
    }

    /** The finished team with a new id for each Pokemon, or null while the draft is not complete. */
    public Team finish(Supplier<UUID> newId) {
        if (!complete()) return null;
        List<RentalSetDefinition> sets = team();
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < sets.size(); i++) ids.add(newId.get());
        return new Team(sets, ids, godFlags());
    }
}
