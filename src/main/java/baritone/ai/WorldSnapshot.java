/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.ai;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.process.IBaritoneProcess;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the live game state into a paragraph of text. This is the AI's entire sense of the
 * world, so it aims for the things a player glances at — where am I, am I hurt, is it dark,
 * what's near me, what am I already doing — and nothing else, because every line costs tokens.
 *
 * <p>Must be built on the game thread.
 */
public final class WorldSnapshot {

    private WorldSnapshot() {}

    public static String describe(Baritone baritone) {
        IPlayerContext ctx = baritone.getPlayerContext();
        Player player = ctx.player();
        Level world = ctx.world();
        if (player == null || world == null) {
            return "Not in a world right now.";
        }

        StringBuilder sb = new StringBuilder();
        BetterBlockPos feet = ctx.playerFeet();

        sb.append("Position: ").append(feet.x).append(' ').append(feet.y).append(' ').append(feet.z)
                .append(" in ").append(dimensionName(world)).append('\n');
        sb.append("Health: ").append(Math.round(player.getHealth())).append("/20")
                .append("   Food: ").append(player.getFoodData().getFoodLevel()).append("/20")
                .append("   Time: ").append(timeOfDay(world))
                .append(world.isRaining() ? " (raining)" : "").append('\n');
        sb.append("Holding: ").append(itemName(player.getMainHandItem())).append('\n');
        sb.append("Inventory: ").append(inventory(player)).append('\n');
        sb.append("Nearby players: ").append(nearbyPlayers(ctx)).append('\n');
        sb.append("Nearby hostiles: ").append(nearbyHostiles(ctx)).append('\n');
        sb.append("Currently: ").append(activity(baritone));
        return sb.toString();
    }

    /** One-line version, cheap enough to append after every command. */
    public static String brief(Baritone baritone) {
        IPlayerContext ctx = baritone.getPlayerContext();
        Player player = ctx.player();
        if (player == null) {
            return "not in a world";
        }
        BetterBlockPos feet = ctx.playerFeet();
        return "at " + feet.x + " " + feet.y + " " + feet.z
                + ", hp " + Math.round(player.getHealth()) + "/20"
                + ", food " + player.getFoodData().getFoodLevel() + "/20"
                + ", " + activity(baritone);
    }

    private static String activity(Baritone baritone) {
        StringBuilder sb = new StringBuilder();
        IBaritoneProcess process = baritone.getPathingControlManager().mostRecentInControl().orElse(null);
        if (process == null) {
            sb.append("idle");
        } else {
            sb.append("running ").append(process.displayName());
        }
        if (baritone.getPathingBehavior().isPathing()) {
            sb.append(", pathing");
            Goal goal = baritone.getPathingBehavior().getGoal();
            if (goal != null) {
                sb.append(" toward ").append(goal);
            }
        }
        return sb.toString();
    }

    private static String dimensionName(Level world) {
        try {
            return world.dimension().identifier().getPath();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String timeOfDay(Level world) {
        long time = world.getOverworldClockTime() % 24000L;
        String label;
        if (time < 6000) {
            label = "morning";
        } else if (time < 12000) {
            label = "midday";
        } else if (time < 13000) {
            label = "sunset";
        } else if (time < 23000) {
            label = "NIGHT (mobs spawning)";
        } else {
            label = "dawn";
        }
        return label + " (" + time + ")";
    }

    private static String itemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "nothing";
        }
        try {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return stack.getHoverName().getString();
        }
    }

    private static String inventory(Player player) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                counts.merge(itemName(stack), stack.getCount(), Integer::sum);
            }
        } catch (Exception e) {
            return "unreadable";
        }
        if (counts.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return sb.toString();
    }

    private static String nearbyPlayers(IPlayerContext ctx) {
        List<String> found = new ArrayList<>();
        try {
            for (Entity entity : ctx.entities()) {
                if (!(entity instanceof Player other) || other == ctx.player()) {
                    continue;
                }
                double distance = other.distanceTo(ctx.player());
                if (distance <= 96) {
                    found.add(other.getGameProfile().name() + " " + Math.round(distance) + "m");
                }
            }
        } catch (Exception ignored) {
            // Entity list can change under us; a partial answer is fine.
        }
        return found.isEmpty() ? "none" : String.join(", ", found);
    }

    private static String nearbyHostiles(IPlayerContext ctx) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        String nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        try {
            for (Entity entity : ctx.entities()) {
                if (!(entity instanceof Monster monster) || !((LivingEntity) monster).isAlive()) {
                    continue;
                }
                double distance = monster.distanceTo(ctx.player());
                if (distance > 32) {
                    continue;
                }
                String name = monster.getType().toShortString();
                counts.merge(name, 1, Integer::sum);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = name;
                }
            }
        } catch (Exception ignored) {
        }
        if (counts.isEmpty()) {
            return "none within 32m";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        sb.append(" (nearest ").append(nearest).append(" at ").append(Math.round(nearestDistance)).append("m)");
        return sb.toString();
    }
}
