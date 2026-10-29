package com.cobbletowers.halloween.vault;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.InteractionElement;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import eu.pb4.polymer.virtualentity.api.elements.VirtualElement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The vault's display entities: four persistent item displays (body, lid, face plate, candy pile) and up to three reveal candies, all
 * Polymer virtual entities at the vault root, yaw 0, so a vanilla client needs nothing but the resource pack. Server-controlled: every
 * transformation comes from {@link VaultTransforms}; nothing is animated by the client. Main thread only.
 */
final class VaultDisplay {

    private static final Vector3f ONE = new Vector3f(1, 1, 1);

    /**
     * Without the pack a client would see four floating sheets of paper, so a player who has not loaded the Polymer pack is simply not shown
     * the vault (the room around it still works). Flip to false only for a test server that serves the pack some other way.
     */
    static volatile boolean requirePack = true;

    private final ElementHolder holder = new ElementHolder() {
        @Override
        public boolean startWatching(ServerGamePacketListenerImpl handler) {
            if (requirePack && !PolymerResourcePackUtils.hasMainPack(handler.getPlayer())) return false;
            return super.startWatching(handler);
        }
    };
    private final Map<VaultPiece, ItemDisplayElement> pieces = new EnumMap<>(VaultPiece.class);
    private final List<ItemDisplayElement> reveals = new ArrayList<>();
    private final VaultFacing facing;
    private boolean destroyed;

    /** The click box: wide enough for the chest, tall enough for the open lid. */
    private static final float CLICK_WIDTH = 1.7f, CLICK_HEIGHT = 1.6f;

    VaultDisplay(ServerLevel level, Vec3 root, VaultFacing facing, Consumer<ServerPlayer> onClick) {
        this.facing = facing;
        if (onClick != null) {
            InteractionElement hit = new InteractionElement(new VirtualElement.InteractionHandler() {
                @Override
                public void interact(ServerPlayer player, InteractionHand hand) {
                    if (hand == InteractionHand.MAIN_HAND) onClick.accept(player);   // both hands fire for one click
                }
            });
            hit.setSize(CLICK_WIDTH, CLICK_HEIGHT);
            holder.addElement(hit);
        }
        for (VaultPiece piece : VaultPiece.values()) {
            if (!piece.persistent) continue;
            ItemDisplayElement element = new ItemDisplayElement(VaultModels.stack(piece));
            element.setModelTransformation(ItemDisplayContext.NONE);
            element.setViewRange(1.5f);
            element.setShadowRadius(0f);
            element.setInterpolationDuration(1);
            holder.addElement(element);
            pieces.put(piece, element);
        }
        apply(VaultPose.REST, 1);
        ChunkAttachment.ofTicking(holder, level, root);
    }

    /** Sends the pose of the four persistent pieces; the client interpolates over {@code interpolationTicks}. */
    void apply(VaultPose pose, int interpolationTicks) {
        if (destroyed) return;
        for (Map.Entry<VaultPiece, ItemDisplayElement> entry : pieces.entrySet()) {
            VaultTransforms.Xform x = VaultTransforms.piece(entry.getKey(), pose, facing);
            ItemDisplayElement element = entry.getValue();
            element.setTranslation(new Vector3f((float) x.t()[0], (float) x.t()[1], (float) x.t()[2]));
            element.setLeftRotation(new Quaternionf(x.q()[0], x.q()[1], x.q()[2], x.q()[3]));
            element.setScale(ONE);
            element.setInterpolationDuration(Math.max(1, interpolationTicks));
            element.startInterpolationIfDirty();
        }
    }

    void spawnReveals() {
        clearReveals();
        for (int i = 0; i < 3; i++) {
            ItemDisplayElement element = new ItemDisplayElement(VaultModels.stack(VaultPiece.values()[VaultPiece.REVEAL_A.ordinal() + i]));
            element.setModelTransformation(ItemDisplayContext.NONE);
            element.setViewRange(1.5f);
            element.setShadowRadius(0f);
            element.setScale(new Vector3f(0.001f, 0.001f, 0.001f));
            element.setInterpolationDuration(1);
            holder.addElement(element);
            reveals.add(element);
        }
    }

    /** Places the reveal candies; {@code state} is null for a candy still waiting out its stagger (it stays invisible). */
    void applyReveal(int index, VaultReveal.State state, double collectScale) {
        if (destroyed || index >= reveals.size()) return;
        ItemDisplayElement element = reveals.get(index);
        if (state == null) return;
        double[] p = VaultMath.rotate(VaultMath.qAxis('y', facing.yawDeg), state.blocks());
        double[] q = VaultMath.qMul(VaultMath.qMul(VaultMath.qAxis('y', facing.yawDeg), VaultMath.qAxis('y', state.yawDeg())), VaultTransforms.FLIP_Q);
        float s = (float) (state.scale() * collectScale);
        element.setTranslation(new Vector3f((float) p[0], (float) p[1], (float) p[2]));
        element.setLeftRotation(new Quaternionf(q[0], q[1], q[2], q[3]));
        element.setScale(new Vector3f(s, s, s));
        element.setInterpolationDuration(1);
        element.startInterpolationIfDirty();
    }

    int revealCount() {
        return reveals.size();
    }

    void clearReveals() {
        for (ItemDisplayElement element : reveals) holder.removeElement(element);
        reveals.clear();
    }

    void destroy() {
        destroyed = true;
        reveals.clear();
        pieces.clear();
        holder.destroy();
    }
}
