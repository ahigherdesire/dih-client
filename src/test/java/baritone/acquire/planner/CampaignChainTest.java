package baritone.acquire.planner;

import baritone.acquire.knowledge.WorldView;
import baritone.acquire.model.InventorySnapshot;
import baritone.acquire.model.Location;
import baritone.beat.CampaignChain;
import baritone.beat.Phase;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code #beat plan}: every phase planned from where the one before leaves you. */
final class CampaignChainTest {
    private static final FakeKnowledge KNOWLEDGE = FakeKnowledge.withDimensions();

    private static AcquirePlanner planner(FakeKnowledge knowledge) {
        return new AcquirePlanner(knowledge, WorldView.UNKNOWN, PlannerOptions.DEFAULT);
    }

    @Test
    void theWholeChainPlansEveryPhase() {
        List<CampaignChain.PhasePlan> chain = CampaignChain.plan(planner(KNOWLEDGE), InventorySnapshot.empty(), Location.OVERWORLD, Phase.GEAR);
        assertEquals(Arrays.asList(Phase.values()), chain.stream().map(CampaignChain.PhasePlan::phase).toList(),
                () -> String.join("\n", CampaignChain.explain(chain)));
        for (CampaignChain.PhasePlan phase : chain) {
            assertTrue(phase.complete(), phase.phase() + ": " + phase.plans().stream().flatMap(p -> p.missing().stream()).toList());
        }
        List<String> lines = CampaignChain.explain(chain);
        assertTrue(lines.get(0).startsWith("Phase 1/9: gear ("), lines.get(0));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Phase 3/9: blaze rods")), String.join("\n", lines));
        assertTrue(lines.stream().anyMatch(l -> l.startsWith("Phase 9/9: the dragon")), String.join("\n", lines));
        // Home: from the Nether back through the portal it came by, which needs nothing new.
        CampaignChain.PhasePlan home = chain.get(Phase.HOME.ordinal());
        List<baritone.acquire.model.Step> homeSteps = home.plans().get(0).steps();
        assertEquals(1, homeSteps.size(), String.join("\n", lines));
        baritone.acquire.model.Step.Travel back = (baritone.acquire.model.Step.Travel) homeSteps.get(0);
        assertEquals(Location.OVERWORLD, back.to(), String.join("\n", lines));
        assertTrue(back.consumes().isEmpty(), String.join("\n", lines));
        assertTrue(lines.get(lines.size() - 1).startsWith("The whole route: "), lines.get(lines.size() - 1));
        // The gear made in phase 1 is not made again for the Nether gear checkpoint in phase 2.
        long swords = chain.stream().flatMap(p -> p.plans().stream()).flatMap(p -> p.steps().stream())
                .filter(s -> s instanceof baritone.acquire.model.Step.Craft c && c.recipe().output().equals(FakeKnowledge.IRON_SWORD)).count();
        assertEquals(1, swords, String.join("\n", lines));
    }

    @Test
    void resumingPlansFromTheSavedPhase() {
        InventorySnapshot inv = InventorySnapshot.empty();
        inv.add(FakeKnowledge.ENDER_EYE, 12);
        inv.add(FakeKnowledge.STONE_PICKAXE, 1);
        List<CampaignChain.PhasePlan> chain = CampaignChain.plan(planner(KNOWLEDGE), inv, Location.OVERWORLD, Phase.STRONGHOLD);
        assertEquals(List.of(Phase.STRONGHOLD, Phase.END, Phase.DRAGON), chain.stream().map(CampaignChain.PhasePlan::phase).toList());
    }

    @Test
    void theChainStopsWhereAPhaseCantBePlanned() {
        List<CampaignChain.PhasePlan> chain = CampaignChain.plan(planner(FakeKnowledge.withDimensions(false)),
                InventorySnapshot.empty(), Location.OVERWORLD, Phase.GEAR);
        assertEquals(Phase.PORTAL, chain.get(chain.size() - 1).phase(), "no obsidian: the portal phase can't be planned");
        List<String> lines = CampaignChain.explain(chain);
        assertTrue(lines.stream().anyMatch(l -> l.contains("can't plan") && l.contains("obsidian")), String.join("\n", lines));
        assertEquals("The route stops there.", lines.get(lines.size() - 1));
    }
}
