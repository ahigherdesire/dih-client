package baritone.parkour.sim;

/**
 * What a player presses in one client tick: the movement keys, jump, sprint and sneak, and where they look. Only
 * vanilla inputs; the simulator turns them into movement exactly as the client does.
 */
public record TickInput(boolean forward, boolean back, boolean left, boolean right,
                        boolean jump, boolean sprint, boolean sneak, float yaw) {

    public static TickInput none(float yaw) {
        return new TickInput(false, false, false, false, false, false, false, yaw);
    }

    /** Holding forward and sprint, looking along {@code yaw}, jumping when asked. */
    public static TickInput sprint(float yaw, boolean jump) {
        return new TickInput(true, false, false, false, jump, true, false, yaw);
    }

    public TickInput withJump(boolean jump) {
        return new TickInput(forward, back, left, right, jump, sprint, sneak, yaw);
    }

    public TickInput withYaw(float yaw) {
        return new TickInput(forward, back, left, right, jump, sprint, sneak, yaw);
    }

    /** Forward impulse as the client computes it: +1, -1 or 0. */
    float forwardImpulse() {
        return forward == back ? 0.0F : forward ? 1.0F : -1.0F;
    }

    /** Left impulse as the client computes it: +1 (left), -1 (right) or 0. */
    float leftImpulse() {
        return left == right ? 0.0F : left ? 1.0F : -1.0F;
    }

    /** A compact form for logs and fixtures, e.g. "WA J S @-90.0". */
    public String compact() {
        StringBuilder out = new StringBuilder();
        if (forward) out.append('W');
        if (left) out.append('A');
        if (back) out.append('S');
        if (right) out.append('D');
        if (jump) out.append(" J");
        if (sprint) out.append(" R");
        if (sneak) out.append(" C");
        return out.append(" @").append(yaw).toString().trim();
    }

    /** Parses {@link #compact()}. */
    public static TickInput parse(String text) {
        String keys = text;
        float yaw = 0.0F;
        int at = text.indexOf('@');
        if (at >= 0) {
            yaw = Float.parseFloat(text.substring(at + 1).trim());
            keys = text.substring(0, at);
        }
        String[] parts = keys.trim().split("\\s+");
        String move = parts.length > 0 && !parts[0].equals("J") && !parts[0].equals("R") && !parts[0].equals("C") ? parts[0] : "";
        boolean jump = false, sprint = false, sneak = false;
        for (String part : parts) {
            switch (part) {
                case "J" -> jump = true;
                case "R" -> sprint = true;
                case "C" -> sneak = true;
                default -> {
                }
            }
        }
        return new TickInput(move.indexOf('W') >= 0, move.indexOf('S') >= 0, move.indexOf('A') >= 0, move.indexOf('D') >= 0,
            jump, sprint, sneak, yaw);
    }
}
