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
            return Result.defer();
        }
        if (!mine.isActive()) {
            return Result.failed("mining stopped with " + x.have(step.item()) + "/" + step.untilCount() + " "
                    + Step.shortId(step.item()) + " (nothing reachable left nearby?)");
        }
        return Result.defer();
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
