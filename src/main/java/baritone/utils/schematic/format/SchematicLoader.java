/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils.schematic.format;

import baritone.utils.schematic.LoadedSchematic;
import baritone.utils.schematic.format.defaults.LitematicaSchematic;
import baritone.utils.schematic.format.defaults.MCEditSchematic;
import baritone.utils.schematic.format.defaults.SpongeSchematic;
import baritone.utils.schematic.format.defaults.StructureSchematic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ReportedNbtException;
import net.minecraft.nbt.Tag;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipException;

/**
 * Reads any schematic file DIH supports into a {@link LoadedSchematic}. The format is told from the file's contents,
 * not its extension, since tools have written Sponge data under ".schematic" and the like.
 * <ul>
 *     <li>{@code .litematic}: Litematica, versions 1 to 7, every region</li>
 *     <li>{@code .schematic}: MCEdit, Schematica and WorldEdit before 1.13, with legacy ids mapped to today's
 *     blocks; WorldEdit 7's Sponge data saved under this extension</li>
 *     <li>{@code .schem}: Sponge, versions 1 to 3</li>
 *     <li>{@code .nbt}: vanilla structure files</li>
 * </ul>
 * Anything else, or a damaged file, fails with a {@link SchematicLoadException} whose message says what is wrong.
 */
public final class SchematicLoader {

    /**
     * The most uncompressed NBT a file may hold.
     */
    private static final long MAX_NBT_BYTES = 512L * 1024 * 1024;

    public static final List<String> EXTENSIONS = List.of("litematic", "schematic", "schem", "nbt");

    private SchematicLoader() {}

    public static LoadedSchematic load(File file) throws SchematicLoadException {
        if (!file.isFile()) {
            throw new SchematicLoadException(file.getName() + " does not exist");
        }
        try (InputStream in = new FileInputStream(file)) {
            return load(in, file.getName());
        } catch (SchematicLoadException e) {
            throw e;
        } catch (IOException e) {
            throw new SchematicLoadException(file.getName() + " could not be read: " + e.getMessage(), e);
        }
    }

    /**
     * @param fileName used in messages, and as the name when the file records none
     */
    public static LoadedSchematic load(InputStream in, String fileName) throws SchematicLoadException {
        CompoundTag root = readNbt(in, fileName);
        try {
            return fromNbt(root, fileName).named(baseName(fileName));
        } catch (SchematicLoadException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new SchematicLoadException(fileName + " can't be loaded: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new SchematicLoadException(fileName + " is damaged or not a schematic (" + e + ")", e);
        } catch (OutOfMemoryError e) {
            throw new SchematicLoadException(fileName + " is too large for the memory available", e);
        }
    }

    /**
     * Picks the format from the tags the file holds.
     */
    public static LoadedSchematic fromNbt(CompoundTag root, String fileName) throws SchematicLoadException {
        if (root.getCompound("Schematic").isPresent() || root.getCompound("Blocks").isPresent()) {
            return new SpongeSchematic(root); // version 3
        }
        if (root.contains("Regions")) {
            return new LitematicaSchematic(root);
        }
        if (root.contains("BlockData") || (root.contains("Palette") && !root.contains("Materials") && !root.contains("Blocks"))) {
            return new SpongeSchematic(root);
        }
        if (root.contains("Materials") || root.getByteArray("Blocks").isPresent()) {
            return new MCEditSchematic(root);
        }
        if (root.contains("blocks") && root.contains("size")) {
            return new StructureSchematic(root);
        }
        throw new SchematicLoadException(fileName + " is not a schematic DIH can read (its top-level tags are "
            + root.keySet() + "). Supported: .litematic, .schematic, .schem and .nbt structure files.");
    }

    /**
     * Reads gzip-compressed NBT, as every schematic format uses, or uncompressed NBT.
     */
    public static CompoundTag readNbt(InputStream in, String fileName) throws SchematicLoadException {
        BufferedInputStream buffered = new BufferedInputStream(in);
        try {
            buffered.mark(2);
            int first = buffered.read();
            int second = buffered.read();
            buffered.reset();
            if (first == -1) {
                throw new SchematicLoadException(fileName + " is empty");
            }
            NbtAccounter accounter = NbtAccounter.create(MAX_NBT_BYTES);
            if (first == 0x1F && second == 0x8B) {
                return NbtIo.readCompressed(buffered, accounter);
            }
            if (first == Tag.TAG_COMPOUND) {
                return NbtIo.read(new DataInputStream(buffered), accounter);
            }
            throw new SchematicLoadException(fileName + " is not an NBT file, so it is no schematic");
        } catch (SchematicLoadException e) {
            throw e;
        } catch (EOFException e) {
            throw new SchematicLoadException(fileName + " is cut short (incomplete download?)", e);
        } catch (ZipException e) {
            throw new SchematicLoadException(fileName + " is damaged: its compressed NBT data is corrupt", e);
        } catch (ReportedNbtException e) {
            throw new SchematicLoadException(fileName + " is damaged or too large to read as NBT (" + e.getMessage() + ")", e);
        } catch (IOException | RuntimeException e) {
            throw new SchematicLoadException(fileName + " could not be read as NBT: " + e.getMessage(), e);
        }
    }

    public static String baseName(String fileName) {
        String name = new File(fileName).getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
