package dihclient.modules;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Target and held-item filters behind the combat modules' settings. */
final class CombatFilters {
    /** Every sword, axe and spear, plus the mace and trident. */
    static final String DEFAULT_WEAPONS = String.join("|", defaultWeapons());

    private CombatFilters() {
    }

    static List<String> defaultWeapons() {
        List<String> ids = new ArrayList<>();
        for (String material : List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite")) {
            for (String kind : List.of("sword", "axe", "spear")) ids.add("minecraft:" + material + "_" + kind);
        }
        ids.add("minecraft:mace");
        ids.add("minecraft:trident");
        return ids;
    }

    /** A list setting's registry ids, lower-cased and kept both with and without their namespace. */
    static final class IdSet {
        private String source;
        private Set<String> ids = Set.of();

        boolean contains(List<String> entries, String id) {
            Set<String> set = of(entries);
            String key = id.toLowerCase(Locale.ROOT);
            int split = key.indexOf(':');
            return set.contains(key) || split >= 0 && set.contains(key.substring(split + 1));
        }

        private Set<String> of(List<String> entries) {
            String joined = String.join("|", entries);
            if (joined.equals(source)) return ids;
            ids = normalize(entries);
            source = joined;
            return ids;
        }
    }

    static Set<String> normalize(List<String> entries) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry == null) continue;
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) continue;
            normalized.add(value);
            int split = value.indexOf(':');
            if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
        }
        return Set.copyOf(normalized);
    }

    /** Whether {@code stack} is one of the listed items; an empty hand never is. */
    static boolean holds(ItemStack stack, IdSet cache, List<String> entries) {
        if (stack == null || stack.isEmpty()) return false;
        return cache.contains(entries, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    static boolean wearsNoArmor(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !entity.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a pet filter set to Off, Yours or All skips {@code entity}. */
    static boolean ignoredPet(Entity entity, String mode, LivingEntity self) {
        if (!(entity instanceof OwnableEntity pet) || pet.getOwnerReference() == null) return false;
        if ("All".equals(mode)) return true;
        return "Yours".equals(mode) && self != null && pet.getOwnerReference().matches(self);
    }

    /** Degrees between two directions; 0 when either has no length. */
    static double angleBetween(Vec3 a, Vec3 b) {
        double lengths = a.length() * b.length();
        if (lengths < 1.0E-9D) return 0.0D;
        return Math.toDegrees(Math.acos(Mth.clamp(a.dot(b) / lengths, -1.0D, 1.0D)));
    }
}
