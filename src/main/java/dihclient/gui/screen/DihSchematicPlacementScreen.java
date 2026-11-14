package dihclient.gui.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dihclient.gui.vanillaui.UiBounds;
import dihclient.gui.vanillaui.UiRenderer;
import dihclient.gui.vanillaui.components.Button;
import dihclient.gui.vanillaui.components.CompactTheme;
import dihclient.gui.vanillaui.components.UiText;
import dihclient.gui.vanillaui.components.UiTone;
import dihclient.schematic.PlacementControls;
import dihclient.schematic.SchematicActions;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.SchematicTransform;
import dihclient.schematic.Schematics;
import dihclient.util.DihTheme;
import dihclient.util.DihUiScale;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Moves one placement in the world with the mouse, with the world still visible behind it.
 * <p>
 * Ported from Autism's placement screen ({@code AiPlacementScreen} with {@code AiSchematicPlacement}'s movement):
 * right-click or drag puts the placement where the mouse points (on the block face it hits, or level with the
 * placement when it hits nothing), the wheel turns it, Shift + wheel pushes it nearer or farther along the ray,
 * middle-drag looks around, left-click keeps the placement and Escape puts it back. The arrow keys, Page Up/Down,
 * R, M and G move, turn, mirror and ground it from the keyboard.
 */
public final class DihSchematicPlacementScreen extends DihScreen {
    private static final CompactTheme THEME = new CompactTheme();
    private static final int PAD = 4;
    private static final int GAP = 2;
    private static final int CONTROL = 16;
    private static final int LINE = 11;

    private final SchematicPlacement placement;
    private final Screen parent;
    private final BlockPos startOrigin;
    private final SchematicTransform startTransform;

    private boolean dragging;
    private boolean looking;
    private boolean shift;
    private double scrollRemainder;
    /** The ray the placement was last moved along, and how far along it; null until it is moved with the mouse. */
    private Vec3 direction;
    private double depth = 6.0;
    private UiBounds panel = UiBounds.of(0, 0, 0, 0);

