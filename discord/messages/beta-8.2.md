@everyone
## DIH Client 5.1-beta.8.2 is out
Fabric, Minecraft 26.2 and 26.3. **Download: https://github.com/ahigherdesire/dih-client/releases/tag/v5.1-beta.8.2**
Pre-release: try it in a singleplayer world first.

**New: build scripts without coding**
- Open **Scripts → + New → + Add step**. Pick actions and fill in simple fields instead of writing code.
- Stack commands, waits, menu clicks, inventory actions, Baritone and saved macros. Put steps inside **If** and **Repeat** blocks.
- Use **When finished / stopped** for cleanup, such as stopping Baritone.
- **Recipes** gives you a starting point: repeated messages, mining with a low-health retreat, or opening a menu and clicking Confirm. Adjust example commands for your server.
- Reorder, copy and remove steps, with undo/redo.
- Prefer writing code? Switch to **Code**. Your existing advanced scripts stay intact.

**Fixes**
- Empty script loops now respond to Stop.
- Inventory transfers handle partial moves and blocked stacks correctly.
- Editing imported block text preserves tabs and carriage returns.

To update, replace your old DIH jar in `mods` with the 8.2 jar for your Minecraft version. The new block UI still needs in-game visual testing. Found a bug or want something new? Use **Report Bug** or **Request Feature** below.
