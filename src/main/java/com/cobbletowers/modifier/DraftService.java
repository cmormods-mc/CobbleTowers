package com.cobbletowers.modifier;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinition;
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
 * Opens, takes votes on and settles a draft (TDS #2, #23, #57). Decisions are pure functions over a run; only a few
 * methods write.
 */
public final class DraftService {

    /** TDS #57: every fifth accumulated challenge produces a lock-in. */
    public static final int LOCK_IN_EVERY = 5;

    private DraftService() {}

    // ---------------------------------------------------------------- pure

    /** Whether the next draft is a Lock-In Draft: one per five challenges, derived rather than stored. */
    public static boolean lockInDue(RunModifierState state) {
        return state.challengeCount() / LOCK_IN_EVERY > state.lockedIn().size();
    }

    /** The seed a draft is drawn from and tie-broken with; the same seed so voting order cannot steer the tie. */
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

    /** The relics a run is carrying (P34), resolved against loaded content, in the order found. */
    public static List<ModifierDefinition> relicsHeld(TowerContent content, RunModifierState state) {
        List<ModifierDefinition> held = new ArrayList<>(state.relics().size());
        for (ResourceLocation id : state.relics()) content.modifier(id).ifPresent(held::add);
        return List.copyOf(held);
    }

    /** The seed a relic draft at this floor is drawn from, and its tie is broken with. */
    public static long relicSeed(PersistedRun run, int floorIndex) {
        return EncounterSeed.of(run.seed(), floorIndex, DraftDraw.RELIC_ORDINAL_BASE);
    }

    /** The relics offered after clearing this floor: milestone floors only, with room, and legal for the run. */
    public static List<ResourceLocation> relicCardsFor(TowerContent content, PersistedRun run, int floorIndex) {
        RunModifierState state = run.modifiers();
        if (!state.hasRelicRoom() || content.milestoneAt(run.towerId(), floorIndex).isEmpty()) return List.of();
        List<ModifierDefinition> pool = ModifierResolver.eligibleFrom(content.relicPool(),
                relicsHeld(content, state));
        List<ResourceLocation> cards = new ArrayList<>();
        for (ModifierDefinition card : DraftDraw.drawRelics(pool, run.seed(), floorIndex)) cards.add(card.id());
        return List.copyOf(cards);
    }

    /** What a run's modifiers add up to, with locked-in ones counted twice (that is what a lock-in does). */
    public static ModifierEffects effects(TowerContent content, RunModifierState state) {
        List<ModifierDefinition> counted = new ArrayList<>(held(content, state));
        for (ResourceLocation locked : state.lockedIn()) {
            content.modifier(locked).ifPresent(counted::add);
        }
        counted.addAll(relicsHeld(content, state));
        return ModifierEffects.of(counted);
    }

    /**
     * What a run's CUSTOM modifiers (P29) reduce to; locked-in copies do not matter, a behavior is held or it is not.
     */
    public static CustomEffects customs(PersistedRun run) {
        TowerContent content = TowerDefinitionRegistry.content();
        List<ModifierDefinition> all = new ArrayList<>(held(content, run.modifiers()));
        all.addAll(relicsHeld(content, run.modifiers()));
        return CustomEffects.of(all);
    }

    /** A run's effects, read from whatever content is loaded now. */
    public static ModifierEffects effects(PersistedRun run) {
        return effects(TowerDefinitionRegistry.content(), run.modifiers()).withAscension(ascensionOf(run));
    }

    /** Which Ascension a run's current floor is in (0 for the base cycle, and for any tower that does not ascend). */
    public static int ascensionOf(PersistedRun run) {
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        return tower == null ? 0 : tower.ascensionOf(run.floorIndex());
    }

    /**
     * The cards offered at this floor, or empty. A lock-in draws from held modifiers (#57); an ordinary draft from
     * the floor's pool (#58).
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
     * Opens a draft if the run should have one. Called on arrival at INTERMISSION; idempotent, so an open draft keeps
     * its votes.
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
            // Nothing to draft, but a milestone may still have a relic to give.
            List<ResourceLocation> relics = relicCardsFor(content, run, run.floorIndex());
            if (!relics.isEmpty()) {
                PersistedDraft relicDraft = PersistedDraft.openingRelics(run.floorIndex(), relics);
                TowerRuns.save(server, run.withModifiers(run.modifiers().withDraft(relicDraft), now), true);
                TowerLog.info("Run {} opened a RELIC draft at floor {}: {}", runId, run.floorIndex(), relics);
                return Optional.of(relicDraft);
            }
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

    /** Records a vote and settles once everybody has voted. @return the draft, or empty when none is open */
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
     * Settles the open draft whether or not everybody voted; an unvoted draft still resolves (see {@link DraftVote}).
     */
    public static PersistedDraft settle(MinecraftServer server, UUID runId, long now) {
        PersistedRun run = TowerRuns.get(runId).orElseThrow(
                () -> new IllegalStateException("no run " + runId));
        PersistedDraft draft = run.modifiers().draft()
                .orElseThrow(() -> new IllegalStateException("run " + runId + " has no draft to settle"));
        if (draft.resolved()) return draft;

        DraftVote.Result result = DraftVote.resolve(draft.votes(), draft.cards().size(),
                draft.event() ? com.cobbletowers.intermission.IntermissionEvents.seedOf(run, draft.floorIndex())
                        : draft.relic() ? relicSeed(run, draft.floorIndex()) : draftSeed(run, draft.floorIndex()));
        PersistedDraft resolved = draft.resolvedAs(result.cardIndex(), result.byTieBreak());
        ResourceLocation won = resolved.cards().get(result.cardIndex());
        String kind = draft.event() ? "event" : draft.relic() ? "relic" : "draft";
        for (ResourceLocation card : resolved.cards()) com.cobbletowers.mastery.TuningCounters.bump(server, kind + "_offered." + card);
        com.cobbletowers.mastery.TuningCounters.bump(server, kind + "_taken." + won);

        if (draft.event()) {
            return settleEvent(server, run, runId, resolved, won, result.byTieBreak(), now);
        }

        RunModifierState next = draft.relic()
                ? run.modifiers().withRelic(won, draft.floorIndex() + 1).withDraft(resolved)
                : draft.lockIn()
                        ? run.modifiers().lockingIn(won).withDraft(resolved)
                        : run.modifiers().accumulating(won, draft.floorIndex() + 1).withDraft(resolved);

        if (draft.relic()) {
            PersistedRun afterRelic = run.withModifiers(next, now);
            TowerLog.info("Run {} found relic {} at floor {}{}", runId, won, draft.floorIndex(),
                    result.byTieBreak() ? " on a seed tie-break" : "");
            // A regional milestone may also offer an Echo Duel, once the relic is chosen.
            Optional<com.cobbletowers.intermission.IntermissionEvents.Room> duel =
                    com.cobbletowers.intermission.IntermissionEvents.roomFor(TowerDefinitionRegistry.content(), afterRelic,
                            draft.floorIndex(), com.cobbletowers.echo.EchoService.duelAvailable(server, afterRelic, draft.floorIndex()));
            if (duel.isPresent()) {
                afterRelic = afterRelic.withModifiers(next.withDraft(PersistedDraft.openingEvent(draft.floorIndex(),
                        com.cobbletowers.intermission.IntermissionEvents.cardsOf(duel.get()))), now);
                TowerLog.info("Run {} opened an EVENT room ({}) at floor {}", runId, duel.get(), draft.floorIndex());
            }
            TowerRuns.save(server, afterRelic, true);
            return resolved;
        }

        // A milestone boss also pays a relic: once the challenge is settled, put the relics on the table.
        // The run cannot leave the intermission while a draft is open, so the relic choice cannot be skipped.
        TowerContent content = TowerDefinitionRegistry.content();
        PersistedRun after = run.withModifiers(next, now);
        List<ResourceLocation> relics = relicCardsFor(content, after, draft.floorIndex());
        if (!relics.isEmpty()) {
            after = after.withModifiers(next.withDraft(PersistedDraft.openingRelics(draft.floorIndex(), relics)), now);
            TowerLog.info("Run {} opened a RELIC draft at floor {}: {}", runId, draft.floorIndex(), relics);
        } else {
            // No relic to give: a non-milestone floor may have an event room instead.
            Optional<com.cobbletowers.intermission.IntermissionEvents.Room> room =
                    com.cobbletowers.intermission.IntermissionEvents.roomFor(content, after, draft.floorIndex(),
                            com.cobbletowers.echo.EchoService.duelAvailable(server, after, draft.floorIndex()));
            if (room.isPresent()) {
                after = after.withModifiers(next.withDraft(PersistedDraft.openingEvent(draft.floorIndex(),
                        com.cobbletowers.intermission.IntermissionEvents.cardsOf(room.get()))), now);
                TowerLog.info("Run {} opened an EVENT room ({}) at floor {}", runId, room.get(), draft.floorIndex());
            }
        }
        TowerRuns.save(server, after, true);
        TowerLog.info("Run {} drafted {}{} at floor {}{}", runId, won, draft.lockIn() ? " (LOCKED IN)" : "",
                draft.floorIndex(), result.byTieBreak() ? " on a seed tie-break" : "");
        TowerDefinitionRegistry.content().modifier(won).ifPresent(modifier ->
                com.cobbletowers.events.TowerEvents.emit(new com.cobbletowers.events.TowerEvent.Drafted(runId,
                        run.participants().stream().map(PersistedParticipant::playerId).toList(), won, modifier.risk())));
        return resolved;
    }

    /** Applies the option an event room's vote chose (P34b). */
    private static PersistedDraft settleEvent(MinecraftServer server, PersistedRun run, UUID runId,
                                              PersistedDraft resolved, ResourceLocation won, boolean tie, long now) {
        var outcome = com.cobbletowers.intermission.IntermissionEvents.Option.fromCard(won).map(option ->
                com.cobbletowers.intermission.IntermissionEvents.resolve(TowerDefinitionRegistry.content(), run,
                        resolved.floorIndex(), option));
        RunModifierState base = outcome.map(com.cobbletowers.intermission.IntermissionEvents.Outcome::state)
                .orElse(run.modifiers());
        TowerRuns.save(server, run.withModifiers(base.withDraft(resolved), now), true);
        TowerLog.info("Run {} event choice {} at floor {}{}: {}", runId, won, resolved.floorIndex(),
                tie ? " on a seed tie-break" : "", outcome.map(com.cobbletowers.intermission.IntermissionEvents.Outcome::message).orElse("unknown option"));
        if (outcome.isPresent() && outcome.get().duel()) {
            com.cobbletowers.echo.EchoService.beginDuel(server, run.withModifiers(base.withDraft(resolved), now),
                    resolved.floorIndex());
            return resolved;
        }
        outcome.ifPresent(o -> {
            for (PersistedParticipant participant : run.participants()) {
                net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
                if (player == null) continue;
                if (o.healParty()) com.cobbletowers.economy.VendorServices.apply(
                        com.cobbletowers.definition.VendorEffect.FULL_HEAL, player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(o.message()));
            }
        });
        return resolved;
    }

    /** Clears a settled draft as the run leaves its intermission; an open one stays and blocks it. */
    public static void clearIfSettled(MinecraftServer server, UUID runId, long now) {
        TowerRuns.get(runId).ifPresent(run -> {
            if (run.modifiers().draft().isPresent() && run.modifiers().draft().get().resolved()) {
                TowerRuns.save(server, run.withModifiers(run.modifiers().withoutDraft(), now), false);
            }
        });
    }
}
