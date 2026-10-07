package com.cobbletowers.runtime;

import com.cobbletowers.definition.RulesetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a ruleset asks of a party before a run takes an instance (TDS #41, #46), and which Pokemon a run reads levels
 * from (TDS #45). Pure: the caller reads Cobblemon into {@link PartyMember}s. Duplicate species and held items are
 * legal (TDS #43, #44).
 */
public final class PartyValidation {

    private PartyValidation() {}

    /**
     * One Pokemon as validation sees it: identity, level, whether it can fight, and what a playlist's clauses ask
     * (P32): species, types, whether it can evolve, labels.
     */
    public record PartyMember(UUID id, int level, boolean fainted, String species, List<String> types,
                              boolean fullyEvolved, Set<String> labels) {
        public PartyMember {
            species = species == null ? "" : species;
            types = List.copyOf(types);
            labels = Set.copyOf(labels);
        }

        /** A member nothing is known about beyond the basics, which is how most rules and tests see one. */
        public PartyMember(UUID id, int level, boolean fainted) {
            this(id, level, fainted, "", List.of(), false, Set.of());
        }
    }

    /**
     * The outcome for one player.
     * @param registered the first {@code registeredPartySize} Pokemon in party order, valid or not
     * @param problems why the party is not acceptable; empty when it is
     */
    public record Result(List<UUID> registered, List<String> problems) {
        public Result {
            registered = List.copyOf(registered);
            problems = List.copyOf(problems);
        }

        public boolean valid() {
            return problems.isEmpty();
        }
    }

    public static Result validate(List<PartyMember> party, RulesetDefinition ruleset) {
        // A party larger than the ruleset allows is not an error: the first N register and the rest
        // simply stay home, so a six-Pokemon player is not punished by a three-slot ruleset.
        List<PartyMember> registered = party.subList(0, Math.min(party.size(), ruleset.registeredPartySize()));
        List<String> problems = new ArrayList<>();
        if (registered.isEmpty()) {
            problems.add("has no Pokemon to register");
        } else if (ruleset.requiresBattleReadyParty()) {
            long fainted = registered.stream().filter(PartyMember::fainted).count();
            if (fainted > 0) {
                problems.add(fainted + " registered Pokemon fainted; heal the party first");
            }
        }
        return new Result(registered.stream().map(PartyMember::id).toList(), problems);
    }

    /**
     * The levels a floor is drawn against: those of the registered Pokemon still in {@code live}. Released or boxed
     * ones are skipped; if none are found the whole live party is used.
     */
    public static List<Integer> levels(List<PartyMember> live, List<UUID> registered) {
        if (!registered.isEmpty()) {
            Set<UUID> wanted = Set.copyOf(registered);
            List<Integer> found = live.stream().filter(member -> wanted.contains(member.id()))
                    .map(PartyMember::level).collect(Collectors.toList());
            if (!found.isEmpty()) return found;
        }
        return live.stream().map(PartyMember::level).collect(Collectors.toList());
    }
}
