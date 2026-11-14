package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Respawns as soon as you die. Ported from LiquidBounce's AutoRespawn (GPL-3.0, CCBlueX), with an optional command
 * sent once you are back, such as a kit. Waypoints already marks where you died.
 */
public final class AutoRespawnModule extends Module {
    private int deadTicks = -1;
    private int commandTicks = -1;
    private int restoreSlot = -1;
    private boolean diedSeen;

    public AutoRespawnModule() {
        super("auto-respawn", "AutoRespawn", ModuleCategory.PLAYER, "Respawns as soon as you die.");
        add(new IntSetting("delay", "Delay", 0, 0, 100, 1).unit("ticks")
            .group("General")
            .description("Wait this long on the death screen."));
        add(new BoolSetting("skip-hardcore", "Skip Hardcore", false)
            .group("General")
            .description("Leaves the death screen alone in hardcore worlds"));
        add(new BoolSetting("auto-disable", "Auto Disable", false)
            .group("General")
            .description("Turns itself off after one respawn"));

        add(new BoolSetting("print-coords", "Print Coords", false)
            .group("After Respawn")
            .description("Says in chat where you died"));
        add(new BoolSetting("restore-slot", "Restore Slot", false)
            .group("After Respawn")
            .description("Selects the hotbar slot you held when you died"));
        add(new StringSetting("command", "Command", "")
            .group("After Respawn")
            .description("Sent after respawning, e.g. /kit. Leave empty for none."));
        add(new IntSetting("command-delay", "Command Delay", 20, 0, 200, 1).unit("ticks")
            .group("After Respawn")
            .description("Waits this long after respawning before sending the command")
            .visibleWhen(() -> !text("command").isBlank()));
    }

    @Override
    public void onDisable() {
        deadTicks = -1;
        commandTicks = -1;
        restoreSlot = -1;
        diedSeen = false;
    }

    @Override
    public void tick() {
        if (MC.player == null) return;
        if (MC.gui.screen() instanceof DeathScreen) {
            if (bool("skip-hardcore") && MC.level != null && MC.level.getLevelData().isHardcore()) return;
            if (!diedSeen) {
                diedSeen = true;
                restoreSlot = MC.player.getInventory().getSelectedSlot();
                if (bool("print-coords")) {
                    DihClientMessaging.sendPrefixed("Died at " + MC.player.getBlockX() + ", " + MC.player.getBlockY()
                        + ", " + MC.player.getBlockZ() + ".");
                }
            }
            deadTicks++;
            if (deadTicks < integer("delay")) return;
            deadTicks = -1;
            diedSeen = false;
            MC.player.respawn();
            MC.gui.setScreen(null);
            if (!text("command").isBlank()) commandTicks = 0;
            if (!bool("restore-slot")) restoreSlot = -1;
            finishIfDone();
            return;
        }
        deadTicks = -1;
        diedSeen = false;
        if (restoreSlot >= 0 && MC.player.isAlive() && MC.player.tickCount > 1) {
            MC.player.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
            finishIfDone();
        }
        if (commandTicks >= 0 && MC.player.isAlive() && commandTicks++ >= integer("command-delay")) {
            commandTicks = -1;
            sendCommand(text("command").trim());
            finishIfDone();
        }
    }

    private void finishIfDone() {
        if (bool("auto-disable") && commandTicks < 0 && restoreSlot < 0) setEnabled(false);
    }
}
