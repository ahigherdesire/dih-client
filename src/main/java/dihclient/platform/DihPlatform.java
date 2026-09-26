package dihclient.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
//? if neoforge {
/*import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
*///?}

/**
 * The mod-loader seam: loader queries and client event hooks, implemented with Fabric API on Fabric and with
 * NeoForge's event buses on NeoForge. Everything else calls this instead of a loader API.
 */
public final class DihPlatform {

    private DihPlatform() {
    }

    // ------------------------------------------------------------------ client events

    /** The per-frame world-geometry hook: what a listener may submit into. */
    public record SubmitContext(LevelRenderState levelState, SubmitNodeCollector submitNodeCollector, PoseStack poseStack) {
    }

    @FunctionalInterface
    public interface ScreenExtractListener {
        void afterExtract(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta);
    }

    @FunctionalInterface
    public interface AvatarLayerFactory {
        RenderLayer<?, ?> create(AvatarRenderer<?> renderer, EntityRendererProvider.Context context);
    }

    //? if neoforge {
    /*private static net.neoforged.bus.api.IEventBus modBus;
    private static final List<Consumer<ClientConfigurationPacketListenerImpl>> CONFIG_INIT = new CopyOnWriteArrayList<>();
    private static final List<Runnable> CONFIG_DISCONNECT = new CopyOnWriteArrayList<>();
    private static final Map<Screen, List<ScreenExtractListener>> SCREEN_EXTRACT = new WeakHashMap<>();
    private static final Map<Screen, List<Consumer<Screen>>> SCREEN_REMOVE = new WeakHashMap<>();
    private static boolean screenHooks;

    private static Runnable clientStart;

    /^*
     * Called by the NeoForge mod constructor. NeoForge constructs mods before Minecraft exists, so {@code start} (the
     * client init Fabric runs from its client entrypoint) runs from DihPlatformStartMixin once the Minecraft
     * constructor has set the game directory, before resources load.
     ^/
    public static void initNeoForge(net.neoforged.bus.api.IEventBus bus, Runnable start) {
        modBus = bus;
        clientStart = start;
    }

    public static void fireClientStart() {
        Runnable start = clientStart;
        clientStart = null;
        if (start != null) start.run();
    }

    private static net.neoforged.bus.api.IEventBus gameBus() {
        return net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
    }

    /^* From DihPlatformHooksMixin: a configuration-phase connection started / ended. ^/
    public static void fireConfigurationInit(ClientConfigurationPacketListenerImpl listener) {
        for (var l : CONFIG_INIT) l.accept(listener);
    }

    public static void fireConfigurationDisconnect() {
        for (Runnable l : CONFIG_DISCONNECT) l.run();
    }

    private static void ensureScreenHooks() {
        if (screenHooks) return;
        screenHooks = true;
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Pre e) -> {
            SCREEN_EXTRACT.remove(e.getScreen());
            SCREEN_REMOVE.remove(e.getScreen());
        });
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) -> {
            List<ScreenExtractListener> ls = SCREEN_EXTRACT.get(e.getScreen());
            if (ls == null) return;
            for (ScreenExtractListener l : List.copyOf(ls)) {
                l.afterExtract(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick());
            }
        });
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Closing e) -> {
            List<Consumer<Screen>> ls = SCREEN_REMOVE.remove(e.getScreen());
            if (ls != null) for (Consumer<Screen> l : ls) l.accept(e.getScreen());
        });
    }
    *///?}

    public static void onEndClientTick(Consumer<Minecraft> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
        //?}
    }

    public static void onEndLevelTick(Consumer<ClientLevel> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ClientLevel level) listener.accept(level);
        });
        *///?} else {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_LEVEL_TICK.register(listener::accept);
        //?}
    }

    public static void onClientStopping(Consumer<Minecraft> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(listener::accept);
        //?}
    }

    /** A configuration-phase connection started (joining a server, or a server sent the player back to it). */
    public static void onConfigurationInit(Consumer<ClientConfigurationPacketListenerImpl> listener) {
        //? if neoforge {
        /*CONFIG_INIT.add(listener);
        *///?} else {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.INIT.register((handler, client) -> listener.accept(handler));
        //?}
    }

    public static void onConfigurationDisconnect(Runnable listener) {
        //? if neoforge {
        /*CONFIG_DISCONNECT.add(listener);
        *///?} else {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
        //?}
    }

    /** Joined a world (play phase started). */
    public static void onPlayJoin(Consumer<Minecraft> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.accept(client));
        //?}
    }

    public static void onPlayDisconnect(Runnable listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> listener.run());
        *///?} else {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
        //?}
    }

    /** Item tooltips: the stack and its (mutable) lines. */
    public static void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) -> listener.accept(e.getItemStack(), e.getToolTip()));
        *///?} else {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> listener.accept(stack, lines));
        //?}
    }

    /** Runs after every client resource (re)load. */
    public static void onClientResourceReload(String path, Runnable listener) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("dihclient", path);
        //? if neoforge {
        /*modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent e) ->
            e.addListener(id, (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> listener.run()));
        *///?} else {
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
            .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                @Override
                public net.minecraft.resources.Identifier getFabricId() {
                    return id;
                }

                @Override
                public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                    listener.run();
                }
            });
        //?}
    }

    /** Custom world geometry, submitted each frame alongside the level. */
    public static void onCollectSubmits(Consumer<SubmitContext> listener) {
        //? if neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) ->
            listener.accept(new SubmitContext(e.getLevelRenderState(), e.getSubmitNodeCollector(), e.getPoseStack())));
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context ->
            listener.accept(new SubmitContext(context.levelState(), context.submitNodeCollector(), context.poseStack())));
        //?}
    }

    /** Adds a render layer to every player (avatar) renderer. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addAvatarLayer(AvatarLayerFactory factory) {
        //? if neoforge {
        /*modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) -> {
            for (var skin : e.getSkins()) {
                AvatarRenderer player = e.getPlayerRenderer(skin);
                if (player != null) player.addLayer(factory.create(player, e.getContext()));
                AvatarRenderer mannequin = e.getMannequinRenderer(skin);
                if (mannequin != null) mannequin.addLayer(factory.create(mannequin, e.getContext()));
            }
        });
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register((RenderLayer) factory.create(avatar, context));
        });
        //?}
    }

    /** Draws after {@code screen} is extracted, until the screen is re-initialised. Call from the screen's init. */
    public static void afterScreenExtract(Screen screen, ScreenExtractListener listener) {
        //? if neoforge {
        /*ensureScreenHooks();
        SCREEN_EXTRACT.computeIfAbsent(screen, s -> new ArrayList<>()).add(listener);
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(listener::afterExtract);
        //?}
    }

    /** Runs when {@code screen} is closed or replaced. Call from the screen's init. */
    public static void onScreenRemove(Screen screen, Consumer<Screen> listener) {
        //? if neoforge {
        /*ensureScreenHooks();
        SCREEN_REMOVE.computeIfAbsent(screen, s -> new ArrayList<>()).add(listener);
        *///?} else {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen).register(listener::accept);
        //?}
    }
}
