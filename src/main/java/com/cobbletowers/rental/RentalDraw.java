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
 * The three packs of a rental draft (P33), a pure function of pool and seed so a Daily Trial gives everyone the same
 * draft. Each pack is three common, one uncommon and one rare-or-better species (by the set's own rarity, its class,
 * which is what the sets were tuned by). At least one epic-or-better appears overall, a small chance makes a God Pack
 * of five epic-or-better (never in a trial), and no species repeats.
 *
 * <p>Every card also has a tier (P53): a rarity rolled apart from its species, with the same shape (three common, one
 * uncommon and one rare-or-better per pack, an epic-or-better tier somewhere, epic-or-better throughout a God Pack),
 * then dealt onto the cards at random. The tier is what the card shows, what the team cap counts, and what the lent
 * Pokemon's AscensionLib profile is made at.
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

    /** One pack: five cards, the tier dealt to each (same order), and whether it is the God Pack. */
    public record Pack(List<RentalSetDefinition> cards, List<Rarity> tiers, boolean god) {
        public Pack {
            cards = List.copyOf(cards);
            tiers = List.copyOf(tiers);
            if (tiers.size() != cards.size()) throw new IllegalArgumentException("a pack needs one tier per card");
        }
    }

    /** The whole offer: three packs. */
    public record Offer(List<Pack> packs) {
        public Offer {
            packs = List.copyOf(packs);
        }

        /** Whether a species of epic class or better was offered. */
        public boolean hasEpicOrBetter() {
            return packs.stream().flatMap(pack -> pack.cards().stream()).anyMatch(card -> card.rarity().atLeast(Rarity.EPIC));
        }

        /** Whether a card of epic tier or better was offered. */
        public boolean hasEpicTierOrBetter() {
            return packs.stream().flatMap(pack -> pack.tiers().stream()).anyMatch(tier -> tier.atLeast(Rarity.EPIC));
        }

        public boolean hasGodPack() {
            return packs.stream().anyMatch(Pack::god);
        }
    }

    /**
     * @param pool every set that may be offered
     * @param allowGodPack false for a trial so every draft has the same shape
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

        List<List<Rarity>> tiers = dealTiers(draw, god);

        List<Pack> out = new ArrayList<>();
        for (int pack = 0; pack < PACKS; pack++) {
            List<RentalSetDefinition> cards = new ArrayList<>(packs.get(pack));
            draw.shuffle(cards, "s" + pack);
            List<Rarity> packTiers = new ArrayList<>(tiers.get(pack));
            draw.shuffle(packTiers, "ts" + pack);
            out.add(new Pack(cards, packTiers, pack == god));
        }
        return new Offer(out);
    }

    /**
     * The tiers of every pack, in slot order, rolled with their own contexts so they do not follow the species. The same
     * shape the species have: a normal pack is three common, one uncommon and one top slot; a God Pack is epic or better.
     */
    private static List<List<Rarity>> dealTiers(Draw draw, int god) {
        List<List<Rarity>> tiers = new ArrayList<>();
        for (int pack = 0; pack < PACKS; pack++) {
            List<Rarity> slots = new ArrayList<>();
            if (pack == god) {
                for (int i = 0; i < CARDS; i++) slots.add(godChain(draw, "Tg" + pack + "." + i, i).get(0));
            } else {
                for (int i = 0; i < 3; i++) slots.add(Rarity.COMMON);
                slots.add(Rarity.UNCOMMON);
                slots.add(topChain(draw, "Tt" + pack).get(0));
            }
            tiers.add(slots);
        }
        boolean any = tiers.stream().flatMap(List::stream).anyMatch(tier -> tier.atLeast(Rarity.EPIC));
        if (!any) tiers.get(draw.roll("Tupgrade", PACKS)).set(CARDS - 1, epicChain(draw, "Tupgrade").get(0));
        return tiers;
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

    /**
     * A God Pack card: epic or better, and at most two of the five legendary or mythic (so a team can still keep two
     * that are not).
     */
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

        <T> void shuffle(List<T> cards, String context) {
            for (int i = cards.size() - 1; i > 0; i--) {
                int j = roll(context + "." + i, i + 1);
                T swap = cards.get(i);
                cards.set(i, cards.get(j));
                cards.set(j, swap);
            }
        }
    }
}
