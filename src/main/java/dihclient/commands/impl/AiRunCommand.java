package dihclient.commands.impl;

import baritone.ai.AiBrain;
import baritone.ai.director.Director;
import baritone.ai.director.DirectorState;
import baritone.ai.director.RunStatus;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dihclient.ai.ToolRunner;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihAiPanelOverlay;
import dihclient.gui.screen.DihAiSetupScreen;
import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/**
 * {@code .ai start <objective>} and its controls: the AI plans once and tools do the work. Without a key it runs in
 * basic mode. The Baritone {@code #ai} command (chat brain, keys, trust) stays as it was.
 */
public class AiRunCommand extends Command {

    public AiRunCommand() {
        super("ai", "Give the AI an objective: .ai start get full iron armor. Also stop, pause, resume, status, why.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            usage();
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("start")
                .executes(ctx -> {
                    DihClientMessaging.sendPrefixed("§eUsage: " + DihCommands.effectivePrefix() + "ai start <objective>");
                    return SUCCESS;
                })
                .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("objective", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            start(StringArgumentType.getString(ctx, "objective"));
                            return SUCCESS;
                        })));
        root.then(control("stop", director -> director.stop("You stopped it.")));
        root.then(control("pause", director -> director.pause("You paused it. .ai resume carries on.")));
        root.then(control("resume", Director::resume));
        root.then(control("confirm", Director::confirm));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("status").executes(ctx -> {
            status();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("why").executes(ctx -> {
            Director director = director();
            if (director == null) return SUCCESS;
            String reason = director.state().lastReason();
            DihClientMessaging.sendPrefixed("§7" + (reason.isBlank() ? "No reason recorded yet." : reason));
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("setup").executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.gui.setScreen(new DihAiSetupScreen(mc.gui.screen())));
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("panel").executes(ctx -> {
            Minecraft.getInstance().execute(DihAiPanelOverlay::toggle);
            return SUCCESS;
        }));
    }

    private static void usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§e" + p + "ai start <objective>§7, then §e" + p + "ai status§7, §estop§7, §epause§7, "
                + "§eresume§7, §ewhy§7, §econfirm§7. §e" + p + "ai setup§7 for the model, §e" + p + "ai panel§7 for progress.");
    }

    private static LiteralArgumentBuilder<DihCommandSource> control(String name, Consumer<Director> action) {
        return LiteralArgumentBuilder.<DihCommandSource>literal(name).executes(ctx -> {
            Director director = director();
            if (director == null) return SUCCESS;
            if (!director.state().status().isActive()) {
                DihClientMessaging.sendPrefixed("§7No run is going. " + DihCommands.effectivePrefix() + "ai start <objective>");
                return SUCCESS;
            }
            action.accept(director);
            return SUCCESS;
        });
    }

    private static void start(String objective) {
        AiBrain brain = ToolRunner.brain();
        if (brain == null) {
            DihClientMessaging.sendPrefixed("§cJoin a world first.");
            return;
        }
        String error = brain.startRun(objective);
        if (error != null) DihClientMessaging.sendPrefixed("§c" + error);
        else Minecraft.getInstance().execute(DihAiPanelOverlay::open);
    }

    /** The current or last run, or null (after saying so). */
    private static Director director() {
        AiBrain brain = ToolRunner.brain();
        Director director = brain == null ? null : brain.director();
        if (director == null) {
            DihClientMessaging.sendPrefixed("§7No run yet. " + DihCommands.effectivePrefix() + "ai start <objective>");
        }
        return director;
    }

    private static void status() {
        Director director = director();
        if (director == null) return;
        DirectorState state = director.state();
        DihClientMessaging.sendPrefixed("§f" + state.objective() + " §7(" + (state.basic() ? "basic mode" : "smart") + ", "
                + state.status().id() + ", " + minutes(System.currentTimeMillis() - state.startedMillis()) + ")");
        for (int i = 0; i < state.steps().size(); i++) {
            DirectorState.StepView step = state.steps().get(i);
            String mark = switch (step.status()) {
                case DONE -> "§a✔";
                case RUNNING -> "§b▶";
                case FAILED -> "§c✘";
                case PENDING -> i == state.current() && state.status() != RunStatus.PAUSED ? "§b▶" : "§8·";
            };
            DihClientMessaging.sendPrefixed(mark + " §7" + (i + 1) + ". §f" + step.tool() + " §8" + step.args()
                    + (step.note().isBlank() ? "" : " §7- " + step.note()));
        }
        if (!state.lastReason().isBlank()) DihClientMessaging.sendPrefixed("§7" + state.lastReason());
        if (!state.basic()) {
            DihClientMessaging.sendPrefixed("§8Model calls: " + state.modelCalls() + ", tokens: " + state.promptTokens() + " in, "
                    + state.completionTokens() + " out.");
        }
    }

    private static String minutes(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return seconds < 120 ? seconds + " s" : seconds / 60 + " min";
    }
}
