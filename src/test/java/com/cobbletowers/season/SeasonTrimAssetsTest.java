package com.cobbletowers.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The season trims (P36b), read from the real resource files: every authored season has a complete trim, so the step-30 finale can never
 * hand out a template whose pattern is missing, and the tower armor and the templates are wired into the smithing table's tags.
 */
class SeasonTrimAssetsTest {

    private static final Path RESOURCES = Paths.get("src/main/resources");
    private static final Path DATA = RESOURCES.resolve("data/cobbletowers");
    private static final Path ASSETS = RESOURCES.resolve("assets/cobbletowers");
    private static final Set<Integer> PALETTE_GREYS = Set.of(224, 192, 160, 128, 96, 64, 32, 0);

    private static JsonObject json(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<Integer> authoredSeasons() throws IOException {
        Set<Integer> numbers = new HashSet<>();
        try (Stream<Path> files = Files.list(DATA.resolve("cobbletowers/seasons"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                numbers.add(json(file).get("number").getAsInt());
            }
        }
        return numbers;
    }

    @Test
    @DisplayName("every authored season has a trim pattern naming its own template item, both textures, an atlas entry and language")
    void everySeasonHasATrim() throws IOException {
        JsonObject atlas = json(RESOURCES.resolve("assets/minecraft/atlases/armor_trims.json"));
        Set<String> baked = new HashSet<>();
        for (JsonElement source : atlas.getAsJsonArray("sources")) {
            for (JsonElement texture : source.getAsJsonObject().getAsJsonArray("textures")) baked.add(texture.getAsString());
        }
        JsonObject lang = json(ASSETS.resolve("lang/en_us.json"));
        Set<Integer> seasons = authoredSeasons();
        assertTrue(!seasons.isEmpty());
        for (int season : seasons) {
            assertTrue(season <= SeasonTrimItems.MAX_SEASONS,
                    "season " + season + " is past the " + SeasonTrimItems.MAX_SEASONS + " pre-registered templates; raise MAX_SEASONS");
            JsonObject pattern = json(DATA.resolve("trim_pattern/season_" + season + ".json"));
            assertEquals(SeasonTrimItems.patternId(season).toString(), pattern.get("asset_id").getAsString());
            assertEquals(SeasonTrimItems.templateId(season).toString(), pattern.get("template_item").getAsString());
            assertEquals("trim_pattern.cobbletowers.season_" + season,
                    pattern.getAsJsonObject("description").get("translate").getAsString());
            for (String suffix : new String[] {"", "_leggings"}) {
                assertTrue(baked.contains("cobbletowers:trims/models/armor/season_" + season + suffix),
                        "the atlas must bake season " + season + suffix);
                assertTrue(Files.exists(ASSETS.resolve("textures/trims/models/armor/season_" + season + suffix + ".png")));
            }
            assertTrue(lang.has("trim_pattern.cobbletowers.season_" + season), "language for the pattern of season " + season);
        }
    }

    @Test
    @DisplayName("every template item up to the registered maximum has a model, a name, and is in the trim_templates tag")
    void templatesAreWired() throws IOException {
        JsonObject lang = json(ASSETS.resolve("lang/en_us.json"));
        Set<String> tagged = new HashSet<>();
        JsonArray values = json(RESOURCES.resolve("data/minecraft/tags/item/trim_templates.json")).getAsJsonArray("values");
        for (JsonElement value : values) tagged.add(value.getAsString());
        for (int season = 1; season <= SeasonTrimItems.MAX_SEASONS; season++) {
            assertTrue(Files.exists(ASSETS.resolve("models/item/season_trim_template_" + season + ".json")), "model " + season);
            assertTrue(lang.has("item.cobbletowers.season_trim_template_" + season), "name " + season);
            assertTrue(tagged.contains(SeasonTrimItems.templateId(season).toString()), "tag " + season);
        }
        assertTrue(Files.exists(ASSETS.resolve("textures/item/season_trim_template.png")));
    }

    @Test
    @DisplayName("all sixteen tower armor pieces are trimmable")
    void armorIsTrimmable() throws IOException {
        Set<String> trimmable = new HashSet<>();
        for (JsonElement value : json(RESOURCES.resolve("data/minecraft/tags/item/trimmable_armor.json")).getAsJsonArray("values")) {
            trimmable.add(value.getAsString());
        }
        int expected = 0;
        for (String set : new String[] {"challenger", "tideforge", "rootvale", "duskvale"}) {
            for (String slot : new String[] {"helmet", "chestplate", "leggings", "boots"}) {
                assertTrue(trimmable.contains("cobbletowers:" + set + "_" + slot), set + "_" + slot);
                expected++;
            }
        }
        assertEquals(16, expected);
    }

    @Test
    @DisplayName("trim textures are 64x32 and painted only in the game's eight palette greys, or transparent, so every material recolours them")
    void texturesUseThePalette() throws IOException {
        for (int season : authoredSeasons()) {
            for (String suffix : new String[] {"", "_leggings"}) {
                BufferedImage image = ImageIO.read(ASSETS.resolve("textures/trims/models/armor/season_" + season + suffix + ".png").toFile());
                assertEquals(64, image.getWidth());
                assertEquals(32, image.getHeight());
                int painted = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int argb = image.getRGB(x, y);
                        if ((argb >>> 24) == 0) continue;
                        painted++;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        assertTrue(r == g && g == b && PALETTE_GREYS.contains(r),
                                "season " + season + suffix + " pixel " + x + "," + y + " is not a palette grey: " + r + "," + g + "," + b);
                    }
                }
                assertTrue(painted > 100, "season " + season + suffix + " draws something (" + painted + " pixels)");
            }
        }
    }
}
