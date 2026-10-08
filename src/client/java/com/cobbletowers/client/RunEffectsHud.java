package com.cobbletowers.client;

import com.cobbletowers.network.RunEffectsPayload;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * What the run is carrying, readable in the middle of a fight (P41). The Hall will not open mid-battle, so the server sends
 * the list when a tower battle starts and this draws it on a key, top right, over whatever the battle is showing: each
 * modifier and relic with its benefits in green and costs in red. Drawn only inside the tower.
 */
public final class RunEffectsHud implements HudRenderCallback {

    private static final ResourceLocation TOWER = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tower");
    /** The same greens and reds as the parchment cards, lightened for the dark panel. */
    private static final int GOOD = 0xFF8FE08F;
    private static final int BAD = 0xFFFF8A80;
    private static final int PANEL_WIDTH = 230;

    private static volatile RunEffectsPayload latest;
    private static volatile boolean visible;

    private RunEffectsHud() {}

    public static final RunEffectsHud INSTANCE = new RunEffectsHud();

    public static void update(RunEffectsPayload payload) {
        // Floor 0 is the server saying the run is over.
        latest = payload.floor() <= 0 ? null : payload;
    }

    public static void toggle() {
        visible = !visible;
    }

    static boolean available() {
        Minecraft client = Minecraft.getInstance();
        return latest != null && client.level != null && client.level.dimension().location().equals(TOWER);
    }

    /** One drawn line: its text and colour, and how far it is indented. */
    private record Row(FormattedCharSequence text, int color, int indent) {}

    @Override
    public void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (!available() || client.player == null || client.options.hideGui) return;
        draw(graphics, latest, ClientKeys.runEffectsKey(), visible);
    }

    /** The overlay itself (or its one-line hint when hidden), apart from the checks that it is wanted. Also what a screenshot calls. */
    static void draw(GuiGraphics graphics, RunEffectsPayload run, String key, boolean visible) {
        Font font = TowerFonts.get();
        if (!visible) {
            String hint = "[" + key + "] Run effects";
            graphics.drawString(font, hint, graphics.guiWidth() - font.width(hint) - 6, 4, 0xFFB8A98A, false);
            return;
        }
        List<Row> rows = new ArrayList<>();
        int inner = PANEL_WIDTH - 14;
        rows.add(row(font, "Floor " + run.floor() + "  *  risk +" + run.riskPercent() + "%", TowerUi.BRONZE_LIGHT, 0, inner));
        if (run.items().isEmpty()) rows.add(row(font, "No modifiers or relics yet.", TowerUi.TEXT, 0, inner));
        int maxHeight = graphics.guiHeight() / 2;
        int step = font.lineHeight + 1;
        int shown = 0;
        for (RunEffectsPayload.Item item : run.items()) {
            List<Row> block = new ArrayList<>();
            String title = (item.relic() ? "Relic: " : "") + item.name() + (item.count() > 1 ? " x" + item.count() : "")
                    + (item.floor() > 0 ? "  (floor " + item.floor() + ")" : "");
            block.addAll(rowsOf(font, title, item.relic() ? TowerUi.SAGE : TowerUi.TEXT, 0, inner));
            for (String line : item.lines()) {
                boolean good = line.startsWith("[+] ");
                boolean bad = line.startsWith("[-] ");
                String text = good || bad ? line.substring(4) : line;
                block.addAll(rowsOf(font, text, good ? GOOD : bad ? BAD : TowerUi.TEXT, 6, inner - 6));
            }
            if ((rows.size() + block.size()) * step + 14 > maxHeight) break;
            rows.addAll(block);
            shown++;
        }
        if (shown < run.items().size()) rows.add(row(font, "+" + (run.items().size() - shown) + " more: open This run", 0xFFB8A98A, 0, inner));
        rows.add(row(font, "[" + key + "] hide", 0xFFB8A98A, 0, inner));

        int x = graphics.guiWidth() - PANEL_WIDTH - 6;
        int y = 6;
        PixelUi.frame(graphics, PixelUi.Frame.DARK, x, y, PANEL_WIDTH, rows.size() * step + 12);
        int textY = y + 6;
        for (Row row : rows) {
            graphics.drawString(font, row.text(), x + 7 + row.indent(), textY, row.color(), false);
            textY += step;
        }
    }

    private static Row row(Font font, String text, int color, int indent, int width) {
        return rowsOf(font, text, color, indent, width).get(0);
    }

    private static List<Row> rowsOf(Font font, String text, int color, int indent, int width) {
        List<Row> out = new ArrayList<>();
        for (FormattedCharSequence part : font.split(Component.literal(text), Math.max(40, width))) out.add(new Row(part, color, indent));
        if (out.isEmpty()) out.add(new Row(FormattedCharSequence.EMPTY, color, indent));
        return out;
    }
}
