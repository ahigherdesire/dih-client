package dihclient.commands.impl;

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
import dihclient.trade.EnchantPlanner;
import dihclient.trade.TradeCaches;
import dihclient.util.DihClientMessaging;
import dihclient.util.DihFakeGamemode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * {@code .enchant sharpness 255}: puts any enchantment at any level on the item in your hand, whatever the item, by
 * handing the server the edited stack the way the creative inventory does; so it needs real creative mode.
 * {@code .enchant all}, {@code .enchant remove <enchantment>} and {@code .enchant clear} edit the rest.
 * {@code .enchant plan mending unbreaking3} is the cheapest route to those books across the villagers whose trades
 * you have opened in this world.
 */
public class EnchantCommand extends Command {
    /** Highest level the item format stores; anything above is cut to this. */
    public static final int MAX_LEVEL = 255;

    public EnchantCommand() {
        super("enchant", "Enchant your held item with anything (creative only): <enchantment> [level], all [level], "
            + "remove <enchantment>, clear. plan <enchantments> finds villagers selling books.");
    }

    @Override
    public void build(LiteralArgumentBuilder<DihCommandSource> root) {
        root.executes(ctx -> usage());
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("plan")
            .executes(ctx -> usage())
            .then(RequiredArgumentBuilder.<DihCommandSource, String>argument("enchantments", StringArgumentType.greedyString())
                .executes(ctx -> {
                    plan(StringArgumentType.getString(ctx, "enchantments"));
                    return SUCCESS;
                })));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("all")
            .executes(ctx -> all(-1))
            .then(level().executes(ctx -> all(IntegerArgumentType.getInteger(ctx, "level")))));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("clear")
            .executes(ctx -> clear()));
        root.then(LiteralArgumentBuilder.<DihCommandSource>literal("remove")
            .then(enchantment().executes(ctx -> remove(StringArgumentType.getString(ctx, "enchantment")))));
        root.then(enchantment()
            .executes(ctx -> enchant(StringArgumentType.getString(ctx, "enchantment"), -1))
            .then(level().executes(ctx -> enchant(StringArgumentType.getString(ctx, "enchantment"),
                IntegerArgumentType.getInteger(ctx, "level")))));
    }

    private static RequiredArgumentBuilder<DihCommandSource, String> enchantment() {
        return RequiredArgumentBuilder.<DihCommandSource, String>argument("enchantment", StringArgumentType.word())
            .suggests(EnchantCommand::suggestEnchantments);
    }

    private static RequiredArgumentBuilder<DihCommandSource, Integer> level() {
        return RequiredArgumentBuilder.<DihCommandSource, Integer>argument("level", IntegerArgumentType.integer(1))
            .suggests((ctx, builder) -> CommandSuggest.literals(builder, "1", "5", "10", "100", "255"));
    }

    private int usage() {
        String p = DihCommands.effectivePrefix();
        DihClientMessaging.sendPrefixed("§eUsage: " + p + "enchant <enchantment> [level] §7| §e" + p + "enchant all [level] §7| §e"
            + p + "enchant remove <enchantment> §7| §e" + p + "enchant clear");
        DihClientMessaging.sendPrefixed("§7Needs creative mode. Levels go up to " + MAX_LEVEL + ". Villager books: §e"
            + p + "enchant plan <enchantments>");
        return SUCCESS;
    }

    private static int enchant(String name, int requested) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;
        Holder<Enchantment> holder = find(registry(mc), name);
        if (holder == null) {
            DihClientMessaging.sendPrefixed("§cUnknown enchantment: §f" + name);
            return SUCCESS;
        }
        warnIfCut(requested);
        int level = levelFor(holder, requested);
        edit(mc, enchantments -> enchantments.set(holder, level),
            "§aAdded " + Enchantment.getFullname(holder, level).getString());
        return SUCCESS;
    }

    private static int all(int requested) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;
        List<Holder.Reference<Enchantment>> every = registry(mc).listElements().toList();
        warnIfCut(requested);
        edit(mc, enchantments -> {
            for (Holder.Reference<Enchantment> holder : every) enchantments.set(holder, levelFor(holder, requested));
        }, "§aAdded all " + every.size() + " enchantments");
        return SUCCESS;
    }

    private static int remove(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;
        Holder<Enchantment> holder = find(registry(mc), name);
        if (holder == null) {
            DihClientMessaging.sendPrefixed("§cUnknown enchantment: §f" + name);
            return SUCCESS;
        }
        edit(mc, enchantments -> enchantments.removeIf(h -> h.unwrapKey().equals(holder.unwrapKey())),
            "§aRemoved " + holder.value().description().getString());
        return SUCCESS;
    }

    private static int clear() {
        Minecraft mc = Minecraft.getInstance();
        if (!ready(mc)) return SUCCESS;
        edit(mc, enchantments -> enchantments.removeIf(h -> true), "§aRemoved all enchantments");
        return SUCCESS;
    }

    /** In a world, really in creative (fake gamemode does not count, the server would refuse), holding an item. */
    private static boolean ready(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            DihClientMessaging.sendPrefixed("§cJoin a world first.");
            return false;
        }
        if (DihFakeGamemode.snapshot().realMode() != GameType.CREATIVE) {
            DihClientMessaging.sendPrefixed("§cCreative mode required.");
            return false;
        }
        if (mc.player.getMainHandItem().isEmpty()) {
            DihClientMessaging.sendPrefixed("§cHold an item first.");
            return false;
        }
        return true;
    }

    /** Edits a copy of the held stack (stored enchantments on a book) and gives it back through the creative slot. */
    private static void edit(Minecraft mc, Consumer<ItemEnchantments.Mutable> change, String done) {
        int slot = mc.player.getInventory().getSelectedSlot();
        ItemStack stack = mc.player.getMainHandItem().copy();
        EnchantmentHelper.updateEnchantments(stack, change);
        mc.getConnection().send(new ServerboundSetCreativeModeSlotPacket((short) (36 + slot), stack));
        mc.player.getInventory().setItem(slot, stack);
        DihClientMessaging.sendPrefixed(done + "§a.");
    }

    /** The requested level cut to what the item format holds, or the enchantment's own top level when none was given. */
    private static int levelFor(Holder<Enchantment> holder, int requested) {
        return clampLevel(requested <= 0 ? holder.value().getMaxLevel() : requested);
    }

    private static void warnIfCut(int requested) {
        if (requested > MAX_LEVEL) {
            DihClientMessaging.sendPrefixed("§7Items store levels up to " + MAX_LEVEL + ", so level " + requested
                + " becomes " + MAX_LEVEL + ".");
        }
    }

    static int clampLevel(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    private static Registry<Enchantment> registry(Minecraft mc) {
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
    }

    /** By path ("sharpness"), vanilla's namespace first, so datapack enchantments work too. */
    private static Holder<Enchantment> find(Registry<Enchantment> registry, String name) {
        String wanted = normalize(name);
        Holder<Enchantment> other = null;
        for (Holder.Reference<Enchantment> holder : registry.listElements().toList()) {
            var id = holder.key().identifier();
            if (!id.getPath().equals(wanted)) continue;
            if ("minecraft".equals(id.getNamespace())) return holder;
            if (other == null) other = holder;
        }
        return other;
    }

    /** "Fire-Aspect" and "fire_aspect" both name fire_aspect. */
    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(CommandContext<DihCommandSource> ctx, SuggestionsBuilder builder) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return builder.buildFuture();
        List<String> paths = new ArrayList<>();
        for (Holder.Reference<Enchantment> holder : registry(mc).listElements().toList()) {
            String path = holder.key().identifier().getPath();
            if (!paths.contains(path)) paths.add(path);
        }
        paths.sort(null);
        return CommandSuggest.literals(builder, paths.toArray(String[]::new));
    }

    private static void plan(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            DihClientMessaging.sendPrefixed("§cJoin a world first.");
            return;
        }
        try {
            var wants = EnchantPlanner.parseWants(Arrays.asList(text.trim().split("\\s+")));
            var villagers = TradeCaches.current().all();
            if (villagers.isEmpty()) {
                DihClientMessaging.sendPrefixed("§7No villager trades seen in this world yet. Open some villagers' trades first.");
                return;
            }
            var plan = EnchantPlanner.plan(wants, villagers, mc.player.getX(), mc.player.getY(), mc.player.getZ());
            DihClientMessaging.sendPrefixed("§eEnchant plan §7(" + villagers.size() + " villagers seen):");
            for (String line : EnchantPlanner.explain(plan)) DihClientMessaging.sendPrefixed("§f" + line);
        } catch (IllegalArgumentException e) {
            DihClientMessaging.sendPrefixed("§c" + e.getMessage() + ".");
        }
    }
}
