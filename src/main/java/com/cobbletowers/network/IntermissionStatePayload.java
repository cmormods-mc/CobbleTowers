package com.cobbletowers.network;

import com.cobbletowers.CobbleTowers;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the intermission screen shows (P17): the draft with votes, who is ready or voted to cash out, and the
 * countdown.
 * @param open true to open the screen (arrival or request); false for an update to an open one
 */
public record IntermissionStatePayload(int floor, Draft draft, List<Member> members, int countdown, String message,
                                       boolean open, Extras extras) implements CustomPacketPayload {

    /** The native screens do not use the extras (P51 slice 2), so they keep building the state without them. */
    public IntermissionStatePayload(int floor, Draft draft, List<Member> members, int countdown, String message, boolean open) {
        this(floor, draft, members, countdown, message, open, Extras.NONE);
    }

    /**
     * What the Frontier Desk page draws around the draft: the team's health and where the run is in its tower.
     * @param tower the tower's display name, or empty
     * @param cycleFloor the floor just cleared within the current cycle (1 to {@code cycleLength}); 0 when unknown
     * @param milestones the cycle's milestone floors (boss, champion)
     * @param party up to six Pokemon, party order
     */
    public record Extras(String tower, int cycleFloor, int cycleLength, List<Integer> milestones,
                         List<TowerHallStatePayload.Mon> party, Route route) {
        public static final Extras NONE = new Extras("", 0, 0, List.of(), List.of(), Route.NONE);
        static final StreamCodec<RegistryFriendlyByteBuf, Extras> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(64), Extras::tower,
                ByteBufCodecs.VAR_INT, Extras::cycleFloor,
                ByteBufCodecs.VAR_INT, Extras::cycleLength,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), Extras::milestones,
                TowerHallStatePayload.Mon.CODEC.apply(ByteBufCodecs.list(6)), Extras::party,
                Route.CODEC, Extras::route, Extras::new);
    }

    /**
     * The route between floors (P50) as the page draws it.
     * @param lane the lane the party stands on, or -1 when it has not taken a step
     * @param now the stops of the step on the table: lane, the index of the card riding on it, its kind (a RouteGraph.Kind name), and the tower whose
     *     building its battle is fought in when that is a detour (empty for the run's own); empty when this step is not a route step
     * @param ahead the steps scouting can see, with the kinds of their stops (empty for a milestone, which pays a relic)
     */
    public record Route(int lane, List<Node> now, List<Step> ahead, Graph graph) {
        public static final Route NONE = new Route(-1, List.of(), List.of());

        /** A route with no branching map: every tower before P56. */
        public Route(int lane, List<Node> now, List<Step> ahead) {
            this(lane, now, ahead, Graph.NONE);
        }

        /**
         * The branching map of the tower's cycle (P56): every stop and link, and the stop the party stands on.
         * @param rows floors in the cycle; 0 when this tower has no map
         * @param reveal the highest row whose kinds the party can see
         * @param trail the party's lane at each row from the start (row 0) to its stop; its length less one is the row it stands on
         */
        public record Graph(int rows, int reveal, List<Integer> trail, List<Stop> stops, List<Link> links) {
            public static final Graph NONE = new Graph(0, 0, List.of(), List.of(), List.of());

            /** A stop: its row, its lane, and a {@code RouteMap.Stop} name. */
            public record Stop(int row, int lane, String kind) {
                static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Stop::row, ByteBufCodecs.VAR_INT, Stop::lane, ByteBufCodecs.stringUtf8(16), Stop::kind, Stop::new);
            }

            /** A link from a lane of {@code row} to a lane of the row above. */
            public record Link(int row, int from, int to) {
                static final StreamCodec<RegistryFriendlyByteBuf, Link> CODEC = StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, Link::row, ByteBufCodecs.VAR_INT, Link::from, ByteBufCodecs.VAR_INT, Link::to, Link::new);
            }

            static final StreamCodec<RegistryFriendlyByteBuf, Graph> CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Graph::rows, ByteBufCodecs.VAR_INT, Graph::reveal,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(64)), Graph::trail,
                    Stop.CODEC.apply(ByteBufCodecs.list(128)), Graph::stops,
                    Link.CODEC.apply(ByteBufCodecs.list(256)), Graph::links, Graph::new);
        }

        public record Node(int lane, int card, String kind, String arena) {
            static final StreamCodec<RegistryFriendlyByteBuf, Node> CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Node::lane, ByteBufCodecs.VAR_INT, Node::card, ByteBufCodecs.stringUtf8(16), Node::kind,
                    ByteBufCodecs.stringUtf8(64), Node::arena, Node::new);
        }

        public record Step(int floor, boolean milestone, List<String> kinds) {
            static final StreamCodec<RegistryFriendlyByteBuf, Step> CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Step::floor, ByteBufCodecs.BOOL, Step::milestone,
                    ByteBufCodecs.stringUtf8(16).apply(ByteBufCodecs.list(3)), Step::kinds, Step::new);
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Route> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Route::lane,
                Node.CODEC.apply(ByteBufCodecs.list(3)), Route::now,
                Step.CODEC.apply(ByteBufCodecs.list(8)), Route::ahead,
                Graph.CODEC, Route::graph, Route::new);
    }

    /**
     * One card on offer.
     * @param risk 0 minor, 1 moderate, 2 severe, -1 for a non-modifier card
     * @param theme the picture key the client paints it with ({@code ModifierArt}), never a rule
     * @param lines the authoritative description, one fact per line
     */
    public record Card(ResourceLocation id, String displayName, int votes, int risk, String theme, List<String> lines) {
        static final StreamCodec<RegistryFriendlyByteBuf, Card> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Card::id,
                ByteBufCodecs.STRING_UTF8, Card::displayName,
                ByteBufCodecs.VAR_INT, Card::votes,
                ByteBufCodecs.VAR_INT, Card::risk,
                ByteBufCodecs.STRING_UTF8, Card::theme,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(32)), Card::lines,
                Card::new);
    }

    /**
     * @param state 0 no draft, 1 open for votes, 2 settled
     * @param chosen the winning card's index once settled, else -1
     * @param myVote the viewer's own vote, or -1
     */
    public record Draft(int state, List<Card> cards, int chosen, int myVote) {
        static final StreamCodec<RegistryFriendlyByteBuf, Draft> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Draft::state,
                Card.STREAM_CODEC.apply(ByteBufCodecs.list()), Draft::cards,
                ByteBufCodecs.VAR_INT, Draft::chosen,
                ByteBufCodecs.VAR_INT, Draft::myVote,
                Draft::new);
    }

    public record Member(String name, boolean ready, boolean cashOut) {
        static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.BOOL, Member::ready,
                ByteBufCodecs.BOOL, Member::cashOut,
                Member::new);
    }

    public static final CustomPacketPayload.Type<IntermissionStatePayload> TYPE = new CustomPacketPayload.Type<>(
            CobbleTowers.id("intermission_state"));

    /** Seven fields is one more than StreamCodec.composite takes, so this one is written out. */
    public static final StreamCodec<RegistryFriendlyByteBuf, IntermissionStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ByteBufCodecs.VAR_INT.encode(buf, p.floor());
                Draft.STREAM_CODEC.encode(buf, p.draft());
                Member.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.members());
                ByteBufCodecs.VAR_INT.encode(buf, p.countdown());
                ByteBufCodecs.STRING_UTF8.encode(buf, p.message());
                ByteBufCodecs.BOOL.encode(buf, p.open());
                Extras.CODEC.encode(buf, p.extras());
            },
            buf -> new IntermissionStatePayload(ByteBufCodecs.VAR_INT.decode(buf), Draft.STREAM_CODEC.decode(buf),
                    Member.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf), Extras.CODEC.decode(buf)));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
