package dihclient.modules;

import dihclient.api.module.ActionSetting;
import dihclient.api.module.BoolSetting;
import dihclient.api.module.ColorSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.gui.screen.DihWaypointsScreen;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihWaypoints;

public final class WaypointsModule extends Module {
    private final BoolSetting onDeath;
    private final BoolSetting trackDeaths;
    private final IntSetting deathMemory;
    private final ColorSetting deathColor;
    private final ColorSetting defaultColor;
    private final IntSetting dotSize;

    private boolean wasAlive = true;

    public WaypointsModule() {
        super("waypoints", "Waypoints", ModuleCategory.RENDER, "Save and render named destinations.");
        add(new ActionSetting("manage-waypoints", "Manage Waypoints", this::openManager)
            .availableOffline().buttonLabel("Open").description("Open the waypoint manager.").group("General").build());
        defaultColor = add(new ColorSetting("default-color", "Default Color", 0xFF3BD7FF)
            .description("Color for new waypoints.").group("General").build());

        onDeath = add(new BoolSetting("on-death", "Waypoint On Death", true)
            .description("Save a waypoint where you die.").group("Deaths").build());
        trackDeaths = add(new BoolSetting("track-deaths", "Track Deaths", true)
            .description("Save deaths even when off.").group("Deaths").build());
        deathMemory = add(new IntSetting("death-memory", "Death Memory", 10, 1, 50, 1)
            .description("Max deaths to keep.").group("Deaths").build());
        deathColor = add(new ColorSetting("death-color", "Death Color", 0xFFFF3B3B)
            .description("Color for death waypoints.").group("Deaths").build());
        add(new BoolSetting("death-chat", "Death Message", true)
            .description("Say in chat where the death waypoint was saved.").group("Deaths").build());
        add(new BoolSetting("show-deaths", "Show Deaths", true)
            .description("Draw death waypoints in the world.").group("Deaths").build());

        add(new BoolSetting("show-dot", "Dot", true)
            .description("Draw the dot at each waypoint.").group("Render").build());
        dotSize = add(new IntSetting("dot-size", "Dot Size", 3, 1, 10, 1)
            .description("Waypoint dot size.").group("Render").visibleWhen(() -> bool("show-dot")).build());
        add(new BoolSetting("show-names", "Names", true)
            .description("Show each waypoint's name.").group("Render").build());
        add(new BoolSetting("show-distance", "Show Distance", true)
            .description("Show your distance below waypoints.").group("Render").build());
        add(new DoubleSetting("text-scale", "Text Scale", 1.0D, 0.5D, 3.0D, 0.1D)
            .description("Size of the name and distance.").group("Render").build());
        add(new BoolSetting("text-outline", "Text Outline", true)
            .description("Dark outline around the text.").group("Render").build());
        add(new DoubleSetting("hide-distance", "Hide Within", 2.5D, 0.0D, 5.5D, 0.5D).unit("blocks")
            .description("Hides a waypoint once you are this close.").group("Render").build());
        add(new IntSetting("max-distance", "Max Distance", 0, 0, 100_000, 100).unit("blocks")
            .description("Hides waypoints farther than this; 0 is no limit.").group("Render").numericTextField().build());
    }

    public int defaultColor() {
        return defaultColor.get();
    }

    @Override
    public boolean ticksWhenDisabled() {
        return true;
    }

    @Override
    public void onGameJoin() {
        if (MC == null) return;
        DihWaypoints.get().pruneDeaths(DihWaypoints.scopeKey(MC), deathMemory.get());
    }

    @Override
    public void tick() {
        if (MC == null || MC.player == null) {
            wasAlive = true;
            return;
        }
        boolean alive = MC.player.getHealth() > 0.0F;
        if (wasAlive && !alive) onDeath();
        wasAlive = alive;
    }

    private void onDeath() {
        boolean enabled = isEnabled();

        if (!(enabled ? onDeath.get() : trackDeaths.get())) return;
        int x = (int) Math.floor(MC.player.getX());
        int y = (int) Math.floor(MC.player.getY());
        int z = (int) Math.floor(MC.player.getZ());
        DihWaypoints.Waypoint added = DihWaypoints.get().addDeath(
            DihWaypoints.scopeKey(MC), x, y, z, deathColor.get(), System.currentTimeMillis(), deathMemory.get());
        if (added != null && enabled && bool("death-chat")) {
            DihClientMessaging.sendPrefixed("§aDeath waypoint saved: §f" + added.name()
                + " §7(" + x + " " + y + " " + z + ")");
        }
    }

    private void openManager() {
        if (MC == null || MC.gui == null) return;
        MC.gui.setScreen(new DihWaypointsScreen(MC.gui.screen()));
    }
}
