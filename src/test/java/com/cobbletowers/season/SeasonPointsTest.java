package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.SeasonTrackDefinition;
import com.cobbletowers.persistence.TowerSeasonProgressStore;
import com.cobbletowers.season.SeasonPoints.Progress;
import com.cobbletowers.season.SeasonPoints.Result;
import com.cobbletowers.season.SeasonPoints.Source;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Season points, their caps, the track, and the store that keeps them (P36b). */
class SeasonPointsTest {

    private static final String D1 = "2026-10-12";
    private static final String D2 = "2026-10-13";
    private static final String W1 = "2026-w42";
    private static final String W2 = "2026-w43";

    private static Progress award(Progress progress, Source source, int param, boolean spotlight, String day, String week) {
        return SeasonPoints.award(progress, source, param, spotlight, day, week).next();
    }

    private static int granted(Progress progress, Source source, int param, boolean spotlight, String day, String week) {
        return SeasonPoints.award(progress, source, param, spotlight, day, week).granted();
    }

    @Test
    @DisplayName("a regional clear is worth 20, or 30 in the spotlight region, and at most three count a day")
    void regionalClears() {
        Progress p = Progress.EMPTY;
        assertEquals(20, granted(p, Source.REGIONAL_CLEAR, 0, false, D1, W1));
        assertEquals(30, granted(p, Source.REGIONAL_CLEAR, 0, true, D1, W1));
        for (int i = 0; i < 3; i++) p = award(p, Source.REGIONAL_CLEAR, 0, false, D1, W1);
        assertEquals(60, p.total());
        Result fourth = SeasonPoints.award(p, Source.REGIONAL_CLEAR, 0, false, D1, W1);
        assertEquals(0, fourth.granted(), "a fourth clear in a day counts for nothing");
        assertEquals(p, fourth.next());
        assertEquals(20, granted(p, Source.REGIONAL_CLEAR, 0, false, D2, W1), "and the next day starts the count over");
    }

    @Test
    @DisplayName("the daily sources together stop at 80 a day, and the last award is trimmed to fit rather than refused")
    void dailyCap() {
        Progress p = Progress.EMPTY;
        p = award(p, Source.REGIONAL_CLEAR, 0, true, D1, W1);   // 30
        p = award(p, Source.REGIONAL_CLEAR, 0, true, D1, W1);   // 60
        p = award(p, Source.DAILY_TRIAL, 0, false, D1, W1);     // 75
        assertEquals(75, p.total());
        Result contract = SeasonPoints.award(p, Source.DAILY_CONTRACT, 0, false, D1, W1);
        assertEquals(5, contract.granted(), "5 more fits exactly to 80");
        Result more = SeasonPoints.award(contract.next(), Source.ECHO_DUEL, 0, false, D1, W1);
        assertEquals(0, more.granted(), "the cap is reached");
        Result trimmed = SeasonPoints.award(award(Progress.EMPTY, Source.REGIONAL_CLEAR, 0, true, D1, W1), Source.REGIONAL_CLEAR, 0, true, D1, W1);
        assertEquals(60, trimmed.next().total());
        Result near = SeasonPoints.award(new Progress(0, 0, D1, 70, java.util.Map.of(), Set.of()), Source.REGIONAL_CLEAR, 0, true, D1, W1);
        assertEquals(10, near.granted(), "30 would pass the cap, so only the 10 that fit are added");
    }

    @Test
    @DisplayName("the weekly sources and streak milestones sit outside the daily cap, and count once a week or once each")
    void weeklyAndStreak() {
        Progress p = new Progress(0, 0, D1, 80, java.util.Map.of(), Set.of());
        assertEquals(40, granted(p, Source.WEEKLY_TRIAL, 0, false, D1, W1), "a full day does not block the weekly trial");
        p = award(p, Source.WEEKLY_TRIAL, 0, false, D1, W1);
        assertEquals(0, granted(p, Source.WEEKLY_TRIAL, 0, false, D2, W1), "but it counts once a week");
        assertEquals(40, granted(p, Source.WEEKLY_TRIAL, 0, false, D2, W2), "and again the next week");

        assertEquals(20, granted(p, Source.WEEKLY_CONTRACT, 0, false, D1, W1));
        assertEquals(20, granted(p, Source.CLUB_CLAIM, 0, false, D1, W1));

        p = award(p, Source.STREAK_MILESTONE, 7, false, D1, W1);
        assertEquals(40 + 20, p.total());
        assertEquals(0, granted(p, Source.STREAK_MILESTONE, 7, false, D2, W2), "a milestone counts once this season");
        assertEquals(10, SeasonPoints.streakPoints(3));
        assertEquals(20, SeasonPoints.streakPoints(7));
        assertEquals(30, SeasonPoints.streakPoints(14));
        assertEquals(50, SeasonPoints.streakPoints(30));
        assertEquals(50, SeasonPoints.streakPoints(100));
        assertEquals(0, SeasonPoints.streakPoints(5), "only real milestones are worth anything");
    }

