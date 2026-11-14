package dihclient.script;

import dihclient.script.lang.Node.*;
import dihclient.script.lang.Parser;
import dihclient.script.lang.Token.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Typed, Minecraft-independent blocks. The script itself is the only persisted document. */
public final class ScriptBlocksModel {
    public enum Input { NONE, TEXT, NUMBER, INTEGER }

    public enum Kind {
        MESSAGE("Show a message", "Message", "Hello!", Input.TEXT, "print", "Chat"),
        COMMAND("Run a command", "Command (include /, # or .)", "/home", Input.TEXT, "command", "Chat"),
        SAY("Send chat message", "Chat message", "Hello!", Input.TEXT, "say", "Chat"),
        WAIT_CHAT("Wait for a chat message", "Text the message contains", "has requested to teleport", Input.TEXT, "waitForChat", "Chat"),
        WAIT("Wait", "Seconds", "1", Input.NUMBER, "waitSeconds", "Timing"),
        WAIT_GUI("Wait for a menu", "Timeout in ticks (20 = 1 second)", "100", Input.INTEGER, "waitForGui", "Menus"),
        CLICK_ITEM("Click a menu item", "Item name or ID", "Confirm", Input.TEXT, "clickItem", "Menus"),
        CLICK_SLOT("Click a menu slot", "Slot number (first slot = 0)", "13", Input.INTEGER, "clickSlot", "Menus"),
        CLOSE_GUI("Close the menu", "", "", Input.NONE, "closeGui", "Menus"),
        DEPOSIT("Deposit an item", "Item ID (* = everything)", "raw_gold", Input.TEXT, "deposit", "Inventory"),
        WITHDRAW("Withdraw an item", "Item ID (takes all available)", "bread", Input.TEXT, "withdraw", "Inventory"),
        SELECT_ITEM("Hold an item", "Item name or ID in hotbar", "bread", Input.TEXT, "selectItem", "Inventory"),
        SELECT_SLOT("Select a hotbar slot", "Hotbar slot (1 to 9)", "1", Input.INTEGER, "selectSlot", "Inventory"),
        EAT("Eat until full", "", "", Input.NONE, "eatUntilFull", "Inventory"),
        BARITONE("Run Baritone", "Baritone command (without #)", "mine gold_ore", Input.TEXT, "baritone", "Movement"),
        STOP_BARITONE("Stop Baritone", "", "", Input.NONE, "stopBaritone", "Movement"),
        GOTO("Walk to a place", "Block name (chest), Y level, or x y z", "chest", Input.TEXT, "goto", "Movement"),
        WAYPOINT("Walk to a waypoint", "Saved Baritone waypoint name", "home", Input.TEXT, "gotoWaypoint", "Movement"),
        OPEN_NEAREST("Open a nearby container", "Block ID (must be within reach)", "chest", Input.TEXT, "openNearest", "Menus"),
        MACRO("Run a saved macro", "Macro name", "my_macro", Input.TEXT, "macro", "Control"),
        STOP("Stop this script", "", "", Input.NONE, "stop", "Control"),
        REPEAT("Repeat a number of times", "Times", "3", Input.INTEGER, "repeat", "Control"),
        FOREVER("Repeat forever", "", "", Input.NONE, "while", "Control"),
        IF_HEALTH("If health is below", "Health points (2 = 1 heart)", "6", Input.NUMBER, "health", "Conditions"),
        IF_FOOD("If hunger is below", "Hunger points (20 = full)", "10", Input.NUMBER, "food", "Conditions"),
        IF_NEARBY("If another player is nearby", "Radius in blocks", "48", Input.NUMBER, "playerNearby", "Conditions"),
        IF_FULL("If inventory is full", "", "", Input.NONE, "inventoryFull", "Conditions"),
        IF_GUI("If a menu is open", "", "", Input.NONE, "guiOpen", "Conditions"),
        IF_BUSY("If Baritone is busy", "", "", Input.NONE, "baritoneBusy", "Conditions"),
        IF_ITEM("If carrying an item", "Item name or ID", "bread", Input.TEXT, "hasItem", "Conditions"),
        IF_CHAT("If chat said", "Text a new message contains", "tpa", Input.TEXT, "chatSeen", "Conditions");

        public final String label, field, initial, function, category;
        public final Input input;
        Kind(String label, String field, String initial, Input input, String function, String category) {
            this.label = label; this.field = field; this.initial = initial;
            this.input = input; this.function = function; this.category = category;
        }
        public boolean container() { return this == REPEAT || this == FOREVER || category.equals("Conditions"); }
    }

    public static final class Block {
        public Kind kind;
        public String value;
        public final List<Block> children = new ArrayList<>();
        public Block(Kind kind, String value) { this.kind = kind; this.value = value; }
        public Block copy() {
            Block copy = new Block(kind, value);
            for (Block child : children) copy.children.add(child.copy());
            return copy;
        }
        public String argument() {
            if (kind.input == Input.NONE) return "";
            if (kind.input == Input.TEXT) return quote(value);
            try {
                if (!value.trim().matches("[0-9]+(?:\\.[0-9]+)?")) throw new NumberFormatException();
                BigDecimal n = new BigDecimal(value.trim());
                if (n.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0
                    || (kind.input == Input.INTEGER && n.stripTrailingZeros().scale() > 0)
                    || (kind == Kind.SELECT_SLOT && (n.intValue() < 1 || n.intValue() > 9))) throw new NumberFormatException();
                return n.stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(kind == Kind.SELECT_SLOT ? "Choose a hotbar slot from 1 to 9."
                    : "Enter a non-negative " + (kind.input == Input.INTEGER ? "whole number." : "number (for example 0.5)."));
            }
        }
        private String expression() {
            String arg = argument();
            if (kind == Kind.REPEAT) return "repeat (" + arg + ")";
            if (kind == Kind.FOREVER) return "while (true)";
            if (kind == Kind.IF_HEALTH || kind == Kind.IF_FOOD) return "if (" + kind.function + "() < " + arg + ")";
            String call = kind.function + "(" + arg + ")";
            return kind.container() ? "if (" + call + ")" : call;
        }
    }

