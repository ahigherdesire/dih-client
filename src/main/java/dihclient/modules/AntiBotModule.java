package dihclient.modules;
import dihclient.api.module.*;

public final class AntiBotModule extends Module {
    public AntiBotModule() {
        super("antibot", "AntiBot", ModuleCategory.MISC, "Ignores fake/bot players.");
        add(new ChoiceSetting("mode", "Mode", "Conservative", "Conservative", "Aggressive")
            .description("Detection strictness preset").build());
    }

    @Override
    public void tick() {
        DihAntiBot.tick();
    }

    @Override
    public void onDisable() {
        DihAntiBot.reset();
    }

    @Override
    public void onGameJoin() {
        DihAntiBot.reset();
    }

    @Override
    public void onGameLeft() {
        DihAntiBot.reset();
    }
}
