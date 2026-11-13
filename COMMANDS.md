# DIH Client commands

Every chat command in DIH Client and how to type it, the client's menus, and guides. Written for 5.1 beta 8.3, on
Minecraft 26.2 and 26.3, Fabric Loader 0.19.3+, Java 25. [MODULES.md](MODULES.md) lists every module and setting, and
[BETA-CHANGES.md](BETA-CHANGES.md) what changed in each beta. The website shows all three together at `/docs`.

## Basics

### Two prefixes

DIH has two command systems, both typed in normal chat.

| Prefix | What it runs | List them with |
|---|---|---|
| `.` | DIH client commands. They never reach the server. | `.commands` or `.help` |
| `#` | The built-in Baritone pathfinder and the AI. | `#help` |

The `.` prefix is `%` instead when Meteor Client is also installed, because Meteor already uses `.`. Change it
with `.prefix`; the choices are `. % - _ * # @ & =`. The `#` prefix is the Baritone `prefix` setting.

### Getting help in game

Type a command with no arguments to see its usage. `.help <command>` shows a client command's description and
aliases, and `#help <command>` prints the full help of a `#` command. Tab completion works on every argument:
modules, macros, item and block ids, packet classes, slot names, players and waypoints.

### Turning DIH off

`^toggledih`, typed in chat exactly like that, turns the whole client on or off. While panic hide has locked the
client, every DIH command is swallowed under any prefix, so nothing reaches the server.

### Notation

| Written | Means |
|---|---|
| `<arg>` | Required. |
| `[arg]` | Optional. |
| `a\|b` | Pick one. |
| `…` | Any number of words. |
| `on\|off\|toggle` | Also accepts `enable`/`true`/`1` and `disable`/`false`/`0`. Leaving it out toggles. |

Examples use the default `.` prefix. Swap in yours if you changed it.

### Lite builds and addons

Lite builds leave out `setting`, `irc`, `matchmaking`, `multi`, `toggle`, `modules`, `oresim`, `ghostblock`,
`friend`, `waypoints`, `gamemode`, `fakegm`, `xcarry`, `rv` and `namescrape`, and in the beta also `schem`,
`invsee`, `tools` and `ai`. Addons can add their own `.` commands; one whose name is already taken shows up as
`<addon>:<name>`.

### Beta commands

