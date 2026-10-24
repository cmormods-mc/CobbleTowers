package com.cobbletowers.webgui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebGuiConfigTest {

    @Test
    @DisplayName("an empty file is all defaults, and the bridge is off unless the file turns it on")
    void defaults() {
        WebGuiConfig config = WebGuiConfig.parse("{}");
        assertFalse(config.enabled());
        assertEquals(WebGuiConfig.DEFAULT_URL, config.url());
        assertEquals(WebGuiConfig.Mode.FALLBACK, config.mode());
        assertFalse(WebGuiConfig.OFF.enabled());
    }

    @Test
    @DisplayName("the file written on first start reads back as the default-on config")
    void writtenFile() {
        assertEquals(WebGuiConfig.DEFAULT_ON, WebGuiConfig.parse(WebGuiConfig.DEFAULT_FILE));
    }

    @Test
    @DisplayName("every setting is read")
    void reads() {
        WebGuiConfig config = WebGuiConfig.parse(
                "{\"enabled\":true,\"url\":\"https://example.com/hall\",\"mode\":\"prefer\",\"heartbeat_seconds\":5}");
        assertTrue(config.enabled());
        assertEquals("https://example.com/hall", config.url());
        assertEquals(WebGuiConfig.Mode.PREFER, config.mode());
        assertEquals(5, config.heartbeatSeconds());
    }

    @Test
    @DisplayName("a bad value throws, so the loader keeps the bridge off rather than half-on")
    void refusesBadValues() {
        assertThrows(RuntimeException.class, () -> WebGuiConfig.parse("{\"mode\":\"always\"}"));
        assertThrows(RuntimeException.class, () -> WebGuiConfig.parse("{\"url\":\"\"}"));
        assertThrows(RuntimeException.class, () -> WebGuiConfig.parse("{\"url\":\"webgui:/a.html#hall\"}"));
        assertThrows(RuntimeException.class, () -> WebGuiConfig.parse("{\"heartbeat_seconds\":1}"));
        assertThrows(RuntimeException.class, () -> WebGuiConfig.parse("not json"));
    }
}
