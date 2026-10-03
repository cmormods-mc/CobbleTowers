package com.cobbletowers.lobby;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.lobby.TowerLobby.Response;
import com.cobbletowers.lobby.TowerLobby.Result;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A team forming: invites, answers, lapsing, starting early and the countdown. */
class TowerLobbyTest {

    private static final ResourceLocation NEUTRAL = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");
    private static final ResourceLocation OTHER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
    private static final UUID HOST = id(1);
    private static final UUID A = id(2);
    private static final UUID B = id(3);
    private static final UUID C = id(4);
    private static final UUID D = id(5);

    private static UUID id(int n) {
        return UUID.fromString("00000000-0000-0000-0000-00000000000" + n);
    }

    @Test
    @DisplayName("a lobby starts with only its host on the team")
    void startsWithHost() {
        assertEquals(List.of(HOST), new TowerLobby(HOST, NEUTRAL).team());
    }

    @Test
    @DisplayName("an accepted invite puts a player on the team; a pending one does not")
    void acceptJoinsTeam() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);
        lobby.invite(B, 0);
        lobby.accept(A, 1);

        assertEquals(List.of(HOST, A), lobby.team());
        assertEquals(List.of(B), lobby.pending(), "starting early drops exactly the unanswered");
        assertEquals(Response.ACCEPTED, lobby.responseOf(A).orElseThrow());
    }

    @Test
    @DisplayName("the host and three invitees fill the lobby; a fifth is refused")
    void capsAtFour() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        assertEquals(Result.OK, lobby.invite(A, 0));
        assertEquals(Result.OK, lobby.invite(B, 0));
        assertEquals(Result.OK, lobby.invite(C, 0));
        assertEquals(Result.FULL, lobby.invite(D, 0), "pending invites count toward the cap");
    }

    @Test
    @DisplayName("inviting the host or someone already invited is refused")
    void noDuplicates() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);

        assertEquals(Result.ALREADY_ON_TEAM, lobby.invite(A, 1));
        assertEquals(Result.ALREADY_ON_TEAM, lobby.invite(HOST, 1));
    }

    @Test
    @DisplayName("declining frees the slot")
    void declineFreesSlot() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);
        lobby.invite(B, 0);
        lobby.invite(C, 0);
        assertEquals(Result.OK, lobby.decline(A));

        assertEquals(Result.OK, lobby.invite(D, 1));
        assertEquals(Result.NO_INVITE, lobby.decline(A), "nothing left to decline");
    }

    @Test
    @DisplayName("an invite lapses after three minutes and cannot be accepted late")
    void invitesLapse() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);

        assertEquals(List.of(), lobby.expire(TowerLobby.INVITE_TTL_MILLIS - 1));
        assertEquals(Result.NO_INVITE, lobby.accept(A, TowerLobby.INVITE_TTL_MILLIS));
        assertFalse(lobby.contains(A));
    }

    @Test
    @DisplayName("an accepted invite does not lapse")
    void acceptedDoesNotLapse() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);
        lobby.accept(A, 1000);

        assertEquals(List.of(), lobby.expire(TowerLobby.INVITE_TTL_MILLIS * 10));
        assertTrue(lobby.contains(A));
    }

    @Test
    @DisplayName("changing the tower un-accepts everyone, since they agreed to a different one")
    void newTowerReasks() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);
        lobby.accept(A, 1);
        lobby.selectTower(OTHER);

        assertEquals(OTHER, lobby.tower());
        assertEquals(List.of(HOST), lobby.team());
        assertEquals(List.of(A), lobby.pending());
    }

    @Test
    @DisplayName("the countdown reports whole seconds left, becomes due, and cancels when someone leaves")
    void countdown() {
        TowerLobby lobby = new TowerLobby(HOST, NEUTRAL);
        lobby.invite(A, 0);
        lobby.accept(A, 0);
        assertEquals(-1, lobby.secondsLeft(0));

        lobby.beginCountdown(1000, 5000);
        assertEquals(5, lobby.secondsLeft(1000));
        assertEquals(1, lobby.secondsLeft(5500));
        assertFalse(lobby.countdownDue(5999));
        assertTrue(lobby.countdownDue(6000));

        lobby.remove(A);
        assertFalse(lobby.counting(), "a team that changed is not the team that agreed to start");
    }
}
