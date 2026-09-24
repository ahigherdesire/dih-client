package dihclient.util.multi;

import dihclient.util.DihAccount;
import dihclient.util.DihAccountManager;
import dihclient.util.DihAccountSessionSwitcher;
import dihclient.util.DihAccountType;
import dihclient.util.DihAuthNetwork;
import com.mojang.authlib.Environment;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;

import java.util.Optional;

final class MultiIdentityResolver {
    private static final Environment ALTENING_ENVIRONMENT = new Environment(
        "http://sessionserver.thealtening.com",
        "http://authserver.thealtening.com",
        "https://api.mojang.com",
        "The Altening"
    );

    record Identity(
        String accountId,
        DihAccountType type,
        User user,
        MinecraftSessionService sessionService,
        ProfileKeyPairManager keyPairManager,

        SignatureValidator profileKeyValidator
    ) {
    }

    private MultiIdentityResolver() {
    }

    static Identity resolve(String accountId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MultiProfile.DEFAULT_ACCOUNT_ID.equals(accountId)) {
            User user = DihAccountSessionSwitcher.getOriginalUser();
            YggdrasilAuthenticationService authentication =
                new YggdrasilAuthenticationService(DihAuthNetwork.directProxy());
            Services services = Services.create(authentication, minecraft.gameDirectory);
            return new Identity(
                accountId,
                user.getAccessToken().isBlank() ? DihAccountType.Cracked : DihAccountType.Session,
                user,
                services.sessionService(),
                keyManager(minecraft, authentication, user),
                profileKeyValidator(services)
            );
        }

        DihAccount stored = DihAccountManager.get().findById(accountId);
        if (stored == null) throw new IllegalStateException("Account no longer exists");
        DihAccount account = copy(stored);
        if (!account.fetchInfoSilently()) {
            throw new IllegalStateException(account.lastError().isBlank() ? "Account authentication failed" : account.lastError());
        }
        DihAccountManager.get().applyResolvedCredentials(account);

        User user = new User(
            account.username,
            parseProfileId(account),
            accessToken(account),
            Optional.empty(),
            Optional.empty()
        );
        YggdrasilAuthenticationService authentication = account.type == DihAccountType.TheAltening
            ? new YggdrasilAuthenticationService(minecraft.getProxy(), ALTENING_ENVIRONMENT)
            : new YggdrasilAuthenticationService(DihAuthNetwork.directProxy());

        YggdrasilAuthenticationService mojangAuth =
            new YggdrasilAuthenticationService(DihAuthNetwork.directProxy());
        Services services = Services.create(authentication, minecraft.gameDirectory);
        return new Identity(account.id, account.type, user, services.sessionService(),
            keyManager(minecraft, mojangAuth, user), profileKeyValidator(services));
    }

    private static ProfileKeyPairManager keyManager(Minecraft minecraft, YggdrasilAuthenticationService auth, User user) {
        if (user.getAccessToken() == null || user.getAccessToken().isBlank()) return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        try {
            UserApiService userApi = auth.createUserApiService(user.getAccessToken());
            return ProfileKeyPairManager.create(userApi, user, minecraft.gameDirectory.toPath());
        } catch (RuntimeException ignored) {
            return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        }
    }

    private static SignatureValidator profileKeyValidator(Services services) {
        try {
            return services.canValidateProfileKeys() ? services.profileKeySignatureValidator() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static java.util.UUID parseProfileId(DihAccount account) {
        if (account.uuid != null && !account.uuid.isBlank()) {
            try {
                return UndashedUuid.fromStringLenient(account.uuid);
            } catch (RuntimeException ignored) {
                try {
                    return java.util.UUID.fromString(account.uuid);
                } catch (RuntimeException ignoredAgain) {
                }
            }
        }
        return UUIDUtil.createOfflinePlayerUUID(account.username);
    }

    private static String accessToken(DihAccount account) {
        if (account.type == DihAccountType.TheAltening) return safe(account.sessionToken);
        if (account.type == DihAccountType.Cracked || account.type == DihAccountType.Generated) return "";
        return safe(account.token);
    }

    private static DihAccount copy(DihAccount source) {
        DihAccount copy = new DihAccount();
        copy.id = source.stableId();
        copy.type = source.type;
        copy.label = safe(source.label);
        copy.token = safe(source.token);
        copy.sessionToken = safe(source.sessionToken);
        copy.username = safe(source.username);
        copy.uuid = safe(source.uuid);
        copy.sessionTokenExpiresAt = source.sessionTokenExpiresAt;
        return copy;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
