package dihclient.mixin.security;

import dihclient.DihClientAddon;
import dihclient.security.DihProtectorServerPackFailureGuard;
import dihclient.security.DihProtectorPackStrip;
import dihclient.util.DihNotifications;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.server.packs.DownloadQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mixin(ServerPackManager.class)
public abstract class DihProtectorServerPackManagerMixin {

    @Unique private static long dih$lastRecoveryReloadMs;
    @Unique private static long dih$lastRecoveryToastMs;

    @Shadow public abstract void popAll();

    @Inject(method = "onDownload", at = @At("HEAD"))
    private void dih$makeFailedServerPackBatchAtomic(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;
        DihProtectorServerPackFailureGuard.suppressServerPacksTemporarily();

        try {
            Map<UUID, ?> downloaded = result.downloaded();
            if (downloaded != null) downloaded.clear();
        } catch (Throwable error) {
            DihClientAddon.LOG.warn("[DihProtector] Failed to clear partial server-pack batch.", error);
        }
    }

    @Inject(method = "onDownload", at = @At("TAIL"))
    private void dih$recoverFromFailedServerPackDownload(Collection<?> data, DownloadQueue.BatchResult result, CallbackInfo ci) {
        if (result == null || result.failed().isEmpty()) return;

        DihProtectorServerPackFailureGuard.suppressServerPacksTemporarily();
        DihProtectorPackStrip.clearAll();

        try {
            popAll();
        } catch (Throwable error) {
            DihClientAddon.LOG.warn("[DihProtector] Failed to clear server packs after download failure.", error);
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            client.execute(() -> {
                try {
                    client.getDownloadedPackSource().popAll();
                    long now = System.currentTimeMillis();
                    if (now - dih$lastRecoveryToastMs > 5000L) {
                        dih$lastRecoveryToastMs = now;
                        DihNotifications.warning("Server resource pack failed. Restored client resources.");
                    }
                    if (now - dih$lastRecoveryReloadMs > 1000L) {
                        dih$lastRecoveryReloadMs = now;
                        client.reloadResourcePacks();
                    }
                } catch (Throwable error) {
                    DihClientAddon.LOG.warn("[DihProtector] Failed to clear downloaded pack source after download failure.", error);
                }
            });
        }
    }
}
