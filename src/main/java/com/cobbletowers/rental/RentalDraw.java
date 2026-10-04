package com.cobbletowers.rental;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.definition.RentalSetDefinition.Rarity;
import com.cobbletowers.trial.TrialSeed;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The three packs of a rental draft (P33), a pure function of the pool and a seed: the same seed always offers the same packs,
 * which is what lets a Daily Trial give everyone the same draft.
 *
 * <p>The shape is the one CobblemonCards' own packs have: <b>three common, one uncommon and one rare-or-better</b> card, shuffled.
 * On top of that, <b>at least one epic-or-better card appears across the three packs</b> (a card is upgraded if chance gave none),
 * and a small chance turns one pack into a <b>God Pack</b> of five epic-or-better cards, which a trial never rolls. No species is
 * offered twice in one draft.
 */
public final class RentalDraw {

    public static final int PACKS = 3;
    public static final int CARDS = 5;
    /** The chance of a God Pack in a draft, in thousandths. */
    public static final int GOD_PACK_PER_MILLE = 30;
    /** The top slot of a normal pack: this share of 100 is rare, then epic, and the rest legendary or mythic. */
    static final int RARE_PERCENT = 70;
    static final int EPIC_PERCENT = 22;

    private RentalDraw() {}

    /** One pack: five cards, and whether it is the God Pack. */
    public record Pack(List<RentalSetDefinition> cards, boolean god) {
        public Pack {
            cards = List.copyOf(cards);
        }
    }

    /** The whole offer: three packs. */
    public record Offer(List<Pack> packs) {
        public Offer {
            packs = List.copyOf(packs);
        }

        public boolean hasEpicOrBetter() {
            return packs.stream().flatMap(pack -> pack.cards().stream()).anyMatch(card -> card.rarity().atLeast(Rarity.EPIC));
        }

        public boolean hasGodPack() {
            return packs.stream().anyMatch(Pack::god);
        }
    }

    /**
     * @param pool         every set that may be offered
     * @param allowGodPack false for a trial, so every player's draft has the same shape
     */
    public static Offer draw(List<RentalSetDefinition> pool, long seed, boolean allowGodPack) {
        Map<Rarity, List<RentalSetDefinition>> byRarity = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) byRarity.put(rarity, new ArrayList<>());
        for (RentalSetDefinition set : pool) byRarity.get(set.rarity()).add(set);
        for (List<RentalSetDefinition> list : byRarity.values()) list.sort(Comparator.comparing(set -> set.id().toString()));
        Draw draw = new Draw(byRarity, seed);

        int god = -1;
        if (allowGodPack && draw.roll("god", 1000) < GOD_PACK_PER_MILLE) god = draw.roll("godpack", PACKS);

        List<List<RentalSetDefinition>> packs = new ArrayList<>();
        for (int pack = 0; pack < PACKS; pack++) {
            List<RentalSetDefinition> cards = new ArrayList<>();
            if (pack == god) {
                for (int i = 0; i < CARDS; i++) cards.add(draw.take("g" + pack + "." + i, godChain(draw, "g" + pack + "." + i, i)));
            } else {
                for (int i = 0; i < 3; i++) cards.add(draw.take("c" + pack + "." + i, List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE)));
                cards.add(draw.take("u" + pack, List.of(Rarity.UNCOMMON, Rarity.COMMON, Rarity.RARE)));
                cards.add(draw.take("t" + pack, topChain(draw, "t" + pack)));
            }
            packs.add(cards);
        }

        // The guarantee: somewhere in the three packs there is an epic or better.
        boolean any = packs.stream().flatMap(List::stream).anyMatch(card -> card.rarity().atLeast(Rarity.EPIC));
        if (!any) {
            int pack = draw.roll("upgrade", PACKS);
            RentalSetDefinition upgrade = draw.take("upgrade", epicChain(draw, "upgrade"));
            if (upgrade != null) packs.get(pack).set(CARDS - 1, upgrade);
        }

        List<Pack> out = new ArrayList<>();
        for (int pack = 0; pack < PACKS; pack++) {
            List<RentalSetDefinition> cards = new ArrayList<>(packs.get(pack));
            draw.shuffle(cards, "s" + pack);
            out.add(new Pack(cards, pack == god));
        }
        return new Offer(out);
    }

    /** The preference order for the top slot of a normal pack: usually rare, sometimes epic, rarely legendary. */
    private static List<Rarity> topChain(Draw draw, String context) {
        int roll = draw.roll(context + ".r", 100);
        if (roll < RARE_PERCENT) return List.of(Rarity.RARE, Rarity.EPIC, Rarity.UNCOMMON);
        if (roll < RARE_PERCENT + EPIC_PERCENT) return List.of(Rarity.EPIC, Rarity.RARE, Rarity.LEGENDARY);
        return draw.roll(context + ".m", 4) == 0
                ? List.of(Rarity.MYTHIC, Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE)
                : List.of(Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE);
    }

    private static List<Rarity> epicChain(Draw draw, String context) {
        return draw.roll(context + ".r", 5) == 0 ? List.of(Rarity.LEGENDARY, Rarity.EPIC, Rarity.RARE) : List.of(Rarity.EPIC, Rarity.LEGENDARY, Rarity.RARE);
    }

    /** A God Pack card: epic or better, and at most two of the five legendary or mythic (so a team can still keep two that are not). */
    private static List<Rarity> godChain(Draw draw, String context, int index) {
        boolean top = index < 2 && draw.roll(context + ".t", 100) < 40;
        return top ? List.of(Rarity.LEGENDARY, Rarity.MYTHIC, Rarity.EPIC, Rarity.RARE) : List.of(Rarity.EPIC, Rarity.RARE, Rarity.UNCOMMON);
    }

    /** The seeded picking, with the species already offered remembered. */
    private static final class Draw {
        private final Map<Rarity, List<RentalSetDefinition>> byRarity;
        private final long seed;
        private final Set<String> used = new HashSet<>();

        Draw(Map<Rarity, List<RentalSetDefinition>> byRarity, long seed) {
            this.byRarity = byRarity;
            this.seed = seed;
        }

        int roll(String context, int bound) {
            return (int) Math.floorMod(TrialSeed.of(seed + "|" + context), (long) bound);
        }

        /** The first rarity in the chain that still has an unused species gives one, chosen by the seed. */
        RentalSetDefinition take(String context, List<Rarity> chain) {
            for (Rarity rarity : chain) {
                List<RentalSetDefinition> fresh = byRarity.get(rarity).stream().filter(set -> !used.contains(set.species())).toList();
                if (fresh.isEmpty()) continue;
                RentalSetDefinition chosen = fresh.get(roll(context + "." + rarity, fresh.size()));
                used.add(chosen.species());
                return chosen;
            }
            // A pool too small to avoid repeats: take anything left rather than fail a draft.
            for (Rarity rarity : Rarity.values()) {
                List<RentalSetDefinition> any = byRarity.get(rarity);
                if (!any.isEmpty()) return any.get(roll(context + ".fallback", any.size()));
            }
            throw new IllegalArgumentException("the rental pool is empty; there is nothing to draft from");
        }

        void shuffle(List<RentalSetDefinition> cards, String context) {
            for (int i = cards.size() - 1; i > 0; i--) {
                int j = roll(context + "." + i, i + 1);
                RentalSetDefinition swap = cards.get(i);
                cards.set(i, cards.get(j));
                cards.set(j, swap);
            }
        }
    }
}
