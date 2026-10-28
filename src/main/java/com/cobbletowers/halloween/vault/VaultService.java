package com.cobbletowers.halloween.vault;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the Jack-o'-Lantern Vaults (the Halloween trick-or-treat reward chest): spawns the display entities, advances each vault's
 * {@link VaultMachine} once per server tick on the main thread and plays the manifest's sound and particle events. The reward and the
 * encounter are NOT decided here: {@code reward_trigger}, {@code encounter_trigger} and the rest go to the registered listeners, so the
 * tower code that owns the outcome stays the only thing that grants or spawns.
 *
 * <p>Vaults are keyed by UUID and keep a dimension key and a position, never a level reference. A vault whose chunk is not loaded pauses.
 * Looping states (closed idle, open hold) send a pose every {@value #IDLE_TICKS} ticks, active states every tick. Vaults are runtime-only:
 * they are not saved, the owner (the tower floor) spawns them when it builds the room.
 */
public final class VaultService {

    static final int IDLE_TICKS = 5;

    private static final Map<UUID, Vault> VAULTS = new LinkedHashMap<>();
    private static final List<BiConsumer<UUID, VaultEvent>> LISTENERS = new ArrayList<>();
    private static VaultManifest manifest;
    private static VaultReveal reveal;
    private static boolean installed;

    private VaultService() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        VaultModels.bootstrap();
        manifest = VaultManifest.load();
        reveal = new VaultReveal(manifest.animation());
        ServerTickEvents.END_SERVER_TICK.register(VaultService::tick);
        ServerState.onStop(VaultService::clear);
    }

    /** Called for every non-cosmetic event: reward_trigger, encounter_trigger (and close_request, reveal_spawn, reveal_collect after they ran). */
    public static void addListener(BiConsumer<UUID, VaultEvent> listener) {
        LISTENERS.add(listener);
    }

    /** Spawns a vault whose root (the floor centre of its footprint) is the middle of the block at {@code floor}, at that block's bottom edge. */
    public static UUID spawn(ServerLevel level, BlockPos floor, VaultFacing facing) {
        UUID id = UUID.randomUUID();
        Vec3 root = new Vec3(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        VAULTS.put(id, new Vault(id, level.dimension(), root, facing, new VaultDisplay(level, root, facing), manifest.newMachine()));
        return id;
    }

    public static boolean start(UUID id, VaultMachine.Outcome outcome) {
        Vault v = VAULTS.get(id);
        return v != null && v.machine.start(outcome);
    }

    public static boolean close(UUID id) {
        Vault v = VAULTS.get(id);
        return v != null && v.machine.close();
    }

    /** Any state to closed at rest, reveal candy removed. */
    public static boolean reset(UUID id) {
        Vault v = VAULTS.get(id);
        if (v == null) return false;
        v.reset();
        return true;
    }

    public static boolean remove(UUID id) {
        Vault v = VAULTS.remove(id);
        if (v == null) return false;
        v.display.destroy();
        return true;
    }

    public static VaultMachine.State state(UUID id) {
        Vault v = VAULTS.get(id);
        return v == null ? null : v.machine.state();
    }

    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Vault v : VAULTS.values()) {
            out.add(String.format(Locale.ROOT, "%s %s at %.1f %.1f %.1f facing %s: %s tick %d outcome %s", v.id, v.dimension.location(), v.root.x, v.root.y,
                    v.root.z, v.facing.name().toLowerCase(Locale.ROOT), v.machine.state(), v.machine.stateTick(), v.machine.outcome()));
        }
        return out;
    }

    private static void clear() {
        for (Vault v : VAULTS.values()) v.display.destroy();
        VAULTS.clear();
    }

    private static void tick(MinecraftServer server) {
        if (VAULTS.isEmpty()) return;
        for (Vault v : new ArrayList<>(VAULTS.values())) {
            try {
                ServerLevel level = server.getLevel(v.dimension);
                if (level == null || !level.isLoaded(BlockPos.containing(v.root))) continue;
                v.step(level);
            } catch (RuntimeException ex) {
                TowerLog.error("Vault " + v.id + " failed this tick; removing it", ex);
                remove(v.id);
            }
        }
    }

    private static final class Vault {
        final UUID id;
        final ResourceKey<Level> dimension;
        final Vec3 root;
        final VaultFacing facing;
        final VaultDisplay display;
        final VaultMachine machine;
        /** Ticks since the reveal candy spawned, or -1; and the tick the collect began, or -1. */
        int revealTicks = -1, collectStart = -1;

        Vault(UUID id, ResourceKey<Level> dimension, Vec3 root, VaultFacing facing, VaultDisplay display, VaultMachine machine) {
            this.id = id;
            this.dimension = dimension;
            this.root = root;
            this.facing = facing;
            this.display = display;
            this.machine = machine;
        }

        void reset() {
            machine.reset();
            revealTicks = collectStart = -1;
            display.clearReveals();
            display.apply(VaultPose.REST, 1);
        }

        void step(ServerLevel level) {
            VaultMachine.Frame frame = machine.tick();
            boolean loopState = frame.state() == VaultMachine.State.CLOSED || frame.state() == VaultMachine.State.OPEN;
            if (!loopState || frame.stateTick() % IDLE_TICKS == 0) {
                display.apply(frame.pose(), loopState && frame.stateTick() > 0 ? IDLE_TICKS : 1);
            }
            for (VaultEvent e : frame.events()) run(level, e);
            if (revealTicks >= 0) {
                if (frame.state() != VaultMachine.State.OPEN && frame.state() != VaultMachine.State.CLOSING) {
                    display.clearReveals();
                    revealTicks = collectStart = -1;
                } else {
                    double collectScale = 1;
                    if (collectStart >= 0) collectScale = Math.max(0, 1.0 - (double) (revealTicks - collectStart) / reveal.collectTicks());
                    for (int i = 0; i < display.revealCount(); i++) display.applyReveal(i, reveal.state(i, revealTicks), collectScale);
                    revealTicks++;
                    if (collectStart >= 0 && revealTicks - collectStart > reveal.collectTicks()) {
                        display.clearReveals();
                        revealTicks = collectStart = -1;
                    }
                }
            }
        }

        void run(ServerLevel level, VaultEvent e) {
            JsonObject d = e.data();
            switch (e.type()) {
                case "sound" -> {
                    Vec3 at = anchor(d.get("at").getAsString());
                    ResourceLocation sound = ResourceLocation.parse(d.get("id").getAsString());
                    BuiltInRegistries.SOUND_EVENT.getOptional(sound).ifPresentOrElse(
                            (SoundEvent s) -> level.playSound(null, at.x, at.y, at.z, s, SoundSource.BLOCKS, d.get("volume").getAsFloat(), d.get("pitch").getAsFloat()),
                            () -> TowerLog.warn("Vault event names an unknown sound {}", sound));
                }
                case "particle" -> {
                    Vec3 at = anchor(d.get("at").getAsString());
                    ResourceLocation id = ResourceLocation.parse(d.get("id").getAsString());
                    var type = BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
                    if (type instanceof ParticleOptions options) {
                        var spread = d.getAsJsonArray("spread");
                        level.sendParticles(options, at.x, at.y, at.z, d.get("count").getAsInt(), spread.get(0).getAsDouble(), spread.get(1).getAsDouble(),
                                spread.get(2).getAsDouble(), d.get("speed").getAsDouble());
                    } else {
                        TowerLog.warn("Vault event names an unusable particle {}", id);
                    }
                }
                case "close_request" -> {
                    machine.close();
                    notifyListeners(e);
                }
                case "reveal_spawn" -> {
                    display.spawnReveals();
                    revealTicks = 0;
                    collectStart = -1;
                    notifyListeners(e);
                }
                case "reveal_collect" -> {
                    if (revealTicks >= 0) collectStart = revealTicks;
                    notifyListeners(e);
                }
                default -> notifyListeners(e);
            }
        }

        void notifyListeners(VaultEvent e) {
            for (BiConsumer<UUID, VaultEvent> l : LISTENERS) {
                try {
                    l.accept(id, e);
                } catch (RuntimeException ex) {
                    TowerLog.error("Vault listener failed on " + e.type(), ex);
                }
            }
        }

        /** An anchor (model units, vault space) as a world position: turned by the facing, relative to the root. */
        Vec3 anchor(String name) {
            double[] a = manifest.anchor(name);
            double[] v = VaultMath.rotate(VaultMath.qAxis('y', facing.yawDeg), new double[] {(a[0] - 8) / 16, a[1] / 16, (a[2] - 8) / 16});
            return new Vec3(root.x + v[0], root.y + v[1], root.z + v[2]);
        }
    }
}
