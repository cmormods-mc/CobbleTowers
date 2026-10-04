package com.cobbletowers.runtime;

import com.cobbletowers.definition.PlaylistDefinition;
import com.cobbletowers.definition.PlaylistDefinition.Clauses;
import com.cobbletowers.runtime.PartyValidation.PartyMember;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A playlist's party clauses, applied (P32). Pure: a registered party and the clauses in, readable problems out, in the same
 * wording the other party rules use. A clause about something a member does not say (no species recorded) is skipped rather
 * than guessed at, which only happens in tests and for a Pokemon Cobblemon could not describe.
 */
public final class PlaylistRules {

    private PlaylistRules() {}

    /** Why {@code registered} breaks the playlist's clauses; empty when it keeps them. */
    public static List<String> problems(List<PartyMember> registered, PlaylistDefinition playlist) {
        return problems(registered, playlist.party());
    }

    public static List<String> problems(List<PartyMember> registered, Clauses clauses) {
        List<String> problems = new ArrayList<>();
        if (registered.isEmpty()) return problems;

        if (clauses.sameType()) sameType(registered, problems);
        for (PartyMember member : registered) {
            String name = member.species().isEmpty() ? "a Pokemon" : member.species();
            if (clauses.maxLevel() > 0 && member.level() > clauses.maxLevel()) {
                problems.add(name + " is level " + member.level() + " (the limit is " + clauses.maxLevel() + ")");
            }
            if (clauses.noFullyEvolved() && !member.species().isEmpty() && member.fullyEvolved()) {
                problems.add(name + " is fully evolved");
            }
            for (String label : clauses.bannedLabels()) {
                if (member.labels().contains(label)) {
                    problems.add(name + " is a " + label.replace('_', ' ') + " Pokemon");
                }
            }
        }
        return List.copyOf(problems);
    }

    /** Monotype: find the type most of the party shares and name everyone who lacks it. */
    private static void sameType(List<PartyMember> party, List<String> problems) {
        if (party.stream().anyMatch(member -> member.types().isEmpty())) return;   // not described: nothing to judge
        Map<String, Integer> counts = new HashMap<>();
        for (PartyMember member : party) {
            for (String type : member.types()) counts.merge(type.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        String best = counts.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(Map.Entry::getKey).orElse("");
        if (counts.getOrDefault(best, 0) == party.size()) return;
        String pretty = best.isEmpty() ? "" : Character.toUpperCase(best.charAt(0)) + best.substring(1);
        for (PartyMember member : party) {
            if (member.types().stream().noneMatch(type -> type.equalsIgnoreCase(best))) {
                problems.add(member.species() + " is not a " + pretty + " type");
            }
        }
    }
}
