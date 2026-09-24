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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The handful of things the AI should still know after a relog. Deliberately a flat list of
 * short strings — small enough to paste into every prompt, simple enough to edit by hand.
 */
public final class AiMemory {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_FACTS = 40;

    private final Path file;
    private final List<String> facts;

    public AiMemory(Path file) {
        this.file = file;
        List<String> loaded = new ArrayList<>();
        if (Files.exists(file)) {
            try {
                List<String> parsed = GSON.fromJson(
                        Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<List<String>>() {}.getType()
                );
                if (parsed != null) {
                    loaded = parsed;
                }
            } catch (Exception e) {
                System.err.println("[DIH] ai_memory.json unreadable: " + e.getMessage());
            }
        }
        this.facts = loaded;
    }

    public synchronized String remember(String fact) {
        String clean = fact == null ? "" : fact.trim();
        if (clean.isEmpty()) {
            return "Nothing to remember.";
        }
        if (clean.length() > 180) {
            clean = clean.substring(0, 180);
        }
        if (this.facts.contains(clean)) {
            return "Already remembered.";
        }
        this.facts.add(clean);
        while (this.facts.size() > MAX_FACTS) {
            this.facts.remove(0);
        }
        save();
        return "Remembered: " + clean;
    }

    public synchronized String forget(String needle) {
        int before = this.facts.size();
        String lower = needle.toLowerCase();
        this.facts.removeIf(f -> f.toLowerCase().contains(lower));
        save();
        int removed = before - this.facts.size();
        return removed == 0 ? "No matching memory." : "Forgot " + removed + " memory item(s).";
    }

    public synchronized void clear() {
        this.facts.clear();
        save();
    }

    public synchronized List<String> all() {
        return Collections.unmodifiableList(new ArrayList<>(this.facts));
    }

    public synchronized String digest() {
        if (this.facts.isEmpty()) {
            return "(nothing remembered yet)";
        }
        StringBuilder sb = new StringBuilder();
        for (String fact : this.facts) {
            sb.append("- ").append(fact).append('\n');
        }
        return sb.toString().trim();
    }

    private void save() {
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, GSON.toJson(this.facts), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[DIH] could not save ai_memory.json: " + e.getMessage());
        }
    }
}
