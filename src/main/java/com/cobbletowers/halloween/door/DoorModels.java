package com.cobbletowers.halloween.door;

import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;

/** The door parts (frame, wing, glow) allocated through Polymer on paper, like the vault. JSONs and atlas: {@code assets/cobbletowers/models/halloween/trick_door/}. */
public final class DoorModels {

    static final String[] PARTS = {"frame", "wing", "glow"};
    private static final PolymerModelData[] MODELS = new PolymerModelData[PARTS.length];

    static {
        for (int i = 0; i < PARTS.length; i++) {
            MODELS[i] = PolymerResourcePackUtils.requestModel(Items.PAPER,
                    ResourceLocation.fromNamespaceAndPath("cobbletowers", "halloween/trick_door/" + PARTS[i] + "_right"));
        }
    }

    private DoorModels() {}

    /** Forces the class to load, so every model is requested before Polymer builds the pack. */
    public static void bootstrap() {}

    public static ItemStack stack(int part) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(MODELS[part].value()));
        return stack;
    }
}
