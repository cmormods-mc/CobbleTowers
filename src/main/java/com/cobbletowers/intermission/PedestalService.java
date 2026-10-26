package com.cobbletowers.intermission;

import com.cobbletowers.ServerState;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.instance.CellGrid;
import com.cobbletowers.instance.CellPreparer;
import com.cobbletowers.instance.TowerDimension;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.route.FloorLayouts;
import com.cobbletowers.runtime.TowerRuns;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The modifier cards as pedestals (P55, the "Arcade Cabinet" look): per card a neon-capped stand, a spinning gem, three
 * stacked text pieces (name, big payout, details) and an invisible click box, standing near the vendor for an
 * intermission on a tower with {@code pedestal_votes}. A right-click votes through the same {@link
 * IntermissionService#pick} as the menu. Entities only, so releasing the cell removes them; what they show is rebuilt
 * from the draft, never stored.
 */
public final class PedestalService {

    private static final int GAZE_EVERY_TICKS = 4;
    private static final int SYNC_EVERY_TICKS = 20;
    private static final int AMBIENT_EVERY_TICKS = 8;
    private static final int SPIN_TICKS = 20;
    /** Plate colours (ARGB): normal, the chosen card, and the cards that lost. */
    private static final int PLATE = 0xE6040410;
    private static final int PLATE_CHOSEN = 0xE6063A1E;
    private static final int PLATE_LOST = 0x99040410;

    /** What stands for one run: enough to aim at it, redo it, and tell what changed. */
    private record Placed(List<PedestalRules.Slot> slots, List<String> names, List<PedestalStyle.Style> styles,
                          String signature, boolean settled, int[] votes, int chosen, List<UUID> gems, List<UUID> texts) {}

    private static final Map<UUID, Placed> PLACED = new HashMap<>();
    /** Which card each player's crosshair was last on, so the chime sounds once per look. */
    private static final Map<UUID, Integer> LAST_GAZE = new HashMap<>();
    private static final UUID NO_VIEWER = new UUID(0L, 0L);

    static {
        ServerState.onStop(PLACED::clear);
        ServerState.onStop(LAST_GAZE::clear);
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
        int count = draft.cards().size();
        List<PedestalRules.Slot> slots = PedestalRules.layout(vendor.getX() + 0.5, vendor.getZ() + 0.5,
                toward.getX() + 0.5, toward.getZ() + 0.5, vendor.getY(), count);
        boolean settled = draft.state() == 2;
        int voters = Math.max(1, DraftService.voters(run).size());

        List<PedestalHolo.Holo> holos = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<PedestalStyle.Style> styles = new ArrayList<>();
        int[] votes = new int[count];
        StringBuilder signature = new StringBuilder().append(draft.state()).append('|').append(draft.chosen());
        for (int i = 0; i < count; i++) {
            IntermissionStatePayload.Card card = draft.cards().get(i);
            PedestalHolo.Holo holo = PedestalHolo.build(card, card.votes(), voters, settled, draft.chosen() == i);
            holos.add(holo);
            names.add(card.displayName());
            styles.add(PedestalStyle.styleFor(card.risk()));
            votes[i] = card.votes();
            signature.append('|').append(holo.name().getString()).append(holo.big().getString()).append(holo.body().getString());
        }
        Placed before = PLACED.get(run.runId());
        if (before != null && before.signature().equals(signature.toString())) return;

        if (before != null && before.slots().equals(slots) && updateTexts(level, before.texts(), holos, settled, draft.chosen())) {
            react(level, before, slots, styles, votes, settled, draft.chosen());
            PLACED.put(run.runId(), new Placed(slots, names, styles, signature.toString(), settled, votes, draft.chosen(),
                    before.gems(), before.texts()));
            return;
        }
        removeEntities(level, run);
        List<UUID> gems = new ArrayList<>();
        List<UUID> texts = new ArrayList<>();
        if (!spawnAll(level, run, slots, styles, holos, settled, draft.chosen(), gems, texts)) {
            removeEntities(level, run);
            PLACED.remove(run.runId());
            TowerLog.warn("Run {}: the modifier pedestals could not be spawned", run.runId());
            return;
        }
        PLACED.put(run.runId(), new Placed(slots, names, styles, signature.toString(), settled, votes, draft.chosen(), gems, texts));
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

    private static int plate(boolean settled, int chosen, int index) {
        return !settled ? PLATE : chosen == index ? PLATE_CHOSEN : PLATE_LOST;
    }

    private static Component piece(PedestalHolo.Holo holo, int line) {
        return switch (line) {
            case PedestalRules.LINE_NAME -> holo.name();
            case PedestalRules.LINE_BIG -> holo.big();
            default -> holo.body();
        };
    }

    private static boolean updateTexts(ServerLevel level, List<UUID> texts, List<PedestalHolo.Holo> holos, boolean settled, int chosen) {
        if (texts.size() != holos.size() * 3) return false;
        for (int i = 0; i < holos.size(); i++) {
            for (int line = 0; line < 3; line++) {
                Entity entity = level.getEntity(texts.get(i * 3 + line));
                if (entity == null || entity.isRemoved()) return false;
                CompoundTag data = entity.saveWithoutId(new CompoundTag());
                data.putString("text", Component.Serializer.toJson(piece(holos.get(i), line), level.registryAccess()));
                data.putInt("background", plate(settled, chosen, i));
                entity.load(data);
            }
        }
        return true;
    }

    private static boolean spawnAll(ServerLevel level, PersistedRun run, List<PedestalRules.Slot> slots,
                                    List<PedestalStyle.Style> styles, List<PedestalHolo.Holo> holos, boolean settled,
                                    int chosen, List<UUID> gems, List<UUID> texts) {
        PedestalRules.Stack stack = PedestalRules.stack(PedestalRules.BODY_LINES);
        for (int i = 0; i < slots.size(); i++) {
            PedestalRules.Slot slot = slots.get(i);
            PedestalStyle.Style look = styles.get(i);
            String[] tags = {PedestalRules.TAG, PedestalRules.runTag(run.runId()), PedestalRules.cardTag(i)};
            List<CompoundTag> parts = new ArrayList<>();

            // stand: dark base and column, neon trim and cap
            parts.add(block("minecraft:black_concrete", slot, 0, -0.55f, 0f, -0.55f, 1.1f, 0.28f, 1.1f, false, tags, PedestalRules.PART_STAND));
            parts.add(block(look.capBlock(), slot, 0, -0.58f, 0.28f, -0.58f, 1.16f, 0.05f, 1.16f, true, tags, PedestalRules.PART_STAND));
            parts.add(block("minecraft:polished_blackstone", slot, 0, -0.35f, 0.33f, -0.35f, 0.7f, 0.62f, 0.7f, false, tags, PedestalRules.PART_STAND));
            parts.add(block(look.capBlock(), slot, 0, -0.45f, 0.95f, -0.45f, 0.9f, 0.1f, 0.9f, true, tags, PedestalRules.PART_STAND));

            CompoundTag gem = base("minecraft:block_display", slot.x(), slot.y() + PedestalRules.GEM_HEIGHT, slot.z(), tags, PedestalRules.PART_GEM);
            CompoundTag state = new CompoundTag();
            state.putString("Name", look.gemBlock());
            gem.put("block_state", state);
            gem.put("transformation", gemTransformation(0));
            gem.put("brightness", fullbright());
            gem.putInt("interpolation_duration", SPIN_TICKS);

            double[] ys = {stack.nameY(), stack.bigY(), stack.bodyY()};
            double[] scales = {PedestalRules.NAME_SCALE, PedestalRules.BIG_SCALE, PedestalRules.BODY_SCALE};
            int[] widths = {400, 200, 190};
            List<CompoundTag> lines = new ArrayList<>();
            for (int line = 0; line < 3; line++) {
                CompoundTag text = base("minecraft:text_display", slot.x(), slot.y() + ys[line], slot.z(), tags, PedestalRules.PART_TEXT);
                text.getList("Tags", 8).add(StringTag.valueOf(PedestalRules.lineTag(line)));
                text.putString("text", Component.Serializer.toJson(piece(holos.get(i), line), level.registryAccess()));
                text.putString("billboard", "center");
                text.putString("alignment", "center");
                text.putInt("line_width", widths[line]);
                text.putInt("background", plate(settled, chosen, i));
                text.putBoolean("shadow", true);
                text.put("brightness", fullbright());
                float s = (float) scales[line];
                text.put("transformation", transformation(new double[] {0, 0, 0}, new double[] {0, 0, 0, 1}, s, s, s));
                lines.add(text);
            }

            CompoundTag hitbox = base("minecraft:interaction", slot.x(), slot.y(), slot.z(), tags, PedestalRules.PART_HITBOX);
            hitbox.putFloat("width", (float) PedestalRules.HITBOX_WIDTH);
            hitbox.putFloat("height", (float) PedestalRules.HITBOX_HEIGHT);
            hitbox.putBoolean("response", true);

            for (CompoundTag tag : parts) if (!add(level, tag, null)) return false;
            if (!add(level, gem, gems)) return false;
            for (CompoundTag tag : lines) if (!add(level, tag, texts)) return false;
            if (!add(level, hitbox, null)) return false;
        }
        return true;
    }

    private static boolean add(ServerLevel level, CompoundTag tag, List<UUID> sink) {
        Optional<Entity> made = EntityType.create(tag, level);
        if (made.isEmpty() || !level.addFreshEntity(made.get())) return false;
        if (sink != null) sink.add(made.get().getUUID());
        return true;
    }

    private static CompoundTag block(String blockId, PedestalRules.Slot slot, double dy, float tx, float ty, float tz,
                                     float sx, float sy, float sz, boolean glow, String[] tags, String part) {
        CompoundTag tag = base("minecraft:block_display", slot.x(), slot.y() + dy, slot.z(), tags, part);
        CompoundTag state = new CompoundTag();
        state.putString("Name", blockId);
        tag.put("block_state", state);
        tag.put("transformation", transformation(new double[] {tx, ty, tz}, new double[] {0, 0, 0, 1}, sx, sy, sz));
        if (glow) tag.put("brightness", fullbright());
        return tag;
    }

    private static CompoundTag fullbright() {
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        return brightness;
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

    private static CompoundTag gemTransformation(double spin) {
        double[] q = PedestalRules.gemRotation(spin);
        return transformation(PedestalRules.centreOffset(q, PedestalRules.GEM_SIZE), q,
                (float) PedestalRules.GEM_SIZE, (float) PedestalRules.GEM_SIZE, (float) PedestalRules.GEM_SIZE);
    }

    private static CompoundTag transformation(double[] translation, double[] rotation, float sx, float sy, float sz) {
        CompoundTag t = new CompoundTag();
        t.put("translation", floats((float) translation[0], (float) translation[1], (float) translation[2]));
        t.put("scale", floats(sx, sy, sz));
        t.put("left_rotation", floats((float) rotation[0], (float) rotation[1], (float) rotation[2], (float) rotation[3]));
        t.put("right_rotation", floats(0f, 0f, 0f, 1f));
        return t;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float v : values) list.add(FloatTag.valueOf(v));
        return list;
    }

    // ---- effects ---------------------------------------------------------------------------------------------

    private static DustParticleOptions dust(PedestalStyle.Style look, float size) {
        return new DustParticleOptions(new Vector3f(look.dust()[0], look.dust()[1], look.dust()[2]), size);
    }

    /** Sparks and tinted dust around a gem; {@code big} adds a firework ring for the winner. */
    private static void burst(ServerLevel level, PedestalRules.Slot slot, PedestalStyle.Style look, int ring, boolean big) {
        double y = slot.y() + PedestalRules.GEM_HEIGHT;
        level.sendParticles(dust(look, 1.4f), slot.x(), y, slot.z(), ring, 0.45, 0.35, 0.45, 0.0);
        for (int j = 0; j < ring; j++) {
            double a = j * Math.PI * 2 / ring;
            level.sendParticles(big ? ParticleTypes.FIREWORK : ParticleTypes.ELECTRIC_SPARK, slot.x(), y, slot.z(), 0,
                    Math.cos(a) * 0.35, 0.12, Math.sin(a) * 0.35, 1.0);
        }
    }

    /** What changed since the last look: a card that gained a vote pops, and a card that wins fires. */
    private static void react(ServerLevel level, Placed before, List<PedestalRules.Slot> slots, List<PedestalStyle.Style> styles,
                              int[] votes, boolean settled, int chosen) {
        for (int i = 0; i < slots.size() && i < before.votes().length; i++) {
            if (votes[i] > before.votes()[i]) {
                PedestalRules.Slot slot = slots.get(i);
                burst(level, slot, styles.get(i), 14, false);
                level.playSound(null, slot.x(), slot.y() + 1.5, slot.z(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8f, 1.3f);
            }
        }
        if (settled && !before.settled() && chosen >= 0 && chosen < slots.size()) {
            PedestalRules.Slot slot = slots.get(chosen);
            burst(level, slot, styles.get(chosen), 28, true);
            level.playSound(null, slot.x(), slot.y() + 1.5, slot.z(), SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.7f, 1.0f);
        }
    }

    /** Turns each gem a quarter turn; the client interpolates the second between, so the spin looks continuous. */
    private static void spin(ServerLevel level, long tick) {
        double angle = (tick / SPIN_TICKS) * (Math.PI / 2);
        for (UUID runId : List.copyOf(PLACED.keySet())) {
            Placed placed = PLACED.get(runId);
            for (UUID id : placed.gems()) {
                Entity gem = level.getEntity(id);
                if (gem == null || gem.isRemoved()) {
                    PLACED.remove(runId);   // the next sync puts the stand back up
                    break;
                }
                CompoundTag data = gem.saveWithoutId(new CompoundTag());
                data.put("transformation", gemTransformation(angle));
                data.putInt("start_interpolation", 0);
                data.putInt("interpolation_duration", SPIN_TICKS);
                gem.load(data);
            }
        }
    }

    /** A few tinted sparks hovering round each gem. */
    private static void ambient(ServerLevel level) {
        for (Placed placed : PLACED.values()) {
            for (int i = 0; i < placed.slots().size(); i++) {
                PedestalRules.Slot slot = placed.slots().get(i);
                level.sendParticles(dust(placed.styles().get(i), 0.9f), slot.x(), slot.y() + PedestalRules.GEM_HEIGHT, slot.z(),
                        2, 0.4, 0.4, 0.4, 0.0);
            }
        }
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
        long now = server.getTickCount();
        boolean syncNow = now % SYNC_EVERY_TICKS == 0;
        Set<UUID> synced = new HashSet<>();
        Set<UUID> watching = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(TowerDimension.LEVEL)) continue;
            Optional<PersistedRun> run = TowerRuns.forPlayer(player.getUUID()).filter(r -> r.state() == RunState.INTERMISSION);
            if (run.isEmpty() || !enabled(run.get())) continue;
            UUID runId = run.get().runId();
            // A run with nothing standing yet (after a restart) is brought up here, then kept in step each second.
            if ((syncNow || !PLACED.containsKey(runId)) && synced.add(runId)) sync(server, run.get());
            watching.add(player.getUUID());
            gaze(player, PLACED.get(runId));
        }
        LAST_GAZE.keySet().retainAll(watching);
        ServerLevel level = TowerDimension.level(server);
        if (level == null || PLACED.isEmpty()) return;
        if (now % AMBIENT_EVERY_TICKS == 0) ambient(level);
        if (syncNow) {
            prune(server);
            spin(level, now);
        }
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
        if (placed == null || placed.settled()) {
            LAST_GAZE.remove(player.getUUID());
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Optional<Integer> hit = PedestalRules.gazedAt(eye.x, eye.y, eye.z, look.x, look.y, look.z, placed.slots());
        if (hit.isEmpty() || hit.get() >= placed.names().size()) {
            LAST_GAZE.remove(player.getUUID());
            return;
        }
        int index = hit.get();
        PedestalRules.Slot slot = placed.slots().get(index);
        PedestalStyle.Style style = placed.styles().get(index);
        MutableComponent prompt = Component.literal("▶ Click to vote: " + placed.names().get(index).toUpperCase(Locale.ROOT))
                .setStyle(Style.EMPTY.withColor(style.accent()).withBold(true));
        player.displayClientMessage(prompt, true);
        player.serverLevel().sendParticles(player, dust(style, 1.0f), false, slot.x(), slot.y() + PedestalRules.GEM_HEIGHT, slot.z(),
                3, 0.45, 0.4, 0.45, 0.0);
        Integer last = LAST_GAZE.put(player.getUUID(), index);
        if (last == null || last != index) {
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, style.pitch());
            player.serverLevel().sendParticles(player, ParticleTypes.ELECTRIC_SPARK, false, slot.x(), slot.y() + PedestalRules.GEM_HEIGHT,
                    slot.z(), 8, 0.4, 0.3, 0.4, 0.0);
        }
    }
}
