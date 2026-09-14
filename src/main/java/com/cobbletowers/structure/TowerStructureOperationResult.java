package com.cobbletowers.structure;

/**
 * Result of one bounded structure operation.
 *
 * <p>Expected asset/configuration faults are represented as data rather than thrown. Unexpected
 * runtime exceptions are caught by the scheduler and converted into retryable failures so they do
 * not escape into the Minecraft server tick loop.
 */
public enum TowerStructureOperationResult {
    SUCCESS,
    RETRYABLE_FAILURE,
    ASSET_FAULT
}
