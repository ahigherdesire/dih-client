package dihclient.mixin.accessor;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.CompletableFuture;

@Mixin(Minecraft.class)
public interface DihMinecraftAccessor {
    @Mutable
    @Accessor("user")
    void dih$setUser(User user);

    @Mutable
    @Accessor("profileKeyPairManager")
    void dih$setProfileKeyPairManager(ProfileKeyPairManager manager);

    @Mutable
    @Accessor("userApiService")
    void dih$setUserApiService(UserApiService service);

    @Mutable
    @Accessor("skinManager")
    void dih$setSkinManager(SkinManager manager);

    @Mutable
    @Accessor("playerSocialManager")
    void dih$setPlayerSocialManager(PlayerSocialManager manager);

    @Mutable
    @Accessor("remoteFriendListUpdateHandler")
    void dih$setRemoteFriendListUpdateHandler(RemoteFriendListUpdateHandler handler);

    @Mutable
    @Accessor("reportingContext")
    void dih$setReportingContext(ReportingContext context);

    @Mutable
    @Accessor("profileFuture")
    void dih$setProfileFuture(CompletableFuture<ProfileResult> future);

    @Mutable
    @Accessor("services")
    void dih$setServices(Services services);

    @Accessor("rightClickDelay")
    int dih$getRightClickDelay();

    @Accessor("rightClickDelay")
    void dih$setRightClickDelay(int delay);

    @Accessor("missTime")
    int dih$getMissTime();

    @Invoker("startAttack")
    boolean dih$startAttack();
}
