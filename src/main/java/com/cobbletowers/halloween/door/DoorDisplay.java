package com.cobbletowers.halloween.door;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** One door display entities (three standard, six Grand) at the lower block corner. Self-ticking (the attachment ticks the holder); never saved. Main thread only. */
final class DoorDisplay extends ElementHolder {

    /** A client without the Polymer pack would see sheets of paper, so it is not shown the door (the wall and gap still work). */
    static volatile boolean requirePack = true;

    private final ItemDisplayElement[] parts;
    private final DoorMotion motion;
    private final int turns;
    private final boolean grand;

    DoorDisplay(ServerLevel level, Vec3 corner, int quarterTurns, boolean open, boolean grand) {
        this.turns = quarterTurns;
        this.grand = grand;
        this.motion = new DoorMotion(open);
        this.parts = new ItemDisplayElement[(grand ? DoorModels.GRAND_PARTS : DoorModels.PARTS).length];
        for (int i = 0; i < parts.length; i++) {
            ItemDisplayElement element = new ItemDisplayElement(grand ? DoorModels.grandStack(i) : DoorModels.stack(i));
            element.setModelTransformation(ItemDisplayContext.NONE);
            element.setInterpolationDuration(1);
            element.setTeleportDuration(0);
            if (grand) element.setDisplaySize(9, 10);
            else element.setDisplaySize(3, 3);
            element.setViewRange(1);
            element.setShadowRadius(0);
            // the glow parts: the last of three, every second of six
            if (grand ? i % 2 == 1 : i == parts.length - 1) element.setBrightness(new Brightness(15, 15));
            parts[i] = addElement(element);
        }
        pose(false);
        ChunkAttachment.ofTicking(this, level, corner);
    }

    @Override
    public boolean startWatching(ServerGamePacketListenerImpl handler) {
        return (!requirePack || PolymerResourcePackUtils.hasMainPack(handler.getPlayer())) && super.startWatching(handler);
    }

    @Override
    protected void onTick() {
        if (motion.tick()) pose(true);
    }

    void setOpen(boolean open) {
        motion.setOpen(open);
    }

    private void pose(boolean interpolate) {
        for (int i = 0; i < parts.length; i++) {
            parts[i].setTransformation(grand
                    ? DoorPose.grand(turns, i < 2 ? 0 : i < 4 ? -1 : 1, motion.angle())
                    : DoorPose.matrix(turns, motion.angle(), i != 0));
            if (interpolate) parts[i].startInterpolation();
        }
    }
}
