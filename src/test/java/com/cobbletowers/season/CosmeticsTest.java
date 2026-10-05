package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cosmetics, titles, the chat decoration and the season banners (P36d). */
class CosmeticsTest {

    @Test
    @DisplayName("a cosmetic name is taken apart into its season, name and kind, and anything else is refused")
    void parsing() {
        Cosmetics.Parsed champion = Cosmetics.parse("s12:title_champion").orElseThrow();
        assertEquals(12, champion.season());
        assertEquals(Cosmetics.Kind.TITLE, champion.kind());
        assertEquals(Cosmetics.Kind.BADGE, Cosmetics.parse("s1:badge").orElseThrow().kind());
        assertEquals(Cosmetics.Kind.BANNER, Cosmetics.parse("s1:banner_2").orElseThrow().kind());
        assertEquals(Cosmetics.Kind.CLUB, Cosmetics.parse("s1:club_gold").orElseThrow().kind());
        for (String bad : new String[] {"", "title_champion", "s:title_champion", "s0:badge", "sx:badge", "s1:nonsense", "x1:badge", null}) {
            assertTrue(Cosmetics.parse(bad).isEmpty(), "not a cosmetic: " + bad);
        }
    }

    @Test
    @DisplayName("full names and short chat forms read as the design says, and only a title has a short form")
    void names() {
        assertEquals("Champion of The Rising Tide", Cosmetics.fullName(Cosmetics.parse("s1:title_champion").orElseThrow(), "The Rising Tide"));
        assertEquals("Challenger of Deep Roots", Cosmetics.fullName(Cosmetics.parse("s2:title_challenger").orElseThrow(), "Deep Roots"));
        assertEquals("Deep Roots Badge", Cosmetics.fullName(Cosmetics.parse("s2:badge").orElseThrow(), "Deep Roots"));
        assertEquals("Deep Roots Banner III", Cosmetics.fullName(Cosmetics.parse("s2:banner_3").orElseThrow(), "Deep Roots"));
        assertEquals("The Long Dusk Club Champion", Cosmetics.fullName(Cosmetics.parse("s3:club_gold").orElseThrow(), "The Long Dusk"));
        assertEquals(Optional.of("Champion S1"), Cosmetics.shortTitle(Cosmetics.parse("s1:title_champion").orElseThrow()));
        assertEquals(Optional.of("Challenger S4"), Cosmetics.shortTitle(Cosmetics.parse("s4:title_challenger").orElseThrow()));
        assertTrue(Cosmetics.shortTitle(Cosmetics.parse("s1:badge").orElseThrow()).isEmpty());
        assertTrue(Cosmetics.shortTitle(Cosmetics.parse("s1:banner_1").orElseThrow()).isEmpty());
    }

    @Test
    @DisplayName("titles are listed newest season first and the higher title first within a season, ignoring everything that is not a title")
    void titleOrder() {
        List<String> titles = Cosmetics.titlesOf(Set.of("s1:title_challenger", "s2:title_challenger", "s1:title_champion", "s1:badge",
                "s1:banner_1", "s2:club_gold"));
        assertEquals(List.of("s2:title_challenger", "s1:title_champion", "s1:title_challenger"), titles);
    }

    @Test
    @DisplayName("a selection counts only while the player still owns that title, and only a title can be selected")
    void selection() {
        Set<String> owned = Set.of("s1:title_champion", "s1:badge");
        assertEquals(Optional.of("s1:title_champion"), Cosmetics.validSelection("s1:title_champion", owned));
        assertTrue(Cosmetics.validSelection("s2:title_champion", owned).isEmpty(), "not owned");
        assertTrue(Cosmetics.validSelection("s1:badge", owned).isEmpty(), "a badge is not wearable");
        assertTrue(Cosmetics.validSelection("", owned).isEmpty());
        assertTrue(Cosmetics.validSelection(null, owned).isEmpty());
    }

    @Test
    @DisplayName("the decoration is the title in gold then the club tag in the club's colour, and empty when there is neither")
    void decoration() {
        List<Cosmetics.Segment> both = Cosmetics.decoration(Optional.of("Champion S1"), Optional.of("TC"), Optional.of("blue"));
        assertEquals(List.of(new Cosmetics.Segment("Champion S1 ", "gold"), new Cosmetics.Segment("[TC] ", "blue")), both);
        assertEquals("Champion S1 [TC] ", Cosmetics.plain(both));
        assertEquals("[TC] ", Cosmetics.plain(Cosmetics.decoration(Optional.empty(), Optional.of("TC"), Optional.empty())),
                "a club with no banner colour shows white");
        assertEquals("Champion S1 ", Cosmetics.plain(Cosmetics.decoration(Optional.of("Champion S1"), Optional.empty(), Optional.empty())));
        assertTrue(Cosmetics.decoration(Optional.empty(), Optional.empty(), Optional.empty()).isEmpty(), "a plain name is not touched");
    }

