package com.cobbletowers.trial;

import com.cobbletowers.trial.TrialSchedule.Instance;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** The words for a rotating trial's parts (P46): the briefing chips and {@code /tower trial}. */
public final class TrialParts {

    private TrialParts() {}

    /** What a weekly rule does, by its id. */
    public static String ruleText(String rule) {
        return switch (rule) {
            case "no_vendor" -> "No vendor: the shop is closed all week";
            case "no_healing_rooms" -> "No Rest rooms: nothing heals between floors for free";
            case "double_risk" -> "Double risk bonus on the final payout";
            case "relics_every_second_floor" -> "A relic room (Shrine, Forge or Library) on every second floor";
            default -> rule;
        };
    }

    /** The weekly rule a trial instance carries, if any. */
    public static Optional<String> weeklyRule(Instance trial) {
        return trial.roll().flatMap(RotatingRoll::weeklyRule);
    }

    /** One line per part beyond what the briefing already lists (mode, level, mutators); empty for a hand-written entry. */
    public static List<String> lines(Instance trial, Function<ResourceLocation, String> nameOf) {
        List<String> lines = new ArrayList<>();
        trial.roll().ifPresent(roll -> {
            List<String> route = roll.segmentRegions().stream().map(RotatingRoll::regionName).toList();
            lines.add("Route: " + String.join(" > ", route));
            roll.startingRelic().ifPresent(relic -> lines.add("Starting relic: " + nameOf.apply(relic)));
            roll.roomBias().ifPresent(room -> lines.add("Rooms lean " + room.substring(0, 1).toUpperCase(Locale.ROOT) + room.substring(1)));
            roll.weeklyRule().ifPresent(rule -> lines.add("Weekly rule: " + ruleText(rule)));
        });
        return lines;
    }
}
