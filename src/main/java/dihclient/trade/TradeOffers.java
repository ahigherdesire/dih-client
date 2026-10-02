package dihclient.trade;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the game's merchant offers and villagers into the plain {@link TradeOffer} data the logic uses. */
public final class TradeOffers {

    private TradeOffers() {
    }

    public static List<TradeOffer> from(MerchantOffers offers) {
        List<TradeOffer> out = new ArrayList<>();
        if (offers == null) return out;
        for (int i = 0; i < offers.size(); i++) out.add(from(i, offers.get(i)));
        return out;
    }

    public static TradeOffer from(int index, MerchantOffer offer) {
        ItemStack result = offer.getResult();
        ItemStack costA = offer.getCostA();
        ItemStack costB = offer.getCostB();
        return new TradeOffer(index, id(result), result.getCount(), storedEnchantments(result),
                id(costA), costA.getCount(), costB.isEmpty() ? null : id(costB), costB.isEmpty() ? 0 : costB.getCount(),
                offer.getUses(), offer.getMaxUses());
    }

    public static String id(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static Map<String, Integer> storedEnchantments(ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) return Map.of();
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> e : stored.entrySet()) {
            e.getKey().unwrapKey().ifPresent(key -> out.put(key.identifier().toString(), e.getIntValue()));
        }
        return out;
    }

    /** {@code minecraft:librarian}; wandering traders are {@code minecraft:wandering_trader}. */
    public static String profession(AbstractVillager merchant) {
        if (merchant instanceof Villager villager) {
            return villager.getVillagerData().profession().unwrapKey()
                    .map(key -> key.identifier().toString()).orElse("minecraft:none");
        }
        return BuiltInRegistries.ENTITY_TYPE.getKey(merchant.getType()).toString();
    }

    public static int level(AbstractVillager merchant) {
        return merchant instanceof Villager villager ? villager.getVillagerData().level() : 0;
    }
}
