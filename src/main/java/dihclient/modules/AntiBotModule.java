package dihclient.modules;
import dihclient.api.module.*;

public final class AntiBotModule extends Module {
    public AntiBotModule() {
        super("antibot", "AntiBot", ModuleCategory.MISC, "Ignores fake/bot players.");
        add(new ChoiceSetting("mode", "Mode", "Conservative", "Conservative", "Aggressive")
            .group("General")
            .description("Aggressive adds the flying-on-ground check, which can catch laggy real players.")
            .build());

        add(new BoolSetting("check-duplicate-name", "Duplicate Names", true)
            .group("Checks")
            .description("Treat two players sharing a name with different UUIDs as bots.")
            .build());
        add(new BoolSetting("check-invalid-name", "Invalid Names", true)
            .group("Checks")
            .description("Treat players whose name isn't a valid Minecraft username as bots.")
            .build());
        add(new BoolSetting("check-impossible-pitch", "Impossible Pitch", true)
            .group("Checks")
            .description("Treat players looking past straight up or down as bots.")
            .build());
        add(new IntSetting("pitch-threshold", "Pitch Limit", 90, 60, 90, 1)
            .group("Checks")
            .description("Pitch past this many degrees counts as impossible.")
            .visibleWhen(() -> bool("check-impossible-pitch"))
            .build());
        add(new BoolSetting("check-missing-profile", "No Tab Entry", true)
            .group("Checks")
            .description("Treat players with no matching tab-list profile as bots.")
            .build());
        add(new BoolSetting("check-bot-spots", "Bot Spots", true)
            .group("Checks")
            .description("Treat still players standing exactly where a bot was as bots.")
            .build());
        add(new BoolSetting("check-ground-flying", "Flying On Ground", true)
            .group("Checks")
            .description("Treat players marked on-ground while their height keeps changing as bots.")
            .visibleWhen(() -> "Aggressive".equals(choice("mode")))
            .build());
        add(new IntSetting("ground-violations", "Ground Threshold", 10, 1, 40, 1)
            .group("Checks")
            .description("Flying-on-ground ticks before a player counts as a bot.")
            .visibleWhen(() -> "Aggressive".equals(choice("mode")) && bool("check-ground-flying"))
            .build());
    }

    boolean aggressive() {
        return "Aggressive".equals(choice("mode"));
    }

    boolean check(String id) {
        return bool(id);
    }

    int pitchThreshold() {
        return integer("pitch-threshold");
    }

    int groundViolationThreshold() {
        return Math.max(1, integer("ground-violations"));
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
