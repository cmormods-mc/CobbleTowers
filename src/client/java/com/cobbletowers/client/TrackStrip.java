package com.cobbletowers.client;

import com.cobbletowers.network.TrackStatePayload;
import com.cobbletowers.network.TrackStatePayload.Node;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * One battle-track lane (P37): a row of reward tiles that scrolls sideways (wheel, drag, Left/Right, {@link
 * #jumpToCurrent}), opens on the current node and marks claimable, claimed and locked tiles. Only visible tiles are
 * drawn, each one batched frame.
 */
final class TrackStrip extends AbstractWidget {
    static final int TILE_W = 38, TILE_H = 46, GAP = 4, PITCH = TILE_W + GAP;

    private final TrackStatePayload.Lane lane;
    private final String kind;
    private final Runnable onSelect;
    private int selected = -1;
    private double scroll, target;
    private double pressX = Double.NaN;
    private boolean dragged;
    private final long opened = System.nanoTime();

    TrackStrip(int x, int y, int w, int h, String kind, TrackStatePayload.Lane lane, int selected, double target, Runnable onSelect) {
        super(x, y, w, h, Component.literal(lane.title()));
        this.kind = kind;
        this.lane = lane;
        this.onSelect = onSelect;
        this.selected = selected >= 0 && selected < lane.nodes().size() ? selected : -1;
        if (Double.isNaN(target)) {
            this.target = centredOn(currentIndex());
            this.scroll = this.target;
        } else {
            this.target = clamp(target);
            this.scroll = this.target;
        }
    }

    String kind() {
        return kind;
    }

    int selectedIndex() {
        return selected;
    }

    double scrollTarget() {
        return target;
    }

    Node selectedNode() {
        return selected >= 0 && selected < lane.nodes().size() ? lane.nodes().get(selected) : null;
    }

    boolean anyClaimable() {
        for (Node node : lane.nodes()) if (node.state() == TrackStatePayload.CLAIMABLE) return true;
        return false;
    }

    /** The index of the node the player is on: the level or step reached, or the first node if none yet. */
    int currentIndex() {
        return Math.max(0, Math.min(lane.nodes().size() - 1, lane.current() - 1));
    }

    private double centredOn(int index) {
        return clamp(index * PITCH - (width - TILE_W) / 2.0);
    }

    private double clamp(double value) {
        double max = Math.max(0, lane.nodes().size() * PITCH - GAP - width);
        return Math.max(0, Math.min(max, value));
    }

    void jumpToCurrent() {
        target = centredOn(currentIndex());
    }

    private void select(int index) {
        if (index < 0 || index >= lane.nodes().size()) return;
        selected = index;
        double left = index * PITCH, right = left + TILE_W;
        if (left < target) target = clamp(left - 4);
        else if (right > target + width) target = clamp(right - width + 4);
        onSelect.run();
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float delta) {
        scroll += (target - scroll) * 0.35;
        if (Math.abs(target - scroll) < 0.5) scroll = target;
        var font = TowerFonts.get();
        int x = getX(), y = getY();
        g.fill(x, y, x + width, y + height, 0x66000000);
        g.enableScissor(x, y, x + width, y + height);
        int first = Math.max(0, (int) Math.floor(scroll / PITCH));
        int last = Math.min(lane.nodes().size() - 1, (int) Math.floor((scroll + width) / PITCH));
        long age = (System.nanoTime() - opened) / 1_000_000L;
        boolean pulse = TowerUiSettings.motion && (age / 500L) % 2 == 0;
        int tileY = y + (height - TILE_H) / 2;
        for (int i = first; i <= last; i++) {
            Node node = lane.nodes().get(i);
            int tx = x + (int) Math.round(i * PITCH - scroll);
            boolean hover = isHoveredOrFocused() && mx >= tx && mx < tx + TILE_W && my >= tileY && my < tileY + TILE_H;
            tile(g, font, node, tx, tileY, i == selected, i == currentIndex(), hover, pulse);
        }
        g.disableScissor();
        // Edge arrows hint that the strip scrolls.
        if (scroll > 0.5) g.drawString(font, "<", x + 1, y + height / 2 - 4, TowerUi.BRONZE_LIGHT, false);
        if (scroll < clamp(Double.MAX_VALUE) - 0.5) g.drawString(font, ">", x + width - 6, y + height / 2 - 4, TowerUi.BRONZE_LIGHT, false);
    }

    private void tile(GuiGraphics g, net.minecraft.client.gui.Font font, Node node, int x, int y, boolean selectedTile, boolean current,
                      boolean hover, boolean pulse) {
        boolean claimable = node.state() == TrackStatePayload.CLAIMABLE;
        PixelUi.Frame frame = claimable ? PixelUi.Frame.BUTTON : node.state() == TrackStatePayload.CLAIMED ? PixelUi.Frame.SECONDARY : PixelUi.Frame.DARK;
        PixelUi.frame(g, frame, x, y, TILE_W, TILE_H);
        int ink = node.state() == TrackStatePayload.LOCKED ? TowerUi.MUTED : TowerUi.TEXT;
        g.drawString(font, Integer.toString(node.number()), x + 5, y + 4, ink, false);
        int cx = x + (TILE_W - 16) / 2, cy = y + 15;
        if (!node.icon().isEmpty()) {
            drawItem(g, node.icon(), cx, cy);
        } else if (!node.note().isEmpty()) {
            MenuIcons.draw(g, node.note().contains("rank") ? "star" : "gear", cx, cy, 16);
        }
        switch (node.state()) {
            case TrackStatePayload.CLAIMED -> MenuIcons.draw(g, "check", x + TILE_W - 19, y + TILE_H - 19, 16);
            case TrackStatePayload.LOCKED -> {
                g.fill(x + 3, y + 3, x + TILE_W - 3, y + TILE_H - 3, 0x77000000);
                MenuIcons.draw(g, "lock", x + TILE_W - 19, y + TILE_H - 19, 16);
            }
            case TrackStatePayload.CLAIMABLE -> {
                if (pulse) PixelUi.brackets(g, x + 2, y + 2, TILE_W - 4, TILE_H - 4, TowerUi.BRONZE_LIGHT);
                g.drawString(font, "!", x + TILE_W - 9, y + 4, 0xFFE3BD7F, false);
            }
            default -> {
            }
        }
        if (current) g.fill(x + 3, y + TILE_H - 5, x + TILE_W - 3, y + TILE_H - 3, TowerUi.BRONZE_LIGHT);
        if (selectedTile) PixelUi.brackets(g, x, y, TILE_W, TILE_H, 0xFFF0DFBF);
        else if (hover) PixelUi.brackets(g, x + 1, y + 1, TILE_W - 2, TILE_H - 2, TowerUi.BRONZE_LIGHT);
    }

    /**
     * An item's own icon, or the mod's coin or gift picture when the item is a currency or this client does not have
     * it.
     */
    private static void drawItem(GuiGraphics g, String id, int x, int y) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        var item = location == null ? java.util.Optional.<net.minecraft.world.item.Item>empty() : BuiltInRegistries.ITEM.getOptional(location);
        if (item.isPresent() && !item.get().equals(net.minecraft.world.item.Items.AIR)) {
            g.renderItem(new ItemStack(item.get()), x, y);
        } else {
            MenuIcons.draw(g, id.endsWith("cobble_dollar") || id.endsWith("raid_points") ? "coin" : "gift", x, y, 16);
        }
    }

    // ---- input ------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !isMouseOver(mx, my)) return false;
        pressX = mx;
        dragged = false;
        setFocused(true);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (Double.isNaN(pressX)) return false;
        if (Math.abs(mx - pressX) > 3) dragged = true;
        if (dragged) {
            target = clamp(target - dx);
            scroll = target;
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (Double.isNaN(pressX)) return false;
        boolean click = !dragged;
        pressX = Double.NaN;
        if (click) select((int) Math.floor((mx - getX() + scroll) / PITCH));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!isMouseOver(mx, my)) return false;
        target = clamp(target - (Math.abs(horizontal) > Math.abs(vertical) ? horizontal : vertical) * PITCH);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) return false;
        if (key == GLFW.GLFW_KEY_LEFT) {
            select(selected < 0 ? currentIndex() : selected - 1);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT) {
            select(selected < 0 ? currentIndex() : selected + 1);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
