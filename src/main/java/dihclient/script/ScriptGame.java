package dihclient.script;

import dihclient.mixin.accessor.DihMinecraftAccessor;
import dihclient.platform.DihLoader;
import dihclient.script.lang.Interpreter;
import dihclient.script.lang.Library;
import dihclient.script.lang.ScriptException;
import dihclient.util.DihInventoryHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/** Script game actions and observations, always evaluated on the Minecraft client thread. */
public final class ScriptGame {
    private static final Map<Interpreter, Set<String>> HELD_KEYS = new WeakHashMap<>();
    private static final Map<String, Boolean> PREVIOUS_KEYS = new HashMap<>();
    private ScriptGame() { }

    public static Library create() {
        Library lib = new Library();
        lib.define("Player", "alive()", "True while a living player is in the world.", 0, 0,
            a -> GameLibrary.onClient(() -> mc().player != null && mc().level != null && mc().player.isAlive()));
        lib.define("Player", "usingItem()", "True while either hand is using an item, including Meteor eating.", 0, 0, a -> player(LocalPlayer::isUsingItem));
        lib.define("Player", "onGround()", "Whether the player is standing on ground.", 0, 0, a -> player(LocalPlayer::onGround));
        lib.define("Player", "inWater()", "Whether the player touches water.", 0, 0, a -> player(LocalPlayer::isInWater));
        lib.define("Player", "submerged()", "Whether the player's eyes are underwater.", 0, 0, a -> player(LocalPlayer::isUnderWater));
        lib.define("Player", "inLava()", "Whether the player touches lava.", 0, 0, a -> player(LocalPlayer::isInLava));
        lib.define("Player", "onFire()", "Whether the player is burning.", 0, 0, a -> player(LocalPlayer::isOnFire));
        lib.define("Player", "air()", "Remaining air supply.", 0, 0, a -> player(p -> (double) p.getAirSupply()));
        lib.define("Player", "fallDistance()", "Accumulated fall distance.", 0, 0, a -> player(p -> (double) p.fallDistance));
        lib.define("Player", "velocity()", "Object with x, y and z velocity in blocks per tick.", 0, 0, a -> player(p -> vector(p.getDeltaMovement())));
        lib.define("Player", "yaw()", "Current yaw in degrees.", 0, 0, a -> player(p -> (double) p.getYRot()));
        lib.define("Player", "pitch()", "Current pitch in degrees.", 0, 0, a -> player(p -> (double) p.getXRot()));
        lib.define("World", "biome()", "Namespaced biome ID at the player, or null if unregistered.", 0, 0,
            a -> player(p -> p.level().getBiome(p.blockPosition()).unwrapKey().map(k -> k.identifier().toString()).orElse(null)));
        lib.define("World", "groundDistance(maxDistance)", "Vertical collision distance below the feet, or null if no ground is found. Liquids do not count as ground.", 0, 1,
            a -> player(p -> groundDistance(p, boundedDistance(a.number(0, 64), 512))));
        lib.define("World", "nearestThreat(radius)", "Nearest hostile, targeting or observed attacking entity: identity, position, distance, targeting and attacking. Hostility alone never implies attacking.", 0, 1,
            a -> player(p -> nearestThreat(p, boundedDistance(a.number(0, 32), 256))));
        lib.define("World", "blockInfo(x, y, z)", "Block ID, fluid, collision, fullBlockSupport, falling and loaded; unloaded blocks have null ID.", 3, 3,
            a -> player(p -> blockInfo(p, new BlockPos(a.integer(0), a.integer(1), a.integer(2)))));
        lib.define("World", "crosshairTarget()", "Object with type entity/block/miss and target identity or block coordinates.", 0, 0,
            a -> GameLibrary.onClient(ScriptGame::crosshairTarget));
        lib.define("Player", "look(yaw, pitch, sync)", "Set direction; sync=true sends rotation before returning.", 2, 3, a -> player(p -> {
            double yaw = a.number(0), pitch = a.number(1);
            if (!Double.isFinite(yaw) || !Double.isFinite(pitch) || pitch < -90 || pitch > 90) throw a.error("finite yaw and pitch between -90 and 90 required");
            p.setYRot((float) yaw); p.setXRot((float) pitch);
            if (a.has(2) && bool(a, 2)) p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround(), p.horizontalCollision));
            return true;
        }));
        lib.define("Player", "attackCooldown()", "Current attack strength, from zero to one.", 0, 0,
            a -> player(p -> (double) Math.max(0, Math.min(1, p.getAttackStrengthScale(0)))));
        lib.define("Player", "attack()", "Perform the normal crosshair attack. Check attackCooldown() before mending attacks.", 0, 0,
            a -> player(p -> mc().gameMode != null && mc().gui.screen() == null && ((DihMinecraftAccessor) mc()).dih$startAttack()));
        lib.define("Player", "useItem(hand)", "Use the item in mainhand (default) or offhand; returns whether the action was accepted locally.", 0, 1,
            a -> player(p -> mc().gameMode != null && !ScriptInventory.yieldToSafety() && !p.isUsingItem() && mc().gameMode.useItem(p, handArg(a, 0)).consumesAction()));
        lib.define("World", "interactBlock(x, y, z, face, hand)", "Interact with the exact loaded block in reach; face is down/up/north/south/west/east.", 4, 5,
            a -> player(p -> interact(p, new BlockPos(a.integer(0), a.integer(1), a.integer(2)), direction(a.string(3)), handArg(a, 4))));
        lib.define("Player", "setKey(key, pressed)", "Hold/release forward, back, left, right, jump, sneak, sprint, attack or use. Script keys release on stop.", 2, 2,
            a -> GameLibrary.onClient(() -> setKey(a.interpreter(), a.string(0), bool(a, 1))));
        lib.define("Player", "eat(hand)", "Eat one hotbar food (no argument), or from mainhand/offhand; refuses to compete with current item use.", 0, 1,
            a -> eat(a.interpreter(), a.has(0) ? handArg(a, 0) : null));
        lib.define("Mods", "modLoaded(id)", "Whether a mod is loaded.", 1, 1, a -> DihLoader.isModLoaded(a.string(0)));
        lib.define("Mods", "moduleEnabled(provider, module)", "Whether an optional provider module is active; false if missing.", 2, 2,
            a -> GameLibrary.onClient(() -> ScriptMeteor.enabled(a.string(0), a.string(1))));
        lib.define("Mods", "setModuleEnabled(provider, module, enabled)", "Idempotently set module state; false if provider/module is unavailable.", 3, 3,
            a -> GameLibrary.onClient(() -> ScriptMeteor.setEnabled(a.string(0), a.string(1), bool(a, 2))));
        return lib;
    }

    private static double boundedDistance(double distance, double max) {
        if (!Double.isFinite(distance) || distance < 0 || distance > max) throw new ScriptException("Distance must be between 0 and " + max, 0);
        return distance;
    }

    static InteractionHand hand(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "mainhand", "main_hand", "main" -> InteractionHand.MAIN_HAND;
            case "offhand", "off_hand", "off" -> InteractionHand.OFF_HAND;
            default -> throw new IllegalArgumentException("hand must be mainhand or offhand");
        };
    }

    private static InteractionHand handArg(Library.Args a, int index) {
        try { return hand(a.string(index, "mainhand")); }
        catch (IllegalArgumentException e) { throw a.error(e.getMessage()); }
    }

    private static Direction direction(String name) {
        try { return Direction.valueOf(name.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new ScriptException("Unknown block face: " + name, 0); }
    }

    private static boolean bool(Library.Args a, int index) {
        if (!(a.get(index) instanceof Boolean b)) throw a.error("argument " + (index + 1) + " must be boolean");
        return b;
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    private static <T> T player(Function<LocalPlayer, T> action) {
        return GameLibrary.onClient(() -> {
            if (mc().player == null || mc().level == null) throw new ScriptException("Not in a world", 0);
            return action.apply(mc().player);
        });
    }

    static Map<String, Object> vector(Vec3 v) {
        return new LinkedHashMap<>(Map.of("x", v.x, "y", v.y, "z", v.z));
    }

    private static Object groundDistance(LocalPlayer p, double max) {
        Vec3 feet = p.position();
        Vec3 end = feet.add(0, -max, 0);
        // Refuse to describe unloaded terrain as safe air or ground.
        for (double y = feet.y; y >= end.y; y -= 1) if (!p.level().hasChunkAt(BlockPos.containing(feet.x, y, feet.z))) return null;
        BlockHitResult hit = p.level().clip(new ClipContext(feet, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? null : Math.max(0, feet.y - hit.getLocation().y);
    }

    static Map<String, Object> threatState(boolean hostile, boolean targeting, boolean recentlyHurtPlayer) {
        return Map.of("hostile", hostile, "targeting", targeting, "attacking", recentlyHurtPlayer);
    }

    static boolean isRecentAttacker(Object entity, Object owner, Object directSource, int hurtTime) {
        return entity != null && hurtTime > 0 && (entity == owner || entity == directSource);
    }

    private static boolean recentlyAttacked(LocalPlayer player, Entity entity) {
        var source = player.getLastDamageSource();
        return source != null && isRecentAttacker(entity, source.getEntity(), source.getDirectEntity(), player.hurtTime);
    }

    private static Map<String, Object> entityInfo(LocalPlayer p, Entity entity) {
        Map<String, Object> out = vector(entity.position());
        out.put("id", entity.getUUID().toString());
        out.put("entityId", (double) entity.getId());
        out.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        out.put("name", entity.getName().getString());
        out.put("distance", (double) entity.distanceTo(p));
        boolean targeting = entity instanceof Mob mob && mob.getTarget() == p;
        boolean hurt = recentlyAttacked(p, entity);
        out.putAll(threatState(entity instanceof Enemy, targeting, hurt));
        return out;
    }

    private static Object nearestThreat(LocalPlayer p, double radius) {
        Entity nearest = null;
        double distance = radius * radius;
        for (Entity entity : p.level().getEntities(p, p.getBoundingBox().inflate(radius))) {
            if (!entity.isAlive()) continue;
            boolean targeting = entity instanceof Mob mob && mob.getTarget() == p;
            if (!(entity instanceof Enemy) && !targeting && !recentlyAttacked(p, entity)) continue;
            double d = entity.distanceToSqr(p);
            if (d <= distance) { distance = d; nearest = entity; }
        }
        return nearest == null ? null : entityInfo(p, nearest);
    }

    private static Map<String, Object> blockInfo(LocalPlayer p, BlockPos pos) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("x", (double) pos.getX()); out.put("y", (double) pos.getY()); out.put("z", (double) pos.getZ());
        boolean loaded = p.level().hasChunkAt(pos);
        out.put("loaded", loaded);
        out.put("id", null); out.put("fluid", null); out.put("collision", null);
        out.put("fullBlockSupport", null); out.put("falling", null);
        if (!loaded) return out;
        var state = p.level().getBlockState(pos);
        out.put("id", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        out.put("fluid", BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString());
        out.put("collision", !state.getCollisionShape(p.level(), pos).isEmpty());
        out.put("fullBlockSupport", state.isFaceSturdy(p.level(), pos, Direction.UP));
        out.put("falling", state.getBlock() instanceof FallingBlock);
        return out;
    }

    private static Map<String, Object> crosshairTarget() {
        HitResult hit = mc().hitResult;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "miss");
        if (hit == null || hit.getType() == HitResult.Type.MISS) return out;
        if (hit instanceof EntityHitResult entity && mc().player != null) {
            out.putAll(entityInfo(mc().player, entity.getEntity()));
            out.put("entityType", out.get("type")); out.put("type", "entity");
        } else if (hit instanceof BlockHitResult block) {
            out.put("type", "block");
            out.put("x", (double) block.getBlockPos().getX()); out.put("y", (double) block.getBlockPos().getY()); out.put("z", (double) block.getBlockPos().getZ());
            out.put("face", block.getDirection().name().toLowerCase(Locale.ROOT));
            if (mc().player != null) out.put("id", blockInfo(mc().player, block.getBlockPos()).get("id"));
        }
        return out;
    }

    static Vec3 facePoint(BlockPos pos, AABB bounds, Direction face) {
        double x = (bounds.minX + bounds.maxX) * .5;
        double y = (bounds.minY + bounds.maxY) * .5;
        double z = (bounds.minZ + bounds.maxZ) * .5;
        switch (face) {
            case DOWN -> y = bounds.minY;
            case UP -> y = bounds.maxY;
            case NORTH -> z = bounds.minZ;
            case SOUTH -> z = bounds.maxZ;
            case WEST -> x = bounds.minX;
            case EAST -> x = bounds.maxX;
        }
        return new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
    }

    private static boolean interact(LocalPlayer p, BlockPos pos, Direction face, InteractionHand hand) {
        if (mc().gameMode == null || ScriptInventory.busy() || p.isUsingItem() || !p.level().hasChunkAt(pos)) return false;
        var shape = p.level().getBlockState(pos).getShape(p.level(), pos);
        if (shape.isEmpty()) return false;
        Vec3 location = facePoint(pos, shape.bounds(), face);
        if (p.getEyePosition().distanceTo(location) > p.blockInteractionRange()) return false;
        BlockHitResult visible = p.level().clip(new ClipContext(p.getEyePosition(), location.add(face.getStepX() * -.001, face.getStepY() * -.001, face.getStepZ() * -.001), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (visible.getType() != HitResult.Type.BLOCK || !visible.getBlockPos().equals(pos)) return false;
        return mc().gameMode.useItemOn(p, hand, new BlockHitResult(location, face, pos, false)).consumesAction();
    }

    private static KeyMapping key(String name) {
        var options = mc().options;
        return switch (name) {
            case "forward" -> options.keyUp; case "back" -> options.keyDown;
            case "left" -> options.keyLeft; case "right" -> options.keyRight;
            case "jump" -> options.keyJump; case "sneak" -> options.keyShift;
            case "sprint" -> options.keySprint; case "attack" -> options.keyAttack;
            case "use" -> options.keyUse;
            default -> throw new ScriptException("Unknown key: " + name, 0);
        };
    }

    private static synchronized boolean setKey(Interpreter it, String raw, boolean pressed) {
        String name = raw.toLowerCase(Locale.ROOT);
        KeyMapping mapping = key(name);
        if (pressed) {
            PREVIOUS_KEYS.putIfAbsent(name, mapping.isDown());
            HELD_KEYS.computeIfAbsent(it, ignored -> new HashSet<>()).add(name);
            mapping.setDown(true);
        } else {
            Set<String> owned = HELD_KEYS.get(it);
            if (owned != null) { owned.remove(name); if (owned.isEmpty()) HELD_KEYS.remove(it); }
            if (HELD_KEYS.values().stream().noneMatch(keys -> keys.contains(name))) {
                Boolean previous = PREVIOUS_KEYS.remove(name);
                if (previous != null) mapping.setDown(previous);
            }
        }
        return true;
    }

    /** Safe from any thread, including an interrupted script's finally block. */
    public static void releaseKeys(Interpreter it) {
        Runnable release = () -> {
            List<String> keys;
            synchronized (ScriptGame.class) { keys = new ArrayList<>(HELD_KEYS.getOrDefault(it, Set.of())); }
            for (String name : keys) setKey(it, name, false);
        };
        if (mc().isSameThread()) release.run(); else mc().execute(release);
    }

    private static boolean eat(Interpreter it, InteractionHand requested) {
        int[] selected = player(p -> {
            if (p.isUsingItem() || ScriptInventory.busy() || ScriptMeteor.enabled("meteor", "auto-eat") || mc().gameMode == null || mc().gui.screen() != null) return null;
            int previous = p.getInventory().getSelectedSlot();
            if (requested != null) {
                var stack = p.getItemInHand(requested);
                var food = stack.get(DataComponents.FOOD);
                return food != null && (p.getFoodData().getFoodLevel() < 20 || food.canAlwaysEat()) ? new int[] {previous, previous, stack.getCount(), p.getFoodData().getFoodLevel()} : null;
            }
            for (int slot = 0; slot < 9; slot++) {
                var stack = p.getInventory().getItem(slot); var food = stack.get(DataComponents.FOOD);
                if (food != null && (p.getFoodData().getFoodLevel() < 20 || food.canAlwaysEat())) return new int[] {slot, previous, stack.getCount(), p.getFoodData().getFoodLevel()};
            }
            return null;
        });
        if (selected == null) return false;
        InteractionHand hand = requested == null ? InteractionHand.MAIN_HAND : requested;
        boolean[] acquiredUse = {false};
        try {
            boolean started = player(p -> {
                if (p.isUsingItem() || ScriptInventory.busy() || mc().gameMode == null) return false;
                DihInventoryHelper.selectHotbarSlot(mc(), selected[0]);
                boolean accepted = mc().gameMode.useItem(p, hand).consumesAction();
                if (accepted) {
                    synchronized (ScriptGame.class) {
                        if (!HELD_KEYS.getOrDefault(it, Set.of()).contains("use")) {
                            setKey(it, "use", true);
                            acquiredUse[0] = true;
                        }
                    }
                }
                return accepted;
            });
            if (!started) return false;
            boolean observed = false;
            for (int ticks = 0; ticks < 80; ticks++) {
                it.waitTicks(1);
                boolean using = player(LocalPlayer::isUsingItem);
                observed |= using;
                if (!using && (observed || ticks > 10)) break;
            }
            return player(p -> p.getItemInHand(hand).getCount() < selected[2] || p.getFoodData().getFoodLevel() > selected[3]);
        } finally {
            mc().execute(() -> {
                if (acquiredUse[0]) setKey(it, "use", false);
                if (mc().player == null || mc().gameMode == null) return;
                if (mc().player.isUsingItem() && mc().player.getUsedItemHand() == hand) mc().gameMode.releaseUsingItem(mc().player);
                DihInventoryHelper.selectHotbarSlot(mc(), selected[1]);
            });
        }
    }

    /** Capture on the client thread before disconnect/death discards the level. */
    public static Map<String, Object> snapshot() {
        LocalPlayer p = mc().player;
        if (p == null || mc().level == null) return new LinkedHashMap<>();
        Map<String, Object> out = vector(p.position());
        out.put("dimension", p.level().dimension().identifier().toString());
        out.put("health", (double) p.getHealth()); out.put("alive", p.isAlive());
        out.put("onGround", p.onGround()); out.put("inWater", p.isInWater()); out.put("submerged", p.isUnderWater());
        out.put("inLava", p.isInLava()); out.put("onFire", p.isOnFire()); out.put("air", (double) p.getAirSupply());
        out.put("fallDistance", (double) p.fallDistance); out.put("velocity", vector(p.getDeltaMovement()));
        List<Object> nearby = new ArrayList<>();
        for (Entity entity : p.level().getEntities(p, p.getBoundingBox().inflate(16))) if (nearby.size() < 64) nearby.add(entityInfo(p, entity));
        out.put("nearbyEntities", nearby);
        return out;
    }
}
