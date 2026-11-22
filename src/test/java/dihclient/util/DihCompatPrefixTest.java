package dihclient.util;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DihCompatPrefixTest {
    @Test
    void dotRemainsDefaultAndSelectableWithAndWithoutMeteor() throws Exception {
        for (boolean meteor : new boolean[]{false, true}) {
            Class<?> compat = isolatedCompat(meteor);
            assertEquals(meteor, call(compat, "isMeteorAvailable"));
            assertEquals(".", call(compat, "environmentDefaultCommandPrefix"));
            assertEquals(".", call(compat, "normalizeStoredCommandPrefix", "."));
            assertEquals(true, call(compat, "isSelectableCommandPrefix", "."));
            for (String invalid : new String[]{null, "", " ", "invalid"}) {
                assertEquals(".", call(compat, "normalizeStoredCommandPrefix", invalid));
            }
        }
    }

    @Test
    void explicitAlternativePrefixesSurviveMeteorCompatibility() throws Exception {
        for (boolean meteor : new boolean[]{false, true}) {
            Class<?> compat = isolatedCompat(meteor);
            for (String prefix : new String[]{"%", "-", "_", "*", "#", "@", "&", "="}) {
                assertEquals(prefix, call(compat, "normalizeStoredCommandPrefix", " " + prefix + " "));
                assertEquals(true, call(compat, "isSelectableCommandPrefix", prefix));
            }
            assertEquals(false, call(compat, "isSelectableCommandPrefix", "invalid"));
        }
    }

    @Test
    void meteorReceivesOnlyUnclaimedBodiesUnderItsOwnPrefix() throws Exception {
        Class<?> compat = isolatedCompat(true);
        var defer = compat.getMethod("shouldDeferCommandToMeteor", String.class, boolean.class);
        assertEquals(false, defer.invoke(null, ".structure village", true));
        assertEquals(false, defer.invoke(null, ".autosleep", true));
        assertEquals(false, defer.invoke(null, ".seedinput 42", true));
        assertEquals(false, defer.invoke(null, ".str", true));
        assertEquals(true, defer.invoke(null, ".meteor-only", false));
        assertEquals(true, defer.invoke(null, "  .meteor-only  ", false));
        assertEquals(false, defer.invoke(null, "%meteor-only", false));
        assertEquals(false, defer.invoke(null, "/meteor-only", false));
        assertEquals(false, defer.invoke(null, null, false));
        assertEquals(false, isolatedCompat(false)
            .getMethod("shouldDeferCommandToMeteor", String.class, boolean.class)
            .invoke(null, ".meteor-only", false));
    }

    @Test
    void honorsMeteorAlternativePrefixWhenDecidingWhetherToDefer() throws Exception {
        Class<?> compat = isolatedCompat(true);
        var cachedPrefix = compat.getDeclaredField("cachedMeteorCommandPrefix");
        cachedPrefix.setAccessible(true);
        cachedPrefix.set(null, "%");
        var refreshAt = compat.getDeclaredField("meteorPrefixRefreshAtNanos");
        refreshAt.setAccessible(true);
        refreshAt.setLong(null, Long.MAX_VALUE);
        var defer = compat.getMethod("shouldDeferCommandToMeteor", String.class, boolean.class);
        assertEquals(true, defer.invoke(null, "%meteor-only", false));
        assertEquals(false, defer.invoke(null, ".meteor-only", false));
        assertEquals(false, defer.invoke(null, "%structure", true));
    }

    // Isolate optional-mod discovery, keeping the production prefix policy unchanged.
    static Class<?> isolatedCompat(boolean meteor) throws Exception {
        ClassLoader loader = new ClassLoader(DihCompatPrefixTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded != null) return loaded;
                    byte[] bytes;
                    if (name.equals("dihclient.platform.DihLoader")) {
                        ClassWriter writer = new ClassWriter(0);
                        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
                        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isModLoaded", "(Ljava/lang/String;)Z", null, null);
                        method.visitCode();
                        method.visitInsn(meteor ? Opcodes.ICONST_1 : Opcodes.ICONST_0);
                        method.visitInsn(Opcodes.IRETURN);
                        method.visitMaxs(1, 1);
                        method.visitEnd();
                        writer.visitEnd();
                        bytes = writer.toByteArray();
                    } else if (name.equals("meteordevelopment.meteorclient.systems.macros.Macros") && meteor) {
                        ClassWriter writer = new ClassWriter(0);
                        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, name.replace('.', '/'), null, "java/lang/Object", null);
                        writer.visitEnd();
                        bytes = writer.toByteArray();
                    } else if (name.equals("dihclient.util.DihCompatManager")) {
                        try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                            if (input == null) throw new ClassNotFoundException(name);
                            bytes = input.readAllBytes();
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    } else if (name.equals("baritone.api.BaritoneAPI") || name.startsWith("meteordevelopment.")) {
                        throw new ClassNotFoundException(name);
                    } else {
                        return super.loadClass(name, resolve);
                    }
                    Class<?> defined = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(defined);
                    return defined;
                }
            }
        };
        return Class.forName("dihclient.util.DihCompatManager", true, loader);
    }

    private static Object call(Class<?> owner, String method, String... args) throws Exception {
        return args.length == 0 ? owner.getMethod(method).invoke(null)
            : owner.getMethod(method, String.class).invoke(null, (Object) args[0]);
    }
}
