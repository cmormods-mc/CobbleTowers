package com.cobbletowers.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobbletowers.TowerLog;
import com.cobbletowers.api.tower.RunEvent;
import com.cobbletowers.api.tower.RunState;
import com.cobbletowers.battle.cobblemon.PartyReader;
import com.cobbletowers.battle.cobbleraids.TowerBossAdapter;
import com.cobbletowers.definition.ModifierDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.definition.VendorServiceDefinition;
import com.cobbletowers.economy.VendorPurchaseService;
import com.cobbletowers.intermission.IntermissionService;
import com.cobbletowers.modifier.DraftService;
import com.cobbletowers.modifier.ModifierEffects;
import com.cobbletowers.modifier.ModifierResolver;
import com.cobbletowers.encounter.TowerEncounters;
import com.cobbletowers.network.TowerNetworking;
import com.cobbletowers.persistence.PendingTowerReward;
import com.cobbletowers.persistence.PersistedDraft;
import com.cobbletowers.persistence.PersistedParticipant;
import com.cobbletowers.persistence.LedgerEntry;
import com.cobbletowers.persistence.PersistedRun;
import com.cobbletowers.persistence.TowerPendingRewardStore;
import com.cobbletowers.persistence.TowerWalletStore;
import com.cobbletowers.runtime.RunFactory;
import com.cobbletowers.runtime.RunLifecycle;
import com.cobbletowers.runtime.RunTransitionService;
import com.cobbletowers.runtime.TowerPresence;
import com.cobbletowers.runtime.TowerRuns;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /cobbletowers runs list|show|create|advance|...}: the run machine, visible and drivable. {@code create} and
 * {@code advance} are dev tools.
 */
public final class RunsCommand {

    private RunsCommand() {}

