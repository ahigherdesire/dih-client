package baritone.ai.director;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import baritone.guardian.GuardianProcess;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the planner reads about the world, as compact JSON: where, how healthy, what's worn and held (with durability),
 * what's carried (the biggest stacks when there are many kinds), threats, players and the running job.
 */
public final class StateJson {

    static final int MAX_INVENTORY_KINDS = 24;

    private StateJson() {
    }

    /** Game thread. */
    public static JsonObject build(Baritone baritone) {
        JsonObject out = new JsonObject();
        IPlayerContext ctx = baritone.getPlayerContext();
        Player player = ctx.player();
        Level level = ctx.world();
        if (player == null || level == null) {
            out.addProperty("in_world", false);
            return out;
        }
        BetterBlockPos feet = ctx.playerFeet();
        JsonArray position = new JsonArray();
        position.add(feet.x);
        position.add(feet.y);
        position.add(feet.z);
        out.add("position", position);
        out.addProperty("dimension", level.dimension().identifier().getPath());
        level.getBiome(feet).unwrapKey().ifPresent(key -> out.addProperty("biome", key.identifier().getPath()));
        long time = level.getOverworldClockTime() % 24000L;
        out.addProperty("time", time < 12000 ? "day" : time < 13000 ? "sunset" : time < 23000 ? "night" : "dawn");
        out.addProperty("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");
        out.addProperty("health", Math.round(player.getHealth()));
        out.addProperty("food", player.getFoodData().getFoodLevel());

        JsonObject armor = new JsonObject();
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) armor.addProperty(id(stack), durabilityOr(stack, "worn"));
        }
        out.add("armor", armor);
        ItemStack held = player.getMainHandItem();
        out.addProperty("holding", held.isEmpty() ? "nothing" : id(held)
                + (held.isDamageableItem() ? " (" + durability(held.getDamageValue(), held.getMaxDamage()) + ")" : ""));

        Map<String, Integer> counts = new LinkedHashMap<>();
        int free = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) {
                free++;
            } else {
                counts.merge(id(stack), stack.getCount(), Integer::sum);
            }
        }
        out.add("inventory", inventory(counts, MAX_INVENTORY_KINDS));
        out.addProperty("free_slots", free);

        List<String> threats = new ArrayList<>();
        List<String> players = new ArrayList<>();
        for (Entity entity : ctx.entities()) {
            double distance = entity.distanceTo(player);
            if (entity instanceof Monster monster && ((LivingEntity) monster).isAlive() && distance <= 24) {
                threats.add(monster.getType().toShortString() + " " + Math.round(distance) + "m");
            } else if (entity instanceof Player other && other != player && distance <= 96) {
                players.add(other.getGameProfile().name() + " " + Math.round(distance) + "m");
            }
        }
        out.add("threats", strings(threats, 8));
        out.add("players", strings(players, 8));

        IBaritoneProcess process = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        out.addProperty("job", process == null ? "idle" : process.displayName());
        AcquireControl acquire = AcquireControl.get();
        if (acquire != null && acquire.isActive()) out.addProperty("acquire", acquire.status());
        GuardianProcess guardian = baritone.getGuardianProcess();
        if (guardian != null) out.addProperty("guardian", guardian.status());
        return out;
    }

    /** The biggest {@code max} stacks by count; the rest as "N more kinds". */
    static JsonObject inventory(Map<String, Integer> counts, int max) {
        JsonObject out = new JsonObject();
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()));
        for (int i = 0; i < Math.min(max, sorted.size()); i++) {
            out.addProperty(sorted.get(i).getKey(), sorted.get(i).getValue());
        }
        if (sorted.size() > max) out.addProperty("more", (sorted.size() - max) + " more kinds");
        return out;
    }

    /** "200/250" uses left, or null for things that don't wear out. */
    static String durability(int damage, int maxDamage) {
        return maxDamage <= 0 ? null : (maxDamage - damage) + "/" + maxDamage;
    }

    private static String durabilityOr(ItemStack stack, String fallback) {
        String text = stack.isDamageableItem() ? durability(stack.getDamageValue(), stack.getMaxDamage()) : null;
        return text == null ? fallback : text;
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    private static JsonArray strings(List<String> list, int max) {
        JsonArray out = new JsonArray();
        list.stream().limit(max).forEach(out::add);
        return out;
    }
}
