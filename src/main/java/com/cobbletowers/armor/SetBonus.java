package com.cobbletowers.armor;

import com.google.gson.JsonArray;
import net.minecraft.resources.ResourceLocation;

/**
 * One thing an armor set does once enough of its pieces are worn (P24). A closed set of kinds, each a plain
 * record: nothing here knows how a bonus is applied, which is what keeps resolving them a pure function.
 */
public sealed interface SetBonus {

    /** How many worn pieces of the set switch this bonus on. */
    int pieces();

    /**
     * A vanilla attribute modifier on the wearer. {@code operation} is {@code add_value},
     * {@code add_multiplied_base} or {@code add_multiplied_total}.
     */
    record PlayerAttribute(int pieces, ResourceLocation attribute, String operation, double amount) implements SetBonus {}

    /** A Cobblemon number scaled for the wearer. */
    record CobblemonModifier(int pieces, Kind kind, int percent) implements SetBonus {
        public enum Kind {
            XP_PERCENT("xp_percent"),
            CATCH_RATE_PERCENT("catch_rate_percent"),
            SHINY_PERCENT("shiny_percent");

            private final String id;

            Kind(String id) {
                this.id = id;
            }

            public String id() {
                return id;
            }

            public static Kind of(String id) {
                for (Kind kind : values()) if (kind.id.equals(id)) return kind;
                throw new IllegalArgumentException("unknown cobblemon modifier '" + id + "'");
            }
        }
    }

    /** P23 battle operations, with logical sides, merged into the wearer's tower battles. Already validated. */
    record BattleEffects(int pieces, JsonArray effects) implements SetBonus {}

    /** A change to a tower mechanic for the wearer. */
    record TowerModifier(int pieces, Kind kind, int percent) implements SetBonus {
        public enum Kind {
            VENDOR_DISCOUNT_PERCENT("vendor_discount_percent", 50),
            RAID_POINTS_PERCENT("raid_points_percent", 100);

            private final String id;
            private final int max;

            Kind(String id, int max) {
                this.id = id;
                this.max = max;
            }

            public String id() {
                return id;
            }

            /** The largest percent a single bonus (and the sum of all of them) may reach. */
            public int max() {
                return max;
            }

            public static Kind of(String id) {
                for (Kind kind : values()) if (kind.id.equals(id)) return kind;
                throw new IllegalArgumentException("unknown tower modifier '" + id + "'");
            }
        }
    }
}
