package dihclient.script.lang;

import dihclient.script.lang.Token.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns DihScript source text into tokens. Supports {@code //} and {@code /* *\/} comments. */
public final class Lexer {
    public static final Map<String, Type> KEYWORDS = Map.ofEntries(
        Map.entry("function", Type.FUNCTION),
        Map.entry("let", Type.LET),
        Map.entry("const", Type.CONST),
        Map.entry("if", Type.IF),
        Map.entry("else", Type.ELSE),
        Map.entry("while", Type.WHILE),
        Map.entry("for", Type.FOR),
        Map.entry("in", Type.IN),
        Map.entry("repeat", Type.REPEAT),
        Map.entry("break", Type.BREAK),
        Map.entry("continue", Type.CONTINUE),
        Map.entry("return", Type.RETURN),
        Map.entry("true", Type.TRUE),
        Map.entry("false", Type.FALSE),
        Map.entry("null", Type.NULL)
    );

    private final String src;
    private final List<Token> tokens = new ArrayList<>();
    private int start;
    private int pos;
    private int line = 1;
    private int lineStart;
    private int tokenLine;
    private int tokenColumn;

    public Lexer(String source) {
        this.src = source == null ? "" : source;
    }

    public static List<Token> tokenize(String source) {
        return new Lexer(source).run();
    }

    public List<Token> run() {
        while (!atEnd()) {
            start = pos;
            tokenLine = line;
            tokenColumn = pos - lineStart + 1;
            scan();
        }
        tokens.add(new Token(Type.EOF, "", null, line, pos - lineStart + 1));
        return tokens;
    }

    private void scan() {
        char c = advance();
        switch (c) {
            case '(' -> add(Type.LEFT_PAREN);
            case ')' -> add(Type.RIGHT_PAREN);
            case '{' -> add(Type.LEFT_BRACE);
            case '}' -> add(Type.RIGHT_BRACE);
            case '[' -> add(Type.LEFT_BRACKET);
            case ']' -> add(Type.RIGHT_BRACKET);
            case ',' -> add(Type.COMMA);
            case ';' -> add(Type.SEMICOLON);
            case '.' -> add(Type.DOT);
            case '%' -> add(Type.PERCENT);
            case '+' -> add(match('=') ? Type.PLUS_EQUAL : Type.PLUS);
            case '-' -> add(match('=') ? Type.MINUS_EQUAL : Type.MINUS);
            case '*' -> add(match('=') ? Type.STAR_EQUAL : Type.STAR);
            case '!' -> add(match('=') ? Type.BANG_EQUAL : Type.BANG);
            case '=' -> add(match('=') ? Type.EQUAL_EQUAL : Type.EQUAL);
            case '<' -> add(match('=') ? Type.LESS_EQUAL : Type.LESS);
            case '>' -> add(match('=') ? Type.GREATER_EQUAL : Type.GREATER);
            case '&' -> {
                if (!match('&')) throw error("Expected '&&' (a single '&' is not an operator)");
                add(Type.AND_AND);
            }
            case '|' -> {
                if (!match('|')) throw error("Expected '||' (a single '|' is not an operator)");
                add(Type.OR_OR);
            }
            case '/' -> {
                if (match('/')) {
                    while (!atEnd() && peek() != '\n') pos++;
                } else if (match('*')) {
                    blockComment();
                } else {
                    add(match('=') ? Type.SLASH_EQUAL : Type.SLASH);
                }
            }
            case ' ', '\t', '\r' -> { }
            case '\n' -> newline();
            case '"', '\'' -> string(c);
            default -> {
                if (isDigit(c)) number();
                else if (isIdentStart(c)) identifier();
                else throw error("Unexpected character '" + c + "'");
            }
        }
    }

    private void blockComment() {
        while (!atEnd()) {
            char c = advance();
            if (c == '\n') newline();
            else if (c == '*' && match('/')) return;
        }
        throw error("Unterminated /* comment");
    }

    private void string(char quote) {
        StringBuilder out = new StringBuilder();
        while (!atEnd() && peek() != quote) {
            char c = advance();
            if (c == '\n') throw error("Unterminated string (strings cannot span lines)");
            if (c == '\\') {
                if (atEnd()) break;
                char e = advance();
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\' -> out.append('\\');
                    case '"' -> out.append('"');
                    case '\'' -> out.append('\'');
                    default -> throw error("Unknown escape '\\" + e + "'");
                }
            } else {
                out.append(c);
            }
        }
        if (atEnd()) throw error("Unterminated string");
        pos++; // closing quote
        add(Type.STRING, out.toString());
    }

    private void number() {
        while (isDigit(peek())) pos++;
        if (peek() == '.' && isDigit(peekNext())) {
            pos++;
            while (isDigit(peek())) pos++;
        }
        add(Type.NUMBER, Double.parseDouble(src.substring(start, pos)));
    }

    private void identifier() {
        while (isIdentPart(peek())) pos++;
        String text = src.substring(start, pos);
        add(KEYWORDS.getOrDefault(text, Type.IDENTIFIER));
    }

    private void newline() {
        line++;
        lineStart = pos;
    }

    private void add(Type type) {
        add(type, null);
    }

    private void add(Type type, Object literal) {
        tokens.add(new Token(type, src.substring(start, pos), literal, tokenLine, tokenColumn));
    }

    private ScriptException error(String message) {
        return new ScriptException(message, tokenLine, tokenColumn);
    }

    private boolean match(char expected) {
        if (atEnd() || src.charAt(pos) != expected) return false;
        pos++;
        return true;
    }

    private char advance() { return src.charAt(pos++); }

    private char peek() { return atEnd() ? '\0' : src.charAt(pos); }

    private char peekNext() { return pos + 1 >= src.length() ? '\0' : src.charAt(pos + 1); }

    private boolean atEnd() { return pos >= src.length(); }

    static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    static boolean isIdentStart(char c) { return Character.isLetter(c) || c == '_'; }

    static boolean isIdentPart(char c) { return isIdentStart(c) || isDigit(c); }
}