    public DihSchematicPlacementScreen(SchematicPlacement placement, Screen parent) {
        super(Component.literal("Schematic placement"));
        this.placement = placement;
        this.parent = parent;
        this.startOrigin = placement.origin();
        this.startTransform = placement.transform();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void init() {
        clearWidgets();
        int w = panelWidth();
        int h = LINE * 3 + 3 * (CONTROL + GAP) + 2 * PAD;
        int x = PAD;
        int y = Math.max(PAD, screenHeight() - h - PAD);
        this.panel = UiBounds.of(x, y, w, h);
        int inner = w - PAD * 2;
        int left = x + PAD;
        int row = y + PAD + LINE * 2;
        int cell = (inner - GAP * 5) / 6;
        String[] labels = {"West", "East", "North", "South", "Up", "Down"};
        int[][] offsets = {{-1, 0, 0}, {1, 0, 0}, {0, 0, -1}, {0, 0, 1}, {0, 1, 0}, {0, -1, 0}};
        for (int i = 0; i < labels.length; i++) {
            int[] o = offsets[i];
            addButton(left + i * (cell + GAP), row, cell, labels[i], Button.Tone.SECONDARY, b -> nudge(o[0], o[1], o[2]));
        }
        row += CONTROL + GAP;
        int third = (inner - GAP * 2) / 3;
        addButton(left, row, third, "Rotate 90", Button.Tone.NORMAL, b -> rotate(1));
        addButton(left + third + GAP, row, third, "Mirror", Button.Tone.NORMAL, b -> mirror());
        addButton(left + 2 * (third + GAP), row, inner - 2 * (third + GAP), "Ground", Button.Tone.NORMAL, b -> ground());
        row += CONTROL + GAP;
        int half = (inner - GAP) / 2;
        addButton(left, row, half, "Cancel", Button.Tone.SECONDARY, b -> close(false));
        addButton(left + half + GAP, row, inner - half - GAP, "Use placement", Button.Tone.SUCCESS, b -> close(true));
    }

    private int panelWidth() {
        return Math.min(320, screenWidth() - 2 * PAD);
    }

    private void addButton(int x, int y, int w, String label, Button.Tone tone,
                           net.minecraft.client.gui.components.Button.OnPress press) {
        addRenderableWidget(new DihStyledButton(x, y, Math.max(1, w), CONTROL, Component.literal(label), tone, press));
    }

    @Override
    public void tick() {
        if (this.minecraft == null || this.minecraft.level == null || this.minecraft.player == null
            || !Schematics.get().placements().contains(this.placement)) {
            close(false);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int virtualMouseX = DihUiScale.toVirtualInt(mouseX);
        int virtualMouseY = DihUiScale.toVirtualInt(mouseY);
        DihUiScale.pushOverlayScale(graphics);
        try {
            Identifier font = THEME.fontFor(UiTone.BODY);
            int text = DihTheme.recolor(DihScreenPalette.TEXT, DihTheme.Channel.TEXT);
            int muted = DihTheme.recolor(DihScreenPalette.MUTED, DihTheme.Channel.TEXT);
            int fill = DihTheme.recolor(DihScreenPalette.PANEL_BG, DihTheme.Channel.BUTTON);
            int border = DihTheme.recolor(DihScreenPalette.BORDER, DihTheme.Channel.OUTLINE);

            String controls = "Right-click or drag: move   Wheel: turn   Shift + wheel: nearer / farther";
            String detail = "Left-click: keep   Middle-drag: look   Esc: put back   Arrows, PgUp/PgDn, R, M, G";
            int bannerW = Math.min(screenWidth() - 16, Math.max(UiText.width(this.font, controls, font, text),
                UiText.width(this.font, detail, font, text)) + 18);
            int bannerX = (screenWidth() - bannerW) / 2;
            UiRenderer.frame(graphics, UiBounds.of(bannerX, 4, bannerW, LINE * 2 + 8), fill, border);
            UiText.draw(graphics, this.font, controls, font, text, bannerX + 9, 8, false);
            UiText.draw(graphics, this.font, detail, font, muted, bannerX + 9, 8 + LINE, false);

            UiRenderer.frame(graphics, this.panel, fill, border);
            int x = this.panel.x() + PAD;
            int y = this.panel.y() + PAD;
            int inner = this.panel.width() - PAD * 2;
            BlockPos o = this.placement.origin();
            SchematicTransform t = this.placement.transform();
            String turn = t.rotation() + " degrees" + (t.mirror() == SchematicTransform.Mirroring.NONE ? "" : ", mirror " + t.mirror().id());
            UiText.drawEllipsized(graphics, this.font, this.placement.name(), font, text, x, y, inner, false);
            UiText.drawEllipsized(graphics, this.font, "Origin " + o.getX() + " " + o.getY() + " " + o.getZ() + "  /  " + turn,
                font, muted, x, y + LINE, inner, false);
            int status = this.placement.error().isEmpty() ? muted : DihTheme.recolor(DihScreenPalette.ERROR, DihTheme.Channel.TEXT);
            UiText.drawEllipsized(graphics, this.font, SchematicActions.describe(this.placement), font, status,
                x, this.panel.y() + this.panel.height() - PAD - LINE + 2, inner, false);
            super.extractRenderState(graphics, virtualMouseX, virtualMouseY, delta);
        } finally {
            DihUiScale.popOverlayScale(graphics);
        }
    }

    // ---- mouse ----

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        MouseButtonEvent virtual = virtualEvent(event);
        if (super.mouseClicked(virtual, doubled) || this.panel.contains((int) virtual.x(), (int) virtual.y())) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.dragging = true;
            moveToRay(event.x(), event.y());
        } else if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
            this.looking = true;
        } else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            close(true);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging && event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            moveToRay(event.x(), event.y());
            return true;
        }
        if (this.looking && this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.setYRot(this.minecraft.player.getYRot() + (float) dx * 0.25F);
            this.minecraft.player.setXRot(Mth.clamp(this.minecraft.player.getXRot() + (float) dy * 0.25F, -90.0F, 90.0F));
            return true;
        }
        return super.mouseDragged(virtualEvent(event), dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.dragging = false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
            this.looking = false;
        }
        return super.mouseReleased(virtualEvent(event));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0 || !Double.isFinite(scrollY)) {
            return true;
        }
        if (this.shift) {
            pushAlongRay(scrollY, mouseX, mouseY);
        } else {
            this.scrollRemainder += scrollY;
            int steps = (int) this.scrollRemainder;
            if (steps != 0) {
                this.scrollRemainder -= steps;
                rotate(steps);
            }
        }
        return true;
    }

    // ---- keys ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT) {
            this.shift = true;
            return true;
        }
        Direction facing = this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getDirection() : Direction.NORTH;
        switch (key) {
            case InputConstants.KEY_ESCAPE -> close(false);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> close(true);
            case InputConstants.KEY_UP -> nudge(facing);
            case InputConstants.KEY_DOWN -> nudge(facing.getOpposite());
            case InputConstants.KEY_LEFT -> nudge(facing.getCounterClockWise());
            case InputConstants.KEY_RIGHT -> nudge(facing.getClockWise());
            case InputConstants.KEY_PAGEUP -> nudge(0, 1, 0);
            case InputConstants.KEY_PAGEDOWN -> nudge(0, -1, 0);
            case InputConstants.KEY_R -> rotate(this.shift ? -1 : 1);
            case InputConstants.KEY_M -> mirror();
            case InputConstants.KEY_G -> ground();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == InputConstants.KEY_LSHIFT || event.key() == InputConstants.KEY_RSHIFT) {
            this.shift = false;
        }
        return super.keyReleased(event);
    }

    // ---- moving ----

    private void nudge(Direction direction) {
        nudge(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private void nudge(int dx, int dy, int dz) {
        this.placement.nudge(dx, dy, dz);
        this.direction = null;
    }

    private void rotate(int steps) {
        this.placement.rotate(steps);
    }

    private void mirror() {
        this.placement.cycleMirror();
    }

    /**
     * Drops the placement onto the ground at its column, if that column is loaded.
     */
    private void ground() {
        if (this.minecraft != null && SchematicActions.ground(this.minecraft, this.placement)) {
            this.direction = null;
        }
    }

    /**
     * @return the direction from the camera through a point on the screen
     */
    private Vec3 rayThrough(double screenX, double screenY) {
        if (this.width <= 0 || this.height <= 0 || this.minecraft == null) {
            return Vec3.ZERO;
        }
        Camera camera = this.minecraft.gameRenderer.mainCamera();
        float f = (float) Math.tan(Math.toRadians(camera.getFov()) / 2.0);
        Vector3f ray = new Vector3f(
            (float) ((2.0 * screenX / this.width - 1.0) * this.width / this.height) * f,
            (float) (1.0 - 2.0 * screenY / this.height) * f,
            -1.0F).rotate(camera.rotation()).normalize();
        return new Vec3(ray);
    }

    private void moveToRay(double screenX, double screenY) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        Vec3 ray = rayThrough(screenX, screenY);
        if (ray.lengthSqr() < 0.5) {
            return;
        }
        Vec3 eye = this.minecraft.gameRenderer.mainCamera().position();
        double reach = (this.minecraft.options.getEffectiveRenderDistance() + 2) * 16.0;
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(ray.scale(reach)), ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, this.minecraft.player));
        if (hit.getType() == HitResult.Type.BLOCK && level.isLoaded(hit.getBlockPos())) {
            BlockPos target = hit.getBlockPos().relative(hit.getDirection());
            this.placement.moveTo(target);
            this.depth = Vec3.atCenterOf(target).distanceTo(eye);
        } else {
            this.depth = PlacementControls.distanceToHeight(eye, ray, this.placement.origin().getY() + 0.5, this.depth);
            moveTo(level, PlacementControls.along(eye, ray, this.depth));
        }
        this.direction = ray;
    }

    private void pushAlongRay(double scroll, double screenX, double screenY) {
        ClientLevel level = level();
        if (level == null) {
            return;
        }
        Vec3 eye = this.minecraft.gameRenderer.mainCamera().position();
        if (this.direction == null) {
            Vec3 fromEye = Vec3.atCenterOf(this.placement.origin()).subtract(eye);
            this.direction = fromEye.lengthSqr() < 0.01 ? rayThrough(screenX, screenY) : fromEye.normalize();
            this.depth = Math.max(PlacementControls.MIN_DEPTH, fromEye.length());
        }
        if (this.direction.lengthSqr() < 0.5) {
            return;
        }
        this.depth = PlacementControls.depth(this.depth, scroll);
        moveTo(level, PlacementControls.along(eye, this.direction, this.depth));
    }

    /**
     * Moves the placement's origin to a point, kept inside the world border and the build height.
     */
    private void moveTo(ClientLevel level, Vec3 point) {
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)) {
            return;
        }
        WorldBorder border = level.getWorldBorder();
        this.placement.moveTo(BlockPos.containing(
            Mth.clamp(point.x, border.getMinX(), border.getMaxX() - 1.0),
            Mth.clamp(point.y, level.getMinY(), level.getMaxY() - 1),
            Mth.clamp(point.z, border.getMinZ(), border.getMaxZ() - 1.0)));
    }

    private ClientLevel level() {
        return this.minecraft == null ? null : this.minecraft.level;
    }

    // ---- closing ----

    @Override
    public void onClose() {
        close(false);
    }

    private void close(boolean keep) {
        if (!keep) {
            this.placement.moveTo(this.startOrigin);
            this.placement.setTransform(this.startTransform);
        }
        Schematics.get().changed();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
