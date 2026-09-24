package dihclient.mixin.security;

import dihclient.DihClientAddon;
import dihclient.security.DihProtector;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Path;
import java.util.UUID;

@Mixin(DownloadQueue.class)
public class DihProtectorDownloadQueueMixin {
    @Shadow @Final private Path cacheDir;

    @SuppressWarnings("UnresolvedMixinReference")
    @ModifyExpressionValue(
        method = {"lambda$runDownload$0", "method_55485"},
        at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;resolve(Ljava/lang/String;)Ljava/nio/file/Path;"),
        require = 0)
    private Path dih$isolatePackCache(Path original, @Local(argsOnly = true) UUID packId) {
        if (!DihProtector.shouldIsolatePackCache()) return original;
        if (original == null || cacheDir == null || packId == null) return original;
        Path parent = original.getParent();
        if (parent == null || !parent.equals(cacheDir)) return original;

        UUID accountId = Minecraft.getInstance().getUser().getProfileId();
        if (accountId == null) {
            DihClientAddon.LOG.warn("[DihProtector] Cannot isolate resource-pack cache: account UUID is null.");
            return original;
        }
        return cacheDir.resolve(accountId.toString()).resolve(packId.toString());
    }
}
