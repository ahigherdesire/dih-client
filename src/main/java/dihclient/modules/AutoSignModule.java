package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.ChoiceSetting;
import dihclient.api.module.IntSetting;
import dihclient.api.module.StringSetting;
import dihclient.util.DihClientMessaging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class AutoSignModule extends Module {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    AutoSignModule() {
        super("auto-sign", "AutoSign", ModuleCategory.PLAYER, "Fills signs with configured text.");

        add(new StringSetting("line-1", "Line 1", "")
            .group("General")
            .description("Text for sign line one.").build());
        add(new StringSetting("line-2", "Line 2", "")
            .group("General")
            .description("Text for sign line two.").build());
        add(new StringSetting("line-3", "Line 3", "")
            .group("General")
            .description("Text for sign line three.").build());
        add(new StringSetting("line-4", "Line 4", "")
            .group("General")
            .description("Text for sign line four.").build());
        add(new BoolSetting("placeholders", "Placeholders", false)
            .group("General")
            .description("Replaces {player}, {date}, {time}, {x}, {y} and {z} in the lines").build());

        add(new BoolSetting("edit-existing", "Also When Editing", false)
            .group("Signs")
            .description("Replace text when editing signs.").build());
        add(new ChoiceSetting("blank-lines", "Blank Lines", "Clear", "Clear", "Keep")
            .group("Signs")
            .description("What an empty line above does to text already on the sign")
            .visibleWhen(() -> bool("edit-existing")).build());
        add(new ChoiceSetting("sides", "Sides", "Both", "Both", "Front", "Back")
            .group("Signs")
            .description("Which side of the sign gets filled").build());
        add(new BoolSetting("hanging-signs", "Hanging Signs", true)
            .group("Signs")
            .description("Also fills hanging signs").build());
        add(new BoolSetting("only-sneaking", "Only While Sneaking", false)
            .group("Signs")
            .description("Fills only when you place or open the sign while sneaking").build());

        add(new BoolSetting("auto-done", "Auto Done", true)
            .group("Finish")
            .description("Close editor after filling text.").build());
        add(new IntSetting("done-delay", "Done Delay", 0, 0, 40, 1).unit("ticks")
            .group("Finish")
            .description("Waits this long before closing the editor")
            .visibleWhen(() -> bool("auto-done")).build());
        add(new BoolSetting("notify", "Notify", false)
            .group("Finish")
            .description("Says in chat when a sign was filled").build());
    }

    /** Whether a sign editor that just opened should be filled. */
    public boolean shouldFill(boolean empty, boolean front, boolean hanging) {
        if (!empty && !editExisting()) return false;
        if (hanging && !bool("hanging-signs")) return false;
        String sides = choice("sides");
        if ("Front".equals(sides) && !front) return false;
        if ("Back".equals(sides) && front) return false;
        return !bool("only-sneaking") || MC.player != null && MC.player.isShiftKeyDown();
    }

    /** The lines to write, given what the sign already says. */
    public String[] fill(String[] existing) {
        String[] lines = signLines();
        boolean keepBlank = editExisting() && "Keep".equals(choice("blank-lines"));
        for (int i = 0; i < lines.length; i++) {
            if (bool("placeholders")) lines[i] = expand(lines[i]);
            if (keepBlank && lines[i].isEmpty() && existing != null && i < existing.length && existing[i] != null) {
                lines[i] = existing[i];
            }
        }
        if (bool("notify")) DihClientMessaging.sendPrefixed("AutoSign filled a sign.");
        return lines;
    }

    private String expand(String line) {
        if (line.indexOf('{') < 0) return line;
        LocalDateTime now = LocalDateTime.now();
        String result = line.replace("{date}", DATE.format(now)).replace("{time}", TIME.format(now));
        if (MC.player != null) {
            result = result.replace("{player}", MC.player.getGameProfile().name())
                .replace("{x}", Integer.toString(MC.player.getBlockX()))
                .replace("{y}", Integer.toString(MC.player.getBlockY()))
                .replace("{z}", Integer.toString(MC.player.getBlockZ()));
        }
        return result;
    }

    public String[] signLines() {
        return new String[]{text("line-1"), text("line-2"), text("line-3"), text("line-4")};
    }

    public boolean editExisting() {
        return bool("edit-existing");
    }

    public boolean autoDone() {
        return bool("auto-done");
    }

    /** Ticks to wait before closing; only used when {@link #autoDone()} is on. */
    public int doneDelay() {
        return Math.max(0, integer("done-delay"));
    }
}
