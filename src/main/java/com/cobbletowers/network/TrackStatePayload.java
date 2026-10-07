package com.cobbletowers.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The Progress tab's battle tracks (P37): the mastery lane of one tower (with the list of towers to switch between) and, while a season
 * runs, the season lane. Sent on opening the tab and after every claim, never per frame. {@code endsInMillis} is a duration from now, not a
 * clock time, so a client whose clock is off still counts down correctly.
 */
public record TrackStatePayload(boolean autoClaim, List<Tower> towers, String selectedTower, Lane mastery, Lane season,
                                String message) implements CustomPacketPayload {

    /** Every text field is cut to what its codec writes ({@code writeUtf} throws past the limit and would break the packet). */
    private static String clip(String text, int max) {
        return text == null ? "" : text.length() <= max ? text : text.substring(0, Math.max(0, max - 1)) + "…";
    }

    public TrackStatePayload {
        selectedTower = clip(selectedTower, 128);
        message = clip(message, 256);
    }

    /** One tower in the selector and the level the player holds there. */
    public record Tower(ResourceLocation id, String name, int level) {
        public Tower {
            name = clip(name, 128);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Tower> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Tower::id, ByteBufCodecs.stringUtf8(128), Tower::name,
                ByteBufCodecs.VAR_INT, Tower::level, Tower::new);
    }

    /** Node states. */
    public static final int LOCKED = 0, CLAIMABLE = 1, CLAIMED = 2, INFO = 3, UNAVAILABLE = 4;

    /**
     * One node. {@code icon} is an item id (or empty); {@code reward} says what claiming gives; {@code note} is a perk or rank change shown
     * even when there is nothing to claim.
     */
    public record Node(int number, int state, String icon, String reward, String note) {
        public Node {
            icon = clip(icon, 128);
            reward = clip(reward, 512);
            note = clip(note, 256);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Node> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Node::number, ByteBufCodecs.VAR_INT, Node::state, ByteBufCodecs.stringUtf8(128), Node::icon,
                ByteBufCodecs.stringUtf8(512), Node::reward, ByteBufCodecs.stringUtf8(256), Node::note, Node::new);
    }

    /**
     * A lane. {@code current} is the level (mastery) or the step reached (season); {@code into}/{@code need} are the progress toward the next
     * node (0/0 when there is no next one); {@code endsInMillis} is the time left in a season lane, 0 for the mastery lane.
     */
    public record Lane(boolean present, String title, String subtitle, int current, int into, int need, long endsInMillis, List<Node> nodes) {
        public Lane {
            title = clip(title, 128);
            subtitle = clip(subtitle, 256);
            nodes = nodes.size() > 512 ? List.copyOf(nodes.subList(0, 512)) : List.copyOf(nodes);
        }

        public static final Lane NONE = new Lane(false, "", "", 0, 0, 0, 0, List.of());
        private static final StreamCodec<RegistryFriendlyByteBuf, List<Node>> NODES = Node.CODEC.apply(ByteBufCodecs.list(512));
        static final StreamCodec<RegistryFriendlyByteBuf, Lane> CODEC = StreamCodec.of(
                (buf, lane) -> {
                    buf.writeBoolean(lane.present());
                    buf.writeUtf(lane.title(), 128);
                    buf.writeUtf(lane.subtitle(), 256);
                    buf.writeVarInt(lane.current());
                    buf.writeVarInt(lane.into());
                    buf.writeVarInt(lane.need());
                    buf.writeVarLong(lane.endsInMillis());
                    NODES.encode(buf, lane.nodes());
                },
                buf -> new Lane(buf.readBoolean(), buf.readUtf(128), buf.readUtf(256), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarLong(), NODES.decode(buf)));
    }

    public static final Type<TrackStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("cobbletowers", "tracks_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrackStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, TrackStatePayload::autoClaim, Tower.CODEC.apply(ByteBufCodecs.list(256)), TrackStatePayload::towers,
            ByteBufCodecs.stringUtf8(128), TrackStatePayload::selectedTower, Lane.CODEC, TrackStatePayload::mastery,
            Lane.CODEC, TrackStatePayload::season, ByteBufCodecs.stringUtf8(256), TrackStatePayload::message, TrackStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
