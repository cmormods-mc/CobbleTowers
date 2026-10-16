package com.cobbletowers.armor.worn;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws the 3D parts of worn armor (P49): one {@link ElementHolder} per wearer, attached to the player, with an item display
 * per part. Head parts follow the head yaw and pitch, torso parts the body yaw. All on the main thread; holders are keyed by
 * UUID and found again through the player list, never held by player reference. Every {@value #SCAN_TICKS} ticks the worn
 * slots are re-read (bounded by the player count); between scans only the wearers are touched.
 */
public final class WornModelService {

    static final int SCAN_TICKS = 10;
    /** Vanilla draws the player model at 15/16 scale, so the parts are scaled the same and the pivots sit lower. */
    private static final float MODEL_SCALE = 0.9375f;
    /** Neck and shoulder height standing, and lowered when sneaking (head 4.2 px, shoulders 3.2 px, minus the 0.125 render offset). */
    private static final Vec3 HEAD_STAND = new Vec3(0, 1.5 * MODEL_SCALE, 0), HEAD_SNEAK = new Vec3(0, (24 - 4.2) / 16 * MODEL_SCALE - 0.125, 0);
    private static final Vec3 TORSO_STAND = HEAD_STAND, TORSO_SNEAK = new Vec3(0, (24 - 3.2) / 16 * MODEL_SCALE - 0.125, 0);
    private static final float SNEAK_LEAN = 28.6f;
    /** Moves the model pivot (neck, shoulder line) onto the entity origin; model units are 1/16 block. */
    private static final Vector3f HEAD_PIVOT = new Vector3f(0, 0.5f * MODEL_SCALE, 0), TORSO_PIVOT = new Vector3f(0, -1.0f * MODEL_SCALE, 0);
    private static final Vector3f SCALE = new Vector3f(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

    private static final Map<UUID, Wearer> WEARERS = new HashMap<>();
    private static WornConfig config = WornConfig.DEFAULT;
    private static boolean installed;
    private static int ticks;
    private static long updateNanos, updates, scanNanos, scans;

    private WornModelService() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        WornPart.bootstrap();
        config = WornConfig.load();
        ServerTickEvents.END_SERVER_TICK.register(WornModelService::tick);
        ServerState.onStop(WEARERS::clear);
    }

    private static void tick(MinecraftServer server) {
        if (!config.enabled()) {
            if (!WEARERS.isEmpty()) destroyAll();
            return;
        }
        ticks++;
        try {
            if (ticks % SCAN_TICKS == 0) {
                long t = System.nanoTime();
                scan(server);
                scanNanos += System.nanoTime() - t;
                scans++;
            }
            if (!WEARERS.isEmpty() && ticks % config.updateTicks() == 0) {
                long t = System.nanoTime();
                for (Map.Entry<UUID, Wearer> entry : WEARERS.entrySet()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                    if (player != null) entry.getValue().update(player);
                }
                updateNanos += System.nanoTime() - t;
                updates++;
            }
        } catch (RuntimeException ex) {
            TowerLog.error("Worn 3D parts failed this tick", ex);
        }
    }

    private static void scan(MinecraftServer server) {
        Set<UUID> seen = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            List<WornPart> parts = player.isAlive() ? partsOf(player) : List.of();
            if (parts.isEmpty()) continue;
            seen.add(id);
            Wearer have = WEARERS.get(id);
            if (have != null && have.parts.equals(parts) && have.alive()) continue;
            if (have != null) have.destroy();
            WEARERS.put(id, new Wearer(player, parts));
        }
        WEARERS.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            entry.getValue().destroy();
            return true;
        });
    }

    private static List<WornPart> partsOf(ServerPlayer player) {
        List<WornPart> parts = null;
        for (WornPart part : WornPart.values()) {
            EquipmentSlot slot = part.anchor() == WornPart.Anchor.HEAD ? EquipmentSlot.HEAD : EquipmentSlot.CHEST;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!id.equals(part.piece())) continue;
            if (parts == null) parts = new ArrayList<>(2);
            parts.add(part);
        }
        return parts == null ? List.of() : parts;
    }

    private static void destroyAll() {
        for (Wearer wearer : WEARERS.values()) wearer.destroy();
        WEARERS.clear();
    }

    /** One line for the admin command: wearers, parts, and what the last updates cost. */
    public static String stats() {
        int parts = 0, watchers = 0;
        for (Wearer wearer : WEARERS.values()) {
            parts += wearer.parts.size();
            watchers += wearer.holder.getWatchingPlayers().size();
        }
        return String.format(java.util.Locale.ROOT, "worn 3D: enabled=%s wearers=%d parts=%d watchers=%d update=%.1fus/tick scan=%.1fus",
                config.enabled(), WEARERS.size(), parts, watchers, updates == 0 ? 0.0 : updateNanos / 1000.0 / updates,
                scans == 0 ? 0.0 : scanNanos / 1000.0 / scans);
    }

    /** Re-reads the config file and rebuilds every wearer, so a setting can be tuned on a running server. */
    public static void reload() {
        config = WornConfig.load();
        destroyAll();
    }

    /** The cost counters restart so a measurement covers only what follows. */
    public static void resetStats() {
        updateNanos = updates = scanNanos = scans = 0;
    }

    private static final class Wearer {
        final List<WornPart> parts;
        private final ElementHolder holder;
        private final List<ItemDisplayElement> elements = new ArrayList<>();
        private boolean sneaking;
        private boolean destroyed;
        private boolean shown = true;

        Wearer(ServerPlayer player, List<WornPart> parts) {
            this.parts = parts;
            UUID owner = player.getUUID();
            this.holder = new ElementHolder() {
                @Override
                public boolean startWatching(ServerGamePacketListenerImpl handler) {
                    if (config.hideFromOwner() && handler.getPlayer().getUUID().equals(owner)) return false;
                    if (config.requirePack() && !PolymerResourcePackUtils.hasMainPack(handler.getPlayer())) return false;
                    return super.startWatching(handler);
                }
            };
            for (WornPart part : parts) {
                ItemDisplayElement element = new ItemDisplayElement(part.stack());
                element.setModelTransformation(ItemDisplayContext.NONE);
                element.setScale(SCALE);
                element.setTranslation(part.anchor() == WornPart.Anchor.HEAD ? HEAD_PIVOT : TORSO_PIVOT);
                element.setTeleportDuration(config.smoothingTicks());
                element.setViewRange(0.5f);
                element.setShadowRadius(0f);
                element.setOffset(part.anchor() == WornPart.Anchor.HEAD ? HEAD_STAND : TORSO_STAND);
                holder.addElement(element);
                elements.add(element);
            }
            EntityAttachment.ofTicking(holder, player);
            update(player);
        }

        boolean alive() {
            return !destroyed;
        }

        void update(ServerPlayer player) {
            // swimming, gliding and sleeping lay the body down, which the parts cannot follow
            boolean show = !(player.isSwimming() || player.isFallFlying() || player.isSleeping() || player.isVisuallyCrawling());
            if (show != shown) {
                shown = show;
                for (int i = 0; i < elements.size(); i++) elements.get(i).setItem(show ? parts.get(i).stack() : ItemStack.EMPTY);
            }
            if (!show) return;
            boolean sneak = player.isCrouching();
            float headYaw = player.getYHeadRot() + config.yawOffset();
            float bodyYaw = player.yBodyRot + config.yawOffset();
            float pitch = player.getXRot() * config.pitchSign();
            boolean poseChanged = sneak != sneaking;
            sneaking = sneak;
            for (int i = 0; i < elements.size(); i++) {
                ItemDisplayElement element = elements.get(i);
                if (parts.get(i).anchor() == WornPart.Anchor.HEAD) {
                    element.setYaw(headYaw);
                    element.setPitch(pitch);
                    if (poseChanged) element.setOffset(sneak ? HEAD_SNEAK : HEAD_STAND);
                } else {
                    element.setYaw(bodyYaw);
                    element.setPitch(sneak ? SNEAK_LEAN * config.pitchSign() : 0f);
                    if (poseChanged) element.setOffset(sneak ? TORSO_SNEAK : TORSO_STAND);
                }
            }
        }

        void destroy() {
            destroyed = true;
            holder.destroy();
        }
    }
}
