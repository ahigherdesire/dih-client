package dihclient.mixin.security;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dihclient.security.DihFontFileGuard;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.InputStream;

/**
 * minecraft2shell guard: a "ttf" font provider passes the font file's bytes straight to native FreeType. Swap the
 * file open for one that refuses files from server and world packs, so a malicious server pack cannot feed FreeType.
 * The same Resource is checked and opened, so there is no gap between the check and the read.
 */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class DihTrueTypeFontGuardMixin {
    @WrapOperation(method = "load", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/packs/resources/ResourceManager;open(Lnet/minecraft/resources/Identifier;)Ljava/io/InputStream;"))
    private InputStream dih$refuseUntrustedFontFile(ResourceManager manager, Identifier id,
                                                    Operation<InputStream> original) throws IOException {
        return DihFontFileGuard.open(manager, id);
    }
}
