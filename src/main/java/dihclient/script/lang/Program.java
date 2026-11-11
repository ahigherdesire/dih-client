package dihclient.script.lang;

import java.util.List;
import java.util.Map;

/**
 * A parsed DihScript file: its top-level {@code let}/{@code const} declarations (run once, in order,
 * before {@code main}) and its functions by name.
 */
public record Program(List<Node.VarDecl> globals, Map<String, Node.Function> functions) {
    public static final String ENTRY_POINT = "main";
    public static final String STOP_HANDLER = "onStop";

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }
}
