package com.cobbletowers.webgui;

import com.cobbletowers.network.IntermissionActionPayload;
import com.cobbletowers.network.MasteryRequestPayload;
import com.cobbletowers.network.PlayActionPayload;
import com.cobbletowers.network.RentalDraftActionPayload;
import com.cobbletowers.network.TowerFeatureRequest;
import com.cobbletowers.network.TowerHallActionPayload;
import com.cobbletowers.network.TrackActionPayload;
import com.cobbletowers.network.VendorPurchasePayload;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Turns a page's message into the same action payload the native screens send, so a click on a web page and a click on
 * a native screen reach exactly the same server handler. A page is untrusted input exactly as a modified client is:
 * nothing here decides whether an action is allowed (the handlers re-check everything, as they always have), this only
 * refuses what cannot even be read, and bounds what it does read.
 */
public final class PageActions {

    private static final int MAX_TEXT = 256;
    private static final int MAX_INDEX = 255;

    private PageActions() {}

    /** {@code empty} for an unknown {@code kind} or a message that is malformed in any way. Never throws. */
    public static Optional<CustomPacketPayload> decode(JsonObject message) {
        try {
            String kind = text(message, "kind", null);
            if (kind == null) return Optional.empty();
            return Optional.ofNullable(switch (kind) {
                case "IntermissionAction" -> new IntermissionActionPayload(
                        constant(IntermissionActionPayload.Action.class, message, "action"), index(message, "argument"));
                case "RentalDraftAction" -> new RentalDraftActionPayload(
                        constant(RentalDraftActionPayload.Action.class, message, "action"), index(message, "a"), index(message, "b"));
                case "PlayAction" -> new PlayActionPayload(
                        constant(PlayActionPayload.Action.class, message, "action"), text(message, "argument", ""));
                case "TowerHallAction" -> new TowerHallActionPayload(
                        text(message, "action", null), text(message, "argument", ""), text(message, "revision", ""));
                case "TowerFeatureRequest" -> new TowerFeatureRequest(requestId(message), text(message, "section", null),
                        text(message, "action", null), text(message, "argument", ""));
                case "TrackAction" -> new TrackActionPayload(
                        text(message, "action", null), text(message, "lane", ""), text(message, "tower", ""), index(message, "number"));
                case "MasteryRequest" -> new MasteryRequestPayload(text(message, "tower", ""), text(message, "tab", "mastery"));
                case "VendorPurchase" -> new VendorPurchasePayload(
                        ResourceLocation.parse(text(message, "serviceId", null)), UUID.fromString(text(message, "targetPlayerId", null)));
                default -> null;
            });
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** A string field, at most {@link #MAX_TEXT} characters; {@code fallback} when absent, an error when absent with no fallback. */
    private static String text(JsonObject message, String name, String fallback) {
        if (!message.has(name) || message.get(name).isJsonNull()) {
            if (fallback == null && !"kind".equals(name)) throw new IllegalArgumentException("missing " + name);
            return fallback;
        }
        String value = message.get(name).getAsString();
        if (value.length() > MAX_TEXT) throw new IllegalArgumentException(name + " is too long");
        return value;
    }

    /** A small non-negative number (a card or pack index, a level); -1 is allowed because the screens use it for "none". */
    private static int index(JsonObject message, String name) {
        if (!message.has(name) || message.get(name).isJsonNull()) return 0;
        int value = message.get(name).getAsInt();
        if (value < -1 || value > MAX_INDEX) throw new IllegalArgumentException(name + " is out of range");
        return value;
    }

    /** The page's own request number; the server echoes it back so a late reply can be told from the current one. */
    private static int requestId(JsonObject message) {
        if (!message.has("requestId") || message.get("requestId").isJsonNull()) return 0;
        int value = message.get("requestId").getAsInt();
        if (value < 0 || value > 1_000_000) throw new IllegalArgumentException("requestId is out of range");
        return value;
    }

    private static <E extends Enum<E>> E constant(Class<E> type, JsonObject message, String name) {
        return Enum.valueOf(type, text(message, name, null).toUpperCase(Locale.ROOT));
    }
}
