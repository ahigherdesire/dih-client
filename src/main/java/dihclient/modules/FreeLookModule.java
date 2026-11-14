package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;

public final class FreeLookModule extends Module {
    private float cameraYaw;
    private float cameraPitch;
    private CameraType previousPerspective;

    public FreeLookModule() {
        super("free-look", "FreeLook", ModuleCategory.RENDER, "Look around without rotating the player.");
        add(new BoolSetting("hold", "Hold Key", true).group("General")
            .description("Only while the key is held; off makes the key toggle it.").build());
        add(new ChoiceSetting("perspective", "Perspective", "Back", "Back", "Front").group("General")
            .description("Camera behind you or facing you.").build());
        add(new BoolSetting("restore-perspective", "Restore View", true).group("General")
            .description("Goes back to your previous camera view when released.").build());
        add(new IntSetting("sensitivity", "Sensitivity", 100, 10, 300, 5).unit("%").group("Mouse")
            .description("How fast the camera turns.").build());
        add(new BoolSetting("invert-y", "Invert Y", false).group("Mouse")
            .description("Flips up and down for the free camera.").build());
        add(new BoolSetting("no-pitch-limit", "No Pitch Limit", true).group("Mouse")
            .description("Lets the camera look past straight up and down.").build());
    }

    @Override
    public boolean holdToActivate() {
        return bool("hold");
    }

    @Override
    public void onEnable() {
        if (MC.player != null) {
            cameraYaw = MC.player.getYRot();
            cameraPitch = MC.player.getXRot();
        }
        if (MC.options != null) {
            previousPerspective = MC.options.getCameraType();
            MC.options.setCameraType(desiredPerspective());
        }
    }

    @Override
    public void onDisable() {
        if (MC.options != null && previousPerspective != null && bool("restore-perspective")) {
            MC.options.setCameraType(previousPerspective);
        }
        previousPerspective = null;
    }

    @Override
    public void onGameLeft() {

        if (isEnabled()) setEnabled(false);
    }

    @Override
    public void preMovementTick() {

        if (MC.options != null && MC.options.getCameraType() != desiredPerspective()) {
            MC.options.setCameraType(desiredPerspective());
        }
    }

    private CameraType desiredPerspective() {
        return invertedView() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK;
    }

    private boolean invertedView() {
        return "Front".equals(choice("perspective"));
    }

    private static FreeLookModule activeInstance() {
        Module module = ModuleRegistry.get("free-look");
        return module instanceof FreeLookModule freeLook && freeLook.isEnabled() ? freeLook : null;
    }

    public static FreeLookModule lookingInstance() {
        return MC == null || MC.player == null ? null : activeInstance();
    }

    public boolean isInvertedView() {
        return invertedView();
    }

    public float cameraYaw() {
        return cameraYaw;
    }

    public float cameraPitch() {
        return cameraPitch;
    }

    public static boolean consumeMouseTurn(double deltaX, double deltaY) {
        FreeLookModule freeLook = activeInstance();
        if (freeLook == null || MC.player == null) return false;
        double speed = 0.15D * freeLook.integer("sensitivity") / 100.0D;
        freeLook.cameraYaw += (float) (deltaX * speed);
        freeLook.cameraPitch += (float) ((freeLook.bool("invert-y") ? -deltaY : deltaY) * speed);
        if (!freeLook.bool("no-pitch-limit")) {
            freeLook.cameraPitch = Mth.clamp(freeLook.cameraPitch, -90.0F, 90.0F);
        }
        return true;
    }
}
