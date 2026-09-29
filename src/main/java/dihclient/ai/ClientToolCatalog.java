package dihclient.ai;

import baritone.ai.LiveCommands;
import baritone.ai.catalog.CommandAdapters;
import baritone.ai.director.JobStatus;
import baritone.ai.director.LiveDirectorHost;
import baritone.ai.tool.ToolRegistry;
import baritone.api.BaritoneAPI;
import baritone.api.command.ICommand;
import dihclient.commands.Command;
import dihclient.commands.DihCommands;
import dihclient.util.macro.MacroExecutor;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Adds the client's side of the tool catalog: its own tools, and one adapter per {@code #} and {@code .} command.
 * Installed once from the (non-lite) command setup.
 */
public final class ClientToolCatalog {

    private static boolean installed;

    private ClientToolCatalog() {
    }

    public static synchronized void install() {
        if (installed) return;
        installed = true;
        LiveCommands.setClientRunner(DotCommandRunner::run);
        // Jobs the client tools start, so a director run can wait for them.
        LiveDirectorHost.registerJob("macro", new LiveDirectorHost.ClientJob(
                () -> MacroExecutor.isVisibleRunning() ? JobStatus.running() : JobStatus.done("the macro finished"),
                () -> Minecraft.getInstance().execute(MacroExecutor::stop)));
        LiveDirectorHost.registerJob("connect", new LiveDirectorHost.ClientJob(
                () -> Minecraft.getInstance().level != null ? JobStatus.done("joined")
                        : Minecraft.getInstance().gui.screen() instanceof net.minecraft.client.gui.screens.DisconnectedScreen
                        ? JobStatus.failed("couldn't join the server") : JobStatus.running(),
                () -> { }));
        ToolRegistry.contribute(registry -> {
            ClientTools.register(registry);
            CommandAdapters.registerBaritone(registry, baritoneCommands());
            CommandAdapters.registerClient(registry, clientCommands());
        });
    }

    static List<CommandAdapters.Info> baritoneCommands() {
        List<CommandAdapters.Info> out = new ArrayList<>();
        try {
            for (ICommand command : BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().getRegistry().descendingStream().toList()) {
                out.add(new CommandAdapters.Info(command.getNames().get(0), command.getNames(), command.getShortDesc()));
            }
        } catch (RuntimeException | LinkageError e) {
            // Baritone isn't up (or, in unit tests, can't load): the adapters for # commands are simply missing, run_command still works.
        }
        return out;
    }

    static List<CommandAdapters.Info> clientCommands() {
        List<CommandAdapters.Info> out = new ArrayList<>();
        for (Command command : DihCommands.all()) {
            List<String> names = new ArrayList<>();
            names.add(command.name());
            names.addAll(Arrays.asList(command.aliases()));
            out.add(new CommandAdapters.Info(command.name(), names, command.description()));
        }
        return out;
    }
}
