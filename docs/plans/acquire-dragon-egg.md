# Plan: `#acquire dragon_egg` beats the game

**Goal.** In a fresh survival world (Normal difficulty, empty inventory, Overworld spawn), `#acquire dragon_egg`
runs with no further input until the dragon egg is in the inventory: wood and stone, iron and diamonds, a nether
portal, blaze rods, ender pearls, eyes of ender, the stronghold, the End, the dragon, the egg.

**Status.** Plan only. Nothing here is built yet. Written 2026-09-26 against `beta` at `39ef83c`
(5.1-beta.2 plus fixes).

---

## 1. Where we start

What `#acquire` can do today (5.1 beta):

- Plans any item from the real inventory: mine, craft, smelt and kill, with re-planning when a step fails.
- Heals while it runs: eats, backs off, fetches food (`acquireHeal`), and `#eat`.
- Never hunts players, pets, villagers or golems.

What it cannot do, and why `#acquire end_stone` fails today:

- **The planner has no idea of dimensions.** It sees "end stone: mine with a pickaxe", plans a wooden pickaxe,
  then Baritone searches the Overworld for end stone, fails 8 times and gives up. The same goes for every
  Nether-only or End-only item.
- **No travel steps.** Nothing builds a portal, walks through one, finds a structure or fills an end portal.
- **Combat is basic.** `KillRunner` walks up and swings. That is fine for cows, not for blazes or the dragon.
- **No goal outside items.** "Kill the dragon" and "push the egg off the pillar" are not items with recipes.

Pieces already in the client that the plan reuses:

| Piece | Where | Used for |
|---|---|---|
| `#structure` (stronghold, fortress, bastion...) | `StructureCommand`, `SeedStructureScanner` | Finding the stronghold and a fortress without searching. Singleplayer asks the integrated server; multiplayer uses the seed (`#seedinput`). |
| `#portal` (go to and enter a nether portal) | `PortalCommand` | Using portals once built. |
| Baritone Nether pathfinder, elytra | `ElytraProcess`, `baritone/process/elytra` | Moving around the Nether safely. |
| `BuilderProcess` | Baritone | Building the portal frame from a schematic. |
| Healing (`HealthPolicy`, `EatBehavior`) | `baritone/acquire/exec` | Every fight. |
| `AutoArmor`, `AutoTotem`, `KillAura`, `Surround` | `dihclient/modules` | Candidates for fight support (see M2). |
| Knowledge: blaze rod from blazes, obsidian needs a diamond pickaxe, blaze rod as fuel | `VanillaKnowledge` (tested in `VanillaExpectations`) | Planning the chain. |

---

## 2. The shape of the solution

Two changes carry most of the weight. Everything else is a new step runner.

### 2.1 A dimension-aware planner

- **Locations in the model.** `WorldView` and every `Source` get a location: `OVERWORLD`, `NETHER`, `END`, plus
  sites inside them that must be found first (`FORTRESS`, `STRONGHOLD`, `END_MAIN_ISLAND`). The knowledge base
  tags sources: end stone in the End, blazes at a fortress, nether quartz in the Nether, endermen anywhere
  (mostly in warped forests and the End).
- **Where the player is becomes part of the plan state.** `PlanState` tracks the current location, the way it
  tracks the inventory. A source in another location is only usable after a travel step.
- **Travel steps, with costs and prerequisites.** New `Step` records:
  - `Travel(from, to)`: Overworld to Nether needs a lit portal (see M3); Overworld to End needs the stronghold
    portal filled (see M5). Their prerequisites are ordinary item goals (obsidian x10, flint and steel, eyes of
    ender x12), so the existing planner machinery plans them.
  - `Locate(site)`: find the fortress or stronghold (`#structure`), falling back to a search (M4, M5).
- **Goals that aren't items.** A `Goal` type beside items: `ItemGoal(id, count)` (today's goal),
  `DragonDead`, `AtLocation(loc)`. `dragon_egg` gets one scripted source: needs `DragonDead` and a piston,
  collected by the egg runner (M6). `#acquire dragon_egg` then plans the whole run as one plan.
