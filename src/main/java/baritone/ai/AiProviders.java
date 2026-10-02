package baritone.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The provider presets for the AI setup screen and the price table for the panel's cost estimate, read from
 * {@code assets/dihclient/ai_providers.json} so they're kept in one place.
 */
public final class AiProviders {

    public record Model(String id, Double inputPerMillion, Double outputPerMillion) {
        public boolean priced() {
            return this.inputPerMillion != null && this.outputPerMillion != null;
        }
    }

    /**
     * @param keyPrefixes how this provider's keys start ("gsk_" for Groq), to tell which provider a pasted key is for
     * @param keyNote     what else to check when this provider refuses a key, or ""
     */
    public record Provider(String id, String name, String baseUrl, boolean needsKey, boolean local, String keyUrl,
                           String source, List<Model> models, List<String> keyPrefixes, String keyNote) {
        public Model model(String id) {
            for (Model model : this.models) {
                if (model.id().equalsIgnoreCase(id == null ? "" : id.trim())) return model;
            }
            return null;
        }
    }

    public static final String RESOURCE = "/assets/dihclient/ai_providers.json";
    private static volatile List<Provider> loaded;

    private AiProviders() {
    }

    public static List<Provider> all() {
        List<Provider> providers = loaded;
        if (providers == null) {
            try (InputStream in = AiProviders.class.getResourceAsStream(RESOURCE)) {
                providers = in == null ? List.of() : parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("[DIH] could not read the AI provider presets: " + e);
                providers = List.of();
            }
            loaded = providers;
        }
        return providers;
    }

    static List<Provider> parse(String json) {
        List<Provider> out = new ArrayList<>();
        for (JsonElement element : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("providers")) {
            JsonObject p = element.getAsJsonObject();
            List<Model> models = new ArrayList<>();
            for (JsonElement m : p.getAsJsonArray("models")) {
                JsonObject model = m.getAsJsonObject();
                models.add(new Model(model.get("id").getAsString(),
                        model.has("in") ? model.get("in").getAsDouble() : null,
                        model.has("out") ? model.get("out").getAsDouble() : null));
            }
            out.add(new Provider(p.get("id").getAsString(), p.get("name").getAsString(), p.get("baseUrl").getAsString(),
                    p.get("needsKey").getAsBoolean(), p.has("local") && p.get("local").getAsBoolean(),
                    p.has("keyUrl") ? p.get("keyUrl").getAsString() : "", p.has("source") ? p.get("source").getAsString() : "",
                    List.copyOf(models), strings(p, "keyPrefixes"), p.has("keyNote") ? p.get("keyNote").getAsString() : ""));
        }
        return List.copyOf(out);
    }

    private static List<String> strings(JsonObject object, String field) {
        List<String> out = new ArrayList<>();
        if (object.has(field)) object.getAsJsonArray(field).forEach(e -> out.add(e.getAsString()));
        return List.copyOf(out);
    }

    /** The preset named {@code idOrName} (its id, or its name ignoring case), or null. */
    public static Provider named(List<Provider> providers, String idOrName) {
        String want = idOrName == null ? "" : idOrName.trim().toLowerCase(Locale.ROOT);
        if (want.isEmpty()) return null;
        for (Provider provider : providers) {
            if (provider.id().equals(want) || provider.name().toLowerCase(Locale.ROOT).equals(want)) return provider;
        }
        return null;
    }

    /**
     * The preset a key belongs to, told by how it starts ("gsk_" is Groq's), or null when the key doesn't say (plain
     * "sk-" keys come from OpenAI, DeepSeek, Qwen and others). The longest matching prefix wins.
     */
    public static Provider forKey(List<Provider> providers, String key) {
        String k = key == null ? "" : key.trim();
        Provider best = null;
        int bestLength = 0;
        for (Provider provider : providers) {
            for (String prefix : provider.keyPrefixes()) {
                if (k.startsWith(prefix) && prefix.length() > bestLength) {
                    best = provider;
                    bestLength = prefix.length();
                }
            }
        }
        return best;
    }

    /**
     * The provider {@code key} is for when requests to {@code baseUrl} would go somewhere else (a Groq key sent to
     * Qwen), else null: also null when the key doesn't say whose it is.
     */
    public static Provider keyMismatch(List<Provider> providers, String key, String baseUrl) {
        Provider owner = forKey(providers, key);
        if (owner == null || owner.baseUrl().isBlank()) return null;
        return owner == forUrl(providers, baseUrl) ? null : owner;
    }

    public static Provider keyMismatch(String key, String baseUrl) {
        return keyMismatch(all(), key, baseUrl);
    }

    /**
     * Why a provider may have refused the key, in a few words, or "": the key is another provider's (a Groq key sent to
     * Qwen), or the provider's own note (Qwen keys only work in their region).
     */
    public static String refusedKeyReason(List<Provider> providers, String key, String baseUrl) {
        Provider owner = keyMismatch(providers, key, baseUrl);
        if (owner != null) {
            return "that's " + article(owner.name()) + " key, but requests go to " + host(baseUrl)
                    + ": run #ai provider " + owner.id() + " (or .ai setup)";
        }
        Provider at = forUrl(providers, baseUrl);
        return at == null ? "" : at.keyNote();
    }

    public static String refusedKeyReason(String key, String baseUrl) {
        return refusedKeyReason(all(), key, baseUrl);
    }

    private static String article(String name) {
        return ("aeiou".indexOf(Character.toLowerCase(name.charAt(0))) >= 0 ? "an " : "a ") + name;
    }

    static String host(String baseUrl) {
        try {
            String host = java.net.URI.create(baseUrl.trim()).getHost();
            return host == null ? baseUrl : host;
        } catch (RuntimeException e) {
            return baseUrl;
        }
    }

    /** The preset whose base URL matches {@code baseUrl} (ignoring case and trailing slashes), or null. */
    public static Provider forUrl(List<Provider> providers, String baseUrl) {
        String want = normalize(baseUrl);
        if (want.isEmpty()) return null;
        for (Provider provider : providers) {
            if (!provider.baseUrl().isEmpty() && normalize(provider.baseUrl()).equals(want)) return provider;
        }
        return null;
    }

    public static Provider forUrl(String baseUrl) {
        return forUrl(all(), baseUrl);
    }

    private static String normalize(String url) {
        return url == null ? "" : url.trim().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
    }

    /**
     * What {@code promptTokens} in and {@code completionTokens} out cost: "$0.012", "free (local)", or null when the
     * price isn't known (the panel then shows tokens only).
     */
    public static String cost(List<Provider> providers, String baseUrl, String model, long promptTokens, long completionTokens) {
        Provider provider = forUrl(providers, baseUrl);
        if (provider == null) return null;
        if (provider.local()) return "free (local)";
        Model priced = provider.model(model);
        if (priced == null || !priced.priced()) return null;
        double dollars = promptTokens / 1e6 * priced.inputPerMillion() + completionTokens / 1e6 * priced.outputPerMillion();
        return dollars < 0.01 ? String.format(Locale.ROOT, "$%.4f", dollars) : String.format(Locale.ROOT, "$%.2f", dollars);
    }
}