    @Test
    @DisplayName("every banner colour, the three prestige ones included, maps to a real vanilla chat colour")
    void colours() {
        Set<String> real = Set.of("white", "gold", "light_purple", "aqua", "green", "dark_gray", "gray", "dark_aqua", "dark_purple", "blue",
                "dark_green", "red", "black", "yellow");
        for (String banner : com.cobbletowers.club.ClubBook.BANNERS) assertTrue(real.contains(Cosmetics.chatColor(banner)), banner);
        for (String banner : com.cobbletowers.club.ClubBook.PRESTIGE_BANNERS) assertTrue(real.contains(Cosmetics.chatColor(banner)), banner);
        assertEquals("gold", Cosmetics.chatColor("gold"));
        assertEquals("gray", Cosmetics.chatColor("silver"));
        assertEquals("white", Cosmetics.chatColor("not_a_colour"), "an unknown colour is white, never an error");
    }

    @Test
    @DisplayName("earn command templates fill the named tokens and leave unknown ones as written")
    void templating() {
        Map<String, String> values = Map.of("player", "Ash", "id", "s1_title_champion", "season", "1");
        assertEquals("lp user Ash permission set cobbletowers.cosmetic.s1_title_champion true",
                Cosmetics.expand("lp user {player} permission set cobbletowers.cosmetic.{id} true", values));
        assertEquals("say {mystery} Ash", Cosmetics.expand("say {mystery} {player}", values));
    }

    @Test
    @DisplayName("the store says which cosmetics were new, remembers a worn title, and keeps both across a save and load")
    void store() {
        TowerSeasonProgressStore store = new TowerSeasonProgressStore();
        UUID player = new UUID(3, 3);
        assertEquals(Set.of("s1:title_champion", "s1:badge"), store.addCosmetics(player, Set.of("s1:title_champion", "s1:badge")));
        assertEquals(Set.of("s1:banner_1"), store.addCosmetics(player, Set.of("s1:badge", "s1:banner_1")), "only the new one is reported");
        assertTrue(store.addCosmetics(player, Set.of("s1:badge")).isEmpty(), "a repeat award reports nothing, so no command would run twice");
        assertEquals("", store.selectedTitle(player));
        store.selectTitle(player, "s1:title_champion");
        TowerSeasonProgressStore restored = TowerSeasonProgressStore.load(store.save(new CompoundTag(), null), null);
        assertEquals("s1:title_champion", restored.selectedTitle(player));
        assertEquals(3, restored.cosmeticsOf(player).size());
        restored.selectTitle(player, "");
        assertEquals("", restored.selectedTitle(player));
    }

    @Test
    @DisplayName("the shipped banner steps are real banners: for every season the item text parses, carries patterns and a name, in the season's colour")
    void bannersParse() throws IOException {
        SeasonTrackDefinition track;
        try (Reader reader = Files.newBufferedReader(Paths.get(
                "src/main/resources/data/cobbletowers/cobbletowers/season_tracks/default.json"))) {
            track = SeasonTrackDefinition.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        }
        int[] steps = {6, 12, 18};
        String[] roman = {"I", "II", "III"};
        int[] layers = {1, 2, 4};
        for (int season = 1; season <= 3; season++) {
            Map<String, String> tokens = SeasonProgressService.tokensOf(season);
            String color = tokens.get("color");
            for (int i = 0; i < steps.length; i++) {
                SeasonTrackDefinition.Grant grant = track.steps().get(steps[i] - 1).grants().get(0);
                assertEquals("minecraft:" + color + "_banner", Cosmetics.expand(grant.item(), tokens));
                assertEquals(tokens.get("season_name") + " Banner " + roman[i], Cosmetics.expand(grant.label(), tokens));
                CompoundTag tag;
                try {
                    tag = TagParser.parseTag(Cosmetics.expand(grant.components(), tokens));
                } catch (Exception ex) {
                    throw new AssertionError("season " + season + " step " + steps[i] + " does not parse: " + ex.getMessage(), ex);
                }
                assertEquals("minecraft:" + color + "_banner", tag.getString("id"));
                CompoundTag components = tag.getCompound("components");
                ListTag patterns = components.getList("minecraft:banner_patterns", Tag.TAG_COMPOUND);
                assertEquals(layers[i], patterns.size(), "a banner with " + layers[i] + " pattern layer(s)");
                assertTrue(components.getString("minecraft:custom_name").contains(tokens.get("season_name") + " Banner " + roman[i]));
                assertFalse(components.getString("minecraft:custom_name").contains("{season"), "no token is left unexpanded");
            }
        }
    }

    @Test
    @DisplayName("a season's banner colour follows its spotlight region")
    void bannerColours() {
        assertEquals("blue", SeasonProgressService.tokensOf(1).get("color"));
        assertEquals("green", SeasonProgressService.tokensOf(2).get("color"));
        assertEquals("purple", SeasonProgressService.tokensOf(3).get("color"));
        assertEquals("blue", SeasonProgressService.tokensOf(4).get("color"), "a generated season rotates back to the first region");
    }
}