Commands marked **Beta** are in the 5.1 beta builds and not in 5.0.1 stable. The beta download is on the website
and on [GitHub releases](https://github.com/ahigherdesire/dih-client/releases). Everything else works in both.

### Same name, different command

The two systems are separate, so these pairs do different things.

| Client | Pathfinder |
|---|---|
| `.wp` saves DIH waypoints, which `.tp` can use. | `#wp` manages Baritone's tagged waypoints. |
| `.set` changes a module setting. | `#set` changes a Baritone setting. |
| `.gui` saves, desyncs and reloads container screens. | `#gui` is `#menu`, which reads and clicks plugin GUIs. |
| `.help` lists client commands. | `#help` lists pathfinder commands. |
| `.ai` (beta) gives the AI an objective it works through with its tools. | `#ai` sets up the chat AI that plays through Baritone commands. |

Two `#` aliases also point somewhere other than their help text says. `#flee` opens the Flee trigger system, so
use `#runaway` or `#escape` to run away. `#s` opens `#sel`, so use `#search` or `#blocks` to search.

## Using the client

### Install the beta

Install Fabric Loader 0.19.3+ and Fabric API, with Java 25. Put the DIH jar for your Minecraft version in
`.minecraft/mods`, plus JourneyMap if you want the seed map and heatmap on a map. The jar is on the website and on
[GitHub releases](https://github.com/ahigherdesire/dih-client/releases). Betas are Fabric only; try each one in a
singleplayer world first.

### The module menu

Press Right Shift to open it. Click a module to turn it on, and right-click it for its settings. It's one window: a
category rail on the left (All, Enabled, Combat, Movement, Player, Misc, Render), the module list, and the selected
module's settings side by side. Search finds modules across every category, and the arrow keys and Enter work in the
list. Settings come in groups, and a setting that only matters when another one is on stays hidden until you turn that
one on. The old floating category windows are still a setting away. Macros and Settings are in the Main Menu window.

### Find anything with Ctrl+K

Ctrl+K opens a search box over modules, settings, `.` and `#` commands, macros and store addons. Type a few letters,
move with the arrow keys and press Enter: it toggles, opens or runs what you picked. Esc goes back to where you were.
The shortcut can be changed in Mod Menu, under Command Palette.

### Windows and text size

DIH windows remember where you left them, and you can pin one to keep it on screen in game. Text Size goes from 80 to
150%. A short tour runs the first time you start the client, and the **Replay tour** button in Settings shows it again.

### The addon store

The store lists community addons. Every listing is reviewed before it goes live, its source is public, and DIH checks
each download's SHA-256 before installing it. A listing means a maintainer read it, not that it's risk-free: addons run
with the same access as the client. Open the store any of these ways:

- Ctrl+K, then type `addons`. Store addons can be found by name too.
- `.addons` in chat.
- The **Addons** button in the module menu.
- **ADDON STORE** on the title screen.
- DIH's settings in Mod Menu.

Click a card, click **Install** twice, and restart Minecraft. **Uninstall** works the same way. **My Addons** lists what
you have. Three addons are built into the jar and off by default, and need no internet: Coords HUD, Death Point and
Session Stats. Turn them on in Addons.

## Client commands

### Help & info

#### .help

Show every client command, or the description and aliases of one.

```
.help
.help macro
```

#### .commands

Aliases: `.cmds`

List every command name on a few lines, with the current prefix.

#### .modules

Aliases: `.features`

List every installed module, built-in and addon.

#### .oresim

Explain why OreSim (the Xray module's ore predictor) is or isn't drawing ores.

It reports whether Xray is on, its mode and style, the seed in use or why it's missing, where that seed is
saved, how many ore families are selected, the local worldgen status, chunks simulated, and how many nearby
real ores matched a prediction.

### Modules & settings

#### .toggle

Aliases: `.t`

Turn a module on, off, or flip it.

```
.toggle <module> [on|off|toggle]
```

```
.toggle killaura
.t scaffold on
```

#### .setting

Aliases: `.set`

Read or change any module setting without opening the module menu.

| Usage | Effect |
|---|---|
| `.setting <module>` | List every setting with its value. |
| `.setting <module> <key>` | Show one setting. |
| `.setting <module> <key> <value…>` | Set it. `reset` or `default` restores the default. |

Write module names that contain spaces with `-`, such as `storage-esp`.

```
.set killaura range 4.2
.set killaura range reset
```

#### .friend

Aliases: `.team`

Manage your friends list. Combat modules leave friends alone, and ESP and nametags can mark them.

| Usage | Effect |
|---|---|
| `.friend` | Show the list. |
| `.friend add <name>` | Add a player. Tab completes players on the server. |
| `.friend remove <name>` | Remove a player. |
| `.friend clear` | Empty the list. |

#### .ghostblock

Aliases: `.gb`

Place blocks that only you can see.

| Usage | Effect |
|---|---|
| `.gb toggle` | Turn the GhostBlock module on or off. |
| `.gb reset` | Remove every ghost block and show the real blocks again. |
| `.gb creative [on\|off\|toggle]` | Fake creative mode: everything you place becomes a ghost block. Also turns the module on. |
| `.gb block` | Show the armed block. |
| `.gb block <id>` | Arm a block to place, such as `diamond_block`. Also turns the module on. |
| `.gb block off` | Disarm it (`none` and `reset` work too), so held blocks place ghosts again. |

### Keybinds & prefix

#### .bind

Bind a key to any chat line: a client command, a `#` command or plain chat.

```
.bind <key> <command…>
.bind clear <key>
```

```
.bind G .macro myMacro
.bind H #goto 0 0
.bind clear G
```

Right Shift (module menu) and V (reload the stored GUI) are set in the config, not with `.bind`. The packet
queue keys (flush, clear, toggle send, toggle delay, toggle logger) start unbound.

#### .binds

List every command keybind.

#### .prefix

Show, change or reset the client command prefix.

```
.prefix
.prefix -
.prefix reset
```

The choices are `. % - _ * # @ & =`. `reset` goes back to `.`, or `%` with Meteor installed. Picking `.` while
Meteor is installed keeps `%` and warns you.

### Macros

#### .macro

Run macros, loop them, or build them from chat. Macros are saved in `config/dih/dih_macros.nbt`.

##### Run

| Usage | Effect |
|---|---|
| `.macro <name>` | Run once. |
| `.macro <name> <times>` | Run up to 100 000 times, each run starting when the last one ends. |
| `.macro <name> <times> <delayTicks>` | Same, waiting `delayTicks` between runs (20 ticks is 1 second, up to 72 000). |
| `.macro stop` | Stop every macro and loop. |
| `.macro stop <name>` | Stop one macro and its loop. |
| `.macro clear` | Cancel queued loops and let the current run finish. |

##### Build from chat

| Usage | Effect |
|---|---|
| `.macro new <name>` | Create an empty macro. |
| `.macro add <name> <TYPE> [field=value …]` | Add a step. `TYPE` tab completes. Values can't contain spaces. |
| `.macro show <name>` | List the steps with their numbers. |
| `.macro removeaction <name> <index>` | Delete step `index`, counting from 0. |
| `.macro delete <name>` | Delete the macro. |

```
.macro new dupe1
.macro add dupe1 DELAY ticks=5
.macro dupe1 10 20
.macro stop
```

While you pilot a Multi bot, `.macro` runs, loops and stops on that bot instead of you.

##### Share macros

Beta only. Macros can sit in folders in the editor, and a share code carries a macro or a whole folder.

| Usage | Effect |
|---|---|
| `.macro export <macro or folder>` | Copy a `DIHM1:` share code to the clipboard. |
| `.macro import [code]` | Add the macros in a code, or the one on your clipboard. Macros whose name is taken get a new one. |

##### Beta macro steps

- **Trade** buys matching offers from trades an earlier step opened; see `AutoTrade`. Later steps can read `trade_count`,
  `trade_spent` and `trade_status`.
- **AI Tool** runs any of the tools `.tools` lists.

#### .scripts

Write automation in DihScript, a small C-style language, and run it by name. Scripts are plain text files in
`config/dih/scripts/<name>.dscript`; open the editor from the module menu's **Scripts** button (also in the Main
Menu panel of container GUIs) or with `.scripts edit`. The [Scripting guides](#writing-scripts) cover the language, every function and a full gold miner.
Alias: `.script`.

| Usage | Effect |
|---|---|
| `.scripts run <name>` | Check, then start a script. Several scripts can run at once. |
| `.scripts stop <name>` | Stop one script. Its `onStop()` runs; stop it again to kill it outright. |
| `.scripts stop` | Stop every running script (`.scripts stop all` does the same). |
| `.scripts list` | Every script, with the running ones, their uptime and current line. |
| `.scripts check <name>` | Report syntax errors and unknown functions without running anything. |
| `.scripts edit [name]` | Open the Scripts editor, optionally on one script. |
| `.scripts new <name>` | Create a script from the starter template. |
| `.scripts delete <name>` | Delete a script (stopping it first). |
| `.scripts functions [category]` | List the built-in functions, by category. |
| `.scripts folder` | Print where the script files live. |

```
.scripts run gold_miner
.scripts stop gold_miner
```

### Schematics

#### .schem

Aliases: `.schematic`, `.schematics`

Beta: 5.1 beta only, not in 5.0.1 stable.

Load schematic files, place them in the world, see what is still to build, and let Baritone build them. Works like a built-in Litematica.

It reads `.litematic`, `.schematic` (MCEdit, Schematica and WorldEdit), `.schem` (Sponge v1 to v3) and vanilla `.nbt`
files. Files live in `.minecraft/schematics`. You don't need the Litematica mod; if you have it, its placements stay
separate and `#litematica` still builds those.

##### Get a file in

| Usage | Effect |
|---|---|
| `.schem menu` | Open the Schematics screen: your files on the left, this world's placements on the right. The module's Menu Key does the same. |
| `.schem open` | Pick a file from anywhere with your system's file picker, starting in Downloads. The module's Open File Key does the same. |
| `.schem import <path>` | The same with a typed path, quotes and backslashes allowed, or a file name in Downloads. |
| `.schem list` | List the files in the schematics folder. |
| `.schem place <file> [x y z]` | Place a file from the folder in front of you, or at a position. `.schem load` is the same. |

You can also drag files onto the game window. One file is placed straight away; several open the Schematics screen.
The screen also lists your 20 newest schematic downloads, tagged "Downloads". Picked and dropped files are copied into
the schematics folder, and nothing there is overwritten: a different file with the same name becomes `name (2).litematic`.

##### Place it

A new placement goes four blocks in front of you and you are straight back in the game with the camera free. Until you
keep it, these keys move it, and a panel at the top of the screen lists them:

| Key | Does |
|---|---|
| Arrow keys | Move it forward, back, left or right, relative to where you face. Hold to repeat. |
| Page Up / Page Down | Move it up or down. |
| R | Turn it 90° clockwise. |
| M | Mirror it (none, then x, then z). |
| G | Drop it onto the ground at its column. |
| Enter | Keep it where it is. |
| Backspace | Remove it. |

The keys only do this while a new placement is being placed and no screen is open, so they never clash with the game
otherwise. If another mod also uses R, M or G, it may react too while you place.

##### Move it later

| Usage | Effect |
|---|---|
| `.schem adjust` | Open the mouse placement screen: right-click or drag puts it where you point, the wheel turns it, Shift + wheel pushes it nearer or farther, middle-drag looks around, left-click keeps it and Esc puts it back. The **Move** button and the Adjust Key open it too. |
| `.schem move <x y z>` | Put its corner at those coordinates. |
| `.schem here` | Put its corner where you stand. |
| `.schem nudge <dx dy dz>` | Shift it by that many blocks. |
| `.schem rotate [quarters]` | Turn it 90° clockwise, or by -3 to 3 quarter turns. The Rotate Key does one turn. |
| `.schem mirror [none\|x\|z]` | Cycle the mirror, or set it. The Mirror Key cycles it. |

These act on the selected placement: the last one you placed or picked.

##### Remove it or hide it

| Usage | Effect |
|---|---|
| `.schem remove` | Remove the selected placement from the world. |
| `.schem remove <n>` | Remove placement `n`, by its number in `.schem placements` or the start of its name. |
| `.schem remove all` | Remove every placement in this world. |
| `.schem hide [n]` | Stop drawing it but keep it, with its position and turn. `.schem show [n]` brings it back. |
| Schematics screen | Click the placement on the right, then **Remove** or **Show / Hide**. |
| Backspace | Removes a placement you haven't kept yet. |

Removing only takes the placement out of the world; the file stays in the schematics folder. To hide every placement at
once without losing them, turn the Schematics module off, for example with `.toggle schematics`.

##### See what it needs

| Usage | Effect |
|---|---|
| `.schem materials [n]` | The first items still needed, in chat, against your inventory. |
| **Materials** | In the Schematics screen: the whole list as a table with Total, Have and Missing, biggest shortfall first. It updates every 2 seconds, scrolls, and **Missing only** hides what you have enough of. |
| `.schem materials csv [n]` | Save the list to `.minecraft/schematics/materials/<name>-materials.csv` for a spreadsheet, with item, name, needed, have and missing. **Save CSV** in the screen does the same. Saving again replaces the file. |
| `.schem verify [n]` | Count the blocks that are right, missing, the wrong block, the wrong state or extra, in loaded chunks. |

In the world, the overlay tints blocks that aren't right: blue missing, red the wrong block, orange the right block
turned wrong, pink an extra block. Blocks in shulker boxes or chests don't count as yours.

##### Build it

| Usage | Effect |
|---|---|
| `.schem build [n]` | Hand the placement to Baritone's builder, the one `#build` uses. **Build** in the screen does the same. |

It turns on `#allowInventory` so blocks anywhere in your inventory get used, and it pauses when a block runs out, then
lists what is still to place. Get those and type `#resume`. If it stops on stairs or other turned blocks,
`#buildIgnoreDirection true` lets it place them. To build with no blocks at all, see `#construct`.

##### Placements and layers

| Usage | Effect |
|---|---|
| `.schem placements` | List this world's placements with their numbers. |
| `.schem select <n>` | Make placement `n` the selected one. |
| `.schem info [n]` | Its file, position, turn, size and block count. |
| `.schem reload [n]` | Read its file again after you changed it. |
| `.schem layer <all\|single\|below\|above> [y]` | Draw every layer, one, or those below or above a height (your feet by default). |

Placements are saved per server, or per world folder in singleplayer, and come back when you rejoin. The list shows
placements from every dimension, marked "(Nether)" and so on when they're not in yours.

### Packets

#### .packet

Aliases: `.pkt`

Control the outgoing packet queue, the same as the Send, Delay, Flush and Clear buttons.

| Usage | Effect |
|---|---|
| `.packet status` | Show whether send and delay are on, and how many packets are queued. |
| `.packet send [on\|off\|toggle]` | With send off, GUI packets aren't sent. |
| `.packet delay [on\|off\|toggle]` | Hold packets in the queue. Turning delay off sends what was held. |
| `.packet flush` | Send every queued packet now. |
| `.packet clear` | Throw every queued packet away. |

#### .delay

Shortcut for `.packet delay`.

```
.delay [on|off|toggle]
```

#### .gate

Aliases: `.packetgate`

Cancel, hold or whitelist specific packet types until you end the gate.

| Usage | Effect |
|---|---|
| `.gate cancel <packet…>` | Drop matching packets. |
| `.gate delay <packet…>` | Hold matching packets in the queue. |
| `.gate allow <packet…>` | Drop every packet except these. |
| `.gate end [id]` | End a gate and send what it held. Gates made here use the id `cli`. |
| `.gate clear` | End every gate. |
| `.gate status` | List active gates. |

Name packets by class (they tab complete), separated by spaces or commas.

```
.gate cancel ServerboundContainerClosePacket
.gate end
```

#### .send

Build and send any client-to-server packet by class name.

```
.send <PacketClass> [field=value …]
```

Press Tab after the space to search packet classes, and again after the name for its fields. If you leave
fields out, DIH lists the ones the packet needs. Quote values with spaces (`message="hello"`) and give raw bytes
as `hex:…`. Add `dry` (or `dryrun`, `preview`, `nosend`) to build the packet without sending it. Sent packets
skip the delay queue, and server-to-client packets are refused.

```
.send ServerboundSetCarriedItemPacket slot=0
.send ServerboundChatPacket message="hello" dry
```

### Container GUIs

#### .gui

Aliases: `.screen`

Save, desync and reload container screens, the same as the on-screen buttons.

| Usage | Effect |
|---|---|
| `.gui save` | Store the open screen on your side. Sends nothing. |
| `.gui desync` | Tell the server the screen closed but keep it open for you. |
| `.gui load` | Reopen the stored screen, like the V key. |
| `.gui close` | Close the screen and tell the server. `.gui close packet` does the same. |
| `.gui close nopacket` | Close the screen on your side only. |

#### .click-slot

Click a slot in the open GUI or your inventory.

```
.click-slot <slot> [click] [times]
```

Slots: `gui1`, `gui2`… for container slots, `hotbar1`–`hotbar9`, `inventory1`–`inventory27`, `helmet`,
`chestplate`, `leggings`, `boots`, `offhand`, or a raw slot number.

| Click | Does |
|---|---|
| `left`, `right`, `middle` | Normal clicks. `left` is the default. |
| `shift-left`, `shift-right` | Quick move. |
| `clone` | Middle-click clone (creative). |
| `pickup-all` | Double-click collect. |
| `drop-item` | Drop `times` items from the slot. |
| `drop-stack` | Drop the whole stack. Takes no count. |
| `swap <1-9> [times]` | Swap with that hotbar slot. |

`times` goes up to 100 000.

```
.click-slot gui5 shift-left
.click-slot hotbar1 swap 3
.click-slot inventory4 drop-item 16
```

#### .click-item

Click an item in the open GUI by its visible name. Takes the same clicks as `.click-slot`.

```
.click-item "<item name>" [click] [times]
```

Matching ignores case and colour codes. Quote names that contain spaces.

```
.click-item "Confirm"
.click-item "Diamond Sword" shift-left
```

#### .xcarry

Stash the item in your hand in a slot the server doesn't expect to hold items, so you keep it.

```
.xcarry <slot>
```

Open your inventory with E first. Slots: `craft1`–`craft4`, `craft5` (the crafting output, also `result`),
`helmet`, `chestplate`, `leggings`, `boots`, `offhand`, or `cursor` to keep holding it on the cursor.

### Inventory & items

#### .drop

Drop your held stack, your whole inventory, or a set amount of an item.

| Usage | Effect |
|---|---|
| `.drop hand` | Drop the held stack. |
| `.drop fullinventory` | Drop every stack in the inventory and hotbar. |
| `.drop <amount>` | Drop `amount` of the held item, taking from other stacks of it if needed. |
| `.drop <amount> <item>` | Drop `amount` of that item from anywhere in the inventory. |

```
.drop 32
.drop 10 diamond
```

#### .change-slot

Select a hotbar slot.

```
.change-slot <1-9>
```

#### .give

Put an item in your selected hotbar slot. Needs creative mode.

```
.give <item> [count]
```

`count` is 1 to 64.

```
.give tnt 64
```

#### .nbt

Inspect or copy the item in your hand.

| Usage | Effect |
|---|---|
| `.nbt` | Open the component (NBT) inspector for the item in your main hand, or offhand if it's empty. `.nbt get` does the same. |
| `.nbt copy` | Copy a `/give` command that recreates the item. |

### Movement & position

#### .vclip

Clip up or down through blocks with movement packets.

| Usage | Effect |
|---|---|
| `.vclip <blocks>` | Move up (positive) or down (negative). |
| `.vclip top` | Find a safe spot above you and clip there. |
| `.vclip bottom` | Find a safe spot below you and clip there. |
| `.vclip paper <blocks>` | Same as `<blocks>`, padded for Paper servers. `default` is the same. |
| `.vclip single <blocks>` | Use one movement packet. `normal` is the same. |
| `.vclip custom <blocks> <segment> [maxPackets] [updateLocal] [vehicle] [forceGround]` | Set every option: `segment` 1–50 blocks per step, `maxPackets` 1–100, then `true` or `false` for each flag. |

```
.vclip 5
.vclip -30
.vclip custom -25 10 20 true true
```

#### .hclip

Clip forward through blocks with movement packets.

| Usage | Effect |
|---|---|
| `.hclip <blocks>` | Clip forward `blocks`. |
| `.hclip forward` | Clip through the wall in front of you to the free space past it. |
| `.hclip back` | Same, through the wall behind you. |
| `.hclip paper <blocks>` | Padded for Paper servers. `default` and `padding` are the same. |
| `.hclip single <blocks>` | Use one movement packet. `normal` is the same. |
| `.hclip custom <blocks> <segment> [maxPackets] [updateLocal] [vehicle] [forceGround]` | Set every option, like `.vclip custom`. |

#### .tp

Teleport in paced HClip bursts, ground first.

| Usage | Effect |
|---|---|
| `.tp <x> <y> <z> [maxPackets] [pauseMs]` | Teleport. `~` offsets work. `maxPackets` 1–100 per burst, `pauseMs` 50–10 000 between bursts. |
| `.tp <waypoint>` | Teleport to a saved DIH waypoint. Quote names with spaces. |
| `.tp status` | Show the running teleport. |
| `.tp stop` | Cancel it. |
| `.tp config <maxPackets> <pauseMs>` | Save new defaults. |
| `.tp reset` | Go back to 20 packets and 500 ms. |

It works while riding but won't start while a macro runs.

```
.tp ~100 ~ ~
.tp 1200 64 -300 30 400
.tp base
```

#### .copypos

Copy your block position as `x y z`. Uses your vehicle while riding and the camera in freecam.

#### .waypoints

Aliases: `.wp`

Save and manage DIH waypoints for this server or world. They're separate from Baritone's `#wp`.

| Usage | Effect |
|---|---|
| `.wp` | Save a waypoint here, named `Waypoint 1`, `Waypoint 2`… |
| `.wp <x> <y> <z>` | Save one at those coordinates. |
| `.wp gui` | Open the waypoint manager. `.wp list` does the same. |
| `.wp del <name>` | Delete a waypoint. |
| `.wp clear` | Delete every waypoint for this server or world. |

#### .dismount

Get off any vehicle.

### Player & world

#### .gamemode

Change your real game mode without sending a chat command. The server still has to allow it.

```
.gamemode <survival|creative|adventure|spectator>
```

#### .fakegm

Show a different game mode on your side only.

```
.fakegm <survival|creative|adventure|spectator|reset>
```

`reset` (or `r`) goes back to your real mode.

#### .damage

Send `/damage @s <amount>`. Works only where you can use `/damage`.

```
.damage <amount>
```

`amount` is 0.1 to 1024.

#### .rv

Watch another loaded player's point of view.

| Usage | Effect |
|---|---|
| `.rv` | Watch the player you're looking at. |
| `.rv <name>` | Watch a player by name. |
| `.rv stop` | Go back to your own view. |

#### .namescrape

Aliases: `.scrapenames`

Collect player names from the server and copy them to the clipboard.

| Usage | Effect |
|---|---|
| `.namescrape [count]` | Scrape, stopping at `count` names if given. |
| `.namescrape deep [count]` | Scrape exhaustively. |
| `.namescrape pause` | Pause a running scrape. |
| `.namescrape resume` | Resume it. |
| `.namescrape stop` | Stop it. |

#### .enchant

Beta: 5.1 beta only, not in 5.0.1 stable.

Enchant the item in your hand with any enchantment, at any level up to 255. Needs creative mode.

| Usage | Effect |
|---|---|
| `.enchant <enchantment> [level]` | Add one, at its normal maximum level unless you give one. Tab completes the names. |
| `.enchant all [level]` | Add every enchantment. |
| `.enchant remove <enchantment>` | Take one off. |
| `.enchant clear` | Take them all off. |
| `.enchant plan <enchantments>` | Works in survival: from the villager trades you've opened in this world, find who sells those books and plan the trip. AutoTrade records those trades. |

```
.enchant sharpness 10
.enchant all
.enchant plan mending unbreaking
```

#### .invsee

Beta: 5.1 beta only, not in 5.0.1 stable.

Look at a loaded player's armour and held items, with durability and enchantments. View only.

```
.invsee
.invsee <player>
```

With no name it opens for the player you're looking at.

### Server, chat & network

#### .server

Open the server panel or check TPS.

| Usage | Effect |
|---|---|
| `.server` | Open the server info panel. `.server info` does the same. |
| `.server plugins` | Open the plugin scanner. |
| `.server tps` | Print the estimated server TPS. |

#### .plugins

Shortcut for `.server plugins`.

#### .say

Send a chat message, or a server command if it starts with `/`.

```
.say <message>
```

Use it in binds and macros, or to send a line that starts with your DIH prefix.

#### .clear

Aliases: `.cls`

Clear the chat window.

#### .disconnect

Leave the server.

### Multi, LAN Sync & Matchmaking

#### .multi

Run several accounts at once.

| Usage | Effect |
|---|---|
| `.multi` | Open the Multi menu, or the console while a batch runs. `.multi open` does the same. |
| `.multi launch <profile>` | Start a saved profile. |
| `.multi status` | Show the batch name and how many bots are ready and connected. |
| `.multi stop` | Disconnect every bot. |
| `.multi macro <macro> [bot]` | Run a macro on one bot, or all of them. |

While you pilot a bot, `click-slot`, `click-item`, `change-slot`, `drop`, `send`, `say`, `damage`, `hclip`,
`vclip` and `tp` run on the bot, and `disconnect` disconnects the bot. `delay`, `gamemode`, `server`, `plugins`,
`matchmaking` and `dismount` are blocked so they can't touch your main account.

#### .sync

Aliases: `.lan-sync`, `.lansync`

Run the same thing on every client in your LAN Sync session. Join a session first.

| Usage | Effect |
|---|---|
| `.sync send <message or /command>` | Send the line from every synced client. |
| `.sync macro <name>` | Start a macro on every synced client at the same moment. |

#### .matchmaking

Aliases: `.mm`

Show or hide the Matchmaking overlay.

#### .irc

Send a message to your Matchmaking lobby. Join or create a lobby first.

```
.irc <message>
```

### Addons & AI tools

#### .addons

Aliases: `.addon`

Beta: 5.1 beta only, not in 5.0.1 stable.

Open the addon store: browse, install and manage addons. **My Addons** lists what is installed.

Install from a card, click **Install** again to confirm, and restart Minecraft. DIH checks each download's SHA-256 before
installing it. The store is also on the website's Addons page.

#### .ai

Beta: 5.1 beta only, not in 5.0.1 stable.

Give the AI an objective in plain words and let it work through it with its tools.

| Usage | Effect |
|---|---|
| `.ai start <objective>` | Start a run, for example `.ai start get full iron armor`. The progress panel opens. |
| `.ai status` | What it's doing. |
| `.ai why` | Why it made its last choice. |
| `.ai pause` | Pause the run. `.ai resume` carries on. |
| `.ai stop` | Stop the run. |
| `.ai confirm` | Allow a step it asked permission for. |
| `.ai setup` | Pick the model and key. |
| `.ai panel` | Show or hide the progress panel. |

`.ai setup` has presets for OpenRouter, OpenAI, Claude, Gemini, Groq, DeepSeek, Qwen, Ollama and LM Studio, and a Test
button that says what's wrong in plain words. The model plans once and is asked again only when a step fails. Without a
key it runs in a simpler basic mode. The panel is a live checklist with Pause, Stop and Why?.

This is a different thing from `#ai`, the chat AI that plays through Baritone commands.

#### .tools

Aliases: `.tool`

Beta: 5.1 beta only, not in 5.0.1 stable.

Run one of the AI's tools yourself, without the AI.

| Usage | Effect |
|---|---|
| `.tools` | List every tool by category. |
| `.tools help <tool>` | Its arguments and what it does. |
| `.tools <tool> [args]` | Run it. Tab completes tools and arguments. Tools that could cost you something ask you to click **[confirm]** first. |

The list has about 155 tools, in categories such as job, movement, mining, building, combat and world. Macros can run
them too, with an **AI Tool** step.

#### .coords

Beta: 5.1 beta only, not in 5.0.1 stable.

Show or copy your coordinates. Comes with the built-in Coords HUD addon, so turn that on in Addons first.

#### .deathpoint

Beta: 5.1 beta only, not in 5.0.1 stable.

Show where you last died. Comes with the built-in Death Point addon, so turn that on in Addons first.

### Updates

#### .update

Aliases: `.updates`

Check for a newer DIH build.

| Usage | Effect |
|---|---|
| `.update` | Show whether a newer build exists. |
| `.update check` | Ask GitHub again. |
| `.update download` | Open the new jar's download. Replace the old jar in `mods` and restart. |
| `.update on` | Turn automatic checks on. |
| `.update off` | Turn them off. DIH then never contacts GitHub. |

## Pathfinder & AI commands

Coordinates accept `~` like vanilla commands. `#help` lists everything; `#help <command>` shows one command's
full help.

### Control

#### #cancel

Aliases: `#c`, `#stop`

Stop whatever Baritone is doing.

#### #forcecancel

Stop harder than `#cancel` when it doesn't work.

#### #pause

Aliases: `#p`, `#paws`

Pause the current task.

#### #resume

Aliases: `#r`, `#unpause`, `#unpaws`

Pick up where `#pause` left off.

#### #paused

Say whether Baritone is paused.

#### #proc

Show which process is in control and its state.

#### #eta

Estimate the time to the next path segment and to the goal.

### Going places

#### #goto

Walk to a block type, a Y level or coordinates.

```
#goto <block>
#goto <y>
#goto <x> <z>
#goto <x> <y> <z>
```

#### #goal

Set a goal without moving. Start walking with `#path`.

```
#goal
#goal <y>
#goal <x> <z>
#goal <x> <y> <z>
#goal clear
```

`#goal` alone uses your position. `reset` and `none` work like `clear`.

#### #path

Start walking to the current goal.

#### #come

Walk to where your camera is. Use it with freecam.

#### #thisway

Aliases: `#forward`

Set a goal some distance straight ahead.

```
#thisway <distance>
```

#### #axis

Aliases: `#highway`

Head to the nearest axis, X=0 or Z=0.

#### #surface

Aliases: `#top`

Get out of a cave to the nearest surface or open air.

#### #invert

Walk away from the current goal instead of toward it.

#### #runaway

Aliases: `#escape`

Get at least `distance` blocks from where you stand.

```
#runaway [distance]
```

The default is 32. `#flee` opens the Flee triggers instead, not this.

#### #follow

Follow players or mobs.

| Usage | Effect |
|---|---|
| `#follow players` | Follow every player. |
| `#follow player <name…>` | Follow these players. |
| `#follow entities` | Follow every living entity. |
| `#follow entity <type…>` | Follow these entity types, such as `horse`. |

#### #explore

Explore outward from you, or from X Z.

```
#explore [x z]
```

#### #explorefilter

Mark chunks as already explored before `#explore`.

```
#explorefilter <path> [invert]
```

The file is JSON like `[{"x":0,"z":0}]`. With `invert`, the listed chunks count as not explored.

#### #elytra

Fly with an elytra and rockets in any dimension. Wear the elytra and carry fireworks.

| Usage | Effect |
|---|---|
| `#elytra goto <x> <z>` | Fly to X Z. |
| `#elytra goto <x> <y> <z>` | Fly to an exact block. |
| `#elytra` | Fly to the current `#goal`. |
| `#elytra reset` | Reset pathfinding and keep the goal. |
| `#elytra repack` | Send the loaded chunks to the pathfinder again. |
| `#elytra supported` | Check that the native library loaded. |

#### #nether

Aliases: `#nc`, `#coords`

Convert coordinates between the Overworld and the Nether. X and Z divide or multiply by 8; Y stays.

| Usage | Effect |
|---|---|
| `#nether` | Convert your position, detecting your dimension. |
| `#nether <x> <y> <z>` | Convert these, detecting the direction. |
| `#nether <x> <z>` | Convert X and Z only. |
| `#nether overworld <x> <y> <z>` | Overworld to Nether. |
| `#nether nether <x> <y> <z>` | Nether to Overworld. |

#### #portal

Aliases: `#whereportal`, `#findportal`

Walk to the nearest known nether portal and step in. Explores if it knows none.

`#portal skip` skips the nearest one and goes to the next. `#set enterPortal false` stops it stepping in.

#### #sleep

Go to the nearest known bed and sleep once it's night or thundering. In the day it waits at the bed.

#### #autosleep

Aliases: `#nightowl`

Do `#sleep` on its own every night.

| Usage | Effect |
|---|---|
| `#autosleep` | Toggle it. `on` and `off` work too. |
| `#autosleep interrupt` | Toggle whether it may stop another task. Off by default. |
| `#autosleep status` | Show the settings. |

### Mining, farming & building

#### #mine

Find and mine blocks.

```
#mine <block…>
```

Stone and deepslate ores pair up, so `#mine diamond_ore` gets both. See `#set legitMine` for a less obvious
style.

#### #tunnel

Dig straight ahead the way you face.

```
#tunnel
#tunnel <height> <width> <depth>
```

With no size it digs 1 wide and 2 high.

#### #farm

Harvest grown crops and replant them.

```
#farm [range] [waypoint]
```

`range` limits it to that many blocks from you, or from `waypoint`.

#### #pickup

Pick up dropped items, all of them or only the ones you name.

```
#pickup [item…]
```

#### #build

Build `<file>.schematic` here or at a position.

```
#build <file> [x y z]
```

#### #litematica

Build the schematic open in Litematica.

```
#litematica [#]
```

#### #blacklist

While heading to a block, skip the closest one.

#### #construct

Aliases: `#buildfromscratch`

Beta: 5.1 beta only, not in 5.0.1 stable.

Build a schematic starting with none of its blocks. It works out what the blocks cost, gathers a batch that fits your
inventory with `#acquire`, builds what it can, and repeats until it's done.

| Usage | Effect |
|---|---|
| `#construct <file> [x y z]` | Build a file from the schematics folder here, or at x y z. |
| `#construct plan <file> [x y z]` | List what it takes and what you still need to gather. |
| `#construct status` | What it's doing. |
| `#construct stop` | Stop. `#stop` and `#cancel` work too. |

It never mines inside the build, keeps the blocks it gathered, and skips blocks no item places, like water. It turns
`allowInventory` on while it runs and back off after.

### Survival on autopilot

#### #acquire

Aliases: `#aquire`

Beta: 5.1 beta only, not in 5.0.1 stable.

Get an item from whatever you have now: it plans the whole chain (trees, planks, tools, ore, a furnace, mobs) and runs it.

| Usage | Effect |
|---|---|
| `#acquire <item> [count]` | Get it, such as `#acquire stone_pickaxe` or `#acquire torch 64`. |
| `#acquire <count> <item>` | The same, count first. |
| `#acquire food [count]` | The cheapest food that covers 10 to 20 food points, or `count` of it. |
| `#acquire plan <item> [count]` | Print the steps without doing anything. |
| `#acquire status` | The current step and progress. |
| `#acquire stop` | Stop. `#stop` works too, and `#pause` pauses it. |

Items can be typed loosely: `iron pick`, `Iron Pickaxe` and `iron_pickaxe` all work. It counts what you already carry,
uses a crafting table or furnace nearby or places its own, eats when you're hurt or hungry, and re-plans from your real
inventory when a step falls short or a tool breaks. It never runs server commands or teleports. While it runs the
`Guardian` steps in on danger. Big smelts are split across up to 4 furnaces, and its crafting table and furnace are
picked back up.

##### Settings

Change these with `#set <setting> <value>`.

| Setting | Default | What it does |
|---|---|---|
| `acquirePlaceStations` | true | Place a crafting table or furnace when none is near. Off: only use ones that exist. |
| `acquireStationRadius` | 32 | How far to look for a crafting table or furnace first, in blocks. |
| `acquireKillMobs` | true | Kill mobs for their drops. Only the mob the step names, never players, babies, named mobs or tamed pets. |
| `acquireGearUp` | true | Plan a stone sword before ore trips, and affordable iron armour and a shield after getting iron. |
| `acquireMaxReplans` | 8 | How many times one `#acquire` can re-plan before it gives up. |
| `acquireStepTimeoutSeconds` | 300 | Seconds a step can take before it counts as stuck. |
| `acquireDropJunk` | false | When full, drop a stack of filler the plan doesn't need. Off: it stops and says the inventory is full. |
| `acquireHeal` | true | Eat when hurt and below 18 food or at 6 food, and fetch food when there is none. |
| `acquireHealHealth` | 12 | Health in half-hearts at which it gets food before carrying on, when you hold nothing safe to eat. |
| `acquireEmergencyHealth` | 6 | Health in half-hearts at which it eats a golden apple, and backs off from a fight to eat. |

#### #eat

Beta: 5.1 beta only, not in 5.0.1 stable.

Eat one item now. It never eats on its own.

| Usage | Effect |
|---|---|
| `#eat` | Eat the food that fills your missing hunger with the least waste. |
| `#eat <item>` | Eat that item, such as `#eat golden_apple`. |
| `#eat stop` | Stop eating. |

Harmful food is only eaten when you're starving and have nothing else, and golden apples only at low health. Pathing
pauses while you eat.

#### #beat

Beta: 5.1 beta only, not in 5.0.1 stable.

Play towards beating the game in 9 phases: gear, a nether portal, blaze rods, pearls, home, eyes of ender, the
stronghold, the End and the dragon. Progress is saved per world.

| Usage | Effect |
|---|---|
| `#beat` | Start, or carry on with this world's saved run. |
| `#beat plan` | Print every phase and step first. |
| `#beat resume` | Continue the saved run. |
| `#beat status` | The phase and its progress. |
| `#beat stop` | Stop. The run stays saved. |
| `#beat restart` | Forget the saved run and start over. |

Phases that aren't finished yet, like going through portals, finding structures and the dragon fight, stop the run with
a message saying so.

#### #parkour

Beta: 5.1 beta only, not in 5.0.1 stable.

Run parkour with each jump solved exactly, using only normal key presses and turning.

| Usage | Effect |
|---|---|
| `#parkour` | Follow the course you're standing on until no jumps are left ahead. |
| `#parkour goto <x> <y> <z>` | Jump your way to that block. |
| `#parkour solve <x> <y> <z>` | Work out one jump onto that block and say how, without moving. |
| `#parkour status` | What it's doing. |
| `#parkour stop` | Stop. `#stop` and `#cancel` work too. |

It handles neos, strafes, head-hitters, slabs, fences, ladders and momentum jumps, and lands still on one-block pillars.
It can't do triple neos or slime and ice momentum jumps yet, and neo jumps leave little room for lag.

With `allowParkour` and `#set allowHardParkour true` on, Baritone also paths through jumps its own parkour can't make
(diagonal, round a wall, onto fences and slabs) and hands them to the solver. `parkourPreview` (on by default) draws the
line a jump will follow and outlines where it lands.

### Search & memory

These read Baritone's chunk cache, so they send no extra packets.

#### #search

Aliases: `#blocks`

List the nearest matches for a block, with distance and direction, and walk to one.

| Usage | Effect |
|---|---|
| `#search <block>` | List the nearest matches. |
| `#search <block> goto` | List them and walk to the nearest. |
| `#search <N> goto` | Walk to match N from the last search. |

It searches loaded chunks plus the cache, so ores you saw before anti-xray hid them still show up.

#### #find

Look up cached positions of blocks. Tab completion only offers blocks in the cache.

```
#find <block…>
```

#### #chest

Aliases: `#chests`, `#findchest`

Search what's inside every container you've opened. DIH records them as you play.

| Usage | Effect |
|---|---|
| `#chest <item>` | Containers holding `item`. Partial names match. |
| `#chest <item> goto` | Search and walk to the best match. |
| `#chest <N> goto` | Walk to match N from the last search. |
| `#chest list [page]` | Browse everything recorded. |
| `#chest stats` | Count the recorded containers. |
| `#chest forget <N>` | Delete match N from the record. |
| `#chest clear` | Delete the whole record. |

Saved in `baritone/chest_memory.json`.

#### #bases

Aliases: `#basefinder`

Find likely player bases from clusters of beacons, ender chests, enchanting tables, anvils, shulkers and similar
blocks.

| Usage | Effect |
|---|---|
| `#bases [N]` | Top N bases in this dimension (10 by default). |
| `#bases pie` | Break the finds down by block type. |
| `#bases <N> goto` | Walk to base N. |

Run `#repack` first to include chunks you just loaded. Tune it with `#set baseFinderMinScore` (30),
`baseFinderMinIndicators` (3) and `baseFinderEpsilon` (50, the cluster radius in blocks).

#### #heatmap

Aliases: `#activity`

Rank 512 × 512 block zones by signs of players, including portals, hoppers, observers, player heads and jukeboxes.

```
#heatmap [N]
#heatmap <N> goto
```

It also shows as a JourneyMap layer.

#### #players

Aliases: `#player`, `#seen`

Look up every player you've seen and where. Sightings are recorded even with `#threats` off.

| Usage | Effect |
|---|---|
| `#players` | Everyone seen. |
| `#players <name>` | Where and when you saw them. |
| `#players goto <name>` | Walk to where you last saw them. |
| `#players forget <name>` | Delete them from the record. |
| `#players clear` | Delete the whole record. |
| `#players stats` | Summarise the record. |

#### #menu

Aliases: `#gui`

Read and click server plugin GUIs, such as shops, warp menus and random teleport.

| Usage | Effect |
|---|---|
| `#menu dump` | Print every filled slot of the open menu. |
| `#menu click <matcher>` | Click a slot in the open menu. |
| `#menu run <command> <matcher>` | Run `/command`, wait for the menu, then click. |
| `#menu stop` | Cancel a pending click. |

A matcher is `item:<id>` (most reliable), `slot:<n>` counting from 0, or text from the item's name or lore. Run
`#menu dump` with the GUI open first to see the real names.

```
#menu run warps item:compass
#menu click slot:18
```

### Seeds & structures

#### #seedinput

Aliases: `#seed`

Save this server's world seed so `#seedmap`, `#structure` and `#where` work in multiplayer. OreSim uses it too.

| Usage | Effect |
|---|---|
| `#seedinput <seed>` | Save it. Text converts like on the world creation screen. |
| `#seedinput <seed> force` | Save it even if it doesn't match the server. |
| `#seedinput` | Show the seed and whether it matches. `#seedinput check` does the same. |
| `#seedinput clear` | Forget it. |

The server sends a hash of its real seed when you join, and a seed that doesn't match is refused. Singleplayer
needs no seed.

#### #structure

Aliases: `#struct`, `#locate`

Find the nearest structure and walk there.

```
#structure <name>
```

Names: `village`, `stronghold`, `fortress` (or `nether_fortress`), `bastion`, `mansion`, `monument`,
`ancient_city`, `end_city`, `mineshaft`, `buried_treasure`, `desert_pyramid`, `jungle_pyramid`,
`pillager_outpost`, `shipwreck`, `igloo`, `swamp_hut`, `ocean_ruin`, `ruined_portal`, `trial_chambers`,
`trail_ruins`.

#### #where

Show your position, or a structure's coordinates without walking there.

```
#where
#where <structure>
```

With a structure it prints coordinates, distance and direction. It takes the same names as `#structure`.

#### #seedmap

Aliases: `#structures`, `#smap`

Draw every structure the seed really generates on the JourneyMap fullscreen map. Without JourneyMap it lists
them in chat.

| Usage | Effect |
|---|---|
| `#seedmap` | Everything within 2000 blocks. |
| `#seedmap <radius>` | Up to 8000 blocks. |
| `#seedmap <kinds…>` | Only these, such as `village monument`. |
| `#seedmap <r> at <x> <z>` | Centre the search somewhere else. |
| `#seedmap wp` | Also add waypoints on landmarks. |
| `#seedmap strongholds` | The nearest strongholds, plus all 128. |
| `#seedmap off` | Hide the overlay. |

### Safety & automation

#### #threats

Aliases: `#threat`

Alert you in chat when a player comes within range.

| Usage | Effect |
|---|---|
| `#threats` | Toggle alerts. `on` and `off` work too. |
| `#threats <N>` | Set the range to N blocks (64 by default). |
| `#threats status` | Show the state and range. |

#### #flee

Disconnect, or run a command, when something goes wrong. Every trigger starts off, and by default triggers are
only checked while Baritone is busy.

| Usage | Effect |
|---|---|
| `#flee` | Show the status. |
| `#flee on` | Turn Flee on. `off` turns it off. |
| `#flee players [on\|off\|N]` | Trigger on a player within N blocks. |
| `#flee health <hearts>` | Trigger at that many hearts. |
| `#flee hunger <food>` | Trigger at that food level. |
| `#flee tool <durability>` | Trigger when your tool gets that low. |
| `#flee pickaxe` | Trigger when you run out of pickaxes while mining. |
| `#flee full` | Trigger when your inventory is full. |
| `#flee action <disconnect\|command\|stop>` | What happens when a trigger fires. |
| `#flee command <command>` | The command to run, such as `/spawn`. |
| `#flee check always` | Also check while Baritone is idle. |
| `#flee reconnect on` | Let AutoReconnect rejoin afterwards. |
| `#flee gui` | Open the settings screen. |
| `#flee test` | Fire the action now. |

#### #listentomine

Aliases: `#ltm`

Watch chat for a word, then warp and mine. By default it waits for `legendary`, runs `/warp mine`, drops a stack
if your inventory is full, and mines `ancient_debris`.

```
#ltm [on|off|status]
```

Change it with `#set listenToMineWord`, `listenToMineWarp`, `listenToMineTarget`, `listenToMineWarpDelay` (40
ticks) and `listenToMineCooldown` (200 ticks).

### AI

#### #ai

Let a language model read server chat and play by running `#` commands. Messages from trusted players are
orders; everyone else's are only context.

##### Set up

```
#ai key <api-key>
#ai provider [name]
#ai url <base-url>
#ai model <name>
#ai trust <player>
#ai on
```

The key can also come from the `MINECRAFTAI_LLM_KEY` environment variable. In the beta, `.ai setup` is the easiest
way in: Groq, OpenAI, OpenRouter, Claude and Gemini keys pick their provider by themselves, and `#ai provider` lists the
providers or switches to one, setting the URL and a model. Any OpenAI-compatible endpoint works, and the default model
is `qwen-plus`.

##### Use

| Usage | Effect |
|---|---|
| `#ai <anything>` | Tell it to do something yourself. |
| `#ai status` | Show the setup, token use and last error. |
| `#ai off` | Stop listening. |
| `#ai auto [on\|off]` | Let it think on a timer. |
| `#ai followups [on\|off\|n]` | Beta: carry on after an `#acquire` it started ends, up to `n` times in a row. |
| `#ai chat [on\|off]` | Let it talk in server chat. |
| `#ai trigger <word\|none>` | Make trusted players start orders with a wake word. |
| `#ai goal <text>` | Give it a standing objective. |
| `#ai persona [text]` | Show or set its personality. |
| `#ai trust [name]` | Trust a player, or list trusted players. |
| `#ai untrust <name>` | Stop trusting a player. |
| `#ai deny <command>` | Forbid a `#` command. |
| `#ai allow <command>` | Allow it again. |
| `#ai extra <json\|none>` | Add JSON to every API request. |
| `#ai forget [all\|text]` | See or wipe its long-term memory. |
| `#ai clear` | Forget the current conversation. |

Saved in `baritone/ai.json` and `baritone/ai_memory.json`.

### Waypoints & homes

#### #home

Aliases: `#homes`, `#sethome`

Save named homes and walk back to them.

| Usage | Effect |
|---|---|
| `#home set <name>` | Save your position. `#sethome <name>` does the same. |
| `#home <name>` | Walk there. |
| `#home list` | List homes with distances. |
| `#home del <name>` | Delete one. |

Saved in `baritone/homes.json`.

#### #waypoints

Aliases: `#waypoint`, `#wp`

Manage Baritone's tagged waypoints. DIH's own waypoints are `.wp`.

| Usage | Effect |
|---|---|
| `#wp [list] [tag]` | List waypoints, or only one tag. Short form `l`. |
| `#wp save [tag] [name] [pos]` | Save one. With nothing else it saves here, tagged USER. Short form `s`. |
| `#wp info <tag\|name>` | Show one. Also `show` or `i`. |
| `#wp delete <tag\|name>` | Delete one. Short form `d`. |
| `#wp restore <n>` | Bring back the last n deleted. |
| `#wp clear <tag>` | Delete every waypoint with that tag. Short form `c`. |
| `#wp goal <tag\|name>` | Make it the goal. Short form `g`. |
| `#wp goto <tag\|name>` | Make it the goal and walk there. |

### Building selections

#### #sel

Aliases: `#selection`, `#s`

Select areas like WorldEdit, then fill, clear, copy or paste them. Selections show in the world.

| Usage | Effect |
|---|---|
| `#sel pos1 [x y z]` | Set corner 1, here or at a position. Also `p1` or `1`. |
| `#sel pos2 [x y z]` | Set corner 2. Also `p2` or `2`. |
| `#sel clear` | Clear the selection. Short form `c`. |
| `#sel undo` | Undo the last change to the selection. Short form `u`. |
| `#sel set [block]` | Fill it. Also `fill`, `s` or `f`. |
| `#sel walls [block]` | Fill the walls. Short form `w`. |
| `#sel shell [block]` | Walls, floor and ceiling. Short form `shl`. |
| `#sel sphere [block]` | A sphere inside it. Short form `sph`. `hsphere` is hollow. |
| `#sel cylinder [block] [axis]` | A cylinder along an axis, `y` by default. Short form `cyl`. `hcylinder` is hollow. |
| `#sel cleararea` | Fill it with air. Short form `ca`. |
| `#sel replace <blocks…> <with>` | Swap blocks for another. Short form `r`. |
| `#sel copy [x y z]` | Copy it. Short form `cp`. |
| `#sel paste [x y z]` | Build the copy. Short form `p`. |
| `#sel expand <target> <direction> <blocks>` | Grow selections. Short form `ex`. |
| `#sel contract <target> <direction> <blocks>` | Shrink them. Short form `ct`. |
| `#sel shift <target> <direction> <blocks>` | Move them without resizing. Short form `sh`. |

A target is `all` (`a`), `newest` (`n`) or `oldest` (`o`).

### Settings & upkeep

#### #set

Aliases: `#setting`, `#settings`

View or change Baritone settings.

| Usage | Effect |
|---|---|
| `#set` | List settings. `#set list [page]` does the same. |
| `#set modified [page]` | List the ones you changed. |
| `#set <setting>` | Show one. |
| `#set <setting> <value>` | Change it. |
| `#set toggle <setting>` | Flip a true/false setting. |
| `#set reset <setting>` | Reset one. `#set reset all` resets everything. |
| `#set save` | Save now. It also saves on its own. |
| `#set load [file]` | Load settings from a file in `minecraft/baritone`. |

Settings mentioned on this page: `prefix`, `renderPath`, `renderGoal`, `enterPortal`, `legitMine`,
`colorCurrentPath`, `pathRenderLineWidthPixels`.

#### #modified

Aliases: `#mod`, `#baritone`, `#modifiedsettings`

Shortcut for `#set modified`.

#### #reset

Shortcut for `#set reset`.

#### #repack

Aliases: `#rescan`

Re-cache the chunks around you.

#### #reloadall

Reload the world cache from disk.

#### #saveall

Save the world cache to disk.

#### #render

Fix chunks that render wrong.

#### #click

Open Baritone's click-to-go screen.

#### #gc

Run Java garbage collection.

#### #version

Show the Baritone version.

#### #help

Aliases: `#?`

List `#` commands, or show one command's full help.

```
#help [command]
```

## Guides

### Staying alive

#### Guardian

Beta: 5.1 beta only, not in 5.0.1 stable.

Protects you while any Baritone job runs, such as `#acquire`, `#mine` or a goal, then lets the job carry on.

It steps in on lava, long falls (with a water bucket), creepers, mobs attacking or shooting you, drowning and low
health. Hurt or outnumbered at night, it digs in and seals a pit until healed. It never acts while no job runs, and a
player who isn't on your friends list coming within 12 blocks stops the job and hands you control.

| Setting | Default | What it does |
|---|---|---|
| `guardianEnabled` | true | Turn the Guardian on. |
| `guardianFleeHealth` | 10 | Health in half-hearts at which it stops fighting and retreats. |
| `guardianStopForPlayers` | true | Stop the job when a stranger comes within 12 blocks. |

Change these with `#set <setting> <value>`. To stop or disconnect on your own rules, see `#flee`.

### Trading

#### AutoTrade

Beta: 5.1 beta only, not in 5.0.1 stable.

Buy villager trades that match a rule, or reroll a villager until it sells what you want.

```
.toggle auto-trade
.setting auto-trade mode Reroll
.setting auto-trade rule mending
```

It's in the module menu under Misc. **Mode** picks what it does when you turn it on: **Trade** (the default) or
**Reroll**.

##### Trade mode: buy matching offers

It buys every offer that matches **Match**, one purchase at a time, and waits for the server to confirm each one before
the next click.

1. If a villager's trades are open, it buys from that villager.
2. Otherwise, with **Walk To Villagers** on, Baritone walks to the nearest villager within 48 blocks and opens its
   trades. It skips villagers with the wrong **Profession**, ones whose remembered offers have nothing matching, and
   ones it visited within the **Restock Wait**.
3. When a villager has nothing more to sell, it closes the trades, says in chat what it bought and how many emeralds it
   has spent, and goes to the next one.

With **Walk To Villagers** off, it waits for you to open a villager's trades. With every villager visited, it shows
"waiting for villagers to restock" until the wait passes.

##### Reroll mode: get a specific trade

Look at a villager, usually a librarian at a lectern, and turn AutoTrade on. If you're not looking at one, it takes the
nearest villager with a job within 6 blocks. It then repeats:

1. Open the trades. If an offer matches, buy it once to lock the trades in, and stop.
2. Otherwise close them, break the villager's workstation and wait until the villager loses its job.
3. Pick the block up, put it back, and wait until the villager takes the job again.

Every step waits for the game to confirm it, with a timeout and a message saying what failed. Two limits come from
the game:

- Only a villager **nobody has traded with** can be rerolled. One trade fixes its offers.
- Villagers only take jobs during the day, so it waits for daytime before breaking the workstation.

It turns itself off once the trades are locked, and says how many rerolls it took.

##### Settings

| Setting | What it does |
|---|---|
| Mode | **Trade** buys matching offers. **Reroll** rerolls the villager you look at until one matches. |
| Match | What to buy. `mending`, `unbreaking3` or `sharpness 4-5`: an enchanted book with that enchantment, at least or between those levels. `book`: any enchanted book. `slot 2`: the third trade, counting from 0. `item:bread`: offers that give that item. `any`: every offer. |
| Profession | Only villagers with this job, such as `librarian`. Empty: any. |
| Max Price | The most emeralds to pay per purchase. 0: no limit. |
| Emerald Budget | The most emeralds to spend while it's on. 0: no limit. |
| Emerald Reserve | Never spend below this many emeralds. |
| Per-Visit Cap | Purchases per villager visit. 0: no limit. |
| Restock Wait (s) | Trade mode: seconds before it visits the same villager again (300 by default). |
| Walk To Villagers | Trade mode: walk to nearby villagers and open their trades itself. On by default. |
| Offer Labels | Show each villager's best remembered offer above its head, such as "mending 1 – 14e". Works with the module off. |

It turns itself off when the budget is spent, when the next purchase would take you below the reserve, when you run
out of emeralds, and when you leave the world.

##### What it remembers

Whenever you open a villager's trades, even with AutoTrade off, it remembers that villager's offers for this world.
Those records feed **Offer Labels**, the villagers Trade mode skips, and `.enchant plan`, which finds which villagers
you've seen selling the books you name.

##### From macros and the AI

- A macro **Trade** step buys up to a set number of matching offers from trades an earlier step opened. Later steps can
  read `trade_count` (purchases), `trade_spent` (emeralds) and `trade_status` (`ok`, `none`, or `failed: <reason>`).
- The AI's `trade` tool starts AutoTrade in either mode. Run it yourself with `.tools trade`; `.tools help trade` lists
  its arguments.

### Scripting

#### Writing scripts

Beta: 5.1 beta only, not in 5.0.1 stable.

Scripts are your own bots: a mining loop that retreats home when it's hurt, a restock run, a menu-clicking sequence.
You build one in either of two ways, and both make the same kind of file:

- **Blocks**: stack plain-language steps such as *Run a command*, *Wait*, *If health is below*. No code needed. See
  `Block programming`.
- **Code**: write DihScript, a small language that looks like C or JavaScript. It can do everything blocks can, plus
  variables, your own functions, lists and maths. The rest of this guide covers it.

##### Where scripts live

Every script is one text file: `.minecraft/config/dih/scripts/<name>.dscript`. With a launcher that uses instances
(Prism, MultiMC, CurseForge, Modrinth) it's inside the instance's own `config/dih/scripts/`. `.scripts folder` prints
the exact path.

You can edit the files in any text editor, copy them to back them up, or drop a `.dscript` file in the folder to add
a script. DIH reads the file every time you run it, so a saved change counts on the next run. The first time the
folder is created DIH puts two examples in it: `hello` and `gold_miner`.

Script names use letters, digits, `_` and `-`, up to 32 characters. Names ignore capitals when you type them.

##### The Scripts window

Open it from the module menu's **Scripts** button, the Main Menu panel in container GUIs, or `.scripts edit [name]`.

| Part | What it does |
|---|---|
| Script list (left) | Every script. A green dot means it's running. Click one to open it. |
| **+ New** | Type a name and press Enter. New scripts open in Blocks. |
| **Delete** | Click twice within 3 seconds to delete the open script (stopping it first). |
| **Blocks \| Code** | Switch between the block builder and the code editor for the same script. |
| **Run** / **Stop** | Saves, checks and starts the script, or stops it while it runs. |
| **Save** | Saves the file. Switching scripts, running and closing the window also save. |
| **?** | The function reference. In Code, click a function to insert it at the cursor. |
| Status line (bottom) | The first problem in the script, the live status of a running one (time and current line), or the cursor position. |

The code editor checks the script about a third of a second after you stop typing and marks problem lines red in the
line-number gutter.

##### Running and stopping

| Command | What it does |
|---|---|
| `.scripts run <name>` | Check the script, then start it. Refused if it's already running or has problems. |
| `.scripts stop <name>` | Ask the script to stop. Its `onStop()` cleanup runs. |
| `.scripts stop <name>` again | Stop it at once, even mid-cleanup. |
| `.scripts stop` | Stop every running script (`.scripts stop all` too). |
| `.scripts list` | Every script; running ones show their uptime and current line. |
| `.scripts check <name>` | Report problems without running anything. |
| `.scripts new <name>` | Create a script from the starter template. |
| `.scripts edit [name]` | Open the Scripts window. |
| `.scripts delete <name>` | Delete a script. |
| `.scripts functions [category]` | List the built-in functions in chat. |
| `.scripts folder` | Print the scripts folder. |

Chat shows when a script starts, finishes, stops or fails, and every `print()` with the script's name in front.
Several scripts can run at the same time. Scripts don't run while panic hide has locked the client, and panic stops
any that are running.

A script keeps running if you open menus, but most game functions fail with "Not in a world" once you leave the
server, which stops the script with an error.

#### Block programming

Beta: 5.1 beta 8.2 and later.

Build a script from steps without writing code. A block script is an ordinary `.dscript` file: it runs, stops and
checks exactly like one you typed.

##### Your first block script

1. Open the Scripts window and press **+ New**. Type `hello_blocks` and press Enter. It opens in Blocks.
2. Under **When started**, press **+ Add step**, type `message` and choose **Show a message**.
3. Click the step, type `Hello from blocks!` and press **Apply** (or Enter).
4. Press **+ Step**, choose **Wait**, and set it to `2`.
5. Press **Run**. Chat shows the message, the script waits 2 seconds and finishes.

##### The two sections

| Section | When it runs |
|---|---|
| **When started** | When you run the script. Steps run once, top to bottom, then the script finishes. |
| **When finished / stopped** | Once at the end, whether the steps finished, you stopped the script, or it hit an error. Put clean-up here, such as **Stop Baritone**. |

Click a section's tab to edit it.

##### Adding and arranging steps

| Control | What it does |
|---|---|
| **+ Add step** | Add a step at the end of the list (or inside a group, where it reads **+ Add step inside**). |
| **+ Step** | Add a step right after the selected one, in the same list. |
| Search box | Type a few letters of a step or category, then click one. **Back** returns without adding. |
| Click a step | Select it. If it has a value, an editor opens under it. |
| **Up** / **Down** | Move the selected step within its list. |
| **Copy** | Duplicate the selected step, including any steps inside it. |
| **Remove** | Delete the selected step and everything inside it. |
| **Recipes** | Insert a ready-made set of steps after the existing ones. Nothing is replaced. |

Editing a value: type it, then press **Apply**, Enter or Tab. Escape cancels. A bad value, such as text where a number
goes, shows a red message and the step keeps its old value. Ctrl+Z undoes the last block edit.

Group steps, the *Repeat* and *If* steps, hold other steps and are drawn in gold with their contents indented under
them. Groups can be nested up to 32 levels deep.

##### Every block

| Block | Category | Value | What it does | Code it becomes |
|---|---|---|---|---|
| Show a message | Chat | text | Shows the text in your chat (only you see it). | `print("Hello!");` |
| Run a command | Chat | text | Runs a command as if typed: `/home` goes to the server, `#mine gold_ore` to Baritone, `.toggle flee` to DIH. | `command("/home");` |
| Send chat message | Chat | text | Sends a normal chat message everyone sees. | `say("Hello!");` |
| Wait | Timing | seconds | Pauses; decimals like `0.5` work. | `waitSeconds(1);` |
| Wait for a menu | Menus | ticks | Waits until a chest or server menu is open, up to this many ticks (20 = 1 second). | `waitForGui(100);` |
| Click a menu item | Menus | item name or id | Left-clicks the item with that name in the open menu. | `clickItem("Confirm");` |
| Click a menu slot | Menus | slot number | Left-clicks a slot, counting from 0 at the top-left. | `clickSlot(13);` |
| Close the menu | Menus | - | Closes the open screen. | `closeGui();` |
| Open a nearby container | Menus | block id | Looks at the closest such block within reach, right-clicks it and waits for its menu. | `openNearest("chest");` |
| Deposit an item | Inventory | item id, or `*` | Shift-clicks every stack of it into the open container. `*` moves everything. | `deposit("raw_gold");` |
| Withdraw an item | Inventory | item id | Shift-clicks all of it out of the open container. | `withdraw("bread");` |
| Hold an item | Inventory | item name or id | Selects the hotbar slot holding it. | `selectItem("bread");` |
| Select a hotbar slot | Inventory | 1 to 9 | Selects that hotbar slot. | `selectSlot(1);` |
| Eat until full | Inventory | - | Eats food from the hotbar until the hunger bar is full. | `eatUntilFull();` |
| Run Baritone | Movement | command without `#` | Starts a Baritone job and carries straight on. | `baritone("mine gold_ore");` |
| Stop Baritone | Movement | - | Cancels whatever Baritone is doing. | `stopBaritone();` |
| Walk to a waypoint | Movement | target | Walks there with Baritone and waits until it arrives. The value goes to `#goto`: a block name such as `chest`, a Y level, or `x y z`. Saved waypoint names don't work here yet; for those use *Run a command* with `#wp goto <name>`. | `goto("chest");` |
| Run a saved macro | Control | macro name | Runs one of your DIH macros and waits for it to finish. | `macro("my_macro");` |
| Stop this script | Control | - | Ends the script (the finished / stopped section still runs). | `stop();` |
| Repeat a number of times | Control | times | Runs the steps inside it that many times. | `repeat (3) { ... }` |
| Repeat forever | Control | - | Runs the steps inside it until the script is stopped. | `while (true) { ... }` |
| If health is below | Conditions | health points | Runs the steps inside it only when health is under the value (20 = full, 2 per heart). | `if (health() < 6) { ... }` |
| If hunger is below | Conditions | hunger points | Same for the hunger bar (20 = full). | `if (food() < 10) { ... }` |
| If another player is nearby | Conditions | radius | Another player within that many blocks. Friends, teammates and bots don't count. | `if (playerNearby(48)) { ... }` |
| If inventory is full | Conditions | - | No empty main-inventory slot. | `if (inventoryFull()) { ... }` |
| If a menu is open | Conditions | - | A chest or server menu is open. | `if (guiOpen()) { ... }` |
| If Baritone is busy | Conditions | - | Baritone is mining, walking or farming. | `if (baritoneBusy()) { ... }` |
| If carrying an item | Conditions | item name or id | You carry at least one. | `if (hasItem("bread")) { ... }` |

Numbers can't be negative. Values for *Repeat a number of times*, *Wait for a menu* and *Click a menu slot* must be
whole numbers.

##### Recipes

| Recipe | What it adds |
|---|---|
| Say hello three times | Repeat 3: show "Hello!" and wait 1 second. |
| Mine gold; retreat on low health | Run Baritone `mine gold_ore`, then repeat forever: if health is below 6, stop Baritone, run `/home` and stop; wait 1 second. Its finished section stops Baritone. |
| Open a menu and click Confirm | Run `/menu`, wait for a menu, click "Confirm". Change `/menu` to your server's command. |

##### Patterns

A *Repeat forever* loop needs a wait inside it, or it checks thousands of times a second. Put the checks at the top
and one **Wait** at the bottom:

```
When started
  Run Baritone: mine gold_ore
  Repeat forever
    If another player is nearby: 48
      Stop Baritone
      Run a command: /home
      Stop this script
    If health is below: 8
      Eat until full
    Wait: 0.5
When finished / stopped
  Stop Baritone
```

Navigating a server menu, with a wait after each click so the next page can load:

```
When started
  Run a command: /warp
  Wait for a menu: 100
  Click a menu item: Mines
  Wait: 0.5
  Click a menu slot: 13
```

##### Switching between Blocks and Code

**Blocks | Code** switches views on the same file. Code shows exactly what the blocks make, so blocks are a good way to
learn DihScript.

- Switching views never changes the file by itself.
- A block edit rewrites the whole file from the blocks: formatting is redone and comments are dropped. Ctrl+Z
  brings the previous text back.
- A script only opens as blocks when every line is something a block can show. Otherwise it opens in Code, with the
  message "This script uses advanced code", and nothing is lost.

A script can be shown as blocks when it has only `main()` and `onStop()`, no parameters, no top-level `let` or
`const`, and its lines are only the calls in the table above with plain text or number values, `repeat (n)`,
`while (true)` and the *If* conditions above with no `else`. Variables, your own functions, `else`, `&&`, maths and
other functions keep it in Code. You can always write it in Code and keep running it from there.

#### Script language

Beta: 5.1 beta only, not in 5.0.1 stable.

The rules of DihScript. Every example is a complete script you can paste in and run unless it says otherwise.

##### Program structure

A script file holds three kinds of things at the top level, in any order:

```js
// 1. constants and variables shared by every function
const ORE = "gold_ore";
let trips = 0;

// 2. main(): required, runs when the script starts
function main() {
    trips += 1;
    print("Trip " + trips + ": mining " + ORE);
    helper();
}

// 3. your own functions, called from main() or each other
function helper() {
    print("helping");
}

// optional: onStop() runs once whenever the script ends
function onStop() {
    print("done");
}
```

Statements such as `print(...)` can't sit at the top level; they go inside a function. Top-level `let` and `const`
run once, top to bottom, before `main()`.

When `main()` returns, the script is finished. `onStop()` then runs, whether `main()` returned, you stopped the
script, `stop()` was called, or an error ended it.

##### Statements and comments

Each statement ends with `;`. Blocks of statements go in `{ }`, and the braces are required after `if`, `else`,
`while`, `repeat` and `for`, even for one statement. Spaces, tabs and line breaks don't matter.

```js
// a comment runs to the end of the line
/* a block comment
   can span lines */
function main() {
    print("one"); print("two");   // two statements on one line is fine
}
```

##### Names

Names of variables and functions start with a letter or `_`, then letters, digits or `_`: `gold`, `trip_count`,
`_tmp2`. Capitals matter: `Gold` and `gold` are different. Write constants in capitals (`LOW_HEALTH`) by convention.

These words are reserved and can't be names: `function let const if else while for in repeat break continue return
true false null`.

##### Values

| Type | Examples | Notes |
|---|---|---|
| number | `20`, `0.5`, `-3` | All numbers are decimals underneath. Whole numbers print without `.0`. There is no `1e3` form. |
| text | `"gold_ore"`, `'Badlands'` | Single or double quotes. Escapes: `\n` new line, `\t` tab, `\r`, `\"`, `\'`, `\\`. A string can't span lines. |
| boolean | `true`, `false` | |
| list | `[1, 2, "three"]`, `[]` | Holds any values, including other lists. See `Lists and text`. |
| nothing | `null` | What a missing value or a function without `return` gives. |

`type(value)` gives `"number"`, `"string"`, `"boolean"`, `"list"` or `"null"`.

##### True and false

`if`, `while`, `&&`, `||` and `!` accept any value. These count as false: `false`, `null`, `0`, `""` (empty text) and
`[]` (empty list). Everything else counts as true, including `"0"` and `" "`.

```js
function main() {
    let name = nearestPlayer(48);   // a name, or null
    if (name) {
        print("Someone is near: " + name);
    } else {
        print("Nobody around");
    }
}
```

##### Variables and constants

```js
const LIMIT = 5;          // can never change
let count = 0;            // can change
let nothing;              // starts as null

function main() {
    count = count + 1;
    count += 2;           // also -=, *=, /=
    print(count);         // 3
}
```

- Declare before use. Assigning to a name you never declared is an error that tells you to add `let`.
- Changing a `const` is an error.
- A variable lives in the `{ }` it was declared in, and everything nested inside it. Top-level ones are visible in
  every function.
- An inner block can declare a name that also exists outside it; inside, the inner one wins. Declaring the same name
  twice in the same block is an error. A function's parameters count as declared in its body.
- Top-level variables keep their value for the whole run, so functions can share state through them.

##### Operators

From tightest to loosest:

| Operators | Meaning | Notes |
|---|---|---|
| `x(...)`, `x[i]` | call, index | |
| `!x`, `-x` | not, negate | `-` needs a number. |
| `*` `/` `%` | times, divide, remainder | Dividing by 0 is an error. `%` keeps the sign of the left side. |
| `+` `-` | plus, minus | `+` joins text if either side is text, and joins two lists. |
| `<` `<=` `>` `>=` | compare | Numbers with numbers, or text with text (dictionary order). |
| `==` `!=` | equal, not equal | Works on any values. Lists are equal when their items are. |
| `&&` | and | Stops at the first false value and gives it. |
| `\|\|` | or | Stops at the first true value and gives it. |
| `=` `+=` `-=` `*=` `/=` | assign | Gives the new value. |

Use brackets to group: `(a + b) * 2`. `&&` and `||` skip the right side when the left already decides, so
`hasItem("bread") && eat()` only eats when you have bread. `||` is handy for defaults:
`let who = nearestPlayer(64) || "nobody";`.

There's no `++`, no `? :`, and single `&` or `|` are errors.

##### if and else

```js
function main() {
    let hp = health();
    if (hp >= 18) {
        print("Healthy");
    } else if (hp >= 8) {
        print("Hurt");
    } else {
        print("In danger");
    }
}
```

##### Loops

```js
function main() {
    // while: repeat as long as the condition is true
    while (food() < 20 && eat()) {
        print("ate, hunger " + food());
    }

    // repeat: a set number of times (the number is rounded down; 0 or less runs nothing)
    repeat (3) {
        print("tick");
        wait(20);
    }

    // for-in: once per item of a list, or per character of text
    for (let name in playersNearby(64)) {
        print("nearby: " + name);
    }
    for (let i in range(1, 4)) {
        print(i);                  // 1 2 3
    }
}
```

- `break;` leaves the loop it's in. `continue;` skips to the next round. Using either outside a loop is an error.
- The loop variable of `for` exists only inside the loop. The loop walks a copy of the list, so changing the list
  inside the loop doesn't change which items it visits.
- A loop that runs for a long time must `wait`, or it wastes CPU checking thousands of times a second. See
  `Timing and the game`.

##### Functions

```js
// parameters are just names; return gives a value back
function percent(part, whole) {
    if (whole == 0) {
        return 0;
    }
    return round(part * 100 / whole);
}

function main() {
    print("Health: " + percent(health(), maxHealth()) + "%");
}
```

- Declare functions at the top level only. Their order doesn't matter.
- Call with fewer arguments and the missing ones are `null`. Calling with more is an error.
- `return;` or reaching the end gives `null`.
- A function can call itself, up to 200 calls deep; deeper is an error ("is it calling itself forever?").
- Defining two functions with the same name is an error. A function with the same name as a built-in replaces the
  built-in for the whole script, so pick names the built-ins don't use.
- Functions aren't values: you can't store one in a variable or pass it to another function.

##### Lists and text

```js
function main() {
    let ores = ["gold_ore", "iron_ore"];
    push(ores, "coal_ore");        // add to the end
    print(len(ores), ores[0], ores[-1]);   // 3 gold_ore coal_ore
    ores[1] = "copper_ore";        // replace an item
    let last = pop(ores);          // remove and return the last item
    print(contains(ores, "gold_ore"), last);
    print(join(ores, " + "));      // gold_ore + copper_ore

    let parts = split("a,b,c", ",");   // ["a", "b", "c"]
    print(upper(parts[1]), "abc"[0], len("hello"));
}
```

- Indexes start at 0. Negative indexes count from the end: `-1` is the last item. An index outside the list, or one
  that isn't a whole number, is an error.
- Indexing text gives one character as text. You can't assign into text; build a new string instead.
- A list is shared, not copied: `let b = a;` makes both names point at the same list, so `push(b, 1)` changes `a`
  too. `a + []` makes a copy.
- `+` joins lists: `[1, 2] + [3]` is `[1, 2, 3]`.

##### Numbers and conversion

Whole numbers print without a decimal point (`3`, not `3.0`); others print in full (`3.5`). `str(x)` turns anything
into text, and `num("12")` turns text into a number, giving `null` when it isn't one. Text joins with `+` convert for
you: `"Trips: " + 3` is `"Trips: 3"`.

Rounding helpers: `floor`, `ceil`, `round`. `random(1, 6)` is a whole number from 1 to 6.

#### Timing and the game

Beta: 5.1 beta only, not in 5.0.1 stable.

How scripts run alongside Minecraft, and the rules every game function follows.

##### Ticks and waiting

Minecraft runs 20 ticks a second. `wait(ticks)` pauses the script for that many ticks and `waitSeconds(s)` for that
many seconds (`waitSeconds(0.5)` is 10 ticks). The game keeps running while a script waits.

Each script runs on its own thread, so even a script that never waits can't freeze the game. It does burn CPU, though:
after 20 000 statements in a row without a wait, DIH makes it pause for one tick. A loop that watches for something
should wait between checks; `wait(5)` (4 checks a second) is plenty for most bots.

```js
function main() {
    let start = time();
    waitSeconds(2);
    print("waited " + (time() - start) + " ms");
}
```

##### Stopping, and onStop()

A script ends when `main()` returns, when it calls `stop()`, when an error happens, or when you run `.scripts stop`.
In every case `onStop()` then runs once, if the script has one.

- Inside `onStop()`, a single stop request doesn't interrupt it, and `wait` still works, so cleanup can finish.
- A second `.scripts stop` ends it at once.
- `onStop()` can't take parameters.

```js
function main() {
    baritone("mine diamond_ore");
    while (true) { wait(20); }
}

function onStop() {
    stopBaritone();          // never leave Baritone running after the script ends
    print("Stopped mining.");
}
```

##### Commands and chat

`command(text)` runs `text` as if you typed it in chat, routed by its first character:

| Starts with | Goes to | Example |
|---|---|---|
| `#` | Baritone | `command("#mine gold_ore")` |
| your DIH prefix (`.`, or `%` with Meteor) | DIH | `command(".toggle flee")` |
| anything else | the server, as a command (the `/` is optional) | `command("/home")`, `command("spawn")` |

`say(text)` sends a normal chat message instead. `print(...)` only shows text to you.

##### Baritone

`baritone("mine gold_ore")` starts a Baritone job and returns at once; the script keeps running while Baritone works.
Use `baritoneBusy()` to see whether it's still going, `waitForBaritone(ticks)` to wait until it's idle, and
`stopBaritone()` to cancel it. `goto(...)` walks and waits until Baritone arrives: `goto("chest")` goes to the nearest
known chest, `goto(100, 64, -200)` to coordinates. For a saved waypoint, use `command("#wp goto home")`.

Baritone settings work too: `baritone("set itemSaver true")` makes it skip tools close to breaking, and
`baritone("pause")` / `baritone("resume")` pause the current job, for example while eating.

##### Item and block names

Item functions accept an item id (`raw_gold`, `minecraft:raw_gold`, `Raw Gold` with spaces is read as
`raw_gold`) or the name shown on the item in game (`"Raw Gold"`, or a custom name such as `"Lucky Pick"`). Names
ignore capitals and punctuation. `"*"` matches any item in `deposit`, `withdraw`, `count` and similar. Block
functions (`findBlock`, `openNearest`) take block ids such as `chest` or `ender_chest`.

##### Slot numbers

There are two numberings.

**GUI slots**, used by `clickSlot`, `slotItem`, `slotName`, `slotCount` and `findSlot`, count from 0 at the top-left
of the open menu, left to right and row by row:

```
row 1:   0  1  2  3  4  5  6  7  8
row 2:   9 10 11 12 13 14 15 16 17
row 3:  18 19 20 21 22 23 24 25 26
```

A double chest goes on to 53. `guiSize()` is the menu's own slot count; your inventory's slots follow right after it.

**Inventory slots**, used by `inventorySlot`, `clickInventory` and `durability`, are 1-36: 1-9 is the hotbar, the
same as the number keys, and 10-36 the three rows above it from the top-left. `inventorySlot(n)` converts one to the
GUI number of the open screen, so `clickSlot(inventorySlot(1))` clicks your first hotbar slot in any menu.

To find the numbers of a server menu's buttons, open the menu and run this:

```js
function main() {
    for (let i in range(guiSize())) {
        if (slotName(i) != null) { print(i + ": " + slotName(i)); }
    }
}
```

##### Menus and containers

- `waitForGui(ticks)` waits for a chest or server menu to open; it returns `false` if it timed out. Check the result
  instead of clicking blind. `waitForGui(100, "Shop")` also requires the title to contain "Shop".
- `waitForItem("Confirm", 100)` waits for an item to appear in the open menu, which is the safest way to wait for a
  menu's next page.
- `clickItem(name)` clicks the menu item with that exact shown name, else one with that item id, else the first whose
  name contains it. It ignores your own inventory while a menu is open. It returns `false` if nothing matched.
- Clicks wait a tick or two afterwards so the server can answer before the next click.
- `deposit`, `withdraw` and `fillSlots` need an open container and shift-click one stack per tick. They return how
  many items or slots they managed, and stop early when there's no room.
- `openNearest("chest")` only reaches blocks within about 5 blocks of your eyes. Walk closer first with
  `goto("chest")`.

##### Eating and tools

- `eat()` eats the first food in your hotbar, left to right, and returns `false` if there's none. It eats anything
  edible, including golden apples, chorus fruit and rotten flesh, so keep only the food you want it to use in the
  hotbar.
- `eatUntilFull()` keeps eating until the hunger bar is full or the food runs out.
- `heldDurability()`, `durability(slot)`, `pickaxeCount(min)` and `equipPickaxe(min)` let a bot swap off a worn
  pickaxe. `equipPickaxe` picks the strongest material first, then the most uses left, and swaps it into your
  selected hotbar slot from your inventory if needed.

##### Players nearby

`playerNearby(r)`, `nearestPlayer(r)` and `playersNearby(r)` ignore you, spectators, your friends and teammates (the
Teams module) and players the AntiBot module marks as bots.

#### Errors and debugging

Beta: 5.1 beta only, not in 5.0.1 stable.

##### Checked before running

`.scripts run`, `.scripts check` and the editor's live check all catch these before anything runs:

- syntax errors: a missing `;`, `)` or `}`, an unclosed string or comment, a stray character
- statements at the top level, or a function declared inside another
- no `main()` function, or an `onStop()` with parameters
- calls to functions that don't exist, with a "did you mean" hint for near misses
- the wrong number of arguments to a built-in, or too many to one of your own functions
- `break` or `continue` outside a loop

Each problem gives its line, and column for syntax errors: `line 12:5: Expected ';' after the statement but found
'print'`.

##### Errors while running

Some mistakes only show up while running. They stop the script, print the line in chat, and run `onStop()`.

| Message | Cause |
|---|---|
| `Unknown variable 'x'` | Used a name you never declared, or misspelt it. |
| `Unknown variable 'x' — declare it first with: let x = ...;` | Assigned to an undeclared name. |
| `Cannot change const 'X'` | Assigned to a constant. |
| `'x' is already declared in this scope` | Two `let`s of the same name in one block, or a `let` with a parameter's name. |
| `Cannot apply '-' to string and number` | Maths on the wrong types. Use `num(...)` to convert text. |
| `Cannot compare string with number` | `<` and friends only compare numbers with numbers or text with text. |
| `Division by zero` | `/` or `%` by 0. |
| `Index 5 is out of range (size 3)` | List or text index too big or too small. |
| `Too many nested calls (over 200)` | A function calling itself without end. |
| `Not in a world` | A game function ran while you weren't in a world. |
| `no container is open` | `deposit`, `withdraw` or `fillSlots` with no chest open. |
| `The game did not respond` | The game was busy for 5 seconds; usually a lag spike. |
| `error("...")` text | Your own `error()` call. |

##### Debugging tips

- `print` what you're unsure about: `print("health", health(), "food", food());`. Values are joined with spaces.
- `.scripts list` shows the line and function each running script is on.
- Add a `waitSeconds(1)` after each step while testing, so you can watch it happen.
- Test new scripts in a singleplayer world first.
- Use constants for numbers you'll tune (`const LOW_HEALTH = 5;`), and `error(...)` early when a setting is missing,
  as the gold miner does for its slot numbers.

#### Script functions

Beta: 5.1 beta only, not in 5.0.1 stable.

Every built-in function, by category, as listed in game by `.scripts functions <category>` and the editor's **?**
button. Arguments can be left off from the right where the description gives a default: `waitForGui()` waits 100
ticks. Item and block names follow the rules in `Timing and the game`.

##### Control

| Function | What it does |
|---|---|
| `print(value, ...)` | Show a message in chat (values are joined with spaces). |
| `wait(ticks)` | Pause for a number of game ticks (20 ticks = 1 second). |
| `waitSeconds(seconds)` | Pause for a number of seconds (decimals allowed). |
| `stop()` | End the script right now (onStop() still runs). |
| `error(message)` | Stop the script with an error message. |
| `time()` | Milliseconds since 1970 — use it to measure how long things take. |

##### Player

| Function | What it does |
|---|---|
| `inWorld()` | True while you are in a world/server. |
| `health()` | Your health (20 = full, 2 per heart). |
| `maxHealth()` | Your maximum health. |
| `food()` | Your hunger bar (20 = full). |
| `x()` | Your X coordinate. |
| `y()` | Your Y coordinate. |
| `z()` | Your Z coordinate. |
| `dimension()` | "overworld", "the_nether" or "the_end". |
| `playerName()` | Your username. |
| `playerNearby(radius)` | True if another player is within radius blocks (default 32). Friends, teammates and bots don't count. |
| `nearestPlayer(radius)` | Name of the closest other player within radius (default 32), or null. |
| `playersNearby(radius)` | List of other players' names within radius (default 32), closest first. |
| `eat()` | Eat one food item from your hotbar. Returns false if there is nothing to eat. |
| `eatUntilFull()` | Keep eating until the hunger bar is full or you run out of food. Returns true if full. |

##### Chat

| Function | What it does |
|---|---|
| `command(text)` | Run a command as if typed in chat: "/home" (server), "#mine gold_ore" (Baritone) or ".toggle flee" (DIH). |
| `say(text)` | Send a plain chat message. |

##### Baritone

| Function | What it does |
|---|---|
| `baritone(command)` | Run a Baritone command without the #, e.g. baritone("mine gold_ore"). |
| `stopBaritone()` | Cancel whatever Baritone is doing. |
| `baritoneBusy()` | True while Baritone is mining, pathing, farming... |
| `waitForBaritone(timeoutTicks)` | Wait until Baritone is idle. Returns false if it timed out (default: no timeout). |
| `goto(target, ...)` | Walk with Baritone and wait until it arrives: goto("chest") or goto(x, y, z). |

##### Inventory

| Function | What it does |
|---|---|
| `count(item)` | How many of an item you carry, e.g. count("raw_gold"). |
| `hasItem(item)` | True if you carry at least one of the item. |
| `freeSlots()` | Number of empty main-inventory slots (0-36). |
| `inventoryFull(item)` | True when there is no empty slot left. With an item, e.g. inventoryFull("raw_gold"), only true once no slot is empty AND every stack of that item is full (64), so stacks with room don't count as full. |
| `roomFor(item)` | How many more of item fit in your main inventory: empty slots plus the space left on stacks of it. |
| `heldItem()` | Id of the item in your main hand, e.g. "diamond_pickaxe" ("air" if empty). |
| `heldDurability()` | Uses left on the item in your main hand before it breaks, or -1 if it can't break. |
| `durability(slot)` | Uses left on the item in inventory slot 1-36 (1-9 = hotbar), or -1 if it can't break or the slot is empty. |
| `pickaxeCount(minDurability)` | How many pickaxes you carry with at least minDurability uses left (default 1). |
| `equipPickaxe(minDurability)` | Hold your best pickaxe that has at least minDurability uses left (default 1), moving it into the hotbar if needed. Returns false if you have none. |
| `selectSlot(slot)` | Select hotbar slot 1-9. |
| `selectItem(item)` | Select the hotbar slot holding item. Returns false if it isn't in the hotbar. |

##### GUI

| Function | What it does |
|---|---|
| `guiOpen()` | True while a chest or other container GUI is open. |
| `guiTitle()` | Title of the open screen, or "" if none. |
| `waitForGui(timeoutTicks, title)` | Wait for a container GUI to open (optionally with a title containing text). Returns false on timeout (default 100 ticks). |
| `waitForItem(name, timeoutTicks)` | Wait until an item with this name shows up in the open GUI. Returns false on timeout (default 100 ticks). |
| `clickItem(name, button)` | Click the item in the open GUI whose name (or id) matches, e.g. clickItem("Badlands"). button: 0 left (default), 1 right. Returns false if not found. |
| `clickSlot(slot, button, shift)` | Click a GUI slot by number: 0 is the top-left slot of the menu (0-26 in a 27-slot menu), your inventory comes after guiSize(). button 0 left, 1 right; shift=true shift-clicks. |
| `guiSize()` | Number of the open GUI's own slots (27 for a chest or most server menus, 54 for a double chest; 0 if none). Your inventory comes after these. |
| `slotItem(slot)` | Item id in a GUI slot, e.g. "raw_gold", or null if empty. |
| `slotName(slot)` | Name shown on the item in a GUI slot, or null if empty. |
| `slotCount(slot)` | How many items are in a GUI slot (0 if empty). |
| `findSlot(item)` | First of the GUI's own slots holding item (by id or name), or -1. |
| `inventorySlot(n)` | GUI slot number of your own inventory slot n (1-9 hotbar, 10-36 the rows above), for clickSlot(). -1 if not shown. |
| `clickInventory(n, button, shift)` | Click your own inventory slot n (1-9 hotbar, 10-36 the rows above). Works with a chest open too. |
| `fillSlots(item, count)` | Take item from the open container and put exactly 1 in each of count empty inventory slots. Returns how many slots were filled. |
| `deposit(item)` | Shift-click every stack of item from your inventory into the open container. "*" moves everything. Returns the number of items moved. |
| `withdraw(item, amount)` | Shift-click stacks of item from the open container into your inventory until you have taken at least amount (default: all). Returns the number moved. |
| `closeGui()` | Close the open screen. |

##### World

| Function | What it does |
|---|---|
| `findBlock(block, radius)` | Closest block of this type within radius (default 16, max 32) as [x, y, z], or null. |
| `lookAt(x, y, z)` | Turn to face a position. |
| `openNearest(block, radius)` | Look at and right-click the closest block of this type within reach (radius default 5), then wait for its GUI. Returns true if a GUI opened. |
| `macro(name)` | Run one of your saved DIH macros and wait for it to finish. Returns false if it doesn't exist. |

##### Math

| Function | What it does |
|---|---|
| `abs(x)` | Absolute value. |
| `floor(x)` | Round down. |
| `ceil(x)` | Round up. |
| `round(x)` | Round to the nearest whole number. |
| `sqrt(x)` | Square root. |
| `min(a, b, ...)` | Smallest of the numbers. |
| `max(a, b, ...)` | Largest of the numbers. |
| `random(min, max)` | Random whole number from min to max (both included). |

##### Text

| Function | What it does |
|---|---|
| `str(value)` | Turn any value into text. |
| `num(text)` | Turn text into a number (null if it isn't one). |
| `type(value)` | Type name: number, string, boolean, list or null. |
| `lower(text)` | Lower-case copy of the text. |
| `upper(text)` | Upper-case copy of the text. |
| `trim(text)` | Text without leading/trailing spaces. |
| `startsWith(text, prefix)` | True if the text begins with prefix. |
| `endsWith(text, suffix)` | True if the text ends with suffix. |
| `replace(text, find, with)` | Replace every occurrence of find. |
| `split(text, separator)` | Split text into a list. |
| `join(list, separator)` | Join list items into text. |

##### Lists

| Function | What it does |
|---|---|
| `len(value)` | Length of a list or string. |
| `contains(listOrText, value)` | True if the list holds value (or the text contains it). |
| `push(list, value)` | Add value to the end of the list. |
| `pop(list)` | Remove and return the last item (null if empty). |
| `removeAt(list, index)` | Remove and return the item at index. |
| `range(start, end)` | List of whole numbers from start up to (not including) end. |

#### Script examples

Beta: 5.1 beta only, not in 5.0.1 stable.

Short complete scripts showing common jobs. Change the names and numbers to fit your server.

##### Wait for something with a timeout

```js
// Waits up to 30 seconds for Baritone to finish; gives up instead of hanging forever.
function main() {
    baritone("goto 100 64 -200");
    if (!waitForBaritone(30 * 20)) {
        stopBaritone();
        error("Didn't arrive within 30 seconds");
    }
    print("Arrived");
}
```

##### Retry a flaky step

```js
// Tries to open the nearest chest up to 3 times.
function openChestWithRetries() {
    repeat (3) {
        if (openNearest("chest")) {
            return true;
        }
        waitSeconds(1);
    }
    return false;
}

function main() {
    if (!openChestWithRetries()) {
        error("No chest in reach");
    }
    print("Stored " + deposit("*") + " items");
    closeGui();
}
```

##### Click through a server menu by slot

```js
const MENU_SLOT = 13;     // button on the first page
const CONFIRM_SLOT = 22;  // button on the second page

function main() {
    command("/shop");
    if (!waitForGui(100)) { error("/shop did not open"); }
    clickSlot(MENU_SLOT);
    if (!waitForItem("Confirm", 100)) { error("No Confirm button"); }
    clickSlot(CONFIRM_SLOT);
    closeGui();
}
```

##### Keep a list of what happened

```js
let log = [];

function note(text) {
    push(log, text);
    print(text);
}

function main() {
    note("Health " + health());
    note("Players: " + join(playersNearby(64), ", "));
    note("Raw gold: " + count("raw_gold"));
}

function onStop() {
    print("Summary: " + len(log) + " notes");
}
```

##### Watch for danger while a job runs

```js
const SAFE_RADIUS = 32;

function main() {
    baritone("mine iron_ore");
    while (baritoneBusy()) {
        if (playerNearby(SAFE_RADIUS) || health() < 10) {
            stopBaritone();
            command("/spawn");
            stop();
        }
        if (food() < 10) {
            baritone("pause");
            eatUntilFull();
            baritone("resume");
        }
        wait(5);
    }
}

function onStop() {
    stopBaritone();
}
```

#### Gold miner script

Beta: 5.1 beta only, not in 5.0.1 stable. Needs beta 8.3 or later.

A complete bot, following this plan:

1. Start: `/rtpmenu`, click **Overworld**, then **Badlands**.
2. `#mine gold_ore`, checking 4 times a second:
   - hunger at 4 drumsticks or less: pause, eat until full, keep mining
   - pickaxe worn out: switch to the next pickaxe; with none left, `/home` and end
   - a player nearby, low health, or no room for more raw gold: `/home`
3. At home: eat and heal until health and hunger are full, put the raw gold in the chest, take 27 back with 1 in each
   slot, then back to step 1's `/rtpmenu`.

Make a script for it, paste this in, set the two slot numbers at the top, then run it:

```
.scripts new gold_miner
.scripts edit gold_miner
.scripts run gold_miner
.scripts stop gold_miner
```

```js
// gold_miner.dscript
// start -> /rtpmenu -> Overworld -> Badlands -> #mine gold_ore
// While mining:
//   - hunger at 4 drumsticks or less  -> pause, eat until full, keep mining
//   - pickaxe worn out                -> use the next pickaxe; none left -> /home -> end
//   - player nearby or low health     -> /home -> eat + heal to full
//                                        -> store gold, leave 27 (1 per slot) -> /rtpmenu again
//   - inventory full of raw gold      -> same as above
//
// Run:  .scripts run gold_miner      Stop: .scripts stop gold_miner

// ---- set these two -------------------------------------------------------
const OVERWORLD_SLOT = -1;    // slot number of the Overworld button in /rtpmenu
const BADLANDS_SLOT  = -1;    // slot number of the Badlands button
// ---------------------------------------------------------------------------

const HOME_COMMAND   = "/home";
const ORE            = "gold_ore";
const LOW_HEALTH     = 5;     // 20 = full health, 2 per heart
const LOW_HUNGER     = 8;     // 20 = full, 2 per drumstick: 8 = 4 drumsticks
const SAFE_RADIUS    = 48;    // how close a player can get (blocks)
const KEEP_GOLD      = 27;    // raw gold to leave in the inventory, 1 per slot
const MIN_DURABILITY = 20;    // a pickaxe with fewer uses left counts as worn out
const MENU_TIMEOUT   = 100;   // ticks to wait for a menu (100 = 5 seconds)

let warnedNoFood = false;

function main() {
    if (OVERWORLD_SLOT < 0 || BADLANDS_SLOT < 0) {
        error("Set OVERWORLD_SLOT and BADLANDS_SLOT at the top of the script first");
    }
    // Baritone skips tools with fewer than MIN_DURABILITY uses left
    baritone("set itemSaver true");
    baritone("set itemSaverThreshold " + MIN_DURABILITY);

    if (!equipPickaxe(MIN_DURABILITY)) {
        error("You have no pickaxe with at least " + MIN_DURABILITY + " durability");
    }

    travelToBadlands();                         // start -> /rtpmenu -> Overworld -> Badlands
    while (true) {
        let reason = goldMining();
        print("Going home: " + reason);
        goHome();
        if (reason == "no pickaxe left") {
            print("No usable pickaxe left - ending here.");
            stop();                             // /home -> end
        }
        atHome();                               // eat + heal, then chest
        travelToBadlands();                     // back to /rtpmenu
    }
}

// ---------------------------------------------------------------- mining

// Mines until something sends it home, then returns why.
function goldMining() {
    baritone("mine " + ORE);                    // #mine gold_ore
    while (true) {
        if (playerNearby(SAFE_RADIUS)) { return "player nearby (" + nearestPlayer(SAFE_RADIUS) + ")"; }
        if (health() <= LOW_HEALTH) { return "low health (" + health() + ")"; }
        if (inventoryFull("raw_gold")) { return "inventory full"; }
        if (!checkPickaxe()) { return "no pickaxe left"; }
        if (food() <= LOW_HUNGER) { eatWhileMining(); }
        wait(5);                                // check 4 times a second
    }
}

// Hunger bar low -> pause mining, eat until the bar is full, carry on.
function eatWhileMining() {
    baritone("pause");
    wait(5);
    let ate = false;
    while (food() < 20 && eat()) {
        ate = true;
    }
    baritone("resume");
    if (ate) {
        warnedNoFood = false;
        print("Ate - hunger now " + food() + "/20");
    } else if (!warnedNoFood) {
        warnedNoFood = true;
        print("Hunger is low but there is no food in the hotbar");
    }
}

// Pickaxe durability low -> use the next pickaxe. Returns false when none is left.
function checkPickaxe() {
    if (pickaxeCount(MIN_DURABILITY) == 0) {
        return false;
    }
    if (contains(heldItem(), "pickaxe") && heldDurability() < MIN_DURABILITY) {
        print("Pickaxe worn out (" + heldDurability() + " left) - switching");
        stopBaritone();
        if (!equipPickaxe(MIN_DURABILITY)) {
            return false;
        }
        baritone("mine " + ORE);                // back to #mine gold_ore with the new pickaxe
    }
    return true;
}

function goHome() {
    stopBaritone();                             // stop #mine
    command(HOME_COMMAND);
    waitSeconds(5);                             // teleport warm-up
}

// ---------------------------------------------------------------- at home

function atHome() {
    // eat and heal to full health and a full hunger bar
    eatAndHeal();

    // go to the chest
    if (findBlock("chest", 5) == null) {
        goto("chest");
    }
    if (!openNearest("chest")) {
        error("No chest found near home");
    }

    // deposit the gold, then leave 27, 1 in each inventory slot
    let stored = deposit("raw_gold");
    print("Stored " + stored + " raw gold");
    let filled = fillSlots("raw_gold", KEEP_GOLD);
    print("Filled " + filled + " slots with raw gold");
    closeGui();

    equipPickaxe(MIN_DURABILITY);
}

function eatAndHeal() {
    while (health() < maxHealth() || food() < 20) {
        if (food() < 20) {
            if (!eat()) {
                print("Out of food");
                if (health() >= maxHealth()) { return; }   // can't fill hunger, but health is full
                waitSeconds(2);                            // wait for health to regenerate
            }
        } else {
            waitSeconds(1);                                // hunger full -> health regenerates
        }
    }
}

// ---------------------------------------------------------------- teleport

function travelToBadlands() {
    command("/rtpmenu");
    if (!waitForGui(MENU_TIMEOUT)) {
        error("/rtpmenu did not open");
    }
    wait(5);                                    // let the menu fill in
    print("Clicking Overworld: slot " + OVERWORLD_SLOT + " (" + slotName(OVERWORLD_SLOT) + ")");
    clickSlot(OVERWORLD_SLOT);

    wait(10);                                   // the Badlands menu opens or replaces this one
    if (!waitForGui(MENU_TIMEOUT)) {
        error("The biome menu did not open after clicking Overworld");
    }
    wait(5);
    print("Clicking Badlands: slot " + BADLANDS_SLOT + " (" + slotName(BADLANDS_SLOT) + ")");
    clickSlot(BADLANDS_SLOT);

    waitSeconds(8);                             // let the teleport land and chunks load
}

// runs whenever the script stops
function onStop() {
    stopBaritone();
}
```

##### Finding the two slot numbers

The script clicks the RTP menu's buttons by slot number: 0 is the top-left slot, counting left to right and row by
row (row 2 starts at 9, row 3 at 18). To find them, make a script called `slots`:

```js
function main() {
    for (let i in range(guiSize())) {
        if (slotName(i) != null) { print(i + ": " + slotName(i)); }
    }
}
```

Type `/rtpmenu`, then `.scripts run slots`: it prints every button with its number. The number next to Overworld goes
in `OVERWORLD_SLOT`. Click Overworld, run it again, and the number next to Badlands goes in `BADLANDS_SLOT`. Until
both are set, the gold miner stops with a message instead of clicking.

##### Before you run it

- Set your home within 5 blocks of a chest, or Baritone walks to the nearest chest it can see. If your server's home
  command isn't `/home`, change `HOME_COMMAND`.
- Carry at least one pickaxe with 20 or more uses left (`MIN_DURABILITY`), and spares if you like: worn ones are
  swapped out, and with none left the script goes home and ends.
- Keep only the food you want eaten in your hotbar: `eat()` takes the first food it finds there, golden apples
  included.
- `fillSlots("raw_gold", 27)` fills empty slots only; with fewer than 27 free it fills what it can and says how many.
- `.scripts check gold_miner` finds typos before you run it.

#### Editor keys

Beta: 5.1 beta only, not in 5.0.1 stable.

##### Code editor

| Keys | What they do |
|---|---|
| Ctrl+S | Save. |
| Ctrl+Enter | Run, or stop if running. |
| Ctrl+Z, Ctrl+Y or Ctrl+Shift+Z | Undo, redo. |
| Ctrl+/ | Comment or uncomment the line or the selected lines. |
| Tab, Shift+Tab | Indent, outdent. With several lines selected, every line moves. |
| Enter | New line, keeping the indent; after `{` it indents one more level. Typing `}` on an empty line outdents. |
| Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V | Select all, copy, cut, paste. With nothing selected, copy and cut take the whole line. |
| Arrows, Home, End, Page Up, Page Down | Move. Hold Shift to select. Home jumps to the first non-space character, then to column 1. |
| Ctrl+Left, Ctrl+Right, Ctrl+Backspace | Move or delete by word. |
| Ctrl+Home, Ctrl+End | Start or end of the script. |
| Click, drag, double-click, Shift+click | Place the cursor, select, select a word, extend the selection. |
| Mouse wheel, Shift+wheel | Scroll up and down, left and right. |
| Esc | Leave the editor so game keys work again. |

##### Block value fields

| Keys | What they do |
|---|---|
| Enter, Tab | Apply the value. |
| Esc | Cancel the edit. |
| Ctrl+A, Ctrl+C, Ctrl+X, Ctrl+V | Select all, copy, cut, paste (line breaks become spaces). |
| Ctrl+Z, Ctrl+Y | Undo, redo inside the field. |