- **Explainable.** `#acquire plan dragon_egg` prints the full chain, phase by phase, before anything runs.

### 2.2 A campaign layer over the steps

A full run takes one to three hours of game time. The executor needs:

- **Phases** in the status line and chat (`Phase 3/7: the Nether, blaze rods 4/7`).
- **Saving and resuming.** The goal and progress are saved to the config folder, so a crash, disconnect or
  `#pause` picks up where it left off (`#acquire resume`).
- **Death recovery.** After a death: go back for the items if the death spot is reachable within the 5-minute
  despawn window, else re-plan from what is left. Today it only re-plans.
- **Gear checkpoints.** Before a dangerous phase, a gear goal must be met (M2), and the planner adds it
  automatically.

---

## 3. Milestones

Each milestone ships as its own beta and is useful on its own. Later milestones depend on earlier ones.

### M0: A way to test in the game (prerequisite)

Nothing below can be trusted without running it in Minecraft, and CI cannot run the game today.

- Add Fabric **client game tests** (Fabric API's client gametest module) to the build: a headless client in
  GitHub Actions (under xvfb) that creates a world from a fixed seed, runs commands and checks the result.
- Scenario tests set the stage with commands (`/give`, `/tp`, `/effect`), for example "standing at a fortress
  with a sword and iron armor, acquire 6 blaze_rod".
- A nightly job runs the full `#acquire dragon_egg` on a few fixed seeds and records how far it got.
- Also play-test the 5.1 healing, which every later fight depends on.

**Done when:** a scenario test and a full-run test run in CI and report results.

### M1: Dimension-aware planning (planner only)

- Locations, travel steps, non-item goals and the scripted `dragon_egg` source, as in 2.1.
- Unit tests with fake knowledge: plans for `blaze_rod`, `ender_eye`, `end_stone` and `dragon_egg` from an
  empty inventory have the right order (portal before the Nether, eyes before the stronghold, and so on).
- `#acquire plan dragon_egg` prints the whole chain. Running it still stops at the first travel step, with a
  clear message instead of 8 useless re-plans.

**Done when:** every item in the game either plans correctly or says exactly why not.

### M2: Gear and safer fighting

- **Gear goals.** Before the Nether: iron armor, a shield and a sword (iron or better). Before the End: a bow
  and about 64 arrows, blocks for pillaring, a water bucket, food for the fight. The planner adds these.
- **A combat runner** replacing `KillRunner` for dangerous mobs: shield up while approaching, critical hits,
  back off when a creeper hisses, strafe against skeletons and blazes, fight from cover.
- **Ranged attacks.** Aim a bow with drop compensation, used for blazes, End crystals and the dragon.
- **Safety settings** during `#acquire`: no pathing next to lava, water bucket landing for long falls (Baritone
  already has it), leave when on fire.

**Done when:** scenario tests win against 1 blaze, 3 blazes at a spawner and 5 zombies at night, over repeated
runs, without dying.

### M3: Obsidian and a nether portal

- **Diamonds.** Branch mine near y -58 (Baritone `#mine` already handles ore search).
- **Obsidian.** Mine natural obsidian where there is some (near lava lakes). Otherwise make it: pour water
  onto still lava source blocks, then mine the result. 10 blocks for a portal.
- **Build and light the portal** with `BuilderProcess` (a small portal schematic) and flint and steel, then
  enter it. Remember the portal's position in both dimensions.
- **Alternatives, costed by the planner:** complete a ruined portal, or the speedrun bucket method later.

**Done when:** from an empty inventory, `#acquire` reaches the Nether on 3 of 3 test seeds.

### M4: The Nether: blaze rods and ender pearls

- **Find a fortress** with `#structure fortress`. Without a seed in multiplayer, fall back to exploring along
  the Nether's X or Z axis, where fortresses cluster.
- **Travel** with Baritone's Nether pathfinder, avoiding lava and ghasts.
- **Blazes.** Find the spawner or patrolling blazes and kill them with the combat runner until there are 7 rods
  (enough for 14 powder, leaving spare).
- **Ender pearls, cheapest first (planner decides):**
  - endermen in a warped forest (`#structure` or biome search), or
  - bartering with piglins: gold ingots, wearing a gold armor piece, collect the dropped pearls, or
  - endermen in the Overworld at night (slow).
  12 pearls, plus spares for any that break when thrown in M5.
- **Get home.** Walk back to the remembered portal and step through.

**Done when:** a scenario test starting in the Nether with gear returns with 7 blaze rods and 12 pearls.

### M5: The stronghold

- **Eyes of ender.** Craft 12 or more (blaze powder plus pearls; existing crafting).
- **Find it.** With the seed known (always in singleplayer), `#structure stronghold` gives the position
  directly, with no eyes thrown. Otherwise, triangulate: throw an eye, track the eye entity's flight, move
  sideways a few hundred blocks, throw again, and intersect the two lines. Pick up eyes that don't break.
- **Reach the portal room.** Tunnel down near the stronghold's position, then search its corridors for the end
  portal frame blocks (Baritone's block cache; silverfish are handled by the combat runner).
- **Fill the frame.** Place eyes in every empty frame, then step in.

**Done when:** from the Overworld with 12 eyes, `#acquire` enters the End on 3 of 3 test seeds.

### M6: The End: the dragon and the egg

- **Landing.** Stand on the obsidian spawn platform. Never look an enderman in the eyes (keep the pitch down, or
  wear a carved pumpkin, the planner's choice). Bridge to the main island with blocks.
- **End crystals.** Shoot the crystals on the pillars with the bow. For caged ones, pillar up next to the
  tower and break the cage and crystal from below. Stay out of crystal blast range.
- **The dragon.** Wait for it to perch on the exit portal, then hit it with the sword (crits), backing off out
  of dragon breath. Shoot it with the bow while it circles. Water bucket against knockback falls. Heal between
  passes (healing already backs off at emergency health).
- **The egg.** The egg sits on the bedrock pillar of the exit portal. Place a piston beside it and power it with
  a lever: the pushed egg drops as an item. Pick it up. Done.

**Done when:** a scenario test starting in the End with full gear gets the egg, on repeated runs.

### M7: The full run

- **Saving, resuming and death recovery** (2.2), tested by killing the game mid-run and by forced deaths.
- **Timeouts and give-up rules per phase**, with a clear final report if it gives up
  ("stuck in phase 5: no stronghold portal room found near -1840 32 912").
- **Full runs** on 10 or more fixed seeds in CI.

**Done when:** `#acquire dragon_egg` from nothing succeeds on at least 7 of 10 test seeds, and every failure
ends with a readable reason, not a hang.

---

## 4. Risks and how the plan handles them

| Risk | Handling |
|---|---|
| Dying in lava or to fall damage (the biggest killer of bots) | M2 safety settings; water bucket always carried; death recovery in M7. |
| The dragon fight is the hardest thing to automate | Test it on its own from M6 scenarios before any full run; melee-at-perch is the simplest proven strategy. |
| Endermen aggro in the End | Pitch down or a carved pumpkin (M6). |
| No seed on multiplayer servers | Triangulation (M5) and axis exploring (M4) fall back to searching. |
| A full run takes hours, so bugs are slow to find | Scenario tests per milestone (M0) catch most bugs in minutes. |
| Servers treat this as botting | Singleplayer first. On servers it runs like any Baritone task and follows the existing Flee and `#stop` rules; using it there is the player's call. |

---

## 5. Order and size

| Milestone | Depends on | Rough size |
|---|---|---|
| M0 test harness | - | medium |
| M1 dimension-aware planner | - | large |
| M2 gear and combat | M0 | large |
| M3 obsidian and portal | M1 | medium |
| M4 the Nether | M1, M2, M3 | large |
| M5 the stronghold | M1, M4 | medium |
| M6 the End | M2, M5 | large |
| M7 the full run | all | medium |

M0 and M1 can start in parallel: M1 is pure code with unit tests, and M0 is build and test setup.
