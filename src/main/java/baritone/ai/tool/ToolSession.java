package baritone.ai.tool;

import com.google.gson.JsonArray;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The tools one AI run can see: every job tool, plus whole categories the model loaded with {@code load_tools}.
 * Loaded categories stay until the run ends. Definitions keep registration order, so loading a category inserts its
 * tools in place and the rest stay byte-identical.
 */
public final class ToolSession {

    private final ToolRegistry registry;
    private final Set<ToolCategory> loaded = EnumSet.noneOf(ToolCategory.class);

    public ToolSession(ToolRegistry registry) {
        this.registry = registry;
    }

    public ToolRegistry registry() {
        return this.registry;
    }

    public synchronized boolean isVisible(AiTool tool) {
        return tool.job() || this.loaded.contains(tool.category());
    }

    /** Makes a category visible; returns how many tools that added. */
    public int load(ToolCategory category) {
        int added = 0;
        synchronized (this) {
            if (!this.loaded.add(category)) {
                return 0;
            }
        }
        for (AiTool tool : this.registry.inCategory(category)) {
            if (!tool.job()) {
                added++;
            }
        }
        return added;
    }

    public synchronized Set<ToolCategory> loaded() {
        return EnumSet.copyOf(this.loaded);
    }

    public List<AiTool> visible() {
        return this.registry.all().stream().filter(this::isVisible).toList();
    }

    public JsonArray definitions() {
        return this.registry.definitions(this::isVisible);
    }
}
