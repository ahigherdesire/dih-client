package dihclient.mixin;

import dihclient.util.DihKeys;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dihclient.modules.DihModule;
import dihclient.modules.InventoryTweaksModule;
import dihclient.modules.NameCensorModule;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiTextRenderer;
import dihclient.gui.vanillaui.components.Banner;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihColors;
import dihclient.util.DihCustomFilterOverlay;
import dihclient.util.DihCustomFilterPresetOverlay;
import dihclient.util.DihFabricatorOverlay;
import dihclient.util.DihFabricatorRegistry;
import dihclient.util.DihLANSync;
import dihclient.util.DihLANSyncOverlay;
import dihclient.util.DihLauncherOverlay;
import dihclient.util.DihMacroListOverlay;
import dihclient.util.DihQueueEditorOverlay;
import dihclient.util.IDihOverlay;
import dihclient.util.DihInventoryMoveHelper;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihMacroEditorOverlay;
import dihclient.util.DihItemNbtInspectOverlay;
import dihclient.util.DihPacketLoggerOverlay;
import dihclient.util.DihShulkerPreview;
import dihclient.util.DihText;
import dihclient.util.DihUiScale;

import dihclient.util.DihSharedState;
import dihclient.util.DihCursorClickHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Shadow;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

@Mixin(AbstractContainerScreen.class)
public abstract class DihHandledScreenMixin<T extends AbstractContainerMenu> extends Screen {
    @Shadow @Nullable protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput actionType);
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private Slot dih$blockedFocusedSlot;

    @Unique private DihLauncherOverlay launcherOverlay;
    @Unique private DihFabricatorOverlay fabricatorOverlay;
    @Unique private DihLANSyncOverlay lanSyncOverlay;
    @Unique private DihMacroListOverlay macroListOverlay;
    @Unique private DihQueueEditorOverlay queueEditorOverlay;
    @Unique private DihPacketLoggerOverlay packetLoggerOverlay;
    @Unique private DihCustomFilterOverlay customFilterOverlay;
    @Unique private DihCustomFilterPresetOverlay customFilterPresetOverlay;
    @Unique private DihMacroEditorOverlay macroEditorOverlay;
    @Unique private DihItemNbtInspectOverlay itemNbtInspectOverlay;
    @Unique private dihclient.util.DihKeybindOverlay keybindOverlay;
    @Unique private dihclient.util.DihServerInfoOverlay serverInfoOverlay;
    @Unique private dihclient.util.IDihOverlay matchmakingOverlay;
    @Unique private dihclient.util.IDihOverlay profilesOverlay;
    @Unique private Button inventoryTweaksStealButton;
    @Unique private Button inventoryTweaksDumpButton;
    @Unique private boolean dih$overlaysBuilt;
    @Unique private int inventoryTweaksLastShiftDragSlot = -1;
    @Unique private ItemStack dih$cursorClickBeforeCarried = ItemStack.EMPTY;
    @Unique private ItemStack dih$cursorClickBeforeSlot = ItemStack.EMPTY;
    @Unique private int dih$cursorClickBeforeSlotId = -1;
    @Unique private int dih$cursorClickBeforeButton = 0;
    @Unique private ContainerInput dih$cursorClickBeforeInput = null;

    protected DihHandledScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void dih$syncInvMoveOnInit(CallbackInfo ci) {
        if (!isDihActive()) return;
        DihInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dih$syncInvMoveOnTick(CallbackInfo ci) {
        if (!isDihActive()) return;
        DihInventoryMoveHelper.syncHeldMovementKeysIfSafe();
    }

    @ModifyArg(
        method = "extractLabels",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"),
        index = 1,
        require = 0
    )
    private Component dih$censorContainerLabel(Component component) {
        return NameCensorModule.censorServerComponent(component);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void yang$init(CallbackInfo ci) {

        Screen screen = (Screen)(Object)this;
        ScreenEvents.afterExtract(screen).register((scrn, drawContext, mouseX, mouseY, tickDelta) -> {
            if (!isDihActive()) return;
            try {
                DihOverlayManager.get().renderAll(drawContext, mouseX, mouseY, tickDelta);
                DihUiScale.pushOverlayScale(drawContext);
                try {
                    renderMacroCaptureBanner(drawContext);
                } finally {
                    DihUiScale.popOverlayScale(drawContext);
                }
                dih$renderShulkerPreview(drawContext, mouseX, mouseY);
            } catch (Throwable t) {
                dih$logOverlayError(t);
            }
        });
        try {
            long perfStart = dihclient.util.DihPerf.begin();

            if (!dih$overlaysBuilt || !DihOverlayManager.get().hasRegisteredOverlays()) {
                dih$buildInventoryOverlays();
                dih$overlaysBuilt = true;
            }
            dihclient.util.DihPerf.endSpike("mixin.buildInventoryOverlays", perfStart, 10_000_000L);
        } catch (Throwable t) {
            dih$logOverlayError(t);
        }
    }

    @Unique
    private void dih$buildInventoryOverlays() {
        DihLANSync.getInstance().setOnSessionStateChanged(() -> {});

        AbstractContainerScreen<?> handledScreen = (AbstractContainerScreen<?>) (Object) this;
        fabricatorOverlay = DihFabricatorOverlay.getSharedOverlay(handledScreen);
        lanSyncOverlay = DihLANSyncOverlay.getSharedOverlay(this.font);
        macroListOverlay = new DihMacroListOverlay(this.font);
        queueEditorOverlay = new DihQueueEditorOverlay(this.font);
        customFilterOverlay = new DihCustomFilterOverlay(this.font);
        customFilterPresetOverlay = customFilterOverlay.getPresetManagerOverlay();
        fabricatorOverlay.restoreState();
        lanSyncOverlay.restoreState();
        macroListOverlay.restoreState();
        queueEditorOverlay.restoreState();
        customFilterOverlay.restoreLayout();
        if (customFilterPresetOverlay != null) {
            customFilterPresetOverlay.restoreLayout();
        }

        macroEditorOverlay = DihMacroEditorOverlay.getSharedOverlay();
        if (macroEditorOverlay != null) {
            macroEditorOverlay.restoreState();
        }

        DihOverlayManager manager = DihOverlayManager.get();
        manager.clear();
        manager.register(fabricatorOverlay);
        manager.register(lanSyncOverlay);
        manager.register(macroListOverlay);
        manager.register(queueEditorOverlay);
        manager.register(customFilterOverlay);
        if (customFilterPresetOverlay != null) {
            manager.register(customFilterPresetOverlay);
        }
        if (macroEditorOverlay != null) {
            manager.register(macroEditorOverlay);
        }

        manager.register(dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlay());
        itemNbtInspectOverlay = DihItemNbtInspectOverlay.getSharedOverlay(this.font);
        if (itemNbtInspectOverlay != null) manager.register(itemNbtInspectOverlay);

        DihModule dihModule = DihModule.get();

        if (!dihclient.util.DihLiteVariant.enabled()) {
            dihclient.util.IDihOverlay multiOverlay = dihModule == null ? null : dihModule.getMultiOverlayIfExists();
            if (multiOverlay != null && multiOverlay.isVisible()) manager.register(multiOverlay);
        }
        keybindOverlay = new dihclient.util.DihKeybindOverlay();
        keybindOverlay.restoreLayout();
        manager.register(keybindOverlay);

        launcherOverlay = new DihLauncherOverlay(macroListOverlay, fabricatorOverlay, lanSyncOverlay, queueEditorOverlay, packetLoggerOverlay, customFilterOverlay);
        launcherOverlay.setKeybindOverlay(keybindOverlay);
        launcherOverlay.setPacketLoggerOverlaySupplier(() -> {
            if (packetLoggerOverlay == null && dihModule != null) {
                packetLoggerOverlay = dihModule.getPacketLoggerOverlay();
                if (packetLoggerOverlay != null) packetLoggerOverlay.restoreState();
            }
            if (packetLoggerOverlay != null) manager.register(packetLoggerOverlay);
            return packetLoggerOverlay;
        });
        launcherOverlay.setServerDataOverlaySupplier(() -> {
            if (serverInfoOverlay == null) {
                serverInfoOverlay = dihclient.modules.DihModule.get().getServerDataOverlay();
            }
            if (serverInfoOverlay != null) manager.register(serverInfoOverlay);
            return serverInfoOverlay;
        });
        if (packetLoggerOverlay == null && dihclient.util.DihPacketLoggerOverlay.shouldRestoreSavedVisible()) {
            packetLoggerOverlay = dihclient.modules.DihModule.get().getPacketLoggerOverlay();
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.restoreState();
                if (packetLoggerOverlay.isVisible()) manager.register(packetLoggerOverlay);
            }
        }
        if (serverInfoOverlay == null && dihclient.util.DihServerInfoOverlay.shouldRestoreSavedVisible()) {
            serverInfoOverlay = dihclient.modules.DihModule.get().getServerDataOverlay();
            if (serverInfoOverlay != null) {
                serverInfoOverlay.restoreState();
                if (serverInfoOverlay.isVisible()) manager.register(serverInfoOverlay);
            }
        }

        if (!dihclient.util.DihLiteVariant.enabled()) {
            matchmakingOverlay = dihclient.modules.DihModule.get().getMatchmakingOverlay();
            if (matchmakingOverlay != null && matchmakingOverlay.isVisible()) {
                manager.register(matchmakingOverlay);
            }
            profilesOverlay = dihclient.modules.DihModule.get().getProfilesOverlay();
            if (profilesOverlay != null && profilesOverlay.isVisible()) {
                manager.register(profilesOverlay);
            }
        }
        launcherOverlay.restoreLayout();
        manager.register(launcherOverlay);

        inventoryTweaksStealButton = Button.builder(Component.literal("Steal"), button -> InventoryTweaksModule.stealFromButton())
                .bounds(leftPos, topPos - 22, 40, 20)
                .build();
        inventoryTweaksDumpButton = Button.builder(Component.literal("Dump"), button -> InventoryTweaksModule.dumpFromButton())
                .bounds(leftPos + 42, topPos - 22, 40, 20)
                .build();
        addRenderableWidget(inventoryTweaksStealButton);
        addRenderableWidget(inventoryTweaksDumpButton);

        refreshButtonVisibility();
    }

    @Unique private static long dih$lastOverlayErrorMs;

    @Unique
    private void dih$logOverlayError(Throwable t) {
        long now = System.currentTimeMillis();
        if (now - dih$lastOverlayErrorMs < 5000L) return;
        dih$lastOverlayErrorMs = now;
        dihclient.DihClientAddon.LOG.warn("[Dih] in-screen overlay render failed; isolated to protect the UI", t);
    }

    @Unique private boolean coreExpanded = true;
    @Unique private boolean queueExpanded = false;
    @Unique private boolean toolsExpanded = false;
    @Unique private int coreButtonsStartY, queueHeaderY, queueButtonsStartY, queueButtonsEndY;
    @Unique private int toolsHeaderY, toolsButtonsStartY, toolsButtonsEndY;

    @Unique
    private void refreshButtonVisibility() {
        boolean visible = isDihActive() && MC != null && MC.player != null
                && InventoryTweaksModule.shouldShowButtons(MC.player.containerMenu);
        if (inventoryTweaksStealButton != null) {
            inventoryTweaksStealButton.visible = visible;
            inventoryTweaksStealButton.active = visible;
            inventoryTweaksStealButton.setX(leftPos);
            inventoryTweaksStealButton.setY(topPos - 22);
        }
        if (inventoryTweaksDumpButton != null) {
            inventoryTweaksDumpButton.visible = visible;
            inventoryTweaksDumpButton.active = visible;
            inventoryTweaksDumpButton.setX(leftPos + 42);
            inventoryTweaksDumpButton.setY(topPos - 22);
        }
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void yang$blockCoveredSlotHover(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            dih$blockedFocusedSlot = hoveredSlot;
            hoveredSlot = null;
        } else {
            dih$blockedFocusedSlot = null;
        }
    }

    @Inject(method = "getHoveredSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$blockCoveredSlotLookup(double mouseX, double mouseY, CallbackInfoReturnable<Slot> cir) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$blockCoveredSlotHitbox(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().shouldBlockUnderlyingHover(pointX, pointY)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void dih$blockCoveredHandledTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().shouldBlockUnderlyingHover(x, y)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void yang$render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (dih$blockedFocusedSlot != null) {
            hoveredSlot = dih$blockedFocusedSlot;
            dih$blockedFocusedSlot = null;
        }
        refreshButtonVisibility();
    }

    @Unique
    private void renderMacroCaptureBanner(GuiGraphicsExtractor context) {
        dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        dihclient.util.DihAdminToolsOverlay adminToolsOverlay = null;
        boolean adminCapture = false;

        if (!dihclient.util.DihLiteVariant.enabled()) {
            adminToolsOverlay = dihclient.util.DihAdminToolsOverlay.getSharedOverlayIfExists();
            adminCapture = adminToolsOverlay != null && adminToolsOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        }
        boolean macroCapture = macroEditorOverlay != null && macroEditorOverlay.shouldRenderAbstractContainerScreenCaptureBanner();
        boolean actionCapture = actionEditor != null && actionEditor.shouldRenderAbstractContainerScreenCaptureBanner();
        if (!macroCapture && !actionCapture && !adminCapture) return;
        if (MC == null || MC.getWindow() == null || this.font == null) return;

        String title = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureTitle()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureTitle()

                : (!dihclient.util.DihLiteVariant.enabled()
                    ? adminToolsOverlay.getAbstractContainerScreenCaptureTitle() : "");
        String instruction = macroCapture
                ? macroEditorOverlay.getAbstractContainerScreenCaptureInstruction()
                : actionCapture
                ? actionEditor.getAbstractContainerScreenCaptureInstruction()
                : (!dihclient.util.DihLiteVariant.enabled()
                    ? adminToolsOverlay.getAbstractContainerScreenCaptureInstruction() : "");
        String hover = "";

        if (hoveredSlot != null) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            String itemName = stack.isEmpty() ? "" : stack.getHoverName().getString();
            String registryId = stack.isEmpty() ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            hover = macroCapture
                    ? macroEditorOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : actionCapture
                    ? actionEditor.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId)
                    : (!dihclient.util.DihLiteVariant.enabled()
                        ? adminToolsOverlay.getAbstractContainerScreenCaptureHoverText(hoveredSlot, itemName, registryId) : "");
        }

        UiTextRenderer text = UiContexts.textRenderer(this.font);
        int maxTextWidth = Math.max(text.width(title), text.width(instruction));
        if (!hover.isEmpty()) {
            maxTextWidth = Math.max(maxTextWidth, text.width(hover));
        }

        int screenWidth = DihUiScale.getVirtualScreenWidth();
        int boxWidth = Math.min(screenWidth - 16, Math.max(250, maxTextWidth + 18));
        int boxX = (screenWidth - boxWidth) / 2;
        int boxY = 0;
        var uiContext = UiContexts.overlay(context, this.font, 0, 0);
        int bannerHeight = Banner.height(uiContext, boxWidth, instruction, hover);
        Banner.render(uiContext, UiBounds.of(boxX, boxY, boxWidth, bannerHeight),
            title, instruction, hover);
        if (actionCapture && actionEditor != null && actionEditor.hasAbstractContainerScreenCaptureToasts()) {
            actionEditor.renderAbstractContainerScreenCaptureToasts(context, boxX, boxY + bannerHeight + 6, boxWidth);
        }
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void yang$removed(CallbackInfo ci) {
        if (!isDihActive()) return;
        DihInventoryMoveHelper.releaseMovementKeysIfSafe();

        dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        boolean skipTransientCaptureSave = actionEditor != null && actionEditor.hasActiveCaptureSession();

        DihOverlayManager.get().restoreClampedAwayBounds();

        long perfStart = dihclient.util.DihPerf.begin();
        if (!skipTransientCaptureSave) {
            if (fabricatorOverlay != null) {
                fabricatorOverlay.saveState();
            }
            if (lanSyncOverlay != null) {
                lanSyncOverlay.saveState();
            }
            if (macroListOverlay != null) {
                macroListOverlay.saveState();
            }
            if (queueEditorOverlay != null) {
                queueEditorOverlay.saveState();
            }
            if (macroEditorOverlay != null) {
                macroEditorOverlay.saveState();
            }
            if (launcherOverlay != null) {
                launcherOverlay.saveLayout();
            }
            if (packetLoggerOverlay != null) {
                packetLoggerOverlay.saveState();
            }
            if (customFilterOverlay != null) {
                customFilterOverlay.saveLayout();
            }
            if (customFilterPresetOverlay != null) {
                customFilterPresetOverlay.saveLayout();
            }
            if (keybindOverlay != null) {
                keybindOverlay.saveLayout();
            }
            if (serverInfoOverlay != null) {
                serverInfoOverlay.saveState();
            }
            if (matchmakingOverlay != null) {
                matchmakingOverlay.saveLayout();
            }
            if (profilesOverlay != null) {
                profilesOverlay.saveLayout();
            }
        }
        dihclient.util.DihPerf.endSpike("mixin.removedSaveState", perfStart, 10_000_000L);

        DihOverlayManager.get().clear();
    }

    @Unique
    private void dih$renderShulkerPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (hoveredSlot == null) return;
        if (DihOverlayManager.get().shouldBlockUnderlyingHover(mouseX, mouseY)) return;
        ItemStack stack = hoveredSlot.getItem();
        if (!DihShulkerPreview.shouldPreview(stack)) return;

        context.nextStratum();
        DihShulkerPreview.render(context, this.font, stack, hoveredSlot.index, mouseX, mouseY, this.width, this.height);
    }

    @Unique
    private boolean isDihActive() {
        DihModule module = DihModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private void updateButtonLabels() {
    }

    @Unique
    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Unique
    private static String stateText(boolean value) {
        return value ? "enabled" : "disabled";
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void yang$mouseClicked(MouseButtonEvent click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();

        DihOverlayManager manager = DihOverlayManager.get();

        if (manager.handleMouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortMouse(button, hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (button == 1 && hoveredSlot != null && click.hasControlDown() && click.hasShiftDown()) {
            net.minecraft.world.item.ItemStack stack = hoveredSlot.getItem();
            if (!stack.isEmpty()) {
                if (itemNbtInspectOverlay == null) {
                    itemNbtInspectOverlay = new DihItemNbtInspectOverlay(this.font);
                    manager.register(itemNbtInspectOverlay);
                }
                itemNbtInspectOverlay.open(stack, (int) Math.round(mouseX + 8), (int) Math.round(mouseY + 8));
                cir.setReturnValue(true);
                return;
            }
        }

        if (button == 1 && hoveredSlot != null) {
            net.minecraft.world.item.ItemStack captureStack = hoveredSlot.getItem();
            String captureItemName   = captureStack.isEmpty() ? "" : captureStack.getHoverName().getString();
            String captureRegistryId = captureStack.isEmpty() ? "" :
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(captureStack.getItem()).toString();

            DihMacroEditorOverlay editor = macroEditorOverlay;
            if (editor != null && editor.wantsSlotCapture()
                    && editor.onSlotRightClick(hoveredSlot, captureItemName, captureRegistryId)) {
                cir.setReturnValue(true);
                return;
            }

            boolean captureSlotNumber = click.hasControlDown() && !click.hasShiftDown();
            dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.wantsItemSlotCapture()
                    && actionEditor.onInventorySlotCapture(hoveredSlot, captureItemName, captureRegistryId, captureSlotNumber)) {
                cir.setReturnValue(true);
                return;
            }

            if (!dihclient.util.DihLiteVariant.enabled()) {
                dihclient.util.DihAdminToolsOverlay adminToolsOverlay =
                        dihclient.util.DihAdminToolsOverlay.getSharedOverlay();
                if (adminToolsOverlay != null && adminToolsOverlay.wantsItemStackCapture()
                        && adminToolsOverlay.onInventoryItemStackCapture(hoveredSlot)) {
                    cir.setReturnValue(true);
                    return;
                }
            }

            if (dihclient.util.DihContainerHold.hasPendingCapture()) {
                dihclient.util.DihPacketClick.Target captured =
                        dihclient$buildPacketClickTarget(hoveredSlot, captureItemName);
                if (captured != null && dihclient.util.DihContainerHold.deliverCapture(captured)) {
                    cir.setReturnValue(true);
                    return;
                }
            }
        }

        if (fabricatorOverlay != null && fabricatorOverlay.isVisible() && button == 1 && hoveredSlot != null) {
            fabricatorOverlay.onSlotClick(hoveredSlot, button);
            cir.setReturnValue(true);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private dihclient.util.DihPacketClick.Target dihclient$buildPacketClickTarget(
            net.minecraft.world.inventory.Slot slot, String itemName) {
        if (MC == null || MC.player == null || slot == null) return null;
        net.minecraft.world.inventory.AbstractContainerMenu handler = MC.player.containerMenu;
        if (handler == null) return null;
        net.minecraft.client.gui.screens.Screen screen = MC.gui.screen();
        String screenTitle = (screen != null && screen.getTitle() != null) ? screen.getTitle().getString() : "";
        String menuClass = handler.getClass().getName();
        int visibleSlot = dihclient.util.DihInventoryHelper.toUserVisibleSlot(MC, slot.index);
        return new dihclient.util.DihPacketClick.Target(
                handler.containerId,
                handler.getStateId(),
                slot.index,
                visibleSlot,
                screenTitle,
                menuClass,
                itemName == null ? "" : itemName,
                dihclient.util.DihPacketClick.Mode.RIGHT_CLICK,
                System.currentTimeMillis()
        );
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void yang$keyPressed(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        boolean inventoryKey = MC != null && MC.options != null && MC.options.keyInventory.matches(input);
        if (inventoryKey) {
            if (DihSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (DihOverlayManager.get().handleKeyPressed(input.key(), DihKeys.secondaryCode(input), input.modifiers())) {
            cir.setReturnValue(true);
            return;
        }

        if (InventoryTweaksModule.handleSortKey(input.key(), hoveredSlot)) {
            cir.setReturnValue(true);
            return;
        }

        if (input.key() == InputConstants.KEY_ESCAPE) {
            if (DihSharedState.get().consumeCaptureCancelCallback()) {
                cir.setReturnValue(true);
                return;
            }

            dihclient.gui.macro.editor.ActionEditorOverlay actionEditor =
                    dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
            if (actionEditor != null && actionEditor.cancelCaptureIfActive()) {
                cir.setReturnValue(true);
            }
        }

        if (DihInventoryMoveHelper.handleKeyEvent(input, true)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true, require = 0)
    private void yang$keyReleased(KeyEvent input, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        if (DihInventoryMoveHelper.handleKeyEvent(input, false)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void yang$mouseReleased(MouseButtonEvent click, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;
        inventoryTweaksLastShiftDragSlot = -1;

        if (DihOverlayManager.get().handleMouseReleased(click.x(), click.y(), click.button())) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), require = 0)
    private void dih$captureCursorClickOriginBefore(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        dih$cursorClickBeforeCarried = ItemStack.EMPTY;
        dih$cursorClickBeforeSlot = ItemStack.EMPTY;
        dih$cursorClickBeforeSlotId = slotId;
        dih$cursorClickBeforeButton = button;
        dih$cursorClickBeforeInput = actionType;
        if (MC.player == null || MC.player.containerMenu == null) return;
        AbstractContainerMenu handler = MC.player.containerMenu;
        dih$cursorClickBeforeCarried = handler.getCarried().copy();
        if (slotId >= 0 && slotId < handler.slots.size()) {
            dih$cursorClickBeforeSlot = handler.slots.get(slotId).getItem().copy();
        }
    }

    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("TAIL"), require = 0)
    private void dih$captureCursorClickOriginAfter(Slot slot, int slotId, int button, ContainerInput actionType, CallbackInfo ci) {
        if (MC.player == null || MC.player.containerMenu == null) return;
        if (slotId != dih$cursorClickBeforeSlotId || button != dih$cursorClickBeforeButton || actionType != dih$cursorClickBeforeInput) return;
        DihCursorClickHelper.recordAfterContainerClick(
                MC,
                MC.player.containerMenu,
                slotId,
                button,
                actionType,
                dih$cursorClickBeforeCarried,
                dih$cursorClickBeforeSlot
        );
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void yang$mouseDragged(MouseButtonEvent click, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().handleMouseDragged(click.x(), click.y(), click.button(), deltaX, deltaY)) {
            cir.setReturnValue(true);
            return;
        }

        if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && click.hasShiftDown()
                && hoveredSlot != null && !hoveredSlot.getItem().isEmpty()
                && InventoryTweaksModule.shouldShiftDragMove()
                && inventoryTweaksLastShiftDragSlot != hoveredSlot.index) {
            inventoryTweaksLastShiftDragSlot = hoveredSlot.index;
            slotClicked(hoveredSlot, hoveredSlot.index, 0, ContainerInput.QUICK_MOVE);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void yang$mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount, CallbackInfoReturnable<Boolean> cir) {
        if (!isDihActive()) return;

        if (DihOverlayManager.get().handleMouseScrolled(mouseX, mouseY, verticalAmount)) {
            cir.setReturnValue(true);
            return;
        }
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent input) {
        if (!isDihActive()) return super.charTyped(input);

        if (DihOverlayManager.get().handleCharTyped((char) input.codepoint(), 0)) {
            return true;
        }

        return super.charTyped(input);
    }
}
