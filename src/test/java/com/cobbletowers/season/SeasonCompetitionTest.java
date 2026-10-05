package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.club.ClubBook;
import com.cobbletowers.club.ClubBook.Result;
import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.echo.Echo;
import com.cobbletowers.echo.EchoPolicy;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.persistence.TowerClubStore;
import com.cobbletowers.persistence.TowerEchoStore;
import com.cobbletowers.persistence.TowerHallStore;
import com.cobbletowers.reward.RewardDraw;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What seasons add to loot, clubs and Echoes (P36c). */
class SeasonCompetitionTest {

    private static final ResourceLocation TIDE = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale");
    private static final ResourceLocation NEUTRAL = ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral");

    private static ResourceLocation item(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    private static UUID player(int n) {
        return new UUID(4, n);
    }

    // ---- the spotlight --------------------------------------------------------------------------------

    @Test
    @DisplayName("the spotlight region's own armor is worth double in its own tower, and nothing else changes")
    void spotlightWeights() {
        Optional<ResourceLocation> spotlight = Optional.of(TIDE);
        assertEquals(200, SeasonSpotlight.weightPercent(spotlight, TIDE, item("tideforge_helmet")));
        assertEquals(200, SeasonSpotlight.weightPercent(spotlight, TIDE, item("tideforge_boots")));
        assertEquals(100, SeasonSpotlight.weightPercent(spotlight, TIDE, item("rootvale_helmet")), "another region's armor is unchanged");
        assertEquals(100, SeasonSpotlight.weightPercent(spotlight, TIDE, item("challenger_helmet")), "so is the neutral set");
        assertEquals(100, SeasonSpotlight.weightPercent(spotlight, TIDE, ResourceLocation.fromNamespaceAndPath("cobblemon", "exp_candy_xl")));
        assertEquals(100, SeasonSpotlight.weightPercent(spotlight, ROOT, item("rootvale_helmet")), "a tower that is not in the spotlight is unchanged");
        assertEquals(100, SeasonSpotlight.weightPercent(Optional.empty(), TIDE, item("tideforge_helmet")), "with no season there is no spotlight");
        assertEquals(100, SeasonSpotlight.weightPercent(spotlight, NEUTRAL, item("tideforge_helmet")), "and Neutral never has one");
        assertFalse(SeasonSpotlight.isRegionArmor(TIDE, item("tideforge_sword")));
    }

    @Test
    @DisplayName("doubling the weight makes the armor about twice as likely over many rolls, leaves a weight of 100 exactly as it was, and never removes an entry")
    void spotlightRolls() {
        List<RewardTableDefinition.Entry> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) pool.add(new RewardTableDefinition.Entry(item("filler_" + i), 1, 1, 10));
        RewardTableDefinition.Entry helmet = new RewardTableDefinition.Entry(item("tideforge_helmet"), 1, 1, 5);
        pool.add(helmet);
        int plain = 0;
        int doubled = 0;
        for (int n = 0; n < 40_000; n++) {
            if (RewardDraw.pickItem(77L, 3, n, pool).equals(helmet)) plain++;
            if (RewardDraw.pickItem(77L, 3, n, pool, e -> SeasonSpotlight.weightPercent(Optional.of(TIDE), TIDE, e.item())).equals(helmet)) doubled++;
            assertEquals(RewardDraw.pickItem(77L, 3, n, pool), RewardDraw.pickItem(77L, 3, n, pool, e -> 100),
                    "a weight of 100 is the same draw as before");
        }
        double ratio = (double) doubled / plain;
        assertTrue(ratio > 1.7 && ratio < 2.1, "about twice as likely, got " + ratio + " (" + plain + " vs " + doubled + ")");
        assertTrue(RewardDraw.scaled(new RewardTableDefinition.Entry(item("x"), 1, 1, 1), e -> 0) >= 1, "an entry never drops out of the pool");
    }

    // ---- club seasons ------------------------------------------------------------------------------------

    private static ClubBook twoClubs() {
        ClubBook book = new ClubBook();
        book.create(player(1), "Ash", "Tidal_Crew", "tc", 0L);
        book.join(player(2), "Bo", "Tidal_Crew");
        book.create(player(3), "Cy", "Root_Crew", "rc", 0L);
        return book;
    }

