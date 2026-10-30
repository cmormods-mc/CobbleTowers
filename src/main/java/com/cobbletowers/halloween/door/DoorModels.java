package com.cobbletowers.halloween.door;

import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The door parts allocated through Polymer on paper, like the vault. Standard door: frame, wing, glow
 * ({@code assets/cobbletowers/models/halloween/trick_door/}). Grand door: frame, frame glow, left, left glow, right, right glow
 * ({@code .../grand_door/}).
 */
public final class DoorModels {

    static final String[] PARTS = {"frame", "wing", "glow"};
    static final String[] GRAND_PARTS = {"frame", "frame_glow", "left", "left_glow", "right", "right_glow"};
    private static final PolymerModelData[] MODELS = new PolymerModelData[PARTS.length];
    private static final PolymerModelData[] GRAND = new PolymerModelData[GRAND_PARTS.length];

    static {
        for (int i = 0; i < PARTS.length; i++) {
            MODELS[i] = PolymerResourcePackUtils.requestModel(Items.PAPER,
                    ResourceLocation.fromNamespaceAndPath("cobbletowers", "halloween/trick_door/" + PARTS[i] + "_right"));
        }
        for (int i = 0; i < GRAND_PARTS.length; i++) {
            GRAND[i] = PolymerResourcePackUtils.requestModel(Items.PAPER,
                    ResourceLocation.fromNamespaceAndPath("cobbletowers", "halloween/grand_door/" + GRAND_PARTS[i]));
        }
    }

    private DoorModels() {}

    /** Forces the class to load, so every model is requested before Polymer builds the pack. */
    public static void bootstrap() {}

    public static ItemStack stack(int part) {
        return paper(MODELS[part]);
    }

    public static ItemStack grandStack(int part) {
        return paper(GRAND[part]);
    }

    private static ItemStack paper(PolymerModelData model) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(model.value()));
        return stack;
    }
}
