package com.cobbletowers.economy;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * AscensionLib's currency materials as reward-table ids ({@code ascensionlib:facet}, ...). A material is an ordinary item that
 * carries {@code ascensionlib.material} custom data; the stacks below are copied from the library's own currency loot tables, so
 * what a tower pays is what the library pays. Recognised only in {@code RewardDelivery}; no compile dependency on the library.
 */
public final class AscensionMaterials {

    public static final String NAMESPACE = "ascensionlib";

    /** The stack as SNBT (item, count 1, components) and the name it is listed under. */
    public record Material(String label, String stack) {}

    private static final Map<String, Material> ALL = build();

    private AscensionMaterials() {}

    public static Optional<Material> of(ResourceLocation id) {
        return NAMESPACE.equals(id.getNamespace()) ? Optional.ofNullable(ALL.get(id.getPath())) : Optional.empty();
    }

    private static Map<String, Material> build() {
        java.util.Map<String, Material> m = new java.util.LinkedHashMap<>();
        m.put("resonance_dust", new Material("Resonance Dust", "{id:\"minecraft:glowstone_dust\",count:1,components:{\"minecraft:custom_data\":{ascensionlib:{material:\"resonance_dust\"}},\"minecraft:custom_model_data\":8101,\"minecraft:custom_name\":'{\"text\":\"Resonance Dust\",\"color\":\"#d9a441\",\"italic\":false}',\"minecraft:lore\":['[\"\",{\"text\":\"COMMON\",\"color\":\"#c1c5cf\",\"bold\":true,\"italic\":false},{\"text\":\" \u00b7 Currency\",\"color\":\"dark_gray\",\"bold\":false,\"italic\":false}]','{\"text\":\"Raw resonance, spent on upgrades,\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"refining and perfecting.\",\"color\":\"gray\",\"italic\":false}']}}"));
        m.put("facet", new Material("Facet", "{id:\"minecraft:amethyst_shard\",count:1,components:{\"minecraft:custom_data\":{ascensionlib:{material:\"facet\"}},\"minecraft:custom_model_data\":8102,\"minecraft:custom_name\":'{\"text\":\"Facet\",\"color\":\"#4f9ad9\",\"italic\":false}',\"minecraft:lore\":['[\"\",{\"text\":\"UNCOMMON\",\"color\":\"#1bd7e8\",\"bold\":true,\"italic\":false},{\"text\":\" \u00b7 Currency\",\"color\":\"dark_gray\",\"bold\":false,\"italic\":false}]','{\"text\":\"A cut crystal that sharpens an affix.\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"Spent on reforging and overreaching.\",\"color\":\"gray\",\"italic\":false}']}}"));
        m.put("tower_mark", new Material("Tower Mark", "{id:\"minecraft:disc_fragment_5\",count:1,components:{\"minecraft:custom_data\":{ascensionlib:{material:\"tower_mark\"}},\"minecraft:custom_model_data\":8108,\"minecraft:custom_name\":'{\"text\":\"Tower Mark\",\"color\":\"#e8e1c9\",\"italic\":false}',\"minecraft:lore\":['[\"\",{\"text\":\"RARE\",\"color\":\"#38ccef\",\"bold\":true,\"italic\":false},{\"text\":\" \u00b7 Currency\",\"color\":\"dark_gray\",\"bold\":false,\"italic\":false}]','{\"text\":\"Proof of a Tower clear.\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"Bound to you; it cannot be traded.\",\"color\":\"gray\",\"italic\":false}']}}"));
        m.put("scouter", new Material("Scouter", "{id:\"minecraft:prismarine_crystals\",count:1,components:{\"minecraft:custom_data\":{ascensionlib:{material:\"scouter\"}},\"minecraft:custom_model_data\":8106,\"minecraft:custom_name\":'{\"text\":\"Scouter\",\"color\":\"#6f9a52\",\"italic\":false}',\"minecraft:lore\":['[\"\",{\"text\":\"UNCOMMON\",\"color\":\"#1bd7e8\",\"bold\":true,\"italic\":false},{\"text\":\" \u00b7 Currency\",\"color\":\"dark_gray\",\"bold\":false,\"italic\":false}]','{\"text\":\"Reveals one enemy's rating.\",\"color\":\"gray\",\"italic\":false}']}}"));
        m.put("ascension_core", new Material("Ascension Core", "{id:\"minecraft:heart_of_the_sea\",count:1,components:{\"minecraft:custom_data\":{ascensionlib:{material:\"ascension_core\"}},\"minecraft:custom_model_data\":8103,\"minecraft:custom_name\":'{\"text\":\"Ascension Core\",\"color\":\"#c9694f\",\"italic\":false}',\"minecraft:lore\":['[\"\",{\"text\":\"EPIC\",\"color\":\"#af69ff\",\"bold\":true,\"italic\":false},{\"text\":\" \u00b7 Currency\",\"color\":\"dark_gray\",\"bold\":false,\"italic\":false}]','{\"text\":\"The heart of a promotion.\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"Earned in the Tower.\",\"color\":\"gray\",\"italic\":false}']}}"));
        return Map.copyOf(m);
    }
}
