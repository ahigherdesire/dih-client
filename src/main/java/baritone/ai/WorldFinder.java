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
import baritone.api.BaritoneAPI;
import baritone.api.utils.BetterBlockPos;
import baritone.api.utils.IPlayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The {@code find} tool: where is the nearest block, mob or player of a kind, as distance and
 * compass direction. Read-only. Blocks come from the loaded chunks plus Baritone's block cache
 * (which remembers ores and containers it has seen), so it can only find what the client has
 * actually received.
 */
final class WorldFinder {

    static final int SCAN_RADIUS_CHUNKS = 8;
    static final int MAX_SCAN = 256;
    static final int SHOW = 3;

    private WorldFinder() {}

    /** Must run on the game thread. */
    static String find(Baritone baritone, String rawTarget) {
        List<String> ids = FindQuery.candidates(rawTarget);
        if (ids.isEmpty()) {
            return "Say what to look for: a block (diamond_ore, chest), a mob (cow, zombie) or a player name.";
        }
        IPlayerContext ctx = baritone.getPlayerContext();
        Player self = ctx.player();
        if (self == null || ctx.world() == null) {
            return "Not in a world.";
        }
        BetterBlockPos origin = ctx.playerFeet();

        String player = findPlayer(ctx, rawTarget.trim(), origin);
        if (player != null) {
            return player;
        }

        List<String> sections = new ArrayList<>();
        List<Block> blocks = resolveBlocks(ids);
        if (!blocks.isEmpty()) {
            sections.add(findBlocks(ctx, blocks, origin));
        }
        EntityType<?> type = resolveEntity(ids);
        if (type != null) {
            sections.add(findEntities(ctx, type, origin));
        }
        if (sections.isEmpty()) {
            return "\"" + rawTarget.trim() + "\" is not a block id, mob id or nearby player. "
                    + "Use ids like diamond_ore, oak_log, chest, cow, zombie.";
        }
        return String.join("\n", sections);
    }

    // ── Lookups ─────────────────────────────────────────────────────────────

    private static List<Block> resolveBlocks(List<String> ids) {
        for (String id : ids) {
            Block block = block(id);
            if (block == null) {
                continue;
            }
            List<Block> blocks = new ArrayList<>();
            blocks.add(block);
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            for (String variant : FindQuery.oreVariants(path)) {
                Block other = block(variant);
                if (other != null && !blocks.contains(other)) {
                    blocks.add(other);
                }
            }
            return blocks;
        }
        return List.of();
    }

    private static Block block(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
        return block == null || block.defaultBlockState().isAir() ? null : block;
    }

    private static EntityType<?> resolveEntity(List<String> ids) {
        for (String id : ids) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier == null) {
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(identifier).orElse(null);
            if (type != null && type != EntityTypes.PLAYER) {
                return type;
            }
        }
        return null;
    }

    private static String findPlayer(IPlayerContext ctx, String name, BetterBlockPos origin) {
        if (name.isEmpty() || name.contains(":") || name.contains(" ")) {
            return null;
        }
        try {
            for (Entity entity : ctx.entities()) {
                if (entity instanceof Player other && other != ctx.player()
                        && other.getGameProfile().name().equalsIgnoreCase(name)) {
                    return "Player " + other.getGameProfile().name() + ": " + where(origin, entity.blockPosition()) + ".";
                }
            }
        } catch (Exception ignored) {
            // Entity list changed under us; fall through to block and mob lookups.
        }
        return null;
    }

    private static String findBlocks(IPlayerContext ctx, List<Block> blocks, BetterBlockPos origin) {
        String name = BuiltInRegistries.BLOCK.getKey(blocks.get(0)).getPath();
        Set<BlockPos> found = new LinkedHashSet<>();
        try {
            found.addAll(BaritoneAPI.getProvider().getWorldScanner()
                    .scanChunkRadius(ctx, blocks, MAX_SCAN, 0, SCAN_RADIUS_CHUNKS));
        } catch (Throwable ignored) {
            // A failed scan still leaves the cache.
        }
        for (Block block : blocks) {
            try {
                for (BlockPos pos : ctx.worldData().getCachedWorld().getLocationsOf(
                        BuiltInRegistries.BLOCK.getKey(block).getPath(), MAX_SCAN, origin.x, origin.z, 4)) {
                    // The cache can be stale; where the chunk is loaded, believe the chunk.
                    boolean loaded = ctx.world().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    if (!loaded || blocks.contains(ctx.world().getBlockState(pos).getBlock())) {
                        found.add(pos.immutable());
                    }
                }
            } catch (Throwable ignored) {
                // No cache for this world yet.
            }
        }
        if (found.isEmpty()) {
            return "No " + name + " found in loaded chunks (about " + SCAN_RADIUS_CHUNKS * 16
                    + " blocks around you) or in Baritone's cache. It can only find blocks the client has seen.";
        }
        List<BlockPos> sorted = new ArrayList<>(found);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(SHOW, sorted.size()); i++) {
            shown.add(where(origin, sorted.get(i)));
        }
        return "Nearest " + name + ": " + String.join("; ", shown) + ". " + sorted.size() + " known"
                + (blocks.size() > 1 ? " (including " + otherNames(blocks) + ")" : "") + ".";
    }

    private static String findEntities(IPlayerContext ctx, EntityType<?> type, BetterBlockPos origin) {
        String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
        List<Entity> matches = new ArrayList<>();
        try {
            for (Entity entity : ctx.entities()) {
                if (entity.getType() == type && entity.isAlive() && entity != ctx.player()) {
                    matches.add(entity);
                }
            }
        } catch (Exception ignored) {
            // Partial list is fine.
        }
        if (matches.isEmpty()) {
            return "No " + name + " within loaded range right now.";
        }
        matches.sort(Comparator.comparingDouble(entity -> entity.blockPosition().distSqr(origin)));
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < Math.min(SHOW, matches.size()); i++) {
            shown.add(where(origin, matches.get(i).blockPosition()));
        }
        return "Nearest " + name + ": " + String.join("; ", shown) + ". " + matches.size() + " within loaded range.";
    }

    private static String otherNames(List<Block> blocks) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i < blocks.size(); i++) {
            names.add(BuiltInRegistries.BLOCK.getKey(blocks.get(i)).getPath());
        }
        return String.join(", ", names);
    }

    private static String where(BetterBlockPos origin, BlockPos pos) {
        return FindQuery.relative(pos.getX() - origin.x, pos.getY() - origin.y, pos.getZ() - origin.z)
                + " (" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ")";
    }
}
