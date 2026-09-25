package baritone.acquire.exec;

import baritone.Baritone;
import baritone.acquire.model.CraftSource;
import baritone.acquire.model.Ingredient;
import baritone.acquire.model.Step;
import dihclient.util.DihCraftingHelper;
import dihclient.util.DihCraftingHelper.CraftExecutionResult;
import dihclient.util.DihCraftingHelper.CraftableRecipeOption;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * Crafts through {@link DihCraftingHelper}, the same code the macro Craft action uses. It crafts from the
 * 2x2 inventory grid, or opens a crafting table within reach, fills the grid through the recipe book
 * when the server unlocked the recipe and by hand (slot clicks) when it did not, and closes the table.
 *
 * <p>The helper blocks while it waits for server replies, so it runs on a Baritone worker thread and the
 * runner polls it; the player stands still meanwhile. For a table recipe the runner first walks to the
 * nearest usable table (normally the one the preceding PlaceStation step set up).
 */
final class CraftRunner extends RunnerBase {

    private enum State { CHECK, WALK, LOOK, RUN, WAIT }

    private static final int MAX_ATTEMPTS = 3;

    private final Step.Craft step;
    private State state = State.CHECK;
    private BlockPos table;
    private int lookTicks;
    private int attempts;
    private int startCount;
    private FutureTask<CraftExecutionResult> task;

    CraftRunner(ExecContext x, Step.Craft step) {
        super(x);
        this.step = step;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        if (x.have(step.item()) >= step.untilCount() && task == null) return Result.done();
        for (int guard = 0; guard < 5; guard++) {
            switch (state) {
                case CHECK -> {
                    String missing = missingInputs();
                    if (missing != null) return Result.failed("not enough " + missing + " to craft " + Step.shortId(step.item()));
                    if (!step.recipe().needsTable()) {
                        state = State.RUN;
                        continue;
                    }
                    List<BlockPos> tables = x.stations.find(AcquireRun.CRAFTING_TABLE, x.stationRadius());
                    if (tables.isEmpty()) return Result.failed("no crafting table within " + x.stationRadius() + " blocks");
                    table = tables.get(0);
                    state = State.WALK;
                }
                case WALK -> {
                    if (!ctx.world().getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
                        state = State.CHECK;
                        continue;
                    }
                    Result walking = approach(table, calcFailed);
                    if (walking == null) {
                        state = State.LOOK;
                        lookTicks = 0;
                        continue;
                    }
                    if (walking.kind() == Result.Kind.FAILED) {
                        x.stations.markUnusable(table);
                        state = State.CHECK;
                        return Result.pause();
                    }
                    return walking;
                }
                case LOOK -> {
                    if (!safeToCancel) return Result.pause();
                    if (!inReach(table)) {
                        state = State.WALK;
                        continue;
                    }
                    aim(table);
                    if (++lookTicks < 3) return Result.pause();
                    state = State.RUN;
                }
                case RUN -> {
                    if (!safeToCancel) return Result.pause();
                    // A leftover open screen would make the helper craft into the wrong menu.
                    if (!InventoryOps.inventoryMenuOpen(ctx.player())) ctx.player().closeContainer();
                    startCount = x.have(step.item());
                    int amount = Math.max(1, step.untilCount() - startCount);
                    CraftSource recipe = step.recipe();
                    task = new FutureTask<>(() -> craft(recipe, amount));
                    Baritone.getExecutor().execute(task);
                    state = State.WAIT;
                    return Result.pause();
                }
                case WAIT -> {
                    if (!task.isDone()) return Result.pause();
                    String message = message(task);
                    task = null;
                    int now = x.have(step.item());
                    if (now >= step.untilCount()) return Result.done();
                    if (now > startCount && ++attempts < MAX_ATTEMPTS) {
                        state = step.recipe().needsTable() ? State.WALK : State.RUN;
                        continue;
                    }
                    return Result.failed("crafting " + Step.shortId(step.item()) + " failed: " + message);
                }
            }
        }
        return Result.pause();
    }

    @Override
    public void cancel() {
        if (task != null) {
            task.cancel(true); // the helper stops at its next wait and closes a table it opened
            task = null;
        }
    }

    /** Runs on a worker thread: finds the helper's recipe for the planner's and crafts {@code amount} items. */
    private static CraftExecutionResult craft(CraftSource recipe, int amount) {
        Minecraft mc = Minecraft.getInstance();
        CraftableRecipeOption option = pick(DihCraftingHelper.getCraftableRecipes(mc), recipe);
        if (option == null) return CraftExecutionResult.failure("no recipe for " + Step.shortId(recipe.output()) + " is available");
        return DihCraftingHelper.executeCraftImmediately(mc, option.recipeKey, option.recipeId, amount);
    }

    /**
     * The helper's option for the planner's recipe. The planner's ids are recipe data file ids
     * ("minecraft:stick"), which are also the helper's keys for recipes it loads from the game data, and
     * the helper merges server recipe-book entries into those by ingredient signature. So the same key
     * wins; failing that (a server-only recipe), any recipe with the same output and count, craftable first.
     */
    static CraftableRecipeOption pick(List<CraftableRecipeOption> options, CraftSource recipe) {
        CraftableRecipeOption best = null;
        int bestScore = -1;
        for (CraftableRecipeOption option : options) {
            int key = RecipeKeys.match(option.recipeKey, recipe.recipeId());
            boolean sameOutput = option.registryId.equals(recipe.output()) && option.result.getCount() == recipe.outputCount();
            if (key == 0 && !sameOutput) continue;
            int score = key * 4 + (option.craftableNow ? 2 : 0) + (sameOutput ? 1 : 0);
            if (score > bestScore) {
                best = option;
                bestScore = score;
            }
        }
        return best;
    }

    private static String message(FutureTask<CraftExecutionResult> task) {
        try {
            CraftExecutionResult result = task.get();
            return result == null ? "no result" : result.message;
        } catch (ExecutionException e) {
            return String.valueOf(e.getCause());
        } catch (Exception e) {
            return "interrupted";
        }
    }

    /**
     * The first ingredient the inventory cannot cover, as "3 stick", or null. Uses the planner's chosen
     * item per ingredient, and falls back to any accepted item because the helper may pick a substitute.
     */
    private String missingInputs() {
        List<Ingredient> ingredients = step.recipe().ingredients();
        Map<String, Integer> chosen = new HashMap<>();
        for (int i = 0; i < ingredients.size(); i++) {
            String id = i < step.inputs().size() ? step.inputs().get(i) : ingredients.get(i).anyOf().get(0);
            chosen.merge(id, ingredients.get(i).count() * step.times(), Integer::sum);
        }
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            String id = i < step.inputs().size() ? step.inputs().get(i) : ing.anyOf().get(0);
            if (x.have(id) >= chosen.get(id)) continue;
            int pool = 0;
            for (String alt : ing.anyOf()) pool += x.have(alt);
            if (pool < ing.count() * step.times()) return (ing.count() * step.times()) + " " + Step.shortId(id);
        }
        return null;
    }
}
