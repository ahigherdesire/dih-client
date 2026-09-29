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

    public record Provider(String id, String name, String baseUrl, boolean needsKey, boolean local, String keyUrl,
                           String source, List<Model> models) {
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
                    List.copyOf(models)));
        }
        return List.copyOf(out);
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
