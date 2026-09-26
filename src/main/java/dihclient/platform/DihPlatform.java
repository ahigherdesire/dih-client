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
//? if !fabric {
/*import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
*///?}

/**
 * The mod-loader seam for client events: Fabric API on Fabric, the NeoForge or Forge event buses elsewhere (plus
 * DihPlatformHooksMixin / DihPlatformStartMixin / DihForgeSubmitMixin where the loader has no event). Everything
 * else calls this instead of a loader API. Loader queries are in {@link DihLoader}.
 */
public final class DihPlatform {

    private DihPlatform() {
    }

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

    //? if !fabric {
    /*// ---------------------------------------------------------------- NeoForge and Forge: hooks without an event
    private static final List<Consumer<ClientConfigurationPacketListenerImpl>> CONFIG_INIT = new CopyOnWriteArrayList<>();
    private static final List<Runnable> CONFIG_DISCONNECT = new CopyOnWriteArrayList<>();
    private static final Map<Screen, List<ScreenExtractListener>> SCREEN_EXTRACT = new WeakHashMap<>();
    private static final Map<Screen, List<Consumer<Screen>>> SCREEN_REMOVE = new WeakHashMap<>();
    private static boolean screenHooks;
    private static Runnable clientStart;

    /^*
     * The mod constructor hands over {@code start}: the client init Fabric runs from its client entrypoint. These
     * loaders construct mods before Minecraft exists, so it runs from DihPlatformStartMixin once the Minecraft
     * constructor has set the game directory, before resources load.
     ^/
    public static void setClientStart(Runnable start) {
        clientStart = start;
    }

    public static void fireClientStart() {
        Runnable start = clientStart;
        clientStart = null;
        if (start != null) start.run();
    }

    /^* From DihPlatformHooksMixin: a configuration-phase connection started / ended. ^/
    public static void fireConfigurationInit(ClientConfigurationPacketListenerImpl listener) {
        for (var l : CONFIG_INIT) l.accept(listener);
    }

    public static void fireConfigurationDisconnect() {
        for (Runnable l : CONFIG_DISCONNECT) l.run();
    }

    private static void screenInit(Screen screen) {
        SCREEN_EXTRACT.remove(screen);
        SCREEN_REMOVE.remove(screen);
    }

    private static void screenRendered(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        List<ScreenExtractListener> ls = SCREEN_EXTRACT.get(screen);
        if (ls == null) return;
        for (ScreenExtractListener l : List.copyOf(ls)) l.afterExtract(screen, graphics, mouseX, mouseY, tickDelta);
    }

    private static void screenClosing(Screen screen) {
        List<Consumer<Screen>> ls = SCREEN_REMOVE.remove(screen);
        if (ls != null) for (Consumer<Screen> l : ls) l.accept(screen);
    }
    *///?}

    //? if neoforge {
    /*private static net.neoforged.bus.api.IEventBus modBus;

    /^* Called first thing by the NeoForge mod constructor. ^/
    public static void initNeoForge(net.neoforged.bus.api.IEventBus bus, Runnable start) {
        modBus = bus;
        setClientStart(start);
    }

    private static net.neoforged.bus.api.IEventBus gameBus() {
        return net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
    }

    private static void ensureScreenHooks() {
        if (screenHooks) return;
        screenHooks = true;
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Pre e) -> screenInit(e.getScreen()));
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Render.Post e) ->
            screenRendered(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()));
        gameBus().addListener((net.neoforged.neoforge.client.event.ScreenEvent.Closing e) -> screenClosing(e.getScreen()));
    }
    *///?} elif forge {
    /*private static final List<Consumer<SubmitContext>> SUBMITS = new CopyOnWriteArrayList<>();

    /^* From DihForgeSubmitMixin: where NeoForge fires SubmitCustomGeometryEvent (Forge has no such event). ^/
    public static void fireSubmits(LevelRenderState state, SubmitNodeCollector collector, PoseStack pose) {
        if (SUBMITS.isEmpty()) return;
        SubmitContext context = new SubmitContext(state, collector, pose);
        for (var l : SUBMITS) l.accept(context);
    }

    private static void ensureScreenHooks() {
        if (screenHooks) return;
        screenHooks = true;
        net.minecraftforge.client.event.ScreenEvent.Init.Pre.BUS.addListener(e -> { screenInit(e.getScreen()); });
        net.minecraftforge.client.event.ScreenEvent.Render.Post.BUS.addListener(e ->
            screenRendered(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()));
        net.minecraftforge.client.event.ScreenEvent.Closing.BUS.addListener(e -> { screenClosing(e.getScreen()); });
    }
    *///?}

    public static void onEndClientTick(Consumer<Minecraft> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(listener::accept);
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        /*net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(e -> { listener.accept(Minecraft.getInstance()); });
        *///?}
    }

