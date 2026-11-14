package dihclient.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dihclient.commands.Command;
import dihclient.commands.DihCommandSource;
import dihclient.gui.screen.DihAddonStoreScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

/** {@code .addons}: opens the addon store, where My Addons lists what is installed. */
public final class AddonsCommand extends Command {
    public AddonsCommand() {
        super("addons", "Open the addon store: browse, install and manage addons.", "addon");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                Screen current = mc.gui.screen();
                mc.gui.setScreen(new DihAddonStoreScreen(current instanceof ChatScreen ? null : current));
            });
            return SUCCESS;
        });
    }
}
