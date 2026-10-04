package dihclient.modules;

import dihclient.api.module.DoubleSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Raises how far the player can hit entities and reach blocks by adding a client-side modifier to the two
 * interaction-range attributes. The server re-sends attributes on respawn and on changes, which drops the modifier,
 * so it is put back every tick. A vanilla server accepts hits up to its own range plus 3 blocks and block use up to
 * plus 1; plugins and anticheats may check tighter.
 */
public final class ReachModule extends Module {
    static final Identifier MODIFIER_ID = Identifier.fromNamespaceAndPath("dihclient", "reach");

    public ReachModule() {
        super("reach", "Reach", ModuleCategory.COMBAT,
            "Hit entities and reach blocks from further away. Will flag anticheats.");
        add(new DoubleSetting("entity-range", "Entity Range", 3.5D, 3.0D, 6.0D, 0.1D)
            .description("How far you can hit, in blocks (vanilla is 3). Vanilla servers allow up to 6."));
        add(new DoubleSetting("block-range", "Block Range", 4.5D, 4.5D, 5.5D, 0.1D)
            .description("How far you can break and place, in blocks (vanilla is 4.5). Vanilla servers allow up to 5.5."));
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null) return;
        apply(player, Attributes.ENTITY_INTERACTION_RANGE, decimal("entity-range"));
        apply(player, Attributes.BLOCK_INTERACTION_RANGE, decimal("block-range"));
    }

    @Override
    public void onDisable() {
        LocalPlayer player = MC.player;
        if (player == null) return;
        remove(player, Attributes.ENTITY_INTERACTION_RANGE);
        remove(player, Attributes.BLOCK_INTERACTION_RANGE);
    }

    private static void apply(LocalPlayer player, Holder<Attribute> attribute, double wanted) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(MODIFIER_ID);
        double without = instance.getValue() - (current == null ? 0.0D : current.amount());
        double bonus = bonus(without, wanted);
        if (current != null && Math.abs(current.amount() - bonus) < 1.0E-6D) return;
        if (current != null) instance.removeModifier(MODIFIER_ID);
        if (bonus > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void remove(LocalPlayer player, Holder<Attribute> attribute) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getModifier(MODIFIER_ID) != null) instance.removeModifier(MODIFIER_ID);
    }

    /** The modifier that lifts a range of {@code base} to {@code wanted}; never lowers a range the server grants. */
    static double bonus(double base, double wanted) {
        return Math.max(0.0D, wanted - base);
    }
}
