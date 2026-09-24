/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.RenderEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.listener.AbstractGameEventListener;
import baritone.utils.BaritoneRenderBuffer;
import baritone.utils.IRenderer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ESP overlay: highlights nearby matching blocks, other players, dropped items,
 * and hostile mobs, with optional labels (name + distance) and view tracers.
 *
 * <ul>
 *   <li><b>Block ESP</b> ({@code #esp blocks}) — the cube of half-extent {@link
 *       baritone.api.Settings#espBlockRange} around you is scanned <em>incrementally</em>,
 *       one X-column per few thousand blocks of budget per tick, so a large radius
 *       never freezes the client (the old all-at-once scan of a radius-24 cube was
 *       ~117k block reads on the render thread — that is what dropped FPS to single
 *       digits). Matches are cached and drawn as boxes each frame; a fresh pass
 *       starts {@link baritone.api.Settings#espBlockRescanTicks} ticks after the last.</li>
 *   <li><b>Player ESP</b> ({@code #esp players}) — the vanilla glowing outline forced
 *       by {@code MixinEntity} (a real silhouette through walls), plus an optional
 *       label/tracer.</li>
 *   <li><b>Item ESP</b> ({@code #esp items}) / <b>Mob ESP</b> ({@code #esp mobs})
 *       — boxes around dropped items / hostile mobs, with labels and tracers.</li>
 * </ul>
 */
public final class EspBehavior extends Behavior implements AbstractGameEventListener {

    /** Published matches, drawn each frame. Swapped in only when a scan pass completes. */
    private final List<BlockPos> blockHits = new ArrayList<>();
    /** Matches accumulated by the in-progress incremental pass. */
    private final List<BlockPos> building = new ArrayList<>();

    private boolean scanActive;
    private int restCountdown;
    // Fixed snapshot of the current pass so the player moving mid-scan can't skew it.
    private int passCx, passCz, passMinY, passMaxY, passRange, cursorX, passLimit;
    private String[] passWanted;

    /** Above this many highlighted blocks, per-block labels are suppressed to avoid text spam. */
    private static final int MAX_BLOCK_LABELS = 40;
    /** Rough block-read budget spent per tick on the incremental scan. */
    private static final int SCAN_BUDGET_PER_TICK = 2500;

    public EspBehavior(Baritone baritone) {
        super(baritone);
    }

    // ── Incremental block scan (spread across ticks, hard per-tick budget) ──────

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            resetScan();
            return;
        }
        if (!Baritone.settings().espBlocks.value) {
            if (!blockHits.isEmpty() || scanActive) {
                resetScan();
            }
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        if (!scanActive) {
            if (restCountdown > 0) {
                restCountdown--;
                return;
            }
            startPass();
            if (!scanActive) {
                return; // nothing to match against
            }
        }
        scanBudget();
    }

    private void resetScan() {
        blockHits.clear();
        building.clear();
        scanActive = false;
        restCountdown = 0;
    }

    private void startPass() {
        String[] wanted = Baritone.settings().espBlockList.value.toLowerCase(Locale.ROOT).split("[,\\s]+");
        boolean any = false;
        for (String w : wanted) {
            if (!w.isBlank()) {
                any = true;
                break;
            }
        }
        if (!any) {
            blockHits.clear();
            restCountdown = Math.max(1, Baritone.settings().espBlockRescanTicks.value);
            return;
        }
        final Level world = ctx.world();
        final BlockPos center = ctx.player().blockPosition();
        passWanted = wanted;
        passRange = Math.max(1, Baritone.settings().espBlockRange.value);
        passLimit = Math.max(1, Baritone.settings().espBlockLimit.value);
        passCx = center.getX();
        passCz = center.getZ();
        passMinY = Math.max(world.getMinY(), center.getY() - passRange);
        passMaxY = Math.min(world.getMinY() + world.getHeight() - 1, center.getY() + passRange);
        cursorX = -passRange;
        building.clear();
        scanActive = true;
    }

    private void scanBudget() {
        final Level world = ctx.world();
        final int zSpan = 2 * passRange + 1;
        final int ySpan = passMaxY - passMinY + 1;
        final int columnCost = zSpan * ySpan;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int budget = SCAN_BUDGET_PER_TICK;
        while (scanActive && cursorX <= passRange) {
            final int x = passCx + cursorX;
            for (int z = passCz - passRange; z <= passCz + passRange; z++) {
                for (int y = passMinY; y <= passMaxY; y++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (matches(path, passWanted)) {
                        building.add(pos.immutable());
                        if (building.size() >= passLimit) {
                            finishPass();
                            return;
                        }
                    }
                }
            }
            cursorX++;
            budget -= columnCost;
            if (budget <= 0) {
                break; // resume next tick
            }
        }
        if (cursorX > passRange) {
            finishPass();
        }
    }

    private void finishPass() {
        blockHits.clear();
        blockHits.addAll(building);
        building.clear();
        scanActive = false;
        restCountdown = Math.max(1, Baritone.settings().espBlockRescanTicks.value);
    }

    private static boolean matches(String path, String[] wanted) {
        for (String w : wanted) {
            if (!w.isBlank() && path.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void onRenderPass(RenderEvent event) {
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        final boolean depthIgnored = Baritone.settings().espIgnoreDepth.value;
        final boolean labels = Baritone.settings().espLabels.value;
        final boolean tracers = Baritone.settings().espTracers.value;
        final float width = Baritone.settings().espLineWidthPixels.value;
        final float pt = event.getPartialTicks();
        final PoseStack stack = event.getModelViewStack();

        final List<Label> pendingLabels = new ArrayList<>();
        final Vec3 eye = ctx.player().getEyePosition(pt);

        // ── Blocks ──────────────────────────────────────────────────────────
        if (Baritone.settings().espBlocks.value && !blockHits.isEmpty()) {
            BufferBuilder bb = IRenderer.startLines(Baritone.settings().colorEspBlock.value, 1.0f);
            for (BlockPos p : blockHits) {
                AABB box = new AABB(p.getX(), p.getY(), p.getZ(),
                        p.getX() + 1.0, p.getY() + 1.0, p.getZ() + 1.0);
                IRenderer.emitAABB(bb, stack, box, width);
            }
            IRenderer.endLines(bb, depthIgnored);

            if (labels && blockHits.size() <= MAX_BLOCK_LABELS) {
                int argb = argb(Baritone.settings().colorEspBlock.value);
                for (BlockPos p : blockHits) {
                    String name = ctx.world().getBlockState(p).getBlock().getName().getString();
                    Vec3 at = new Vec3(p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5);
                    pendingLabels.add(new Label(at, name + " (" + dist(at) + "m)", argb));
                }
            }
        }

        // ── Entities: players (label/tracer only), items, mobs ────────────────
        final Entity self = ctx.player();
        final boolean doPlayers = Baritone.settings().espPlayers.value;
        final boolean doItems = Baritone.settings().espItems.value;
        final boolean doMobs = Baritone.settings().espMobs.value;

        if (doPlayers || doItems || doMobs) {
            List<Entity> items = new ArrayList<>();
            List<Entity> mobs = new ArrayList<>();
            List<Entity> players = new ArrayList<>();

            for (Entity e : ctx.entities()) {
                if (e == null || e == self) {
                    continue;
                }
                if (doItems && e instanceof ItemEntity && inRange(e, self, Baritone.settings().espItemRange.value)) {
                    items.add(e);
                } else if (doMobs && e instanceof Enemy && inRange(e, self, Baritone.settings().espMobRange.value)) {
                    mobs.add(e);
                } else if (doPlayers && e instanceof Player && inRange(e, self, Baritone.settings().espPlayerRange.value)) {
                    players.add(e);
                }
            }

            drawEntityBoxes(stack, items, Baritone.settings().colorEspItem.value, width, depthIgnored, tracers, eye, pt);
            drawEntityBoxes(stack, mobs, Baritone.settings().colorEspMob.value, width, depthIgnored, tracers, eye, pt);
            // Players get a glow silhouette from the mixin, so only a tracer here.
            if (tracers && !players.isEmpty()) {
                BufferBuilder bb = IRenderer.startLines(Baritone.settings().colorEspPlayer.value, 1.0f);
                for (Entity e : players) {
                    IRenderer.emitLine(bb, stack, eye, centerOf(e, pt), width);
                }
                IRenderer.endLines(bb, depthIgnored);
            }

            if (labels) {
                for (Entity e : items) {
                    ItemStack st = ((ItemEntity) e).getItem();
                    String txt = st.getCount() + "x " + st.getHoverName().getString();
                    pendingLabels.add(entityLabel(e, txt, argb(Baritone.settings().colorEspItem.value), pt));
                }
                for (Entity e : mobs) {
                    pendingLabels.add(entityLabel(e, e.getName().getString(), argb(Baritone.settings().colorEspMob.value), pt));
                }
                for (Entity e : players) {
                    pendingLabels.add(entityLabel(e, e.getName().getString(), argb(Baritone.settings().colorEspPlayer.value), pt));
                }
            }
        }

        if (!pendingLabels.isEmpty()) {
            drawLabels(stack, pendingLabels);
        }
    }

    private void drawEntityBoxes(PoseStack stack, List<Entity> ents, Color color, float width,
                                 boolean depthIgnored, boolean tracers, Vec3 eye, float pt) {
        if (ents.isEmpty()) {
            return;
        }
        BufferBuilder bb = IRenderer.startLines(color, 1.0f);
        for (Entity e : ents) {
            IRenderer.emitAABB(bb, stack, interpBox(e, pt), width);
            if (tracers) {
                IRenderer.emitLine(bb, stack, eye, centerOf(e, pt), width);
            }
        }
        IRenderer.endLines(bb, depthIgnored);
    }

    // ── Geometry helpers ────────────────────────────────────────────────────

    private static boolean inRange(Entity e, Entity from, double range) {
        return e.distanceToSqr(from) <= range * range;
    }

    private static AABB interpBox(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xOld, e.getX());
        double y = Mth.lerp(pt, e.yOld, e.getY());
        double z = Mth.lerp(pt, e.zOld, e.getZ());
        double hw = e.getBbWidth() / 2.0;
        double h = e.getBbHeight();
        return new AABB(x - hw, y, z - hw, x + hw, y + h, z + hw);
    }

    private static Vec3 centerOf(Entity e, float pt) {
        double x = Mth.lerp(pt, e.xOld, e.getX());
        double y = Mth.lerp(pt, e.yOld, e.getY());
        double z = Mth.lerp(pt, e.zOld, e.getZ());
        return new Vec3(x, y + e.getBbHeight() * 0.5, z);
    }

    private Label entityLabel(Entity e, String text, int argb, float pt) {
        double x = Mth.lerp(pt, e.xOld, e.getX());
        double y = Mth.lerp(pt, e.yOld, e.getY());
        double z = Mth.lerp(pt, e.zOld, e.getZ());
        Vec3 at = new Vec3(x, y + e.getBbHeight() + 0.4, z);
        return new Label(at, text + " (" + dist(at) + "m)", argb);
    }

    private int dist(Vec3 at) {
        return (int) Math.round(ctx.player().position().distanceTo(at));
    }

    private static int argb(Color c) {
        return 0xFF000000 | (c.getRGB() & 0xFFFFFF);
    }

    // ── Label rendering (billboarded text, same path as vanilla nametags) ──────

    private record Label(Vec3 pos, String text, int argb) {}

    private void drawLabels(PoseStack stack, List<Label> labels) {
        // Billboarded, see-through text, drawn with the rest of the frame by BaritoneRenderBuffer.
        for (Label label : labels) {
            BaritoneRenderBuffer.label(label.pos(), label.text(), label.argb());
        }
    }
}
