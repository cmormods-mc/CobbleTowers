package com.cobbletowers.client;

import com.cobbletowers.network.VendorCatalogPayload;
import com.cobbletowers.network.VendorPurchasePayload;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The Tower Supply Vendor's shop (TDS #16), reached by {@code /cobbletowers runs vendor} or the
 * intermission screen's Vendor button rather than a physical NPC (see the design doc's scope decision).
 *
 * <p>P19: a row of teammate buttons chooses who a purchase is for (TDS #18: "may pay for recovery
 * targeted at teammates"). The default is the buyer; the choice survives the refresh that follows every
 * purchase, so buying two services for the same teammate takes no re-selecting. The server re-checks the
 * target (in the run, online) and answers with a message that is shown at the bottom.
 */
public final class VendorScreen extends Screen {

    private VendorCatalogPayload catalog;
    /** Who a purchase is for. Null until the first catalog arrives, then the buyer. */
    private UUID target;

    public VendorScreen(VendorCatalogPayload catalog) {
        super(Component.literal("Tower Supply Vendor"));
        this.catalog = catalog;
        this.target = defaultTarget(catalog);
    }

    /** Called when a fresh catalog arrives (e.g. right after a purchase) while this screen is open. */
    public void updateCatalog(VendorCatalogPayload catalog) {
        this.catalog = catalog;
        // Keep the choice unless that teammate has left the run.
        boolean stillThere = catalog.team().stream().anyMatch(member -> member.id().equals(target));
        if (!stillThere) target = defaultTarget(catalog);
        clearWidgets();
        buildWidgets();
    }

    private static UUID defaultTarget(VendorCatalogPayload catalog) {
        return catalog.team().isEmpty() ? null : catalog.team().get(0).id();
    }

    @Override
    protected void init() {
        buildWidgets();
    }

    private int top() {
        int rows = catalog.services().size() + (catalog.team().size() > 1 ? 1 : 0);
        return height / 2 - 12 * rows;
    }

    private void buildWidgets() {
        int y = top();
        if (catalog.team().size() > 1) {
            int count = catalog.team().size();
            int each = Math.min(96, 200 / count);
            int x = width / 2 - each * count / 2;
            for (VendorCatalogPayload.Teammate member : catalog.team()) {
                boolean chosen = member.id().equals(target);
                Button button = Button.builder(Component.literal((chosen ? "> " : "") + member.name()),
                                b -> choose(member.id()))
                        .pos(x, y).size(each - 2, 20).build();
                button.active = member.online() && !chosen;
                addRenderableWidget(button);
                x += each;
            }
            y += 26;
        }
        // As wide as the longest label needs (a long service name must not be cut off), never narrower than 200 or wider than the screen.
        int wide = 200;
        for (VendorCatalogPayload.Entry entry : catalog.services()) {
            wide = Math.max(wide, font.width(label(entry)) + 16);
        }
        wide = Math.min(wide, width - 12);
        for (VendorCatalogPayload.Entry entry : catalog.services()) {
            boolean canAfford = catalog.cobbleDollars() >= entry.priceCobbleDollars();
            boolean inStock = entry.remainingPurchases() != 0;
            Button button = Button.builder(Component.literal(label(entry)), b -> buy(entry.id()))
                    .pos(width / 2 - wide / 2, y)
                    .size(wide, 20)
                    .build();
            button.active = canAfford && inStock && target != null;
            addRenderableWidget(button);
            y += 24;
        }
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .pos(width / 2 - 50, y + 10)
                .size(100, 20)
                .build());
    }

    private static String label(VendorCatalogPayload.Entry entry) {
        String stock = entry.remainingPurchases() < 0 ? "" : " (" + entry.remainingPurchases() + " left)";
        return entry.displayName() + " -- " + entry.priceCobbleDollars() + stock;
    }

    private void choose(UUID id) {
        target = id;
        clearWidgets();
        buildWidgets();
    }

    private void buy(ResourceLocation serviceId) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || target == null) return;
        if (ClientPlayNetworking.canSend(VendorPurchasePayload.TYPE)) {
            ClientPlayNetworking.send(new VendorPurchasePayload(serviceId, target));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "CobbleDollars: " + catalog.cobbleDollars(),
                width / 2, top() - 28, 0xFFFFFF);
        if (catalog.team().size() > 1) {
            graphics.drawCenteredString(font, "Buying for:", width / 2, top() - 14, 0xAAAAAA);
        }
        if (!catalog.message().isEmpty()) {
            graphics.drawCenteredString(font, catalog.message(), width / 2, height - 20, 0xFFFF55);
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
