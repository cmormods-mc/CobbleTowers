package com.cobbletowers.command;

/** Who may open the Hall by typing /tower: the NPC (an operator-level command) opens it for everyone else. */
public final class HallAccess {

    public static final String REFUSAL = "Talk to the Tower Keeper to open the Tower Hall.";

    private HallAccess() {}

    /** Operators always; a player already in a team or run keeps the way back to their own screen. */
    public static boolean mayTypeIt(boolean operator, boolean inLobby, boolean inRun) {
        return operator || inLobby || inRun;
    }
}
