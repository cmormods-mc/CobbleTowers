package com.cobbletowers.client;

import com.cobbletowers.definition.RentalSetDefinition;
import com.cobbletowers.network.IntermissionStatePayload;
import com.cobbletowers.network.MasteryScreenPayload;
import com.cobbletowers.network.PlayStatePayload;
import com.cobbletowers.network.RegistrationStatePayload;
import com.cobbletowers.network.RentalDraftPayload;
import com.cobbletowers.network.RewardRevealPayload;
import com.cobbletowers.network.ScoutingRevealPayload;
import com.cobbletowers.network.VendorCatalogPayload;
import com.cobbletowers.rental.RentalDraft;
import com.cobbletowers.rental.RentalDraw;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development tooling, inert unless the environment variable {@code COBBLETOWERS_SCREENSHOTS} names a directory: opens each of this
 * mod's screens with sample data and saves a picture of it, then quits. It exists because the screens cannot otherwise be seen
 * without a person at a client: run the dev client with the variable set ({@code validation/client_screens.py}) and read the pictures.
 *
 * <p>It draws what the screens draw and presses what a player would press (a click on a pack, on two cards), and decides
 * nothing about the game. A production client never sets the variable, so none of this runs there.
 */
public final class ScreenshotHarness {

    private static final Logger LOGGER = LoggerFactory.getLogger("cobbletowers-screens");
    private static final String VARIABLE = "COBBLETOWERS_SCREENSHOTS";

    /** One thing to do and how long to wait after the previous one. */
    private record Step(long waitMs, String label, Runnable action) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static Path directory;
    private static int next;
    private static long due;
    private static boolean started;
    /** The frame-time run puts the cursor where it wants it; the screenshot runs park it in the corner. */
    private static boolean keepCursor;

    private ScreenshotHarness() {}

