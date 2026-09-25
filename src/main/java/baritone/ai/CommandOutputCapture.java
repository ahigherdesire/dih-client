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

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tees Baritone's chat logger while a command runs, so {@code run_command} can show the model
 * what the command printed: its errors ("no block found by that id"), its results ({@code #find}
 * coordinates) and the first thing the job it started says. Without this the model only learned
 * "Ran #x" and had to guess.
 *
 * <p>Install and uninstall on the game thread. The tee always forwards to the real logger, so the
 * player sees exactly what they saw before.
 */
final class CommandOutputCapture implements Consumer<Component> {

    static final int MAX_LINES = 8;
    static final int MAX_CHARS = 600;
    private static final int MAX_STORED = 40;

    private final List<String> lines = new ArrayList<>();
    private Settings.Setting<Consumer<Component>> setting;
    private Consumer<Component> original;
    private volatile boolean open;

    void install() {
        this.setting = BaritoneAPI.getSettings().logger;
        this.original = this.setting.value;
        this.setting.value = this;
        this.open = true;
    }

    void uninstall() {
        this.open = false;
        // Only unhook if nobody replaced the logger meanwhile; otherwise stay as a pass-through.
        if (this.setting != null && this.setting.value == this) {
            this.setting.value = this.original;
        }
    }

    @Override
    public void accept(Component message) {
        if (this.open && message != null) {
            try {
                synchronized (this.lines) {
                    if (this.lines.size() < MAX_STORED) {
                        this.lines.add(message.getString());
                    }
                }
            } catch (Throwable ignored) {
                // Capturing is best effort; the real logger below must still run.
            }
        }
        if (this.original != null) {
            this.original.accept(message);
        }
    }

    /** What was printed, condensed to one line for the model, or "" if nothing. */
    String summary() {
        synchronized (this.lines) {
            return summarize(this.lines, MAX_LINES, MAX_CHARS);
        }
    }

    /** Drops the "[Baritone]" prefix and blank or repeated lines, joins with " | " and caps the length. */
    static String summarize(List<String> raw, int maxLines, int maxChars) {
        List<String> kept = new ArrayList<>();
        String previous = null;
        int skipped = 0;
        for (String line : raw) {
            String text = stripPrefix(line);
            if (text.isEmpty() || text.equals(previous)) {
                continue;
            }
            previous = text;
            if (kept.size() >= maxLines) {
                skipped++;
                continue;
            }
            kept.add(text);
        }
        if (kept.isEmpty()) {
            return "";
        }
        String joined = String.join(" | ", kept);
        if (joined.length() > maxChars) {
            joined = joined.substring(0, maxChars) + "…";
        }
        return skipped > 0 ? joined + " (+" + skipped + " more lines)" : joined;
    }

    static String stripPrefix(String line) {
        if (line == null) {
            return "";
        }
        String text = line.replaceAll("\\s+", " ").trim();
        return text.replaceFirst("^\\[(Baritone|Baritoe|B)]\\s*", "").trim();
    }
}
