package com.cobbletowers.halloween.vault;

import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Allocates the vault's seven runtime models through Polymer ({@code requestModel}, so the custom model data values are Polymer's and never
 * hardcoded) on paper, the same base item the worn armor parts use. The model JSONs and the shared atlas live in the jar under
 * {@code assets/cobbletowers/models/halloween/jack_o_lantern_vault/} and are copied into the pack by {@code addModAssets}.
 */
public final class VaultModels {

    private static final Map<VaultPiece, PolymerModelData> MODELS = new EnumMap<>(VaultPiece.class);

    static {
        for (VaultPiece piece : VaultPiece.values()) {
            MODELS.put(piece, PolymerResourcePackUtils.requestModel(Items.PAPER,
                    ResourceLocation.fromNamespaceAndPath("cobbletowers", VaultManifest.ID + "/" + piece.model)));
        }
    }

    private VaultModels() {}

    /** Forces the class to load, so every model is requested before Polymer builds the pack. */
    public static void bootstrap() {}

    /** The stack an item display shows for a piece: paper wearing that piece's model. */
    public static ItemStack stack(VaultPiece piece) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(MODELS.get(piece).value()));
        return stack;
    }
}
