package dihclient.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.injection.Inject;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/** Guards the actual vanilla call path: setScreenAndShow is bypassed by normal container screens. */
class StorageCaptureHookTest {
    @Test
    void storageCaptureHooksTheScreenTransitionUsedByVanillaOpeningAndClosing() throws Exception {
        var hook = Arrays.stream(DihGuiSetScreenMixin.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("dih$storageScreenChanged")).findFirst().orElse(null);
        assertNotNull(hook, "Chest memory must observe Gui screen transitions, not only Minecraft.setScreenAndShow");
        var injection = hook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertTrue(Arrays.asList(injection.method()).contains("setScreen"));
        boolean modern = Arrays.stream(Gui.class.getDeclaredMethods()).anyMatch(method -> method.getName().equals("setScreen"));
        Class<?> screenOwner = modern ? Gui.class : Minecraft.class;
        String screenOwnerName = screenOwner.getName().replace('.', '/');
        assertNotNull(screenOwner.getDeclaredMethod("setScreen", net.minecraft.client.gui.screens.Screen.class));
        assertTrue(calls("net/minecraft/client/gui/screens/MenuScreens$ScreenConstructor", "fromPacket",
            screenOwnerName, "setScreen"));
        assertTrue(calls("net/minecraft/client/player/LocalPlayer", "clientSideCloseContainer",
            screenOwnerName, "setScreen"));
        assertTrue(calls("dihclient/mixin/DihGuiSetScreenMixin", hook.getName(),
            "baritone/cache/ChestMemory", "onScreenChange"));
    }

    private static boolean calls(String className, String methodName, String calledOwner, String calledName) throws IOException {
        var found = new AtomicBoolean();
        try (var input = StorageCaptureHookTest.class.getResourceAsStream("/" + className + ".class")) {
            assertNotNull(input, className);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (!name.equals(methodName)) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            if (owner.equals(calledOwner) && name.equals(calledName)) found.set(true);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return found.get();
    }
}
