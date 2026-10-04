package baritone.construct;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.acquire.exec.AcquireProcess;
import baritone.acquire.exec.InventoryReader;
import baritone.ai.director.JobStatus;
import baritone.ai.director.LiveDirectorHost;
import baritone.api.event.events.TickEvent;
import baritone.api.schematic.ISchematic;
import baritone.api.utils.Helper;
import baritone.behavior.Behavior;
import baritone.process.BuilderProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code #construct}: builds a schematic starting from nothing. It works out what the blocks still to place cost in
 * items, has {@code #acquire} gather a batch that fits the inventory, hands the schematic to Baritone's builder, and
 * when the builder runs out it gathers the next batch, until the build is done. While gathering, the build site is
 * off limits to mining and pathing, and the gathered blocks are kept: never thrown away as junk, never used as
 * scaffolding.
 *
 * <p>Game thread only.
 */
public final class ConstructJob extends Behavior {
    /** Inventory slots left free in every batch, for tools, food and what mining picks up on the way. */
    static final int SPARE_SLOTS = 8;
    /** Gather-then-build rounds before giving up. */
    static final int MAX_ROUNDS = 60;

    private enum Phase { IDLE, GATHER, BUILD }

    private static volatile int[] protectedBox;

    private Phase phase = Phase.IDLE;
    private String name;
    private ISchematic schematic;
    private BlockPos origin;
    private final Deque<BillOfMaterials.Gather> queue = new ArrayDeque<>();
    /** The item id handed to #acquire, while it runs. */
    private String waiting;
    /** Items #acquire couldn't get this build: left out of later batches. */
    private final Set<String> unobtainable = new LinkedHashSet<>();
    private int rounds;
    private int buildTicks;
    private boolean listening;
    private Boolean savedAllowInventory;
    private String outcome = "";

    /** How the last build ended, for a director waiting on the "construct" job: null while running or never run. */
    private JobStatus jobOutcome;

    public ConstructJob(Baritone baritone) {
        super(baritone);
        if (!registered) {
            registered = true;
            LiveDirectorHost.registerJob("construct", new LiveDirectorHost.ClientJob(this::jobStatus,
                () -> net.minecraft.client.Minecraft.getInstance().execute(this::stop)));
        }
    }

    private static boolean registered;

    /** For a director step waiting on the construct_schematic tool. */
    public JobStatus jobStatus() {
        if (phase != Phase.IDLE) return JobStatus.running();
        return jobOutcome == null ? JobStatus.failed("#construct isn't running") : jobOutcome;
    }

    /** True for a block inside the build while gathering: mining and pathing must leave it alone. */
    public static boolean isProtected(int x, int y, int z) {
        int[] box = protectedBox;
        return box != null && x >= box[0] && y >= box[1] && z >= box[2] && x <= box[3] && y <= box[4] && z <= box[5];
    }

    // ---------------------------------------------------------------- commands

    public String start(String name, ISchematic schematic, BlockPos origin) {
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        if (phase != Phase.IDLE) stop();
        this.name = name;
        this.schematic = schematic;
        this.origin = origin.immutable();
        this.rounds = 0;
        this.unobtainable.clear();
        this.outcome = "";
        this.jobOutcome = null;
        listen();
        BillOfMaterials.Tally tally = remaining();
        if (tally.isEmpty()) return finish("Nothing to build: " + name + " is already standing at " + at() + ".");
        say("Building " + name + " at " + at() + " from scratch: " + tally.total() + " items ("
            + BillOfMaterials.describe(tally.items(), 6) + "). #construct status shows progress, #construct stop stops.");
        if (!tally.unplaceable().isEmpty()) {
            say("No item places " + BillOfMaterials.describe(tally.unplaceable(), 6) + "; those blocks are skipped.");
        }
        savedAllowInventory = Baritone.settings().allowInventory.value;
        Baritone.settings().allowInventory.value = true;
        AcquireProcess.setOtherNeeds(this::keep);
        return next();
    }

    /** What the build still needs and what is missing from the inventory, without starting anything. */
    public String plan(String name, ISchematic schematic, BlockPos origin) {
        if (ctx.player() == null || ctx.world() == null) throw new IllegalArgumentException("Join a world first.");
        BlockPos keepOrigin = this.origin;
        ISchematic keepSchematic = this.schematic;
        this.schematic = schematic;
        this.origin = origin.immutable();
        try {
            BillOfMaterials.Tally tally = remaining();
            if (tally.isEmpty()) return name + " is already standing at " + at() + ".";
            Map<String, Integer> missing = BillOfMaterials.deficit(tally.items(), this::held);
            StringBuilder out = new StringBuilder(name + " at " + at() + " takes " + tally.total() + " items: "
                + BillOfMaterials.describe(tally.items(), 12) + ".");
            out.append(missing.isEmpty() ? "\nYou carry all of it." : "\nTo gather: " + BillOfMaterials.describe(missing, 12) + ".");
            if (!tally.unplaceable().isEmpty()) out.append("\nSkipped (no item places them): ").append(BillOfMaterials.describe(tally.unplaceable(), 8)).append('.');
            return out.toString();
        } finally {
            this.schematic = keepSchematic;
            this.origin = keepOrigin;
        }
    }

    public String stop() {
        if (phase == Phase.IDLE) return "Not constructing anything.";
        Phase was = phase;
        String line = end("Stopped building " + name + ".");
        if (was == Phase.GATHER) {
            AcquireControl acquire = AcquireControl.get();
            if (acquire != null && acquire.isActive()) acquire.stop();
        } else {
            baritone.getPathingBehavior().cancelEverything();
        }
        return line;
    }

    public boolean isActive() {
        return phase != Phase.IDLE;
    }

    /** One line: what it is doing, or how the last build ended. */
    public String status() {
        if (phase == Phase.IDLE) return outcome.isEmpty() ? "Not constructing anything." : outcome;
        BillOfMaterials.Tally tally = remaining();
        String left = tally.total() + " items left to place";
        if (phase == Phase.GATHER) {
            AcquireControl acquire = AcquireControl.get();
            return "Building " + name + " (round " + rounds + "), gathering: " + (acquire == null ? "?" : acquire.status()) + "; " + left + ".";
        }
        return "Building " + name + " (round " + rounds + "), placing blocks; " + left + ".";
    }

    // ---------------------------------------------------------------- the loop

    /** Decides the next round: done, build with what is held, or gather a batch. */
    private String next() {
        if (++rounds > MAX_ROUNDS) return fail("still not done after " + MAX_ROUNDS + " rounds of gathering and building");
        BillOfMaterials.Tally tally = remaining();
        if (tally.isEmpty()) return finish("Done: " + name + " is built at " + at() + ".");
        Map<String, Integer> missing = BillOfMaterials.deficit(tally.items(), this::held);
        unobtainable.forEach(missing::remove);
        if (missing.isEmpty()) return startBuild();
        List<BillOfMaterials.Gather> batch = BillOfMaterials.batch(missing, Math.max(0, freeSlots() - SPARE_SLOTS), ConstructJob::stackSize);
        if (batch.isEmpty()) {
            if (holdsAnyOf(tally.items())) return startBuild();
            return fail("the inventory is full; make room and #construct again");
        }
        queue.clear();
        queue.addAll(batch);
        phase = Phase.GATHER;
        protectedBox = box();
        say("Round " + rounds + ": gathering " + describe(batch) + ".");
        return gatherNext();
    }

    private String gatherNext() {
        AcquireControl acquire = AcquireControl.get();
        if (acquire == null) return fail("#acquire didn't load");
        while (!queue.isEmpty()) {
            BillOfMaterials.Gather gather = queue.poll();
            int target = held(gather.item()) + gather.count();
            String started;
            try {
                started = acquire.start(gather.item(), target);
            } catch (IllegalArgumentException e) {
                unobtainable.add(gather.item());
                say("Can't get " + shortId(gather.item()) + " (" + e.getMessage() + "); building without it.");
                continue;
            }
            if (started.startsWith("You already have")) continue;
            waiting = gather.item();
            return started;
        }
        return startBuild();
    }

    private String startBuild() {
        waiting = null;
        protectedBox = null;
        phase = Phase.BUILD;
        buildTicks = 0;
        baritone.getBuilderProcess().build(name, schematic, origin);
        return "Placing blocks for " + name + ".";
    }

    private void listen() {
        if (listening) return;
        AcquireControl acquire = AcquireControl.get();
        if (acquire == null) return;
        listening = true;
        acquire.addListener(this::onAcquire);
    }

    private void onAcquire(AcquireControl.AcquireEvent event) {
        if (phase != Phase.GATHER || waiting == null || !sameItem(waiting, event.item())) return;
        switch (event.kind()) {
            case DONE -> {
                waiting = null;
                gatherNext();
            }
            case FAILED -> {
                unobtainable.add(waiting);
                say("Couldn't gather " + shortId(waiting) + "; building without it.");
                waiting = null;
                gatherNext();
            }
            case STOPPED -> {
                if (phase != Phase.IDLE) end("Stopped building " + name + " (gathering was stopped).");
            }
            default -> {
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN || phase != Phase.BUILD) return;
        if (ctx.player() == null) {
            end("Stopped building " + name + ": left the world.");
            return;
        }
        buildTicks++;
        BuilderProcess builder = baritone.getBuilderProcess();
        if (builder.isActive() && !builder.isPaused()) return;
        if (buildTicks < 5) return;
        BillOfMaterials.Tally tally = remaining();
        if (tally.isEmpty()) {
            if (builder.isActive()) baritone.getPathingBehavior().cancelEverything();
            finish("Done: " + name + " is built at " + at() + ".");
            return;
        }
        if (!builder.isActive()) {
            // The builder let go with blocks still to place: the player stopped it.
            end("Stopped building " + name + " with " + tally.total() + " items left to place.");
            return;
        }
        // Paused: it can't place anything more with what is held.
        baritone.getPathingBehavior().cancelEverything();
        Map<String, Integer> missing = BillOfMaterials.deficit(tally.items(), this::held);
        unobtainable.forEach(missing::remove);
        if (missing.isEmpty()) {
            fail(unobtainable.isEmpty()
                ? "the builder is stuck with every block it needs held (see the reason above)"
                : "only " + BillOfMaterials.describe(tally.items(), 6) + " are left, which couldn't be gathered");
            return;
        }
        next();
    }

    // ---------------------------------------------------------------- endings

    private String finish(String line) {
        end(line);
        jobOutcome = JobStatus.done(line);
        return line;
    }

    private String fail(String why) {
        String line = end("Stopped building " + name + ": " + why + ".");
        jobOutcome = JobStatus.failed(why);
        return line;
    }

    private String end(String line) {
        phase = Phase.IDLE;
        waiting = null;
        queue.clear();
        protectedBox = null;
        AcquireProcess.setOtherNeeds(null);
        if (savedAllowInventory != null) {
            Baritone.settings().allowInventory.value = savedAllowInventory;
            savedAllowInventory = null;
        }
        outcome = line;
        jobOutcome = JobStatus.failed(line);
        say(line);
        return line;
    }

    // ---------------------------------------------------------------- counting

    /** The items the blocks not yet in place cost; blocks in unloaded chunks count as not in place. */
    BillOfMaterials.Tally remaining() {
        List<BlockState> wanted = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < schematic.heightY(); y++) {
            for (int x = 0; x < schematic.widthX(); x++) {
                for (int z = 0; z < schematic.lengthZ(); z++) {
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    boolean loaded = ctx.world().hasChunkAt(pos);
                    BlockState current = loaded ? ctx.world().getBlockState(pos) : Blocks.AIR.defaultBlockState();
                    if (!schematic.inSchematic(x, y, z, current)) continue;
                    BlockState desired = schematic.desiredState(x, y, z, current, List.of());
                    if (desired == null || loaded && current.getBlock() == desired.getBlock()) continue;
                    wanted.add(desired);
                }
            }
        }
        return BillOfMaterials.tally(wanted);
    }

    /** What the build is holding on to: everything still to place, for #acquire's junk and scaffolding rules. */
    private Map<String, Integer> keep() {
        if (phase == Phase.IDLE || schematic == null || ctx.world() == null) return Map.of();
        return remainingCache();
    }

    private Map<String, Integer> cachedKeep = Map.of();
    private long cachedKeepAt = Long.MIN_VALUE;

    /** {@link #remaining()} is a full scan: asked often by pathing, so reuse it for a second. */
    private Map<String, Integer> remainingCache() {
        long now = System.currentTimeMillis();
        if (now - cachedKeepAt > 1000L) {
            cachedKeep = new LinkedHashMap<>(remaining().items());
            cachedKeepAt = now;
        }
        return cachedKeep;
    }

    private int held(String id) {
        return InventoryReader.count(ctx.player(), id);
    }

    private boolean holdsAnyOf(Map<String, Integer> items) {
        for (String id : items.keySet()) if (held(id) > 0) return true;
        return false;
    }

    private int freeSlots() {
        int free = 0;
        List<ItemStack> main = ctx.player().getInventory().getNonEquipmentItems();
        for (ItemStack stack : main) if (stack.isEmpty()) free++;
        return free;
    }

    private static int stackSize(String id) {
        Item item = InventoryReader.itemOf(id);
        return item == null ? 64 : item.getDefaultMaxStackSize();
    }

    private int[] box() {
        return new int[]{origin.getX(), origin.getY(), origin.getZ(),
            origin.getX() + schematic.widthX() - 1, origin.getY() + schematic.heightY() - 1, origin.getZ() + schematic.lengthZ() - 1};
    }

    private String at() {
        return origin.getX() + " " + origin.getY() + " " + origin.getZ();
    }

    private static String describe(List<BillOfMaterials.Gather> batch) {
        Map<String, Integer> items = new LinkedHashMap<>();
        for (BillOfMaterials.Gather gather : batch) items.put(gather.item(), gather.count());
        return BillOfMaterials.describe(items, 8);
    }

    static boolean sameItem(String a, String b) {
        return shortId(a).equals(shortId(b));
    }

    private static String shortId(String id) {
        return id != null && id.startsWith("minecraft:") ? id.substring(10) : String.valueOf(id);
    }

    private static void say(String line) {
        Helper.HELPER.logDirect(line);
    }
}
