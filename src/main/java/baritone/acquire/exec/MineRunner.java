package baritone.acquire.exec;

import baritone.acquire.model.Step;
import baritone.api.BaritoneAPI;
import baritone.process.MineProcess;

/**
 * Mines through Baritone's {@link MineProcess} with no quantity limit and stops it once the inventory
 * holds {@code untilCount} of the step's item. MineProcess counts the block's own item, so stone for
 * cobblestone would never stop on its own; this runner watches the dropped item instead.
 */
final class MineRunner extends RunnerBase {

    private final Step.Mine step;
    private boolean started;
    /** The step's item held when mining first started. */
    private int startCount = -1;

    MineRunner(ExecContext x, Step.Mine step) {
        super(x);
        this.step = step;
    }

    @Override
    public Result tick(boolean calcFailed, boolean safeToCancel) {
        MineProcess mine = x.baritone.getMineProcess();
        if (x.have(step.item()) >= step.untilCount()) {
            cancel();
            return Result.done();
        }
        if (!x.hasTool(step.tool())) {
            String why = (started ? "your tool broke, need a " : "need a ") + ExecContext.describeTool(step.tool())
                    + " to mine " + Step.shortId(step.blocks().get(0));
            cancel();
            return Result.failed(why);
        }
        Result full = x.checkRoom(step.item());
        if (full != null) {
            cancel();
            return full;
        }
        if (ExecContext.needsTool(step.tool())) {
            int slot = InventoryOps.toHotbar(ctx, stack -> x.toolMatcher(step.tool()).test(stack)
                    && (!stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue()
                    > Math.ceil(stack.getMaxDamage() * 0.10)));
            if (slot < 0) {
                cancel();
                return Result.failed("the " + ExecContext.describeTool(step.tool())
                        + " is worn below 10%; need a replacement before mining");
            }
            ctx.player().getInventory().setSelectedSlot(slot);
        }
        if (!started) {
            // Baritone only switches between tools already on the hotbar.
            try {
                mine.mineByName(0, step.blocks().toArray(new String[0]));
            } catch (IllegalArgumentException e) {
                return Result.failed("can't mine " + step.blocks() + ": " + e.getMessage());
            }
            if (!mine.isActive()) return Result.failed("Baritone won't mine " + Step.shortId(step.blocks().get(0)) + " (allowBreak off?)");
            BaritoneAPI.getProvider().getWorldScanner().repack(ctx);
            started = true;
            if (startCount < 0) startCount = x.have(step.item());
            return Result.defer();
        }
        if (!mine.isActive()) {
            gaveUp();
            return Result.failed("mining stopped with " + x.have(step.item()) + "/" + step.untilCount() + " "
                    + Step.shortId(step.item()) + " (nothing reachable left nearby?)");
        }
        return Result.defer();
    }

    /**
     * Whether mining that stopped (no way to any of the blocks left) marks them unreachable for the run: nothing came
     * of it, and the block is mined for itself, as a placed block in a structure is. Ore is not: it drops another item,
     * and lies everywhere.
     */
    /** The step stopped short (no way to the blocks, or out of time): leave them out of the run if nothing came of it. */
    void gaveUp() {
        if (unreachable(startCount, x.have(step.item()), step.item(), step.blocks())) x.unreachableBlocks.addAll(step.blocks());
    }

    static boolean unreachable(int before, int now, String item, java.util.List<String> blocks) {
        return before >= 0 && now <= before && blocks.contains(item);
    }

    @Override
    public void cancel() {
        if (started) {
            started = false;
            MineProcess mine = x.baritone.getMineProcess();
            if (mine.isActive()) mine.cancel();
        }
    }
}
