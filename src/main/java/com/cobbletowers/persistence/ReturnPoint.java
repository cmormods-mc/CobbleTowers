package com.cobbletowers.persistence;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;

/**
 * Where a player was standing before a run took them into the tower (P20): the dimension's id and a
 * position and facing. Plain values only -- never a live level or player (TDS section 10).
 */
public record ReturnPoint(String dimension, double x, double y, double z, float yaw, float pitch) {

    public ReturnPoint {
        Objects.requireNonNull(dimension, "dimension");
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        tag.putDouble("z", z);
        tag.putFloat("yaw", yaw);
        tag.putFloat("pitch", pitch);
        return tag;
    }

    public static ReturnPoint fromTag(CompoundTag tag) {
        if (!tag.contains("dimension")) throw new IllegalArgumentException("a return point names no dimension");
        return new ReturnPoint(tag.getString("dimension"), tag.getDouble("x"), tag.getDouble("y"),
                tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
    }
}
