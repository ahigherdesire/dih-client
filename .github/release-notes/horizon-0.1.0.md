DIH Client for Horizon 0.1.0: the first native port

[Horizon](https://github.com/horizon-rust/horizon) is Minecraft: Java Edition 26.3 rewritten in Rust. It can't load
Fabric jars, so this is a separate build: a Horizon **native mod** (`.hnmod`) that the Horizon launcher compiles
into the game. It is an early preview with four pieces of DIH Client; everything else is still Fabric only.

**In this build**
- **Active Modules**: the HUD list in the top right, widest first, in DIH's Rainbow, Flat or Random colors.
- **Zoom**: hold C (or set it to toggle), eased in and out; the wheel zooms further and turning slows with the zoom.
- **SafeWalk**: sneaks at drops ahead of you, with Look Down, Edge Distance, Min Drop, Pause When Jumping and Only
  Backwards.
- **AntiAFK**: after you idle a while, jumps, steps or crouches now and then, and stops the moment you move or turn.
  Turning, swinging, hotbar and chat actions aren't on Horizon yet.

SafeWalk and AntiAFK have unbound toggle keys; set them, and every setting, under Options > Mods > DIH Client.

**Install**
1. Install the [Horizon launcher](https://playhorizon.dev) and sign in with an account that owns Java Edition.
2. Put `dihclient-0.1.0.hnmod` in your Horizon game folder's `horizon-native-mods/` (the folder holding
   `options.txt`).
3. Open the launcher's Mods page and press **Build**. Native mods are unsandboxed native code, so the launcher
   shows a trust warning first.

Needs a Horizon with native hook API 411 or later (Nightly 2026-11-08 or newer). Checked by compiling against
Horizon; not yet played on a server, so try it in a singleplayer world first.
