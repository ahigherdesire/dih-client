package dihclient.schematic;

import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Places a placement's blocks straight from where the player is, several a tick, each turned the right way: it
 * tries the faces it could click and the ways it could look, asks the block what that would place, and sends the
 * look and the click that place the schematic's state.
 * <p>
 * {@link Kind#ASSIST} runs beside Baritone's builder in fast mode and places from the hotbar. {@link Kind#CREATIVE}
 * runs alone: it puts each block in the hand from the creative inventory, breaks what is in the way, and flies to
 * what is left, or, when the player may run commands, pastes the whole thing with {@code /fill} and
 * {@code /setblock}.
 */
public final class SchematicPrinter {

    public enum Kind { ASSIST, CREATIVE }

    /**
     * @param perTick         blocks placed (or broken) per tick at most
     * @param reach           how far from the eyes a click may land
     * @param airPlace        click the empty position itself when nothing is next to it
     * @param replace         creative: break blocks that are not the schematic's
     * @param commands        creative: paste with commands when the player is op
     * @param commandsPerTick creative paste: commands sent per tick
     * @param flySpeed        creative: blocks per tick when flying to what is left
     */
    public record Options(int perTick, double reach, boolean airPlace, boolean replace, boolean commands,
                          int commandsPerTick, double flySpeed) {}

    /** Properties that say which way a block is turned, and so depend on how it is placed. */
    private static final Set<String> ORIENTATION = Set.of("facing", "axis", "half", "type", "face", "rotation",
        "hinge", "orientation", "vertical_direction", "attachment", "hanging");
    private static final int MAX_ATTEMPTS = 4;
    private static final int RESCAN_TICKS = 20;
    private static final int STUCK_TICKS = 60;
    /** How long a position that could not be placed right is left alone before it is tried again. */
    private static final int WAIT_TICKS = 20;
    /** Tries without a click that places it the right way before creative places it the closest way, or gives up. */
    private static final int MAX_FAILURES = 6;
    /** Path nodes ahead of Baritone that fast mode keeps clear, so it does not wall Baritone in. */
    private static final int PATH_AHEAD = 8;

    private static SchematicPrinter active;

    private final SchematicPlacement placement;
    private final Kind kind;
    private final Options options;
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private final Set<BlockPos> skipped = new HashSet<>();
    private final Map<BlockPos, Integer> failures = new HashMap<>();
    private final Map<BlockPos, Integer> waitUntil = new HashMap<>();
    private int ticks;

    private List<String> commands;
    private int commandIndex;
    private int unloadedAtStart;

    private List<BlockPos> todo = List.of();
    private BlockPos nearestUnloaded;
    private int sinceScan = RESCAN_TICKS;
    private BlockPos flyTarget;
    private Vec3 lastPos;
    private int stuckTicks;

    private int handSlot = -1;
    private ItemStack handOriginal = ItemStack.EMPTY;

    private int placed;
    private int broken;

    private SchematicPrinter(SchematicPlacement placement, Kind kind, Options options) {
        this.placement = placement;
        this.kind = kind;
        this.options = options;
    }

    public static SchematicPrinter active() {
        return active;
    }

    public SchematicPlacement placement() {
        return this.placement;
    }

    public Kind kind() {
        return this.kind;
    }

    /**
     * @return what to tell the player
     */
    public static String start(Minecraft mc, SchematicPlacement p, Kind kind, Options options) {
        stop(mc, null);
        SchematicPrinter printer = new SchematicPrinter(p, kind, options);
        if (kind == Kind.CREATIVE) {
            if (!mc.player.getAbilities().instabuild) {
                return "Creative paste needs creative mode";
            }
            if (options.commands() && mc.player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)) {
                printer.planCommands(mc.level);
                active = printer;
                return "Pasting " + p.name() + " with " + printer.commands.size() + " commands"
                    + (printer.unloadedAtStart > 0 ? " (" + printer.unloadedAtStart
                    + " positions are in chunks you have not loaded; run it again from there)" : "");
            }
            printer.handSlot = mc.player.getInventory().getSelectedSlot();
            printer.handOriginal = mc.player.getInventory().getItem(printer.handSlot).copy();
        }
        active = printer;
        return kind == Kind.CREATIVE
            ? "Creative printing " + p.name() + ": blocks come from the creative inventory into hotbar slot "
                + (printer.handSlot + 1) + ", and it flies to what is left"
            : "";
    }

    /**
     * Stops the printer, puts the hand back, and says {@code reason} when there is one.
     */
    public static void stop(Minecraft mc, String reason) {
        SchematicPrinter printer = active;
        if (printer == null) {
            return;
        }
        active = null;
        printer.restoreHand(mc);
        if (mc.player != null && printer.kind == Kind.CREATIVE && printer.commands == null) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }
        if (reason != null) {
            DihClientMessaging.sendPrefixed(reason);
        }
    }

    public static void tick(Minecraft mc) {
        SchematicPrinter printer = active;
        if (printer == null) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            active = null;
            return;
        }
        if (!printer.placement.isLoaded() || !Schematics.get().placements().contains(printer.placement)) {
            stop(mc, "§7Printer stopped: the placement is gone");
            return;
        }
        if (printer.kind == Kind.CREATIVE && !mc.player.getAbilities().instabuild) {
            stop(mc, "§cPrinter stopped: you left creative mode");
            return;
        }
        if (printer.commands != null) {
            printer.tickCommands(mc);
        } else {
            printer.tickPrint(mc);
        }
    }

    // ---- creative paste with commands ----

    private void planCommands(ClientLevel level) {
        BoundingBox box = this.placement.bounds();
        List<SchematicPaste.Cell> cells = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (box != null) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    for (int x = box.minX(); x <= box.maxX(); x++) {
                        BlockState want = this.placement.expected(x, y, z);
                        if (want == null) {
                            continue;
                        }
                        if (!level.hasChunk(x >> 4, z >> 4)) {
                            this.unloadedAtStart++;
                            continue;
                        }
                        pos.set(x, y, z);
                        BlockState have = level.getBlockState(pos);
                        if (SchematicVerifier.classify(want, have) == SchematicVerifier.Mismatch.NONE) {
                            continue;
                        }
                        if (want.isAir() && !this.options.replace()) {
                            continue;
                        }
                        int pass = want.isAir() ? SchematicPaste.PASS_AIR
                            : want.isCollisionShapeFullBlock(level, pos) ? SchematicPaste.PASS_SOLID
                            : SchematicPaste.PASS_ATTACHED;
                        cells.add(new SchematicPaste.Cell(x, y, z, BlockStateParser.serialize(want), pass));
                    }
                }
            }
        }
        this.commands = SchematicPaste.commands(cells);
    }

    private void tickCommands(Minecraft mc) {
        int sent = 0;
        while (this.commandIndex < this.commands.size() && sent < Math.max(1, this.options.commandsPerTick())) {
            mc.getConnection().sendCommand(this.commands.get(this.commandIndex++));
            sent++;
        }
        if (this.commandIndex >= this.commands.size()) {
            stop(mc, "§aPasted §f" + this.placement.name() + "§a (" + this.commands.size() + " commands)");
        }
    }

    // ---- printing ----

    private void tickPrint(Minecraft mc) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Vec3 eye = player.getEyePosition();
        BoundingBox box = this.placement.bounds();
        if (box == null) {
            return;
        }
        this.ticks++;
        Set<BlockPos> keepClear = this.kind == Kind.ASSIST ? baritonePathAhead() : Set.of();
        int r = (int) Math.ceil(this.options.reach());
        BlockPos center = BlockPos.containing(eye);
        int minX = Math.max(box.minX(), center.getX() - r), maxX = Math.min(box.maxX(), center.getX() + r);
        int minY = Math.max(box.minY(), center.getY() - r), maxY = Math.min(box.maxY(), center.getY() + r);
        int minZ = Math.max(box.minZ(), center.getZ() - r), maxZ = Math.min(box.maxZ(), center.getZ() + r);

        List<BlockPos> near = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (this.skipped.contains(pos) || waiting(pos) || keepClear.contains(pos) || occupied(player, pos)
                        || eye.distanceToSqr(Vec3.atCenterOf(pos)) > (this.options.reach() + 0.5) * (this.options.reach() + 0.5)) {
                        continue;
                    }
                    BlockState want = this.placement.expected(x, y, z);
                    if (want != null && work(want, level.getBlockState(pos)) != Work.NONE) {
                        near.add(pos);
                    }
                }
            }
        }
        // bottom up, nearest first, so supports come before what sits on them
        near.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(a.getY(), b.getY())
            : Double.compare(eye.distanceToSqr(Vec3.atCenterOf(a)), eye.distanceToSqr(Vec3.atCenterOf(b))));

        int originalSlot = player.getInventory().getSelectedSlot();
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        boolean turned = false;
        int actions = 0;
        for (BlockPos pos : near) {
            if (actions >= Math.max(1, this.options.perTick())) {
                break;
            }
            BlockState want = this.placement.expected(pos);
            BlockState have = level.getBlockState(pos);
            Work work = work(want, have);
            if (work == Work.BREAK) {
                mc.gameMode.startDestroyBlock(pos, Direction.UP);
                attempt(pos);
                this.broken++;
                actions++;
                have = level.getBlockState(pos);
                work = work(want, have);
            }
            if (work == Work.PLACE && place(mc, pos, want, have)) {
                turned = true;
                attempt(pos);
                this.placed++;
                actions++;
                // what leaned on this block may have a face to be clicked now
                for (Direction d : Direction.values()) {
                    this.waitUntil.remove(pos.relative(d));
                }
            }
        }
        if (turned) {
            // the server keeps the last look it was sent; give it the real one back, and tell the client that is
            // what the server has, or it skips sending its next look when that matches its own stale copy and
            // Baritone's next click lands with the printer's look
            player.setYRot(yaw);
            player.setXRot(pitch);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
            ((dihclient.mixin.accessor.DihLocalPlayerAccessor) player).dih$setYRotLast(yaw);
            ((dihclient.mixin.accessor.DihLocalPlayerAccessor) player).dih$setXRotLast(pitch);
        }
        if (this.kind == Kind.ASSIST && player.getInventory().getSelectedSlot() != originalSlot) {
            player.getInventory().setSelectedSlot(originalSlot);
        }

        if (this.kind != Kind.CREATIVE) {
            return;
        }
        if (actions > 0) {
            player.setDeltaMovement(Vec3.ZERO);
            this.stuckTicks = 0;
            return;
        }
        fly(mc, level, box);
    }

    private void fly(Minecraft mc, ClientLevel level, BoundingBox box) {
        LocalPlayer player = mc.player;
        if (++this.sinceScan >= RESCAN_TICKS || this.todo.isEmpty()) {
            scan(level, box);
            this.sinceScan = 0;
        }
        BlockPos target = null;
        boolean anyWaiting = false;
        for (int tries = 0; tries < 32 && target == null; tries++) {
            double best = Double.MAX_VALUE;
            for (BlockPos pos : this.todo) {
                if (this.skipped.contains(pos)) {
                    continue;
                }
                if (waiting(pos)) {
                    anyWaiting = true;
                    continue;
                }
                double d = player.position().distanceToSqr(Vec3.atCenterOf(pos));
                if (d < best) {
                    best = d;
                    target = pos;
                }
            }
            // the list is up to a second old: drop what has been placed since instead of flying to it
            if (target != null && work(this.placement.expected(target), level.getBlockState(target)) == Work.NONE) {
                this.todo = new ArrayList<>(this.todo);
                this.todo.remove(target);
                target = null;
            } else {
                break;
            }
        }
        boolean unloaded = false;
        if (target == null && this.nearestUnloaded != null) {
            target = this.nearestUnloaded;
            unloaded = true;
        }
        if (target == null && anyWaiting) {
            // what is left waits on its neighbours; hold still until one of them can be tried again
            player.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (target == null) {
            stop(mc, "§aFinished §f" + this.placement.name() + "§a: " + this.placed + " placed, " + this.broken + " broken"
                + (this.skipped.isEmpty() ? "" : ", " + this.skipped.size() + " could not be placed (#schem verify shows them)"));
            return;
        }
        if (!target.equals(this.flyTarget)) {
            this.flyTarget = target;
            this.stuckTicks = 0;
        }
        if (!player.getAbilities().flying && player.getAbilities().mayfly) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
        Vec3 goal = new Vec3(target.getX() + 0.5, target.getY() + 2.0, target.getZ() + 0.5);
        Vec3 delta = goal.subtract(player.position());
        double length = delta.length();
        double reach = this.options.reach() - 0.5;
        boolean inReach = !unloaded && !occupied(player, target)
            && player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= reach * reach;
        if (inReach) {
            // close enough to click it; the next tick places it, or puts it on hold and picks another
            player.setDeltaMovement(Vec3.ZERO);
        } else if (length < 0.25) {
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 move = delta.scale(Math.min(this.options.flySpeed(), length * 0.6) / length);
            if (player.horizontalCollision) {
                // a wall of the build is in the way: go up over it rather than push into it
                move = new Vec3(move.x * 0.3, Math.max(move.y, Math.min(this.options.flySpeed(), 0.6)), move.z * 0.3);
            }
            player.setDeltaMovement(move);
        }
        Vec3 now = player.position();
        if (this.lastPos != null && now.distanceToSqr(this.lastPos) < 0.0025 && length > 1.0 && !inReach) {
            if (++this.stuckTicks > STUCK_TICKS) {
                this.skipped.add(target);
                this.todo = new ArrayList<>(this.todo);
                this.todo.remove(target);
                if (target.equals(this.nearestUnloaded)) {
                    this.nearestUnloaded = null;
                }
                this.stuckTicks = 0;
            }
        } else {
            this.stuckTicks = 0;
        }
        this.lastPos = now;
    }

    private void scan(ClientLevel level, BoundingBox box) {
        List<BlockPos> list = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos unloaded = null;
        double unloadedBest = Double.MAX_VALUE;
        Vec3 at = Minecraft.getInstance().player.position();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockState want = this.placement.expected(x, y, z);
                    if (want == null) {
                        continue;
                    }
                    pos.set(x, y, z);
                    if (!level.hasChunk(x >> 4, z >> 4)) {
                        double d = at.distanceToSqr(x + 0.5, at.y, z + 0.5);
                        if (d < unloadedBest && !this.skipped.contains(pos)) {
                            unloadedBest = d;
                            unloaded = new BlockPos(x, (int) at.y - 2, z);
                        }
                        continue;
                    }
                    if (!this.skipped.contains(pos) && work(want, level.getBlockState(pos)) != Work.NONE) {
                        list.add(pos.immutable());
                    }
                }
            }
        }
        this.todo = list;
        this.nearestUnloaded = unloaded;
    }

    private void attempt(BlockPos pos) {
        int n = this.attempts.merge(pos.immutable(), 1, Integer::sum);
        if (n >= MAX_ATTEMPTS) {
            this.skipped.add(pos.immutable());
        }
    }

    private enum Work { NONE, PLACE, BREAK }

    private Work work(BlockState want, BlockState have) {
        if (SchematicVerifier.classify(want, have) == SchematicVerifier.Mismatch.NONE) {
            return Work.NONE;
        }
        if (isSecondHalf(want)) {
            return Work.NONE; // comes with the other half
        }
        boolean canBreak = this.kind == Kind.CREATIVE && this.options.replace()
            && !(have.getBlock() instanceof LiquidBlock) && !have.isAir();
        if (want.isAir()) {
            return canBreak ? Work.BREAK : Work.NONE;
        }
        if (!placeable(want)) {
            return Work.NONE;
        }
        if (have.isAir() || have.canBeReplaced() || mergesSlab(want, have)) {
            return Work.PLACE;
        }
        return canBreak ? Work.BREAK : Work.NONE;
    }

    private static boolean placeable(BlockState want) {
        return want.getBlock().asItem() != Items.AIR && !(want.getBlock() instanceof LiquidBlock);
    }

    private static boolean isSecondHalf(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.BED_PART) && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD;
    }

    private static boolean mergesSlab(BlockState want, BlockState have) {
        return want.getBlock() instanceof SlabBlock && have.getBlock() == want.getBlock()
            && want.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && have.getValue(SlabBlock.TYPE) != SlabType.DOUBLE;
    }

    /**
     * Places {@code want} only when a click turns it the schematic's way. When none does yet (the face it hangs on,
     * or the one that sets its axis, is not built), the position waits for its neighbours. Fast mode never places it
     * the wrong way: Baritone would break it and the printer would put it back wrong, over and over. Creative places
     * the closest way after {@link #MAX_FAILURES} tries, and its replace setting fixes it later.
     *
     * @return whether a click was sent (and the look sent to the server changed)
     */
    private boolean place(Minecraft mc, BlockPos pos, BlockState want, BlockState have) {
        LocalPlayer player = mc.player;
        Item item = want.getBlock().asItem();
        int slot = handFor(mc, item);
        if (slot < 0) {
            return false;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        Plan plan = solve(mc, pos, want, have, stack);
        if (plan == null || !plan.exact) {
            int n = fail(pos);
            if (plan == null || this.kind != Kind.CREATIVE || n < MAX_FAILURES) {
                if (plan == null && n >= MAX_FAILURES) {
                    this.skipped.add(pos.immutable()); // nothing to click from anywhere it has been tried
                }
                return false;
            }
        }
        player.getInventory().setSelectedSlot(slot);
        player.setYRot(plan.yaw);
        player.setXRot(plan.pitch);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(plan.yaw, plan.pitch, player.onGround(), player.horizontalCollision));
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, plan.hit);
        if (result.consumesAction()) {
            dihclient.util.DihEntities.swing(player, InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /**
     * Puts {@code pos} on hold for {@link #WAIT_TICKS}.
     *
     * @return how many times it has been put on hold
     */
    private int fail(BlockPos pos) {
        BlockPos key = pos.immutable();
        this.waitUntil.put(key, this.ticks + WAIT_TICKS);
        return this.failures.merge(key, 1, Integer::sum);
    }

    private boolean waiting(BlockPos pos) {
        Integer until = this.waitUntil.get(pos);
        return until != null && until > this.ticks;
    }

    /**
     * @return whether a block at {@code pos} would sit in the player, or in the space just over their head that a
     *         jump or a pillar needs
     */
    private static boolean occupied(LocalPlayer player, BlockPos pos) {
        AABB body = player.getBoundingBox().expandTowards(0, 1, 0).deflate(1.0E-4);
        return body.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    /**
     * @return the feet and head positions of Baritone's next few path nodes, which fast mode leaves empty
     */
    private static Set<BlockPos> baritonePathAhead() {
        Set<BlockPos> clear = new HashSet<>();
        try {
            baritone.api.pathing.path.IPathExecutor current = baritone.api.BaritoneAPI.getProvider()
                .getPrimaryBaritone().getPathingBehavior().getCurrent();
            if (current == null) {
                return clear;
            }
            List<baritone.api.utils.BetterBlockPos> nodes = current.getPath().positions();
            int from = Math.max(0, current.getPosition());
            for (int i = from; i < Math.min(nodes.size(), from + PATH_AHEAD); i++) {
                // plain BlockPos: BetterBlockPos hashes its own way
                BlockPos node = new BlockPos(nodes.get(i).getX(), nodes.get(i).getY(), nodes.get(i).getZ());
                clear.add(node);
                clear.add(node.above());
            }
        } catch (RuntimeException ignored) {
            // the path changed under us; keep nothing clear this tick
        }
        return clear;
    }

    /**
     * @return the hotbar slot that holds {@code item}, after putting it there in creative; -1 when there is none
     */
    private int handFor(Minecraft mc, Item item) {
        LocalPlayer player = mc.player;
        if (this.kind == Kind.CREATIVE) {
            if (!player.getInventory().getItem(this.handSlot).is(item)) {
                ItemStack stack = new ItemStack(item, item.getDefaultMaxStackSize());
                mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) (36 + this.handSlot), stack));
                player.getInventory().setItem(this.handSlot, stack);
            }
            return this.handSlot;
        }
        int selected = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(selected).is(item)) {
            return selected;
        }
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    private void restoreHand(Minecraft mc) {
        if (this.handSlot < 0 || mc.player == null || mc.getConnection() == null || !mc.player.getAbilities().instabuild) {
            return;
        }
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) (36 + this.handSlot), this.handOriginal));
        mc.player.getInventory().setItem(this.handSlot, this.handOriginal);
        mc.player.getInventory().setSelectedSlot(this.handSlot);
    }

    /** @param exact whether every orientation property comes out as the schematic has it */
    private record Plan(BlockHitResult hit, float yaw, float pitch, boolean exact) {}

    /**
     * Finds a click and a look that place {@code want} at {@code pos}: every face it could click, each way it could
     * face, asking the block what it would become. Takes the first exact match, or the closest one.
     */
    private Plan solve(Minecraft mc, BlockPos pos, BlockState want, BlockState have, ItemStack stack) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Vec3 eye = player.getEyePosition();
        double reachSq = this.options.reach() * this.options.reach();
        if (want.getBlock() instanceof SlabBlock && want.getValue(SlabBlock.TYPE) == SlabType.DOUBLE && !mergesSlab(want, have)) {
            want = want.setValue(SlabBlock.TYPE, SlabType.BOTTOM); // one half now, the other merges into it next
        }

        List<BlockHitResult> hits = new ArrayList<>();
        if (mergesSlab(want, have)) {
            // click the slab itself, on its open side
            Direction face = have.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
            hits.add(new BlockHitResult(Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(0.5)), face, pos, false));
        } else {
            for (Direction d : Direction.values()) {
                BlockPos against = pos.relative(d);
                BlockState support = level.getBlockState(against);
                if (support.isAir() || support.canBeReplaced() || interactive(level, against, support)) {
                    continue;
                }
                Direction face = d.getOpposite();
                Vec3 base = Vec3.atCenterOf(against).add(face.getUnitVec3().scale(0.5));
                if (face.getAxis().isHorizontal()) {
                    hits.add(new BlockHitResult(base.add(0, 0.25, 0), face, against, false));
                    hits.add(new BlockHitResult(base.add(0, -0.25, 0), face, against, false));
                } else {
                    hits.add(new BlockHitResult(base, face, against, false));
                }
            }
            if (this.options.airPlace()) {
                for (Direction face : Direction.values()) {
                    Vec3 base = Vec3.atCenterOf(pos).add(face.getUnitVec3().scale(0.5));
                    if (face.getAxis().isHorizontal()) {
                        hits.add(new BlockHitResult(base.add(0, 0.25, 0), face, pos, false));
                        hits.add(new BlockHitResult(base.add(0, -0.25, 0), face, pos, false));
                    } else {
                        hits.add(new BlockHitResult(base, face, pos, false));
                    }
                }
            }
        }
        hits.removeIf(h -> h.getLocation().distanceToSqr(eye) > reachSq);
        if (hits.isEmpty()) {
            return null;
        }

        int wanted = 0;
        for (Property<?> property : want.getProperties()) {
            if (ORIENTATION.contains(property.getName())) {
                wanted++;
            }
        }
        float realYaw = player.getYRot();
        float realPitch = player.getXRot();
        Plan best = null;
        int bestScore = -1;
        try {
            for (BlockHitResult hit : hits) {
                Vec3 look = hit.getLocation().subtract(eye);
                float lookYaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90.0);
                float lookPitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)));
                for (float[] l : looks(want, wanted, lookYaw, lookPitch)) {
                    player.setYRot(l[0]);
                    player.setXRot(l[1]);
                    BlockState result;
                    try {
                        result = want.getBlock().getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
                    } catch (RuntimeException e) {
                        result = null;
                    }
                    if (result == null || result.getBlock() != want.getBlock()) {
                        continue;
                    }
                    int score = score(want, result);
                    if (score > bestScore) {
                        bestScore = score;
                        best = new Plan(hit, l[0], l[1], score == wanted);
                        if (score == wanted) {
                            return best;
                        }
                    }
                }
            }
        } finally {
            player.setYRot(realYaw);
            player.setXRot(realPitch);
        }
        return best;
    }

    /**
     * The looks to try for one click: straight at the click first, then every way the block can face. Signs,
     * banners and heads keep 16 turns, so they get all 16; the rest get the four sides, level, up and down.
     */
    private static List<float[]> looks(BlockState want, int wanted, float lookYaw, float lookPitch) {
        List<float[]> looks = new ArrayList<>();
        looks.add(new float[]{lookYaw, lookPitch});
        if (wanted == 0) {
            return looks;
        }
        boolean sixteen = false;
        for (Property<?> property : want.getProperties()) {
            sixteen |= property.getName().equals("rotation");
        }
        int steps = sixteen ? 16 : 4;
        for (float pitch : new float[]{0, 80, -80}) {
            for (int i = 0; i < steps; i++) {
                looks.add(new float[]{net.minecraft.util.Mth.wrapDegrees(i * 360f / steps), pitch});
            }
        }
        return looks;
    }

    private static int score(BlockState want, BlockState got) {
        int score = 0;
        for (Property<?> property : want.getProperties()) {
            if (ORIENTATION.contains(property.getName()) && got.hasProperty(property)
                && want.getValue(property).equals(got.getValue(property))) {
                score++;
            }
        }
        return score;
    }

    /**
     * @return whether clicking the block would open or toggle it instead of placing against it
     */
    private static boolean interactive(ClientLevel level, BlockPos pos, BlockState state) {
        Block b = state.getBlock();
        return level.getBlockEntity(pos) != null || state.getMenuProvider(level, pos) != null
            || b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
            || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof BedBlock || b instanceof NoteBlock;
    }
}