    @Test
    @DisplayName("a club's season score is its members' best clears in that season only, kept apart from the all-time score and from other seasons")
    void clubSeasonScore() {
        ClubBook book = twoClubs();
        book.recordClear(player(1), 50, "w1", 1);
        book.recordClear(player(1), 30, "w1", 1);
        book.recordClear(player(2), 40, "w1", 1);
        book.recordClear(player(3), 70, "w1", 1);
        ClubBook.Club tidal = book.find("Tidal_Crew").orElseThrow();
        assertEquals(90, book.seasonScore(tidal, 1), "50 + 40: a worse clear never lowers a best");
        assertEquals(90, book.score(tidal), "the all-time score is still kept");
        assertEquals(0, book.seasonScore(tidal, 2), "another season starts from nothing");
        book.recordClear(player(1), 20, "w1", 2);
        assertEquals(20, book.seasonScore(tidal, 2));
        assertEquals(90, book.seasonScore(tidal, 1), "and does not disturb the old one");
        assertEquals(90, book.score(tidal), "the all-time best stays at 50 + 40 because 20 does not beat 50");

        book.recordClear(player(1), 999, "w1", 0);
        assertEquals(0, book.seasonBestOf(0, player(1)), "a clear outside any season is not a season score");
        assertEquals(999, book.bestOf(player(1)), "but it is an all-time best");
    }

    @Test
    @DisplayName("the season's club board ranks only clubs that scored, best first, ties by name")
    void clubSeasonBoard() {
        ClubBook book = twoClubs();
        book.create(player(4), "Di", "Quiet_Crew", "qc", 0L);
        book.recordClear(player(1), 60, "w", 1);
        book.recordClear(player(3), 60, "w", 1);
        List<ClubBook.Club> board = book.topForSeason(1, 10);
        assertEquals(2, board.size(), "a club that did not score is not on it");
        assertEquals("Root_Crew", board.get(0).name(), "a tie is broken by name");
        assertEquals("Tidal_Crew", board.get(1).name());
        assertEquals(1, book.topForSeason(1, 1).size());
    }

    @Test
    @DisplayName("prestige banners need honouring: a club cannot set gold until it has finished in the top three, and honours are idempotent")
    void prestigeBanners() {
        ClubBook book = twoClubs();
        assertEquals(Result.BAD_BANNER, book.setBanner(player(1), "gold"));
        assertEquals(Result.OK, book.setBanner(player(1), "red"));
        ClubBook.Club tidal = book.find("Tidal_Crew").orElseThrow();
        book.honor(tidal, "gold", "Season 1: champion");
        book.honor(tidal, "gold", "Season 1: champion");
        assertEquals(List.of("Season 1: champion"), tidal.honors(), "a replayed award adds nothing");
        assertEquals(Set.of("gold"), tidal.unlockedBanners());
        assertEquals(Result.OK, book.setBanner(player(1), "gold"));
        assertEquals("gold", tidal.banner());
        assertEquals(Result.BAD_BANNER, book.setBanner(player(1), "silver"), "only the one it earned");
        assertEquals(Result.BAD_BANNER, book.setBanner(player(3), "gold"), "and another club's is not unlocked by it");
    }

    @Test
    @DisplayName("old season bests are forgotten once their season is finalised, keeping the one just finished")
    void pruningSeasonBests() {
        ClubBook book = twoClubs();
        book.recordClear(player(1), 10, "w", 1);
        book.recordClear(player(1), 20, "w", 2);
        book.recordClear(player(1), 30, "w", 3);
        book.pruneSeasonBests(2);
        assertEquals(0, book.seasonBestOf(1, player(1)));
        assertEquals(20, book.seasonBestOf(2, player(1)));
        assertEquals(30, book.seasonBestOf(3, player(1)));
    }

    @Test
    @DisplayName("season bests, honours and prestige banners survive a save and load")
    void clubStore() {
        TowerClubStore store = new TowerClubStore();
        ClubBook book = store.book();
        book.create(player(1), "Ash", "Tidal_Crew", "tc", 0L);
        book.recordClear(player(1), 55, "w", 2);
        book.honor(book.find("Tidal_Crew").orElseThrow(), "silver", "Season 1: second place");
        ClubBook restored = TowerClubStore.load(store.save(new CompoundTag(), null), null).book();
        assertEquals(55, restored.seasonBestOf(2, player(1)));
        ClubBook.Club club = restored.find("tidal_crew").orElseThrow();
        assertEquals(List.of("Season 1: second place"), club.honors());
        assertEquals(Set.of("silver"), club.unlockedBanners());
    }

    @Test
    @DisplayName("the Hall freezes the season's club board with the season and brings it back after a save and load")
    void hallClubs() {
        Key key = new Key(Board.SPEED, TIDE, Mode.SOLO).inSeason("s1");
        Map<Key, List<Entry>> boards = new LinkedHashMap<>();
        boards.put(key, List.of(new Entry(List.of(), 5, new UUID(1, 1), 0, 1, 1, 1, "d", 0L)));
        List<HallSeason.Club> clubs = new ArrayList<>();
        for (int i = 0; i < 12; i++) clubs.add(new HallSeason.Club("Club_" + i, "C" + i, "red", 100 - i, List.of("A" + i, "B" + i)));
        HallSeason plan = SeasonFinalizer.plan(new SeasonDefinition(1, "One", Optional.of(TIDE)), LocalDate.of(2026, 11, 15), boards, clubs);
        assertEquals(10, plan.clubs().size(), "the Hall keeps the top ten clubs");
        assertEquals("Club_0", plan.clubs().get(0).name());
        assertEquals(0, SeasonFinalizer.plan(new SeasonDefinition(1, "One", Optional.empty()), LocalDate.of(2026, 11, 15), boards).clubs().size(),
                "a plan made without clubs has none");

        TowerHallStore hall = new TowerHallStore();
        hall.add(plan);
        HallSeason read = TowerHallStore.load(hall.save(new CompoundTag(), null), null).get(1).orElseThrow();
        assertEquals(10, read.clubs().size());
        assertEquals(List.of("A3", "B3"), read.clubs().get(3).members());
        assertEquals(97, read.clubs().get(3).score());
    }

