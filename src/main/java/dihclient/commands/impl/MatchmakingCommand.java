package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.modules.DihModule;
import net.minecraft.client.Minecraft;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public class MatchmakingCommand extends Command {
    public MatchmakingCommand() { super("matchmaking", "Toggle the Matchmaking overlay.", "mm"); }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();

            mc.execute(() -> {
                DihModule mod = DihModule.get();
                if (mod != null) mod.toggleMatchmakingUiBehavior();
            });
            return SUCCESS;
        });
    }
}
