package dihclient.script.lang;

import dihclient.script.lang.Node.*;
import dihclient.script.lang.Token.Type;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Recursive-descent parser for DihScript.
 *
 * <pre>
 * program    := ( "function" IDENT "(" params? ")" block | varDecl )*
 * varDecl    := ("let" | "const") IDENT ( "=" expr )? ";"
 * statement  := varDecl | if | while | repeat | for | block
 *             | "break" ";" | "continue" ";" | "return" expr? ";" | expr ";"
 * if         := "if" "(" expr ")" block ( "else" ( if | block ) )?
 * while      := "while" "(" expr ")" block
 * repeat     := "repeat" "(" expr ")" block
 * for        := "for" "(" ("let")? IDENT "in" expr ")" block
 * expr       := assignment
 * assignment := ( IDENT | postfix "[" expr "]" ) ( "=" | "+=" | "-=" | "*=" | "/=" ) assignment | or
 * or         := and ( "||" and )*
 * and        := equality ( "&&" equality )*
 * equality   := comparison ( ( "==" | "!=" ) comparison )*
 * comparison := term ( ( "<" | "<=" | ">" | ">=" ) term )*
 * term       := factor ( ( "+" | "-" ) factor )*
 * factor     := unary ( ( "*" | "/" | "%" ) unary )*
 * unary      := ( "!" | "-" ) unary | postfix
 * postfix    := primary ( "(" args? ")" | "[" expr "]" )*
 * primary    := NUMBER | STRING | "true" | "false" | "null" | IDENT | "(" expr ")" | "[" ( expr ("," expr)* )? "]"
 * </pre>
 *
 * Blocks are mandatory after {@code if}/{@code while}/etc. so there is never a dangling-else question.
 */
public final class Parser {
    private final List<Token> tokens;
    private int pos;

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public static Program parse(String source) {
        return new Parser(Lexer.tokenize(source)).program();
    }

    private Program program() {
        List<VarDecl> globals = new ArrayList<>();
        Map<String, Function> functions = new LinkedHashMap<>();
        while (!check(Type.EOF)) {
            if (match(Type.FUNCTION)) {
                Function fn = function();
                if (functions.containsKey(fn.name())) {
                    throw new ScriptException("Function '" + fn.name() + "' is defined twice", fn.line());
                }
                functions.put(fn.name(), fn);
            } else if (check(Type.LET) || check(Type.CONST)) {
                globals.add(varDecl());
            } else {
                Token t = peek();
                throw new ScriptException("Expected 'function', 'let' or 'const' at top level but found " + t
                    + " (statements must go inside a function, e.g. function main() { ... })", t.line(), t.column());
            }
        }
        return new Program(List.copyOf(globals), Map.copyOf(functions));
    }

    private Function function() {
        Token name = consume(Type.IDENTIFIER, "Expected a function name");
        consume(Type.LEFT_PAREN, "Expected '(' after the function name");
        List<String> params = new ArrayList<>();
        if (!check(Type.RIGHT_PAREN)) {
            do {
                Token p = consume(Type.IDENTIFIER, "Expected a parameter name");
                if (params.contains(p.text())) throw error(p, "Duplicate parameter '" + p.text() + "'");
                params.add(p.text());
            } while (match(Type.COMMA));
        }
        consume(Type.RIGHT_PAREN, "Expected ')' after the parameters");
        return new Function(name.text(), List.copyOf(params), block(), name.line());
    }

    private VarDecl varDecl() {
        Token kw = advance();
        boolean constant = kw.type() == Type.CONST;
        Token name = consume(Type.IDENTIFIER, "Expected a variable name");
        Expr init = null;
        if (match(Type.EQUAL)) init = expression();
        else if (constant) throw error(peek(), "A const needs a value: const " + name.text() + " = ...;");
        consume(Type.SEMICOLON, "Expected ';' after the declaration");
        return new VarDecl(name.text(), init, constant, kw.line());
    }

