# DIH Client

DIH Client is the best Duping/Debugging client built for people who actually want control over what actions server recieves.

It is not trying to be a bloated all-in-one mess with 400 random buttons nobody uses. The point is simple: useful modules, a strong macro system, clean addon support, and tools that make testing, automation, and server interaction less painful.

This client is built around speed, control, and not pretending that users are too stupid to touch advanced features.

**Download:** https://github.com/ahigherdesire/dih-client/releases · **Website:** the static site in `website/`; run it locally with `python website/serve.py` (http://localhost:8080).

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.3+ for Minecraft 26.2 and [Fabric API](https://modrinth.com/mod/fabric-api). Java 25 is required.
2. Put `DIH-Client-<version>-26.2.jar` in `.minecraft/mods`.
3. Launch. `R.Shift` opens the module manager, `.help` lists client commands, `#help` lists Baritone commands.

Coming from XINYUAN or Autism Client? Your accounts, proxies, macros and config are copied to the new `dih` names on first launch; the old files are left in place.

## Building

```
./gradlew build -Prelease
```

The jar lands in `build/libs/`. If the project sits in a synced folder (Google Drive, OneDrive) that locks files, build elsewhere with `-PdihBuildDir=C:/dih-build`.

The UI uses the [Geist](https://github.com/vercel/geist-font) typeface (SIL OFL 1.1, shipped in `META-INF/licenses`). Set `modernFont` to `false` in the config to go back to the pixel font.

## What It Includes

DIH Client includes the normal client stuff you would expect, but the main focus is on systems that are actually useful.

Main features include:

* module system
* macro editor
* custom macro actions
* custom macro conditions
* addon support
* addon presets
* LAN sync tools
* custom payload sender
* NBT viewer
* packet and payload testing tools
* HUD elements
* commands
* event system

The client is made to be expandable. If the base client is not enough, addons can add their own modules, macro actions, conditions, presets, HUD elements, commands, events, and mixins.

## AI & Pathfinding (integrated)

DIH Client now ships with a full Baritone-based pathfinding engine and the MinecraftAI feature set built directly into the jar — no second mod required. These are driven with the `#` command prefix in chat:

* `#ai` — hand the controls to a language model: it reads server chat and plays by running Baritone commands. Configure with `#ai key <api-key>` (or the `MINECRAFTAI_LLM_KEY` env var), `#ai url <openai-compatible-endpoint>`, `#ai model <name>`, then `#ai on`. Defaults to an OpenAI-compatible endpoint running `qwen-plus`.
* `#goto` / `#mine` / `#follow` / `#elytra` and the rest of the Baritone command surface — pathfinding, mining, and Overworld/Nether elytra travel.
* `#threats` / `#players` — proximity alerts plus a persistent player-sighting log.
* `#chest <item>` — silently records every container you open; search by item name and navigate to the result.
* `#structure` / `#where` — locate structures in singleplayer or multiplayer. On multiplayer they work from the seed (`#seedinput <seed>`) and are biome-validated, strongholds included.
* `#seedmap` — an in-game seed map (like mcseedmap.net): every structure around you, validated by running vanilla structure generation offline, drawn on the JourneyMap fullscreen map with explorer-map icons, real footprints, hover details and right-click "go to". `#seedmap village monument`, `#seedmap 4000`, `#seedmap strongholds`, `#seedmap wp`, `#seedmap off`.
* `#autosleep` — navigate to a bed automatically at night (experimental).

* `#seedinput <seed>` — the world seed for the server you're on (numbers or text, converted like the world-creation screen). Each server keeps its own seed, shared with OreSim, and it's **verified** against the seed hash the server sends when you join — a wrong seed is refused instead of silently drawing a wrong map. `#seedinput check` shows the status.

Baritone's in-world overlays all work on 26.2: `#sel` selection boxes, the `#click` GUI highlight and elytra flight paths.

While Baritone is pathing, its route is drawn in the world: a guide line from you to the next step, the current and queued path, the goal (box for block goals, beacon column for `#goto x z`), and the blocks it will break or place. Toggle with `#set renderPath false` / `#set renderGoal false`; colours and widths use the usual Baritone settings (`colorCurrentPath`, `pathRenderLineWidthPixels`, …).

Pathfinding, the native nether pathfinder, and all AI behaviour are bundled into the single client jar.

### JourneyMap (install it with the client)

DIH has no minimap of its own; it draws on JourneyMap's. Download [JourneyMap for Fabric 26.2](https://modrinth.com/mod/journeymap/versions?g=26.2&l=fabric) and put it in `mods` next to the DIH jar. It isn't bundled because JourneyMap's licence doesn't allow redistribution. The client registers as a JourneyMap plugin, which adds:

* toolbar toggles on the fullscreen map for the seed-map layer (map icon) and the `#heatmap` layer (fire-charge icon);
* right-click on the map → "Baritone: go here" or "Seed map around here";
* right-click on a structure → "Baritone: go to …" or "Add waypoint".

Without JourneyMap everything else still works; `#seedmap` then lists results in chat with clickable coordinates.

---

# Credits

DIH Client includes original code written for this project, but it also uses, references, or derives ideas/code from other open-source Minecraft client projects.

Huge credit to the following developers and projects:

## OpSec by aurickk

Parts of DIH Client related to client privacy, mod detection protection, compatibility handling, and anti-fingerprinting behavior are inspired by or derived from OpSec.

Project: https://github.com/aurickk/OpSec
License: GPL-3.0

## Meteor Client

DIH Client uses ideas, structure, APIs, or source-code references from Meteor Client, especially around Fabric client architecture, modules, utilities, and client-side systems.

Project: https://github.com/MeteorDevelopment/meteor-client
License: GPL-3.0

## Wurst Client

DIH Client uses ideas, references, or code patterns from Wurst Client, one of the long-running open-source Minecraft utility clients.

Project: https://github.com/Wurst-Imperium/Wurst7
License: GPL-3.0

## ExploitPreventer by NikOverflow

DIH Client uses ideas, references, or derived logic from ExploitPreventer for protection against known client-side exploits.

Project: https://github.com/NikOverflow/ExploitPreventer
License: MIT

## Dupe Radar by ErneTalu

The Radar idea is credited to Dupe Radar by ErneTalu.

Project: https://github.com/ErneTalu/Dupe-Radar

## Better Storage ESP by bestluaucoder

The Storage ESP "Ignore Structures" filters come from the Better Storage ESP addon: the idea of hiding containers that generated inside a structure, and the list of structures worth filtering.

Project: https://github.com/bestluaucoder/Xinyuan-Better-Storage-esp-1-addon/tree/main

## Baritone / MinecraftAI

The integrated pathfinding engine and AI feature set are built on Baritone (via the MinecraftAI fork). The `baritone.*` source tree bundled in this client is derived from those projects.

Baritone project: https://github.com/cabaletta/baritone
License: LGPL-3.0

## License

DIH Client is a fork of [Autism Client](https://github.com/AutismDevelopment/Autism-Client) by Melonik, simeonvartik and the AutismDevelopment contributors, modified by ahigherdesire (see `NOTICE.md` for what changed).

DIH Client is free software under the GNU General Public License, version 3 or (at your option) any later version. See `LICENSE`. The complete source for every release is at https://github.com/ahigherdesire/dih-client, and anyone who redistributes a modified version must keep it under the same terms and keep these notices.

Bundled libraries and fonts keep their own licenses; see `licenses/THIRD_PARTY_NOTICES.md` (shipped in the jar under `META-INF/licenses/`).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

If any credit is missing or inaccurate, contact the maintainers so it can be corrected.
