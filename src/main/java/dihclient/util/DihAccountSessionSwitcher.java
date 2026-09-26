package dihclient.util;

import dihclient.DihClientAddon;
import dihclient.mixin.accessor.DihMinecraftAccessor;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.server.Services;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public final class DihAccountSessionSwitcher {
    private static User originalUser;
    private static String lastError = "";

    private DihAccountSessionSwitcher() {
    }

    public static User getOriginalUser() {
        if (originalUser == null) originalUser = Minecraft.getInstance().getUser();
        return originalUser;
    }

    public static boolean setSession(User user) {
        return setSession(user, DihAuthServices.mojang(DihAuthNetwork.directProxy()));
    }

    public static boolean setSession(User user, DihAuthServices authService) {
        lastError = "";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (originalUser == null) originalUser = mc.getUser();
            DihMinecraftAccessor accessor = (DihMinecraftAccessor) mc;
            DihAuthServices userApiAuthService = DihAuthServices.mojang(DihAuthNetwork.directProxy());
            Services services = authService.services(mc.gameDirectory);
            UserApiService apiService = userApiAuthService.userApi(user.getAccessToken());
            FriendsService friendsService = userApiAuthService.friends(user.getAccessToken());
            RemoteFriendListUpdateHandler friendListUpdateHandler = new RemoteFriendListUpdateHandler(friendsService, mc);
            Path skinCachePath = mc.gameDirectory.toPath().resolve("assets").resolve("skins");

            accessor.dih$setServices(services);
            accessor.dih$setUser(user);
            accessor.dih$setUserApiService(apiService);
            accessor.dih$setRemoteFriendListUpdateHandler(friendListUpdateHandler);
            accessor.dih$setPlayerSocialManager(new PlayerSocialManager(mc, apiService, friendsService, friendListUpdateHandler));
            accessor.dih$setProfileKeyPairManager(ProfileKeyPairManager.create(apiService, user, mc.gameDirectory.toPath()));
            accessor.dih$setReportingContext(ReportingContext.create(ReportEnvironment.local(), apiService));
            accessor.dih$setProfileFuture(CompletableFuture.supplyAsync(() -> mc.services().sessionService().fetchProfile(mc.getUser().getProfileId(), true), Util.nonCriticalIoPool()));
            accessor.dih$setSkinManager(new SkinManager(skinCachePath, services,
                new SkinTextureDownloader(DihAuthNetwork.directProxy(), mc.getTextureManager(), mc), mc));
            return true;
        } catch (Exception e) {
            lastError = shortError(e);
            DihClientAddon.LOG.error("Failed to switch Dih account session", e);
            return false;
        }
    }

    public static String lastError() {
        return lastError == null ? "" : lastError;
    }

    private static String shortError(Throwable error) {
        if (error == null) return "unknown error";
        String name = error.getClass().getSimpleName();
        String message = error.getMessage();
        if (message == null || message.isBlank()) return name;
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (message.length() > 120) message = message.substring(0, 117) + "...";
        return name + ": " + message;
    }
}
