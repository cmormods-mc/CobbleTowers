package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * What a run is carrying, for the in-fight overlay: the Hall refuses to open mid-battle, so the server pushes this at the
 * start of each tower battle and the client draws it on a key (P41). Lines carry the benefit or cost marker of
 * {@code ModifierMenuText}. Bounded on both ends: at most {@value #MAX_ITEMS} items of {@value #MAX_LINES} lines.
 */
public record RunEffectsPayload(int floor, int riskPercent, List<Item> items) implements CustomPacketPayload {

    public static final int MAX_ITEMS = 24;
    public static final int MAX_LINES = 6;
    public static final int MAX_TEXT = 120;

    /** One modifier or relic: its name, how many copies, the floor it first applied to (0 unknown) and its effects. */
    public record Item(String name, boolean relic, int count, int floor, List<String> lines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Item> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Item::name,
                ByteBufCodecs.BOOL, Item::relic,
                ByteBufCodecs.VAR_INT, Item::count,
                ByteBufCodecs.VAR_INT, Item::floor,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_LINES)), Item::lines,
                Item::new);
    }

    public RunEffectsPayload {
        items = List.copyOf(items);
    }

    public static final CustomPacketPayload.Type<RunEffectsPayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("run_effects"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunEffectsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RunEffectsPayload::floor,
            ByteBufCodecs.VAR_INT, RunEffectsPayload::riskPercent,
            Item.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ITEMS)), RunEffectsPayload::items,
            RunEffectsPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