    public final List<Block> main = new ArrayList<>();
    public final List<Block> cleanup = new ArrayList<>();

    public String source() {
        StringBuilder out = new StringBuilder("function main() {\n");
        write(out, main, 1);
        out.append("}\n\nfunction onStop() {\n");
        write(out, cleanup, 1);
        return out.append("}\n").toString();
    }

    private static void write(StringBuilder out, List<Block> blocks, int depth) {
        if (depth > 32) throw new IllegalArgumentException("Use at most 32 levels of nested blocks.");
        for (Block b : blocks) {
            out.append("    ".repeat(depth)).append(b.expression());
            if (b.kind.container()) {
                out.append(" {\n");
                write(out, b.children, depth + 1);
                out.append("    ".repeat(depth)).append("}\n");
            } else out.append(";\n");
        }
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    /** Import only fully representable scripts; never silently discard advanced code. */
    public static ScriptBlocksModel parse(String source) {
        try {
            var program = Parser.parse(source);
            if (!program.globals().isEmpty() || !program.hasFunction("main")) throw unsupported();
            var model = new ScriptBlocksModel();
            for (var fn : program.functions().values()) {
                if (!fn.params().isEmpty()) throw unsupported();
                List<Block> target = switch (fn.name()) {
                    case "main" -> model.main;
                    case "onStop" -> model.cleanup;
                    default -> throw unsupported();
                };
                read(fn.body(), target, 0);
            }
            model.source(); // enforce the same bounds on imported fields
            return model;
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw unsupported(); }
    }

    private static void read(dihclient.script.lang.Node.Block body, List<Block> target, int depth) {
        if (depth > 32) throw unsupported();
        for (Stmt statement : body.body()) {
            Block b;
            dihclient.script.lang.Node.Block nested = null;
            if (statement instanceof ExprStmt s && s.expr() instanceof Call call) {
                Kind kind = java.util.Arrays.stream(Kind.values()).filter(k -> !k.container() && k.function.equals(call.name()))
                    .findFirst().orElseThrow(ScriptBlocksModel::unsupported);
                b = fromCall(kind, call);
            } else if (statement instanceof Repeat s) {
                b = new Block(Kind.REPEAT, number(s.count())); nested = s.body();
            } else if (statement instanceof While s && s.condition() instanceof Literal l && Boolean.TRUE.equals(l.value())) {
                b = new Block(Kind.FOREVER, ""); nested = s.body();
            } else if (statement instanceof If s && s.otherwise() == null && s.then() instanceof dihclient.script.lang.Node.Block bodyThen) {
                b = condition(s.condition()); nested = bodyThen;
            } else throw unsupported();
            if (nested != null) read(nested, b.children, depth + 1);
            target.add(b);
        }
    }

    private static Block condition(Expr expr) {
        if (expr instanceof Binary e && e.op() == Type.LESS && e.left() instanceof Call call && call.args().isEmpty()) {
            Kind k = switch (call.name()) { case "health" -> Kind.IF_HEALTH; case "food" -> Kind.IF_FOOD; default -> throw unsupported(); };
            return new Block(k, number(e.right()));
        }
        if (expr instanceof Call call) {
            for (Kind k : Kind.values()) if (k.category.equals("Conditions") && k != Kind.IF_HEALTH && k != Kind.IF_FOOD
                && k.function.equals(call.name())) return fromCall(k, call);
        }
        throw unsupported();
    }

    private static Block fromCall(Kind kind, Call call) {
        if (call.args().size() != (kind.input == Input.NONE ? 0 : 1)) throw unsupported();
        String value = "";
        if (kind.input == Input.TEXT) {
            if (!(call.args().getFirst() instanceof Literal l) || !(l.value() instanceof String s)) throw unsupported();
            value = s;
        } else if (kind.input != Input.NONE) value = number(call.args().getFirst());
        return new Block(kind, value);
    }

    private static String number(Expr expr) {
        if (expr instanceof Literal l && l.value() instanceof Double n && Double.isFinite(n)) return BigDecimal.valueOf(n).stripTrailingZeros().toPlainString();
        throw unsupported();
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("This script uses advanced code. Keep editing in Code, or create a new block script.");
    }

    public record Recipe(String name, String source) { }
    public static List<Recipe> recipes() {
        return List.of(
            new Recipe("Say hello three times", "function main() { repeat (3) { print(\"Hello!\"); waitSeconds(1); } }"),
            new Recipe("Mine gold; retreat on low health", """
                function main() {
                    baritone("mine gold_ore");
                    while (true) {
                        if (health() < 6) { stopBaritone(); command("/home"); stop(); }
                        waitSeconds(1);
                    }
                }
                function onStop() { stopBaritone(); }
                """),
            new Recipe("Open a menu and click Confirm", "function main() { command(\"/menu\"); waitForGui(100); clickItem(\"Confirm\"); }"));
    }
}
