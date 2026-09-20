package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A run's vendor catalog, as it stands right now (TDS #16, #19), plus the caller's own CobbleDollar
 * balance -- the only place a player learns it (no always-on wallet HUD; see the design doc's
 * assumptions). One common catalog for every tower; a per-tower catalog is content this phase does
 * not build.
 */
public record VendorCatalogPayload(long cobbleDollars, List<Entry> services) implements CustomPacketPayload {

    /**
     * @param remainingPurchases -1 means unlimited -- a wire-level sentinel rather than an
     *                           {@code OptionalInt} codec, the same way a missing jersey number is a
     *                           real {@code OptionalInt} in Java but need not be one over the network
     */
    public record Entry(ResourceLocation id, String displayName, int priceCobbleDollars, int remainingPurchases) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::displayName,
                ByteBufCodecs.VAR_INT, Entry::priceCobbleDollars,
                ByteBufCodecs.VAR_INT, Entry::remainingPurchases,
                Entry::new);
    }

    public static final CustomPacketPayload.Type<VendorCatalogPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "vendor_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendorCatalogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, VendorCatalogPayload::cobbleDollars,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), VendorCatalogPayload::services,
            VendorCatalogPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
