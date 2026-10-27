package dihclient.commands.impl;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dihclient.commands.Command;
import dihclient.commands.CommandSuggest;
import dihclient.commands.DihCommandSource;
import dihclient.commands.DihCommands;
import dihclient.gui.screen.DihSchematicPlacementScreen;
import dihclient.gui.screen.DihSchematicsScreen;
import dihclient.modules.Module;
import dihclient.modules.ModuleRegistry;
import dihclient.modules.SchematicsModule;
import dihclient.schematic.SchematicActions;
import dihclient.schematic.SchematicPlacement;
import dihclient.schematic.SchematicTransform;
import dihclient.schematic.SchematicVerifier;
import dihclient.schematic.Schematics;
import dihclient.util.DihClientMessaging;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@code .schem}: place schematic files in the world, turn and move them, and check, list or build what they still
 * need. Files come from {@code <game dir>/schematics}.
 */
public class SchematicCommand extends Command {

    private static final int MATERIAL_LINES = 12;

    public SchematicCommand() {
        super("schem", "Schematics: load, place, turn and build schematic files (.litematic, .schematic, .schem, .nbt).",
            "schematic", "schematics");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());
        root.then(literal("list").executes(ctx -> listFiles()));
        root.then(literal("placements").executes(ctx -> listPlacements()));
        root.then(literal("menu").executes(ctx -> openLater(() -> Minecraft.getInstance().gui.setScreen(new DihSchematicsScreen(null)))));
        for (String verb : new String[]{"load", "place"}) {
            root.then(literal(verb)
                .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("file", StringArgumentType.string())
                    .suggests(SchematicCommand::files)
                    .executes(ctx -> place(StringArgumentType.getString(ctx, "file"), null))
                    .then(coordinates(ctx -> place(StringArgumentType.getString(ctx, "file"), position(ctx))))));
        }
        root.then(literal("open").executes(ctx -> openLater(() -> dihclient.schematic.SchematicDrop.pick(Minecraft.getInstance()))));
        root.then(literal("import")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("path", StringArgumentType.greedyString())
                .executes(ctx -> importAndPlace(StringArgumentType.getString(ctx, "path")))));
        root.then(literal("select")
            .then(placementArgument().executes(ctx -> withPlacement(ctx, p -> {
                Schematics.get().select(p);
                say("§aSelected §f" + p.name());
            }))));
        root.then(literal("info")
            .executes(ctx -> withPlacement(null, p -> say("§7" + SchematicActions.describe(p))))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, p -> say("§7" + SchematicActions.describe(p))))));
        root.then(literal("move")
            .then(coordinates(ctx -> withPlacement(null, p -> {
                p.moveTo(position(ctx));
                changed(p, "Moved");
            }))));
        root.then(literal("here").executes(ctx -> withPlacement(null, p -> {
            p.moveTo(Minecraft.getInstance().player.blockPosition());
            changed(p, "Moved");
        })));
        root.then(literal("nudge")
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("dx", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("dy", IntegerArgumentType.integer())
                    .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("dz", IntegerArgumentType.integer())
                        .executes(ctx -> withPlacement(null, p -> {
                            p.nudge(IntegerArgumentType.getInteger(ctx, "dx"), IntegerArgumentType.getInteger(ctx, "dy"),
                                IntegerArgumentType.getInteger(ctx, "dz"));
                            changed(p, "Moved");
                        }))))));
        root.then(literal("rotate")
            .executes(ctx -> withPlacement(null, p -> {
                p.rotate(1);
                changed(p, "Rotated");
            }))
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("quarters", IntegerArgumentType.integer(-3, 3))
                .executes(ctx -> withPlacement(null, p -> {
                    p.rotate(IntegerArgumentType.getInteger(ctx, "quarters"));
                    changed(p, "Rotated");
                }))));
        root.then(literal("mirror")
            .executes(ctx -> withPlacement(null, p -> {
                p.cycleMirror();
                changed(p, "Mirrored");
            }))
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("axis", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSuggest.literals(builder, "none", "x", "z"))
                .executes(ctx -> withPlacement(null, p -> {
                    p.setTransform(p.transform().withMirror(SchematicTransform.Mirroring.parse(StringArgumentType.getString(ctx, "axis"))));
                    changed(p, "Mirrored");
                }))));
        root.then(literal("adjust").executes(ctx -> withPlacement(null,
            p -> openLater(() -> Minecraft.getInstance().gui.setScreen(new DihSchematicPlacementScreen(p, null))))));
        root.then(literal("show")
            .executes(ctx -> withPlacement(null, p -> visible(p, true)))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, p -> visible(p, true)))));
        root.then(literal("hide")
            .executes(ctx -> withPlacement(null, p -> visible(p, false)))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, p -> visible(p, false)))));
        root.then(literal("remove")
            .executes(ctx -> withPlacement(null, SchematicCommand::remove))
            .then(literal("all").executes(ctx -> removeAll()))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, SchematicCommand::remove))));
        root.then(literal("reload")
            .executes(ctx -> withPlacement(null, p -> {
                Schematics.get().reload(p);
                say("§7Reloading §f" + p.file());
            })));
        root.then(literal("materials")
            .executes(ctx -> withPlacement(null, SchematicCommand::materials))
            .then(literal("csv")
                .executes(ctx -> withPlacement(null, SchematicCommand::exportMaterials))
                .then(placementArgument().executes(ctx -> withPlacement(ctx, SchematicCommand::exportMaterials))))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, SchematicCommand::materials))));
        root.then(literal("verify")
            .executes(ctx -> withPlacement(null, SchematicCommand::verify))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, SchematicCommand::verify))));
        root.then(literal("build")
            .executes(ctx -> withPlacement(null, p -> say("§a" + SchematicActions.build(p))))
            .then(placementArgument().executes(ctx -> withPlacement(ctx, p -> say("§a" + SchematicActions.build(p))))));
        root.then(literal("layer")
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("mode", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSuggest.literals(builder, "all", "single", "below", "above"))
                .executes(ctx -> layer(StringArgumentType.getString(ctx, "mode"), null))
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("y", IntegerArgumentType.integer())
                    .executes(ctx -> layer(StringArgumentType.getString(ctx, "mode"), IntegerArgumentType.getInteger(ctx, "y"))))));
    }

    private static LiteralArgumentBuilder<DihCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static RequiredArgumentBuilder<DihCommandSource, String> placementArgument() {
        return RequiredArgumentBuilder.<DihCommandSource, String>argument("placement", StringArgumentType.string())
            .suggests(SchematicCommand::placements);
    }

    private static RequiredArgumentBuilder<DihCommandSource, Integer> coordinates(com.mojang.brigadier.Command<DihCommandSource> run) {
        return RequiredArgumentBuilder.<DihCommandSource, Integer>argument("x", IntegerArgumentType.integer())
            .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("y", IntegerArgumentType.integer())
                .then(RequiredArgumentBuilder.<DihCommandSource, Integer>argument("z", IntegerArgumentType.integer())
                    .executes(run)));
    }

    private static BlockPos position(CommandContext<DihCommandSource> ctx) {
        return new BlockPos(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "y"),
            IntegerArgumentType.getInteger(ctx, "z"));
    }

    private static int usage() {
        String p = DihCommands.effectivePrefix() + "schem ";
        say("§eSchematics §7(files in §f" + Schematics.folder() + "§7)");
        say("§7" + p + "list §8- §7files you can place");
        say("§7" + p + "place <file> [x y z] §8- §7place one at you or at a position");
        say("§7" + p + "open §8- §7pick a file from anywhere on your PC and place it");
        say("§7" + p + "import <path> §8- §7the same with a typed path (or a name in Downloads)");
        say("§7Or drag a file onto the game window to place it.");
        say("§7" + p + "placements / select <n> / info §8- §7your placements here");
        say("§7" + p + "move <x y z> / here / nudge <dx dy dz> / adjust §8- §7move the selected one");
        say("§7" + p + "rotate [quarters] / mirror [none|x|z] §8- §7turn it");
        say("§7" + p + "materials / verify / build §8- §7what it needs, what is wrong, build it with Baritone");
        say("§7" + p + "materials csv §8- §7save the material list as a spreadsheet in schematics/materials");
        say("§7" + p + "show / hide / remove [n|all] / reload / layer <all|single|below|above> [y] / menu");
        if (baritone.utils.schematic.litematica.LitematicaHelper.isLitematicaPresent()) {
            say("§7Litematica is installed too: its placements stay separate, and #litematica builds those.");
        }
        return SUCCESS;
    }

    private static int listFiles() {
        List<String> files = Schematics.files();
        if (files.isEmpty()) {
            say("§7No schematic files in §f" + Schematics.folder() + "§7. Pick one with §f"
                + DihCommands.effectivePrefix() + "schem open§7 or drag one onto the game window.");
            return SUCCESS;
        }
        say("§eSchematic files §7(" + files.size() + "):");
        for (String file : files) {
            say("§7- §f" + file);
        }
        return SUCCESS;
    }

    private static int listPlacements() {
        List<SchematicPlacement> list = Schematics.get().placements();
        if (list.isEmpty()) {
            say("§7No placements here. §f" + DihCommands.effectivePrefix() + "schem place <file>");
            return SUCCESS;
        }
        SchematicPlacement selected = Schematics.get().selected();
        say("§ePlacements §7(" + list.size() + "):");
        for (int i = 0; i < list.size(); i++) {
            SchematicPlacement p = list.get(i);
            say((p == selected ? "§a> " : "§7  ") + (i + 1) + ". §f" + SchematicActions.describe(p));
        }
        return SUCCESS;
    }

    private static int place(String file, BlockPos at) {
        try {
            SchematicPlacement p = SchematicActions.place(Minecraft.getInstance(), file, at);
            enableModule();
            dihclient.schematic.SchematicPlacing.start(p);
            BlockPos o = p.origin();
            say("§aPlaced §f" + file + "§a at §f" + o.getX() + " " + o.getY() + " " + o.getZ()
                + "§7. Arrows and PgUp/PgDn move it, R/M/G turn, mirror and ground it, Enter keeps it.");
        } catch (IllegalArgumentException e) {
            say("§c" + e.getMessage());
        }
        return SUCCESS;
    }

    /**
     * Copies a file from anywhere (a pasted Windows path, quoted or not, or a name in Downloads) into the schematics
     * folder and places it.
     */
    private static int importAndPlace(String typed) {
        String raw = typed.strip();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        java.nio.file.Path path;
        try {
            path = java.nio.file.Path.of(raw);
            if (!path.isAbsolute()) {
                path = dihclient.schematic.SchematicFiles.downloadsFolder().resolve(raw);
            }
        } catch (java.nio.file.InvalidPathException e) {
            say("§cNot a file path: " + raw);
            return SUCCESS;
        }
        if (!dihclient.schematic.SchematicFiles.isSchematic(path)) {
            say("§cNo .litematic, .schematic, .schem or .nbt file at §f" + path);
            return SUCCESS;
        }
        try {
            String file = Schematics.importFile(path);
            say("§7Copied into §f" + Schematics.folder() + "§7 as §f" + file);
            return place(file, null);
        } catch (IllegalArgumentException e) {
            say("§c" + e.getMessage());
            return SUCCESS;
        }
    }

    private static int withPlacement(CommandContext<DihCommandSource> ctx, Consumer<SchematicPlacement> action) {
        String query = ctx == null ? null : StringArgumentType.getString(ctx, "placement");
        SchematicPlacement p = Schematics.get().find(query);
        if (p == null) {
            say(query == null
                ? "§7No placement selected. §f" + DihCommands.effectivePrefix() + "schem place <file>"
                : "§cNo placement \"" + query + "\". §7" + DihCommands.effectivePrefix() + "schem placements lists them.");
            return SUCCESS;
        }
        action.accept(p);
        return SUCCESS;
    }

    private static void changed(SchematicPlacement p, String what) {
        Schematics.get().changed();
        say("§a" + what + " §f" + SchematicActions.describe(p));
    }

    private static void visible(SchematicPlacement p, boolean visible) {
        p.setVisible(visible);
        Schematics.get().changed();
        say("§7" + p.name() + (visible ? " shown" : " hidden"));
    }

    private static void remove(SchematicPlacement p) {
        Schematics.get().remove(p);
        say("§7Removed §f" + p.name());
    }

    private static int removeAll() {
        List<SchematicPlacement> all = Schematics.get().placements();
        all.forEach(Schematics.get()::remove);
        say("§7Removed " + all.size() + " placements");
        return SUCCESS;
    }

    private static void materials(SchematicPlacement p) {
        List<String> lines = SchematicActions.materials(Minecraft.getInstance(), p, MATERIAL_LINES);
        say("§e" + lines.getFirst());
        for (int i = 1; i < lines.size(); i++) {
            say("§7  " + lines.get(i));
        }
        if (lines.size() > 1) {
            say("§8Full list: " + DihCommands.effectivePrefix() + "schem menu, then Materials. Save it with "
                + DihCommands.effectivePrefix() + "schem materials csv");
        }
    }

    private static void exportMaterials(SchematicPlacement p) {
        try {
            say("§a" + SchematicActions.exportMaterials(Minecraft.getInstance(), p));
        } catch (IllegalArgumentException e) {
            say("§c" + e.getMessage());
        }
    }

    private static void verify(SchematicPlacement p) {
        if (!p.isLoaded()) {
            say("§7" + SchematicActions.describe(p));
            return;
        }
        SchematicVerifier.Counts c = SchematicActions.verify(Minecraft.getInstance(), p);
        say("§e" + p.name() + "§7: " + c.correct() + "/" + c.total() + " right, "
            + c.of(SchematicVerifier.Mismatch.MISSING) + " missing, "
            + c.of(SchematicVerifier.Mismatch.WRONG_BLOCK) + " wrong block, "
            + c.of(SchematicVerifier.Mismatch.WRONG_STATE) + " wrong state, "
            + c.of(SchematicVerifier.Mismatch.EXTRA) + " extra (loaded chunks only)");
    }

    private static int layer(String mode, Integer y) {
        SchematicsModule module = module();
        if (module == null) {
            return SUCCESS;
        }
        String normalized = switch (mode.toLowerCase(Locale.ROOT)) {
            case "single", "one" -> "Single";
            case "below" -> "Below";
            case "above" -> "Above";
            default -> "All";
        };
        int height = y != null ? y : Minecraft.getInstance().player == null ? 64 : Minecraft.getInstance().player.blockPosition().getY();
        module.setLayer(normalized, normalized.equals("All") ? null : height);
        say("§7Layers: §f" + normalized + (normalized.equals("All") ? "" : " " + height));
        return SUCCESS;
    }

    private static void enableModule() {
        SchematicsModule module = module();
        if (module != null && !module.isEnabled()) {
            module.setEnabled(true);
        }
    }

    private static SchematicsModule module() {
        Module module = ModuleRegistry.get("schematics");
        if (module instanceof SchematicsModule schematics) {
            return schematics;
        }
        say("§cThe Schematics module is not available.");
        return null;
    }

    /**
     * Opens a screen after the chat screen the command came from has closed.
     */
    private static int openLater(Runnable open) {
        Minecraft.getInstance().execute(open);
        return SUCCESS;
    }

    private static CompletableFuture<Suggestions> files(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining().toLowerCase(Locale.ROOT).replace("\"", "");
        for (String file : Schematics.files()) {
            if (file.toLowerCase(Locale.ROOT).startsWith(typed)) {
                builder.suggest(quote(file));
            }
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> placements(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        List<SchematicPlacement> list = Schematics.get().placements();
        for (int i = 0; i < list.size(); i++) {
            builder.suggest(String.valueOf(i + 1));
        }
        return builder.buildFuture();
    }

    /**
     * @return the text as Brigadier reads it back: quoted when it has characters an unquoted string cannot hold
     */
    static String quote(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(text.charAt(i))) {
                return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return text;
    }

    private static void say(String message) {
        DihClientMessaging.sendPrefixed(message);
    }
}
