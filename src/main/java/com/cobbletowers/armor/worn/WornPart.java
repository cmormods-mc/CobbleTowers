package com.cobbletowers.armor.worn;

import java.util.List;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/**
 * One 3D part drawn on a wearer (P49): a cuboid model from the pack, shown by an item display. A part belongs to the piece
 * that carries it and rides either the head or the torso.
 */
public enum WornPart {
    TIDEFORGE_CREST("tideforge_crest", "tideforge_helmet", Anchor.HEAD),
    TIDEFORGE_MANTLE("tideforge_mantle", "tideforge_chestplate", Anchor.TORSO);

    /** Which body part the display follows. */
    public enum Anchor { HEAD, TORSO }

    private final ResourceLocation piece;
    private final Anchor anchor;
    private final PolymerModelData model;

    WornPart(String modelName, String piece, Anchor anchor) {
        this.piece = ResourceLocation.fromNamespaceAndPath("cobbletowers", piece);
        this.anchor = anchor;
        // requested when the class loads, which install() forces before Polymer builds the pack
        this.model = PolymerResourcePackUtils.requestModel(Items.PAPER, ResourceLocation.fromNamespaceAndPath("cobbletowers", "worn/" + modelName));
    }

    public ResourceLocation piece() {
        return piece;
    }

    public Anchor anchor() {
        return anchor;
    }

    /** The stack an item display shows: paper wearing this part's model. */
    public ItemStack stack() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model.value()));
        return stack;
    }

    /** Forces the enum to load so every model is requested before the pack is generated. */
    static List<WornPart> bootstrap() {
        return List.of(values());
    }
}
