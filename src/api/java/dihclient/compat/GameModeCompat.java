package dihclient.compat;

//? if <26.1 {
/*import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

// Minecraft 1.21.11 only: 26.1 members of MultiPlayerGameMode, injected by Loom at compile time
// (src/compat1211/resources/fabric.mod.json) and added at runtime by DihGameModeCompatMixin.
public interface GameModeCompat {
    // 26.1 merged interactAt into interact: try the hit position first, then the entity, as vanilla does.
    default InteractionResult interact(Player player, Entity entity, EntityHitResult hit, InteractionHand hand) {
        MultiPlayerGameMode self = (MultiPlayerGameMode) (Object) this;
        InteractionResult result = self.interactAt(player, entity, hit, hand);
        return result.consumesAction() ? result : self.interact(player, entity, hand);
    }
}
*///?} else {
/** Minecraft 1.21.11 only (see the other branch). */
public interface GameModeCompat {
}
//?}