    public static void onEndLevelTick(Consumer<ClientLevel> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_LEVEL_TICK.register(listener::accept);
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.event.tick.LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ClientLevel level) listener.accept(level);
        });
        *///?} else {
        /*net.minecraftforge.event.TickEvent.LevelTickEvent.Post.BUS.addListener(e -> {
            if (e.level() instanceof ClientLevel level) listener.accept(level);
        });
        *///?}
    }

    public static void onClientStopping(Consumer<Minecraft> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(listener::accept);
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        /*net.minecraftforge.event.GameShuttingDownEvent.BUS.addListener(e -> { listener.accept(Minecraft.getInstance()); });
        *///?}
    }

    /** A configuration-phase connection started (joining a server, or a server sent the player back to it). */
    public static void onConfigurationInit(Consumer<ClientConfigurationPacketListenerImpl> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.INIT.register((handler, client) -> listener.accept(handler));
        //?} else {
        /*CONFIG_INIT.add(listener);
        *///?}
    }

    public static void onConfigurationDisconnect(Runnable listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
        //?} else {
        /*CONFIG_DISCONNECT.add(listener);
        *///?}
    }

    /** Joined a world (play phase started). */
    public static void onPlayJoin(Consumer<Minecraft> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.accept(client));
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) -> listener.accept(Minecraft.getInstance()));
        *///?} else {
        /*net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(e -> { listener.accept(Minecraft.getInstance()); });
        *///?}
    }

    public static void onPlayDisconnect(Runnable listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> listener.run());
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> listener.run());
        *///?} else {
        /*net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> { listener.run(); });
        *///?}
    }

    /** Item tooltips: the stack and its (mutable) lines. */
    public static void onItemTooltip(BiConsumer<ItemStack, List<Component>> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> listener.accept(stack, lines));
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e) -> listener.accept(e.getItemStack(), e.getToolTip()));
        *///?} else {
        /*net.minecraftforge.event.entity.player.ItemTooltipEvent.BUS.addListener(e -> { listener.accept(e.getItemStack(), e.getToolTip()); });
        *///?}
    }

    /** Runs after every client resource (re)load. */
    public static void onClientResourceReload(String path, Runnable listener) {
        net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.fromNamespaceAndPath("dihclient", path);
        //? if fabric {
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
        //?} elif neoforge {
        /*modBus.addListener((net.neoforged.neoforge.client.event.AddClientReloadListenersEvent e) ->
            e.addListener(id, (net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> listener.run()));
        *///?} else {
        /*// Forge fires its reload-listener event inside ClientModLoader.begin, before DIH starts; the resource
        // manager already exists by then, so register on it directly.
        ((net.minecraft.server.packs.resources.ReloadableResourceManager) Minecraft.getInstance().getResourceManager())
            .registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) manager -> listener.run());
        *///?}
    }

    /** Custom world geometry, submitted each frame alongside the level. */
    public static void onCollectSubmits(Consumer<SubmitContext> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(context ->
            listener.accept(new SubmitContext(context.levelState(), context.submitNodeCollector(), context.poseStack())));
        //?} elif neoforge {
        /*gameBus().addListener((net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent e) ->
            listener.accept(new SubmitContext(e.getLevelRenderState(), e.getSubmitNodeCollector(), e.getPoseStack())));
        *///?} else {
        /*SUBMITS.add(listener);
        *///?}
    }

    /** Adds a render layer to every player (avatar) renderer. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addAvatarLayer(AvatarLayerFactory factory) {
        //? if fabric {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register((RenderLayer) factory.create(avatar, context));
        });
        //?} elif neoforge {
        /*modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers e) -> {
            for (var skin : e.getSkins()) {
                AvatarRenderer player = e.getPlayerRenderer(skin);
                if (player != null) player.addLayer(factory.create(player, e.getContext()));
                AvatarRenderer mannequin = e.getMannequinRenderer(skin);
                if (mannequin != null) mannequin.addLayer(factory.create(mannequin, e.getContext()));
            }
        });
        *///?} else {
        /*net.minecraftforge.client.event.EntityRenderersEvent.AddLayers.BUS.addListener(e -> {
            for (var type : e.getModelTypes()) {
                if (e.getPlayerRenderer(type) instanceof AvatarRenderer player) player.addLayer(factory.create(player, e.getContext()));
                if (e.getMannequinRenderer(type) instanceof AvatarRenderer mannequin) mannequin.addLayer(factory.create(mannequin, e.getContext()));
            }
        });
        *///?}
    }

    /** Draws after {@code screen} is extracted, until the screen is re-initialised. Call from the screen's init. */
    public static void afterScreenExtract(Screen screen, ScreenExtractListener listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterExtract(screen).register(listener::afterExtract);
        //?} else {
        /*ensureScreenHooks();
        SCREEN_EXTRACT.computeIfAbsent(screen, s -> new ArrayList<>()).add(listener);
        *///?}
    }

    /** Runs when {@code screen} is closed or replaced. Call from the screen's init. */
    public static void onScreenRemove(Screen screen, Consumer<Screen> listener) {
        //? if fabric {
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.remove(screen).register(listener::accept);
        //?} else {
        /*ensureScreenHooks();
        SCREEN_REMOVE.computeIfAbsent(screen, s -> new ArrayList<>()).add(listener);
        *///?}
    }
}
