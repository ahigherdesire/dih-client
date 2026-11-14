# Stacked script editor

Make scripts approachable without coding while retaining the existing text editor and runner.

New scripts start in Blocks. Actions use plain labels and typed fields; control blocks contain visibly indented children. Users can insert, reorder, duplicate, remove, undo and redo blocks. A separate finish/stop section supports cleanup. Starter recipes insert a useful sequence without replacing existing work.

Blocks generate normal DihScript using the existing parser, checker and runtime. Text values are escaped and numeric fields validated. Switching to Code exposes the generated source. Switching back parses only constructs that Blocks can faithfully represent. Unsupported code remains untouched in Code mode with an explanation. Merely opening or switching views must not rewrite source. The first block edit regenerates formatting and comments; undo restores the previous text.

Implementation: add a Minecraft-independent block model and conversion tests; build a panel using existing theme, buttons and text editing model; integrate mode selection, save/run and new-script defaults; document controls; run the script tests and full Fabric suite.

Validation covers nested execution, cleanup, escaped strings, invalid numbers, every block's generated syntax, starter recipes and rejection of unsupported source without data loss. Check the compiled native UI and use a live client when available.
