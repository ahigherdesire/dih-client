package baritone.beat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * One {@code #beat} run as it is saved: the goal, the phase it is in, progress counters, and the portal and structure
 * positions it has found. Pure data; {@link CampaignStore} writes it to {@code baritone/beat/<world>.json}.
 */
public final class Campaign {
    public static final int VERSION = 1;
    public static final String GOAL = "dragon_dead";

    private final String world;
    private Phase phase = Phase.GEAR;
    private final long started;
    private long saved;
    private String problem = "";
    /** Progress when last saved, by phase id ("blaze_rods" -> 4). */
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    /** Remembered portal positions, by dimension ("overworld" -> x y z). */
    private final Map<String, int[]> portals = new LinkedHashMap<>();
    /** Found structures ("fortress", "stronghold" -> x y z). */
    private final Map<String, int[]> structures = new LinkedHashMap<>();

    public Campaign(String world, long started) {
        this.world = world;
        this.started = started;
    }

    public String world() {
        return world;
    }

    public Phase phase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public long started() {
        return started;
    }

    public long saved() {
        return saved;
    }

    /** Why the run stopped last, or "" while it goes. */
    public String problem() {
        return problem;
    }

    public void setProblem(String problem) {
        this.problem = problem == null ? "" : problem;
    }

    public Map<String, Integer> counters() {
        return counters;
    }

    public Map<String, int[]> portals() {
        return portals;
    }

    public Map<String, int[]> structures() {
        return structures;
    }

    /** Moves to the next phase; false when the dragon phase was the last. */
    public boolean next() {
        if (phase.ordinal() + 1 >= Phase.values().length) return false;
        phase = Phase.values()[phase.ordinal() + 1];
        problem = "";
        return true;
    }

    /** "Phase 3/8: blaze rods 4/7". */
    public String statusLine(ToIntFunction<String> have) {
        return phase.number() + ": " + phase.progress(have);
    }

    /** Records the current phase's progress for the save file. */
    public void count(ToIntFunction<String> have) {
        String progress = phase.progress(have);
        int slash = progress.lastIndexOf('/');
        int space = progress.lastIndexOf(' ', slash);
        if (slash > 0 && space >= 0) {
            try {
                counters.put(phase.id(), Integer.parseInt(progress.substring(space + 1, slash)));
            } catch (NumberFormatException ignored) {
                // A place phase has no counter.
            }
        }
    }

    public JsonObject toJson(long now) {
        this.saved = now;
        JsonObject json = new JsonObject();
        json.addProperty("version", VERSION);
        json.addProperty("goal", GOAL);
        json.addProperty("world", world);
        json.addProperty("phase", phase.id());
        json.addProperty("phaseNumber", phase.ordinal() + 1);
        json.addProperty("started", started);
        json.addProperty("saved", saved);
        json.addProperty("problem", problem);
        JsonObject counts = new JsonObject();
        counters.forEach(counts::addProperty);
        json.add("counters", counts);
        json.add("portals", positions(portals));
        json.add("structures", positions(structures));
        return json;
    }

    /** Reads a saved campaign; unknown or missing fields fall back to a fresh start at the gear phase. */
    public static Campaign fromJson(JsonObject json, String world) {
        long started = json.has("started") ? json.get("started").getAsLong() : 0;
        Campaign campaign = new Campaign(json.has("world") ? json.get("world").getAsString() : world, started);
        Phase phase = json.has("phase") ? Phase.byId(json.get("phase").getAsString()) : null;
        if (phase != null) campaign.phase = phase;
        if (json.has("saved")) campaign.saved = json.get("saved").getAsLong();
        if (json.has("problem")) campaign.problem = json.get("problem").getAsString();
        if (json.has("counters")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("counters").entrySet()) {
                campaign.counters.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        read(json, "portals", campaign.portals);
        read(json, "structures", campaign.structures);
        return campaign;
    }

    private static JsonObject positions(Map<String, int[]> map) {
        JsonObject out = new JsonObject();
        map.forEach((key, pos) -> {
            JsonArray array = new JsonArray();
            for (int v : pos) array.add(v);
            out.add(key, array);
        });
        return out;
    }

    private static void read(JsonObject json, String name, Map<String, int[]> into) {
        if (!json.has(name) || !json.get(name).isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject(name).entrySet()) {
            JsonArray array = e.getValue().getAsJsonArray();
            if (array.size() != 3) continue;
            into.put(e.getKey(), new int[]{array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt()});
        }
    }
}