    @Test
    @DisplayName("the tally records the day it was counted on, so a new day starts the daily count and total again")
    void newDay() {
        Progress p = award(Progress.EMPTY, Source.DAILY_TRIAL, 0, false, D1, W1);
        assertEquals(D1, p.day());
        assertEquals(15, p.dayTotal());
        Progress next = award(p, Source.DAILY_TRIAL, 0, false, D2, W1);
        assertEquals(D2, next.day());
        assertEquals(15, next.dayTotal(), "the day total restarted");
        assertEquals(30, next.total(), "while the season total kept growing");
    }

    // ---- the track ----------------------------------------------------------------------------------

    private static SeasonTrackDefinition shipped() throws IOException {
        try (Reader reader = Files.newBufferedReader(Paths.get(
                "src/main/resources/data/cobbletowers/cobbletowers/season_tracks/default.json"))) {
            return SeasonTrackDefinition.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    @Test
    @DisplayName("the shipped track is 30 steps of 75 points, 2,250 in all, and steps are reached in whole multiples")
    void track() throws IOException {
        SeasonTrackDefinition track = shipped();
        assertEquals(30, track.stepCount());
        assertEquals(75, track.stepCost());
        assertEquals(2250, track.totalPoints());
        assertEquals(0, track.stepsFor(74));
        assertEquals(1, track.stepsFor(75));
        assertEquals(15, track.stepsFor(1140), "a casual player's six weeks reaches about halfway");
        assertEquals(22, track.stepsFor(1680));
        assertEquals(30, track.stepsFor(2250));
        assertEquals(30, track.stepsFor(99999), "never more than the track has");
        assertEquals(0, track.stepsFor(-5));
    }

    @Test
    @DisplayName("the shipped rewards match the design: its totals, the banners and titles, and the finale is four trim templates")
    void trackContents() throws IOException {
        SeasonTrackDefinition track = shipped();
        int dollars = 0;
        int raidPoints = 0;
        int packs = 0;
        int candy = 0;
        for (SeasonTrackDefinition.Step step : track.steps()) {
            for (SeasonTrackDefinition.Grant grant : step.grants()) {
                switch (grant.item()) {
                    case "cobbletowers:cobble_dollar" -> dollars += grant.amount();
                    case "cobbleraids:raid_points" -> raidPoints += grant.amount();
                    case "cobblemon-cards:booster_pack" -> packs += grant.amount();
                    case "cobblemon:exp_candy_xl" -> candy += grant.amount();
                    default -> { }
                }
            }
        }
        assertEquals(1050, dollars);
        assertEquals(200, raidPoints);
        assertEquals(5, packs);
        assertEquals(7, candy);
        long banners = track.steps().stream().flatMap(step -> step.cosmetics().stream()).filter(name -> name.startsWith("banner_")).count();
        assertEquals(3, banners);
        SeasonTrackDefinition.Step finale = track.steps().get(29);
        assertTrue(finale.cosmetics().containsAll(java.util.List.of("title_champion", "badge")));
        assertEquals(1, finale.grants().size());
        assertEquals("cobbletowers:season_trim_template_{season}", finale.grants().get(0).item());
        assertEquals(4, finale.grants().get(0).amount(), "one template for each armor piece");
        assertTrue(track.steps().get(23).cosmetics().contains("title_challenger"));
    }

    // ---- the store ------------------------------------------------------------------------------------

    @Test
    @DisplayName("points belong to one season, cosmetics are forever, and both survive a save and load")
    void store() {
        TowerSeasonProgressStore store = new TowerSeasonProgressStore();
        UUID player = new UUID(7, 7);
        Progress p = award(award(Progress.EMPTY, Source.REGIONAL_CLEAR, 0, false, D1, W1), Source.WEEKLY_TRIAL, 0, false, D1, W1);
        store.put(player, 1, p.withSteps(0));
        store.addCosmetics(player, Set.of("s1:banner_1"));
        assertEquals(60, store.of(player, 1).total());
        assertEquals(Progress.EMPTY, store.of(player, 2), "another season has none of it");

        TowerSeasonProgressStore restored = TowerSeasonProgressStore.load(store.save(new CompoundTag(), null), null);
        Progress read = restored.of(player, 1);
        assertEquals(60, read.total());
        assertEquals(D1, read.day());
        assertEquals(1, read.dayCounts().get(Source.REGIONAL_CLEAR));
        assertTrue(read.once().contains("WEEKLY_TRIAL:" + W1), "the once-a-week guard is saved, so a restart cannot count it twice");
        assertEquals(Set.of("s1:banner_1"), restored.cosmeticsOf(player));
        restored.put(player, 2, Progress.EMPTY);
        assertEquals(Set.of("s1:banner_1"), restored.cosmeticsOf(player), "a new season never touches earned cosmetics");
    }
}
