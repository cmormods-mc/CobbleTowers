package com.cobbletowers.api.tower.participant;

import java.util.Objects;

/**
 * A participant's state on three independent axes.
 *
 * <p>TDS §8 lists these as one enum, which cannot hold two facts at once -- and they do occur
 * together: a player who is knocked out and then loses connection is both, and a reconnect has to
 * put them back exactly where they were. With separate axes a disconnect touches only
 * {@link ConnectionState}, so the combat axis is still there to return to; with one enum it would
 * have to be guessed.
 *
 * <p>Immutable. Every change returns a new state, and a participant who left is frozen: once
 * membership is {@link MembershipState#VOLUNTARILY_LEFT}, every transition here returns {@code this}.
 */
public record ParticipantState(ConnectionState connection, CombatState combat, MembershipState membership) {

    public ParticipantState {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(membership, "membership");
    }

    /** How a player enters a run: present, able to fight, a member. */
    public static ParticipantState joined() {
        return new ParticipantState(ConnectionState.ONLINE, CombatState.ACTIVE, MembershipState.MEMBER);
    }

    /** True only for a member who is online and able to fight. */
    public boolean canFight() {
        return membership == MembershipState.MEMBER
                && connection == ConnectionState.ONLINE
                && combat == CombatState.ACTIVE;
    }

    /** True while they are watching rather than fighting. */
    public boolean isSpectating() {
        return combat == CombatState.SPECTATING_TEAM || combat == CombatState.KNOCKED_OUT;
    }

    /** True while the run still counts them as one of its players. */
    public boolean isInRun() {
        return membership == MembershipState.MEMBER;
    }

    /** The connection dropped. The combat axis is untouched, which is what makes a reconnect exact. */
    public ParticipantState disconnected() {
        return withConnection(ConnectionState.DISCONNECTED);
    }

    /** Back on the server, in whatever combat state they left. */
    public ParticipantState reconnected() {
        return withConnection(ConnectionState.ONLINE);
    }

    /** Their whole registered party fainted. */
    public ParticipantState knockedOut() {
        return withCombat(CombatState.KNOCKED_OUT);
    }

    /** Watching a teammate after being knocked out. */
    public ParticipantState spectating() {
        return withCombat(CombatState.SPECTATING_TEAM);
    }

    /** Teammates cleared the floor; they return at the next intermission. */
    public ParticipantState revivePending() {
        return withCombat(CombatState.REVIVE_PENDING);
    }

    /** The intermission returned them to the fight. */
    public ParticipantState revived() {
        return withCombat(CombatState.ACTIVE);
    }

    /** They chose to leave. Irreversible. */
    public ParticipantState left() {
        return membership == MembershipState.VOLUNTARILY_LEFT ? this
                : new ParticipantState(connection, combat, MembershipState.VOLUNTARILY_LEFT);
    }

    private ParticipantState withConnection(ConnectionState next) {
        if (membership == MembershipState.VOLUNTARILY_LEFT || connection == next) return this;
        return new ParticipantState(next, combat, membership);
    }

    private ParticipantState withCombat(CombatState next) {
        if (membership == MembershipState.VOLUNTARILY_LEFT || combat == next) return this;
        return new ParticipantState(connection, next, membership);
    }
}
