package com.cobbletowers.webgui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A page action that {@link PageActions} decodes but {@code TowerNetworking.handleAction} ignores is rejected as "not a screen action" at run time. */
class ActionCoverageTest {

    /** One valid message per kind the page can send; add one here with every new kind (the first test fails if one is missing). */
    private static final List<String> SAMPLES = List.of(
            "{\"kind\":\"IntermissionAction\",\"action\":\"READY\"}",
            "{\"kind\":\"RentalDraftAction\",\"action\":\"OPEN\"}",
            "{\"kind\":\"PlayAction\",\"action\":\"REFRESH\"}",
            "{\"kind\":\"TowerHallAction\",\"action\":\"refresh\"}",
            "{\"kind\":\"TowerFeatureRequest\",\"requestId\":1,\"section\":\"club\",\"action\":\"open\"}",
            "{\"kind\":\"TrackAction\",\"action\":\"refresh\"}",
            "{\"kind\":\"MasteryRequest\",\"tower\":\"\"}",
            "{\"kind\":\"RanksRequest\",\"left\":\"tideforge\",\"right\":\"\"}",
            "{\"kind\":\"VendorPurchase\",\"serviceId\":\"cobbletowers:full_heal\",\"targetPlayerId\":\"00000000-0000-0000-0000-000000000001\"}");

    private static String read(String path) throws Exception {
        return Files.readString(Paths.get(path));
    }

    @Test
    @DisplayName("every kind the page can send appears in the samples above")
    void everyKindHasASample() throws Exception {
        String source = read("src/main/java/com/cobbletowers/webgui/PageActions.java");
        var kinds = new java.util.TreeSet<String>();
        var matcher = java.util.regex.Pattern.compile("case \"([A-Za-z]+)\" ->").matcher(source);
        while (matcher.find()) kinds.add(matcher.group(1));
        for (String kind : kinds) {
            assertTrue(SAMPLES.stream().anyMatch(s -> s.contains("\"kind\":\"" + kind + "\"")), "no sample for page action kind " + kind);
        }
        assertTrue(kinds.size() >= 8, "found only " + kinds);
    }

    @Test
    @DisplayName("every decoded action is handled by TowerNetworking.handleAction")
    void everyDecodedActionIsHandled() throws Exception {
        String source = read("src/main/java/com/cobbletowers/network/TowerNetworking.java");
        int start = source.indexOf("public static void handleAction");
        int end = source.indexOf("handleAction was given", start);
        assertTrue(start > 0 && end > start, "handleAction not found in TowerNetworking");
        String body = source.substring(start, end);
        for (String sample : SAMPLES) {
            var payload = PageActions.decode(JsonParser.parseString(sample).getAsJsonObject());
            assertTrue(payload.isPresent(), "does not decode: " + sample);
            String type = payload.get().getClass().getSimpleName();
            assertTrue(body.contains("instanceof " + type) || body.contains("instanceof com.cobbletowers.network." + type),
                    type + " is decoded from a page but handleAction has no branch for it");
        }
    }
}