    // ---------------------------------------------------------------- statements

    private Stmt statement() {
        if (check(Type.LET) || check(Type.CONST)) return varDecl();
        if (match(Type.IF)) return ifStatement(previous());
        if (match(Type.WHILE)) {
            Token kw = previous();
            Expr cond = parenthesized("while");
            return new While(cond, block(), kw.line());
        }
        if (match(Type.REPEAT)) {
            Token kw = previous();
            Expr count = parenthesized("repeat");
            return new Repeat(count, block(), kw.line());
        }
        if (match(Type.FOR)) return forStatement(previous());
        if (check(Type.LEFT_BRACE)) return block();
        if (match(Type.BREAK)) {
            Token kw = previous();
            consume(Type.SEMICOLON, "Expected ';' after 'break'");
            return new Break(kw.line());
        }
        if (match(Type.CONTINUE)) {
            Token kw = previous();
            consume(Type.SEMICOLON, "Expected ';' after 'continue'");
            return new Continue(kw.line());
        }
        if (match(Type.RETURN)) {
            Token kw = previous();
            Expr value = check(Type.SEMICOLON) ? null : expression();
            consume(Type.SEMICOLON, "Expected ';' after the return value");
            return new Return(value, kw.line());
        }
        if (check(Type.FUNCTION)) {
            throw error(peek(), "Functions must be declared at the top level, not inside another function");
        }
        Expr expr = expression();
        consume(Type.SEMICOLON, "Expected ';' after the statement");
        return new ExprStmt(expr, expr.line());
    }

    private Stmt ifStatement(Token kw) {
        Expr cond = parenthesized("if");
        Block then = block();
        Stmt otherwise = null;
        if (match(Type.ELSE)) {
            otherwise = match(Type.IF) ? ifStatement(previous()) : block();
        }
        return new If(cond, then, otherwise, kw.line());
    }

    private Stmt forStatement(Token kw) {
        consume(Type.LEFT_PAREN, "Expected '(' after 'for'");
        match(Type.LET);
        Token name = consume(Type.IDENTIFIER, "Expected a loop variable name: for (let item in list) { ... }");
        consume(Type.IN, "Expected 'in' after the loop variable: for (let item in list) { ... }");
        Expr iterable = expression();
        consume(Type.RIGHT_PAREN, "Expected ')' after the for-loop header");
        return new ForIn(name.text(), iterable, block(), kw.line());
    }

    private Expr parenthesized(String keyword) {
        consume(Type.LEFT_PAREN, "Expected '(' after '" + keyword + "'");
        Expr e = expression();
        consume(Type.RIGHT_PAREN, "Expected ')' after the " + keyword + " condition");
        return e;
    }

    private Block block() {
        Token open = consume(Type.LEFT_BRACE, "Expected '{'");
        List<Stmt> body = new ArrayList<>();
        while (!check(Type.RIGHT_BRACE) && !check(Type.EOF)) body.add(statement());
        if (check(Type.EOF)) throw new ScriptException("Missing '}' for the block opened here", open.line(), open.column());
        advance();
        return new Block(List.copyOf(body), open.line());
    }

    // ---------------------------------------------------------------- expressions

    private Expr expression() {
        return assignment();
    }

    private Expr assignment() {
        Expr target = or();
        if (match(Type.EQUAL, Type.PLUS_EQUAL, Type.MINUS_EQUAL, Type.STAR_EQUAL, Type.SLASH_EQUAL)) {
            Token op = previous();
            Expr value = assignment();
            if (target instanceof Variable v) return new Assign(v.name(), op.type(), value, op.line());
            if (target instanceof Index ix) return new IndexAssign(ix.target(), ix.index(), op.type(), value, op.line());
            throw error(op, "Can only assign to a variable or a list element");
        }
        return target;
    }

