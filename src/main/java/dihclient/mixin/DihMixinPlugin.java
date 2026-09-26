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
        "DihSodiumDefaultChunkRendererMixin"
    );

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
