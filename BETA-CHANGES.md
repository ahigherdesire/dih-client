# DIH Client beta changes

## What's new in the beta

The 5.1 betas with release notes, newest first. Recent betas are Fabric only, on Minecraft 26.2 and 26.3; NeoForge and Forge stay on beta 3.

### 5.1 beta 7g.3: place schematics like Litematica, full material list, CSV export

**Schematics**
- Placing a schematic no longer locks your camera. Picking a file in the Schematics screen, **Open File...**,
  dropping a file on the window and `.schem place` all put you straight back in the game with the camera free, and
  a panel at the top shows the keys: arrows move it relative to where you face (hold to repeat), Page Up/Down move
  it up and down, R rotates, M mirrors, G drops it onto the ground, Enter keeps it and Backspace removes it. These
  keys only do this until you keep or remove the new placement. The mouse placement screen is still there under
  **Move**, `.schem adjust` and the Adjust Key.
- **Materials** opens a full material list, like Litematica's: every item with how many the build needs, how many
  you have and how many are still missing, biggest shortfalls first. It scrolls, updates every 2 seconds while you
  gather, and **Missing only** hides what you already have enough of. Before, the screen only had room for 3 items.
- **Save CSV** (in the Schematics screen and the material list) and `.schem materials csv` save the list to
  `.minecraft/schematics/materials/<name>-materials.csv`, ready for Excel or Google Sheets: item, name, needed,
  have, missing. Exporting again replaces the file with the current list.
- Placements in singleplayer are now saved per world folder instead of per world name, so two worlds with the same
  name (two "New World"s) no longer share their placements. Existing placements move over the first time you open
  each world.
- The placements list says which server or world it belongs to, and placements in another dimension are marked,
  for example "mobfarm (Nether)".

### 5.1 beta 7g.2: schematic builds use your whole inventory

**Schematics**
- **Build** (the button and `.schem build`) turns on Baritone's `#allowInventory`, so blocks anywhere in your
  inventory get used, not just the ones on your hotbar. It stays on, and the build message says when it was turned on.
- When Baritone runs out of blocks it now lists what is still to place in plain words, most needed first
  (`64x Oak Planks`, `22x Glass`, ...), instead of raw block data, and says to `#resume` once you have them. This
  list shows for `#build` and `#litematica` too.

You don't need every block before you start: Baritone places what you carry and pauses when it runs out. Blocks in
shulker boxes or chests aren't used.

### 5.1 beta 7g.1: open schematics from anywhere

Load a schematic straight from the game. It no longer has to be in the `.minecraft/schematics` folder first.

**Schematics**
- **Open File...** in the Schematics screen (or `.schem open`, or the new Open File Key in the module) opens your
  system's file picker in Downloads. Pick a `.litematic`, `.schematic`, `.schem` or `.nbt` file from anywhere and it
  is placed in front of you with the mouse controls open.
- Drag schematic files onto the game window, in game or on any screen. One file is placed right away; several open
  the Schematics screen so you can pick one. Dropped on the title screen, they are saved for when you join.
- The Schematics screen also lists your 20 newest schematic downloads, tagged "Downloads". Click one to place it.
- `.schem import <path>` does the same with a pasted path (quotes and Windows backslashes are fine) or a file name
  in Downloads.
- Picked and dropped files are copied into the schematics folder so they are still there next time. Nothing is
  overwritten: a different file with the same name is saved as `name (2).litematic`.

waits while the file picker is open.

### 5.1 beta 7g: Schematics, a built-in Litematica

Load schematic files, place them in the world, see what is still to build, and let Baritone build them. No
Litematica mod needed.

**Schematics (new module and `.schem` command)**
- Reads `.litematic` (every region), `.schematic` (MCEdit, Schematica and WorldEdit, with old 1.12 block ids turned
  into today's blocks, bed and banner colours, flower pots, skulls and doors included), `.schem` (Sponge v1, v2 and
  v3) and vanilla `.nbt` structure files. Put them in your `.minecraft/schematics` folder.
