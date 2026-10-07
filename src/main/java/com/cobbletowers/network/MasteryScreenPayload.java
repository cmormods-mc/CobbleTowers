package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything the mastery screen shows (P31): towers with the player's level and, for the chosen tower, achievements
 * or one leaderboard, worded by {@code MasteryView}.
 * @param tab {@code "mastery"} or a board name ({@code "speed"}, {@code "ascension"}, {@code "difficulty"}, {@code
 *     "clears"})
 * @param open true only when the player asked for the screen; a refresh never opens one
 */
public record MasteryScreenPayload(List<Tower> towers, String selected, String tab, Mastery mastery, Board board,
                                   boolean open) implements CustomPacketPayload {

    public record Tower(ResourceLocation id, String displayName, int level, String rank) {
        static final StreamCodec<RegistryFriendlyByteBuf, Tower> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Tower::id,
                ByteBufCodecs.STRING_UTF8, Tower::displayName,
                ByteBufCodecs.VAR_INT, Tower::level,
                ByteBufCodecs.STRING_UTF8, Tower::rank,
                Tower::new);
    }

    public record Achievement(String name, String description, boolean held) {
        static final StreamCodec<RegistryFriendlyByteBuf, Achievement> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Achievement::name,
                ByteBufCodecs.STRING_UTF8, Achievement::description,
                ByteBufCodecs.BOOL, Achievement::held,
                Achievement::new);
    }

    /** The chosen tower's standing: a sentence of progress, a sentence of perks, and every achievement. */
    public record Mastery(String progress, String perks, List<Achievement> achievements) {
        static final StreamCodec<RegistryFriendlyByteBuf, Mastery> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Mastery::progress,
                ByteBufCodecs.STRING_UTF8, Mastery::perks,
                Achievement.STREAM_CODEC.apply(ByteBufCodecs.list()), Mastery::achievements,
                Mastery::new);

        public static Mastery none() {
            return new Mastery("", "", List.of());
        }
    }

    /**
     * One board's rows, already worded. {@code split} boards list solo and team apart; others use {@code solo} only.
     */
    public record Board(String title, boolean split, List<String> solo, List<String> team) {
        static final StreamCodec<RegistryFriendlyByteBuf, Board> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Board::title,
                ByteBufCodecs.BOOL, Board::split,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Board::solo,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Board::team,
                Board::new);

        public static Board none() {
            return new Board("", false, List.of(), List.of());
        }
    }

    public static final CustomPacketPayload.Type<MasteryScreenPayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("mastery_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MasteryScreenPayload> STREAM_CODEC = StreamCodec.composite(
            Tower.STREAM_CODEC.apply(ByteBufCodecs.list()), MasteryScreenPayload::towers,
            ByteBufCodecs.STRING_UTF8, MasteryScreenPayload::selected,
            ByteBufCodecs.STRING_UTF8, MasteryScreenPayload::tab,
            Mastery.STREAM_CODEC, MasteryScreenPayload::mastery,
            Board.STREAM_CODEC, MasteryScreenPayload::board,
            ByteBufCodecs.BOOL, MasteryScreenPayload::open,
            MasteryScreenPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
