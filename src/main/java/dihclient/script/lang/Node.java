package dihclient.script.lang;

import dihclient.script.lang.Token.Type;

import java.util.List;

/** The DihScript syntax tree. Every node remembers its source line for error messages. */
public final class Node {
    private Node() {
    }

    // ---------------------------------------------------------------- expressions

    public sealed interface Expr permits Literal, Variable, Assign, IndexAssign, Unary, Binary, Logical, Call, Index, ListLiteral {
        int line();
    }

    public record Literal(Object value, int line) implements Expr { }

    public record Variable(String name, int line) implements Expr { }

    /** {@code name = value}, or a compound form ({@code +=} etc.) when {@code op} is not EQUAL. */
    public record Assign(String name, Type op, Expr value, int line) implements Expr { }

    /** {@code target[index] = value}. */
    public record IndexAssign(Expr target, Expr index, Type op, Expr value, int line) implements Expr { }

    public record Unary(Type op, Expr operand, int line) implements Expr { }

    public record Binary(Expr left, Type op, Expr right, int line) implements Expr { }

    /** Short-circuiting {@code &&} / {@code ||}. */
    public record Logical(Expr left, Type op, Expr right, int line) implements Expr { }

    public record Call(String name, List<Expr> args, int line) implements Expr { }

    public record Index(Expr target, Expr index, int line) implements Expr { }

    public record ListLiteral(List<Expr> items, int line) implements Expr { }

    // ---------------------------------------------------------------- statements

    public sealed interface Stmt permits ExprStmt, VarDecl, Block, If, While, Repeat, ForIn, Break, Continue, Return {
        int line();
    }

    public record ExprStmt(Expr expr, int line) implements Stmt { }

    public record VarDecl(String name, Expr init, boolean constant, int line) implements Stmt { }

    public record Block(List<Stmt> body, int line) implements Stmt { }

    public record If(Expr condition, Stmt then, Stmt otherwise, int line) implements Stmt { }

    public record While(Expr condition, Block body, int line) implements Stmt { }

    public record Repeat(Expr count, Block body, int line) implements Stmt { }

    public record ForIn(String name, Expr iterable, Block body, int line) implements Stmt { }

    public record Break(int line) implements Stmt { }

    public record Continue(int line) implements Stmt { }

    public record Return(Expr value, int line) implements Stmt { }

    // ---------------------------------------------------------------- top level

    public record Function(String name, List<String> params, Block body, int line) { }
}
