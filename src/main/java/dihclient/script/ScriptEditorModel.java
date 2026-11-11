package dihclient.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Text, cursor, selection and undo for the Scripts code editor. Positions are character offsets
 * into {@link #text()}; no Minecraft types, so it can be unit-tested.
 */
public final class ScriptEditorModel {
    public static final String INDENT = "    ";
    private static final int MAX_UNDO = 200;

    private record Snapshot(String text, int cursor, int anchor) { }

    private String text = "";
    private int cursor;
    private int anchor; // == cursor when nothing is selected
    private int preferredColumn = -1;
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private boolean coalesceTyping;
    private long revision;

    public String text() { return text; }

    public int cursor() { return cursor; }

    public int anchor() { return anchor; }

    /** Bumped on every text change (for caches such as highlighting and live checks). */
    public long revision() { return revision; }

    public void setText(String newText) {
        text = normalize(newText);
        cursor = anchor = 0;
        undo.clear();
        redo.clear();
        coalesceTyping = false;
        revision++;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n').replace("\t", INDENT);
    }

    // ---------------------------------------------------------------- lines

    public List<String> lines() {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (true) {
            int nl = text.indexOf('\n', start);
            if (nl < 0) {
                out.add(text.substring(start));
                return out;
            }
            out.add(text.substring(start, nl));
            start = nl + 1;
        }
    }

    public int lineOf(int offset) {
        int line = 0;
        for (int i = 0; i < offset && i < text.length(); i++) if (text.charAt(i) == '\n') line++;
        return line;
    }

    public int lineStart(int line) {
        int offset = 0;
        for (int l = 0; l < line; l++) {
            int nl = text.indexOf('\n', offset);
            if (nl < 0) return text.length();
            offset = nl + 1;
        }
        return offset;
    }

    public int lineEnd(int line) {
        int nl = text.indexOf('\n', lineStart(line));
        return nl < 0 ? text.length() : nl;
    }

    public int columnOf(int offset) {
        return offset - lineStart(lineOf(offset));
    }

    public int offsetOf(int line, int column) {
        int lines = lineCount();
        line = Math.max(0, Math.min(lines - 1, line));
        int start = lineStart(line);
        return Math.min(start + Math.max(0, column), lineEnd(line));
    }

    public int lineCount() {
        int n = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') n++;
        return n;
    }

    // ---------------------------------------------------------------- selection

    public boolean hasSelection() { return cursor != anchor; }

    public int selectionStart() { return Math.min(cursor, anchor); }

    public int selectionEnd() { return Math.max(cursor, anchor); }

    public String selectedText() { return text.substring(selectionStart(), selectionEnd()); }

    public void selectAll() {
        anchor = 0;
        cursor = text.length();
        preferredColumn = -1;
    }

    /** Moves the cursor; with {@code extend} the selection anchor stays put. */
    public void moveTo(int offset, boolean extend) {
        cursor = Math.max(0, Math.min(text.length(), offset));
        if (!extend) anchor = cursor;
        preferredColumn = -1;
        coalesceTyping = false;
    }

    public void left(boolean extend, boolean word) {
        if (hasSelection() && !extend) {
            moveTo(selectionStart(), false);
            return;
        }
        moveTo(word ? wordBoundary(cursor, -1) : cursor - 1, extend);
    }

    public void right(boolean extend, boolean word) {
        if (hasSelection() && !extend) {
            moveTo(selectionEnd(), false);
            return;
        }
        moveTo(word ? wordBoundary(cursor, 1) : cursor + 1, extend);
    }

    public void up(boolean extend) { vertical(-1, extend); }

    public void down(boolean extend) { vertical(1, extend); }

    public void verticalBy(int lines, boolean extend) { vertical(lines, extend); }

    private void vertical(int delta, boolean extend) {
        int line = lineOf(cursor);
        int col = preferredColumn >= 0 ? preferredColumn : columnOf(cursor);
        int target = line + delta;
        int newOffset;
        if (target < 0) newOffset = 0;
        else if (target >= lineCount()) newOffset = text.length();
        else newOffset = offsetOf(target, col);
        cursor = newOffset;
        if (!extend) anchor = cursor;
        preferredColumn = col;
        coalesceTyping = false;
    }

    /** Home: first non-space character of the line, then column 0. */
    public void home(boolean extend) {
        int line = lineOf(cursor);
        int start = lineStart(line);
        int firstText = start;
        while (firstText < text.length() && text.charAt(firstText) == ' ') firstText++;
        moveTo(cursor == firstText ? start : firstText, extend);
    }

    public void end(boolean extend) {
        moveTo(lineEnd(lineOf(cursor)), extend);
    }

    public void documentStart(boolean extend) { moveTo(0, extend); }

    public void documentEnd(boolean extend) { moveTo(text.length(), extend); }

    private int wordBoundary(int from, int dir) {
        int i = from;
        if (dir < 0) {
            while (i > 0 && !isWord(text.charAt(i - 1))) i--;
            while (i > 0 && isWord(text.charAt(i - 1))) i--;
        } else {
            while (i < text.length() && !isWord(text.charAt(i))) i++;
            while (i < text.length() && isWord(text.charAt(i))) i++;
        }
        return i;
    }

    private static boolean isWord(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** Selects the word around {@code offset} (double-click). */
    public void selectWord(int offset) {
        int s = Math.max(0, Math.min(text.length(), offset));
        int e = s;
        while (s > 0 && isWord(text.charAt(s - 1))) s--;
        while (e < text.length() && isWord(text.charAt(e))) e++;
        anchor = s;
        cursor = e;
        preferredColumn = -1;
    }

    // ---------------------------------------------------------------- editing

    /** Inserts typed or pasted text in place of the selection. */
    public void insert(String raw) {
        String s = normalize(raw);
        if (s.isEmpty() && !hasSelection()) return;
        boolean single = s.length() == 1 && !hasSelection() && s.charAt(0) != '\n' && s.charAt(0) != ' ';
        snapshot(single);
        replaceSelection(s);
        coalesceTyping = single;
    }

    /** Typing one character, with editor niceties: closing-brace dedent. */
    public void type(char c) {
        if (c == '}' && !hasSelection()) {
            int start = lineStart(lineOf(cursor));
            String before = text.substring(start, cursor);
            if (!before.isEmpty() && before.isBlank() && before.length() >= INDENT.length()) {
                snapshot(false);
                text = text.substring(0, cursor - INDENT.length()) + text.substring(cursor);
                cursor -= INDENT.length();
                anchor = cursor;
                revision++;
                replaceSelection("}");
                coalesceTyping = false;
                return;
            }
        }
        insert(String.valueOf(c));
    }

    /** Enter: keeps the current indentation, adds a level after '{'. */
    public void newline() {
        int start = lineStart(lineOf(cursor));
        String line = text.substring(start, cursor);
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') spaces++;
        String indent = " ".repeat(spaces);
        boolean opensBlock = line.stripTrailing().endsWith("{");
        boolean closesNext = cursor < text.length() && text.charAt(cursor) == '}';
        snapshot(false);
        if (opensBlock && closesNext) {
            replaceSelection("\n" + indent + INDENT + "\n" + indent);
            cursor = anchor = cursor - 1 - indent.length();
        } else {
            replaceSelection("\n" + indent + (opensBlock ? INDENT : ""));
        }
        coalesceTyping = false;
    }

    /** Tab: indent (every selected line when a selection spans lines). */
    public void indent() {
        if (hasSelection() && lineOf(selectionStart()) != lineOf(selectionEnd())) {
            shiftLines(true);
            return;
        }
        int col = columnOf(cursor);
        insert(" ".repeat(INDENT.length() - col % INDENT.length()));
    }

    /** Shift+Tab: remove one level of indentation from the current/selected lines. */
    public void outdent() {
        shiftLines(false);
    }

    private void shiftLines(boolean in) {
        int first = lineOf(selectionStart());
        int last = lineOf(selectionEnd());
        if (hasSelection() && last > first && columnOf(selectionEnd()) == 0) last--;
        snapshot(false);
        StringBuilder sb = new StringBuilder();
        List<String> lines = lines();
        int cursorShift = 0;
        int anchorShift = 0;
        int cursorLine = lineOf(cursor);
        int anchorLine = lineOf(anchor);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (i >= first && i <= last) {
                int delta;
                if (in) {
                    l = INDENT + l;
                    delta = INDENT.length();
                } else {
                    int remove = 0;
                    while (remove < INDENT.length() && remove < l.length() && l.charAt(remove) == ' ') remove++;
                    l = l.substring(remove);
                    delta = -remove;
                }
                if (i <= cursorLine) cursorShift += delta;
                if (i <= anchorLine) anchorShift += delta;
            }
            if (i > 0) sb.append('\n');
            sb.append(l);
        }
        text = sb.toString();
        cursor = Math.max(lineStart(cursorLine), Math.min(text.length(), cursor + cursorShift));
        anchor = Math.max(lineStart(anchorLine), Math.min(text.length(), anchor + anchorShift));
        revision++;
        coalesceTyping = false;
    }

    public void backspace(boolean word) {
        if (hasSelection()) {
            snapshot(false);
            replaceSelection("");
            return;
        }
        if (cursor == 0) return;
        int from;
        if (word) {
            from = wordBoundary(cursor, -1);
        } else {
            // inside leading indentation, delete back to the previous indent stop
            int start = lineStart(lineOf(cursor));
            String before = text.substring(start, cursor);
            if (!before.isEmpty() && before.isBlank()) {
                int col = before.length();
                from = cursor - (col % INDENT.length() == 0 ? INDENT.length() : col % INDENT.length());
            } else {
                from = cursor - 1;
            }
        }
        snapshot(false);
        anchor = from;
        replaceSelection("");
    }

    public void delete(boolean word) {
        if (hasSelection()) {
            snapshot(false);
            replaceSelection("");
            return;
        }
        if (cursor >= text.length()) return;
        int to = word ? wordBoundary(cursor, 1) : cursor + 1;
        snapshot(false);
        anchor = to;
        replaceSelection("");
    }

    /** Cut: removes and returns the selection (the whole line when nothing is selected). */
    public String cut() {
        if (!hasSelection()) {
            int line = lineOf(cursor);
            anchor = lineStart(line);
            cursor = Math.min(text.length(), lineEnd(line) + 1);
        }
        String out = selectedText();
        snapshot(false);
        replaceSelection("");
        return out;
    }

    /** Copy: the selection, or the whole current line when nothing is selected. */
    public String copy() {
        if (hasSelection()) return selectedText();
        int line = lineOf(cursor);
        return text.substring(lineStart(line), lineEnd(line)) + "\n";
    }

    /** Toggles {@code //} on the current/selected lines. */
    public void toggleComment() {
        int first = lineOf(selectionStart());
        int last = lineOf(selectionEnd());
        if (hasSelection() && last > first && columnOf(selectionEnd()) == 0) last--;
        List<String> lines = lines();
        boolean allCommented = true;
        for (int i = first; i <= last; i++) {
            if (!lines.get(i).isBlank() && !lines.get(i).stripLeading().startsWith("//")) allCommented = false;
        }
        snapshot(false);
        int cursorLine = lineOf(cursor);
        int cursorCol = columnOf(cursor);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            if (i >= first && i <= last && !l.isBlank()) {
                int indent = l.length() - l.stripLeading().length();
                if (allCommented) {
                    String rest = l.substring(indent + 2);
                    if (rest.startsWith(" ")) rest = rest.substring(1);
                    l = l.substring(0, indent) + rest;
                } else {
                    l = l.substring(0, indent) + "// " + l.substring(indent);
                }
            }
            if (i > 0) sb.append('\n');
            sb.append(l);
        }
        text = sb.toString();
        revision++;
        cursor = anchor = offsetOf(cursorLine, cursorCol + (allCommented ? -3 : 3));
        coalesceTyping = false;
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(new Snapshot(text, cursor, anchor));
        restore(undo.pop());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(new Snapshot(text, cursor, anchor));
        restore(redo.pop());
        return true;
    }

    private void restore(Snapshot s) {
        text = s.text();
        cursor = Math.min(s.cursor(), text.length());
        anchor = Math.min(s.anchor(), text.length());
        revision++;
        coalesceTyping = false;
        preferredColumn = -1;
    }

    private void snapshot(boolean typing) {
        if (typing && coalesceTyping) return;
        undo.push(new Snapshot(text, cursor, anchor));
        while (undo.size() > MAX_UNDO) undo.removeLast();
        redo.clear();
    }

    private void replaceSelection(String s) {
        int start = selectionStart();
        int end = selectionEnd();
        text = text.substring(0, start) + s + text.substring(end);
        cursor = anchor = start + s.length();
        preferredColumn = -1;
        revision++;
    }
}