    private static LiteralArgumentBuilder<CommandSourceStack> op(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, UUID> runArg() {
        return Commands.argument("run", UuidArgument.uuid());
    }

    /** An operator literal that takes just a run id. */
    private static LiteralArgumentBuilder<CommandSourceStack> opOnRun(String name, Command<CommandSourceStack> action) {
        return op(name).then(runArg().executes(action));
    }

    private static SuggestionProvider<CommandSourceStack> suggesting(List<String> values) {
        return (context, builder) -> {
            values.forEach(builder::suggest);
            return builder.buildFuture();
        };
    }

    /** Each subcommand carries its own {@code requires}: Brigadier keeps the first registration's on a merged literal. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cobbletowers")
                .then(playerCommands())
                .then(operatorCommands()));
    }

    /** Commands a player runs for themselves: no permission. */
    private static LiteralArgumentBuilder<CommandSourceStack> playerCommands() {
        LiteralArgumentBuilder<CommandSourceStack> group = Commands.literal("runs");
        group.then(Commands.literal("leave").executes(RunsCommand::leave));
        // Cashing out is the party's decision; not blocked by an open draft.
        group.then(Commands.literal("cashout").executes(RunsCommand::cashout));
        group.then(Commands.literal("reward").then(Commands.literal("show").executes(RunsCommand::rewardShow)));
        // INTERMISSION-only is enforced by VendorPurchaseService; credit and buy are operator test tools.
        group.then(Commands.literal("vendor").executes(RunsCommand::vendor)
                .then(op("credit")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(RunsCommand::vendorCredit))))
                .then(op("buy")
                        .then(runArg()
                                .then(Commands.argument("service", ResourceLocationArgument.id())
                                        .then(Commands.argument("payer", EntityArgument.player())
                                                .then(Commands.argument("target", EntityArgument.player())
                                                        .executes(RunsCommand::vendorBuy)))))));
        // {@code force} puts a modifier on a run without a draw (operator).
        group.then(Commands.literal("draft")
                .then(Commands.literal("show").executes(RunsCommand::draftShow))
                .then(Commands.literal("vote")
                        .then(Commands.argument("card", IntegerArgumentType.integer(1, 9))
                                .executes(RunsCommand::draftVote)))
                .then(opOnRun("force", RunsCommand::draftForce)));
        return group;
    }

    /** Dev tools that drive the run machine by hand: permission 2. */
    private static LiteralArgumentBuilder<CommandSourceStack> operatorCommands() {
        LiteralArgumentBuilder<CommandSourceStack> group = Commands.literal("runs");
        group.then(op("grant")
                .then(runArg()
                        .then(Commands.argument("modifier", ResourceLocationArgument.id())
                                .executes(RunsCommand::grant))));
        group.then(op("list").executes(RunsCommand::list));
        group.then(opOnRun("show", RunsCommand::show));
        group.then(op("create")
                .then(Commands.argument("tower", ResourceLocationArgument.id())
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(RunsCommand::create))));
        group.then(opOnRun("validate", RunsCommand::validate));
        // Queues battle effects (P23) for a player's next tower battle; `fx <player> clear` empties the queue.
        group.then(op("fx")
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("json", StringArgumentType.greedyString())
                                .executes(RunsCommand::fx))));
        group.then(op("earn")
                .then(runArg()
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(suggesting(List.of("opponent", "boss", "floor", "milestone")))
                                .executes(RunsCommand::earn))));
        group.then(opOnRun("allocate", RunsCommand::allocate));
        group.then(opOnRun("encounter", RunsCommand::encounter));
        group.then(op("watchdog")
                .then(Commands.argument("cap", StringArgumentType.word())
                        .suggests(suggesting(List.of("player", "floor")))
                        .executes(RunsCommand::watchdog)));
        group.then(op("advance")
                .then(runArg()
                        .then(Commands.argument("event", StringArgumentType.word())
                                .suggests(suggesting(Arrays.stream(RunEvent.values())
                                        .map(event -> event.name().toLowerCase(Locale.ROOT)).toList()))
                                .executes(RunsCommand::advance))));
        return group;
    }

    /**
     * {@code /cobbletowers runs watchdog player|floor}: runs the real sweep with the clock wound forward, so the caps
     * can be tested.
     */
    private static int watchdog(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String cap = StringArgumentType.getString(context, "cap");
        long ahead = cap.equalsIgnoreCase("floor")
                ? TowerPresence.FLOOR_STALL_MILLIS : TowerPresence.PLAYER_STALL_MILLIS;

        int floors = TowerEncounters.active();
        TowerPresence.sweep(source.getServer(), System.currentTimeMillis() + ahead + 1);
        source.sendSuccess(() -> Component.literal("Swept " + floors + " floor(s) as if the " + cap
                + " cap had passed").withStyle(ChatFormatting.GOLD), false);
        return floors;
    }

    /** {@code /cobbletowers runs leave}: terminal for the player; ends the run if they were the last (TDS #39). */
    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!TowerPresence.leave(source.getServer(), player, System.currentTimeMillis())) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You have left the tower run.")
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /** {@code /cobbletowers runs cashout}: casts a cash-out vote; a strict majority cashes out. */
    private static int cashout(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String reply = IntermissionService.cashOut(source.getServer(), player, true);
        source.sendSuccess(() -> Component.literal(reply).withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    /** {@code /cobbletowers runs reward show}: what is banked, at risk and queued for the caller. */
    private static int rewardShow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        PersistedRun run = found.get();
        source.sendSuccess(() -> Component.literal("Banked through floor " + run.lastBankedFloor())
                .withStyle(ChatFormatting.GOLD), false);
        for (var entry : run.ledger()) {
            boolean banked = entry.floorIndex() <= run.lastBankedFloor();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s floor %d %s  [%s]",
                    entry.kind(), entry.floorIndex(), entry.what(), banked ? "banked" : "at risk")), false);
        }
        List<PendingTowerReward> pending = TowerPendingRewardStore.get(source.getServer())
                .queueFor(player.getUUID());
        source.sendSuccess(() -> Component.literal("Queued for you: " + pending.size() + " grant(s)"), false);
        for (PendingTowerReward reward : pending) {
            source.sendSuccess(() -> Component.literal("  " + reward.item() + " x" + reward.amount()
                    + " (floor " + reward.floorIndex() + ")"), false);
        }
        return pending.size();
    }

    /**
     * {@code /cobbletowers runs vendor}: opens the shop screen, or a chat listing on a client without the channel.
     */
    private static int vendor(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        PersistedRun run = found.get();
        if (com.cobbletowers.definition.PlaylistRegistry.vendorClosed(run)) {
            source.sendFailure(Component.literal("The vendor is closed in this mode."));
            return 0;
        }
        TowerNetworking.sendVendorCatalog(source.getServer(), player, run);

        long balance = TowerWalletStore.get(source.getServer()).balanceOf(player.getUUID());
        source.sendSuccess(() -> Component.literal("CobbleDollars: " + balance).withStyle(ChatFormatting.GOLD), false);
        for (VendorServiceDefinition service : TowerDefinitionRegistry.content().vendorCatalog()) {
            int cap = service.maxPurchasesPerRun();
            String remaining = cap <= 0 ? "unlimited" : (cap - run.purchasesOf(service.id())) + "/" + cap + " left";
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  %s -- %d CobbleDollars (%s)",
                    service.displayName(),
                    com.cobbletowers.armor.ArmorBonusEffects.vendorPrice(player, service.priceCobbleDollars()),
                    remaining)), false);
        }
        return TowerDefinitionRegistry.content().vendorCatalog().size();
    }

    /** {@code /cobbletowers runs vendor credit <player> <amount>}: an operator's tool, not a reward. */
    private static int vendorCredit(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int amount = IntegerArgumentType.getInteger(context, "amount");

        TowerWalletStore store = TowerWalletStore.get(source.getServer());
        store.credit(player.getUUID(), amount);
        store.checkpoint(source.getServer());
        source.sendSuccess(() -> Component.literal("Credited " + amount + " CobbleDollars to "
                + player.getGameProfile().getName()), true);
        return amount;
    }

    /** {@code /cobbletowers runs vendor buy <run> <service> <payer> <target>}: an operator's tool. */
    private static int vendorBuy(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        ResourceLocation serviceId = ResourceLocationArgument.getId(context, "service");
        ServerPlayer payer = EntityArgument.getPlayer(context, "payer");
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        VendorPurchaseService.Result result = VendorPurchaseService.purchase(
                source.getServer(), runId, payer.getUUID(), target.getUUID(), serviceId);
        if (result != VendorPurchaseService.Result.SUCCESS) {
            source.sendFailure(Component.literal("Could not buy " + serviceId + ": " + result));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Bought " + serviceId + " for "
                + target.getGameProfile().getName() + ": " + result), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<PersistedRun> runs = TowerRuns.all();
        source.sendSuccess(() -> Component.literal(runs.size() + " run(s)").withStyle(ChatFormatting.GOLD), false);
        for (PersistedRun run : runs) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  %s  %s  floor %d  %d player(s)", run.runId(), run.towerId(), run.state(),
                    run.floorIndex(), run.participants().size())), false);
        }
        return runs.size();
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No run " + runId));
            return 0;
        }
        PersistedRun run = found.get();
        source.sendSuccess(() -> Component.literal(run.runId() + "  " + run.state())
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  tower %s rev %d  digest %s  ruleset rev %d  structure rev %d",
                run.towerId(), run.towerRevision(),
                // The first eight hex characters are enough to see that content changed.
                run.towerDigest().isEmpty() ? "none" : run.towerDigest().substring(0, 8),
                run.rulesetRevision(), run.structureRevision())), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "  floor %d  seed %d  updated %d",
                run.floorIndex(), run.seed(), run.updatedAt())), false);
        source.sendSuccess(() -> Component.literal("  cell " + (run.cell().isPresent()
                ? String.valueOf(run.cell().getAsInt()) : "none")), false);
        source.sendSuccess(() -> Component.literal("  checkpoint " + run.lastCheckpoint()
                .map(checkpoint -> checkpoint.key() + " @ " + checkpoint.state()).orElse("none")), false);
        source.sendSuccess(() -> Component.literal("  committed " + run.committedTransactions()), false);
        source.sendSuccess(() -> Component.literal("  unclaimed pool: " + run.ledger().size() + " entry(s)"), false);
        for (var entry : run.ledger()) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "    %s floor %d %s",
                    entry.kind(), entry.floorIndex(), entry.what())), false);
        }
        run.options().playlist().ifPresent(playlist -> source.sendSuccess(() -> Component.literal("  mode: "
                + com.cobbletowers.definition.PlaylistRegistry.get(playlist)
                        .map(com.cobbletowers.definition.PlaylistDefinition::displayName).orElse(playlist.toString())), false));
        run.options().trial().ifPresent(trial -> source.sendSuccess(() -> Component.literal("  trial: " + trial + " ("
                + (run.options().scored() ? "scored" : "practice") + ", " + run.options().floorLimit() + " floors"
                + (run.options().enemyLevelLock() > 0 ? ", enemies level " + run.options().enemyLevelLock() : "") + ")"), false));
        var tower = TowerDefinitionRegistry.content().towers().get(run.towerId());
        if (tower != null && tower.ascension()) {
            source.sendSuccess(() -> Component.literal("  ascension " + tower.ascensionOf(run.floorIndex())
                    + " (tower floor " + tower.contentFloor(run.floorIndex()) + " of " + tower.floorCount() + ")"), false);
        }
        // TDS #60: state, modifiers, seed and last transition, all keyed by run.
        ModifierEffects effects = DraftService.effects(run);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "  modifiers: %d drafted (%d locked in)  ->  level %+d, +%d opponent(s), boss level %+d,"
                        + " boss hp %d%%, reward %d%%",
                run.modifiers().accumulated().size(), run.modifiers().lockedIn().size(),
                effects.levelOffset(), effects.extraOpponents(), effects.bossLevelOffset(),
                effects.bossHealthPercent(), effects.rewardPercent())), false);
        for (ResourceLocation modifier : run.modifiers().accumulated()) {
            source.sendSuccess(() -> Component.literal("    " + modifier
                    + (run.modifiers().lockedIn().contains(modifier) ? "  [LOCKED IN]" : "")), false);
        }
        if (!run.vendorPurchases().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  vendor purchases: " + run.vendorPurchases()), false);
        }
        source.sendSuccess(() -> Component.literal("  intermission: " + IntermissionService.describe(run)), false);
        run.modifiers().draft().ifPresent(draft -> source.sendSuccess(() -> Component.literal(
                "  draft at floor " + draft.floorIndex() + (draft.lockIn() ? " (LOCK-IN)" : "")
                        + ": " + draft.cards() + (draft.resolved()
                        ? " -> " + draft.chosenModifier().orElse(null)
                        + (draft.decidedByTieBreak() ? " (tie-break)" : "")
                        : " OPEN, " + draft.votes().size() + " vote(s)")), false));
        TowerEncounters.of(runId).ifPresent(round -> source.sendSuccess(() -> Component.literal(
                "  fighting now: " + round.phase() + " " + round.byPlayer()
                        + (round.waves().values().stream().anyMatch(wave -> wave.remaining() > 0)
                        ? "  waves " + round.waves() : "")), false));
        TowerBossAdapter.of(runId).ifPresent(boss -> source.sendSuccess(() -> Component.literal(
                "  boss: " + boss.definition() + " at level " + boss.level()), false));
        for (PersistedParticipant participant : run.participants()) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s  %s/%s/%s  %d registered", participant.playerId(), participant.state().connection(),
                    participant.state().combat(), participant.state().membership(),
                    participant.registeredPokemon().size())), false);
        }
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceLocation towerId = ResourceLocationArgument.getId(context, "tower");
        List<UUID> players = new ArrayList<>();
        Map<UUID, List<UUID>> parties = new HashMap<>();
        for (ServerPlayer player : EntityArgument.getPlayers(context, "players")) {
            players.add(player.getUUID());
            List<UUID> party = new ArrayList<>();
            for (Pokemon pokemon : Cobblemon.INSTANCE.getStorage().getParty(player)) {
                party.add(pokemon.getUuid());
            }
            parties.put(player.getUUID(), party);
        }

        Optional<PersistedRun> created = RunFactory.create(TowerDefinitionRegistry.content(), towerId, players, parties,
                source.getServer().overworld().getRandom().nextLong(), System.currentTimeMillis());
        if (created.isEmpty()) {
            source.sendFailure(Component.literal("No tower " + towerId + " is loaded"));
            return 0;
        }
        PersistedRun run = created.get();
        // Checkpointed on creation: a run nobody can find after a crash is worse than no run at all.
        TowerRuns.save(source.getServer(), run, true);
        source.sendSuccess(() -> Component.literal("Created run " + run.runId() + " on " + towerId
                + " with " + players.size() + " player(s)").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** {@code /cobbletowers runs validate <run>}: registers and checks every party, for real (TDS #41). */
    private static int validate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        MinecraftServer server = source.getServer();
        RunTransitionService.Outcome outcome = RunLifecycle.validateParty(server, runId, System.currentTimeMillis(),
                playerId -> Optional.ofNullable(server.getPlayerList().getPlayer(playerId)).map(PartyReader::members));
        if (outcome instanceof RunTransitionService.Move move) {
            boolean accepted = move.next().state() != RunState.ABANDONED;
            source.sendSuccess(() -> Component.literal("Party " + (accepted ? "validated" : "rejected") + ": "
                    + move.from() + " -> " + move.next().state()), true);
            return accepted ? 1 : 0;
        }
        source.sendFailure(Component.literal("Could not validate: " + outcome));
        return 0;
    }

    /**
     * {@code /cobbletowers runs earn <run> opponent|boss|floor|milestone}: puts an entry in the unclaimed pool; a
     * test seam.
     */
    private static int fx(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            net.minecraft.server.level.ServerPlayer player = net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player");
            String json = StringArgumentType.getString(context, "json").trim();
            if (json.equals("clear")) {
                com.cobbletowers.showdown.TowerBattleFx.clearQueued(player.getUUID());
                source.sendSuccess(() -> Component.literal("Cleared the queued battle effects"), true);
                return 1;
            }
            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseString(json);
            if (!parsed.isJsonArray()) {
                source.sendFailure(Component.literal("Expected a JSON array of operations, or 'clear'"));
                return 0;
            }
            int kept = com.cobbletowers.showdown.TowerBattleFx.queue(player.getUUID(), parsed.getAsJsonArray());
            int offered = parsed.getAsJsonArray().size();
            source.sendSuccess(() -> Component.literal("Queued " + kept + " of " + offered + " battle effect(s)"), true);
            return kept;
        } catch (com.google.gson.JsonParseException | com.mojang.brigadier.exceptions.CommandSyntaxException ex) {
            source.sendFailure(Component.literal("Could not queue effects: " + ex.getMessage()));
            return 0;
        }
    }

    private static int earn(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        String kind = StringArgumentType.getString(context, "kind");
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No run " + runId));
            return 0;
        }
        PersistedRun run = found.get();
        long now = System.currentTimeMillis();
        int floor = run.floorIndex();
        TowerContent content = TowerDefinitionRegistry.content();

        LedgerEntry entry;
        switch (kind) {
            case "opponent" -> entry = LedgerEntry.opponentDefeated(floor,
                    ResourceLocation.fromNamespaceAndPath("cobblemon", "machoke"), run.participants().get(0).playerId(), now);
            case "boss" -> entry = LedgerEntry.bossDefeated(floor,
                    ResourceLocation.fromNamespaceAndPath("cobbleraids", "lucario"), now);
            case "floor" -> {
                Optional<com.cobbletowers.definition.FloorDefinition> definition = content.floorAt(run.towerId(), floor);
                if (definition.isEmpty()) {
                    source.sendFailure(Component.literal("Floor " + floor + " is not loaded"));
                    return 0;
                }
                entry = LedgerEntry.floorCleared(floor, definition.get().id(), now);
            }
            case "milestone" -> {
                Optional<com.cobbletowers.definition.MilestoneDefinition> milestone = content.milestoneAt(run.towerId(), floor);
                if (milestone.isEmpty()) {
                    source.sendFailure(Component.literal("Floor " + floor + " has no milestone"));
                    return 0;
                }
                entry = LedgerEntry.milestoneCleared(floor, milestone.get().id(), now);
            }
            default -> {
                source.sendFailure(Component.literal("kind must be opponent, boss, floor or milestone"));
                return 0;
            }
        }
        TowerEncounters.earn(source.getServer(), runId, entry);
        source.sendSuccess(() -> Component.literal("Earned " + kind + " on floor " + floor), true);
        return 1;
    }

    private static int allocate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        RunTransitionService.Outcome outcome =
                RunLifecycle.allocateInstance(source.getServer(), runId, System.currentTimeMillis());
        if (outcome instanceof RunTransitionService.Move move) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s -> %s, cell %s",
                            move.from(), move.next().state(), move.next().cell().isPresent()
                                    ? String.valueOf(move.next().cell().getAsInt()) : "none"))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        RunTransitionService.Refusal refusal = (RunTransitionService.Refusal) outcome;
        source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
        return 0;
    }

    /** Starts the floor's prerequisite round by hand; driven by the floor tests over RCON. */
    private static int encounter(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");

        RunTransitionService.Outcome started =
                RunTransitionService.apply(source.getServer(), runId, RunEvent.ENCOUNTER_STARTED,
                        System.currentTimeMillis());
        if (started instanceof RunTransitionService.Refusal refusal) {
            source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
            return 0;
        }

        Optional<TowerEncounters.Round> round;
        try {
            round = TowerEncounters.begin(source.getServer(), runId);
        } catch (RuntimeException ex) {
            // Logged first: Minecraft hides command stack traces outside an IDE.
            TowerLog.error("Starting the floor for run {} threw", runId, ex);
            source.sendFailure(Component.literal("Starting the floor threw: " + ex));
            return 0;
        }
        if (round.isEmpty()) {
            // The floor could not be put up at all: unknown content, nobody able to fight, no cell.
            // None of those are the party's doing, so it is a technical fault and the run parks.
            source.sendFailure(Component.literal("The floor could not be started; parking the run"));
            RunTransitionService.apply(source.getServer(), runId, RunEvent.TECHNICAL_FAILURE,
                    System.currentTimeMillis());
            return 0;
        }
        TowerEncounters.Round begun = round.get();
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                        "Floor %d begun: %d opponent(s)", begun.floorIndex(), begun.byPlayer().size()))
                .withStyle(ChatFormatting.GREEN), true);
        return begun.byPlayer().size();
    }

    private static int advance(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        String raw = StringArgumentType.getString(context, "event");
        RunEvent event;
        try {
            event = RunEvent.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source.sendFailure(Component.literal("Unknown event '" + raw + "'"));
            return 0;
        }

        RunTransitionService.Outcome outcome =
                RunTransitionService.apply(source.getServer(), runId, event, System.currentTimeMillis());
        if (outcome instanceof RunTransitionService.Move move) {
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "%s -> %s%s",
                            move.from(), move.next().state(),
                            move.checkpoint() ? " [checkpoint " + move.key() + "]" : ""))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        RunTransitionService.Refusal refusal = (RunTransitionService.Refusal) outcome;
        source.sendFailure(Component.literal(refusal.reason() + ": " + refusal.detail()));
        return 0;
    }

    /** Grants a modifier onto a run without a draft, refused if the resolver would not allow it. */
    private static int grant(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        ResourceLocation modifierId = ResourceLocationArgument.getId(context, "modifier");

        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("No run " + runId));
            return 0;
        }
        PersistedRun run = found.get();
        TowerContent content = TowerDefinitionRegistry.content();
        Optional<ModifierDefinition> modifier = content.modifier(modifierId);
        if (modifier.isEmpty()) {
            source.sendFailure(Component.literal("No modifier " + modifierId + " is loaded"));
            return 0;
        }
        if (!ModifierResolver.eligible(modifier.get(), DraftService.held(content, run.modifiers()))) {
            source.sendFailure(Component.literal(modifierId + " cannot be held alongside what this run"
                    + " already has: " + run.modifiers().accumulated()));
            return 0;
        }

        TowerRuns.save(source.getServer(),
                run.withModifiers(run.modifiers().accumulating(modifierId), System.currentTimeMillis()), true);
        source.sendSuccess(() -> Component.literal("Granted " + modifierId + " to run " + runId), true);
        return 1;
    }

    /** Prints the draft with cards numbered from 1; converted to zero-based here, once. */
    private static int draftShow(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        Optional<PersistedDraft> draft = found.get().modifiers().draft();
        if (draft.isEmpty()) {
            source.sendFailure(Component.literal("There is no draft open."));
            return 0;
        }
        PersistedDraft open = draft.get();
        TowerContent content = TowerDefinitionRegistry.content();
        source.sendSuccess(() -> Component.literal((open.lockIn() ? "LOCK-IN draft" : "Draft")
                + " at floor " + open.floorIndex()).withStyle(ChatFormatting.GOLD), false);
        for (int index = 0; index < open.cards().size(); index++) {
            int card = index;
            ResourceLocation id = open.cards().get(index);
            String name = content.modifier(id).map(ModifierDefinition::displayName).orElse(id.toString());
            String risk = content.modifier(id).map(modifier -> modifier.risk().name()).orElse("?");
            long votes = open.votes().values().stream().filter(choice -> choice == card).count();
            source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  [%d] %s (%s) - %d vote(s)%s", card + 1, name, risk, votes,
                    open.chosen().isPresent() && open.chosen().getAsInt() == card ? "  <- CHOSEN" : "")), false);
        }
        if (open.resolved()) {
            source.sendSuccess(() -> Component.literal("  settled"
                    + (open.decidedByTieBreak() ? " on a seed tie-break" : " by majority")), false);
        }
        return open.cards().size();
    }

    /** Votes for a card, by its 1-based number. */
    private static int draftVote(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Optional<PersistedRun> found = TowerRuns.forPlayer(player.getUUID());
        if (found.isEmpty()) {
            source.sendFailure(Component.literal("You are not in a tower run."));
            return 0;
        }
        int card = IntegerArgumentType.getInteger(context, "card") - 1;
        Optional<PersistedDraft> after = DraftService.vote(source.getServer(), found.get().runId(),
                player.getUUID(), card, System.currentTimeMillis());
        if (after.isEmpty()) {
            source.sendFailure(Component.literal("There is no draft open to vote on."));
            return 0;
        }
        boolean settled = after.get().resolved();
        source.sendSuccess(() -> Component.literal(settled
                ? "Vote recorded; the draft is settled."
                : "Vote recorded."), false);
        return 1;
    }

    /** Settles an open draft without waiting for the rest of the party. */
    private static int draftForce(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID runId = UuidArgument.getUuid(context, "run");
        Optional<PersistedRun> found = TowerRuns.get(runId);
        if (found.isEmpty() || found.get().modifiers().draft().isEmpty()) {
            source.sendFailure(Component.literal("Run " + runId + " has no draft open."));
            return 0;
        }
        PersistedDraft settled = DraftService.settle(source.getServer(), runId, System.currentTimeMillis());
        source.sendSuccess(() -> Component.literal("Draft settled on "
                + settled.chosenModifier().map(ResourceLocation::toString).orElse("nothing")
                + (settled.decidedByTieBreak() ? " (seed tie-break)" : " (majority)")), true);
        return 1;
    }
}

