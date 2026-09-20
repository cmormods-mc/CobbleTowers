package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What a spectator's HUD panel shows: the teammate they are currently following (TDS #25).
 *
 * <p>Sent whenever who they follow changes and whenever that followed teammate's own fight resolves,
 * so the panel never shows a stale fight.
 */
public record SpectatorPanelPayload(String teammateName, int remainingCount, int totalCount, int floorIndex,
                                     String runStateLabel) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpectatorPanelPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(CobbleTowers.MOD_ID, "spectator_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpectatorPanelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpectatorPanelPayload::teammateName,
            ByteBufCodecs.VAR_INT, SpectatorPanelPayload::remainingCount,
            ByteBufCodecs.VAR_INT, SpectatorPanelPayload::totalCount,
            ByteBufCodecs.VAR_INT, SpectatorPanelPayload::floorIndex,
            ByteBufCodecs.STRING_UTF8, SpectatorPanelPayload::runStateLabel,
            SpectatorPanelPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
