package com.cobbletowers.armor.flare;

import com.cobbletowers.TowerLog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector3f;

/**
 * Particles around the back attachments of the Tideforge, Rootvale and Duskvale chestplates: bubbles and gauge sparks, falling
 * leaves and a glowing seed, drifting stars and ghost dust. Armor Model API draws the models static, so this is the animation.
 *
 * <p>The four traps: a pass every {@value #PASS_TICKS} ticks, never every tick; it scans the online players once and caps the
 * wearers served per pass ({@link FlareConfig#maxWearers()}), rotating the start so nobody starves; it keeps no per-player state
 * (the phase comes from the UUID), so there is nothing to leak or to clean up on a disconnect; and it runs on the server thread
 * from the end-of-tick event, a failure logged once per pass rather than thrown.
 */
public final class ArmorFlareService {

    static final int PASS_TICKS = 2;

    private static final Map<String, ParticleOptions> CACHE = new HashMap<>();
    private static FlareConfig config = FlareConfig.DEFAULT;
    private static boolean installed;
    private static boolean warnedParticle;

    private ArmorFlareService() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        config = FlareConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(ArmorFlareService::tick);
    }

    private static void tick(MinecraftServer server) {
        if (!config.enabled() || server.getTickCount() % PASS_TICKS != 0) return;
        try {
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            long pass = server.getTickCount() / PASS_TICKS;
            int start = (int) (pass % players.size());
            int served = 0;
            for (int i = 0; i < players.size() && served < config.maxWearers(); i++) {
                ServerPlayer player = players.get((start + i) % players.size());
                String set = wornSet(player);
                if (set == null || skip(player)) continue;
                emit(player, FlarePlan.at(set, pass, FlarePlan.phaseOf(player.getUUID()), player.isUnderWater(), player.isSprinting()));
                served++;
            }
        } catch (RuntimeException ex) {
            TowerLog.error("Armor flares failed this pass", ex);
        }
    }

    private static String wornSet(ServerPlayer player) {
        var stack = player.getItemBySlot(EquipmentSlot.CHEST);
        if (stack.isEmpty()) return null;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return FlarePlan.setOf(id.getNamespace(), id.getPath());
    }

    /** No flares for anyone the body cannot be located on: dead, spectating, invisible, gliding or asleep. */
    private static boolean skip(ServerPlayer player) {
        return !player.isAlive() || player.isSpectator() || player.isInvisible() || player.isFallFlying() || player.isSleeping();
    }

    private static void emit(ServerPlayer player, List<FlarePlan.Emit> emits) {
        if (emits.isEmpty()) return;
        ServerLevel level = player.serverLevel();
        double yaw = Math.toRadians(player.yBodyRot);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);            // the wearer's right
        double bx = Math.sin(yaw), bz = -Math.cos(yaw);             // behind them
        boolean prone = player.isSwimming() || player.isVisuallyCrawling();
        double lean = player.isCrouching() ? 0.4 : 0.0;                 // radians the torso tips forward, about the hip
        double cos = Math.cos(lean), sin = Math.sin(lean);
        var motion = player.getDeltaMovement();
        for (FlarePlan.Emit e : emits) {
            ParticleOptions options = options(e.particle());
            if (options == null) continue;
            double up = e.up(), back = e.back();
            if (lean != 0.0) {
                double d = up - 0.75;
                up = 0.75 + d * cos + back * sin;
                back = back * cos - d * sin;
            }
            double x, y, z;
            if (prone) {
                // lying flat: the head leads, so height along the body runs forward and the back faces up
                x = player.getX() + rx * e.right() - bx * up;
                z = player.getZ() + rz * e.right() - bz * up;
                y = player.getY() + 0.2 + back * 0.9;
            } else {
                x = player.getX() + rx * e.right() + bx * back;
                y = player.getY() + up;
                z = player.getZ() + rz * e.right() + bz * back;
            }
            if (e.count() == 0) {
                // count 0 makes the client treat the three offsets as a velocity, so the particle drifts the way the plan says;
                // it also carries the wearer's own motion so a jump or a dash does not leave leaves hanging in the air
                double vx = rx * e.velRight() + bx * e.velBack() + motion.x * 0.8;
                double vz = rz * e.velRight() + bz * e.velBack() + motion.z * 0.8;
                double vy = prone ? e.velUp() * 0.3 : e.velUp() + motion.y * 0.5;
                level.sendParticles(options, x, y, z, 0, vx, vy, vz, 1.0);
            } else {
                level.sendParticles(options, x, y, z, e.count(), e.spread(), e.spread(), e.spread(), 0.01);
            }
        }
    }

    private static ParticleOptions options(String id) {
        return CACHE.computeIfAbsent(id, ArmorFlareService::build);
    }

    private static ParticleOptions build(String id) {
        if (id.startsWith("block:")) {
            var block = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(id.substring("block:".length())));
            return new BlockParticleOption(ParticleTypes.BLOCK, (block == Blocks.AIR ? Blocks.AZALEA_LEAVES : block).defaultBlockState());
        }
        if (id.equals("dust_transition")) {
            return new DustColorTransitionOptions(new Vector3f(0.72f, 0.60f, 1.0f), new Vector3f(1.0f, 0.45f, 0.82f), 0.8f);
        }
        var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.withDefaultNamespace(id));
        if (type instanceof SimpleParticleType simple) return simple;
        if (!warnedParticle) {
            warnedParticle = true;
            TowerLog.error("Armor flare particle '{}' is not a simple particle; it is skipped", id);
        }
        return null;
    }
}
