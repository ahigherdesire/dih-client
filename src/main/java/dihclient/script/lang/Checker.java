package dihclient.script.lang;

import dihclient.script.lang.Node.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Static checks run before a script starts: it must have {@code main()}, every called function must
 * exist with an acceptable number of arguments, and {@code break}/{@code continue} must sit inside a loop.
 */
public final class Checker {
    private final Program program;
    private final Library library;
    private final List<ScriptException> problems = new ArrayList<>();

    private Checker(Program program, Library library) {
        this.program = program;
        this.library = library;
    }

    /** Parses and checks; returns every problem found (empty when the script is fine). */
    public static List<ScriptException> check(String source, Library library) {
        Program program;
        try {
            program = Parser.parse(source);
        } catch (ScriptException e) {
            return List.of(e);
        }
        return check(program, library);
    }

    public static List<ScriptException> check(Program program, Library library) {
        Checker c = new Checker(program, library);
        if (!program.hasFunction(Program.ENTRY_POINT)) {
            c.problems.add(new ScriptException("No main() function — add: function main() { ... }", 0));
        }
        Function stop = program.functions().get(Program.STOP_HANDLER);
        if (stop != null && !stop.params().isEmpty()) {
            c.problems.add(new ScriptException("onStop() must not take parameters", stop.line()));
        }
        for (VarDecl g : program.globals()) c.stmt(g, 0);
        for (Function f : program.functions().values()) {
            for (Stmt s : f.body().body()) c.stmt(s, 0);
        }
        c.problems.sort((a, b) -> Integer.compare(a.line(), b.line()));
        return List.copyOf(c.problems);
    }

    private void stmt(Stmt stmt, int loops) {
        switch (stmt) {
            case ExprStmt s -> expr(s.expr());
            case VarDecl s -> { if (s.init() != null) expr(s.init()); }
            case Block s -> { for (Stmt inner : s.body()) stmt(inner, loops); }
            case If s -> {
                expr(s.condition());
                stmt(s.then(), loops);
                if (s.otherwise() != null) stmt(s.otherwise(), loops);
            }
            case While s -> { expr(s.condition()); stmt(s.body(), loops + 1); }
            case Repeat s -> { expr(s.count()); stmt(s.body(), loops + 1); }
            case ForIn s -> { expr(s.iterable()); stmt(s.body(), loops + 1); }
            case Break s -> { if (loops == 0) problems.add(new ScriptException("'break' outside a loop", s.line())); }
            case Continue s -> { if (loops == 0) problems.add(new ScriptException("'continue' outside a loop", s.line())); }
            case Return s -> { if (s.value() != null) expr(s.value()); }
        }
    }

    private void expr(Expr expr) {
        switch (expr) {
            case Literal e -> { }
            case Variable e -> { }
            case Assign e -> expr(e.value());
            case IndexAssign e -> { expr(e.target()); expr(e.index()); expr(e.value()); }
            case Unary e -> expr(e.operand());
            case Binary e -> { expr(e.left()); expr(e.right()); }
            case Logical e -> { expr(e.left()); expr(e.right()); }
            case Index e -> { expr(e.target()); expr(e.index()); }
            case ListLiteral e -> { for (Expr item : e.items()) expr(item); }
            case Call e -> {
                for (Expr a : e.args()) expr(a);
                call(e);
            }
        }
    }

    private void call(Call e) {
        Function fn = program.functions().get(e.name());
        if (fn != null) {
            if (e.args().size() > fn.params().size()) {
                problems.add(new ScriptException(e.name() + "() takes " + fn.params().size()
                    + " argument(s) but is called with " + e.args().size(), e.line()));
            }
            return;
        }
        Library.Entry entry = library.get(e.name());
        if (entry == null) {
            String hint = library.suggest(e.name());
            problems.add(new ScriptException("Unknown function '" + e.name() + "'"
                + (hint == null ? "" : " — did you mean " + hint + "()?"), e.line()));
            return;
        }
        int n = e.args().size();
        if (n < entry.minArgs() || (entry.maxArgs() >= 0 && n > entry.maxArgs())) {
            problems.add(new ScriptException("Wrong number of arguments: usage is " + entry.signature(), e.line()));
        }
    }
}
