package baritone.parkour.sim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A recorded movement trace: the blocks around it, the player's state before the first tick, the input of each tick
 * and the real state after each tick. The fidelity game test writes these from the live client; the unit test replays
 * them through {@link PlayerPhysics} with no game running.
 */
public final class TraceFixture {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public final String name;
    public final List<Map.Entry<BlockPos, BlockState>> blocks;
    public final PlayerState start;
    public final List<TickInput> inputs;
    /** Per tick: x, y, z, vx, vy, vz, onGround (1/0). */
    public final List<double[]> expected;

    public TraceFixture(String name, List<Map.Entry<BlockPos, BlockState>> blocks, PlayerState start, List<TickInput> inputs, List<double[]> expected) {
        this.name = name;
        this.blocks = blocks;
        this.start = start;
        this.inputs = inputs;
        this.expected = expected;
    }

    public BlockSource.MapSource world() {
        BlockSource.MapSource source = new BlockSource.MapSource();
        for (Map.Entry<BlockPos, BlockState> block : blocks) {
            BlockPos p = block.getKey();
            source.set(p.getX(), p.getY(), p.getZ(), block.getValue());
        }
        return source;
    }

    public static double[] sample(PlayerState s) {
        return new double[]{s.x, s.y, s.z, s.vx, s.vy, s.vz, s.onGround ? 1 : 0};
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("name", name);
        // Blocks as runs along x, [x0, x1, y, z, state], so floors stay small.
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(blocks);
        sorted.sort(java.util.Comparator.<Map.Entry<BlockPos, BlockState>>comparingInt(e -> e.getKey().getY())
            .thenComparingInt(e -> e.getKey().getZ()).thenComparingInt(e -> e.getKey().getX()));
        JsonArray runs = new JsonArray();
        JsonArray open = null;
        for (Map.Entry<BlockPos, BlockState> block : sorted) {
            BlockPos p = block.getKey();
            String state = BlockStateParser.serialize(block.getValue());
            if (open != null && open.get(2).getAsInt() == p.getY() && open.get(3).getAsInt() == p.getZ()
                && open.get(4).getAsString().equals(state) && open.get(1).getAsInt() == p.getX() - 1) {
                open.set(1, new com.google.gson.JsonPrimitive(p.getX()));
                continue;
            }
            open = new JsonArray();
            open.add(p.getX());
            open.add(p.getX());
            open.add(p.getY());
            open.add(p.getZ());
            open.add(state);
            runs.add(open);
        }
        root.add("runs", runs);
        JsonObject st = new JsonObject();
        st.addProperty("x", start.x);
        st.addProperty("y", start.y);
        st.addProperty("z", start.z);
        st.addProperty("vx", start.vx);
        st.addProperty("vy", start.vy);
        st.addProperty("vz", start.vz);
        st.addProperty("yaw", start.yaw);
        st.addProperty("onGround", start.onGround);
        st.addProperty("sprinting", start.sprinting);
        st.addProperty("fallDistance", start.fallDistance);
        if (start.support != null) st.addProperty("support", start.support.asLong());
        root.add("start", st);
        JsonArray in = new JsonArray();
        for (TickInput input : inputs) in.add(input.compact());
        root.add("inputs", in);
        JsonArray out = new JsonArray();
        for (double[] row : expected) {
            JsonArray r = new JsonArray();
            for (double v : row) r.add(v);
            out.add(r);
        }
        root.add("expected", out);
        return GSON.toJson(root);
    }

    public static TraceFixture fromJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        List<Map.Entry<BlockPos, BlockState>> blocks = new ArrayList<>();
        if (root.has("blocks")) {
            for (var element : root.getAsJsonArray("blocks")) {
                JsonArray entry = element.getAsJsonArray();
                BlockPos pos = new BlockPos(entry.get(0).getAsInt(), entry.get(1).getAsInt(), entry.get(2).getAsInt());
                blocks.add(Map.entry(pos, parseState(entry.get(3).getAsString())));
            }
        }
        if (root.has("runs")) {
            // [x0, x1, y, z, state]: the blocks from x0 to x1 in one row
            for (var element : root.getAsJsonArray("runs")) {
                JsonArray run = element.getAsJsonArray();
                BlockState state = parseState(run.get(4).getAsString());
                for (int x = run.get(0).getAsInt(); x <= run.get(1).getAsInt(); x++) {
                    blocks.add(Map.entry(new BlockPos(x, run.get(2).getAsInt(), run.get(3).getAsInt()), state));
                }
            }
        }
        JsonObject st = root.getAsJsonObject("start");
        PlayerState start = new PlayerState();
        start.x = st.get("x").getAsDouble();
        start.y = st.get("y").getAsDouble();
        start.z = st.get("z").getAsDouble();
        start.vx = st.get("vx").getAsDouble();
        start.vy = st.get("vy").getAsDouble();
        start.vz = st.get("vz").getAsDouble();
        start.yaw = st.get("yaw").getAsFloat();
        start.onGround = st.get("onGround").getAsBoolean();
        start.sprinting = st.get("sprinting").getAsBoolean();
        start.fallDistance = st.get("fallDistance").getAsDouble();
        if (st.has("support")) start.support = BlockPos.of(st.get("support").getAsLong());
        start.onGroundNoBlocks = start.onGround && start.support == null;
        List<TickInput> inputs = new ArrayList<>();
        for (var element : root.getAsJsonArray("inputs")) inputs.add(TickInput.parse(element.getAsString()));
        List<double[]> expected = new ArrayList<>();
        for (var element : root.getAsJsonArray("expected")) {
            JsonArray r = element.getAsJsonArray();
            double[] row = new double[r.size()];
            for (int i = 0; i < row.length; i++) row[i] = r.get(i).getAsDouble();
            expected.add(row);
        }
        return new TraceFixture(root.get("name").getAsString(), blocks, start, inputs, expected);
    }

    private static BlockState parseState(String text) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, text, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("bad block state " + text, e);
        }
    }
}
