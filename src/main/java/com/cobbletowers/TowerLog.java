package com.cobbletowers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one logger. Everything in CobbleTowers logs through here so every line carries the same name
 * and a server operator can filter the mod's output in one place.
 */
public final class TowerLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleTowers");

    private TowerLog() {}

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    /** A trailing Throwable in {@code args} is logged with its stack trace, as SLF4J does. */
    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }
}
