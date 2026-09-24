package dihclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiContexts;
import dihclient.gui.vanillaui.UiTextRenderer;
import dihclient.gui.vanillaui.components.Banner;
import dihclient.modules.DihModule;
import dihclient.modules.Module;
import dihclient.modules.AntiVanishModule;
import dihclient.modules.PackHideState;
import dihclient.modules.ModuleRenderUtil;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.ModuleScreenRenderer;
import dihclient.modules.ModuleNameTagRenderer;
import dihclient.util.DihHudManager;
import dihclient.util.DihCaptureBannerSpec;
import dihclient.util.DihMacroProgressRenderer;
import dihclient.util.DihNotifications;
import dihclient.util.DihOverlayManager;
import dihclient.util.DihPayloadStudySession;
import dihclient.util.DihQueueRenderer;
import dihclient.util.DihServerInfoOverlay;
import dihclient.util.DihSharedState;
import dihclient.util.DihUiScale;
import dihclient.util.macro.MacroExecutor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.DeltaTracker;

@Mixin(Hud.class)
public abstract class DihInGameHudMixin {
    @Unique private static final Minecraft MC = Minecraft.getInstance();
    @Unique private static final int PACKUTIL_RIGHT_PANEL_W = 172;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void yang$renderDihQueue(GuiGraphicsExtractor context, DeltaTracker deltaTracker, CallbackInfo ci) {

        try {
            dih$renderHudBody(context);
        } catch (Throwable t) {
            dih$logRenderError("hudRoot", t);
        }
    }

    @Unique
    private void dih$renderHudBody(GuiGraphicsExtractor context) {
        if (!isDihActive()) return;
        boolean macroFrameWork = MacroExecutor.hasRenderWork();
        dihclient.util.DihRuntimeActivity.Snapshot activity = dihclient.util.DihRuntimeActivity.current();
        long hudWork = dihclient.util.DihRuntimeActivity.HUD_MODULE
            | dihclient.util.DihRuntimeActivity.NAMETAGS
            | dihclient.util.DihRuntimeActivity.OVERLAY
            | dihclient.util.DihRuntimeActivity.HUD_AUX;
        if (!macroFrameWork && !activity.has(hudWork)) return;

        dihclient.gui.vanillaui.UiScissorStack.global().clear(context);
        if (macroFrameWork) MacroExecutor.onRender(1.0f);
        if (MC.gui.hud.isHidden()) return;
        if (PackHideState.isActive()) return;

        DihSharedState shared = DihSharedState.get();
        var screen = MC.gui.screen();
        boolean macroRunning = MacroExecutor.isVisibleRunning();
        boolean queueSending = shared.hasStaggeredPackets();
        boolean queueVisible = shared.shouldDelayGuiPackets()
            || shared.hasDelayedPackets()
            || queueSending;
        boolean captureActive = hasAnyCaptureSession(shared);
        boolean payloadStudyActive = DihPayloadStudySession.isActive();
        Module hud = activity.has(dihclient.util.DihRuntimeActivity.HUD_MODULE) ? ModuleRegistry.get("hud") : null;
        boolean nativeHudVisible = hud != null && DihHudManager.shouldRenderInGame(screen, hud);
        boolean esp2dVisible = ModuleRenderUtil.has2dEspWork();
        boolean nametagsVisible = activity.has(dihclient.util.DihRuntimeActivity.NAMETAGS);
        boolean antiVanishHudVisible = screen == null && AntiVanishModule.shouldShowHud();
        boolean mainHudVisible = nativeHudVisible || antiVanishHudVisible || macroRunning || queueVisible
            || captureActive || payloadStudyActive || esp2dVisible || nametagsVisible;

        DihServerInfoOverlay serverInfoOverlay = null;
        boolean serverProbeBannerVisible = false;
        DihOverlayManager overlayManager = null;
        boolean overlayVisible = false;
        if (screen == null) {
            serverInfoOverlay = DihModule.get().getServerDataOverlayIfExists();
            serverProbeBannerVisible = serverInfoOverlay != null && serverInfoOverlay.shouldRenderBackgroundProbeBanner();
            if (activity.has(dihclient.util.DihRuntimeActivity.OVERLAY)) {
                overlayManager = DihOverlayManager.get();
                overlayVisible = overlayManager.hasVisibleOverlay();
            }
        }
        boolean notificationsVisible = DihNotifications.hasVisible();
        if (!mainHudVisible && !serverProbeBannerVisible && !overlayVisible && !notificationsVisible) return;

        Runnable renderHudElements = () -> {
            int screenWidth = DihUiScale.getVirtualScreenWidth();
            int x = Math.max(0, screenWidth - PACKUTIL_RIGHT_PANEL_W);
            int y = 0;
            DihCaptureBannerSpec captureBanner = captureActive ? captureBannerSpec(shared, context) : null;
            DihCaptureBannerSpec payloadStudyBanner = payloadStudyActive ? payloadStudyBannerSpec(context, captureBanner == null ? 0 : captureBanner.height()) : null;
            java.util.ArrayList<DihHudManager.ElementBounds> hudOccluders = new java.util.ArrayList<>(3);
            if (captureBanner != null) {
                hudOccluders.add(new DihHudManager.ElementBounds("capture_banner",
                    captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()));
            }
            if (payloadStudyBanner != null) {
                hudOccluders.add(new DihHudManager.ElementBounds("payload_study_banner",
                    payloadStudyBanner.x(), payloadStudyBanner.y(), payloadStudyBanner.width(), payloadStudyBanner.height()));
            } else if (captureActive) {
                int fallbackW = Math.min(screenWidth - 16, 300);
                hudOccluders.add(new DihHudManager.ElementBounds("capture_banner",
                    Math.max(0, (screenWidth - fallbackW) / 2), 0, fallbackW, 56));
            }
            if (queueVisible) {
                int queueHeight = DihQueueRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 8);
                if (queueHeight > 0) {
                    hudOccluders.add(new DihHudManager.ElementBounds("packet_queue", x, y, PACKUTIL_RIGHT_PANEL_W, queueHeight));
                    y += queueHeight;
                }
            }
            if (macroRunning) {
                int macroHeight = DihMacroProgressRenderer.measureStacked(MC.font, PACKUTIL_RIGHT_PANEL_W, 10);
                if (macroHeight > 0) hudOccluders.add(new DihHudManager.ElementBounds("macro_queue", x, y, PACKUTIL_RIGHT_PANEL_W, macroHeight));
            }

            if (nativeHudVisible) DihHudManager.render(context, MC.font, false, null, -1, -1, hudOccluders);
            else if (antiVanishHudVisible) DihHudManager.renderSingle(context, MC.font, DihHudManager.ANTI_VANISH);

            if (captureBanner != null) {
                Banner.render(UiContexts.overlay(context, MC.font, 0, 0),
                    UiBounds.of(captureBanner.x(), captureBanner.y(), captureBanner.width(), captureBanner.height()),
                    captureBanner.title(), captureBanner.line1(), captureBanner.line2());
            }
            if (payloadStudyBanner != null) {
                Banner.render(UiContexts.overlay(context, MC.font, 0, 0),
                    UiBounds.of(payloadStudyBanner.x(), payloadStudyBanner.y(), payloadStudyBanner.width(), payloadStudyBanner.height()),
                    payloadStudyBanner.title(), payloadStudyBanner.line1(), payloadStudyBanner.line2());
            }

            y = 0;

            if (queueVisible) {
                int queueHeight = DihQueueRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 8,
                    false, !macroRunning, false);
                y += queueHeight;
            }

