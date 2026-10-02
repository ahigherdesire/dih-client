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

import baritone.Baritone;
import baritone.ai.AiBrain;
import baritone.ai.AiConfig;
import baritone.ai.AiProviders;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.behavior.AiBehavior;
import net.minecraft.ChatFormatting;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * {@code #ai} — the whole control surface for the language-model brain.
 */
public class AiCommand extends Command {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "on", "off", "status", "key", "provider", "url", "model", "extra", "trust", "untrust",
            "trigger", "goal", "persona", "chat", "auto", "followups", "forget", "clear", "deny", "allow"
    );

    public AiCommand(IBaritone baritone) {
        super(baritone, "ai");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        AiBehavior behavior = ((Baritone) this.baritone).getAiBehavior();
        AiConfig config = behavior.getConfig();

        if (!args.hasAny()) {
            status(behavior, config);
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "on": {
                config.enabled = true;
                config.save();
                logDirect("AI enabled." + (config.hasKey() ? "" : " No API key set yet — use #ai key <key>."), ChatFormatting.GREEN);
                return;
            }
            case "off": {
                config.enabled = false;
                config.save();
                logDirect("AI disabled.", ChatFormatting.YELLOW);
                return;
            }
            case "status": {
                status(behavior, config);
                return;
            }
            case "key": {
                String key = args.rawRest().trim();
                if (key.isEmpty()) {
                    logDirect("Usage: #ai key <your-api-key>   (or set the " + AiConfig.KEY_ENV_VAR + " environment variable)");
                    return;
                }
                config.apiKey = key;
                // A key says whose it is ("gsk_" is Groq's): sent to the default Qwen URL it only gets a 401.
                AiProviders.Provider owner = AiProviders.keyMismatch(key, config.baseUrl);
                boolean switched = owner != null && AiProviders.forUrl(config.baseUrl) != null;
                if (switched) use(config, owner);
                config.save();
                logDirect("Key saved to ai.json (" + mask(key) + ").", ChatFormatting.GREEN);
                if (switched) {
                    logDirect("That's " + owner.name() + "'s key: switched to " + owner.name() + ", model " + config.model
                            + ". #ai model <name> to change it.", ChatFormatting.GREEN);
                } else if (owner != null) {
                    logDirect("That looks like " + owner.name() + "'s key, but requests go to " + config.baseUrl
                            + ". #ai provider " + owner.id() + " to use " + owner.name() + ".", ChatFormatting.YELLOW);
                } else {
                    logDirect("Requests go to " + providerLabel(config) + ". If the key is from somewhere else: #ai provider <name>"
                            + " (#ai provider lists them).", ChatFormatting.GRAY);
                }
                logDirect("It is stored in plain text. Prefer the " + AiConfig.KEY_ENV_VAR + " environment variable if that bothers you.", ChatFormatting.GRAY);
                return;
            }
            case "provider": {
                String name = args.rawRest().trim();
                AiProviders.Provider provider = AiProviders.named(AiProviders.all(), name);
                if (provider == null || provider.baseUrl().isBlank()) {
                    if (!name.isEmpty()) logDirect("No provider called \"" + name + "\".", ChatFormatting.RED);
                    logDirect("Now: " + providerLabel(config) + ", model " + config.model);
                    for (AiProviders.Provider p : AiProviders.all()) {
                        if (p.baseUrl().isBlank()) continue;
                        logDirect("  #ai provider " + p.id() + "  - " + p.name()
                                + (p.local() ? " (free, runs on this PC)" : p.keyUrl().isEmpty() ? "" : ", keys: " + p.keyUrl()));
                    }
                    logDirect("Or .ai setup for the setup screen.", ChatFormatting.GRAY);
                    return;
                }
                use(config, provider);
                config.save();
                logDirect("Using " + provider.name() + ", model " + config.model + ".", ChatFormatting.GREEN);
                if (provider.needsKey() && !config.hasKey()) {
                    logDirect("Now #ai key <key>" + (provider.keyUrl().isEmpty() ? "" : " (get one at " + provider.keyUrl() + ")") + ".");
                } else if (AiProviders.keyMismatch(config.resolveKey(), config.baseUrl) != null) {
                    logDirect("Your saved key is " + AiProviders.forKey(AiProviders.all(), config.resolveKey()).name()
                            + "'s: #ai key <key> with a " + provider.name() + " key.", ChatFormatting.YELLOW);
                }
                return;
            }
            case "url": {
                String url = args.rawRest().trim();
                if (url.isEmpty()) {
                    logDirect("Base URL: " + config.baseUrl);
                    return;
                }
                config.baseUrl = url;
                config.save();
                logDirect("Base URL set to " + url, ChatFormatting.GREEN);
                return;
            }
            case "model": {
                String model = args.rawRest().trim();
                if (model.isEmpty()) {
                    logDirect("Model: " + config.model);
                    return;
                }
                config.model = model;
                config.save();
                logDirect("Model set to " + model, ChatFormatting.GREEN);
                return;
            }
            case "extra": {
                String extra = args.rawRest().trim();
                config.extraBody = extra.equalsIgnoreCase("none") ? "" : extra;
                config.save();
                logDirect("Extra request body: " + (config.extraBody.isEmpty() ? "(none)" : config.extraBody), ChatFormatting.GREEN);
                return;
            }
            case "trust": {
                String name = args.rawRest().trim();
                if (name.isEmpty()) {
                    logDirect("Trusted: " + (config.trusted.isEmpty() ? "(nobody)" : String.join(", ", config.trusted)));
                    return;
                }
                if (!config.isTrusted(name)) {
                    config.trusted.add(name);
                    config.save();
                }
                logDirect(name + " can now give the AI orders in chat.", ChatFormatting.GREEN);
                return;
            }
            case "untrust": {
                String name = args.rawRest().trim();
                config.trusted.removeIf(existing -> existing.equalsIgnoreCase(name));
                config.save();
                logDirect(name + " can no longer give orders.", ChatFormatting.YELLOW);
                return;
            }
            case "trigger": {
                String word = args.rawRest().trim();
                config.triggerWord = word.equalsIgnoreCase("none") ? "" : word;
                config.save();
                logDirect(config.triggerWord.isEmpty()
                        ? "Every message from a trusted player is now an instruction."
                        : "Trusted messages must contain \"" + config.triggerWord + "\" to count as an instruction.", ChatFormatting.GREEN);
                return;
            }
            case "goal": {
                String goal = args.rawRest().trim();
                if (goal.isEmpty()) {
                    logDirect("Goal: " + config.goal);
                    return;
                }
                config.goal = goal;
                config.save();
                logDirect("Goal set.", ChatFormatting.GREEN);
                return;
            }
            case "persona": {
                String persona = args.rawRest().trim();
                if (persona.isEmpty()) {
                    logDirect("Persona: " + config.persona);
                    return;
                }
                config.persona = persona;
                config.save();
                logDirect("Persona set.", ChatFormatting.GREEN);
                return;
            }
            case "chat": {
                config.respondInChat = parseToggle(args.rawRest(), !config.respondInChat);
                config.save();
                logDirect(config.respondInChat
                        ? "The AI will reply in server chat."
                        : "The AI will only print replies locally.", ChatFormatting.GREEN);
                return;
            }
            case "auto": {
                config.autonomous = parseToggle(args.rawRest(), !config.autonomous);
                config.save();
                logDirect(config.autonomous
                        ? "Autonomous mode on — it will think every " + config.idleSeconds + "s and spend tokens on its own."
                        : "Autonomous mode off — it only acts when spoken to.", ChatFormatting.GREEN);
                return;
            }
            case "followups": {
                String value = args.rawRest().trim().toLowerCase(Locale.ROOT);
                if (value.matches("\\d{1,3}")) {
                    config.maxAutoFollowUps = Math.min(Integer.parseInt(value), AiConfig.MAX_AUTO_FOLLOW_UPS_LIMIT);
                } else {
                    config.acquireFollowUps = parseToggle(value, !config.acquireFollowUps);
                    if (config.acquireFollowUps && config.maxAutoFollowUps <= 0) {
                        config.maxAutoFollowUps = AiConfig.DEFAULT_AUTO_FOLLOW_UPS;
                    }
                }
                config.save();
                logDirect(followUpsLine(config), ChatFormatting.GREEN);
                return;
            }
            case "forget": {
                String needle = args.rawRest().trim();
                if (needle.isEmpty()) {
                    logDirect("Memory (" + behavior.getMemories().worldKey() + "):");
                    logDirect(behavior.getMemories().digest());
                    return;
                }
                if (needle.equalsIgnoreCase("all")) {
                    behavior.getMemory().clear();
                    logDirect("This world's memory wiped.", ChatFormatting.YELLOW);
                    return;
                }
                String forgot = behavior.getMemory().forget(needle);
                if (forgot.startsWith("No matching") && behavior.getMemories().global() != behavior.getMemory()) {
                    forgot = behavior.getMemories().global().forget(needle);
                }
                logDirect(forgot, ChatFormatting.YELLOW);
                return;
            }
            case "clear": {
                behavior.getBrain().clearHistory();
                logDirect("Conversation history cleared.", ChatFormatting.GREEN);
                return;
            }
            case "deny": {
                String name = args.rawRest().trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty()) {
                    logDirect("Denied commands: " + String.join(", ", config.deniedCommands));
                    return;
                }
                if (config.isCommandAllowed(name)) {
                    config.deniedCommands.add(name);
                    config.save();
                }
                logDirect("The AI may no longer run #" + name, ChatFormatting.YELLOW);
                return;
            }
            case "allow": {
                String name = args.rawRest().trim();
                config.deniedCommands.removeIf(existing -> existing.equalsIgnoreCase(name));
                config.save();
                logDirect("The AI may now run #" + name, ChatFormatting.GREEN);
                return;
            }
            default: {
                // Anything else is treated as something to say to the AI directly.
                String prompt = (sub + " " + args.rawRest()).trim();
                logDirect("> " + prompt, ChatFormatting.GRAY);
                behavior.getBrain().submit("Your operator says: " + prompt);
            }
        }
    }

    private void status(AiBehavior behavior, AiConfig config) {
        AiBrain brain = behavior.getBrain();
        logDirect("DIH Client brain", ChatFormatting.GOLD);
        logDirect("  state: " + (config.enabled ? "on" : "off")
                + (brain.isThinking() ? " (thinking)" : "")
                + (config.autonomous ? ", autonomous" : ""));
        logDirect("  model: " + config.model + " @ " + providerLabel(config));
        logDirect("  key: " + (config.hasKey() ? mask(config.resolveKey()) : "MISSING"));
        logDirect("  trusted: " + (config.trusted.isEmpty() ? "(nobody)" : String.join(", ", config.trusted)));
        logDirect("  trigger: " + (config.triggerWord == null || config.triggerWord.isEmpty() ? "(any message)" : config.triggerWord));
        logDirect("  replies in chat: " + config.respondInChat);
        logDirect("  " + followUpsLine(config));
        logDirect("  tokens used: " + brain.getLlm().getPromptTokens() + " in / "
                + brain.getLlm().getCompletionTokens() + " out over " + brain.getLlm().getCalls() + " calls");
        if (brain.getLastError() != null) {
            logDirect("  last error: " + brain.getLastError(), ChatFormatting.RED);
        }
    }

    /** Points the AI at {@code provider}: its URL and its first suggested model. */
    private static void use(AiConfig config, AiProviders.Provider provider) {
        config.baseUrl = provider.baseUrl();
        if (!provider.models().isEmpty()) config.model = provider.models().get(0).id();
    }

    /** "Groq (https://api.groq.com/openai/v1)", or the bare URL for one that isn't a preset. */
    private static String providerLabel(AiConfig config) {
        AiProviders.Provider at = AiProviders.forUrl(config.baseUrl);
        return at == null ? config.baseUrl : at.name() + " (" + config.baseUrl + ")";
    }

    private static String followUpsLine(AiConfig config) {
        if (!config.followUpsActive()) {
            return "acquire follow-ups: off (the AI stops after starting an acquire)";
        }
        return "acquire follow-ups: on, up to " + config.maxAutoFollowUps
                + " automatic turns in a row when an acquire it started ends";
    }

    private static boolean parseToggle(String raw, boolean fallback) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("on") || value.equals("true") || value.equals("yes")) {
            return true;
        }
        if (value.equals("off") || value.equals("false") || value.equals("no")) {
            return false;
        }
        return fallback;
    }

    private static String mask(String key) {
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "…" + key.substring(key.length() - 4);
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            return SUBCOMMANDS.stream();
        }
        if (args.getString().equalsIgnoreCase("provider") && args.hasExactlyOne()) {
            return AiProviders.all().stream().filter(p -> !p.baseUrl().isBlank()).map(AiProviders.Provider::id);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Talk to the language-model brain";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The ai command controls the language model that reads chat and plays for you.",
                "",
                "It watches every message the server sends. Messages from trusted players are",
                "instructions; everything else is context it can read but must not obey.",
                "",
                "Setup (easiest: .ai setup opens a setup screen):",
                "> ai key <api-key>        - store your key; Groq, OpenAI, OpenRouter, Claude and Gemini keys pick their provider",
                "> ai provider [name]      - list the providers, or switch to one (sets the URL and a model)",
                "> ai url <base-url>       - any OpenAI-compatible endpoint",
                "> ai model <name>         - e.g. openai/gpt-oss-120b on Groq",
                "> ai trust <player>       - let that player command the bot in chat",
                "> ai on                   - start listening",
                "",
                "Day to day:",
                "> ai <anything>           - tell the AI to do something yourself",
                "> ai status               - config, token usage, last error",
                "> ai auto [on|off]        - let it think on its own timer",
                "> ai followups [on|off|n] - continue after an acquire it started ends (n = max in a row)",
                "> ai chat [on|off]        - whether it may talk in server chat",
                "> ai trigger <word|none>  - require a wake word from trusted players",
                "> ai goal <text>          - its standing objective",
                "> ai forget [all|text]    - inspect or wipe long-term memory",
                "> ai clear                - forget the current conversation",
                "> ai deny <command>       - forbid a command outright",
                "",
                "Everything is stored in ai.json and ai_memory.json in your baritone folder."
        );
    }

    /** Kept for tab-complete symmetry with other multi-word commands. */
    static List<String> subcommands() {
        return Collections.unmodifiableList(SUBCOMMANDS);
    }
}
