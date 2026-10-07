package dihclient.util;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/** What another entity is wearing and holding, as far as the server has told the client; for Nametags and .invsee. */
public final class DihEquipment {
    /** Held items around the armour, in the order the gear reads left to right. */
    public static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.MAINHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND);

    private DihEquipment() {
    }

    /** The entity's items in {@link #SLOTS} order, empty slots left out. */
    public static List<ItemStack> worn(LivingEntity entity) {
        List<ItemStack> out = new ArrayList<>(SLOTS.size());
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) out.add(stack);
        }
        return out;
    }

    /** "123/363" for damageable items, else null. */
    public static String durability(ItemStack stack) {
        if (!stack.isDamageableItem()) return null;
        return (stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage();
    }

    /** Short enchantment tags such as "Sha5" or "FA2", strongest first. */
    public static List<String> shortEnchantments(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        List<Object2IntMap.Entry<Holder<Enchantment>>> entries = new ArrayList<>(enchantments.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
        List<String> out = new ArrayList<>(entries.size());
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : entries) {
            String path = entry.getKey().unwrapKey().map(key -> key.identifier().getPath()).orElse("?");
            out.add(abbreviate(path) + entry.getIntValue());
        }
        return out;
    }

    /** "Sharpness V" style names, strongest first; levels past X are written as numbers. */
    public static List<String> fullEnchantments(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        List<Object2IntMap.Entry<Holder<Enchantment>>> entries = new ArrayList<>(enchantments.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()));
        List<String> out = new ArrayList<>(entries.size());
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : entries) {
            Enchantment enchantment = entry.getKey().value();
            String name = enchantment.description().getString();
            int level = entry.getIntValue();
            out.add(level == 1 && enchantment.getMaxLevel() == 1 ? name : name + " " + levelName(level));
        }
        return out;
    }

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    /** I to X, then plain digits. */
    public static String levelName(int level) {
        return level >= 1 && level <= ROMAN.length ? ROMAN[level - 1] : String.valueOf(level);
    }

    /** "sharpness" to "Sha", "fire_aspect" to "FA", "projectile_protection" to "PP". */
    public static String abbreviate(String path) {
        String[] words = path.toLowerCase(Locale.ROOT).split("_");
        if (words.length == 1) {
            String word = words[0];
            String head = word.substring(0, Math.min(3, word.length()));
            return head.isEmpty() ? "?" : Character.toUpperCase(head.charAt(0)) + head.substring(1);
        }
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0)));
        }
        return out.toString();
    }

    /** Joins the parts with ", " into lines no wider than {@code maxWidth}, breaking only between parts. */
    public static List<String> wrapJoined(List<String> parts, int maxWidth, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String piece = parts.get(i) + (i < parts.size() - 1 ? "," : "");
            String candidate = line.isEmpty() ? piece : line + " " + piece;
            if (!line.isEmpty() && width.applyAsInt(candidate) > maxWidth) {
                lines.add(line.toString());
                line.setLength(0);
                line.append(piece);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }
}
