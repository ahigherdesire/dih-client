/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.cache;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Path;

/**
 * Identifies the server / singleplayer world the player is currently in, so the
 * persistent memory stores ({@link ChestMemory}, {@link HomeMemory},
 * {@link PlayerMemory}) can scope their records to it.
 *
 * <p>Without scoping, every store dumps records from every world into one global
 * file and they bleed across servers and saves. Mirrors how {@code WorldProvider}
 * picks per-world cache directories: singleplayer → save-folder name, multiplayer
 * → server address.
 */
public final class WorldScope {

    private WorldScope() {}

    /** Stable identifier for the world/server the player is currently in. */
    public static String currentWorldKey() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
                Path name = root.getFileName();
                return "sp:" + (name == null ? root.toString() : name.toString());
            }
            ServerData sd = mc.getCurrentServer();
            if (sd != null) {
                return "mp:" + (sd.isRealm() ? "realms" : sd.ip);
            }
        } catch (Exception ignored) {}
        return "unknown";
    }
}
