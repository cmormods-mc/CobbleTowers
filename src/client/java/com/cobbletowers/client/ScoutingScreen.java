package com.cobbletowers.client;

import com.cobbletowers.network.ScoutingRevealPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What a floor's scouting profile reveals about its draw (TDS #22, #49), shown before the encounter
 * rather than gating it -- a slow or absent screen must never delay a floor.
 *
 * <p>Read-only, the same shape {@link RewardRevealScreen} already is: nothing here is a choice.
 */
public final class ScoutingScreen extends Screen {

    private final ScoutingRevealPayload payload;

    public ScoutingScreen(ScoutingRevealPayload payload) {
        super(Component.literal("Scouting Report"));
        this.payload = payload;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(width / 2 - 50, height / 2 + 40)
                .size(100, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        int y = height / 2 - 40;
        graphics.drawCenteredString(font, "Floor " + payload.floorIndex(), width / 2, y, 0xFFFFFF);
        y += 14;
        if (payload.categories().isEmpty()) {
            graphics.drawCenteredString(font, "Nothing revealed", width / 2, y, 0xAAAAAA);
        }
        for (ScoutingRevealPayload.Category category : payload.categories()) {
            graphics.drawCenteredString(font, category.name() + ": " + category.value(), width / 2, y, 0xFFFFFF);
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
