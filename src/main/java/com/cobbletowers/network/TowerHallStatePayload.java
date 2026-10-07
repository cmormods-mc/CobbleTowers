package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Read-only Hall snapshot. A separate channel leaves the existing lobby protocol unchanged. */
public record TowerHallStatePayload(List<Destination> towers, List<Trial> trials,
                                    PlayStatePayload play, String runStatus, boolean canResume) implements CustomPacketPayload {
    public record Destination(ResourceLocation id, String name, int floors, int milestones, boolean ascends) {
        static final StreamCodec<RegistryFriendlyByteBuf, Destination> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Destination::id,
                ByteBufCodecs.stringUtf8(256), Destination::name,
                ByteBufCodecs.VAR_INT, Destination::floors,
                ByteBufCodecs.VAR_INT, Destination::milestones,
                ByteBufCodecs.BOOL, Destination::ascends, Destination::new);
    }

    public record Trial(String kind, String revision, String title, ResourceLocation tower, List<String> rules) {
        static final StreamCodec<RegistryFriendlyByteBuf, Trial> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Trial::kind,
                ByteBufCodecs.stringUtf8(256), Trial::revision,
                ByteBufCodecs.stringUtf8(256), Trial::title,
                ResourceLocation.STREAM_CODEC, Trial::tower,
                ByteBufCodecs.stringUtf8(1024).apply(ByteBufCodecs.list(32)), Trial::rules, Trial::new);
    }

    public static final Type<TowerHallStatePayload> TYPE = new Type<>(
            CobbleTowers.id("tower_hall_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TowerHallStatePayload> STREAM_CODEC = StreamCodec.composite(
            Destination.CODEC.apply(ByteBufCodecs.list(256)), TowerHallStatePayload::towers,
            Trial.CODEC.apply(ByteBufCodecs.list(2)), TowerHallStatePayload::trials,
            PlayStatePayload.STREAM_CODEC, TowerHallStatePayload::play,
            ByteBufCodecs.stringUtf8(1024), TowerHallStatePayload::runStatus,
            ByteBufCodecs.BOOL, TowerHallStatePayload::canResume, TowerHallStatePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
