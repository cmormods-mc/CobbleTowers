package com.cobbletowers.instance;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

class DeferredRemovalQueueTest {
    /** The real Minecraft manager owns the section and removal callback; only the entity is synthetic. */
    private static final class TestEntity implements EntityAccess {
        final int id;
        final UUID uuid = UUID.randomUUID();
        EntityInLevelCallback callback = EntityInLevelCallback.NULL;
        TestEntity(int id) { this.id = id; }
        public int getId() { return id; }
        public UUID getUUID() { return uuid; }
        public BlockPos blockPosition() { return new BlockPos(1, 64, 1); }
        public AABB getBoundingBox() { return new AABB(1, 64, 1, 2, 65, 2); }
        public void setLevelCallback(EntityInLevelCallback value) { callback = value; }
        public Stream<TestEntity> getSelfAndPassengers() { return Stream.of(this); }
        public Stream<TestEntity> getPassengersAndSelf() { return Stream.of(this); }
        public void setRemoved(Entity.RemovalReason reason) { callback.onRemove(reason); }
        public boolean shouldBeSaved() { return true; }
        public boolean isAlwaysTicking() { return false; }
    }

    private static PersistentEntitySectionManager<TestEntity> manager(java.util.function.Consumer<TestEntity> loaded) {
        var callbacks = new LevelCallback<TestEntity>() {
            public void onCreated(TestEntity e) {}
            public void onDestroyed(TestEntity e) {}
            public void onTickingStart(TestEntity e) {}
            public void onTickingEnd(TestEntity e) {}
            public void onTrackingEnd(TestEntity e) {}
            public void onSectionChange(TestEntity e) {}
            public void onTrackingStart(TestEntity e) { loaded.accept(e); }
        };
        var storage = new EntityPersistentStorage<TestEntity>() {
            public CompletableFuture<ChunkEntities<TestEntity>> loadEntities(ChunkPos p) {
                return CompletableFuture.completedFuture(new ChunkEntities<>(p, List.of()));
            }
            public void storeEntities(ChunkEntities<TestEntity> entities) {}
            public void flush(boolean sync) {}
        };
        var manager = new PersistentEntitySectionManager<>(TestEntity.class, callbacks, storage);
        for (int i = 0; i < 4; i++) manager.addNewEntity(new TestEntity(i));
        return manager;
    }

    @Test
    void immediateRemovalReproducesReportedTrackingFailure() {
        var manager = manager(e -> e.setRemoved(Entity.RemovalReason.DISCARDED));
        var failure = assertThrows(ConcurrentModificationException.class,
                () -> manager.updateChunkStatus(new ChunkPos(0, 0), Visibility.TRACKED));
        assertTrue(Arrays.stream(failure.getStackTrace()).anyMatch(frame ->
                frame.getClassName().endsWith("PersistentEntitySectionManager") && frame.getLineNumber() == 256));
    }

    @Test
    void trackingCompletesBeforeQueuedEntitiesAreRemoved() {
        var pending = new DeferredRemovalQueue<TestEntity>();
        var manager = manager(pending::add);
        assertDoesNotThrow(() -> manager.updateChunkStatus(new ChunkPos(0, 0), Visibility.TRACKED));
        assertEquals(4, manager.count(), "load callbacks must not remove anything from the live section");
        pending.drain(256, e -> true, e -> e.setRemoved(Entity.RemovalReason.DISCARDED));
        assertEquals(0, manager.count(), "debris is still removed after tracking finishes");
    }

    @Test
    void deduplicatesBoundsWorkAndRechecksEligibility() {
        var pending = new DeferredRemovalQueue<String>();
        pending.add("player-owned");
        pending.add("debris");
        pending.add("debris");
        pending.add("later");
        var removed = new ArrayList<String>();
        pending.drain(2, value -> !value.equals("player-owned"), removed::add);
        assertEquals(List.of("debris"), removed);
        pending.drain(2, value -> true, removed::add);
        assertEquals(List.of("debris", "later"), removed);
    }

    @Test
    void callbackAdditionsWaitAndShutdownDropsReferences() {
        var pending = new DeferredRemovalQueue<String>();
        pending.add("first");
        var removed = new ArrayList<String>();
        pending.drain(256, value -> true, value -> {
            removed.add(value);
            pending.add("next-tick");
        });
        assertEquals(List.of("first"), removed);
        pending.clear();
        pending.drain(256, value -> true, removed::add);
        assertEquals(List.of("first"), removed);
    }
}
