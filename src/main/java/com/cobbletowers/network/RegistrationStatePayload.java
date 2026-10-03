package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the party chooser shows (P18): every Pokemon the player owns in the party and the boxes, which are
 * chosen, and how many may be.
 *
 * @param open true when the screen should be opened (the player asked); false for a change pushed to an
 *             already-open chooser
 */
public record RegistrationStatePayload(List<Entry> pokemon, List<UUID> chosen, int max, String message, boolean open)
        implements CustomPacketPayload {

    /** @param where a short place name: "Party 1" or "Box 3". */
    public record Entry(UUID id, String name, int level, boolean fainted, String where) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.VAR_INT, Entry::level,
                ByteBufCodecs.BOOL, Entry::fainted,
                ByteBufCodecs.STRING_UTF8, Entry::where,
                Entry::new);
    }

    public static final CustomPacketPayload.Type<RegistrationStatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "registration_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RegistrationStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), RegistrationStatePayload::pokemon,
                    UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), RegistrationStatePayload::chosen,
                    ByteBufCodecs.VAR_INT, RegistrationStatePayload::max,
                    ByteBufCodecs.STRING_UTF8, RegistrationStatePayload::message,
                    ByteBufCodecs.BOOL, RegistrationStatePayload::open,
                    RegistrationStatePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
