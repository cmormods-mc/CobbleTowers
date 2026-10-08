package com.cobbletowers.client;

import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.IntermissionStatePayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * The between-floors menu (P17): modifier choice, vendor, ready-up and cash-out vote. Holds nothing the server
 * decides; Confirm sends the vote and the server answers with a fresh {@link IntermissionStatePayload}. Reduced
 * motion or Skip shows cards at once.
 */
public final class IntermissionScreen extends TowerScreen implements ModifierCardButton.View {

    /** The reveal: a short beat, then each card's shutters slide apart a little after the last. */
    private static final long ANTICIPATION_MS = 650, STAGGER_MS = 120, OPEN_MS = 520;
    /** A confirm with no answer after this long is treated as lost, so the button comes back. */
    private static final long PENDING_TIMEOUT_MS = 8_000;

    private IntermissionStatePayload state;
    private int selected = -1;
    private boolean pending;
    private long pendingAt;
    private boolean skipped;
    private long revealStart;
    private boolean latchPlayed;
    private boolean[] landed = new boolean[0];
    private int page;
    private int inspected = -1;
    private List<FormattedCharSequence> body = List.of();

    // Layout, worked out in buildWidgets.
    private int panelX, panelW, sheetX, sheetY, sheetW, sheetH, linesPerPage, actionsTop;

    public IntermissionScreen(IntermissionStatePayload state) {
        super(Component.literal("Intermission"), TowerUi.Theme.MODIFIER);
        this.state = state;
        resetOffer();
    }

    private static boolean sameOffer(IntermissionStatePayload a, IntermissionStatePayload b) {
        if (a.floor() != b.floor() || a.draft().cards().size() != b.draft().cards().size()) return false;
        for (int i = 0; i < a.draft().cards().size(); i++) {
            if (!a.draft().cards().get(i).id().equals(b.draft().cards().get(i).id())) return false;
        }
        return true;
    }

    /** A new offer: the shutters close again, and the choice starts from the player's own vote. */
    private void resetOffer() {
        revealStart = System.nanoTime();
        skipped = false;
        latchPlayed = false;
        landed = new boolean[state.draft().cards().size()];
        selected = state.draft().state() == 2 ? state.draft().chosen() : state.draft().myVote();
        page = 0;
        inspected = -1;
        pending = false;
    }