            if (macroRunning) {
                DihMacroProgressRenderer.renderStacked(context, MC.font, x, y, PACKUTIL_RIGHT_PANEL_W, 10,
                    false, true, false);
            }

            if (esp2dVisible) ModuleScreenRenderer.render(context);
            if (nametagsVisible) ModuleNameTagRenderer.render(context);
        };

        if (mainHudVisible) {
            long perfStart = dihclient.util.DihPerf.beginSampled();
            DihUiScale.pushOverlayScale(context);
            try {
                renderHudElements.run();
            } catch (Throwable t) {
                dih$logRenderError("hudElements", t);
            } finally {
                DihUiScale.popOverlayScale(context);
            }
            dihclient.util.DihPerf.end("hud.section.hudElements", perfStart);
        }

        if (MC.gui.screen() == null) {
            if (serverProbeBannerVisible) {
                long perfStart = dihclient.util.DihPerf.beginSampled();
                DihUiScale.pushOverlayScale(context);
                try {
                    serverInfoOverlay.renderBackgroundProbeBanner(context);
                } catch (Throwable t) {
                    dih$logRenderError("probeBanner", t);
                } finally {
                    DihUiScale.popOverlayScale(context);
                }
                dihclient.util.DihPerf.end("hud.section.probeBanner", perfStart);
            }

            if (overlayVisible) {
                long perfStart = dihclient.util.DihPerf.beginSampled();
                try {
                    overlayManager.renderAll(context, -1, -1, 0f);
                } catch (Throwable t) {
                    dih$logRenderError("overlays", t);
                }
                dihclient.util.DihPerf.end("hud.section.overlays", perfStart);
            }
        }

        if (notificationsVisible) {
            long perfStart = dihclient.util.DihPerf.beginSampled();
            DihUiScale.pushOverlayScale(context);
            try {
                DihNotifications.render(context);
            } catch (Throwable t) {
                dih$logRenderError("notifications", t);
            } finally {
                DihUiScale.popOverlayScale(context);
            }
            dihclient.util.DihPerf.end("hud.section.notifications", perfStart);
        }
    }

    @Unique private static long dih$lastRenderErrorMs;

    @Unique private net.minecraft.world.level.block.state.BlockState dih$cachedBlockState;
    @Unique private String dih$cachedBlockName = "";
    @Unique private net.minecraft.world.entity.EntityType<?> dih$cachedEntityType;
    @Unique private String dih$cachedEntityLabel = "";

    @Unique
    private void dih$logRenderError(String where, Throwable t) {
        long now = System.currentTimeMillis();
        if (now - dih$lastRenderErrorMs < 5000L) return;
        dih$lastRenderErrorMs = now;
        dihclient.DihClientAddon.LOG.warn("[Dih] HUD section '{}' failed; isolated to protect the UI", where, t);
    }

    @Unique
    private boolean isDihActive() {
        DihModule module = DihModule.get();
        return module != null && module.isActive();
    }

    @Unique
    private DihCaptureBannerSpec captureBannerSpec(DihSharedState shared, GuiGraphicsExtractor graphics) {
        boolean blockCap = shared.hasBlockCaptureCallback();
        boolean entityCap = shared.hasEntityCaptureCallback();
        boolean attackCap = shared.hasAttackCaptureCallback();
        boolean gbreakCap = shared.isGBreakCapturing();
        if (!blockCap && !entityCap && !attackCap && !gbreakCap) return null;

        String title = gbreakCap
            ? "GBreak Capture"
            : (blockCap ? "Block Capture" : (entityCap ? "Entity Capture" : "Position Capture"));
        String line1 = gbreakCap
            ? "Break a block to capture the insta-break packet. Esc = cancel"
            : (blockCap
                ? "Right-click a block to capture it. Esc = cancel"
                : (entityCap
                    ? "Right-click an entity to capture it. Esc = cancel"
                    : "Left-click to capture the target position. Esc = cancel"));
        String line2 = "";
        if (gbreakCap) {
            line2 = "Waiting for the block-break packet from your next block break";
        } else if (blockCap && MC.hitResult != null
                && MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                && MC.level != null) {
            net.minecraft.world.phys.BlockHitResult bhr = (net.minecraft.world.phys.BlockHitResult) MC.hitResult;
            net.minecraft.core.BlockPos bp = bhr.getBlockPos();
            net.minecraft.world.level.block.state.BlockState state = MC.level.getBlockState(bp);
            if (state != dih$cachedBlockState) {
                dih$cachedBlockName = state.getBlock().getName().getString();
                dih$cachedBlockState = state;
            }
            line2 = "Aimed at: " + dih$cachedBlockName + " (" + bp.getX() + ", " + bp.getY() + ", " + bp.getZ() + ")";
        } else if (entityCap && MC.crosshairPickEntity != null && MC.crosshairPickEntity != MC.player) {
            net.minecraft.world.entity.EntityType<?> type = MC.crosshairPickEntity.getType();
            if (type != dih$cachedEntityType) {
                String eName = type.getDescription().getString();
                String eId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                dih$cachedEntityLabel = eName + " (" + eId + ")";
                dih$cachedEntityType = type;
            }
            line2 = "Aimed at: " + dih$cachedEntityLabel;
        }

        int sw = DihUiScale.getVirtualScreenWidth();
        UiTextRenderer text = UiContexts.textRenderer(MC.font);
        int boxWidth = Math.min(sw - 16, Math.max(270, Math.max(
            text.width(title),
            Math.max(
                text.width(line1),
                line2.isEmpty() ? 0 : text.width(line2)
            )
        ) + 18));
        int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
        return new DihCaptureBannerSpec((sw - boxWidth) / 2, 0, boxWidth, height, title, line1, line2);
    }

    @Unique
    private DihCaptureBannerSpec payloadStudyBannerSpec(GuiGraphicsExtractor graphics, int topOffset) {
        String title = DihPayloadStudySession.bannerTitle();
        if (title.isBlank()) return null;
        String line1 = DihPayloadStudySession.bannerLine1();
        String line2 = DihPayloadStudySession.bannerLine2();
        int sw = DihUiScale.getVirtualScreenWidth();
        UiTextRenderer text = UiContexts.textRenderer(MC.font);
        int boxWidth = Math.min(sw - 16, Math.max(276, Math.max(
            text.width(title),
            Math.max(text.width(line1), text.width(line2))
        ) + 18));
        int height = Banner.height(UiContexts.overlay(graphics, MC.font, 0, 0), boxWidth, line1, line2);
        return new DihCaptureBannerSpec((sw - boxWidth) / 2, Math.max(0, topOffset), boxWidth, height, title, line1, line2);
    }

    @Unique
    private boolean hasAnyCaptureSession(DihSharedState shared) {
        if (shared == null) return false;
        if (shared.isCaptureMode() || shared.hasCaptureCancelCallback() || shared.hasAttackCaptureCallback()
            || shared.hasBlockCaptureCallback() || shared.hasEntityCaptureCallback() || shared.isGBreakCapturing()) {
            return true;
        }
        dihclient.gui.macro.editor.ActionEditorOverlay editor =
            dihclient.gui.macro.editor.ActionEditorOverlay.getSharedOverlayIfExists();
        return editor != null && editor.hasActiveCaptureSession();
    }
}
