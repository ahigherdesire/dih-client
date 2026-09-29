package dihclient.ai;

import baritone.ai.tool.CommandRunner;
import dihclient.commands.DihCommands;
import dihclient.util.DihClientMessaging;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs DIH {@code .} commands for tools on the game thread, and reports what they printed. */
public final class DotCommandRunner {

    /** How long to keep listening after the command, for messages it sends a moment later. */
    static final long SETTLE_MILLIS = 400L;
    static final int MAX_CHARS = 600;

    private DotCommandRunner() {
    }

    public static CommandRunner.Outcome run(String command) {
        String body = command == null ? "" : command.trim();
        while (body.startsWith(".")) body = body.substring(1).trim();
        if (body.isEmpty()) return CommandRunner.Outcome.unknown();
        String name = body.split("\\s+")[0];
        if (DihCommands.find(name) == null) return CommandRunner.Outcome.unknown();

        List<Component> printed = java.util.Collections.synchronizedList(new ArrayList<>());
        Minecraft mc = Minecraft.getInstance();
        String run = body;
        try (AutoCloseable ignored = DihClientMessaging.tap(printed::add)) {
            if (mc.isSameThread()) {
                DihCommands.dispatch(run);
            } else {
                CompletableFuture<Void> done = new CompletableFuture<>();
                mc.execute(() -> {
                    try {
                        DihCommands.dispatch(run);
                    } finally {
                        done.complete(null);
                    }
                });
                done.get(10, TimeUnit.SECONDS);
                Thread.sleep(SETTLE_MILLIS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            return CommandRunner.Outcome.failed("." + name + " didn't finish: " + e.getMessage());
        }
        List<Component> lines;
        synchronized (printed) {
            lines = new ArrayList<>(printed);
        }
        boolean error = lines.stream().anyMatch(DotCommandRunner::isError);
        return new CommandRunner.Outcome(true, error, summarize(lines.stream().map(Component::getString).toList()));
    }

    /** Red text anywhere in the message: DIH prints its errors in red. */
    static boolean isError(Component message) {
        TextColor red = TextColor.fromLegacyFormat(ChatFormatting.RED);
        return message.visit((style, text) -> !text.isBlank() && red.equals(style.getColor())
                ? Optional.of(Boolean.TRUE) : Optional.empty(), Style.EMPTY).orElse(Boolean.FALSE);
    }

    /** Lines without the "[DIH]" tag, repeats dropped, joined with " | " and capped. */
    static String summarize(List<String> lines) {
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String text = line.replaceAll("\\s+", " ").trim().replaceFirst("^\\[DIH]\\s*", "");
            if (!text.isEmpty() && !kept.contains(text)) kept.add(text);
        }
        String joined = String.join(" | ", kept);
        return joined.length() > MAX_CHARS ? joined.substring(0, MAX_CHARS) + "…" : joined;
    }
}
