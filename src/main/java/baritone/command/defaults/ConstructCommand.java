package baritone.command.defaults;

import baritone.Baritone;
import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.schematic.ISchematic;
import baritone.api.schematic.format.ISchematicFormat;
import baritone.api.utils.BetterBlockPos;
import baritone.construct.ConstructJob;
import baritone.utils.schematic.SchematicSystem;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * {@code #construct}: builds a schematic from an empty inventory, gathering every block it needs with
 * {@code #acquire}. The work happens in {@link ConstructJob}; this parses the chat input.
 */
public class ConstructCommand extends Command {

    private final File schematicsDir;

    public ConstructCommand(IBaritone baritone) {
        super(baritone, "construct", "buildfromscratch");
        this.schematicsDir = new File(baritone.getPlayerContext().minecraft().gameDirectory, "schematics");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        ConstructJob job = ((Baritone) baritone).getConstructJob();
        String first = args.hasAny() ? args.peekString() : "";
        switch (first.toLowerCase(java.util.Locale.ROOT)) {
            case "status" -> {
                args.get();
                logDirect(job.status());
                return;
            }
            case "stop" -> {
                args.get();
                logDirect(job.stop());
                return;
            }
            default -> {
            }
        }
        boolean planOnly = first.equalsIgnoreCase("plan");
        if (planOnly) args.get();
        args.requireMin(1);
        File file = schematicFile(args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile());
        BetterBlockPos origin = ctx.playerFeet();
        if (args.hasAny()) {
            args.requireMax(3);
            origin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
        }
        ISchematic schematic = load(file);
        String name = FilenameUtils.getBaseName(file.getName());
        try {
            if (planOnly) {
                for (String line : job.plan(name, schematic, origin).split("\n")) logDirect(line);
            } else {
                job.start(name, schematic, origin);
            }
        } catch (IllegalArgumentException e) {
            throw new CommandInvalidStateException(e.getMessage());
        }
    }

    private static File schematicFile(File file0) throws CommandException {
        File file = file0;
        if (FilenameUtils.getExtension(file.getAbsolutePath()).isEmpty()) {
            file = new File(file.getAbsolutePath() + "." + Baritone.settings().schematicFallbackExtension.value);
        }
        if (!file.exists()) throw new CommandInvalidStateException("Cannot find " + file);
        return file;
    }

    private static ISchematic load(File file) throws CommandException {
        Optional<ISchematicFormat> format = SchematicSystem.INSTANCE.getByFile(file);
        if (format.isEmpty()) {
            throw new CommandInvalidStateException("Unsupported schematic format. Known extensions: "
                + String.join(", ", SchematicSystem.INSTANCE.getFileExtensions()));
        }
        try (InputStream in = new FileInputStream(file)) {
            return format.get().parse(in);
        } catch (Exception e) {
            throw new CommandInvalidStateException("Couldn't read " + file.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (args.hasExactlyOne()) {
            String prefix = args.peekString();
            return Stream.concat(Stream.of("plan", "status", "stop").filter(s -> s.startsWith(prefix)),
                RelativeFile.tabComplete(args, schematicsDir));
        }
        if (args.hasAny() && args.peekString().equalsIgnoreCase("plan")) {
            args.get();
            if (args.hasExactlyOne()) return RelativeFile.tabComplete(args, schematicsDir);
        }
        if (args.has(2)) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Build a schematic from an empty inventory";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "The construct command builds a schematic without you having any of its blocks. It works out what",
            "the blocks still to place cost, gathers a batch that fits your inventory with #acquire (mining,",
            "crafting and smelting as needed), builds what it can, and repeats until the build is done.",
            "",
            "While gathering it never mines inside the build, and the blocks it gathered are kept: not thrown",
            "away as junk and not used to pillar with. Blocks no item can place (water, fire) are skipped.",
            "allowInventory is turned on while it runs and put back after.",
            "",
            "Usage:",
            "> construct <file> [x y z] - Build the file (from the schematics folder) here, or at x y z.",
            "> construct plan <file> [x y z] - List what it takes and what you still need to gather.",
            "> construct status - What it is doing.",
            "> construct stop - Stop (so do #stop and #cancel)."
        );
    }
}
