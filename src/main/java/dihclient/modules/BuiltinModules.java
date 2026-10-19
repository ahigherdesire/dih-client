package dihclient.modules;
import dihclient.util.DihFileDialogs;
import dihclient.util.DihItems;
import dihclient.util.DihBlocks;
import dihclient.util.DihEntities;
import dihclient.util.DihPackets;
import dihclient.api.module.*;

import dihclient.util.DihClientMessaging;
import dihclient.util.DihBookPayloadBuilder;
import dihclient.util.DihBookFileReader;
import dihclient.util.DihConfig;
import dihclient.util.DihFakeCoords;
import dihclient.util.DihInputGate;
import dihclient.util.AutoFishStopMacroFactory;
import dihclient.util.DihInstaBreakRenderer;
import dihclient.util.DihPacketRegistry;
import dihclient.util.DihBindUtil;
import dihclient.util.DihHudManager;
import dihclient.util.DihHumanRng;
import dihclient.util.DihInputClicker;
import dihclient.util.DihInventoryHelper;
import dihclient.util.DihHandArbiter;
import dihclient.util.DihKillAuraRotation;
import dihclient.util.DihPlacementTick;
import dihclient.security.DihItemNbtSanity;
import dihclient.util.DihItemNbtInspector;
import dihclient.util.DihItemCommandSerializer;
import dihclient.util.DihKeyMappingBridge;
import dihclient.util.DihNotifications;
import dihclient.util.DihWaypoints;
import dihclient.util.oresim.DihOreGhostModels;
import dihclient.util.oresim.DihOreSimEngine;
import dihclient.util.oresim.DihOreSimOre;
import dihclient.util.oresim.DihOreSimSeedStore;
import dihclient.util.DihMouseInputSimulator;
import dihclient.util.QuantizedRotationSmoother;
import dihclient.util.DihRotationUtil;
import dihclient.gui.screen.DihHudEditorScreen;
import dihclient.util.PacketListCodec;
import dihclient.mixin.accessor.DihFishingHookAccessor;
import dihclient.mixin.accessor.DihMinecraftAccessor;
import dihclient.mixin.accessor.DihMobEffectInstanceAccessor;
import dihclient.mixin.accessor.DihLocalPlayerAccessor;
import dihclient.mixin.accessor.DihMovePlayerPacketAccessor;
import dihclient.mixin.accessor.DihMultiPlayerGameModeAccessor;
import dihclient.util.DihAutoTool;
import dihclient.util.DihSharedState;
import dihclient.util.DihSilentAim;
import dihclient.util.RegistryListCodec;
import net.minecraft.world.level.block.state.BlockState;
import dihclient.util.macro.ServerTickTracker;
import dihclient.util.macro.MacroAction;
import dihclient.util.macro.MacroActionType;
import dihclient.util.macro.MacroConditionUtil;
import dihclient.util.macro.MacroExecutor;
import dihclient.util.macro.NbtBookAction;
import dihclient.util.macro.WaitDurabilityAction;
import dihclient.util.macro.WaitFreeSlotsAction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Unit;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

public final class BuiltinModules {
    private static FastUseModule fastUseInstance;

    private BuiltinModules() {
    }

    static void register() {
        migrateUtilityModuleState("inv-move", DihConfig.getGlobal().inventoryMove);
        migrateUtilityModuleState("xcarry", DihConfig.getGlobal().xCarry);
        migrateUtilityModuleState("golden-lever", true);

        if (dihclient.util.DihLiteVariant.enabled()) {
            ModuleRegistry.register(new HideModule());
            return;
        }
        ModuleRegistry.register(new FlightModule());
        ModuleRegistry.register(new SprintModule());
        ModuleRegistry.register(new ParkourModule());
        ModuleRegistry.register(new SpeedModule());
        ModuleRegistry.register(new BoatFlyModule());
        ModuleRegistry.register(new EntityControlModule());
        ModuleRegistry.register(new InvMoveModule());
        ModuleRegistry.register(new SneakModule());
        ModuleRegistry.register(new AirJumpModule());
        ModuleRegistry.register(new NoClipModule());
        ModuleRegistry.register(new PhaseModule());
        ModuleRegistry.register(new WaypointsModule());
        ModuleRegistry.register(new FastUseModule());
        ModuleRegistry.register(new FastBreakModule());
        ModuleRegistry.register(new AimAssistModule());
        ModuleRegistry.register(new AutoClickerModule());
        ModuleRegistry.register(new TriggerBotModule());

        ModuleRegistry.register(new AutoTotemModule());
        ModuleRegistry.register(new BedDefenderModule());
        ModuleRegistry.register(new BedEspModule());
        ModuleRegistry.register(new DangerModule());
        ModuleRegistry.register(new SurroundModule());
        ModuleRegistry.register(new AnchorAuraModule());
        ModuleRegistry.register(new CrystalAuraModule());
        ModuleRegistry.register(new AutoTrapModule());
        ModuleRegistry.register(new KillAuraModule());
        ModuleRegistry.register(new MaceKillModule());
        ModuleRegistry.register(new SpearKillModule());
        ModuleRegistry.register(new ArrowDamageModule());
        ModuleRegistry.register(new CriticalsModule());
        ModuleRegistry.register(new ReachModule());
        ModuleRegistry.register(new AutoFarmModule());
        ModuleRegistry.register(new AutoArmorModule());
        ModuleRegistry.register(new AutoSignModule());
        ModuleRegistry.register(new AutoLoginModule());
        ModuleRegistry.register(new TpClickModule());
        migrateUtilityModuleState("antibot", true);
        ModuleRegistry.register(new AntiBotModule());
        ModuleRegistry.register(new AntiVanishModule());
        ModuleRegistry.register(new AutoFishModule());
        ModuleRegistry.register(new InstantRebreakModule());
        ModuleRegistry.register(new AutoToolModule());
        ModuleRegistry.register(new ScaffoldModule());
        ModuleRegistry.register(new NoFallModule());
        ModuleRegistry.register(new ClutchModule());
        ModuleRegistry.register(new NoInteractModule());
        ModuleRegistry.register(new AirPlaceModule());
        ModuleRegistry.register(new GhostBlockModule());
        ModuleRegistry.register(new OffhandInteractModule());
        ModuleRegistry.register(new SpamModule());
        ModuleRegistry.register(new PayAllModule());
        ModuleRegistry.register(new AutoTradeModule());
        ModuleRegistry.register(new BookBotModule());
        ModuleRegistry.register(new PacketCancellerModule());
        ModuleRegistry.register(new AutoReconnectModule());
        ModuleRegistry.register(new FleeModule());
        ModuleRegistry.register(new HideModule());
        ModuleRegistry.register(new InventoryTweaksModule());
        ModuleRegistry.register(new AntiHungerModule());
        ModuleRegistry.register(new SafeWalkModule());
        ModuleRegistry.register(new SpawnerEspModule());
        ModuleRegistry.register(new BetterTooltipsModule());
        ModuleRegistry.register(new FullbrightModule());
        ModuleRegistry.register(new FakeCoordsModule());
        ModuleRegistry.register(new XrayModule());
        ModuleRegistry.register(new WorldModule());
        ModuleRegistry.register(new ViewmodelModule());
        ModuleRegistry.register(new FreecamModule());
        ModuleRegistry.register(new FreeLookModule());
        ModuleRegistry.register(new EspModule());
        ModuleRegistry.register(new ChamsModule());
        ModuleRegistry.register(new ItemEspModule());
        ModuleRegistry.register(new NameTagsModule());
        ModuleRegistry.register(new TracersModule());
        ModuleRegistry.register(new TrajectoriesModule());
        ModuleRegistry.register(new StorageEspModule());
        ModuleRegistry.register(new BlockEspModule());
        ModuleRegistry.register(new HoleEspModule());
        ModuleRegistry.register(new NoRenderModule());
        ModuleRegistry.register(new XCarryModule());
        ModuleRegistry.register(new HudModule());
        ModuleRegistry.register(new GifModule());
        ModuleRegistry.register(new AdminToolsModule());
        ModuleRegistry.register(new GoldenLeverModule());
        ModuleRegistry.register(new NameCensorModule());
        migrateUtilityModuleState("teams", true);
        ModuleRegistry.register(new TeamsModule());
        ModuleRegistry.register(new PingSpoofModule());
        ModuleRegistry.register(new BlinkModule());
        // Ported from LiquidBounce (GPL-3.0, CCBlueX).
        ModuleRegistry.register(new RadarModule());
        ModuleRegistry.register(new ZoomModule());
        ModuleRegistry.register(new NewChunksModule());
        ModuleRegistry.register(new LogoffSpotModule());
        ModuleRegistry.register(new BreadcrumbsModule());
        ModuleRegistry.register(new AutoRespawnModule());
        ModuleRegistry.register(new AntiAfkModule());
        ModuleRegistry.register(new SmartEatModule());
        ModuleRegistry.register(new BetterChatModule());
        ModuleRegistry.register(new NukerModule());
        ModuleRegistry.register(new PacketMineModule());
        ModuleRegistry.register(new WTapModule());
        ModuleRegistry.register(new STapModule());
        ModuleRegistry.register(new BlockHitModule());
        ModuleRegistry.register(new BowAimAssistModule());
        ModuleRegistry.register(new AutoMaceModule());
    }

    private static void migrateUtilityModuleState(String id, boolean enabled) {
        DihConfig config = DihConfig.getGlobal();
        if (config.modules.containsKey(id)) return;
        DihConfig.ModuleState state = config.modules.computeIfAbsent(id, ignored -> new DihConfig.ModuleState());
        state.enabled = enabled;
    }

