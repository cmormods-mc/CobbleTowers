package com.cobbletowers.modifier;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.encounter.EncounterSeed;
import com.cobbletowers.persistence.PersistedDraft;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.RunModifierState;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The one place a draft opens, takes a vote and settles (TDS #2, #23, #57).
 *
 * <p>Split the way {@link com.cobbletowers.runtime.ParticipantService} is: the decisions are pure
 * functions over a run, and only the handful of methods taking a server write anything. Every rule
 * below is therefore a unit test with no Minecraft in reach, which is the only way the tie-break and
 * the lock-in cadence get tested at all -- neither is reachable in a smoke test without playing five
 * floors.
 */
public final class DraftService {

    /** TDS #57: every fifth accumulated challenge produces a lock-in. */
    public static final int LOCK_IN_EVERY = 5;

    private DraftService() {}

    // ---------------------------------------------------------------- pure

    /**
     * Whether the next draft this run opens should be a Lock-In Draft.
     *
     * <p>Derived rather than stored. A run owes one lock-in per five challenges, and it has had as
     * many as it has locked in; when it owes more than it has had, the next draft is the lock-in.
     * Storing a "lock-in due" flag would be a second source of truth that a crash could disagree
     * with.
     */
    public static boolean lockInDue(RunModifierState state) {
        return state.challengeCount() / LOCK_IN_EVERY > state.lockedIn().size();
    }

    /**
     * The seed a draft at this floor is drawn from, and the one its tie is broken with.
     *
     * <p>The same seed for both on purpose: the tie-break is then fixed at the moment the cards are,
     * before anybody has voted, so it cannot be steered by voting order.
     */
    public static long draftSeed(PersistedRun run, int floorIndex) {
        return EncounterSeed.of(run.seed(), floorIndex, DraftDraw.DRAFT_ORDINAL_BASE);
    }

    /** The modifiers a run is carrying, resolved against loaded content, in draft order. */
    public static List<ModifierDefinition> held(TowerContent content, RunModifierState state) {
        List<ModifierDefinition> held = new ArrayList<>(state.accumulated().size());
        for (ResourceLocation id : state.accumulated()) {
            content.modifier(id).ifPresent(held::add);
        }
        return List.copyOf(held);
    }

    /**
     * What a run's modifiers add up to, with locked-in ones counted twice.
     *
     * <p><b>This is what locking in means mechanically.</b> The TDS calls it "a permanent lock-in
     * mechanic" (#2, #57) without saying what it does, and a lock-in that only set a flag would be a
     * ceremony -- the party would vote on nothing. Counting the chosen modifier a second time makes
     * the Lock-In Draft a real decision about which challenge to intensify for the rest of the run,
     * using machinery that already exists: {@link ModifierEffects} compounds percentages and sums
     * offsets, so a second copy needs no special case anywhere.
     */
    public static ModifierEffects effects(TowerContent content, RunModifierState state) {
        List<ModifierDefinition> counted = new ArrayList<>(held(content, state));
        for (ResourceLocation locked : state.lockedIn()) {
            content.modifier(locked).ifPresent(counted::add);
        }
        return ModifierEffects.of(counted);
    }

    /** A run's effects, read from whatever content is loaded now. */
    public static ModifierEffects effects(PersistedRun run) {
        return effects(TowerDefinitionRegistry.content(), run.modifiers());
    }

    /**
     * The cards a run would be offered at this floor, or empty if it should not be offered a draft.
     *
     * <p>A lock-in draws from what the run already holds (#57); an ordinary draft draws from the
     * floor's pool, filtered to what the run is eligible for (#58).
     */
    public static List<ResourceLocation> cardsFor(TowerContent content, PersistedRun run, int floorIndex) {
        RunModifierState state = run.modifiers();
        List<ModifierDefinition> pool;
        if (lockInDue(state)) {
            // Only what is not already locked in: offering a locked modifier again would be a card
            // that changes nothing, and three of them would be a draft with no choice in it.
            pool = new ArrayList<>();
            for (ModifierDefinition candidate : held(content, state)) {
                if (!state.lockedIn().contains(candidate.id()) && !pool.contains(candidate)) pool.add(candidate);
            }
        } else {
            pool = ModifierResolver.eligibleFrom(
                    content.draftablePool(run.towerId(), floorIndex), held(content, state));
        }
        if (pool.isEmpty()) return List.of();

        List<ResourceLocation> cards = new ArrayList<>();
        for (ModifierDefinition card : DraftDraw.draw(pool, run.seed(), floorIndex)) cards.add(card.id());
        return List.copyOf(cards);
    }

    /** Who is entitled to vote: everybody still in the run who could fight (TDS #23). */
    public static List<UUID> voters(PersistedRun run) {
        List<UUID> voters = new ArrayList<>();
        for (PersistedParticipant participant : run.participants()) {
            if (participant.state().canFight()) voters.add(participant.playerId());
        }
        return List.copyOf(voters);
    }

    /** Whether everybody entitled to vote has. */
    public static boolean everybodyVoted(PersistedRun run, PersistedDraft draft) {
        List<UUID> voters = voters(run);
        if (voters.isEmpty()) return true;
        for (UUID voter : voters) {
            if (!draft.votes().containsKey(voter)) return false;
        }
        return true;
    }

    // -------------------------------------------------------------- writing

    /**
     * Opens a draft, if this run should have one.
     *
     * <p>Called on arrival at {@code INTERMISSION} rather than from the event that got there, for
     * the reason P7 gives about revives: every road into an intermission has to behave the same, or
     * "you draft between floors" is a promise that holds only on the common path.
     *
     * <p>Idempotent. A run that already has an open draft keeps it -- re-opening would discard votes
     * already cast, and recovery replays arrival at a state it was already in.
     */
    public static Optional<PersistedDraft> open(MinecraftServer server, UUID runId, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Optional.empty();
        PersistedRun run = found.get();
        if (run.modifiers().draft().isPresent() && !run.modifiers().draft().get().resolved()) {
            return run.modifiers().draft();
        }

        TowerContent content = TowerDefinitionRegistry.content();
        List<ResourceLocation> cards = cardsFor(content, run, run.floorIndex());
        if (cards.isEmpty()) {
            TowerLog.info("Run {} has no modifier to draft at floor {}", runId, run.floorIndex());
            return Optional.empty();
        }

        boolean lockIn = lockInDue(run.modifiers());
        PersistedDraft draft = PersistedDraft.opening(run.floorIndex(), lockIn, cards);
        // Checkpointed, because TDS #145 lists intermission among the forced checkpoints and a draft
        // lost to a crash would come back as three different cards.
        TowerRuns.save(server, run.withModifiers(run.modifiers().withDraft(draft), now), true);
        TowerLog.info("Run {} opened a{} draft at floor {}: {}", runId, lockIn ? " LOCK-IN" : "",
                run.floorIndex(), cards);
        return Optional.of(draft);
    }

    /**
     * Records one player's vote, and settles the draft once everybody has voted.
     *
     * @return the draft as it stands, or empty when there is nothing open to vote on
     */
    public static Optional<PersistedDraft> vote(MinecraftServer server, UUID runId, UUID playerId,
                                                int cardIndex, long now) {
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) return Optional.empty();
        PersistedRun run = found.get();
        Optional<PersistedDraft> open = run.modifiers().draft().filter(draft -> !draft.resolved());
        if (open.isEmpty()) return Optional.empty();
        if (cardIndex < 0 || cardIndex >= open.get().cards().size()) return open;

        PersistedDraft voted = open.get().withVote(playerId, cardIndex);
        TowerRuns.save(server, run.withModifiers(run.modifiers().withDraft(voted), now), false);

        if (everybodyVoted(run, voted)) return Optional.of(settle(server, runId, now));
        return Optional.of(voted);
    }

    /**
     * Settles whatever draft is open, whether or not everybody voted.
     *
     * <p>Used by the last vote, by the operator command, and by the watchdog when a party has walked
     * away from a draft it never answered. An unvoted draft still resolves -- see {@link DraftVote}.
     */
    public static PersistedDraft settle(MinecraftServer server, UUID runId, long now) {
        PersistedRun run = TowerRuns.get(runId).orElseThrow(
                () -> new IllegalStateException("no run " + runId));
        PersistedDraft draft = run.modifiers().draft()
                .orElseThrow(() -> new IllegalStateException("run " + runId + " has no draft to settle"));
        if (draft.resolved()) return draft;

        DraftVote.Result result = DraftVote.resolve(
                draft.votes(), draft.cards().size(), draftSeed(run, draft.floorIndex()));
        PersistedDraft resolved = draft.resolvedAs(result.cardIndex(), result.byTieBreak());
        ResourceLocation won = resolved.cards().get(result.cardIndex());

        RunModifierState next = draft.lockIn()
                ? run.modifiers().lockingIn(won).withDraft(resolved)
                : run.modifiers().accumulating(won).withDraft(resolved);

        TowerRuns.save(server, run.withModifiers(next, now), true);
        TowerLog.info("Run {} drafted {}{} at floor {}{}", runId, won, draft.lockIn() ? " (LOCKED IN)" : "",
                draft.floorIndex(), result.byTieBreak() ? " on a seed tie-break" : "");
        return resolved;
    }

    /**
     * Clears a settled draft away as a run leaves its intermission.
     *
     * <p>Only a settled one: an open draft is what stops {@code INTERMISSION_COMPLETE}, and clearing
     * it here would turn "you must draft" into "you must draft unless you ask twice".
     */
    public static void clearIfSettled(MinecraftServer server, UUID runId, long now) {
        TowerRuns.get(runId).ifPresent(run -> {
            if (run.modifiers().draft().isPresent() && run.modifiers().draft().get().resolved()) {
                TowerRuns.save(server, run.withModifiers(run.modifiers().withoutDraft(), now), false);
            }
        });
    }
}
