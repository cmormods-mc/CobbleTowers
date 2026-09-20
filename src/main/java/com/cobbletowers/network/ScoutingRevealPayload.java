package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What a scouting profile reveals about a floor's draw, sent alongside the encounter starting rather
 * than gating it (TDS #22, #49). A concealed category is simply absent, not shown-and-blanked.
 */
public record ScoutingRevealPayload(int floorIndex, List<Category> categories) implements CustomPacketPayload {

    public record Category(String name, String value) {
        static final StreamCodec<RegistryFriendlyByteBuf, Category> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Category::name,
                ByteBufCodecs.STRING_UTF8, Category::value,
                Category::new);
    }

    public static final CustomPacketPayload.Type<ScoutingRevealPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "scouting_reveal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScoutingRevealPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScoutingRevealPayload::floorIndex,
            Category.STREAM_CODEC.apply(ByteBufCodecs.list()), ScoutingRevealPayload::categories,
            ScoutingRevealPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
