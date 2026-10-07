package com.cobbletowers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The one logger: every line carries the same name so an operator can filter the mod's output. */
public final class TowerLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("CobbleTowers");

    private static final Set<String> LOGGED_ONCE = ConcurrentHashMap.newKeySet();

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

    /** Like {@link #error} but only the first time {@code key} is seen, for failures on hot paths. */
    public static void errorOnce(String key, String message, Object... args) {
        if (LOGGED_ONCE.add(key)) LOGGER.error(message, args);
    }
}
