package com.cobbletowers.rental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.PlaylistDefinition;
import com.cobbletowers.definition.PlaylistDefinition.CardRewards;
import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.definition.RentalSetDefinition.Rarity;
import com.cobbletowers.persistence.PartyJournalEntry.LentCard;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.PartyJournalEntry;
import com.cobbletowers.rental.CardRewardPolicy.Decision;
import com.cobbletowers.rental.CardRewardPolicy.Outcome;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** When a finished rental run earns real cards (P33b), and the plain-data pieces that carry them. */
class CardRewardPolicyTest {

    private static final Path SETS = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/rental_sets");
    private static final Path PLAYLISTS = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/playlists");
    private static final CardRewards ON = new CardRewards(true, Rarity.EPIC, 3);

    private static Map<String, RentalSetDefinition> sets() throws IOException {
        Map<String, RentalSetDefinition> all = new java.util.LinkedHashMap<>();
        try (Stream<Path> files = Files.list(SETS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.put(id.toString(), RentalSetDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }

    private static Function<String, Optional<RentalSetDefinition>> lookup() throws IOException {
        Map<String, RentalSetDefinition> all = sets();
        return id -> Optional.ofNullable(all.get(id));
    }

    private static List<LentCard> team(String... species) {
        List<LentCard> team = new ArrayList<>();
        int n = 0;
        for (String one : species) team.add(new LentCard(new UUID(1, n++), "cobbletowers:" + one, false));
        return team;
    }

    // ---- the policy -------------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a completed, scored run earns one card for each Pokemon the player ran with")
    void grants() throws IOException {
        Decision decision = CardRewardPolicy.decide(ON, true, false, 0, team("garchomp", "machamp", "lugia"), lookup());
        assertTrue(decision.grants());
        assertEquals(3, decision.cards().size());
        assertEquals("garchomp", decision.cards().get(0).card().pokemonId());
        assertEquals("Garchomp card (epic)", decision.cards().get(0).label());
        assertEquals("common", decision.cards().get(1).card().rarity());
        assertEquals("epic", decision.cards().get(2).card().rarity(), "Lugia is legendary, and the cap is epic");
    }

    @Test
    @DisplayName("each rule that withholds cards says which one")
    void withholds() throws IOException {
        List<LentCard> team = team("garchomp");
        assertEquals(Outcome.DISABLED, CardRewardPolicy.decide(CardRewards.NONE, true, false, 0, team, lookup()).outcome());
        assertEquals(Outcome.NOT_COMPLETED, CardRewardPolicy.decide(ON, false, false, 0, team, lookup()).outcome());
        assertEquals(Outcome.PRACTICE_TRIAL, CardRewardPolicy.decide(ON, true, true, 0, team, lookup()).outcome());
        assertEquals(Outcome.DAILY_LIMIT, CardRewardPolicy.decide(ON, true, false, 3, team, lookup()).outcome());
        assertEquals(Outcome.NO_TEAM, CardRewardPolicy.decide(ON, true, false, 0, List.of(), lookup()).outcome());
        assertEquals(Outcome.NO_TEAM, CardRewardPolicy.decide(ON, true, false, 0, team("no_such_set"), lookup()).outcome(),
                "a set that is no longer loaded makes nothing");
        for (Outcome outcome : List.of(Outcome.DISABLED, Outcome.NOT_COMPLETED, Outcome.PRACTICE_TRIAL, Outcome.DAILY_LIMIT)) {
            assertTrue(CardRewardPolicy.decide(outcome == Outcome.DISABLED ? CardRewards.NONE : ON, outcome != Outcome.NOT_COMPLETED,
                    outcome == Outcome.PRACTICE_TRIAL, outcome == Outcome.DAILY_LIMIT ? 3 : 0, team, lookup()).cards().isEmpty());
        }
    }

    @Test
    @DisplayName("the daily allowance is runs, not cards: two runs under a limit of three both earn, the fourth does not")
    void dailyLimit() throws IOException {
        List<LentCard> team = team("garchomp", "machamp");
        assertTrue(CardRewardPolicy.decide(ON, true, false, 0, team, lookup()).grants());
        assertTrue(CardRewardPolicy.decide(ON, true, false, 2, team, lookup()).grants());
        assertFalse(CardRewardPolicy.decide(ON, true, false, 3, team, lookup()).grants());
    }

    @Test
    @DisplayName("a Pokemon from a God Pack makes a shiny card")
    void godPackCard() throws IOException {
        List<LentCard> team = List.of(new LentCard(new UUID(2, 0), "cobbletowers:kingambit", true));
        Decision decision = CardRewardPolicy.decide(ON, true, false, 0, team, lookup());
        assertTrue(decision.cards().get(0).card().shiny());
        assertEquals("Kingambit card (shiny epic)", decision.cards().get(0).label());
    }

    // ---- the playlist file --------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the shipped Rental playlist grants cards, capped at epic, from three runs a day; no other playlist does")
    void shippedPlaylist() throws IOException {
        for (Path file : Files.list(PLAYLISTS).filter(p -> p.toString().endsWith(".json")).toList()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
            try (Reader reader = Files.newBufferedReader(file)) {
                PlaylistDefinition playlist = PlaylistDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject());
                if (id.getPath().equals("rental")) {
                    assertEquals(new CardRewards(true, Rarity.EPIC, 3), playlist.cardRewards());
                } else {
                    assertFalse(playlist.cardRewards().enabled(), id.getPath());
                }
            }
        }
    }

    @Test
    @DisplayName("a bad card_rewards block is refused: an unknown rarity, no runs, a negative number")
    void badConfig() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", "x");
        String base = "{\"schema_version\":1,\"display_name\":\"X\",";
        assertThrows(IllegalArgumentException.class, () -> PlaylistDefinition.fromJson(id, JsonParser.parseString(
                base + "\"card_rewards\":{\"enabled\":true,\"max_rarity\":\"ultra\"}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PlaylistDefinition.fromJson(id, JsonParser.parseString(
                base + "\"card_rewards\":{\"enabled\":true,\"runs_per_day\":0}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> PlaylistDefinition.fromJson(id, JsonParser.parseString(
                base + "\"card_rewards\":{\"enabled\":false,\"runs_per_day\":-1}}").getAsJsonObject()));
        assertEquals(CardRewards.NONE, PlaylistDefinition.fromJson(id, JsonParser.parseString(base + "\"x\":1}").getAsJsonObject()).cardRewards());
    }

    // ---- the plain data that carries a card ------------------------------------------------------------------------

    @Test
    @DisplayName("a pending card keeps its data and its label through disk, and an old pending reward still loads")
    void pendingCardRoundTrip() throws IOException {
        RentalCards.Spec card = RentalCards.of(sets().get("cobbletowers:garchomp"), false, Rarity.EPIC);
        UUID run = UUID.fromString("dddddddd-0000-0000-0000-000000000009");
        PendingTowerReward reward = new PendingTowerReward(run, 5, RentalCards.ITEM, 1, 1L, card.toItemTag().toString(), "Garchomp card (epic)");
        PendingTowerReward back = PendingTowerReward.fromTag(reward.toTag());
        assertEquals(reward, back);
        assertTrue(back.components().contains("cobblemon-cards:card_data") && back.components().contains("dragon_spawn"));
        assertEquals("Garchomp card (epic)", back.label());

        CompoundTag old = new PendingTowerReward(run, 5, ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), 2, 1L).toTag();
        assertFalse(old.contains("components") || old.contains("label"), "a plain reward writes nothing extra");
        PendingTowerReward plain = PendingTowerReward.fromTag(old);
        assertEquals("", plain.components());
        assertEquals("", plain.label());
    }

    @Test
    @DisplayName("the party journal keeps which set each rental was and whether it was God Pack, and an old journal has none")
    void journalTokens() {
        UUID player = new UUID(5, 5);
        UUID run = new UUID(6, 6);
        List<UUID> ids = List.of(new UUID(7, 1), new UUID(7, 2));
        List<LentCard> cards = List.of(new LentCard(ids.get(0), "cobbletowers:garchomp", false), new LentCard(ids.get(1), "cobbletowers:lugia", true));
        PartyJournalEntry entry = new PartyJournalEntry(player, run, List.of(), ids, cards);
        PartyJournalEntry back = PartyJournalEntry.fromTag(entry.toTag());
        assertEquals(entry, back);
        assertEquals(cards, back.cards());
        CompoundTag old = entry.toTag();
        old.remove("cards");
        assertEquals(List.of(), PartyJournalEntry.fromTag(old).cards());
        assertEquals(ids, PartyJournalEntry.fromTag(old).rentals(), "the rentals themselves are never lost");
    }
}
