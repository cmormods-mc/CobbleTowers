package com.cobbletowers.halloween.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The shipped files: the eight models, the shared atlas and both manifests must exist and agree with each other. */
class VaultAssetsTest {

    private static final String MODELS = "/assets/cobbletowers/models/halloween/jack_o_lantern_vault/";
    private static final String TEXTURE = "/assets/cobbletowers/textures/item/halloween/jack_o_lantern_vault/vault.png";
    private static final Set<Double> ANGLES = Set.of(0.0, 22.5, -22.5, 45.0, -45.0);

    private static JsonObject read(String path) throws Exception {
        try (InputStream in = VaultAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyModelIsAValidItemModelOnTheSharedAtlas() throws Exception {
        BufferedImage atlas;
        try (InputStream in = VaultAssetsTest.class.getResourceAsStream(TEXTURE)) {
            assertNotNull(in, "missing " + TEXTURE);
            atlas = ImageIO.read(in);
        }
        assertEquals(atlas.getWidth(), atlas.getHeight());
        int persistentElements = 0, full = -1;
        for (String name : new String[] {"body", "lid", "face_plate", "candy_fill", "reveal_a", "reveal_b", "reveal_c", "vault_full"}) {
            JsonObject model = read(MODELS + name + ".json");
            assertEquals("cobbletowers:item/halloween/jack_o_lantern_vault/vault", model.getAsJsonObject("textures").get("0").getAsString());
            assertEquals(atlas.getWidth(), model.getAsJsonArray("texture_size").get(0).getAsInt());
            JsonArray elements = model.getAsJsonArray("elements");
            assertTrue(elements.size() > 0, name);
            if (name.equals("vault_full")) full = elements.size();
            else if (!name.startsWith("reveal")) persistentElements += elements.size();
            for (JsonElement el : elements) {
                JsonObject e = el.getAsJsonObject();
                for (String key : new String[] {"from", "to"}) for (JsonElement v : e.getAsJsonArray(key)) {
                    assertTrue(v.getAsDouble() >= -16 && v.getAsDouble() <= 32, name + " " + e.get("name") + " " + key);
                }
                for (int i = 0; i < 3; i++) assertTrue(e.getAsJsonArray("to").get(i).getAsDouble() > e.getAsJsonArray("from").get(i).getAsDouble(), name + " " + e.get("name"));
                if (e.has("rotation")) assertTrue(ANGLES.contains(e.getAsJsonObject("rotation").get("angle").getAsDouble()), name + " " + e.get("name"));
                for (var face : e.getAsJsonObject("faces").entrySet()) {
                    JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                    for (JsonElement u : uv) assertTrue(u.getAsDouble() >= 0 && u.getAsDouble() <= 16, name + " uv");
                    assertTrue(uv.get(2).getAsDouble() > uv.get(0).getAsDouble() && uv.get(3).getAsDouble() > uv.get(1).getAsDouble(), name + " uv size");
                    assertEquals("#0", face.getValue().getAsJsonObject().get("texture").getAsString());
                }
            }
        }
        assertEquals(persistentElements, full, "vault_full is exactly the four persistent pieces");
    }

    @Test
    void manifestsAgreeWithEachOtherAndWithTheMachine() throws Exception {
        VaultManifest manifest = VaultManifest.load();
        JsonObject anim = manifest.animation();
        assertEquals(20, anim.get("ticks_per_second").getAsInt());
        assertEquals("cobbletowers:halloween/jack_o_lantern_vault", anim.get("id").getAsString());
        for (var seq : anim.getAsJsonObject("sequences").entrySet()) {
            JsonObject s = seq.getValue().getAsJsonObject();
            assertEquals(s.get("ticks").getAsInt() / 20.0, s.get("seconds").getAsDouble(), 1e-9, seq.getKey());
        }
        for (var piece : anim.getAsJsonObject("pieces").entrySet()) {
            String model = piece.getValue().getAsJsonObject().get("model").getAsString();
            assertTrue(model.startsWith("cobbletowers:halloween/jack_o_lantern_vault/"), model);
            assertNotNull(VaultAssetsTest.class.getResourceAsStream(MODELS + model.substring(model.lastIndexOf('/') + 1) + ".json"), model);
        }
        for (VaultPiece p : VaultPiece.values()) assertTrue(anim.getAsJsonObject("pieces").has(p.model), p.model);
        for (VaultEvent e : manifest.events()) {
            if (e.state() == VaultMachine.State.ANTICIPATING || e.state() == VaultMachine.State.UNLOCKING || e.state() == VaultMachine.State.OPENING) {
                assertEquals("any", e.outcome(), "outcome-specific event before the lid is open: " + e.type());
            }
            if (e.type().equals("sound") || e.type().equals("particle")) {
                assertTrue(e.data().get("id").getAsString().startsWith("minecraft:"), "only vanilla ids (no client mod needed): " + e.data().get("id"));
                manifest.anchor(e.data().get("at").getAsString());
            }
        }
        assertTrue(manifest.events().stream().noneMatch(e -> e.type().contains("reward") && e.state() != VaultMachine.State.OPEN), "reward triggers only once OPEN");
    }
}
