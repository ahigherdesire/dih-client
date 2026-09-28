package dihclient.trade;

import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.modules.AutoTradeModule;
import dihclient.modules.PackHideState;
import dihclient.util.DihUiScale;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Draws each villager's best remembered offer (e.g. "mending 1 – 14e") above its head, in the HUD pass. */
public final class TradeLabels {

    private static final double MAX_DISTANCE = 24;
    private static final int BG = 0x90000000;
    private static final int COLOR = 0xFF7FE0A0;

    private TradeLabels() {
    }

    public static void render(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        if (PackHideState.isActive() || mc.level == null || mc.player == null || mc.gui.hud.isHidden()) return;
        if (!AutoTradeModule.labelsOn()) return;
        OfferCache cache = TradeCaches.current();
        if (cache.size() == 0) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null) return;
        Vec3 cam = camera.position();
        Matrix4f matrix = camera.getViewRotationProjectionMatrix(new Matrix4f());
        int width = DihUiScale.getVirtualScreenWidth();
        int height = DihUiScale.getVirtualScreenHeight();
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (AbstractVillager villager : mc.level.getEntitiesOfClass(AbstractVillager.class,
                mc.player.getBoundingBox().inflate(MAX_DISTANCE), AbstractVillager::isAlive)) {
            VillagerOffers offers = cache.get(villager.getStringUUID());
            String label = offers == null ? null : offers.label();
            if (label == null) continue;
            double x = Mth.lerp(delta, villager.xOld, villager.getX());
            double y = Mth.lerp(delta, villager.yOld, villager.getY()) + villager.getBbHeight() + 0.75;
            double z = Mth.lerp(delta, villager.zOld, villager.getZ());
            Vector4f v = new Vector4f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z), 1.0f);
            matrix.transform(v);
            if (v.w <= 0.001f) continue;
            float sx = (v.x / v.w * 0.5f + 0.5f) * width;
            float sy = (0.5f - v.y / v.w * 0.5f) * height;
            if (!Float.isFinite(sx) || !Float.isFinite(sy)) continue;
            int w = mc.font.width(label);
            int left = Math.round(sx) - w / 2;
            int top = Math.round(sy) - 10;
            UiRenderer.rect(context, UiBounds.of(left - 2, top - 1, w + 4, 10), BG);
            context.text(mc.font, label, left, top, COLOR, true);
        }
    }
}
