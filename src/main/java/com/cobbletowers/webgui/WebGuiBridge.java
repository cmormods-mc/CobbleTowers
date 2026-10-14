package com.cobbletowers.webgui;

import com.cobbletowers.TowerLog;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.network.MasteryScreenPayload;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.RegistrationStatePayload;
import com.cobbletowers.network.SpectatorPanelPayload;
import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.RunEffectsPayload;
import com.cobbletowers.network.ScoutingRevealPayload;
import com.cobbletowers.network.TowerFeatureState;
import com.cobbletowers.network.TowerHallStatePayload;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.network.TrackStatePayload;
import com.cobbletowers.network.VendorCatalogPayload;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Lets the WebGUI mod stand in for CobbleTowers' own client screens (docs/design/webgui-bridge.md).
 *
 * <p>Nothing about the game changes: every screen already has a state payload the server builds and an action payload the
 * server handles. This only changes the transport. A state payload is sent to the player's WebGUI page as JSON instead of
 * to a native screen, and the page's reply is decoded back into the same action payload and given to the same handler.
 * A player with neither client gets what they always got: chat.
 *
 * <p>Everything here runs on the server thread. Without WebGUI installed, or with the config off, {@link #takesOver} is
 * always false and no WebGUI class is ever loaded.
 */
public final class WebGuiBridge {

    /** The handshake channel WebGUI's client registers; its presence is how we know a player can see a page. */
    private static final ResourceLocation WEBGUI_HELLO = ResourceLocation.fromNamespaceAndPath("webgui", "hello");
    /** WebGUI refuses an emitted payload longer than this, so a longer one is dropped here with a warning. */
    private static final int MAX_EMIT = 32_000;
    /** How long a page just opened is given to load and say hello before it is considered lost. */
    private static final long LOAD_MILLIS = 6_000;

    /**
     * What the page can show today (P44, one entry per screen built). Anything else keeps its old behaviour: a native screen if
     * the client has one, else chat. Add a payload here in the same change that adds its screen to {@code web/}.
     */
    private static final Set<CustomPacketPayload.Type<?>> SUPPORTED = Set.of(
            IntermissionStatePayload.TYPE, RentalDraftPayload.TYPE, RewardRevealPayload.TYPE, VendorCatalogPayload.TYPE,
            RunEffectsPayload.TYPE, TowerHallStatePayload.TYPE, PlayStatePayload.TYPE, TowerFeatureState.TYPE, TrackStatePayload.TYPE,
            RegistrationStatePayload.TYPE, MasteryScreenPayload.TYPE,
            ScoutingRevealPayload.TYPE, SpectatorPanelPayload.TYPE);

    private static volatile WebGuiConfig config = WebGuiConfig.OFF;
    private static volatile boolean linked;
    private static final Map<UUID, PageSession> SESSIONS = new ConcurrentHashMap<>();

    private WebGuiBridge() {}

    /** Called once from the mod entrypoint. */
    public static void install() {
        WebGuiConfig loaded = WebGuiConfig.load();
        if (!loaded.enabled()) return;
        if (!FabricLoader.getInstance().isModLoaded("webgui")) {
            TowerLog.warn("cobbletowers-webgui.json is enabled but the WebGUI mod is not installed; tower screens stay native");
            return;
        }
        try {
            WebGuiLink.install();
        } catch (LinkageError | RuntimeException ex) {
            // A WebGUI too old (or too new) to have the API we use: say so once and stay native, never break startup.
            TowerLog.error("The WebGUI mod is installed but its API did not match ({}); tower screens stay native", ex.toString());
            return;
        }
        WebUiFiles.install();
        config = loaded;
        linked = true;
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SESSIONS.remove(handler.getPlayer().getUUID()));
        TowerLog.info("Tower screens can be shown through WebGUI at {} ({} mode)", loaded.url(), loaded.mode());
    }

    // ---- the decision
    // -------------------------------------------------------------------------------------------------

    /**
     * Whether the WebGUI page, rather than a native screen or chat, shows this payload type to this player. In
     * {@code fallback} mode that is only a player with no native channel for it; in {@code prefer} mode, every player who has
     * WebGUI.
     */
    public static boolean takesOver(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        if (!linked || !SUPPORTED.contains(type)) return false;
        if (!ServerPlayNetworking.canSend(player, WEBGUI_HELLO)) return false;
        return config.mode() == WebGuiConfig.Mode.PREFER || !ServerPlayNetworking.canSend(player, type);
    }

    private static PageSession.Policy policyOf(CustomPacketPayload payload) {
        if (payload instanceof RewardRevealPayload || payload instanceof ScoutingRevealPayload) return PageSession.Policy.REPLACE;
        if (payload instanceof IntermissionStatePayload || payload instanceof PlayStatePayload
                || payload instanceof RegistrationStatePayload || payload instanceof MasteryScreenPayload) return PageSession.Policy.UPDATE_OR_OPEN_IF_FLAGGED;
        if (payload instanceof TrackStatePayload || payload instanceof SpectatorPanelPayload) return PageSession.Policy.UPDATE_ONLY;
        if (payload instanceof RunEffectsPayload effects) {
            return effects.floor() > 0 ? PageSession.Policy.UPDATE_OR_OPEN : PageSession.Policy.UPDATE_ONLY;
        }
        return PageSession.Policy.UPDATE_OR_OPEN;
    }

    private static boolean flagged(CustomPacketPayload payload) {
        if (payload instanceof IntermissionStatePayload p) return p.open();
        if (payload instanceof PlayStatePayload p) return p.open();
        if (payload instanceof RegistrationStatePayload p) return p.open();
        if (payload instanceof MasteryScreenPayload p) return p.open();
        return true;
    }

    // ---- server to page
    // ----------------------------------------------------------------------------------------------

    /**
     * Sends a state payload to the player's page if the page is to show it. Returns {@code true} when the bridge handled it
     * (including when it deliberately dropped it), so the caller sends nothing natively.
     */
    public static boolean deliver(ServerPlayer player, CustomPacketPayload payload) {
        if (!takesOver(player, payload.type())) return false;
        String json = PayloadJson.envelope(payload).toString();
        if (json.length() > MAX_EMIT) {
            TowerLog.warn("A {} for {} is {} characters, over WebGUI's limit; not sent", payload.getClass().getSimpleName(),
                    player.getGameProfile().getName(), json.length());
            return true;
        }
        long now = System.currentTimeMillis();
        PageSession session = SESSIONS.computeIfAbsent(player.getUUID(), id -> new PageSession());
        boolean hud = payload instanceof RunEffectsPayload || payload instanceof SpectatorPanelPayload;
        switch (session.offer(PayloadJson.kindOf(payload.getClass()), json, hud, policyOf(payload), flagged(payload), now,
                config.heartbeatSeconds(), LOAD_MILLIS)) {
            case EMIT -> WebGuiLink.emit(player, json);
            // The page is not loaded when the open arrives, so nothing is emitted now: it asks for what it missed with a
            // `hello` as soon as it has loaded (see onPage).
            case OPEN -> {
                if (hud) WebGuiLink.openHud(player, config.url() + "#hud");
                else WebGuiLink.openGui(player, config.url());
            }
            case NOTHING -> { }
        }
        return true;
    }

    // ---- page to server
    // ----------------------------------------------------------------------------------------------

    /** Every message a page posts on the {@code cobbletowers} channel. Runs on the server thread. */
    static void onPage(ServerPlayer player, String raw) {
        JsonObject message;
        try {
            message = JsonParser.parseString(raw).getAsJsonObject();
        } catch (RuntimeException ex) {
            return;
        }
        String kind = message.has("kind") && message.get("kind").isJsonPrimitive() ? message.get("kind").getAsString() : "";
        boolean hud = message.has("hud") && message.get("hud").isJsonPrimitive() && message.get("hud").getAsBoolean();
        long now = System.currentTimeMillis();
        switch (kind) {
            case "hello" -> {
                for (String missed : SESSIONS.computeIfAbsent(player.getUUID(), id -> new PageSession()).hello(hud, now)) {
                    WebGuiLink.emit(player, missed);
                }
            }
            case "ping" -> SESSIONS.computeIfAbsent(player.getUUID(), id -> new PageSession()).ping(hud, now);
            case "bye" -> {
                PageSession session = SESSIONS.get(player.getUUID());
                if (session != null) session.bye(hud);
            }
            default -> PageActions.decode(message).ifPresentOrElse(
                    action -> TowerNetworking.handleAction(player.server, player, action),
                    () -> TowerLog.warn("Ignored an unreadable page message from {}: kind '{}'", player.getGameProfile().getName(), kind));
        }
    }
}
