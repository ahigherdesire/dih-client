package dihclient.api;

import dihclient.api.macro.MacroActionEntry;
import dihclient.api.macro.MacroPresetRegistry;
import dihclient.api.macro.SimpleAction;
import dihclient.api.macro.SimpleCondition;
import dihclient.modules.Module;
import dihclient.util.macro.MacroAction;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class SimpleAddon extends DihAddon {
    private final int apiVersion;
    private final String rootPackage;

    protected SimpleAddon(int apiVersion, String rootPackage) {
        this.apiVersion = apiVersion;
        this.rootPackage = rootPackage == null ? "" : rootPackage;
    }

    @Override
    public final int apiVersion() {
        return apiVersion;
    }

    @Override
    public final String getPackage() {
        return rootPackage;
    }

    @Override
    public final void onInitialize() {
        initialize();
    }

    protected abstract void initialize();

    protected final String id(String localId) {
        return DihAddons.id(localId);
    }

    protected final AddonRegistrationResult registerModule(Module module) {
        return DihAddons.modules().registerDetailed(module);
    }

    protected final AddonRegistrationResult registerAction(MacroActionEntry entry) {
        return DihAddons.macroActions().registerDetailed(entry);
    }

    protected final AddonRegistrationResult registerPreset(String label, String tip, Supplier<List<MacroAction>> builder) {
        return DihAddons.presets().registerDetailed(label, tip, builder);
    }

    protected final AddonRegistrationResult registerSimpleAction(
            String localId, String label, String tip, String icon, Consumer<Minecraft> runner) {
        return registerAction(simpleAction(localId, label, tip, icon, runner));
    }

    protected final AddonRegistrationResult registerSimpleCondition(
            String localId, String label, String tip, String status, String icon, Predicate<Minecraft> predicate) {
        return registerAction(simpleCondition(localId, label, tip, status, icon, predicate));
    }

    protected final MacroActionEntry simpleAction(
        String localId,
        String label,
        String tip,
        String icon,
        Consumer<Minecraft> runner
    ) {
        String typeId = id(localId);
        return MacroActionEntry.builder(typeId, () -> new SimpleAction(typeId, label, icon, runner))
            .picker(label, tip)
            .build();
    }

    protected final MacroActionEntry simpleCondition(
        String localId,
        String label,
        String tip,
        String status,
        String icon,
        Predicate<Minecraft> predicate
    ) {
        String typeId = id(localId);
        return MacroActionEntry.builder(typeId, () -> new SimpleCondition(typeId, label, status, icon, predicate))
            .condition(label, tip)
            .build();
    }
}
