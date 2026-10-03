package com.cobbletowers.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.network.ArmorSetsPayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ArmorTooltipTest {

    private static final List<String> SET_IDS = List.of("challenger", "tideforge", "rootvale", "duskvale");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cobbletowers", path);
    }

    private static ArmorSetDefinition bundled(String set) throws IOException {
        try (InputStream in = ArmorTooltipTest.class.getResourceAsStream("/data/cobbletowers/cobbletowers/armor_sets/" + set + ".json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return ArmorSetDefinition.fromJson(id(set), json);
        }
    }

    private static Map<String, ResourceLocation> wearing(String set, String... slots) {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        for (String slot : slots) {
            worn.put(slot, id(set + "_" + switch (slot) {
                case "head" -> "helmet";
                case "chest" -> "chestplate";
                case "legs" -> "leggings";
                default -> "boots";
            }));
        }
        return worn;
    }

    private static List<String> text(List<Component> lines) {
        return lines.stream().map(Component::getString).collect(Collectors.toList());
    }

    private static List<Component> tooltip(String set, Map<String, ResourceLocation> worn, boolean expanded) throws IOException {
        ArmorSetView view = ArmorSetViews.of(bundled(set));
        return ArmorTooltipBuilder.build(view, worn, item -> Component.literal(item.getPath()), expanded);
    }

    // ---- describing bonuses ---------------------------------------------------------------------------------------------

    @Test
    void everyShippedBonusReadsAsPlainEnglish() throws IOException {
        for (String set : SET_IDS) {
            for (SetBonus bonus : bundled(set).bonuses()) {
                List<String> lines = SetBonusDescriber.describe(bonus);
                assertFalse(lines.isEmpty(), set + " " + bonus);
                for (String line : lines) {
                    assertFalse(line.isBlank(), set + " has a blank line");
                    assertFalse(line.contains("minecraft:") || line.contains("generic.") || line.contains("_"),
                            set + " leaks an internal id: " + line);
                }
            }
        }
    }

    @Test
    void attributesAreNamedAndSignedByOperation() {
        assertEquals("+2 Max Health", SetBonusDescriber.attributeLine(new SetBonus.PlayerAttribute(2, id("x").withPath("generic.max_health"), "add_value", 2.0)));
        assertEquals("+5% Movement Speed", SetBonusDescriber.attributeLine(new SetBonus.PlayerAttribute(2, id("x").withPath("generic.movement_speed"), "add_multiplied_base", 0.05)));
        assertEquals("+0.5 Water Movement", SetBonusDescriber.attributeLine(new SetBonus.PlayerAttribute(2, id("x").withPath("generic.water_movement_efficiency"), "add_value", 0.5)));
        assertEquals("-10% Attack Speed", SetBonusDescriber.attributeLine(new SetBonus.PlayerAttribute(2, id("x").withPath("generic.attack_speed"), "add_multiplied_total", -0.1)));
        assertEquals("+3 Some New Thing", SetBonusDescriber.attributeLine(new SetBonus.PlayerAttribute(2, id("x").withPath("player.some_new_thing"), "add_value", 3)),
                "an attribute it has never heard of is title-cased, not dropped");
    }

    @Test
    void battleEffectsSayWhoTheyHelp() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("{\"op\":\"weather\",\"id\":\"raindance\"}", "Starts the battle in Rain");
        expected.put("{\"op\":\"weather\",\"id\":\"sandstorm\",\"duration\":3}", "Starts the battle in a Sandstorm for 3 turns");
        expected.put("{\"op\":\"terrain\",\"id\":\"grassyterrain\",\"duration\":5}", "Grassy Terrain at the start of the battle for 5 turns");
        expected.put("{\"op\":\"boost\",\"side\":\"self\",\"stat\":\"spe\",\"stages\":1}", "Your lead starts with +1 Speed");
        expected.put("{\"op\":\"boost\",\"side\":\"foe\",\"stat\":\"atk\",\"stages\":-1}", "The opposing lead starts with -1 Attack");
        expected.put("{\"op\":\"hp\",\"side\":\"foe\",\"percent\":80}", "The opposing lead starts at 80% HP");
        expected.put("{\"op\":\"status\",\"side\":\"foe\",\"status\":\"par\"}", "The opposing lead starts Paralyzed");
        expected.put("{\"op\":\"sidecondition\",\"side\":\"self\",\"id\":\"tailwind\",\"duration\":4}", "Your side starts with Tailwind for 4 turns");
        expected.put("{\"op\":\"damage\",\"side\":\"self\",\"type\":\"Water\",\"percent\":120}", "Your Water moves deal 20% more damage");
        expected.put("{\"op\":\"damage\",\"side\":\"foe\",\"type\":\"any\",\"percent\":80}", "The opposing moves deal 20% less damage");
        expected.put("{\"op\":\"resist\",\"side\":\"self\",\"type\":\"any\",\"percent\":90}", "You take 10% less damage");
        expected.put("{\"op\":\"resist\",\"side\":\"self\",\"type\":\"Fire\",\"percent\":75}", "You take 25% less damage from Fire moves");
        expected.put("{\"op\":\"sidecondition\",\"side\":\"both\",\"id\":\"mist\"}", "Both sides start with Mist");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            assertEquals(entry.getValue(), SetBonusDescriber.effectLine(JsonParser.parseString(entry.getKey()).getAsJsonObject()), entry.getKey());
        }
    }

    @Test
    void numbersNeverShowTrailingZerosOrExponents() {
        assertEquals("2", SetBonusDescriber.number(2.0));
        assertEquals("0.5", SetBonusDescriber.number(0.5));
        assertEquals("12.25", SetBonusDescriber.number(12.25));
        assertEquals("5", SetBonusDescriber.number(0.05 * 100));
        assertEquals("0.07", SetBonusDescriber.number(0.07));
    }

    // ---- views ----------------------------------------------------------------------------------------------------------

    @Test
    void aViewGroupsBonusesIntoAscendingTiersAndKeepsSlotOrder() throws IOException {
        ArmorSetView view = ArmorSetViews.of(bundled("tideforge"));
        assertEquals(List.of("head", "chest", "legs", "feet"), view.pieces().stream().map(ArmorSetView.Piece::slot).toList());
        assertEquals(List.of(2, 4), view.tiers().stream().map(ArmorSetView.Tier::pieces).toList());
        assertEquals(3, view.tiers().get(0).lines().size(), "breath, water movement and catch rate share the two-piece tier");
        assertEquals(0x46B4E6, view.color());
        assertEquals("Tideforged Plate", view.name());
    }

    @Test
    void aSetWithNoColourGetsTheNeutralDefault() {
        ArmorSetDefinition plain = ArmorSetDefinition.fromJson(id("t"), JsonParser.parseString(
                "{\"schema_version\":1,\"display_name\":\"T\",\"pieces\":{\"head\":\"cobbletowers:t_helmet\"}}").getAsJsonObject());
        assertEquals(ArmorSetDefinition.DEFAULT_COLOR, plain.color());
        for (String bad : new String[] {"red", "#12345", "#1234567", "#gggggg", ""}) {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> ArmorSetDefinition.fromJson(id("t"),
                    JsonParser.parseString("{\"schema_version\":1,\"display_name\":\"T\",\"color\":\"" + bad + "\",\"pieces\":{\"head\":\"a:b\"}}").getAsJsonObject()), bad);
        }
    }

    // ---- the tooltip ----------------------------------------------------------------------------------------------------

    @Test
    void nothingWornShowsEverythingLocked() throws IOException {
        List<String> lines = text(tooltip("tideforge", Map.of(), false));
        assertEquals("", lines.get(0), "a spacer separates it from the item's own lines");
        assertTrue(lines.get(1).contains("Tideforged Plate") && lines.get(1).contains("0/4"), lines.get(1));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("◇ 2 pieces")), "the two-piece tier is locked");
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("◇ ★ Full Set")), "and so is the full set");
        assertTrue(lines.contains("Hold Shift for pieces"));
    }

    @Test
    void twoPiecesLightTheFirstTierOnly() throws IOException {
        List<Component> lines = tooltip("tideforge", wearing("tideforge", "head", "chest"), false);
        List<String> shown = text(lines);
        assertTrue(shown.get(1).contains("2/4"));
        assertTrue(shown.stream().anyMatch(l -> l.startsWith("✔ 2 pieces")), shown.toString());
        assertTrue(shown.stream().anyMatch(l -> l.startsWith("◇ ★ Full Set")), shown.toString());
        Component lit = lines.stream().filter(l -> l.getString().startsWith("✔")).findFirst().orElseThrow();
        Component locked = lines.stream().filter(l -> l.getString().startsWith("◇ ★")).findFirst().orElseThrow();
        assertEquals("green", lit.getStyle().getColor().serialize(), "the tick of a lit tier is green");
        assertEquals("white", lit.getSiblings().get(0).getStyle().getColor().serialize(), "and its label is white");
        assertEquals("dark_gray", locked.getStyle().getColor().serialize(), "a locked tier is dimmed");
    }

    @Test
    void aFullSetLightsEverythingAndCrownsTheHeader() throws IOException {
        List<String> shown = text(tooltip("tideforge", wearing("tideforge", "head", "chest", "legs", "feet"), false));
        assertTrue(shown.get(1).contains("4/4") && shown.get(1).contains("★"), shown.get(1));
        assertTrue(shown.stream().anyMatch(l -> l.startsWith("✔ ★ Full Set")), shown.toString());
        assertFalse(shown.stream().anyMatch(l -> l.startsWith("◇")), "nothing is locked");
    }

    @Test
    void holdingShiftListsThePiecesWornAndMissing() throws IOException {
        List<String> shown = text(tooltip("rootvale", wearing("rootvale", "head", "feet"), true));
        assertFalse(shown.contains("Hold Shift for pieces"), "the hint goes once it is open");
        assertTrue(shown.contains("  ◆ rootvale_helmet"), shown.toString());
        assertTrue(shown.contains("  ◇ rootvale_chestplate"), shown.toString());
        assertTrue(shown.contains("  ◆ rootvale_boots"), shown.toString());
    }

    @Test
    void aPieceInTheWrongSlotOrAnotherSetDoesNotCount() throws IOException {
        Map<String, ResourceLocation> worn = new LinkedHashMap<>();
        worn.put("head", id("tideforge_boots"));          // right set, wrong slot
        worn.put("chest", id("rootvale_chestplate"));     // right slot, wrong set
        assertEquals(0, ArmorTooltipBuilder.wornCount(ArmorSetViews.of(bundled("tideforge")), worn));
    }

    @Test
    void theTitleIsBoldAndTintedInTheSetColour() throws IOException {
        Component header = tooltip("duskvale", Map.of(), false).get(1);
        Style title = header.getSiblings().get(0).getStyle();
        assertTrue(title.isBold());
        assertEquals(0xC08CEB, title.getColor().getValue());
    }

    // ---- the wire -------------------------------------------------------------------------------------------------------

    @Test
    void thePayloadSurvivesTheWire() throws IOException {
        List<ArmorSetView> views = new java.util.ArrayList<>();
        for (String set : SET_IDS) views.add(ArmorSetViews.of(bundled(set)));
        ArmorSetsPayload sent = new ArmorSetsPayload(views);

        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        ArmorSetsPayload.STREAM_CODEC.encode(buffer, sent);
        ArmorSetsPayload received = ArmorSetsPayload.STREAM_CODEC.decode(buffer);

        assertEquals(sent, received);
        assertEquals(0, buffer.readableBytes(), "nothing left over");
    }

    // ---- for a human ---------------------------------------------------------------------------------------------------

    /** Writes every shipped set's tooltips (nothing worn, two worn, full set, expanded) for tools/render_tooltips.py. */
    @Test
    void writesTheTooltipsForTheRenderer() throws IOException {
        JsonArray out = new JsonArray();
        for (String set : SET_IDS) {
            String[][] outfits = {{}, {"head", "chest"}, {"head", "chest", "legs", "feet"}};
            for (String[] outfit : outfits) {
                for (boolean expanded : new boolean[] {false, true}) {
                    if (expanded && outfit.length != 2) continue;
                    JsonObject entry = new JsonObject();
                    entry.addProperty("set", set);
                    entry.addProperty("worn", outfit.length);
                    entry.addProperty("expanded", expanded);
                    JsonArray lines = new JsonArray();
                    for (Component line : tooltip(set, wearing(set, outfit), expanded)) lines.add(describe(line));
                    entry.add("lines", lines);
                    out.add(entry);
                }
            }
        }
        Path file = Path.of("build", "tooltip_preview.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString());
        assertTrue(Files.size(file) > 1000);
    }

    /** One line as a list of styled runs: [{text, color, bold, italic}]. */
    private static JsonArray describe(Component line) {
        JsonArray runs = new JsonArray();
        line.visit((style, text) -> {
            if (!text.isEmpty()) {
                JsonObject run = new JsonObject();
                run.addProperty("text", text);
                run.addProperty("color", style.getColor() == null ? "gray" : style.getColor().serialize());
                run.addProperty("bold", style.isBold());
                run.addProperty("italic", style.isItalic());
                runs.add(run);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return runs;
    }
}
