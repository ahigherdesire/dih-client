package dihclient.ai;

import baritone.ai.catalog.CommandAdapters;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.api.IBaritone;
import baritone.api.command.ICommand;
import baritone.command.defaults.DefaultCommands;
import dihclient.commands.DihCommands;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The whole catalog, as the client builds it: at least 110 tools, 70 of them hand-written, every category covered. */
final class ToolCatalogCountTest {

    /** An object of {@code type} whose methods do nothing and return more of the same, so constructors can run. */
    @SuppressWarnings("unchecked")
    static <T> T inert(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            if (method.getName().equals("toString")) return "inert " + type.getSimpleName();
            if (r == boolean.class) return false;
            if (r == int.class || r == long.class || r == short.class || r == byte.class) return 0;
            if (r == double.class || r == float.class) return 0.0;
            if (r.isInterface()) return inert(r);
            if (r == Minecraft.class) return unconstructed(Minecraft.class);
            return null;
        });
    }

    /** An instance made without running a constructor: every field null. Enough for code that only reads fields. */
    @SuppressWarnings("unchecked")
    static <T> T unconstructed(Class<T> type) throws ReflectiveOperationException {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    static ToolRegistry fullCatalog() {
        ToolRegistry registry = ToolRegistry.withDefaults();
        ClientTools.register(registry);
        List<CommandAdapters.Info> baritone = new ArrayList<>();
        for (ICommand command : DefaultCommands.createAll(inert(IBaritone.class))) {
            baritone.add(new CommandAdapters.Info(command.getNames().get(0), command.getNames(), command.getShortDesc()));
        }
        CommandAdapters.registerBaritone(registry, baritone);
        DihCommands.init();
        CommandAdapters.registerClient(registry, ClientToolCatalog.clientCommands());
        return registry;
    }

    @Test
    void atLeast110ToolsWith70HandWrittenAndEveryCategoryCovered() {
        ToolRegistry registry = fullCatalog();
        List<AiTool> all = registry.all();
        List<AiTool> handWritten = all.stream().filter(t -> !t.name().startsWith("cmd_") && !t.name().startsWith("dot_")).toList();
        assertTrue(all.size() >= 110, "tools: " + all.size());
        assertTrue(handWritten.size() >= 70, "hand-written: " + handWritten.size());
        long cmd = all.stream().filter(t -> t.name().startsWith("cmd_")).count();
        long dot = all.stream().filter(t -> t.name().startsWith("dot_")).count();
        assertTrue(cmd >= 30, "# adapters: " + cmd);
        assertTrue(dot >= 20, ". adapters: " + dot);
        for (ToolCategory category : ToolCategory.values()) {
            assertFalse(registry.inCategory(category).isEmpty(), "no tools in " + category.id());
        }
        System.out.println("[ToolCatalogCountTest] " + all.size() + " tools: " + handWritten.size() + " hand-written, "
                + cmd + " # adapters, " + dot + " . adapters");
    }

    @Test
    void everyToolIsDescribedAndOnlyJobsAreAlwaysVisible() {
        for (AiTool tool : fullCatalog().all()) {
            assertFalse(tool.summary().isBlank(), tool.name());
            assertFalse(tool.description().isBlank(), tool.name());
            assertTrue(tool.definition().getAsJsonObject("function").get("description").getAsString().length() > 10, tool.name());
            if (tool.name().startsWith("cmd_") || tool.name().startsWith("dot_")) {
                assertTrue(tool.category() == ToolCategory.RAW && !tool.job(), "adapters are raw: " + tool.name());
            }
        }
    }
}
