package dihclient.util.multi;

import dihclient.util.DihAccount;
import dihclient.util.DihAccountManager;
import dihclient.util.DihAccountSessionSwitcher;
import dihclient.util.DihAccountType;
import dihclient.util.DihAuthNetwork;
import dihclient.util.DihAuthServices;
import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.Services;
import net.minecraft.util.SignatureValidator;

import java.util.Optional;

final class MultiIdentityResolver {
    record Identity(
        String accountId,
        DihAccountType type,
        User user,
        Services services,
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
            DihAuthServices authentication = DihAuthServices.mojang(DihAuthNetwork.directProxy());
            Services services = authentication.services(minecraft.gameDirectory);
            return new Identity(
                accountId,
                user.getAccessToken().isBlank() ? DihAccountType.Cracked : DihAccountType.Session,
                user,
                services,
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
        DihAuthServices authentication = account.type == DihAccountType.TheAltening
            ? DihAuthServices.theAltening(minecraft.getProxy())
            : DihAuthServices.mojang(DihAuthNetwork.directProxy());

        DihAuthServices mojangAuth = DihAuthServices.mojang(DihAuthNetwork.directProxy());
        Services services = authentication.services(minecraft.gameDirectory);
        return new Identity(account.id, account.type, user, services,
            keyManager(minecraft, mojangAuth, user), profileKeyValidator(services));
    }

    private static ProfileKeyPairManager keyManager(Minecraft minecraft, DihAuthServices auth, User user) {
        if (user.getAccessToken() == null || user.getAccessToken().isBlank()) return ProfileKeyPairManager.EMPTY_KEY_MANAGER;
        try {
            UserApiService userApi = auth.userApi(user.getAccessToken());
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
