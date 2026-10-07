package dihclient.modules;

import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import net.minecraft.client.gui.screens.DeathScreen;

/**
 * Respawns as soon as you die. Ported from LiquidBounce's AutoRespawn (GPL-3.0, CCBlueX), with an optional command
 * sent once you are back, such as a kit. Waypoints already marks where you died.
 */
public final class AutoRespawnModule extends Module {
    private int deadTicks = -1;
    private int commandTicks = -1;

    public AutoRespawnModule() {
        super("auto-respawn", "AutoRespawn", ModuleCategory.PLAYER, "Respawns as soon as you die.");
        add(new IntSetting("delay", "Delay", 0, 0, 100, 1).unit("ticks").description("Wait this long on the death screen."));
        add(new StringSetting("command", "Command", "")
            .description("Sent after respawning, e.g. /kit. Leave empty for none."));
        add(new IntSetting("command-delay", "Command Delay", 20, 0, 200, 1).unit("ticks")
            .visibleWhen(() -> !text("command").isBlank()));
    }

    @Override
    public void onDisable() {
        deadTicks = -1;
        commandTicks = -1;
    }

    @Override
    public void tick() {
        if (MC.player == null) return;
        if (MC.gui.screen() instanceof DeathScreen) {
            deadTicks++;
            if (deadTicks < integer("delay")) return;
            deadTicks = -1;
            MC.player.respawn();
            MC.gui.setScreen(null);
            if (!text("command").isBlank()) commandTicks = 0;
            return;
        }
        deadTicks = -1;
        if (commandTicks >= 0 && MC.player.isAlive() && commandTicks++ >= integer("command-delay")) {
            commandTicks = -1;
            sendCommand(text("command").trim());
        }
    }
}