    /**
     * A fresh state arrived while this screen is open. A refresh of the same offer keeps the choice and never replays
     * the reveal.
     */
    public void update(IntermissionStatePayload next) {
        boolean same = sameOffer(state, next);
        this.state = next;
        if (!same) {
            resetOffer();
        } else {
            pending = false;
            var draft = next.draft();
            if (draft.state() == 2) selected = draft.chosen();
            else if (selected >= draft.cards().size()) selected = draft.myVote();
        }
        clearWidgets();
        buildWidgets();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    // ---- ModifierCardButton.View
    // ----------------------------------------------------------------------------------------

    private boolean motionOn() {
        return TowerUiSettings.motion && !skipped;
    }

    private long revealMs() {
        return (System.nanoTime() - revealStart) / 1_000_000L;
    }

    @Override
    public float reveal(int index) {
        if (!motionOn()) return 1f;
        long start = ANTICIPATION_MS + STAGGER_MS * index;
        return Math.max(0f, Math.min(1f, (revealMs() - start) / (float) OPEN_MS));
    }

    private boolean revealed() {
        int n = state.draft().cards().size();
        return n == 0 || reveal(n - 1) >= 1f;
    }

    @Override
    public boolean selected(int index) {
        return state.draft().state() == 1 && index == selected;
    }

    @Override
    public boolean chosen(int index) {
        return state.draft().state() == 2 && index == state.draft().chosen();
    }

    @Override
    public boolean settled() {
        return state.draft().state() == 2;
    }

    // ---- building
    // -----------------------------------------------------------------------------------------------------

    private boolean mine(boolean ready) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) return false;
        String name = me.getGameProfile().getName();
        for (IntermissionStatePayload.Member member : state.members()) {
            if (member.name().equals(name)) return ready ? member.ready() : member.cashOut();
        }
        return false;
    }

    private void buildWidgets() {
        IntermissionStatePayload.Draft draft = state.draft();
        boolean open = draft.state() == 1;
        int n = draft.cards().size();

        panelW = Math.min(width - 12, 440);
        panelX = (width - panelW) / 2;
        int gap = 4;
        int cardW = n == 0 ? 0 : Math.min(124, (panelW - 12 - (n - 1) * gap) / n);
        int cardH = Math.max(78, Math.min(96, height * 32 / 100));
        int cardsLeft = (width - (n * cardW + Math.max(0, n - 1) * gap)) / 2;
        int cardsTop = 32;

        for (int i = 0; i < n; i++) {
            int index = i;
            ModifierCardButton card = new ModifierCardButton(cardsLeft + i * (cardW + gap), cardsTop, cardW, cardH, i,
                    draft.cards().get(i), this, () -> choose(index));
            card.active = open && state.countdown() < 0 && !pending && reveal(i) >= 1f;
            addRenderableWidget(card);
        }

        int bottom = height - 8;
        actionsTop = bottom - 56;
        sheetX = panelX + 6;
        sheetW = panelW - 12;
        sheetY = (n == 0 ? cardsTop : cardsTop + cardH) + 6;
        sheetH = Math.max(36, actionsTop - 6 - sheetY);
        linesPerPage = Math.max(1, (sheetH - 26) / 10);

        int rowW = panelW - 12;
        int confirmW = rowW * 58 / 100 - 2;
        Button confirm = TowerButton.builder(Component.literal(confirmLabel(draft)),
                        b -> confirm()).pos(sheetX, actionsTop).size(confirmW, 18).build();
        confirm.active = open && state.countdown() < 0 && !pending && selected >= 0 && selected != draft.myVote() && revealed();
        addRenderableWidget(confirm);
        addRenderableWidget(TowerButton.builder(Component.literal("Close"), b -> onClose())
                .pos(sheetX + confirmW + 4, actionsTop).size(rowW - confirmW - 4, 18).build());

        int third = (rowW - 8) / 3;
        addRenderableWidget(TowerButton.builder(Component.literal("Vendor"), b -> send(IntermissionActionPayload.Action.VENDOR, 0))
                .pos(sheetX, actionsTop + 21).size(third, 18).build());
        boolean ready = mine(true);
        Button readyButton = TowerButton.builder(Component.literal(ready ? "Not ready" : "Ready"),
                        b -> send(ready ? IntermissionActionPayload.Action.UNREADY : IntermissionActionPayload.Action.READY, 0))
                .pos(sheetX + third + 4, actionsTop + 21).size(third, 18).build();
        readyButton.active = ready || !open;
        addRenderableWidget(readyButton);
        boolean cashing = mine(false);
        addRenderableWidget(TowerButton.builder(Component.literal(cashing ? "Keep going" : "Cash out"),
                        b -> send(cashing ? IntermissionActionPayload.Action.STAY : IntermissionActionPayload.Action.CASH_OUT, 0))
                .pos(sheetX + (third + 4) * 2, actionsTop + 21).size(rowW - (third + 4) * 2, 18).build());

        // What this run is carrying: every modifier and relic, with what each does (the same view the Hall's codex links to).
        addRenderableWidget(TowerButton.builder(Component.literal("This run"), b -> TowerFeatureScreen.open(this, "run"))
                .pos(panelX + panelW - 116, 9).size(62, 16).build());
        if (!revealed() && motionOn()) {
            addRenderableWidget(TowerButton.builder(Component.literal("Skip"), b -> {
                        skipped = true;
                        clearWidgets();
                        buildWidgets();
                    }).pos(panelX + panelW - 50, 9).size(40, 16).build());
        }
        rebuildBody();
        if (pageCount() > 1) {
            addRenderableWidget(TowerButton.builder(Component.literal("<"), b -> turn(-1))
                    .pos(sheetX + sheetW - 48, sheetY + sheetH - 17).size(20, 14).build());
            addRenderableWidget(TowerButton.builder(Component.literal(">"), b -> turn(1))
                    .pos(sheetX + sheetW - 26, sheetY + sheetH - 17).size(20, 14).build());
        }
    }

    private String confirmLabel(IntermissionStatePayload.Draft draft) {
        if (pending) return "Sending...";
        if (draft.state() == 2) return "Settled";
        if (draft.state() != 1) return "Confirm";
        if (selected < 0) return "Choose a card";
        if (selected == draft.myVote()) return "Voted";
        return "Confirm";
    }

    private void choose(int index) {
        if (pending || state.draft().state() != 1) return;
        selected = index;
        clearWidgets();
        buildWidgets();
    }

    private void confirm() {
        if (pending || selected < 0 || state.draft().state() != 1) return;
        pending = true;
        pendingAt = System.nanoTime();
        send(IntermissionActionPayload.Action.PICK_CARD, selected);
        clearWidgets();
        buildWidgets();
    }

    private void turn(int by) {
        int pages = pageCount();
        page = Math.floorMod(page + by, pages);
    }

    private int pageCount() {
        return Math.max(1, (body.size() + linesPerPage - 1) / linesPerPage);
    }

    /** The card the parchment describes: the one under the pointer or focus, else the chosen one, else the first. */
    private int inspectedNow() {
        for (var child : children()) {
            if (child instanceof ModifierCardButton card && card.isHoveredOrFocused()) {
                return children().indexOf(card);
            }
        }
        return selected >= 0 && selected < state.draft().cards().size() ? selected : state.draft().cards().isEmpty() ? -1 : 0;
    }

    private void rebuildBody() {
        inspected = inspectedNow();
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (inspected >= 0 && inspected < state.draft().cards().size()) {
            for (String line : state.draft().cards().get(inspected).lines()) {
                lines.addAll(font.split(TowerUi.styled(line), Math.max(40, sheetW - 14)));
            }
        }
        body = lines;
        page = Math.min(page, pageCount() - 1);
    }

    private void send(IntermissionActionPayload.Action action, int argument) {
        if (ClientPlayNetworking.canSend(IntermissionActionPayload.TYPE)) {
            ClientPlayNetworking.send(new IntermissionActionPayload(action, argument));
        }
    }

    // ---- once-per-stage sounds, and the pending timeout: from the tick, never from the render loop
    // ----------------------------

    @Override
    public void tick() {
        super.tick();
        if (pending && (System.nanoTime() - pendingAt) / 1_000_000L > PENDING_TIMEOUT_MS) {
            pending = false;
            clearWidgets();
            buildWidgets();
        }
        boolean wasRevealed = true;
        if (motionOn()) {
            long ms = revealMs();
            if (!latchPlayed && ms >= ANTICIPATION_MS) {
                latchPlayed = true;
                play(SoundEvents.BARREL_OPEN, 0.9f, 0.35f);
            }
            for (int i = 0; i < landed.length; i++) {
                if (!landed[i] && reveal(i) >= 1f) {
                    landed[i] = true;
                    play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8f + i * 0.05f, 0.3f);
                    wasRevealed = false;
                }
            }
            // Cards become pressable as their shutters finish; rebuild once as each one lands.
            if (!wasRevealed) {
                clearWidgets();
                buildWidgets();
            }
        } else if (!latchPlayed) {
            latchPlayed = true;   // reduced motion or skipped: no reveal, so no cues
            java.util.Arrays.fill(landed, true);
        }
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        if (TowerUiSettings.sounds) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ---- drawing
    // ------------------------------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        super.renderBackground(g, mx, my, dt);
        TowerUi.panel(g, panelX, 4, panelW, height - 8, theme.accent);
        PixelUi.sheet(g, sheetX, sheetY, sheetW, sheetH);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (inspectedNow() != inspected) {
            // What the pointer rests on changed: the parchment follows it. The widgets stay exactly where they are.
            rebuildBody();
        }
        graphics.drawCenteredString(font, "Floor " + state.floor() + " cleared", width / 2, 12, TowerUi.TEXT);
        String hint = state.countdown() >= 0 ? "Next floor in " + state.countdown() + "..."
                : !state.message().isEmpty() ? state.message()
                : switch (state.draft().state()) {
                    case 1 -> pending ? "Sending your vote..." : "Choose a modifier for the next floor";
                    case 2 -> "Modifier chosen";
                    default -> "No modifier this floor";
                };
        TowerUi.label(graphics, font, hint, panelX + 12, 22, panelW - 24, state.countdown() >= 0 ? TowerUi.BRONZE_LIGHT : TowerUi.MUTED);

        // The parchment: the inspected card's real description, a page at a time.
        var cards = state.draft().cards();
        if (!revealed() && !cards.isEmpty()) {
            // Behind the shutters: the parchment does not give away what is still closed.
            TowerUi.label(graphics, font, "The offer is being revealed...", sheetX + 7, sheetY + 7, sheetW - 14, 0xFF6B4A33);
        } else if (inspected < 0 || inspected >= cards.size()) {
            TowerUi.label(graphics, font, "No modifier is on offer this floor.", sheetX + 7, sheetY + 7, sheetW - 14, TowerUi.INK);
            TowerUi.label(graphics, font, "You can still visit the vendor, ready up or cash out.", sheetX + 7, sheetY + 19, sheetW - 14, 0xFF6B4A33);
        } else {
            var card = cards.get(inspected);
            TowerUi.label(graphics, font, card.displayName() + "  -  " + ModifierCardButton.riskLabel(card.risk()) + (card.risk() >= 0 ? " risk" : ""),
                    sheetX + 7, sheetY + 6, sheetW - 14, TowerUi.INK);
            int y = sheetY + 18;
            int first = page * linesPerPage;
            for (int i = first; i < Math.min(body.size(), first + linesPerPage); i++) {
                graphics.drawString(font, body.get(i), sheetX + 7, y, TowerUi.INK, false);
                y += 10;
            }
            if (pageCount() > 1) {
                graphics.drawString(font, (page + 1) + "/" + pageCount(), sheetX + sheetW - 74, sheetY + sheetH - 14, 0xFF6B4A33, false);
            }
        }

        // The team, on one line: ready is sage, waiting is muted.
        int x = panelX + 12;
        int y = actionsTop + 44;
        for (IntermissionStatePayload.Member member : state.members()) {
            String status = member.name() + " " + (member.ready() ? "ready" : "waiting") + (member.cashOut() ? " (cashing out)" : "") + "   ";
            if (x + font.width(status) > panelX + panelW - 8) break;
            graphics.drawString(font, status, x, y, member.ready() ? TowerUi.SAGE : TowerUi.MUTED, false);
            x += font.width(status);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
