package com.cobbletowers.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A player's CobbleDollar wallet (TDS #18): survives a save/load, and never goes negative. */
class TowerWalletStoreTest {

    private static final UUID PLAYER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000000a");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000000b");

    @Test
    @DisplayName("a fresh wallet is empty")
    void freshWalletIsEmpty() {
        assertEquals(0L, new TowerWalletStore().balanceOf(PLAYER_A));
    }

    @Test
    @DisplayName("credits accumulate")
    void creditsAccumulate() {
        TowerWalletStore store = new TowerWalletStore();
        store.credit(PLAYER_A, 25);
        store.credit(PLAYER_A, 15);

        assertEquals(40L, store.balanceOf(PLAYER_A));
    }

    @Test
    @DisplayName("a debit that fits succeeds and takes exactly that much")
    void debitThatFitsSucceeds() {
        TowerWalletStore store = new TowerWalletStore();
        store.credit(PLAYER_A, 40);

        assertTrue(store.debit(PLAYER_A, 25));
        assertEquals(15L, store.balanceOf(PLAYER_A));
    }

    @Test
    @DisplayName("a debit that does not fit fails and changes nothing")
    void debitThatDoesNotFitFails() {
        TowerWalletStore store = new TowerWalletStore();
        store.credit(PLAYER_A, 10);

        assertFalse(store.debit(PLAYER_A, 11));
        assertEquals(10L, store.balanceOf(PLAYER_A), "a failed purchase must not partially charge");
    }

    @Test
    @DisplayName("wallets are independent per player")
    void walletsAreIndependent() {
        TowerWalletStore store = new TowerWalletStore();
        store.credit(PLAYER_A, 50);

        assertEquals(0L, store.balanceOf(PLAYER_B));
        assertFalse(store.debit(PLAYER_B, 1));
    }

    @Test
    @DisplayName("balances survive a save and load")
    void roundTrip() {
        TowerWalletStore store = new TowerWalletStore();
        store.credit(PLAYER_A, 40);
        store.credit(PLAYER_B, 5);

        TowerWalletStore restored = TowerWalletStore.load(store.save(new CompoundTag(), null), null);

        assertEquals(40L, restored.balanceOf(PLAYER_A));
        assertEquals(5L, restored.balanceOf(PLAYER_B));
    }
}
