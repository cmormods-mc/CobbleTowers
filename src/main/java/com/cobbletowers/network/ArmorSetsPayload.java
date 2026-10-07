package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.armor.ArmorSetView;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Every armor set, worded for tooltips (P25), sent on join and after each reload; replaces the client's list. */
public record ArmorSetsPayload(List<ArmorSetView> sets) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ArmorSetsPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "armor_sets"));

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmorSetView.Piece> PIECE = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ArmorSetView.Piece::slot,
            ResourceLocation.STREAM_CODEC, ArmorSetView.Piece::item,
            ArmorSetView.Piece::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmorSetView.Tier> TIER = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArmorSetView.Tier::pieces,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ArmorSetView.Tier::lines,
            ArmorSetView.Tier::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, ArmorSetView> VIEW = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ArmorSetView::id,
            ByteBufCodecs.STRING_UTF8, ArmorSetView::name,
            ByteBufCodecs.VAR_INT, ArmorSetView::color,
            PIECE.apply(ByteBufCodecs.list()), ArmorSetView::pieces,
            TIER.apply(ByteBufCodecs.list()), ArmorSetView::tiers,
            ArmorSetView::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorSetsPayload> STREAM_CODEC = StreamCodec.composite(
            VIEW.apply(ByteBufCodecs.list()), ArmorSetsPayload::sets,
            ArmorSetsPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
