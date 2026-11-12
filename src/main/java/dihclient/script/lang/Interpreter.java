package dihclient.script.lang;

import dihclient.script.lang.Node.*;
import dihclient.script.lang.Token.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree-walking interpreter for a parsed {@link Program}. One instance runs one script on one thread;
 * {@link #run()} blocks until {@code main()} returns, the script calls {@code stop()}, or the host
 * cancels it.
 */
public final class Interpreter {
    /** Statements a script may run back-to-back before it is made to yield one tick. */
    static final int STEPS_PER_TICK = 20_000;
    static final int MAX_CALL_DEPTH = 200;

    /** Thrown to unwind the script when it stops (by {@code stop()} or because it was cancelled). */
    public static final class StopSignal extends RuntimeException {
        public StopSignal() {
            super(null, null, false, false);
        }
    }

    private static final class BreakSignal extends RuntimeException {
        static final BreakSignal INSTANCE = new BreakSignal();
        BreakSignal() { super(null, null, false, false); }
    }

    private static final class ContinueSignal extends RuntimeException {
        static final ContinueSignal INSTANCE = new ContinueSignal();
        ContinueSignal() { super(null, null, false, false); }
    }

    private static final class ReturnSignal extends RuntimeException {
        final Object value;
        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    private static final class Slot {
        Object value;
        final boolean constant;
        Slot(Object value, boolean constant) {
            this.value = value;
            this.constant = constant;
        }
    }

    private static final class Env {
        final Env parent;
        final Map<String, Slot> vars = new HashMap<>();
        Env(Env parent) { this.parent = parent; }

        Slot find(String name) {
            for (Env e = this; e != null; e = e.parent) {
                Slot s = e.vars.get(name);
                if (s != null) return s;
            }
            return null;
        }
    }

    private final Program program;
    private final Library library;
    private final ScriptHost host;
    private final Env globals = new Env(null);
    private int depth;
    private int stepsSinceYield;
    private volatile int currentLine;
    private volatile String currentFunction = "";
    private boolean ignoreCancel;

    public Interpreter(Program program, Library library, ScriptHost host) {
        this.program = program;
        this.library = library;
        this.host = host;
    }

    public ScriptHost host() { return host; }

    public int currentLine() { return currentLine; }

    public String currentFunction() { return currentFunction; }

    /** Runs the globals, then {@code main()}. A {@link StopSignal} ends the run quietly. */
    public void run() {
        Function main = program.functions().get(Program.ENTRY_POINT);
        if (main == null) throw new ScriptException("No main() function — add: function main() { ... }", 0);
        try {
            for (VarDecl decl : program.globals()) execute(decl, globals);
            invoke(main, List.of(), main.line());
        } catch (StopSignal ignored) {
            // normal stop
        }
    }

    /**
     * Calls {@code onStop()} if the script defines it. Cancellation is ignored while it runs (it is
     * the cleanup path) but waits still work; a {@link StopSignal} from {@code stop()} ends it.
     */
    public void runStopHandler() {
        Function handler = program.functions().get(Program.STOP_HANDLER);
        if (handler == null) return;
        ignoreCancel = true;
        try {
            invoke(handler, List.of(), handler.line());
        } catch (StopSignal ignored) {
            // fine
        } finally {
            ignoreCancel = false;
        }
    }

    /** Called by built-ins that wait so the implicit-yield budget resets. */
    public void noteYield() {
        stepsSinceYield = 0;
    }

    /** Waits through the host; used by built-ins. */
    public void waitTicks(int ticks) {
        checkCancelled();
        stepsSinceYield = 0;
        host.waitTicks(Math.max(0, ticks));
        checkCancelled();
    }

    public void checkCancelled() {
        if (!ignoreCancel && host.isCancelled()) throw new StopSignal();
    }

    // ---------------------------------------------------------------- statements

    private void checkpoint(int line) {
        currentLine = line;
        checkCancelled();
        if (++stepsSinceYield >= STEPS_PER_TICK) {
            stepsSinceYield = 0;
            host.waitTicks(1);
        }
    }

    private void execute(Stmt stmt, Env env) {
        checkpoint(stmt.line());
        switch (stmt) {
            case ExprStmt s -> evaluate(s.expr(), env);
            case VarDecl s -> {
                if (env.vars.containsKey(s.name())) {
                    throw new ScriptException("'" + s.name() + "' is already declared in this scope", s.line());
                }
                Object value = s.init() == null ? null : evaluate(s.init(), env);
                env.vars.put(s.name(), new Slot(value, s.constant()));
            }
            case Block s -> executeBlock(s, new Env(env));
            case If s -> {
                if (Values.truthy(evaluate(s.condition(), env))) execute(s.then(), env);
                else if (s.otherwise() != null) execute(s.otherwise(), env);
            }
            case While s -> {
                while (Values.truthy(evaluate(s.condition(), env))) {
                    if (runLoopBody(s.body(), env)) break;
                }
            }
            case Repeat s -> {
                Object countValue = evaluate(s.count(), env);
                if (!(countValue instanceof Double d)) {
                    throw new ScriptException("repeat(...) needs a number, got " + Values.typeName(countValue), s.line());
                }
                long count = (long) Math.floor(d);
                for (long i = 0; i < count; i++) {
                    if (runLoopBody(s.body(), env)) break;
                }
            }
            case ForIn s -> {
                Object iterable = evaluate(s.iterable(), env);
                List<Object> items;
                if (iterable instanceof List<?> l) items = new ArrayList<>(l);
                else if (iterable instanceof String str) {
                    items = new ArrayList<>();
                    for (char c : str.toCharArray()) items.add(String.valueOf(c));
                } else {
                    throw new ScriptException("for-in needs a list or string, got " + Values.typeName(iterable), s.line());
                }
                for (Object item : items) {
                    Env loopEnv = new Env(env);
                    loopEnv.vars.put(s.name(), new Slot(item, false));
                    if (runLoopBody(s.body(), loopEnv)) break;
                }
            }
            case Break s -> throw BreakSignal.INSTANCE;
            case Continue s -> throw ContinueSignal.INSTANCE;
            case Return s -> throw new ReturnSignal(s.value() == null ? null : evaluate(s.value(), env));
        }
    }

    /** Runs one loop iteration; returns true when the loop should end ({@code break}). */
    private boolean runLoopBody(Block body, Env env) {
        // Empty bodies still need to yield and observe cancellation.
        checkpoint(body.line());
        try {
            executeBlock(body, new Env(env));
            return false;
        } catch (BreakSignal b) {
            return true;
        } catch (ContinueSignal c) {
            return false;
        }
    }

    private void executeBlock(Block block, Env env) {
        for (Stmt s : block.body()) execute(s, env);
    }

    // ---------------------------------------------------------------- expressions

    private Object evaluate(Expr expr, Env env) {
        return switch (expr) {
            case Literal e -> e.value();
            case ListLiteral e -> {
                List<Object> out = new ArrayList<>(e.items().size());
                for (Expr item : e.items()) out.add(evaluate(item, env));
                yield out;
            }
            case Variable e -> {
                Slot slot = env.find(e.name());
                if (slot == null) {
                    if (program.hasFunction(e.name()) || library.get(e.name()) != null) {
                        throw new ScriptException("'" + e.name() + "' is a function — call it with " + e.name() + "()", e.line());
                    }
                    throw new ScriptException("Unknown variable '" + e.name() + "'", e.line());
                }
                yield slot.value;
            }
            case Assign e -> {
                Slot slot = env.find(e.name());
                if (slot == null) {
                    throw new ScriptException("Unknown variable '" + e.name() + "' — declare it first with: let "
                        + e.name() + " = ...;", e.line());
                }
                if (slot.constant) throw new ScriptException("Cannot change const '" + e.name() + "'", e.line());
                Object value = evaluate(e.value(), env);
                slot.value = e.op() == Type.EQUAL ? value : arithmetic(compoundOp(e.op()), slot.value, value, e.line());
                yield slot.value;
            }
            case IndexAssign e -> {
                Object target = evaluate(e.target(), env);
                if (!(target instanceof List<?>)) {
                    throw new ScriptException("Only list elements can be assigned with [i], got " + Values.typeName(target), e.line());
                }
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) target;
                int i = listIndex(list.size(), evaluate(e.index(), env), e.line());
                Object value = evaluate(e.value(), env);
                Object result = e.op() == Type.EQUAL ? value : arithmetic(compoundOp(e.op()), list.get(i), value, e.line());
                list.set(i, result);
                yield result;
            }
            case Unary e -> {
                Object v = evaluate(e.operand(), env);
                if (e.op() == Type.BANG) yield !Values.truthy(v);
                if (v instanceof Double d) yield -d;
                throw new ScriptException("Cannot negate a " + Values.typeName(v), e.line());
            }
            case Logical e -> {
                Object left = evaluate(e.left(), env);
                if (e.op() == Type.OR_OR) yield Values.truthy(left) ? left : evaluate(e.right(), env);
                yield !Values.truthy(left) ? left : evaluate(e.right(), env);
            }
            case Binary e -> binary(e, evaluate(e.left(), env), evaluate(e.right(), env));
            case Index e -> {
                Object target = evaluate(e.target(), env);
                Object index = evaluate(e.index(), env);
                if (target instanceof List<?> l) yield l.get(listIndex(l.size(), index, e.line()));
                if (target instanceof String s) {
                    int i = listIndex(s.length(), index, e.line());
                    yield String.valueOf(s.charAt(i));
                }
                throw new ScriptException("Cannot index into a " + Values.typeName(target), e.line());
            }
            case Call e -> call(e, env);
        };
    }

    private static Type compoundOp(Type op) {
        return switch (op) {
            case PLUS_EQUAL -> Type.PLUS;
            case MINUS_EQUAL -> Type.MINUS;
            case STAR_EQUAL -> Type.STAR;
            case SLASH_EQUAL -> Type.SLASH;
            default -> op;
        };
    }

    private static int listIndex(int size, Object index, int line) {
        if (!(index instanceof Double d) || d != Math.floor(d)) {
            throw new ScriptException("Index must be a whole number, got " + Values.str(index), line);
        }
        int i = d.intValue();
        if (i < 0) i += size;
        if (i < 0 || i >= size) throw new ScriptException("Index " + Values.str(index) + " is out of range (size " + size + ")", line);
        return i;
    }

    private Object binary(Binary e, Object left, Object right) {
        return switch (e.op()) {
            case EQUAL_EQUAL -> Values.equal(left, right);
            case BANG_EQUAL -> !Values.equal(left, right);
            case LESS, LESS_EQUAL, GREATER, GREATER_EQUAL -> {
                int cmp;
                if (left instanceof Double a && right instanceof Double b) cmp = Double.compare(a, b);
                else if (left instanceof String a && right instanceof String b) cmp = a.compareTo(b);
                else throw new ScriptException("Cannot compare " + Values.typeName(left) + " with " + Values.typeName(right), e.line());
                yield switch (e.op()) {
                    case LESS -> cmp < 0;
                    case LESS_EQUAL -> cmp <= 0;
                    case GREATER -> cmp > 0;
                    default -> cmp >= 0;
                };
            }
            default -> arithmetic(e.op(), left, right, e.line());
        };
    }

    private static Object arithmetic(Type op, Object left, Object right, int line) {
        if (op == Type.PLUS) {
            if (left instanceof String || right instanceof String) return Values.str(left) + Values.str(right);
            if (left instanceof List<?> a && right instanceof List<?> b) {
                List<Object> out = new ArrayList<>(a);
                out.addAll(b);
                return out;
            }
        }
        if (!(left instanceof Double a) || !(right instanceof Double b)) {
            throw new ScriptException("Cannot apply '" + symbol(op) + "' to " + Values.typeName(left)
                + " and " + Values.typeName(right), line);
        }
        return switch (op) {
            case PLUS -> a + b;
            case MINUS -> a - b;
            case STAR -> a * b;
            case SLASH -> {
                if (b == 0) throw new ScriptException("Division by zero", line);
                yield a / b;
            }
            case PERCENT -> {
                if (b == 0) throw new ScriptException("Division by zero", line);
                yield a % b;
            }
            default -> throw new ScriptException("Unsupported operator '" + symbol(op) + "'", line);
        };
    }

    private static String symbol(Type op) {
        return switch (op) {
            case PLUS -> "+";
            case MINUS -> "-";
            case STAR -> "*";
            case SLASH -> "/";
            case PERCENT -> "%";
            default -> op.name();
        };
    }

    private Object call(Call e, Env env) {
        List<Object> args = new ArrayList<>(e.args().size());
        for (Expr a : e.args()) args.add(evaluate(a, env));

        Function fn = program.functions().get(e.name());
        if (fn != null) return invoke(fn, args, e.line());

        Library.Entry entry = library.get(e.name());
        if (entry == null) {
            String hint = library.suggest(e.name());
            throw new ScriptException("Unknown function '" + e.name() + "'" + (hint == null ? "" : " — did you mean " + hint + "()?"), e.line());
        }
        if (args.size() < entry.minArgs() || (entry.maxArgs() >= 0 && args.size() > entry.maxArgs())) {
            throw new ScriptException("Wrong number of arguments: usage is " + entry.signature(), e.line());
        }
        currentLine = e.line();
        Object result;
        try {
            result = entry.fn().call(new Library.Args(entry, args, this, e.line()));
        } catch (ScriptException ex) {
            if (ex.line() > 0) throw ex;
            throw new ScriptException(e.name() + "(): " + ex.getMessage(), e.line());
        }
        if (result instanceof Integer i) return i.doubleValue();
        if (result instanceof Long l) return l.doubleValue();
        if (result instanceof Float f) return f.doubleValue();
        return result;
    }

    private Object invoke(Function fn, List<Object> args, int callLine) {
        if (args.size() > fn.params().size()) {
            throw new ScriptException(fn.name() + "() takes " + fn.params().size() + " argument(s) but got " + args.size(), callLine);
        }
        if (depth >= MAX_CALL_DEPTH) {
            throw new ScriptException("Too many nested calls (over " + MAX_CALL_DEPTH + ") — is " + fn.name() + "() calling itself forever?", callLine);
        }
        Env env = new Env(globals);
        for (int i = 0; i < fn.params().size(); i++) {
            env.vars.put(fn.params().get(i), new Slot(i < args.size() ? args.get(i) : null, false));
        }
        String previousFunction = currentFunction;
        depth++;
        currentFunction = fn.name();
        try {
            executeBlock(fn.body(), env);
            return null;
        } catch (ReturnSignal r) {
            return r.value;
        } catch (BreakSignal | ContinueSignal s) {
            throw new ScriptException("'break'/'continue' used outside a loop in " + fn.name() + "()", fn.line());
        } finally {
            depth--;
            currentFunction = previousFunction;
        }
    }
}
