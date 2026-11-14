package baritone.ai.director;

import baritone.Baritone;
import baritone.acquire.AcquireControl;
import baritone.ai.AiBrain;
import baritone.ai.tool.ToolContext;
import baritone.ai.tool.ToolRegistry;
import baritone.ai.tool.ToolResult;
import baritone.guardian.GuardianLog;
import baritone.guardian.GuardianProcess;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dihclient.modules.TeamsModule;
import dihclient.util.DihKeyMappingBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** The game as a director sees it. Called from the run's thread; game state is read on the game thread. */
public final class LiveDirectorHost implements DirectorHost {

    /** A job the client runs (a macro, a server connection): how it's going and how to stop it. */
    public record ClientJob(Supplier<JobStatus> status, Runnable stop) {
    }

    private static final Map<String, ClientJob> CLIENT_JOBS = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** How long a Baritone job must look idle before it counts as finished (it hands over between processes). */
    private static final long IDLE_MILLIS = 1000;
    /** A job gets this long to show up as running before an idle look means it already finished. */
    private static final long START_GRACE_MILLIS = 1500;

    public static void registerJob(String job, ClientJob clientJob) {
        CLIENT_JOBS.put(job, clientJob);
    }

    private final AiBrain brain;
    private final Path journalFile;
    private long guardianSeen = System.currentTimeMillis();
    private long handBackSeen = System.currentTimeMillis();
    private long movementSince = -1;
    private long jobStartedAt;
    private long idleSince = -1;
    private AcquireControl listeningTo;
    private volatile int acquireEnds;
    private volatile AcquireControl.AcquireEvent lastAcquireEnd;
    private int acquireMark;
    /** The job the last running step started. */
    private volatile String currentJob;

