package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Ranks tab's duel between two regions. The server decides every number, word and who leads, so the page only draws.
 * @param scope what the numbers cover, such as {@code Season 3} or {@code All time}
 * @param yourRegion the viewer's region id, or empty when CobbleTeams has not placed them
 * @param left the region id on the left of the duel
 * @param right the region id on the right
 * @param rows one line per stat, in display order
 * @param message a line for the page to show, or empty
 */
public record RanksStatePayload(String scope, String yourRegion, String left, String right, List<Region> regions,
                                List<Row> rows, List<Champion> leftChampions, List<Champion> rightChampions,
                                String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RanksStatePayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("ranks_state"));

    /** A region the viewer can pick. */
    public record Region(String id, String name, int members) {
        static final StreamCodec<RegistryFriendlyByteBuf, Region> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Region::id,
                ByteBufCodecs.STRING_UTF8, Region::name,
                ByteBufCodecs.VAR_INT, Region::members,
                Region::new);
    }

    /**
     * One stat of the duel. {@code leftBar} and {@code rightBar} are 0-100 widths; {@code lead} is -1 when the left
     * leads, 1 when the right does, 0 when level.
     */
    public record Row(String key, String label, String leftText, String rightText, int leftBar, int rightBar, int lead) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of(
                (buf, row) -> {
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.key);
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.label);
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.leftText);
                    ByteBufCodecs.STRING_UTF8.encode(buf, row.rightText);
                    buf.writeVarInt(row.leftBar);
                    buf.writeVarInt(row.rightBar);
                    buf.writeVarInt(row.lead + 1);
                },
                buf -> new Row(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                        ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1));
    }

    public record Champion(String name, int points) {
        static final StreamCodec<RegistryFriendlyByteBuf, Champion> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Champion::name,
                ByteBufCodecs.VAR_INT, Champion::points,
                Champion::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RanksStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, p.scope);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.yourRegion);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.left);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.right);
                Region.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.regions);
                Row.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.rows);
                Champion.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.leftChampions);
                Champion.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.rightChampions);
                ByteBufCodecs.STRING_UTF8.encode(buf, p.message);
            },
            buf -> new RanksStatePayload(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    Region.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Champion.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Champion.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
