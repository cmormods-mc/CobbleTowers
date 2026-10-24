package com.cobbletowers.rental;

import com.cobbletowers.definition.RentalSetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntBiFunction;

/**
 * The IVs and EVs one rental card carries (hp, atk, def, spa, spd, spe), rolled when the draft is drawn so the card
 * shows exactly what the lent Pokemon gets. EVs: the set's main stat and one other, random, at 252. IVs: 15 to 31.
 */
public record RentalSpread(List<Integer> ivs, List<Integer> evs) {

    public static final int IV_FLOOR = 15;
    public static final int MAX_IV = 31;
    private static final int SPEED = 5;

    public RentalSpread {
        ivs = List.copyOf(ivs);
        evs = List.copyOf(evs);
    }

    /** What the sets file says, unrolled. */
    public static RentalSpread of(RentalSetDefinition set) {
        return new RentalSpread(set.ivs(), set.evs());
    }

    /**
     * @param roll a seeded roll: a context name and an exclusive bound, giving 0 up to the bound
     */
    public static RentalSpread roll(RentalSetDefinition set, ToIntBiFunction<String, Integer> roll) {
        int main = 0;
        for (int i = 1; i < 6; i++) if (set.evs().get(i) > set.evs().get(main)) main = i;
        if (set.evs().get(main) == 0) main = roll.applyAsInt("main", 6);
        int other = roll.applyAsInt("other", 5);
        if (other >= main) other++;

        List<Integer> evs = new ArrayList<>();
        for (int i = 0; i < 6; i++) evs.add(i == main || i == other ? RentalSetDefinition.MAX_EVS_PER_STAT : 0);

        // A set that invests in Speed keeps a perfect Speed IV; a low one would lose speed ties it was built to win.
        List<Integer> ivs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            boolean fast = i == SPEED && set.evs().get(SPEED) > 0;
            ivs.add(fast ? MAX_IV : IV_FLOOR + roll.applyAsInt("iv" + i, MAX_IV - IV_FLOOR + 1));
        }
        return new RentalSpread(ivs, evs);
    }
}
