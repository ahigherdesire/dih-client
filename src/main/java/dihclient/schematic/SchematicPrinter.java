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

    private static SchematicPrinter active;

    private final SchematicPlacement placement;
    private final Kind kind;
    private final Options options;
    private final Map<BlockPos, Integer> attempts = new HashMap<>();
    private final Set<BlockPos> skipped = new HashSet<>();

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
                    if (this.skipped.contains(pos) || eye.distanceToSqr(Vec3.atCenterOf(pos)) > (this.options.reach() + 0.5) * (this.options.reach() + 0.5)) {
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
            if (work == Work.PLACE) {
                Boolean done = place(mc, pos, want, have);
                if (done != null) {
                    turned |= done;
                    attempt(pos);
                    this.placed++;
                    actions++;
                }
            }
        }
        if (turned) {
            // the server keeps the last look it was sent; give it the real one back
            player.setYRot(yaw);
            player.setXRot(pitch);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, player.onGround(), player.horizontalCollision));
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
        double best = Double.MAX_VALUE;
        for (BlockPos pos : this.todo) {
            if (this.skipped.contains(pos)) {
                continue;
            }
            double d = player.position().distanceToSqr(Vec3.atCenterOf(pos));
            if (d < best) {
                best = d;
                target = pos;
            }
        }
        if (target == null && this.nearestUnloaded != null) {
            target = this.nearestUnloaded;
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
        if (length < 0.25) {
            player.setDeltaMovement(Vec3.ZERO);
        } else {
            player.setDeltaMovement(delta.scale(Math.min(this.options.flySpeed(), length * 0.6) / length));
        }
        Vec3 now = player.position();
        if (this.lastPos != null && now.distanceToSqr(this.lastPos) < 0.0025 && length > 1.0) {
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
     * @return null when nothing was sent; otherwise whether the look sent to the server was changed
     */
    private Boolean place(Minecraft mc, BlockPos pos, BlockState want, BlockState have) {
        LocalPlayer player = mc.player;
        Item item = want.getBlock().asItem();
        int slot = handFor(mc, item);
        if (slot < 0) {
            return null;
        }
        ItemStack stack = player.getInventory().getItem(slot);
        Plan plan = solve(mc, pos, want, have, stack);
        if (plan == null) {
            return null;
        }
        player.getInventory().setSelectedSlot(slot);
        player.setYRot(plan.yaw);
        player.setXRot(plan.pitch);
        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(plan.yaw, plan.pitch, player.onGround(), player.horizontalCollision));
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, plan.hit);
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
        return Boolean.TRUE;
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

    private record Plan(BlockHitResult hit, float yaw, float pitch) {}

    /**
     * Finds a click and a look that place {@code want} at {@code pos}: every face it could click, each way it could
     * face, asking the block what it would become. Takes the first exact match, or the closest one.
     */
    private Plan solve(Minecraft mc, BlockPos pos, BlockState want, BlockState have, ItemStack stack) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        Vec3 eye = player.getEyePosition();
        double reachSq = this.options.reach() * this.options.reach();

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
                float[][] looks = wanted == 0
                    ? new float[][]{{lookYaw, lookPitch}}
                    : new float[][]{{lookYaw, lookPitch}, {0, 0}, {90, 0}, {180, 0}, {-90, 0},
                        {0, 80}, {0, -80}, {90, 80}, {180, 80}, {-90, 80}, {90, -80}, {180, -80}, {-90, -80}};
                for (float[] l : looks) {
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
                        best = new Plan(hit, l[0], l[1]);
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
