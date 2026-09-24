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

package baritone.command.defaults;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.WorldOptions;
import dihclient.util.DihWaypoints;
import dihclient.util.oresim.DihOreSimSeedInput;
import dihclient.util.oresim.DihOreSimSeedStore;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.OptionalLong;

/**
 * The world seed used by every seed-based feature ({@code #seedmap}, {@code #structure},
 * {@code #where}) — stored <b>per server / per singleplayer world</b> in the same store OreSim uses,
 * so a seed entered once works for all of them and switching servers never reuses the wrong seed.
 *
 * <p>Seeds can be verified: the server sends a SHA-256 hash of its seed at login (it drives biome
 * blending), and {@link #check(long)} compares a candidate against it exactly.
 *
 * <p>Singleplayer needs none of this — the integrated server's seed is read directly.
 */
public final class ClientStructureFinder {

    /** Pre-per-server builds kept one global seed here; still read as a last resort. */
    private static final String LEGACY_FILE = "baritone/seed.txt";

    private ClientStructureFinder() {}

    // -------------------------------------------------------------------------
    // Storage (per scope)
    // -------------------------------------------------------------------------

    /** The seed stored for the server/world you're on, if any. */
    public static OptionalLong stored() {
        String raw = DihOreSimSeedStore.get().value(scope(), legacySeed());
        DihOreSimSeedInput.Result parsed = DihOreSimSeedInput.parse(raw);
        return parsed.isValid() ? OptionalLong.of(parsed.value()) : OptionalLong.empty();
    }

    public static boolean hasSeed() {
        return stored().isPresent();
    }

    /** @throws IllegalStateException if none is stored — check {@link #hasSeed()} first. */
    public static long getSeed() {
        return stored().orElseThrow(() -> new IllegalStateException("No seed stored for " + scope()));
    }

    public static void setSeed(long seed) {
        DihOreSimSeedStore.get().put(scope(), Long.toString(seed));
    }

    public static void clearSeed() {
        DihOreSimSeedStore.get().put(scope(), "");
    }

    /** Human-readable name of where the seed is stored (server address or world name). */
    public static String scope() {
        return DihWaypoints.scopeKey(Minecraft.getInstance());
    }

    // -------------------------------------------------------------------------
    // Parsing & verification
    // -------------------------------------------------------------------------

    /**
     * Parses a seed exactly like the "Seed for the world generator" box: numbers are used as-is,
     * any other text becomes its {@code String.hashCode()}.
     */
    public static OptionalLong parse(String input) {
        if (input == null || input.isBlank()) return OptionalLong.empty();
        return WorldOptions.parseSeed(input.trim());
    }

    public enum Verdict {
        /** Candidate hashes to exactly what the server sent. */
        MATCH,
        /** Server's hash is known and differs — this is not the server's seed. */
        MISMATCH,
        /** Not in a world, or the hash couldn't be read. */
        UNKNOWN
    }

    /** Compares {@code seed} with the seed hash the current server sent at login. */
    public static Verdict check(long seed) {
        Long hash = serverSeedHash();
        if (hash == null) return Verdict.UNKNOWN;
        return BiomeManager.obfuscateSeed(seed) == hash ? Verdict.MATCH : Verdict.MISMATCH;
    }

    /**
     * The hashed seed the server sent (vanilla's {@code BiomeManager.biomeZoomSeed}), or
     * {@code null} if unavailable. MC 26.2 is unobfuscated, so the field name is stable.
     */
    public static Long serverSeedHash() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            BiomeManager manager = mc.level.getBiomeManager();
            Field f = BiomeManager.class.getDeclaredField("biomeZoomSeed");
            f.setAccessible(true);
            return f.getLong(manager);
        } catch (Throwable t) {
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Legacy global seed (read-only migration)
    // -------------------------------------------------------------------------

    /**
     * The old global seed, offered to the per-scope store only on first use of a scope. It is
     * ignored when the server's seed hash proves it belongs to a different world.
     */
    private static String legacySeed() {
        try {
            File f = new File(Minecraft.getInstance().gameDirectory, LEGACY_FILE);
            if (!f.exists()) return null;
            String content = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            long seed = Long.parseLong(content);
            return check(seed) == Verdict.MISMATCH ? null : content;
        } catch (Exception ignored) {
            return null;
        }
    }
}
