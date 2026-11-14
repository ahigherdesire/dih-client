package dihclient.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.minecraft.TelemetrySession;
import com.mojang.authlib.minecraft.UserApiService;
import dihclient.security.DihProtector;
import net.minecraft.client.telemetry.ClientTelemetryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.Executor;

/**
 * Telemetry off at the call site as well as in authlib (DihProtectorYggdrasilMixin): NeoForge loads authlib outside
 * the transformable game layer, so there only this one applies.
 */
@Mixin(ClientTelemetryManager.class)
public class DihProtectorTelemetryMixin {
    @WrapOperation(method = "createEventSender", at = @At(value = "INVOKE",
        target = "Lcom/mojang/authlib/minecraft/UserApiService;newTelemetrySession(Ljava/util/concurrent/Executor;)Lcom/mojang/authlib/minecraft/TelemetrySession;"))
    private TelemetrySession dih$disableTelemetrySession(UserApiService service, Executor executor, Operation<TelemetrySession> original) {
        if (DihProtector.shouldDisableTelemetry()) return TelemetrySession.DISABLED;
        return original.call(service, executor);
    }
}
