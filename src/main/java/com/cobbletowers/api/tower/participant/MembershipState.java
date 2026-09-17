package com.cobbletowers.api.tower.participant;

/** Whether a participant still belongs to the run at all. */
public enum MembershipState {
    MEMBER,
    /** Left on purpose. Irreversible (TDS #39), and distinct from a disconnect. */
    VOLUNTARILY_LEFT
}