    // ---- the Echo pool --------------------------------------------------------------------------------------

    private static Echo echo(int n, int season) {
        return new Echo(new UUID(5, n), player(n), "P" + n, TIDE, new UUID(6, n), List.of("x level=5"), 0L, 0, 0, season);
    }

    @Test
    @DisplayName("a new season serves the previous season's Echoes until it has five of its own, then only its own")
    void echoPool() {
        List<Echo> echoes = new ArrayList<>(List.of(echo(1, 1), echo(2, 1), echo(3, 2)));
        List<Echo> serving = EchoPolicy.pool(echoes, 2);
        assertEquals(3, serving.size(), "season 2 has one of its own, so season 1's still serve");
        for (int n = 4; n <= 7; n++) echoes.add(echo(n, 2));
        List<Echo> own = EchoPolicy.pool(echoes, 2);
        assertEquals(5, own.size(), "five of its own and the Hall teams stand down");
        assertTrue(own.stream().allMatch(e -> e.season() == 2));
        assertTrue(EchoPolicy.pool(List.of(echo(9, 1)), 3).isEmpty(), "Echoes older than the previous season never serve");
    }

    @Test
    @DisplayName("with seasons off the pool is the all-time Echoes, and the first season is served by the Echoes made before it began")
    void echoPoolEdges() {
        List<Echo> echoes = List.of(echo(1, 0), echo(2, 0));
        assertEquals(2, EchoPolicy.pool(echoes, 0).size(), "seasons off: season 0 is the all-time pool");
        assertEquals(2, EchoPolicy.pool(echoes, 1).size(), "season 1 starts with the legacy Echoes serving");
        assertEquals(0, EchoPolicy.pool(echoes, 2).size(), "and they are gone by season 2");
        assertEquals(2, EchoPolicy.pool(List.of(echo(1, 1), echo(2, 2)), 2).size(), "one of each season: the previous serves while this has fewer than five");
    }

    @Test
    @DisplayName("a prune keeps this season's Echoes only while their run is in the top ten, and always keeps the previous season's")
    void echoPrune() {
        TowerEchoStore store = new TowerEchoStore();
        store.add(echo(1, 2));    // this season, run 6/1 on the board
        store.add(echo(2, 2));    // this season, fell off
        store.add(echo(3, 1));    // the previous season's: kept
        store.add(echo(4, 0));    // older than that: dropped
        assertEquals(2, store.prune(TIDE, 2, Set.of(new UUID(6, 1))));
        assertEquals(2, store.count());
        assertTrue(store.find(new UUID(5, 1)).isPresent());
        assertTrue(store.find(new UUID(5, 3)).isPresent(), "the Hall Echo stays");
        assertFalse(store.find(new UUID(5, 2)).isPresent());
        assertFalse(store.find(new UUID(5, 4)).isPresent());

        TowerEchoStore restored = TowerEchoStore.load(store.save(new CompoundTag(), null), null);
        assertEquals(2, restored.find(new UUID(5, 1)).orElseThrow().season(), "the season is part of what is saved");
    }

    @Test
    @DisplayName("Echo top runs are read from one season's boards at a time, and a season's runs never mix with another's or the all-time ones")
    void echoTopRunsBySeason() {
        Map<Key, List<Entry>> boards = new LinkedHashMap<>();
        Key all = new Key(Board.SPEED, TIDE, Mode.SOLO);
        boards.put(all, List.of(new Entry(List.of(), 1, new UUID(7, 0), 0, 0, 1, 1, "d", 0L)));
        boards.put(all.inSeason("s1"), List.of(new Entry(List.of(), 1, new UUID(7, 1), 0, 0, 1, 1, "d", 0L)));
        boards.put(all.inSeason("s2"), List.of(new Entry(List.of(), 1, new UUID(7, 2), 0, 0, 1, 1, "d", 0L)));
        assertEquals(Set.of(new UUID(7, 0)), EchoPolicy.topRuns(boards, TIDE));
        assertEquals(Set.of(new UUID(7, 1)), EchoPolicy.topRuns(boards, TIDE, "s1"));
        assertEquals(Set.of(new UUID(7, 2)), EchoPolicy.topRuns(boards, TIDE, "s2"));
    }
}
