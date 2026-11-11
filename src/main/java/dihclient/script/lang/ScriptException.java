package dihclient.script.lang;

/**
 * A DihScript error — either a syntax error found while parsing or a runtime error raised while a
 * script runs. Always carries the 1-based source line (0 when unknown) so the editor and chat can
 * point at it.
 */
public class ScriptException extends RuntimeException {
    private final int line;
    private final int column;

    public ScriptException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public ScriptException(String message, int line) {
        this(message, line, 0);
    }

    public int line() { return line; }

    public int column() { return column; }

    /** "line 12: Unknown function 'foo'" — the form shown to the user. */
    public String describe() {
        if (line <= 0) return getMessage();
        return "line " + line + (column > 0 ? ":" + column : "") + ": " + getMessage();
    }
}
