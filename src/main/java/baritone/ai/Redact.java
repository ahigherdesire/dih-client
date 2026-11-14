package baritone.ai;

import java.util.regex.Pattern;

/** Keeps API keys out of anything shown or saved: chat, logs, the run journal. */
public final class Redact {

    /** Things shaped like provider keys: sk-..., sk-ant-..., sk-or-..., gsk_..., AIza... */
    private static final Pattern KEY_SHAPED = Pattern.compile("\\b(sk-(?:ant-|or-|proj-)?|gsk_|AIza)[A-Za-z0-9_\\-]{12,}");

    private Redact() {
    }

    /** {@code text} with {@code key} and anything key-shaped masked. */
    public static String text(String text, String key) {
        if (text == null) return "";
        String out = text;
        if (key != null && key.trim().length() >= 6) out = out.replace(key.trim(), mask(key.trim()));
        return KEY_SHAPED.matcher(out).replaceAll(match -> mask(match.group()));
    }

    /** "sk-a…wxyz": enough to tell keys apart, not enough to use one. */
    public static String mask(String key) {
        if (key == null || key.isBlank()) return "";
        String k = key.trim();
        if (k.length() <= 8) return "…";
        return k.substring(0, 4) + "…" + k.substring(k.length() - 4);
    }
}
