package com.cobbletowers.reward;

import com.cobbletowers.TowerLog;
import com.cobbletowers.api.reward.RewardKind;
import com.cobbletowers.definition.RewardTableDefinition;
import com.cobbletowers.definition.TowerContent;
import com.cobbletowers.definition.TowerDefinitionRegistry;
import com.cobbletowers.economy.CobbleDollars;
import com.cobbletowers.economy.RaidPointsCurrency;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * Checks, at startup, that every item a reward table names can actually be given (P21).
 *
 * <p>Before this nothing did. {@code RewardDelivery.give} skips an item that is not registered and logs it, but by
 * then the pending reward has already been drained from the queue, so a typo in a table -- or an optional mod that
 * is not installed -- silently cost players the drop. Two different situations, reported differently:
 *
 * <ul>
 *   <li>an item in a namespace whose mod <b>is</b> loaded but which does not exist: a typo, an {@code ERROR};</li>
 *   <li>an item in a namespace whose mod is <b>not</b> loaded: an optional dependency (CobbleCards, say), a
 *       {@code NOTE} -- those rewards are skipped, which is the intended graceful degradation.</li>
 * </ul>
 *
 * <p>The two reserved currency ids are never checked: they are credited, not given.
 */
public final class RewardCatalogCheck {

    public enum Severity { ERROR, NOTE }

    /** One item that cannot be given, and where a table names it. */
    public record Problem(Severity severity, ResourceLocation table, String where, ResourceLocation item) {}

    private RewardCatalogCheck() {}

    /** Pure over its two predicates, so it is a unit test with no Minecraft in reach. */
    public static List<Problem> check(Collection<RewardTableDefinition> tables, Predicate<ResourceLocation> itemExists,
                                      Predicate<String> namespaceLoaded) {
        List<Problem> problems = new ArrayList<>();
        for (RewardTableDefinition table : tables) {
            for (RewardKind kind : RewardKind.values()) {
                for (RewardTableDefinition.Entry entry : table.entriesFor(kind)) {
                    consider(problems, table, "tiers." + kind.name().toLowerCase(), entry.item(), itemExists, namespaceLoaded);
                }
            }
            for (var milestone : table.milestones().entrySet()) {
                String base = "milestones." + milestone.getKey().name().toLowerCase();
                for (RewardTableDefinition.Guaranteed fixed : milestone.getValue().guaranteed()) {
                    consider(problems, table, base + ".guaranteed", fixed.item(), itemExists, namespaceLoaded);
                }
                for (RewardTableDefinition.Entry entry : milestone.getValue().bonusPool()) {
                    consider(problems, table, base + ".bonus_pool", entry.item(), itemExists, namespaceLoaded);
                }
            }
        }
        return List.copyOf(problems);
    }

    private static void consider(List<Problem> problems, RewardTableDefinition table, String where, ResourceLocation item,
                                 Predicate<ResourceLocation> itemExists, Predicate<String> namespaceLoaded) {
        if (item.equals(CobbleDollars.ITEM_ID) || item.equals(RaidPointsCurrency.ITEM_ID)) return;
        if (itemExists.test(item)) return;
        problems.add(new Problem(namespaceLoaded.test(item.getNamespace()) ? Severity.ERROR : Severity.NOTE,
                table.id(), where, item));
    }

    /** Runs the check against the live registry and says what it found. */
    public static void report() {
        TowerContent content = TowerDefinitionRegistry.content();
        List<Problem> problems = check(content.rewardTables().values(), BuiltInRegistries.ITEM::containsKey,
                namespace -> FabricLoader.getInstance().isModLoaded(namespace));
        Set<String> absentMods = new LinkedHashSet<>();
        for (Problem problem : problems) {
            if (problem.severity() == Severity.ERROR) {
                TowerLog.error("Reward table {} names {} in {}, which is not a registered item; players would lose"
                        + " that drop. Check the id for a typo.", problem.table(), problem.item(), problem.where());
            } else {
                absentMods.add(problem.item().getNamespace());
            }
        }
        for (String namespace : absentMods) {
            long count = problems.stream().filter(p -> p.severity() == Severity.NOTE
                    && p.item().getNamespace().equals(namespace)).count();
            TowerLog.info("{} reward entr{} name items from '{}', which is not installed; they will be skipped.",
                    count, count == 1 ? "y" : "ies", namespace);
        }
        if (problems.isEmpty()) TowerLog.info("Every item the reward tables name is registered.");
    }
}
