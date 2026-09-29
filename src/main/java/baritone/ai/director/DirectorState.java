package baritone.ai.director;

import java.util.List;

/** A snapshot of a run for the chat checklist and the panel. */
public record DirectorState(String objective, boolean basic, RunStatus status, List<StepView> steps, int current,
                            String lastReason, int modelCalls, long promptTokens, long completionTokens, long startedMillis,
                            int planVersion) {

    public record StepView(String tool, String args, String reason, StepStatus status, String note) {
    }

    public static final DirectorState IDLE = new DirectorState("", false, RunStatus.IDLE, List.of(), 0, "", 0, 0, 0, 0, 0);
}
