# DIH Client — Command Manual

Every chat command in DIH Client, what it does, and how to use it. Written from the source in
`src/main/java/dihclient/commands/impl/` (dot commands) and
`src/main/java/baritone/command/defaults/` (`#` commands).

> Minecraft **26.2** · Fabric Loader **0.19.3+** · Java **25**

---

## Contents

1. [The basics](#1-the-basics)
2. [Reading this manual](#2-reading-this-manual)
3. [Client (`.`) commands](#3-client--commands)
   - [Help & info](#31-help--info)
   - [Modules & settings](#32-modules--settings)
   - [Keybinds & prefix](#33-keybinds--prefix)
   - [Macros](#34-macros)
   - [Packets](#35-packets)
   - [Container GUIs](#36-container-guis)
   - [Inventory & items](#37-inventory--items)
   - [Movement & position](#38-movement--position)
   - [Player & world](#39-player--world)
   - [Server, chat & network](#310-server-chat--network)
   - [Multi, LAN Sync & Matchmaking](#311-multi-lan-sync--matchmaking)
   - [Updates](#312-updates)
4. [Pathfinding & AI (`#`) commands](#4-pathfinding--ai--commands)
   - [Control](#41-control)
   - [Goals & navigation](#42-goals--navigation)
   - [Mining, farming & building](#43-mining-farming--building)
   - [Search, cache & memory](#44-search-cache--memory)
   - [Seeds & structures](#45-seeds--structures)
   - [Safety & automation](#46-safety--automation)
   - [AI](#47-ai)
   - [Waypoints & homes](#48-waypoints--homes)
   - [Selections (WorldEdit-style)](#49-selections-worldedit-style)
   - [Settings & maintenance](#410-settings--maintenance)
5. [Name clashes between `.` and `#`](#5-name-clashes-between--and-)
6. [Quick reference](#6-quick-reference)

---

## 1. The basics

DIH has **two command systems**, both typed in normal chat:

| Prefix | System | Lists commands | Notes |
|---|---|---|---|
| `.` | DIH client commands | `.commands`, `.help` | Never sent to the server. Prefix can be changed with `.prefix`. |
| `#` | Integrated Baritone + AI | `#help` | Pathfinding, mining, seed tools, AI. Prefix is the Baritone `prefix` setting. |

- **Default `.` prefix:** `.` — or `%` when Meteor Client is also installed (Meteor already owns `.`).
  The prefix can be any of `. % - _ * # @ & =`.
- **Tab completion** works on every argument: module names, macro names, item/block ids, packet class
  names, slot names, player names, waypoints.
- **Typing a command with no arguments** prints its usage.
- **`.help <command>`** shows the description and aliases of any `.` command.
  **`#help <command>`** prints the full built-in help of any `#` command (click it in `#help` to open).
- **`^toggledih`** (typed in chat as-is) turns the whole client on or off.
- **Panic / hide mode:** while the client is hard-locked by panic hide, *all* DIH commands are swallowed
  silently (under any prefix) so nothing leaks to the server.
- **Lite builds** ship without: `setting`, `irc`, `matchmaking`, `multi`, `toggle`, `modules`, `oresim`,
  `ghostblock`, `friend`, `waypoints`, `gamemode`, `fakegm`, `xcarry`, `rv`, `namescrape`.
- **Addons** can register more `.` commands. If an addon's command name collides with an existing one it
  is registered as `<addonId>:<name>` instead.

## 2. Reading this manual

- `<arg>` — required, `[arg]` — optional, `a|b` — pick one, `…` — any number of words.
- Examples use the default `.` prefix — substitute yours if you changed it.
- **State words.** Wherever a command takes `on|off|toggle`, the client accepts
  `on`/`enable`/`true`/`1`, `off`/`disable`/`false`/`0`, and `toggle`. Leaving it out toggles.

---

## 3. Client (`.`) commands

### 3.1 Help & info

#### `.help [command]`
With no argument, prints every command with its one-line description. With a command name (or alias),
prints that command's description and aliases.
```
.help
.help macro
```

#### `.commands` — alias `.cmds`
Compact comma-separated list of every registered command and the current prefix.

#### `.modules` — alias `.features`
Lists every installed module name (built-in and addon).

#### `.oresim`
Diagnostic report for OreSim (the Xray module's ore predictor): whether Xray is on, its mode and style,
the seed it is using (or why it's invalid/missing), the scope the seed is saved under, how many ore
families are selected, the local worldgen status, chunks simulated, and how many nearby real ores match.
Use it when OreSim isn't drawing anything.

---

### 3.2 Modules & settings

#### `.toggle <module> [on|off|toggle]` — alias `.t`
Turns a module on, off, or flips it.
```
.toggle killaura
.t scaffold on
.t freecam off
```

#### `.setting <module> [key] [value…]` — alias `.set`
Reads or writes any module setting without opening the ClickGUI.

| Form | Effect |
|---|---|
| `.setting <module>` | List every setting of the module with its current value. |
| `.setting <module> <key>` | Print one setting. |
| `.setting <module> <key> <value…>` | Set it. Use `reset` or `default` as the value to restore the default. |

Module names with spaces can be written with `-` (e.g. `storage-esp`).
```
.setting xray
.set xray mode
.set killaura range 4.2
.set killaura range reset
```

#### `.friend [add|remove|clear] [name]` — alias `.team`
Manages the friends/teams list used by combat and ESP modules.

| Form | Effect |
|---|---|
| `.friend` | Show the friend list. |
| `.friend add <name>` | Add a player (tab-completes online players). |
| `.friend remove <name>` | Remove a player. |
| `.friend clear` | Empty the list. |

#### `.ghostblock …` — alias `.gb`
Client-side "ghost" blocks that only you can see (controls the GhostBlock module).

| Form | Effect |
|---|---|
| `.gb toggle` | Turn the module on/off. |
| `.gb reset` | Remove every ghost block and restore the real blocks. |
| `.gb creative [on|off|toggle]` | Fake creative mode — whatever you place becomes a ghost block. Turning it on also enables the module. |
| `.gb block` | Show the currently armed block. |
| `.gb block <id>` | Arm a block for placement (e.g. `diamond_block`). Enables the module. |
| `.gb block off` | Disarm (`none`/`reset` also work) — held blocks become ghosts again. |

---

### 3.3 Keybinds & prefix

#### `.bind <key> <command…>` / `.bind clear <key>`
Binds a key to run any chat line — a DIH command, a `#` command, or plain chat.
```
.bind G .macro myMacro
.bind H #goto 0 0
.bind clear G
```

#### `.binds`
Lists all command keybinds.

#### `.prefix [new|reset]`
Shows, changes or resets the `.` command prefix. Allowed: `. % - _ * # @ & =`. `reset` goes back to the
environment default (`.` or `%` with Meteor). If Meteor is installed and you pick `.`, the client keeps
`%` and warns you.
```
.prefix
.prefix -
.prefix reset
```

> Built-in key defaults (changed in the config, not with `.bind`): **Right Shift** opens the module menu,
> **V** reloads the stored GUI. Flush queue / clear queue / toggle send / toggle delay / toggle logger are
> unbound by default.

---

### 3.4 Macros

Macros are built in the macro editor (module menu → Macros) or entirely from chat with the subcommands
below. They're stored in `config/dih/dih_macros.nbt`.

#### Running
| Form | Effect |
|---|---|
| `.macro <name>` | Run the macro once. |
| `.macro <name> <times>` | Run it `times` times (1–100 000) back to back; each run waits for the previous to finish. |
| `.macro <name> <times> <delayTicks>` | Same, with `delayTicks` (0–72 000; 20 ticks = 1 s) between runs. |
| `.macro stop` | Stop every running macro and loop. |
| `.macro stop <name>` | Stop one macro and its loop. |
| `.macro clear` | Cancel queued loops (the current run is allowed to finish). |

#### Authoring from chat
| Form | Effect |
|---|---|
| `.macro new <name>` | Create an empty macro. |
| `.macro add <name> <TYPE> [field=value …]` | Append an action. `TYPE` tab-completes from every macro action type. Fields are the action's public fields; enums, booleans, ints, longs, floats, doubles and strings are accepted (no spaces in values). |
| `.macro show <name>` | List the steps with their indices. |
| `.macro removeaction <name> <index>` | Delete step `index` (0-based). |
| `.macro delete <name>` | Delete the macro. |

```
.macro new dupe1
.macro add dupe1 DELAY ticks=5
.macro show dupe1
.macro dupe1 10 20        # run 10 times, 1 second apart
.macro stop
```

> When you are piloting a Multi bot (POV mode), `.macro` runs, loops and stops on **that bot** instead of
> your own player.

---

### 3.5 Packets

DIH sits on every outgoing packet. These commands drive the same controls as the on-screen
Send / Delay / Flush / Clear buttons and the packet gate manager.

#### `.packet …` — alias `.pkt`
| Form | Effect |
|---|---|
| `.packet status` | Show whether sending and delaying are on, and how many packets are queued. |
| `.packet send [on|off|toggle]` | Toggle sending of GUI/outgoing packets. Off = packets are not sent. |
| `.packet delay [on|off|toggle]` | Toggle the delay queue. Turning delay off flushes what was held. |
| `.packet flush` | Release every queued packet to the server now. |
| `.packet clear` | Throw away every queued packet. |

#### `.delay [on|off|toggle]`
Shortcut for `.packet delay`.

#### `.gate …` — alias `.packetgate`
A *gate* is a rule that cancels, holds, or whitelists specific packet types until you end it.
Packet names are class names (tab-complete), separated by spaces or commas.

| Form | Effect |
|---|---|
| `.gate cancel <packet…>` | Drop matching packets. |
| `.gate delay <packet…>` | Hold matching packets in the queue. |
| `.gate allow <packet…>` | Drop **everything except** these. |
| `.gate end [id]` | End a gate and flush what it held. The default id for gates made here is `cli`. |
| `.gate clear` | End every gate. |
| `.gate status` | List active gates. |

```
.gate cancel ServerboundContainerClosePacket
.gate status
.gate end
```

#### `.send <PacketClass> [field=value …]`
Builds and sends any client-to-server packet by class name. Press **Tab** after the space to search
packet classes and, after the packet name, its fields. Sending bypasses the delay queue. Server-to-client
packets are refused. If the arguments are incomplete the client prints the fields the packet needs.
Add `dry` (also `dryrun`, `preview`, `nosend`) to build the packet without sending it. Values can be quoted
(`message="hello"`) and raw bytes given as `hex:…`.
```
.send ServerboundSetCarriedItemPacket slot=0
.send ServerboundChatPacket message="hello" dry
```

---

### 3.6 Container GUIs

#### `.gui …` — alias `.screen`
The building blocks of the desync workflow (same code as the on-screen buttons).

| Form | Effect |
|---|---|
| `.gui save` | Store the current container screen locally (no packet). |
| `.gui desync` | Send the close-container packet to the server but keep the screen open on your side. |
| `.gui load` | Re-open the stored screen (same as the **V** key). |
| `.gui close` / `.gui close packet` | Close the screen and tell the server. |
| `.gui close nopacket` | Close the screen locally only. |

#### `.click-slot <slot> [click] [times]`
Clicks a slot in the open GUI or your inventory.

**Slot names:** `gui1`, `gui2`… (container slots), `hotbar1`–`hotbar9`, `inventory1`–`inventory27`,
`helmet`, `chestplate`, `leggings`, `boots`, `offhand`, or a raw visible numeric id.

**Click modes** (default `left`; `times` up to 100 000):

| Mode | What it does |
|---|---|
| `left` / `right` / `middle` | Normal pick-up clicks. |
| `shift-left` / `shift-right` | Quick-move. |
| `clone` | Middle-click clone (creative). |
| `pickup-all` | Double-click collect. |
| `drop-item` | Drop `times` items from the slot. |
| `drop-stack` | Drop the whole stack (no repeat count). |
| `swap <1-9> [times]` | Swap with that hotbar slot. |

```
.click-slot gui5
.click-slot gui5 shift-left
.click-slot hotbar1 swap 3
.click-slot inventory4 drop-item 16
```

#### `.click-item "<item name>" [click] [times]`
Same as `.click-slot`, but finds the slot by the item's **visible name** in the current GUI
(case/format-insensitive). Quote names with spaces.
```
.click-item "Confirm"
.click-item "Diamond Sword" shift-left
```

#### `.xcarry <slot>`
Stashes the item in your hand into a slot the server doesn't expect to hold items, so you keep it.
Open your inventory (**E**) first.
Slots: `craft1`–`craft4`, `craft5` (also `crafting5`/`result`, the crafting output), `helmet`,
`chestplate`, `leggings`, `boots`, `offhand`, or `cursor` (keep holding it on the cursor).
```
.xcarry craft1
.xcarry cursor
```

---

### 3.7 Inventory & items

#### `.drop …`
| Form | Effect |
|---|---|
| `.drop hand` | Drop the whole held stack. |
| `.drop fullinventory` | Drop every stack in the main inventory and hotbar. |
| `.drop <amount>` | Drop `amount` of the held item, pulling from other stacks of it if needed. |
| `.drop <amount> <item_id>` | Drop `amount` of a specific item from anywhere in the inventory. |

```
.drop 32
.drop 10 diamond
```

#### `.change-slot <1-9>`
Selects a hotbar slot.

#### `.give <item> [count]`
Puts an item (count 1–64) into the selected hotbar slot. **Creative mode required.**
```
.give netherite_sword
.give tnt 64
```

#### `.nbt [get|copy]`
- `.nbt` / `.nbt get` — open the component/NBT inspector for the item in your main hand (or offhand).
- `.nbt copy` — copy a `/give` command that recreates the held item to your clipboard.

---

### 3.8 Movement & position

#### `.vclip …`
Clips vertically through blocks using movement packets.

| Form | Effect |
|---|---|
| `.vclip <blocks>` | Move up (positive) or down (negative) by `blocks`. |
| `.vclip top` / `.vclip bottom` | Clip to the top/bottom free space. |
| `.vclip default <blocks>` / `.vclip paper <blocks>` | Same as `<blocks>` (Paper-safe padding). |
| `.vclip single <blocks>` / `.vclip normal <blocks>` | One single movement packet. |
| `.vclip custom <blocks> <segment> [maxPackets] [updateLocal] [vehicle] [forceGround]` | Full control: `segment` 1–50 blocks per step, `maxPackets` 1–100, then `true`/`false` flags. |

```
.vclip 5
.vclip -30
.vclip top
.vclip custom -25 10 20 true true
```

#### `.hclip …`
Clips horizontally (in the direction you face).

| Form | Effect |
|---|---|
| `.hclip <blocks>` | Clip forward `blocks`. |
| `.hclip forward` / `.hclip back` | Clip to the next free space forward/back. |
| `.hclip default\|paper\|padding <blocks>` | Padded (Paper-safe) variants. |
| `.hclip single\|normal <blocks>` | One single movement packet. |
| `.hclip custom <blocks> <segment> [maxPackets] [updateLocal] [vehicle] [forceGround]` | Full control, same as vclip. |

#### `.tp …`
Ground-first teleport made of paced HClip bursts.

| Form | Effect |
|---|---|
| `.tp <x> <y> <z> [maxPackets] [pauseMs]` | Teleport. `~` offsets work. `maxPackets` 1–100 per burst, `pauseMs` 50–10 000 between bursts. |
| `.tp <waypoint>` / `.tp "<waypoint name>"` | Teleport to a saved DIH waypoint. |
| `.tp status` | Show the running teleport. |
| `.tp stop` | Cancel it. |
| `.tp config <maxPackets> <pauseMs>` | Save new defaults. |
| `.tp reset` | Restore defaults (20 packets, 500 ms). |

It won't start while a macro is running. Works while riding.
```
.tp ~100 ~ ~
.tp 1200 64 -300 30 400
.tp base
```

#### `.copypos`
Copies your block position (`x y z`) to the clipboard. Uses your vehicle's position while riding and the
camera position while in freecam.

#### `.waypoints …` — alias `.wp`
DIH's own waypoints (separate from Baritone's `#wp`), saved per server/world.

| Form | Effect |
|---|---|
| `.wp` | Save a waypoint at your position (auto-named `Waypoint N`). |
| `.wp <x> <y> <z>` | Save one at those coordinates. |
| `.wp gui` / `.wp list` | Open the waypoint manager. |
| `.wp del <name>` | Delete a waypoint. |
| `.wp clear` | Delete all waypoints for this server/world. |

#### `.dismount`
Force-dismounts from any vehicle.

---

### 3.9 Player & world

#### `.gamemode <survival|creative|adventure|spectator>`
Changes your **real** game mode without sending a chat command (needs permission on the server).

#### `.fakegm <survival|creative|adventure|spectator|reset>`
Client-side-only fake game mode. `reset` (or `r`) returns to the real one.

#### `.damage <amount>`
Sends `/damage @s <amount>` (0.1–1024). Only works where you're allowed to use `/damage` (op).

#### `.rv [player|stop]`
Remote view: watch another loaded player's point of view.
- `.rv` — watch the player you're looking at.
- `.rv <name>` — watch a named loaded player.
- `.rv stop` — return to your own view.

#### `.namescrape [count] | deep [count] | pause | resume | stop` — alias `.scrapenames`
Collects player names from the server and copies them to the clipboard. `count` limits how many,
`deep` does an exhaustive scrape; `pause`/`resume`/`stop` control a running scrape.

---

### 3.10 Server, chat & network

#### `.server [info|plugins|tps]`
- `.server` / `.server info` — open the server info panel.
- `.server plugins` — open the plugin scanner tab.
- `.server tps` — print the estimated server TPS.

#### `.plugins`
Shortcut for `.server plugins`.

#### `.say <message>`
Sends a chat message. If it starts with `/` it's sent as a server command. Useful in binds and macros,
and for sending text that begins with your DIH prefix.

#### `.clear` — alias `.cls`
Clears the chat window.

#### `.disconnect`
Disconnects from the server.

---

### 3.11 Multi, LAN Sync & Matchmaking

#### `.multi …`
Multi runs several accounts (bots) at once.

| Form | Effect |
|---|---|
| `.multi` / `.multi open` | Open the Multi menu (or the console if a batch is running). |
| `.multi launch <profile>` | Start a saved Multi profile. |
| `.multi status` | Batch name, ready and connected counts. |
| `.multi stop` | Disconnect every bot in the batch. |
| `.multi macro <macro> [bot]` | Run a macro on one bot, or on all bots if none is named. |

**POV mode.** While you're piloting a bot, these commands run on the bot instead of you:
`click-slot`, `click-item`, `change-slot`, `drop`, `send`, `say`, `damage`, `hclip`, `vclip`, `tp`
(`disconnect` disconnects the bot). These are blocked in POV so they can't touch your main account:
`delay`, `gamemode`, `server`, `plugins`, `matchmaking`/`mm`, `dismount`.

#### `.sync …` — aliases `.lan-sync`, `.lansync`
Requires an active LAN Sync session.
- `.sync send <message or /command>` — send the same chat line from every synced client.
- `.sync macro <name>` — start a macro on every synced client at the same moment.

#### `.matchmaking` — alias `.mm`
Toggles the Matchmaking overlay.

#### `.irc <message>`
Sends a message to your Matchmaking lobby's chat (join or create a lobby first).

---

### 3.12 Updates

#### `.update [check|download|on|off]` — alias `.updates`
| Form | Effect |
|---|---|
| `.update` | Show whether a newer build exists. |
| `.update check` | Ask GitHub again. |
| `.update download` | Open the new jar's download page. Replace the old jar in `mods` and restart. |
| `.update on` / `.update off` | Turn automatic update checks on/off. With checks off, DIH never contacts GitHub. |

---

## 4. Pathfinding & AI (`#`) commands

The integrated Baritone engine. Wherever coordinates are expected you can use `~` like vanilla commands.
`#help` lists every command; `#help <command>` prints its full built-in help.

### 4.1 Control

| Command | Aliases | Effect |
|---|---|---|
| `#cancel` | `#c`, `#stop` | Stop whatever Baritone is doing. |
| `#forcecancel` | | Like cancel, but more forceful. |
| `#pause` | `#p`, `#paws` | Pause the current task. |
| `#resume` | `#r`, `#unpause`, `#unpaws` | Resume after a pause. |
| `#paused` | | Say whether Baritone is paused. |
| `#proc` | | Show the process that is in control. |
| `#eta` | | Estimated time to the next segment and the goal. |

### 4.2 Goals & navigation

#### `#goto`
```
#goto <block>          go to a block type anywhere it's known
#goto <y>              go to a Y level
#goto <x> <z>          go to X,Z
#goto <x> <y> <z>      go to X,Y,Z
```

#### `#goal` / `#path`
`#goal` sets a goal without moving; `#path` starts walking to it.
```
#goal                  goal = your position
#goal <y> | <x> <z> | <x> <y> <z>
#goal clear            (reset / none also work)
#path
```

#### Other movement
| Command | Aliases | Usage |
|---|---|---|
| `#come` | | Walk to where your camera is (handy with freecam). |
| `#thisway <distance>` | `#forward` | Goal `distance` blocks in the direction you face. |
| `#axis` | `#highway` | Head to the nearest axis (X=0 or Z=0). |
| `#surface` | `#top` | Get out of caves to the nearest surface/open air. |
| `#invert` | | Run away from the current goal instead of toward it. |
| `#runaway [distance]` | `#escape` | Get at least `distance` blocks (default 32) from where you stand. *(`#flee` is taken by the Flee command — see §4.6.)* |
| `#follow players` | | Follow all players. |
| `#follow player <name…>` | | Follow specific players. |
| `#follow entities` | | Follow all living entities. |
| `#follow entity <type…>` | | Follow entity types (e.g. `horse`). |
| `#explore [x z]` | | Explore outward from you (or from X,Z). |
| `#explorefilter <path> [invert]` | | Load a JSON chunk list (`[{"x":0,"z":0},…]`) marking chunks as explored (or *not* explored with `invert`) before `#explore`. |

#### `#elytra`
Flies with elytra and rockets (needs elytra worn + fireworks in inventory). Works in every dimension.
```
#elytra goto <x> <z>
#elytra goto <x> <y> <z>
#elytra                    fly to the current #goal
#elytra reset              reset pathfinding, keep the goal
#elytra repack             re-feed loaded chunks to the pathfinder
#elytra supported          check the native library loaded
```

#### `#nether` — aliases `#nc`, `#coords`
Converts coordinates between Overworld and Nether (X/Z ÷8 or ×8, Y unchanged).
```
#nether                       your position, auto-detects dimension
#nether <x> <y> <z>           auto-detects direction
#nether <x> <z>
#nether overworld <x> <y> <z> overworld → nether
#nether nether <x> <y> <z>    nether → overworld
```

#### `#portal` — aliases `#whereportal`, `#findportal`
Walk to (and by default into) the nearest cached nether portal; explores if none is known.
`#portal skip` blacklists the nearest one and goes to the next. Turn off auto-entry with
`#set enterPortal false`.

#### `#sleep` / `#autosleep`
- `#sleep` — go to the nearest cached bed and sleep when it's night (or thundering); waits there if it's day.
- `#autosleep [on|off|interrupt|status]` (alias `#nightowl`) — do that automatically every night.
  `interrupt` toggles whether it may interrupt other tasks (default: only when idle).

### 4.3 Mining, farming & building

| Command | Usage |
|---|---|
| `#mine <block…>` | Find and mine blocks. Stone/deepslate ore variants are paired automatically (`#mine diamond_ore` gets both). See `#set legitMine`. |
| `#tunnel` | Mine a straight 1×2 tunnel the way you face. |
| `#tunnel <height> <width> <depth>` | Custom-size tunnel. |
| `#farm [range] [waypoint]` | Harvest mature crops and replant, optionally within `range` of you or of a waypoint. |
| `#pickup [item…]` | Pick up dropped items (all, or only listed items). |
| `#build <file> [x y z]` | Build `<file>.schematic` here or at a position. |
| `#litematica [#]` | Build the schematic open in Litematica. |
| `#blacklist` | While heading to a block, blacklist the closest one so Baritone skips it. |

### 4.4 Search, cache & memory

These read Baritone's chunk cache — no extra packets.

#### `#search <block> [goto]` — alias `#blocks`
Lists the nearest matches (loaded chunks + cache) with distance and direction.
`#search <block> goto` paths to the nearest; `#search <N> goto` paths to result N.

> The built-in help also lists `#s` as an alias, but `#s` actually opens `#sel`. Use `#search` or `#blocks`.

#### `#find <block…>`
Looks up cached positions of blocks (tab-complete only offers cached blocks).

#### `#chest` — aliases `#chests`, `#findchest`
DIH records the contents of every container you open. Search it:
```
#chest <item>          containers holding <item> (partial, case-insensitive)
#chest <item> goto     search and path to the best result
#chest <N> goto        path to result N of the last search
#chest list [page]     browse everything recorded
#chest stats
#chest forget <N>
#chest clear
```
Saved in `baritone/chest_memory.json`.

#### `#bases` — alias `#basefinder`
Clusters cached "base indicator" blocks (beacons, ender chests, enchanting tables, anvils, shulkers…)
into likely player bases.
```
#bases            top 10 in this dimension
#bases <N>
#bases pie        breakdown by indicator type
#bases <N> goto   path to base N
```
Tune with `baseFinderMinScore` (30), `baseFinderMinIndicators` (3), `baseFinderEpsilon` (50). Run
`#repack` first to include freshly loaded chunks.

#### `#heatmap` — alias `#activity`
Scores 512×512 zones by player activity (adds portals, hoppers, observers, heads, jukeboxes).
`#heatmap [N]`, `#heatmap <N> goto`. Also drawn as a JourneyMap layer.

#### `#players` — aliases `#player`, `#seen`
Player-sighting log (always recorded, even with `#threats` off).
```
#players              everyone seen
#players <name>       sighting history
#players goto <name>  path to their last known position
#players forget <name>
#players clear
#players stats
```

#### `#menu` — alias `#gui`
Reads and clicks server plugin GUIs (shops, warp menus, RTP).
```
#menu dump                     print every non-empty slot of the open menu
#menu click <matcher>          click a slot in the open menu
#menu run <command> <matcher>  run /command, wait for the menu, click
#menu stop                     cancel a pending click
```
Matchers: `item:<id>` (most reliable), `slot:<n>` (0-based), or text from the name/lore.
```
#menu run warps item:compass
#menu click slot:18
```

### 4.5 Seeds & structures

#### `#seedinput` — alias `#seed`
Stores the world seed for the current server (needed for `#seedmap`, `#structure`, `#where` on
multiplayer; shared with OreSim). Singleplayer needs nothing.
```
#seedinput <seed>         number or text (text is converted like world creation)
#seedinput <seed> force   keep it even if it fails verification
#seedinput / check        show the seed and whether it matches the server's hash
#seedinput clear
```

#### `#structure <name>` — aliases `#struct`, `#locate`
Finds the nearest structure and paths there. Names: `village`, `stronghold`, `fortress`
(`nether_fortress`), `bastion`, `mansion`, `monument`, `ancient_city`, `end_city`, `mineshaft`,
`buried_treasure`, `desert_pyramid`, `jungle_pyramid`, `pillager_outpost`, `shipwreck`, `igloo`,
`swamp_hut`, `ocean_ruin`, `ruined_portal`, `trial_chambers`, `trail_ruins`.

#### `#where [structure]`
`#where` prints your X Y Z and dimension. `#where <structure>` gives the nearest structure's coordinates,
distance and direction **without** moving.

#### `#seedmap` — aliases `#structures`, `#smap`
Draws every biome-validated structure on the JourneyMap fullscreen map (chat list without JourneyMap).
```
#seedmap                     everything within 2000 blocks
#seedmap <radius>            up to 8000
#seedmap <kinds…>            e.g. #seedmap village monument
#seedmap <r> at <x> <z>      centre elsewhere
#seedmap wp                  also add landmark waypoints
#seedmap strongholds         nearest strongholds (+ all 128)
#seedmap off                 hide the overlay
```

### 4.6 Safety & automation

#### `#threats` — alias `#threat`
Chat alert when a player enters range. `#threats` toggles; `on`, `off`, `<N>` (radius, default 64),
`status`.

#### `#flee`
Disconnects you (or runs a command) when a trigger fires. All triggers start **off**; by default they're
only checked while Baritone is busy.
```
#flee                     status
#flee on | off            master switch
#flee players [on|off|N]  players within N blocks
#flee health <hearts>
#flee hunger <food>
#flee tool <durability>
#flee pickaxe             none left while mining
#flee full                inventory full
#flee action disconnect|command|stop
#flee command /spawn      command to run with action=command
#flee check always        also check while idle (default: busy)
#flee reconnect on        let AutoReconnect reconnect afterwards
#flee gui                 settings screen
#flee test                fire the action now
```

#### `#listentomine` — alias `#ltm`
Watches chat for a word (default `legendary`), then runs a warp (default `/warp mine`), waits, frees a
slot if the inventory is full, and `#mine`s the target (default `ancient_debris`).
`#ltm` toggles; `on`, `off`, `status`. Configure with `#set listenToMineWord`, `listenToMineWarp`,
`listenToMineTarget`, `listenToMineWarpDelay` (40), `listenToMineCooldown` (200).

### 4.7 AI

#### `#ai`
A language model reads server chat and plays by issuing `#` commands. Trusted players' messages are
instructions; everyone else's are context only.

**Setup**
```
#ai key <api-key>     (or set MINECRAFTAI_LLM_KEY)
#ai url <base-url>    any OpenAI-compatible endpoint
#ai model <name>      default qwen-plus
#ai trust <player>    let that player command the bot
#ai on
```

**Day to day**
| Form | Effect |
|---|---|
| `#ai <anything>` | Tell the AI to do something yourself. |
| `#ai status` | Config, token usage, last error. |
| `#ai off` | Stop listening. |
| `#ai auto [on|off]` | Let it think on its own timer. |
| `#ai chat [on|off]` | Whether it may talk in server chat. |
| `#ai trigger <word|none>` | Require a wake word from trusted players. |
| `#ai goal <text>` | Its standing objective. |
| `#ai persona [text]` | Show/set its personality. |
| `#ai trust [name]` / `#ai untrust <name>` | Manage trusted players (`trust` alone lists them). |
| `#ai deny <command>` / `#ai allow <command>` | Forbid / re-allow a `#` command. |
| `#ai extra <json|none>` | Extra JSON merged into every API request. |
| `#ai forget [all|text]` | Inspect or wipe long-term memory. |
| `#ai clear` | Forget the current conversation. |

Stored in `baritone/ai.json` and `baritone/ai_memory.json`.

### 4.8 Waypoints & homes

#### `#home` — aliases `#homes`, `#sethome`
```
#home set <name>      save your position (#sethome <name> does the same)
#home <name>          path there
#home list            list with distances
#home del <name>
```
Saved in `baritone/homes.json`.

#### `#waypoints` — aliases `#waypoint`, `#wp`
Baritone's tagged waypoints (not the DIH `.wp` ones).
```
#wp [list] [tag]
#wp save [tag] [name] [pos]
#wp info <tag|name>
#wp delete <tag|name>
#wp restore <n>             undo the last n deletions
#wp clear <tag>
#wp goal <tag|name>         set as goal
#wp goto <tag|name>         set as goal and go
```
Short forms: `l`, `s`, `i`/`show`, `d`, `c`, `g`.

### 4.9 Selections (WorldEdit-style)

#### `#sel` — aliases `#selection`, `#s`
```
#sel pos1 [x y z]   /  #sel pos2 [x y z]     (p1/1, p2/2)
#sel clear (c)      #sel undo (u)
#sel set [block]           fill (also fill/s/f)
#sel walls [block]         (w)
#sel shell [block]         walls + floor + ceiling (shl)
#sel sphere [block]        (sph)     #sel hsphere [block]  hollow (hsph)
#sel cylinder [block] [axis]  (cyl)  #sel hcylinder …       hollow (hcyl)
#sel cleararea             set air (ca)
#sel replace <blocks…> <with>  (r)
#sel copy [x y z]  (cp)    #sel paste [x y z]  (p)
#sel expand <target> <direction> <blocks>   (ex)
#sel contract <target> <direction> <blocks> (ct)
#sel shift <target> <direction> <blocks>    (sh)
```
Targets: `a`/`all`, `n`/`newest`, `o`/`oldest`. Selections are drawn in-world.

### 4.10 Settings & maintenance

| Command | Aliases | Effect |
|---|---|---|
| `#set` | `#setting`, `#settings` | `#set [list [page]]`, `#set modified [page]`, `#set <setting> [value]`, `#set toggle <setting>`, `#set reset <setting>\|all`, `#set save`, `#set load [file]`. |
| `#modified` | `#mod`, `#baritone`, `#modifiedsettings` | Shortcut for `#set modified`. |
| `#reset` | | Shortcut for `#set reset`. |
| `#repack` | `#rescan` | Re-cache the chunks around you. |
| `#reloadall` | | Reload the world cache. |
| `#saveall` | | Save the world cache. |
| `#render` | | Fix glitched chunk rendering. |
| `#click` | | Open Baritone's click-to-go screen. |
| `#gc` | | Run Java garbage collection. |
| `#version` | | Baritone version. |
| `#help [command]` | `#?` | List commands or show one command's help. |

Useful settings: `prefix` (`#`), `renderPath`, `renderGoal`, `enterPortal`, `legitMine`,
`colorCurrentPath`, `pathRenderLineWidthPixels`.

---

## 5. Name clashes between `.` and `#`

The two systems are separate, so similar names do different things:

| You type | Does |
|---|---|
| `.wp` | DIH waypoints (per server, used by `.tp`). |
| `#wp` | Baritone waypoints (tagged, used by `#wp goto`). |
| `.set` | DIH module setting. |
| `#set` | Baritone setting. |
| `.gui` | Save / desync / load container screens. |
| `#gui` | `#menu` — read and click plugin GUIs. |
| `.help` | DIH command help. |
| `#help` | Baritone command help. |

Inside the `#` system, later registrations win:
- `#flee` is the **Flee** trigger system; the run-away command is `#runaway` / `#escape`.
- `#s` is **`#sel`**; for search use `#search` / `#blocks`.

---

## 6. Quick reference

### `.` commands

| Command | Aliases | Summary |
|---|---|---|
| `bind <key> <cmd…>` / `bind clear <key>` | | Bind a key to a chat line. |
| `binds` | | List command binds. |
| `change-slot <1-9>` | | Select hotbar slot. |
| `clear` | `cls` | Clear chat. |
| `click-item "<name>" [click] [times]` | | Click an item by visible name. |
| `click-slot <slot> [click] [times]` | | Click a slot. |
| `commands` | `cmds` | List commands. |
| `copypos` | | Copy coordinates. |
| `damage <amount>` | | `/damage @s`. |
| `delay [on\|off\|toggle]` | | Toggle packet delay queue. |
| `disconnect` | | Leave the server. |
| `dismount` | | Leave vehicle. |
| `drop hand\|fullinventory\|<n> [item]` | | Drop items. |
| `fakegm <mode\|reset>` | | Fake gamemode. |
| `friend [add\|remove\|clear] [name]` | `team` | Friends list. |
| `gamemode <mode>` | | Real gamemode. |
| `gate cancel\|delay\|allow\|end\|clear\|status` | `packetgate` | Packet gates. |
| `ghostblock toggle\|reset\|creative\|block` | `gb` | Ghost blocks. |
| `give <item> [count]` | | Creative give. |
| `gui save\|desync\|load\|close` | `screen` | Container screen tools. |
| `hclip …` | | Horizontal clip. |
| `help [command]` | | Help. |
| `irc <message>` | | Matchmaking lobby chat. |
| `macro …` | | Run / author macros. |
| `matchmaking` | `mm` | Matchmaking overlay. |
| `modules` | `features` | List modules. |
| `multi [open\|launch\|status\|stop\|macro]` | | Multi accounts. |
| `namescrape …` | `scrapenames` | Scrape player names. |
| `nbt [get\|copy]` | | Inspect / copy item NBT. |
| `oresim` | | OreSim diagnostics. |
| `packet status\|send\|delay\|flush\|clear` | `pkt` | Packet queue. |
| `plugins` | | Plugin scanner. |
| `prefix [new\|reset]` | | Command prefix. |
| `rv [player\|stop]` | | Remote view. |
| `say <message>` | | Send chat / command. |
| `send <Packet> [k=v…]` | | Send a raw packet. |
| `server [info\|plugins\|tps]` | | Server panel. |
| `setting <module> [key] [value]` | `set` | Module settings. |
| `sync send\|macro` | `lan-sync`, `lansync` | LAN Sync. |
| `toggle <module> [state]` | `t` | Toggle module. |
| `tp <x y z>\|<waypoint>\|stop\|status\|config\|reset` | | Paced teleport. |
| `update [check\|download\|on\|off]` | `updates` | Update checker. |
| `vclip …` | | Vertical clip. |
| `waypoints [x y z\|gui\|del\|clear]` | `wp` | DIH waypoints. |
| `xcarry <slot>` | | Stash item in XCarry slot. |

### `#` commands

| Command | Aliases |
|---|---|
| `ai` | |
| `autosleep` | `nightowl` |
| `axis` | `highway` |
| `bases` | `basefinder` |
| `blacklist` | |
| `build` | |
| `cancel` | `c`, `stop` |
| `chest` | `chests`, `findchest` |
| `click` | |
| `come` | |
| `elytra` | |
| `eta` | |
| `explore` | |
| `explorefilter` | |
| `farm` | |
| `find` | |
| `flee` | |
| `follow` | |
| `forcecancel` | |
| `gc` | |
| `goal` | |
| `goto` | |
| `heatmap` | `activity` |
| `help` | `?` |
| `home` | `homes`, `sethome` |
| `invert` | |
| `listentomine` | `ltm` |
| `litematica` | |
| `menu` | `gui` |
| `mine` | |
| `modified` | `mod`, `baritone`, `modifiedsettings` |
| `nether` | `nc`, `coords` |
| `path` | |
| `pause` | `p`, `paws` |
| `paused` | |
| `pickup` | |
| `players` | `player`, `seen` |
| `portal` | `whereportal`, `findportal` |
| `proc` | |
| `reloadall` | |
| `render` | |
| `repack` | `rescan` |
| `reset` | |
| `resume` | `r`, `unpause`, `unpaws` |
| `runaway` | `escape` |
| `saveall` | |
| `search` | `blocks` |
| `seedinput` | `seed` |
| `seedmap` | `structures`, `smap` |
| `sel` | `selection`, `s` |
| `set` | `setting`, `settings` |
| `sleep` | |
| `structure` | `struct`, `locate` |
| `surface` | `top` |
| `thisway` | `forward` |
| `threats` | `threat` |
| `tunnel` | |
| `version` | |
| `waypoints` | `waypoint`, `wp` |
| `where` | |
