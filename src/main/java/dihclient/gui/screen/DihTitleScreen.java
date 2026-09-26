package dihclient.gui.screen;

import dihclient.util.DihScreens;
import dihclient.gui.vanillaui.assets.UiAssets;
import dihclient.gui.vanillaui.components.UiSizing;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.modules.Module;
import dihclient.modules.ModuleCategory;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.PackHideState;
import dihclient.util.DihColors;
import dihclient.util.DihDiscordLogin;
import dihclient.util.DihHudManager;
import dihclient.util.DihLinks;
import dihclient.util.DihMarquee;
import dihclient.util.DihPerf;
import dihclient.util.DihSpotify;
import dihclient.util.DihTheme;
import dihclient.util.DihTheme.Channel;
import dihclient.util.DihThemeTextures;
import dihclient.util.DihUiScale;
import com.mojang.authlib.minecraft.BanDetails;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CommonButtons;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ARGB;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public class DihTitleScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("dihclient", "textures/gui/title/dih_client_logo.png");
    private static final Identifier BUTTON_CLICK_SOUND_ID = Identifier.fromNamespaceAndPath("dihclient", "gui.main_menu_click");
    private static final SoundEvent BUTTON_CLICK_SOUND = SoundEvent.createVariableRangeEvent(BUTTON_CLICK_SOUND_ID);
    private static final int LOGO_TEXTURE_WIDTH = 506;
    private static final int LOGO_TEXTURE_HEIGHT = 58;

    private static final Identifier ESSENTIAL_ICON = Identifier.fromNamespaceAndPath("dihclient", "textures/gui/title/icons/essential.png");
    private static final Identifier MODMENU_ICON = Identifier.fromNamespaceAndPath("dihclient", "textures/gui/title/icons/modmenu.png");
    private static final Identifier REPLAYMOD_ICON = Identifier.fromNamespaceAndPath("replaymod", "logo_button.png");
    private static final Identifier FLASHBACK_ICON = Identifier.fromNamespaceAndPath("flashback", "icon.png");

    private static final Identifier LANGUAGE_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/language");
    private static final Identifier ACCESSIBILITY_SPRITE = Identifier.fromNamespaceAndPath("minecraft", "icon/accessibility");
    private static final int VANILLA_SPRITE_SIZE = 15;
    private static final int ICON_TEXTURE_SIZE = 32;
    private static final int REPLAYMOD_ICON_TEXTURE_SIZE = 164;
    private static final int FLASHBACK_ICON_TEXTURE_SIZE = 128;
    private static final Component TITLE = Component.translatable("narrator.screen.title");

    private static final int PANEL_PAD = 5;
    private static final int STATUS_ROW_H = 11;
    private static final int BIG_LINE_H = 15;
    private static final int CATEGORY_ROW_H = 12;
    private static final int MANAGER_BUTTON_H = 16;

    private final CompactTheme theme = new CompactTheme();
    private final List<MenuButton> buttons = new ArrayList<>();
    private final String modCountText = createModCountText();
    private final boolean modMenuLoaded = dihclient.platform.DihLoader.isModLoaded("modmenu");
    private final boolean essentialLoaded = dihclient.platform.DihLoader.isModLoaded("essential");
    private final boolean replayModLoaded = dihclient.platform.DihLoader.isModLoaded("replaymod");
    private final boolean flashbackLoaded = dihclient.platform.DihLoader.isModLoaded("flashback");
    private List<MeteorCreditLine> meteorCredits = List.of();
    private boolean meteorCreditsLoadFailed;
    private int cachedServerCount = -1;
    private long serverCountCheckedAt;
    private boolean layoutDirty = true;
    private int seenUpdateGeneration = -1;
    private int layoutScreenW = -1;
    private int layoutScreenH = -1;

    private final long openedAtNanos = System.nanoTime();

    private int cardX, cardY, cardW, cardH;
    private int supportX, supportY, supportW, supportH;
    private int statusX, statusY, statusW, statusH;

    private int statusLabelW;
    private int modulesX, modulesY, modulesW, modulesH;
    private boolean sidePanelsVisible;
    private int utilityRowW;

    private int utilityRowX;

    private int centerStackBottom;
    private final List<StatusRow> statusRows = new ArrayList<>();
    private final List<CategoryRow> categoryRows = new ArrayList<>();
    private int moduleTotalCount;
    private int moduleActiveCount;

    private static final Component COPYRIGHT_TEXT = Component.translatable("title.credits");
    private static final java.util.Random SPLASH_RANDOM = new java.util.Random();

    private static List<String> vanillaSplashPool;
    private final LogoRenderer vanillaLogo = new LogoRenderer(false);
    private SplashRenderer vanillaSplash;
    private boolean vanillaSplashChosen;

    public DihTitleScreen() {
        super(TITLE);
    }

    @Override
    protected void init() {

        if (!dihclient.util.DihMenuPrefs.customMainMenuEnabled() && this.minecraft != null) {
            this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            return;
        }

        this.layoutDirty = true;
        dihclient.util.DihUpdateChecker.checkOnce();

        this.cachedServerCount = -1;
    }

    private void initVanillaSkin() {
        int spacing = 24;
        int topPos = this.height / 4 + 48;

        this.addRenderableWidget(Button.builder(Component.translatable("menu.singleplayer"),
            b -> this.minecraft.gui.setScreen(new SelectWorldScreen(this)))
            .bounds(this.width / 2 - 100, topPos, 200, 20).build());

        Component disabledReason = multiplayerDisabledReason();
        boolean multiplayerAllowed = disabledReason == null;
        Tooltip tooltip = disabledReason != null ? Tooltip.create(disabledReason) : null;

        int multiplayerY = topPos + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.multiplayer"), b -> {
            Screen next = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.gui.setScreen(next);
        }).bounds(this.width / 2 - 100, multiplayerY, 200, 20).tooltip(tooltip).build()).active = multiplayerAllowed;

        int realmsY = multiplayerY + spacing;
        this.addRenderableWidget(Button.builder(Component.translatable("menu.online"),
            b -> this.minecraft.gui.setScreen(new RealmsMainScreen(this)))
            .bounds(this.width / 2 - 100, realmsY, 200, 20).tooltip(tooltip).build()).active = multiplayerAllowed;

        int rowY = realmsY + 36;
        SpriteIconButton language = this.addRenderableWidget(CommonButtons.language(20,
            b -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())), true));
        language.setPosition(this.width / 2 - 124, rowY);
        this.addRenderableWidget(Button.builder(Component.translatable("menu.options"),
            b -> this.minecraft.gui.setScreen(DihScreens.options(this, this.minecraft.options, false)))
            .bounds(this.width / 2 - 100, rowY, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("menu.quit"), b -> this.minecraft.stop())
            .bounds(this.width / 2 + 2, rowY, 98, 20).build());
        SpriteIconButton accessibility = this.addRenderableWidget(CommonButtons.accessibility(20,
            b -> this.minecraft.gui.setScreen(new net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen(this, this.minecraft.options)), true));
        accessibility.setPosition(this.width / 2 + 104, rowY);

        int copyrightWidth = this.font.width(COPYRIGHT_TEXT);
        this.addRenderableWidget(new PlainTextButton(this.width - copyrightWidth - 2, this.height - 10, copyrightWidth, 10,
            COPYRIGHT_TEXT, b -> this.minecraft.gui.setScreen(new CreditsAndAttributionScreen(this)), this.font));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long perf = DihPerf.begin();
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);

        float uiMouseX = (float) DihUiScale.toVirtual(mouseX);
        float uiMouseY = (float) DihUiScale.toVirtual(mouseY);
        layout();

        DihUiScale.pushOverlayScale(graphics);
        try {
            if (sidePanelsVisible) {
                renderSupportPanel(graphics);
                renderStatusPanel(graphics);
                renderModulesPanel(graphics);
            }
            renderTitleCard(graphics);
            Component hoveredTooltip = null;
            for (MenuButton button : buttons) {
                button.render(graphics, uiMouseX, uiMouseY, delta);
                if (button.contains(uiMouseX, uiMouseY)) {
                    graphics.requestCursor(button.enabled ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
                    if (button.tooltip != null) {
                        hoveredTooltip = button.tooltip;
                    }
                }
            }
            if (hoveredTooltip != null) {
                renderCustomTooltip(graphics, hoveredTooltip, uiMouseX, uiMouseY);
            }
            renderMeteorCredits(graphics);
            renderModCount(graphics);
            renderSpotifyStrip(graphics);
        } finally {
            DihUiScale.popOverlayScale(graphics);
            DihPerf.end("title.render", perf);
        }
    }

    private void renderVanillaSkin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.minecraft.gameRenderer.panorama().extractRenderState(graphics, this.width, this.height);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        this.vanillaLogo.extractRenderState(graphics, this.width, 1.0F);
        if (!this.vanillaSplashChosen) {
            this.vanillaSplash = pickVanillaSplash();
            this.vanillaSplashChosen = true;
        }
        if (this.vanillaSplash != null && !this.minecraft.options.hideSplashTexts().get()) {
            this.vanillaSplash.extractRenderState(graphics, this.width, this.font, 1.0F);
        }
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name();
        graphics.text(this.font, version, 2, this.height - 10, DihColors.accent());
    }

    private SplashRenderer pickVanillaSplash() {
        List<String> pool = vanillaSplashPool();
        if (pool.isEmpty()) return null;
        String text = pool.get(SPLASH_RANDOM.nextInt(pool.size()));
        return new SplashRenderer(Component.literal(text).setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(-256)));
    }

    private List<String> vanillaSplashPool() {
        if (vanillaSplashPool != null) return vanillaSplashPool;
        List<String> pool = new ArrayList<>();
        try {
            net.minecraft.server.packs.resources.IoSupplier<java.io.InputStream> supplier =
                this.minecraft.getVanillaPackResources().getResource(
                    net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                    Identifier.withDefaultNamespace("texts/splashes.txt"));
            if (supplier != null) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(supplier.get(), java.nio.charset.StandardCharsets.UTF_8))) {
                    reader.lines().map(String::trim)
                        .filter(line -> !line.isEmpty() && line.hashCode() != 125780783)
                        .forEach(pool::add);
                }
            }
        } catch (Exception ignored) {  }
        vanillaSplashPool = List.copyOf(pool);
        return vanillaSplashPool;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;

        float uiMouseX = (float) DihUiScale.toVirtual(event.x());
        float uiMouseY = (float) DihUiScale.toVirtual(event.y());
        layout();

        for (MenuButton button : buttons) {
            if (button.click(uiMouseX, uiMouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void removed() {
    }

    private void layout() {
        int screenW = DihUiScale.getVirtualScreenWidth();
        int screenH = DihUiScale.getVirtualScreenHeight();
        int updateGeneration = dihclient.util.DihUpdateChecker.generation();
        if (updateGeneration != seenUpdateGeneration) {
            seenUpdateGeneration = updateGeneration;
            layoutDirty = true; // an update notice may have appeared
        }
        if (!layoutDirty && screenW == layoutScreenW && screenH == layoutScreenH) {
            return;
        }
        layoutDirty = false;
        layoutScreenW = screenW;
        layoutScreenH = screenH;

        boolean compact = screenH < 320;
        int margin = 8;

        cardW = Math.min(296, Math.max(180, screenW - margin * 2));
        cardH = Math.max(20, Math.round(LOGO_TEXTURE_HEIGHT
            * Math.min(1.0f, (cardW - 16) / (float) LOGO_TEXTURE_WIDTH)));
        int rowW = Math.min(cardW - 28, compact ? 220 : 240);
        int rowH = compact ? 18 : 22;
        int rowGap = compact ? 3 : 5;
        int cardToRows = compact ? 6 : 10;
        int stackH = cardH + cardToRows + rowH * 5 + rowGap * 4;
        int utilitySize = compact ? 16 : 20;
        int bottomBarH = utilitySize + margin + 14;
        cardX = (screenW - cardW) / 2;
        cardY = Math.max(margin, (screenH - stackH - bottomBarH) / 2);

        centerStackBottom = cardY + stackH;
        int rowTop = cardY + cardH + cardToRows;
        int rowX = cardX + (cardW - rowW) / 2;

        int sideW = Math.min(172, (screenW - cardW) / 2 - margin * 2);
        sidePanelsVisible = sideW >= 104 && screenH >= 250;

        Component disabledReason = multiplayerDisabledReason();
        boolean multiplayerAllowed = disabledReason == null;

        buttons.clear();
        MenuButton singleplayer = new MenuButton(rowX, rowTop, rowW, rowH, Component.translatable("menu.singleplayer"), true,
            () -> this.minecraft.gui.setScreen(new SelectWorldScreen(this))).asMainRow(1);
        buttons.add(singleplayer);

        MenuButton multiplayer = new MenuButton(rowX, rowTop + (rowH + rowGap), rowW, rowH, Component.translatable("menu.multiplayer"), multiplayerAllowed,
            () -> {
                Screen next = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
                this.minecraft.gui.setScreen(next);
            }).withTooltip(disabledReason).withRightBadge(serverCountBadge()).asMainRow(2);
        buttons.add(multiplayer);

        MenuButton realms = new MenuButton(rowX, rowTop + (rowH + rowGap) * 2, rowW, rowH, Component.translatable("menu.online"), multiplayerAllowed,
            () -> this.minecraft.gui.setScreen(new RealmsMainScreen(this))).withTooltip(disabledReason).asMainRow(3);
        buttons.add(realms);

        MenuButton options = new MenuButton(rowX, rowTop + (rowH + rowGap) * 3, rowW, rowH, Component.translatable("menu.options"), true,
            () -> this.minecraft.gui.setScreen(DihScreens.options(this, this.minecraft.options, false))).asMainRow(4);
        buttons.add(options);

        MenuButton quit = new MenuButton(rowX, rowTop + (rowH + rowGap) * 4, rowW, rowH, Component.translatable("menu.quit"), true,
            () -> this.minecraft.stop()).asMainRow(5);
        buttons.add(quit);

        layoutUtilityButtons(screenW, screenH, margin, utilitySize);

        if (!sidePanelsVisible) {
            statusRows.clear();
            categoryRows.clear();
            return;
        }

        int maxModulesPanelH = screenH - bottomBarH - cardY;
        int fixedModulesH = PANEL_PAD + BIG_LINE_H + 3 + MANAGER_BUTTON_H + PANEL_PAD;
        int maxCategoryRows = Math.max(1, (maxModulesPanelH - fixedModulesH - 3) / CATEGORY_ROW_H);
        buildCategoryRows(maxCategoryRows);
        buildStatusRows();

        statusLabelW = 0;
        int statusValueW = 0;
        for (StatusRow row : statusRows) {
            statusLabelW = Math.max(statusLabelW,
                UiText.width(this.font, row.label(), UiAssets.FONT_LABEL, 0));
            statusValueW = Math.max(statusValueW,
                UiText.width(this.font, row.value().get(), UiAssets.FONT_BODY, 0));
        }
        statusW = statusLabelW + statusValueW + 24;
        statusH = PANEL_PAD + statusRows.size() * STATUS_ROW_H + PANEL_PAD - 1;
        statusX = margin;
        statusY = cardY;

        int supportRowH = Math.max(compact ? 16 : 20, UiText.fontHeight(UiAssets.FONT_LABEL) + 8);
        int supportRowGap = 3;

        dihclient.util.DihUpdateChecker.Result update = dihclient.util.DihUpdateChecker.result();
        String updateLabel = update.updateAvailable()
            ? (update.newerBuild() ? "Update " + update.latest() + " (new build)" : "Update to " + update.latest())
            : null;
        String[] supportLabels = updateLabel == null
            ? new String[] {"Website", "Source code"}
            : new String[] {updateLabel, "Website", "Source code"};
        int supportTextW = 0;
        for (String label : supportLabels) {
            supportTextW = Math.max(supportTextW,
                UiText.width(this.font, label, UiAssets.FONT_LABEL, 0));
        }
        supportW = Math.max(statusW, supportTextW + 22);
        supportH = PANEL_PAD + supportLabels.length * supportRowH + (supportLabels.length - 1) * supportRowGap + PANEL_PAD;
        supportX = margin;
        supportY = statusY + statusH + 6;
        int supportRowY = supportY + PANEL_PAD;

        int supportRow = 0;
        if (updateLabel != null) {
            String download = update.downloadUrl().isEmpty() ? update.pageUrl() : update.downloadUrl();
            buttons.add(new MenuButton(supportX + 4, supportRowY, supportW - 8, supportRowH,
                Component.literal(updateLabel), true, () -> DihLinks.open(download))
                .asSupportRow()
                .withTooltip(Component.literal(dihclient.util.DihUpdateChecker.summary(update).getString()
                    + "  Click to download.")));
            supportRow++;
        }
        addSupportRow(supportX + 4, supportRowY + supportRow++ * (supportRowH + supportRowGap), supportW - 8, supportRowH,
            "Website", () -> DihLinks.open(DihLinks.WEBSITE), DihLinks.WEBSITE);
        addSupportRow(supportX + 4, supportRowY + supportRow * (supportRowH + supportRowGap), supportW - 8, supportRowH,
            "Source code", () -> DihLinks.open(DihLinks.SOURCE), DihLinks.SOURCE);

        String activeText = Integer.toString(moduleActiveCount);
        String activeSuffix = " / " + moduleTotalCount + " ACTIVE";
        int modulesContentW = 7 + UiText.width(this.font, activeText, UiAssets.FONT_TITLE, 0) + 3
            + UiText.width(this.font, activeSuffix, UiAssets.FONT_BODY, 0) + 7;
        for (CategoryRow row : categoryRows) {
            if (row.total() < 0) {
                modulesContentW = Math.max(modulesContentW,
                    12 + UiText.width(this.font, row.label(), UiAssets.FONT_BODY, 0));
            } else {
                String count = row.active() + "/" + row.total();
                modulesContentW = Math.max(modulesContentW,
                    6 + UiText.width(this.font, row.label(), UiAssets.FONT_BODY, 0) + 8
                        + UiText.width(this.font, count, UiAssets.FONT_BODY, 0) + 6);
            }
        }
        modulesContentW = Math.max(modulesContentW,
            10 + UiText.width(this.font, "OPEN MANAGER", UiAssets.FONT_LABEL, 0) + 20);
        modulesW = Math.min(sideW, modulesContentW);
        modulesH = fixedModulesH + 3 + categoryRows.size() * CATEGORY_ROW_H;
        modulesX = screenW - margin - modulesW;
        modulesY = cardY;
        MenuButton openManager = new MenuButton(modulesX + 5, modulesY + modulesH - PANEL_PAD - MANAGER_BUTTON_H,
            modulesW - 10, MANAGER_BUTTON_H, Component.literal("OPEN MANAGER"), true,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    this.minecraft.gui.setScreen(new DihModuleScreen(this, DihModuleScreen.Mode.TITLE_SETUP));
                }
            }).asMainRow(0).withIntro(260, true);
        buttons.add(openManager);
    }

    private void addSupportRow(int x, int y, int w, int h, String text, Runnable action, String url) {
        buttons.add(new MenuButton(x, y, w, h, Component.literal(text), true, action)
            .asSupportRow()
            .withTooltip(Component.literal(url)));
    }

    private void layoutUtilityButtons(int screenW, int screenH, int margin, int utilitySize) {

        List<UtilityButtonSpec> all = new ArrayList<>();
        if (modMenuLoaded) {
            all.add(new UtilityButtonSpec("MODS", "", MODMENU_ICON, null,
                ICON_TEXTURE_SIZE, () -> openModMenu(), Component.literal("Mod Menu")));
        }
        all.add(new UtilityButtonSpec("LANGUAGE", "", null, LANGUAGE_SPRITE,
            ICON_TEXTURE_SIZE,
            () -> this.minecraft.gui.setScreen(new LanguageSelectScreen(this, this.minecraft.options, this.minecraft.getLanguageManager())),
            Component.literal("Language")));
        all.add(new UtilityButtonSpec("MODULES", "", UiAssets.ICON_MAIN_MENU_CATEGORY, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    this.minecraft.gui.setScreen(new DihModuleScreen(this, DihModuleScreen.Mode.TITLE_SETUP));
                }
            },
            Component.literal("Modules & Macros")));
        all.add(new UtilityButtonSpec("MATCHMAKING", "", UiAssets.ICON_MATCHMAKING, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    dihclient.modules.DihModule mod = dihclient.modules.DihModule.get();
                    dihclient.util.IDihOverlay overlay = mod == null ? null : mod.getMatchmakingOverlay();
                    if (overlay != null) {
                        dihclient.util.DihOverlayManager.get().register(overlay);
                        ((dihclient.util.DihMatchmakingOverlay) overlay).setMainMenuMode(true);
                        overlay.setVisible(true);
                        this.minecraft.gui.setScreen(new DihOverlayHostScreen(overlay, this, true));
                    }
                }
            },
            Component.literal("Matchmaking")));
        all.add(new UtilityButtonSpec("PROFILES", "", UiAssets.ICON_PROFILES, null,
            ICON_TEXTURE_SIZE,
            () -> {
                if (!PackHideState.isHardLocked()) {
                    dihclient.modules.DihModule mod = dihclient.modules.DihModule.get();
                    dihclient.util.IDihOverlay overlay = mod == null ? null : mod.getProfilesOverlay();
                    if (overlay != null) {
                        dihclient.util.DihOverlayManager.get().register(overlay);
                        ((dihclient.util.DihProfilesOverlay) overlay).setMainMenuMode(true);
                        overlay.setVisible(true);
                        this.minecraft.gui.setScreen(new DihOverlayHostScreen(overlay, this, true));
                    }
                }
            },
            Component.literal("Profiles")));
        all.add(new UtilityButtonSpec("ACCESSIBILITY", "", null, ACCESSIBILITY_SPRITE,
            ICON_TEXTURE_SIZE,
            () -> this.minecraft.gui.setScreen(new AccessibilityOptionsScreen(this, this.minecraft.options)),
            Component.literal("Accessibility")));
        if (replayModLoaded) {
            all.add(new UtilityButtonSpec("REPLAYS", "", REPLAYMOD_ICON, null,
                REPLAYMOD_ICON_TEXTURE_SIZE, () -> openReplayViewer(), Component.literal("Replay Viewer")));
        }
        if (flashbackLoaded) {
            all.add(new UtilityButtonSpec("FLASHBACK", "", FLASHBACK_ICON, null,
                FLASHBACK_ICON_TEXTURE_SIZE, () -> openFlashback(), Component.literal("Flashback Replays")));
        }
        if (essentialLoaded) {
            all.add(new UtilityButtonSpec("ESSENTIAL", "", ESSENTIAL_ICON, null,
                ICON_TEXTURE_SIZE, () -> openEssential(), Component.literal("Essential")));
        }

        int gap = 4;
        utilityRowW = all.isEmpty() ? 0 : all.size() * utilitySize + (all.size() - 1) * gap;
        int x = screenW - margin - utilityRowW;
        utilityRowX = all.isEmpty() ? screenW : x;
        int y = screenH - margin - utilitySize;
        for (int i = 0; i < all.size(); i++) {
            addUtilityButton(all.get(i), x + i * (utilitySize + gap), y, utilitySize);
        }
    }

    private void addUtilityButton(UtilityButtonSpec spec, int x, int y, int size) {
        MenuButton button = new MenuButton(x, y, size, size,
            Component.literal(spec.title()), spec.onPress() != null, spec.onPress())
            .asUtility(spec.subtitle())
            .withTooltip(spec.tooltip());
        if (spec.sprite() != null) {
            button.withSprite(spec.sprite());
        } else if (spec.icon() != null) {
            button.withIcon(spec.icon(), spec.iconTextureSize());
        }
        buttons.add(button);
    }

    private Component multiplayerDisabledReason() {
        if (this.minecraft.allowsMultiplayer()) return null;
        if (this.minecraft.isNameBanned()) return Component.translatable("title.multiplayer.disabled.banned.name");

        BanDetails multiplayerBan = this.minecraft.multiplayerBan();
        if (multiplayerBan != null) {
            return multiplayerBan.expires() != null
                ? Component.translatable("title.multiplayer.disabled.banned.temporary")
                : Component.translatable("title.multiplayer.disabled.banned.permanent");
        }
        return Component.translatable("title.multiplayer.disabled");
    }

    private void buildStatusRows() {
        statusRows.clear();
        statusRows.add(new StatusRow("USER:", () -> this.minecraft.getUser().getName()));
        statusRows.add(new StatusRow("BUILD:", () -> modVersion()
            + (dihclient.util.DihUpdateChecker.result().updateAvailable() ? " (outdated)" : "")));
    }

    private void buildCategoryRows(int maxCategoryRows) {
        categoryRows.clear();
        int total = 0;
        int active = 0;
        List<CategoryRow> all = new ArrayList<>();

        for (ModuleCategory category : ModuleCategory.values()) {
            List<Module> modules = ModuleRegistry.byCategory(category);
            if (modules.isEmpty()) continue;
            int categoryActive = 0;
            for (Module module : modules) {
                if (module.isEnabled()) categoryActive++;
            }
            all.add(new CategoryRow(category.label().toUpperCase(Locale.ROOT), categoryActive, modules.size()));
            total += modules.size();
            active += categoryActive;
        }
        moduleTotalCount = total;
        moduleActiveCount = active;
        if (all.size() <= maxCategoryRows) {
            categoryRows.addAll(all);
            return;
        }

        int keep = Math.max(0, maxCategoryRows - 1);
        for (int i = 0; i < keep; i++) {
            categoryRows.add(all.get(i));
        }
        categoryRows.add(new CategoryRow("+" + (all.size() - keep) + " MORE", -1, -1));
    }

    private static String modVersion() {
        String version = DihDiscordLogin.modVersionString();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    private float introProgress(int delayMs) {
        float elapsedMs = (System.nanoTime() - openedAtNanos) / 1_000_000.0f - delayMs;
        if (elapsedMs <= 0.0f) return 0.0f;
        float p = Math.min(1.0f, elapsedMs / 340.0f);
        return 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
    }

    private static int introOffset(float progress) {
        return Math.round((1.0f - progress) * 5.0f);
    }

    private static int fade(int color, float progress) {
        return UiRenderer.applyAlpha(color, progress);
    }

    private void renderTitleCard(GuiGraphicsExtractor graphics) {
        float p = introProgress(0);
        if (p <= 0.0f) return;

        float scale = Math.min(1.0f, (cardW - 16) / (float) LOGO_TEXTURE_WIDTH);
        int drawW = Math.max(1, Math.round(LOGO_TEXTURE_WIDTH * scale));
        int drawH = Math.max(1, Math.round(LOGO_TEXTURE_HEIGHT * scale));
        int logoX = UiSizing.centerInside(cardX, cardW, drawW);
        int logoY = cardY + introOffset(p) + Math.max(0, (cardH - drawH) / 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, DihThemeTextures.recolored(LOGO, Channel.ACCENT), logoX, logoY, 0.0F, 0.0F, drawW, drawH,
            LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, ARGB.white(p));
    }

    private void renderSupportPanel(GuiGraphicsExtractor graphics) {
        float p = introProgress(140);
        if (p <= 0.0f) return;
        renderPanelChrome(graphics, supportX, supportY + introOffset(p), supportW, supportH, null, p);
    }

    private void renderStatusPanel(GuiGraphicsExtractor graphics) {
        float p = introProgress(200);
        if (p <= 0.0f) return;
        int x = statusX;
        int y = statusY + introOffset(p);
        renderPanelChrome(graphics, x, y, statusW, statusH, null, p);

        int rowY = y + PANEL_PAD;
        int labelColor = fade(theme.color(UiTone.ACCENT), p * 0.85f);
        int valueColor = fade(theme.color(UiTone.BODY), p);
        for (StatusRow row : statusRows) {
            UiText.draw(graphics, this.font, row.label(), UiAssets.FONT_LABEL, labelColor, x + 6, rowY, false);
            int valueX = x + 12 + statusLabelW;
            String value = fitText(row.value().get(), x + statusW - 6 - valueX, UiAssets.FONT_BODY, valueColor);
            UiText.draw(graphics, this.font, value, UiAssets.FONT_BODY, valueColor, valueX, rowY + 1, false);
            rowY += STATUS_ROW_H;
        }
    }

    private void renderModulesPanel(GuiGraphicsExtractor graphics) {
        float p = introProgress(260);
        if (p <= 0.0f) return;
        int x = modulesX;
        int y = modulesY + introOffset(p);
        renderPanelChrome(graphics, x, y, modulesW, modulesH, null, p);

        int accent = fade(theme.color(UiTone.ACCENT), p);
        int muted = fade(theme.color(UiTone.MUTED), p);
        int body = fade(theme.color(UiTone.BODY), p);

        int bigY = y + PANEL_PAD;
        String activeText = Integer.toString(moduleActiveCount);
        UiText.draw(graphics, this.font, activeText, UiAssets.FONT_TITLE, accent, x + 7, bigY, false);
        int activeW = UiText.width(this.font, activeText, UiAssets.FONT_TITLE, accent);
        UiText.draw(graphics, this.font, " / " + moduleTotalCount + " ACTIVE", UiAssets.FONT_BODY, muted,
            x + 7 + activeW + 3, bigY + (UiText.fontHeight(UiAssets.FONT_TITLE) - UiText.fontHeight(UiAssets.FONT_BODY)), false);
        UiRenderer.horizontalEdge(graphics, x + 5, bigY + BIG_LINE_H, modulesW - 10, fade(theme.borderSoft(), p));

        int rowY = bigY + BIG_LINE_H + 3;
        for (CategoryRow row : categoryRows) {
            if (row.total() < 0) {

                UiText.draw(graphics, this.font, row.label(), UiAssets.FONT_BODY, muted, x + 6, rowY + 2, false);
            } else {
                String count = row.active() + "/" + row.total();
                int countColor = row.active() > 0 ? accent : muted;
                int countW = UiText.width(this.font, count, UiAssets.FONT_BODY, countColor);
                String name = fitText(row.label(), modulesW - countW - 20, UiAssets.FONT_BODY, body);
                UiText.draw(graphics, this.font, name, UiAssets.FONT_BODY, body, x + 6, rowY + 2, false);
                UiText.draw(graphics, this.font, count, UiAssets.FONT_BODY, countColor, x + modulesW - 6 - countW, rowY + 2, false);
            }
            rowY += CATEGORY_ROW_H;
        }
    }

    private void renderPanelChrome(GuiGraphicsExtractor graphics, int x, int y, int w, int h, String header, float p) {
        UiRenderer.softRect(graphics, UiBounds.of(x, y, w, h), 2, fade(theme.windowFill(), p));
        int accent = fade(theme.color(UiTone.ACCENT), p);
        UiRenderer.rect(graphics, UiBounds.of(x + 2, y, w - 4, 2), accent);
        if (header != null && !header.isBlank()) {
            String text = "/// " + header + " ///";
            int textW = UiText.width(this.font, text, UiAssets.FONT_LABEL, accent);
            UiText.draw(graphics, this.font, text, UiAssets.FONT_LABEL, accent, x + (w - textW) / 2, y + 4, false);
        }
    }

    private void drawHudText(GuiGraphicsExtractor graphics, String text, int x, int y, int color, Identifier fontId) {
        UiText.draw(graphics, this.font, text == null ? "" : text, fontId, color, x, y, false);
    }

    private void renderModCount(GuiGraphicsExtractor graphics) {
        float p = introProgress(320);
        if (p <= 0.0f) return;
        Identifier fontId = UiAssets.FONT_BODY;
        int muted = fade(theme.color(UiTone.MUTED), p);
        int y = DihUiScale.getVirtualScreenHeight() - UiText.fontHeight(fontId) - 3;
        UiText.draw(graphics, this.font, modCountText, fontId, muted, 4, y, false);
    }

    private static final int MENU_ART_SIZE = 16;

    private void renderSpotifyStrip(GuiGraphicsExtractor graphics) {

        if (!DihHudManager.spotifyMenuStrip(DihHudManager.SPOTIFY)) return;
        int screenW = DihUiScale.getVirtualScreenWidth();
        int screenH = DihUiScale.getVirtualScreenHeight();

        if (screenH < 200) return;
        DihSpotify.setWanted();
        DihSpotify.Snapshot snapshot = DihSpotify.snapshot();
        boolean playing = snapshot != null && snapshot.status() == DihSpotify.Status.PLAYING;
        boolean paused = snapshot != null && snapshot.status() == DihSpotify.Status.PAUSED;

        if (!playing && !paused) return;
        String track = DihMarquee.trackText(snapshot);
        if (track.isEmpty()) return;
        float p = introProgress(320);
        if (p <= 0.0f) return;

        Identifier fontId = UiAssets.FONT_BODY;
        int textColor = fade(theme.color(UiTone.MUTED), p);
        int fullTextWidth = UiText.width(this.font, track, fontId, textColor);

        DihHudManager.SpotifyArt art = DihHudManager.spotifyArt(snapshot);
        int artSize = art != null ? MENU_ART_SIZE : 0;
        int artGap = art != null ? 4 : 0;

        int modCountRight = 4 + UiText.width(this.font, modCountText, fontId, 0);
        int availableHalf = Math.min(screenW / 2 - modCountRight, utilityRowX - screenW / 2) - 4;
        int natural = artSize + artGap + fullTextWidth;
        int stripWidth = Math.min(natural, 2 * Math.max(0, availableHalf));

        if (stripWidth < 24) return;

        int clip = stripWidth - artSize - artGap;
        if (clip < 16) {
            art = null;
            artSize = 0;
            artGap = 0;
            clip = stripWidth;
        }

        int total = artSize + artGap + Math.min(fullTextWidth, clip);
        int x = screenW / 2 - total / 2;

        int fontH = UiText.fontHeight(fontId);
        int barY = screenH - 4;
        int textY = barY - 2 - fontH;

        if (textY < centerStackBottom + 4) return;

        long holdUntil = snapshot.updatedAtMs() + 1200L;

        int speed = DihHudManager.spotifyScrollSpeed(DihHudManager.SPOTIFY);
        long nowMs = System.currentTimeMillis();
        if (art != null) {
            int artY = textY + (fontH - MENU_ART_SIZE) / 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, art.id(), x, artY, 0.0F, 0.0F,
                MENU_ART_SIZE, MENU_ART_SIZE, art.width(), art.height(), art.width(), art.height(),
                fade(0xFFFFFFFF, p));
        }
        DihMarquee.drawMarquee(graphics, this.font, track, fontId, textColor,
            x + artSize + artGap, textY, clip, false, nowMs, holdUntil, speed);

        double progress = DihHudManager.spotifyProgressFor(snapshot);
        UiText.fill(graphics, x, barY, x + total, barY + 2, fade(theme.color(UiTone.MUTED), p * 0.22f));
        int fillW = (int) Math.round(total * progress);
        if (fillW > 0) {
            UiText.fill(graphics, x, barY, x + fillW, barY + 2, fade(theme.color(UiTone.ACCENT), p * 0.9f));
        }
    }

    private String fitText(String text, int maxWidth, Identifier fontId, int color) {
        if (text == null) return "";
        if (UiText.width(this.font, text, fontId, color) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && UiText.width(this.font, trimmed + ".", fontId, color) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + ".";
    }

    private void renderMeteorCredits(GuiGraphicsExtractor graphics) {
        List<MeteorCreditLine> credits = getMeteorCredits();
        if (credits.isEmpty()) return;
        float p = introProgress(320);
        if (p <= 0.0f) return;

        int lineGap = UiText.fontHeight(UiAssets.FONT_BODY) + 2;

        int y = DihUiScale.getVirtualScreenHeight() - UiText.fontHeight(UiAssets.FONT_BODY) - 5 - credits.size() * lineGap;
        for (MeteorCreditLine credit : credits) {
            int x = 4;
            for (MeteorCreditSegment segment : credit.segments()) {
                if (!segment.text().isEmpty()) {
                    int color = fade(segment.color(), p);
                    UiText.draw(graphics, this.font, segment.text(), UiAssets.FONT_BODY, color, x, y, false);
                    x += UiText.width(this.font, segment.text(), UiAssets.FONT_BODY, color);
                }
            }
            y += lineGap;
        }
    }

    private List<MeteorCreditLine> getMeteorCredits() {
        if (!meteorCredits.isEmpty() || meteorCreditsLoadFailed) return meteorCredits;
        if (!dihclient.platform.DihLoader.isModLoaded("meteor-client")) return meteorCredits;

        try {
            Class<?> addonManagerClass = Class.forName("meteordevelopment.meteorclient.addons.AddonManager");
            Field addonsField = addonManagerClass.getField("ADDONS");
            Object value = addonsField.get(null);
            if (!(value instanceof Iterable<?> addons)) return meteorCredits;

            List<MeteorCreditLine> loaded = new ArrayList<>();
            for (Object addon : addons) {
                MeteorCreditLine line = meteorCreditLine(addon);
                if (line != null) loaded.add(line);
            }
            meteorCredits = List.copyOf(loaded);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            meteorCreditsLoadFailed = true;
        }
        return meteorCredits;
    }

    private static MeteorCreditLine meteorCreditLine(Object addon) throws ReflectiveOperationException {
        if (addon == null) return null;
        Class<?> addonClass = addon.getClass();
        String name = stringField(addonClass, addon, "name");
        String[] authors = authorsField(addonClass, addon);
        if (name == null || name.isBlank() || authors.length == 0) return null;

        int addonColor = addonColor(addonClass, addon);
        List<MeteorCreditSegment> segments = new ArrayList<>();
        segments.add(new MeteorCreditSegment(name, addonColor));
        segments.add(new MeteorCreditSegment(" by ", 0xFFAAAAAA));
        for (int i = 0; i < authors.length; i++) {
            if (i > 0) segments.add(new MeteorCreditSegment(i == authors.length - 1 ? " & " : ", ", 0xFFAAAAAA));
            segments.add(new MeteorCreditSegment(authors[i], 0xFFFFFFFF));
        }
        return new MeteorCreditLine(List.copyOf(segments));
    }

    private static String stringField(Class<?> type, Object instance, String name) throws ReflectiveOperationException {
        Object value = type.getField(name).get(instance);
        return value instanceof String text ? text : null;
    }

    private static String[] authorsField(Class<?> type, Object instance) throws ReflectiveOperationException {
        Object value = type.getField("authors").get(instance);
        if (!(value instanceof String[] authors)) return new String[0];
        return authors;
    }

    private static int addonColor(Class<?> type, Object instance) throws ReflectiveOperationException {
        Object color = type.getField("color").get(instance);
        if (color == null) return 0xFFFFFFFF;
        Method getPacked = color.getClass().getMethod("getPacked");
        Object packed = getPacked.invoke(color);
        return packed instanceof Integer intColor ? intColor : 0xFFFFFFFF;
    }

    private void openModMenu() {
        try {
            Class<?> apiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            Method createMethod = apiClass.getMethod("createModsScreen", Screen.class);
            Screen modsScreen = (Screen) createMethod.invoke(null, this);
            this.minecraft.gui.setScreen(modsScreen);
        } catch (Exception e) {  }
    }

    private static String createModCountText() {
        int modCount = dihclient.platform.DihLoader.modIds().size();
        return modCount + (modCount == 1 ? " Mod" : " Mods");
    }

    private void openEssential() {
        try {
            Class<?> clazz = Class.forName("gg.essential.gui.modals.QuickAccessModal");
            Object companion = clazz.getDeclaredField("Companion").get(null);
            Method open = companion.getClass().getDeclaredMethod("open");
            open.setAccessible(true);
            open.invoke(companion);
        } catch (Exception e) {  }
    }

    private void openReplayViewer() {
        try {
            Class<?> replayModuleClass = Class.forName("com.replaymod.replay.ReplayModReplay");
            Field instanceField = replayModuleClass.getField("instance");
            Object replayModule = instanceField.get(null);
            if (replayModule == null) return;

            Class<?> viewerClass = Class.forName("com.replaymod.replay.gui.screen.GuiReplayViewer");
            Constructor<?> constructor = viewerClass.getConstructor(replayModuleClass);
            Object viewer = constructor.newInstance(replayModule);
            Method display = findNoArgMethod(viewerClass, "display");
            if (display == null) return;
            display.setAccessible(true);
            display.invoke(viewer);
        } catch (Exception e) {  }
    }

    private void openFlashback() {
        try {

            Class<?> screenClass = Class.forName("com.moulberry.flashback.screen.select_replay.SelectReplayScreen");
            Constructor<?> constructor = screenClass.getConstructor(Screen.class);
            Object screen = constructor.newInstance(this);
            if (screen instanceof Screen s) this.minecraft.gui.setScreen(s);
        } catch (Exception e) {  }
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private void renderCustomTooltip(GuiGraphicsExtractor graphics, Component tooltip, float uiMouseX, float uiMouseY) {
        dihclient.gui.vanillaui.components.Tooltip.render(
            UiContexts.overlay(graphics, this.font, Math.round(uiMouseX), Math.round(uiMouseY)),
            tooltip.getString(), Math.round(uiMouseX), Math.round(uiMouseY), 220);
    }

    private String serverCountBadge() {
        int count = savedServerCount();
        if (count < 0) return null;
        return Integer.toString(count);
    }

    private int savedServerCount() {
        long now = System.currentTimeMillis();
        if (cachedServerCount >= 0 && now - serverCountCheckedAt < 2000L) return cachedServerCount;
        serverCountCheckedAt = now;
        try {
            ServerList serverList = new ServerList(this.minecraft);
            serverList.load();
            cachedServerCount = serverList.size();
        } catch (RuntimeException ignored) {
            cachedServerCount = -1;
        }
        return cachedServerCount;
    }

    private record StatusRow(String label, Supplier<String> value) {
    }

    private record CategoryRow(String label, int active, int total) {
    }

    private record UtilityButtonSpec(String title, String subtitle, Identifier icon, Identifier sprite,
                                     int iconTextureSize, Runnable onPress, Component tooltip) {
    }

    private record MeteorCreditLine(List<MeteorCreditSegment> segments) {
        private int width(net.minecraft.client.gui.Font font) {
            int width = 0;
            for (MeteorCreditSegment segment : segments) {
                width += UiText.width(font, segment.text(), UiAssets.FONT_BODY, segment.color());
            }
            return width;
        }
    }

    private record MeteorCreditSegment(String text, int color) {
    }

    private final class MenuButton {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final Component label;
        private final boolean enabled;
        private final Runnable onPress;
        private Identifier icon;
        private int iconTextureSize = ICON_TEXTURE_SIZE;
        private Identifier iconSprite;
        private Identifier leftIcon;
        private int leftIconTextureWidth = ICON_TEXTURE_SIZE;
        private int leftIconTextureHeight = ICON_TEXTURE_SIZE;
        private int leftIconDrawSize = 14;
        private Identifier labelTexture;
        private int labelTextureWidth;
        private int labelTextureHeight;
        private int labelDrawWidth;
        private int labelDrawHeight;
        private Component tooltip;
        private String rightBadge;
        private int rowIndex = -1;
        private boolean utilityCell;
        private String utilitySubtitle = "";
        private boolean supportRow;

        private int introDelayMs = 320;
        private boolean introSlide;

        private MenuButton(int x, int y, int width, int height, Component label, boolean enabled, Runnable onPress) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.label = label;
            this.enabled = enabled;
            this.onPress = onPress;
        }

        private MenuButton withTooltip(Component tooltip) {
            this.tooltip = tooltip;
            return this;
        }

        private MenuButton withRightBadge(String rightBadge) {
            this.rightBadge = rightBadge;
            return this;
        }

        private MenuButton withIcon(Identifier icon) {
            return withIcon(icon, ICON_TEXTURE_SIZE);
        }

        private MenuButton withIcon(Identifier icon, int iconTextureSize) {
            this.icon = icon;
            this.iconTextureSize = Math.max(1, iconTextureSize);
            return this;
        }

        private MenuButton withSprite(Identifier sprite) {
            this.iconSprite = sprite;
            return this;
        }

        private MenuButton withLeftIcon(Identifier icon, int textureWidth, int textureHeight, int drawSize) {
            this.leftIcon = icon;
            this.leftIconTextureWidth = Math.max(1, textureWidth);
            this.leftIconTextureHeight = Math.max(1, textureHeight);
            this.leftIconDrawSize = Math.max(1, drawSize);
            return this;
        }

        private MenuButton withLabelTexture(Identifier labelTexture, int labelTextureWidth, int labelTextureHeight, int labelDrawWidth, int labelDrawHeight) {
            this.labelTexture = labelTexture;
            this.labelTextureWidth = labelTextureWidth;
            this.labelTextureHeight = labelTextureHeight;
            this.labelDrawWidth = labelDrawWidth;
            this.labelDrawHeight = labelDrawHeight;
            return this;
        }

        private MenuButton asMainRow(int rowIndex) {
            this.rowIndex = rowIndex;
            if (rowIndex > 0) this.introDelayMs = 60 + rowIndex * 50;
            return this;
        }

        private MenuButton asSupportRow() {
            this.supportRow = true;
            this.introDelayMs = 140;
            this.introSlide = true;
            return this;
        }

        private MenuButton withIntro(int delayMs, boolean slide) {
            this.introDelayMs = delayMs;
            this.introSlide = slide;
            return this;
        }

        private MenuButton asUtility(String subtitle) {
            this.utilityCell = true;
            this.utilitySubtitle = subtitle == null ? "" : subtitle;
            return this;
        }

        private boolean contains(float mouseX, float mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }

        private boolean click(float mouseX, float mouseY) {
            if (!contains(mouseX, mouseY)) return false;

            if (introProgress(introDelayMs) < 1.0f) return false;
            if (!enabled) return true;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(BUTTON_CLICK_SOUND, 1.0F, 0.7F));
            if (onPress != null) onPress.run();
            return true;
        }

        private void render(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float delta) {
            float p = introProgress(introDelayMs);
            if (p <= 0.0f) return;
            if (utilityCell) {
                renderUtility(graphics, mouseX, mouseY, p);
                return;
            }
            if (supportRow) {
                renderSupportRow(graphics, mouseX, mouseY, p);
                return;
            }
            renderMainRow(graphics, mouseX, mouseY, p);
        }

        private void renderMainRow(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float p) {
            boolean hovered = enabled && contains(mouseX, mouseY);
            int yDraw = y + (introSlide ? introOffset(p) : 0);

            UiRenderer.softRect(graphics, UiBounds.of(x, yDraw, width, height), 2,
                fade(enabled ? 0xB8181C28 : 0x80181C28, p));
            float hoverT = dihclient.gui.vanillaui.HoverFades.get(
                dihclient.gui.vanillaui.HoverFades.key(UiBounds.of(x, y, width, height)), hovered);
            int accent = theme.color(UiTone.ACCENT);
            if (hoverT > 0.001f) {
                UiRenderer.softRect(graphics, UiBounds.of(x, yDraw, width, height), 2, fade(accent, 0.24f * hoverT * p));
                UiRenderer.rect(graphics, UiBounds.of(x, yDraw + 3, 2, height - 6), fade(accent, hoverT * p));
            }
            int slide = Math.round(hoverT * 3.0f);

            String marker = rightBadge != null && !rightBadge.isBlank() ? rightBadge : null;
            if (labelTexture != null && labelTextureWidth > 0 && labelTextureHeight > 0 && labelDrawWidth > 0 && labelDrawHeight > 0) {
                int maxW = Math.max(1, width - 8);
                int maxH = Math.max(1, height <= 18 ? height - 4 : height - 8);
                float scale = Math.min(1.0f, Math.min(maxW / (float) labelDrawWidth, maxH / (float) labelDrawHeight));
                int drawW = Math.max(1, Math.round(labelDrawWidth * scale));
                int drawH = Math.max(1, Math.round(labelDrawHeight * scale));

                int textX = x + (width - drawW) / 2;
                int textY = yDraw + (height - drawH) / 2;
                int textColor = fade(DihTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT), p);
                graphics.blit(RenderPipelines.GUI_TEXTURED, labelTexture, textX, textY, 0.0F, 0.0F, drawW, drawH,
                    labelTextureWidth, labelTextureHeight, labelTextureWidth, labelTextureHeight, textColor);
            } else if (label != null) {
                String text = label.getString();
                int textColor = fade(DihTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT), p);
                Identifier fontId = UiAssets.FONT_LABEL;
                int textY = UiSizing.alignTextY(yDraw, height, UiText.fontHeight(fontId), 1);
                int textX;
                String drawText = text;
                if (leftIcon != null) {
                    int iconSize = Math.min(leftIconDrawSize, Math.max(1, Math.min(height - 2, width - 12)));
                    int iconX = x + 3;
                    int iconY = yDraw + (height - iconSize) / 2;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, DihThemeTextures.whitened(leftIcon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                        leftIconTextureWidth, leftIconTextureHeight, leftIconTextureWidth, leftIconTextureHeight,
                        fade(DihTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT), p));
                    textX = iconX + iconSize + 4;
                    int maxTextW = Math.max(1, x + width - 4 - textX);
                    drawText = UiText.trimToWidth(DihTitleScreen.this.font, text, maxTextW, fontId, textColor);
                } else if (rowIndex > 0) {
                    textX = x + 10 + slide;
                    String index = String.format(java.util.Locale.ROOT, "%02d", rowIndex);
                    int indexColor = fade(0x66FFFFFF, p);
                    if (marker == null) {
                        UiText.draw(graphics, DihTitleScreen.this.font, index, fontId, indexColor,
                            x + width - 8 - UiText.width(DihTitleScreen.this.font, index, fontId, indexColor), textY, false);
                    }
                } else {
                    int textW = UiText.width(DihTitleScreen.this.font, text, fontId, textColor);
                    textX = x + (width - textW) / 2;
                }
                UiText.draw(graphics, DihTitleScreen.this.font, drawText, fontId, textColor, textX, textY, false);
            }

            if (marker != null && !marker.isBlank()) {
                Identifier fontId = UiAssets.FONT_LABEL;
                int markerColor = fade(enabled ? DihTheme.recolor(0xFFFFD4D4, Channel.ACCENT) : 0xFF806565, p);
                int markerW = UiText.width(DihTitleScreen.this.font, marker, fontId, markerColor);
                int markerX = x + width - markerW - 6;
                int markerY = UiSizing.alignTextY(yDraw, height, UiText.fontHeight(fontId), 1);
                drawHudText(graphics, marker, markerX, markerY, markerColor, fontId);
            }
        }

        private void renderSupportRow(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float p) {
            boolean hovered = enabled && contains(mouseX, mouseY);
            int yDraw = y + (introSlide ? introOffset(p) : 0);
            UiRenderer.softRect(graphics, UiBounds.of(x, yDraw, width, height), 1, fade(0x14FFFFFF, p));
            float hoverT = dihclient.gui.vanillaui.HoverFades.get(
                dihclient.gui.vanillaui.HoverFades.key(UiBounds.of(x, y, width, height)), hovered);
            if (hoverT > 0.001f) {
                UiRenderer.softRect(graphics, UiBounds.of(x, yDraw, width, height), 1,
                    fade(theme.color(UiTone.ACCENT), 0.3f * hoverT * p));
            }

            if (leftIcon != null) {
                int iconSize = Math.min(leftIconDrawSize, Math.max(1, height - 6));
                int iconX = x + 5;
                int iconY = yDraw + (height - iconSize) / 2;
                int tint = fade(DihTheme.recolor(0xFFF7EEEE, Channel.TEXT), p);
                graphics.blit(RenderPipelines.GUI_TEXTURED, DihThemeTextures.whitened(leftIcon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                    leftIconTextureWidth, leftIconTextureHeight, leftIconTextureWidth, leftIconTextureHeight, tint);
                if (hoverT > 0.001f) {

                    graphics.blit(RenderPipelines.GUI_TEXTURED, DihThemeTextures.whitened(leftIcon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                        leftIconTextureWidth, leftIconTextureHeight, leftIconTextureWidth, leftIconTextureHeight,
                        fade(theme.color(UiTone.ACCENT), hoverT * p));
                }
            }

            int textX = leftIcon != null ? x + 5 + Math.min(leftIconDrawSize, Math.max(1, height - 6)) + 5 : x + 7;
            int maxTextW = Math.max(1, x + width - 4 - textX);
            int lineColor = fade(theme.color(UiTone.BODY), p);
            int arrowColor = fade(hoverT > 0.001f ? 0xFFFFFFFF : 0x66FFFFFF, p);
            UiText.draw(graphics, DihTitleScreen.this.font, "\u2197", UiAssets.FONT_LABEL, arrowColor,
                x + width - 12, UiSizing.alignTextY(yDraw, height, UiText.fontHeight(UiAssets.FONT_LABEL), 1), false);

            String line = UiText.trimToWidth(DihTitleScreen.this.font, label.getString(), maxTextW, UiAssets.FONT_LABEL, lineColor);
            int lineY = UiSizing.alignTextY(yDraw, height, UiText.fontHeight(UiAssets.FONT_LABEL), 1);
            UiText.draw(graphics, DihTitleScreen.this.font, line, UiAssets.FONT_LABEL, lineColor, textX, lineY, false);
        }

        private void renderUtility(GuiGraphicsExtractor graphics, float mouseX, float mouseY, float p) {
            boolean hovered = enabled && contains(mouseX, mouseY);
            UiRenderer.softRect(graphics, UiBounds.of(x, y, width, height), 2, fade(enabled ? 0xB00B0C10 : 0x700B0C10, p));
            float hoverT = dihclient.gui.vanillaui.HoverFades.get(
                dihclient.gui.vanillaui.HoverFades.key(UiBounds.of(x, y, width, height)), hovered);
            if (hoverT > 0.001f) {
                UiRenderer.softRect(graphics, UiBounds.of(x, y, width, height), 2,
                    fade(theme.color(UiTone.ACCENT), 0.35f * hoverT * p));
            }

            if (iconSprite != null) {
                int iconSize = Math.min(VANILLA_SPRITE_SIZE, Math.max(12, height - 6));
                int iconX = x + (width - iconSize) / 2;
                int iconY = y + (height - iconSize) / 2;
                float alpha = (enabled ? 1.0f : 0.45f) * p;
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, iconSprite, iconX, iconY, iconSize, iconSize, alpha);
                return;
            }
            if (icon == null) return;
            int iconSize = Math.min(16, Math.max(12, height - 7));
            int iconX = x + (width - iconSize) / 2;
            int iconY = y + (height - iconSize) / 2;

            int iconTint = fade(DihTheme.recolor(enabled ? 0xFFF7EEEE : 0xFF806565, Channel.TEXT), p);
            graphics.blit(RenderPipelines.GUI_TEXTURED, DihThemeTextures.whitened(icon), iconX, iconY, 0.0F, 0.0F, iconSize, iconSize,
                iconTextureSize, iconTextureSize, iconTextureSize, iconTextureSize, iconTint);
        }

    }
}
