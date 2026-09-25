package baritone.acquire.knowledge;

import baritone.acquire.model.Source;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link Knowledge} built from the vanilla data that ships in the Minecraft jar (recipes, loot tables,
 * tags), plus the recipes the connected server sends when available.
 *
 * <p>SCAFFOLD: the knowledge agent replaces this body. Keep {@link #get()} as the entry point.
 */
public final class VanillaKnowledge implements Knowledge {

    private VanillaKnowledge() {
    }

    /** Loads (once, then cached) and returns the knowledge base. Safe to call from the game thread. */
    public static synchronized Knowledge get() {
        throw new UnsupportedOperationException("VanillaKnowledge is not implemented yet");
    }

    @Override
    public List<Source> sourcesFor(String item) {
        return List.of();
    }

    @Override
    public String toolType(String item) {
        return null;
    }

    @Override
    public int toolTier(String item) {
        return 0;
    }

    @Override
    public List<String> toolsOf(String type, int minTier) {
        return List.of();
    }

    @Override
    public Map<String, Integer> fuels() {
        return Map.of();
    }

    @Override
    public boolean isItem(String item) {
        return false;
    }

    @Override
    public Optional<String> resolveItem(String userText) {
        return Optional.empty();
    }

    @Override
    public List<String> suggest(String userText, int limit) {
        return List.of();
    }
}
