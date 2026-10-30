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

/** One door's three display entities at the lower block corner. Self-ticking (the attachment ticks the holder); never saved. Main thread only. */
final class DoorDisplay extends ElementHolder {

    /** A client without the Polymer pack would see sheets of paper, so it is not shown the door (the wall and gap still work). */
    static volatile boolean requirePack = true;

    private final ItemDisplayElement[] parts = new ItemDisplayElement[DoorModels.PARTS.length];
    private final DoorMotion motion;
    private final int turns;

    DoorDisplay(ServerLevel level, Vec3 corner, int quarterTurns, boolean open) {
        this.turns = quarterTurns;
        this.motion = new DoorMotion(open);
        for (int i = 0; i < parts.length; i++) {
            ItemDisplayElement element = new ItemDisplayElement(DoorModels.stack(i));
            element.setModelTransformation(ItemDisplayContext.NONE);
            element.setInterpolationDuration(1);
            element.setTeleportDuration(0);
            element.setDisplaySize(3, 3);
            element.setViewRange(1);
            element.setShadowRadius(0);
            if (i == parts.length - 1) element.setBrightness(new Brightness(15, 15));   // the glow part
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
            parts[i].setTransformation(DoorPose.matrix(turns, motion.angle(), i != 0));
            if (interpolate) parts[i].startInterpolation();
        }
    }
}
