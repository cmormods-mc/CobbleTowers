package com.cobbletowers.vendor;

import com.cobbletowers.CobbleTowers;
import com.cobbletowers.halloween.vault.VaultMath;
import com.cobbletowers.halloween.vault.VaultTransforms;
import eu.pb4.polymer.resourcepack.api.PolymerModelData;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Grizelda's body: the Blockbench model {@code halloween/goblin_merchant/goblin} shown by a Polymer item display riding the vendor mob. The
 * mob (a vanilla witch) stays as the invisible click box, so the right-click and access code are untouched. Needs the Polymer pack, which the
 * server already requires of every player.
 */
final class GoblinDisplay {

    private static final PolymerModelData MODEL = PolymerResourcePackUtils.requestModel(Items.PAPER, CobbleTowers.id("halloween/goblin_merchant/goblin"));

    private GoblinDisplay() {}

    /** Forces the class to load so the model is requested before Polymer builds the pack. */
    static void bootstrap() {}

    /** Hides {@code vendor} and draws the goblin on it, facing where the mob faces (the model's front is its north face). */
    static void attach(Mob vendor) {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(MODEL.value()));
        ItemDisplayElement element = new ItemDisplayElement(stack);
        element.setModelTransformation(ItemDisplayContext.NONE);
        element.setViewRange(1.5f);
        element.setShadowRadius(0f);
        // the same contract as the vault: the model's centre sits half a block up, and the client's own half turn is cancelled by sending it again
        double[] q = VaultMath.qMul(VaultMath.qAxis('y', netYaw(vendor.getYRot())), VaultTransforms.FLIP_Q);
        element.setTranslation(new Vector3f(0f, 0.5f, 0f));
        element.setLeftRotation(new Quaternionf(q[0], q[1], q[2], q[3]));
        ElementHolder holder = new ElementHolder();
        holder.addElement(element);
        EntityAttachment.ofTicking(holder, (Entity) vendor);
        vendor.setInvisible(true);
    }

    /** The net rotation about +y for a mob yaw (0 = south): the model faces north at 0, west at 90. */
    static double netYaw(float mobYaw) {
        return 180.0 - mobYaw;
    }
}
