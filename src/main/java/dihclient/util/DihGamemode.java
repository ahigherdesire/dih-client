package dihclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundChangeGameModePacket;
import net.minecraft.world.level.GameType;

public final class DihGamemode {
    private DihGamemode() {
    }

    public static DihFakeGamemode.Result real(GameType mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) {
            return DihFakeGamemode.Result.fail("Not connected.");
        }
        if (mode == null) {
            return DihFakeGamemode.Result.fail("Unknown game mode.");
        }

        boolean clearedFake = DihFakeGamemode.snapshot().fakeActive();
        if (clearedFake) DihFakeGamemode.reset();
        mc.player.connection.send(new ServerboundChangeGameModePacket(mode));
        return DihFakeGamemode.Result.ok("Requested real gamemode: " + mode.getName()
            + (clearedFake ? " (fake cleared)" : ""));
    }
}
