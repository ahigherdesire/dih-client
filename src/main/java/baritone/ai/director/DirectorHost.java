package baritone.ai.director;

import baritone.ai.tool.ToolResult;
import com.google.gson.JsonObject;

import java.util.List;

/** The world as a director sees it. {@link LiveDirectorHost} is the game; tests script one. */
public interface DirectorHost {

    ToolResult runTool(String tool, JsonObject args, boolean confirmed);

    /** How the job named {@code job} (a running tool result's job id) is going. */
    JobStatus poll(String job);

    /** The structured state as JSON: what the planner reads. */
    String state();

    /** Why the run must hand control back right now, or null. */
    String autoStop();

    /** Guardian decisions since the last call. */
    List<String> guardianEvents();

    long now();

    /** A line for the player. */
    void report(String line);

    /** Stops whatever job is running. */
    void stopJobs();

    /** Saves the run journal (called whenever it changes). */
    void journal(JsonObject journal);
}
