package com.cobbletowers.client;

import com.cobbletowers.network.RentalDraftActionPayload;
import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.rental.PackReveal;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * The Rental Draft's pack opening (P33): a sealed pack that shakes and tears open, five cards that turn over one by one with the
 * best last, then a choice of two to keep, three times, and a summary of the six-Pokemon team.
 *
 * <p>The screen decides nothing. It draws what the server sent in a {@link RentalDraftPayload} and sends back which two cards
 * were chosen; the server checks every pick. Timing and feel come from {@link com.cobbletowers.rental.PackReveal}, the card art from a
 * {@link CardFace}, and a render fault closes the screen with a note instead of taking the client down with it.
 */
public final class RentalPackScreen extends TowerScreen {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("cobbletowers");

    private enum Stage { TABLE, TEARING, REVEALING, CHOOSING, TEAM }

    /** Whether to play the animations at all. Kept for the session, so a player who turns them off is not asked again. */
    private static boolean animations = true;

    private static final int CARD_W = ByzantineCardFace.WIDTH;
    private static final int CARD_H = ByzantineCardFace.HEIGHT;
    private static final int GAP = 6;

    /** Whether to draw cards as CobblemonCards cards when that mod is installed; kept for the session like the animation setting. */
    private static boolean collectionLook = true;

    private CardFace face() {
        return collectionLook && CobblemonCardFace.available() ? CobblemonCardFace.INSTANCE : ByzantineCardFace.INSTANCE;
    }
    private RentalDraftPayload draft;
    private Stage stage;
    private long stageStart;
    private int pack = -1;
    private final List<Integer> selected = new ArrayList<>();
    private final List<Integer> revealOrder = new ArrayList<>();
    private final boolean[] landed = new boolean[5];
    private final long[] landedAt = new long[5];
    private final List<Spark> sparks = new ArrayList<>();
    private String message = "";
    private boolean failed;
    private Button keep;
    private Button restart;
    private Button toggle;
    private Button done;
    private Button lookToggle;

    public RentalPackScreen(RentalDraftPayload draft) {
        super(Component.literal("Rental Draft"), TowerUi.Theme.RENTAL);
        accept(draft);
    }

    /** A fresh view of the draft arrived: show the pack the server says is current, or the finished team. */
    public void accept(RentalDraftPayload next) {
        boolean advanced = draft == null || next.current() != draft.current() || next.complete() != draft.complete();
        this.draft = next;
        this.message = next.message();
        if (!advanced) {
            refreshButtons();   // a refused pick leaves the draft where it was: let the player choose again
            return;
        }
        selected.clear();
        if (next.complete()) {
            enter(Stage.TEAM);
        } else {
            pack = next.current();
            enter(Stage.TABLE);
        }
        refreshButtons();
    }

    /** The card under the pointer, which stays chosen while the pointer is anywhere over it (a lifted card covers its neighbours). */
    private int hoverIndex = -1;

    private boolean slicePlayed;
    private boolean spillPlayed;