    private Expr or() {
        Expr e = and();
        while (match(Type.OR_OR)) {
            Token op = previous();
            e = new Logical(e, op.type(), and(), op.line());
        }
        return e;
    }

    private Expr and() {
        Expr e = equality();
        while (match(Type.AND_AND)) {
            Token op = previous();
            e = new Logical(e, op.type(), equality(), op.line());
        }
        return e;
    }

    private Expr equality() {
        Expr e = comparison();
        while (match(Type.EQUAL_EQUAL, Type.BANG_EQUAL)) {
            Token op = previous();
            e = new Binary(e, op.type(), comparison(), op.line());
        }
        return e;
    }

    private Expr comparison() {
        Expr e = term();
        while (match(Type.LESS, Type.LESS_EQUAL, Type.GREATER, Type.GREATER_EQUAL)) {
            Token op = previous();
            e = new Binary(e, op.type(), term(), op.line());
        }
        return e;
    }

    private Expr term() {
        Expr e = factor();
        while (match(Type.PLUS, Type.MINUS)) {
            Token op = previous();
            e = new Binary(e, op.type(), factor(), op.line());
        }
        return e;
    }

    private Expr factor() {
        Expr e = unary();
        while (match(Type.STAR, Type.SLASH, Type.PERCENT)) {
            Token op = previous();
            e = new Binary(e, op.type(), unary(), op.line());
        }
        return e;
    }

    private Expr unary() {
        if (match(Type.BANG, Type.MINUS)) {
            Token op = previous();
            return new Unary(op.type(), unary(), op.line());
        }
        return postfix();
    }

    private Expr postfix() {
        Expr e = primary();
        while (true) {
            if (match(Type.LEFT_PAREN)) {
                Token paren = previous();
                if (!(e instanceof Variable v)) throw error(paren, "Only named functions can be called");
                List<Expr> args = new ArrayList<>();
                if (!check(Type.RIGHT_PAREN)) {
                    do {
                        args.add(expression());
                    } while (match(Type.COMMA));
                }
                consume(Type.RIGHT_PAREN, "Expected ')' after the arguments");
                e = new Call(v.name(), List.copyOf(args), v.line());
            } else if (match(Type.LEFT_BRACKET)) {
                Token bracket = previous();
                Expr index = expression();
                consume(Type.RIGHT_BRACKET, "Expected ']' after the index");
                e = new Index(e, index, bracket.line());
            } else {
                return e;
            }
        }
    }

    private Expr primary() {
        Token t = advance();
        return switch (t.type()) {
            case NUMBER, STRING -> new Literal(t.literal(), t.line());
            case TRUE -> new Literal(Boolean.TRUE, t.line());
            case FALSE -> new Literal(Boolean.FALSE, t.line());
            case NULL -> new Literal(null, t.line());
            case IDENTIFIER -> new Variable(t.text(), t.line());
            case LEFT_PAREN -> {
                Expr e = expression();
                consume(Type.RIGHT_PAREN, "Expected ')'");
                yield e;
            }
            case LEFT_BRACKET -> {
                List<Expr> items = new ArrayList<>();
                if (!check(Type.RIGHT_BRACKET)) {
                    do {
                        items.add(expression());
                    } while (match(Type.COMMA));
                }
                consume(Type.RIGHT_BRACKET, "Expected ']' to close the list");
                yield new ListLiteral(List.copyOf(items), t.line());
            }
            default -> throw error(t, "Expected a value but found " + t);
        };
    }

    // ---------------------------------------------------------------- helpers

    private boolean match(Type... types) {
        for (Type type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(Type type) {
        return peek().type() == type;
    }

    private Token consume(Type type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message + " but found " + peek());
    }

    private Token advance() {
        Token t = tokens.get(pos);
        if (t.type() != Type.EOF) pos++;
        return t;
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token previous() {
        return tokens.get(pos - 1);
    }

    private static ScriptException error(Token t, String message) {
        return new ScriptException(message, t.line(), t.column());
    }
}
