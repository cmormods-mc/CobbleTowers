package com.cobbletowers.persistence;

import com.cobbletowers.run.TowerParticipant;
import com.cobbletowers.run.TowerReturnLocation;
import com.cobbletowers.run.TowerRunSnapshot;
import com.cobbletowers.run.TowerRunState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Pure NBT codec for persisted Tower run snapshots.
 *
 * <p>The codec contains no world/server references and performs strict validation while decoding.
 * Saved runs are untrusted input: malformed UUIDs, resource IDs, enum values, dimensions, floors,
 * participant counts, and modifier tiers fail only that run at the caller's isolation boundary.
 */
public final class TowerRunNbtCodec {
    private static final String RUN_ID = "run_id";
    private static final String SEED = "seed";
    private static final String MAX_FLOORS = "max_floors";
    private static final String SLOT_INDEX = "slot_index";
    private static final String CURRENT_FLOOR = "current_floor";
    private static final String STATE = "state";
    private static final String PARTICIPANTS = "participants";
    private static final String ACCEPTED_CHALLENGES = "accepted_challenge_count";
    private static final String PROMOTION_PENDING = "promotion_pending";
    private static final String PENDING_TEMPORARY = "pending_temporary";
    private static final String RECENT_ACCEPTED = "recent_accepted";
    private static final String PERMANENT_TIERS = "permanent_tiers";

    private TowerRunNbtCodec() {}

    public static CompoundTag encode(TowerRunSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(RUN_ID, snapshot.runId());
        tag.putLong(SEED, snapshot.seed());
        tag.putInt(MAX_FLOORS, snapshot.maxFloors());
        tag.putInt(SLOT_INDEX, snapshot.slotIndex());
        tag.putInt(CURRENT_FLOOR, snapshot.currentFloor());
        tag.putString(STATE, snapshot.state().name());

        ListTag participants = new ListTag();
        snapshot.participants().stream()
                .sorted(Comparator.comparing(participant -> participant.playerId().toString()))
                .map(TowerRunNbtCodec::encodeParticipant)
                .forEach(participants::add);
        tag.put(PARTICIPANTS, participants);

        tag.putInt(ACCEPTED_CHALLENGES, snapshot.acceptedChallengeCount());
        tag.putBoolean(PROMOTION_PENDING, snapshot.promotionPending());
        if (snapshot.pendingTemporary() != null) {
            tag.putString(PENDING_TEMPORARY, snapshot.pendingTemporary().toString());
        }

        ListTag recentAccepted = new ListTag();
        for (ResourceLocation id : snapshot.recentAccepted()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            recentAccepted.add(entry);
        }
        tag.put(RECENT_ACCEPTED, recentAccepted);

        ListTag permanentTiers = new ListTag();
        snapshot.permanentTiers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> {
                    CompoundTag modifier = new CompoundTag();
                    modifier.putString("id", entry.getKey().toString());
                    modifier.putInt("tier", entry.getValue());
                    permanentTiers.add(modifier);
                });
        tag.put(PERMANENT_TIERS, permanentTiers);
        return tag;
    }

    public static TowerRunSnapshot decode(CompoundTag tag) {
        List<TowerParticipant> participants = new ArrayList<>();
        ListTag participantTags = tag.getList(PARTICIPANTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < participantTags.size(); i++) {
            participants.add(decodeParticipant(participantTags.getCompound(i)));
        }

        ResourceLocation pendingTemporary = null;
        String pendingTemporaryText = tag.getString(PENDING_TEMPORARY);
        if (!pendingTemporaryText.isBlank()) {
            pendingTemporary = parseId(pendingTemporaryText, PENDING_TEMPORARY);
        }

        List<ResourceLocation> recentAccepted = new ArrayList<>();
        ListTag recentTags = tag.getList(RECENT_ACCEPTED, Tag.TAG_COMPOUND);
        for (int i = 0; i < recentTags.size(); i++) {
            recentAccepted.add(parseId(recentTags.getCompound(i).getString("id"), RECENT_ACCEPTED));
        }

        Map<ResourceLocation, Integer> permanentTiers = new LinkedHashMap<>();
        ListTag permanentTags = tag.getList(PERMANENT_TIERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < permanentTags.size(); i++) {
            CompoundTag modifier = permanentTags.getCompound(i);
            ResourceLocation id = parseId(modifier.getString("id"), PERMANENT_TIERS);
            int tier = modifier.getInt("tier");
            if (tier < 1) throw new IllegalArgumentException("Permanent modifier tier must be >= 1 for " + id);
            if (permanentTiers.put(id, tier) != null) {
                throw new IllegalArgumentException("Duplicate permanent modifier entry: " + id);
            }
        }

        TowerRunState state;
        try {
            state = TowerRunState.valueOf(tag.getString(STATE));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown Tower run state: " + tag.getString(STATE), ex);
        }

        return new TowerRunSnapshot(
                tag.getUUID(RUN_ID),
                tag.getLong(SEED),
                tag.getInt(MAX_FLOORS),
                tag.getInt(SLOT_INDEX),
                tag.getInt(CURRENT_FLOOR),
                state,
                participants,
                tag.getInt(ACCEPTED_CHALLENGES),
                tag.getBoolean(PROMOTION_PENDING),
                pendingTemporary,
                recentAccepted,
                permanentTiers
        );
    }

    private static CompoundTag encodeParticipant(TowerParticipant participant) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player_id", participant.playerId());
        tag.putBoolean("active", participant.active());
        tag.putBoolean("connected", participant.connected());
        tag.putInt("reconnect_grace_ticks", participant.reconnectGraceTicksRemaining());

        TowerReturnLocation location = participant.returnLocation();
        CompoundTag returnLocation = new CompoundTag();
        returnLocation.putString("dimension", location.dimensionId());
        returnLocation.putDouble("x", location.x());
        returnLocation.putDouble("y", location.y());
        returnLocation.putDouble("z", location.z());
        returnLocation.putFloat("yaw", location.yaw());
        returnLocation.putFloat("pitch", location.pitch());
        tag.put("return_location", returnLocation);
        return tag;
    }

    private static TowerParticipant decodeParticipant(CompoundTag tag) {
        CompoundTag location = tag.getCompound("return_location");
        TowerReturnLocation returnLocation = new TowerReturnLocation(
                location.getString("dimension"),
                location.getDouble("x"),
                location.getDouble("y"),
                location.getDouble("z"),
                location.getFloat("yaw"),
                location.getFloat("pitch")
        );
        return new TowerParticipant(
                tag.getUUID("player_id"),
                returnLocation,
                tag.getBoolean("active"),
                tag.getBoolean("connected"),
                tag.getInt("reconnect_grace_ticks")
        );
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException("Invalid resource location in " + field + ": " + value);
        return id;
    }
}
