package com.cobbletowers.client;

import com.cobbletowers.network.RewardRevealPayload;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The reward reveal the design doc replaces P9's chat-only stopgap with (TDS #9's grants shown, not
 * chosen).
 *
 * <p>Read-only: a grant is never a player choice ("No player choice in what is granted",
 * {@code docs/design/P9-economy.md}), so there is nothing here to drag, click or confirm beyond
 * dismissing the screen once it has been read.
 */
public final class RewardRevealScreen extends Screen {

    private final int floorIndex;
    private final List<RewardRevealPayload.Grant> grants;

    public RewardRevealScreen(RewardRevealPayload payload) {
        super(Component.literal("Tower Rewards"));
        this.floorIndex = payload.floorIndex();
        this.grants = payload.grants();
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .pos(width / 2 - 50, height / 2 + 40)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int y = height / 2 - 40;
        graphics.drawCenteredString(font, "Rewards through floor " + floorIndex, width / 2, y, 0xFFFFFF);
        y += 14;
        for (RewardRevealPayload.Grant grant : grants) {
            graphics.drawCenteredString(font, grant.item().getPath() + " x" + grant.amount(), width / 2, y, 0xFFFFFF);
            y += 12;
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
