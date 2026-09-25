package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import dihclient.modules.FleeModule;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * {@code #flee} — chat front end for the Flee module. Everything here is also in the module's
 * settings screen ({@code #flee gui}).
 *
 * <pre>
 *   #flee                          status
 *   #flee on | off                 master switch
 *   #flee players                  toggle a trigger
 *   #flee players on | off         set a trigger
 *   #flee players 48               set its threshold (and turn it on)
 *   #flee whenpeoplenearby = true  loose form works too
 *   #flee action disconnect | command | stop
 *   #flee command /spawn
 *   #flee check busy | always
 *   #flee test                     fire the action now
 * </pre>
 */
public class FleeCommand extends Command {

    /** Loose names (lower case, letters and digits only) -> trigger setting id. */
    private static final Map<String, String> TRIGGER_NAMES = Map.ofEntries(
            Map.entry("players", "player-nearby"), Map.entry("player", "player-nearby"),
            Map.entry("people", "player-nearby"), Map.entry("nearby", "player-nearby"),
            Map.entry("playernearby", "player-nearby"), Map.entry("playersnearby", "player-nearby"),
            Map.entry("whenplayersnearby", "player-nearby"), Map.entry("whenpeoplenearby", "player-nearby"),
            Map.entry("whenpeoplenearly", "player-nearby"), Map.entry("peoplenearby", "player-nearby"),
            Map.entry("health", "low-health"), Map.entry("lowhealth", "low-health"), Map.entry("hp", "low-health"),
            Map.entry("hunger", "low-hunger"), Map.entry("lowhunger", "low-hunger"), Map.entry("food", "low-hunger"),
            Map.entry("tool", "tool-worn"), Map.entry("toolworn", "tool-worn"), Map.entry("durability", "tool-worn"),
            Map.entry("pickaxe", "no-pickaxe"), Map.entry("nopickaxe", "no-pickaxe"),
            Map.entry("full", "inventory-full"), Map.entry("inventoryfull", "inventory-full"),
            Map.entry("inventory", "inventory-full"), Map.entry("invfull", "inventory-full"));

    private static final List<String> TRIGGER_WORDS = List.of("players", "health", "hunger", "tool", "pickaxe", "full");

    public FleeCommand(IBaritone baritone) {
        super(baritone, "flee");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        FleeModule flee = FleeModule.get();
        if (flee == null) throw new CommandInvalidStateException("The Flee module isn't available.");

        String raw = args.rawRest().trim();
        String[] words = raw.replace("=", " ").trim().split("\\s+");
        if (raw.isEmpty()) {
            status(flee);
            return;
        }
        String head = key(words[0]);
        String arg = words.length > 1 ? words[1].toLowerCase(Locale.ROOT) : "";

        switch (head) {
            case "on", "enable", "true" -> {
                flee.setConfiguredEnabled(true);
                logDirect("Flee is on. " + onTriggers(flee));
                return;
            }
            case "off", "disable", "false" -> {
                flee.setConfiguredEnabled(false);
                logDirect("Flee is off.");
                return;
            }
            case "status", "list" -> {
                status(flee);
                return;
            }
            case "gui", "settings", "menu", "ui" -> {
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> mc.gui.setScreen(new dihclient.gui.screen.DihModuleScreen(null).openingSettingsOf(FleeModule.ID)));
                return;
            }
            case "test" -> {
                logDirect("Firing the flee action now.");
                flee.flee("test");
                return;
            }
            case "action", "do", "then" -> {
                setAction(flee, arg, raw);
                return;
            }
            case "command", "cmd", "run" -> {
                String cmd = raw.replaceFirst("(?i)^\\s*[a-z]+\\s*=?\\s*", "").trim();
                if (cmd.isEmpty()) throw new CommandInvalidStateException("Give the command, e.g. #flee command /spawn");
                flee.setValue("command", cmd);
                flee.setValue("action", FleeModule.ACTION_COMMAND);
                logDirect("Flee will run " + cmd + ".");
                return;
            }
            case "check", "when", "busy", "onlywhilebusy" -> {
                boolean always = arg.equals("always") || arg.equals("off") || arg.equals("false");
                flee.setValue("when", always ? FleeModule.WHEN_ALWAYS : FleeModule.WHEN_BUSY);
                logDirect(always ? "Checking triggers all the time." : "Checking triggers only while Baritone is busy.");
                return;
            }
            case "reconnect" -> {
                boolean allow = parseBool(arg, false);
                flee.setValue("block-reconnect", String.valueOf(!allow));
                logDirect(allow ? "AutoReconnect may reconnect after a flee." : "You'll stay disconnected after a flee.");
                return;
            }
            default -> {
            }
        }

        String trigger = TRIGGER_NAMES.get(head);
        if (trigger == null) {
            throw new CommandInvalidStateException("Unknown '" + words[0] + "'. Triggers: "
                    + String.join(", ", TRIGGER_WORDS) + ". See #help flee.");
        }
        setTrigger(flee, trigger, arg);
    }

    private void setTrigger(FleeModule flee, String trigger, String arg) throws CommandException {
        String threshold = thresholdOf(trigger);
        boolean on;
        if (arg.isEmpty() || arg.equals("toggle")) {
            on = !Boolean.parseBoolean(flee.value(trigger));
        } else if (isNumber(arg)) {
            if (threshold == null) throw new CommandInvalidStateException(trigger + " has no number to set.");
            flee.setValue(threshold, arg);
            on = true;
        } else {
            on = parseBool(arg, true);
        }
        flee.setValue(trigger, String.valueOf(on));
        if (on && !flee.isEnabled()) flee.setConfiguredEnabled(true);
        logDirect(describe(flee, trigger) + (on ? " ON" : " off") + ". " + onTriggers(flee)
                + " Action: " + actionText(flee) + ".");
    }

    private void setAction(FleeModule flee, String arg, String raw) throws CommandException {
        String action = switch (arg) {
            case "disconnect", "dc", "leave", "quit" -> FleeModule.ACTION_DISCONNECT;
            case "stop", "stoponly", "none", "cancel" -> FleeModule.ACTION_STOP;
            case "command", "cmd" -> FleeModule.ACTION_COMMAND;
            default -> null;
        };
        if (action == null && (arg.startsWith("/") || arg.startsWith("#") || arg.startsWith("."))) {
            String cmd = raw.substring(raw.indexOf(arg.charAt(0))).trim();
            flee.setValue("command", cmd);
            action = FleeModule.ACTION_COMMAND;
        }
        if (action == null) throw new CommandInvalidStateException("Action is disconnect, command or stop.");
        flee.setValue("action", action);
        logDirect("Flee action: " + actionText(flee) + ".");
    }

    private void status(FleeModule flee) {
        logDirect("Flee is " + (flee.isEnabled() ? "ON" : "off") + ", checking "
                + (FleeModule.WHEN_ALWAYS.equals(flee.value("when")) ? "all the time" : "while Baritone is busy")
                + ". Action: " + actionText(flee) + ".");
        for (String[] t : FleeModule.TRIGGERS) {
            boolean on = Boolean.parseBoolean(flee.value(t[0]));
            logDirect("  " + (on ? "[ON]  " : "[off] ") + describe(flee, t[0]));
        }
        logDirect("#flee <trigger> [on|off|number], #flee action ..., #flee gui");
    }

    private static String describe(FleeModule flee, String trigger) {
        return switch (trigger) {
            case "player-nearby" -> "players within " + trim(flee.value("player-radius")) + " blocks";
            case "low-health" -> "health at " + trim(flee.value("health-hearts")) + " hearts or less";
            case "low-hunger" -> "food at " + flee.value("hunger-level") + "/20 or less";
            case "tool-worn" -> "held tool at " + flee.value("tool-durability") + " durability or less";
            case "no-pickaxe" -> "no pickaxe left while mining";
            case "inventory-full" -> "inventory full";
            default -> trigger;
        };
    }

    private static String actionText(FleeModule flee) {
        String action = flee.value("action");
        return FleeModule.ACTION_COMMAND.equals(action) ? "run " + flee.value("command") : action.toLowerCase(Locale.ROOT);
    }

    private static String onTriggers(FleeModule flee) {
        StringBuilder sb = new StringBuilder();
        for (String[] t : FleeModule.TRIGGERS) {
            if (Boolean.parseBoolean(flee.value(t[0]))) sb.append(sb.length() == 0 ? "" : ", ").append(t[0]);
        }
        return sb.length() == 0 ? "No triggers are on yet." : "On: " + sb + ".";
    }

    private static String thresholdOf(String trigger) {
        for (String[] t : FleeModule.TRIGGERS) if (t[0].equals(trigger)) return t[1];
        return null;
    }

    private static String key(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static boolean parseBool(String s, boolean fallback) {
        return switch (s) {
            case "on", "true", "yes", "enable", "1" -> true;
            case "off", "false", "no", "disable", "0" -> false;
            default -> fallback;
        };
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String trim(String number) {
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            String p = "";
            try { p = args.peekString().toLowerCase(Locale.ROOT); } catch (Exception ignored) {}
            final String pf = p;
            return Stream.concat(TRIGGER_WORDS.stream(),
                    Stream.of("on", "off", "status", "action", "command", "check", "reconnect", "gui", "test"))
                    .filter(s -> s.startsWith(pf));
        }
        if (args.hasExactly(2)) {
            String first = "";
            try { first = key(args.peekString()); } catch (Exception ignored) {}
            if (first.equals("action")) return Stream.of("disconnect", "command", "stop");
            if (first.equals("check")) return Stream.of("busy", "always");
            if (TRIGGER_NAMES.containsKey(first) || first.equals("reconnect")) return Stream.of("on", "off");
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Disconnect or run a command when a trigger you picked fires";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Flee gets you out when something goes wrong. Every trigger starts OFF;",
                "turn on only the ones you want. By default triggers are checked only while",
                "Baritone is busy (mining, pathing, farming).",
                "",
                "Triggers: players (within N blocks), health (hearts), hunger (food),",
                "tool (durability left), pickaxe (none left while mining), full (inventory).",
                "",
                "Usage:",
                "> flee                        - status",
                "> flee on / off               - master switch",
                "> flee players                - toggle a trigger",
                "> flee players on / off       - set it",
                "> flee players 48             - set the range and turn it on",
                "> flee health 4               - flee at 4 hearts",
                "> flee action disconnect      - or: command, stop",
                "> flee command /spawn         - run this instead of disconnecting",
                "> flee check always           - check even when Baritone is idle (default: busy)",
                "> flee reconnect on           - let AutoReconnect reconnect after a flee",
                "> flee gui                    - open the settings screen",
                "> flee test                   - fire the action now"
        );
    }
}
