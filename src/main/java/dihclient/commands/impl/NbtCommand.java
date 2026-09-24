package dihclient.commands.impl;

import dihclient.commands.DihCommandSource;
import dihclient.commands.Command;
import dihclient.gui.screen.DihOverlayHostScreen;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihItemCommandSerializer;
import dihclient.util.DihItemNbtInspectOverlay;
import dihclient.util.DihNotifications;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

public class NbtCommand extends Command {
    public NbtCommand() {
        super("nbt", "Inspect or copy the held item's components (NBT).");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> {
            inspect();
            return SUCCESS;
        });
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("get").executes(ctx -> {
            inspect();
            return SUCCESS;
        }));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("copy").executes(ctx -> {
            copy();
            return SUCCESS;
        }));
    }

    private static ItemStack held() {
        net.minecraft.world.entity.player.Player player = dihclient.util.multi.MultiPilot.commandPlayer();
        if (player == null) return ItemStack.EMPTY;
        ItemStack main = player.getMainHandItem();
        return !main.isEmpty() ? main : player.getOffhandItem();
    }

    private static void inspect() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            DihClientMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        if (!DihItemNbtInspectOverlay.openGlobal(stack)) {
            DihClientMessaging.sendPrefixed("\u00a7cCould not open the NBT inspector.");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            dihclient.util.DihItemNbtInspectOverlay overlay =
                dihclient.util.DihItemNbtInspectOverlay.getSharedOverlay(mc.font);
            mc.execute(() -> {

                if (mc.gui.screen() == null) {
                    mc.gui.setScreen(new DihOverlayHostScreen(overlay));
                }
            });
        }
    }

    private static void copy() {
        ItemStack stack = held();
        if (stack.isEmpty()) {
            DihClientMessaging.sendPrefixed("\u00a7cHold an item in either hand first.");
            return;
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(DihItemCommandSerializer.giveCommand(stack));
            DihNotifications.copied("Copied /give command.");
        } catch (Throwable t) {
            DihNotifications.error("Copy failed.");
        }
    }
}
