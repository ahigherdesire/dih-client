package dihclient.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Executes the real hook bytecode with isolated command-registry and chat-history boundaries. */
public class DihMeteorCommandRoutingTest {
    private static final String SEND_MIXIN = "dihclient/mixin/DihClientPlayNetworkHandlerMixin";
    private static final String SUGGEST_MIXIN = "dihclient/mixin/DihChatSuggestMixin";
    private static final Set<String> ROOTS = Set.of("structure", "autosleep", "seedinput", "scriptroot");
    private static final List<String> dispatched = new ArrayList<>();
    private static boolean bypass;
    private static boolean panic;
    private static String prefix;
    private static Class<?> compat;

    @BeforeEach
    void reset() throws Exception {
        dispatched.clear();
        bypass = false;
        panic = false;
        prefix = ".";
        compat = DihCompatPrefixTest.isolatedCompat(true);
    }

    @Test
    void knownSharedDotRootsCancelBeforeMeteorWhileUnknownRootsPassThrough() throws Exception {
        assertTrue(priority(SEND_MIXIN) > 1000, "Meteor's sendChat HEAD hook uses default priority");
        Method send = isolatedHook(SEND_MIXIN, "dih$dispatchDihCommand");
        Object receiver = send.getDeclaringClass().getConstructor().newInstance();
        for (String body : List.of("structure village", "autosleep", "seedinput 42", "scriptroot arg")) {
            CallbackInfo callback = new CallbackInfo("sendChat", true);
            send.invoke(receiver, "." + body, callback);
            assertTrue(callback.isCancelled(), body + " must never reach Meteor's later hook");
            assertEquals(body, dispatched.getLast());
        }
        for (String body : List.of("meteor-only", "str", "structureother")) {
            CallbackInfo callback = new CallbackInfo("sendChat", true);
            int count = dispatched.size();
            send.invoke(receiver, "." + body, callback);
            assertFalse(callback.isCancelled(), body + " must reach Meteor");
            assertEquals(count, dispatched.size());
        }
    }

    @Test
    void bypassAndPanicPrecedeSharedPrefixOwnership() throws Exception {
        Method send = isolatedHook(SEND_MIXIN, "dih$dispatchDihCommand");
        Object receiver = send.getDeclaringClass().getConstructor().newInstance();
        bypass = true;
        panic = true;
        CallbackInfo plain = new CallbackInfo("sendChat", true);
        send.invoke(receiver, ".structure", plain);
        assertFalse(plain.isCancelled());
        assertTrue(dispatched.isEmpty());
        bypass = false;
        panic = true;
        CallbackInfo blocked = new CallbackInfo("sendChat", true);
        send.invoke(receiver, ".meteor-only", blocked);
        assertTrue(blocked.isCancelled());
        assertTrue(dispatched.isEmpty());
    }

    @Test
    void realCommandClassifierAlwaysAcceptsDotAlongsideASavedAlternative() throws Exception {
        Method classify = isolatedHook("dihclient/commands/DihCommands", "isDihCommandMessage");
        Method body = isolatedHook("dihclient/commands/DihCommands", "commandBody");
        for (String alternative : List.of("%", "&", "#")) {
            prefix = alternative;
            assertEquals(true, classify.invoke(null, ".seedinput 42"));
            assertEquals(true, classify.invoke(null, alternative + "seedinput 42"));
            assertEquals(false, classify.invoke(null, "ordinary chat"));
            assertEquals("seedinput 42", body.invoke(null, ".seedinput 42"));
            assertEquals("seedinput 42", body.invoke(null, alternative + "seedinput 42"));
        }
    }

    @Test
    void suggestionsOwnPartialKnownRootsAndReleaseMeteorOnlyRoots() throws Exception {
        assertTrue(priority(SUGGEST_MIXIN) > 1000);
        Method suggest = isolatedHook(SUGGEST_MIXIN, "dih$isDihCommandInput");
        for (String input : List.of(".", ".str", ".structure village", ".auto", ".seedinput", ".scriptroot")) {
            assertEquals(true, suggest.invoke(null, input), input);
        }
        for (String input : List.of(".meteor-only", ".structureother", ".str argument", ".str ", "hello", "/structure")) {
            assertEquals(false, suggest.invoke(null, input), input);
        }
        prefix = "%";
        assertEquals(true, suggest.invoke(null, "%unknown"));
        assertEquals(true, suggest.invoke(null, ".structure"));
        panic = true;
        assertEquals(false, suggest.invoke(null, "%structure"));
    }

    private static int priority(String owner) throws Exception {
        int[] priority = {1000};
        try (var input = DihMeteorCommandRoutingTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) return null;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public void visit(String name, Object value) {
                            if (name.equals("priority")) priority[0] = (int) value;
                        }
                    };
                }
            }, ClassReader.SKIP_CODE);
        }
        return priority[0];
    }

    private static Method isolatedHook(String owner, String hook) throws Exception {
        String generated = "dihclient/util/IsolatedMeteorHook";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, generated, null, "java/lang/Object", null);
        var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        try (var input = DihMeteorCommandRoutingTest.class.getResourceAsStream("/" + owner + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals(hook)) return null;
                    var target = writer.visitMethod(Opcodes.ACC_PUBLIC | (access & Opcodes.ACC_STATIC), "run", descriptor, null, exceptions);
                    return new MethodVisitor(Opcodes.ASM9, target) {
                        @Override public void visitMethodInsn(int opcode, String calledOwner, String name, String descriptor, boolean isInterface) {
                            if (calledOwner.equals("dihclient/commands/DihCommands")
                                || calledOwner.equals("dihclient/util/DihClientMessaging")
                                || calledOwner.equals("dihclient/util/DihCompatManager")) {
                                calledOwner = "dihclient/util/DihMeteorCommandRoutingTest";
                            }
                            super.visitMethodInsn(opcode, calledOwner, name, descriptor, isInterface);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        writer.visitEnd();
        byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(DihMeteorCommandRoutingTest.class.getClassLoader()) {
            Class<?> define() { return defineClass(generated.replace('/', '.'), bytes, 0, bytes.length); }
        }.define();
        return type.getDeclaredMethods()[0];
    }

    public static boolean plainChatBypass() { return bypass; }
    public static String effectivePrefix() { return prefix; }
    public static boolean commandsBlockedByPanic() { return panic; }
    public static boolean isDihCommandMessage(String value) { return value != null && (value.startsWith(prefix) || value.startsWith(".")); }
    public static boolean isBlockedPanicCommandMessage(String value) { return panic; }
    public static String commandBody(String value) { return value.substring(prefix.length()).trim(); }
    public static boolean isKnownCommandBody(String body) { return ROOTS.contains(body.split(" ", 2)[0]); }
    public static boolean hasCommandPrefix(String body) {
        return body.contains(" ") ? isKnownCommandBody(body) : ROOTS.stream().anyMatch(root -> root.startsWith(body));
    }
    public static boolean shouldDeferCommandToMeteor(String value, boolean owned) throws Exception {
        return (boolean) compat.getMethod("shouldDeferCommandToMeteor", String.class, boolean.class).invoke(null, value, owned);
    }
    public static void rememberRecentChat(String value) {}
    public static boolean dispatch(String body) { dispatched.add(body); return true; }
}
