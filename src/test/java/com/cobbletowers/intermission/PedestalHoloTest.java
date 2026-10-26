package com.cobbletowers.intermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.network.IntermissionStatePayload;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PedestalHoloTest {

    private static IntermissionStatePayload.Card card(int risk, String... lines) {
        return new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "x"), "No Retreat", 0, risk,
                "theme", List.of(lines));
    }

    @Test
    void thePayoutIsTheFirstPercentageOnTheCard() {
        assertEquals("+15%", PedestalHolo.payoutOf(card(2, "[+] Severe risk * +15% to your final payout", "[-] No switching")));
    }

    @Test
    void aRoomWithNoPayoutIsAnEventAndAnEmptyModifierIsADash() {
        assertEquals("EVENT", PedestalHolo.payoutOf(card(-1, "Library: study")));
        assertEquals("—", PedestalHolo.payoutOf(card(1, "[-] Foes rise")));
    }

    @Test
    void theDetailIsTheDownsideClippedToOneRow() {
        String detail = PedestalHolo.detailOf(card(2, "[+] +15%", "[-] No switching: your lead stands or falls until the end"));
        assertTrue(detail.length() <= PedestalHolo.MAX_DETAIL_CHARS, detail);
        assertTrue(detail.startsWith("No switching"), detail);
        assertTrue(detail.endsWith("…"), detail);
    }

    @Test
    void aCardWithNoDownsideShowsItsFirstNonUpsideLine() {
        assertEquals("Rain falls every battle.",
                PedestalHolo.detailOf(card(1, "[+] Moderate risk * +8% to your final payout", "Rain falls every battle.")));
    }

    @Test
    void aRoomShowsItsFirstLineAsTheDetail() {
        assertEquals("Library: study", PedestalHolo.detailOf(card(-1, "Library: study")));
    }

    @Test
    void pipsShowCastVotesOutOfTheElectorateAndNeverOverflow() {
        assertEquals("●○○", PedestalHolo.pips(1, 3));
        assertEquals("●●●", PedestalHolo.pips(9, 3));
        assertEquals("○", PedestalHolo.pips(0, 0));
        assertEquals("○○○○○○", PedestalHolo.pips(0, 40));
    }

    @Test
    void theBodyAlwaysHasTheSameNumberOfRowsSoThePiecesNeverMove() {
        for (int risk : new int[] {-1, 0, 1, 2}) {
            for (boolean settled : new boolean[] {false, true}) {
                String body = PedestalHolo.build(card(risk, "[+] +8% payout", "[-] Something"), 1, 3, settled, true).body().getString();
                assertEquals(PedestalRules.BODY_LINES, body.split("\n", -1).length, body);
            }
        }
    }

    @Test
    void aSettledCardSaysChosenOrNotChosen() {
        assertTrue(PedestalHolo.build(card(1, "[-] x"), 3, 3, true, true).body().getString().contains("CHOSEN"));
        assertTrue(PedestalHolo.build(card(1, "[-] x"), 0, 3, true, false).body().getString().contains("not chosen"));
        assertTrue(PedestalHolo.build(card(1, "[-] x"), 0, 3, false, false).body().getString().contains("Right-click"));
    }
}
