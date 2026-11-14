package dihclient.mixin;

import dihclient.platform.DihLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class DihMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> SODIUM_MIXINS = Set.of(
        "DihSodiumBlockRendererMixin",
        "DihSodiumBlockContextMixin",
        "DihSodiumLightDataAccessMixin",
        "DihSodiumDefaultFluidRendererMixin",
        "DihSodiumFluidRendererImplMixin",
        "DihSodiumRenderSectionManagerMixin",
        "DihSodiumDefaultChunkRendererMixin",
        "DihSodiumDefaultChunkRendererOitMixin"
    );

    /** Arguments of Sodium's {@code DefaultChunkRenderer.render} each wrapper of it is written for. */
    private static final java.util.Map<String, Integer> SODIUM_RENDER_WRAPPERS = java.util.Map.of(
        "DihSodiumDefaultChunkRendererMixin", 9,
        "DihSodiumDefaultChunkRendererOitMixin", 11
    );
    private int sodiumRenderArgs = -2; // -2: not read yet, -1: unreadable

    private boolean sodiumLoaded;
    private boolean indigoLoaded;
    private boolean opsecLoaded;
    private boolean exploitPreventerLoaded;
    private boolean replayModLoaded;
    private boolean flashbackLoaded;
    private boolean essentialLoaded;
    private boolean lithiumLoaded;

    private static final Set<String> EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS = Set.of(
        "DihProtectorTranslatableContentsMixin",
        "DihProtectorKeybindContentsMixin",
        "DihProtectorComponentSerializationMixin",
        "DihProtectorPacketProcessorMixin",
        "DihProtectorPacketDecoderMixin",
        "DihProtectorHttpUtilMixin",
        "DihProtectorConnectionTrackingMixin",
        "DihProtectorDownloadQueueMixin",
        "DihProtectorClientLanguageMixin",
        "DihProtectorDeprecatedTranslationsInfoMixin",
        "DihProtectorOptionsMixin",
        "DihProtectorKeyMappingRegistryImplMixin",
        "DihProtectorPayloadTypeRegistryImplMixin",
        "DihProtectorResourceLoaderImplMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
        sodiumLoaded = DihLoader.isModLoaded("sodium");
        indigoLoaded = DihLoader.isModLoaded("fabric-renderer-indigo");
        opsecLoaded = DihLoader.isModLoaded("opsec");
        exploitPreventerLoaded = DihLoader.isModLoaded("exploitpreventer");
        replayModLoaded = DihLoader.isModLoaded("replaymod");
        flashbackLoaded = DihLoader.isModLoaded("flashback");
        essentialLoaded = DihLoader.isModLoaded("essential")
            || DihLoader.isModLoaded("essential-container")
            || DihLoader.isModLoaded("essential-loader");
        lithiumLoaded = DihLoader.isModLoaded("lithium");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        // Dev bisecting: -Ddih.disableMixins=DihFooMixin,DihBarMixin skips those mixins.
        String disabled = System.getProperty("dih.disableMixins");
        if (disabled != null && java.util.Arrays.asList(disabled.split(",")).contains(simpleName)) return false;
        Integer renderArgs = SODIUM_RENDER_WRAPPERS.get(simpleName);
        if (renderArgs != null) return sodiumLoaded && sodiumRenderArgs(targetClassName) == renderArgs;
        if (SODIUM_MIXINS.contains(simpleName)) return sodiumLoaded;
        if ("DihFluidRendererMixin".equals(simpleName) && sodiumLoaded) return false;
        if ("DihReplayModGuiHandlerMixin".equals(simpleName) || "DihReplayStudioTeamMixin".equals(simpleName)) return replayModLoaded;
        if ("DihFlashbackConnectionMixin".equals(simpleName)) return flashbackLoaded;
        if (simpleName.startsWith("DihEssential")) return essentialLoaded;

        if (mixinClassName.startsWith("dihclient.mixin.indigo.")) return indigoLoaded && !sodiumLoaded;
        if (mixinClassName.startsWith("dihclient.mixin.lithium.")) return lithiumLoaded;

        if (mixinClassName.startsWith("dihclient.mixin.security.")) {
            if (opsecLoaded) return false;
            return !exploitPreventerLoaded || !EXPLOIT_PREVENTER_OVERLAP_SECURITY_MIXINS.contains(simpleName);
        }
        return true;
    }

    /**
     * How many arguments the installed Sodium's {@code render} takes, read from its class file without loading it.
     * A {@code @WrapMethod} must match them exactly, and Sodium changes them between versions (9 before 0.9, 11 in
     * 0.9 for 26.3); a mismatch fails when a world is joined, so neither wrapper applies to a shape we don't know.
     */
    private int sodiumRenderArgs(String targetClassName) {
        if (sodiumRenderArgs != -2) return sodiumRenderArgs;
        int args = -1;
        try {
            ClassNode node = org.spongepowered.asm.service.MixinService.getService().getBytecodeProvider()
                .getClassNode(targetClassName.replace('/', '.'));
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                if ("render".equals(method.name)) {
                    args = org.objectweb.asm.Type.getArgumentTypes(method.desc).length;
                    break;
                }
            }
        } catch (Exception | LinkageError e) {
            args = -1;
        }
        return sodiumRenderArgs = args;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
