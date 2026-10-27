# DIH Client commands

Every chat command in DIH Client and how to type it. Minecraft 26.2, Fabric Loader 0.19.3+, Java 25. The website
shows the same manual at `/docs`.

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
`friend`, `waypoints`, `gamemode`, `fakegm`, `xcarry`, `rv` and `namescrape`. Addons can add their own `.`
commands; one whose name is already taken shows up as `<addon>:<name>`.

### Same name, different command

The two systems are separate, so these pairs do different things.

| Client | Pathfinder |
|---|---|
| `.wp` saves DIH waypoints, which `.tp` can use. | `#wp` manages Baritone's tagged waypoints. |
| `.set` changes a module setting. | `#set` changes a Baritone setting. |
| `.gui` saves, desyncs and reloads container screens. | `#gui` is `#menu`, which reads and clicks plugin GUIs. |
| `.help` lists client commands. | `#help` lists pathfinder commands. |

Two `#` aliases also point somewhere other than their help text says. `#flee` opens the Flee trigger system, so
use `#runaway` or `#escape` to run away. `#s` opens `#sel`, so use `#search` or `#blocks` to search.

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
#ai url <base-url>
#ai model <name>
#ai trust <player>
#ai on
```

The key can also come from the `MINECRAFTAI_LLM_KEY` environment variable. Any OpenAI-compatible endpoint
works, and the default model is `qwen-plus`.

##### Use

| Usage | Effect |
|---|---|
| `#ai <anything>` | Tell it to do something yourself. |
| `#ai status` | Show the setup, token use and last error. |
| `#ai off` | Stop listening. |
| `#ai auto [on\|off]` | Let it think on a timer. |
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