    public static void installIfRequested() {
        String where = System.getenv(VARIABLE);
        if (where == null || where.isBlank() || FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        directory = Path.of(where);
        try {
            Files.createDirectories(directory);
            script();
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("The screenshot harness could not be set up", ex);
            return;
        }
        LOGGER.info("Screenshot harness armed: {} steps, saving to {}", STEPS.size(), directory);
        ClientTickEvents.END_CLIENT_TICK.register(ScreenshotHarness::tick);
    }

    private static void tick(Minecraft minecraft) {
        // Wait until the game has finished loading and is showing the title screen.
        if (!started) {
            if (minecraft.getOverlay() != null || !(minecraft.screen instanceof TitleScreen)) return;
            started = true;
            due = System.currentTimeMillis() + 3000;
        }
        if (next >= STEPS.size()) return;
        if (System.currentTimeMillis() < due) return;
        if (!keepCursor) org.lwjgl.glfw.GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),0,0);
        Step step = STEPS.get(next++);
        try {
            step.action().run();
        } catch (RuntimeException ex) {
            LOGGER.error("Screenshot step {} failed", step.label(), ex);
        }
        due = System.currentTimeMillis() + (next < STEPS.size() ? STEPS.get(next).waitMs() : 0);
        if (next >= STEPS.size()) {
            LOGGER.info("Screenshot harness finished");
            minecraft.stop();
        }
    }

    private static void shot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            image.writeToFile(directory.resolve(name + ".png"));
            LOGGER.info("Saved {}", name);
        } catch (IOException ex) {
            LOGGER.error("Could not save {}", name, ex);
        }
    }

    private static void add(long waitMs, String label, Runnable action) {
        STEPS.add(new Step(waitMs, label, action));
    }

    // ---- the script --------------------------------------------------------------------------------------------------

    private static void featureScript() {
        var parent=new TowerHallScreen(new com.cobbletowers.network.TowerHallStatePayload(List.of(),List.of(),
                new PlayStatePayload(List.of(),PlayStatePayload.Lobby.none(),List.of(),"",false),"",false));
        add(0,"Progress hub",()->{Minecraft.getInstance().setScreen(parent);parent.navigate("Progress");});
        add(400,"Progress shot",()->featureShot("features_progress"));
        add(0,"Social hub",()->parent.navigate("Social"));
        add(400,"Social shot",()->featureShot("features_social"));
        for(var sample:FeatureScreenSamples.all()) {
            TowerFeatureScreen[] screen=new TowerFeatureScreen[1];
            add(0,sample.section(),()->{screen[0]=TowerFeatureScreen.preview(parent,sample);Minecraft.getInstance().setScreen(screen[0]);});
            add(400,"feature shot",()->featureShot("features_"+sample.section()));
            if(sample.section().equals("cosmetics")) {
                add(0,"Title detail",()->screen[0].mouseClicked(screen[0].contentX+20,screen[0].contentY+63,0));
                add(300,"Title shot",()->featureShot("features_title_detail"));
            }
            if(sample.section().equals("echoes")) {
                add(0,"Echo confirmation",()->screen[0].mouseClicked(screen[0].contentX+20,screen[0].contentY+63,0));
                add(300,"Echo confirmation shot",()->featureShot("features_echo_confirmation"));
            }
            if(sample.section().equals("watch")) {
                add(0,"Watch input",()->screen[0].mouseClicked(screen[0].contentX+20,screen[0].contentY+63,0));
                add(300,"Watch input shot",()->featureShot("features_watch_input"));
            }
        }
        add(0,"Done",()->{});
    }

    private static void featureShot(String name) {
        var screen=Minecraft.getInstance().screen;
        var widgets=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget)
                .map(w->(net.minecraft.client.gui.components.AbstractWidget)w).toList();
        for(var widget:widgets) {
            if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>screen.width||widget.getY()+widget.getHeight()>screen.height)
                throw new IllegalStateException("Control outside screen: "+widget.getMessage().getString());
        }
        shot(name);
    }

    private static void hallScript() {
        var towers = List.of("tideforge", "rootvale", "duskvale", "neutral", "custom_arena").stream()
                .map(id -> new com.cobbletowers.network.TowerHallStatePayload.Destination(
                        ResourceLocation.fromNamespaceAndPath("cobbletowers", id),
                        id.equals("custom_arena") ? "A custom datapack arena with a long title" : id.substring(0,1).toUpperCase()+id.substring(1)+" Tower", 10, 2, true)).toList();
        var play = new PlayStatePayload(List.of(), PlayStatePayload.Lobby.none(), List.of(50,50,50), "", false);
        var trial = new com.cobbletowers.network.TowerHallStatePayload.Trial("daily", "sample",
                "Daily Trial: Stormwatch", towers.getFirst().id(), List.of("5 floors / sample period", "Mode: Level Cap 50", "Enemy level: 50", "First launch is scored; later attempts are practice."));
        var state = new com.cobbletowers.network.TowerHallStatePayload(towers, List.of(trial), play, "", false);
        TowerHallScreen[] hall = new TowerHallScreen[1];
        add(0,"Hall",()->{hall[0]=new TowerHallScreen(state);Minecraft.getInstance().setScreen(hall[0]);});
        add(900,"Hall screenshot",()->shot("hall_home"));
        add(0,"Tower briefing",()->hall[0].mouseClicked(hall[0].contentX+20,hall[0].contentY+45,0));
        add(400,"Briefing screenshot",()->shot("hall_tower"));
        add(0,"Trials",()->hall[0].navigate("Trials"));
        add(400,"Trials screenshot",()->shot("hall_trials"));
        add(0,"Daily",()->hall[0].mouseClicked(hall[0].contentX+20,hall[0].contentY+45,0));
        add(400,"Daily screenshot",()->shot("hall_daily"));
        add(0,"Reduced motion",()->{TowerUiSettings.motion=false;TowerUiSettings.glow=false;hall[0].navigate("Tower Hall");});
        add(400,"Fallback screenshot",()->shot("hall_fallback"));
        add(0,"Done",()->{});
    }


    // ---- modifier selection stages (the approved oak treatment) -----------------------------------------------------------

    private static IntermissionStatePayload.Card sampleCard(String id, String name, int votes, int risk, String theme, String... lines) {
        return new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", id), name, votes, risk, theme, List.of(lines));
    }

    private static IntermissionStatePayload sampleOffer(int state, int chosen, int myVote, List<IntermissionStatePayload.Card> cards) {
        return new IntermissionStatePayload(4, new IntermissionStatePayload.Draft(state, cards, chosen, myVote),
                List.of(new IntermissionStatePayload.Member("Alex", true, false), new IntermissionStatePayload.Member("Sam", false, false),
                        new IntermissionStatePayload.Member("Jo", false, true)), -1, "", true);
    }

    private static net.minecraft.client.gui.components.AbstractWidget cardWidget(int index) {
        int seen = 0;
        for (var child : Minecraft.getInstance().screen.children()) {
            if (child instanceof ModifierCardButton card) {
                if (seen++ == index) return card;
            }
        }
        throw new IllegalStateException("no card " + index);
    }

    private static void press(String label) {
        for (var child : Minecraft.getInstance().screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) {
                if (!button.active) throw new IllegalStateException(label + " is not active");
                button.onPress();
                return;
            }
        }
        throw new IllegalStateException("no button " + label);
    }

    private static void modifierScript() {
        var offer = List.of(
                sampleCard("downpour", "Downpour", 1, 1, "weather:raindance", "Risk: moderate / Stack limit: 1",
                        "Battle weather: raindance", "No direct reward amount multiplier.", "Risk is separate from reward amount. Conditional offers are not guaranteed."),
                sampleCard("glass_cannon", "Glass Cannon", 0, 2, "custom:glass_cannon", "Risk: severe / Stack limit: 1",
                        "Start battles at 60% HP with +2 Attack and Sp. Atk.", "Boss health: 120% of baseline", "Enemy level offset: 3",
                        "Eligible reward amounts: x1.25", "Banned moves: recover, roost, softboiled", "Switching is disabled.",
                        "Items are disabled in battle.", "Conflicts: cobbletowers:iron_hide, cobbletowers:war_banner",
                        "Tags: offense, risky", "Risk is separate from reward amount. Conditional offers are not guaranteed."),
                sampleCard("locked_doors", "Locked Doors", 0, 1, "constraint", "Risk: moderate / Stack limit: 1", "Switching is disabled.",
                        "No direct reward amount multiplier."),
                sampleCard("windfall", "Windfall", 2, 0, "reward_up", "Risk: minor / Stack limit: 2", "Eligible reward amounts: x1.25"));
        var strange = sampleOffer(1, -1, -1, List.of(
                sampleCard("mystery_pack", "Mystery From A Datapack", 0, -1, "unknown", "Mystery From A Datapack"),
                sampleCard("echo_event", "A Quiet Shrine", 0, -1, "event", "A Quiet Shrine"),
                sampleCard("ancient", "Ancient Custom Rule", 0, 0, "custom:not_a_real_behavior", "Risk: minor / Stack limit: 1", "Custom behavior: not_a_real_behavior")));
        var screens = new IntermissionScreen[1];
        add(0, "open offer", () -> {
            TowerUiSettings.motion = true;
            screens[0] = new IntermissionScreen(sampleOffer(1, -1, -1, offer));
            Minecraft.getInstance().setScreen(screens[0]);
        });
        add(120, "shutters closed", () -> featureShot("modifier_01_shutters_closed"));
        add(1000, "mid reveal", () -> featureShot("modifier_02_mid_reveal"));
        add(1700, "revealed", () -> featureShot("modifier_03_revealed"));
        add(0, "focus a card", () -> screens[0].setFocused(cardWidget(0)));
        add(250, "focused", () -> featureShot("modifier_04_focused"));
        add(0, "select card 4", () -> ((net.minecraft.client.gui.components.Button) cardWidget(3)).onPress());
        add(250, "selected", () -> featureShot("modifier_05_selected_inspected"));
        add(0, "select card 2 (long text)", () -> ((net.minecraft.client.gui.components.Button) cardWidget(1)).onPress());
        add(250, "long description", () -> featureShot("modifier_06_long_description_page1"));
        add(0, "next page", () -> press(">"));
        add(250, "page 2", () -> featureShot("modifier_07_long_description_page2"));
        add(0, "confirm", () -> press("Confirm"));
        add(250, "pending", () -> featureShot("modifier_08_pending_confirmation"));
        add(0, "unknown offer", () -> {
            screens[0] = new IntermissionScreen(strange);
            Minecraft.getInstance().setScreen(screens[0]);
        });
        add(2400, "unknown shot", () -> featureShot("modifier_09_unknown_fallback"));
        add(0, "reduced motion", () -> {
            TowerUiSettings.motion = false;
            TowerUiSettings.glow = false;
            screens[0] = new IntermissionScreen(sampleOffer(1, -1, 2, offer));
            Minecraft.getInstance().setScreen(screens[0]);
        });
        add(200, "reduced shot", () -> featureShot("modifier_10_reduced_motion_glow_off"));
        add(0, "empty offer", () -> {
            screens[0] = new IntermissionScreen(sampleOffer(0, -1, -1, List.of()));
            Minecraft.getInstance().setScreen(screens[0]);
        });
        add(200, "empty shot", () -> featureShot("modifier_11_empty_offer"));
        add(0, "settled", () -> {
            screens[0] = new IntermissionScreen(sampleOffer(2, 3, 3, offer));
            Minecraft.getInstance().setScreen(screens[0]);
        });
        add(200, "settled shot", () -> featureShot("modifier_12_settled_chosen"));
        add(0, "Done", () -> {});
    }


    private static void script() throws IOException {
        if ("1".equals(System.getenv("COBBLETOWERS_FEATURES_ONLY"))) { featureScript(); return; }
        if ("1".equals(System.getenv("COBBLETOWERS_HALL_ONLY"))) { hallScript(); return; }
        if ("1".equals(System.getenv("COBBLETOWERS_MODIFIER_ONLY"))) { modifierScript(); return; }
        if ("1".equals(System.getenv("COBBLETOWERS_FRAMETIME_ONLY"))) {
            keepCursor = true;
            FrameSampler.script(ScreenshotHarness::add, directory);
            return;
        }

        List<RentalSetDefinition> pool = rentalPool();
        // A draft whose first pack holds a legendary, so the best reveal is on the screen; and one with a God Pack.
        RentalDraft showy = null;
        RentalDraft god = null;
        for (long seed = 0; seed < 5000 && (showy == null || god == null); seed++) {
            RentalDraw.Offer offer = RentalDraw.draw(pool, seed, true);
            if (god == null && offer.packs().get(0).god()) god = new RentalDraft(offer);
            boolean legendary = offer.packs().get(0).cards().stream().anyMatch(card -> card.rarity() == RentalSetDefinition.Rarity.LEGENDARY);
            if (showy == null && legendary && !offer.hasGodPack()) showy = new RentalDraft(offer);
        }
        if (showy == null || god == null) throw new IOException("no sample drafts found");
        final RentalDraft draft = showy;
        final RentalDraft godDraft = god;
        RentalPackScreen[] screen = new RentalPackScreen[1];

        add(0, "open pack", () -> {
            screen[0] = new RentalPackScreen(RentalDraftPayload.of(draft, ""));
            Minecraft.getInstance().setScreen(screen[0]);
        });
        add(700, "pack table", () -> featureShot("rental_01_pack"));
        add(0, "tear", () -> screen[0].mouseClicked(screen[0].width / 2.0, screen[0].height / 2.0, 0));
        add(600, "tearing", () -> featureShot("rental_02_tearing"));
        add(1500, "revealing early", () -> featureShot("rental_03_reveal_early"));
        add(1300, "revealing late", () -> featureShot("rental_04_reveal_late"));
        add(2000, "choosing", () -> featureShot("rental_05_choosing"));
        add(0, "pick two", () -> {
            clickCard(screen[0], 1);
            clickCard(screen[0], 3);
        });
        add(400, "chosen", () -> featureShot("rental_06_chosen"));
        add(0, "keep pack one", () -> {
            draft.pick(0, List.of(1, 3));
            screen[0].accept(RentalDraftPayload.of(draft, ""));
        });
        add(500, "second pack", () -> featureShot("rental_07_second_pack"));
        add(0, "finish draft", () -> {
            draft.pick(1, List.of(0, 2));
            draft.pick(2, List.of(1, 4));
            screen[0].accept(RentalDraftPayload.of(draft, ""));
        });
        add(500, "team", () -> featureShot("rental_08_team"));
        add(0, "god pack", () -> {
            screen[0] = new RentalPackScreen(RentalDraftPayload.of(godDraft, ""));
            Minecraft.getInstance().setScreen(screen[0]);
        });
        add(700, "god pack table", () -> featureShot("rental_09_god_pack"));
        add(0, "skip into the god cards", () -> screen[0].mouseClicked(screen[0].width / 2.0, screen[0].height / 2.0, 0));
        add(5200, "god cards", () -> featureShot("rental_10_god_cards"));
        add(0, "refused pick", () -> {
            screen[0].accept(RentalDraftPayload.of(godDraft, "A team may keep at most 2 legendary or mythic Pokemon."));
        });
        add(300, "message", () -> featureShot("rental_11_message"));

        // The play screen as a host in Rental mode, then as a plain host; and the mastery screen with the real achievements.
        ResourceLocation tower = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge");
        List<PlayStatePayload.Tower> towers = List.of(
                new PlayStatePayload.Tower(ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral"), "Challenger Tower"),
                new PlayStatePayload.Tower(tower, "Tideforge Spire"),
                new PlayStatePayload.Tower(ResourceLocation.fromNamespaceAndPath("cobbletowers", "rootvale"), "Rootvale Keep"),
                new PlayStatePayload.Tower(ResourceLocation.fromNamespaceAndPath("cobbletowers", "duskvale"), "Duskvale Crypt"));
        List<String> ids = List.of("hardcore", "level_cap_50", "monotype", "rental", "solo_gauntlet", "underdog");
        List<String> names = List.of("Hardcore", "Level Cap 50", "Monotype", "Rental Draft", "Solo Gauntlet", "Underdog");
        add(0, "play screen, rental", () -> Minecraft.getInstance().setScreen(new PlayScreen(new PlayStatePayload(towers,
                new PlayStatePayload.Lobby(1, tower.toString(), "Alex", List.of(new PlayStatePayload.Member("Sam", true, true),
                        new PlayStatePayload.Member("Jo", false, false)), -1,
                        new PlayStatePayload.Options(new PlayStatePayload.Depth(2, 4, true),
                                new PlayStatePayload.Modes(ids, names, "rental", true, new PlayStatePayload.Readiness(true, true, false, true)))),
                List.of(100, 87, 64, 50, 50, 12), "Mode: Rental Draft. Open your packs with /tower draft.", true))));
        add(600, "play screen shot", () -> featureShot("play_01_rental_host"));
        add(0, "play screen, plain", () -> Minecraft.getInstance().setScreen(new PlayScreen(new PlayStatePayload(towers,
                PlayStatePayload.Lobby.none(), List.of(100, 87, 64, 50, 50, 12), "", true))));
        add(600, "play screen plain shot", () -> featureShot("play_02_no_lobby"));
        add(0, "intermission", () -> Minecraft.getInstance().setScreen(new IntermissionScreen(new IntermissionStatePayload(3,
                new IntermissionStatePayload.Draft(1, List.of(
                        new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "downpour"), "Downpour", 2, 1, "weather:raindance", List.of("Risk: moderate / Stack limit: 1","Battle weather: raindance","No direct reward amount multiplier.")),
                        new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "grassy_terrain"), "Grassy Terrain", 0, 0, "terrain:grassyterrain", List.of("Risk: minor / Stack limit: 1","Battle terrain: grassyterrain","No direct reward amount multiplier.")),
                        new IntermissionStatePayload.Card(ResourceLocation.fromNamespaceAndPath("cobbletowers", "empty_pockets"), "Empty Pockets", 1, 1, "reward_down", List.of("Risk: moderate / Stack limit: 1","Eligible reward amounts: x0.50"))),
                        -1, 0),
                List.of(new IntermissionStatePayload.Member("Alex", true, false), new IntermissionStatePayload.Member("Sam", false, false),
                        new IntermissionStatePayload.Member("Jo", false, true)),
                27, "Vote for the next floor's modifier.", true))));
        add(600, "intermission shot", () -> featureShot("intermission_01_draft"));
        add(0, "vendor", () -> Minecraft.getInstance().setScreen(new VendorScreen(new VendorCatalogPayload(18450, List.of(
                new VendorCatalogPayload.Entry(ResourceLocation.fromNamespaceAndPath("cobbletowers", "heal_party"), "Heal the party", 1200, 3),
                new VendorCatalogPayload.Entry(ResourceLocation.fromNamespaceAndPath("cobbletowers", "revive_one"), "Revive one Pokemon", 2500, 2),
                new VendorCatalogPayload.Entry(ResourceLocation.fromNamespaceAndPath("cobbletowers", "reroll_draft"), "Reroll the modifier draft", 900, 1)),
                List.of(new VendorCatalogPayload.Teammate(new java.util.UUID(0, 1), "Alex", true),
                        new VendorCatalogPayload.Teammate(new java.util.UUID(0, 2), "Sam", true),
                        new VendorCatalogPayload.Teammate(new java.util.UUID(0, 3), "Jo", false)),
                "Healed Sam's party."))));
        add(600, "vendor shot", () -> featureShot("vendor_01_catalog"));
        add(0, "registration", () -> Minecraft.getInstance().setScreen(new RegistrationScreen(new RegistrationStatePayload(List.of(
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 1), "Glaceon", 100, false, "Party 1"),
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 2), "Garchomp", 78, false, "Party 2"),
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 3), "Magikarp", 1, true, "Party 3"),
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 4), "Snorlax", 60, false, "Box 1"),
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 5), "Charizard", 50, false, "Box 1"),
                new RegistrationStatePayload.Entry(new java.util.UUID(1, 6), "Gengar", 45, false, "Box 2")),
                List.of(new java.util.UUID(1, 1), new java.util.UUID(1, 4)), 3, "Choose up to 3.", true))));
        add(600, "registration shot", () -> featureShot("registration_01_chooser"));
        add(0, "reward reveal", () -> Minecraft.getInstance().setScreen(new RewardRevealScreen(new RewardRevealPayload(4, List.of(
                new RewardRevealPayload.Grant(ResourceLocation.fromNamespaceAndPath("cobblemon", "rare_candy"), 3),
                new RewardRevealPayload.Grant(ResourceLocation.fromNamespaceAndPath("cobblemon", "ultra_ball"), 12),
                new RewardRevealPayload.Grant(ResourceLocation.fromNamespaceAndPath("minecraft", "diamond"), 2))))));
        add(600, "reward reveal shot", () -> featureShot("reward_01_reveal"));
        add(0, "scouting", () -> Minecraft.getInstance().setScreen(new ScoutingScreen(new ScoutingRevealPayload(5, List.of(
                new ScoutingRevealPayload.Category("Types", "Water, Ice"),
                new ScoutingRevealPayload.Category("Boss", "Gyarados"),
                new ScoutingRevealPayload.Category("Modifier", "Downpour"))))));
        add(600, "scouting shot", () -> featureShot("scouting_01_report"));
        // The armor set tooltip (P25), over a piece of the set, as the server would describe it. The viewer wears nothing, so the
        // tiers show as locked; Shift (the checklist) cannot be held from here.
        add(0, "armor tooltip", () -> {
            ResourceLocation helmet = ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge_helmet");
            ArmorTooltips.update(List.of(new com.cobbletowers.armor.ArmorSetView(ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge"),
                    "Tideforged Plate", 0x46B4E6,
                    List.of(new com.cobbletowers.armor.ArmorSetView.Piece("head", helmet),
                            new com.cobbletowers.armor.ArmorSetView.Piece("chest", ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge_chestplate")),
                            new com.cobbletowers.armor.ArmorSetView.Piece("legs", ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge_leggings")),
                            new com.cobbletowers.armor.ArmorSetView.Piece("feet", ResourceLocation.fromNamespaceAndPath("cobbletowers", "tideforge_boots"))),
                    List.of(new com.cobbletowers.armor.ArmorSetView.Tier(2, List.of("+1 underwater breathing", "+50% water movement", "+5% catch rate on Water types")),
                            new com.cobbletowers.armor.ArmorSetView.Tier(4, List.of("Water attacks hit 10% harder", "Vendor prices -5%"))))));
            Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("tooltip")) {
                @Override
                public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                    super.render(graphics, mouseX, mouseY, partialTick);
                    graphics.renderTooltip(font, new net.minecraft.world.item.ItemStack(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.get(helmet)), width / 2 - 40, 30);
                }
            });
        });
        add(600, "armor tooltip shot", () -> featureShot("armor_01_tooltip"));
        add(0, "mastery", () -> Minecraft.getInstance().setScreen(new MasteryScreen(masteryPayload(tower, "mastery"))));
        add(600, "mastery shot", () -> featureShot("mastery_01_achievements"));
        add(0, "mastery board", () -> Minecraft.getInstance().setScreen(new MasteryScreen(masteryPayload(tower, "speed"))));
        add(600, "mastery board shot", () -> featureShot("mastery_02_board"));
        add(0,"Partner collection",()->Minecraft.getInstance().setScreen(new PartnerInspectionScreen(null,null)));
        add(700,"Partner shot",()->featureShot("modern_partner"));
        add(0,"Settings",()->Minecraft.getInstance().setScreen(new TowerOptionsScreen(null)));
        add(500,"Settings shot",()->featureShot("modern_settings"));
        add(0,"Shader settings",()->Minecraft.getInstance().screen.mouseClicked(30,130,0));
        add(500,"Shader settings shot",()->featureShot("modern_shader_settings"));
        add(0,"Reduced motion",()->TowerUiSettings.motion=false);
        add(300,"Reduced motion shot",()->featureShot("modern_reduced_motion"));
        add(0,"Glow off",()->TowerUiSettings.glow=false);
        add(300,"Fallback shot",()->featureShot("modern_fallback"));
        add(0,"Restore presentation",()->{TowerUiSettings.motion=true;TowerUiSettings.glow=true;});
    }

    private static MasteryScreenPayload masteryPayload(ResourceLocation tower, String tab) {
        List<MasteryScreenPayload.Achievement> achievements = new ArrayList<>();
        try {
            Path root = FabricLoader.getInstance().getModContainer("cobbletowers").orElseThrow()
                    .findPath("data/cobbletowers/cobbletowers/achievements").orElseThrow();
            try (var files = Files.list(root)) {
                int n = 0;
                for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                    try (Reader reader = Files.newBufferedReader(file)) {
                        var json = JsonParser.parseReader(reader).getAsJsonObject();
                        achievements.add(new MasteryScreenPayload.Achievement(json.get("display_name").getAsString(),
                                json.get("description").getAsString(), n++ % 3 != 0));
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            LOGGER.error("Could not read the achievements", ex);
        }
        return new MasteryScreenPayload(
                List.of(new MasteryScreenPayload.Tower(tower, "Tideforge Spire", 14, "Gold"),
                        new MasteryScreenPayload.Tower(ResourceLocation.fromNamespaceAndPath("cobbletowers", "neutral"), "Challenger Tower", 3, "Bronze")),
                tower.toString(), tab,
                new MasteryScreenPayload.Mastery("Mastery level 14 (Gold): 20 of 30 achievements. 4 more for Platinum.",
                        "+4% reward items, 2 free vendor rerolls, a title", achievements),
                new MasteryScreenPayload.Board("Fastest cycle clear", true,
                        List.of("1. Alex  6m 41s", "2. Sam  7m 02s", "3. Jo  7m 55s", "4. Riley  8m 12s", "5. Kai  9m 30s"),
                        List.of("1. Alex + Sam  5m 12s", "2. Jo + Riley  6m 03s")), true);
    }

    private static void clickCard(RentalPackScreen screen, int index) {
        int[] rect = screen.cardRect(index, 5, screen.scale());
        screen.mouseClicked(rect[0] + rect[2] / 2.0, rect[1] + rect[3] / 2.0, 0);
    }

    /** Every shipped rental set, read from the mod's own resources. */
    private static List<RentalSetDefinition> rentalPool() throws IOException {
        Path root = FabricLoader.getInstance().getModContainer("cobbletowers").orElseThrow()
                .findPath("data/cobbletowers/cobbletowers/rental_sets").orElseThrow();
        List<RentalSetDefinition> all = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                try (Reader reader = Files.newBufferedReader(file)) {
                    all.add(RentalSetDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("cobbletowers", name),
                            JsonParser.parseReader(reader).getAsJsonObject()));
                }
            }
        }
        return all;
    }
}
