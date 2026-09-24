package dihclient.mixin.security;

import dihclient.security.DihFromPacketAccess;
import dihclient.security.DihProtector;
import dihclient.security.DihProtectorTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TranslatableContents.class)
public abstract class DihProtectorTranslatableContentsMixin implements DihFromPacketAccess {

    @Unique
    private boolean dih$fromPacket;

    @Unique
    private boolean dih$silent;

    @Override
    public void dih$setFromPacket() {
        this.dih$fromPacket = true;
    }

    @Override
    public void dih$setSilent() {
        this.dih$silent = true;
    }

    @Unique
    private static final String DIH_ALLOW = "\0__dih_allow__";

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;)Ljava/lang/String;")
    )
    private String dih$wrapGetOrDefault(Language instance, String id, Operation<String> original) {
        String result = dih$handle(id, id);
        if (result == DIH_ALLOW) return original.call(instance, id);
        return result;
    }

    @WrapOperation(
        method = "decompose",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/locale/Language;getOrDefault(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")
    )
    private String dih$wrapGetOrDefaultFallback(Language instance, String idArg, String defaultValue,
                                                   Operation<String> original) {
        String result = dih$handle(idArg, defaultValue);
        if (result == DIH_ALLOW) return original.call(instance, idArg, defaultValue);
        return result;
    }

    @Unique
    private String dih$handle(String translationKey, String defaultValue) {

        if (dih$silent) return DIH_ALLOW;
        if (!this.dih$fromPacket) return DIH_ALLOW;
        if (!DihProtector.shouldProtectTranslationKeys()) return DIH_ALLOW;

        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return DIH_ALLOW;
        }
        if (mc == null || mc.hasSingleplayerServer()) return DIH_ALLOW;

        String replacement = DihProtectorTracker.translationReplacement(translationKey, defaultValue);
        return replacement == null ? DIH_ALLOW : replacement;
    }
}
