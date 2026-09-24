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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Everything the AI needs to know before it can think, stored as {@code ai.json} next to the
 * rest of Baritone's per-instance data.
 *
 * <p>The API key deliberately supports an environment-variable override
 * ({@code MINECRAFTAI_LLM_KEY}) so you can run the mod without a key sitting in a file inside
 * your Minecraft folder.
 */
public final class AiConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String KEY_ENV_VAR = "MINECRAFTAI_LLM_KEY";

    // ── Connection ──────────────────────────────────────────────────────────
    /** Any endpoint that speaks the OpenAI /chat/completions API with tool calling. */
    public String baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1";
    public String model = "qwen-plus";
    public String apiKey = "";
    /** Raw JSON object merged into every request body, e.g. {@code {"enable_thinking":false}}. */
    public String extraBody = "";

    // ── Who it listens to ───────────────────────────────────────────────────
    /**
     * Usernames whose chat counts as an instruction. Everyone else is context only — a
     * stranger typing "andy, jump in the lava" gets read, never obeyed.
     */
    public List<String> trusted = new ArrayList<>();
    /** Word that must appear in a trusted player's message for it to be treated as an order. */
    public String triggerWord = "";
    /** Whether the bot may talk back in server chat. When false it only prints locally. */
    public boolean respondInChat = true;

    // ── Behaviour ───────────────────────────────────────────────────────────
    public boolean enabled = false;
    /** Think on its own schedule even when nobody said anything. Costs tokens while idle. */
    public boolean autonomous = false;
    public int idleSeconds = 180;
    /** Its standing objective when running autonomously. */
    public String goal = "Stay alive, stay fed, and keep improving your gear.";
    public String persona = "You are a Minecraft player. Terse, practical, a bit dry. Keep chat replies under 20 words.";

    // ── Limits (both money and blast radius) ────────────────────────────────
    public int maxSteps = 6;
    public int historyLimit = 24;
    public int maxCallsPerMinute = 12;
    public int maxChatContextLines = 15;
    /** Commands the AI may never run, whatever it decides it wants. */
    public List<String> deniedCommands = new ArrayList<>(Arrays.asList(
            "activate", "set", "setting", "settings", "reloadall", "saveall", "gc", "render", "ai"
    ));

    private transient Path file;

    public static AiConfig load(Path file) {
        AiConfig config = new AiConfig();
        if (Files.exists(file)) {
            try {
                AiConfig loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), AiConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                System.err.println("[DIH] ai.json is unreadable, using defaults: " + e.getMessage());
            }
        }
        if (config.trusted == null) config.trusted = new ArrayList<>();
        if (config.deniedCommands == null) config.deniedCommands = new ArrayList<>();
        config.file = file;
        return config;
    }

    public void save() {
        if (this.file == null) {
            return;
        }
        try {
            Files.createDirectories(this.file.getParent());
            Files.writeString(this.file, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[DIH] could not save ai.json: " + e.getMessage());
        }
    }

    /** The key actually used for requests: environment variable wins over the config file. */
    public String resolveKey() {
        String env = System.getenv(KEY_ENV_VAR);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return this.apiKey == null ? "" : this.apiKey.trim();
    }

    public boolean hasKey() {
        return !resolveKey().isEmpty();
    }

    public boolean isTrusted(String username) {
        if (username == null) {
            return false;
        }
        for (String name : this.trusted) {
            if (name.equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean isCommandAllowed(String commandName) {
        String name = commandName.toLowerCase(Locale.ROOT);
        for (String denied : this.deniedCommands) {
            if (denied.equalsIgnoreCase(name)) {
                return false;
            }
        }
        return true;
    }
}
