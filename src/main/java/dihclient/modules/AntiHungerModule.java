package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.mixin.accessor.DihMovePlayerPacketAccessor;
import dihclient.mixin.accessor.DihMultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class AntiHungerModule extends Module {
    private static final Minecraft MC = Minecraft.getInstance();

    public AntiHungerModule() {
        super("anti-hunger", "AntiHunger", ModuleCategory.MISC,
            "Prevents hunger from decreasing. Will flag anticheats.");
        add(new BoolSetting("no-sprint", "NoSprint", true)
            .group("Sprint")
            .description("Hide sprinting from the server so it doesn't drain hunger."));
        add(new BoolSetting("sprint-while-swimming", "While Swimming", false)
            .group("Sprint")
            .description("Also hide sprinting while you swim.")
            .visibleWhen(() -> Boolean.parseBoolean(value("no-sprint"))));
        add(new BoolSetting("keep-floating", "KeepFloating", true)
            .group("Ground")
            .description("Tell the server you're never on the ground, which stops some hunger drain."));
    }

    boolean noSprintActive() {
        return bool("no-sprint") && (!MC.player.isSwimming() || bool("sprint-while-swimming"));
    }

    public static boolean noSprintRequested() {
        Module module = ModuleRegistry.get("anti-hunger");
        if (module instanceof AntiHungerModule antiHunger && antiHunger.isEnabled()
            && MC.player != null && antiHunger.noSprintActive()) {
            return true;
        }
        Module flight = ModuleRegistry.get("flight");
        if (flight instanceof BuiltinModules.FlightModule flightModule
            && flightModule.isEnabled() && flightModule.antiHungerToggle()) {
            return true;
        }
        return false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (MC.player == null || MC.getConnection() == null) return false;
        if (packet instanceof ServerboundMovePlayerPacket move) {

            if (!bool("keep-floating") || MC.player.isPassenger() || isDestroyingBlock()) return false;
            if (MC.player.isInWater() || MC.player.isSwimming() || MC.player.isUnderWater()) return false;
            if (move.isOnGround() && MC.player.fallDistance <= 0.0F) {
                ((DihMovePlayerPacketAccessor) move).dih$setOnGround(false);
            }
        }
        return false;
    }

    private static boolean isDestroyingBlock() {
        return MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor
            && accessor.dih$isDestroying();
    }
}
