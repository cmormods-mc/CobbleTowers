package com.cobbletowers.runtime;

import com.cobbletowers.definition.RulesetDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a ruleset asks of a party before a run may take an instance (TDS #41, #46), and which Pokemon
 * of a party a run reads its levels from afterwards (TDS #45).
 *
 * <p>Pure: Cobblemon is read by the caller into {@link PartyMember}s, the same split
 * {@link RunFactory} keeps. Duplicate species and held items are legal by default (TDS #43, #44), so
 * nothing here looks at either.
 */
public final class PartyValidation {

    private PartyValidation() {}

    /**
     * One Pokemon as validation sees it: identity, level, and whether it can fight right now, plus what a playlist's
     * clauses ask about (P32): species name, types, whether it can evolve further, and Cobblemon's labels (legendary...).
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
     *
     * @param registered the Pokemon this player registers: the first {@code registeredPartySize} of
     *                   their party, in party order, whether or not the party was valid
     * @param problems   why the party is not acceptable; empty when it is
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
     * The levels a floor is drawn against for one player: those of their registered Pokemon that are
     * still in {@code live}. A registered Pokemon that has since been released or boxed is skipped
     * rather than failing the floor, and if none of them can be found the whole live party is used --
     * "locked" must never become a new way to stall a run.
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
