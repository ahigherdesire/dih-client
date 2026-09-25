package baritone.acquire.model;

/**
 * Killing {@code entity} drops {@code output}.
 *
 * @param dropsPerKill expected count without Looting
 * @param needsPlayerKill the drop only happens when a player lands the kill (fine for us, we are the player)
 */
public record KillSource(String entity, String output, double dropsPerKill, boolean needsPlayerKill) implements Source {
    @Override
    public double outputPerAction() {
        return dropsPerKill;
    }
}
