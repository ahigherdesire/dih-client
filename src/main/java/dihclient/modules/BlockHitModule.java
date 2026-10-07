package dihclient.modules;

import dihclient.api.module.BoolSetting;
import dihclient.api.module.DoubleSetting;
import dihclient.api.module.IntSetting;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Blocks between hits. Since 1.9 swords cannot block, so this uses whatever can (a shield, or any item a server gives
 * the blocks-attacks component): it holds block while your swing recharges and lets go just before it is ready,
 * because nothing attacks while an item is in use.
 */
public final class BlockHitModule extends Module {
    private boolean holding;
    private int sinceAttack = Integer.MAX_VALUE;

    public BlockHitModule() {
        super("block-hit", "BlockHit", ModuleCategory.COMBAT, "Blocks with your shield between hits.");
        add(new DoubleSetting("unblock-at", "Unblock At", 0.85D, 0.3D, 1.0D, 0.05D)
            .description("Lower the shield once the swing is this charged, ready for the next hit."));
        add(new DoubleSetting("range", "Range", 4.5D, 2.0D, 8.0D, 0.5D).unit("blocks")
            .description("Only block with an enemy player this close."));
        add(new BoolSetting("after-hits", "Only After Hits", true)
            .description("Block only in the fight you started, for a while after each hit."));
        add(new IntSetting("after-hit-time", "After Hit Time", 30, 5, 200, 5).unit("ticks")
            .visibleWhen(() -> bool("after-hits")));
    }

    @Override
    public void onDisable() {
        release();
        sinceAttack = Integer.MAX_VALUE;
    }

    @Override
    public void onGameLeft() {
        holding = false;
    }

    @Override
    public boolean onPacketSend(Packet<?> packet) {
        if (packet instanceof ServerboundAttackPacket) sinceAttack = 0;
        return false;
    }

    @Override
    public void tick() {
        LocalPlayer player = MC.player;
        if (player == null || MC.level == null || MC.options == null) return;
        if (sinceAttack < Integer.MAX_VALUE) sinceAttack++;
        boolean want = MC.gui.screen() == null && canBlock(player) && enemyNear(player)
            && (!bool("after-hits") || sinceAttack <= integer("after-hit-time"))
            && player.getAttackStrengthScale(0.0F) < decimal("unblock-at");
        if (want) {
            MC.options.keyUse.setDown(true);
            holding = true;
        } else {
            release();
        }
    }

    private void release() {
        if (!holding || MC.options == null) return;
        KeyMapping use = MC.options.keyUse;
        use.setDown(DihKeyMappingBridge.of(use).dih$isActuallyDown());
        holding = false;
    }

    /** Use would raise a blocking item: the main hand blocks, or it does nothing and the offhand blocks. */
    private static boolean canBlock(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.has(DataComponents.BLOCKS_ATTACKS)) return true;
        boolean mainUsable = !main.isEmpty() && main.getUseAnimation() != ItemUseAnimation.NONE;
        return !mainUsable && player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
    }

    private boolean enemyNear(LocalPlayer player) {
        double range = decimal("range");
        for (Player other : MC.level.players()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (DihAntiBot.suppress(other) || TeamsModule.isFriendOrTeam(other)) continue;
            if (other.distanceToSqr(player) <= range * range) return true;
        }
        return false;
    }
}
