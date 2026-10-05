package com.cobbletowers.echo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.encounter.EncounterSnapshot;
import com.cobbletowers.mastery.LeaderboardRules.Board;
import com.cobbletowers.mastery.LeaderboardRules.Entry;
import com.cobbletowers.mastery.LeaderboardRules.Key;
import com.cobbletowers.mastery.LeaderboardRules.Mode;
import com.cobbletowers.persistence.TowerEchoStore;
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

/** Echoes (P35): who is in the top ten, when a slot is an Echo's, and the store that keeps them. */
class EchoPolicyTest {

    private static final ResourceLocation TIDE = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale");

    private static Entry entry(UUID run, long value) {
        return new Entry(List.of(), value, run, 0, 0, 1, 1, "d", 0L);
    }

    private static Echo echo(int n, UUID owner, ResourceLocation tower, UUID run, String... team) {
        return new Echo(new UUID(0, n), owner, "Player" + n, tower, run, List.of(team), 0L, 0);
    }

    @Test
    @DisplayName("only the top ten of a counting board on that tower count, and the Clears board (no run) never does")
    void topRuns() {
        Map<Key, List<Entry>> boards = new LinkedHashMap<>();
        List<Entry> speed = new ArrayList<>();
        for (int i = 0; i < 12; i++) speed.add(entry(new UUID(1, i), i));
        boards.put(new Key(Board.SPEED, TIDE, Mode.SOLO, ""), speed);
        boards.put(new Key(Board.DIFFICULTY, ROOT, Mode.SOLO, ""), List.of(entry(new UUID(2, 0), 5)));
        boards.put(new Key(Board.CLEARS, TIDE, Mode.ANY, ""), List.of(entry(null, 5)));

        Set<UUID> runs = EchoPolicy.topRuns(boards, TIDE);
        assertEquals(10, runs.size());
        assertTrue(runs.contains(new UUID(1, 9)));
        assertFalse(runs.contains(new UUID(1, 10)), "eleventh is out");
        assertFalse(runs.contains(new UUID(2, 0)), "another tower's run is not this tower's");
    }

    @Test
    @DisplayName("a pick never shows a player their own team, is deterministic, and is empty when nothing is eligible")
    void pick() {
        UUID me = new UUID(5, 5);
        List<Echo> pool = List.of(echo(1, me, TIDE, new UUID(9, 1), "mine level=50"),
                echo(2, new UUID(6, 6), TIDE, new UUID(9, 2), "garchomp level=50", "gengar level=50"));
        for (long seed = 0; seed < 50; seed++) {
            EchoPolicy.Pick pick = EchoPolicy.pick(pool, Set.of(me), seed, 2, 0).orElseThrow();
            assertEquals(new UUID(0, 2), pick.echo().id());
            assertEquals(pick, EchoPolicy.pick(pool, Set.of(me), seed, 2, 0).orElseThrow());
        }
        assertTrue(EchoPolicy.pick(List.of(pool.get(0)), Set.of(me), 1L, 1, 0).isEmpty());
        assertTrue(EchoPolicy.pick(List.of(), Set.of(), 1L, 1, 0).isEmpty());
    }

    @Test
    @DisplayName("an Echo fights at the floor's level, whatever its own was, and the fallback is its bare species")
    void level() {
        assertEquals("garchomp level=22 nature=jolly moves=a,b",
                EchoPolicy.atLevel("garchomp level=100 nature=jolly moves=a,b", 22));
        assertEquals("garchomp nature=jolly level=30", EchoPolicy.atLevel("garchomp nature=jolly", 30));
        assertEquals("garchomp", EchoPolicy.speciesOf("garchomp level=1 nature=jolly"));

        EncounterSnapshot snapshot = new EncounterSnapshot(0, ResourceLocation.fromNamespaceAndPath("cobblemon", "machoke"),
                List.of(), 25).withEcho("garchomp level=100 nature=jolly held_item=cobblemon:rocky_helmet", "Ash");
        assertEquals("garchomp level=25 nature=jolly held_item=cobblemon:rocky_helmet", snapshot.toProperties());
        assertEquals("garchomp level=25", snapshot.toProperties(false));
        assertEquals(Optional.of("Ash"), snapshot.echoOwner());
    }

    @Test
    @DisplayName("Echoes apply to regional towers outside a trial only")
    void applies() {
        assertTrue(EchoPolicy.applies(true, false));
        assertFalse(EchoPolicy.applies(false, false));
        assertFalse(EchoPolicy.applies(true, true));
    }

    @Test
    @DisplayName("the store keeps one Echo per player per tower, drops those that left the top ten, and honours an opt-out at once")
    void store() {
        TowerEchoStore store = new TowerEchoStore();
        UUID a = new UUID(1, 1);
        UUID b = new UUID(2, 2);
        store.add(echo(1, a, TIDE, new UUID(9, 1), "x level=5"));
        store.add(echo(2, a, TIDE, new UUID(9, 2), "y level=5"));
        assertEquals(1, store.forTower(TIDE).size(), "the older Echo of the same player on the same tower is replaced");
        store.add(echo(3, a, ROOT, new UUID(9, 3), "z level=5"));
        store.add(echo(4, b, TIDE, new UUID(9, 4), "w level=5"));
        assertEquals(3, store.count());

        assertEquals(1, store.prune(TIDE, 0, Set.of(new UUID(9, 4))), "a's tideforge run left the top ten");
        assertEquals(2, store.count());
        assertEquals(1, store.ownedBy(a).size(), "its rootvale Echo is untouched by a tideforge prune");

        store.recordFaced(new UUID(0, 4));
        store.recordResult(new UUID(0, 4), true);
        store.recordResult(new UUID(0, 4), false);
        CompoundTag saved = store.save(new CompoundTag(), null);
        TowerEchoStore restored = TowerEchoStore.load(saved, null);
        assertEquals(2, restored.count());
        assertEquals(1, restored.find(new UUID(0, 4)).orElseThrow().faced());
        assertEquals(1, restored.find(new UUID(0, 4)).orElseThrow().beat(), "only a duel the Echo won counts as a win");

        assertEquals(1, restored.setOptedOut(a, true), "opting out removes their Echo now");
        assertTrue(restored.isOptedOut(a));
        assertEquals(0, restored.ownedBy(a).size());
        assertTrue(TowerEchoStore.load(restored.save(new CompoundTag(), null), null).isOptedOut(a));
    }
}
