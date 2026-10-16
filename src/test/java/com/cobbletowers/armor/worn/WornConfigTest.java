package com.cobbletowers.armor.worn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WornConfigTest {

    @Test
    @DisplayName("an empty file is the defaults, and the parts are off unless the file turns them on")
    void defaults() {
        WornConfig config = WornConfig.parse("{}");
        assertFalse(config.enabled());
        assertTrue(config.hideFromOwner());
        assertTrue(config.requirePack());
        assertEquals(1, config.updateTicks());
        assertEquals(WornConfig.DEFAULT, config);
    }

    @Test
    @DisplayName("every setting is read")
    void reads() {
        WornConfig config = WornConfig.parse("{\"enabled\":true,\"hide_from_owner\":false,\"require_pack\":false,"
                + "\"update_ticks\":2,\"smoothing_ticks\":3,\"yaw_offset\":90,\"pitch_sign\":1}");
        assertTrue(config.enabled());
        assertFalse(config.hideFromOwner());
        assertFalse(config.requirePack());
        assertEquals(2, config.updateTicks());
        assertEquals(3, config.smoothingTicks());
        assertEquals(90f, config.yawOffset());
        assertEquals(1f, config.pitchSign());
    }

    @Test
    @DisplayName("a bad value throws, so the loader falls back to the defaults")
    void refusesBadValues() {
        assertThrows(RuntimeException.class, () -> WornConfig.parse("{\"update_ticks\":0}"));
        assertThrows(RuntimeException.class, () -> WornConfig.parse("{\"smoothing_ticks\":40}"));
        assertThrows(RuntimeException.class, () -> WornConfig.parse("[1]"));
    }
}
