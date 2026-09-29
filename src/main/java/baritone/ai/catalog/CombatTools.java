package baritone.ai.catalog;

import baritone.Baritone;
import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import baritone.guardian.GuardianLog;
import baritone.guardian.GuardianProcess;

import java.util.List;

/** The Guardian: the always-on safety layer that fights, flees, shelters and eats. */
final class CombatTools {

    private CombatTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("set_guardian", ToolCategory.COMBAT)
                .gameThread()
                .summary("Turn the Guardian on or off, or change when it flees.")
                .description("Change the Guardian, which pre-empts any job to deal with mobs, lava, falls and low health. "
                        + "Only the parameters given change.")
                .schema(ToolSchema.builder()
                        .bool("enabled", "Guardian on or off.")
                        .integer("flee_health", "Fight above this health (half-hearts), retreat at or below.").range(1, 19)
                        .bool("stop_for_players", "Hand control back when a stranger comes near.")
                        .build())
                .handler((ctx, args) -> {
                    var settings = Baritone.settings();
                    if (args.has("enabled")) settings.guardianEnabled.value = args.bool("enabled");
                    if (args.has("flee_health")) settings.guardianFleeHealth.value = args.integer("flee_health");
                    if (args.has("stop_for_players")) settings.guardianStopForPlayers.value = args.bool("stop_for_players");
                    return describe(settings.guardianEnabled.value, settings.guardianFleeHealth.value,
                            settings.guardianStopForPlayers.value);
                })
                .build());

        registry.register(AiTool.builder("guardian_status", ToolCategory.COMBAT)
                .gameThread()
                .summary("What the Guardian is doing and its recent decisions.")
                .schema(ToolSchema.EMPTY)
                .handler((ctx, args) -> {
                    GuardianProcess guardian = ctx.guardian();
                    if (guardian == null) return ToolResult.failed("Not in a game.");
                    List<String> recent = guardian.log().recent().stream().map(GuardianLog.Event::text).toList();
                    List<String> last = recent.subList(Math.max(0, recent.size() - 8), recent.size());
                    return ToolResult.ok(guardian.status() + (last.isEmpty() ? "" : ". Recently: " + String.join(" | ", last)))
                            .fact("status", guardian.status()).fact("recent", last);
                })
                .build());
    }

    static ToolResult describe(boolean enabled, int fleeHealth, boolean stopForPlayers) {
        return ToolResult.ok("Guardian " + (enabled ? "on" : "off") + ", fleeing at " + fleeHealth + " health or below"
                        + (stopForPlayers ? ", handing back when a stranger comes near." : "."))
                .fact("enabled", enabled).fact("flee_health", fleeHealth).fact("stop_for_players", stopForPlayers);
    }
}
