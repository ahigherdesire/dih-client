package baritone.ai.director;

import baritone.ai.AiConfig;

/**
 * What one run may use: model calls (it pauses and asks for more), wall time (it stops), and how long a step's job may
 * take before it counts as stuck ({@code #acquire} gets longer: it gathers whole crafting chains).
 */
public record DirectorLimits(int maxModelCalls, long maxRunMillis, long stepTimeoutMillis, long acquireTimeoutMillis) {

    public static DirectorLimits from(AiConfig config) {
        return new DirectorLimits(Math.max(1, config.maxModelCallsPerRun), Math.max(1, config.maxRunMinutes) * 60_000L,
                10 * 60_000L, 30 * 60_000L);
    }
}
