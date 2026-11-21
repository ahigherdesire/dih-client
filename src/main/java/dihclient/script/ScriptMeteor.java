package dihclient.script;

import dihclient.platform.DihLoader;

/** Optional Meteor adapter. No Meteor classes are linked when the provider is absent. */
final class ScriptMeteor {
    private ScriptMeteor() { }

    static boolean enabled(String provider, String name) {
        Object module = module(provider, name);
        if (module == null) return false;
        try { return Boolean.TRUE.equals(module.getClass().getMethod("isActive").invoke(module)); }
        catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    static boolean setEnabled(String provider, String name, boolean enabled) {
        return setState(module(provider, name), enabled);
    }

    static boolean setState(Object module, boolean enabled) {
        if (module == null) return false;
        try {
            var active = module.getClass().getMethod("isActive");
            if (Boolean.TRUE.equals(active.invoke(module)) != enabled) module.getClass().getMethod("toggle").invoke(module);
            return Boolean.TRUE.equals(active.invoke(module)) == enabled;
        } catch (ReflectiveOperationException | LinkageError e) { return false; }
    }

    private static Object module(String provider, String name) {
        if (!"meteor".equalsIgnoreCase(provider)) return null;
        try {
            if (!DihLoader.isModLoaded("meteor-client")) return null;
            Class<?> modules = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules");
            Object manager = modules.getMethod("get").invoke(null);
            return modules.getMethod("get", String.class).invoke(manager, name);
        } catch (ReflectiveOperationException | LinkageError e) { return null; }
    }
}