    private void enter(Stage next) {
        stage = next;
        hoverIndex = -1;
        if (next == Stage.TEARING) {
            slicePlayed = false;
            spillPlayed = false;
        }
        stageStart = now();
        if (next == Stage.REVEALING) {
            revealOrder.clear();
            revealOrder.addAll(PackReveal.revealOrder(currentCards().stream().map(RentalDraftPayload.Card::rarity).toList()));
            java.util.Arrays.fill(landed, false);
            sparks.clear();
        }
        if (next == Stage.CHOOSING) java.util.Arrays.fill(landed, true);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private List<RentalDraftPayload.Card> currentCards() {
        if (pack < 0 || pack >= draft.packs().size()) return List.of();
        return draft.packs().get(pack).cards();
    }

    private boolean godPack() {
        return pack >= 0 && pack < draft.packs().size() && draft.packs().get(pack).god();
    }

    // ---- widgets -----------------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        keep = addRenderableWidget(TowerButton.builder(Component.literal("Keep these two"), b -> sendPick())
                .pos(width / 2 - 100, height - 30).size(130, 20).build());
        restart = addRenderableWidget(TowerButton.builder(Component.literal("Draft again"),
                        b -> send(RentalDraftActionPayload.Action.RESTART, 0, 0))
                .pos(width / 2 + 36, height - 30).size(64, 20).build());
        done = addRenderableWidget(TowerButton.builder(Component.literal("Done"), b -> onClose())
                .pos(width / 2 - 100, height - 30).size(130, 20).build());
        toggle = addRenderableWidget(TowerButton.builder(animLabel(), b -> {
                    animations = !animations;
                    toggle.setMessage(animLabel());
                    if (!animations && (stage == Stage.TEARING || stage == Stage.REVEALING)) enter(Stage.CHOOSING);
                }).pos(6, height - 24).size(102, 16).build());
        if (CobblemonCardFace.available()) {
            lookToggle = addRenderableWidget(TowerButton.builder(lookLabel(), b -> {
                        collectionLook = !collectionLook;
                        lookToggle.setMessage(lookLabel());
                    }).pos(width - 108, height - 24).size(102, 16).build());
        }
        refreshButtons();
    }

    private static Component lookLabel() {
        return Component.literal("Cards: " + (collectionLook ? "Collection" : "Mosaic"));
    }

    private static Component animLabel() {
        return Component.literal("Animations: " + (animations ? "On" : "Off"));
    }

    private void refreshButtons() {
        if (keep == null) return;
        keep.visible = stage == Stage.CHOOSING;
        done.visible = stage == Stage.TEAM;
        keep.active = selected.size() == 2;
        restart.visible = stage == Stage.CHOOSING && pack > 0 || stage == Stage.TEAM;
        restart.setMessage(Component.literal(stage == Stage.TEAM ? "Draft again" : "Start over"));
        restart.setWidth(80);
    }

    private void send(RentalDraftActionPayload.Action action, int a, int b) {
        if (ClientPlayNetworking.canSend(RentalDraftActionPayload.TYPE)) {
            ClientPlayNetworking.send(new RentalDraftActionPayload(action, a, b));
        }
    }

    private void sendPick() {
        if (selected.size() != 2) return;
        send(RentalDraftActionPayload.Action.PICK, selected.get(0), selected.get(1));
        keep.active = false;   // until the server answers with the next pack
    }

    // ---- layout ------------------------------------------------------------------------------------------------------

    /** Cards are drawn at their own size whenever the window is tall enough; width is handled by overlapping them, not by shrinking. */
    float scale() {
        return Math.max(0.5f, Math.min(1f, (height - 74f) / CARD_H));
    }

    /** The distance between one card's left edge and the next's: the natural gap, or less so a whole row fits (a fan). */
    private int stride(int count, float scale) {
        int w = Math.round(CARD_W * scale);
        int natural = w + Math.round(GAP * scale);
        if (count <= 1) return natural;
        return Math.min(natural, Math.max(12, (width - 16 - w) / (count - 1)));
    }

    /** x, y, width, height of card {@code index} in a row of {@code count} cards; later cards overlap earlier ones when the row is wide. */
    int[] cardRect(int index, int count, float scale) {
        int w = Math.round(CARD_W * scale);
        int h = Math.round(CARD_H * scale);
        int stride = stride(count, scale);
        int total = w + (count - 1) * stride;
        int left = (width - total) / 2;
        return new int[] {left + index * stride, (height - h) / 2 + 6, w, h};
    }

    /** A point on the part of card {@code index} that no other card covers, for a script that wants to click it. */
    int[] visiblePoint(int index, int count, float scale) {
        int[] r = cardRect(index, count, scale);
        int visible = index == count - 1 ? r[2] : Math.min(r[2], stride(count, scale));
        return new int[] {r[0] + visible / 2, r[1] + r[3] / 2};
    }

    /** Which card a point is on, honouring who is on top: the hovered card, then later cards over earlier ones. */
    private int topCardAt(double mx, double my, int count) {
        float scale = scale();
        if (hoverIndex >= 0 && hoverIndex < count && inside(mx, my, cardRect(hoverIndex, count, scale))) return hoverIndex;
        for (int i = count - 1; i >= 0; i--) {
            if (inside(mx, my, cardRect(i, count, scale))) return i;
        }
        return -1;
    }

    // ---- drawing -----------------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failed) return;
        try {
            // Screen.render draws the background (once: a second pass would blur everything drawn before it), then calls
            // renderBackground below for our own content, then the widgets on top.
            super.render(graphics, mouseX, mouseY, partialTick);
        } catch (RuntimeException ex) {
            // A fault in drawing must never take the client down: say so, and let the player draft in chat instead.
            failed = true;
            LOGGER.error("The rental pack screen failed to draw", ex);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.setScreen(null);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal(
                        "The pack screen could not be drawn. Use /tower draft text to draft in chat."), false);
            }
        }
    }

    /** The table itself: the vanilla background, then everything this screen draws, so the buttons land on top. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (failed) return;
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        draw(graphics, mouseX, mouseY);
    }

    private void draw(GuiGraphics graphics, int mouseX, int mouseY) {
        long t = now();
        advance(t);

        // A rare card turning over shakes the whole table.
        float shakeX = 0f;
        float shakeY = 0f;
        if (TowerUiSettings.motion && stage == Stage.REVEALING) {
            for (int i = 0; i < landed.length && i < currentCards().size(); i++) {
                if (!landed[i]) continue;
                float s = PackReveal.shake(currentCards().get(i).rarity(), t - landedAt[i]);
                shakeX += s;
                shakeY += s * 0.6f;
            }
        } else if (TowerUiSettings.motion && stage == Stage.TEARING) {
            shakeX = PackReveal.tearShake(t - stageStart, topRank());
        }
        graphics.pose().pushPose();
        graphics.pose().translate(shakeX, shakeY, 0f);

        switch (stage) {
            case TABLE, TEARING -> drawPack(graphics, t);
            case REVEALING, CHOOSING -> drawCards(graphics, mouseX, mouseY, t);
            case TEAM -> drawTeam(graphics, mouseX, mouseY, t);
        }
        drawSparks(graphics, t);
        graphics.pose().popPose();

        drawHeader(graphics);
    }

    private int topRank() {
        int best = 0;
        for (RentalDraftPayload.Card card : currentCards()) best = Math.max(best, PackReveal.rank(card.rarity()));
        return best;
    }

    /** Moves the stage along: a tear ends in the reveal, a reveal in the choice. */
    private void advance(long t) {
        if (stage == Stage.TEARING && TowerUiSettings.motion) {
            // One cue as the blade goes through and one as the light spills out: once each, never from the render loop's own state.
            float p = Math.min(1f, (t - stageStart) / (float) PackReveal.TEAR_MS);
            if (!slicePlayed && p >= CUT_START) {
                slicePlayed = true;
                if (TowerUiSettings.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SHEEP_SHEAR, 1.4f, 0.6f));
            }
            if (!spillPlayed && p >= SPILL_START) {
                spillPlayed = true;
                if (TowerUiSettings.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1f, 0.7f));
            }
        }
        if (stage == Stage.TEARING && (!animations || !TowerUiSettings.motion || t - stageStart >= PackReveal.TEAR_MS)) {
            enter(animations && TowerUiSettings.motion ? Stage.REVEALING : Stage.CHOOSING);
            refreshButtons();
        }
        if (stage == Stage.REVEALING) {
            long since = t - stageStart;
            List<RentalDraftPayload.Card> cards = currentCards();
            for (int position = 0; position < revealOrder.size(); position++) {
                int index = revealOrder.get(position);
                if (landed[index] || since < PackReveal.flipStart(position) + PackReveal.FLIP_MS / 2) continue;
                landed[index] = true;
                landedAt[index] = t;
                land(cards.get(index), index, cards.size(), t);
            }
            if (since >= PackReveal.totalMs(cards.size())) {
                enter(Stage.CHOOSING);
                refreshButtons();
            }
        }
    }

    /** A card has just turned face up: a sound, and sparkles for the ones worth it. */
    private void land(RentalDraftPayload.Card card, int index, int count, long t) {
        int rank = PackReveal.rank(card.rarity());
        float pitch = 0.8f + 0.12f * rank;
        SoundEvent sound = rank >= 4 ? SoundEvents.PLAYER_LEVELUP : SoundEvents.EXPERIENCE_ORB_PICKUP;
        if(TowerUiSettings.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, rank >= 4 ? 0.6f : 0.5f));
        int[] rect = cardRect(index, count, scale());
        int cx = rect[0] + rect[2] / 2;
        int cy = rect[1] + rect[3] / 2;
        int color = PackReveal.colorAt(card.rarity(), t);
        java.util.Random random = new java.util.Random(index * 31L + t);
        for (int i = 0; i < PackReveal.particleCount(card.rarity()); i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.05 + random.nextDouble() * 0.12;
            sparks.add(new Spark(cx, cy, Math.cos(angle) * speed, Math.sin(angle) * speed, t, 500 + random.nextInt(500), color));
        }
    }

    private void drawHeader(GuiGraphics graphics) {
        String title = stage == Stage.TEAM ? "Your rental team" : "Pack " + (pack + 1) + " of " + draft.packs().size()
                + (godPack() ? "  -  GOD PACK" : "");
        int color = stage != Stage.TEAM && godPack() ? TowerUi.BRONZE_LIGHT : TowerUi.TEXT;
        graphics.drawCenteredString(font, title, width / 2, 10, color);
        String hint = switch (stage) {
            case TABLE -> "Click the pack to open it";
            case TEARING -> "";
            case REVEALING -> "Click to skip the reveal";
            case CHOOSING -> "Choose two cards to keep (" + selected.size() + " of 2)";
            case TEAM -> "Drafted. Close this and the host can start the run.";
        };
        graphics.drawCenteredString(font, hint, width / 2, 24, TowerUi.MUTED);
        if (!message.isEmpty()) graphics.drawCenteredString(font, message, width / 2, height - 46, TowerUi.DANGER);
    }

    // ---- the pack, and the blade that opens it -------------------------------------------------------------------------------

    private static final ResourceLocation PACK_TOP = ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/byzantine/pack_top.png");
    private static final ResourceLocation PACK_BODY = ResourceLocation.fromNamespaceAndPath("cobbletowers", "textures/gui/byzantine/pack_body.png");
    private static final int PACK_W = 112;
    private static final int PACK_TOP_H = 16;
    private static final int PACK_BODY_H = 144;
    /** The cut, as fractions of the tear: a held breath, the blade across, then the foil strip lifts away on a spill of light. */
    private static final float CUT_START = 0.25f;
    private static final float CUT_END = 0.55f;
    private static final float SPILL_START = 0.55f;

    private static int white(float alpha) {
        return (Math.max(0, Math.min(255, Math.round(alpha * 255))) << 24) | 0xFFFFFF;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private void drawPack(GuiGraphics graphics, long t) {
        int x = (width - PACK_W) / 2;
        int y = (height - (PACK_TOP_H + PACK_BODY_H)) / 2 + 6;
        int cutY = y + PACK_TOP_H;
        // The sealed pack keeps its secret: gold for the God Pack, otherwise one neutral colour, never the best card colour.
        int glow = godPack() ? TowerUi.BRONZE_LIGHT : TowerUi.BURGUNDY;
        long since = t - stageStart;
        float p = stage == Stage.TEARING ? clamp01(since / (float) PackReveal.TEAR_MS) : 0f;
        int pulse = (int) (0x30 + 0x30 * Math.sin(TowerUiSettings.motion ? t / 300.0 : 0) + 0x40 * p);
        for (int g = 9; g >= 3; g -= 3) {
            graphics.fill(x - g, y - g, x + PACK_W + g, y + PACK_TOP_H + PACK_BODY_H + g, (Math.min(255, pulse / (g / 2)) << 24) | (glow & 0xFFFFFF));
        }
        graphics.blit(PACK_BODY, x, cutY, PACK_W, PACK_BODY_H, 0f, 0f, PACK_W, PACK_BODY_H, PACK_W, PACK_BODY_H);
        // No drop shadow: dark lettering on a gold plate turns to a smudge with one (the cards' lettering has none either).
        String label = "RENTAL PACK";
        graphics.drawString(font, label, x + (PACK_W - font.width(label)) / 2, cutY + 112 + 5, TowerUi.INK, false);

        float blade = clamp01((p - CUT_START) / (CUT_END - CUT_START));
        float lift = clamp01((p - SPILL_START) / (1f - SPILL_START));
        float easeLift = 1f - (1f - lift) * (1f - lift) * (1f - lift);
        int rise = Math.round(easeLift * 30);

        float fade = 1f - clamp01((p - 0.9f) / 0.1f);         // the light settles as the cards take over
        boolean cutting = stage == Stage.TEARING && p >= CUT_START;

        // The light that spills out of the opening, behind the strip: a bright gap, and a beam that is hottest in the middle.
        if (cutting && lift > 0f) {
            float strength = (1f - 0.35f * lift) * fade;
            graphics.fill(x + 3, cutY - rise, x + PACK_W - 3, cutY, white(strength));
            int top = cutY - rise - Math.round(easeLift * 78);
            graphics.fillGradient(x + 3, top, x + PACK_W - 3, cutY - rise, white(0f), white(0.5f * strength));
            graphics.fillGradient(x + 30, top + 10, x + PACK_W - 30, cutY - rise, white(0f), white(0.8f * strength));
        }

        // The foil strip: in place until the blade has passed, then it tilts and lifts away, fading as it goes.
        graphics.pose().pushPose();
        if (lift > 0f) {
            graphics.pose().translate(x + PACK_W, cutY, 0);
            graphics.pose().mulPose(new org.joml.Quaternionf().rotateZ(-easeLift * 0.2f));
            graphics.pose().translate(-(x + PACK_W), -cutY - rise, 0);
        }
        float stripAlpha = lift < 0.65f ? 1f : 1f - (lift - 0.65f) / 0.35f;
        graphics.setColor(1f, 1f, 1f, stripAlpha);
        graphics.blit(PACK_TOP, x, y, PACK_W, PACK_TOP_H, 0f, 0f, PACK_W, PACK_TOP_H, PACK_W, PACK_TOP_H);
        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();

        if (!cutting) return;
        // The slice of light: the seam stays lit behind the blade, with a soft halo, and the tip flares.
        int tip = x + Math.round(PACK_W * (1f - (1f - blade) * (1f - blade)));
        graphics.fill(x, cutY - 1, tip, cutY + 1, white(fade));
        graphics.fill(x, cutY - 3, tip, cutY + 3, white(0.45f * fade));
        graphics.fill(x, cutY - 6, tip, cutY + 6, white(0.2f * fade));
        if (blade < 1f) {
            graphics.fill(tip - 14, cutY - 1, tip + 2, cutY + 1, white(1f));
            graphics.fill(tip - 1, cutY - 7, tip + 1, cutY + 7, white(0.9f));
            graphics.fill(tip - 3, cutY - 2, tip + 3, cutY + 2, white(0.8f));
        }
    }

    private void drawCards(GuiGraphics graphics, int mouseX, int mouseY, long t) {
        List<RentalDraftPayload.Card> cards = currentCards();
        float scale = scale();
        long since = t - stageStart;
        hoverIndex = stage == Stage.CHOOSING ? topCardAt(mouseX, mouseY, cards.size()) : -1;
        // Left to right, so a later card lies over an earlier one; kept cards after those, and the hovered card last of all.
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < cards.size(); i++) {
                boolean hover = i == hoverIndex;
                if ((hover ? 2 : selected.contains(i) ? 1 : 0) != pass) continue;
                int[] r = cardRect(i, cards.size(), scale);
                float flip = 1f;
                if (stage == Stage.REVEALING) {
                    int position = revealOrder.indexOf(i);
                    flip = PackReveal.flip(since - PackReveal.flipStart(position));
                }
                boolean chosen = selected.contains(i);
                int lift = chosen ? -10 : hover ? -8 : 0;
                drawOne(graphics, cards.get(i), r[0], r[1] + lift, r[2], r[3], flip, chosen, t, scale);
            }
        }
    }

    private void drawTeam(GuiGraphics graphics, int mouseX, int mouseY, long t) {
        List<RentalDraftPayload.Card> team = new ArrayList<>();
        for (RentalDraftPayload.Pack kept : draft.packs()) {
            for (int index : kept.kept()) {
                if (index >= 0 && index < kept.cards().size()) team.add(kept.cards().get(index));
            }
        }
        float scale = scale();
        hoverIndex = topCardAt(mouseX, mouseY, team.size());
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < team.size(); i++) {
                boolean hover = i == hoverIndex;
                if (hover != (pass == 1)) continue;
                int[] r = cardRect(i, team.size(), scale);
                drawOne(graphics, team.get(i), r[0], r[1] + (hover ? -8 : 0), r[2], r[3], 1f, false, t, scale);
            }
        }
    }

    /** One card at one point of its turn: the back before halfway, the face after, squeezed sideways through the middle. */
    private void drawOne(GuiGraphics graphics, RentalDraftPayload.Card card, int x, int y, int w, int h, float flip,
                         boolean chosen, long t, float scale) {
        float squeeze = (float) Math.abs(Math.cos(Math.PI * flip));
        if (flip >= 1f || flip <= 0f) squeeze = 1f;
        int cx = x + w / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(cx, 0, 0);
        graphics.pose().scale(Math.max(0.02f, squeeze), 1f, 1f);
        graphics.pose().translate(-cx, 0, 0);
        if (flip >= 0.5f) {
            int rank = PackReveal.rank(card.rarity());
            if (rank >= 3 && flip >= 1f) glow(graphics, x, y, w, h, PackReveal.colorAt(card.rarity(), t), t, rank);
            // The card is drawn at its natural size and scaled whole, so its text stays inside the frame at any GUI size.
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1f);
            face().drawFace(graphics, font, card, 0, 0, CARD_W, CARD_H, chosen, t);
            graphics.pose().popPose();
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(scale, scale, 1f);
            face().drawBack(graphics, font, 0, 0, CARD_W, CARD_H, t);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    /** A pulsing halo behind an epic or better card. */
    private static void glow(GuiGraphics graphics, int x, int y, int w, int h, int color, long t, int rank) {
        int base = 0x10 + rank * 3;
        int pulse = (int) (base + base * 0.5 * Math.sin(TowerUiSettings.motion?t / 220.0:0));
        for (int g = 9; g >= 3; g -= 3) {
            graphics.fill(x - g, y - g, x + w + g, y + h + g, (Math.max(0, Math.min(255, pulse)) << 24) | (color & 0xFFFFFF));
        }
    }

    private record Spark(double x, double y, double vx, double vy, long born, int life, int color) {}

    private void drawSparks(GuiGraphics graphics, long t) {
        if(!TowerUiSettings.motion){sparks.clear();return;}
        sparks.removeIf(spark -> t - spark.born() > spark.life());
        for (Spark spark : sparks) {
            double age = t - spark.born();
            int px = (int) (spark.x() + spark.vx() * age);
            int py = (int) (spark.y() + spark.vy() * age + 0.00006 * age * age);
            int alpha = (int) (255 * (1.0 - age / spark.life()));
            graphics.fill(px, py, px + 2, py + 2, (Math.max(0, alpha) << 24) | (spark.color() & 0xFFFFFF));
        }
    }

    private static boolean inside(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] - 10 && my < r[1] + r[3];
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if(button==1&&stage==Stage.CHOOSING&&!failed){var cards=currentCards();int top=topCardAt(mouseX,mouseY,cards.size());if(top>=0){minecraft.setScreen(new PartnerInspectionScreen(this,cards.get(top)));return true;}}
        if (button != 0 || failed) return false;
        switch (stage) {
            case TABLE -> {
                if (animations && TowerUiSettings.motion) {
                    enter(Stage.TEARING);
                    if(TowerUiSettings.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1f, 0.7f));
                } else {
                    enter(Stage.CHOOSING);
                }
                refreshButtons();
                return true;
            }
            case TEARING -> {
                return true;
            }
            case REVEALING -> {
                enter(Stage.CHOOSING);
                refreshButtons();
                return true;
            }
            case CHOOSING -> {
                List<RentalDraftPayload.Card> cards = currentCards();
                int i = topCardAt(mouseX, mouseY, cards.size());
                if (i >= 0) {
                    if (selected.contains(i)) {
                        selected.remove((Integer) i);
                    } else if (selected.size() < 2) {
                        selected.add(i);
                    } else {
                        // A third pick replaces the oldest, so changing your mind is one click, not two.
                        selected.remove(0);
                        selected.add(i);
                    }
                    message = "";
                    refreshButtons();
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode==GLFW.GLFW_KEY_I&&stage==Stage.CHOOSING&&!selected.isEmpty()){minecraft.setScreen(new PartnerInspectionScreen(this,currentCards().get(selected.getFirst())));return true;}
        if (keyCode == GLFW.GLFW_KEY_SPACE && (stage == Stage.REVEALING || stage == Stage.TEARING)) {
            enter(Stage.CHOOSING);
            refreshButtons();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
