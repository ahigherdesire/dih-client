package baritone.ai.catalog;

import baritone.ai.tool.CommandRunner;

import java.util.ArrayList;
import java.util.List;

/** Records the commands tools run, and answers each with {@link #next}. */
public final class RecordingCommands implements CommandRunner {
    public final List<String> baritone = new ArrayList<>();
    public final List<String> client = new ArrayList<>();
    public Outcome next = Outcome.ok("");

    @Override
    public Outcome baritone(String command) {
        this.baritone.add(command);
        return this.next;
    }

    @Override
    public Outcome client(String command) {
        this.client.add(command);
        return this.next;
    }
}
