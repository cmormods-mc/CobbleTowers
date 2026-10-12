package com.cobbletowers.webgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.RunEffectsPayload;
import com.cobbletowers.network.VendorCatalogPayload;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.rental.RentalDraw;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What a WebGUI page is sent. The page is written against exactly this JSON, so the shapes asserted here are the contract,
 * and every run rewrites {@code build/webgui-fixtures/} from the real payload records for the page's author to read.
 */
class PayloadJsonTest {

    private static final Path FIXTURES = Paths.get("build", "webgui-fixtures");

    private enum Color { RED }

    private record Sample(Color color, UUID id, List<Integer> numbers, java.util.Optional<String> note, long big) {}

    private static void fixture(String name, Object payload) throws IOException {
        Files.createDirectories(FIXTURES);
        Files.writeString(FIXTURES.resolve(name + ".json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(PayloadJson.envelope(payload)) + "\n");
    }

    @Test
    @DisplayName("a record becomes an object; an enum, a UUID and an Optional become plain JSON")
    void shapes() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000042");
        JsonObject out = PayloadJson.toJson(new Sample(Color.RED, id, List.of(1, 2), java.util.Optional.empty(), 5_000_000_000L)).getAsJsonObject();
        assertEquals("RED", out.get("color").getAsString());
        assertEquals(id.toString(), out.get("id").getAsString());
        assertEquals(2, out.getAsJsonArray("numbers").size());
        assertTrue(out.get("note").isJsonNull());
        assertEquals(5_000_000_000L, out.get("big").getAsLong());
    }

    @Test
    @DisplayName("the envelope names the screen by the record, without the Payload suffix")
    void envelope() {
        assertEquals("IntermissionState", PayloadJson.kindOf(IntermissionStatePayload.class));
        assertEquals("RentalDraft", PayloadJson.kindOf(RentalDraftPayload.class));
        assertEquals("TowerFeatureState", PayloadJson.kindOf(com.cobbletowers.network.TowerFeatureState.class));
    }

    @Test
    @DisplayName("the intermission screen's state reaches the page with every field the native screen reads")
    void intermission() throws IOException {
        var card = new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "downpour"), "Downpour", 2, 1,
                "weather:raindance", List.of("Risk: moderate / Stack limit: 1", "Battle weather: raindance"));
        var sent = new IntermissionStatePayload(4, new IntermissionStatePayload.Draft(1, List.of(card), -1, 0),
                List.of(new IntermissionStatePayload.Member("Alex", true, false)), 24, "Floor 4 cleared", true);
        fixture("IntermissionState", sent);

        JsonObject envelope = PayloadJson.envelope(sent);
        assertEquals("IntermissionState", envelope.get("kind").getAsString());
        JsonObject data = envelope.getAsJsonObject("data");
        assertEquals(4, data.get("floor").getAsInt());
        assertTrue(data.get("open").getAsBoolean());
        assertEquals(24, data.get("countdown").getAsInt());
        JsonObject first = data.getAsJsonObject("draft").getAsJsonArray("cards").get(0).getAsJsonObject();
        assertEquals("cobbletowers:downpour", first.get("id").getAsString());
        assertEquals("Downpour", first.get("displayName").getAsString());
        assertEquals(2, first.getAsJsonArray("lines").size());
        assertTrue(data.getAsJsonArray("members").get(0).getAsJsonObject().get("ready").getAsBoolean());
    }

    @Test
    @DisplayName("a real rental draft reaches the page as three packs of five cards with their moves")
    void rentalDraft() throws IOException {
        RentalDraft draft = new RentalDraft(RentalDraw.draw(pool(), 11, false));
        RentalDraftPayload sent = RentalDraftPayload.of(draft, "");
        fixture("RentalDraft", sent);

        JsonObject data = PayloadJson.envelope(sent).getAsJsonObject("data");
        assertEquals(3, data.getAsJsonArray("packs").size());
        JsonObject pack = data.getAsJsonArray("packs").get(0).getAsJsonObject();
        assertEquals(5, pack.getAsJsonArray("cards").size());
        JsonObject card = pack.getAsJsonArray("cards").get(0).getAsJsonObject();
        assertTrue(card.get("species").getAsString().length() > 0);
        assertTrue(card.get("rarity").getAsString().length() > 0);
        assertTrue(card.has("details"), "the card carries its set so the page can draw it");
    }

    @Test
    @DisplayName("reward, run-effects and vendor payloads convert without losing a list or a nested record")
    void others() throws IOException {
        var reward = new RewardRevealPayload(4, List.of(new RewardRevealPayload.Grant(
                ResourceLocation.fromNamespaceAndPath("cobblemon", "super_potion"), 2, "Super Potion")));
        fixture("RewardReveal", reward);
        var grant = PayloadJson.envelope(reward).getAsJsonObject("data").getAsJsonArray("grants").get(0).getAsJsonObject();
        assertEquals("cobblemon:super_potion", grant.get("item").getAsString());
        assertEquals(2, grant.get("amount").getAsInt());

        var effects = new RunEffectsPayload(4, 20, List.of(new RunEffectsPayload.Item("Lucky Charm", true, 1, 2, List.of("Loot swells to x1.25"))));
        fixture("RunEffects", effects);
        assertEquals("Lucky Charm", PayloadJson.envelope(effects).getAsJsonObject("data").getAsJsonArray("items").get(0).getAsJsonObject().get("name").getAsString());

        UUID me = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var vendor = new VendorCatalogPayload(180, List.of(new VendorCatalogPayload.Entry(
                ResourceLocation.fromNamespaceAndPath("cobbletowers", "full_heal"), "Full Heal", 25, -1)),
                List.of(new VendorCatalogPayload.Teammate(me, "Alex", true)), "");
        fixture("VendorCatalog", vendor);
        JsonObject data = PayloadJson.envelope(vendor).getAsJsonObject("data");
        assertEquals(180, data.get("cobbleDollars").getAsLong());
        assertEquals(me.toString(), data.getAsJsonArray("team").get(0).getAsJsonObject().get("id").getAsString());
    }

    private static List<RentalSetDefinition> pool() throws IOException {
        List<RentalSetDefinition> all = new ArrayList<>();
        Path dir = Paths.get("src/main/resources/data/cobbletowers/cobbletowers/rental_sets");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobbletowers", file.getFileName().toString().replace(".json", ""));
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(RentalSetDefinition.fromJson(id, JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }
}
