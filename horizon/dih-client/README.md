# DIH Client for Horizon (native mod)

[Horizon](https://github.com/horizon-rust/horizon) is Minecraft: Java Edition 26.3 rewritten in Rust. It has no
JVM, so the Fabric/Forge/NeoForge jars this repository builds don't run there. This folder is the start of a
native port: a [horizon-mixins](https://github.com/horizon-rust/horizon-mixins) bundle that the Horizon launcher
compiles into the game.

## What is ported

| DIH module | Horizon | Notes |
|---|---|---|
| Module core | key toggles, saved settings, `[DIH] X enabled` chat line | Settings live in `horizon-mods/.horizon-mod-data/dihclient/` |
| Active Modules (HUD) | top right, widest first, Rainbow / Flat / Random colors | DIH's rainbow and soften values |
| Zoom | hold or toggle, eased in and out, wheel zooms, slower turning | One transition time for in and out; no Cinematic, Hide HUD or Remember Scroll yet |
| SafeWalk | sneaks at drops ahead, Look Down, Edge Distance, Min Drop, hold band, Pause When Jumping, Only Backwards | No Only With Blocks or Scaffold hand-off yet |
| AntiAFK | idle time, Random / All, Jump, Walk, Sneak | Rotate, Swing, Hotbar, Chat and Spin need writes the hook points don't offer |

Everything else in DIH Client is not ported. Most combat and movement modules (KillAura, Reach, Velocity-style
tweaks, packet modules) need to write player motion, rotation or packets, which Horizon's stable hook points
don't allow. Those need commit-pinned mixins on Horizon internals, which is the next step.

## Keys and settings

Options > Mods > DIH Client lists the keys (Zoom on C; SafeWalk and AntiAFK unbound) and a settings page where
each button cycles one setting.

## Layout

| File | What it is |
|---|---|
| `mixins/dih_client/horizon-mixin.toml` | The bundle: one module and eight HEAD hooks on `native_hooks.rs` |
| `mixins/dih_client/runtime.rs` | All logic and state, added to the game as `game::dih_client`; also this crate's library, for its tests |
| `mixins/dih_client/*.rs` | One small helper per hook |
| `compatibility.json` | Hook API it needs (411) and the Horizon commit it was checked on |

The helpers read `game.settings.fov` directly (the base field of view, for DIH's degree-based zoom). That field is
not a hook point, so a Horizon rename would make the launcher leave the mod out until it is updated.

## Build and check

Needs Linux (WSL works), Rust 1.95, Python 3.12, and a checkout of
[horizon-rust/horizon-mods](https://github.com/horizon-rust/horizon-mods) for its `native/tools/native.py`:

```sh
cargo test                                                   # the logic, in this folder
cp -r . ../horizon-mods/native/dih-client                    # beside Horizon's own native mods
python3 ../horizon-mods/native/tools/native.py check dih-client     # apply on Horizon and cargo check the game
python3 ../horizon-mods/native/tools/native.py package dih-client   # native/target/dihclient-0.1.0.hnmod
```

To play it, put the `.hnmod` in the game folder's `horizon-native-mods/` and build from the launcher's Mods page.
Native mods are unsandboxed native code, and the launcher shows a trust warning before building one.