    public LiveDirectorHost(AiBrain brain, Path runsDir) {
        this.brain = brain;
        this.journalFile = runsDir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".json");
    }

    public Path journalFile() {
        return this.journalFile;
    }

    @Override
    public ToolResult runTool(String tool, JsonObject args, boolean confirmed) {
        this.brain.onGameThread(this::listenToAcquire, null);
        ToolContext ctx = ToolContext.of(this.brain, ToolContext.Source.AI).confirmed(confirmed).directed();
        ToolResult result = ToolRegistry.standard().call(ctx, tool, args);
        if (result.status() == ToolResult.Status.RUNNING) {
            this.jobStartedAt = now();
            this.idleSince = -1;
            this.acquireMark = this.acquireEnds;
            this.currentJob = String.valueOf(result.facts().get("job"));
        }
        return result;
    }

    private Void listenToAcquire() {
        AcquireControl control = AcquireControl.get();
        if (control != null && control != this.listeningTo) {
            this.listeningTo = control;
            control.addListener(event -> {
                if (event.kind() == AcquireControl.AcquireEvent.Kind.DONE || event.kind() == AcquireControl.AcquireEvent.Kind.FAILED
                        || event.kind() == AcquireControl.AcquireEvent.Kind.STOPPED) {
                    this.lastAcquireEnd = event;
                    this.acquireEnds++;
                }
            });
        }
        return null;
    }

    @Override
    public JobStatus poll(String job) {
        ClientJob client = CLIENT_JOBS.get(job);
        if (client != null) return client.status().get();
        if ("acquire".equals(job)) return pollAcquire();
        return this.brain.onGameThread(this::pollBaritone, JobStatus.running());
    }

    private JobStatus pollAcquire() {
        AcquireControl.AcquireEvent end = this.lastAcquireEnd;
        if (this.acquireEnds > this.acquireMark && end != null) {
            String message = end.message() == null || end.message().isBlank()
                    ? end.kind().name().toLowerCase() + ": " + end.count() + " " + end.item() : end.message();
            return end.kind() == AcquireControl.AcquireEvent.Kind.DONE ? JobStatus.done(message) : JobStatus.failed(message);
        }
        boolean active = this.brain.onGameThread(() -> {
            AcquireControl control = AcquireControl.get();
            return control != null && control.isActive();
        }, true);
        if (active) return JobStatus.running();
        return now() - this.jobStartedAt > 3000 ? JobStatus.failed("The acquire isn't running any more.") : JobStatus.running();
    }

    private JobStatus pollBaritone() {
        Baritone baritone = this.brain.getBaritone();
        boolean busy = baritone.getPathingControlManager().mostRecentInControl().isPresent()
                || baritone.getPathingBehavior().isPathing();
        long now = now();
        if (busy) {
            this.idleSince = -1;
            return JobStatus.running();
        }
        if (this.idleSince < 0) this.idleSince = now;
        return now - this.idleSince >= IDLE_MILLIS && now - this.jobStartedAt >= START_GRACE_MILLIS
                ? JobStatus.done("finished") : JobStatus.running();
    }

    @Override
    public String state() {
        return this.brain.onGameThread(() -> StateJson.build(this.brain.getBaritone()).toString(), "{\"in_world\":false}");
    }

    @Override
    public String autoStop() {
        return this.brain.onGameThread(this::checkVitals, null);
    }

    private String checkVitals() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return "Left the world";
        GuardianProcess guardian = this.brain.getBaritone().getGuardianProcess();
        if (guardian != null) {
            for (GuardianLog.Event event : guardian.log().recent()) {
                if (event.millis() > this.handBackSeen && event.text().contains("you have control")) {
                    this.handBackSeen = event.millis();
                    return "Guardian: " + event.text();
                }
            }
        }
        boolean hasFood = player.getOffhandItem().has(DataComponents.FOOD);
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.has(DataComponents.FOOD)) {
                hasFood = true;
                break;
            }
        }
        String stranger = null;
        double distance = Double.POSITIVE_INFINITY;
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || TeamsModule.isFriendOrTeam(other)) continue;
            String name = other.getGameProfile().name();
            if (this.brain.getConfig().isTrusted(name)) continue;
            double d = other.distanceTo(player);
            if (d < distance) {
                distance = d;
                stranger = name;
            }
        }
        long now = now();
        if (mc.gui.screen() == null && anyDown(mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                mc.options.keyJump)) {
            if (this.movementSince < 0) this.movementSince = now;
        } else {
            this.movementSince = -1;
        }
        long held = this.movementSince < 0 ? 0 : now - this.movementSince;
        return AutoStop.check(new AutoStop.Vitals(player.getHealth(), hasFood, stranger, distance, held));
    }

    private static boolean anyDown(KeyMapping... keys) {
        for (KeyMapping key : keys) {
            if (DihKeyMappingBridge.of(key).dih$isActuallyDown()) return true;
        }
        return false;
    }

    @Override
    public List<String> guardianEvents() {
        return this.brain.onGameThread(() -> {
            List<String> out = new ArrayList<>();
            GuardianProcess guardian = this.brain.getBaritone().getGuardianProcess();
            if (guardian == null) return out;
            for (GuardianLog.Event event : guardian.log().recent()) {
                if (event.millis() > this.guardianSeen) {
                    out.add(event.text());
                    this.guardianSeen = event.millis();
                }
            }
            return out;
        }, List.of());
    }

    @Override
    public long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void report(String line) {
        this.brain.logAsync("[AI] " + line, ChatFormatting.AQUA);
    }

    @Override
    public void askConfirm(String tool, String why) {
        Minecraft.getInstance().execute(() -> dihclient.ai.AiConfirmPrompt.show(tool, why));
    }

    @Override
    public void stopJobs() {
        ClientJob client = this.currentJob == null ? null : CLIENT_JOBS.get(this.currentJob);
        if (client != null) {
            try {
                client.stop().run();
            } catch (RuntimeException e) {
                System.err.println("[DIH] could not stop " + this.currentJob + ": " + e);
            }
        }
        this.brain.onGameThread(() -> {
            AcquireControl acquire = AcquireControl.get();
            if (acquire != null && acquire.isActive()) acquire.stop();
            this.brain.getBaritone().getPathingBehavior().cancelEverything();
            return true;
        }, false);
    }

    @Override
    public void journal(JsonObject journal) {
        try {
            Files.createDirectories(this.journalFile.getParent());
            Files.writeString(this.journalFile, GSON.toJson(journal), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[DIH] could not write the AI run journal: " + e.getMessage());
        }
    }
}
