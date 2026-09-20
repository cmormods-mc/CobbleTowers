package com.cobbletowers.client;

import com.cobbletowers.network.VendorCatalogPayload;
import com.cobbletowers.network.VendorPurchasePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Tower Supply Vendor's shop (TDS #16), reached by {@code /cobbletowers runs vendor} rather than
 * a physical NPC (see the design doc's scope decision).
 *
 * <p>Every buy button targets the local player. TDS #18's "recovery targeted at teammates" is carried
 * by the wire protocol ({@link VendorPurchasePayload#targetPlayerId}) but not yet reachable from this
 * screen -- a teammate picker is a client-side enhancement the protocol does not need to change for.
 */
public final class VendorScreen extends Screen {

    private VendorCatalogPayload catalog;

    public VendorScreen(VendorCatalogPayload catalog) {
        super(Component.literal("Tower Supply Vendor"));
        this.catalog = catalog;
    }

    /** Called when a fresh catalog arrives (e.g. right after a purchase) while this screen is open. */
    public void updateCatalog(VendorCatalogPayload catalog) {
        this.catalog = catalog;
        clearWidgets();
        buildWidgets();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private void buildWidgets() {
        int y = height / 2 - 20 * catalog.services().size() / 2;
        for (VendorCatalogPayload.Entry entry : catalog.services()) {
            boolean canAfford = catalog.cobbleDollars() >= entry.priceCobbleDollars();
            boolean inStock = entry.remainingPurchases() != 0;
            String stock = entry.remainingPurchases() < 0 ? "" : " (" + entry.remainingPurchases() + " left)";
            Button button = Button.builder(
                    Component.literal(entry.displayName() + " -- " + entry.priceCobbleDollars() + stock),
                    b -> buy(entry.id()))
                    .pos(width / 2 - 100, y)
                    .size(200, 20)
                    .build();
            button.active = canAfford && inStock;
            addRenderableWidget(button);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(width / 2 - 50, y + 10)
                .size(100, 20)
                .build());
    }

    private void buy(ResourceLocation serviceId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (ClientPlayNetworking.canSend(VendorPurchasePayload.TYPE)) {
            ClientPlayNetworking.send(new VendorPurchasePayload(serviceId, player.getUUID()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "CobbleDollars: " + catalog.cobbleDollars(),
                width / 2, height / 2 - 20 * catalog.services().size() / 2 - 16, 0xFFFFFF);
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