- Place a file in front of you, then move it with the mouse: right-drag to move, wheel to turn, Shift + wheel to
  push it nearer or farther, arrow keys and Page Up/Down to nudge, R to rotate, M to mirror, G to drop it onto the
  ground. Left-click keeps it, Esc puts it back.
- Ghost blocks show what is still missing, and the overlay colours every block that isn't right: blue missing,
  red wrong block, orange wrong state (facing and so on), pink extra. Settings for render range, layers (all, one,
  below or above a height), see-through, colours and keybinds.
- Several placements at once, saved per server or world, so they come back when you rejoin.
- **Materials** lists what is still needed against your inventory, **Verify** counts what is right and wrong, and
  **Build** hands the placement to Baritone's builder (the one `#build` uses). If Baritone pauses on stairs or other
  turned blocks, `#buildIgnoreDirection true` lets it place them.
- Open it from the Schematics screen (Menu Key in the module, or `.schem menu`), or use `.schem place <file>`,
  `.schem rotate`, `.schem mirror`, `.schem materials`, `.schem build` and the rest (`.schem` lists them).

**Also**
- `#build` and `#construct` read schematics the same way, so old `.schematic` files and Sponge v3 files now load
  there too, and a broken file says what is wrong instead of failing silently.
- If you also run the real Litematica mod, nothing changes for it: its placements stay separate and `#litematica`
  still builds those.

build); the mouse controls and keybinds haven't been played with much yet. Try it in a singleplayer world first.

### 5.1 beta 7f: PearlCharge, and Clutch can MLG a water bucket

Two player-requested additions.

**PearlCharge (new module)**
- Throws an ender pearl and pops a wind charge together to launch you — straight up, or toward where you look.
  Bind a key to it and tap to fire; it switches back to the item you were holding afterward.
- Settings: **Order** (pearl or charge first), **Delay** between the two throws, **Launch Pitch** (90 pops the
  charge at your feet for a straight-up launch), **Pearl Where Looking** (off throws it straight up for height),
  and **Switch Back**. It needs an ender pearl and a wind charge on your hotbar.

**Clutch: water-bucket MLG**
- Clutch has a new **Clutch With** setting: **Blocks** (what it did before), **Water** (MLG a water bucket onto the
  ground below so a big fall does no damage), or **Auto** (water when it will catch you, blocks otherwise — and in
  the Nether, where water would vanish, it clutches with blocks).
- New **Pick Up Water** (on by default) scoops the water back into the bucket after you land.
- Clutch still defaults to **Blocks**, so nothing changes unless you pick Water or Auto.

### 5.1 beta 7e: a lot more settings in almost every module, and floating BedESP labels

**More settings everywhere**
Almost every module got more to tune: about 880 settings before, about 1,390 now. Settings are split into groups
(General first, then groups like Targets, Range, Aim, Timing, Render), and every setting has a one-line description.
Settings that only matter when another one is on stay hidden until you turn it on. Your saved settings carry over,
and new settings start at the old behavior.

Some highlights:
- **KillAura** (17 → 36): **Held Items Only**, **PerfectHit** (times hits for when damage immunity ends),
  **Through Walls** range, **Turn Speed**, **Min Charge**, **Only On Click**, **Pause In GUI**, hitsound volume and
  pitch, and a **Targets** group: ignore invisible, naked, named, baby, sleeping, pets, or targets over a health cap.
- **Combat**: AimAssist, TriggerBot, AutoTrap, BowAimAssist, Danger, AutoMace and the rest got targeting filters,
  ranges and timing. Criticals, Reach, AutoClicker and MaceKill went from 1-3 settings to 7-8.
- **Movement**: Flight, Speed, NoFall, Scaffold (block choice, info, auto-disable), Clutch, SafeWalk, and new
  options for InvMove, Parkour, Sneak, Sprint and AirJump.
