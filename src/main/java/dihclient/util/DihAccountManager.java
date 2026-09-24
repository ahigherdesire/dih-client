package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.io.File;
import java.util.Iterator;
import java.util.List;

public final class DihAccountManager extends PersistentNbtManager<DihAccount> implements Iterable<DihAccount> {
    private static final DihAccountManager INSTANCE = new DihAccountManager();

    private DihAccountManager() {
    }

    public static DihAccountManager get() {
        INSTANCE.ensureLoaded();
        DihMeteorImport.ensureImported();
        INSTANCE.ensureStableIds();
        return INSTANCE;
    }

    private synchronized void ensureStableIds() {
        boolean changed = false;
        for (DihAccount account : items) {
            if (account == null) continue;
            account.stableId();
            changed |= account.generatedStableId;
            account.generatedStableId = false;
        }
        if (changed) save();
    }

    public synchronized DihAccount findById(String id) {
        if (id == null || id.isBlank()) return null;
        for (DihAccount account : items) {
            if (id.equals(account.stableId())) return account;
        }
        return null;
    }

    public synchronized void applyResolvedCredentials(DihAccount resolved) {
        if (resolved == null || resolved.id == null) return;
        DihAccount stored = findById(resolved.id);
        if (stored == null) return;
        stored.label = resolved.label;
        stored.token = resolved.token;
        stored.sessionToken = resolved.sessionToken;
        stored.username = resolved.username;
        stored.uuid = resolved.uuid;
        stored.sessionTokenExpiresAt = resolved.sessionTokenExpiresAt;
        save();
    }

    public synchronized boolean rename(String accountId, String newLabel) {
        if (newLabel == null || newLabel.isBlank()) return false;
        DihAccount stored = findById(accountId);
        if (stored == null || stored.type != DihAccountType.Cracked) return false;
        String trimmed = newLabel.trim();
        if (trimmed.equals(stored.label)) return true;
        for (DihAccount account : items) {
            if (account != stored && trimmed.equalsIgnoreCase(account.label)) return false;
        }
        stored.label = trimmed;
        stored.username = trimmed;
        stored.uuid = net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(trimmed).toString();
        save();
        return true;
    }

    public synchronized void invalidateSessionToken(String accountId) {
        DihAccount stored = findById(accountId);
        if (stored == null || stored.type != DihAccountType.Microsoft) return;
        stored.token = "";
        stored.sessionTokenExpiresAt = 0L;
        save();
    }

    @Override
    protected File saveFile() {
        return new File(Minecraft.getInstance().gameDirectory, "dih-accounts.nbt");
    }

    @Override
    protected String listKey() {
        return "accounts";
    }

    @Override
    protected DihAccount fromTag(CompoundTag tag) {
        return new DihAccount().fromTag(tag);
    }

    @Override
    protected CompoundTag toTag(DihAccount item) {
        return item.toTag();
    }

    @Override
    protected String describe() {
        return "Dih accounts";
    }

    public synchronized void add(DihAccount account) {
        if (account == null) return;
        items.add(account);
        save();
    }

    public synchronized int addAll(List<DihAccount> accounts) {
        if (accounts == null || accounts.isEmpty()) return 0;
        int added = 0;
        for (DihAccount account : accounts) {
            if (account != null) {
                items.add(account);
                added++;
            }
        }
        if (added > 0) save();
        return added;
    }

    public synchronized void remove(DihAccount account) {
        if (items.remove(account)) save();
    }

    public synchronized int removeExpired() {
        int removed = 0;
        Iterator<DihAccount> iterator = items.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().checkStatus == DihAccount.CheckStatus.EXPIRED) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) save();
        return removed;
    }

    public void login(DihAccount account) {
        if (account == null) return;
        Thread thread = new Thread(() -> {
            if (account.fetchInfo() && account.login()) {
                save();
                DihClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
            } else {
                DihClientMessaging.sendPrefixed("Failed to login account: " + account.displayName() + account.failureSuffix());
            }
        }, "Dih-Account-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public void loginMicrosoft(DihAccount account) {
        if (account == null || account.type != DihAccountType.Microsoft) return;
        DihMicrosoftLogin.getRefreshToken(refreshToken -> {
            if (refreshToken == null) {
                DihClientMessaging.sendPrefixed("Microsoft login cancelled or failed.");
                return;
            }
            account.label = refreshToken;
            Thread thread = new Thread(() -> {
                if (account.fetchInfo() && account.login()) {
                    synchronized (this) {
                        if (!items.contains(account)) items.add(account);
                    }
                    save();
                    DihClientMessaging.sendPrefixed("Logged in as " + account.displayName() + ".");
                } else {
                    DihClientMessaging.sendPrefixed("Failed to login Microsoft account" + account.failureSuffix() + ".");
                }
            }, "Dih-Microsoft-Login");
            thread.setDaemon(true);
            thread.start();
        });
    }

    @Override
    public Iterator<DihAccount> iterator() {
        return all().iterator();
    }
}
