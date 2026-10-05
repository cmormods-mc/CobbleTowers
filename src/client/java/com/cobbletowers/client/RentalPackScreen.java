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

    private static final int CARD_W = 96;
    private static final int CARD_H = 140;
    private static final int GAP = 8;

    /** Whether to draw cards as CobblemonCards cards when that mod is installed; kept for the session like the animation setting. */
    private static boolean collectionLook = true;

    private CardFace face() {
        return collectionLook && CobblemonCardFace.available() ? CobblemonCardFace.INSTANCE : FallbackCardFace.INSTANCE;
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

    private void enter(Stage next) {
        stage = next;
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
        return Component.literal("Cards: " + (collectionLook ? "Collection" : "Plain"));
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
        restart.setWidth(64);
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

    float scale() {
        float fit = (width - 16f) / (5 * CARD_W + 4 * GAP);
        float tall = (height - 110f) / CARD_H;
        return Math.max(0.5f, Math.min(1f, Math.min(fit, tall)));
    }

    /** x, y, width, height of card {@code index} in a row of {@code count} cards. */
    int[] cardRect(int index, int count, float scale) {
        int w = Math.round(CARD_W * scale);
        int h = Math.round(CARD_H * scale);
        int gap = Math.round(GAP * scale);
        int total = count * w + (count - 1) * gap;
        int left = (width - total) / 2;
        return new int[] {left + index * (w + gap), (height - h) / 2 - 4, w, h};
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

    private void drawPack(GuiGraphics graphics, long t) {
        int w = 110;
        int h = 150;
        int x = (width - w) / 2;
        int y = (height - h) / 2 - 6;
        // The sealed pack keeps its secret: gold for the God Pack, otherwise one neutral blue, never the best card colour.
        int glow = godPack() ? TowerUi.BRONZE_LIGHT : TowerUi.BURGUNDY;
        long since = t - stageStart;
        // The pack glows more as it tears: the suspense.
        float tear = stage == Stage.TEARING ? Math.min(1f, since / (float) PackReveal.TEAR_MS) : 0f;
        int pulse = (int) (0x30 + 0x30 * Math.sin(t / 300.0) + 0x60 * tear);
        for (int g = 9; g >= 3; g -= 3) {
            graphics.fill(x - g, y - g, x + w + g, y + h + g, (Math.min(255, pulse / (g / 2)) << 24) | (glow & 0xFFFFFF));
        }
        graphics.fill(x, y, x + w, y + h, TowerUi.OAK);
        graphics.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF5A3A28, 0xFF2A1C15);
        graphics.renderOutline(x, y, w, h, TowerUi.BRONZE);
        graphics.fill(x, y + h / 2 - 1, x + w, y + h / 2 + 1, TowerUi.BRONZE);
        int cx = x + w / 2;
        int cy = y + h / 2;
        graphics.fill(cx - 7, cy - 7, cx + 7, cy + 7, TowerUi.BRONZE);
        graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, TowerUi.OAK);
        if (stage == Stage.TEARING) {
            // The seal splits: a bright seam widening across the middle.
            int seam = (int) (tear * 14);
            graphics.fill(x, cy - seam, x + w, cy + seam, 0xCCFFFFFF);
        }
        graphics.drawCenteredString(font, "RENTAL PACK", cx, y + 10, TowerUi.TEXT);
        graphics.drawCenteredString(font, (pack + 1) + " / " + draft.packs().size(), cx, y + h - 16, TowerUi.BRONZE);
    }

    private void drawCards(GuiGraphics graphics, int mouseX, int mouseY, long t) {
        List<RentalDraftPayload.Card> cards = currentCards();
        float scale = scale();
        long since = t - stageStart;
        for (int i = 0; i < cards.size(); i++) {
            int[] r = cardRect(i, cards.size(), scale);
            float flip = 1f;
            if (stage == Stage.REVEALING) {
                int position = revealOrder.indexOf(i);
                flip = PackReveal.flip(since - PackReveal.flipStart(position));
            }
            boolean chosen = selected.contains(i);
            boolean hover = stage == Stage.CHOOSING && inside(mouseX, mouseY, r);
            int lift = chosen ? -10 : hover ? -4 : 0;
            drawOne(graphics, cards.get(i), r[0], r[1] + lift, r[2], r[3], flip, chosen, t, scale);
        }
    }

    private void drawTeam(GuiGraphics graphics, int mouseX, int mouseY, long t) {
        List<RentalDraftPayload.Card> team = new ArrayList<>();
        for (RentalDraftPayload.Pack kept : draft.packs()) {
            for (int index : kept.kept()) {
                if (index >= 0 && index < kept.cards().size()) team.add(kept.cards().get(index));
            }
        }
        float scale = Math.max(0.5f, Math.min(1f, (width - 16f) / (team.size() * (CARD_W + GAP))));
        for (int i = 0; i < team.size(); i++) {
            int[] r = cardRect(i, team.size(), scale);
            drawOne(graphics, team.get(i), r[0], r[1], r[2], r[3], 1f, false, t, scale);
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
        if(button==1&&stage==Stage.CHOOSING&&!failed){var cards=currentCards();for(int i=0;i<cards.size();i++)if(inside(mouseX,mouseY,cardRect(i,cards.size(),scale()))){minecraft.setScreen(new PartnerInspectionScreen(this,cards.get(i)));return true;}}
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
                for (int i = 0; i < cards.size(); i++) {
                    if (!inside(mouseX, mouseY, cardRect(i, cards.size(), scale()))) continue;
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
