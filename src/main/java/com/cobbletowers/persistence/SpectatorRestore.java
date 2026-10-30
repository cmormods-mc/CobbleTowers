package com.cobbletowers.persistence;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.GameType;

/** The mode and abilities a temporary Tower spectator must get back, including custom flight. */
public record SpectatorRestore(String mode, boolean invulnerable, boolean flying, boolean mayfly,
                               boolean instabuild, boolean mayBuild, float flyingSpeed, float walkingSpeed) {
    public SpectatorRestore {
        Objects.requireNonNull(mode);
        if (GameType.byName(mode, null) == null) throw new IllegalArgumentException("Unknown original spectator mode");
        if (!Float.isFinite(flyingSpeed) || flyingSpeed < 0 || !Float.isFinite(walkingSpeed) || walkingSpeed < 0) {
            throw new IllegalArgumentException("Invalid spectator restore speeds");
        }
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("mode", mode);
        tag.putBoolean("invulnerable", invulnerable);
        tag.putBoolean("flying", flying);
        tag.putBoolean("mayfly", mayfly);
        tag.putBoolean("instabuild", instabuild);
        tag.putBoolean("mayBuild", mayBuild);
        tag.putFloat("flyingSpeed", flyingSpeed);
        tag.putFloat("walkingSpeed", walkingSpeed);
        return tag;
    }

    public static SpectatorRestore fromTag(CompoundTag tag) {
        if (!tag.contains("mode", Tag.TAG_STRING)) throw new IllegalArgumentException("Missing original spectator mode");
        String mode = tag.getString("mode");
        for (String key : new String[]{"invulnerable", "flying", "mayfly", "instabuild", "mayBuild"}) {
            if (!tag.contains(key, Tag.TAG_BYTE)) throw new IllegalArgumentException("Missing original ability " + key);
        }
        if (!tag.contains("flyingSpeed", Tag.TAG_FLOAT) || !tag.contains("walkingSpeed", Tag.TAG_FLOAT)) {
            throw new IllegalArgumentException("Missing original ability speeds");
        }
        return new SpectatorRestore(mode, tag.getBoolean("invulnerable"), tag.getBoolean("flying"),
                tag.getBoolean("mayfly"), tag.getBoolean("instabuild"), tag.getBoolean("mayBuild"),
                tag.getFloat("flyingSpeed"), tag.getFloat("walkingSpeed"));
    }
}
