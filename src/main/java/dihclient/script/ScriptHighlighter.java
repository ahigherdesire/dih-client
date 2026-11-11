package dihclient.script;

import dihclient.script.lang.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Line-by-line syntax colouring for the editor. Lenient: never throws on half-typed code. */
public final class ScriptHighlighter {
    public enum Kind { TEXT, KEYWORD, LITERAL, STRING, NUMBER, COMMENT, FUNCTION, BUILTIN, CONSTANT }

    public record Span(int start, int end, Kind kind) { }

    private ScriptHighlighter() {
    }

    /** Spans for every line; {@code isBuiltin} decides which called names are library functions. */
    public static List<List<Span>> highlight(List<String> lines, Predicate<String> isBuiltin) {
        List<List<Span>> out = new ArrayList<>(lines.size());
        boolean inBlockComment = false;
        for (String line : lines) {
            List<Span> spans = new ArrayList<>();
            int i = 0;
            int n = line.length();
            while (i < n) {
                int start = i;
                char c = line.charAt(i);
                if (inBlockComment) {
                    int close = line.indexOf("*/", i);
                    i = close < 0 ? n : close + 2;
                    if (close >= 0) inBlockComment = false;
                    spans.add(new Span(start, i, Kind.COMMENT));
                } else if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/') {
                    spans.add(new Span(i, n, Kind.COMMENT));
                    i = n;
                } else if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*') {
                    inBlockComment = true;
                    i += 2;
                    int close = line.indexOf("*/", i);
                    if (close >= 0) {
                        inBlockComment = false;
                        i = close + 2;
                    } else {
                        i = n;
                    }
                    spans.add(new Span(start, i, Kind.COMMENT));
                } else if (c == '"' || c == '\'') {
                    i++;
                    while (i < n && line.charAt(i) != c) i += line.charAt(i) == '\\' ? 2 : 1;
                    i = Math.min(n, i + 1);
                    spans.add(new Span(start, i, Kind.STRING));
                } else if (Character.isDigit(c)) {
                    while (i < n && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.')) i++;
                    spans.add(new Span(start, i, Kind.NUMBER));
                } else if (Character.isLetter(c) || c == '_') {
                    while (i < n && (Character.isLetterOrDigit(line.charAt(i)) || line.charAt(i) == '_')) i++;
                    String word = line.substring(start, i);
                    int next = i;
                    while (next < n && line.charAt(next) == ' ') next++;
                    boolean call = next < n && line.charAt(next) == '(';
                    Kind kind;
                    if (word.equals("true") || word.equals("false") || word.equals("null")) kind = Kind.LITERAL;
                    else if (Lexer.KEYWORDS.containsKey(word)) kind = Kind.KEYWORD;
                    else if (call) kind = isBuiltin.test(word) ? Kind.BUILTIN : Kind.FUNCTION;
                    else if (word.length() > 1 && word.equals(word.toUpperCase()) && word.chars().anyMatch(Character::isLetter)) kind = Kind.CONSTANT;
                    else kind = Kind.TEXT;
                    spans.add(new Span(start, i, kind));
                } else {
                    i++;
                    spans.add(new Span(start, i, Kind.TEXT));
                }
            }
            out.add(spans);
        }
        return out;
    }
}