    public static boolean ownsManualFastUse() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.ownsManualInput();
    }

    public static boolean ownsManualFastExp() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.manualExpActive;
    }

    public static boolean beginManualFastUseClick() {
        FastUseModule fastUse = activeFastUse();
        return fastUse != null && fastUse.beginManualInputClick();
    }

    public static float manualFastExpUseYaw(float vanillaYaw) {
        if (!DihInputClicker.isFastExpUseInProgress()) return vanillaYaw;
        FastUseModule fastUse = activeFastUse();
        DihRotationUtil.Rotation rotation = fastUse == null ? null : fastUse.activeExpRotation();
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float manualFastExpUsePitch(float vanillaPitch) {
        if (!DihInputClicker.isFastExpUseInProgress()) return vanillaPitch;
        FastUseModule fastUse = activeFastUse();
        DihRotationUtil.Rotation rotation = fastUse == null ? null : fastUse.activeExpRotation();
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    public static float outgoingFastExpMovementYaw(net.minecraft.client.player.LocalPlayer player, float vanillaYaw) {
        DihRotationUtil.Rotation rotation = activeFastExpRotation(player);
        return rotation == null ? vanillaYaw : rotation.yaw();
    }

    public static float outgoingFastExpMovementPitch(net.minecraft.client.player.LocalPlayer player, float vanillaPitch) {
        DihRotationUtil.Rotation rotation = activeFastExpRotation(player);
        return rotation == null ? vanillaPitch : rotation.pitch();
    }

    private static DihRotationUtil.Rotation activeFastExpRotation(net.minecraft.client.player.LocalPlayer player) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || client == null || player != client.player) return null;
        FastUseModule fastUse = activeFastUse();
        return fastUse == null ? null : fastUse.activeExpRotation();
    }

    private static FastUseModule activeFastUse() {
        FastUseModule fastUse = fastUseInstance;
        return fastUse != null && fastUse.isEnabled() ? fastUse : null;
    }

    static final class HideModule extends Module {
        HideModule() {
            super(PackHideState.HIDE_ID, "PanicMode", ModuleCategory.MISC, "Temporarily hides client modules and UI.");
        }

        @Override
        public void onEnable() {
            PackHideState.enable(this);
            dih$refreshDisguise();
        }

        @Override
        public void onDisable() {
            PackHideState.disableAndRestore(this);
            dih$refreshDisguise();
        }

        private void dih$refreshDisguise() {
            if (MC == null) return;

            dihclient.util.DihWindowBranding.refresh(MC);

            if (!dihclient.util.DihLiteVariant.enabled()
                    && MC.gui.screen() instanceof dihclient.gui.screen.DihTitleScreen) {
                MC.gui.setScreen(new dihclient.gui.screen.DihTitleScreen());
            }
        }

        @Override
        public boolean emitsToggleMessage() {
            return false;
        }
    }

    static final class InvMoveModule extends Module {
        InvMoveModule() {
            super("inv-move", "InvMove", ModuleCategory.MOVEMENT, "Allows movement keys in normal container screens.");
            add(new BoolSetting("arrow-look", "Arrow Keys Look", false)
                .group("General")
                .description("Turns your view with the arrow keys while a screen is open")
                .build());
            add(new IntSetting("look-speed", "Look Speed", 5, 1, 20, 1)
                .group("General")
                .unit("deg")
                .description("Degrees turned per tick by the arrow keys")
                .visibleWhen(() -> bool("arrow-look"))
                .build());

            add(new BoolSetting("jump", "Jump", true)
                .group("Keys")
                .description("Lets the jump key work in screens")
                .build());
            add(new BoolSetting("sneak", "Sneak", true)
                .group("Keys")
                .description("Lets the sneak key work in screens")
                .build());
            add(new BoolSetting("sprint", "Sprint", true)
                .group("Keys")
                .description("Lets the sprint key work in screens")
                .build());

            add(new BoolSetting("player-inventory", "Player Inventory", true)
                .group("Screens")
                .description("Works in your own and the creative inventory")
                .build());
            add(new BoolSetting("containers", "Containers", true)
                .group("Screens")
                .description("Works in chests, furnaces and other block screens")
                .build());
        }

        @Override
        public void onEnable() {
            DihConfig.getGlobal().inventoryMove = true;
        }

        @Override
        public void onDisable() {
            DihConfig.getGlobal().inventoryMove = false;
        }

        @Override
        public void tick() {
            dihclient.util.DihInventoryMoveHelper.syncHeldMovementKeysIfSafe();
            if (bool("arrow-look")) arrowLook();
        }

        private void arrowLook() {
            if (MC.player == null || MC.gui.screen() == null) return;
            if (!dihclient.util.DihInventoryMoveHelper.handlesCurrentScreen()) return;
            float step = Math.max(1, integer("look-speed"));
            float yaw = 0.0F;
            float pitch = 0.0F;
            if (dihclient.util.DihKeys.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LEFT)) yaw -= step;
            if (dihclient.util.DihKeys.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT)) yaw += step;
            if (dihclient.util.DihKeys.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_UP)) pitch -= step;
            if (dihclient.util.DihKeys.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_DOWN)) pitch += step;
            if (yaw == 0.0F && pitch == 0.0F) return;
            MC.player.setYRot(MC.player.getYRot() + yaw);
            MC.player.setXRot(Mth.clamp(MC.player.getXRot() + pitch, -90.0F, 90.0F));
        }
    }

    static final class XCarryModule extends Module {
        /** Crafting grid slots of the player's inventory menu. */
        private static final int[] GRID_SLOTS = {1, 2, 3, 4};

        private boolean inventoryWasOpen;

        XCarryModule() {
            super("xcarry", "XCarry", ModuleCategory.PLAYER, "Keeps stored items in crafting / result / armor / offhand slots when closing inventory.");
            add(new BoolSetting("use-crafting", "Use Crafting Grid", true)
                .group("Slots")
                .description("Use all craft slots").build());
            add(new BoolSetting("use-armor",    "Use Armor Slots", true)
                .group("Slots")
                .description("Use armor slots 5-8.").build());
            add(new BoolSetting("use-offhand",  "Use Offhand", true)
                .group("Slots")
                .description("Use offhand slot.").build());
            add(new BoolSetting("carry-cursor", "Carry Cursor", true)
                .group("Slots")
                .description("Keep cursor stack.").build());

            add(new BoolSetting("notify", "Notify", false)
                .group("General")
                .description("Says in chat how many stacks you carry when you close your inventory").build());
            add(new BoolSetting("show-count", "Show Count", true)
                .group("General")
                .description("Shows the carried crafting-grid stacks next to the module name").build());
            add(new BoolSetting("empty-on-disable", "Empty On Disable", false)
                .group("General")
                .description("Moves crafting-grid items back into your inventory when turned off").build());
        }

        @Override
        public void onEnable() {
            DihConfig.getGlobal().xCarry = true;
            inventoryWasOpen = false;
        }

        @Override
        public void onDisable() {
            if (bool("empty-on-disable")) emptyGrid();
            DihConfig.getGlobal().xCarry = false;
        }

        @Override
        public void tick() {
            boolean open = MC.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
            if (inventoryWasOpen && !open && bool("notify")) {
                int carried = carriedStacks();
                if (carried > 0) {
                    dihclient.util.DihClientMessaging.sendPrefixed("XCarry: carrying " + carried + " stack"
                        + (carried == 1 ? "" : "s") + ".");
                }
            }
            inventoryWasOpen = open;
        }

        @Override
        public String info() {
            if (!bool("show-count")) return "";
            int carried = carriedStacks();
            return carried > 0 ? Integer.toString(carried) : "";
        }

        /** Stacks sitting in the crafting grid, which a normal inventory close would drop. */
        private int carriedStacks() {
            if (MC.player == null || MC.player.inventoryMenu == null) return 0;
            net.minecraft.world.inventory.InventoryMenu menu = MC.player.inventoryMenu;
            int count = 0;
            for (int slot : GRID_SLOTS) {
                if (slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty()) count++;
            }
            return count;
        }

        private void emptyGrid() {
            if (MC.player == null || MC.gameMode == null || MC.player.inventoryMenu == null) return;
            if (MC.gui.screen() != null
                && !(MC.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) return;
            net.minecraft.world.inventory.InventoryMenu menu = MC.player.inventoryMenu;
            for (int slot : GRID_SLOTS) {
                if (slot >= menu.slots.size() || menu.slots.get(slot).getItem().isEmpty()) continue;
                MC.gameMode.handleContainerInput(menu.containerId, slot, 0,
                    net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, MC.player);
            }
        }
    }

    static final class FlightModule extends Module {
        private int delayLeft;
        private int offLeft;
        private double lastPacketY = Double.MAX_VALUE;
        private boolean touchedAbilities;
        private String lastMode = "Abilities";
        private double vulcanTargetY;
        private int vulcanJumpCooldown;
        private int vulcanGlideRequestCooldown;
        private boolean vulcanGlideRequestedThisAir;
        private Boolean meteorFlightActiveCache;
        private long meteorFlightActiveCacheAt;
        private int cachedSettingsRevision = Integer.MIN_VALUE;
        private String cachedMode = "Abilities";
        private String cachedAntiKickMode = "Packet";
        private double cachedSpeed = 0.10;
        private boolean cachedVerticalSpeedMatch;
        private boolean cachedNoSneak;
        private int cachedDelay = 20;
        private int cachedOffTime = 1;
        private long cachedAirTick = Long.MIN_VALUE;
        private boolean cachedOnAir;
        private boolean sendingAntiKickReplacement;
        private boolean vulcanNoElytraDisableQueued;
        private double cachedSprintBoost = 1.5;
        private double cachedGlideSpeed;
        private boolean cachedStopInstantly;
        private double cachedNudge = 0.03130D;
        private volatile boolean setbackPending;

        FlightModule() {
            super("flight", "Flight", ModuleCategory.MOVEMENT, "Client-side flight.");
            add(new ChoiceSetting("mode", "Mode", "Abilities", "Abilities", "Velocity", "Vulcan").group("General").description("Flight method").build());
            add(new DoubleSetting("speed", "Speed", 0.10, 0.0, 2.0, 0.01).group("General").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Your speed when flying.").build());
            add(new DoubleSetting("sprint-boost", "Sprint Boost", 1.5, 1.0, 3.0, 0.05).group("General")
                .visibleWhen(() -> "Velocity".equals(value("mode")))
                .description("Speed multiplier while sprinting").build());
            add(new BoolSetting("vertical-speed-match", "Vertical Speed Match", false).group("General").description("Rise and sink twice as fast, matching horizontal speed").visibleWhen(() -> "Velocity".equals(value("mode"))).build());
            add(new DoubleSetting("glide-speed", "Glide Speed", 0.0, 0.0, 1.0, 0.01).group("General")
                .visibleWhen(() -> "Velocity".equals(value("mode")))
                .description("Sinks this many blocks per tick when you press neither jump nor sneak").build());
            add(new BoolSetting("no-sneak", "No Sneak", false).group("General").description("Ignore sneak input.").visibleWhen(() -> "Velocity".equals(value("mode"))).build());
            add(new BoolSetting("stop-instantly", "Stop Instantly", false).group("General")
                .visibleWhen(() -> "Abilities".equals(value("mode")))
                .description("Stops dead when you let go of the keys instead of drifting").build());
            add(new BoolSetting("anti-hunger", "AntiHunger", false).group("General")
                .description("Hide sprint from server.").build());
            add(new BoolSetting("stop-on-disable", "Stop On Disable", false).group("General")
                .description("Clears your momentum when Flight turns off").build());

            add(new BoolSetting("anti-break-elytra", "Anti Break Elytra", true).group("Vulcan").visibleWhen(() -> "Vulcan".equals(value("mode"))).description("Preserve low-durability elytra.").build());
            add(new IntSetting("min-durability", "Min Durability", 40, 1, 200, 1).group("Vulcan")
                .visibleWhen(() -> "Vulcan".equals(value("mode")) && bool("anti-break-elytra"))
                .description("Swaps out an elytra with this little durability left (or under 10%)").build());

            add(new ChoiceSetting("anti-kick-mode", "Anti Kick", "Packet", "Normal", "Packet", "None").group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Reduce floating kicks").build());
            add(new IntSetting("delay", "Delay", 20, 1, 200, 1).group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Ticks between anti-kick nudges.").build());
            add(new IntSetting("off-time", "Off Time", 1, 1, 20, 1).group("Anti Kick").visibleWhen(() -> !"Vulcan".equals(value("mode"))).description("Ticks spent nudging down.").build());
            add(new DoubleSetting("nudge", "Nudge Distance", 0.0313, 0.01, 0.2, 0.0001).group("Anti Kick")
                .visibleWhen(() -> "Packet".equals(value("anti-kick-mode")) && !"Vulcan".equals(value("mode")))
                .description("How far each anti-kick packet drops you, in blocks").build());

            add(new BoolSetting("disable-on-setback", "Disable On Setback", false).group("Conditions")
                .description("Turns Flight off when the server teleports you back").build());
        }

        @Override
        public void onEnable() {
            updateFlightCache();
            resetFlightState();
            lastMode = cachedMode;
            vulcanNoElytraDisableQueued = false;
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if ("Vulcan".equals(lastMode)) {
                if (!ensureVulcanElytra()) {
                    disableVulcanNoElytra(true);
                    return;
                }
                vulcanTargetY = MC.player.getY();
                boolean airborne = !MC.player.onGround();
                vulcanJumpCooldown = airborne ? 10 : 0;
                vulcanGlideRequestCooldown = 0;
                vulcanGlideRequestedThisAir = MC.player.isFallFlying();
                if (airborne) requestVulcanGlide();
                else vulcanJump();
                return;
            }
            if ("Abilities".equals(lastMode)) abilitiesOn();
        }

        @Override
        public void tick() {
            if (setbackPending) {
                setbackPending = false;
                disableWithToggleMessage("Flight disabled: server set you back.");
                return;
            }
            updateFlightCache();
            if (MC.player == null || shouldYieldToMeteorFlight() || "Vulcan".equals(cachedMode)) return;
        }

        @Override
        public void preMovementTick() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.gameMode == null || MC.player.isSpectator() || shouldYieldToMeteorFlight()) return;
            if (DihJoinGrace.isMovementPaused()) {

                if (touchedAbilities) abilitiesOff();
                return;
            }
            updateFlightCache();
            String mode = cachedMode;
            syncModeChange(mode);
            if ("Vulcan".equals(mode)) {
                tickVulcan();
                return;
            }

            if (delayLeft > 0) delayLeft--;
            if (offLeft <= 0 && delayLeft <= 0) {
                delayLeft = cachedDelay;
                offLeft = cachedOffTime;
                if (usesPacketAntiKick()) ((DihLocalPlayerAccessor) MC.player).dih$setPositionReminder(20);
            } else if (delayLeft <= 0) {
                boolean shouldReturn = false;
                if ("Normal".equals(cachedAntiKickMode) && "Abilities".equals(mode)) {
                    abilitiesOff();
                    shouldReturn = true;
                } else if (usesPacketAntiKick() && offLeft == cachedOffTime) {
                    ((DihLocalPlayerAccessor) MC.player).dih$setPositionReminder(20);
                }
                offLeft--;
                if (shouldReturn) return;
            }

            if ("Velocity".equals(mode)) {

                MC.player.getAbilities().flying = false;
                MC.player.setDeltaMovement(Vec3.ZERO);
                Vec3 velocity = MC.player.getDeltaMovement();
                double vertical = cachedSpeed * (cachedVerticalSpeedMatch ? 10.0 : 5.0);
                boolean rise = MC.options.keyJump.isDown();
                boolean sink = MC.options.keyShift.isDown();
                if (rise) velocity = velocity.add(0.0, vertical, 0.0);
                if (sink) velocity = velocity.subtract(0.0, vertical, 0.0);
                if (!rise && !sink && cachedGlideSpeed > 0.0) velocity = velocity.subtract(0.0, cachedGlideSpeed, 0.0);
                MC.player.setDeltaMovement(velocity);
                if (cachedNoSneak) MC.player.setOnGround(false);
            } else if ("Abilities".equals(mode)) {

                if (MC.player.isSpectator()) return;
                MC.player.getAbilities().setFlyingSpeed((float) cachedSpeed);
                MC.player.getAbilities().flying = true;
                touchedAbilities = true;
                if (!MC.player.getAbilities().instabuild) {
                    MC.player.getAbilities().mayfly = true;
                }
                if (cachedStopInstantly) stopDrift();
            }
        }

        private void stopDrift() {
            Vec3 velocity = MC.player.getDeltaMovement();
            boolean horizontalInput = MC.player.input.getMoveVector().lengthSquared() > 0.0F;
            boolean verticalInput = MC.options.keyJump.isDown() || MC.options.keyShift.isDown();
            MC.player.setDeltaMovement(horizontalInput ? velocity.x : 0.0, verticalInput ? velocity.y : 0.0,
                horizontalInput ? velocity.z : 0.0);
        }

        @Override
        public void onDisable() {
            if (dihclient.util.multi.MultiPilot.isActive()) {
                resetFlightState();
                touchedAbilities = false;
                return;
            }
            boolean wasVulcan = "Vulcan".equals(lastMode) || "Vulcan".equals(cachedMode);
            if (wasVulcan) {
                stopVulcanGlideState();
            }
            resetFlightState();
            setbackPending = false;
            if (MC.player == null || MC.player.isSpectator()) return;
            if (touchedAbilities || "Abilities".equals(lastMode)) abilitiesOff();
            if (bool("stop-on-disable")) MC.player.setDeltaMovement(Vec3.ZERO);
        }

        @Override
        public void onGameLeft() {
            resetFlightState();
            sendingAntiKickReplacement = false;
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (dihclient.util.multi.MultiPilot.isActive()) return false;
            if (sendingAntiKickReplacement) return false;
            if (DihJoinGrace.isMovementPaused()) return false;
            if (MC.player == null || MC.getConnection() == null || shouldYieldToMeteorFlight()) return false;
            updateFlightCache();
            if ("Vulcan".equals(cachedMode)) return false;
            if (!usesPacketAntiKick() || !(packet instanceof ServerboundMovePlayerPacket move)) return false;
            double currentY = move.getY(Double.MAX_VALUE);
            if (currentY != Double.MAX_VALUE) {
                antiKickPacket(move, currentY);
                return false;
            }

            ServerboundMovePlayerPacket fullPacket = move.hasRotation()
                ? new ServerboundMovePlayerPacket.PosRot(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(),
                    move.getYRot(0.0f), move.getXRot(0.0f), move.isOnGround(), MC.player.horizontalCollision
                )
                : new ServerboundMovePlayerPacket.Pos(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(), move.isOnGround(), MC.player.horizontalCollision
                );
            antiKickPacket(fullPacket, MC.player.getY());
            sendingAntiKickReplacement = true;
            try {
                MC.getConnection().send(fullPacket);
            } finally {
                sendingAntiKickReplacement = false;
            }
            return true;
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (dihclient.util.multi.MultiPilot.isActive()) return false;
            if (MC.player == null || shouldYieldToMeteorFlight()) return false;
            if (packet instanceof ClientboundPlayerPositionPacket && bool("disable-on-setback")) setbackPending = true;
            updateFlightCache();
            if (!(packet instanceof ClientboundPlayerAbilitiesPacket abilities) || !"Abilities".equals(cachedMode)) return false;
            MC.player.getAbilities().invulnerable = abilities.isInvulnerable();
            MC.player.getAbilities().instabuild = abilities.canInstabuild();
            MC.player.getAbilities().setWalkingSpeed(abilities.getWalkingSpeed());
            return true;
        }

        float getFlyingSpeed() {
            if (DihJoinGrace.isMovementPaused()) return -1.0f;
            updateFlightCache();
            if (!isEnabled() || shouldYieldToMeteorFlight() || !"Velocity".equals(cachedMode)) return -1.0f;

            return (float) (cachedSpeed * 10.0 * (MC.player != null && MC.player.isSprinting() ? cachedSprintBoost : 1.0));
        }

        boolean antiHungerToggle() {
            return bool("anti-hunger");
        }

        boolean noSneak() {
            if (DihJoinGrace.isMovementPaused()) return false;
            updateFlightCache();
            return isEnabled() && !shouldYieldToMeteorFlight() && "Velocity".equals(cachedMode) && cachedNoSneak;
        }

        private void abilitiesOn() {
            if (MC.player == null || MC.player.isSpectator()) return;
            MC.player.getAbilities().flying = true;
            touchedAbilities = true;
            if (!MC.player.getAbilities().instabuild) MC.player.getAbilities().mayfly = true;
        }

        private void abilitiesOff() {
            if (MC.player == null || MC.player.isSpectator()) return;
            MC.player.getAbilities().setFlyingSpeed(0.05f);
            MC.player.getAbilities().flying = false;
            if (!MC.player.getAbilities().instabuild) {
                MC.player.getAbilities().mayfly = false;
            }
            touchedAbilities = false;
        }

        private void antiKickPacket(ServerboundMovePlayerPacket packet, double currentY) {
            if (delayLeft <= 0 && lastPacketY != Double.MAX_VALUE && shouldFlyDown(currentY, lastPacketY) && isOnAirCached()) {
                ((DihMovePlayerPacketAccessor) packet).dih$setY(lastPacketY - cachedNudge);
            } else {
                lastPacketY = currentY;
            }
        }

        private boolean shouldFlyDown(double currentY, double lastY) {
            return currentY >= lastY || lastY - currentY < cachedNudge;
        }

        private boolean isOnAirCached() {
            long tick = MC.level == null ? Long.MIN_VALUE : MC.level.getGameTime();
            if (cachedAirTick == tick) return cachedOnAir;
            cachedAirTick = tick;
            cachedOnAir = isOnAirNow();
            return cachedOnAir;
        }

        private boolean isOnAirNow() {

            if (MC.level == null || MC.player == null) return false;
            net.minecraft.world.phys.AABB box = MC.player.getBoundingBox()
                .inflate(0.0625)
                .expandTowards(0.0, -0.55, 0.0);
            return MC.level.getBlockStates(box)
                .allMatch(state -> state.isAir());
        }

        private void syncModeChange(String mode) {
            if (mode.equals(lastMode)) return;
            if ("Abilities".equals(lastMode)) abilitiesOff();
            lastMode = mode;
            if ("Abilities".equals(mode)) abilitiesOn();
            if ("Vulcan".equals(mode)) resetFlightState();
        }

        private boolean usesPacketAntiKick() {
            return "Packet".equals(cachedAntiKickMode);
        }

        private void tickVulcan() {
            if (!ensureVulcanElytra()) {
                disableVulcanNoElytra(false);
                return;
            }
            if (vulcanJumpCooldown > 0) vulcanJumpCooldown--;
            if (vulcanGlideRequestCooldown > 0) vulcanGlideRequestCooldown--;
            if (MC.player.onGround()) vulcanGlideRequestedThisAir = false;
            requestVulcanGlide();
            boolean jump = MC.options != null && MC.options.keyJump.isDown();
            boolean shift = MC.options != null && MC.options.keyShift.isDown();
            boolean gliding = MC.player.isFallFlying();
            if (jump) {
                vulcanTargetY = Math.max(vulcanTargetY + (gliding ? 0.04 : 0.12), MC.player.getY());
                if (!gliding || MC.player.getY() < vulcanTargetY - 0.08 || MC.player.getDeltaMovement().y < -0.055) {
                    vulcanJump();
                }
            } else if (shift) {
                vulcanTargetY = MC.player.getY() - 0.35;
            } else if (MC.player.getY() < vulcanTargetY - 0.22 && MC.player.getDeltaMovement().y < -0.02) {
                vulcanJump();
            }
        }

        private void disableVulcanNoElytra(boolean duringEnable) {
            if (vulcanNoElytraDisableQueued) return;
            vulcanNoElytraDisableQueued = true;
            String message = "Flight disabled: elytra not found.";
            if (duringEnable) disableSilentlyWithToggleMessage(message);
            else disableWithToggleMessage(message);
            vulcanNoElytraDisableQueued = false;
        }

        private void vulcanJump() {
            if (vulcanJumpCooldown > 0 || MC.player == null) return;
            if (MC.player.onGround()) {
                if (MC.player.isFallFlying()) MC.player.stopFallFlying();
                MC.player.jumpFromGround();
                vulcanJumpCooldown = 3;
                return;
            }
            MC.player.jumpFromGround();
            vulcanJumpCooldown = MC.player.isFallFlying() ? 12 : 3;
        }

        private void requestVulcanGlide() {
            if (vulcanGlideRequestCooldown > 0 || MC.player == null || MC.getConnection() == null) return;
            if (MC.player.onGround() || MC.player.isFallFlying()) return;
            if (vulcanGlideRequestedThisAir) return;
            MC.player.tryToStartFallFlying();
            MC.getConnection().send(new ServerboundPlayerCommandPacket(MC.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            vulcanGlideRequestedThisAir = true;
            vulcanGlideRequestCooldown = 20;
        }

        private void stopVulcanGlideState() {
            if (MC.player == null) return;
            if (MC.player.isFallFlying()) MC.player.stopFallFlying();
            vulcanGlideRequestCooldown = 0;
            vulcanJumpCooldown = 0;
            vulcanGlideRequestedThisAir = false;
        }

        private boolean ensureVulcanElytra() {
            if (MC.player == null) return false;
            ItemStack chest = MC.player.getItemBySlot(EquipmentSlot.CHEST);
            if (isVulcanUsableElytra(chest)) {
                if (!bool("anti-break-elytra") || isVulcanSafeElytra(chest)) return true;
                int replacement = findVulcanElytraSlot(true);
                if (replacement >= 0 && DihInventoryHelper.swapInventorySlots(MC, replacement, 38)) return true;
                int emptySlot = findEmptyInventorySlot();
                if (emptySlot >= 0) DihInventoryHelper.swapInventorySlots(MC, 38, emptySlot);
                return false;
            }

            int elytraSlot = findVulcanElytraSlot(bool("anti-break-elytra"));
            if (elytraSlot < 0) return false;
            return DihInventoryHelper.swapInventorySlots(MC, elytraSlot, 38)
                && isVulcanUsableElytra(MC.player.getItemBySlot(EquipmentSlot.CHEST));
        }

        private int findVulcanElytraSlot(boolean safeOnly) {
            if (MC.player == null) return -1;
            int bestSlot = -1;
            int bestRemaining = -1;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = MC.player.getInventory().getItem(slot);
                if (!isVulcanUsableElytra(stack)) continue;
                if (safeOnly && !isVulcanSafeElytra(stack)) continue;
                int remaining = vulcanElytraRemainingDurability(stack);
                if (remaining > bestRemaining) {
                    bestRemaining = remaining;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        }

        private int findEmptyInventorySlot() {
            if (MC.player == null) return -1;
            for (int slot = 0; slot < 36; slot++) {
                if (MC.player.getInventory().getItem(slot).isEmpty()) return slot;
            }
            return -1;
        }

        private boolean isVulcanUsableElytra(ItemStack stack) {
            return stack != null && stack.is(Items.ELYTRA) && !stack.isBroken() && vulcanElytraRemainingDurability(stack) > 0;
        }

        private boolean isVulcanSafeElytra(ItemStack stack) {
            return isVulcanUsableElytra(stack) && vulcanElytraRemainingDurability(stack) > vulcanElytraSafeDurability(stack);
        }

        private int vulcanElytraRemainingDurability(ItemStack stack) {
            if (stack == null || !stack.isDamageableItem()) return Integer.MAX_VALUE;
            return Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
        }

        private int vulcanElytraSafeDurability(ItemStack stack) {
            int max = stack == null || !stack.isDamageableItem() ? 0 : stack.getMaxDamage();
            return Math.max(Math.max(1, integer("min-durability")), (int) Math.ceil(max * 0.10));
        }

        private void resetFlightState() {
            updateFlightCache();
            delayLeft = cachedDelay;
            offLeft = cachedOffTime;
            lastPacketY = Double.MAX_VALUE;
            cachedAirTick = Long.MIN_VALUE;
            cachedOnAir = false;
            vulcanGlideRequestCooldown = 0;
            vulcanGlideRequestedThisAir = false;
        }

        private void updateFlightCache() {
            int revision = ModuleRegistry.revision();
            if (cachedSettingsRevision == revision) return;
            cachedSettingsRevision = revision;
            cachedMode = choice("mode");
            cachedAntiKickMode = choice("anti-kick-mode");
            cachedSpeed = decimal("speed");
            cachedVerticalSpeedMatch = bool("vertical-speed-match");
            cachedNoSneak = bool("no-sneak");
            cachedDelay = Math.max(1, integer("delay"));
            cachedOffTime = Math.max(1, integer("off-time"));
            cachedSprintBoost = Math.max(1.0, decimal("sprint-boost"));
            cachedGlideSpeed = Math.max(0.0, decimal("glide-speed"));
            cachedStopInstantly = bool("stop-instantly");
            cachedNudge = Math.max(0.01, decimal("nudge"));
        }

        private boolean shouldYieldToMeteorFlight() {
            long now = System.currentTimeMillis();
            if (meteorFlightActiveCache != null && now - meteorFlightActiveCacheAt < 250L) return meteorFlightActiveCache;
            meteorFlightActiveCacheAt = now;
            meteorFlightActiveCache = queryMeteorFlightActive();
            return meteorFlightActiveCache;
        }

        private boolean queryMeteorFlightActive() {
            try {
                Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, FlightModule.class.getClassLoader());
                Class<?> flightClass = Class.forName("meteordevelopment.meteorclient.systems.modules.movement.Flight", false, FlightModule.class.getClassLoader());
                Object modules = modulesClass.getMethod("get").invoke(null);
                Object flight = modulesClass.getMethod("get", Class.class).invoke(modules, flightClass);
                return flight != null && Boolean.TRUE.equals(flight.getClass().getMethod("isActive").invoke(flight));
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    static final class SprintModule extends Module {
        SprintModule() {
            super("sprint", "Sprint", ModuleCategory.MOVEMENT, "Sprints automatically.");
            add(new BoolSetting("omnidirectional", "Omnidirectional", false)
                .group("General").description("Sprints in every direction, not only forward").build());
            add(new BoolSetting("omni-jump", "Jump Toward Movement", false)
                .group("General").description("Sprint-jumps push you the way you walk, not the way you look")
                .visibleWhen(() -> bool("omnidirectional")).build());
            add(new BoolSetting("pause-in-liquids", "Pause In Liquids", false)
                .group("General").description("Leaves sprinting to you while in water or lava").build());

            add(new BoolSetting("ignore-blindness", "Blindness", false)
                .description("Sprint while blind").group("Ignore").build());
            add(new BoolSetting("ignore-collision", "Collision", false)
                .description("Sprint against walls").group("Ignore").build());
            add(new BoolSetting("ignore-item-use", "Item Use", false)
                .description("Keeps sprinting while you eat, drink or block").group("Ignore").build());
        }

        boolean sprintDecision(boolean original, boolean movementTick) {
            boolean sprint = original;
            if (bool("pause-in-liquids") && (MC.player.isInWater() || MC.player.isInLava())) return original;
            if (isMoving()) sprint = true;
            if (movementTick && shouldPreventSprint()) sprint = false;
            return sprint;
        }

        private boolean shouldPreventSprint() {
            if (!bool("ignore-item-use")
                && ((dihclient.mixin.accessor.DihLocalPlayerAccessor) MC.player).dih$isSlowDueToUsingItem()) return true;
            if (MC.player.isShiftKeyDown()) return true;
            if (omnidirectional()) return false;

            dihclient.util.DihRotationUtil.Rotation current = dihclient.util.DihSilentAim.packetRotation();
            if (current == null) return false;
            float deltaYawRad = (MC.player.getYRot() - current.yaw()) * net.minecraft.util.Mth.DEG_TO_RAD;
            net.minecraft.world.phys.Vec2 move = MC.player.input.getMoveVector();
            boolean hasForwardMovement =
                move.y * net.minecraft.util.Mth.cos(deltaYawRad) + move.x * net.minecraft.util.Mth.sin(deltaYawRad) > 1.0E-5F;

            return !hasForwardMovement;
        }

        boolean shouldPreventSprintPublic() {
            return shouldPreventSprint();
        }

        boolean omnidirectional() {
            return bool("omnidirectional");
        }

        boolean jumpUsesMovementYaw() {
            return omnidirectional() && bool("omni-jump");
        }

        boolean ignoreCollision() {
            return bool("ignore-collision");
        }

        boolean ignoreBlindness() {
            return bool("ignore-blindness");
        }

        private boolean isMoving() {
            return MC.player.input.getMoveVector().lengthSquared() > 0.0f;
        }
    }

    static final class ParkourModule extends Module {
        private boolean releaseJumpNextTick;

        ParkourModule() {
            super("parkour", "Parkour", ModuleCategory.MOVEMENT, "Jumps at block edges.");
            add(new IntSetting("min-drop", "Min Drop", 1, 1, 6, 1)
                .group("General").unit("blocks")
                .description("Only jumps off ledges at least this deep, so it walks down steps").build());
            add(new IntSetting("lead-ticks", "Jump Early", 1, 1, 3, 1)
                .group("General").unit("ticks")
                .description("How many ticks ahead it looks for the edge").build());

            add(new BoolSetting("only-sprinting", "Only Sprinting", false)
                .group("Conditions").description("Only jumps while you sprint").build());
            add(new BoolSetting("only-forward", "Only Forward", false)
                .group("Conditions").description("Only jumps while you hold the forward key").build());
            add(new DoubleSetting("min-speed", "Min Speed", 0.0, 0.0, 8.0, 0.1)
                .group("Conditions").unit("b/s")
                .description("Skips the jump when you move slower than this").build());
        }

        @Override
        public void tick() {
            if (MC.player == null || MC.level == null) return;
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (releaseJumpNextTick) {
                dihclient.util.DihKeyMappingBridge.of(MC.options.keyJump).dih$simulatePress(false);
                releaseJumpNextTick = false;
                return;
            }

            if (!MC.player.onGround() || MC.player.isShiftKeyDown()) return;
            if (MC.options.keyShift.isDown() || MC.options.keyJump.isDown()) return;
            if (MC.player.input.getMoveVector().lengthSquared() <= 0.0F) return;
            if (bool("only-sprinting") && !MC.player.isSprinting()) return;
            if (bool("only-forward") && !MC.options.keyUp.isDown()) return;
            if (MC.player.getDeltaMovement().horizontalDistance() * 20.0 < decimal("min-speed")) return;
            if (onGroundNextTick()) return;

            dihclient.util.DihKeyMappingBridge.of(MC.options.keyJump).dih$simulatePress(true);
            releaseJumpNextTick = true;
        }

        private boolean onGroundNextTick() {
            net.minecraft.world.phys.Vec3 d = MC.player.getDeltaMovement();
            int lead = Math.max(1, integer("lead-ticks"));
            double extraDepth = Math.max(0, integer("min-drop") - 1);
            net.minecraft.world.phys.AABB next = MC.player.getBoundingBox()
                .move(d.x * lead, d.y - 0.08, d.z * lead)
                .expandTowards(0.0, -0.02 - extraDepth, 0.0);
            return !MC.level.noCollision(MC.player, next);
        }

        @Override
        public void onDisable() {
            if (releaseJumpNextTick) {
                releaseJumpNextTick = false;
                if (MC != null && MC.options != null) {
                    dihclient.util.DihKeyMappingBridge.of(MC.options.keyJump).dih$simulatePress(false);
                }
            }
        }
    }

    static final class AutoToolModule extends Module {
        private final Random delayRandom = new Random();
        private BlockPos currentTargetPos;
        private boolean switchedForTarget;
        private long mineStartMs;
        private long switchDueMs;
        private int previousSlot = -1;

        private int switchedToSlot = -1;
        private long lastMineMs;
        private long restoreDueMs = -1L;

        AutoToolModule() {
            super("auto-tool", "AutoTool", ModuleCategory.PLAYER, "Switches to the best tool to mine a block.");
            add(new IntSetting("switch-delay-ms", "Switch Delay", 12, 0, 1000, 1).unit("ms")
                .group("General")
                .description("Delay before switching.").build());
            add(new BoolSetting("require-sneaking", "Require Sneaking", false)
                .group("General")
                .description("Only switches while you sneak").build());
            add(new BoolSetting("off-in-creative", "Off In Creative", false)
                .group("General")
                .description("Leaves your slot alone in creative, where every block breaks at once").build());

            add(new BoolSetting("consider-inventory", "Consider Inventory", false)
                .group("Tools")
                .description("Pulls a better tool up from your main inventory").build());
            add(new BoolSetting("ignore-durability", "Ignore Durability", true)
                .group("Tools")
                .description("Also uses tools that are about to break").build());
            add(new BoolSetting("prefer-silk-touch", "Prefer Silk Touch", false)
                .group("Tools")
                .description("Picks a Silk Touch tool when one works on the block").build());
            add(new BoolSetting("skip-instant", "Skip Instant Blocks", false)
                .group("Tools")
                .description("Does not switch for blocks that break at once, like grass and torches").build());

            add(RegistryListSetting.blocks("ignored-blocks", "Ignored Blocks", "")
                .group("Filters")
                .description("Blocks it never switches for").build());
            add(RegistryListSetting.items("keep-items", "Keep Items", "")
                .group("Filters")
                .description("Never switches away while you hold one of these").build());

            add(new BoolSetting("switch-back", "Switch Back", false)
                .group("Switch Back")
                .description("Returns to your old slot when you stop mining").build());
            add(new IntSetting("switch-back-delay-ms", "Restore Delay", 100, 0, 1000, 25).unit("ms")
                .group("Switch Back")
                .description("Delay before restoring.")
                .visibleWhen(() -> bool("switch-back")).build());
        }

        private boolean blockedByFilters(BlockPos pos, BlockState state) {
            if (bool("off-in-creative") && MC.player.isCreative()) return true;
            if (bool("skip-instant") && state.getDestroySpeed(MC.level, pos) == 0.0F) return true;
            if (listMatches("ignored-blocks", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())) return true;
            ItemStack held = MC.player.getMainHandItem();
            return !held.isEmpty() && listMatches("keep-items", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        }

        private boolean listMatches(String settingId, String id) {
            for (String entry : list(settingId)) {
                if (RegistryListCodec.matches(id, entry)) return true;
            }
            return false;
        }

        @Override
        public void onDisable() {

            previousSlot = -1;
            switchedToSlot = -1;
            currentTargetPos = null;
            switchedForTarget = false;
            switchDueMs = 0L;
            restoreDueMs = -1L;
        }

        @Override
        public void tick() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.level == null || MC.gameMode == null) return;

            if (AutoTotemModule.operationActive()) return;
            if (!(MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor)) return;

            boolean destroying = accessor.dih$isDestroying();
            if (destroying) {
                restoreDueMs = -1L;
                if (bool("require-sneaking") && !MC.player.isShiftKeyDown()) return;
                BlockPos pos = accessor.dih$getDestroyBlockPos();
                if (pos == null) return;
                BlockState state = MC.level.getBlockState(pos);
                if (state.isAir()) return;
                if (!pos.equals(currentTargetPos) && blockedByFilters(pos, state)) return;

                lastMineMs = nowMs();
                if (!pos.equals(currentTargetPos)) {

                    currentTargetPos = pos;
                    switchedForTarget = false;
                    mineStartMs = nowMs();
                    switchDueMs = mineStartMs + sampledDelayMs(integer("switch-delay-ms"));
                    if (previousSlot < 0) previousSlot = MC.player.getInventory().getSelectedSlot();
                }
                if (!switchedForTarget && nowMs() >= switchDueMs) {
                    int slot = resolveToolHotbarSlot(state);
                    if (slot >= 0) selectHotbarWithRealKey(slot, true);
                }
            } else {
                if (bool("switch-back")) {

                    if (switchedToSlot >= 0
                        && MC.player.getInventory().getSelectedSlot() != switchedToSlot) {
                        previousSlot = -1;
                        switchedToSlot = -1;
                        restoreDueMs = -1L;
                    }
                    if (previousSlot >= 0 && restoreDueMs < 0L) {
                        restoreDueMs = lastMineMs + sampledDelayMs(integer("switch-back-delay-ms"));
                    }
                    if (previousSlot >= 0 && nowMs() >= restoreDueMs) {
                        selectHotbarWithRealKey(previousSlot, false);
                        currentTargetPos = null;
                        switchedForTarget = false;
                        switchDueMs = 0L;
                        restoreDueMs = -1L;
                        previousSlot = -1;
                        switchedToSlot = -1;
                    }
                } else {
                    previousSlot = -1;
                    switchedToSlot = -1;
                    currentTargetPos = null;
                    switchedForTarget = false;
                    switchDueMs = 0L;
                    restoreDueMs = -1L;
                }
            }
        }

        private void selectHotbarWithRealKey(int slot, boolean forward) {
            if (MC.player == null) return;
            int clamped = Math.max(0, Math.min(8, slot));
            if (MC.player.getInventory().getSelectedSlot() == clamped) {
                switchedForTarget = true;
                if (forward) switchedToSlot = clamped;
                return;
            }
            if (!DihHandArbiter.beginHandPacketGroup(id())) return;
            try {
                DihInputClicker.queueHotbarSlot(clamped);
                if (forward) switchedToSlot = clamped;
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }

        private int resolveToolHotbarSlot(BlockState state) {
            if (MC.player == null || state == null) return -1;

            int limit = bool("consider-inventory") ? 36 : 9;
            int best = -1;
            if (bool("prefer-silk-touch")) best = DihAutoTool.bestToolSlot(MC, state, limit, bool("ignore-durability"), true);
            if (best < 0) best = DihAutoTool.bestToolSlot(MC, state, limit, bool("ignore-durability"), false);
            if (best < 0) return -1;

            if (DihHandArbiter.slotReserved(best, id())) return -1;

            int selectedSlot = MC.player.getInventory().getSelectedSlot();
            if (best < 9) {
                if (!isToolBetterThanSelectedSlot(state, best, selectedSlot)) return selectedSlot;
                return best;
            }

            int selected = MC.player.getInventory().getSelectedSlot();
            if (!bool("consider-inventory")) return -1;

            if (!DihHandArbiter.beginHandPacketGroup(id())) return -1;
            try {
                if (!DihInventoryHelper.swapInventorySlots(MC, best, selected)) return -1;
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
            return selected;
        }

        private boolean isToolBetterThanSelectedSlot(BlockState state, int candidateSlot, int selectedSlot) {
            if (MC.player == null) return false;
            if (candidateSlot == selectedSlot) return true;
            ItemStack candidate = MC.player.getInventory().getItem(candidateSlot);
            ItemStack current = MC.player.getInventory().getItem(Math.max(0, Math.min(8, selectedSlot)));
            float candidateSpeed = DihAutoTool.destroySpeed(MC, candidate, state);
            float currentSpeed = DihAutoTool.destroySpeed(MC, current, state);
            if (bool("prefer-silk-touch") && candidateSpeed > 1f && DihAutoTool.hasSilkTouch(MC, candidate)) {
                return !(currentSpeed > 1f && DihAutoTool.hasSilkTouch(MC, current));
            }
            return candidateSpeed > currentSpeed;
        }

        private int sampledDelayMs(int baseMs) {
            if (baseMs <= 0) return 0;
            int spread = Math.max(15, Math.min(180, Math.round(baseMs * 0.55f)));
            int extra = delayRandom.nextInt(spread + 1);
            if (delayRandom.nextInt(12) == 0) extra += 15 + delayRandom.nextInt(Math.max(16, spread / 2 + 1));
            return Math.min(1000, baseMs + extra);
        }

        private static long nowMs() {
            return System.currentTimeMillis();
        }
    }

    static final class SpeedModule extends Module {
        private int strafeStage;
        private double strafeSpeed;
        private double lastDistance;
        private long speedLimitTimer;
        private String lastSpeedMode = "Vanilla";
        private Boolean meteorSpeedActiveCache;
        private long meteorSpeedActiveCacheAt;
        private volatile boolean setbackPending;

        SpeedModule() {
            super("speed", "Speed", ModuleCategory.MOVEMENT, "Boosts movement speed.");
            add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Strafe").group("General").description("Speed method").build());
            add(new DoubleSetting("timer", "Timer", 1.0, 0.01, 10.0, 0.05).group("General").description("Client timer speed.").build());

            add(new DoubleSetting("vanilla-speed", "Vanilla Speed", 5.6, 0.0, 20.0, 0.1).group("Vanilla").visibleWhen(() -> "Vanilla".equals(value("mode"))).description("Blocks per second").build());
            add(new BoolSetting("speed-effect", "Speed Effect", true).group("Vanilla")
                .visibleWhen(() -> "Vanilla".equals(value("mode")))
                .description("Adds the Speed potion bonus on top").build());
            add(new BoolSetting("auto-jump", "Auto Jump", false).group("Vanilla")
                .visibleWhen(() -> "Vanilla".equals(value("mode")))
                .description("Hops whenever you walk on the ground").build());

            add(new DoubleSetting("strafe-speed", "Strafe Speed", 1.6, 0.0, 3.0, 0.05).group("Strafe").visibleWhen(() -> "Strafe".equals(value("mode"))).description("Strafe speed multiplier.").build());
            add(new DoubleSetting("hop-height", "Hop Height", 0.40123128, 0.2, 0.6, 0.01).group("Strafe")
                .visibleWhen(() -> "Strafe".equals(value("mode")))
                .description("Upward speed of each strafe hop; vanilla jumps are 0.42").build());
            add(new BoolSetting("speed-limit", "Speed Limit", false).group("Strafe").visibleWhen(() -> "Strafe".equals(value("mode"))).description("Limit max speed.").build());

            add(new BoolSetting("in-liquids", "In Liquids", false).group("Conditions").description("Keeps the boost in water and lava").build());
            add(new BoolSetting("when-sneaking", "When Sneaking", false).group("Conditions").description("Keeps the boost while sneaking").build());
            add(new BoolSetting("only-on-ground", "Only Ground", false).group("Conditions").visibleWhen(() -> "Vanilla".equals(value("mode"))).description("Only boosts while your feet are on the ground").build());
            add(new BoolSetting("pause-when-hurt", "Pause When Hurt", false).group("Conditions")
                .description("Lets knockback play out instead of overwriting it").build());
            add(new BoolSetting("pause-while-using", "Pause While Using", false).group("Conditions")
                .description("No boost while you eat, drink, block or draw a bow").build());
            add(new BoolSetting("disable-on-setback", "Disable On Setback", false).group("Conditions")
                .description("Turns Speed off when the server teleports you back").build());
        }

        @Override
        public void onEnable() {
            lastSpeedMode = choice("mode");
            resetStrafe();
        }

        @Override
        public void onDisable() {
            resetStrafe();
        }

        @Override
        public void preMovementTick() {
            if (setbackPending) {
                setbackPending = false;
                disableWithToggleMessage("Speed disabled: server set you back.");
                return;
            }
            if (MC.player == null || shouldYieldToMeteorSpeed() || stopSpeed()) return;
            syncSpeedMode();
            if (isInLiquid()) {
                resetStrafe();
                return;
            }
            if ("Vanilla".equals(choice("mode")) && bool("auto-jump") && MC.player.onGround() && isMoving()
                && !MC.options.keyJump.isDown()) {
                MC.player.jumpFromGround();
            }
            lastDistance = Math.sqrt((MC.player.getX() - MC.player.xo) * (MC.player.getX() - MC.player.xo) + (MC.player.getZ() - MC.player.zo) * (MC.player.getZ() - MC.player.zo));
        }

        @Override
        public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
            if (type != MoverType.SELF || MC.player == null || MC.options == null || movement == null) return movement;
            if (shouldYieldToMeteorSpeed() || stopSpeed()) return movement;
            syncSpeedMode();
            if (isInLiquid()) return liquidMovement(movement);
            return "Strafe".equals(choice("mode")) ? strafeMovement(movement) : vanillaMovement(movement);
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (packet instanceof ClientboundPlayerPositionPacket) {
                resetStrafe();
                if (bool("disable-on-setback")) setbackPending = true;
            }
            return false;
        }

        @Override
        public boolean shouldApplySpeedTimer() {
            return !shouldYieldToMeteorSpeed() && !stopSpeed() && isMoving();
        }

        @Override
        public float speedTimerMultiplier() {
            if (!shouldApplySpeedTimer()) return 1.0f;
            try {
                return Math.max(0.01f, Math.min(10.0f, (float) decimal("timer")));
            } catch (Exception ignored) {
                return 1.0f;
            }
        }

        private Vec3 vanillaMovement(Vec3 movement) {
            Vec3 horizontal = horizontalVelocity(decimal("vanilla-speed"));
            double x = horizontal.x;
            double z = horizontal.z;
            if (bool("speed-effect") && MC.player.hasEffect(MobEffects.SPEED)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SPEED);
                if (effect != null) {
                    double multiplier = (effect.getAmplifier() + 1) * 0.205;
                    x += x * multiplier;
                    z += z * multiplier;
                }
            }
            return new Vec3(x, movement.y, z);
        }

        private Vec3 liquidMovement(Vec3 movement) {
            if (!"Strafe".equals(choice("mode"))) return vanillaMovement(movement);
            double liquidSpeed = defaultSpeed() * Math.max(0.0, decimal("strafe-speed"));
            Vec3 horizontal = transformStrafe(liquidSpeed);
            return new Vec3(horizontal.x, movement.y, horizontal.z);
        }

        Vec3 afterLiquidTravel(Vec3 movement) {
            if (movement == null || MC.player == null || !bool("in-liquids") || !isInLiquid()) return movement;
            if (shouldYieldToMeteorSpeed() || stopSpeed()) return movement;
            syncSpeedMode();
            return liquidMovement(movement);
        }

        private Vec3 strafeMovement(Vec3 movement) {

            if (strafeStage == 0) {
                if (isMoving()) {
                    strafeStage = 1;
                    strafeSpeed = 1.1799999475479126 * defaultSpeed() - 0.01;

                }
            }
            if (strafeStage == 1) {
                if (isMoving() && MC.player.onGround()) {
                    movement = new Vec3(movement.x, hop(decimal("hop-height")), movement.z);
                    strafeSpeed *= decimal("strafe-speed");
                    strafeStage = 2;
                }
            } else if (strafeStage == 2) {
                strafeSpeed = lastDistance - 0.76 * (lastDistance - defaultSpeed());
                strafeStage = 3;
            } else if (strafeStage == 3) {
                if (!hasVerticalMoveSpace() || (MC.player.verticalCollision && strafeStage > 0)) strafeStage = 0;
                strafeSpeed = lastDistance - lastDistance / 159.0;
            } else if (strafeStage > 3 || strafeStage < 0) {
                strafeStage = 0;
            }

            strafeSpeed = Math.max(strafeSpeed, defaultSpeed());
            if (bool("speed-limit")) {
                long now = System.currentTimeMillis();
                if (now - speedLimitTimer > 2500L) speedLimitTimer = now;
                strafeSpeed = Math.min(strafeSpeed, now - speedLimitTimer > 1250L ? 0.44 : 0.43);
            }

            Vec3 horizontal = transformStrafe(strafeSpeed);
            return new Vec3(horizontal.x, movement.y, horizontal.z);
        }

        private Vec3 horizontalVelocity(double blocksPerSecond) {
            if (MC.player == null || MC.player.input == null) return Vec3.ZERO;
            double speed = blocksPerSecond / 20.0;
            Vec3 forward = Vec3.directionFromRotation(0.0f, MC.player.getYRot());
            Vec3 right = Vec3.directionFromRotation(0.0f, MC.player.getYRot() + 90.0f);
            double x = 0.0;
            double z = 0.0;
            boolean hasForward = false;
            if (MC.options.keyUp.isDown()) {
                x += forward.x * speed;
                z += forward.z * speed;
                hasForward = true;
            }
            if (MC.options.keyDown.isDown()) {
                x -= forward.x * speed;
                z -= forward.z * speed;
                hasForward = true;
            }
            boolean hasSide = false;
            if (MC.options.keyRight.isDown()) {
                x += right.x * speed;
                z += right.z * speed;
                hasSide = true;
            }
            if (MC.options.keyLeft.isDown()) {
                x -= right.x * speed;
                z -= right.z * speed;
                hasSide = true;
            }
            if (hasForward && hasSide) {
                double diagonal = 1.0 / Math.sqrt(2.0);
                x *= diagonal;
                z *= diagonal;
            }
            return new Vec3(x, 0.0, z);
        }

        private Vec3 transformStrafe(double speed) {
            if (MC.player == null || MC.player.input == null) return Vec3.ZERO;
            Vec2 input = MC.player.input.getMoveVector();
            float forward = Math.signum(input.y);
            float side = Math.signum(input.x);
            if (forward == 0.0f && side == 0.0f) return Vec3.ZERO;

            float yaw = MC.player.getYRot(movementPartialTick());
            float strafe = 90.0f * side;
            if (forward != 0.0f) strafe *= forward * 0.5f;
            yaw -= strafe;
            if (forward < 0.0f) yaw -= 180.0f;
            double radians = Math.toRadians(yaw);
            return new Vec3(-Math.sin(radians) * speed, 0.0, Math.cos(radians) * speed);
        }

        private float movementPartialTick() {
            try {
                return MC.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            } catch (Exception ignored) {
                return 1.0f;
            }
        }

        private double defaultSpeed() {
            double speed = 0.2873;
            if (MC.player.hasEffect(MobEffects.SPEED)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SPEED);
                if (effect != null) speed *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            }
            if (MC.player.hasEffect(MobEffects.SLOWNESS)) {
                MobEffectInstance effect = MC.player.getEffect(MobEffects.SLOWNESS);
                if (effect != null) speed /= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            }
            return speed;
        }

        private double hop(double base) {
            MobEffectInstance effect = MC.player.getEffect(MobEffects.JUMP_BOOST);
            return effect == null ? base : base + (effect.getAmplifier() + 1) * 0.1f;
        }

        private boolean hasVerticalMoveSpace() {
            return MC.level != null && MC.level.noCollision(MC.player.getBoundingBox().move(0.0, MC.player.getDeltaMovement().y, 0.0));
        }

        private boolean stopSpeed() {

            if (MC.player == null) return true;
            if (MC.player.isFallFlying() || MC.player.onClimbable() || MC.player.getVehicle() != null) return true;
            if (!bool("when-sneaking") && MC.player.isShiftKeyDown()) return true;
            if (bool("pause-when-hurt") && MC.player.hurtTime > 0) return true;
            if (bool("pause-while-using") && MC.player.isUsingItem()) return true;
            boolean inLiquid = isInLiquid();
            boolean liquidSpeedAllowed = inLiquid && bool("in-liquids");
            if (bool("only-on-ground") && !MC.player.onGround() && "Vanilla".equals(choice("mode")) && !liquidSpeedAllowed) return true;
            return inLiquid && !liquidSpeedAllowed;
        }

        private boolean isMoving() {
            if (MC.player == null) return false;
            if (MC.player.input != null) {
                Vec2 input = MC.player.input.getMoveVector();
                if (input.x != 0.0f || input.y != 0.0f) return true;
            }
            return MC.player.xxa != 0.0f || MC.player.zza != 0.0f;
        }

        private boolean isInLiquid() {
            return MC.player != null && (MC.player.isInWater() || MC.player.isInLava());
        }

        private void syncSpeedMode() {
            String mode = choice("mode");
            if (mode.equals(lastSpeedMode)) return;
            lastSpeedMode = mode;
            resetStrafe();
        }

        private boolean shouldYieldToMeteorSpeed() {
            long now = System.currentTimeMillis();
            if (meteorSpeedActiveCache != null && now - meteorSpeedActiveCacheAt < 250L) return meteorSpeedActiveCache;
            meteorSpeedActiveCacheAt = now;
            meteorSpeedActiveCache = queryMeteorSpeedActive();
            return meteorSpeedActiveCache;
        }

        private boolean queryMeteorSpeedActive() {
            try {
                Class<?> modulesClass = Class.forName("meteordevelopment.meteorclient.systems.modules.Modules", false, SpeedModule.class.getClassLoader());
                Class<?> speedClass = Class.forName("meteordevelopment.meteorclient.systems.modules.movement.speed.Speed", false, SpeedModule.class.getClassLoader());
                Object modules = modulesClass.getMethod("get").invoke(null);
                Object speed = modulesClass.getMethod("get", Class.class).invoke(modules, speedClass);
                return speed != null && Boolean.TRUE.equals(speed.getClass().getMethod("isActive").invoke(speed));
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void resetStrafe() {

            strafeStage = 0;
            strafeSpeed = 0.2873;
            lastDistance = 0.0;
        }

    }

    static final class SneakModule extends Module {
        private boolean sentPacketSneak;
        private boolean heldVanillaSneak;

        SneakModule() {
            super("sneak", "Sneak", ModuleCategory.MOVEMENT, "Keeps sneak held for you.");
            add(new ChoiceSetting("mode", "Mode", "Vanilla", "Vanilla", "Packet").group("General").description("Which method to sneak.").build());

            add(new BoolSetting("pause-in-air", "Pause In Air", false)
                .group("Conditions").description("Lets go of sneak while you are off the ground").build());
            add(new BoolSetting("pause-in-liquids", "Pause In Liquids", false)
                .group("Conditions").description("Lets go of sneak in water and lava so you don't sink").build());
            add(new BoolSetting("pause-on-climbable", "Pause On Ladders", false)
                .group("Conditions").description("Lets go of sneak on ladders, vines and scaffolding").build());
            add(new BoolSetting("pause-when-sprinting", "Pause When Sprinting", false)
                .group("Conditions").description("Lets go of sneak while you hold the sprint key").build());
        }

        boolean holdsSneakDown() {
            return isEnabled()
                && !dihclient.util.multi.MultiPilot.isActive()
                && !dihclient.util.multi.PacketTeleportController.ownsMainMovement()
                && !"Packet".equals(choice("mode"))
                && MC != null && MC.options != null && MC.player != null
                && !MC.player.getAbilities().flying
                && !paused();
        }

        private boolean paused() {
            if (MC.player == null || MC.options == null) return false;
            if (bool("pause-in-air") && !MC.player.onGround()) return true;
            if (bool("pause-in-liquids") && (MC.player.isInWater() || MC.player.isInLava())) return true;
            if (bool("pause-on-climbable") && MC.player.onClimbable()) return true;
            return bool("pause-when-sprinting") && DihKeyMappingBridge.of(MC.options.keySprint).dih$isActuallyDown();
        }

        private void releaseSneak() {
            if (sentPacketSneak) {
                sendInputSneak(false);
                sentPacketSneak = false;
            }
            if (heldVanillaSneak) {
                heldVanillaSneak = false;
                MC.options.keyShift.setDown(DihKeyMappingBridge.of(MC.options.keyShift).dih$isActuallyDown());
            }
        }

        @Override
        public void tick() {
            holdSneak();
        }

        @Override
        public void preMovementTick() {

            if (!"Packet".equals(choice("mode"))) holdSneak();
        }

        private void holdSneak() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;

            if (dihclient.util.multi.PacketTeleportController.ownsMainMovement()) return;
            if (MC.options == null || MC.player == null || MC.player.getAbilities().flying) return;
            if (paused()) {
                releaseSneak();
                return;
            }
            if ("Packet".equals(choice("mode"))) {
                sendInputSneak(true);
                sentPacketSneak = true;
            } else {
                MC.options.keyShift.setDown(true);
                heldVanillaSneak = true;
            }
        }

        @Override
        public void onDisable() {
            if (dihclient.util.multi.MultiPilot.isActive()) {
                sentPacketSneak = false;
                return;
            }
            if (sentPacketSneak) {
                sendInputSneak(false);
                sentPacketSneak = false;
            }
            heldVanillaSneak = false;
            if (MC.options != null) MC.options.keyShift.setDown(false);
        }

        private void sendInputSneak(boolean sneak) {
            if (MC.options == null || MC.getConnection() == null) return;
            Input input = new Input(
                MC.options.keyUp.isDown(),
                MC.options.keyDown.isDown(),
                MC.options.keyLeft.isDown(),
                MC.options.keyRight.isDown(),
                MC.options.keyJump.isDown(),
                sneak,
                MC.options.keySprint.isDown()
            );
            MC.getConnection().send(new ServerboundPlayerInputPacket(input));
        }
    }

    static final class FakeCoordsModule extends Module {
        FakeCoordsModule() {
            super("fake-coords", "FakeCoords", ModuleCategory.RENDER,
                "Spoofs displayed coordinates only.");
            add(new ChoiceSetting("mode", "Mode", "Offset", "Offset", "Scaled", "Rotated", "Scrambled", "Frozen", "Custom")
                .group("General").description("Choose spoofing mode.").build());
            add(new BoolSetting("fake-y", "Fake Y", false)
                .group("General").description("Spoof vertical coordinate.").build());
            add(new BoolSetting("affect-f3", "Debug Screen", true)
                .group("General").description("Spoof the coordinates on the F3 screen.").build());
            add(new BoolSetting("affect-hud", "HUD", true)
                .group("General").description("Spoof the coordinates in the HUD lines.").build());
            add(new ActionSetting("reroll", "Reroll", DihFakeCoords::regenerate)
                .group("General").buttonLabel("Reroll").description("Pick new random fake coordinates.")
                .visibleWhen(() -> !"Custom".equals(choice("mode"))).build());
            add(new BoolSetting("reroll-on-join", "Reroll On Join", false)
                .group("General").description("Pick new fake coordinates every time you join a world.")
                .visibleWhen(() -> !"Custom".equals(choice("mode"))).build());
            add(new DoubleSetting("custom-x", "Custom X", 0, -30_000_000, 30_000_000, 1)
                .group("Custom").description("Set displayed X.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new DoubleSetting("custom-y", "Custom Y", 64, -30_000_000, 30_000_000, 1)
                .group("Custom").description("Set displayed Y.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new DoubleSetting("custom-z", "Custom Z", 0, -30_000_000, 30_000_000, 1)
                .group("Custom").description("Set displayed Z.")
                .numericTextField()
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
            add(new ActionSetting("anchor", "Anchor", this::captureCurrentPosition)
                .buttonLabel("Capture Position")
                .group("Custom").description("Use current position.")
                .visibleWhen(() -> "Custom".equals(choice("mode"))).build());
        }

        @Override public boolean showInArrayList() { return false; }

        @Override
        public void onEnable() {
            DihFakeCoords.Mode m = mode();
            DihFakeCoords.enable(m, bool("fake-y"));
            DihFakeCoords.setTargets(bool("affect-f3"), bool("affect-hud"));
            if (m == DihFakeCoords.Mode.CUSTOM) pushCustom();
        }

        @Override
        public void onGameJoin() {
            if (isEnabled() && bool("reroll-on-join")) DihFakeCoords.regenerate();
        }

        @Override
        public void onDisable() {
            DihFakeCoords.disable();
        }

        @Override
        public void tick() {
            DihFakeCoords.Mode m = mode();
            DihFakeCoords.setMode(m);
            DihFakeCoords.setFakeY(bool("fake-y"));
            DihFakeCoords.setTargets(bool("affect-f3"), bool("affect-hud"));
            if (m == DihFakeCoords.Mode.CUSTOM) pushCustom();
        }

        private void pushCustom() {
            DihFakeCoords.setCustom(decimal("custom-x"), decimal("custom-y"), decimal("custom-z"));
        }

        private void captureCurrentPosition() {
            if (MC.player == null) return;
            setValue("custom-x", Double.toString(MC.player.getX()));
            setValue("custom-y", Double.toString(MC.player.getY()));
            setValue("custom-z", Double.toString(MC.player.getZ()));
            pushCustom();
        }

        private DihFakeCoords.Mode mode() {
            return switch (choice("mode")) {
                case "Scaled" -> DihFakeCoords.Mode.SCALED;
                case "Rotated" -> DihFakeCoords.Mode.ROTATED;
                case "Scrambled" -> DihFakeCoords.Mode.SCRAMBLED;
                case "Frozen" -> DihFakeCoords.Mode.FROZEN;
                case "Custom" -> DihFakeCoords.Mode.CUSTOM;
                default -> DihFakeCoords.Mode.OFFSET;
            };
        }
    }

    static final class NoFallModule extends Module {
        private boolean placedFluid;
        private BlockPos placedTarget;
        private int placedTimer;

        NoFallModule() {
            super("no-fall", "NoFall", ModuleCategory.MOVEMENT, "Reduces fall damage.");
            add(new ChoiceSetting("mode", "Mode", "Packet", "Packet", "AirPlace", "Place").group("General").description("Fall protection method.").build());
            add(new BoolSetting("anti-bounce", "Anti Bounce", true).group("General").description("Stop bounce effects.").build());
            add(new BoolSetting("pause-on-mace", "Pause On Mace", true).group("General").description("Pause holding mace").build());

            add(new DoubleSetting("trigger-speed", "Trigger Speed", 0.5, 0.1, 2.0, 0.05).group("Packet")
                .visibleWhen(() -> "Packet".equals(value("mode")))
                .description("Claims ground once you fall faster than this, in blocks per tick").build());
            add(new BoolSetting("with-flight", "With Flight", true).group("Packet")
                .visibleWhen(() -> "Packet".equals(value("mode")))
                .description("Always claims ground while Flight is on").build());

            add(new ChoiceSetting("placed-item", "Placed Item", "Bucket", "Bucket", "PowderSnow", "HayBale", "Cobweb", "SlimeBlock").group("Place").visibleWhen(() -> "Place".equals(value("mode")) || "AirPlace".equals(value("mode"))).description("What it puts down to break the fall; Bucket uses powder snow in the Nether").build());
            add(new ChoiceSetting("air-place-mode", "Air Place Mode", "BeforeDeath", "BeforeDamage", "BeforeDeath").group("Place").visibleWhen(() -> "AirPlace".equals(value("mode"))).description("Place before any damage, or only before a fall that would kill").build());
            add(new DoubleSetting("place-height", "Place Height", 3.0, 2.0, 20.0, 0.5).group("Place")
                .visibleWhen(() -> "Place".equals(value("mode")))
                .description("Places once you have fallen this many blocks").build());
            add(new IntSetting("reach-down", "Reach Down", 5, 2, 6, 1).group("Place")
                .visibleWhen(() -> "Place".equals(value("mode")))
                .unit("blocks").description("How far below you it looks for ground to place on").build());
            add(new BoolSetting("skip-water", "Skip Above Water", true).group("Place")
                .visibleWhen(() -> "Place".equals(value("mode")))
                .description("Doesn't place when you will land in water anyway").build());
            add(new BoolSetting("pick-up", "Pick Up", true).group("Place")
                .visibleWhen(() -> "Place".equals(value("mode")))
                .description("Scoops the water or powder snow back into the bucket after landing").build());
            add(new BoolSetting("anchor", "Anchor", true).group("Place").visibleWhen(() -> !"Packet".equals(value("mode"))).description("Slow before placing.").build());
            add(new BoolSetting("client-rotate", "Client Rotation", false).group("Place").visibleWhen(() -> !"Packet".equals(value("mode"))).description("Also rotate camera client-side.").build());
        }

        @Override
        public void onEnable() {
            placedFluid = false;
            placedTarget = null;
            placedTimer = 0;
        }

        @Override
        public void onDisable() {
            placedFluid = false;
            placedTarget = null;
            placedTimer = 0;
        }

        @Override
        public void tick() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (DihJoinGrace.isMovementPaused()) return;
            if (MC.player == null || MC.gameMode == null || "Packet".equals(choice("mode"))) return;

            if (DihKillAuraRotation.currentOwner() != null && DihKillAuraRotation.hasCurrentRotation()) return;
            if (bool("pick-up")) cleanupPlacedFluid();
            else placedFluid = false;
            if (!shouldPlaceModeAct()) return;
            if (bool("anchor")) centerPlayer();
            if ("AirPlace".equals(choice("mode"))) tryAirPlaceBelow();
            else tryPlaceBelow();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (dihclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Packet".equals(choice("mode")) || !(packet instanceof ServerboundMovePlayerPacket move) || !shouldPacketSpoof()) return false;
            if (move.isOnGround()) return false;
            ((DihMovePlayerPacketAccessor) move).dih$setOnGround(true);
            return false;
        }

        private boolean shouldPacketSpoof() {
            if (DihJoinGrace.isMovementPaused()) return false;
            if (MC.player == null || MC.player.getAbilities().instabuild) return false;
            if (bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) return false;
            Module flight = ModuleRegistry.get("flight");
            if (bool("with-flight") && flight != null && flight.isEnabled()) return true;
            if (MC.player.isFallFlying()) return false;
            return MC.player.getDeltaMovement().y <= -decimal("trigger-speed");
        }

        private boolean shouldPlaceModeAct() {

            if (MC.player == null || MC.player.getAbilities().instabuild || MC.player.isFallFlying() || MC.player.isInWater() || MC.player.isInLava()) return false;
            if (bool("pause-on-mace") && MC.player.getMainHandItem().is(Items.MACE)) return false;
            double fall = MC.player.fallDistance;
            if ("AirPlace".equals(choice("mode"))) {
                if ("BeforeDeath".equals(choice("air-place-mode"))) {

                    return fall > Math.max(2.0, MC.player.getHealth() + MC.player.getAbsorptionAmount());
                }
                return fall > 2.0;
            }
            return fall > decimal("place-height") && !(bool("skip-water") && isAboveWater());
        }

        private boolean tryPlaceBelow() {

            if (DihBlinkManager.holdsActionsWithoutMovement()) return false;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int slot = findHotbarItem();
            if (slot < 0) return false;
            BlockHitResult ray = MC.level.clip(new ClipContext(
                MC.player.position(),
                MC.player.position().subtract(0, Math.max(1, integer("reach-down")), 0),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                MC.player
            ));
            if (ray == null || ray.getType() != HitResult.Type.BLOCK) return false;
            BlockPos target = ray.getBlockPos().above();

            if (!DihHandArbiter.beginHandPacketGroup(id())) return false;
            try {
                DihInventoryHelper.selectHotbarSlot(MC, slot);
                try {
                    Item item = placeItem();
                    if (item == Items.WATER_BUCKET || item == Items.POWDER_SNOW_BUCKET) {

                        Vec3 hitVec = Vec3.atCenterOf(target);
                        if (ModuleRegistry.shouldCancelUseExcept(ray, InteractionHand.MAIN_HAND, id())) return false;

                        if (!DihPlacementTick.claim(id())) return false;
                        rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                            MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
                        });
                        placedFluid = true;
                        placedTarget = target;
                        placedTimer = 0;
                    } else {
                        BlockPos below = target.below();
                        Vec3 hitVec = Vec3.atCenterOf(below).add(0, 0.5, 0);
                        BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, below, false);
                        if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return false;
                        if (!DihPlacementTick.claim(id())) return false;
                        rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                            MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
                        });
                    }
                    return true;
                } finally {
                    DihInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }

        private boolean tryAirPlaceBelow() {

            if (DihBlinkManager.holdsActionsWithoutMovement()) return false;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int slot = findHotbarBlockItem();
            if (slot < 0) return false;
            Vec3 preVelocity = MC.player.getDeltaMovement();

            if (!DihHandArbiter.beginHandPacketGroup(id())) return false;
            try {
                DihInventoryHelper.selectHotbarSlot(MC, slot);
                try {
                    MC.player.setDeltaMovement(preVelocity.x, 0.0, preVelocity.z);
                    BlockPos below = MC.player.blockPosition().below();
                    Vec3 hitVec = Vec3.atCenterOf(below).add(0, 0.5, 0);
                    BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, below, false);
                    if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return false;
                    if (!DihPlacementTick.claim(id())) return false;

                    rotateAndAct(MC.player.getYRot(), 90.0, () -> {
                        MC.gameMode.useItemOn(MC.player, InteractionHand.MAIN_HAND, hit);
                    });
                    return true;
                } finally {
                    MC.player.setDeltaMovement(preVelocity);
                    DihInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }

        private void rotateAndAct(double yaw, double pitch, Runnable action) {
            if (MC.player == null || MC.getConnection() == null) {
                action.run();
                return;
            }
            boolean clientRotate = bool("client-rotate");
            float ny = (float) yaw;
            float np = (float) pitch;
            float oldYaw = MC.player.getYRot();
            float oldPitch = MC.player.getXRot();
            float oldYawO = MC.player.yRotO;
            float oldPitchO = MC.player.xRotO;
            float oldHeadYaw = MC.player.yHeadRot;
            float oldHeadYawO = MC.player.yHeadRotO;
            float oldBodyYaw = MC.player.yBodyRot;
            float oldBodyYawO = MC.player.yBodyRotO;

            MC.player.setYRot(ny);
            MC.player.setXRot(np);
            if (clientRotate) {

                MC.player.yRotO = ny;
                MC.player.xRotO = np;
                MC.player.yHeadRot = ny;
                MC.player.yHeadRotO = ny;
                MC.player.yBodyRot = ny;
                MC.player.yBodyRotO = ny;
            }
            MC.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                ny, np, MC.player.onGround(), MC.player.horizontalCollision));
            try {
                action.run();
            } finally {
                if (!clientRotate) {

                    MC.player.setYRot(oldYaw);
                    MC.player.setXRot(oldPitch);
                    MC.player.yRotO = oldYawO;
                    MC.player.xRotO = oldPitchO;
                    MC.player.yHeadRot = oldHeadYaw;
                    MC.player.yHeadRotO = oldHeadYawO;
                    MC.player.yBodyRot = oldBodyYaw;
                    MC.player.yBodyRotO = oldBodyYawO;
                }
            }
        }

        private static double yawTo(Vec3 target) {
            double dx = target.x - MC.player.getX();
            double dz = target.z - MC.player.getZ();
            return Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        }

        private static double pitchTo(Vec3 target) {
            Vec3 eye = MC.player.getEyePosition();
            double dx = target.x - eye.x;
            double dy = target.y - eye.y;
            double dz = target.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            return -Math.toDegrees(Math.atan2(dy, horiz));
        }

        private void cleanupPlacedFluid() {

            if (DihBlinkManager.holdsActionsWithoutMovement()) return;
            if (!placedFluid || placedTarget == null || MC.level == null || MC.player == null || MC.gameMode == null) return;
            if (++placedTimer > 20) {
                placedFluid = false;
                placedTarget = null;
                placedTimer = 0;
                return;
            }
            boolean insidePlaced = MC.player.getInBlockState().is(Blocks.WATER) || MC.player.getInBlockState().is(Blocks.POWDER_SNOW);
            boolean snowBelow = MC.level.getBlockState(MC.player.blockPosition().below()).is(Blocks.POWDER_SNOW) && MC.player.fallDistance == 0;
            if (!insidePlaced && !snowBelow) return;
            int oldSlot = MC.player.getInventory().getSelectedSlot();
            int bucket = findHotbarItem(Items.BUCKET);
            if (bucket < 0) return;

            if (!DihHandArbiter.beginHandPacketGroup(id())) return;
            try {
                DihInventoryHelper.selectHotbarSlot(MC, bucket);
                try {

                    Vec3 hitVec = Vec3.atCenterOf(placedTarget);

                    BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, placedTarget, false);
                    if (ModuleRegistry.shouldCancelUseExcept(hit, InteractionHand.MAIN_HAND, id())) return;
                    if (!DihPlacementTick.claim(id())) return;
                    rotateAndAct(yawTo(hitVec), pitchTo(hitVec), () -> {
                        MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
                    });
                    placedFluid = false;
                    placedTarget = null;
                    placedTimer = 0;
                } finally {
                    DihInventoryHelper.selectHotbarSlot(MC, oldSlot);
                }
            } finally {
                DihHandArbiter.endHandPacketGroup(id());
            }
        }

        private void centerPlayer() {

            double x = Math.floor(MC.player.getX()) + 0.5;
            double z = Math.floor(MC.player.getZ()) + 0.5;
            MC.player.setPos(x, MC.player.getY(), z);

            if (!DihBlinkManager.isActive() && MC.getConnection() != null) {
                MC.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    MC.player.getX(), MC.player.getY(), MC.player.getZ(),
                    MC.player.onGround(), MC.player.horizontalCollision));
            }
        }

        @SuppressWarnings("deprecation")
        private boolean isAboveWater() {

            if (MC.level == null || MC.player == null) return false;
            BlockPos.MutableBlockPos pos = MC.player.blockPosition().mutable();
            int bottom = MC.level.getMinY();
            while (pos.getY() > bottom) {
                net.minecraft.world.level.block.state.BlockState state = MC.level.getBlockState(pos);
                if (DihBlocks.blocksMotion(state)) break;
                if (state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) return true;
                pos.move(Direction.DOWN);
            }
            return false;
        }

        private int findHotbarItem() {
            Item item = placeItem();
            return findHotbarItem(item);
        }

        private int findHotbarBlockItem() {
            Item preferred = placeItem();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(preferred) && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) return i;
            }
            return -1;
        }

        private int findHotbarItem(Item item) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = MC.player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(item)) return i;
            }
            return -1;
        }

        private Item placeItem() {
            return switch (choice("placed-item")) {
                case "PowderSnow" -> Items.POWDER_SNOW_BUCKET;
                case "HayBale" -> Items.HAY_BLOCK;
                case "Cobweb" -> Items.COBWEB;
                case "SlimeBlock" -> Items.SLIME_BLOCK;
                default -> MC.level != null && "the_nether".equals(MC.level.dimension().identifier().getPath()) ? Items.POWDER_SNOW_BUCKET : Items.WATER_BUCKET;
            };
        }
    }

    static final class FastUseModule extends Module {

        private static final int EXP_ROTATION_RESET_TICKS = 5;

        private static final ValueRange DEFAULT_CPS = new ValueRange(8, 12);

        private final dihclient.util.DihClickPacer usePacer = new dihclient.util.DihClickPacer();
        private boolean manualExpActive;
        private boolean manualBlockActive;
        private int expRotationResetTicks;
        private DihRotationUtil.Rotation expSilentRotation;
        private DihRotationUtil.Rotation serverRotation;

        FastUseModule() {
            super("fast-use", "FastUse", ModuleCategory.PLAYER, "Speeds up item use.");
            fastUseInstance = this;
            add(new BoolSetting("exp", "Fast EXP", true).group("EXP").description("Speed experience bottles."));
            add(new RangeSetting("exp-cps", "EXP CPS", DEFAULT_CPS, 5, 20, 0.5)
                .group("EXP")
                .description("Throws per second while you hold use")
                .minSeparation(0.5).visibleWhen(() -> bool("exp")).build());
            add(new IntSetting("exp-pitch", "EXP Pitch", 90, 45, 90, 1).unit("°")
                .group("EXP")
                .description("How far down it aims the bottles; 90 is straight at your feet")
                .visibleWhen(() -> bool("exp")).build());
            add(new BoolSetting("exp-only-damaged", "Only When Damaged", false)
                .group("EXP")
                .description("Only speeds bottles up while something you wear or hold is damaged")
                .visibleWhen(() -> bool("exp")).build());

            add(new BoolSetting("blocks", "Fast Blocks", true).group("Blocks").description("Speed block use."));
            add(new RangeSetting("blocks-cps", "Blocks CPS", DEFAULT_CPS, 5, 20, 0.5)
                .group("Blocks")
                .description("Placements per second while you hold use")
                .minSeparation(0.5).visibleWhen(() -> bool("blocks")).build());
            add(new ChoiceSetting("block-filter", "Block Filter", "Off", "Off", "Whitelist", "Blacklist")
                .group("Blocks")
                .description("Limit which blocks get placed faster")
                .visibleWhen(() -> bool("blocks")).build());
            add(RegistryListSetting.placeableBlocks("block-list", "Block List", "")
                .group("Blocks")
                .description("Blocks the filter uses")
                .visibleWhen(() -> bool("blocks") && !"Off".equals(choice("block-filter"))).build());
        }

        private boolean blockAllowed(ItemStack stack) {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            String filter = choice("block-filter");
            if ("Off".equals(filter)) return true;
            String id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            boolean listed = false;
            for (String entry : list("block-list")) {
                if (RegistryListCodec.matches(id, entry)) {
                    listed = true;
                    break;
                }
            }
            return "Whitelist".equals(filter) == listed;
        }

        private boolean expWanted() {
            if (!bool("exp")) return false;
            if (!bool("exp-only-damaged")) return true;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (MC.player.getItemBySlot(slot).isDamaged()) return true;
            }
            return false;
        }

        @Override
        public void preMovementTick() {
            if (MC == null || MC.player == null || MC.gameMode == null || MC.getConnection() == null) {
                resetExpState();
                return;
            }

            if (AutoTotemModule.operationActive()) {
                stopManualUse();
                return;
            }

            if (MC.player.isUsingItem()) {
                stopManualUse();
                return;
            }

            InteractionHand expHand = expWanted() && canUseManualUse() && !mainHandWouldPreempt(true)
                ? experienceBottleHand() : null;
            if (expHand != null && isPhysicalUseHeld()) {
                activateManualUse(true);
                handleExperienceBottles(expHand);
                return;
            }

            if (bool("blocks") && canUseManualUse() && isHoldingBlockItem() && isPhysicalUseHeld()
                && !mainHandWouldPreempt(false)) {
                activateManualUse(false);
                tickExpRotationReset();
                handleBlock();
                return;
            }

            stopManualUse();
        }

        @Override
        public void onDisable() {
            resetExpState();
        }

        @Override
        public void onGameJoin() {
            resetExpState();
        }

        @Override
        public void onGameLeft() {
            resetExpState();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (packet instanceof ServerboundMovePlayerPacket movement && movement.hasRotation()
                && MC != null && MC.player != null) {
                DihRotationUtil.Rotation base = serverRotation();
                serverRotation = new DihRotationUtil.Rotation(
                    movement.getYRot(base.yaw()), movement.getXRot(base.pitch()));
            }
            return false;
        }

        private void handleExperienceBottles(InteractionHand preferredHand) {
            if (expSilentRotation == null) {
                DihRotationUtil.Rotation accepted = serverRotation();

                expSilentRotation = DihRotationUtil.normalizeToSensitivity(
                    new DihRotationUtil.Rotation(accepted.yaw(), (float) integer("exp-pitch")), accepted);

                expRotationResetTicks = EXP_ROTATION_RESET_TICKS;
            }

            tickExpRotationReset();

            if (DihSilentAim.packetRotation() == null && !DihSilentAim.scaffoldOwnsRotation()) {
                expRotationResetTicks = EXP_ROTATION_RESET_TICKS;
            }

            if (!sameRotation(expSilentRotation, serverRotation())) return;

            if (bottleStillHeld(preferredHand) != null && scheduleUseClick()) {
                DihInputClicker.queueFastExpUseClick();
            }
        }

        private boolean ownsManualInput() {
            return manualExpActive || manualBlockActive;
        }

        private boolean beginManualInputClick() {

            if (DihSilentAim.packetRotation() != null || DihSilentAim.scaffoldOwnsRotation()
                || ScaffoldModule.reservesRageInput() || AutoTotemModule.operationActive()) {
                return false;
            }
            if (manualExpActive) {
                return experienceBottleHand() != null
                    && activeExpRotation() != null
                    && DihInputClicker.beginFastExpUseClick();
            }
            return manualBlockActive
                && isHoldingBlockItem()
                && DihInputClicker.beginFastBlockUseClick();
        }

        private boolean canUseManualUse() {
            return MC.options != null
                && MC.level != null
                && MC.gui.screen() == null
                && MC.gui.overlay() == null
                && !MC.player.isDeadOrDying()
                && !MC.player.isSpectator()
                && !PackHideState.isHardLocked()
                && !PackFreecamState.isActive()
                && !dihclient.util.DihRemoteView.isActive()
                && !dihclient.util.multi.MultiPilot.isActive()
                && !dihclient.util.multi.PacketTeleportController.ownsMainMovement()
                && !MacroExecutor.isRunning();
        }

        private boolean isPhysicalUseHeld() {
            return MC.options != null
                && DihKeyMappingBridge.of(MC.options.keyUse).dih$isActuallyDown();
        }

        private InteractionHand experienceBottleHand() {
            if (MC.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.MAIN_HAND;
            if (MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.OFF_HAND;
            return null;
        }

        private InteractionHand bottleStillHeld(InteractionHand preferredHand) {
            if (MC.player.getItemInHand(preferredHand).is(Items.EXPERIENCE_BOTTLE)) return preferredHand;
            return experienceBottleHand();
        }

        private void activateManualUse(boolean experience) {
            if (experience && manualExpActive || !experience && manualBlockActive) return;
            stopManualUse();
            manualExpActive = experience;
            manualBlockActive = !experience;
            usePacer.reset();
        }

        private ValueRange useBand() {
            return ValueRange.parse(value(manualExpActive ? "exp-cps" : "blocks-cps"), DEFAULT_CPS);
        }

        private boolean scheduleUseClick() {
            ValueRange band = useBand();
            return usePacer.shouldClick(band.min(), band.max());
        }

        private void stopManualUse() {
            if (manualExpActive) DihInputClicker.cancelFastExpUseClick();
            if (manualBlockActive) DihInputClicker.cancelFastBlockUseClick();
            manualExpActive = false;
            manualBlockActive = false;
            usePacer.reset();
            tickExpRotationReset();
        }

        private void tickExpRotationReset() {
            if (expRotationResetTicks <= 0) return;
            expRotationResetTicks--;
            if (expRotationResetTicks <= 0) expSilentRotation = null;
        }

        private void resetExpState() {
            DihInputClicker.cancelFastExpUseClick();
            DihInputClicker.cancelFastBlockUseClick();
            manualExpActive = false;
            manualBlockActive = false;
            usePacer.reset();
            expRotationResetTicks = 0;
            expSilentRotation = null;
            serverRotation = null;
        }

        private DihRotationUtil.Rotation serverRotation() {
            if (serverRotation != null) return serverRotation;
            if (MC != null && MC.player != null) {
                return new DihRotationUtil.Rotation(MC.player.getYRot(), MC.player.getXRot());
            }
            return new DihRotationUtil.Rotation(0.0F, 0.0F);
        }

        private DihRotationUtil.Rotation activeExpRotation() {
            return expRotationResetTicks > 0 ? expSilentRotation : null;
        }

        private static boolean sameRotation(DihRotationUtil.Rotation first,
                                            DihRotationUtil.Rotation second) {
            return first != null && second != null
                && Float.compare(first.yaw(), second.yaw()) == 0
                && Float.compare(first.pitch(), second.pitch()) == 0;
        }

        private void handleBlock() {

            if (activeExpRotation() != null) return;
            if (DihSilentAim.packetRotation() != null || DihSilentAim.scaffoldOwnsRotation()) return;
            if (scheduleUseClick()) DihInputClicker.queueFastBlockUseClick();
        }

        private boolean mainHandWouldPreempt(boolean bottlesIntended) {
            ItemStack main = MC.player.getMainHandItem();
            if (main.isEmpty()) return false;
            if (main.getItem() instanceof BlockItem) {

                return bottlesIntended && !MC.player.onGround()
                    && MC.player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE);
            }
            if (bottlesIntended && main.is(Items.EXPERIENCE_BOTTLE)) return false;
            if (main.getUseAnimation() != ItemUseAnimation.NONE) return true;

            return main.is(Items.EXPERIENCE_BOTTLE) || main.is(Items.ENDER_PEARL)
                || main.is(Items.SNOWBALL) || main.is(Items.EGG)
                || main.is(Items.FISHING_ROD) || main.getItem() instanceof net.minecraft.world.item.BucketItem
                || main.is(Items.WIND_CHARGE) || main.is(Items.FIRE_CHARGE)
                || main.is(Items.ENDER_EYE) || main.is(Items.SPLASH_POTION) || main.is(Items.LINGERING_POTION);
        }

        private boolean isHoldingBlockItem() {
            // The main hand is used first, so its block decides when it holds one.
            ItemStack main = MC.player.getMainHandItem();
            return main.getItem() instanceof BlockItem ? blockAllowed(main) : blockAllowed(MC.player.getOffhandItem());
        }
    }

    static final class FastBreakModule extends Module {

        FastBreakModule() {
            super("fast-break", "FastBreak", ModuleCategory.PLAYER, "Speeds up block breaking.");
            add(new ChoiceSetting("mode", "Mode", "Damage", "Normal", "Haste", "Damage", "Packet")
                .group("General")
                .description("Break method.")
                .build());
            add(new BoolSetting("no-break-delay", "No Break Delay", false)
                .group("General")
                .description("Skips the short wait between blocks; Packet mode always does")
                .visibleWhen(() -> !packetMode(this))
                .build());

            add(new ChoiceSetting("blocks-filter", "Blocks Filter", "Blacklist", "Whitelist", "Blacklist")
                .group("Blocks")
                .visibleWhen(() -> !"Haste".equals(value("mode")))
                .description("List mode.")
                .build());
            add(RegistryListSetting.blocks("blocks", "Blocks", "")
                .group("Blocks")
                .visibleWhen(() -> !"Haste".equals(value("mode")))
                .description("Selected blocks.")
                .build());

            add(new DoubleSetting("modifier", "Modifier", 1.4, 0.0, 999.0, 0.1)
                .group("Normal")
                .sliderRange(0.0, 10.0)
                .visibleWhen(() -> normalMode(this))
                .description("Speed multiplier.")
                .build());
            add(new BoolSetting("no-air-penalty", "No Air Penalty", false)
                .group("Normal")
                .visibleWhen(() -> normalMode(this))
                .description("Mines as fast in the air as on the ground")
                .build());
            add(new BoolSetting("no-water-penalty", "No Water Penalty", false)
                .group("Normal")
                .visibleWhen(() -> normalMode(this))
                .description("Mines as fast underwater as on land")
                .build());
            add(new BoolSetting("no-fatigue", "No Mining Fatigue", false)
                .group("Normal")
                .visibleWhen(() -> normalMode(this))
                .description("Cancels the slowdown from Mining Fatigue")
                .build());

            add(new IntSetting("haste-amplifier", "Haste Amplifier", 2, 1, 10, 1)
                .group("Haste")
                .visibleWhen(() -> hasteMode(this))
                .description("Haste amplifier to apply.")
                .build());

            add(new BoolSetting("instamine", "Instamine", true)
                .group("Damage")
                .visibleWhen(() -> damageMode(this))
                .description("Finish blocks early.")
                .build());
            add(new DoubleSetting("finish-progress", "Finish At", 0.7, 0.5, 1.0, 0.05)
                .group("Damage")
                .visibleWhen(() -> damageMode(this) && bool("instamine"))
                .description("Breaks the block once mining is this far along")
                .build());
            add(new DoubleSetting("instant-threshold", "Instant Threshold", 0.5, 0.3, 1.0, 0.05)
                .group("Damage")
                .visibleWhen(() -> damageMode(this) && bool("instamine"))
                .description("Breaks on the first click when one tick of mining does at least this much")
                .build());
            add(new BoolSetting("grim-bypass", "Grim Bypass", false)
                .group("Damage")
                .visibleWhen(() -> damageMode(this))
                .description("Abort after stop.")
                .build());

            add(new IntSetting("activation-chance", "Activation Chance", 100, 0, 100, 1)
                .group("Packet")
                .visibleWhen(() -> packetMode(this))
                .unit("%").formatter(v -> v + "%")
                .description("% to fast-break.")
                .build());
        }

        private final Random fastBreakRandom = new Random();
        private BlockPos lastFastBreakPos;
        private boolean fastBreakActivated;

        @Override
        public String info() {
            return choice("mode");
        }

        @Override
        public void onDisable() {
            removeHaste();
            lastFastBreakPos = null;
            fastBreakActivated = false;
        }

        @Override
        public void tick() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            String mode = choice("mode");
            if ("Haste".equals(mode)) {
                tickHasteMode();
            } else {
                removeHaste();
            }

            if ("Damage".equals(mode)) tickDamageMode();
            if (("Packet".equals(mode) || bool("no-break-delay"))
                && MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor) {
                accessor.dih$setDestroyDelay(0);
            }
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (dihclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Damage".equals(choice("mode")) || !bool("grim-bypass") || MC.getConnection() == null) return false;
            if (packet instanceof ServerboundPlayerActionPacket action
                && action.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                MC.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    action.getPos().above(),
                    action.getDirection()
                ));
            }
            return false;
        }

        @Override
        public boolean onStartDestroyBlock(BlockPos pos, Direction direction) {
            if (dihclient.util.multi.MultiPilot.isActive()) return false;
            if (!"Damage".equals(choice("mode")) || !bool("instamine")) return false;
            if (MC.player == null || MC.level == null || MC.gameMode == null || MC.getConnection() == null || pos == null) return false;
            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return false;
            if (state.getDestroyProgress(MC.player, MC.level, pos) <= (float) decimal("instant-threshold")) return false;
            if (!(MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor)) return false;

            Direction dir = direction == null ? Direction.UP : direction;
            MC.gameMode.destroyBlock(pos);
            accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos.immutable(), dir, sequence));
            accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos.immutable(), dir, sequence));
            return true;
        }

        @Override
        public void onBlockBreakingProgress(BlockPos pos, Direction direction) {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (!"Packet".equals(choice("mode"))) return;

            if (MC.player == null || MC.level == null || MC.getConnection() == null || pos == null) return;
            if (!(MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor)) return;
            if (accessor.dih$getDestroyProgress() >= 1.0f) return;

            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return;

            if (!pos.equals(lastFastBreakPos)) {
                lastFastBreakPos = pos.immutable();
                fastBreakActivated = fastBreakRandom.nextDouble() * 100.0 < Math.max(0, integer("activation-chance"));
            }
            if (!fastBreakActivated) return;

            Direction dir = direction == null ? Direction.UP : direction;
            MC.getConnection().send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos.immutable(), dir));
        }

        @Override
        public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {

            return false;
        }

        float modifyNormalDestroyProgress(float original, BlockState state, BlockPos pos) {
            if (!"Normal".equals(choice("mode"))) return original;
            if (MC.player == null || MC.level == null || pos == null) return original;
            if (!canMine(state, pos) || !passesBlockFilter(state)) return original;
            return (float) (original * Math.max(0.0, decimal("modifier")) * penaltyCompensation());
        }

        /** Undoes the vanilla slowdowns that the Normal-mode options turn off. */
        private double penaltyCompensation() {
            double factor = 1.0;
            if (bool("no-air-penalty") && !MC.player.onGround()) factor *= 5.0;
            if (bool("no-water-penalty") && MC.player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
                double submerged = MC.player.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.SUBMERGED_MINING_SPEED);
                if (submerged > 0.0 && submerged < 1.0) factor /= submerged;
            }
            MobEffectInstance fatigue = bool("no-fatigue") ? MC.player.getEffect(MobEffects.MINING_FATIGUE) : null;
            if (fatigue != null) {
                factor /= switch (fatigue.getAmplifier()) {
                    case 0 -> 0.3;
                    case 1 -> 0.09;
                    case 2 -> 0.0027;
                    default -> 8.1E-4;
                };
            }
            return factor;
        }

        boolean usesNormalDestroyModifier() {
            return "Normal".equals(choice("mode"));
        }

        private void tickHasteMode() {
            if (MC.player == null) return;
            int amplifier = Math.max(1, integer("haste-amplifier"));
            MobEffectInstance haste = MC.player.getEffect(MobEffects.HASTE);
            if (haste == null || !haste.showIcon() || haste.getAmplifier() <= amplifier - 1) {
                MC.player.addEffect(new MobEffectInstance(MobEffects.HASTE, -1, amplifier - 1, false, false, false), null);
            }
        }

        private void removeHaste() {
            if (MC.player == null) return;
            MobEffectInstance haste = MC.player.getEffect(MobEffects.HASTE);
            if (haste != null && !haste.showIcon()) MC.player.removeEffect(MobEffects.HASTE);
        }

        private void tickDamageMode() {
            if (MC.player == null || MC.level == null || MC.gameMode == null || !bool("instamine")) return;
            if (!(MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor)) return;
            BlockPos pos = accessor.dih$getDestroyBlockPos();
            if (pos == null || accessor.dih$getDestroyProgress() <= 0.0f) return;
            finishDamageIfReady(pos);
        }

        private void finishDamageIfReady(BlockPos pos) {
            if (MC.player == null || MC.level == null || MC.gameMode == null || pos == null) return;
            if (!(MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor)) return;
            BlockState state = MC.level.getBlockState(pos);
            if (!canMine(state, pos) || !passesBlockFilter(state)) return;
            float progress = accessor.dih$getDestroyProgress();
            float delta = state.getDestroyProgress(MC.player, MC.level, pos);
            if (progress > 0.0f && progress + delta >= (float) decimal("finish-progress")) {
                accessor.dih$setDestroyProgress(1.0f);
            }
        }

        private boolean canMine(BlockState state, BlockPos pos) {
            return state != null && !state.isAir() && MC.level != null && state.getDestroySpeed(MC.level, pos) >= 0.0f;
        }

        private boolean passesBlockFilter(BlockState state) {
            if (state == null) return false;
            List<String> configured = list("blocks");
            if (configured.isEmpty()) return "Blacklist".equals(choice("blocks-filter"));
            String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().toLowerCase(Locale.ROOT);
            String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            boolean listed = false;
            for (String raw : configured) {
                String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                if (value.equals(id) || value.equals(path)) {
                    listed = true;
                    break;
                }
            }
            return "Whitelist".equals(choice("blocks-filter")) ? listed : !listed;
        }

        private static boolean normalMode(Module module) {
            return module != null && "Normal".equals(module.value("mode"));
        }

        private static boolean hasteMode(Module module) {
            return module != null && "Haste".equals(module.value("mode"));
        }

        private static boolean damageMode(Module module) {
            return module != null && "Damage".equals(module.value("mode"));
        }

        private static boolean packetMode(Module module) {
            return module != null && "Packet".equals(module.value("mode"));
        }
    }

    static final class AimAssistModule extends Module {
        private static final int TARGET_SWITCH_CONFIRM_TICKS = 4;
        private final QuantizedRotationSmoother rotationSmoother = new QuantizedRotationSmoother();
        private DihRotationUtil.Rotation lastTargetRotation;
        private DihRotationUtil.Rotation filteredTargetRotation;
        private float sampledHorizontalSpeed = 0.4f;
        private float sampledVerticalSpeed = 0.3f;
        private float sampledDirectionChange;
        private int trackedTargetId = Integer.MIN_VALUE;
        private int switchCandidateId = Integer.MIN_VALUE;
        private int switchCandidateTicks;
        private String cachedEntityListSource = "";
        private Set<String> cachedEntityIds = Set.of();
        private final CombatFilters.IdSet heldItemIds = new CombatFilters.IdSet();

        AimAssistModule() {
            super("aim-assist", "AimAssist", ModuleCategory.COMBAT, "Smoothly assists your aim toward configured entities.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
                .group("General").description("Entity types to aim at.").build());
            add(new ChoiceSetting("priority", "Priority", "Direction", "Direction", "Type", "Health", "Distance", "HurtTime", "Age")
                .group("General").description("Who gets aimed at first.").build());
            add(new ChoiceSetting("axis", "Axis", "Both", "Both", "Horizontal", "Vertical")
                .group("General").description("Which way your view is turned.").build());
            add(new BoolSetting("only-on-click", "Only On Click", false)
                .group("General").description("Only works while you hold the attack key.").build());
            add(new BoolSetting("pause-mining", "Pause While Mining", true)
                .group("General").description("Stops while you break a block.").build());
            add(new BoolSetting("held-items-only", "Held Items Only", false)
                .group("General").description("Only with listed items.").build());
            add(RegistryListSetting.items("held-items", "Held Items", CombatFilters.DEFAULT_WEAPONS)
                .group("General").description("Main-hand items that turn on the assist.")
                .visibleWhen(() -> bool("held-items-only")).build());

            add(new DoubleSetting("range", "Range", 4.5, 1.0, 8.0, 0.1)
                .group("Targets").description("Farthest target to aim at, in blocks.").build());
            add(new IntSetting("fov", "FOV", 80, 0, 180, 1)
                .group("Targets").description("How far off your crosshair a target may be, in degrees.").build());
            add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1)
                .group("Targets").description("Skips targets flashing from a hit for longer than this, in ticks.").build());
            add(new IntSetting("switch-delay", "Switch Delay", TARGET_SWITCH_CONFIRM_TICKS, 0, 20, 1)
                .group("Targets").description("Ticks a better target must stay better before the aim moves to it.").build());
            add(new BoolSetting("through-walls", "Through Walls", false)
                .group("Targets").description("Aims at targets behind blocks too.").build());
            add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
                .group("Targets").description("Skips invisible entities.").build());
            add(new BoolSetting("ignore-naked", "Ignore Naked", false)
                .group("Targets").description("Skips players wearing no armor.").build());
            add(new BoolSetting("ignore-named", "Ignore Named", false)
                .group("Targets").description("Skips mobs with a name tag.").build());
            add(new BoolSetting("ignore-babies", "Ignore Babies", false)
                .group("Targets").description("Skips baby mobs.").build());
            add(new ChoiceSetting("ignore-pets", "Ignore Pets", "Yours", "Off", "Yours", "All")
                .group("Targets").description("Skips tamed animals: yours, anyone's, or none.").build());
            add(new IntSetting("max-health", "Max Health", 0, 0, 100, 1)
                .group("Targets").description("Skips targets with more health than this; 0 is no limit.").build());

            add(new ChoiceSetting("target-point", "Target Point", "Nearest", "Nearest", "Center", "Head", "Body", "Feet")
                .group("Aim").description("Where on the target to aim.").build());
            add(new IntSetting("safe-zone", "Safe Zone", 5, 0, 100, 1)
                .group("Aim")
                .formatter(v -> v + "%")
                .description("Deadzone around aim point.")
                .build());

            add(new ChoiceSetting("smooth-mode", "Smooth", "Interpolation", "Interpolation", "Instant").group("Smoothing")
                .description("Interpolation eases toward the target; Instant snaps to it.").build());
            add(new IntSetting("horizontal-speed", "Horizontal", 40, 1, 100, 1).group("Smoothing").formatter(v -> v + "%")
                .description("How fast the view turns sideways.").visibleWhen(() -> usesSmoothing(this)).build());
            add(new IntSetting("vertical-speed", "Vertical", 10, 1, 100, 1).group("Smoothing").formatter(v -> v + "%")
                .description("How fast the view turns up and down.").visibleWhen(() -> usesSmoothing(this)).build());
            add(new IntSetting("direction-factor", "Direction Factor", 100, 0, 100, 1).group("Smoothing").formatter(v -> v + "%")
                .description("How much a target changing direction slows the turn.").visibleWhen(() -> usesSmoothing(this)).build());
            add(new DoubleSetting("midpoint", "Midpoint", 0.35, 0.0, 1.0, 0.05).group("Smoothing")
                .description("Where in each turn the view moves fastest.").visibleWhen(() -> usesSmoothing(this)).build());
        }

        private boolean paused() {
            if (bool("pause-mining") && MC.gameMode != null && MC.gameMode.isDestroying()) return true;
            if (bool("only-on-click") && !DihKeyMappingBridge.of(MC.options.keyAttack).dih$isActuallyDown()) return true;
            return bool("held-items-only")
                && !CombatFilters.holds(MC.player.getMainHandItem(), heldItemIds, list("held-items"));
        }

        private static boolean usesSmoothing(Module module) {
            return !"Instant".equals(module.value("smooth-mode"));
        }

        @Override
        public void onDisable() {
            clearAimState();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || DihSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || AutoTotemModule.operationActive()
                || paused()) {
                clearAimState();
                return;
            }
            LivingEntity target = selectTarget();
            if (target == null) {
                clearAimState();
                return;
            }
            Vec3 eyes = MC.player.getEyePosition();
            Vec3 point = aimPoint(eyes, target);
            if (point == null) {
                clearAimState();
                return;
            }
            DihRotationUtil.Rotation wanted = DihRotationUtil.lookingAt(point, eyes);
            DihRotationUtil.Rotation current = DihRotationUtil.playerRotation(MC.player);
            boolean switchedTarget = trackedTargetId != target.getId();
            if (switchedTarget) {
                trackedTargetId = target.getId();
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                sampledDirectionChange = 0.0f;
                sampledHorizontalSpeed = configuredPercent("horizontal-speed");
                sampledVerticalSpeed = configuredPercent("vertical-speed");
                filteredTargetRotation = filterTargetRotation(current, wanted, true);
                rotationSmoother.reset((((long) target.getId()) << 32) ^ System.nanoTime());
                DihMouseInputSimulator.clear(DihMouseInputSimulator.Source.AIM_ASSIST);
            } else {
                float measuredDirectionChange = lastTargetRotation == null ? 0.0f
                    : (DihRotationUtil.angleTo(lastTargetRotation, wanted) / 180.0f)
                        * (integer("direction-factor") / 100.0f);
                sampledDirectionChange = approach(sampledDirectionChange, measuredDirectionChange, 0.2f);
                sampledHorizontalSpeed = approach(sampledHorizontalSpeed, configuredPercent("horizontal-speed"), 0.25f);
                sampledVerticalSpeed = approach(sampledVerticalSpeed, configuredPercent("vertical-speed"), 0.25f);
                filteredTargetRotation = filterTargetRotation(current, wanted, false);
            }

            DihRotationUtil.Rotation guidance = "Instant".equals(choice("smooth-mode"))
                ? wanted
                : filteredTargetRotation;
            String axis = choice("axis");
            if (insideSafeZone(current, wanted, eyes, point, target, axis)) {
                rotationSmoother.halt();
                DihMouseInputSimulator.clear(DihMouseInputSimulator.Source.AIM_ASSIST);
                lastTargetRotation = wanted;
                return;
            }
            if ("Instant".equals(choice("smooth-mode"))) {
                DihRotationUtil.Rotation applied = new DihRotationUtil.Rotation(
                    "Vertical".equals(axis) ? current.yaw() : guidance.yaw(),
                    "Horizontal".equals(axis) ? current.pitch() : guidance.pitch()
                );
                DihMouseInputSimulator.queueRotation(DihMouseInputSimulator.Source.AIM_ASSIST, current, applied);
            } else {
                QuantizedRotationSmoother.Step step = rotationSmoother.step(
                    DihRotationUtil.angleDifference(guidance.yaw(), current.yaw()),
                    DihRotationUtil.angleDifference(guidance.pitch(), current.pitch()),
                    DihRotationUtil.mouseDegreesPerRawInput(),
                    sampledHorizontalSpeed,
                    sampledVerticalSpeed,
                    sampledDirectionChange,
                    (float) decimal("midpoint"),
                    !"Vertical".equals(axis),
                    !"Horizontal".equals(axis)
                );
                DihMouseInputSimulator.queueRotationCounts(
                    DihMouseInputSimulator.Source.AIM_ASSIST,
                    step.yawCounts(),
                    step.pitchCounts()
                );
            }
            lastTargetRotation = wanted;
        }

        private void clearAimState() {
            lastTargetRotation = null;
            filteredTargetRotation = null;
            trackedTargetId = Integer.MIN_VALUE;
            switchCandidateId = Integer.MIN_VALUE;
            switchCandidateTicks = 0;
            rotationSmoother.reset();
            DihMouseInputSimulator.clear(DihMouseInputSimulator.Source.AIM_ASSIST);
        }

        private LivingEntity selectTarget() {
            double rangeSq = decimal("range") * decimal("range");
            DihRotationUtil.Rotation current = DihRotationUtil.playerRotation(MC.player);
            Vec3 eyes = MC.player.getEyePosition();
            String priority = choice("priority");
            LivingEntity best = null;
            LivingEntity tracked = null;
            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living)) continue;
                if (!valid(living, rangeSq, current)) continue;
                if (living.getId() == trackedTargetId) tracked = living;
                if (best == null || compareTargets(living, best, priority, current, eyes) < 0) best = living;
            }
            if (tracked == null || best == null || best == tracked) {
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                return tracked == null ? best : tracked;
            }
            if (compareTargets(best, tracked, priority, current, eyes) >= 0) {
                switchCandidateId = Integer.MIN_VALUE;
                switchCandidateTicks = 0;
                return tracked;
            }
            if (switchCandidateId == best.getId()) switchCandidateTicks++;
            else {
                switchCandidateId = best.getId();
                switchCandidateTicks = 1;
            }
            if (switchCandidateTicks < integer("switch-delay")) return tracked;
            switchCandidateId = Integer.MIN_VALUE;
            switchCandidateTicks = 0;
            return best;
        }

        private boolean valid(LivingEntity entity, double rangeSq, DihRotationUtil.Rotation current) {
            if (entity == MC.player || entity.isRemoved() || !entity.isAlive()) return false;
            if (DihAntiBot.suppress(entity)) return false;
            if (TeamsModule.combatExcluded(entity, "aimassist")) return false;
            if (entity.hurtTime > integer("hurt-time")) return false;
            if (!matchesEntity(entity)) return false;
            if (!passesTargetFilters(entity)) return false;
            if (entity.distanceToSqr(MC.player) > rangeSq) return false;
            DihRotationUtil.Rotation toEntity = DihRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), MC.player.getEyePosition());
            return DihRotationUtil.angleTo(current, toEntity) <= integer("fov");
        }

        private boolean passesTargetFilters(LivingEntity entity) {
            boolean player = entity instanceof net.minecraft.world.entity.player.Player;
            if (bool("ignore-invisible") && entity.isInvisible()) return false;
            if (bool("ignore-naked") && player && CombatFilters.wearsNoArmor(entity)) return false;
            if (bool("ignore-named") && !player && entity.hasCustomName()) return false;
            if (bool("ignore-babies") && entity.isBaby()) return false;
            if (CombatFilters.ignoredPet(entity, choice("ignore-pets"), MC.player)) return false;
            int maxHealth = integer("max-health");
            return maxHealth <= 0 || entity.getHealth() + entity.getAbsorptionAmount() <= maxHealth;
        }

        private int compareTargets(LivingEntity a, LivingEntity b, String priority, DihRotationUtil.Rotation current, Vec3 eyes) {
            return switch (priority) {
                case "Type" -> Integer.compare(typeWeight(a), typeWeight(b));
                case "Health" -> Float.compare(a.getHealth() + a.getAbsorptionAmount(), b.getHealth() + b.getAbsorptionAmount());
                case "Distance" -> Double.compare(a.distanceToSqr(MC.player), b.distanceToSqr(MC.player));
                case "HurtTime" -> Integer.compare(a.hurtTime, b.hurtTime);
                case "Age" -> Integer.compare(b.tickCount, a.tickCount);
                default -> Float.compare(crosshairAngle(a, current, eyes), crosshairAngle(b, current, eyes));
            };
        }

        private int typeWeight(LivingEntity entity) {
            if (entity instanceof net.minecraft.world.entity.player.Player) return 0;
            if (entity instanceof net.minecraft.world.entity.monster.Enemy) return 1;
            return 100;
        }

        private float crosshairAngle(LivingEntity entity, DihRotationUtil.Rotation current, Vec3 eyes) {
            DihRotationUtil.Rotation toEntity = DihRotationUtil.lookingAt(entity.getBoundingBox().getCenter(), eyes);
            return DihRotationUtil.angleTo(current, toEntity);
        }

        private boolean matchesEntity(Entity entity) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            Set<String> ids = cachedEntityIds();
            return ids.contains(id) || ids.contains(id.substring(id.indexOf(':') + 1));
        }

        private Set<String> cachedEntityIds() {
            List<String> entries = list("entities");
            String source = String.join("|", entries);
            if (source.equals(cachedEntityListSource)) return cachedEntityIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : entries) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedEntityListSource = source;
            cachedEntityIds = Set.copyOf(normalized);
            return cachedEntityIds;
        }

        private Vec3 aimPoint(Vec3 eyes, LivingEntity entity) {
            if (entity instanceof net.minecraft.world.entity.player.Player) {
                return playerAimPoint(eyes, entity);
            }

            AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
            double cx = (box.minX + box.maxX) * 0.5;
            double cz = (box.minZ + box.maxZ) * 0.5;
            Vec3 preferred = switch (choice("target-point")) {
                case "Center", "Body" -> box.getCenter();
                case "Head" -> entity.getEyePosition();
                case "Feet" -> new Vec3(cx, box.minY + 0.1, cz);
                default -> nearestPoint(eyes, box);
            };
            if (bool("through-walls") || visible(eyes, preferred)) return preferred;
            return bestVisiblePoint(eyes, box);
        }

        private Vec3 playerAimPoint(Vec3 eyes, LivingEntity player) {

            AABB modelBox = player.getBoundingBox();
            Vec3 preferred = playerModelPoint(
                eyes,
                modelBox,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                choice("target-point")
            );
            if (bool("through-walls") || visible(eyes, preferred)) return preferred;
            return bestVisiblePlayerPoint(eyes, modelBox, player.getX(), player.getZ(), preferred.y);
        }

        static Vec3 playerModelPoint(Vec3 eyes,
                                     AABB modelBox,
                                     double modelX,
                                     double eyeY,
                                     double modelZ,
                                     String targetPoint) {
            double height = Math.max(1.0E-9, modelBox.maxY - modelBox.minY);
            double low = modelBox.minY + Math.min(0.1, height * 0.1);
            double high = modelBox.maxY - Math.min(0.1, height * 0.1);
            String point = targetPoint == null ? "nearest" : targetPoint.trim().toLowerCase(java.util.Locale.ROOT);
            double y = switch (point) {
                case "center", "body" -> (modelBox.minY + modelBox.maxY) * 0.5;
                case "head" -> Mth.clamp(Double.isFinite(eyeY) ? eyeY : modelBox.maxY, low, high);
                case "feet" -> low;
                default -> Mth.clamp(eyes == null ? (modelBox.minY + modelBox.maxY) * 0.5 : eyes.y, low, high);
            };
            return new Vec3(modelX, y, modelZ);
        }

        private Vec3 bestVisiblePlayerPoint(Vec3 eyes,
                                            AABB modelBox,
                                            double modelX,
                                            double modelZ,
                                            double preferredY) {
            double height = Math.max(0.001, modelBox.maxY - modelBox.minY);
            Vec3 best = null;
            double bestVerticalDistance = Double.MAX_VALUE;
            for (int iy = 0; iy < 9; iy++) {
                double y = modelBox.minY + height * ((iy + 0.5) / 9.0);
                Vec3 point = new Vec3(modelX, y, modelZ);
                if (!visible(eyes, point)) continue;
                double verticalDistance = Math.abs(y - preferredY);
                if (verticalDistance < bestVerticalDistance) {
                    bestVerticalDistance = verticalDistance;
                    best = point;
                }
            }
            return best;
        }

        private Vec3 nearestPoint(Vec3 eyes, AABB box) {
            Vec3 closest = new Vec3(
                Mth.clamp(eyes.x, box.minX, box.maxX),
                Mth.clamp(eyes.y, box.minY, box.maxY),
                Mth.clamp(eyes.z, box.minZ, box.maxZ)
            );
            if (closest.distanceToSqr(eyes) < 1.0E-6) return box.getCenter();
            return closest;
        }

        private Vec3 bestVisiblePoint(Vec3 eyes, AABB box) {
            DihRotationUtil.Rotation current = DihRotationUtil.playerRotation(MC.player);
            Vec3 best = null;
            float bestAngle = Float.MAX_VALUE;
            for (int ix = 0; ix <= 2; ix++) {
                double x = Mth.lerp(ix / 2.0, box.minX, box.maxX);
                for (int iy = 0; iy <= 2; iy++) {
                    double y = Mth.lerp(iy / 2.0, box.minY, box.maxY);
                    for (int iz = 0; iz <= 2; iz++) {
                        double z = Mth.lerp(iz / 2.0, box.minZ, box.maxZ);
                        Vec3 point = new Vec3(x, y, z);
                        if (!visible(eyes, point)) continue;
                        float angle = DihRotationUtil.angleTo(current, DihRotationUtil.lookingAt(point, eyes));
                        if (angle < bestAngle) {
                            bestAngle = angle;
                            best = point;
                        }
                    }
                }
            }
            return best;
        }

        private boolean visible(Vec3 eyes, Vec3 point) {
            if (MC.level == null || MC.player == null || point == null) return false;
            HitResult hit = MC.level.clip(new ClipContext(eyes, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, MC.player));
            return hit == null || hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(point) < 0.05;
        }

        private boolean insideSafeZone(DihRotationUtil.Rotation current,
                                       DihRotationUtil.Rotation targetRotation,
                                       Vec3 eyes,
                                       Vec3 targetPoint,
                                       LivingEntity target,
                                       String axis) {
            int configured = integer("safe-zone");
            if (configured <= 0 || current == null || targetRotation == null
                || eyes == null || targetPoint == null || target == null) {
                return false;
            }
            if (!crosshairIntersectsTarget(eyes, target)) return false;

            AABB box = target.getBoundingBox().inflate(target.getPickRadius());
            double targetWidth = Math.max(box.maxX - box.minX, box.maxZ - box.minZ);
            double radius = targetWidth * (configured / 100.0);
            double distance = Math.max(1.0E-4, eyes.distanceTo(targetPoint));
            double angularRadius = Math.toDegrees(Math.atan2(radius, distance));
            double hardwareStep = DihRotationUtil.mouseDegreesPerRawInput();
            if (hardwareStep > 0.0 && Double.isFinite(hardwareStep)) {
                angularRadius = Math.max(angularRadius, hardwareStep);
            }

            boolean yawInside = "Vertical".equals(axis)
                || Math.abs(DihRotationUtil.angleDifference(targetRotation.yaw(), current.yaw())) <= angularRadius;
            boolean pitchInside = "Horizontal".equals(axis)
                || Math.abs(DihRotationUtil.angleDifference(targetRotation.pitch(), current.pitch())) <= angularRadius;
            return yawInside && pitchInside;
        }

        private boolean crosshairIntersectsTarget(Vec3 eyes, LivingEntity target) {
            if (MC.player == null || eyes == null || target == null) return false;
            AABB hitbox = target.getBoundingBox().inflate(target.getPickRadius());
            double rayLength = Math.max(decimal("range"), eyes.distanceTo(hitbox.getCenter()) + hitbox.getSize());
            Vec3 rayEnd = eyes.add(MC.player.getViewVector(1.0f).scale(rayLength));
            Optional<Vec3> intersection = hitbox.clip(eyes, rayEnd);
            return intersection.isPresent() && visible(eyes, intersection.get());
        }

        private DihRotationUtil.Rotation filterTargetRotation(DihRotationUtil.Rotation current,
                                                                  DihRotationUtil.Rotation target,
                                                                  boolean switchedTarget) {
            DihRotationUtil.Rotation base = switchedTarget || filteredTargetRotation == null
                ? current
                : filteredTargetRotation;
            float blend = switchedTarget ? 0.35f : 0.5f;
            float yawStep = Math.abs(DihRotationUtil.angleDifference(target.yaw(), base.yaw())) * blend;
            float pitchStep = Math.abs(DihRotationUtil.angleDifference(target.pitch(), base.pitch())) * blend;
            return DihRotationUtil.towardsLinear(base, target, yawStep, pitchStep);
        }

        private float configuredPercent(String option) {
            return Mth.clamp(integer(option) / 100.0f, 0.01f, 1.0f);
        }

        private static float approach(float current, float target, float factor) {
            return current + (target - current) * Mth.clamp(factor, 0.0f, 1.0f);
        }
    }

    static final class AutoClickerModule extends Module {

        private static final ValueRange DEFAULT_CPS = new ValueRange(6, 11);

        private final dihclient.util.DihClickPacer pacer = new dihclient.util.DihClickPacer();
        private final CombatFilters.IdSet heldItemIds = new CombatFilters.IdSet();

        AutoClickerModule() {
            super("auto-clicker", "AutoClicker", ModuleCategory.COMBAT, "Auto clicks or holds");
            add(new RangeSetting("tps", "CPS", DEFAULT_CPS, 5, 20, 0.5).minSeparation(0.5)
                .group("General").description("Clicks per second, picked between these.")
                .visibleWhen(() -> !"Hold".equals(value("mode"))).build());
            add(new ChoiceSetting("mode", "Mode", "Constant", "Constant", "Hold Down", "Hold")
                .group("General").description("Click or hold").build());
            add(new ChoiceSetting("button", "Button", "Left", "Left", "Right")
                .group("General").description("Mouse button").build());

            add(new BoolSetting("break-blocks", "Break Blocks", false)
                .group("Left Click").description("Holds the button instead of clicking while you aim at a block, so it breaks.")
                .visibleWhen(() -> !"Right".equals(value("button")) && !"Hold".equals(value("mode"))).build());
            add(new IntSetting("min-charge", "Min Charge", 0, 0, 100, 5).unit("%")
                .group("Left Click").description("Skips clicks until the weapon cooldown is this full; 0 never waits.")
                .visibleWhen(() -> !"Right".equals(value("button")) && !"Hold".equals(value("mode"))).build());

            add(new BoolSetting("held-items-only", "Held Items Only", false)
                .group("Conditions").description("Only clicks while holding a listed item.").build());
            add(RegistryListSetting.items("held-items", "Held Items", CombatFilters.DEFAULT_WEAPONS)
                .group("Conditions").description("Main-hand items that let it click.")
                .visibleWhen(() -> bool("held-items-only")).build());
            add(new BoolSetting("pause-using", "Pause While Using", false)
                .group("Conditions").description("Stops while you eat, drink, block or draw a bow.").build());
        }

        @Override
        public void onEnable() {
            pacer.reset();
        }

        @Override
        public void onDisable() {
            standDown();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || DihSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || AutoTotemModule.operationActive()
                || bool("pause-using") && MC.player.isUsingItem()
                || bool("held-items-only")
                    && !CombatFilters.holds(MC.player.getMainHandItem(), heldItemIds, list("held-items"))) {
                standDown();
                return;
            }
            String mode = choice("mode");
            if ("Hold".equals(mode)) {

                pacer.reset();
                holdConfiguredButton();
                return;
            }
            boolean left = !"Right".equals(choice("button"));
            if (left && bool("break-blocks") && MC.hitResult != null
                && MC.hitResult.getType() == HitResult.Type.BLOCK) {
                // Constant mode mines by itself; in Hold Down your own held button already does.
                pacer.reset();
                DihInputClicker.setUseHeld(false);
                DihInputClicker.setAttackHeld("Constant".equals(mode));
                return;
            }
            releaseHeldButton();
            if ("Hold Down".equals(mode) && !configuredButtonDown()) {
                pacer.reset();
                return;
            }
            if (left && MC.player.getAttackStrengthScale(0.0f) < integer("min-charge") / 100.0f) return;

            ValueRange band = ValueRange.parse(value("tps"), DEFAULT_CPS);
            if (pacer.shouldClick(band.min(), band.max())) {
                if ("Right".equals(choice("button"))) DihInputClicker.queueUseClick();
                else DihInputClicker.queueAttackClick();
            }
        }

        private void standDown() {
            pacer.reset();
            releaseHeldButton();
        }

        private void holdConfiguredButton() {
            boolean right = "Right".equals(choice("button"));
            DihInputClicker.setAttackHeld(!right);
            DihInputClicker.setUseHeld(right);
        }

        private void releaseHeldButton() {
            DihInputClicker.setAttackHeld(false);
            DihInputClicker.setUseHeld(false);
        }

        private boolean configuredButtonDown() {
            if (MC.options == null) return false;
            return "Right".equals(choice("button"))
                ? DihKeyMappingBridge.of(MC.options.keyUse).dih$isActuallyDown()
                : DihKeyMappingBridge.of(MC.options.keyAttack).dih$isActuallyDown();
        }

    }

    static final class TriggerBotModule extends Module {
        private static final ValueRange DEFAULT_CPS = new ValueRange(8, 12);
        private final dihclient.util.DihClickPacer pacer = new dihclient.util.DihClickPacer();
        private long readyAtNanos;
        private boolean armed;
        private String cachedEntityListSource = "";
        private Set<String> cachedEntityIds = Set.of();
        private final CombatFilters.IdSet heldItemIds = new CombatFilters.IdSet();

        TriggerBotModule() {
            super("trigger-bot", "TriggerBot", ModuleCategory.COMBAT, "Attacks configured entities when your crosshair is on them.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player")
                .group("General").description("Trigger entities").build());
            add(new ChoiceSetting("timing", "Timing", "Cooldown", "Cooldown", "CPS")
                .group("General")
                .description("Cooldown: hit when the swing is charged. CPS: click at a set rate, like AutoClicker."));
            add(new BoolSetting("respect-cooldown", "Respect Cooldown", true).description("Wait for full cooldown")
                .group("General")
                .visibleWhen(() -> !"CPS".equals(value("timing"))));
            add(new IntSetting("min-charge", "Min Charge", 100, 50, 100, 5).unit("%")
                .group("General").description("Hits once the weapon cooldown is this full.")
                .visibleWhen(() -> !"CPS".equals(value("timing")) && bool("respect-cooldown")));
            add(new BoolSetting("randomize-delay", "Randomize Delay", false)
                .group("General")
                .description("Small delay after cooldown.").visibleWhen(() -> !"CPS".equals(value("timing"))));
            add(new RangeSetting("cps", "CPS", DEFAULT_CPS, 1, 20, 0.5).minSeparation(0.5)
                .group("General")
                .description("Click rate; it drifts and hesitates within this band like AutoClicker.")
                .visibleWhen(() -> "CPS".equals(value("timing"))));
            add(new BoolSetting("wait-for-crit", "Wait For Crits", false)
                .group("General").description("In the air, holds the hit until you fall, so it lands as a critical.").build());
            add(new BoolSetting("pause-using", "Pause While Using", true)
                .group("General").description("Stops while you eat, drink, block or draw a bow.").build());
            add(new BoolSetting("held-items-only", "Held Items Only", false)
                .group("General").description("Only with listed items.").build());
            add(RegistryListSetting.items("held-items", "Held Items", CombatFilters.DEFAULT_WEAPONS)
                .group("General").description("Main-hand items that let it attack.")
                .visibleWhen(() -> bool("held-items-only")).build());

            add(new IntSetting("hurt-time", "Hurt Time", 10, 0, 10, 1)
                .group("Targets").description("Skips targets flashing from a hit for longer than this, in ticks.").build());
            add(new IntSetting("max-health", "Max Health", 0, 0, 100, 1)
                .group("Targets").description("Skips targets with more health than this; 0 is no limit.").build());
            add(new BoolSetting("ignore-invisible", "Ignore Invisible", false)
                .group("Targets").description("Skips invisible entities.").build());
            add(new BoolSetting("ignore-naked", "Ignore Naked", false)
                .group("Targets").description("Skips players wearing no armor.").build());
            add(new BoolSetting("ignore-named", "Ignore Named", false)
                .group("Targets").description("Skips mobs with a name tag.").build());
            add(new BoolSetting("ignore-babies", "Ignore Babies", false)
                .group("Targets").description("Skips baby mobs.").build());
            add(new ChoiceSetting("ignore-pets", "Ignore Pets", "Yours", "Off", "Yours", "All")
                .group("Targets").description("Skips tamed animals: yours, anyone's, or none.").build());
        }

        private boolean waitingForCrit() {
            if (!bool("wait-for-crit")) return false;
            var player = MC.player;
            if (player.onGround() || player.onClimbable() || player.isInWater() || player.isPassenger()
                || player.getAbilities().flying || player.isFallFlying()) {
                return false;
            }
            return player.fallDistance <= 0.0D || player.getDeltaMovement().y >= 0.0D;
        }

        @Override
        public void onEnable() {
            resetTrigger();
        }

        @Override
        public void onDisable() {
            resetTrigger();
        }

        @Override
        public void tick() {

            if (MC == null || MC.player == null || MC.level == null || MC.gui.screen() != null
                || ScaffoldModule.ownsTellyInput() || ScaffoldModule.reservesRageInput()
                || DihSilentAim.packetRotation() != null
                || ScaffoldModule.hasActiveSilentMovementRotation()

                || ownsManualFastExp()

                || AutoTotemModule.operationActive()
                || bool("pause-using") && MC.player.isUsingItem()
                || bool("held-items-only")
                    && !CombatFilters.holds(MC.player.getMainHandItem(), heldItemIds, list("held-items"))) {
                resetTrigger();
                return;
            }
            if (!(MC.hitResult instanceof EntityHitResult entityHit) || !matchesTarget(entityHit.getEntity())) {
                resetTrigger();
                return;
            }
            if (waitingForCrit()) return;

            if ("CPS".equals(choice("timing"))) {
                ValueRange band = ValueRange.parse(value("cps"), DEFAULT_CPS);
                if (pacer.shouldClick(band.min(), band.max())) DihInputClicker.queueAttackClick();
                return;
            }

            boolean respectCooldown = bool("respect-cooldown");
            if (respectCooldown && MC.player.getAttackStrengthScale(0.0f) < integer("min-charge") / 100.0f) {
                resetTrigger();
                return;
            }

            long now = System.nanoTime();
            if (respectCooldown && bool("randomize-delay")) {
                if (!armed) {
                    readyAtNanos = now + DihHumanRng.triggerJitterMs() * 1_000_000L;
                    armed = true;
                }
                if (now < readyAtNanos) return;
            }

            DihInputClicker.queueAttackClick();
            resetTrigger();
        }

        private boolean matchesTarget(Entity entity) {
            if (entity == null || entity == MC.player) return false;
            if (DihAntiBot.suppress(entity)) return false;
            if (TeamsModule.combatExcluded(entity, "triggerbot")) return false;
            Set<String> ids = cachedEntityIds();
            if (ids.isEmpty()) return false;
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase(Locale.ROOT);
            if (!ids.contains(id) && !ids.contains(id.substring(id.indexOf(':') + 1))) return false;
            return !(entity instanceof LivingEntity living) || passesTargetFilters(living);
        }

        private boolean passesTargetFilters(LivingEntity entity) {
            boolean player = entity instanceof net.minecraft.world.entity.player.Player;
            if (entity.hurtTime > integer("hurt-time")) return false;
            if (bool("ignore-invisible") && entity.isInvisible()) return false;
            if (bool("ignore-naked") && player && CombatFilters.wearsNoArmor(entity)) return false;
            if (bool("ignore-named") && !player && entity.hasCustomName()) return false;
            if (bool("ignore-babies") && entity.isBaby()) return false;
            if (CombatFilters.ignoredPet(entity, choice("ignore-pets"), MC.player)) return false;
            int maxHealth = integer("max-health");
            return maxHealth <= 0 || entity.getHealth() + entity.getAbsorptionAmount() <= maxHealth;
        }

        private Set<String> cachedEntityIds() {
            List<String> entries = list("entities");
            String source = String.join("|", entries);
            if (source.equals(cachedEntityListSource)) return cachedEntityIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : entries) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedEntityListSource = source;
            cachedEntityIds = Set.copyOf(normalized);
            return cachedEntityIds;
        }

        private void resetTrigger() {
            readyAtNanos = 0L;
            armed = false;
            pacer.reset();
        }
    }

    static final class AutoFishModule extends Module {
        private final Random rng = new Random();
        private String cachedSoundListSource = "";
        private Set<String> cachedSoundIds = Set.of();
        private boolean pendingCatch;
        private int catchTimer;
        private boolean pendingRecast;
        private int recastTimer;
        private boolean movementActive;
        private int movementTick;
        private int movementDuration;
        private float movementStartYaw;
        private float movementTargetYaw;
        private float movementQueuedYaw;
        private float movementJitter;
        private float anchorYaw;
        private boolean anchorSet;
        private int stepIndex;
        private int stepDirection = 1;
        private boolean bobberWasBiting;
        private boolean stopMacroStarted;
        private long stopMacroRunId = -1L;
        private boolean stopMacroWarningShown;
        private int rodSwitchWaitTicks;
        private int autoCastTimer;
        private int awaitingHookTicks;
        private boolean virtualCastActive;
        private int virtualCastTimeoutTicks;
        private int ownedUsePacketTicks;
        private int ownedUsePackets;
        private int ownedSlotPacketTicks;
        private int ownedSlotPackets;
        private int manualInputCooldownTicks;
        private boolean screenWasOpen;
        private boolean awaitingReelConfirmation;
        private int reelConfirmationTicks;
        private int reelAttempts;
        private int catches;

        private static final int REEL_CONFIRMATION_TICKS = 4;
        private static final int MAX_REEL_ATTEMPTS = 3;

        AutoFishModule() {
            super("auto-fish", "AutoFish", ModuleCategory.PLAYER, "Automatically reels and recasts fishing rods.");
            add(new ChoiceSetting("trigger-mode", "Trigger", "Sound", "Sound", "Bobber").group("General")
                .description("Reel in on the splash sound, or when the bobber dips").build());
            add(RegistryListSetting.soundEvents("sounds", "Sounds", "minecraft:entity.fishing_bobber.splash")
                .group("General")
                .description("Sounds that count as a bite")
                .visibleWhen(() -> "Sound".equals(value("trigger-mode"))).build());
            add(new BoolSetting("sound-distance", "Sound Distance", true).group("General")
                .visibleWhen(() -> "Sound".equals(value("trigger-mode"))).description("Require sound near player."));
            add(new DoubleSetting("max-sound-distance", "Max Distance", 12.0, 1.0, 128.0, 0.5).group("General")
                .visibleWhen(() -> "Sound".equals(value("trigger-mode")) && Boolean.parseBoolean(value("sound-distance")))
                .description("Max player sound range.").build());
            add(new ChoiceSetting("click-mode", "Click Mode", "Real Input", "Real Input", "Interaction API").group("General")
                .description("Press the use key, or call the game's item use directly").build());
            add(new BoolSetting("show-catches", "Show Catches", false).group("General")
                .description("Shows how many fish you caught next to the module name").build());
            add(new IntSetting("stop-after", "Stop After", 0, 0, 1000, 1).group("General")
                .description("Turns off after this many catches; 0 never stops").build());
            add(new BoolSetting("auto-cast", "Auto Cast", true).group("Casting")
                .description("Recast when missing").build());
            add(new IntSetting("cast-timeout", "No Bite Retry", 60, 0, 300, 1).group("Casting")
                .description("0 = wait forever.").unit("s"));
            add(new BoolSetting("randomize-delays", "Randomize Delays", true).group("Delays")
                .description("Picks each delay between a min and a max").build());
            add(new IntSetting("catch-min", "Catch Min", 2, 0, 32, 1).group("Delays").unit("ticks")
                .description("Shortest wait before reeling in")
                .visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("catch-max", "Catch Max", 5, 0, 40, 1).group("Delays").unit("ticks")
                .description("Longest wait before reeling in")
                .visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-min", "Recast Min", 8, 0, 32, 1).group("Delays").unit("ticks")
                .description("Shortest wait before casting again")
                .visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-max", "Recast Max", 12, 0, 40, 1).group("Delays").unit("ticks")
                .description("Longest wait before casting again")
                .visibleWhen(() -> Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("catch-delay", "Catch Delay", 3, 0, 40, 1).group("Delays").unit("ticks")
                .description("Wait before reeling in")
                .visibleWhen(() -> !Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("recast-delay", "Recast Delay", 10, 0, 40, 1).group("Delays").unit("ticks")
                .description("Wait before casting again")
                .visibleWhen(() -> !Boolean.parseBoolean(value("randomize-delays"))).build());
            add(new IntSetting("movement-steps", "Steps", 6, 0, 6, 1).group("Movement")
                .description("Turns one step after each catch, sweeping up to this many steps each way; 0 stays still").build());
            add(new DoubleSetting("degrees-per-step", "Degrees / Step", 5.0, 0.1, 20.0, 0.1)
                .group("Movement").description("How far each turn goes")
                .visibleWhen(() -> !"0".equals(value("movement-steps"))).build());
            add(new IntSetting("movement-duration", "Duration", 6, 1, 30, 1).unit("ticks")
                .group("Movement").description("How long each turn takes")
                .visibleWhen(() -> !"0".equals(value("movement-steps"))).build());
            add(new BoolSetting("anti-break", "Anti Break", true).group("Rod Safety")
                .description("Stops before your rod breaks").build());
            add(new IntSetting("min-durability", "Min Durability", 2, 1, 32, 1)
                .group("Rod Safety").description("Durability a rod must keep")
                .visibleWhen(() -> Boolean.parseBoolean(value("anti-break"))).build());
            add(new BoolSetting("auto-select-rod", "Auto Select Best Rod", true).group("Rod Safety")
                .description("Switches to your best usable rod").build());
            add(new BoolSetting("stop-macro-enabled", "Auto Stop", false).group("Auto Stop")
                .description("Silent conditional rule").build());
            add(new StringSetting("stop-macro", "Rule", "").group("Auto Stop")
                .visibleWhen(() -> Boolean.parseBoolean(value("stop-macro-enabled"))).conditionalMacroPicker());
            add(new ChoiceSetting("stop-macro-trigger", "On Trigger", "Stop AutoFish",
                    "Stop AutoFish", "Run Steps Only", "Stop, Run, Resume").group("Auto Stop")
                .visibleWhen(() -> Boolean.parseBoolean(value("stop-macro-enabled")))
                .description("Action on condition").build());
        }

        @Override
        protected void onOptionValueChanged(String optionId) {
            normalizeDelayPairForEditedOption(optionId);
            if ("stop-macro".equals(optionId) || "stop-macro-enabled".equals(optionId) || "stop-macro-trigger".equals(optionId)
                || "anti-break".equals(optionId) || "min-durability".equals(optionId) || "auto-select-rod".equals(optionId)) {
                tuneSelectedAutoFishPresetMacro();
                cancelStopMacroIfExternal();
            }
        }

        @Override
        public String info() {
            return bool("show-catches") ? Integer.toString(catches) : "";
        }

        @Override
        public void onEnable() {
            catches = 0;
            normalizeDelayPairs();
            migrateLegacyStopSettings();
            tuneSelectedAutoFishPresetMacro();
            resetStopMacroSession();
        }

        @Override
        public void onDisable() {
            cancelStopMacroIfExternal();
            reset();
        }

        @Override
        public void onGameLeft() {
            cancelStopMacro();
            reset();
        }

        @Override
        public void tick() {
            normalizeDelayPairs();
            tickOwnedPacketBudgets();
            if (MC == null || MC.player == null || MC.level == null) {
                resetRuntimeActions();
                return;
            }

            if (AutoTotemModule.operationActive()
                || DihKillAuraRotation.currentOwner() != null && DihKillAuraRotation.hasCurrentRotation()) {
                pendingCatch = false;
                catchTimer = 0;
                pendingRecast = false;
                recastTimer = 0;
                clearReelConfirmation();
                return;
            }
            startStopMacroIfNeeded();
            if (MC.gui.screen() != null) {
                pauseForOpenScreen();
                return;
            }
            if (screenWasOpen) resumeAfterScreenClose();
            if (manualInputCooldownTicks > 0) {
                manualInputCooldownTicks--;
                tickVirtualCastState(false);
                return;
            }
            RodDecision rodDecision = updateRodSafety();
            if (!rodDecision.canProceed()) {
                resetRuntimeActions();
                return;
            }
            tickVirtualCastState(true);
            if (tickReelConfirmation()) return;
            if (tickAutoCast()) return;

            if ("Bobber".equals(choice("trigger-mode"))) {
                FishingHook hook = reliableHook();
                boolean biting = hook != null && ((DihFishingHookAccessor) hook).dih$isBiting();
                if (biting && !bobberWasBiting) scheduleCatch();
                bobberWasBiting = biting;
            }

            if (pendingCatch) {
                if (catchTimer > 0) {
                    catchTimer--;
                } else {
                    boolean clicked = isCastActive() && performUseClick();
                    pendingCatch = false;
                    if (clicked) {
                        awaitingReelConfirmation = true;
                        reelConfirmationTicks = REEL_CONFIRMATION_TICKS;
                        reelAttempts = 1;
                    } else {
                        autoCastTimer = Math.max(autoCastTimer, 2);
                    }
                }
            }

            tickMovement();

            if (pendingRecast) {
                if (recastTimer > 0) recastTimer--;
                if (recastTimer <= 0 && !movementActive) {
                    if (performUseClick()) {
                        markCastAttempt();
                    }
                    pendingRecast = false;
                }
            }
        }

        @Override
        public void onRenderLevel(float partialTick) {
            if (!movementActive || MC == null || MC.player == null) return;
            int duration = Math.max(1, movementDuration);
            float progress = Mth.clamp((movementTick + partialTick) / duration, 0.0f, 1.0f);
            float eased = progress * progress * (3.0f - 2.0f * progress);
            float yaw = movementStartYaw + DihRotationUtil.angleDifference(movementTargetYaw, movementStartYaw) * eased;
            if (progress < 1.0f) yaw += (float) Math.sin(progress * Math.PI) * movementJitter;
            queueMovementYaw(yaw);
        }

        @Override
        public void onSoundPacket(ClientboundSoundPacket packet) {
            if (!"Sound".equals(choice("trigger-mode"))
                || MC == null || MC.player == null || MC.level == null) return;
            if (!isCastActive()) return;
            if (!matchesSound(packet)) return;
            if (bool("sound-distance")) {
                double max = effectiveSoundDistance(packet);
                FishingHook hook = reliableHook();
                Vec3 origin = hook == null ? MC.player.position() : hook.position();
                if (origin.distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > max * max) return;
            }
            scheduleCatch();
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {

            if (DihHandArbiter.handPacketOwner() != null) return false;
            if (packet instanceof ServerboundUseItemPacket usePacket) {
                if (consumeOwnedUsePacket()) return false;
                if (isRodHand(DihPackets.hand(usePacket))) handleManualRodUse();
            } else if (packet instanceof ServerboundUseItemOnPacket useOnPacket) {
                if (consumeOwnedUsePacket()) return false;
                if (isRodHand(DihPackets.hand(useOnPacket))) handleManualRodUse();
            } else if (packet instanceof ServerboundSetCarriedItemPacket carriedPacket) {
                if (consumeOwnedSlotPacket()) return false;
                handleManualSlotSwitch(carriedPacket.getSlot());
            }
            return false;
        }

        private double effectiveSoundDistance(ClientboundSoundPacket packet) {
            double configured = Math.max(1.0, decimal("max-sound-distance"));
            if (packet == null) return configured;
            double audible = Math.max(1.0, packet.getVolume()) * 16.0;
            return Math.min(configured, audible);
        }

        private void scheduleCatch() {
            if (pendingCatch || pendingRecast || awaitingReelConfirmation) return;
            pendingCatch = true;
            catchTimer = nextDelay("catch-delay", "catch-min", "catch-max");
        }

        private int nextDelay(String fixedOption, String minOption, String maxOption) {
            normalizeDelayPairs();
            if (!bool("randomize-delays")) return Math.max(0, integer(fixedOption));
            int min = Math.max(0, integer(minOption));
            int max = Math.max(min, integer(maxOption));
            return min + rng.nextInt(max - min + 1);
        }

        private boolean matchesSound(ClientboundSoundPacket packet) {
            if (packet == null || packet.getSound() == null || packet.getSound().value() == null) return false;
            Identifier id = packet.getSound().value().location();
            if (id == null) return false;
            String soundId = id.toString().toLowerCase(Locale.ROOT);
            Set<String> sounds = cachedSoundIds();
            return sounds.contains(soundId) || sounds.contains(soundId.substring(soundId.indexOf(':') + 1));
        }

        private Set<String> cachedSoundIds() {
            List<String> sounds = list("sounds");
            if (sounds.isEmpty()) sounds = List.of("minecraft:entity.fishing_bobber.splash");
            String source = String.join("|", sounds);
            if (source.equals(cachedSoundListSource)) return cachedSoundIds;
            Set<String> normalized = new LinkedHashSet<>();
            for (String entry : sounds) {
                if (entry == null) continue;
                String value = entry.trim().toLowerCase(Locale.ROOT);
                if (value.isEmpty()) continue;
                normalized.add(value);
                int split = value.indexOf(':');
                if (split >= 0 && split + 1 < value.length()) normalized.add(value.substring(split + 1));
            }
            cachedSoundListSource = source;
            cachedSoundIds = Set.copyOf(normalized);
            return cachedSoundIds;
        }

        private boolean hasFishingRod() {
            InteractionHand hand = rodHand();
            if (hand == null) return false;
            return isRodSafe(MC.player.getItemInHand(hand));
        }

        private boolean isFishingNow() {
            return reliableHook() != null;
        }

        private boolean isCastActive() {
            return virtualCastActive || isFishingNow();
        }

        private InteractionHand rodHand() {
            if (MC.player == null) return null;
            if (MC.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof FishingRodItem) return InteractionHand.MAIN_HAND;

            if (DihHandArbiter.offhandClaimedByOther(id())) return null;
            if (MC.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof FishingRodItem) return InteractionHand.OFF_HAND;
            return null;
        }

        private boolean isRodHand(InteractionHand hand) {
            return MC.player != null
                && hand != null
                && MC.player.getItemInHand(hand).getItem() instanceof FishingRodItem;
        }

        private boolean performUseClick() {

            if (DihBlinkManager.holdsActionsWithoutMovement()) return false;
            if (!hasFishingRod()) return false;
            markOwnedUsePacket();
            if ("Interaction API".equals(choice("click-mode"))) {
                InteractionHand hand = rodHand();
                if (hand == null || MC.gameMode == null || MC.player == null) return false;

                if (ModuleRegistry.shouldCancelUseExcept(MC.hitResult, hand, id())) return false;
                InteractionResult result = MC.gameMode.useItem(MC.player, hand);
                if (result != null && result.consumesAction()) DihEntities.swing(MC.player, hand);
            } else {
                DihInputClicker.queueUseClick();
            }
            return true;
        }

        private boolean tickAutoCast() {
            if (!bool("auto-cast")) {
                autoCastTimer = 0;
                awaitingHookTicks = 0;
                return false;
            }
            FishingHook hook = reliableHook();
            if (hook != null) {
                autoCastTimer = 0;
                awaitingHookTicks = 0;
                virtualCastActive = true;
                return false;
            }
            bobberWasBiting = false;
            if (virtualCastActive) return false;
            if (pendingCatch || pendingRecast || awaitingReelConfirmation || movementActive) return false;
            if (awaitingHookTicks > 0) {
                awaitingHookTicks--;
                return true;
            }
            if (autoCastTimer > 0) {
                autoCastTimer--;
                return true;
            }
            if (performUseClick()) markCastAttempt();
            else autoCastTimer = 1;
            return true;
        }

        private void markCastAttempt() {
            virtualCastActive = true;
            virtualCastTimeoutTicks = castTimeoutTicks();
            awaitingHookTicks = 8;
            autoCastTimer = nextDelay("recast-delay", "recast-min", "recast-max");
        }

        private void tickVirtualCastState(boolean allowRetryClick) {
            if (!virtualCastActive || pendingCatch || pendingRecast || awaitingReelConfirmation) return;
            if (castTimeoutTicks() <= 0) return;
            if (virtualCastTimeoutTicks <= 0) virtualCastTimeoutTicks = castTimeoutTicks();
            virtualCastTimeoutTicks--;
            if (virtualCastTimeoutTicks > 0) return;

            boolean hadReliableHook = isFishingNow();
            clearVirtualCastState();
            if (!allowRetryClick || !bool("auto-cast")) {
                autoCastTimer = Math.max(autoCastTimer, 2);
                return;
            }
            if (hadReliableHook && performUseClick()) {
                pendingRecast = true;
                recastTimer = nextDelay("recast-delay", "recast-min", "recast-max");
            } else {
                autoCastTimer = Math.max(autoCastTimer, nextDelay("recast-delay", "recast-min", "recast-max"));
            }
        }

        private void clearVirtualCastState() {
            virtualCastActive = false;
            virtualCastTimeoutTicks = 0;
            awaitingHookTicks = 0;
        }

        private boolean tickReelConfirmation() {
            if (!awaitingReelConfirmation) return false;
            if (!isFishingNow()) {
                awaitingReelConfirmation = false;
                reelConfirmationTicks = 0;
                reelAttempts = 0;
                clearVirtualCastState();
                catches++;
                int stopAfter = integer("stop-after");
                if (stopAfter > 0 && catches >= stopAfter) {
                    dihclient.util.DihClientMessaging.sendPrefixed("AutoFish: stopped after " + catches + " catches.");
                    setEnabled(false);
                    return true;
                }
                startMovement();
                pendingRecast = bool("auto-cast");
                recastTimer = pendingRecast ? nextDelay("recast-delay", "recast-min", "recast-max") : 0;
                return false;
            }

            if (reelConfirmationTicks > 0) {
                reelConfirmationTicks--;
                return true;
            }
            if (reelAttempts < MAX_REEL_ATTEMPTS && performUseClick()) {
                reelAttempts++;
                reelConfirmationTicks = REEL_CONFIRMATION_TICKS;
                return true;
            }

            awaitingReelConfirmation = false;
            reelConfirmationTicks = 0;
            reelAttempts = 0;
            autoCastTimer = Math.max(autoCastTimer, 2);
            return false;
        }

        private int castTimeoutTicks() {
            return Math.max(0, integer("cast-timeout")) * 20;
        }

        private void handleManualRodUse() {
            if (MC == null || MC.player == null || MC.level == null) return;
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 3);
            pendingCatch = false;
            catchTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();

            if (isFishingNow()) {
                clearVirtualCastState();
                pendingRecast = bool("auto-cast");
                recastTimer = pendingRecast ? nextDelay("recast-delay", "recast-min", "recast-max") : 0;
                autoCastTimer = Math.max(autoCastTimer, 2);
            } else {
                pendingRecast = false;
                recastTimer = 0;
                markCastAttempt();
            }
        }

        private void handleManualSlotSwitch(int slot) {
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 8);
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();
            bobberWasBiting = false;
            if (!isFishingNow()) clearVirtualCastState();
            autoCastTimer = Math.max(autoCastTimer, 6);
        }

        private void pauseForOpenScreen() {
            screenWasOpen = true;
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            movementActive = false;
            movementTick = 0;
            clearReelConfirmation();
            bobberWasBiting = false;
            autoCastTimer = Math.max(autoCastTimer, 4);
        }

        private void resumeAfterScreenClose() {
            screenWasOpen = false;
            manualInputCooldownTicks = Math.max(manualInputCooldownTicks, 4);
            if (isFishingNow()) {
                virtualCastActive = true;
                if (virtualCastTimeoutTicks <= 0) virtualCastTimeoutTicks = castTimeoutTicks();
            } else if (virtualCastActive) {
                clearVirtualCastState();
                autoCastTimer = Math.max(autoCastTimer, nextDelay("recast-delay", "recast-min", "recast-max"));
            }
        }

        private FishingHook reliableHook() {
            if (MC == null || MC.player == null || MC.level == null) return null;
            FishingHook playerHook = MC.player.fishing;
            if (isReliableHookEntity(playerHook)) return playerHook;
            FishingHook fallback = null;
            for (Entity entity : MC.level.entitiesForRendering()) {
                if (!(entity instanceof FishingHook hook)) continue;
                if (!isReliableHookEntity(hook)) continue;
                if (fallback == null || hook.distanceToSqr(MC.player) < fallback.distanceToSqr(MC.player)) fallback = hook;
            }
            return fallback;
        }

        private boolean isReliableHookEntity(FishingHook hook) {
            if (hook == null || hook.isRemoved() || MC.player == null) return false;
            if (hook.distanceToSqr(MC.player) > 1024.0) return false;
            Entity owner = hook.getOwner();
            return owner == MC.player || owner != null && owner.getUUID().equals(MC.player.getUUID());
        }

        private void markOwnedUsePacket() {
            ownedUsePackets = Math.min(ownedUsePackets + 2, 4);
            ownedUsePacketTicks = 4;
        }

        private boolean consumeOwnedUsePacket() {
            if (ownedUsePackets <= 0) return false;
            ownedUsePackets--;
            return true;
        }

        private void markOwnedSlotPacket() {
            ownedSlotPackets = Math.min(ownedSlotPackets + 1, 4);
            ownedSlotPacketTicks = 4;
        }

        private boolean consumeOwnedSlotPacket() {
            if (ownedSlotPackets <= 0) return false;
            ownedSlotPackets--;
            return true;
        }

        private void tickOwnedPacketBudgets() {
            if (ownedUsePacketTicks > 0 && --ownedUsePacketTicks <= 0) ownedUsePackets = 0;
            if (ownedSlotPacketTicks > 0 && --ownedSlotPacketTicks <= 0) ownedSlotPackets = 0;
        }

        private void startMovement() {
            int steps = integer("movement-steps");
            if (steps <= 0 || MC.player == null) return;
            if (!anchorSet) {
                anchorYaw = MC.player.getYRot();
                anchorSet = true;
            }
            if (stepDirection == 0) stepDirection = 1;
            stepIndex += stepDirection;
            if (stepIndex >= steps) {
                stepIndex = steps;
                stepDirection = -1;
            } else if (stepIndex <= -steps) {
                stepIndex = -steps;
                stepDirection = 1;
            }
            movementActive = true;
            movementTick = 0;
            movementDuration = Math.max(1, integer("movement-duration"));
            movementStartYaw = MC.player.getYRot();
            movementTargetYaw = anchorYaw + (float) (stepIndex * decimal("degrees-per-step"));
            movementQueuedYaw = movementStartYaw;
            movementJitter = (rng.nextFloat() - 0.5f) * 0.08f;
        }

        private void tickMovement() {
            if (!movementActive || MC.player == null) return;
            movementTick++;
            if (movementTick >= Math.max(1, movementDuration)) {
                queueMovementYaw(movementTargetYaw);
                movementActive = false;
            }
        }

        private void queueMovementYaw(float yaw) {
            float yawDelta = DihRotationUtil.angleDifference(yaw, movementQueuedYaw);
            movementQueuedYaw = yaw;
            DihMouseInputSimulator.queueRotationDelta(
                DihMouseInputSimulator.Source.AUTO_FISH, yawDelta, 0.0f);
        }

        private void resetRuntimeActions() {
            pendingCatch = false;
            catchTimer = 0;
            pendingRecast = false;
            recastTimer = 0;
            autoCastTimer = 0;
            clearVirtualCastState();
            ownedUsePacketTicks = 0;
            ownedUsePackets = 0;
            ownedSlotPacketTicks = 0;
            ownedSlotPackets = 0;
            manualInputCooldownTicks = 0;
            screenWasOpen = false;
            movementActive = false;
            movementTick = 0;
            bobberWasBiting = false;
            DihMouseInputSimulator.clear(DihMouseInputSimulator.Source.AUTO_FISH);
            clearReelConfirmation();
        }

        private void clearReelConfirmation() {
            awaitingReelConfirmation = false;
            reelConfirmationTicks = 0;
            reelAttempts = 0;
        }

        private void reset() {
            resetRuntimeActions();
            anchorSet = false;
            anchorYaw = 0.0f;
            stepIndex = 0;
            stepDirection = 1;
            rodSwitchWaitTicks = 0;
        }

        private void normalizeDelayPairs() {
            normalizeDelayPair("catch-min", "catch-max");
            normalizeDelayPair("recast-min", "recast-max");
        }

        private void normalizeDelayPairForEditedOption(String optionId) {
            if ("catch-min".equals(optionId) || "catch-max".equals(optionId)) normalizeDelayPair("catch-min", "catch-max", optionId);
            else if ("recast-min".equals(optionId) || "recast-max".equals(optionId)) normalizeDelayPair("recast-min", "recast-max", optionId);
        }

        private void normalizeDelayPair(String minId, String maxId) {
            normalizeDelayPair(minId, maxId, "");
        }

        private void normalizeDelayPair(String minId, String maxId, String editedId) {
            int min = integer(minId);
            int max = integer(maxId);
            if (min <= max) return;
            if (minId.equals(editedId)) {
                setValue(maxId, Integer.toString(min));
            } else {
                setValue(minId, Integer.toString(max));
            }
        }

        private RodDecision updateRodSafety() {
            if (MC.player == null) return RodDecision.pause();

            if (DihBlinkManager.holdsActionsWithoutMovement()) return RodDecision.pause();
            RodChoice best = findBestRod(true);
            if (best == null) {
                return RodDecision.pause();
            }

            if (bool("auto-select-rod")) {
                RodChoice selected = selectedMainHandRod();
                if (selected == null || !selected.safe() || best.score() > selected.score()) {

                    if (best.slot() >= 0 && best.slot() < 9 && !DihHandArbiter.slotReserved(best.slot(), id())) {

                        if (!DihHandArbiter.beginHandPacketGroup(id())) return RodDecision.pause();
                        try {
                            markOwnedSlotPacket();
                            DihInventoryHelper.selectHotbarSlot(MC, best.slot());
                            rodSwitchWaitTicks = 1;
                        } finally {
                            DihHandArbiter.endHandPacketGroup(id());
                        }
                        return RodDecision.pause();
                    }
                    if (best.slot() >= 9 && best.slot() < 36 && !DihHandArbiter.slotReserved(best.slot(), id())) {
                        int target = MC.player.getInventory().getSelectedSlot();

                        if (!DihHandArbiter.slotReserved(target, id())) {

                            if (!DihHandArbiter.beginHandPacketGroup(id())) return RodDecision.pause();
                            try {
                                if (DihInventoryHelper.swapInventorySlots(MC, best.slot(), target)) {
                                    markOwnedSlotPacket();
                                    DihInventoryHelper.selectHotbarSlot(MC, target);
                                    rodSwitchWaitTicks = 1;
                                    return RodDecision.pause();
                                }
                            } finally {
                                DihHandArbiter.endHandPacketGroup(id());
                            }
                        }
                    }
                }
            }

            if (rodSwitchWaitTicks > 0) {
                rodSwitchWaitTicks--;
                return RodDecision.pause();
            }
            return hasFishingRod() ? RodDecision.proceed() : RodDecision.pause();
        }

        private RodChoice selectedMainHandRod() {
            if (MC.player == null) return null;
            int slot = MC.player.getInventory().getSelectedSlot();
            return rodChoice(slot, MC.player.getInventory().getItem(slot), false);
        }

        private RodChoice findBestRod(boolean safeOnly) {
            if (MC.player == null) return null;
            RodChoice best = null;
            int size = MC.player.getInventory().getContainerSize();
            for (int slot = 0; slot < Math.min(36, size); slot++) {
                RodChoice choice = rodChoice(slot, MC.player.getInventory().getItem(slot), safeOnly);
                if (choice != null && (best == null || choice.score() > best.score())) best = choice;
            }
            RodChoice offhand = rodChoice(40, MC.player.getOffhandItem(), safeOnly);
            if (offhand != null && (best == null || offhand.score() > best.score())) best = offhand;
            return best;
        }

        private RodChoice rodChoice(int slot, ItemStack stack, boolean safeOnly) {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) return null;
            boolean safe = isRodSafe(stack);
            if (safeOnly && !safe) return null;
            int durability = stack.isDamageableItem() ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) : 999;
            int score = enchantLevel(stack, Enchantments.LUCK_OF_THE_SEA) * 900
                + enchantLevel(stack, Enchantments.LURE) * 900
                + enchantLevel(stack, Enchantments.UNBREAKING) * 200
                + enchantLevel(stack, Enchantments.MENDING) * 100
                + noVanishingBonus(stack) * 50
                + Math.min(49, durability);
            return new RodChoice(slot, score, safe);
        }

        private boolean isRodSafe(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof FishingRodItem)) return false;
            if (!bool("anti-break") || !stack.isDamageableItem()) return true;
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            return remaining > Math.max(1, integer("min-durability"));
        }

        private int enchantLevel(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key) {
            try {
                if (MC == null || MC.level == null || stack == null || stack.isEmpty()) return 0;
                var holder = MC.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
                return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
            } catch (Throwable ignored) {
                return 0;
            }
        }

        private int noVanishingBonus(ItemStack stack) {
            try {
                return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP) ? 0 : 1;
            } catch (Throwable ignored) {
                return 1;
            }
        }

        private void migrateLegacyStopSettings() {
            if (bool("stop-macro-enabled") || !value("stop-macro").trim().isEmpty()) return;
            if (Boolean.parseBoolean(value("stop-inv-slots"))) {
                dihclient.util.DihMacro macro = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.FREE_SLOTS);
                if (macro != null) {
                    tuneAutoFishPresetMacro(macro, AutoFishStopMacroFactory.Preset.FREE_SLOTS);
                    setValue("stop-macro-enabled", "true");
                    setValue("stop-macro", macro.name);
                }
            } else if (Boolean.parseBoolean(value("stop-out-of-rods"))) {
                dihclient.util.DihMacro macro = AutoFishStopMacroFactory.ensurePreset(AutoFishStopMacroFactory.Preset.DURABILITY);
                if (macro != null) {
                    tuneAutoFishPresetMacro(macro, AutoFishStopMacroFactory.Preset.DURABILITY);
                    setValue("stop-macro-enabled", "true");
                    setValue("stop-macro", macro.name);
                }
            }
        }

        private void tuneAutoFishPresetMacro(dihclient.util.DihMacro macro, AutoFishStopMacroFactory.Preset preset) {
            if (macro == null || macro.actions == null || macro.actions.isEmpty() || preset == null) return;
            if (preset == AutoFishStopMacroFactory.Preset.FREE_SLOTS && macro.actions.get(0) instanceof WaitFreeSlotsAction wait) {
                int slots = Math.max(0, Math.min(36, integer("min-free-slots")));
                boolean changed = wait.countMode != WaitFreeSlotsAction.CountMode.FREE_SLOTS
                    || wait.comparison != WaitFreeSlotsAction.Comparison.AT_MOST
                    || wait.slots != slots;
                wait.countMode = WaitFreeSlotsAction.CountMode.FREE_SLOTS;
                wait.comparison = WaitFreeSlotsAction.Comparison.AT_MOST;
                wait.slots = slots;
                if (changed) dihclient.util.DihMacroManager.get().save();
            } else if (preset == AutoFishStopMacroFactory.Preset.DURABILITY && macro.actions.get(0) instanceof WaitDurabilityAction wait) {
                int value = Math.max(1, integer("min-durability"));
                boolean changed = wait.targetMode != WaitDurabilityAction.TargetMode.ITEM
                    || !"minecraft:fishing_rod".equals(wait.itemName)
                    || wait.measurement != WaitDurabilityAction.Measurement.REMAINING
                    || wait.comparison != WaitDurabilityAction.Comparison.AT_MOST
                    || wait.value != value
                    || !wait.useNext;
                wait.targetMode = WaitDurabilityAction.TargetMode.ITEM;
                wait.itemName = "minecraft:fishing_rod";
                wait.measurement = WaitDurabilityAction.Measurement.REMAINING;
                wait.comparison = WaitDurabilityAction.Comparison.AT_MOST;
                wait.value = value;
                wait.useNext = true;
                if (changed) dihclient.util.DihMacroManager.get().save();
            }
        }

        private void startStopMacroIfNeeded() {
            if (!bool("stop-macro-enabled")) return;
            if (stopMacroStarted) return;
            stopMacroStarted = true;
            tuneSelectedAutoFishPresetMacro();
            String macroName = value("stop-macro").trim();
            if (macroName.isBlank()) {
                warnStopMacroOnce("AutoFish auto stop rule missing.");
                return;
            }
            dihclient.util.DihMacro macro = dihclient.util.DihMacroManager.get().get(macroName);
            if (macro == null) {
                warnStopMacroOnce("AutoFish auto stop rule not found: " + macroName);
                return;
            }
            if (!AutoFishStopMacroFactory.isValidStopMacro(macro)) {
                warnStopMacroOnce("AutoFish auto stop rule must start with a conditional.");
                return;
            }
            dihclient.util.DihMacro runtimeMacro = buildAutoStopRuntimeMacro(macro);
            if (runtimeMacro == null || runtimeMacro.actions == null || runtimeMacro.actions.isEmpty()) {
                warnStopMacroOnce("AutoFish auto stop could not build a valid rule.");
                return;
            }
            runtimeMacro.regenerateAllPackets();
            stopMacroRunId = MacroExecutor.executeTracked(runtimeMacro, MacroExecutor.RunOptions.silentBackground());
            if (stopMacroRunId < 0L) warnStopMacroOnce("AutoFish auto stop could not start.");
        }

        private void tuneSelectedAutoFishPresetMacro() {
            String macroName = value("stop-macro").trim();
            AutoFishStopMacroFactory.Preset preset = AutoFishStopMacroFactory.presetForGeneratedName(macroName);
            if (preset == null) return;
            dihclient.util.DihMacro macro = dihclient.util.DihMacroManager.get().get(macroName);
            if (macro == null) return;
            tuneAutoFishPresetMacro(macro, preset);
        }

        private dihclient.util.DihMacro buildAutoStopRuntimeMacro(dihclient.util.DihMacro source) {
            if (source == null || source.actions == null) return null;
            dihclient.util.DihMacro runtime = source.deepCopy(source.name);
            runtime.keyCode = -1;
            runtime.loop = false;
            runtime.loopCount = -1;
            int conditionIndex = firstEnabledConditionIndex(runtime.actions);
            if (conditionIndex < 0) return null;

            AutoStopTrigger trigger = AutoStopTrigger.from(choice("stop-macro-trigger"));
            boolean injectStop = trigger != AutoStopTrigger.RUN_STEPS_ONLY;
            boolean skipImmediateDuplicateDisable = injectStop
                && conditionIndex + 1 < runtime.actions.size()
                && AutoFishStopMacroFactory.disablesAutoFish(runtime.actions.get(conditionIndex + 1));
            boolean injectResume = trigger == AutoStopTrigger.STOP_RUN_RESUME
                && !hasExplicitAutoFishToggleAfter(runtime.actions, conditionIndex, skipImmediateDuplicateDisable);

            ArrayList<MacroAction> rebuilt = new ArrayList<>();
            for (int i = 0; i < runtime.actions.size(); i++) {
                MacroAction action = runtime.actions.get(i);
                if (i == conditionIndex) {
                    rebuilt.add(action);
                    if (injectStop) rebuilt.add(new AutoFishRuntimeToggleAction(this, false));
                    continue;
                }
                if (skipImmediateDuplicateDisable && i == conditionIndex + 1) continue;
                rebuilt.add(action);
            }
            if (injectResume) rebuilt.add(new AutoFishRuntimeToggleAction(this, true));
            runtime.actions = rebuilt;
            return runtime;
        }

        private int firstEnabledConditionIndex(List<MacroAction> actions) {
            if (actions == null) return -1;
            for (int i = 0; i < actions.size(); i++) {
                MacroAction action = actions.get(i);
                if (action == null || !action.isEnabled()) continue;
                return MacroConditionUtil.isWaitConditionAction(action) ? i : -1;
            }
            return -1;
        }

        private boolean hasExplicitAutoFishToggleAfter(List<MacroAction> actions, int conditionIndex, boolean ignoreImmediateDuplicateDisable) {
            if (actions == null) return false;
            for (int i = Math.max(0, conditionIndex + 1); i < actions.size(); i++) {
                if (ignoreImmediateDuplicateDisable && i == conditionIndex + 1) continue;
                MacroAction action = actions.get(i);
                if (action != null && action.isEnabled() && AutoFishStopMacroFactory.isAutoFishToggleAction(action)) return true;
            }
            return false;
        }

        private enum AutoStopTrigger {
            STOP_AUTO_FISH,
            RUN_STEPS_ONLY,
            STOP_RUN_RESUME;

            static AutoStopTrigger from(String value) {
                String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                if ("run steps only".equals(normalized)) return RUN_STEPS_ONLY;
                if ("stop, run, resume".equals(normalized)) return STOP_RUN_RESUME;
                return STOP_AUTO_FISH;
            }
        }

        private static final class AutoFishRuntimeToggleAction implements MacroAction {
            private final AutoFishModule owner;
            private final boolean enable;

            private AutoFishRuntimeToggleAction(AutoFishModule owner, boolean enable) {
                this.owner = owner;
                this.enable = enable;
            }

            @Override
            public void execute(Minecraft mc) {
                if (owner != null) owner.setEnabledSilently(enable);
            }

            @Override
            public CompoundTag toTag() {
                CompoundTag tag = new CompoundTag();
                tag.putString("type", MacroActionType.TOGGLE_MODULE.name());
                return tag;
            }

            @Override public void fromTag(CompoundTag tag) {}
            @Override public MacroActionType getType() { return MacroActionType.TOGGLE_MODULE; }
            @Override public String getDisplayName() { return enable ? "Resume AutoFish" : "Stop AutoFish"; }
            @Override public String getIcon() { return "M"; }
        }

        private void warnStopMacroOnce(String message) {
            if (stopMacroWarningShown) return;
            stopMacroWarningShown = true;
            DihNotifications.warning(message);
        }

        private void resetStopMacroSession() {
            stopMacroStarted = false;
            stopMacroWarningShown = false;
            stopMacroRunId = -1L;
        }

        private void cancelStopMacroIfExternal() {
            long runId = stopMacroRunId;
            if (runId >= 0L && MacroExecutor.isRunActive(runId) && MacroExecutor.currentRunId() != runId) {
                MacroExecutor.stopRun(runId);
            }
            resetStopMacroSession();
        }

        private void cancelStopMacro() {
            long runId = stopMacroRunId;
            if (runId >= 0L && MacroExecutor.isRunActive(runId)) MacroExecutor.stopRun(runId);
            resetStopMacroSession();
        }

        private record RodChoice(int slot, int score, boolean safe) {
        }

        private record RodDecision(boolean canProceed) {
            static RodDecision proceed() { return new RodDecision(true); }
            static RodDecision pause() { return new RodDecision(false); }
        }
    }

    static final class InstantRebreakModule extends Module {
        private BlockPos target;
        private Direction direction = Direction.UP;
        private int delay;
        private Block targetBlock;
        private boolean sentSinceSolid;
        private int rebreaks;

        InstantRebreakModule() {
            super("instant-rebreak", "InstantRebreak", ModuleCategory.PLAYER, "Rebreaks the last block.");
            add(new IntSetting("delay", "Delay", 0, 0, 20, 1).unit("ticks").group("General")
                .description("Delay between rebreak packets."));
            add(new BoolSetting("rotate", "Rotate", false).group("General").description("Face target first."));
            add(new IntSetting("max-rebreaks", "Max Rebreaks", 0, 0, 100, 1).group("General")
                .description("Forgets the block after breaking it this many times; 0 never stops"));

            add(new BoolSetting("only-pick", "Only Pickaxe", true).group("Conditions").description("Require pickaxe."));
            add(new BoolSetting("same-block", "Same Block Only", false).group("Conditions")
                .description("Only rebreaks when the same kind of block comes back"));
            add(new BoolSetting("range-check", "Range Check", false).group("Conditions")
                .description("Waits while the block is farther than the range"));
            add(new DoubleSetting("range", "Range", 6.0, 1.0, 10.0, 0.5).unit("blocks").group("Conditions")
                .description("Farthest block it sends rebreaks for")
                .visibleWhen(() -> bool("range-check")));
            add(new BoolSetting("pause-while-using", "Pause While Using", false).group("Conditions")
                .description("Waits while you eat, drink or block"));

            add(new BoolSetting("render", "Render", true).group("Render").description("Show the rebreak target."));
            add(new ColorSetting("color", "Color", 0xFFFF3B3B).group("Render").description("Single outline color.")
                .visibleWhen(() -> bool("render")).build());
        }

        @Override
        public String info() {
            return target == null || rebreaks == 0 ? "" : Integer.toString(rebreaks);
        }

        @Override
        public void onDisable() {
            DihInstaBreakRenderer.clear();
            target = null;
        }

        @Override
        public void onStartBreakingBlock(BlockPos pos, Direction direction) {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            target = pos == null ? null : pos.immutable();
            targetBlock = target == null || MC.level == null ? null : MC.level.getBlockState(target).getBlock();
            sentSinceSolid = false;
            rebreaks = 0;
            this.direction = direction == null ? Direction.UP : direction;
            if (bool("render")) DihInstaBreakRenderer.setTarget(target, colorValue("color", colorValue("line-color", 0xFF39D77A)));
            delay = 0;
        }

        @Override
        public void tick() {
            if (dihclient.util.multi.MultiPilot.isActive()) return;
            if (MC.player == null || MC.level == null || MC.getConnection() == null || target == null) return;

            if (AutoTotemModule.operationActive()) return;

            if (!bool("render")) DihInstaBreakRenderer.clear();
            else DihInstaBreakRenderer.setTarget(target, colorValue("color", colorValue("line-color", 0xFF39D77A)));

            if (MC.level.isOutsideBuildHeight(target)) return;
            BlockState current = MC.level.getBlockState(target);
            if (current.isAir()) {
                if (sentSinceSolid) {
                    sentSinceSolid = false;
                    rebreaks++;
                    int max = integer("max-rebreaks");
                    if (max > 0 && rebreaks >= max) {
                        target = null;
                        DihInstaBreakRenderer.clear();
                    }
                }
                return;
            }
            if (bool("same-block") && targetBlock != null && current.getBlock() != targetBlock) return;
            if (bool("only-pick") && !MC.player.getMainHandItem().is(ItemTags.PICKAXES)) return;
            if (bool("pause-while-using") && MC.player.isUsingItem()) return;
            if (bool("range-check")
                && MC.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) > decimal("range") * decimal("range")) {
                return;
            }
            if (delay++ < integer("delay")) return;
            delay = 0;
            if (bool("rotate")) MC.player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));

            BlockPos pos = target;
            Direction dir = direction == null ? Direction.UP : direction;

            if (MC.gameMode instanceof DihMultiPlayerGameModeAccessor accessor) {
                accessor.dih$startPrediction(MC.level, sequence -> new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir, sequence));
            } else {
                MC.getConnection().send(new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, dir));
            }
            MC.getConnection().send(DihPackets.mainHandSwing());
            sentSinceSolid = true;
        }

        private int colorValue(String id, int fallback) {
            try {
                String value = value(id).replace("#", "");
                if (value.length() == 6) value = "FF" + value;
                return (int) Long.parseLong(value, 16);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    static final class NoInteractModule extends Module {
        NoInteractModule() {
            super("no-interact", "NoInteract", ModuleCategory.PLAYER, "Cancels selected inputs.");
            add(new BoolSetting("sneak-bypass", "Sneak Bypass", false).group("General")
                .description("Holding sneak lets every input through").build());
            add(new BoolSetting("pause-in-creative", "Pause In Creative", false).group("General")
                .description("Lets every input through in creative mode").build());
            add(new BoolSetting("notify", "Notify", false).group("General")
                .description("Says in chat when it cancels something, at most once a second").build());

            add(RegistryListSetting.blocks("block-mine", "Block Mine", "").group("Blocks").description("Mining block list.").build());
            add(new ChoiceSetting("block-mine-mode", "Mine Mode", "BlackList", "WhiteList", "BlackList").group("Blocks")
                .description("BlackList stops mining the listed blocks; WhiteList allows only them").build());
            add(RegistryListSetting.blocks("block-interact", "Block Interact", "").group("Blocks").description("Use block list.").build());
            add(new ChoiceSetting("block-interact-mode", "Block Mode", "BlackList", "WhiteList", "BlackList").group("Blocks")
                .description("BlackList stops using the listed blocks; WhiteList allows only them").build());
            add(new ChoiceSetting("block-interact-hand", "Block Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Blocks")
                .description("Which hand the block-use rule applies to").build());
            add(RegistryListSetting.entityTypes("entity-interact", "Entity Interact", "").group("Entities").description("Use entity list.").build());
            add(new ChoiceSetting("entity-interact-mode", "Entity Mode", "BlackList", "WhiteList", "BlackList").group("Entities")
                .description("BlackList stops using the listed entities; WhiteList allows only them").build());
            add(new ChoiceSetting("entity-interact-hand", "Entity Hand", "Both", "Mainhand", "Offhand", "Both", "None").group("Entities")
                .description("Which hand the entity-use rule applies to").build());
            add(RegistryListSetting.entityTypes("entity-hit", "Entity Hit", "").group("Attacks")
                .description("Entities the hit rule uses").build());
            add(new ChoiceSetting("entity-hit-mode", "Hit Mode", "WhiteList", "WhiteList", "BlackList").group("Attacks")
                .description("WhiteList lets you hit only the listed entities; BlackList stops hitting them").build());
            add(new BoolSetting("friends", "Friends", false).group("Entities")
                .description("Also applies the entity rules to friends and teammates"));
            add(new BoolSetting("babies", "Babies", false).group("Entities")
                .description("Also applies the entity rules to baby mobs"));
            add(new BoolSetting("nametagged", "Nametagged", false).group("Entities")
                .description("Also applies the entity rules to mobs with a name tag"));
        }

        private long lastNotifyMs;

        /** Whether the rules are switched off right now. */
        private boolean bypassed() {
            if (MC.player == null) return false;
            if (bool("sneak-bypass") && MC.player.isShiftKeyDown()) return true;
            return bool("pause-in-creative") && MC.player.isCreative();
        }

        private boolean cancelled(boolean cancel, String what) {
            if (!cancel) return false;
            if (bypassed()) return false;
            if (bool("notify")) {
                long now = System.currentTimeMillis();
                if (now - lastNotifyMs >= 1000L) {
                    lastNotifyMs = now;
                    DihClientMessaging.sendPrefixed("NoInteract blocked " + what + ".");
                }
            }
            return true;
        }

        @Override
        public boolean shouldCancelStartBreakingBlock(BlockPos pos, Direction direction) {
            if (MC.level == null || pos == null) return matchesRegistry("", list("block-mine"), choice("block-mine-mode"));
            String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(pos).getBlock()).toString();
            return cancelled(matchesRegistry(id, list("block-mine"), choice("block-mine-mode")), "mining");
        }

        @Override
        public boolean shouldCancelAttack(HitResult hitResult) {
            if (!(hitResult instanceof EntityHitResult entityHit)) return false;
            Entity entity = entityHit.getEntity();
            if (!entityFilterAllowed(entity)) return false;
            return cancelled(matchesRegistry(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                list("entity-hit"), choice("entity-hit-mode")), "a hit");
        }

        @Override
        public boolean shouldCancelUse(HitResult hitResult, InteractionHand hand) {
            if (hitResult instanceof BlockHitResult blockHit) {
                if (!matchesHand(choice("block-interact-hand"), hand)) return false;
                if (MC.level == null) return matchesRegistry("", list("block-interact"), choice("block-interact-mode"));
                String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(blockHit.getBlockPos()).getBlock()).toString();
                return cancelled(matchesRegistry(id, list("block-interact"), choice("block-interact-mode")), "a block use");
            }
            if (hitResult instanceof EntityHitResult entityHit) {
                if (!matchesHand(choice("entity-interact-hand"), hand)) return false;
                if (!entityFilterAllowed(entityHit.getEntity())) return false;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType()).toString();
                return cancelled(matchesRegistry(id, list("entity-interact"), choice("entity-interact-mode")), "an entity use");
            }
            return false;
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (packet instanceof ServerboundUseItemOnPacket use) {
                if (!matchesHand(choice("block-interact-hand"), DihPackets.hand(use))) return false;
                if (MC.level == null) return matchesRegistry("", list("block-interact"), choice("block-interact-mode"));
                String id = BuiltInRegistries.BLOCK.getKey(MC.level.getBlockState(DihPackets.hitResult(use).getBlockPos()).getBlock()).toString();
                return cancelled(matchesRegistry(id, list("block-interact"), choice("block-interact-mode")), "a block use");
            }
            if (packet instanceof ServerboundPlayerActionPacket action && action.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                return shouldCancelStartBreakingBlock(action.getPos(), action.getDirection());
            }
            if (packet instanceof ServerboundInteractPacket interact) {
                if (!matchesHand(choice("entity-interact-hand"), interact.hand())) return false;
                if (MC.level == null) return false;
                Entity entity = MC.level.getEntity(interact.entityId());
                if (entity == null || !entityFilterAllowed(entity)) return false;
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                return cancelled(matchesRegistry(id, list("entity-interact"), choice("entity-interact-mode")), "an entity use");
            }
            return false;
        }

        private boolean matchesHand(String mode, InteractionHand hand) {
            if ("None".equals(mode)) return false;
            if ("Both".equals(mode)) return true;
            return ("Mainhand".equals(mode) && hand == InteractionHand.MAIN_HAND) || ("Offhand".equals(mode) && hand == InteractionHand.OFF_HAND);
        }

        private boolean matchesRegistry(String id, List<String> entries, String mode) {
            return shouldCancelRegistryTarget(id, entries, mode);
        }

        static boolean shouldCancelRegistryTarget(String id, List<String> entries, String mode) {
            boolean whitelist = "WhiteList".equalsIgnoreCase(mode);
            if (entries == null || entries.isEmpty()) return false;
            boolean listed = false;
            for (String entry : entries) {
                if (RegistryListCodec.matches(id, entry)) {
                    listed = true;
                    break;
                }
            }
            return whitelist ? !listed : listed;
        }

        private boolean entityFilterAllowed(Entity entity) {
            if (entity == null) return false;
            if (!bool("nametagged") && entity.hasCustomName()) return false;
            if (!bool("babies") && entity instanceof LivingEntity living && living.isBaby()) return false;
            if (!bool("friends") && TeamsModule.isFriendOrTeam(entity)) return false;
            return true;
        }
    }

    static final class OffhandInteractModule extends Module {
        private boolean sendingSynthetic;

        OffhandInteractModule() {
            super("offhand-interact-placement", "OffhandInteract", ModuleCategory.PLAYER, "Uses the offhand too.");
            add(new BoolSetting("cancelMainhand", "Cancel Mainhand", false).group("General")
                .description("Only the offhand acts; the main-hand click is not sent"));
            add(new ChoiceSetting("main-hand", "Main Hand", "Any", "Any", "No Blocks", "Empty")
                .group("General")
                .description("What the main hand may hold for the offhand to join in"));
            add(new BoolSetting("sneak-only", "Sneak Only", false).group("General")
                .description("Only while you sneak"));
            add(new BoolSetting("skip-interactables", "Skip Interactables", false).group("General")
                .description("Leaves chests, doors, buttons and levers to the main hand so they do not toggle twice"));

            add(new ChoiceSetting("offhand-items", "Offhand Items", "Any", "Any", "Blocks Only", "List")
                .group("Items")
                .description("Which offhand items get used"));
            add(RegistryListSetting.items("item-list", "Item List", "")
                .group("Items")
                .description("Offhand items that get used")
                .visibleWhen(() -> "List".equals(choice("offhand-items"))));
        }

        private boolean conditionsAllow(BlockHitResult hit) {
            if (bool("sneak-only") && !MC.player.isShiftKeyDown()) return false;
            ItemStack main = MC.player.getMainHandItem();
            String mainRule = choice("main-hand");
            if ("Empty".equals(mainRule) && !main.isEmpty()) return false;
            if ("No Blocks".equals(mainRule) && main.getItem() instanceof BlockItem) return false;
            ItemStack off = MC.player.getOffhandItem();
            String itemRule = choice("offhand-items");
            if ("Blocks Only".equals(itemRule) && !(off.getItem() instanceof BlockItem)) return false;
            if ("List".equals(itemRule)) {
                String id = BuiltInRegistries.ITEM.getKey(off.getItem()).toString();
                boolean listed = false;
                for (String entry : list("item-list")) {
                    if (RegistryListCodec.matches(id, entry)) {
                        listed = true;
                        break;
                    }
                }
                if (!listed) return false;
            }
            if (bool("skip-interactables") && MC.level != null && hit != null) {
                BlockPos pos = hit.getBlockPos();
                BlockState state = MC.level.getBlockState(pos);
                Block block = state.getBlock();
                if (state.getMenuProvider(MC.level, pos) != null
                    || block instanceof net.minecraft.world.level.block.DoorBlock
                    || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                    || block instanceof net.minecraft.world.level.block.FenceGateBlock
                    || block instanceof net.minecraft.world.level.block.ButtonBlock
                    || block instanceof net.minecraft.world.level.block.LeverBlock) return false;
            }
            return true;
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            if (sendingSynthetic || MC.gameMode == null || MC.player == null) return false;
            if (packet instanceof ServerboundUseItemOnPacket use && DihPackets.hand(use) == InteractionHand.MAIN_HAND) {

                if (DihBlinkManager.holdsActionsWithoutMovement()) return false;

                if (DihHandArbiter.offhandClaimedByOther(id())) return false;

                if (AutoTotemModule.operationActive()) return false;

                if (!conditionsAllow(DihPackets.hitResult(use))) return false;

                if (ModuleRegistry.shouldCancelUseExcept(DihPackets.hitResult(use), InteractionHand.OFF_HAND, id())) return false;

                String placementOwner = DihPlacementTick.owner();
                if (placementOwner != null && !placementOwner.equals(id())) return false;

                if (!DihPlacementTick.claim(id())) return false;
                sendingSynthetic = true;
                try {

                    MC.gameMode.useItemOn(MC.player, InteractionHand.OFF_HAND, DihPackets.hitResult(use));
                } finally {
                    sendingSynthetic = false;
                }
                return bool("cancelMainhand");
            }
            return false;
        }
    }

    static final class SpamModule extends Module {
        private int timer;
        private int index;

        SpamModule() {
            super("spam", "Spam", ModuleCategory.MISC, "Sends timed chat.");
            add(new StringListSetting("messages", "Messages", "你好，世界！").description("Messages separated by |."));
            add(new IntSetting("delay", "Delay", 20, 0, 200, 1));
            add(new BoolSetting("disable-on-leave", "Disable On Leave", true).group("Safety"));
            add(new BoolSetting("disable-on-disconnect", "Disable On Disconnect", true).group("Safety"));
            add(new BoolSetting("randomise", "Randomise", false));
            add(new BoolSetting("auto-split-messages", "Auto Split", false).group("Splitting"));
            add(new IntSetting("split-length", "Split Length", 256, 8, 256, 8).group("Splitting").visibleWhen(() -> Boolean.parseBoolean(value("auto-split-messages"))).build());
            add(new IntSetting("split-delay", "Split Delay", 20, 0, 200, 1).group("Splitting").visibleWhen(() -> Boolean.parseBoolean(value("auto-split-messages"))).build());
            add(new BoolSetting("bypass", "Bypass", false).group("Bypass"));
            add(new BoolSetting("include-uppercase-characters", "Uppercase", false).group("Bypass").visibleWhen(() -> Boolean.parseBoolean(value("bypass"))).build());
            add(new IntSetting("length", "Bypass Length", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> Boolean.parseBoolean(value("bypass"))).build());
            add(new BoolSetting("uppercase", "Uppercase Legacy", false).group("Bypass").visibleWhen(() -> false).build());
            add(new IntSetting("bypass-length", "Bypass Length Legacy", 4, 1, 16, 1).group("Bypass").visibleWhen(() -> false).build());
        }

        @Override
        public void onEnable() {
            timer = integer("delay");
            index = 0;
        }

        @Override
        public void tick() {
            if (MC.getConnection() == null) return;
            if (timer-- > 0) return;
            timer = integer("delay");
            List<String> messages = splitMessages();
            if (messages.isEmpty()) return;
            int selected = bool("randomise") ? new Random().nextInt(messages.size()) : index++ % messages.size();
            String msg = messages.get(selected);
            if (bool("bypass")) msg += " " + bypassToken();
            if (bool("auto-split-messages") && msg.length() > integer("split-length")) {
                int split = Math.max(1, integer("split-length"));
                msg = msg.substring(0, split);
                timer = integer("split-delay");
            } else if (msg.length() > 256) msg = msg.substring(0, 256);
            if (msg.startsWith("/") && msg.length() > 1) MC.getConnection().sendCommand(msg.substring(1));

            else dihclient.commands.DihCommands.sendPlainChat(MC.getConnection(), msg);
        }

        @Override
        public void onGameLeft() {
            if (bool("disable-on-leave") || bool("disable-on-disconnect")) setEnabled(false);
        }

        private List<String> splitMessages() {
            List<String> messages = new ArrayList<>();
            for (String raw : text("messages").split("\\|")) {
                String msg = raw.trim();
                if (!msg.isEmpty()) messages.add(msg);
            }
            return messages;
        }

        private String bypassToken() {
            String chars = bool("include-uppercase-characters") || bool("uppercase") ? "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789" : "abcdefghijklmnopqrstuvwxyz0123456789";
            Random rng = new Random();
            StringBuilder out = new StringBuilder();
            int length = integer("length") != 4 || integer("bypass-length") == 4 ? integer("length") : integer("bypass-length");
            for (int i = 0; i < length; i++) out.append(chars.charAt(rng.nextInt(chars.length())));
            return out.toString();
        }
    }

    static final class BookBotModule extends Module {
        private int timer;
        private int count;
        private final Random random = new Random();

        BookBotModule() {
            super("bookbot", "BookBot", ModuleCategory.MISC, "Writes into books.");
            add(new ChoiceSetting("mode", "Mode", "Random", "Random", "File").description("Random or file text.").build());
            add(new ChoiceSetting("random-type", "Random Type", "Utf8", "Utf8", "Ascii", "PaperMC").visibleWhen(() -> "Random".equals(value("mode"))).build());
            add(new IntSetting("pages", "Pages", 100, 1, 100, 1).visibleWhen(() -> "Random".equals(value("mode")) && !"PaperMC".equals(value("random-type"))).build());
            add(new IntSetting("characters", "Chars/Page", DihBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE, 1, 1024, 16).visibleWhen(() -> "Random".equals(value("mode")) && !"PaperMC".equals(value("random-type"))).build());
            add(new IntSetting("delay", "Delay", 20, 1, 200, 1));
            add(new BoolSetting("sign", "Sign", true));
            add(new StringSetting("name", "Name", "Dih").visibleWhen(() -> Boolean.parseBoolean(value("sign"))).build());
            add(new BoolSetting("append-count", "Append Count", true).visibleWhen(() -> Boolean.parseBoolean(value("sign"))).build());
            add(new BoolSetting("word-wrap", "Word Wrap", true).visibleWhen(() -> "File".equals(value("mode"))).build());
            add(new StringSetting("file-path", "File", "").visibleWhen(() -> "File".equals(value("mode"))).formatter(BookBotModule::displayFileName).description("Selected file.").filePicker("pick-file"));
            add(new ActionSetting("pick-file", "Pick File", this::pickFile).availableOffline().visibleWhen(() -> false).description("Choose text file.").build());
            add(new StringSetting("text", "Text", "").visibleWhen(() -> false).build());
            add(new BoolSetting("full-default-migrated", "Full Default Migrated", false).visibleWhen(() -> false).build());
        }

        @Override
        public void onEnable() {
            upgradeOldLightweightDefaults();
            timer = integer("delay");
            count = 0;
        }

        @Override
        public void tick() {
            if (MC.player == null || MC.getConnection() == null) return;
            if ("File".equals(choice("mode"))) {
                String fileFailure = fileSelectionFailureMessage();
                if (fileFailure != null) {
                    disableWithToggleMessage(fileFailure);
                    return;
                }
            }
            if (timer-- > 0) return;
            timer = integer("delay");
            if (!prepareWritableBookInHand()) {
                disableWithToggleMessage("BookBot disabled: no empty writable book.");
                return;
            }
            ItemStack stack = MC.player.getMainHandItem();
            List<String> pages = buildPages();
            List<Filterable<Component>> filtered = new ArrayList<>();
            for (String page : pages) filtered.add(Filterable.passThrough(Component.literal(page)));
            String title = text("name").isBlank() ? "Dih" : text("name");
            if (bool("append-count") && count > 0) title += " #" + count;
            if (bool("sign")) {
                stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), MC.player.getGameProfile().name(), 0, filtered, true));
            } else {
                List<Filterable<String>> writablePages = new ArrayList<>();
                for (String page : pages) writablePages.add(Filterable.passThrough(page));
                stack.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(writablePages));
            }
            MC.getConnection().send(new ServerboundEditBookPacket(MC.player.getInventory().getSelectedSlot(), pages, bool("sign") ? Optional.of(title) : Optional.empty()));
            count++;
        }

        private boolean prepareWritableBookInHand() {
            if (MC.player == null) return false;
            if (isEmptyWritableBook(MC.player.getMainHandItem())) return true;
            int slot = findEmptyWritableBookSlot();
            if (slot < 0) return false;
            int selected = MC.player.getInventory().getSelectedSlot();
            if (slot > 8) {
                DihInventoryHelper.swapInventorySlots(MC, slot, selected);
                return true;
            }
            DihInventoryHelper.selectHotbarSlot(MC, slot);
            return true;
        }

        private int findEmptyWritableBookSlot() {
            if (MC.player == null) return -1;
            for (int i = 0; i < MC.player.getInventory().getContainerSize(); i++) {
                if (isEmptyWritableBook(MC.player.getInventory().getItem(i))) return i;
            }
            return -1;
        }

        private boolean isEmptyWritableBook(ItemStack stack) {
            if (stack == null || !stack.is(Items.WRITABLE_BOOK)) return false;
            WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            return content == null || content.pages().isEmpty();
        }

        private List<String> buildPages() {
            List<String> pages = new ArrayList<>();
            String custom = "File".equals(choice("mode")) ? fileText() : "";
            boolean randomMode = "Random".equals(choice("mode"));
            int pageCount = "File".equals(choice("mode")) ? 100 : Math.min(100, Math.max(1, integer("pages")));
            if (randomMode) {
                return DihBookPayloadBuilder.randomPages(pageCount, integer("characters"), choice("random-type"), random);
            }
            int chars = integer("characters");
            if (!custom.isBlank() && bool("word-wrap")) {

                pages.addAll(NbtBookAction.splitTextIntoPages(MC.font, custom, pageCount));
                return pages.isEmpty() ? List.of("Dih") : pages;
            }
            pages.addAll(NbtBookAction.chunkTextIntoPages(custom, pageCount, chars));
            return pages.isEmpty() ? List.of("Dih") : pages;
        }

        private void upgradeOldLightweightDefaults() {
            if (!"Random".equals(choice("mode"))) return;
            if (bool("full-default-migrated")) return;
            if (integer("pages") == 50) setValue("pages", "100");
            if (integer("characters") == 128 || integer("characters") == 512) setValue("characters", Integer.toString(DihBookPayloadBuilder.DEFAULT_CHARS_PER_PAGE));
            if ("Ascii".equals(choice("random-type"))) {
                setValue("random-type", "Utf8");
            }
            setValue("full-default-migrated", "true");
        }

        private String fileText() {
            String path = text("file-path").trim();
            if (path.isBlank()) return "";
            try {
                return DihBookFileReader.read(Path.of(path));
            } catch (Exception ex) {
                DihClientMessaging.sendPrefixed("BookBot file read failed: " + ex.getMessage());
                return "";
            }
        }

        private void pickFile() {
            String current = text("file-path").trim();
            DihFileDialogs.openFile("BookBot Text File", current, java.util.List.of("txt", "md", "json", "nbt.txt"),
                "Text files", selected -> setValue("file-path", selected));
        }

        private String fileSelectionFailureMessage() {
            String path = text("file-path").trim();
            if (path.isBlank()) {
                return "BookBot disabled: choose a file first.";
            }
            try {
                Path file = Path.of(path);
                if (!Files.isRegularFile(file)) {
                    return "BookBot disabled: file missing.";
                }
                if (Files.size(file) <= 0) {
                    return "BookBot disabled: file is empty.";
                }
                return null;
            } catch (Exception ex) {
                return "BookBot disabled: invalid file.";
            }
        }

        private static String displayFileName(String value) {
            if (value == null || value.isBlank()) return "No file selected";
            try {
                Path name = Paths.get(value).getFileName();
                return name == null ? value : name.toString();
            } catch (Exception ignored) {
                return value;
            }
        }

    }

    static final class PacketCancellerModule extends Module {
        private String cachedC2SValue = null;
        private String cachedS2CValue = null;
        private Set<Class<? extends Packet<?>>> cachedC2S = Set.of();
        private Set<Class<? extends Packet<?>>> cachedS2C = Set.of();

        PacketCancellerModule() {
            super("packet-canceller", "PacketCanceller", ModuleCategory.MISC, "Cancels selected packets.");
            add(new PacketListSetting("C2S-packets", "C2S Packets", "").description("Outgoing packet list."));
            add(new PacketListSetting("S2C-packets", "S2C Packets", "").description("Incoming packet list."));
        }

        @Override
        public boolean onPacketSend(Packet<?> packet) {
            return matches(packet, true);
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            return matches(packet, false);
        }

        @Override
        public String info() {
            return "C2S " + packets(true).size() + " | S2C " + packets(false).size();
        }

        private boolean matches(Packet<?> packet, boolean c2s) {
            if (packet == null) return false;
            if (packets(c2s).contains(packet.getClass())) return true;
            return legacyMatches(c2s ? text("C2S-packets") : text("S2C-packets"), packet);
        }

        private Set<Class<? extends Packet<?>>> packets(boolean c2s) {
            String value = c2s ? text("C2S-packets") : text("S2C-packets");
            if (c2s) {
                if (!value.equals(cachedC2SValue)) {
                    cachedC2SValue = value;
                    cachedC2S = PacketListCodec.resolvePackets(value, true);
                }
                return cachedC2S;
            }
            if (!value.equals(cachedS2CValue)) {
                cachedS2CValue = value;
                cachedS2C = PacketListCodec.resolvePackets(value, false);
            }
            return cachedS2C;
        }

        private boolean legacyMatches(String list, Packet<?> packet) {
            if (packet == null || list == null || list.isBlank()) return false;
            String simple = packet.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            String full = packet.getClass().getName().toLowerCase(Locale.ROOT);
            @SuppressWarnings("unchecked")
            Class<? extends Packet<?>> packetClass = (Class<? extends Packet<?>>) packet.getClass();
            String registry = String.valueOf(DihPacketRegistry.getName(packetClass)).toLowerCase(Locale.ROOT);
            for (String token : list.split("[,|]")) {
                String needle = token.trim().toLowerCase(Locale.ROOT);
                if (!needle.isEmpty() && (simple.equals(needle) || full.equals(needle) || registry.equals(needle))) return true;
            }
            return false;
        }
    }

    static final class AutoReconnectModule extends Module {
        AutoReconnectModule() {
            super("auto-reconnect", "AutoReconnect", ModuleCategory.MISC, "Reconnects after disconnect.");
            add(new DoubleSetting("delay", "Delay Sec", 3.5, 0.0, 60.0, 0.5));
        }
    }

    static final class BetterTooltipsModule extends Module {
        private ItemStack tooltipSizeCacheStack = ItemStack.EMPTY;
        private int tooltipSizeCacheBytes = -1;

        BetterTooltipsModule() {
            super("better-tooltips", "BetterTooltips", ModuleCategory.RENDER, "Adds item tooltip info.");
            boolean firstInstall = !DihConfig.getGlobal().modules.containsKey("better-tooltips");
            add(new StringSetting("nbt-shortcut", "NBT Shortcut", "Ctrl+Shift+Right-click").group("Info").readonlySummary());
            add(new BoolSetting("block-nbt-inspect", "Block NBT Inspect", false).group("Info").description("Ctrl+Shift+Right-click blocks.").build());
            add(new BoolSetting("open-contents", "Open Contents", true).group("Containers").description("Preview only.").visibleWhen(() -> false).build());
            add(new BoolSetting("nbt-hint", "NBT Hint", true).group("Info").description("Shows the NBT shortcut line.").build());
            add(new BoolSetting("containers", "Containers", true).group("Containers").description("Stack count of shulkers and other containers.").build());
            add(new BoolSetting("compact-shulker-tooltip", "Compact Shulker", true).group("Containers").description("Compact counts.").build());
            add(new BoolSetting("echests", "Ender Chests", true).group("Containers").description("Server-side data.").visibleWhen(() -> false).build());
            add(new BoolSetting("maps", "Maps", true).group("Items").description("Map id.").build());
            add(new DoubleSetting("map-scale", "Map Scale", 1.0, 0.5, 4.0, 0.1).group("Items").description("Map preview scale.").visibleWhen(() -> false).build());
            add(new BoolSetting("books", "Books", true).group("Items").description("Page count of books.").build());
            add(new BoolSetting("banners", "Banners", true).group("Items").description("Banner patterns.").build());
            add(new BoolSetting("buckets", "Buckets", true).group("Items").description("Marks buckets holding a mob.").build());
            add(new BoolSetting("bundles", "Bundles", true).group("Items").description("Stack count of bundles.").build());
            add(new BoolSetting("minecraft-name", "Minecraft Name", true).group("Info").description("The item's id, such as minecraft:stone.").build());
            add(new BoolSetting("durability", "Durability", true).group("Info").description("Uses left out of the total.").build());
            add(new BoolSetting("durability-percent", "Durability %", false).group("Info")
                .description("Adds the uses left as a percentage.").visibleWhen(() -> bool("durability")).build());
            add(new BoolSetting("repair-cost", "Repair Cost", false).group("Info")
                .description("Anvil repair cost in levels, when the item has one.").build());
            add(new BoolSetting("food-info", "Food Info", true).group("Info").description("Hunger and saturation restored.").build());
            add(new BoolSetting("byte-size", "Byte Size", true).group("Info").description("How big the item's data is.").build());
            add(new ChoiceSetting("byte-size-format", "Byte Format", "Both", "Raw", "Rounded", "Both").group("Info")
                .description("Bytes, KiB, or both.").visibleWhen(() -> bool("byte-size")).build());
            if (firstInstall) setEnabledSilently(true);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public void appendTooltip(ItemStack stack, List<?> lines) {
            if (stack == null || stack.isEmpty()) return;
            List raw = lines;
            if (bool("nbt-hint")) raw.add(Component.literal("NBT: Ctrl+Shift+Right-click").withStyle(ChatFormatting.DARK_GRAY));
            if (bool("minecraft-name")) raw.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("durability") && stack.isDamageableItem()) {
                int maxDamage = Math.max(1, stack.getMaxDamage());
                int remaining = Math.max(0, maxDamage - stack.getDamageValue());
                String percent = bool("durability-percent") ? " (" + Math.round(remaining * 100.0 / maxDamage) + "%)" : "";
                raw.add(Component.literal("Durability: " + remaining + " / " + maxDamage + percent).withStyle(ChatFormatting.GRAY));
            }
            if (bool("repair-cost")) {
                Integer cost = stack.get(DataComponents.REPAIR_COST);
                if (cost != null && cost > 0) raw.add(Component.literal("Repair cost: " + cost).withStyle(ChatFormatting.GRAY));
            }
            if (bool("food-info") && stack.has(DataComponents.FOOD)) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null) raw.add(Component.literal("Food: " + food.nutrition() + " / " + String.format(Locale.ROOT, "%.1f", food.saturation())).withStyle(ChatFormatting.GRAY));
            }
            if (bool("containers") && stack.has(DataComponents.CONTAINER)) {
                ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
                if (contents != null) {
                    long stacks = contents.nonEmptyItemCopyStream().count();
                    raw.add(Component.literal("Container: " + stacks + " stacks").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            if (bool("bundles") && stack.has(DataComponents.BUNDLE_CONTENTS)) {
                BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
                if (contents != null) raw.add(Component.literal("Bundle: " + contents.size() + " stacks").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (bool("maps") && stack.has(DataComponents.MAP_ID)) raw.add(Component.literal("Map: " + stack.get(DataComponents.MAP_ID)).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("banners") && stack.has(DataComponents.BANNER_PATTERNS)) raw.add(Component.literal("Banner patterns: " + stack.get(DataComponents.BANNER_PATTERNS)).withStyle(ChatFormatting.DARK_GRAY));
            if (bool("buckets") && stack.has(DataComponents.BUCKET_ENTITY_DATA)) raw.add(Component.literal("Bucket entity data").withStyle(ChatFormatting.DARK_GRAY));
            if (bool("books") && stack.has(DataComponents.WRITTEN_BOOK_CONTENT)) {
                WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null) raw.add(Component.literal("Pages: " + content.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
            } else if (bool("books") && stack.has(DataComponents.WRITABLE_BOOK_CONTENT)) {
                WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
                if (content != null) raw.add(Component.literal("Pages: " + content.pages().size()).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (bool("byte-size")) {
                int bytes = getTooltipSizeBytes(stack);
                if (bytes >= 0) raw.add(Component.literal("Bytes: " + formatBytes(bytes)).withStyle(ChatFormatting.DARK_GRAY));
                else raw.add(Component.literal("Bytes: unreadable (protected)").withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        private String formatBytes(int bytes) {
            String mode = choice("byte-size-format");
            double kib = bytes / 1024.0;
            return switch (mode) {
                case "Raw" -> Integer.toString(bytes);
                case "Rounded" -> String.format(Locale.ROOT, "%.1f KiB", kib);
                default -> bytes + " (" + String.format(Locale.ROOT, "%.1f KiB", kib) + ")";
            };
        }

        private int getTooltipSizeBytes(ItemStack stack) {
            if (!tooltipSizeCacheStack.isEmpty()
                && tooltipSizeCacheStack.getCount() == stack.getCount()
                && ItemStack.isSameItemSameComponents(tooltipSizeCacheStack, stack)) {
                return tooltipSizeCacheBytes;
            }

            if (MC == null || MC.player == null) return -1;

            int bytes = DihItemNbtSanity.encodedSizeBytes(stack, MC.player.registryAccess());
            tooltipSizeCacheStack = stack.copy();
            tooltipSizeCacheBytes = bytes;
            return bytes;
        }
    }

    static final class FullbrightModule extends Module {
        private String lastMode = "";
        private String lastLightType = "";
        private int lastMinimumLightLevel = Integer.MIN_VALUE;
        private boolean appliedNightVision;
        private boolean lastShowIcon = true;

        FullbrightModule() {
            super("fullbright", "Fullbright", ModuleCategory.RENDER, "Brightens the world.");
            boolean firstInstall = !DihConfig.getGlobal().modules.containsKey("fullbright");
            add(new ChoiceSetting("mode", "Mode", "Gamma", "Gamma", "Potion", "Luminance").group("General")
                .description("How lighting is boosted.").build());
            add(new BoolSetting("show-icon", "Effect Icon", true).group("General")
                .description("Shows the night vision icon.").visibleWhen(() -> "Potion".equals(value("mode"))).build());
            add(new ChoiceSetting("light-type", "Light Type", "BLOCK", "SKY", "BLOCK").group("General")
                .description("Which light to raise.").visibleWhen(() -> "Luminance".equals(value("mode"))).build());
            add(new IntSetting("minimum-light-level", "Min Light", 8, 0, 15, 1).group("General")
                .description("Lowest light level anywhere.").visibleWhen(() -> "Luminance".equals(value("mode"))).build());
            if (firstInstall) setEnabledSilently(true);
        }

        @Override
        public void onEnable() {
            ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onDisable() {
            disableNightVision();
            appliedNightVision = false;
            ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void tick() {
            String mode = choice("mode");
            String lightType = choice("light-type");
            int minimumLightLevel = integer("minimum-light-level");
            if (appliedNightVision && bool("show-icon") != lastShowIcon) {
                disableNightVision();
                appliedNightVision = false;
            }
            lastShowIcon = bool("show-icon");
            boolean changed = !mode.equals(lastMode)
                || !lightType.equals(lastLightType)
                || minimumLightLevel != lastMinimumLightLevel;
            if (changed) {
                lastMode = mode;
                lastLightType = lightType;
                lastMinimumLightLevel = minimumLightLevel;
                if (!"Potion".equals(mode) && appliedNightVision) {
                    disableNightVision();
                    appliedNightVision = false;
                }
                ModuleRenderUtil.refreshWorldRenderer();
            }
            if ("Potion".equals(mode)) applyNightVision();
        }

        public boolean gammaActive() {
            return isEnabled() && "Gamma".equals(choice("mode"));
        }

        public int luminance(String lightType) {
            if (!isEnabled() || !"Luminance".equals(choice("mode")) || !choice("light-type").equals(lightType)) return 0;
            return integer("minimum-light-level");
        }

        private void applyNightVision() {
            if (MC.player == null) return;
            MobEffectInstance instance = MC.player.getEffect(MobEffects.NIGHT_VISION);
            if (instance != null) {
                if (instance.getDuration() < 420) ((DihMobEffectInstanceAccessor) instance).dih$setDuration(420);
            } else {
                MC.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 420, 0, false, true, bool("show-icon")));
            }
            appliedNightVision = true;
        }

        private void disableNightVision() {
            if (MC.player != null && MC.player.hasEffect(MobEffects.NIGHT_VISION)) MC.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    static final class XrayModule extends Module {
        private String lastTintKey = "";

        XrayModule() {
            super("xray", "Xray", ModuleCategory.RENDER, "Shows selected blocks.");

            add(new ChoiceSetting("mode", "Mode", "Normal", "Normal", "OreSim")
                .description("Real blocks or seed.").build());
            add(new ChoiceSetting("render-style", "Rendering Style", "Default", "Default", "ESP")
                .description("See-through or boxes.").build());
            add(RegistryListSetting.blocks("whitelist", "Whitelist", defaultOreList())
                .description("Blocks to show.").visibleWhen(() -> !oreSimMode()).build());

            add(RegistryListSetting.oreSimOres("oresim-ores", "Ores",
                    String.join("|", DihOreSimOre.ORE_SIM_BLOCK_IDS))
                .description("Ores to predict.").visibleWhen(this::oreSimMode).build());

            add(new IntSetting("opacity", "Opacity", 25, 0, 255, 1)
                .description("Hidden block alpha.").visibleWhen(this::tintStyle).build());
            add(new ChoiceSetting("fluid-opacity", "Fluid Opacity", "Both", "None", "Water", "Lava", "Both")
                .description("Visible fluids.").visibleWhen(this::tintStyle).build());
            add(new BoolSetting("exposed-only", "Exposed Only", false)
                .description("Only exposed ores.").visibleWhen(() -> tintStyle() && !oreSimMode()).build());
            add(new BoolSetting("fill", "Fill", true)
                .description("Shade box interiors.").visibleWhen(this::espStyle).build());
            add(new IntSetting("fill-opacity", "Fill Opacity", 30, 5, 100, 5).unit("%")
                .description("How solid the box fill is.").visibleWhen(() -> espStyle() && bool("fill")).build());
            add(new DoubleSetting("line-width", "Line Width", 1.5, 0.5, 6.0, 0.25)
                .description("Box edge thickness.").visibleWhen(this::espStyle).build());

            add(new StringSetting("oresim-seed", "World Seed", "")
                .description("Seed for this world")
                .keepOnReset()
                .requiresWorld().visibleWhen(this::oreSimMode).build());
            add(new IntSetting("oresim-radius", "Simulation Radius", 2, 1,
                    DihOreSimEngine.MAX_SIM_RADIUS, 1)
                .description("Chunk radius to simulate")
                .visibleWhen(this::oreSimMode).build());

            for (DihOreSimOre.Kind kind : DihOreSimOre.Kind.values()) {
                add(new ColorSetting(kind.colorId(), kind.label, kind.defaultColor).group("Colors")
                    .visibleWhen(() -> espStyle() && ModuleOreSim.whitelistHasFamily(this, kind)).build());
            }
            syncCustomColorSettings();
        }

        private boolean tintStyle() {
            return !espStyle();
        }

        private boolean espStyle() {
            return "ESP".equals(value("render-style"));
        }

        private boolean oreSimMode() {
            return "OreSim".equals(value("mode"));
        }

        @Override
        protected String externalSettingValue(String settingId) {
            if (!"oresim-seed".equals(settingId)) return null;
            DihConfig.ModuleState configured = DihConfig.getGlobal().modules.get(id());
            String legacy = configured == null || configured.settings == null
                ? "" : configured.settings.getOrDefault(settingId, "");
            return DihOreSimSeedStore.get().value(DihWaypoints.scopeKey(MC), legacy);
        }

        @Override
        protected boolean setExternalSettingValue(String settingId, String value) {
            if (!"oresim-seed".equals(settingId)) return false;

            DihOreSimSeedStore.get().put(DihWaypoints.scopeKey(MC), value);
            return true;
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("whitelist".equals(settingId)) syncCustomColorSettings();
            if ("oresim-seed".equals(settingId)) DihOreSimEngine.clear();
        }

        private void syncCustomColorSettings() {
            for (String id : ModuleOreSim.nonOreWhitelistIds(this)) {
                String key = "block-color-" + id;
                if (setting(key) != null) continue;
                String label = dihclient.util.DihRegistryLabels.block(id);
                add(new ColorSetting(key, label.isBlank() ? id : label, ModuleOreSim.OTHER_COLOR).group("Colors")
                    .visibleWhen(() -> espStyle() && !oreSimMode() && ModuleOreSim.whitelistHasId(this, id)).build());
            }
        }

        @Override
        public void onEnable() {
            if (tintStyle()) ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onDisable() {

            DihOreSimEngine.suspend();
            if (tintStyle()) ModuleRenderUtil.refreshWorldRenderer();
        }

        @Override
        public void onGameLeft() {

            DihOreSimEngine.clear();
            DihOreSimEngine.forgetDisproven();
            DihOreGhostModels.clear();
        }

        @Override
        public void tick() {

            String tintKey = value("mode") + '|' + value("render-style") + '|' + integer("opacity")
                + '|' + value("fluid-opacity") + '|' + bool("exposed-only")
                + '|' + (oreSimMode() ? "" : value("whitelist"));
            if (!tintKey.equals(lastTintKey)) {
                lastTintKey = tintKey;
                ModuleRenderUtil.refreshWorldRenderer();
            }
            ModuleOreSim.tick(this, MC.level, MC.player);
        }

        @Override
        public String info() {
            return ModuleOreSim.info(this);
        }

        private static String defaultOreList() {
            return String.join("|", DihOreSimOre.ORE_SIM_BLOCK_IDS);
        }
    }

    static final class FreecamModule extends Module {
        FreecamModule() {
            super("freecam", "Freecam", ModuleCategory.RENDER, "Move the camera away from the player.");
            add(new DoubleSetting("speed", "Speed", 1.0, 0.05, 30.0, 0.05).group("Movement").description("Horizontal camera speed."));
            add(new DoubleSetting("vertical-speed", "Vertical Speed", 1.0, 0.05, 30.0, 0.05).group("Movement")
                .description("Vertical camera speed."));
            add(new IntSetting("walk-speed", "Walk Speed", 50, 5, 100, 5).unit("%").group("Movement")
                .description("Speed without sprint, as a share of the full speed."));
            add(new BoolSetting("follow-pitch", "Follow Pitch", false).group("Movement")
                .description("Forward moves where you look, up and down included."));
            add(new BoolSetting("scroll-speed", "Scroll Speed", true).group("Movement")
                .description("Scroll the wheel to change the speed."));
            add(new DoubleSetting("scroll-step", "Scroll Step", 0.5, 0.05, 5.0, 0.05).group("Movement")
                .description("Speed change per scroll notch.").visibleWhen(() -> bool("scroll-speed")));
            add(new IntSetting("sensitivity", "Sensitivity", 100, 10, 300, 5).unit("%").group("Movement")
                .description("How fast the camera turns."));

            add(new BoolSetting("reload-chunks", "Reload Chunks", true).group("General").description("Refresh chunk culling."));
            add(new BoolSetting("interact", "Interact", true).group("General").description("Interact from freecam view"));
            add(new BoolSetting("disable-on-damage", "Disable On Damage", false).group("General")
                .description("Turns off when you take damage."));
            add(new BoolSetting("disable-on-death", "Disable On Death", true).group("General")
                .description("Turns off when you die."));
        }

        private float lastHealth = -1.0F;

        @Override
        public void onEnable() {
            PackFreecamState.enable(bool("reload-chunks"));
            PackFreecamState.setInteractEnabled(bool("interact"));
            PackFreecamState.setSensitivity(integer("sensitivity") / 100.0);
            lastHealth = MC.player == null ? -1.0F : MC.player.getHealth();
        }

        @Override
        public void onDisable() {
            PackFreecamState.disable();
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("interact".equals(settingId)) PackFreecamState.setInteractEnabled(bool("interact"));
            if ("sensitivity".equals(settingId)) PackFreecamState.setSensitivity(integer("sensitivity") / 100.0);
        }

        @Override
        public void onGameLeft() {
            setEnabledSilently(false);
        }

        @Override
        public void tick() {

            if (MC.player == null) return;
            if (bool("disable-on-death") && MC.player.isDeadOrDying()) {
                disableWithToggleMessage("Freecam disabled: you died.");
                return;
            }
            float health = MC.player.getHealth();
            if (bool("disable-on-damage") && lastHealth >= 0.0F && health < lastHealth) {
                disableWithToggleMessage("Freecam disabled: you took damage.");
            }
            lastHealth = health;
        }

        @Override
        public void preMovementTick() {
            PackFreecamState.tickMovement(decimal("speed"), decimal("vertical-speed"),
                integer("walk-speed") / 100.0, bool("follow-pitch"));
        }

        @Override
        public Vec3 onPlayerMove(MoverType type, Vec3 movement) {
            return PackFreecamState.onPlayerMove(type, movement);
        }

        @Override
        public String info() {
            return String.format(Locale.ROOT, "%.2f", decimal("speed"));
        }
    }

    static final class EspModule extends Module {
        EspModule() {
            super("esp", "ESP", ModuleCategory.RENDER, "Highlights entities.");
            add(new ChoiceSetting("mode", "Mode", "2D", "Shader", "2D").group("General")
                .description("Entity highlight style.").build());
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").group("General")
                .description("Entities to highlight.").build());
            add(new DoubleSetting("max-distance", "Max Distance", 0.0, 0.0, 512.0, 8.0).group("General")
                .description("Farthest entity highlighted; 0 is no limit.").build());
            add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General")
                .description("Fades entities closer than this to the camera.").build());
            add(new BoolSetting("ignore-invisible", "Ignore Invisible", false).group("General")
                .description("Skips invisible entities.").build());

            add(new ChoiceSetting("box-style", "Box Style", "Full", "Full", "Corners").group("2D")
                .description("Whole box or only its corners.").visibleWhen(() -> "2D".equals(choice("mode"))).build());
            add(new BoolSetting("fill", "Fill", false).group("2D")
                .description("Shade inside the box.").visibleWhen(() -> "2D".equals(choice("mode"))).build());
            add(new IntSetting("fill-alpha", "Fill Alpha", 50, 5, 255, 5).group("2D")
                .description("Fill strength out of 255.").visibleWhen(() -> "2D".equals(choice("mode")) && bool("fill")).build());
            add(new BoolSetting("border", "Border", true).group("2D")
                .description("Black edge around the box.").visibleWhen(() -> "2D".equals(choice("mode"))).build());
            add(new BoolSetting("health-bar", "Health Bar", true).group("2D")
                .description("Health bar beside living entities.").visibleWhen(() -> "2D".equals(choice("mode"))).build());
            add(new BoolSetting("smooth", "Smooth", true).group("2D")
                .description("Eases the box between frames.").visibleWhen(() -> "2D".equals(choice("mode"))).build());

            add(new BoolSetting("distance-color", "Distance Color", false).group("Colors")
                .description("Colour by distance, red near to green far.").build());
            add(new IntSetting("color-distance", "Color Distance", 40, 8, 256, 4).group("Colors")
                .description("Distance that counts as far.").visibleWhen(() -> bool("distance-color")).build());
            add(new ColorSetting("players-color", "Players", 0xCCFFFFFF).group("Colors")
                .description("Player colour.").visibleWhen(() -> !bool("distance-color")).build());
            add(new ColorSetting("animals-color", "Animals", 0xCC74FF8F).group("Colors")
                .description("Passive mob colour.").visibleWhen(() -> !bool("distance-color")).build());
            add(new ColorSetting("water-animals-color", "Water Animals", 0xCC66D9FF).group("Colors")
                .description("Water mob colour.").visibleWhen(() -> !bool("distance-color")).build());
            add(new ColorSetting("monsters-color", "Monsters", 0xCCFF4A4A).group("Colors")
                .description("Hostile mob colour.").visibleWhen(() -> !bool("distance-color")).build());
            add(new ColorSetting("ambient-color", "Ambient", 0xCCB78CFF).group("Colors")
                .description("Bat and other ambient mob colour.").visibleWhen(() -> !bool("distance-color")).build());
            add(new ColorSetting("misc-color", "Misc", 0xCCCCCCCC).group("Colors")
                .description("Colour for everything else.").visibleWhen(() -> !bool("distance-color")).build());

            add(new BoolSetting("skeleton", "Skeleton", false).group("Skeleton")
                .description("Draw animated skeletons.").build());
            add(new ChoiceSetting("skeleton-color-mode", "Color Mode", "Static", "Static", "Distance")
                .group("Skeleton").visibleWhen(() -> bool("skeleton")).description("Skeleton color source.").build());
            add(new ColorSetting("skeleton-color", "Color", 0xFFFFFFFF).group("Skeleton").description("Skeleton colour.")
                .visibleWhen(() -> bool("skeleton") && "Static".equals(value("skeleton-color-mode"))));
            add(new DoubleSetting("skeleton-width", "Line Width", 2.0, 1.0, 6.0, 0.25).group("Skeleton")
                .visibleWhen(() -> bool("skeleton")).description("Skeleton line thickness.").build());
        }
    }

    static final class ItemEspModule extends Module {
        ItemEspModule() {
            super("item-esp", "ItemESP", ModuleCategory.RENDER, "Outlines dropped items with the shader outline.");
            add(new ChoiceSetting("mode", "Mode", "Shader", "Shader").group("General")
                .description("Dropped item highlight style.").build());
            add(new ChoiceSetting("items-mode", "Items", "All", "All", "Some").group("General").description("Which items to outline").build());
            add(RegistryListSetting.items("items", "Item List", "")
                .group("General")
                .visibleWhen(() -> "Some".equals(value("items-mode")))
                .description("Item ids to outline")
                .build());
            add(new IntSetting("min-count", "Min Stack", 1, 1, 64, 1)
                .group("General")
                .description("Skips stacks smaller than this")
                .build());
            add(new BoolSetting("enchanted-only", "Enchanted Only", false)
                .group("General")
                .description("Only enchanted items")
                .build());
            add(new DoubleSetting("max-distance", "Max Distance", 64.0, 0.0, 256.0, 1.0)
                .group("General")
                .description("0 = unlimited.")
                .build());
            add(new DoubleSetting("fade-distance", "Fade Distance", 3.0, 0.0, 12.0, 0.25).group("General")
                .description("Fades items closer than this to the camera.").build());
            add(new BoolSetting("tracers", "Tracers", false).group("Tracers")
                .description("Line to each outlined item").build());
            add(new DoubleSetting("tracer-width", "Tracer Width", 2.0, 1.0, 6.0, 0.25).group("Tracers")
                .description("Line thickness").visibleWhen(() -> bool("tracers")).build());
            add(new ChoiceSetting("color-mode", "Color Mode", "Dynamic", "Dynamic", "Static")
                .group("Colors")
                .description("Dynamic colours each item by what it is")
                .build());
            add(new ColorSetting("color", "Color", 0xCCFFD76A)
                .group("Colors")
                .description("Outline colour")
                .visibleWhen(() -> "Static".equals(value("color-mode"))));
        }
    }

    static final class TracersModule extends Module {
        TracersModule() {
            super("tracers", "Tracers", ModuleCategory.RENDER, "Draws tracer lines to entities.");
            add(RegistryListSetting.entityTypes("entities", "Entities", "minecraft:player").group("General")
                .description("Traced entities.").build());
            add(new IntSetting("max-distance", "Max Distance", 256, 0, 512, 16).group("General")
                .description("Farthest entity traced; 0 is no limit."));
            add(new BoolSetting("ignore-invisible", "Ignore Invisible", false).group("General")
                .description("Skips invisible entities."));
            add(new ChoiceSetting("target-point", "Target", "Feet", "Feet", "Body", "Head").group("General")
                .description("Where on the entity the line ends.").build());
            add(new DoubleSetting("line-width", "Line Width", 2.0, 2.0, 6.0, 0.25).group("General").description("Tracer thickness."));
            add(new BoolSetting("height-line", "Height Line", false).group("General").description("Vertical line up the entity."));
            add(new IntSetting("opacity", "Opacity", 100, 10, 100, 5).unit("%").group("Colors")
                .description("Line opacity."));
            add(new BoolSetting("distance-color", "Distance Color", false).group("Colors").description("Color by distance"));
            add(new IntSetting("color-distance", "Color Distance", 40, 8, 256, 4).group("Colors")
                .description("Far color distance").visibleWhen(() -> bool("distance-color")));
            add(new ColorSetting("players-color", "Players", 0xCCFFFFFF).group("Colors").description("Player colour.")
                .visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("animals-color", "Animals", 0xCC74FF8F).group("Colors").description("Passive mob colour.")
                .visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("water-animals-color", "Water Animals", 0xCC66D9FF).group("Colors").description("Water mob colour.")
                .visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("monsters-color", "Monsters", 0xCCFF4A4A).group("Colors").description("Hostile mob colour.")
                .visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("ambient-color", "Ambient", 0xCCB78CFF).group("Colors").description("Ambient mob colour.")
                .visibleWhen(() -> !bool("distance-color")));
            add(new ColorSetting("misc-color", "Misc", 0xCCCCCCCC).group("Colors").description("Colour for everything else.")
                .visibleWhen(() -> !bool("distance-color")));
        }

        @Override
        public boolean shouldTraceEntity(Entity entity) {
            return ModuleRenderUtil.shouldTrace(entity);
        }

        @Override
        public int traceColor(Entity entity) {
            return ModuleRenderUtil.tracerColor(entity);
        }
    }

    static final class StorageEspModule extends Module {
        StorageEspModule() {
            super("storage-esp", "StorageESP", ModuleCategory.RENDER, "Highlights chests, shulkers, and other storage.");

            add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
            add(new IntSetting("fill-opacity", "Fill Opacity", 30, 5, 100, 5).unit("%").group("General")
                .description("How solid the fill is.").visibleWhen(() -> bool("fill")));
            add(new BoolSetting("outline", "Outline", true).group("General").description("Draw the box edges."));
            add(new DoubleSetting("line-width", "Line Width", 1.5, 0.5, 6.0, 0.25).group("General")
                .description("Edge thickness.").visibleWhen(() -> bool("outline")));
            add(new BoolSetting("meshing", "Meshing", true).group("General")
                .description("Merge adjacent storage."));

            add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each target"));
            add(new DoubleSetting("tracer-width", "Tracer Width", 2.0, 1.0, 6.0, 0.25).group("Tracers")
                .description("Line thickness.").visibleWhen(() -> bool("tracers")));

            add(RegistryListSetting.storages("storage-list", "Storage", ModuleStorageEsp.DEFAULT_VALUE)
                .group("Storage").description("Blocks and entities"));

            add(new ColorSetting("chest-color", "Chest", 0xCCFFA000).group("Colors").description("Chest + chest minecart/boat."));
            add(new ColorSetting("trapped-chest-color", "Trapped Chest", 0xCCFF2020).group("Colors").description("Trapped chests."));
            add(new ColorSetting("ender-chest-color", "Ender Chest", 0xCC7800FF).group("Colors").description("Ender chests."));
            add(new ColorSetting("barrel-color", "Barrel", 0xCCFFA000).group("Colors").description("Barrels."));
            add(new ColorSetting("shulker-color", "Shulker Box", 0xCCB766FF).group("Colors").description("Shulker boxes."));
            add(new ColorSetting("hopper-color", "Hopper", 0xCC7C8AFF).group("Colors").description("Hopper + minecart."));
            add(new ColorSetting("dispenser-color", "Dispenser", 0xCCB04848).group("Colors").description("Dispenser + dropper."));
            add(new ColorSetting("furnace-color", "Furnace", 0xCCCCB266).group("Colors").description("Furnaces + brewing stand."));
            add(new ColorSetting("crafter-color", "Crafter", 0xCCD8AE6B).group("Colors").description("Crafter, pot, bookshelf"));
            add(new ColorSetting("other-color", "Other", 0xFF8C8C8C).group("Colors").description("Fallback."));

            for (ModuleStorageStructures.Structure structure : ModuleStorageStructures.ALL) {
                add(new BoolSetting(structure.settingId(), structure.label(), false).group("Ignore Structures")
                    .description("Hides storage generated in this structure."));
            }
        }
    }

    static final class SpawnerEspModule extends Module {
        SpawnerEspModule() {
            super("spawner-esp", "SpawnerESP", ModuleCategory.RENDER, "Highlights monster spawners by spawned mob.");

            add(new BoolSetting("fill", "Fill", false).group("General").description("Draw translucent filled boxes."));
            add(new IntSetting("fill-opacity", "Fill Opacity", 30, 5, 100, 5).unit("%").group("General")
                .description("How solid the fill is.").visibleWhen(() -> bool("fill")));
            add(new BoolSetting("outline", "Outline", true).group("General").description("Draw the box edges."));
            add(new DoubleSetting("line-width", "Line Width", 1.5, 0.5, 6.0, 0.25).group("General")
                .description("Edge thickness.").visibleWhen(() -> bool("outline")));
            add(new BoolSetting("meshing", "Meshing", true).group("General")
                .description("Merge adjacent spawners."));
            add(new BoolSetting("tracers", "Tracers", false).group("Tracers").description("Line to each spawner"));
            add(new DoubleSetting("tracer-width", "Tracer Width", 2.0, 1.0, 6.0, 0.25).group("Tracers")
                .description("Line thickness.").visibleWhen(() -> bool("tracers")));
            add(RegistryListSetting.entityTypes("spawner-list", "Spawners", ModuleSpawnerEsp.NATURAL_DEFAULT_VALUE)
                .group("Spawners").description("Mobs to highlight."));

            for (ModuleSpawnerEsp.NaturalTarget target : ModuleSpawnerEsp.NATURAL) {
                String id = target.id();
                add(new ColorSetting("color-" + id, target.label(), target.color()).group("Colors")
                    .description("Spawners of this mob.")
                    .visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, id)));
            }
        }

        @Override
        public void onOptionValueChanged(String settingId) {
            if ("spawner-list".equals(settingId)) syncCustomColorSettings();
        }

        private void syncCustomColorSettings() {
            for (String id : ModuleSpawnerEsp.enabledIds(this)) {
                if (ModuleSpawnerEsp.isNatural(id)) continue;
                String key = "color-" + id;
                if (setting(key) != null) continue;
                String label = dihclient.util.DihRegistryLabels.entity(id);
                add(new ColorSetting(key, label.isBlank() ? id : label, 0xCCFF3B3B).group("Colors")
                    .visibleWhen(() -> ModuleSpawnerEsp.enabledContains(this, id)));
            }
        }
    }

    static final class BlockEspModule extends Module {
        BlockEspModule() {
            super("block-esp", "BlockESP", ModuleCategory.RENDER, "Highlights selected blocks.");
            add(new IntSetting("max-targets", "Max Blocks", 1024, 64, 8192, 64)
                .group("General")
                .description("Max rendered blocks"));
            add(new BoolSetting("fill", "Fill", false)
                .group("General")
                .description("Draw translucent filled boxes."));
            add(new IntSetting("fill-opacity", "Fill Opacity", 30, 5, 100, 5).unit("%").group("General")
                .description("How solid the fill is.").visibleWhen(() -> bool("fill")));
            add(new BoolSetting("outline", "Outline", true).group("General").description("Draw the box edges."));
            add(new DoubleSetting("line-width", "Line Width", 1.5, 0.5, 6.0, 0.25).group("General")
                .description("Edge thickness.").visibleWhen(() -> bool("outline")));
            add(new BoolSetting("meshing", "Meshing", true)
                .group("General")
                .description("Merge adjacent targets."));
            add(new BoolSetting("tracers", "Tracers", false)
                .group("Tracers")
                .description("Line to each target"));
            add(new DoubleSetting("tracer-width", "Tracer Width", 2.0, 1.0, 6.0, 0.25).group("Tracers")
                .description("Line thickness.").visibleWhen(() -> bool("tracers")));
            add(RegistryListSetting.blocks("blocks", "Blocks", "minecraft:diamond_ore")
                .group("Blocks")
                .description("Blocks to highlight."));
            add(new BoolSetting("barriers", "Barriers", false)
                .group("Blocks")
                .description("Show invisible barrier blocks."));
            add(new ColorSetting("color", "Color", 0xCCFF3B3B).group("Colors")
                .description("Colour for non-ore blocks. Ores are coloured by type."));
        }
    }

    static final class HudModule extends Module {
        HudModule() {
            super("hud", "HUD", ModuleCategory.MISC, "Shows HUD elements.");
            boolean firstInstall = !DihConfig.getGlobal().modules.containsKey("hud");
            add(new ActionSetting("edit-hud", "Edit HUD", this::openHudEditor).availableOffline().description("Open HUD editor.").group("Editor"));
            add(new BoolSetting("editor-grid", "Editor Grid", true).description("Show editor grid.").group("Editor"));
            add(new BoolSetting("hide-in-guis", "Hide In GUIs", true).description("Hide in screens.").group("Visibility"));
            add(new BoolSetting("show-in-chat", "Show In Chat", false).description("Show in chat.").group("Visibility"));
            add(new BoolSetting("show-in-pause", "Show In Pause", false).description("Show in pause.").group("Visibility"));
            add(new BoolSetting("show-in-hud-editor", "Show In HUD Editor", true).description("Show while editing.").group("Visibility"));
            if (firstInstall) setEnabledSilently(true);
        }

        @Override
        public void onEnable() {
            DihHudManager.ensureDefaults();
            ServerTickTracker.reset();
        }

        @Override
        public void tick() {

            DihHudManager.tickHeartbeat();
        }

        @Override
        protected void onSettingsReset() {
            DihHudManager.resetAllElements();
        }

        private void openHudEditor() {
            DihHudManager.ensureDefaults();
            MC.gui.setScreen(new DihHudEditorScreen(MC.gui.screen()));
        }
    }

    public static final class AdminToolsModule extends Module {
        private static final List<String> ITEM_EDITOR_SIGNATURE_FIELDS = List.of(
            "nbt-item-id",
            "nbt-item-count",
            "nbt-item-name",
            "nbt-item-lore",
            "nbt-unbreakable",
            "nbt-glint",
            "nbt-rarity",
            "nbt-max-damage",
            "nbt-damage",
            "nbt-max-stack",
            "nbt-enchants",
            "nbt-attributes",
            "nbt-custom-data",
            "nbt-command"
        );
        private final List<String> queuedCommands = new ArrayList<>();
        private int commandDelay;
        private int commandIndex;
        private String queuedDelayOption = "fireball-delay";
        private final Random random = new Random();
        private boolean fireballStreamEnabled;
        private boolean firestormEnabled;
        private boolean fireballStreamBindDown;
        private boolean firestormBindDown;
        private int liveFireballDelay;
        private int liveFirestormDelay;
        private int liveFirestormIndex;
        private long suppressSummonMessagesUntilMs;

        private boolean forceOpRunning;
        private Thread forceOpThread;
        private int forceOpIndex;
        private volatile boolean gotWrongPwMsg;
        private static final String[] RAW_FORCEOP_LIST = {
            "password", "passwort", "password1", "passwort1", "password123", "password1234", "passwort123", "pass", "pw", "pw1", "pw123",
            "hallo", "1122", "112233", "1234", "12345", "123456", "1234567", "12345678", "123456789", "login", "register", "test", "sicher", "me",
            "minecraft", "minecraft1", "minecraft123", "mc", "admin", "server", "tester", "account",
            "creeper", "gronkh", "lol", "auth", "authme", "qwerty", "qwertz",
            "2112", "1212", "cocacola", "xavier", "dolphin", "testing", "dragon", "baseball", "football", "letmein", "monkey", "696969",
            "abc123", "mustang", "michael", "shadow", "master", "jennifer", "111111", "2000", "jordan", "superman", "harley", "hunter", "trustno1",
            "ranger", "buster", "thomas", "tigger", "robert", "soccer", "batman", "killer", "hockey", "george", "charlie", "andrew", "michelle",
            "love", "sunshine", "jessica", "6969", "pepper", "daniel", "access", "654321", "joshua", "maggie", "starwars", "silver",
            "william", "dallas", "yankees", "123123", "ashley", "666666", "hello", "amanda", "orange", "freedom", "computer",
            "thunder", "nicole", "ginger", "heather", "hammer", "summer", "corvette", "taylor", "austin", "1111", "merlin", "matthew",
            "121212", "golfer", "cheese", "princess", "martin", "chelsea", "patrick", "richard", "diamond", "yellow", "bigdog", "secret", "asdfgh",
            "sparky", "cowboy", "camaro", "anthony", "matrix", "falcon", "iloveyou", "bailey", "guitar", "jackson", "purple", "scooter", "phoenix",
            "aaaaaa", "morgan", "tigers", "porsche", "mickey", "maverick", "cookie", "nascar", "peanut", "justin", "131313", "money",
            "123456", "password", "12345678", "qwerty", "123456789", "12345", "1234", "111111", "1234567", "admin", "1234567890", "123123",
            "000000", "123321", "654321", "qwertyuiop", "qwertzuiop", "1q2w3e4r5t", "1qaz2wsx", "zxcvbnm", "asdfgh", "7777777", "666666",
            "555555", "888888", "123qwe", "abc123", "aa123456", "password123", "password1", "secret"
        };
        private final String[] defaultForceOpList = java.util.Arrays.stream(RAW_FORCEOP_LIST).distinct().toArray(String[]::new);
        private String[] forceOpPasswords = defaultForceOpList;

        AdminToolsModule() {
            super("admin-tools", "AdminTools", ModuleCategory.MISC, "Creative/admin helpers.");
            add(new ActionSetting("admin-status", "Show Permission Status", this::showPermissionStatus).group("Status").build());
            add(new ActionSetting("admin-stop-queue", "Stop Queued Commands", this::stopQueuedCommands).group("Status").build());
            add(new IntSetting("fireball-count", "Count", 12, 1, 256, 1).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-spread", "Spread", 8.0, 0.0, 90.0, 1.0).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-speed", "Speed", 1.8, 0.1, 10.0, 0.1).group("Fireball Storm").build());
            add(new IntSetting("fireball-power", "Power", 3, 0, 127, 1).group("Fireball Storm").build());
            add(new IntSetting("fireball-delay", "Delay", 1, 0, 20, 1).group("Fireball Storm").build());
            add(new DoubleSetting("fireball-distance", "Spawn Dist", 2.0, 0.5, 16.0, 0.5).group("Fireball Storm").build());
            add(new ChoiceSetting("fireball-aim", "Aim", "Cone", "Look", "Cone", "Ring").group("Fireball Storm").build());
            add(new BoolSetting("fireball-randomize", "Randomize", true).group("Fireball Storm").build());
            add(new KeybindSetting("fireball-stream-bind", "Stream Bind", -1).group("Fireball Storm").build());
            add(new KeybindSetting("firestorm-bind", "Firestorm Bind", -1).group("Fireball Storm").build());
            add(new DoubleSetting("firestorm-distance", "Firestorm Dist", 8.0, 2.0, 48.0, 1.0).group("Fireball Storm").build());
            add(new DoubleSetting("firestorm-height", "Firestorm Height", 2.0, 0.0, 32.0, 1.0).group("Fireball Storm").build());
            add(new BoolSetting("fireball-stream-active", "Stream Active", false).visibleWhen(() -> false).build());
            add(new BoolSetting("firestorm-active", "Firestorm Active", false).visibleWhen(() -> false).build());
            add(new ActionSetting("fireball-preset-max", "Preset Max Storm", this::presetMaxFireballStorm).group("Fireball Storm").build());
            add(new ActionSetting("fireball-preview", "Preview Command", this::previewFireballCommand).group("Fireball Storm").build());
            add(new ActionSetting("fireball-copy", "Copy Command", this::copyFireballCommand).group("Fireball Storm").build());
            add(new ActionSetting("fireball-storm", "Run Storm", this::startFireballStorm).group("Fireball Storm").build());
            add(new IntSetting("airstrike-count", "Count", 24, 1, 256, 1).group("Airstrike").build());
            add(new DoubleSetting("airstrike-radius", "Radius", 12.0, 0.0, 96.0, 1.0).group("Airstrike").build());
            add(new DoubleSetting("airstrike-height", "Height", 48.0, 8.0, 256.0, 4.0).group("Airstrike").build());
            add(new DoubleSetting("airstrike-speed", "Fall Speed", 2.0, 0.1, 10.0, 0.1).group("Airstrike").build());
            add(new IntSetting("airstrike-power", "Power", 4, 0, 127, 1).group("Airstrike").build());
            add(new IntSetting("airstrike-delay", "Delay", 1, 0, 20, 1).group("Airstrike").build());
            add(new ChoiceSetting("airstrike-target", "Target", "Look", "Look", "Self").group("Airstrike").build());
            add(new ActionSetting("airstrike-preset-small", "Preset Tight Strike", this::presetTightAirstrike).group("Airstrike").build());
            add(new ActionSetting("airstrike-preset-max", "Preset Max Strike", this::presetMaxAirstrike).group("Airstrike").build());
            add(new ActionSetting("airstrike-run", "Run Airstrike", this::startAirstrike).group("Airstrike").build());
            add(new IntSetting("forceop-delay", "Delay (ms)", 500, 0, 5000, 50).group("ForceOP").build());
            add(new BoolSetting("forceop-wait", "Wait for Chat", true).group("ForceOP").build());
            add(new IntSetting("forceop-min-length", "Min Length", 0, 0, 32, 1).group("ForceOP").build());
            add(new IntSetting("forceop-max-length", "Max Length", 0, 0, 32, 1).group("ForceOP").build());
            add(new StringSetting("nbt-item-id", "Item ID", "minecraft:stick").group("NBT Item Editor").build());
            add(new IntSetting("nbt-item-count", "Count", 1, 1, 99, 1).group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-name", "Display Name", "Custom Item").group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-lore", "Lore Lines", "").group("NBT Item Editor").build());
            add(new BoolSetting("nbt-unbreakable", "Unbreakable", false).group("NBT Item Editor").build());
            add(new ChoiceSetting("nbt-glint", "Glint", "Default", "Default", "On", "Off").group("NBT Item Editor").build());
            add(new ChoiceSetting("nbt-rarity", "Rarity", "Default", "Default", "Common", "Uncommon", "Rare", "Epic").group("NBT Item Editor").build());
            add(new IntSetting("nbt-max-damage", "Max Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
            add(new IntSetting("nbt-damage", "Damage", 0, 0, 100000, 1).group("NBT Item Editor").build());
            add(new IntSetting("nbt-max-stack", "Max Stack", 64, 1, 99, 1).group("NBT Item Editor").build());
            add(new StringSetting("nbt-enchants", "Enchantments", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-attributes", "Attributes", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-custom-data", "Custom Data", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-command", "Embedded Command", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-item-components", "Raw Components", "").group("NBT Item Editor").build());
            add(new StringSetting("nbt-imported-components", "Imported Components", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-imported-signature", "Imported Signature", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-item-stack", "Full Item SNBT", "").visibleWhen(() -> false).build());
            add(new StringSetting("nbt-item-stack-signature", "Full Item Signature", "").visibleWhen(() -> false).build());
            add(new ActionSetting("fill-held-item", "Fill Held", this::fillHeldItemEditor).group("NBT Item Editor").build());
            add(new ActionSetting("copy-target-item", "Copy Target", this::copyTargetItem).group("NBT Item Editor").build());
            add(new ActionSetting("apply-held-item", "Apply to Held", this::applyHeldItemEditor).group("NBT Item Editor").build());
            add(new StringSetting("fireballPower", "Fireball Power Legacy", "3").visibleWhen(() -> false).build());
        }

        @Override
        public boolean opensSettingsOnClick() {
            return true;
        }

        @Override
        public boolean hasActivationToggle() {
            return false;
        }

        @Override
        public boolean showInModuleMenu() {
            return false;
        }

        @Override
        public boolean ticksWhenDisabled() {
            return true;
        }

        @Override
        public boolean hasDisabledTickWork() {
            boolean hasFireballBind = integer("fireball-stream-bind") != -1 || integer("firestorm-bind") != -1;
            return hasFireballBind || fireballStreamEnabled || firestormEnabled || !queuedCommands.isEmpty();
        }

        @Override
        protected String displayValueOverride(dihclient.api.module.Setting<?, ?> option) {
            if (option != null && "nbt-item-components".equals(option.id())) return buildEditorComponents();
            return null;
        }

        @Override
        public void tick() {
            boolean hasFireballBind = integer("fireball-stream-bind") != -1 || integer("firestorm-bind") != -1;
            boolean fireballActive = fireballStreamEnabled || firestormEnabled;
            if (!hasFireballBind && !fireballActive && queuedCommands.isEmpty()) return;
            if (hasFireballBind || fireballActive) tickFireballBinds();
            if (fireballActive) tickLiveFireballs();
            if (queuedCommands.isEmpty()) return;
            if (commandDelay-- > 0) return;
            if (!sendAdminCommand(queuedCommands.remove(0), true)) {
                queuedCommands.clear();
                commandDelay = 0;
                commandIndex = 0;
                return;
            }
            commandDelay = integer(queuedDelayOption);
            commandIndex++;
        }

        @Override
        public void onGameLeft() {
            fireballStreamEnabled = false;
            firestormEnabled = false;
            setValue("fireball-stream-active", "false");
            setValue("firestorm-active", "false");
            fireballStreamBindDown = false;
            firestormBindDown = false;
            queuedCommands.clear();
            commandDelay = 0;
            commandIndex = 0;
        }

        @Override
        public boolean onPacketReceive(Packet<?> packet) {
            if (!(packet instanceof ClientboundSystemChatPacket chatPacket)) return false;
            String text = chatPacket.content().getString();
            if (text == null) return false;
            String lower = text.toLowerCase(Locale.ROOT);

            if (forceOpRunning) {
                if (lower.contains("wrong") || lower.contains("incorrect") ||
                    lower.contains("falsch") || lower.contains("mauvais") ||
                    lower.contains("mal") || lower.contains("sbagliato")) {
                    gotWrongPwMsg = true;
                }
            }

            if (System.currentTimeMillis() > suppressSummonMessagesUntilMs && !fireballStreamEnabled && !firestormEnabled) return false;

            return lower.contains("fireball") || lower.contains("summoned");
        }

        private void showPermissionStatus() {
            DihClientMessaging.sendPrefixed("Admin Tools: gamemaster=" + isAdminContext() + ", creative=" + isCreativeContext() + ", queued=" + queuedCommands.size() + ".");
        }

        private void stopQueuedCommands() {
            int count = queuedCommands.size();
            queuedCommands.clear();
            commandDelay = 0;
            commandIndex = 0;
            DihClientMessaging.sendPrefixed(count == 0 ? "Admin Tools: no queued commands." : "Admin Tools: stopped " + count + " queued commands.");
        }

        private void tickFireballBinds() {
            if (!DihInputGate.canRunDihKeybinds()) {
                fireballStreamBindDown = false;
                return;
            }
            boolean streamPressed = bindPressed("fireball-stream-bind");
            if (streamPressed && !fireballStreamBindDown) toggleFireballStream();
            fireballStreamBindDown = streamPressed;

            boolean stormPressed = bindPressed("firestorm-bind");
            if (stormPressed && !firestormBindDown) toggleFirestorm();
            firestormBindDown = stormPressed;
        }

        private boolean bindPressed(String optionId) {
            int bind = integer(optionId);
            return bind != -1 && DihBindUtil.isBindPressed(MC, bind);
        }

        private void tickLiveFireballs() {
            if (MC.player == null || MC.getConnection() == null) return;
            if (fireballStreamEnabled) {
                if (liveFireballDelay-- <= 0) {
                    sendLiveFireball(0, 1, false);
                    liveFireballDelay = Math.max(0, integer("fireball-delay"));
                }
            }
            if (firestormEnabled) {
                if (liveFirestormDelay-- <= 0) {
                    int count = Math.max(1, integer("fireball-count"));
                    int burst = Math.max(1, Math.min(8, count));
                    for (int i = 0; i < burst; i++) {
                        sendLiveFireball(liveFirestormIndex++, count, true);
                    }
                    liveFirestormDelay = Math.max(0, integer("fireball-delay"));
                }
            }
        }

        private void sendLiveFireball(int index, int total, boolean firestorm) {
            String command = firestorm
                ? fireballCommand(index, total, firestormDistance(), firestormHeight())
                : fireballCommand(index, total);
            if (command.isBlank()) return;
            sendAdminCommand(command, true, false);
        }

        private void toggleFireballStream() {
            fireballStreamEnabled = !fireballStreamEnabled;
            setValue("fireball-stream-active", Boolean.toString(fireballStreamEnabled));
            liveFireballDelay = 0;
            DihClientMessaging.sendPrefixed("Admin Tools: fireball stream " + (fireballStreamEnabled ? "enabled." : "disabled."));
        }

        private void toggleFirestorm() {
            firestormEnabled = !firestormEnabled;
            setValue("firestorm-active", Boolean.toString(firestormEnabled));
            liveFirestormDelay = 0;
            liveFirestormIndex = 0;
            DihClientMessaging.sendPrefixed("Admin Tools: firestorm " + (firestormEnabled ? "enabled." : "disabled."));
        }

        public boolean isFireballStreamEnabled() {
            return fireballStreamEnabled;
        }

        public boolean isFirestormEnabled() {
            return firestormEnabled;
        }

        private void startFireballStorm() {
            queuedCommands.clear();
            commandIndex = 0;
            queuedDelayOption = "fireball-delay";
            int count = integer("fireball-count");
            for (int i = 0; i < count; i++) {
                String command = fireballCommand(i, count, firestormDistance(), firestormHeight());
                if (!command.isBlank()) queuedCommands.add(command);
            }
            if (queuedCommands.isEmpty()) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not queue fireballs, join a world first.");
                return;
            }
            commandDelay = 0;
            DihClientMessaging.sendPrefixed("Admin Tools: queued " + count + " fireballs.");
        }

        private void previewFireballCommand() {
            String command = fireballCommand(0, Math.max(1, integer("fireball-count")));
            DihClientMessaging.sendPrefixed(command.isBlank() ? "Admin Tools: join a world first." : "Preview: /" + command);
        }

        private void copyFireballCommand() {
            copyCommandPreview(fireballCommand(0, Math.max(1, integer("fireball-count"))), "fireball command");
        }

        private void startAirstrike() {
            queuedCommands.clear();
            commandIndex = 0;
            queuedDelayOption = "airstrike-delay";
            int count = integer("airstrike-count");
            for (int i = 0; i < count; i++) {
                String command = airstrikeCommand(i, count);
                if (!command.isBlank()) queuedCommands.add(command);
            }
            if (queuedCommands.isEmpty()) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not queue airstrike, join a world first.");
                return;
            }
            commandDelay = 0;
            DihClientMessaging.sendPrefixed("Admin Tools: queued " + count + " airstrike fireballs.");
        }

        public void loadForceOpPasswords() {
            DihFileDialogs.openFile("Load Passwords", null, java.util.List.of("txt"), "Text files", selected -> {
                try {
                    {
                        java.util.List<String> loadedPWs = java.nio.file.Files.readAllLines(java.nio.file.Paths.get(selected), java.nio.charset.StandardCharsets.UTF_8);
                        java.util.Set<String> parsed = new java.util.LinkedHashSet<>();
                        for (String line : loadedPWs) {
                            String pw = line.trim();
                            if (pw.isEmpty()) continue;
                            if (pw.contains(":")) {
                                String[] parts = pw.split(":");
                                pw = parts[parts.length - 1].trim();
                            }
                            if (!pw.isEmpty()) parsed.add(pw);
                        }
                        forceOpPasswords = parsed.toArray(new String[0]);
                        forceOpIndex = 0;
                        DihClientMessaging.sendPrefixed("Admin Tools: Loaded " + forceOpPasswords.length + " passwords.");
                    }
                } catch (Exception e) {
                    DihClientMessaging.sendPrefixed("Admin Tools: Failed to load passwords.");
                    forceOpPasswords = defaultForceOpList;
                    forceOpIndex = 0;
                }
            });
        }

        public void unloadForceOpPasswords() {
            forceOpPasswords = defaultForceOpList;
            forceOpIndex = 0;
            DihClientMessaging.sendPrefixed("Admin Tools: Unloaded custom passwords. Reverted to default.");
        }

        public String[] getForceOpPasswords() {
            return forceOpPasswords;
        }

        public void startForceOp() {
            if (forceOpRunning) return;
            if (MC.player == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            forceOpRunning = true;
            forceOpIndex = 0;
            int delay = integer("forceop-delay");
            boolean waitForMsg = bool("forceop-wait");

            forceOpThread = new Thread(() -> {
                MC.execute(() -> MC.getConnection().sendCommand("login " + MC.getUser().getName()));

                for (int i = 0; i < forceOpPasswords.length; i++) {
                    if (!forceOpRunning) return;

                    if (waitForMsg) gotWrongPwMsg = false;

                    long startWait = System.currentTimeMillis();
                    while (waitForMsg && !gotWrongPwMsg && MC.player != null) {
                        if (!forceOpRunning) return;
                        if (System.currentTimeMillis() - startWait > 2000L) {
                            DihClientMessaging.sendPrefixed("Admin Tools: Timed out waiting for chat message. Stopping ForceOP.");
                            forceOpRunning = false;
                            return;
                        }
                        try { Thread.sleep(50); } catch (InterruptedException ignored) {  }
                    }

                    try { Thread.sleep(delay); } catch (InterruptedException ignored) {  }
                    if (!forceOpRunning) return;

                    boolean sent = false;
                    while (!sent && forceOpRunning && MC.player != null) {
                        try {
                            String pw = forceOpPasswords[i];
                            int min = integer("forceop-min-length");
                            int max = integer("forceop-max-length");

                            if (min > 0 && pw.length() < min) {
                                sent = true;
                                continue;
                            }
                            if (max > 0 && pw.length() > max) {
                                sent = true;
                                continue;
                            }

                            MC.execute(() -> MC.getConnection().sendCommand("login " + pw));
                            sent = true;
                        } catch (Exception e) {
                            try { Thread.sleep(50); } catch (InterruptedException ignored) {  }
                        }
                    }

                    forceOpIndex = i + 1;
                }

                if (forceOpRunning) {
                    MC.execute(() -> DihClientMessaging.sendPrefixed("§c[§4§lFAILURE§c]§f All " + forceOpIndex + " passwords were wrong."));
                    forceOpRunning = false;
                }
            }, "ForceOP");
            forceOpThread.start();
            DihClientMessaging.sendPrefixed("Admin Tools: ForceOP started.");
        }

        public void stopForceOp() {
            if (!forceOpRunning) return;
            forceOpRunning = false;
            if (forceOpThread != null) forceOpThread.interrupt();
            DihClientMessaging.sendPrefixed("Admin Tools: ForceOP stopped.");
        }

        public int getForceOpTotal() {
            return forceOpPasswords.length;
        }

        public int getForceOpIndex() {
            return forceOpIndex;
        }

        public boolean isForceOpRunning() {
            return forceOpRunning;
        }

        private void presetMaxFireballStorm() {
            setValue("fireball-count", "256");
            setValue("fireball-spread", "16.0");
            setValue("fireball-speed", "3.5");
            setValue("fireball-power", "8");
            setValue("fireball-delay", "0");
            setValue("fireball-distance", "3.0");
            setValue("fireball-aim", "Cone");
            setValue("fireball-randomize", "true");
            DihClientMessaging.sendPrefixed("Admin Tools: loaded max fireball storm preset.");
        }

        private void presetTightAirstrike() {
            setValue("airstrike-count", "32");
            setValue("airstrike-radius", "8.0");
            setValue("airstrike-height", "56.0");
            setValue("airstrike-speed", "2.4");
            setValue("airstrike-power", "5");
            setValue("airstrike-delay", "1");
            setValue("airstrike-target", "Look");
            DihClientMessaging.sendPrefixed("Admin Tools: loaded tight airstrike preset.");
        }

        private void presetMaxAirstrike() {
            setValue("airstrike-count", "256");
            setValue("airstrike-radius", "48.0");
            setValue("airstrike-height", "128.0");
            setValue("airstrike-speed", "4.5");
            setValue("airstrike-power", "10");
            setValue("airstrike-delay", "0");
            setValue("airstrike-target", "Look");
            DihClientMessaging.sendPrefixed("Admin Tools: loaded max airstrike preset.");
        }

        private String fireballCommand(int index, int total) {
            return fireballCommand(index, total, decimal("fireball-distance"), 0.0);
        }

        private double firestormDistance() {
            return Math.max(2.0, decimal("firestorm-distance"));
        }

        private double firestormHeight() {
            return Math.max(0.0, decimal("firestorm-height"));
        }

        private String fireballCommand(int index, int total, double spawnDistance, double upOffset) {
            if (MC.player == null) return "";
            Vec3 look = MC.player.getLookAngle();
            Vec3 dir = fireballDirection(look, index, total).normalize();
            double speed = decimal("fireball-speed");
            Vec3 motion = dir.scale(speed);

            Vec3 pos = MC.player.getEyePosition().add(look.scale(spawnDistance)).add(0.0, upOffset, 0.0);
            return String.format(Locale.ROOT,
                "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[%.4f,%.4f,%.4f],Motion:[%.4f,%.4f,%.4f]}",
                pos.x, pos.y, pos.z, integer("fireball-power"), dir.x, dir.y, dir.z, motion.x, motion.y, motion.z);
        }

        private Vec3 fireballDirection(Vec3 look, int index, int total) {
            double spread = Math.toRadians(decimal("fireball-spread"));
            if (spread <= 0.0 || "Look".equals(choice("fireball-aim"))) return look;
            double yaw;
            double pitch;
            if ("Ring".equals(choice("fireball-aim"))) {
                double angle = (Math.PI * 2.0 * index) / Math.max(1, total);
                yaw = Math.cos(angle) * spread;
                pitch = Math.sin(angle) * spread;
            } else {
                yaw = (random.nextDouble() * 2.0 - 1.0) * spread;
                pitch = (random.nextDouble() * 2.0 - 1.0) * spread;
                if (!bool("fireball-randomize")) {
                    double step = total <= 1 ? 0.0 : (index / (double) (total - 1)) * 2.0 - 1.0;
                    yaw = step * spread;
                    pitch = 0.0;
                }
            }
            Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
            if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
            Vec3 up = right.cross(look).normalize();
            return look.add(right.scale(Math.sin(yaw))).add(up.scale(Math.sin(pitch)));
        }

        private String airstrikeCommand(int index, int total) {
            if (MC.player == null) return "";
            Vec3 center = airstrikeCenter();
            double radius = decimal("airstrike-radius");
            double angle = total <= 1 ? 0.0 : Math.PI * 2.0 * (index / (double) total);
            double distance = radius <= 0.0 ? 0.0 : Math.sqrt(random.nextDouble()) * radius;
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y + decimal("airstrike-height");
            double speed = -Math.abs(decimal("airstrike-speed"));
            return String.format(Locale.ROOT,
                "summon minecraft:fireball %.2f %.2f %.2f {ExplosionPower:%db,power:[0.0000,%.4f,0.0000],Motion:[0.0000,%.4f,0.0000]}",
                x, y, z, integer("airstrike-power"), speed, speed);
        }

        private Vec3 airstrikeCenter() {
            if (MC.player == null) return Vec3.ZERO;
            if ("Self".equals(choice("airstrike-target"))) return MC.player.position();
            HitResult hit = MC.hitResult;
            if (hit != null && hit.getType() != HitResult.Type.MISS) return hit.getLocation();
            return MC.player.getEyePosition().add(MC.player.getLookAngle().scale(32.0));
        }

        private void createNamedNbtItem() {
            if (MC.player == null || MC.getConnection() == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = buildEditorStack(true);
            if (stack.isEmpty()) return;
            if (createCreativeStack(stack)) {
                DihClientMessaging.sendPrefixed("Sent edited item packet.");
            }
        }

        private void fillHeldItemEditor() {
            if (MC.player == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            ItemStack stack = MC.player.getMainHandItem();
            if (stack.isEmpty()) {
                DihClientMessaging.sendPrefixed("Admin Tools: hold an item first.");
                return;
            }
            if (fillItemEditorFromStack(stack, true)) {
                DihClientMessaging.sendPrefixed("Admin Tools: filled editor from held item.");
            }
        }

        private void copyTargetItem() {
            if (MC.player == null || MC.getConnection() == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            if (!isCreativeContext()) {
                DihClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
                return;
            }
            if (!(MC.hitResult instanceof EntityHitResult entityHit)) {
                DihClientMessaging.sendPrefixed("Admin Tools: look directly at an entity holding the item.");
                return;
            }

            Entity entity = entityHit.getEntity();
            ItemStack source = entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity
                ? itemEntity.getItem()
                : entity instanceof LivingEntity living ? living.getMainHandItem() : ItemStack.EMPTY;
            if (source.isEmpty()) {
                DihClientMessaging.sendPrefixed("Admin Tools: targeted entity has no visible held item.");
                return;
            }

            ItemStack copy = source.copy();
            if (!fillItemEditorFromStack(copy, false)) return;
            if (createCreativeStack(copy)) {
                DihClientMessaging.sendPrefixed("Copied targeted item with all client-synced data.");
            }
        }

        public boolean fillItemEditorFromStack(ItemStack stack, boolean includeStatusMessage) {
            if (stack == null || stack.isEmpty()) return false;
            selectEditorItemId(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), Math.max(1, Math.min(99, stack.getMaxStackSize())));
            setValue("nbt-item-count", Integer.toString(Math.max(1, Math.min(99, stack.getCount()))));
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            setValue("nbt-item-name", name == null ? "" : name.getString());
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null || lore.lines().isEmpty()) {
                setValue("nbt-item-lore", "");
            } else {
                List<String> lines = new ArrayList<>();
                for (Component line : lore.lines()) lines.add(line.getString());
                setValue("nbt-item-lore", String.join("|", lines));
            }
            setValue("nbt-unbreakable", Boolean.toString(stack.has(DataComponents.UNBREAKABLE)));
            Boolean glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            setValue("nbt-glint", glint == null ? "Default" : (glint ? "On" : "Off"));
            Rarity rarity = explicitComponent(stack, DataComponents.RARITY);
            setValue("nbt-rarity", rarity == null ? "Default" : switch (rarity) {
                case COMMON -> "Common";
                case UNCOMMON -> "Uncommon";
                case RARE -> "Rare";
                case EPIC -> "Epic";
            });
            setValue("nbt-max-damage", Integer.toString(Math.max(0, stack.getMaxDamage())));
            setValue("nbt-damage", Integer.toString(Math.max(0, stack.getDamageValue())));
            setValue("nbt-enchants", editorEnchantments(stack));
            setValue("nbt-attributes", editorAttributes(stack));
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag customTag = customData == null ? new CompoundTag() : customData.copyTag();
            setValue("nbt-custom-data", customTag.isEmpty() ? "" : customTag.toString());
            String embeddedCommand = customTag.getStringOr("command", "");
            TypedEntityData<?> blockEntityData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
            if (embeddedCommand.isBlank() && blockEntityData != null) {
                embeddedCommand = blockEntityData.copyTagWithoutId().getStringOr("Command", "");
            }
            setValue("nbt-command", embeddedCommand);
            String imported = DihItemCommandSerializer.componentPatch(stack);
            setValue("nbt-imported-components", imported);
            setValue("nbt-imported-signature", editorSignature());
            setValue("nbt-item-components", imported);
            setValue("nbt-item-stack", DihItemNbtInspector.prettySnbt(DihItemCommandSerializer.itemStackSnbt(stack)));
            setValue("nbt-item-stack-signature", editorSignature());
            return true;
        }

        public void syncEditorMaxStackForItemId() {
            selectEditorItemId(value("nbt-item-id"));
        }

        public void selectEditorItemId(String itemId) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(itemId == null ? "" : itemId.trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            selectEditorItemId(BuiltInRegistries.ITEM.getKey(item).toString(), editorItemDefaultMaxStack(item));
        }

        private void selectEditorItemId(String itemId, int newMaxStack) {
            int oldMaxStack = Math.max(1, Math.min(99, integer("nbt-max-stack")));
            int currentCount = Math.max(1, Math.min(99, integer("nbt-item-count")));
            int clampedNewMax = Math.max(1, Math.min(99, newMaxStack));
            int nextCount = currentCount;
            if (currentCount > clampedNewMax || currentCount == oldMaxStack) {
                nextCount = clampedNewMax;
            }
            setValue("nbt-item-id", itemId == null || itemId.isBlank() ? BuiltInRegistries.ITEM.getKey(Items.STICK).toString() : itemId);
            setValue("nbt-max-stack", Integer.toString(clampedNewMax));
            setValue("nbt-item-count", Integer.toString(nextCount));
        }

        private void applyHeldItemEditor() {
            if (MC.player == null || MC.getConnection() == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = buildEditorStack(true);
            if (stack.isEmpty()) return;
            if (createCreativeStack(stack)) {
                fillItemEditorFromStack(stack, false);
                DihClientMessaging.sendPrefixed("Sent edited held item packet.");
            }
        }

        private ItemStack buildEditorStack() {
            return buildEditorStack(false);
        }

        private ItemStack buildEditorStack(boolean notifyErrors) {
            String rawStack = value("nbt-item-stack").trim();
            boolean hasRawBase = !rawStack.isBlank();
            ItemStack rawBase = hasRawBase ? DihItemCommandSerializer.itemStackFromSnbt(rawStack) : ItemStack.EMPTY;
            if (hasRawBase && rawBase.isEmpty()) {
                if (notifyErrors) DihClientMessaging.sendPrefixed("Admin Tools: invalid full item SNBT.");
                return ItemStack.EMPTY;
            }

            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(value("nbt-item-id").trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            boolean itemChanged = hasRawBase && rawBase.getItem() != item;
            ItemStack stack;
            if (hasRawBase && !itemChanged) {
                stack = rawBase.copy();
            } else {
                stack = new ItemStack(item);
                if (hasRawBase) stack.applyComponents(rawBase.getComponentsPatch());
            }

            boolean applyAll = !hasRawBase;
            if (applyAll || itemChanged || structuredFieldChanged("nbt-item-count")) {
                stack.setCount(Math.max(1, Math.min(99, integer("nbt-item-count"))));
            }
            if (!applyStructuredComponents(stack, item, applyAll, notifyErrors)) return ItemStack.EMPTY;

            String validationError = DihItemCommandSerializer.validationError(stack);
            if (!validationError.isBlank()) {
                if (notifyErrors) DihClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + validationError);
                return ItemStack.EMPTY;
            }
            return stack;
        }

        private boolean applyStructuredComponents(ItemStack stack, Item item, boolean applyAll, boolean notifyErrors) {
            if (applyAll || structuredFieldChanged("nbt-item-name")) {
                String name = value("nbt-item-name").trim();
                if (name.isBlank()) stack.remove(DataComponents.CUSTOM_NAME);
                else stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            }
            if (applyAll || structuredFieldChanged("nbt-item-lore")) {
                List<Component> lore = editorLoreComponents();
                if (lore.isEmpty()) stack.remove(DataComponents.LORE);
                else stack.set(DataComponents.LORE, new ItemLore(lore));
            }
            if (applyAll || structuredFieldChanged("nbt-unbreakable")) {
                if (bool("nbt-unbreakable")) stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
                else stack.remove(DataComponents.UNBREAKABLE);
            }
            if (applyAll || structuredFieldChanged("nbt-glint")) {
                switch (choice("nbt-glint")) {
                    case "On" -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                    case "Off" -> stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
                    default -> stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
                }
            }
            if (applyAll || structuredFieldChanged("nbt-rarity")) {
                switch (choice("nbt-rarity")) {
                    case "Common" -> stack.set(DataComponents.RARITY, Rarity.COMMON);
                    case "Uncommon" -> stack.set(DataComponents.RARITY, Rarity.UNCOMMON);
                    case "Rare" -> stack.set(DataComponents.RARITY, Rarity.RARE);
                    case "Epic" -> stack.set(DataComponents.RARITY, Rarity.EPIC);
                    default -> stack.remove(DataComponents.RARITY);
                }
            }

            boolean maxDamageChanged = applyAll || structuredFieldChanged("nbt-max-damage");
            boolean damageChanged = applyAll || structuredFieldChanged("nbt-damage");
            if (maxDamageChanged) {
                int maxDamage = integer("nbt-max-damage");
                if (maxDamage > 0) stack.set(DataComponents.MAX_DAMAGE, maxDamage);
                else {
                    stack.remove(DataComponents.MAX_DAMAGE);
                    stack.remove(DataComponents.DAMAGE);
                }
            }
            if (damageChanged && stack.getMaxDamage() > 0) {
                stack.set(DataComponents.DAMAGE, Math.min(Math.max(0, integer("nbt-damage")), stack.getMaxDamage()));
            }
            if (applyAll || structuredFieldChanged("nbt-max-stack")) {
                int maxStack = Math.max(1, Math.min(99, integer("nbt-max-stack")));
                int defaultMaxStack = editorItemDefaultMaxStack(item);
                if (maxStack == defaultMaxStack) stack.remove(DataComponents.MAX_STACK_SIZE);
                else stack.set(DataComponents.MAX_STACK_SIZE, maxStack);
            }
            if (applyAll || structuredFieldChanged("nbt-enchants")) {
                if (!applyEditorEnchantments(stack, notifyErrors)) return false;
            }
            if (applyAll || structuredFieldChanged("nbt-attributes")) {
                ItemAttributeModifiers attributes = parseEditorAttributes(value("nbt-attributes"), notifyErrors);
                if (attributes == null) return false;
                if (attributes.modifiers().isEmpty()) stack.remove(DataComponents.ATTRIBUTE_MODIFIERS);
                else stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
            }

            boolean customChanged = applyAll || structuredFieldChanged("nbt-custom-data")
                || structuredFieldChanged("nbt-command");
            if (customChanged) {
                CompoundTag customData = buildCustomDataTag(notifyErrors);
                if (customData == null) return false;
                if (customData.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
                else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));

                String embeddedCommand = normalizedCommand(value("nbt-command"));
                if (isCommandBlockItem() && !embeddedCommand.isBlank()) {
                    stack.set(DataComponents.BLOCK_ENTITY_DATA,
                        TypedEntityData.of(BlockEntityTypes.COMMAND_BLOCK, commandBlockEntityTag(embeddedCommand)));
                } else if (structuredFieldChanged("nbt-command")) {
                    stack.remove(DataComponents.BLOCK_ENTITY_DATA);
                }
            }
            return true;
        }

        private int editorItemDefaultMaxStack() {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(value("nbt-item-id").trim());
            Item item = id == null ? Items.STICK : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.STICK);
            return editorItemDefaultMaxStack(item);
        }

        private int editorItemDefaultMaxStack(Item item) {
            ItemStack stack = new ItemStack(item == null ? Items.STICK : item);
            return Math.max(1, Math.min(99, stack.getMaxStackSize()));
        }

        private List<Component> editorLoreComponents() {
            String raw = value("nbt-item-lore");
            if (raw == null || raw.isBlank()) return List.of();
            List<Component> lines = new ArrayList<>();
            for (String part : raw.split("\\|")) {
                String line = part.trim();
                if (!line.isEmpty()) lines.add(Component.literal(line));
            }
            return lines;
        }

        private void syncEditorComponentsPatch() {
            setValue("nbt-item-components", buildEditorComponents());
            setValue("nbt-imported-components", "");
            setValue("nbt-imported-signature", "");
            setValue("nbt-item-stack", "");
            setValue("nbt-item-stack-signature", "");
        }

        public void setRawItemComponents(String rawComponents) {
            String raw = rawComponents == null ? "" : rawComponents.trim();
            setValue("nbt-item-components", raw);
            setValue("nbt-imported-components", raw);
            setValue("nbt-imported-signature", editorSignature());
            setValue("nbt-item-stack", "");
            setValue("nbt-item-stack-signature", "");
        }

        public boolean setRawItemStackSnbt(String rawSnbt) {
            ItemStack parsed = DihItemCommandSerializer.itemStackFromSnbt(rawSnbt);
            if (parsed.isEmpty() || !DihItemCommandSerializer.validationError(parsed).isBlank()) return false;
            return fillItemEditorFromStack(parsed, false);
        }

        public void prepareRawItemStackEditor() {
            if (hasActiveImportedStack()) return;
            ItemStack stack = buildEditorStack(false);
            if (stack.isEmpty()) return;
            fillItemEditorFromStack(stack, false);
        }

        private boolean hasActiveImportedStack() {
            String raw = value("nbt-item-stack").trim();
            return !raw.isBlank() && value("nbt-item-stack-signature").equals(editorSignature());
        }

        private String buildEditorComponents() {
            String components = buildEditorComponents(false);
            return components == null ? "" : components;
        }

        private String buildEditorComponents(boolean notifyErrors) {
            String imported = activeImportedComponents();
            if (!imported.isBlank()) return imported;
            return buildManualEditorComponents(notifyErrors);
        }

        private String buildManualEditorComponents(boolean notifyErrors) {
            List<String> parts = new ArrayList<>();
            String name = value("nbt-item-name").trim();
            if (!name.isBlank()) parts.add("custom_name=[" + jsonText(name, "white") + "]");
            String lore = loreComponentString();
            if (!lore.isBlank()) parts.add("lore=" + lore);
            if (bool("nbt-unbreakable")) parts.add("unbreakable={}");
            String glint = choice("nbt-glint");
            if ("On".equals(glint)) parts.add("enchantment_glint_override=true");
            else if ("Off".equals(glint)) parts.add("enchantment_glint_override=false");
            String rarity = choice("nbt-rarity").toLowerCase(Locale.ROOT);
            if (!"default".equals(rarity)) parts.add("rarity=" + rarity);
            int maxDamage = integer("nbt-max-damage");
            if (maxDamage > 0) {
                parts.add("max_damage=" + maxDamage);
                parts.add("damage=" + Math.min(integer("nbt-damage"), maxDamage));
            } else if (integer("nbt-max-stack") != editorItemDefaultMaxStack()) {
                parts.add("max_stack_size=" + Math.max(1, Math.min(99, integer("nbt-max-stack"))));
            }
            String enchantments = enchantmentsComponent(value("nbt-enchants"), notifyErrors);
            if (enchantments == null) return null;
            if (!enchantments.isBlank()) parts.add("enchantments=" + enchantments);
            appendRawListComponent(parts, "attribute_modifiers", value("nbt-attributes"));
            String embeddedCommand = normalizedCommand(value("nbt-command"));
            boolean commandBlockCommand = !embeddedCommand.isBlank() && isCommandBlockItem();
            if (commandBlockCommand) {
                parts.add("block_entity_data=" + commandBlockEntityComponent(embeddedCommand));
            }
            String customData = customDataComponentString(commandBlockCommand ? "" : embeddedCommand);
            if (!customData.isBlank()) parts.add("custom_data=" + customData);
            return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
        }

        private String activeImportedComponents() {
            String imported = value("nbt-imported-components").trim();
            if (imported.isBlank()) return "";
            String signature = value("nbt-imported-signature");
            if (!signature.equals(editorSignature())) return "";
            return imported.startsWith("[") ? imported : "[" + imported + "]";
        }

        private String editorSignature() {
            StringBuilder signature = new StringBuilder();
            for (String field : ITEM_EDITOR_SIGNATURE_FIELDS) {
                String fieldValue = value(field);
                signature.append(fieldValue.length()).append(':').append(fieldValue);
            }
            return signature.toString();
        }

        private boolean structuredFieldChanged(String field) {
            int index = ITEM_EDITOR_SIGNATURE_FIELDS.indexOf(field);
            if (index < 0) return true;
            String signature = value("nbt-item-stack-signature");
            if (signature.isBlank()) return true;
            if (signature.indexOf('\u001F') >= 0) {
                String[] legacyBaseline = signature.split("\u001F", -1);
                return index >= legacyBaseline.length || !value(field).equals(legacyBaseline[index]);
            }
            String baseline = signatureField(signature, index);
            return baseline == null || !value(field).equals(baseline);
        }

        private String signatureField(String signature, int wantedIndex) {
            int cursor = 0;
            for (int index = 0; index <= wantedIndex; index++) {
                int separator = signature.indexOf(':', cursor);
                if (separator < cursor) return null;
                int length;
                try {
                    length = Integer.parseInt(signature.substring(cursor, separator));
                } catch (NumberFormatException ignored) {
                    return null;
                }
                int start = separator + 1;
                int end = start + length;
                if (length < 0 || end < start || end > signature.length()) return null;
                if (index == wantedIndex) return signature.substring(start, end);
                cursor = end;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private <T> T explicitComponent(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type) {
            if (stack == null || stack.isEmpty() || type == null) return null;
            for (Map.Entry<net.minecraft.core.component.DataComponentType<?>, Optional<?>> entry
                : DihItems.patchEntries(stack.getComponentsPatch()).entrySet()) {
                if (entry.getKey() == type) return (T) entry.getValue().orElse(null);
            }
            return null;
        }

        private String editorEnchantments(ItemStack stack) {
            List<String> entries = new ArrayList<>();
            appendEditorEnchantments(entries, stack.get(DataComponents.ENCHANTMENTS));
            appendEditorEnchantments(entries, stack.get(DataComponents.STORED_ENCHANTMENTS));
            return String.join(",", entries);
        }

        private boolean applyEditorEnchantments(ItemStack stack, boolean notifyErrors) {
            List<String> normalized = normalizedEnchantmentEntries(value("nbt-enchants"), notifyErrors);
            if (normalized == null) return false;
            Map<String, Integer> desired = new LinkedHashMap<>();
            for (String entry : normalized) {
                int split = entry.lastIndexOf(':');
                if (split <= 0 || split >= entry.length() - 1) return false;
                String id = stripQuotes(entry.substring(0, split));
                if (!id.contains(":")) id = "minecraft:" + id;
                try {
                    desired.put(id, Integer.parseInt(entry.substring(split + 1)));
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            ItemEnchantments regular = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
            ItemEnchantments.Mutable regularMutable = new ItemEnchantments.Mutable(regular);
            ItemEnchantments.Mutable storedMutable = new ItemEnchantments.Mutable(stored);
            Set<String> assigned = new LinkedHashSet<>();

            for (Holder<Enchantment> holder : List.copyOf(regular.keySet())) {
                String id = enchantmentId(holder);
                Integer level = desired.get(id);
                regularMutable.set(holder, level == null ? 0 : level);
                if (level != null) assigned.add(id);
            }
            for (Holder<Enchantment> holder : List.copyOf(stored.keySet())) {
                String id = enchantmentId(holder);
                Integer level = desired.get(id);
                storedMutable.set(holder, level == null ? 0 : level);
                if (level != null) assigned.add(id);
            }

            boolean preferStored = stack.is(Items.ENCHANTED_BOOK) || (!stored.isEmpty() && regular.isEmpty());
            for (Map.Entry<String, Integer> entry : desired.entrySet()) {
                if (assigned.contains(entry.getKey())) continue;
                Holder<Enchantment> holder = enchantmentHolder(entry.getKey());
                if (holder == null) {
                    if (notifyErrors) {
                        DihClientMessaging.sendPrefixed("Admin Tools: unknown enchantment '" + entry.getKey() + "'.");
                    }
                    return false;
                }
                if (preferStored) storedMutable.set(holder, entry.getValue());
                else regularMutable.set(holder, entry.getValue());
            }

            ItemEnchantments updatedRegular = regularMutable.toImmutable();
            ItemEnchantments updatedStored = storedMutable.toImmutable();
            if (updatedRegular.isEmpty()) stack.remove(DataComponents.ENCHANTMENTS);
            else stack.set(DataComponents.ENCHANTMENTS, updatedRegular);
            if (updatedStored.isEmpty()) stack.remove(DataComponents.STORED_ENCHANTMENTS);
            else stack.set(DataComponents.STORED_ENCHANTMENTS, updatedStored);
            return true;
        }

        private String enchantmentId(Holder<Enchantment> holder) {
            return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("");
        }

        private Holder<Enchantment> enchantmentHolder(String rawId) {
            try {
                if (MC.player == null) return null;
                net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(rawId);
                if (id == null) return null;
                net.minecraft.resources.ResourceKey<Enchantment> key =
                    net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id);
                return MC.player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key).orElse(null);
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private String editorAttributes(ItemStack stack) {
            ItemAttributeModifiers attributes = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
            if (attributes == null || attributes.modifiers().isEmpty() || MC.player == null) return "";
            try {
                Tag encoded = ItemAttributeModifiers.CODEC.encodeStart(
                    MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), attributes
                ).getOrThrow();
                String raw = encoded.toString();
                return raw.length() >= 2 && raw.startsWith("[") && raw.endsWith("]")
                    ? raw.substring(1, raw.length() - 1)
                    : raw;
            } catch (RuntimeException ignored) {
                return "";
            }
        }

        private ItemAttributeModifiers parseEditorAttributes(String raw, boolean notifyErrors) {
            if (raw == null || raw.isBlank()) return ItemAttributeModifiers.EMPTY;
            if (MC.player == null) return null;
            try {
                CompoundTag wrapper = TagParser.parseCompoundFully("{value:[" + raw + "]}");
                Tag encoded = wrapper.get("value");
                if (encoded == null) return ItemAttributeModifiers.EMPTY;
                return ItemAttributeModifiers.CODEC.parse(
                    MC.player.registryAccess().createSerializationContext(NbtOps.INSTANCE), encoded
                ).getOrThrow();
            } catch (Throwable ignored) {
                if (notifyErrors) DihClientMessaging.sendPrefixed("Admin Tools: invalid attribute modifiers.");
                return null;
            }
        }

        private void appendEditorEnchantments(List<String> entries, ItemEnchantments enchantments) {
            if (enchantments == null || enchantments.isEmpty()) return;
            for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> entry : enchantments.entrySet()) {
                String id = entry.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse("").trim();
                if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
                if (!id.isBlank()) entries.add(id + ":" + entry.getIntValue());
            }
        }

        private String customDataComponentString(String embeddedCommand) {
            String raw = value("nbt-custom-data").trim();
            String merged = mergeCustomDataPayload(raw, embeddedCommand);
            return merged.isBlank() ? "" : wrapCompound(merged);
        }

        private CompoundTag buildCustomDataTag(boolean notifyErrors) {
            String embeddedCommand = normalizedCommand(value("nbt-command"));
            String payload = customDataComponentString(isCommandBlockItem() ? "" : embeddedCommand);
            if (payload.isBlank()) return new CompoundTag();
            try {
                return TagParser.parseCompoundFully(payload);
            } catch (Exception e) {
                if (notifyErrors) {
                    DihClientMessaging.sendPrefixed("Admin Tools: invalid custom data / embedded command data.");
                }
                return null;
            }
        }

        private String mergeCustomDataPayload(String rawCustomData, String embeddedCommand) {
            String raw = rawCustomData == null ? "" : rawCustomData.trim();
            String command = normalizedCommand(embeddedCommand);
            if (command.isBlank()) return raw;

            List<String> entries = new ArrayList<>();
            String inner = unwrapCompound(raw);
            if (!inner.isBlank()) {
                for (String entry : splitTopLevelEntries(inner)) {
                    String key = topLevelSnbtKey(entry);
                    if ("dih_admin_tool".equals(key) || "command".equals(key)) continue;
                    entries.add(entry.trim());
                }
            }

            entries.add("dih_admin_tool:1b");
            entries.add("command:\"" + snbtString(command) + "\"");
            return String.join(",", entries);
        }

        private String commandBlockEntityComponent(String command) {
            String normalized = normalizedCommand(command);
            if (normalized.isBlank()) normalized = "say Dih";
            return "{id:\"minecraft:command_block\",Command:\"" + snbtString(normalized) + "\",auto:0b,TrackOutput:1b}";
        }

        private CompoundTag commandBlockEntityTag(String command) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:command_block");
            tag.putString("Command", normalizedCommand(command).isBlank() ? "say Dih" : normalizedCommand(command));
            tag.putByte("auto", (byte) 0);
            tag.putByte("TrackOutput", (byte) 1);
            return tag;
        }

        private boolean isCommandBlockItem() {
            String item = value("nbt-item-id").trim();
            return "minecraft:command_block".equals(item) || "command_block".equals(item)
                || "minecraft:chain_command_block".equals(item) || "chain_command_block".equals(item)
                || "minecraft:repeating_command_block".equals(item) || "repeating_command_block".equals(item);
        }

        private String loreComponentString() {
            List<String> lines = new ArrayList<>();
            String raw = value("nbt-item-lore");
            if (raw == null || raw.isBlank()) return "";
            for (String part : raw.split("\\|")) {
                String line = part.trim();
                if (!line.isEmpty()) lines.add(jsonText(line, "dark_purple"));
            }
            if (lines.isEmpty()) return "";
            List<String> wrapped = new ArrayList<>();
            for (String line : lines) wrapped.add("[" + line + "]");
            return "[" + String.join(",", wrapped) + "]";
        }

        private void appendRawListComponent(List<String> parts, String name, String raw) {
            if (raw == null || raw.isBlank()) return;
            String trimmed = raw.trim();
            parts.add(name + "=" + (trimmed.startsWith("[") ? trimmed : "[" + trimmed + "]"));
        }

        private String enchantmentsComponent(String raw, boolean notifyErrors) {
            List<String> entries = normalizedEnchantmentEntries(raw, notifyErrors);
            if (entries == null) return null;
            return entries.isEmpty() ? "" : "{" + String.join(",", entries) + "}";
        }

        private List<String> normalizedEnchantmentEntries(String raw, boolean notifyErrors) {
            List<String> entries = new ArrayList<>();
            if (raw == null || raw.isBlank()) return entries;
            for (String entry : splitTopLevelEntries(raw)) {
                String normalized = normalizeEnchantmentEntry(entry);
                if (normalized == null) {
                    if (notifyErrors) DihClientMessaging.sendPrefixed("Admin Tools: invalid enchantment entry '" + entry + "'. Use id:level, for example binding_curse:1.");
                    return null;
                }
                entries.add(normalized);
            }
            return entries;
        }

        private String normalizeEnchantmentEntry(String entry) {
            if (entry == null) return null;
            String text = entry.trim();
            if (text.isBlank()) return null;
            if (text.startsWith("[") && text.endsWith("]") && text.length() > 2) text = text.substring(1, text.length() - 1).trim();
            if (text.startsWith("{") && text.endsWith("}") && text.contains("id:") && text.contains("level:")) {
                String id = extractComponentValue(text, "id");
                String level = extractComponentValue(text, "level");
                return normalizeEnchantmentIdAndLevel(id, level);
            }
            int oldLevelIndex = text.indexOf(":{level:");
            if (oldLevelIndex > 0) {
                String id = text.substring(0, oldLevelIndex);
                String levelPart = text.substring(oldLevelIndex + ":{level:".length());
                int levelEnd = levelPart.indexOf('}');
                String level = levelEnd >= 0 ? levelPart.substring(0, levelEnd) : levelPart;
                return normalizeEnchantmentIdAndLevel(id, level);
            }
            int levelIndex = text.lastIndexOf(':');
            if (levelIndex <= 0 || levelIndex >= text.length() - 1) return null;
            String id = text.substring(0, levelIndex);
            while (id.endsWith(":")) id = id.substring(0, id.length() - 1);
            String level = text.substring(levelIndex + 1);
            return normalizeEnchantmentIdAndLevel(id, level);
        }

        private String normalizeEnchantmentIdAndLevel(String rawId, String rawLevel) {
            String id = normalizeEnchantmentId(rawId);
            if (id.isBlank()) return null;
            String levelText = stripQuotes(rawLevel).trim();
            try {
                int level = Integer.parseInt(levelText);
                if (level < 1 || level > 255) return null;
                String commandId = id.contains(":") ? "\"" + snbtString(id) + "\"" : id;
                return commandId + ":" + level;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        private String normalizeEnchantmentId(String rawId) {
            String id = stripQuotes(rawId).trim();
            if (id.isBlank()) return "";
            if (id.startsWith("minecraft:")) id = id.substring("minecraft:".length());
            net.minecraft.resources.Identifier parsed = id.contains(":")
                ? net.minecraft.resources.Identifier.tryParse(id)
                : net.minecraft.resources.Identifier.tryParse("minecraft:" + id);
            return parsed == null ? "" : id;
        }

        private String extractComponentValue(String entry, String key) {
            String needle = key + ":";
            int start = entry.indexOf(needle);
            if (start < 0) return "";
            start += needle.length();
            int end = start;
            boolean quoted = false;
            char quote = 0;
            boolean escaped = false;
            while (end < entry.length()) {
                char c = entry.charAt(end);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (quoted) {
                    if (c == quote) quoted = false;
                } else if (c == '\'' || c == '"') {
                    quoted = true;
                    quote = c;
                } else if (c == ',' || c == '}' || c == ']') {
                    break;
                }
                end++;
            }
            return entry.substring(start, end).trim();
        }

        private List<String> splitTopLevelEntries(String raw) {
            List<String> out = new ArrayList<>();
            if (raw == null || raw.isBlank()) return out;
            String text = raw.trim();
            if ((text.startsWith("[") && text.endsWith("]")) || (text.startsWith("{") && text.endsWith("}"))) {
                text = text.substring(1, text.length() - 1);
            }
            StringBuilder current = new StringBuilder();
            int depth = 0;
            boolean quoted = false;
            char quote = 0;
            boolean escaped = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (escaped) {
                    current.append(c);
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    current.append(c);
                    escaped = true;
                    continue;
                }
                if (quoted) {
                    current.append(c);
                    if (c == quote) quoted = false;
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quoted = true;
                    quote = c;
                    current.append(c);
                    continue;
                }
                if (c == '{' || c == '[' || c == '(') depth++;
                else if (c == '}' || c == ']' || c == ')') depth = Math.max(0, depth - 1);
                if (c == ',' && depth == 0) {
                    String entry = current.toString().trim();
                    if (!entry.isBlank()) out.add(entry);
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
            String entry = current.toString().trim();
            if (!entry.isBlank()) out.add(entry);
            return out;
        }

        private String stripQuotes(String text) {
            if (text == null) return "";
            String value = text.trim();
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }

        private String wrapCompound(String raw) {
            String trimmed = raw.trim();
            return trimmed.startsWith("{") ? trimmed : "{" + trimmed + "}";
        }

        private String unwrapCompound(String raw) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.length() >= 2 && trimmed.startsWith("{") && trimmed.endsWith("}")) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
            return trimmed;
        }

        private String topLevelSnbtKey(String entry) {
            if (entry == null) return "";
            String text = entry.trim();
            int separator = text.indexOf(':');
            if (separator <= 0) return "";
            return stripQuotes(text.substring(0, separator)).trim();
        }

        private String normalizedCommand(String command) {
            if (command == null) return "";
            command = command.trim();
            return command.startsWith("/") ? command.substring(1) : command;
        }

        private void createCommandBlockPreset() {
            setValue("nbt-item-id", "minecraft:command_block");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "Admin Command Block");
            setValue("nbt-command", commandTemplate().isBlank() ? "say Dih" : commandTemplate());
            syncEditorComponentsPatch();
            if (MC.player != null && MC.getConnection() != null) {
                ItemStack stack = buildEditorStack(true);
                if (!stack.isEmpty() && createCreativeStack(stack)) {
                    DihClientMessaging.sendPrefixed("Loaded preset and sent command block item packet.");
                }
            } else {
                DihClientMessaging.sendPrefixed("Loaded command block preset. Join a world to send the item packet.");
            }
        }

        private String adminTarget() {
            return MC.player == null ? "" : MC.player.getGameProfile().name();
        }

        private void createForceOpStickPreset() {
            String target = adminTarget().isBlank() ? "{target}" : adminTarget();
            setValue("nbt-item-id", "minecraft:stick");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "ForceOP Tool");
            setValue("nbt-command", "op " + target);
            setValue("nbt-custom-data", "dih_admin_tool:1b,command:\"" + snbtString("op " + target) + "\"");
            syncEditorComponentsPatch();
            if (MC.player != null && MC.getConnection() != null) {
                ItemStack stack = buildEditorStack(true);
                if (!stack.isEmpty() && createCreativeStack(stack)) {
                    DihClientMessaging.sendPrefixed("Loaded preset and sent ForceOP tool item packet.");
                }
            } else {
                DihClientMessaging.sendPrefixed("Loaded ForceOP stick preset. Join a world to send the item packet.");
            }
        }

        private void presetForceOpCommandBlock() {
            String target = adminTarget().isBlank() ? "{target}" : adminTarget();
            setValue("nbt-item-id", "minecraft:command_block");
            setValue("nbt-item-count", "1");
            setValue("nbt-item-name", "ForceOP Command Block");
            setValue("nbt-command", "op " + target);
            syncEditorComponentsPatch();
            DihClientMessaging.sendPrefixed("Loaded ForceOP command block preset. Use Apply Item in creative mode.");
        }

        private ItemStack namedStack(Item item, int count, String name) {
            ItemStack stack = new ItemStack(item == null ? Items.STICK : item, Math.max(1, Math.min(99, count)));
            if (name != null && !name.isBlank()) stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
            return stack;
        }

        private boolean createCreativeStack(ItemStack stack) {
            if (MC.player == null || MC.getConnection() == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return false;
            }
            if (!isCreativeContext()) {
                DihClientMessaging.sendPrefixed("Admin Tools: creative mode is required.");
                return false;
            }
            if (stack == null || stack.isEmpty()) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet, item stack is empty.");
                return false;
            }
            String validationError = DihItemCommandSerializer.validationError(stack);
            if (!validationError.isBlank()) {
                DihClientMessaging.sendPrefixed("Admin Tools: invalid item data: " + validationError);
                return false;
            }
            int selected = MC.player.getInventory().getSelectedSlot();
            int slot = 36 + selected;
            MC.player.getInventory().setItem(selected, stack.copy());
            try {
                MC.getConnection().send(new ServerboundSetCreativeModeSlotPacket(slot, stack));
                return true;
            } catch (RuntimeException e) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet: " + e.getClass().getSimpleName());
                return false;
            }
        }

        private void createFireChargeStack() {
            if (MC.player == null || MC.getConnection() == null) {
                DihClientMessaging.sendPrefixed("Admin Tools: could not send item packet, join a world first.");
                return;
            }
            ItemStack stack = new ItemStack(Items.FIRE_CHARGE, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Fireball Storm"));
            if (createCreativeStack(stack)) {
                DihClientMessaging.sendPrefixed("Sent Fireball Storm stack item packet.");
            }
        }

        public void giveEditorItem() {
            String id = value("nbt-item-id").trim();
            if (id.startsWith("/")) id = id.substring(1);
            if (id.isBlank()) {
                DihClientMessaging.sendPrefixed("Admin Tools: set an item id first.");
                return;
            }
            String components = buildEditorComponents(true);
            if (components == null) return;
            int count = Math.max(1, Math.min(99, integer("nbt-item-count")));
            sendAdminCommand("give @p " + id + components + " " + count, false);
        }

        private boolean sendAdminCommand(String command, boolean queued) {
            return sendAdminCommand(command, queued, true);
        }

        private boolean sendAdminCommand(String command, boolean queued, boolean notifyFailures) {
            if (MC == null || MC.getConnection() == null) {
                if (notifyFailures) DihClientMessaging.sendPrefixed("Admin Tools: could not send command, join a world first.");
                return false;
            }
            if (command == null || command.isBlank()) {
                if (notifyFailures) DihClientMessaging.sendPrefixed("Admin Tools: could not send command, command is empty.");
                return false;
            }
            String normalized = command.startsWith("/") ? command.substring(1) : command;
            try {
                MC.getConnection().sendCommand(normalized);

                String lowerCmd = normalized.toLowerCase(Locale.ROOT);
                if (lowerCmd.startsWith("summon") && lowerCmd.contains("fireball")) {
                    suppressSummonMessagesUntilMs = System.currentTimeMillis() + 5000L;
                }
                return true;
            } catch (RuntimeException e) {
                if (notifyFailures) DihClientMessaging.sendPrefixed("Admin Tools: could not send " + (queued ? "queued " : "") + "command: " + e.getClass().getSimpleName());
                return false;
            }
        }

        private void copyCommandPreview(String command, String label) {
            if (command == null || command.isBlank()) {
                DihClientMessaging.sendPrefixed("Admin Tools: join a world first.");
                return;
            }
            copyToClipboard("/" + command, "Copied " + label + ".");
        }

        private void copyToClipboard(String text, String message) {
            if (MC == null || MC.keyboardHandler == null || text == null || text.isBlank()) {
                DihNotifications.error("Nothing to copy.");
                return;
            }
            MC.keyboardHandler.setClipboard(text);
            DihNotifications.copied(message);
        }

        private String commandTemplate() {
            String command = value("nbt-command").trim();
            if (command.isBlank()) command = "op {target}";
            String target = adminTarget();
            if (!target.isBlank()) command = command.replace("{target}", target);
            return command.startsWith("/") ? command.substring(1) : command;
        }

        private String namedToolComponents(String name, String lore, String command) {
            return "[custom_name=[" + jsonText(name, "red") + "],lore=[[" + jsonText(lore, "gray") + "]],custom_data={dih_admin_tool:1b,command:\"" + snbtString(command) + "\"}]";
        }

        private String commandBlockComponents(String command) {
            String normalized = command == null || command.isBlank() ? "say Dih" : command;
            normalized = normalized.startsWith("/") ? normalized.substring(1) : normalized;
            return "[custom_name=[" + jsonText(value("nbt-item-name").isBlank() ? "Admin Command Block" : value("nbt-item-name"), "red") + "],block_entity_data={id:\"minecraft:command_block\",Command:\"" + snbtString(normalized) + "\",auto:0b,TrackOutput:1b}]";
        }

        private String jsonText(String text, String color) {
            return "{\"text\":\"" + jsonString(text) + "\",\"color\":\"" + jsonString(color) + "\",\"italic\":false}";
        }

        private String jsonString(String text) {
            if (text == null) return "";
            return text.replace("\\", "\\\\").replace("\"", "\\\"");
        }

        private String snbtString(String text) {
            return jsonString(text);
        }
    }
}
