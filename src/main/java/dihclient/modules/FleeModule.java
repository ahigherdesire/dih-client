package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Get out when something goes wrong: disconnect, run a command, or just stop Baritone.
 *
 * <p>Every trigger is off until you turn it on, so nothing fires by surprise. By default the
 * triggers are only checked while Baritone is working (mining, pathing, farming...). Also driven
 * from chat with {@code #flee}.
 */
public final class FleeModule extends Module {
    public static final String ID = "flee";

    public static final String ACTION_DISCONNECT = "Disconnect";
    public static final String ACTION_COMMAND = "Command";
    public static final String ACTION_STOP = "Stop Only";
    public static final String WHEN_BUSY = "Baritone Busy";
    public static final String WHEN_ALWAYS = "Always";

    /** Trigger toggle id -> its threshold setting id, or null when it has none. */
    public static final String[][] TRIGGERS = {
        {"player-nearby", "player-radius"},
        {"low-health", "health-hearts"},
        {"low-hunger", "hunger-level"},
        {"tool-worn", "tool-durability"},
        {"no-pickaxe", null},
        {"inventory-full", null},
    };

    private long cooldownUntilMs;

    public FleeModule() {
        super(ID, "Flee", ModuleCategory.PLAYER,
            "Disconnects, runs a command or stops Baritone when a trigger you turned on fires.");

        add(new ChoiceSetting("when", "Check", WHEN_BUSY, WHEN_BUSY, WHEN_ALWAYS).group("When")
            .description("Baritone Busy: only while Baritone is mining, pathing or farming."));

        add(new BoolSetting("player-nearby", "Player Nearby", false).group("Triggers")
            .description("Another player comes within range."));
        add(new DoubleSetting("player-radius", "Range", 32.0, 4.0, 128.0, 1.0).group("Triggers").unit("blocks")
            .visibleWhen(() -> bool("player-nearby")));
        add(new BoolSetting("ignore-friends", "Ignore Friends", true).group("Triggers")
            .description("Friends and teammates (Teams module) don't count.")
            .visibleWhen(() -> bool("player-nearby")));
        add(new BoolSetting("low-health", "Low Health", false).group("Triggers")
            .description("Health drops to the threshold."));
        add(new DoubleSetting("health-hearts", "Hearts", 5.0, 0.5, 10.0, 0.5).group("Triggers").unit("hearts")
            .visibleWhen(() -> bool("low-health")));
        add(new BoolSetting("low-hunger", "Low Hunger", false).group("Triggers")
            .description("Food drops to the threshold (20 = full)."));
        add(new IntSetting("hunger-level", "Food", 6, 0, 19, 1).group("Triggers")
            .visibleWhen(() -> bool("low-hunger")));
        add(new BoolSetting("tool-worn", "Tool Worn", false).group("Triggers")
            .description("The tool in your hand is about to break."));
        add(new IntSetting("tool-durability", "Durability Left", 10, 1, 250, 1).group("Triggers")
            .visibleWhen(() -> bool("tool-worn")));
        add(new BoolSetting("no-pickaxe", "No Pickaxe", false).group("Triggers")
            .description("Baritone is mining and you have no pickaxe left."));
        add(new BoolSetting("inventory-full", "Inventory Full", false).group("Triggers")
            .description("No empty slot left."));

        add(new ChoiceSetting("action", "Action", ACTION_DISCONNECT, ACTION_DISCONNECT, ACTION_COMMAND, ACTION_STOP)
            .group("Action"));
        add(new StringSetting("command", "Command", "/home").group("Action")
            .description("/server command, #baritone command or .dih command.")
            .visibleWhen(() -> ACTION_COMMAND.equals(choice("action"))));
        add(new BoolSetting("stop-baritone", "Stop Baritone", true).group("Action")
            .description("Cancel whatever Baritone is doing first."));
        add(new BoolSetting("block-reconnect", "Stay Disconnected", true).group("Action")
            .description("Skip AutoReconnect after fleeing.")
            .visibleWhen(() -> ACTION_DISCONNECT.equals(choice("action"))));
        add(new DoubleSetting("cooldown", "Cooldown", 10.0, 0.0, 120.0, 1.0).group("Action").unit("s")
            .description("Wait this long before fleeing again.")
            .visibleWhen(() -> !ACTION_DISCONNECT.equals(choice("action"))));
    }

    @Override
    public String info() {
        int on = 0;
        for (String[] trigger : TRIGGERS) if (bool(trigger[0])) on++;
        return on + " on";
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || System.currentTimeMillis() < cooldownUntilMs) return;
        boolean busy = baritoneBusy();
        if (WHEN_BUSY.equals(choice("when")) && !busy) return;
        String reason = firstReason(player);
        if (reason != null) flee(reason);
    }

    private String firstReason(LocalPlayer player) {
        if (bool("player-nearby")) {
            String who = nearbyPlayer(player, decimal("player-radius"));
            if (who != null) return "player nearby (" + who + ")";
        }
        if (bool("low-health") && player.getHealth() > 0 && player.getHealth() <= decimal("health-hearts") * 2.0) {
            return String.format(Locale.ROOT, "low health (%.1f hearts)", player.getHealth() / 2.0f);
        }
        if (bool("low-hunger") && player.getFoodData().getFoodLevel() <= integer("hunger-level")) {
            return "low hunger (" + player.getFoodData().getFoodLevel() + "/20)";
        }
        if (bool("tool-worn")) {
            ItemStack held = player.getMainHandItem();
            if (!held.isEmpty() && held.isDamageableItem()) {
                int left = held.getMaxDamage() - held.getDamageValue();
                if (left <= integer("tool-durability")) return "tool worn (" + left + " durability left)";
            }
        }
        if (bool("no-pickaxe") && baritoneMining() && !hasPickaxe(player)) return "no pickaxe left";
        if (bool("inventory-full") && inventoryFull(player)) return "inventory full";
        return null;
    }

    /** Runs the configured action now. Public so {@code #flee test} can fire it by hand. */
    public void flee(String reason) {
        String action = choice("action");
        cooldownUntilMs = System.currentTimeMillis() + (long) (Math.max(0.0, decimal("cooldown")) * 1000.0);
        if (bool("stop-baritone")) stopBaritone();

        if (ACTION_DISCONNECT.equals(action) && MC.getConnection() != null && !MC.hasSingleplayerServer()) {
            if (bool("block-reconnect")) PackAutoReconnectState.holdUntilNextJoin();
            MC.getConnection().getConnection().disconnect(Component.literal("Fled: " + reason));
            return;
        }
        if (ACTION_COMMAND.equals(action)) {
            String cmd = text("command").trim();
            DihClientMessaging.sendPrefixed("§eFlee: " + reason + "§7, running §f" + cmd);
            runCommand(cmd);
            return;
        }
        DihClientMessaging.sendPrefixed("§eFlee: " + reason + "§7. Stopped"
            + (ACTION_DISCONNECT.equals(action) ? " (singleplayer, not disconnecting)." : "."));
    }

    private String nearbyPlayer(LocalPlayer self, double radius) {
        double r2 = radius * radius;
        String nearest = null;
        double best = Double.MAX_VALUE;
        for (Player other : MC.level.players()) {
            if (other == self || other.isSpectator()) continue;
            if (bool("ignore-friends") && TeamsModule.isFriendOrTeam(other)) continue;
            if (DihAntiBot.isBot(other)) continue;
            double d2 = other.distanceToSqr(self);
            if (d2 <= r2 && d2 < best) {
                best = d2;
                nearest = other.getName().getString() + ", " + Math.round(Math.sqrt(d2)) + "m";
            }
        }
        return nearest;
    }

    private static boolean hasPickaxe(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(ItemTags.PICKAXES)) return true;
        }
        return player.getOffhandItem().is(ItemTags.PICKAXES);
    }

    private static boolean inventoryFull(LocalPlayer player) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) return false;
        }
        return true;
    }

    private static baritone.api.IBaritone baritone() {
        try {
            return baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone();
        } catch (Throwable t) {
            return null;
        }
    }

    static boolean baritoneBusy() {
        baritone.api.IBaritone b = baritone();
        if (b == null) return false;
        try {
            return b.getPathingBehavior().isPathing() || b.getPathingControlManager().mostRecentInControl().isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean baritoneMining() {
        baritone.api.IBaritone b = baritone();
        try {
            return b != null && b.getMineProcess().isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void stopBaritone() {
        baritone.api.IBaritone b = baritone();
        if (b == null) return;
        try {
            b.getPathingBehavior().cancelEverything();
        } catch (Throwable ignored) {
        }
    }

    /** {@code /x} server command, {@code #x} Baritone command, {@code .x} DIH command; bare text is a server command. */
    private static void runCommand(String raw) {
        if (raw.isEmpty() || MC.getConnection() == null) return;
        try {
            if (raw.startsWith("#")) {
                baritone.api.IBaritone b = baritone();
                if (b != null) b.getCommandManager().execute(raw.substring(1));
            } else if (raw.startsWith(".")) {
                dihclient.commands.DihCommands.dispatch(raw.substring(1));
            } else {
                MC.getConnection().sendCommand(raw.startsWith("/") ? raw.substring(1) : raw);
            }
        } catch (Throwable t) {
            DihClientMessaging.sendPrefixed("§cFlee command failed: " + t.getMessage());
        }
    }

    public static FleeModule get() {
        return ModuleRegistry.get(ID) instanceof FleeModule flee ? flee : null;
    }
}
