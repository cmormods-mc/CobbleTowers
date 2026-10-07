package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Structured records and server-authorized actions. Explanatory lines are rendered, never parsed. */
public record TowerFeatureState(int requestId,String section,String title,List<Entry> entries,List<Action> actions,String message) implements CustomPacketPayload {
    public record Action(String id,String label,String argument,String confirmation,String inputHint) {
        static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.composite(
                ByteBufCodecs.stringUtf8(32),Action::id,ByteBufCodecs.stringUtf8(128),Action::label,
                ByteBufCodecs.stringUtf8(256),Action::argument,ByteBufCodecs.stringUtf8(512),Action::confirmation,
                ByteBufCodecs.stringUtf8(128),Action::inputHint,Action::new);
    }
    public record Entry(String id,String title,List<String> lines,List<Action> actions) {
        static final StreamCodec<RegistryFriendlyByteBuf,Entry> CODEC=StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256),Entry::id,ByteBufCodecs.stringUtf8(256),Entry::title,
                ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(32)),Entry::lines,
                Action.CODEC.apply(ByteBufCodecs.list(16)),Entry::actions,Entry::new);
    }
    public static final Type<TowerFeatureState> TYPE=new Type<>(CobbleTowers.id("feature_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TowerFeatureState> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,TowerFeatureState::requestId,ByteBufCodecs.stringUtf8(24),TowerFeatureState::section,
            ByteBufCodecs.stringUtf8(128),TowerFeatureState::title,
            Entry.CODEC.apply(ByteBufCodecs.list(128)),TowerFeatureState::entries,
            Action.CODEC.apply(ByteBufCodecs.list(16)),TowerFeatureState::actions,
            ByteBufCodecs.stringUtf8(512),TowerFeatureState::message,TowerFeatureState::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
