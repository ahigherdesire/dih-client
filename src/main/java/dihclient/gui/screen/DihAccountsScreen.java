package dihclient.gui.screen;

import dihclient.gui.vanillaui.components.CompactDropdown;
import dihclient.gui.vanillaui.components.CompactOverlayButton;
import dihclient.util.DihConfig;
import dihclient.gui.vanillaui.direct.DirectLayout;
import dihclient.gui.vanillaui.components.CompactScrollbar;
import dihclient.gui.vanillaui.components.SectionPanel;
import dihclient.gui.vanillaui.components.ScrollState;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.util.DihAccount;
import dihclient.util.DihAccountManager;
import dihclient.util.DihBackgroundTasks;
import dihclient.util.DihAccountSessionSwitcher;
import dihclient.util.DihAccountType;
import dihclient.util.DihHttp;
import dihclient.util.DihMicrosoftLogin;
import dihclient.util.DihUiScale;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.blaze3d.Blaze3D;
import com.mojang.util.UndashedUuid;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.DihRenderTypes;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static dihclient.gui.screen.DihScreenPalette.*;

public class DihAccountsScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int BORDER_DEFAULT = 0xFF5F7CFF;
    private static final int DEFAULT_COLOR = 0xFF8EA0FF;
    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_MARGIN = 12;
    private static final int ROW_HEIGHT = 24;
    private static final int FORM_WIDTH = 292;
    private static final int PREVIEW_WIDTH = 200;
    private static final int PANEL_GAP = 8;
    private static final int PROVIDER_BUTTON_WIDTH = 66;
    private static final int TOP_PANEL_Y = 20;
    private static final int TOP_PANEL_HEIGHT = 178;
    private static final int LIST_TOP = 204;
    private static final int LIST_HEADER_HEIGHT = 22;
    private static final int LIST_BOTTOM_MARGIN = 12;
    private static final int FIELD_Y = 88;
    private static final int ACTION_Y = 114;
    private static final int SEARCH_Y = 140;
    private static final int FILTER_Y = 174;
    private static final int MAX_SKIN_LOOKUPS = 10;
    private static final int LIST_SCROLLBAR_WIDTH = 4;
    private static final int LIST_SCROLLBAR_GUTTER = 12;
    private static final int LIST_CHECK_BUTTON_WIDTH = 64;
    private static final int LIST_CLEAR_BUTTON_WIDTH = 96;
    private static final int LIST_HEADER_BUTTON_GAP = 4;

    private static final int LIST_HEADER_BUTTON_RESERVE = LIST_SCROLLBAR_GUTTER + 16 + LIST_CHECK_BUTTON_WIDTH + LIST_HEADER_BUTTON_GAP + LIST_CLEAR_BUTTON_WIDTH + 136;
    private static final long CANCEL_BUTTON_DELAY_MS = 5000L;
    private static final String TEXTURES_PROPERTY = "textures";
    private static final int SHARE_BUTTON_OUTLINE = 0xFFFF0000;
    private static final int SHARE_GLYPH_PAGE = 0xFF15151B;

    private final Screen parent;
    private final List<CompactOverlayButton> buttons = new ArrayList<>();
    private final List<AccountRow> accountRows = new ArrayList<>();
    private final Map<String, SkinLookup> skinLookups = new LinkedHashMap<>(16, 0.75F, true);
    private final ScrollState savedListScroll = new ScrollState();

    private DihAccountType categoryFilter = null;
    private EditBox labelField;
    private EditBox tokenField;
    private EditBox searchField;

    private DihAccount renamingAccount;
    private Model.Simple widePlayerModel;
    private Model.Simple slimPlayerModel;
    private DihAccountType type = DihAccountType.Cracked;
    private DihAccount selectedAccount;
    private String searchQuery = "";
    private String pendingSearchQuery = "";
    private boolean searchDirty;
    private Operation operation = Operation.NONE;
    private int operationId;
    private long operationStartedAtMs;
    private Future<?> operationTask;
    private CompactOverlayButton cancelOperationButton;
    private CompactOverlayButton checkButton;
    private int savedListScrollOffset;
    private boolean accountScrollbarDragging;
    private int accountScrollbarGrabOffset;
    private boolean previewDragging;
    private float previewRotationX = -5.0F;
    private float previewRotationY = 30.0F;
    private double previewAutoRotationStartTime = Blaze3D.getTime();
    private double lastPreviewMouseX;
    private double lastPreviewMouseY;
    private boolean accountSnapshotDirty = true;
    private long accountSnapshotRevision;
    private List<DihAccount> cachedAccountSnapshot = List.of();
    private long cachedFilteredRevision = Long.MIN_VALUE;
    private String cachedAccountQuery = "";
    private int cachedAccountFilterMask = Integer.MIN_VALUE;
    private List<DihAccount> cachedFilteredAccounts = List.of();
    private List<DihAccount> cachedDisplaySource;
    private List<DisplayAccountRow> cachedDisplayAccounts = List.of();

    private static final int POPUP_NONE = 0;
    private static final int POPUP_GENERATOR = 1;
    private static final int POPUP_CLEAR = 2;
    private int activePopup = POPUP_NONE;
    private int generatorMode;
    private EditBox generatorCountField;
    private EditBox generatorPasswordField;
    private net.minecraft.client.gui.components.MultiLineEditBox generatorListField;
    private String generatorResult = "";
    private final List<CompactOverlayButton> popupButtons = new ArrayList<>();
    private final List<CompactDropdown> popupDropdowns = new ArrayList<>();

    public DihAccountsScreen(Screen parent) {
        super(Component.literal("Accounts"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int panelX = panelX();
        int fieldY = FIELD_Y;
        this.labelField = new EditBox(this.font, panelX + 18, fieldY, FORM_WIDTH - 36, 18, Component.literal("Username"));
        this.labelField.setHint(Component.literal("Username"));
        this.labelField.setMaxLength(256);
        this.addRenderableWidget(labelField);
        this.tokenField = new EditBox(this.font, panelX + 18, fieldY, FORM_WIDTH - 36, 18, Component.literal("Token"));
        this.tokenField.setHint(Component.literal("Token"));
        this.tokenField.setMaxLength(4096);
        this.addRenderableWidget(tokenField);
        this.searchField = new EditBox(this.font, panelX + 18, SEARCH_Y, FORM_WIDTH - 36, 18, Component.literal("Search accounts"));
        this.searchField.setHint(Component.literal("Search nickname..."));
        this.searchField.setMaxLength(64);
        this.searchField.setResponder(value -> {
            pendingSearchQuery = safeTrim(value);
            searchDirty = true;
        });
        this.addRenderableWidget(searchField);
        this.widePlayerModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER), DihRenderTypes::skinPreview);
        this.slimPlayerModel = new Model.Simple(this.minecraft.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), DihRenderTypes::skinPreview);
        this.generatorCountField = new EditBox(this.font, 0, 0, 58, 16, Component.literal("Count"));
        this.generatorCountField.setHint(Component.literal("Count"));
        this.generatorCountField.setMaxLength(4);
        this.generatorCountField.setValue("10");
        this.generatorPasswordField = new EditBox(this.font, 0, 0, 186, 16, Component.literal("Password"));
        this.generatorPasswordField.setHint(Component.literal("Password"));
        this.generatorPasswordField.setMaxLength(16);
        this.generatorPasswordField.setValue(DihConfig.getGlobal().accountGenSharedPassword);
        this.generatorListField = new net.minecraft.client.gui.components.MultiLineEditBox.Builder()
            .setX(0).setY(0)
            .setPlaceholder(Component.literal("Paste names here, one per line or name:password"))
            .build(this.font, 240, 108, Component.literal("Account name list"));
        updateInputVisibility();
        rebuildButtons();
    }

    @Override
    public void tick() {
        super.tick();
        if (!searchDirty) return;
        searchDirty = false;
        searchQuery = pendingSearchQuery;
        savedListScrollOffset = 0;
        savedListScroll.jumpTo(0, 0);
        rebuildButtons();
    }

    private void rebuildButtons() {
        buttons.clear();
        accountRows.clear();
        checkButton = null;
        int panelX = panelX();
        int formX = panelX + 10;
        buttons.add(CompactOverlayButton.create(10, 10, 76, 18, Component.literal("Back"), b -> this.minecraft.gui.setScreen(parent)).setVariant(CompactOverlayButton.Variant.SECONDARY));
        if (!narrowLayout()) {
        int y = 58;
        addProviderButton(formX + 8, y, DihAccountType.Cracked, "Cracked");
        addProviderButton(formX + 77, y, DihAccountType.TheAltening, "Altening");
        addProviderButton(formX + 146, y, DihAccountType.Session, "Session");
        addProviderButton(formX + 215, y, DihAccountType.Microsoft, "Microsoft");

        int actionY = type == DihAccountType.Microsoft ? 84 : ACTION_Y;
        int addWidth = type == DihAccountType.Microsoft ? FORM_WIDTH - 16 : 116;
        int addHeight = type == DihAccountType.Microsoft ? 20 : 18;
        CompactOverlayButton add = CompactOverlayButton.create(formX + 8, actionY, addWidth, addHeight, Component.literal(addButtonLabel()), b -> addAccount()).setVariant(CompactOverlayButton.Variant.PRIMARY);
        add.active = !isBusy();
        buttons.add(add);
        if (type != DihAccountType.Microsoft) {
            CompactOverlayButton clear = CompactOverlayButton.create(formX + 128, ACTION_Y, 64, 18, Component.literal("Clear"), b -> clearFields()).setVariant(CompactOverlayButton.Variant.SECONDARY);
            clear.active = !isBusy();
            buttons.add(clear);
        }
        int cancelX = type == DihAccountType.Microsoft ? formX + 8 : formX + 196;
        int cancelY = type == DihAccountType.Microsoft ? 114 : ACTION_Y;
        int cancelWidth = type == DihAccountType.Microsoft ? FORM_WIDTH - 16 : 88;
        CompactOverlayButton cancel = CompactOverlayButton.create(cancelX, cancelY, cancelWidth, 18, Component.literal("Cancel"), b -> cancelOperation()).setVariant(CompactOverlayButton.Variant.DANGER);
        cancel.visible = shouldShowCancelOperationButton();
        cancelOperationButton = cancel;
        buttons.add(cancel);
        DihAccountType[] filterTypes = {DihAccountType.Cracked, DihAccountType.Microsoft, DihAccountType.Session, DihAccountType.TheAltening, DihAccountType.Generated};
        String[] filterLabels = {"Cracked", "Microsoft", "Session", "Altening", "Generated"};
        for (int i = 0; i < filterTypes.length; i++) {
            addFilterButton(formX + 8 + i * 56, FILTER_Y, filterTypes[i], filterLabels[i]);
        }
        }

        if (!compactListLayout()) {
            boolean checking = operation == Operation.CHECK;

            int checkX = rowRight() - 8 - LIST_CHECK_BUTTON_WIDTH;
            CompactOverlayButton check = CompactOverlayButton.create(
                    checkX, listTop() + 3, LIST_CHECK_BUTTON_WIDTH, 16,
                    Component.literal(checking ? "Checking" : "Check"), b -> checkExpiredAccounts());
            check.setVariant(CompactOverlayButton.Variant.SECONDARY);
            check.active = !isBusy() && !accountSnapshot().isEmpty();
            buttons.add(check);
            checkButton = check;

            CompactOverlayButton clearExpired = CompactOverlayButton.create(
                    checkX - LIST_HEADER_BUTTON_GAP - LIST_CLEAR_BUTTON_WIDTH, listTop() + 3, LIST_CLEAR_BUTTON_WIDTH, 16,
                    Component.literal("Clear Expired"), b -> clearExpiredAccounts());
            clearExpired.setVariant(CompactOverlayButton.Variant.DANGER);
            clearExpired.active = !isBusy() && expiredAccountCount() > 0;
            buttons.add(clearExpired);

            CompactOverlayButton generate = CompactOverlayButton.create(
                    clearExpired.getX() - LIST_HEADER_BUTTON_GAP - 60, listTop() + 3, 60, 16,
                    Component.literal("Generate"), b -> openGenerator());
            generate.setVariant(CompactOverlayButton.Variant.SECONDARY);
            buttons.add(generate);

            CompactOverlayButton clearList = CompactOverlayButton.create(
                    generate.getX() - LIST_HEADER_BUTTON_GAP - 64, listTop() + 3, 64, 16,
                    Component.literal("Clear List"), b -> openClearConfirm());
            clearList.setVariant(CompactOverlayButton.Variant.DANGER);
            clearList.active = !accountSnapshot().isEmpty();
            buttons.add(clearList);
        }

        List<DisplayAccountRow> displayAccounts = displayAccounts();
        if (compactListLayout()) return;
        int maxScroll = savedMaxScroll(displayAccounts.size());
        savedListScrollOffset = quantizeScrollOffset(savedListScrollOffset, ROW_HEIGHT, maxScroll);
        savedListScroll.jumpTo(savedListScrollOffset, maxScroll);
        int firstVisible = savedListScrollOffset / ROW_HEIGHT;
        int rowY = savedRowsTop() - (savedListScrollOffset % ROW_HEIGHT);
        for (int i = firstVisible; i < displayAccounts.size() && rowY + ROW_HEIGHT - 3 <= savedRowsBottom(); i++) {
            DisplayAccountRow displayRow = displayAccounts.get(i);
            DihAccount account = displayRow.account();
            if (rowY + ROW_HEIGHT - 3 <= savedRowsTop()) {
                rowY += ROW_HEIGHT;
                continue;
            }
            boolean current = displayRow.defaultAccount() ? isCurrentDefaultAccount() : isCurrentAccount(account);

            boolean renameable = !displayRow.defaultAccount() && account.type == DihAccountType.Cracked;
            CompactOverlayButton login = CompactOverlayButton.create(rowRight() - (displayRow.defaultAccount() ? 62 : renameable ? 114 : 88), rowButtonY(rowY, 16), 54, 16, Component.literal(current ? "Active" : "Login"), b -> {
                if (displayRow.defaultAccount()) loginDefaultAccount();
                else loginAccount(account);
            });
            login.setVariant(current ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.PRIMARY);
            login.setSelected(current).setAnimationKey("acct-active:" + (displayRow.defaultAccount() ? "default" : account.stableId()));
            login.active = !isBusy() && !current;
            CompactOverlayButton delete = null;
            CompactOverlayButton share = null;
            CompactOverlayButton rename = null;
            if (!displayRow.defaultAccount()) {
                if (renameable) {
                    rename = CompactOverlayButton.create(rowRight() - 54, rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> startRename(account));
                    rename.setVariant(CompactOverlayButton.Variant.SECONDARY).setIcon(dihclient.util.DihUiIcons.EDIT);
                    rename.active = !isBusy();
                }

                delete = CompactOverlayButton.create(rowRight() - 28, rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> deleteAccount(account));
                delete.setVariant(CompactOverlayButton.Variant.DANGER).setIcon(dihclient.util.DihUiIcons.TRASH);
                delete.active = !isBusy();
            }
            if (hasShareableSessionToken(account) && !shareableSessionToken(account).isBlank()) {
                int shareX = displayRow.defaultAccount() ? rowRight() - 88 : rowRight() - (renameable ? 140 : 114);
                share = CompactOverlayButton.create(shareX, rowButtonY(rowY, 16), 20, 16, Component.empty(), b -> copySessionToken(account));
                share.setVariant(CompactOverlayButton.Variant.SECONDARY);
                share.active = !isBusy();
            }
            boolean loadRowSkin = accountRows.size() < MAX_SKIN_LOOKUPS;
            accountRows.add(new AccountRow(account, rowY, login, delete, share, rename, displayRow.defaultAccount(), loadRowSkin));
            rowY += ROW_HEIGHT;
        }
    }

    private void addProviderButton(int x, int y, DihAccountType provider, String label) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, PROVIDER_BUTTON_WIDTH, 18, Component.literal(label), b -> {
            if (isBusy()) return;
            type = provider;
            clearInputFocus();
            updateInputVisibility();
            rebuildButtons();
        });
        button.active = !isBusy();
        button.setVariant(type == provider ? CompactOverlayButton.Variant.SUCCESS : CompactOverlayButton.Variant.SECONDARY);
        buttons.add(button);
    }

    private void addFilterButton(int x, int y, DihAccountType provider, String label) {
        CompactOverlayButton button = CompactOverlayButton.create(x, y, 50, 16, Component.literal(label), b -> toggleFilter(provider));
        button.setVariant(categoryFilter == provider ? CompactOverlayButton.Variant.FILTER_ON : CompactOverlayButton.Variant.FILTER_OFF);
        buttons.add(button);
    }

    private void addAccount() {
        if (isBusy()) return;
        if (renamingAccount != null) {
            String newName = safeTrim(labelField.getValue());
            if (newName.isBlank()) {
                toast("Enter a username first.", WARN);
                return;
            }
            boolean wasCurrent = isCurrentAccount(renamingAccount);
            if (DihAccountManager.get().rename(renamingAccount.stableId(), newName)) {
                toast("Renamed to " + newName + ". Password and proxies carried over.", SUCCESS);
                if (wasCurrent) {

                    DihAccount renamed = DihAccountManager.get().findById(renamingAccount.stableId());
                    if (renamed != null) DihAccountManager.get().login(renamed);
                }
                renamingAccount = null;
                clearFields();
            } else {
                toast("An account with that name already exists.", WARN);
            }
            invalidateAccountSnapshot();
            rebuildButtons();
            return;
        }
        DihAccount account = new DihAccount();
        account.type = type;
        if (type == DihAccountType.Cracked) {
            account.label = safeTrim(labelField.getValue());
            if (account.label.isBlank()) {
                toast("Enter a username first.", WARN);
                return;
            }
            runAdd(account);
        } else if (type == DihAccountType.Session) {
            account.token = safeTrim(tokenField.getValue());
            if (account.token.isBlank()) {
                toast("Paste a token first.", WARN);
                return;
            }
            runAdd(account);
        } else if (type == DihAccountType.TheAltening) {
            account.token = safeTrim(tokenField.getValue());
            if (account.token.isBlank()) {
                toast("Paste a token first.", WARN);
                return;
            }
            runAdd(account);
        } else if (type == DihAccountType.Microsoft) {
            runMicrosoftAdd();
        }
    }

    private void runAdd(DihAccount account) {
        int id = beginOperation(Operation.ADD);
        operationTask = DihBackgroundTasks.runTracked("Dih-Account-Add", () -> {
            boolean fetched = account.fetchInfo();
            if (isCancelled(id)) return;
            if (!fetched) {
                finishOperation(id, false, "Couldn't add account.", false);
                return;
            }
            if (DihAccountManager.get().contains(account)) {
                finishOperation(id, false, "Account already exists.", false);
                return;
            }
            DihAccountManager.get().add(account);
            selectedAccount = account;
            boolean loggedIn = account.login();
            if (isCancelled(id)) return;
            if (!loggedIn) {
                finishOperation(id, true, "Added, but login failed.", true);
                return;
            }
            finishOperation(id, true, "Logged in as " + account.displayName() + ".", true);
        });
    }

    private void runMicrosoftAdd() {
        int id = beginOperation(Operation.MICROSOFT);
        DihMicrosoftLogin.getRefreshToken(refreshToken -> {
            if (isCancelled(id)) return;
            if (refreshToken == null || refreshToken.isBlank()) {
                finishOperation(id, false, "Login cancelled.", false);
                return;
            }
            DihAccount account = new DihAccount();
            account.type = DihAccountType.Microsoft;
            account.label = refreshToken;
            operationTask = DihBackgroundTasks.runTracked("Dih-Microsoft-Add", () -> {
                boolean fetched = account.fetchInfo();
                if (isCancelled(id)) return;
                if (!fetched) {
                    finishOperation(id, false, loginFailMessage(account), false);
                    return;
                }
                if (DihAccountManager.get().contains(account)) {
                    finishOperation(id, false, "Account already exists.", false);
                    return;
                }
                boolean loggedIn = account.login();
                if (isCancelled(id)) return;
                if (!loggedIn) {
                    finishOperation(id, false, loginFailMessage(account), false);
                    return;
                }
                DihAccountManager.get().add(account);
                selectedAccount = account;
                finishOperation(id, true, "Logged in as " + account.displayName() + ".", true);
            });
        });
    }

    private void loginAccount(DihAccount account) {
        if (isBusy() || account == null || isCurrentAccount(account)) return;
        selectedAccount = account;
        int id = beginOperation(Operation.LOGIN);
        operationTask = DihBackgroundTasks.runTracked("Dih-Account-Login", () -> {
            boolean fetched = account.fetchInfo();
            if (isCancelled(id)) return;
            if (!fetched) {

                if (account.type == DihAccountType.Microsoft && isExpiredMicrosoftToken(account)) {
                    reloginMicrosoft(account, id);
                    return;
                }
                finishOperation(id, false, "Token expired.", false);
                return;
            }
            boolean loggedIn = account.login();
            if (isCancelled(id)) return;
            if (loggedIn) {
                DihAccountManager.get().save();
                finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
            } else {
                finishOperation(id, false, loginFailMessage(account), false);
            }
        });
    }

    private static boolean isExpiredMicrosoftToken(DihAccount account) {
        String error = account == null ? null : account.lastError();
        if (error == null || error.isBlank()) return false;
        String lower = error.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("invalid_grant") || lower.contains("expired");
    }

    private void reloginMicrosoft(DihAccount account, int id) {

        this.minecraft.execute(() -> {
            if (isCancelled(id)) return;
            DihMicrosoftLogin.getRefreshToken(refreshToken -> {
                if (isCancelled(id)) return;
                if (refreshToken == null || refreshToken.isBlank()) {
                    finishOperation(id, false, "Token expired.", false);
                    return;
                }
                account.label = refreshToken;
                operationTask = DihBackgroundTasks.runTracked("Dih-Microsoft-Relogin", () -> {
                    boolean fetched = account.fetchInfo();
                    if (isCancelled(id)) return;
                    if (!fetched) {
                        finishOperation(id, false, loginFailMessage(account), false);
                        return;
                    }
                    boolean loggedIn = account.login();
                    if (isCancelled(id)) return;
                    if (loggedIn) {
                        DihAccountManager.get().save();
                        finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
                    } else {
                        finishOperation(id, false, loginFailMessage(account), false);
                    }
                });
            });
        });
    }

    private static String loginFailMessage(DihAccount account) {
        String reason = account == null ? "" : account.lastError();
        return reason == null || reason.isBlank() ? "Login failed." : "Login failed: " + reason;
    }

    private void loginDefaultAccount() {
        if (isBusy() || isCurrentDefaultAccount()) return;
        DihAccount account = defaultMinecraftAccount();
        if (account == null) return;
        selectedAccount = account;
        int id = beginOperation(Operation.LOGIN);
        operationTask = DihBackgroundTasks.runTracked("Dih-Default-Account-Login", () -> {
            boolean loggedIn = DihAccountSessionSwitcher.setSession(DihAccountSessionSwitcher.getOriginalUser());
            if (isCancelled(id)) return;
            if (loggedIn) {
                finishOperation(id, true, "Logged in as " + account.displayName() + ".", false);
            } else {
                finishOperation(id, false, "Login failed.", false);
            }
        });
    }

    private void deleteAccount(DihAccount account) {
        if (isBusy() || account == null) return;
        DihAccountManager.get().remove(account);
        invalidateAccountSnapshot();
        if (account.equals(selectedAccount)) selectedAccount = null;
        toast("Deleted " + account.displayName() + ".", MUTED);
        rebuildButtons();
    }

    private void checkExpiredAccounts() {
        if (isBusy()) return;
        List<DihAccount> accounts = new ArrayList<>();
        for (DihAccount account : accountSnapshot()) {

            if (account.type == DihAccountType.Cracked) {
                account.checkStatus = DihAccount.CheckStatus.UNKNOWN;
            } else {
                accounts.add(account);
            }
        }
        if (accounts.isEmpty()) {
            toast("No accounts to check.", MUTED);
            return;
        }

        for (DihAccount account : accounts) {
            if (account.checkStatus == DihAccount.CheckStatus.UNKNOWN) account.checkStatus = DihAccount.CheckStatus.CHECKING;
        }
        int id = beginOperation(Operation.CHECK);
        operationTask = DihBackgroundTasks.runTracked("Dih-Account-Check-Coordinator", () -> {
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, Math.max(1, accounts.size())), runnable -> {
                Thread worker = new Thread(runnable, "Dih-Account-Check");
                worker.setDaemon(true);
                return worker;
            });
            AtomicInteger valid = new AtomicInteger();
            AtomicInteger expired = new AtomicInteger();
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (DihAccount account : accounts) {
                    futures.add(pool.submit(() -> {
                        if (isCancelled(id)) return;
                        boolean ok;
                        try {
                            ok = account.fetchInfo();
                        } catch (Throwable t) {
                            ok = false;
                        }
                        if (isCancelled(id)) return;
                        account.checkStatus = ok ? DihAccount.CheckStatus.VALID : DihAccount.CheckStatus.EXPIRED;
                        (ok ? valid : expired).incrementAndGet();
                    }));
                }
                for (Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception ignored) {  }
                }
            } finally {
                pool.shutdownNow();
            }
            if (isCancelled(id)) return;
            DihAccountManager.get().save();
            finishOperation(id, true, valid.get() + " valid, " + expired.get() + " expired.", false);
        });
    }

    private int expiredAccountCount() {
        int count = 0;
        for (DihAccount account : accountSnapshot()) {
            if (account.checkStatus == DihAccount.CheckStatus.EXPIRED) count++;
        }
        return count;
    }

    private void clearExpiredAccounts() {
        if (isBusy()) return;
        if (selectedAccount != null && selectedAccount.checkStatus == DihAccount.CheckStatus.EXPIRED) selectedAccount = null;
        int removed = DihAccountManager.get().removeExpired();
        if (removed == 0) {
            toast("No expired accounts.", MUTED);
            return;
        }
        invalidateAccountSnapshot();
        toast("Cleared " + removed + " expired.", MUTED);
        rebuildButtons();
    }

    private static int nameColor(DihAccount account, boolean active, boolean defaultAccount) {
        if (active) return SUCCESS;
        if (defaultAccount) return DEFAULT_COLOR;
        if (account != null) {
            if (account.checkStatus == DihAccount.CheckStatus.EXPIRED) return ERROR;
            if (account.checkStatus == DihAccount.CheckStatus.CHECKING) return WARN;
        }
        return TEXT;
    }

    private static String checkStatusLabel(DihAccount.CheckStatus status) {
        if (status == null) return "";
        return switch (status) {
            case CHECKING -> "CHECK…";
            case VALID -> "VALID";
            case EXPIRED -> "EXPIRED";
            case UNKNOWN -> "";
        };
    }

    private static int checkStatusColor(DihAccount.CheckStatus status) {
        if (status == null) return MUTED;
        return switch (status) {
            case CHECKING -> WARN;
            case VALID -> SUCCESS;
            case EXPIRED -> ERROR;
            case UNKNOWN -> MUTED;
        };
    }

    private static boolean hasShareableSessionToken(DihAccount account) {
        return account != null && (account.type == DihAccountType.Microsoft
            || account.type == DihAccountType.Session
            || account.type == DihAccountType.TheAltening);
    }

    private static String shareableSessionToken(DihAccount account) {
        if (!hasShareableSessionToken(account)) return "";
        if (account.type == DihAccountType.TheAltening) return safeTrim(account.sessionToken);
        return safeTrim(account.token);
    }

    private void copySessionToken(DihAccount account) {
        if (!hasShareableSessionToken(account)) return;
        String sessionToken = shareableSessionToken(account);
        if (sessionToken.isBlank()) {
            toast("No session token.", WARN);
            return;
        }
        if (this.minecraft == null || this.minecraft.keyboardHandler == null) {
            toast("Clipboard unavailable.", ERROR);
            return;
        }
        try {
            this.minecraft.keyboardHandler.setClipboard(sessionToken);
        } catch (Exception e) {
            toast("Failed to copy token.", ERROR);
            return;
        }
        toast("Copied session token.", SUCCESS);
    }

    private int beginOperation(Operation next) {
        operationId++;
        operation = next;
        operationStartedAtMs = System.currentTimeMillis();
        clearInputFocus();
        updateInputVisibility();
        rebuildButtons();
        return operationId;
    }

    private void finishOperation(int id, boolean success, String message, boolean clearOnSuccess) {
        this.minecraft.execute(() -> {
            if (id != operationId) return;
            operation = Operation.NONE;
            operationStartedAtMs = 0L;
            operationTask = null;
            toast(message, success ? SUCCESS : ERROR);
            if (success && clearOnSuccess) clearFields();
            updateInputVisibility();
            clearInputFocus();
            invalidateAccountSnapshot();
            rebuildButtons();
        });
    }

    private void cancelOperation() {
        if (!isBusy()) return;
        operationId++;
        Future<?> task = operationTask;
        if (task != null) task.cancel(true);
        if (operation == Operation.MICROSOFT) DihMicrosoftLogin.stopServer();
        if (operation == Operation.CHECK) {

            for (DihAccount account : accountSnapshot()) {
                if (account.checkStatus == DihAccount.CheckStatus.CHECKING) account.checkStatus = DihAccount.CheckStatus.UNKNOWN;
            }
        }
        operation = Operation.NONE;
        operationStartedAtMs = 0L;
        operationTask = null;
        clearInputFocus();
        toast("Cancelled.", WARN);
        updateInputVisibility();
        rebuildButtons();
    }

    private boolean isCancelled(int id) {
        return id != operationId || Thread.currentThread().isInterrupted();
    }

    private boolean isBusy() {
        return operation != Operation.NONE;
    }

    private boolean shouldShowCancelOperationButton() {
        return isBusy() && operationStartedAtMs > 0L && System.currentTimeMillis() - operationStartedAtMs >= CANCEL_BUTTON_DELAY_MS;
    }

    private void refreshOperationControls() {
        if (cancelOperationButton != null) {
            cancelOperationButton.visible = shouldShowCancelOperationButton();
        }
    }

    private void clearFields() {
        renamingAccount = null;
        if (labelField != null) labelField.setValue("");
        if (tokenField != null) tokenField.setValue("");
    }

    private void startRename(DihAccount account) {
        if (account == null || account.type != DihAccountType.Cracked || isBusy()) return;
        renamingAccount = account;
        type = DihAccountType.Cracked;
        clearInputFocus();
        updateInputVisibility();
        rebuildButtons();
        if (labelField != null) {
            labelField.setValue(account.displayName());
            labelField.setFocused(true);

            this.setFocused(labelField);
            labelField.moveCursorToEnd(false);
        }
    }

    private void updateInputVisibility() {
        if (labelField == null || tokenField == null) return;
        boolean showForm = !narrowLayout();
        boolean labelVisible = showForm && type == DihAccountType.Cracked;
        boolean tokenVisible = showForm && (type == DihAccountType.Session || type == DihAccountType.TheAltening);
        labelField.setVisible(labelVisible);
        tokenField.setVisible(tokenVisible);
        labelField.active = labelVisible && !isBusy();
        tokenField.active = tokenVisible && !isBusy();
        if (!labelVisible || isBusy()) labelField.setFocused(false);
        if (!tokenVisible || isBusy()) tokenField.setFocused(false);
        if (searchField != null) {
            searchField.visible = showForm;
            searchField.active = showForm;
        }
        labelField.setHint(Component.literal("Username"));
        tokenField.setHint(Component.literal(type == DihAccountType.TheAltening ? "TheAltening token" : "Minecraft access token"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = DihUiScale.toVirtualInt(mouseX);
        int virtualMouseY = DihUiScale.toVirtualInt(mouseY);
        refreshOperationControls();
        DihUiScale.pushOverlayScale(graphics);
        try {
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), BG);
        int panelX = panelX();
        int formX = panelX + 10;
        int previewX = previewX();
        if (!narrowLayout()) {
            drawPanel(graphics, formX, TOP_PANEL_Y, FORM_WIDTH, TOP_PANEL_HEIGHT, PANEL_BG);
            drawPanel(graphics, previewX, TOP_PANEL_Y, PREVIEW_WIDTH, TOP_PANEL_HEIGHT, PANEL_BG_SOFT);
        }
        int listHeight = listPanelHeight();
        drawPanel(graphics, listX(), listTop(), listWidth(), listHeight, PANEL_BG);

        try {
            if (!narrowLayout()) {
                drawText(graphics, "Accounts", formX + 12, 31, TEXT, false);
                renderPreview(graphics, previewX, virtualMouseX, virtualMouseY, delta);
            }
            List<DisplayAccountRow> displayAccounts = displayAccounts();
            if (compactListLayout()) {
                drawText(graphics, "Window too small.", listX() + 12, listTop() + 12, MUTED, false, Math.max(0, listWidth() - 24));
            }
            int firstVisibleRow = savedListScrollOffset / ROW_HEIGHT;
            String listTitle = displayAccounts.size() <= savedViewportRows()
                ? "Accounts"
                : "Accounts  showing " + (firstVisibleRow + 1) + "-" + Math.min(displayAccounts.size(), firstVisibleRow + savedViewportRows()) + " / " + displayAccounts.size();
            int titleMaxWidth = compactListLayout() ? listWidth() - 24 : Math.max(20, listWidth() - 24 - LIST_HEADER_BUTTON_RESERVE);
            drawText(graphics, listTitle, listX() + 12, listTop() + 10, TEXT, false, titleMaxWidth);
            if (!compactListLayout()) for (AccountRow row : accountRows) renderRow(graphics, row, virtualMouseX, virtualMouseY);
            if (accountSnapshot().isEmpty()) {
                drawText(graphics, "No extra accounts saved yet.", listX() + 12, listRowTop() + ROW_HEIGHT + 8, MUTED, false, listWidth() - 24);
            } else if (filteredAccounts().isEmpty()) {
                drawText(graphics, "No accounts match the current search or filters.", listX() + 12, listRowTop() + ROW_HEIGHT + 8, MUTED, false, listWidth() - 24);
            }
            for (CompactOverlayButton button : buttons) {
                CompactOverlayButton.renderStyled(graphics, this.font, button, virtualMouseX, virtualMouseY);
            }
            if (!compactListLayout()) {
                CompactScrollbar.Metrics scrollbar = accountScrollbarMetrics(displayAccounts.size());
                CompactScrollbar.draw(graphics, scrollbar, scrollbar.contains(virtualMouseX, virtualMouseY), accountScrollbarDragging);
            }
        } finally {
        }

        super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        if (activePopup != POPUP_NONE) renderPopup(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    private void renderRow(GuiGraphicsExtractor graphics, AccountRow row, int mouseX, int mouseY) {
        DihAccount account = row.account;
        boolean active = row.defaultAccount ? isCurrentDefaultAccount() : isCurrentAccount(account);
        boolean selected = account.equals(selectedAccount);
        int x = rowX();
        int y = row.y;
        int w = rowWidth();
        int fill = active ? successColor(0x3324D86A) : row.defaultAccount ? 0x302B326B : selected ? outlineColor(0x242B1A1D) : 0x18111113;

        UiRenderer.rect(graphics, UiBounds.of(x, y, w, rowVisualHeight()), fill);
        if (active) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, rowVisualHeight()), successColor(BORDER_ACTIVE));
        else if (selected) UiRenderer.rect(graphics, UiBounds.of(x, y, 2, rowVisualHeight()), outlineColor(0xFF5A3038));
        PlayerSkin rowSkin = row.loadSkin || active || selected ? skinLookup(account).skin() : fallbackSkin(account);
        drawHead(graphics, rowSkin, x + 7, y + 3, 16);
        String name = account.displayName().isBlank() ? "(unknown)" : account.displayName();
        String meta = row.defaultAccount ? "Default Minecraft" : account.type.name();
        int nameX = x + 31;
        if (narrowLayout()) {
            int textRight = Math.max(nameX + 1, row.loginButton.getX() - 8);
            drawText(graphics, name, nameX, y + 7, nameColor(account, active, row.defaultAccount), false, Math.max(1, textRight - nameX));
            CompactOverlayButton.renderStyled(graphics, this.font, row.loginButton, mouseX, mouseY);
            if (row.renameButton != null) CompactOverlayButton.renderStyled(graphics, this.font, row.renameButton, mouseX, mouseY);
            if (row.deleteButton != null) CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
            renderShareButton(graphics, row.shareButton, mouseX, mouseY);
            return;
        }
        int metaX = x + 228;
        int badgeX = row.defaultAccount ? row.loginButton.getX() - 70 : row.loginButton.getX() - 58;
        int badgeRight = row.shareButton == null ? row.loginButton.getX() : row.shareButton.getX();
        int nameMaxWidth = Math.max(20, metaX - nameX - 12);
        int metaMaxWidth = Math.max(20, badgeX - metaX - 10);
        drawText(graphics, name, nameX, y + 7, nameColor(account, active, row.defaultAccount), false, nameMaxWidth);
        drawText(graphics, meta, metaX, y + 7, row.defaultAccount ? DEFAULT_COLOR : MUTED, false, metaMaxWidth);
        if (active) drawText(graphics, "CURRENT", badgeX, y + 8, SUCCESS, false, Math.max(1, badgeRight - badgeX - 4));
        else if (!row.defaultAccount) {
            String statusLabel = checkStatusLabel(account.checkStatus);
            if (!statusLabel.isEmpty()) {
                drawText(graphics, statusLabel, badgeX, y + 8, checkStatusColor(account.checkStatus), false, Math.max(1, badgeRight - badgeX - 4));
            }
        }
        CompactOverlayButton.renderStyled(graphics, this.font, row.loginButton, mouseX, mouseY);
        if (row.renameButton != null) CompactOverlayButton.renderStyled(graphics, this.font, row.renameButton, mouseX, mouseY);
        if (row.deleteButton != null) CompactOverlayButton.renderStyled(graphics, this.font, row.deleteButton, mouseX, mouseY);
        renderShareButton(graphics, row.shareButton, mouseX, mouseY);
    }

    private void renderShareButton(GuiGraphicsExtractor graphics, CompactOverlayButton button, int mouseX, int mouseY) {
        if (button == null) return;
        CompactOverlayButton.renderStyled(graphics, this.font, button, mouseX, mouseY);
        UiBounds bounds = UiBounds.of(button.getX(), button.getY(), button.getWidth(), button.getHeight());
        drawCopyGlyph(graphics, bounds, button.active ? 0xFFF2F2F2 : 0xFF766B6E);
    }

    private void drawCopyGlyph(GuiGraphicsExtractor graphics, UiBounds button, int color) {
        int pageW = 7;
        int pageH = 7;
        int offset = 3;
        int ox = button.x() + (button.width() - (pageW + offset)) / 2;
        int oy = button.y() + (button.height() - (pageH + offset)) / 2;

        UiRenderer.outline(graphics, UiBounds.of(ox + offset, oy, pageW, pageH), color);

        UiBounds front = UiBounds.of(ox, oy + offset, pageW, pageH);
        UiRenderer.rect(graphics, front, SHARE_GLYPH_PAGE);
        UiRenderer.outline(graphics, front, color);
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int x, int mouseX, int mouseY, float delta) {
        DihAccount account = previewAccount();
        drawText(graphics, "Skin preview", x + 12, 31, TEXT, false);
        if (account == null) {
            drawText(graphics, "No account selected", x + 16, 78, MUTED, false, PREVIEW_WIDTH - 32);
            drawText(graphics, "Click a row to preview it.", x + 16, 94, MUTED, false, PREVIEW_WIDTH - 32);
            return;
        }
        SkinLookup lookup = skinLookup(account);
        PlayerSkin skin = lookup.skin();
        int modelCenterX = x + PREVIEW_WIDTH / 2;
        int modelX0 = modelCenterX - 111;
        int modelY0 = TOP_PANEL_Y + 34;
        int modelX1 = modelCenterX + 111;
        int modelY1 = TOP_PANEL_Y + TOP_PANEL_HEIGHT - 8;
        render3dSkin(graphics, skin, modelX0, modelY0, modelX1, modelY1);
        if (lookup.loading()) drawText(graphics, "Loading skin...", x + 14, TOP_PANEL_Y + TOP_PANEL_HEIGHT - 20, WARN, false, PREVIEW_WIDTH - 28);
        String display = account.displayName().isBlank() ? "(unknown)" : account.displayName();
        boolean defaultAccount = isDefaultAccount(account);
        boolean active = defaultAccount ? isCurrentDefaultAccount() : isCurrentAccount(account);
        drawText(graphics, display, x + 12, TOP_PANEL_Y + TOP_PANEL_HEIGHT - 34, active ? SUCCESS : TEXT, false, PREVIEW_WIDTH - 24);
        drawText(graphics, defaultAccount ? "Default Minecraft" : account.type.name(), x + 12, TOP_PANEL_Y + TOP_PANEL_HEIGHT - 48, defaultAccount ? DEFAULT_COLOR : MUTED, false, PREVIEW_WIDTH - 24);
        if (active) drawText(graphics, "CURRENT", x + PREVIEW_WIDTH - 66, 31, SUCCESS, false, 54);
    }

    private void render3dSkin(GuiGraphicsExtractor graphics, PlayerSkin skin, int x0, int y0, int x1, int y1) {
        if (widePlayerModel == null || slimPlayerModel == null) return;
        Model.Simple model = skin.model().name().equalsIgnoreCase("slim") ? slimPlayerModel : widePlayerModel;
        float drawScale = DihUiScale.getOverlayDrawScale();
        int scaledX0 = Math.round(x0 * drawScale);
        int scaledY0 = Math.round(y0 * drawScale);
        int scaledX1 = Math.round(x1 * drawScale);
        int scaledY1 = Math.round(y1 * drawScale);
        float scale = 0.97F * Math.max(1, scaledY1 - scaledY0) / 2.125F;
        graphics.skin(model, skin.body().texturePath(), scale, previewRotationX, currentPreviewRotationY(), -1.0625F, scaledX0, scaledY0, scaledX1, scaledY1);
    }

    private float currentPreviewRotationY() {
        if (previewDragging) return previewRotationY;
        return previewRotationY + (float) ((Blaze3D.getTime() - previewAutoRotationStartTime) * 18.0D);
    }

    private void drawHead(GuiGraphicsExtractor graphics, PlayerSkin skin, int x, int y, int size) {
        Identifier texture = skin.body().texturePath();
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 8, 8, size, size, 8, 8, 64, 64);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 40, 8, size, size, 8, 8, 64, 64);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (virtualEvent.button() != 0) return super.mouseClicked(virtualEvent, doubleClick);
        if (activePopup != POPUP_NONE) {
            handlePopupMouse(virtualEvent, doubleClick);
            return true;
        }
        if (compactListLayout()) return super.mouseClicked(virtualEvent, doubleClick);
        CompactScrollbar.Metrics scrollbar = accountScrollbarMetrics(displayAccounts().size());
        if (scrollbar.hasScroll() && scrollbar.contains(virtualEvent.x(), virtualEvent.y())) {
            accountScrollbarDragging = true;
            accountScrollbarGrabOffset = scrollbar.overThumb(virtualEvent.x(), virtualEvent.y()) ? Math.max(0, (int) Math.round(virtualEvent.y()) - scrollbar.thumbY()) : scrollbar.thumbHeight() / 2;
            savedListScrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), accountScrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            savedListScroll.jumpTo(savedListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            clearInputFocus();
            return true;
        }
        if (isInPreview(virtualEvent.x(), virtualEvent.y())) {
            previewRotationY = currentPreviewRotationY();
            previewAutoRotationStartTime = Blaze3D.getTime();
            previewDragging = true;
            lastPreviewMouseX = virtualEvent.x();
            lastPreviewMouseY = virtualEvent.y();
            clearInputFocus();
            return true;
        }
        for (CompactOverlayButton button : buttons) {
            if (CompactOverlayButton.fireIfHit(button, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
        }
        for (AccountRow row : accountRows) {
            if (row.renameButton != null && CompactOverlayButton.fireIfHit(row.renameButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            if (CompactOverlayButton.fireIfHit(row.deleteButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            if (CompactOverlayButton.fireIfHit(row.shareButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            if (CompactOverlayButton.fireIfHit(row.loginButton, virtualEvent.x(), virtualEvent.y(), virtualEvent.button())) return true;
            if (virtualEvent.x() >= rowX() && virtualEvent.x() < rowRight() && virtualEvent.y() >= row.y && virtualEvent.y() < row.y + ROW_HEIGHT - 3) {
                selectedAccount = row.account();
                return true;
            }
        }
        clearInputFocus();
        return super.mouseClicked(virtualEvent, doubleClick);
    }

    private void toggleFilter(DihAccountType filterType) {

        categoryFilter = categoryFilter == filterType ? null : filterType;
        savedListScrollOffset = 0;
        savedListScroll.jumpTo(0, 0);
        rebuildButtons();
    }

    private void openGenerator() {
        activePopup = POPUP_GENERATOR;
        generatorResult = "";
        clearInputFocus();
        rebuildPopupButtons();
    }

    private void openClearConfirm() {
        activePopup = POPUP_CLEAR;
        clearInputFocus();
        rebuildPopupButtons();
    }

    private void closePopup() {
        syncGeneratorPasswordToConfig();
        activePopup = POPUP_NONE;
        generatorResult = "";
        popupDropdowns.clear();
        if (generatorCountField != null) generatorCountField.setFocused(false);
        if (generatorPasswordField != null) generatorPasswordField.setFocused(false);
        if (generatorListField != null) generatorListField.setFocused(false);
    }

    private void syncGeneratorPasswordToConfig() {
        if (generatorPasswordField == null) return;
        DihConfig config = DihConfig.getGlobal();
        String value = generatorPasswordField.getValue();
        if (!java.util.Objects.equals(config.accountGenSharedPassword, value)) {
            config.accountGenSharedPassword = value;
            config.save();
        }
    }

    private int popupX() {
        return Math.max(4, (screenWidth() - 280) / 2);
    }

    private int popupY() {
        return Math.max(4, (screenHeight() - 210) / 2);
    }

    private void rebuildPopupButtons() {
        popupButtons.clear();
        popupDropdowns.clear();
        int px = popupX();
        int py = popupY();
        if (activePopup == POPUP_GENERATOR) {
            CompactOverlayButton random = CompactOverlayButton.create(px + 12, py + 28, 80, 16,
                Component.literal("Random"), b -> {
                    generatorMode = 0;
                    generatorResult = "";
                    rebuildPopupButtons();
                });
            random.setVariant(generatorMode == 0 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
            popupButtons.add(random);
            CompactOverlayButton fromList = CompactOverlayButton.create(px + 96, py + 28, 80, 16,
                Component.literal("From List"), b -> {
                    generatorMode = 1;
                    generatorResult = "";
                    rebuildPopupButtons();
                });
            fromList.setVariant(generatorMode == 1 ? CompactOverlayButton.Variant.PRIMARY : CompactOverlayButton.Variant.SECONDARY);
            popupButtons.add(fromList);
            if (generatorMode == 0) {

                DihConfig config = DihConfig.getGlobal();
                popupButtons.add(CompactOverlayButton.create(px + 12, py + 72, 256, 16,
                    Component.literal("Set Password"), b -> {
                        DihConfig cfg = DihConfig.getGlobal();
                        cfg.accountGenSetPassword = !cfg.accountGenSetPassword;
                        cfg.save();
                        rebuildPopupButtons();
                    }).setToggleState(config.accountGenSetPassword));
                if (config.accountGenSetPassword) {
                    boolean setForAll = "Set For All".equalsIgnoreCase(config.accountGenPasswordMode);
                    popupDropdowns.add(new CompactDropdown(px + 12, py + 94, 256, 16,
                        List.of("Generate", "Set For All"), setForAll ? 1 : 0, index -> {
                            DihConfig cfg = DihConfig.getGlobal();
                            cfg.accountGenPasswordMode = index == 1 ? "Set For All" : "Generate";
                            cfg.save();
                            rebuildPopupButtons();
                        }));
                }
                CompactOverlayButton generate = CompactOverlayButton.create(px + 12, py + 150, 90, 18,
                    Component.literal(generating ? "Working..." : "Generate"), b -> runRandomGeneration())
                    .setVariant(CompactOverlayButton.Variant.PRIMARY);
                generate.active = !generating;
                popupButtons.add(generate);
            } else {
                popupButtons.add(CompactOverlayButton.create(px + 12, py + 160, 90, 16,
                    Component.literal("Paste"), b -> pasteListFromClipboard())
                    .setVariant(CompactOverlayButton.Variant.SECONDARY));
                CompactOverlayButton addAccounts = CompactOverlayButton.create(px + 106, py + 160, 90, 16,
                    Component.literal(generating ? "Working..." : "Add Accounts"), b -> runListGeneration())
                    .setVariant(CompactOverlayButton.Variant.PRIMARY);
                addAccounts.active = !generating;
                popupButtons.add(addAccounts);
            }
            popupButtons.add(CompactOverlayButton.create(px + 198, py + 184, 70, 16,
                Component.literal("Close"), b -> closePopup()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        } else if (activePopup == POPUP_CLEAR) {
            CompactOverlayButton clear = CompactOverlayButton.create(px + 12, py + 74, 90, 18,
                Component.literal("Clear"), b -> confirmClear()).setVariant(CompactOverlayButton.Variant.DANGER);
            clear.active = clearTargetCount() > 0;
            popupButtons.add(clear);
            popupButtons.add(CompactOverlayButton.create(px + 198, py + 74, 70, 18,
                Component.literal("Cancel"), b -> closePopup()).setVariant(CompactOverlayButton.Variant.SECONDARY));
        }
    }

    private volatile boolean generating;

    private void runRandomGeneration() {
        if (generating) return;
        syncGeneratorPasswordToConfig();
        int count = 10;
        try {
            count = Integer.parseInt(safeTrim(generatorCountField.getValue()));
        } catch (NumberFormatException ignored) {
        }
        final int wanted = count;
        beginGeneration();
        runGenerationAsync(() -> {
            List<String> names = dihclient.util.DihAccountGenerator.randomNames(wanted);
            int added = dihclient.util.DihAccountGenerator.addGeneratedAccounts(names);
            return names.isEmpty() ? "Nothing to add."
                : added >= names.size() ? added + " accounts added."
                : added + " added, " + (names.size() - added) + " already existed.";
        });
    }

    private void runListGeneration() {
        if (generating) return;
        syncGeneratorPasswordToConfig();
        final String raw = generatorListField == null ? "" : generatorListField.getValue();
        if (raw == null || raw.isBlank()) {
            generatorResult = "Paste a list first.";
            return;
        }
        beginGeneration();
        runGenerationAsync(() -> {
            dihclient.util.DihAccountGenerator.ParseResult result =
                dihclient.util.DihAccountGenerator.parseNameList(raw);
            int added = dihclient.util.DihAccountGenerator.addGeneratedAccounts(result.names());
            StringBuilder message = new StringBuilder("Added ").append(added).append(" accounts");
            int existed = result.names().size() - added;
            if (existed > 0) message.append(", ").append(existed).append(" existed");
            if (result.skipped() > 0) message.append(", ").append(result.skipped()).append(" invalid");
            if (result.duplicates() > 0) message.append(", ").append(result.duplicates()).append(" dupes");
            return message.toString();
        });
    }

    private void beginGeneration() {
        generating = true;
        generatorResult = "Generating...";
        rebuildPopupButtons();
    }

    private void runGenerationAsync(java.util.function.Supplier<String> work) {
        Thread thread = new Thread(() -> {
            String result;
            try {
                result = work.get();
            } catch (Throwable t) {
                result = "Generation failed.";
            }
            String message = result;
            Runnable done = () -> {
                generating = false;
                generatorResult = message;
                invalidateAccountSnapshot();
                rebuildButtons();
                rebuildPopupButtons();
            };
            if (this.minecraft != null) this.minecraft.execute(done);
            else done.run();
        }, "dih-account-gen");
        thread.setDaemon(true);
        thread.start();
    }

    private void pasteListFromClipboard() {
        if (this.minecraft != null && generatorListField != null) {
            generatorListField.setValue(this.minecraft.keyboardHandler.getClipboard());
        }
    }

    private void confirmClear() {
        int removed = 0;
        for (DihAccount account : new ArrayList<>(DihAccountManager.get().all())) {
            if (categoryFilter == null || account.type == categoryFilter) {
                DihAccountManager.get().remove(account);
                removed++;
            }
        }
        closePopup();
        toast("Cleared " + removed + " accounts.", MUTED);
        invalidateAccountSnapshot();
        rebuildButtons();
    }

    private int clearTargetCount() {
        int count = 0;
        for (DihAccount account : DihAccountManager.get().all()) {
            if (categoryFilter == null || account.type == categoryFilter) count++;
        }
        return count;
    }

    private String clearTargetNames() {
        if (categoryFilter != null) return categoryFilter.name();
        StringBuilder names = new StringBuilder();
        for (DihAccountType type : DihAccountType.values()) {
            if (names.length() > 0) names.append(", ");
            names.append(type.name());
        }
        return names.toString();
    }

    private void renderPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {

        int rawMouseX = mouseX;
        int rawMouseY = mouseY;
        if (CompactDropdown.isMenuOpen(popupDropdowns)) {
            mouseX = Integer.MIN_VALUE;
            mouseY = Integer.MIN_VALUE;
        }
        UiRenderer.rect(graphics, UiBounds.of(0, 0, screenWidth(), screenHeight()), 0xA0000000);
        int px = popupX();
        int py = popupY();
        if (activePopup == POPUP_CLEAR) {
            drawPanel(graphics, px, py, 280, 100, PANEL_BG);
            drawText(graphics, "Clear Accounts?", px + 12, py + 10, TEXT, false);
            drawText(graphics, clearTargetCount() + " accounts will be deleted in:", px + 12, py + 28, MUTED, false, 256);
            drawText(graphics, clearTargetNames(), px + 12, py + 42, WARN, false, 256);
            drawText(graphics, "This cannot be undone.", px + 12, py + 56, MUTED, false, 256);
        } else {
            drawPanel(graphics, px, py, 280, 210, PANEL_BG);
            drawText(graphics, "Generate Accounts", px + 12, py + 10, TEXT, false);
            if (generatorMode == 0) {
                drawText(graphics, "Count:", px + 12, py + 54, MUTED, false);
                generatorCountField.setX(px + 50);
                generatorCountField.setY(py + 50);
                generatorCountField.extractRenderState(graphics, mouseX, mouseY, delta);

                if (passwordFieldVisible()) {
                    generatorPasswordField.setX(px + 12);
                    generatorPasswordField.setY(py + 116);
                    generatorPasswordField.extractRenderState(graphics, mouseX, mouseY, delta);
                }
                if (!generatorResult.isBlank()) drawText(graphics, generatorResult, px + 12, py + 172, SUCCESS, false, 180);
            } else {
                generatorListField.setX(px + 12);
                generatorListField.setY(py + 48);
                generatorListField.setWidth(256);
                generatorListField.setHeight(108);
                generatorListField.extractRenderState(graphics, mouseX, mouseY, delta);
                if (!generatorResult.isBlank()) drawText(graphics, generatorResult, px + 12, py + 182, SUCCESS, false, 180);
            }
        }
        for (CompactOverlayButton button : popupButtons) {
            CompactOverlayButton.renderStyled(graphics, this.font, button, mouseX, mouseY);
        }
        CompactDropdown.renderButtons(graphics, this.font, popupDropdowns, rawMouseX, rawMouseY);
        CompactDropdown.renderOpenMenu(graphics, this.font, popupDropdowns, rawMouseX, rawMouseY);
    }

    private boolean passwordFieldVisible() {
        DihConfig config = DihConfig.getGlobal();
        return activePopup == POPUP_GENERATOR && generatorMode == 0
            && config.accountGenSetPassword && "Set For All".equalsIgnoreCase(config.accountGenPasswordMode);
    }

    private void handlePopupMouse(MouseButtonEvent event, boolean doubleClick) {

        if (activePopup == POPUP_GENERATOR
            && CompactDropdown.mouseClicked(popupDropdowns, event.x(), event.y(), event.button())) {
            return;
        }
        for (CompactOverlayButton button : popupButtons) {
            if (CompactOverlayButton.fireIfHit(button, event.x(), event.y(), event.button())) return;
        }
        if (activePopup == POPUP_GENERATOR) {
            if (generatorMode == 0) {
                if (passwordFieldVisible() && generatorPasswordField != null) {
                    if (generatorPasswordField.mouseClicked(event, doubleClick)) {
                        generatorPasswordField.setFocused(true);
                        if (generatorCountField != null) generatorCountField.setFocused(false);
                        return;
                    }
                    generatorPasswordField.setFocused(false);
                }
                if (generatorCountField != null && generatorCountField.mouseClicked(event, doubleClick)) {

                    generatorCountField.setFocused(true);
                    if (generatorPasswordField != null) generatorPasswordField.setFocused(false);
                    return;
                }
                if (generatorCountField != null) generatorCountField.setFocused(false);
            } else if (generatorMode == 1 && generatorListField != null) {
                if (generatorListField.mouseClicked(event, doubleClick)) {
                    generatorListField.setFocused(true);
                    return;
                }
                generatorListField.setFocused(false);
            }
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (activePopup != POPUP_NONE) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                closePopup();
                return true;
            }
            if (activePopup == POPUP_GENERATOR) {
                if (generatorMode == 0 && passwordFieldVisible() && generatorPasswordField != null && generatorPasswordField.keyPressed(event)) return true;
                if (generatorMode == 0 && generatorCountField != null && generatorCountField.keyPressed(event)) return true;
                if (generatorMode == 1 && generatorListField != null && generatorListField.keyPressed(event)) return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (activePopup != POPUP_NONE) {
            if (activePopup == POPUP_GENERATOR) {
                if (generatorMode == 0 && passwordFieldVisible() && generatorPasswordField != null && generatorPasswordField.charTyped(event)) return true;
                if (generatorMode == 0 && generatorCountField != null && generatorCountField.charTyped(event)) return true;
                if (generatorMode == 1 && generatorListField != null && generatorListField.charTyped(event)) return true;
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (accountScrollbarDragging) {
            accountScrollbarDragging = false;
            return true;
        }
        if (previewDragging) {
            previewAutoRotationStartTime = Blaze3D.getTime();
            previewDragging = false;
            return true;
        }
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent virtualEvent = virtualEvent(event);
        if (accountScrollbarDragging) {
            CompactScrollbar.Metrics scrollbar = accountScrollbarMetrics(displayAccounts().size());
            savedListScrollOffset = quantizeScrollOffset(CompactScrollbar.scrollFromThumb(scrollbar, virtualEvent.y(), accountScrollbarGrabOffset), ROW_HEIGHT, scrollbar.maxScroll());
            savedListScroll.jumpTo(savedListScrollOffset, scrollbar.maxScroll());
            rebuildButtons();
            return true;
        }
        if (previewDragging) {
            previewRotationX = Math.max(-50.0F, Math.min(50.0F, previewRotationX - (float) DihUiScale.toVirtual(dy) * 2.5F));
            previewRotationY += (float) DihUiScale.toVirtual(dx) * 2.5F;
            lastPreviewMouseX = virtualEvent.x();
            lastPreviewMouseY = virtualEvent.y();
            return true;
        }
        return super.mouseDragged(virtualEvent, DihUiScale.toVirtual(dx), DihUiScale.toVirtual(dy));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        x = DihUiScale.toVirtual(x);
        y = DihUiScale.toVirtual(y);
        if (activePopup != POPUP_NONE) {

            CompactDropdown.mouseScrolled(popupDropdowns, x, y, scrollY);
            return true;
        }
        if (compactListLayout()) return super.mouseScrolled(x, y, scrollX, scrollY);
        if (x < listX() || x >= listX() + listWidth() || y < listTop() || y >= listTop() + listPanelHeight()) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        int maxScroll = savedMaxScroll(displayAccounts().size());
        if (maxScroll <= 0) return true;
        int next = savedListScrollOffset - (int) Math.signum(scrollY) * ROW_HEIGHT;
        savedListScrollOffset = quantizeScrollOffset(next, ROW_HEIGHT, maxScroll);
        savedListScroll.setTarget(savedListScrollOffset, maxScroll);
        rebuildButtons();
        return true;
    }

    @Override
    public void onClose() {
        cancelOperation();
        accountScrollbarDragging = false;
        pruneSkinLookups();
        this.minecraft.gui.setScreen(parent);
    }

    private SkinLookup skinLookup(DihAccount account) {
        String key = skinKey(account);
        synchronized (skinLookups) {
            SkinLookup lookup = skinLookups.computeIfAbsent(key, ignored -> createSkinLookup(account));
            pruneSkinLookups();
            return lookup;
        }
    }

    private SkinLookup createSkinLookup(DihAccount account) {
        UUID id = accountUuid(account);
        String name = account == null || account.displayName().isBlank() ? "Dih" : account.displayName();
        if (account != null && account.type == DihAccountType.Cracked && !name.isBlank() && this.minecraft != null) {
            UUID offlineId = UUIDUtil.createOfflinePlayerUUID(name);
            PlayerSkin defaultSkin = DefaultPlayerSkin.get(offlineId);
            try {
                CompletableFuture<Optional<PlayerSkin>> future = CompletableFuture.supplyAsync(
                    () -> resolveCrackedSkinProfile(name),
                    Util.nonCriticalIoPool()
                ).thenCompose(profile -> profile.map(this.minecraft.getSkinManager()::get).orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())));
                return new SkinLookup(() -> {
                    try {
                        return future.getNow(Optional.empty()).orElse(defaultSkin);
                    } catch (Exception ignored) {
                        return defaultSkin;
                    }
                }, future, defaultSkin);
            } catch (Exception ignored) {
                return new SkinLookup(() -> defaultSkin, CompletableFuture.completedFuture(Optional.empty()), defaultSkin);
            }
        }

        if (account != null && !name.isBlank() && this.minecraft != null && isDefaultAccount(account)) {
            UUID offlineId = UUIDUtil.createOfflinePlayerUUID(name);
            PlayerSkin defaultSkin = DefaultPlayerSkin.get(id == null ? offlineId : id);
            ResolvableProfile profile = id == null ? ResolvableProfile.createUnresolved(name) : ResolvableProfile.createUnresolved(id);
            try {
                PlayerSkinRenderCache.RenderInfo defaultInfo = this.minecraft.playerSkinRenderCache().getOrDefault(profile);
                Supplier<PlayerSkinRenderCache.RenderInfo> lookup = this.minecraft.playerSkinRenderCache().createLookup(profile);
                CompletableFuture<Optional<PlayerSkinRenderCache.RenderInfo>> future = this.minecraft.playerSkinRenderCache().lookup(profile);
                return new SkinLookup(() -> {
                    try {
                        PlayerSkinRenderCache.RenderInfo info = lookup.get();
                        return info == null ? defaultSkin : info.playerSkin();
                    } catch (Exception ignored) {
                        return defaultSkin;
                    }
                }, future, defaultInfo.playerSkin());
            } catch (Exception ignored) {
                return new SkinLookup(() -> defaultSkin, CompletableFuture.completedFuture(Optional.empty()), defaultSkin);
            }
        }

        if (id == null) {
            UUID fallbackId = UUIDUtil.createOfflinePlayerUUID(name);
            PlayerSkin fallback = DefaultPlayerSkin.get(fallbackId);
            return new SkinLookup(() -> fallback, CompletableFuture.completedFuture(Optional.empty()), fallback);
        }

        try {
            PlayerSkin fallback = DefaultPlayerSkin.get(id);
            CompletableFuture<Optional<PlayerSkin>> future = CompletableFuture.supplyAsync(
                () -> this.minecraft.services().profileResolver().fetchById(id).orElse(new GameProfile(id, name)),
                Util.nonCriticalIoPool()
            ).thenCompose(profile -> this.minecraft.getSkinManager().get(profile));
            return new SkinLookup(() -> {
                try {
                    return future.getNow(Optional.empty()).orElse(fallback);
                } catch (Exception ignored) {
                    return fallback;
                }
            }, future, fallback);
        } catch (Exception ignored) {
            PlayerSkin fallback = DefaultPlayerSkin.get(id);
            return new SkinLookup(() -> fallback, CompletableFuture.completedFuture(Optional.empty()), fallback);
        }
    }

    private PlayerSkin fallbackSkin(DihAccount account) {
        String name = account == null || account.displayName().isBlank() ? "Dih" : account.displayName();
        UUID id = accountUuid(account);
        if (id == null || account != null && account.type == DihAccountType.Cracked) id = UUIDUtil.createOfflinePlayerUUID(name);
        return DefaultPlayerSkin.get(id);
    }

    private Optional<GameProfile> resolveCrackedSkinProfile(String name) {
        String username = safeTrim(name);
        if (!isValidMinecraftUsername(username) || this.minecraft == null) return Optional.empty();
        try {
            Optional<GameProfile> resolved = this.minecraft.services().profileResolver().fetchByName(username);
            if (resolved.isPresent() && resolved.get().properties().containsKey(TEXTURES_PROPERTY)) {
                return resolved;
            }
            Optional<GameProfile> withTextures = resolved.flatMap(this::withMojangTextures);
            if (withTextures.isPresent()) return withTextures;
        } catch (Exception ignored) {  }
        return fetchMojangProfileByName(username);
    }

    private Optional<GameProfile> fetchMojangProfileByName(String username) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            JsonObject profile = DihHttp.getJsonDirect(
                "https://api.mojang.com/users/profiles/minecraft/" + encoded, null);
            if (profile == null || !profile.has("id") || !profile.has("name")) return Optional.empty();
            UUID id = UndashedUuid.fromStringLenient(profile.get("id").getAsString());
            String resolvedName = profile.get("name").getAsString();
            return withMojangTextures(new GameProfile(id, resolvedName));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<GameProfile> withMojangTextures(GameProfile profile) {
        if (profile == null || profile.id() == null) return Optional.empty();
        if (profile.properties().containsKey(TEXTURES_PROPERTY)) return Optional.of(profile);
        try {
            String id = UndashedUuid.toString(profile.id());
            JsonObject textureProfile = DihHttp.getJsonDirect(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false", null);
            if (textureProfile == null || !textureProfile.has("properties") || !textureProfile.get("properties").isJsonArray()) return Optional.empty();
            JsonArray properties = textureProfile.getAsJsonArray("properties");
            for (JsonElement element : properties) {
                if (!element.isJsonObject()) continue;
                JsonObject property = element.getAsJsonObject();
                String propertyName = jsonString(property, "name");
                String value = jsonString(property, "value");
                String signature = jsonString(property, "signature");
                if (!TEXTURES_PROPERTY.equals(propertyName) || value.isBlank() || signature.isBlank()) continue;
                String resolvedName = jsonString(textureProfile, "name");
                GameProfile texturedProfile = new GameProfile(profile.id(), resolvedName.isBlank() ? profile.name() : resolvedName);
                texturedProfile.properties().put(TEXTURES_PROPERTY, new Property(TEXTURES_PROPERTY, value, signature));
                return Optional.of(texturedProfile);
            }
        } catch (Exception ignored) {  }
        return Optional.empty();
    }

    private static boolean isValidMinecraftUsername(String username) {
        if (username == null || username.length() < 3 || username.length() > 16) return false;
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            if ((c < 'A' || c > 'Z') && (c < 'a' || c > 'z') && (c < '0' || c > '9') && c != '_') return false;
        }
        return true;
    }

    private static String jsonString(JsonObject object, String key) {
        if (object == null || key == null || !object.has(key)) return "";
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private String skinKey(DihAccount account) {
        if (account == null) return "empty";
        return (isDefaultAccount(account) ? "default" : account.type.name()) + ":" + safeTrim(account.username) + ":" + safeTrim(account.uuid) + ":" + safeTrim(account.label);
    }

    private void pruneSkinLookups() {
        synchronized (skinLookups) {
            if (skinLookups.size() <= MAX_SKIN_LOOKUPS) return;
            List<String> protectedKeys = protectedSkinKeys();
            Iterator<Map.Entry<String, SkinLookup>> iterator = skinLookups.entrySet().iterator();
            while (skinLookups.size() > MAX_SKIN_LOOKUPS && iterator.hasNext()) {
                Map.Entry<String, SkinLookup> entry = iterator.next();
                if (!protectedKeys.contains(entry.getKey())) iterator.remove();
            }
        }
    }

    private List<String> protectedSkinKeys() {
        List<String> keys = new ArrayList<>();
        DihAccount preview = previewAccount();
        addProtectedSkinKey(keys, preview);
        for (AccountRow row : accountRows) addProtectedSkinKey(keys, row.account);
        DihAccount defaultAccount = defaultMinecraftAccount();
        addProtectedSkinKey(keys, defaultAccount);
        return keys;
    }

    private void addProtectedSkinKey(List<String> keys, DihAccount account) {
        if (keys == null || account == null || keys.size() >= MAX_SKIN_LOOKUPS) return;
        String key = skinKey(account);
        if (!keys.contains(key)) keys.add(key);
    }

    private List<DihAccount> filteredAccounts() {
        List<DihAccount> accounts = accountSnapshot();
        String query = normalizeSearch(searchQuery);
        int filterMask = accountFilterMask();
        if (cachedFilteredRevision == accountSnapshotRevision
                && query.equals(cachedAccountQuery)
                && filterMask == cachedAccountFilterMask) {
            return cachedFilteredAccounts;
        }
        cachedFilteredRevision = accountSnapshotRevision;
        cachedAccountQuery = query;
        cachedAccountFilterMask = filterMask;
        cachedDisplaySource = null;
        if (query.isEmpty() && categoryFilter == null) {
            cachedFilteredAccounts = accounts;
            return cachedFilteredAccounts;
        }
        List<DihAccount> filtered = new ArrayList<>();
        for (DihAccount account : accounts) {
            if (account == null || (categoryFilter != null && account.type != categoryFilter)) continue;
            if (!query.isEmpty() && !matchesNickname(account, query)) continue;
            filtered.add(account);
        }
        cachedFilteredAccounts = List.copyOf(filtered);
        return cachedFilteredAccounts;
    }

    private List<DisplayAccountRow> displayAccounts() {
        List<DihAccount> filtered = filteredAccounts();
        if (cachedDisplaySource == filtered) return cachedDisplayAccounts;
        List<DisplayAccountRow> rows = new ArrayList<>();
        DihAccount defaultAccount = defaultMinecraftAccount();
        if (defaultAccount != null) rows.add(new DisplayAccountRow(defaultAccount, true));
        for (DihAccount account : filtered) rows.add(new DisplayAccountRow(account, false));
        cachedDisplaySource = filtered;
        cachedDisplayAccounts = List.copyOf(rows);
        return cachedDisplayAccounts;
    }

    private List<DihAccount> accountSnapshot() {
        if (!accountSnapshotDirty) return cachedAccountSnapshot;
        cachedAccountSnapshot = List.copyOf(DihAccountManager.get().all());
        accountSnapshotDirty = false;
        accountSnapshotRevision++;
        cachedFilteredRevision = Long.MIN_VALUE;
        cachedDisplaySource = null;
        return cachedAccountSnapshot;
    }

    private void invalidateAccountSnapshot() {
        accountSnapshotDirty = true;
    }

    private int accountFilterMask() {

        return categoryFilter == null ? -1 : 1 << categoryFilter.ordinal();
    }

    private boolean matchesNickname(DihAccount account, String query) {
        String name = normalizeSearch(account == null ? "" : account.displayName());
        if (query.isEmpty()) return true;
        if (name.contains(query)) return true;
        return fuzzyContains(name, query);
    }

    private static boolean fuzzyContains(String value, String query) {
        if (value == null || query == null || query.isEmpty()) return true;
        int valueIndex = 0;
        int queryIndex = 0;
        int misses = 0;
        int maxMisses = Math.max(1, query.length() / 3);
        while (valueIndex < value.length() && queryIndex < query.length()) {
            if (value.charAt(valueIndex) == query.charAt(queryIndex)) {
                queryIndex++;
            } else if (queryIndex > 0) {
                misses++;
            }
            if (misses > maxMisses) return false;
            valueIndex++;
        }
        return queryIndex == query.length();
    }

    private static String normalizeSearch(String value) {
        return safeTrim(value).toLowerCase(Locale.ROOT);
    }

    private UUID accountUuid(DihAccount account) {
        if (account == null || account.uuid == null || account.uuid.isBlank()) return null;
        try {
            return UndashedUuid.fromStringLenient(account.uuid);
        } catch (Exception ignored) {
            try {
                return UUID.fromString(account.uuid);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private DihAccount previewAccount() {
        if (selectedAccount != null) return selectedAccount;
        DihAccount defaultAccount = defaultMinecraftAccount();
        if (defaultAccount != null && isCurrentDefaultAccount()) return defaultAccount;
        List<DihAccount> accounts = accountSnapshot();
        for (DihAccount account : accounts) {
            if (isCurrentAccount(account)) return account;
        }
        if (defaultAccount != null) return defaultAccount;
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    private boolean isCurrentAccount(DihAccount account) {
        if (account == null || this.minecraft == null || this.minecraft.getUser() == null) return false;
        UUID accountId = accountUuid(account);
        if (accountId != null) return accountId.equals(this.minecraft.getUser().getProfileId());
        String currentName = this.minecraft.getUser().getName();
        String accountName = safeTrim(account.username);
        if (accountName.isBlank()) accountName = safeTrim(account.label);
        return currentName != null && !accountName.isBlank() && currentName.equals(accountName);
    }

    private boolean isCurrentDefaultAccount() {
        User original = DihAccountSessionSwitcher.getOriginalUser();
        User current = this.minecraft == null ? null : this.minecraft.getUser();
        return original != null && current != null && original.getProfileId().equals(current.getProfileId()) && original.getName().equals(current.getName());
    }

    private boolean isDefaultAccount(DihAccount account) {
        DihAccount defaultAccount = defaultMinecraftAccount();
        return defaultAccount != null && account != null && defaultAccount.uuid.equals(account.uuid) && defaultAccount.username.equals(account.username) && account.label != null && account.label.startsWith("Default Minecraft:");
    }

    private DihAccount defaultMinecraftAccount() {
        User user = DihAccountSessionSwitcher.getOriginalUser();
        if (user == null) return null;
        DihAccount account = new DihAccount();

        account.type = safeTrim(user.getAccessToken()).isBlank() ? DihAccountType.Cracked : DihAccountType.Microsoft;
        account.label = "Default Minecraft: " + user.getName();
        account.username = user.getName();
        account.uuid = user.getProfileId().toString();
        account.token = user.getAccessToken();
        return account;
    }

    private int panelX() {
        return DirectLayout.centerPanel(screenWidth(), panelWidth(), PANEL_MARGIN);
    }

    private int panelWidth() {
        return DirectLayout.fitPanelDimension(screenWidth(), PANEL_MARGIN, PANEL_WIDTH);
    }

    private int listX() {
        return panelX() + 10;
    }

    private int listWidth() {
        return Math.max(1, panelWidth() - 20);
    }

    private int previewX() {
        return panelX() + 10 + FORM_WIDTH + PANEL_GAP;
    }

    private boolean isInPreview(double x, double y) {
        if (narrowLayout()) return false;
        int previewX = previewX();
        return x >= previewX && x < previewX + PREVIEW_WIDTH && y >= TOP_PANEL_Y && y < TOP_PANEL_Y + TOP_PANEL_HEIGHT;
    }

    private int listPanelHeight() {
        return Math.max(1, screenHeight() - listTop() - LIST_BOTTOM_MARGIN);
    }

    private int listTop() {
        return narrowLayout() ? 48 : LIST_TOP;
    }

    private int listRowTop() {
        return listTop() + LIST_HEADER_HEIGHT;
    }

    private int savedRowsTop() {
        return listRowTop();
    }

    private int savedRowsBottom() {
        return listTop() + listPanelHeight() - 6;
    }

    private int savedViewportHeight() {
        return Math.max(ROW_HEIGHT, alignViewportHeight(Math.max(1, savedRowsBottom() - savedRowsTop()), ROW_HEIGHT));
    }

    private int savedViewportRows() {
        return Math.max(1, savedViewportHeight() / ROW_HEIGHT);
    }

    private int savedMaxScroll(int savedRows) {
        return Math.max(0, savedRows * ROW_HEIGHT - savedViewportHeight());
    }

    private CompactScrollbar.Metrics accountScrollbarMetrics(int savedRows) {
        int contentPixels = Math.max(0, savedRows) * ROW_HEIGHT;
        int viewPixels = savedViewportHeight();
        int trackX = listX() + listWidth() - 8;
        int trackY = savedRowsTop();
        int trackHeight = savedViewportHeight();
        return CompactScrollbar.compute(contentPixels, viewPixels, trackX, trackY, 4, trackHeight, savedListScroll.tick(0.0f, savedMaxScroll(savedRows)));
    }

    private int rowX() {
        return listX() + 8;
    }

    private int rowRight() {
        return listX() + listWidth() - 8 - LIST_SCROLLBAR_GUTTER;
    }

    private int rowWidth() {
        return Math.max(1, rowRight() - rowX());
    }

    private int rowVisualHeight() {
        return ROW_HEIGHT - 2;
    }

    private int rowButtonY(int rowY, int buttonHeight) {
        return rowY + Math.max(1, (rowVisualHeight() - buttonHeight) / 2);
    }

    private boolean narrowLayout() {
        return panelWidth() < PANEL_WIDTH || screenHeight() < LIST_TOP + 44;
    }

    private boolean compactListLayout() {
        return listWidth() < 190 || listPanelHeight() < LIST_HEADER_HEIGHT + ROW_HEIGHT;
    }

    private static int alignViewportHeight(int height, int step) {
        if (step <= 0) return Math.max(1, height);
        return Math.max(step, (Math.max(1, height) / step) * step);
    }

    private static int quantizeScrollOffset(int value, int step, int maxScroll) {
        int clamped = Math.max(0, Math.min(maxScroll, value));
        if (step <= 0) return clamped;
        int rounded = Math.round(clamped / (float) step) * step;
        return Math.max(0, Math.min(maxScroll, rounded));
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center) {
        drawText(graphics, text, x, y, color, center, Integer.MAX_VALUE);
    }

    private void drawText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, boolean center, int maxWidth) {
        Font renderer = this.font;
        Identifier font = THEME.fontFor(UiTone.BODY);
        String value = text == null ? "" : text;
        if (maxWidth != Integer.MAX_VALUE && !center) {
            UiText.drawFitted(graphics, renderer, value, font, color, x, y, Math.max(1, maxWidth), false);
            return;
        }
        if (maxWidth != Integer.MAX_VALUE) value = UiText.trimToWidth(renderer, value, maxWidth, font, color);
        int w = UiText.width(renderer, value, font, color);
        int drawX = center ? x - w / 2 : x;
        UiText.draw(graphics, renderer, value, font, color, drawX, y, false);
    }

    private void clearInputFocus() {
        if (labelField != null) labelField.setFocused(false);
        if (tokenField != null) tokenField.setFocused(false);
        if (searchField != null) searchField.setFocused(false);
        this.setFocused(null);
    }

    private String inputLabel() {
        return switch (type) {
            case Cracked, Generated -> "Cracked username";
            case TheAltening -> "TheAltening token";
            case Session -> "Session access token";
            case Microsoft -> "";
        };
    }

    private String addButtonLabel() {
        if (renamingAccount != null) return "Rename";
        return switch (type) {
            case Cracked, Generated -> "Add Cracked";
            case TheAltening -> "Add Altening";
            case Session -> "Add Session";
            case Microsoft -> "Login with Microsoft";
        };
    }

    private static int outlineColor(int argb) {
        return dihclient.util.DihTheme.recolor(argb, dihclient.util.DihTheme.Channel.OUTLINE);
    }

    private static int successColor(int argb) {
        return dihclient.util.DihTheme.recolor(argb, dihclient.util.DihTheme.Channel.SUCCESS);
    }

    private enum Operation {
        NONE,
        ADD,
        LOGIN,
        MICROSOFT,
        CHECK
    }

    private record AccountRow(DihAccount account, int y, CompactOverlayButton loginButton, CompactOverlayButton deleteButton, CompactOverlayButton shareButton, CompactOverlayButton renameButton, boolean defaultAccount, boolean loadSkin) {
    }

    private record DisplayAccountRow(DihAccount account, boolean defaultAccount) {
    }

    private record SkinLookup(Supplier<PlayerSkin> supplier, CompletableFuture<?> future, PlayerSkin fallback) {
        private PlayerSkin skin() {
            try {
                PlayerSkin skin = supplier.get();
                return skin == null ? fallback : skin;
            } catch (Exception ignored) {
                return fallback;
            }
        }

        private boolean loading() {
            return future != null && !future.isDone();
        }
    }
}
