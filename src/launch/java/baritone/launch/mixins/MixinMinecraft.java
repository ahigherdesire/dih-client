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

package baritone.launch.mixins;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.TickEvent;
import baritone.api.event.events.WorldEvent;
import baritone.api.event.events.type.EventState;
import baritone.cache.ChestMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiFunction;

/**
 * @author Brady
 * @since 7/31/2018
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    public LocalPlayer player;
    @Shadow
    public ClientLevel level;

    @Unique
    private BiFunction<EventState, TickEvent.Type, TickEvent> tickProvider;

    @Inject(
            method = "<init>",
            at = @At("RETURN")
    )
    private void postInit(CallbackInfo ci) {
        BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    // MC 26.2: Minecraft.tick() no longer reads the current screen via GETFIELD Minecraft.screen
    // (it now calls Gui.screen()), so the old GETFIELD injection point vanished. This is THE hook
    // that drives Baritone every tick — with it unbound, all processes (mine, follow, goto, farm,
    // explore, elytra…) would still print their start message but never actually run, because the
    // tick loop never reached them. Re-anchor to the call of Minecraft.handleKeybinds(): it is the
    // same program point (right before vanilla processes input, and well before LocalPlayer.tick()
    // ticks the ClientInput we override), it occurs exactly once in tick(), and slicing from the
    // missTime store keeps it in the original region. Keep this required (no require = 0): if it
    // ever drifts again it must fail loudly, not silently disable all of Baritone like this did.
    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;handleKeybinds()V"
            ),
            slice = @Slice(
                    from = @At(
                            value = "FIELD",
                            opcode = Opcodes.PUTFIELD,
                            target = "net/minecraft/client/Minecraft.missTime:I"
                    )
            )
    )
    private void runTick(CallbackInfo ci) {
        this.tickProvider = TickEvent.createNextProvider();

        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            TickEvent.Type type = baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            baritone.getGameEventHandler().onTick(this.tickProvider.apply(EventState.PRE, type));
        }
    }

    @Inject(
            method = "tick",
            at = @At("RETURN")
    )
    private void postRunTick(CallbackInfo ci) {
        if (this.tickProvider == null) {
            return;
        }

        for (IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones()) {
            TickEvent.Type type = baritone.getPlayerContext().player() != null && baritone.getPlayerContext().world() != null
                    ? TickEvent.Type.IN
                    : TickEvent.Type.OUT;
            baritone.getGameEventHandler().onPostTick(this.tickProvider.apply(EventState.POST, type));
        }

        this.tickProvider = null;
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/multiplayer/ClientLevel.tickEntities()V",
                    shift = At.Shift.AFTER
            )
    )
    private void postUpdateEntities(CallbackInfo ci) {
        IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(this.player);
        if (baritone != null) {
            // Intentionally call this after all entities have been updated. That way, any modification to rotations
            // can be recognized by other entity code. (Fireworks and Pigs, for example)
            baritone.getGameEventHandler().onPlayerUpdate(new PlayerUpdateEvent(EventState.POST));
        }
    }

    @Inject(
            method = "setLevel",
            at = @At("HEAD")
    )
    private void preLoadWorld(final ClientLevel world, final CallbackInfo ci) {
        // If we're unloading the world but one doesn't exist, ignore it
        if (this.level == null && world == null) {
            return;
        }

        // mc.world changing is only the primary baritone

        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                new WorldEvent(
                        world,
                        EventState.PRE
                )
        );
    }

    @Inject(
            method = "setLevel",
            at = @At("RETURN")
    )
    private void postLoadWorld(final ClientLevel world, final CallbackInfo ci) {
        // still fire event for both null, as that means we've just finished exiting a world

        // mc.world changing is only the primary baritone
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onWorldEvent(
                new WorldEvent(
                        world,
                        EventState.POST
                )
        );
    }

    // MC 26.2: the current screen moved from the Minecraft.screen field to Gui.screen(), so
    // Minecraft.tick() now reads it via an INVOKE of Gui.screen() instead of a GETFIELD. The input
    // slice reads it right before the "Keybindings" profiler constant; disambiguate from the earlier
    // Gui.screen() call (near Gui.tick()) by slicing from Gui.overlay(). require = 0 keeps the mod
    // loadable if this injection point drifts again — the feature just no-ops until re-derived.
    @Redirect(
            method = "tick",
            require = 0,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;screen()Lnet/minecraft/client/gui/screens/Screen;"
            ),
            slice = @Slice(
                    from = @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/Gui;overlay()Lnet/minecraft/client/gui/screens/Overlay;"
                    ),
                    to = @At(
                            value = "CONSTANT",
                            args = "stringValue=Keybindings"
                    )
            )
    )
    private Screen passEvents(Gui instance) {
        // allow user input is only the primary baritone
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && player != null) {
            return null;
        }
        return instance.screen();
    }

    // ── Chest content memory ─────────────────────────────────────────────────
    //
    // Hook into Minecraft.setScreen(Screen) to detect when a container UI
    // opens and closes.  When a container opens we capture the BlockPos the
    // player is looking at (mc.hitResult at that moment = the block just
    // right-clicked).  When the container closes we read the slots and write
    // the record to disk via ChestMemory.
    //
    // require = 0 means: if the method is renamed in a future MC version, the
    // mod still loads — chest memory simply doesn't capture anything until the
    // mixin is updated.

    // MC 26.2: Minecraft.setScreen(Screen) was renamed to setScreenAndShow(Screen), and the current
    // screen moved to Gui.screen().
    @Inject(method = "setScreenAndShow", at = @At("HEAD"), require = 0)
    private void onSetScreen(Screen newScreen, CallbackInfo ci) {
        Minecraft mc  = Minecraft.getInstance();
        Screen    old = mc.gui.screen();

        boolean oldIsContainer = old instanceof AbstractContainerScreen<?>;
        boolean newIsContainer = newScreen instanceof AbstractContainerScreen<?>;

        if (!oldIsContainer && newIsContainer) {
            // A container screen just opened.
            // mc.hitResult is still pointing at the block the player right-clicked
            // (raycasting is suspended while a screen is open, so hitResult stays
            // frozen from the moment the player interacted with the block).
            if (mc.hitResult instanceof BlockHitResult bhr) {
                ChestMemory.onContainerOpen(bhr.getBlockPos());
            }

        } else if (oldIsContainer && !newIsContainer) {
            // A container screen is closing — record its contents.
            AbstractContainerScreen<?> prev = (AbstractContainerScreen<?>) old;
            if (mc.level != null) {
                String dim = mc.level.dimension().identifier().toString();
                ChestMemory.onContainerClose(prev.getMenu(), dim);
            }
        }
    }

    // TODO
    // FIXME
    // bradyfix
    // i cant mixin
    // lol
    // https://discordapp.com/channels/208753003996512258/503692253881958400/674760939681349652
    // https://discordapp.com/channels/208753003996512258/503692253881958400/674756457966862376
    /*@Inject(
            method = "rightClickMouse",
            at = @At(
                    value = "INVOKE",
                    target = "net/minecraft/client/entity/player/ClientPlayerEntity.swingArm(Lnet/minecraft/util/Hand;)V",
                    ordinal = 1
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onBlockUse(CallbackInfo ci, Hand var1[], int var2, int var3, Hand enumhand, ItemStack itemstack, EntityRayTraceResult rt, Entity ent, ActionResultType art, BlockRayTraceResult raytrace, int i, ActionResultType enumactionresult) {
        // rightClickMouse is only for the main player
        BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler().onBlockInteract(new BlockInteractEvent(raytrace.getPos(), BlockInteractEvent.Type.USE));
    }*/
}
