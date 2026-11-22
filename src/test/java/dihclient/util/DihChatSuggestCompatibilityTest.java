package dihclient.util;

import dihclient.mixin.DihMixinPlugin;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DihChatSuggestCompatibilityTest {
    private static final String TARGET = "net/minecraft/client/gui/components/CommandSuggestions";
    private static final String MIXIN = "dihclient/mixin/DihChatSuggestMixin";

    @Test
    void completionHookIsEnabledAndItsShadowsExistOnThisMinecraftVersion() throws Exception {
        ClassNode target = read(TARGET);
        ClassNode mixin = read(MIXIN);
        assertTrue(new DihMixinPlugin().shouldApplyMixin(TARGET.replace('/', '.'), MIXIN.replace('/', '.')));
        AnnotationNode annotation = mixin.invisibleAnnotations.stream()
            .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        int valueIndex = annotation.values.indexOf("value");
        assertTrue(((List<?>) annotation.values.get(valueIndex + 1)).contains(Type.getObjectType(TARGET)));
        for (FieldNode field : mixin.fields) {
            if (!shadow(field.visibleAnnotations, field.invisibleAnnotations)) continue;
            assertTrue(target.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)),
                "Missing target field: " + field.name + field.desc);
        }
        for (MethodNode method : mixin.methods) {
            if (!shadow(method.visibleAnnotations, method.invisibleAnnotations)) continue;
            assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(method.name) && m.desc.equals(method.desc)),
                "Missing target method: " + method.name + method.desc);
        }
    }

    @Test
    void legacyCompletionAvoidsServerUsageRendering() throws Exception {
        ClassNode target = read(TARGET);
        if (target.fields.stream().anyMatch(f -> f.name.equals("currentParseIsCommand"))) return;
        ClassNode mixin = read(MIXIN);
        assertTrue(mixin.methods.stream().anyMatch(m -> m.name.equals("dih$updateDihCommandInfo")),
            "Legacy chat must have the real completion hook");
        for (MethodNode method : mixin.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    assertFalse(call.name.equals("updateUsageInfo"), "Legacy usage rendering uses the server dispatcher");
                }
            }
        }
    }

    private static boolean shadow(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        return (visible != null && visible.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")))
            || (invisible != null && invisible.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")));
    }

    private static ClassNode read(String name) throws Exception {
        try (var input = DihChatSuggestCompatibilityTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
