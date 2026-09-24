package dihclient.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class DihBlockNbtInspector {
    private static final int RAW_DISPLAY_CHAR_CAP = 40_000;

    private DihBlockNbtInspector() {
    }

    public static BlockInspection inspect(CompoundTag source, List<ItemStack> containerItems, String note) {
        CompoundTag root = source == null ? new CompoundTag() : source.copy();
        List<ItemStack> items = copyItems(containerItems);
        CompoundTag state = root.getCompound("state").orElse(new CompoundTag());
        String blockId = state.getStringOr("Name", "minecraft:air");
        String raw = root.toString();

        List<DihItemNbtInspector.InspectionLine> nice = new ArrayList<>();
        section(nice, "Identity", DihColors.packetLightYellow());
        line(nice, "Block: " + blockId, DihColors.packetWhite());
        line(nice, "State: " + root.getStringOr("block_state", blockId), DihColors.textSecondary());
        line(nice, "Dimension: " + root.getStringOr("dimension", "<unknown>"), DihColors.textSecondary());
        int[] pos = root.getIntArray("position").orElse(new int[0]);
        line(nice, "Position: " + position(pos), DihColors.textSecondary());
        line(nice, "Source: " + root.getStringOr("source", "client"), DihColors.textMuted());

        CompoundTag entity = root.getCompound("block_entity").orElse(null);
        if (entity != null) {
            blank(nice);
            section(nice, "Block Entity", DihColors.packetCyan());
            line(nice, "Type: " + entity.getStringOr("id", "<unknown>"), DihColors.packetWhite());
            line(nice, "Fields: " + entity.size(), DihColors.textSecondary());
        }

        if (!items.isEmpty() || root.getBooleanOr("contents_available", false)) {
            blank(nice);
            section(nice, "Contents", DihColors.packetGreen());
            line(nice, "Slots: " + root.getIntOr("container_slots", items.size()), DihColors.textSecondary());
            int nonEmpty = 0;
            for (int slot = 0; slot < items.size(); slot++) {
                ItemStack stack = items.get(slot);
                if (stack == null || stack.isEmpty()) continue;
                nonEmpty++;
                String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                line(nice, "Slot " + slot + ": " + stack.getCount() + "x " + id
                    + " (" + stack.getHoverName().getString() + ")", DihColors.packetWhite());
            }
            if (nonEmpty == 0) line(nice, "Container is empty.", DihColors.textMuted());
        }

        if (note != null && !note.isBlank()) {
            blank(nice);
            section(nice, "Access", DihColors.packetOrange());
            line(nice, note, DihColors.textSecondary());
        }

        List<DihItemNbtInspector.InspectionLine> rawLines = new ArrayList<>();
        section(rawLines, "Raw Block SNBT", DihColors.packetLightYellow());
        boolean truncated = raw.length() > RAW_DISPLAY_CHAR_CAP;
        String shown = truncated ? raw.substring(0, RAW_DISPLAY_CHAR_CAP) : raw;
        for (String rawLine : DihItemNbtInspector.prettySnbtLines(shown)) {
            rawLines.add(new DihItemNbtInspector.InspectionLine(rawLine, DihColors.packetWhite(),
                DihItemNbtInspector.tokenizeStructuredText(rawLine, DihColors.packetWhite())));
        }
        if (truncated) {
            line(rawLines, "... (display truncated; Copy Raw keeps everything)", DihColors.textMuted());
        }

        StringBuilder pretty = new StringBuilder("Block NBT - ").append(blockId);
        for (DihItemNbtInspector.InspectionLine inspectionLine : nice) {
            pretty.append('\n').append(inspectionLine.text());
        }
        return new BlockInspection(blockId, List.copyOf(nice), List.copyOf(rawLines), pretty.toString(), raw);
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        if (source == null || source.isEmpty()) return List.of();
        List<ItemStack> copy = new ArrayList<>(source.size());
        for (ItemStack stack : source) copy.add(stack == null ? ItemStack.EMPTY : stack.copy());
        return List.copyOf(copy);
    }

    private static String position(int[] pos) {
        return pos.length >= 3 ? pos[0] + ", " + pos[1] + ", " + pos[2] : "<unknown>";
    }

    private static void section(List<DihItemNbtInspector.InspectionLine> lines, String title, int color) {
        line(lines, "[" + title + "]", color);
    }

    private static void line(List<DihItemNbtInspector.InspectionLine> lines, String text, int color) {
        lines.add(new DihItemNbtInspector.InspectionLine(text == null ? "" : text, color));
    }

    private static void blank(List<DihItemNbtInspector.InspectionLine> lines) {
        line(lines, "", DihColors.textMuted());
    }

    public record BlockInspection(String title, List<DihItemNbtInspector.InspectionLine> niceLines,
                                  List<DihItemNbtInspector.InspectionLine> rawLines,
                                  String prettyCopyText, String rawCopyText)
        implements DihItemNbtInspector.Inspection {

        @Override
        public String windowTitle() {
            return "Block NBT - " + title;
        }

        @Override
        public String subject() {
            return "block";
        }

        @Override
        public ItemStack stack() {
            return ItemStack.EMPTY;
        }
    }
}
