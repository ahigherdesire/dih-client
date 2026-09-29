package dihclient.palette;

import baritone.api.BaritoneAPI;
import com.mojang.brigadier.tree.CommandNode;
import dihclient.api.module.Setting;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.gui.screen.DihCommandPaletteScreen;
import dihclient.gui.screen.DihModuleScreen;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.PackHideState;
import dihclient.palette.PaletteIndex.Entry;
import dihclient.palette.PaletteIndex.Kind;
import dihclient.util.AutoFishStopMacroFactory;
import dihclient.util.DihConfig;
import dihclient.util.DihMacro;
import dihclient.util.DihMacroManager;
import dihclient.util.DihNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds the palette's entries from modules, settings, commands and macros, and runs the one picked. */
public final class DihCommandPalette {
    private static final int RECENT_MAX = 20;

    private DihCommandPalette() {
    }

    /** The configured shortcut: its key, with Ctrl when that key would otherwise type a character. */
    public static boolean isShortcut(int keyCode, int modifiers) {
        DihConfig config = DihConfig.getGlobal();
        int bind = config == null ? -1 : config.keybindCommandPalette;
        if (bind == -1 || keyCode != bind) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 || ctrlHeld();
        return !needsCtrl(bind) || ctrl;
    }

    public static boolean ctrlHeld() {
        Minecraft mc = Minecraft.getInstance();
        return dihclient.util.DihBindUtil.isBindPressed(mc, GLFW.GLFW_KEY_LEFT_CONTROL)
            || dihclient.util.DihBindUtil.isBindPressed(mc, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** Printable keys (letters, digits, punctuation) need Ctrl so that typing them never opens the palette. */
    public static boolean needsCtrl(int keyCode) {
        return keyCode >= GLFW.GLFW_KEY_SPACE && keyCode <= GLFW.GLFW_KEY_GRAVE_ACCENT;
    }

    /** "Ctrl+K", or the key's name alone for keys like F6. */
    public static String shortcutLabel() {
        DihConfig config = DihConfig.getGlobal();
        int bind = config == null ? -1 : config.keybindCommandPalette;
        if (bind == -1) return "unbound";
        String name = dihclient.util.DihBindUtil.getBindName(bind);
        return needsCtrl(bind) ? "Ctrl+" + name : name;
    }

    public static boolean canOpen() {
        return !PackHideState.isActive();
    }

    /**
     * The shortcut was pressed on the current screen: opens the palette over it unless someone is typing (a DIH or
     * vanilla text field has focus, or chat is open). Returns whether it opened.
     */
    public static boolean openFromKey() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (!canOpen() || screen instanceof ChatScreen || screen instanceof DihCommandPaletteScreen) return false;
        if (dihclient.gui.vanillaui.components.CompactTextInput.anyFocused()
            || dihclient.util.DihOverlayManager.get().isAnyTextFieldFocused()) return false;
        if (screen != null && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box && box.isFocused()) return false;
        open(screen);
        return true;
    }

    /** Opens the palette over {@code parent} (null in-game); closing it goes back to {@code parent}. */
    public static void open(Screen parent) {
        if (!canOpen()) return;
        Minecraft.getInstance().gui.setScreen(new DihCommandPaletteScreen(parent));
    }

    public static PaletteIndex buildIndex() {
        List<Entry> entries = new ArrayList<>();
        for (Module module : ModuleRegistry.all()) {
            if (PackHideState.isActive() && !PackHideState.isHideModule(module)) continue;
            entries.add(new Entry(Kind.MODULE, module.name(), List.of(module.id(), module.category().name()),
                module.description(), "module:" + module.id()));
            for (Setting<?, ?> setting : module.settings()) {
                entries.add(new Entry(Kind.SETTING, module.name() + " › " + setting.label(),
                    List.of(module.name(), setting.id()), "Setting in " + module.name(),
                    "setting:" + module.id() + ":" + setting.id()));
            }
        }
        String dot = DihCommands.effectivePrefix();
        entries.add(new Entry(Kind.DOT_COMMAND, dot + "ai start", List.of("AI objective", "run"),
            "Tell the AI what to do", "ai:start"));
        entries.add(new Entry(Kind.DOT_COMMAND, dot + "ai setup", List.of("AI provider", "model key"),
            "Choose an AI provider and test it", "ai:setup"));
        entries.add(new Entry(Kind.DOT_COMMAND, dot + "ai panel", List.of("AI progress", "checklist"),
            "Show the live AI panel", "ai:panel"));
        for (Command command : DihCommands.all()) {
            entries.add(new Entry(Kind.DOT_COMMAND, dot + command.name(), Arrays.asList(command.aliases()),
                command.description(), "dot:" + command.name()));
        }
        try {
            String hash = BaritoneAPI.getSettings().prefix.value;
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().stream().forEach(command -> {
                if (command.hiddenFromHelp() || command.getNames().isEmpty()) return;
                List<String> names = command.getNames();
                entries.add(new Entry(Kind.HASH_COMMAND, hash + names.get(0), names.subList(1, names.size()),
                    command.getShortDesc(), "hash:" + names.get(0)));
            });
        } catch (Throwable ignored) {
            // Baritone isn't up yet (title screen before first world): commands are simply left out.
        }
        for (DihMacro macro : DihMacroManager.get().getAll()) {
            if (macro == null || macro.name == null || AutoFishStopMacroFactory.isGeneratedStopMacro(macro)) continue;
            entries.add(new Entry(Kind.MACRO, macro.name, macro.hasFolder() ? List.of(macro.folder) : List.of(),
                macro.actions.size() + " steps" + (macro.hasFolder() ? " · " + macro.folder : ""), "macro:" + macro.name));
        }
        for (baritone.ai.tool.AiTool tool : baritone.ai.tool.ToolRegistry.standard().all()) {
            entries.add(new Entry(Kind.AI_TOOL, tool.name(), List.of(tool.category().id(), "tool"),
                tool.summary(), "tool:" + tool.name()));
        }
        return new PaletteIndex(entries);
    }

    /** A one-line hint for the highlighted entry, e.g. the module's current state. */
    public static String hint(Entry entry) {
        if (entry == null) return "";
        return switch (entry.kind()) {
            case MODULE -> {
                Module module = ModuleRegistry.get(entry.action().substring("module:".length()));
                yield module == null ? "" : "Enter turns " + module.name() + (module.isEnabled() ? " off (now on)" : " on (now off)");
            }
            case SETTING -> "Enter opens the module menu at this setting";
            case DOT_COMMAND -> runsDirectly(entry) ? "Enter runs it" : "Enter puts it in chat to finish";
            case HASH_COMMAND, AI_TOOL -> "Enter puts it in chat to finish";
            case MACRO -> "Enter runs the macro";
        };
    }

    /**
     * Does what {@code entry} stands for. Returns the screen to show next: the parent (the palette is done), or a
     * different one (chat, the module menu).
     */
    public static Screen run(Entry entry, Screen parent) {
        DihConfig config = DihConfig.getGlobal();
        config.paletteRecent = PaletteIndex.remember(config.paletteRecent, entry.action(), RECENT_MAX);
        config.save();
        String action = entry.action();
        if (action.equals("ai:start")) return new ChatScreen(DihCommands.effectivePrefix() + "ai start ", false);
        if (action.equals("ai:setup")) return new dihclient.gui.screen.DihAiSetupScreen(parent);
        if (action.equals("ai:panel")) {
            dihclient.util.DihAiPanelOverlay.toggle();
            return parent;
        }
        switch (entry.kind()) {
            case MODULE -> {
                Module module = ModuleRegistry.get(action.substring("module:".length()));
                if (module != null) {
                    module.toggle();
                    DihNotifications.show(module.name() + (module.isEnabled() ? " on" : " off"), 0xFF57F287);
                }
                return parent;
            }
            case SETTING -> {
                String[] parts = action.split(":", 3);
                return new DihModuleScreen(parent).openingSettingsOf(parts.length > 1 ? parts[1] : "");
            }
            case DOT_COMMAND -> {
                String name = action.substring("dot:".length());
                if (runsDirectly(entry)) {
                    Minecraft.getInstance().execute(() -> DihCommands.dispatch(name));
                    return parent;
                }
                return new ChatScreen(DihCommands.effectivePrefix() + name + " ", false);
            }
            case HASH_COMMAND -> {
                return new ChatScreen(entry.title() + " ", false);
            }
            case MACRO -> {
                DihMacro macro = DihMacroManager.get().get(action.substring("macro:".length()));
                if (macro != null) macro.execute();
                return parent;
            }
            case AI_TOOL -> {
                return new ChatScreen(DihCommands.effectivePrefix() + "tools " + action.substring("tool:".length()) + " ", false);
            }
        }
        return parent;
    }

    /** A {@code .} command runs straight away only if it takes nothing: it executes bare and has no arguments. */
    static boolean runsDirectly(Entry entry) {
        if (entry.kind() != Kind.DOT_COMMAND || !entry.action().startsWith("dot:")) return false;
        CommandNode<DihCommandSource> node = DihCommands.dispatcher().getRoot().getChild(entry.action().substring("dot:".length()));
        return node != null && node.getCommand() != null && node.getChildren().isEmpty();
    }
}
