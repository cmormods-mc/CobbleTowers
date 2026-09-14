package com.cobbletowers.structure;

import java.util.Objects;

/**
 * Immutable persistent structure-work cursor for one private Tower cell.
 *
 * <p>{@code sectionIndex} always points at the section currently requiring work. It advances only
 * after that section succeeds. A restart can therefore safely resume by replaying the same canonical
 * section without attempting to infer world state by scanning blocks.
 */
public record TowerCellProgress(
        TowerCellState state,
        int sectionIndex,
        int failedAttempts
) {
    public TowerCellProgress {
        Objects.requireNonNull(state, "state");
        if (sectionIndex < 0 || sectionIndex > TowerStructureSection.ordered().size()) {
            throw new IllegalArgumentException("sectionIndex out of bounds: " + sectionIndex);
        }
        if (failedAttempts < 0) throw new IllegalArgumentException("failedAttempts must be >= 0");
    }

    public static TowerCellProgress allocated() {
        return new TowerCellProgress(TowerCellState.ALLOCATED, 0, 0);
    }

    public TowerCellProgress beginBuild() {
        requireState(TowerCellState.ALLOCATED);
        return new TowerCellProgress(TowerCellState.BUILDING, 0, 0);
    }

    public TowerCellProgress recordBuildSuccess() {
        requireState(TowerCellState.BUILDING);
        int next = sectionIndex + 1;
        if (next == TowerStructureSection.ordered().size()) {
            return new TowerCellProgress(TowerCellState.READY, next, 0);
        }
        return new TowerCellProgress(TowerCellState.BUILDING, next, 0);
    }

    public TowerCellProgress activate() {
        requireState(TowerCellState.READY);
        return new TowerCellProgress(TowerCellState.ACTIVE, sectionIndex, 0);
    }

    public TowerCellProgress beginTeardown() {
        if (state != TowerCellState.ACTIVE && state != TowerCellState.READY && state != TowerCellState.QUARANTINED) {
            throw new IllegalStateException("Cannot begin teardown from " + state);
        }
        return new TowerCellProgress(TowerCellState.TEARDOWN, 0, 0);
    }

    public TowerCellProgress recordCleanupSuccess() {
        requireState(TowerCellState.TEARDOWN);
        int next = sectionIndex + 1;
        return new TowerCellProgress(TowerCellState.TEARDOWN, next, 0);
    }

    public boolean cleanupComplete() {
        return state == TowerCellState.TEARDOWN && sectionIndex == TowerStructureSection.ordered().size();
    }

    public TowerCellProgress recordFailure() {
        if (state != TowerCellState.BUILDING && state != TowerCellState.TEARDOWN) {
            throw new IllegalStateException("Cannot record structure work failure in " + state);
        }
        return new TowerCellProgress(state, sectionIndex, failedAttempts + 1);
    }

    public TowerCellProgress quarantine() {
        return new TowerCellProgress(TowerCellState.QUARANTINED, sectionIndex, failedAttempts);
    }

    public TowerStructureSection currentSection() {
        if (sectionIndex >= TowerStructureSection.ordered().size()) {
            throw new IllegalStateException("No current structure section; cursor is complete");
        }
        return TowerStructureSection.atIndex(sectionIndex);
    }

    private void requireState(TowerCellState expected) {
        if (state != expected) throw new IllegalStateException("Expected " + expected + " but was " + state);
    }
}
