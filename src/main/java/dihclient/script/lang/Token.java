package dihclient.script.lang;

/** One lexical token of a DihScript source file. */
public record Token(Type type, String text, Object literal, int line, int column) {
    public enum Type {
        // punctuation
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, LEFT_BRACKET, RIGHT_BRACKET,
        COMMA, SEMICOLON, DOT,
        // operators
        PLUS, MINUS, STAR, SLASH, PERCENT,
        BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL,
        GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
        AND_AND, OR_OR,
        PLUS_EQUAL, MINUS_EQUAL, STAR_EQUAL, SLASH_EQUAL,
        // literals
        IDENTIFIER, STRING, NUMBER,
        // keywords
        FUNCTION, LET, CONST, IF, ELSE, WHILE, FOR, IN, REPEAT, BREAK, CONTINUE, RETURN,
        TRUE, FALSE, NULL,
        EOF
    }

    @Override
    public String toString() {
        return type == Type.EOF ? "end of file" : "'" + text + "'";
    }
}
