package com.cobbletowers.storage;

import static org.junit.jupiter.api.Assertions.*;
import com.cobbletowers.TestRuns;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.persistence.PartyJournalEntry;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.runtime.ParticipantService;
import com.cobbletowers.runtime.TowerRuns;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartyExitRecoveryTest {
    private static final UUID RUN = UUID.randomUUID();
    private static final PartyJournalEntry ENTRY = new PartyJournalEntry(TestRuns.PLAYER, RUN, List.of(), List.of(UUID.randomUUID()));

    private void index(PersistedRun... runs) throws Exception {
        var reset = TowerRuns.class.getDeclaredMethod("resetForTests", List.class);
        reset.setAccessible(true);
        reset.invoke(null, List.of(runs));
    }

    @AfterEach void clear() throws Exception { index(); }

    @Test void parkedRunMustReturnRentalsWithoutWaitingForAnotherLogin() throws Exception {
        index(TestRuns.at(RUN, RunState.RECOVERY_REQUIRED, TestRuns.NOW));
        assertTrue(PartyJournalService.runIsOverFor(ENTRY));
    }

    @Test void terminalAndMissingRunsRestore() throws Exception {
        for (var state : RunState.values()) {
            index(TestRuns.at(RUN, state, TestRuns.NOW));
            assertEquals(!state.isLive(), PartyJournalService.runIsOverFor(ENTRY), state.name());
        }
        index();
        assertTrue(PartyJournalService.runIsOverFor(ENTRY));
    }

    @Test void leavingRestoresAndActiveMembershipKeepsRentals() throws Exception {
        var run=TestRuns.at(RUN, RunState.ENCOUNTER_ACTIVE, TestRuns.NOW);
        var left=ParticipantService.changed(run, TestRuns.PLAYER, s->s.left(), TestRuns.NOW+1);
        index(left);
        assertTrue(PartyJournalService.runIsOverFor(ENTRY));
        index(run);
        assertFalse(PartyJournalService.runIsOverFor(ENTRY));
    }

    @Test void startupOutsideTowerIsNotMistakenForEscape() {
        for (var state : List.of(RunState.CREATED, RunState.VALIDATING_PARTY, RunState.ALLOCATING_INSTANCE, RunState.PREPARING)) {
            assertFalse(PartyJournalService.rentalNeedsExit(ENTRY, java.util.Optional.of(TestRuns.at(RUN,state,TestRuns.NOW)), false, true));
        }
    }

    @Test void activeRentalDisplacementAndDeathRequireExitButHealthySpectatorsStay() {
        for (var state : List.of(RunState.FLOOR_READY,RunState.ENCOUNTER_ACTIVE,RunState.FLOOR_RESOLVING,RunState.INTERMISSION,RunState.NEXT_FLOOR_READY)) {
            var run=java.util.Optional.of(TestRuns.at(RUN,state,TestRuns.NOW));
            assertTrue(PartyJournalService.rentalNeedsExit(ENTRY,run,false,true));
            assertTrue(PartyJournalService.rentalNeedsExit(ENTRY,run,true,false));
            assertFalse(PartyJournalService.rentalNeedsExit(ENTRY,run,true,true));
        }
    }

    @Test void ordinaryPartyAndFinishedRunsDoNotTriggerRentalDeparture() {
        var regular=new PartyJournalEntry(TestRuns.PLAYER,RUN,List.of());
        assertFalse(PartyJournalService.rentalNeedsExit(regular,java.util.Optional.of(TestRuns.at(RUN,RunState.ENCOUNTER_ACTIVE,TestRuns.NOW)),false,true));
        assertFalse(PartyJournalService.rentalNeedsExit(ENTRY,java.util.Optional.empty(),false,true));
        assertFalse(PartyJournalService.rentalNeedsExit(ENTRY,java.util.Optional.of(TestRuns.at(RUN,RunState.ABANDONED,TestRuns.NOW)),false,true));
    }

    @Test void missingOriginalsAndRefusedPlacementsKeepTheJournal() {
        var id=UUID.randomUUID();
        var destination=Slot.party(0);
        var plan=new PartyArrangement.Restoration(java.util.Map.of(id,destination),List.of(),List.of(),List.of());
        assertFalse(PartyJournalService.restorationComplete(plan,java.util.Map.of(Slot.pc(0,0),id)));
        assertFalse(PartyJournalService.restorationComplete(plan,java.util.Map.of(destination,UUID.randomUUID())));
        assertTrue(PartyJournalService.restorationComplete(plan,java.util.Map.of(destination,id)));
        var missing=new PartyArrangement.Restoration(java.util.Map.of(),List.of(id),List.of(),List.of());
        assertFalse(PartyJournalService.restorationComplete(missing,java.util.Map.of()));
    }
}
