package dihclient.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScriptEditorModelTest {
    @Test
    void typingAndUndo() {
        ScriptEditorModel m = new ScriptEditorModel();
        for (char c : "let".toCharArray()) m.type(c);
        m.insert(" ");
        for (char c : "x".toCharArray()) m.type(c);
        assertEquals("let x", m.text());
        m.undo();
        assertEquals("let ", m.text());
        m.undo();
        assertEquals("let", m.text());
        m.redo();
        assertEquals("let ", m.text());
    }

    @Test
    void enterAfterBraceIndentsAndSplitsClosingBrace() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("function main() {}");
        m.moveTo(m.text().length() - 1, false);
        m.newline();
        assertEquals("function main() {\n    \n}", m.text());
        assertEquals(1, m.lineOf(m.cursor()));
        assertEquals(4, m.columnOf(m.cursor()));
    }

    @Test
    void closingBraceDedents() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("if (x) {\n        ");
        m.documentEnd(false);
        m.type('}');
        assertEquals("if (x) {\n    }", m.text());
    }

    @Test
    void backspaceRemovesWholeIndentStop() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("        x");
        m.moveTo(8, false);
        m.backspace(false);
        assertEquals("    x", m.text());
    }

    @Test
    void verticalMovementKeepsColumn() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("abcdef\nab\nabcdef");
        m.moveTo(5, false);
        m.down(false);
        assertEquals(m.offsetOf(1, 2), m.cursor());
        m.down(false);
        assertEquals(m.offsetOf(2, 5), m.cursor());
    }

    @Test
    void selectionReplaceAndIndentBlock() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("a\nb\nc");
        m.selectAll();
        m.indent();
        assertEquals("    a\n    b\n    c", m.text());
        m.selectAll();
        m.outdent();
        assertEquals("a\nb\nc", m.text());
        m.selectAll();
        m.insert("z");
        assertEquals("z", m.text());
    }

    @Test
    void toggleComment() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.setText("    wait(1);");
        m.toggleComment();
        assertEquals("    // wait(1);", m.text());
        m.toggleComment();
        assertEquals("    wait(1);", m.text());
    }

    @Test
    void pasteNormalizesTabsAndLineEndings() {
        ScriptEditorModel m = new ScriptEditorModel();
        m.insert("a\r\n\tb");
        assertEquals("a\n    b", m.text());
    }
}
