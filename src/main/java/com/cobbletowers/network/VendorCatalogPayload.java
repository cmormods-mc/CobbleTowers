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
 * A run's vendor catalog, as it stands right now (TDS #16, #19), plus the caller's own CobbleDollar
 * balance -- the only place a player learns it (no always-on wallet HUD; see the design doc's
 * assumptions). One common catalog for every tower; a per-tower catalog is content this phase does
 * not build.
 *
 * <p>P19 adds the team, so a purchase can be aimed at a teammate (TDS #18), and a message, so a refused
 * purchase says why instead of simply not happening.
 *
 * @param team    everyone in the run, the caller included, with whether they can be bought for right now
 * @param message the outcome of the last purchase attempt, or empty
 */
public record VendorCatalogPayload(long cobbleDollars, List<Entry> services, List<Teammate> team, String message)
        implements CustomPacketPayload {

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

    /** @param online false for a teammate who is not connected: nothing can be applied to them. */
    public record Teammate(UUID id, String name, boolean online) {
        static final StreamCodec<RegistryFriendlyByteBuf, Teammate> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Teammate::id,
                ByteBufCodecs.STRING_UTF8, Teammate::name,
                ByteBufCodecs.BOOL, Teammate::online,
                Teammate::new);
    }

    public static final CustomPacketPayload.Type<VendorCatalogPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "vendor_catalog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VendorCatalogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, VendorCatalogPayload::cobbleDollars,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), VendorCatalogPayload::services,
            Teammate.STREAM_CODEC.apply(ByteBufCodecs.list()), VendorCatalogPayload::team,
            ByteBufCodecs.STRING_UTF8, VendorCatalogPayload::message,
            VendorCatalogPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
