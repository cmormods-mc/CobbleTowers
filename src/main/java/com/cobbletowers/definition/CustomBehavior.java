package com.cobbletowers.definition;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The coded behaviors a CUSTOM modifier can name (P29).
 *
 * <p>Content picks one by id from {@code effect.custom}; what each does lives in {@code CustomEffects}, which turns a
 * run's held behaviors into typed parameters the engine already knows how to consume. Declared here, in the
 * definition package, so a file naming an unknown behavior is refused at load rather than drafting and doing nothing.
 */
public enum CustomBehavior {

    /** The party starts every battle hitting harder and hurting more: +2 Attack and Sp. Atk, but at 60% HP. */
    GLASS_CANNON("glass_cannon"),

    /** The whole party is fully healed on arrival at every intermission. */
    FIELD_HOSPITAL("field_hospital"),

    /** Every reward rolls a gamble: a rare jackpot, otherwise a poor payout. */
    FORTUNES_WHEEL("fortunes_wheel"),

    /** Vendor prices are halved for the rest of the run. */
    BLACK_MARKET("black_market"),

    /** The party starts every battle with +1 Speed (a relic, P34). */
    SWIFT_START("swift_start"),

    /** The party starts every battle with +1 Defense and +1 Sp. Def (a relic, P34). */
    IRON_HIDE("iron_hide"),

    /** The party starts every battle with +1 Attack and +1 Sp. Atk (a relic, P34). */
    WAR_BANNER("war_banner");

    private final String id;

    CustomBehavior(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static Optional<CustomBehavior> fromId(String id) {
        return Arrays.stream(values()).filter(behavior -> behavior.id.equals(id)).findFirst();
    }

    public static List<String> ids() {
        return Arrays.stream(values()).map(CustomBehavior::id).toList();
    }
}
