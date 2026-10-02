package baritone.ai.catalog;

import baritone.ai.tool.AiTool;
import baritone.ai.tool.ToolCategory;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.ai.tool.ToolSchema;
import net.minecraft.client.Minecraft;

/** Private messages. Saying something in public chat is the say tool. */
final class ChatTools {

    static final int MAX_MESSAGE = 200;

    private ChatTools() {
    }

    static void register(ToolRegistry registry) {
        registry.register(AiTool.builder("whisper", ToolCategory.CHAT)
                .dangerous()
                .gameThread()
                .summary("Send a private message to one player (/msg).")
                .schema(ToolSchema.builder()
                        .string("player", "Who to message.").required()
                        .string("message", "The message.").required()
                        .build())
                .handler((ctx, args) -> {
                    String player = MovementTools.name(args.string("player"));
                    String message = message(args.string("message"));
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) return ToolResult.failed("Not connected to a world.");
                    connection.sendCommand("msg " + player + " " + message);
                    return ToolResult.ok("Whispered to " + player + ": " + message).fact("player", player);
                })
                .build());
    }

    /** One line, no formatting codes, no command, at most {@link #MAX_MESSAGE} characters. */
    static String message(String text) {
        String line = text == null ? "" : text.replaceAll("§.?", "").replaceAll("[\\r\\n]+", " ").trim();
        if (line.isEmpty()) throw new IllegalArgumentException("Nothing to say.");
        if (line.startsWith("/")) throw new IllegalArgumentException("Messages can't be commands.");
        return line.length() > MAX_MESSAGE ? line.substring(0, MAX_MESSAGE) : line;
    }
}