- **Player**: Nuker, TpClick, AutoLogin, SmartEat, AntiAFK, PacketMine, AutoSign and more roughly doubled.
- **Render**: ESP, Nametags, Trajectories, LogoffSpot, Breadcrumbs, HoleESP, Waypoints, Chams, Freecam, Zoom and more:
  colours, line width, fill/outline, distance, fade and filters.
- **Misc**: AntiBot now has a toggle for each bot check. AutoReconnect has a random extra wait and a max attempts
  limit. Teams can group players by name colour or armour dye. PayAll has a dry run.

**BedESP: floating labels**
- Every bed gets a label floating above it with the team, the break time, your distance and what the defence is made
  of. The label has its own settings: on/off, scale, distance scaling, height, what it shows, name colour and background.

**Changes to know about**
- KillAura no longer attacks **your own** tamed pets by default (**Ignore Pets: Yours**). Set it to Off for the old
  behavior.
- NoInteract's **Friends** setting didn't do anything before. Now it works.
- Viewmodel's **Item Scale** is now called **Z (Depth)**, since it always moved the item forward and back. The new
  **Size** setting actually scales the item.

### 5.1 beta 7d: a bedwars kit (BedESP, Clutch, Danger) and GIF images, capes and shields

**BedESP** (new, Render)
- Colours the defence around every nearby bed by layer: the bed, layer 1, layer 2 and layer 3+ each get a colour.
- **Fastest Path** highlights the quickest way in, counting walking time and mining time with the best tool in your
  hotbar (approximate: enchantments and effects aren't counted).
- **HUD Summary** shows the nearest bed above your hotbar: distance, time to get in, and what each layer is made
  of (for example `L1 White Wool ×12  ·  L2 End Stone ×20`).
- **Range**, **Layers** (1 to 6) and **Max Beds** set how much it looks at.

**Clutch** (new, Movement)
- Places blocks back under you when you get knocked toward the void, using any full block in your hotbar.
- **Mode**: **Void** only catches falls with nothing below; **Big Falls** also catches drops deeper than **Fall Height**.
- **Air Window** (ticks after leaving the ground), **Only When Hit** (only falls that start with a hit) and
  **Max Blocks** (per fall) decide when it steps in. **Rotate** looks at the block it clicks; **Switch Back** returns
  to the item you were holding.

**Danger** (new, Combat)
- Warns about arrows, fireballs and thrown items that are going to hit you: time to impact and direction under the
  crosshair, a ping, and the projectile's path drawn in the world.
- **Deflect Fireballs** (off by default) hits incoming fireballs that come within **Deflect Range**. With
  **Return To Sender** on, it turns to the fireball first so it flies back the way it came.

**GIF** (new, Render)
- Shows a GIF, PNG or JPEG on your HUD. **Image Path** is a full path or a path inside `.minecraft`. **Width**,
  **Keep Aspect**/**Height**, **Opacity**, **Speed**, **Anchor** and **X/Y Offset** place it.
- **Cape** and **Shield** turn the image into an animated cape and shield on your own player. **Cape Image** and
  **Shield Image** take a separate file each; leave them blank to use Image Path. Only you see them.

### 5.1 beta 7c: ArrowDamage, a SpearKill Poke mode, and instant CrystalAura and AnchorAura

**ArrowDamage** (new, Combat)
- Bow arrows fly at sniper speed and hit far harder. As you let go, the server is told you're moving fast along your
  aim, and the arrow takes that speed. **Boost** (default 19) is how much is added; a full draw normally flies 3.
- Shots fly almost straight, so aim right at the target. Trajectories and BowAimAssist show and aim the boosted shot.
- **Full Draw Only** (on by default) boosts only fully drawn shots. Crossbows aren't affected.

**SpearKill**
- New **Poke** mode, now the default: huge charge hits while you stand still or walk normally. Each tick a target is
  in the spear's reach, the server is told you just charged at it. **Boost** sets how fast (default 9.5).
- The old fly-at-the-target behaviour is still there as **Rush**.

**CrystalAura and AnchorAura**
- Fixed: both placed and detonated late or not at all. Every click waited for a slow turn, a tick for the key press
  and a tick for each slot switch.
- New **Speed** setting, **Instant** by default: the aura looks at its spot and clicks in the same tick.
- AnchorAura places, charges and detonates an anchor in one tick.
- Delays are now in ticks: CrystalAura **Place Delay** and **Break Delay**, AnchorAura **Step Delay** (all 0).
- **Smooth** keeps the old human-like turning and the millisecond delays.

### 5.1 beta 7a: AutoMace with Wind Burst, an easy-to-find addon store, and a #acquire portal fix

**AutoMace**
- Fixed: AutoMace didn't swap or hit when you came down on a target from above, most of all after a Wind Burst
  launch. Every drop now counts from its own top, so it smashes again on the way back down, and it swaps and hits in
  the same tick it sees the target.
- New **Aim** setting. **Nearby** (the default) smashes a target below you that's in reach and in sight, without
  turning your view. **Crosshair** only hits what you're looking at, like before.

**Addon store**
- The store is now one step away from anywhere:
- **Ctrl+K**: type `addons`. Store addons can be found by name too, e.g. `better esp`.
- **Chat**: `.addons`.
- **Module menu**: the new **Addons** button.
- **Title screen**: an **ADDON STORE** button and a puzzle icon.
- **Mod Menu**: DIH's settings.
- **My Addons** in the store lists what you have installed, and switches back and forth with the store.

**#acquire**
- Fixed: the bucket portal (no obsidian, no diamond pickaxe) gave up while water was still flowing and couldn't reach
  lava in the middle of a pool. It now waits for the water to settle and casts, lights and enters the portal.

### 5.1 beta 5b: #parkour, #construct, a new module menu, Reach and Criticals

**#parkour**
- New: **`#parkour`** runs the parkour course you're standing on. Each jump is solved exactly: the client's own
  movement is simulated tick by tick and a search finds the keys and turns that land, including neos, strafe jumps,
  slabs, fences and ladders. It lands still on one-block pillars instead of sliding off. Only normal key presses and
  turning, nothing a player couldn't do.
- `#parkour goto x y z` jumps your way to a block; `#parkour solve x y z` says how a jump is done without moving.
- With `allowParkour` and the new `allowHardParkour` on, Baritone also paths through jumps its own parkour can't
  make (diagonal, round a wall, onto fences and slabs) and hands them to the solver. `parkourPreview` draws the line a
  jump will follow.
- Not yet: triple neos, slime and ice-momentum jumps. Neo jumps work but leave little room for lag.

**#construct**
- New: **`#construct <file> [x y z]`** builds a schematic from an empty inventory. It works out what the blocks still
  to place cost, gathers a batch that fits your inventory with `#acquire` (mining, crafting and smelting), builds
  what it can and repeats. It never mines inside the build, and gathered blocks aren't thrown away or pillared with.
- `#construct plan <file>` lists what the build takes and what you still need; `status` and `stop` as usual.

**Combat**
- New: **Reach** raises entity reach (up to 6) and block reach (up to 5.5). A vanilla server allows a little over the
  normal range; anticheats check tighter.
- New: **Criticals** turns hits on the ground into critical hits. **Packet** mode crits every hit at once but
  anticheats flag it; **Jump** mode really jumps and hits on the way down, which is plain movement but slower.

**UI**
- The module menu is now one window: a category rail (All, Enabled and each category), the module list and the
  selected module's settings side by side. Search finds modules across every category; arrow keys and Enter work in
  the list. The old floating category windows are still a setting away.
- Rounded corners, soft shadows and quieter headers across every DIH window, and new category icons.

### 5.1 beta 5a: the Guardian, the AI tools, #beat to the Nether, MaceKill and SpearKill

**Combat**
- New: **MaceKill** turns every mace hit into a smash by telling the server you fell first (up to 20 blocks, less
  under a roof). A smash the server turns down leaves you that fall, so by default it skips targets still flashing
  from a hit. **SpearKill** rushes a spear charge into its target for a full-speed hit. Both flag anticheats.

**#acquire and Baritone**
- New: the **Guardian**. While a Baritone job runs it steps in on lava, falls (water bucket), creepers, attacking and
  shooting mobs, drowning, low health and strangers, then lets the job carry on. Hurt or outnumbered at night it digs
  in and seals a pit until healed.
- Kill steps fight properly: crits, shield, bow, and backing off from a hissing creeper.
- The crafting table and furnace come along and are picked back up; tool durability is budgeted; gear comes before
  ore trips; every step has a time limit. Big smelts are split across up to 4 furnaces.
- Fixed: handed the cobblestone for a pickaxe, `#acquire` pillared up with it to reach wood, mined it back, and
  pillared again. Blocks the plan still needs are no longer used to build with.
- A path that keeps the player in one spot for a minute (mining while standing in water, say) counts as failed and is
  re-planned; eating that keeps failing is retried after 30 seconds instead of given up on.
- New: **`#beat`**, a saved campaign that plays toward beating the game through `#acquire` (`#beat plan`, resume and
  stop). This beta reaches the Nether (it builds or casts the portal), walks fortresses and fights blazes, and barters
  with piglins. Blaze rods and ender pearls are still being made reliable: that is beta 5b.

**AI**
- `.ai setup` opens a setup screen with provider presets (OpenRouter, OpenAI, Claude, Gemini, Groq, DeepSeek, Qwen,
  Ollama, LM Studio) and a Test button that says what's wrong in plain words.
- Fixed: a Groq (or OpenAI, OpenRouter, Claude, Gemini) key was sent to the default Qwen server and only got
  "HTTP 401". A pasted key now picks its provider, `#ai provider` lists and switches providers in one command, and a
  refused key says when it belongs to another provider.
- `.ai start <goal>`: the model plans once and is asked again only when a step fails; without a key it runs in a
  simpler basic mode. `.ai panel` shows a live checklist with Pause, Stop and Why?.
- 155 AI tools in one catalog; `.tools` runs any of them from chat, and macros can call them.

**Everything else**
- Ctrl+K command palette: search modules, settings, `.` and `#` commands and macros from anywhere.
- AutoTrade: buy villager trades on a budget and reroll librarians for a wanted book.
- Windows remember where they were; pin one to keep it on screen in-game. Text Size (80-150%).
- Macro folders, and share codes for macros and folders (`.macro export` / `.macro import`).
- A short first-run tour.

### 5.1 beta 3: smelting works, and healing is more reliable

- Fixed: Baritone stopped ticking whenever a screen was open and it wasn't walking. #acquire
  froze at every smelting step with the furnace open (and never timed out), and any Baritone task
  paused while you had chat, your inventory or a chest open.
- Eating holds and releases the use key reliably, puts your hotbar slot back, and stops cleanly when
  the world or screen changes. Harmful food (rotten flesh, spider eyes...) only when starving with
  nothing else held.
- A food detour now fetches the missing food plus a spare meal.
- `#acquire any food` (and "foods", "something to eat") works again, as in beta 2.
- Tested in a real singleplayer client: an emergency golden apple, a normal meal, `#acquire dirt 8`,
  and `#acquire stone_bricks 4` from an empty inventory (logs, tools, cobblestone, furnace, smelting).

### 5.1 beta 2: #acquire looks after your health

- While #acquire runs it eats when you're hurt and below 18 food, or at 6 food.
  Baritone pauses for the meal and carries on afterwards.
- Golden apples only at emergency health (acquireEmergencyHealth), backing off
  from a fight first. Never chorus fruit or poisonous food; food the plan needs
  only in an emergency.
- With nothing safe to eat and low health or food, it fetches the cheapest food
  it can plan, eats, then goes back to the item.
- New: #acquire food [count] and #eat [item|stop].
- Settings: acquireHeal, acquireHealHealth, acquireEmergencyHealth.

