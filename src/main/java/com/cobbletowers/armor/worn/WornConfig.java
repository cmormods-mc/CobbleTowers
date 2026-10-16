package com.cobbletowers.armor.worn;

import com.cobbletowers.TowerLog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/cobbletowers-armor.json}: the 3D parts drawn on armor wearers (P49). Off unless the file turns it on, until the
 * pack-delivery path is verified on a live server.
 *
 * <pre>{"enabled": true, "hide_from_owner": true, "require_pack": true, "update_ticks": 1, "smoothing_ticks": 2}</pre>
 *
 * {@code require_pack}: show parts only to clients that report Polymer's pack loaded (without it a display shows paper).
 * {@code yaw_offset} and {@code pitch_sign} tune how server rotation maps to the display's rotation.
 */
public record WornConfig(boolean enabled, boolean hideFromOwner, boolean requirePack, int updateTicks, int smoothingTicks,
                         float yawOffset, float pitchSign) {

    public static final WornConfig DEFAULT = new WornConfig(false, true, true, 1, 2, 180f, -1f);

    public static WornConfig load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("cobbletowers-armor.json");
        if (!Files.exists(file)) return DEFAULT;
        try {
            return parse(Files.readString(file));
        } catch (IOException | RuntimeException ex) {
            TowerLog.error("{} is not usable ({}); worn 3D parts use the defaults", file, ex.toString());
            return DEFAULT;
        }
    }

    public static WornConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        WornConfig d = DEFAULT;
        boolean enabled = root.has("enabled") ? root.get("enabled").getAsBoolean() : d.enabled;
        boolean hide = root.has("hide_from_owner") ? root.get("hide_from_owner").getAsBoolean() : d.hideFromOwner;
        boolean pack = root.has("require_pack") ? root.get("require_pack").getAsBoolean() : d.requirePack;
        int update = root.has("update_ticks") ? root.get("update_ticks").getAsInt() : d.updateTicks;
        int smooth = root.has("smoothing_ticks") ? root.get("smoothing_ticks").getAsInt() : d.smoothingTicks;
        if (update < 1 || update > 20) throw new IllegalArgumentException("update_ticks must be 1..20");
        if (smooth < 0 || smooth > 20) throw new IllegalArgumentException("smoothing_ticks must be 0..20");
        float yaw = root.has("yaw_offset") ? root.get("yaw_offset").getAsFloat() : d.yawOffset;
        float pitch = root.has("pitch_sign") ? root.get("pitch_sign").getAsFloat() : d.pitchSign;
        return new WornConfig(enabled, hide, pack, update, smooth, yaw, pitch);
    }
}
