package com.cobbletowers.intermission;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellGrid;
import com.cobbletowers.instance.CellPreparer;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.route.FloorLayouts;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * The modifier cards as pedestals (P55): a block stand, a text hologram and an invisible click box per card, standing
 * near the vendor for an intermission on a tower with {@code pedestal_votes}. A right-click votes through the same
 * {@link IntermissionService#pick} as the menu. Entities only, so releasing the cell removes them; what they show is
 * rebuilt from the draft, never stored.
 */
public final class PedestalService {

    private static final int GAZE_EVERY_TICKS = 4;
    private static final int SYNC_EVERY_TICKS = 20;
    /** Tuned live; the hologram's anchor sits inside the click box either way text_display anchors it. */
    private static final double TEXT_HEIGHT = 1.7;
    private static final int MAX_LINES = 3;
    private static final int MAX_LINE_CHARS = 56;

    /** What stands for one run: enough to aim at it and to tell when it needs redoing. */
    private record Placed(List<PedestalRules.Slot> slots, List<String> names, String signature, boolean settled) {}

    private static final Map<UUID, Placed> PLACED = new HashMap<>();
    private static final UUID NO_VIEWER = new UUID(0L, 0L);

    static {
        ServerState.onStop(PLACED::clear);
    }

    private PedestalService() {}

    public static void install() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!entity.getTags().contains(PedestalRules.TAG)) return InteractionResult.PASS;
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
            // Both hands fire for one click; only the main hand votes.
            if (hand == InteractionHand.MAIN_HAND) click(serverPlayer, entity);
            return InteractionResult.SUCCESS;
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!level.dimension().equals(TowerDimension.LEVEL)) return;
            if (!entity.getTags().contains(PedestalRules.TAG)) return;
            boolean live = entity.getTags().stream().map(PedestalRules::runOf).flatMap(Optional::stream).findFirst()
                    .flatMap(TowerRuns::get).filter(run -> run.state() == RunState.INTERMISSION).isPresent();
            if (!live) entity.discard();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % GAZE_EVERY_TICKS != 0) return;
            try {
                tick(server);
            } catch (RuntimeException ex) {
                TowerLog.error("A pedestal tick failed", ex);
            }
        });
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    /** Whether the run's tower shows pedestals. */
    public static boolean enabled(PersistedRun run) {
        var tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        return tower != null && tower.pedestalVotes();
    }

    /** Puts the pedestals up or brings them in step with the draft. Idempotent; safe to call at any time. */
    public static void sync(MinecraftServer server, PersistedRun run) {
        if (!enabled(run)) return;
        ServerLevel level = TowerDimension.level(server);
        if (level == null || run.cell().isEmpty() || run.state() != RunState.INTERMISSION) {
            despawn(server, run);
            return;
        }
        IntermissionStatePayload.Draft draft = IntermissionService.draftOf(run, NO_VIEWER);
        Optional<com.cobbletowers.definition.FloorLayout> layout =
                FloorLayouts.layoutOf(TowerDefinitionRegistry.content(), run, run.floorIndex());
        if (draft.state() == 0 || draft.cards().isEmpty() || layout.isEmpty()) {
            despawn(server, run);
            return;
        }
        Optional<BlockPos> origin = CellPreparer.originFor(server, run.cell().getAsInt(), layout.get());
        if (origin.isEmpty()) return;

        BlockPos vendor = layout.get().exit().in(origin.get());
        BlockPos toward = layout.get().spectator().in(origin.get());
        List<PedestalRules.Slot> slots = PedestalRules.layout(vendor.getX() + 0.5, vendor.getZ() + 0.5,
                toward.getX() + 0.5, toward.getZ() + 0.5, vendor.getY(), draft.cards().size());
        List<Component> texts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        StringBuilder signature = new StringBuilder().append(draft.state()).append('|').append(draft.chosen());
        for (int i = 0; i < draft.cards().size(); i++) {
            Component text = textFor(draft, i);
            texts.add(text);
            names.add(draft.cards().get(i).displayName());
            signature.append('|').append(text.getString());
        }
        boolean settled = draft.state() == 2;
        Placed before = PLACED.get(run.runId());
        if (before != null && before.signature().equals(signature.toString())) return;

        if (before != null && before.slots().equals(slots) && updateTexts(level, run, texts)) {
            PLACED.put(run.runId(), new Placed(slots, names, signature.toString(), settled));
            return;
        }
        removeEntities(level, run);
        if (!spawnAll(level, run, slots, texts)) {
            removeEntities(level, run);
            PLACED.remove(run.runId());
            TowerLog.warn("Run {}: the modifier pedestals could not be spawned", run.runId());
            return;
        }
        PLACED.put(run.runId(), new Placed(slots, names, signature.toString(), settled));
    }

    /** Called after a vote so the count on the hologram changes at once rather than at the next sync. */
    public static void refresh(MinecraftServer server, UUID runId) {
        TowerRuns.get(runId).ifPresent(run -> sync(server, run));
    }

    /** Takes the run's pedestals down. Safe to call at any time and more than once. */
    public static void despawn(MinecraftServer server, PersistedRun run) {
        PLACED.remove(run.runId());
        ServerLevel level = TowerDimension.level(server);
        if (level != null && !run.cell().isEmpty()) removeEntities(level, run);
    }

    // ---- entities --------------------------------------------------------------------------------------------

    /** Searched inside the run's own cell, so a lookup never walks the whole dimension. */
    private static List<Entity> entitiesOf(ServerLevel level, PersistedRun run) {
        String tag = PedestalRules.runTag(run.runId());
        return level.getEntities((Entity) null, CellGrid.boundsOf(run.cell().getAsInt()), e -> e.getTags().contains(tag));
    }

    private static void removeEntities(ServerLevel level, PersistedRun run) {
        for (Entity entity : entitiesOf(level, run)) entity.discard();
    }

    private static boolean updateTexts(ServerLevel level, PersistedRun run, List<Component> texts) {
        Map<Integer, Entity> byCard = new HashMap<>();
        for (Entity entity : entitiesOf(level, run)) {
            if (!entity.getTags().contains(PedestalRules.PART_TEXT)) continue;
            entity.getTags().stream().map(PedestalRules::cardOf).flatMap(Optional::stream).findFirst()
                    .ifPresent(index -> byCard.put(index, entity));
        }
        if (byCard.size() != texts.size()) return false;
        for (int i = 0; i < texts.size(); i++) {
            Entity entity = byCard.get(i);
            if (entity == null) return false;
            CompoundTag data = entity.saveWithoutId(new CompoundTag());
            data.putString("text", Component.Serializer.toJson(texts.get(i), level.registryAccess()));
            entity.load(data);
        }
        return true;
    }

    private static boolean spawnAll(ServerLevel level, PersistedRun run, List<PedestalRules.Slot> slots, List<Component> texts) {
        for (int i = 0; i < slots.size(); i++) {
            PedestalRules.Slot slot = slots.get(i);
            String[] tags = {PedestalRules.TAG, PedestalRules.runTag(run.runId()), PedestalRules.cardTag(i)};

            CompoundTag stand = base("minecraft:block_display", slot.x(), slot.y(), slot.z(), tags, PedestalRules.PART_STAND);
            CompoundTag state = new CompoundTag();
            state.putString("Name", "minecraft:polished_blackstone_bricks");
            stand.put("block_state", state);
            stand.put("transformation", transformation(-0.45f, 0f, -0.45f, 0.9f, 1.0f, 0.9f));

            CompoundTag text = base("minecraft:text_display", slot.x(), slot.y() + TEXT_HEIGHT, slot.z(), tags, PedestalRules.PART_TEXT);
            text.putString("text", Component.Serializer.toJson(texts.get(i), level.registryAccess()));
            text.putString("billboard", "center");
            text.putString("alignment", "center");
            text.putInt("line_width", 170);
            text.putInt("background", 0x99000000);
            text.putBoolean("shadow", true);
            CompoundTag brightness = new CompoundTag();
            brightness.putInt("block", 15);
            brightness.putInt("sky", 15);
            text.put("brightness", brightness);
            text.put("transformation", transformation(0f, 0f, 0f, 0.6f, 0.6f, 0.6f));

            CompoundTag hitbox = base("minecraft:interaction", slot.x(), slot.y(), slot.z(), tags, PedestalRules.PART_HITBOX);
            hitbox.putFloat("width", (float) PedestalRules.HITBOX_WIDTH);
            hitbox.putFloat("height", (float) PedestalRules.HITBOX_HEIGHT);
            hitbox.putBoolean("response", true);

            for (CompoundTag tag : List.of(stand, text, hitbox)) {
                Optional<Entity> made = EntityType.create(tag, level);
                if (made.isEmpty() || !level.addFreshEntity(made.get())) return false;
            }
        }
        return true;
    }

    private static CompoundTag base(String id, double x, double y, double z, String[] tags, String part) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(x));
        pos.add(DoubleTag.valueOf(y));
        pos.add(DoubleTag.valueOf(z));
        tag.put("Pos", pos);
        ListTag tagList = new ListTag();
        for (String t : tags) tagList.add(StringTag.valueOf(t));
        tagList.add(StringTag.valueOf(part));
        tag.put("Tags", tagList);
        tag.putBoolean("Invulnerable", true);
        return tag;
    }

    private static CompoundTag transformation(float tx, float ty, float tz, float sx, float sy, float sz) {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats(tx, ty, tz));
        t.put("scale", floats(sx, sy, sz));
        t.put("left_rotation", floats(0f, 0f, 0f, 1f));
        t.put("right_rotation", floats(0f, 0f, 0f, 1f));
        return t;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // ---- what the hologram says ------------------------------------------------------------------------------

    private static Component textFor(IntermissionStatePayload.Draft draft, int index) {
        IntermissionStatePayload.Card card = draft.cards().get(index);
        ChatFormatting colour = switch (card.risk()) {
            case 0 -> ChatFormatting.GREEN;
            case 1 -> ChatFormatting.GOLD;
            case 2 -> ChatFormatting.RED;
            default -> ChatFormatting.AQUA;
        };
        boolean settled = draft.state() == 2;
        MutableComponent text = Component.literal(card.displayName()).withStyle(colour, ChatFormatting.BOLD);
        if (settled) {
            text.append(draft.chosen() == index
                    ? Component.literal("\nCHOSEN").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                    : Component.literal("\nnot chosen").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            text.append(Component.literal("\n" + card.votes() + (card.votes() == 1 ? " vote" : " votes"))
                    .withStyle(ChatFormatting.YELLOW));
        }
        int shown = 0;
        for (String line : card.lines()) {
            if (shown++ == MAX_LINES) break;
            String clipped = line.length() > MAX_LINE_CHARS ? line.substring(0, MAX_LINE_CHARS - 1) + "…" : line;
            text.append(Component.literal("\n" + clipped).withStyle(settled ? ChatFormatting.DARK_GRAY : ChatFormatting.GRAY));
        }
        if (!settled) text.append(Component.literal("\nRight-click to vote").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        return text;
    }

    // ---- clicking and looking --------------------------------------------------------------------------------

    private static void click(ServerPlayer player, Entity entity) {
        Optional<UUID> pedestalRun = entity.getTags().stream().map(PedestalRules::runOf).flatMap(Optional::stream).findFirst();
        Optional<Integer> card = entity.getTags().stream().map(PedestalRules::cardOf).flatMap(Optional::stream).findFirst();
        if (pedestalRun.isEmpty() || card.isEmpty()) return;
        Optional<PersistedRun> playerRun = TowerRuns.forPlayer(player.getUUID());
        String message;
        if (playerRun.isEmpty() || !playerRun.get().runId().equals(pedestalRun.get())) {
            message = "These pedestals belong to another team.";
        } else if (playerRun.get().state() != RunState.INTERMISSION) {
            message = "You can vote between floors.";
        } else {
            Placed placed = PLACED.get(pedestalRun.get());
            String name = placed != null && card.get() < placed.names().size() ? placed.names().get(card.get()) : "";
            message = IntermissionService.pick(player.server, player, card.get());
            if ("Vote recorded.".equals(message) && !name.isEmpty()) message = "You voted for " + name + ".";
        }
        TowerLog.info("Pedestal of run {}: {} clicked card {}", pedestalRun.get(), player.getGameProfile().getName(), card.get());
        if (!message.isEmpty()) player.displayClientMessage(Component.literal(message), true);
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        boolean syncNow = now % SYNC_EVERY_TICKS == 0;
        Set<UUID> synced = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(TowerDimension.LEVEL)) continue;
            Optional<PersistedRun> run = TowerRuns.forPlayer(player.getUUID()).filter(r -> r.state() == RunState.INTERMISSION);
            if (run.isEmpty() || !enabled(run.get())) continue;
            UUID runId = run.get().runId();
            // A run with nothing standing yet (after a restart) is brought up here, then kept in step each second.
            if ((syncNow || !PLACED.containsKey(runId)) && synced.add(runId)) sync(server, run.get());
            gaze(player, PLACED.get(runId));
        }
        if (syncNow) prune(server);
    }

    /** Takes down pedestals whose run has left the intermission by a road that did not pass through the transition hook. */
    private static void prune(MinecraftServer server) {
        for (UUID runId : List.copyOf(PLACED.keySet())) {
            Optional<PersistedRun> run = TowerRuns.get(runId);
            if (run.isEmpty()) {
                PLACED.remove(runId);
            } else if (run.get().state() != RunState.INTERMISSION) {
                despawn(server, run.get());
            }
        }
    }

    private static void gaze(ServerPlayer player, Placed placed) {
        if (placed == null || placed.settled()) return;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        PedestalRules.gazedAt(eye.x, eye.y, eye.z, look.x, look.y, look.z, placed.slots()).ifPresent(index -> {
            if (index >= placed.names().size()) return;
            PedestalRules.Slot slot = placed.slots().get(index);
            player.displayClientMessage(Component.literal("Click to vote: " + placed.names().get(index)), true);
            player.serverLevel().sendParticles(player, ParticleTypes.WITCH, false, slot.x(), slot.y() + 1.2, slot.z(),
                    3, 0.35, 0.15, 0.35, 0.0);
        });
    }
}
