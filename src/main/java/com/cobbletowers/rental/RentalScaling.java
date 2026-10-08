package com.cobbletowers.rental;

import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.battle.cobblemon.RentalStorage;
import com.cobbletowers.economy.AscensionLibGrants;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * A rental draft's team grows with the run (P40). Every rental starts at level 50 and would otherwise stay there while the
 * floors climb; every {@value #FLOORS_PER_STEP} floors each rental gains {@value #LEVELS_PER_STEP} levels, up to 100. A
 * level step is also an AscensionLib milestone, so each rental earns one upgrade credit that is spent on a random affix
 * slot at once ({@link AscensionLibGrants#upgradeRentals}). Enemy levels follow the party's mean, so the climb is felt as
 * stronger affixes and stats on both sides, not as a free lead. Pure rule plus one hook.
 */
public final class RentalScaling {

    public static final int BASE_LEVEL = 50;
    public static final int FLOORS_PER_STEP = 3;
    public static final int LEVELS_PER_STEP = 10;
    public static final int MAX_LEVEL = 100;

    private RentalScaling() {}

    /** The level a rental has on this floor (the run's floor, counting on through an ascending tower's cycles). */
    public static int levelFor(int floorIndex) {
        int steps = Math.max(0, floorIndex - 1) / FLOORS_PER_STEP;
        return Math.min(MAX_LEVEL, BASE_LEVEL + steps * LEVELS_PER_STEP);
    }

    /** A floor has just been confirmed: lift every participant's rentals to that floor's level and upgrade them. */
    public static void onFloorConfirmed(MinecraftServer server, UUID runId) {
        PersistedRun run = TowerRuns.get(runId).orElse(null);
        if (run == null) return;
        for (PersistedParticipant participant : run.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.playerId());
            if (player != null) raise(player, run.floorIndex());
        }
    }

    /**
     * Lifts the player's rentals to the level of {@code floorIndex} and spends the upgrades that earns. Also what the operator
     * probe calls.
     * @return how many rentals rose
     */
    public static int raise(ServerPlayer player, int floorIndex) {
        int target = levelFor(floorIndex);
        List<Pokemon> raised = new ArrayList<>();
        for (Pokemon rental : RentalStorage.held(player)) {
            if (rental.getLevel() < target) {
                rental.setLevel(target);
                raised.add(rental);
            }
        }
        if (raised.isEmpty()) return 0;
        AscensionLibGrants.upgradeRentals(raised);
        TowerLog.info("Rentals of {} rose to level {} on floor {}", player.getGameProfile().getName(), target, floorIndex);
        player.sendSystemMessage(Component.literal("Your rented team grew to level " + target + " and gained an upgrade each."));
        return raised.size();
    }
}
