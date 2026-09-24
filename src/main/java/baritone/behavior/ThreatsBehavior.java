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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.api.utils.Helper;
import baritone.cache.PlayerMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-tick player scanner.
 *
 * <p>Two roles in one behavior:
 * <ol>
 *   <li><b>Player memory</b> — every tick, every visible non-self player is
 *       silently recorded into {@link PlayerMemory} regardless of whether
 *       threat alerting is on.</li>
 *   <li><b>Threat alerts</b> — when {@link #threatsEnabled} is {@code true},
 *       fires an in-chat warning the first time any player enters within
 *       {@link #threatsRadius} blocks (3D). Re-alerts after a
 *       {@value #ALERT_COOLDOWN_MS}-ms cooldown if the player stays in range.
 *       Cooldown resets if the player leaves render distance and comes back.</li>
 * </ol>
 *
 * <p>Both static fields are written by {@link baritone.command.defaults.ThreatsCommand}.
 */
public final class ThreatsBehavior extends Behavior implements AbstractGameEventListener {

    // ── Static config — written by ThreatsCommand ─────────────────────────────

    /** Whether threat alerting is active. Written by {@code #threats on/off}. */
    public static volatile boolean threatsEnabled = false;

    /** Alert radius in blocks (3D Euclidean). Default 64. */
    public static volatile int threatsRadius = 64;

    // ── Per-session state ─────────────────────────────────────────────────────

    /**
     * UUID → timestamp of the last alert fired for that player.
     * Reset when the player leaves render distance, or on world unload.
     */
    private final Map<UUID, Long> lastAlertTime = new HashMap<>();

    /** UUIDs of players currently tracked as in-range. Cleared on world unload. */
    private final Set<UUID> inRange = new HashSet<>();

    /** Previous value of {@link #threatsEnabled} — used for enable/disable announcements. */
    private boolean prevEnabled = false;

    /** Cooldown between repeated alerts for the same player (ms). */
    private static final long ALERT_COOLDOWN_MS = 30_000L;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ThreatsBehavior(Baritone baritone) {
        super(baritone);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            // World unloading — discard any in-session state
            lastAlertTime.clear();
            inRange.clear();
            prevEnabled = false;
            return;
        }
        if (ctx.player() == null || ctx.world() == null) return;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;

        // ── Enable/disable transition announcements ────────────────────────────
        boolean curEnabled = threatsEnabled;
        if (curEnabled && !prevEnabled) {
            log("Threat detection ON — alert radius " + threatsRadius
                    + " blocks. Type #threats off to disable.");
            inRange.clear();
            lastAlertTime.clear();
        } else if (!curEnabled && prevEnabled) {
            log("Threat detection OFF.");
        }
        prevEnabled = curEnabled;

        UUID selfUUID = ctx.player().getUUID();
        long now = System.currentTimeMillis();
        Set<UUID> seenThisTick = new HashSet<>();

        for (AbstractClientPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            if (uuid.equals(selfUUID)) continue; // skip ourselves
            seenThisTick.add(uuid);

            // ── Always record to PlayerMemory ────────────────────────────────
            String name  = player.getName().getString();
            BlockPos pos = player.blockPosition();
            String dim   = level.dimension().identifier().toString();
            PlayerMemory.updatePlayer(uuid, name, pos, dim);

            // ── Threat alerting ──────────────────────────────────────────────
            if (!curEnabled) continue;

            double dx    = player.getX() - ctx.player().getX();
            double dy    = player.getY() - ctx.player().getY();
            double dz    = player.getZ() - ctx.player().getZ();
            double dist3d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double distH  = Math.sqrt(dx * dx + dz * dz);

            if (dist3d <= threatsRadius) {
                inRange.add(uuid);
                Long last = lastAlertTime.get(uuid);
                if (last == null || (now - last) >= ALERT_COOLDOWN_MS) {
                    lastAlertTime.put(uuid, now);
                    log("⚠ PLAYER NEARBY: " + name
                            + "  X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ()
                            + "  (~" + (int) distH + " blocks)  [" + dimShort(dim) + "]");
                }
            } else {
                // Player left range — clear cooldown so we alert again if they return
                inRange.remove(uuid);
                lastAlertTime.remove(uuid);
            }
        }

        // ── Prune players who left render distance ────────────────────────────
        inRange.retainAll(seenThisTick);
        lastAlertTime.keySet().retainAll(seenThisTick);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String dimShort(String dim) {
        if (dim == null) return "?";
        if (dim.contains("overworld")) return "overworld";
        if (dim.contains("nether"))    return "nether";
        if (dim.contains("end"))       return "end";
        int c = dim.lastIndexOf(':');
        return c < 0 ? dim : dim.substring(c + 1);
    }

    private void log(String msg) {
        Helper.HELPER.logDirect("[Threats] " + msg);
    }
}
