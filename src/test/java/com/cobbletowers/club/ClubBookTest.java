package com.cobbletowers.club;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.club.ClubBook.Result;
import com.cobbletowers.persistence.TowerClubStore;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Clubs (P35): membership, the board, the weekly goal, and the store that keeps them. */
class ClubBookTest {

    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);
    private static final UUID C = new UUID(3, 3);

    private static ClubBook withClub() {
        ClubBook book = new ClubBook();
        assertEquals(Result.OK, book.create(A, "Ash", "Tidal_Crew", "tc", 0L));
        return book;
    }

    @Test
    @DisplayName("names and tags are validated, a name is unique ignoring case, and one player is in one club")
    void creating() {
        ClubBook book = new ClubBook();
        assertEquals(Result.BAD_NAME, book.create(A, "Ash", "x", "tag", 0L));
        assertEquals(Result.BAD_NAME, book.create(A, "Ash", "has space", "tag", 0L));
        assertEquals(Result.BAD_TAG, book.create(A, "Ash", "Good_Name", "t", 0L));
        assertEquals(Result.BAD_TAG, book.create(A, "Ash", "Good_Name", "toolong", 0L));
        assertEquals(Result.OK, book.create(A, "Ash", "Good_Name", "gn", 0L));
        assertEquals("GN", book.find("good_name").orElseThrow().tag(), "the tag is stored upper case, the name found any case");
        assertEquals(Result.NAME_TAKEN, book.create(B, "Bo", "GOOD_NAME", "zz", 0L));
        assertEquals(Result.ALREADY_IN_CLUB, book.create(A, "Ash", "Another", "an", 0L));
    }

    @Test
    @DisplayName("a club holds twelve; leaving passes ownership on, and the last one out ends it")
    void membership() {
        ClubBook book = withClub();
        for (int i = 0; i < ClubBook.MAX_MEMBERS - 1; i++) {
            assertEquals(Result.OK, book.join(new UUID(9, i), "P" + i, "tidal_crew"));
        }
        assertEquals(Result.FULL, book.join(B, "Bo", "Tidal_Crew"));
        assertEquals(Result.NO_SUCH_CLUB, book.join(B, "Bo", "nothing"));

        assertEquals(Result.OK, book.leave(A));
        assertEquals(new UUID(9, 0), book.find("Tidal_Crew").orElseThrow().owner(), "the longest-standing member takes over");
        assertEquals(Result.NOT_IN_CLUB, book.leave(A));

        ClubBook alone = withClub();
        assertEquals(Result.OK, alone.leave(A));
        assertTrue(alone.find("Tidal_Crew").isEmpty(), "a club with nobody left is gone");
    }

    @Test
    @DisplayName("only the owner can kick, disband or change the banner, and an unknown colour is refused")
    void ownerRights() {
        ClubBook book = withClub();
        book.join(B, "Bo", "Tidal_Crew");
        assertEquals(Result.NOT_OWNER, book.kick(B, A));
        assertEquals(Result.NOT_OWNER, book.disband(B));
        assertEquals(Result.NOT_OWNER, book.setBanner(B, "red"));
        assertEquals(Result.BAD_BANNER, book.setBanner(A, "plaid"));
        assertEquals(Result.OK, book.setBanner(A, "RED"));
        assertEquals("red", book.find("Tidal_Crew").orElseThrow().banner());
        assertEquals(Result.NOT_A_MEMBER, book.kick(A, A), "the owner cannot kick themselves");
        assertEquals(Result.NOT_A_MEMBER, book.kick(A, C));
        assertEquals(Result.OK, book.kick(A, B));
        assertTrue(book.clubOf(B).isEmpty());
        assertEquals(Result.OK, book.disband(A));
        assertTrue(book.all().isEmpty());
    }

    @Test
    @DisplayName("a club's score is the sum of its members' best clears, a player's best only ever rises, and joining brings it along")
    void scoring() {
        ClubBook book = withClub();
        book.recordClear(A, 40, "2026-W41");
        book.recordClear(A, 25, "2026-W41");
        assertEquals(40, book.bestOf(A), "a worse clear never lowers a best");
        book.recordClear(B, 90, "2026-W41");   // B is not in a club yet
        assertEquals(40, book.score(book.find("Tidal_Crew").orElseThrow()));
        book.join(B, "Bo", "Tidal_Crew");
        assertEquals(130, book.score(book.find("Tidal_Crew").orElseThrow()), "B's earlier best counts once they join");

        ClubBook other = new ClubBook();
        other.create(C, "Cy", "Rival_Crew", "rv", 0L);
        other.recordClear(C, 500, "2026-W41");
        assertEquals("Rival_Crew", other.top(5).get(0).name());
    }

    @Test
    @DisplayName("the board is best first, then by name, and capped")
    void board() {
        ClubBook book = new ClubBook();
        book.create(A, "Ash", "Bravo_Crew", "bc", 0L);
        book.create(B, "Bo", "Alpha_Crew", "ac", 0L);
        book.create(C, "Cy", "Charlie_Crew", "cc", 0L);
        book.recordClear(C, 70, "w");
        assertEquals("Charlie_Crew", book.top(3).get(0).name());
        assertEquals("Alpha_Crew", book.top(3).get(1).name(), "a tie is broken by name");
        assertEquals(2, book.top(2).size());
    }

    @Test
    @DisplayName("the weekly goal is met on the clear that reaches it, pays each member once, and resets with the week")
    void weeklyGoal() {
        ClubBook book = withClub();
        book.join(B, "Bo", "Tidal_Crew");
        for (int i = 0; i < ClubBook.WEEKLY_GOAL - 1; i++) {
            assertFalse(book.recordClear(i % 2 == 0 ? A : B, 10, "2026-W41"), "not yet");
        }
        assertEquals(Result.GOAL_NOT_MET, book.claim(A, "2026-W41"));
        assertTrue(book.recordClear(A, 10, "2026-W41"), "the clear that reaches the goal says so");
        assertFalse(book.recordClear(B, 10, "2026-W41"), "and only that one");
        assertEquals(Result.OK, book.claim(A, "2026-W41"));
        assertEquals(Result.ALREADY_CLAIMED, book.claim(A, "2026-W41"));
        assertEquals(Result.OK, book.claim(B, "2026-W41"), "each member claims once");
        assertEquals(Result.NOT_IN_CLUB, book.claim(C, "2026-W41"));

        assertEquals(0, book.weekClears(book.find("Tidal_Crew").orElseThrow(), "2026-W42"), "a new week starts at nothing");
        assertEquals(Result.GOAL_NOT_MET, book.claim(A, "2026-W42"));
    }

    @Test
    @DisplayName("the store keeps clubs, members, banners, the week's progress, claims and bests across a save and load")
    void storeRoundTrip() {
        TowerClubStore store = new TowerClubStore();
        ClubBook book = store.book();
        book.create(A, "Ash", "Tidal_Crew", "tc", 5L);
        book.join(B, "Bo", "Tidal_Crew");
        book.setBanner(A, "blue");
        book.recordClear(A, 77, "2026-W41");
        book.addWeekClears(book.find("Tidal_Crew").orElseThrow(), ClubBook.WEEKLY_GOAL, "2026-W41");
        book.claim(B, "2026-W41");

        ClubBook restored = TowerClubStore.load(store.save(new CompoundTag(), null), null).book();
        ClubBook.Club club = restored.find("tidal_crew").orElseThrow();
        assertEquals(A, club.owner());
        assertEquals(2, club.members().size());
        assertEquals("blue", club.banner());
        assertEquals("TC", club.tag());
        assertEquals(77, restored.bestOf(A));
        assertEquals(ClubBook.WEEKLY_GOAL + 1, club.weekClears());
        assertEquals(Result.ALREADY_CLAIMED, restored.claim(B, "2026-W41"), "a claim made before a restart is still made");
        assertEquals(Result.OK, restored.claim(A, "2026-W41"));
    }
}
