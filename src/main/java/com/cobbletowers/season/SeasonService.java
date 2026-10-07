package com.cobbletowers.season;

import com.cobbletowers.TowerLog;
import com.cobbletowers.definition.SeasonDefinition;
import com.cobbletowers.definition.TowerDefinition;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.MasteryView;
import com.cobbletowers.persistence.TowerHallStore;
import com.cobbletowers.persistence.TowerLeaderboardStore;
import com.cobbletowers.persistence.TowerSeasonStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Seasons at run time (P36a): announces a start, finalises an ended season (Hall entry, pruning, announcement) and
 * words the views. Rules are {@link SeasonSchedule} and {@link SeasonFinalizer}. Finalisation stores progress after
 * each of its three steps, so a crash resumes at the next.
 */
public final class SeasonService {

    private static final int CHECK_EVERY_TICKS = 20;
    private static int tickCounter;

    private SeasonService() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter % CHECK_EVERY_TICKS != 0) return;
            try {
                check(server);
            } catch (RuntimeException ex) {
                TowerLog.error("A season check failed", ex);
            }
        });
    }

    /**
     * One look at the calendar: announce a start, finalise anything that has ended. Idempotent, so any number of
     * calls is safe.
     */
    public static void check(MinecraftServer server) {
        if (!Seasons.enabled()) return;
        TowerSeasonStore store = TowerSeasonStore.get(server);
        Seasons.phase().ifPresent(phase -> {
            if (phase instanceof SeasonSchedule.Active active && active.number() > store.lastAnnouncedStart()) {
                store.announcedStart(active.number());
                store.checkpoint(server);
                SeasonDefinition definition = Seasons.definition(active.number());
                server.getPlayerList().broadcastSystemMessage(Component.literal("Season " + active.number() + ": "
                        + definition.name() + " has begun! Six weeks, new boards. /tower season for the details."), false);
                TowerLog.info("Season {} ({}) began", active.number(), definition.name());
            }
        });
        finalizePending(server, false);
    }

    // ---- finalisation --------------------------------------------------------------------------------

    /**
     * Finalises every ended, unfinalised season, oldest first. With {@code dry} nothing is written; the lines say
     * what would be.
     */
    public static List<String> finalizePending(MinecraftServer server, boolean dry) {
        List<String> lines = new ArrayList<>();
        TowerSeasonStore store = TowerSeasonStore.get(server);
        int ended = Seasons.lastEnded();
        for (int number = store.lastFinalized() + 1; number <= ended; number++) {
            lines.addAll(finalizeOne(server, number, number == ended, dry));
        }
        if (lines.isEmpty() && dry) lines.add("Nothing to finalise: no season has ended since season " + store.lastFinalized() + ".");
        return lines;
    }

    private static List<String> finalizeOne(MinecraftServer server, int number, boolean latest, boolean dry) {
        TowerSeasonStore store = TowerSeasonStore.get(server);
        TowerHallStore hall = TowerHallStore.get(server);
        TowerLeaderboardStore boards = TowerLeaderboardStore.get(server);
        SeasonDefinition definition = Seasons.definition(number);
        LocalDate endedOn = SeasonSchedule.lastDayOf(Seasons.config().anchor(), number);
        HallSeason plan = SeasonFinalizer.plan(definition, endedOn, boards.all(),
                com.cobbletowers.club.ClubService.hallClubs(server, number));

        if (dry) {
            List<String> lines = new ArrayList<>();
            lines.add("Season " + number + " (" + definition.name() + ", ended " + endedOn + "): would record "
                    + plan.boards().size() + " board(s) in the Hall"
                    + (hall.has(number) ? " (already there: left alone)" : "") + ", prune seasonal boards older than s"
                    + number + ", and mark it finalised.");
            for (HallSeason.Board board : plan.boards()) {
                Entry top = board.entries().get(0);
                lines.add("  " + boardTitle(board) + ": " + board.entries().size() + " entries, leader "
                        + MasteryView.row(board.key().board(), 1, top));
            }
            if (!plan.clubs().isEmpty()) {
                lines.add("  Club board: " + plan.clubs().size() + " club(s), champion " + plan.clubs().get(0).name() + " ("
                        + plan.clubs().get(0).score() + "); the top " + Math.min(3, plan.clubs().size())
                        + " would unlock the gold, silver and bronze banners");
            }
            return lines;
        }

        int done = store.stepsDone(number);
        if (done > 0) TowerLog.info("Season {} finalisation resumes after step {}", number, done);
        if (done < 1) {
            if (hall.add(plan)) hall.checkpoint(server);
            store.stepDone(number, 1);
            store.checkpoint(server);
            TowerLog.info("Season {} finalisation: step 1 done (the Hall season is written)", number);
            testOnlyCrashAfter(1);
        }
        if (done < 2) {
            // The podium is read from what the Hall froze in step 1, so a resume awards the same clubs whatever
            // changed since.
            List<String> podium = TowerHallStore.get(server).get(number).map(season -> season.clubs().stream()
                    .map(HallSeason.Club::name).toList()).orElse(List.of());
            com.cobbletowers.club.ClubService.awardSeason(server, number, podium);
            boards.prune(key -> SeasonFinalizer.staleSeasonKey(key, number));
            boards.checkpoint(server);
            store.stepDone(number, 2);
            store.checkpoint(server);
            TowerLog.info("Season {} finalisation: step 2 done (old seasonal boards pruned)", number);
            testOnlyCrashAfter(2);
        }
        if (done < 3) {
            store.finalized(number);
            store.checkpoint(server);
            TowerLog.info("Season {} ({}) finalised: {} board(s) in the Hall", number, definition.name(), plan.boards().size());
            if (latest) {
                server.getPlayerList().broadcastSystemMessage(Component.literal("Season " + number + ": " + definition.name()
                        + " has ended. Its winners are in the Hall of Fame: /tower hall " + number
                        + ". The next season starts after a week off."), false);
            }
        }
        return List.of("Season " + number + " finalised.");
    }

    /**
     * Test seam: with {@code -Dcobbletowers.testOnlyCrashAfterSeasonStep=N} the JVM halts right after finalisation
     * step N is written, like a real crash. Never set in production.
     */
    private static void testOnlyCrashAfter(int step) {
        if (Integer.getInteger("cobbletowers.testOnlyCrashAfterSeasonStep", 0) == step) {
            TowerLog.warn("TEST ONLY: halting the JVM after season finalisation step {}", step);
            Runtime.getRuntime().halt(137);
        }
    }

    // ---- words ---------------------------------------------------------------------------------------

    /** What {@code /tower season} says. */
    public static List<String> status() {
        List<String> lines = new ArrayList<>();
        if (!Seasons.enabled()) return List.of("Seasons are switched off on this server: the boards are all-time.");
        SeasonSchedule.Phase phase = Seasons.phase().orElseThrow();
        if (phase instanceof SeasonSchedule.Before before) {
            lines.add("The first season begins on " + before.anchor() + " (" + before.daysUntil() + " day(s)). "
                    + Seasons.definition(1).name() + " opens it.");
        } else if (phase instanceof SeasonSchedule.Active active) {
            SeasonDefinition definition = Seasons.definition(active.number());
            lines.add("Season " + active.number() + ": " + definition.name() + " - week " + active.week() + " of "
                    + SeasonSchedule.WEEKS + ", " + active.daysLeft() + " day(s) left (ends after " + active.lastDay() + ").");
            definition.spotlight().ifPresent(region -> lines.add("Spotlight region: " + regionName(region.toString())
                    + ". Boards: /tower leaderboard <board> shows this season; add alltime for the lifetime board."));
        } else if (phase instanceof SeasonSchedule.OffSeason off) {
            SeasonDefinition ended = Seasons.definition(off.endedNumber());
            lines.add("Off-season: season " + off.endedNumber() + " (" + ended.name() + ") has ended. Its winners: /tower hall "
                    + off.endedNumber() + ".");
            lines.add("Season " + off.nextNumber() + ": " + Seasons.definition(off.nextNumber()).name() + " begins on "
                    + off.nextStartDay() + " (" + off.daysLeft() + " day(s)). Results post to the all-time boards meanwhile.");
        }
        return lines;
    }

    /** What {@code /tower hall} says: the winners of one season (the latest if none is named). */
    public static List<String> hall(MinecraftServer server, Optional<Integer> number) {
        TowerHallStore hall = TowerHallStore.get(server);
        Optional<HallSeason> found = number.isPresent() ? hall.get(number.get()) : hall.latest();
        if (found.isEmpty()) {
            return List.of(number.isPresent() ? "Season " + number.get() + " is not in the Hall of Fame."
                    : "The Hall of Fame is empty: no season has finished yet.");
        }
        HallSeason season = found.get();
        List<String> lines = new ArrayList<>();
        lines.add("Hall of Fame - Season " + season.number() + ": " + season.name() + " (ended " + season.endedOn() + ")");
        if (season.boards().isEmpty()) lines.add("  No results were posted that season.");
        for (HallSeason.Board board : season.boards()) {
            lines.add("  " + boardTitle(board));
            for (int i = 0; i < Math.min(3, board.entries().size()); i++) {
                lines.add("    " + MasteryView.row(board.key().board(), i + 1, board.entries().get(i)));
            }
        }
        if (!season.clubs().isEmpty()) {
            lines.add("  Club board");
            for (int i = 0; i < Math.min(3, season.clubs().size()); i++) {
                HallSeason.Club club = season.clubs().get(i);
                lines.add("    #" + (i + 1) + " [" + club.tag() + "] " + club.name() + "  " + club.score() + "  ("
                        + String.join(", ", club.members()) + ")");
            }
        }
        return lines;
    }

    static String boardTitle(HallSeason.Board board) {
        Board kind = board.key().board();
        TowerDefinition tower = TowerDefinitionRegistry.content().towers().get(board.key().tower());
        return kind.title() + " - " + (tower == null ? board.key().tower().toString() : tower.displayName())
                + (kind.hasMode() ? " (" + (board.key().mode().name().charAt(0) + board.key().mode().name().substring(1).toLowerCase()) + ")" : "")
                + (board.key().playlist().isEmpty() ? "" : " [" + board.key().playlist() + "]");
    }

    private static String regionName(String id) {
        return TowerDefinitionRegistry.content().towers().entrySet().stream()
                .filter(tower -> tower.getKey().toString().equals(id)).map(tower -> tower.getValue().displayName())
                .findFirst().orElse(id);
    }
}
