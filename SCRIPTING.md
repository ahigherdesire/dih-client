# DihScript

DihScript is DIH Client's scripting language for automation you write yourself: a mining loop that retreats home
when it's hurt, a restock routine, a menu-clicking sequence. It looks like C or JavaScript, runs on its own thread so
it never freezes the game, and talks to Minecraft through built-in functions.

Scripts are text files in `config/dih/scripts/<name>.dscript`. Write them in the **Scripts** editor (module menu →
Scripts, or `.scripts edit`) or in any text editor, then:

```
.scripts run gold_miner
.scripts stop gold_miner
```

[COMMANDS.md](COMMANDS.md#scripts) lists every `.scripts` subcommand. The first time the folder is created DIH
writes two examples: `hello` and `gold_miner`.

## Build a script without coding

Open **Scripts**, click **+ New**, type a name, and press Enter. New scripts open in **Blocks**.
Choose **+ Add step**, search for an action, and click it. Select a step to edit its value below;
press **Apply** or Enter to keep the value. Escape cancels a field edit.

- Steps run from top to bottom. **Up** and **Down** reorder steps within their group.
- **Copy** duplicates a step and everything inside it; **Remove** deletes the selected step and its children.
- **Repeat** and **If** blocks contain other steps. Use **+ Add step inside** to put actions in that group.
- **When started** contains the main routine. **When finished / stopped** contains cleanup actions,
  such as stopping Baritone, which run when the routine finishes, fails, or is stopped.
- **Recipes** inserts an example without replacing your current steps. The mining recipe uses `/home`;
  change that command to one supported by your server before running it.
- **Save** saves the script; **Run** saves and starts it. **Stop** ends a running script.
- Ctrl+Z / Ctrl+Y undo and redo block edits when a value field is not focused. In a field they undo typing.

Wait blocks use seconds, including decimals. Health uses points: 2 points is one heart.
The action picker includes menu clicks, inventory transfers, Baritone, saved macros, conditions and loops.
Commands, item IDs and waypoint names are ordinary text fields; quotes and punctuation are handled for you.

Click **Blocks | Code** to edit the same script as code. Compatible scripts can switch back to Blocks.
If the script uses advanced expressions, variables, custom functions or other unsupported constructs,
the editor explains that it must stay in Code; it does not replace or discard your code.
Switching views alone leaves the source unchanged. Editing blocks regenerates formatting and comments;
undo restores the previous source. Block scripts are normal `.dscript` files and use the same runner.

## A complete script

```js
const LOW_HEALTH  = 5;
const SAFE_RADIUS = 48;

function main() {
    baritone("mine gold_ore");
    while (true) {
        if (health() <= LOW_HEALTH || playerNearby(SAFE_RADIUS)) {
            stopBaritone();
            command("/home");
            return;
        }
        wait(5);
    }
}

function onStop() {
    stopBaritone();
}
```

`main()` runs when the script starts. When it returns, the script is finished. `onStop()` is optional and runs
whenever the script ends: it finished, you stopped it, or it hit an error. Use it to clean up.

## The language

### Values

| Type | Examples |
|---|---|
| number | `20`, `0.5`, `-3` |
| string | `"gold_ore"`, `'Badlands'`, `"line\n"`, escapes `\n \r \t \" \' \\` |
| boolean | `true`, `false` |
| list | `[1, 2, "three"]`, `items[0]`, `items[-1]` (last) |
| null | `null` |

`false`, `null`, `0`, `""` and `[]` count as false in conditions; everything else is true. `+` joins text when either
side is a string: `"Health: " + health()`.

### Variables

```js
let count = 0;          // changeable
const ORE = "gold_ore"; // fixed
count += 1;             // also -=, *=, /=
```

Declare a variable with `let` or `const` before using it. Top-level `let`/`const` are globals, visible in every
function. Variables declared inside `{ }` exist only inside those braces.

### Operators

| Kind | Operators |
|---|---|
| Arithmetic | `+ - * / %` |
| Comparison | `== != < <= > >=` |
| Logic | `&& \|\| !` (short-circuit) |

### Control flow

Braces are always required.

```js
if (food() < 10) {
    eat();
} else if (health() < 10) {
    waitSeconds(5);
} else {
    print("fine");
}

while (!inventoryFull()) { wait(10); }

repeat (3) { clickItem("Confirm"); }

for (let name in playersNearby(64)) { print(name); }
for (let i in range(5)) { print(i); }   // 0 1 2 3 4

break;      // leave the loop
continue;   // next iteration
```

### Functions

```js
function distanceTo(x2, z2) {
    let dx = x() - x2;
    let dz = z() - z2;
    return sqrt(dx * dx + dz * dz);
}
```

Functions are declared at the top level and can be called before they are defined. Missing arguments are `null`.

### Comments

`// to the end of the line` and `/* across lines */`.

### Timing and safety

The game runs 20 ticks a second. `wait(ticks)` and `waitSeconds(s)` pause the script without pausing the game. A
loop that never waits is made to yield every 20 000 steps, so a mistake can't lock up the client, but put a `wait`
in every long-running loop anyway.

Before a script starts, DIH checks it for syntax errors, unknown functions and wrong argument counts. The editor
runs the same check as you type and marks problem lines in red. Errors while running stop the script and print the
line number in chat.

## Built-in functions

`.scripts functions <category>` lists these in game, and the **?** button in the editor shows them with
descriptions; clicking one inserts it. Item and block names accept ids (`raw_gold`, `minecraft:raw_gold`) or the
name shown in game (`Raw Gold`).

### Control

| Function | Does |
|---|---|
| `print(value, ...)` | Show a message in chat. |
| `wait(ticks)` | Pause; 20 ticks = 1 second. |
| `waitSeconds(seconds)` | Pause; decimals allowed. |
| `stop()` | End the script now (`onStop()` still runs). |
| `error(message)` | Stop with an error. |
| `time()` | Milliseconds since 1970, for measuring durations. |

### Player

| Function | Does |
|---|---|
| `inWorld()` | True while in a world or server. |
| `health()`, `maxHealth()` | Health; 20 = full, 2 per heart. |
| `food()` | Hunger bar; 20 = full. |
| `x()`, `y()`, `z()` | Your position. |
| `dimension()` | `"overworld"`, `"the_nether"` or `"the_end"`. |
| `playerName()` | Your username. |
| `playerNearby(radius)` | True if another player is within `radius` (default 32). Friends, teammates and bots don't count. |
| `nearestPlayer(radius)` | Closest other player's name, or `null`. |
| `playersNearby(radius)` | Names of other players in range, closest first. |
| `eat()` | Eat one food item from the hotbar. False if there's nothing to eat. |
| `eatUntilFull()` | Eat until the hunger bar is full. False if food ran out first. |

### Chat

| Function | Does |
|---|---|
| `command(text)` | Run a command as if typed: `"/home"` goes to the server, `"#mine gold_ore"` to Baritone, `".toggle flee"` to DIH. |
| `say(text)` | Send a plain chat message. |

### Baritone

| Function | Does |
|---|---|
| `baritone(command)` | Run a Baritone command without the `#`: `baritone("mine gold_ore")`. |
| `stopBaritone()` | Cancel whatever Baritone is doing. |
| `baritoneBusy()` | True while Baritone is mining, pathing or farming. |
| `waitForBaritone(timeoutTicks)` | Wait until Baritone is idle. False on timeout (default: none). |
| `goto("chest")`, `goto(x, y, z)` | Walk there with Baritone and wait until it arrives. |

### Inventory

| Function | Does |
|---|---|
| `count(item)` | How many you carry. |
| `hasItem(item)` | True if you carry at least one. |
| `freeSlots()` | Empty main-inventory slots (0-36). |
| `inventoryFull()` | True when no slot is empty. |
| `heldItem()` | Id of the main-hand item (`"air"` if empty). |
| `selectSlot(slot)` | Select hotbar slot 1-9. |
| `selectItem(item)` | Select the hotbar slot holding `item`. False if it isn't in the hotbar. |

### GUI

| Function | Does |
|---|---|
| `guiOpen()` | True while a chest or other container GUI is open. |
| `guiTitle()` | Title of the open screen. |
| `waitForGui(timeoutTicks, title)` | Wait for a container GUI (optionally one whose title contains `title`). False on timeout (default 100). |
| `waitForItem(name, timeoutTicks)` | Wait until an item with this name shows in the open GUI. False on timeout (default 100). |
| `clickItem(name, button)` | Click the GUI item with this name or id. `button` 0 = left (default), 1 = right. |
| `clickSlot(slot, button, shift)` | Click a GUI slot by number (see below). `button` 0 = left, 1 = right; `shift` true shift-clicks. |
| `guiSize()` | Number of the GUI's own slots: 27 for a chest or most server menus, 54 for a double chest, 0 if none. |
| `slotItem(slot)`, `slotName(slot)`, `slotCount(slot)` | What's in a GUI slot: item id, shown name, count (`null`/`0` when empty). |
| `findSlot(item)` | First of the GUI's own slots holding `item`, or -1. |
| `inventorySlot(n)` | GUI slot number of your inventory slot `n`, for `clickSlot`. |
| `clickInventory(n, button, shift)` | Click your inventory slot `n` directly. |
| `deposit(item)` | Shift-click every stack of `item` into the open container. `"*"` moves everything. Returns items moved. |
| `withdraw(item, amount)` | Shift-click stacks of `item` out of the container until at least `amount` (default all). Returns items moved. |
| `fillSlots(item, count)` | Take `item` from the open container and put exactly 1 in each of `count` empty inventory slots. Returns slots filled. |

**Slot numbers.** GUI slots count from 0 at the top-left of the menu, left to right, row by row: a 27-slot server
menu is 0-26 (row 2 starts at 9, row 3 at 18), a double chest 0-53. Your own inventory follows right after
`guiSize()`. For your inventory there's a simpler numbering: 1-9 is the hotbar (same as the number keys) and 10-36
the three rows above it, top-left first.

```js
command("/menu");
waitForGui(100);
clickSlot(13);                 // middle of a 27-slot menu
if (slotName(22) == "Confirm") { clickSlot(22); }
for (let i in range(guiSize())) {
    if (slotItem(i) == "diamond") { clickSlot(i, 0, true); }   // shift-click every diamond out
}
```
| `closeGui()` | Close the open screen. |

### World

| Function | Does |
|---|---|
| `findBlock(block, radius)` | Closest block of that type within `radius` (default 16, max 32) as `[x, y, z]`, or `null`. |
| `lookAt(x, y, z)` | Turn to face a position. |
| `openNearest(block, radius)` | Look at and right-click the closest such block within reach, then wait for its GUI. True if one opened. |
| `macro(name)` | Run one of your saved DIH macros and wait for it to finish. |

### Math, text and lists

| Function | Does |
|---|---|
| `abs floor ceil round sqrt` | Usual math. |
| `min(a, b, ...)`, `max(a, b, ...)` | Smallest / largest. |
| `random(min, max)` | Random whole number, both ends included. |
| `str(v)`, `num(text)`, `type(v)` | Convert / inspect. `num` returns `null` for non-numbers. |
| `lower upper trim` | Text case and whitespace. |
| `startsWith endsWith contains replace` | Text tests and edits. `contains` also works on lists. |
| `split(text, sep)`, `join(list, sep)` | Text ↔ list. |
| `len(v)` | Length of a list or text. |
| `push(list, v)`, `pop(list)`, `removeAt(list, i)` | Change lists. |
| `range(end)`, `range(start, end)` | `[start, ..., end - 1]`. |

## Editor keys

| Keys | Does |
|---|---|
| Ctrl+S | Save (switching scripts, running and closing the window also save). |
| Ctrl+Enter | Run, or stop if running. |
| Ctrl+Z / Ctrl+Y (Ctrl+Shift+Z) | Undo / redo. |
| Ctrl+/ | Comment or uncomment the line or selection. |
| Tab / Shift+Tab | Indent / outdent (the whole selection when it spans lines). |
| Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V | Select all, copy, cut (the whole line with no selection), paste. |
| Ctrl+←/→, Ctrl+Backspace | Move / delete by word. |
| Double-click | Select a word. |
| Esc | Leave the editor so game keys work again. |
