package com.cobbletowers.battle.cobbleraids;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * The species of a CobbleRaids boss definition, read as data ({@code data/<namespace>/raids/<path>.json}, field
 * {@code species}) because the encounter API does not say. Used only to tell AscensionLib what a scouted boss is; an
 * unreadable definition makes that boss unscoutable.
 */
public final class RaidSpecies {

    private RaidSpecies() {}

    public static Optional<String> of(MinecraftServer server, ResourceLocation definition) {
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(definition.getNamespace(),
                "raids/" + definition.getPath() + ".json");
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource = server.getResourceManager().getResource(file);
            if (resource.isEmpty()) return Optional.empty();
            try (Reader reader = resource.get().openAsReader()) {
                return speciesOf(JsonParser.parseReader(reader));
            }
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** The {@code species} field of a definition document. Package-private so a test can read it without a server. */
    static Optional<String> speciesOf(com.google.gson.JsonElement document) {
        if (!document.isJsonObject()) return Optional.empty();
        JsonObject root = document.getAsJsonObject();
        if (!root.has("species") || !root.get("species").isJsonPrimitive()) return Optional.empty();
        String species = root.get("species").getAsString();
        return species.isBlank() ? Optional.empty() : Optional.of(species);
    }
}
