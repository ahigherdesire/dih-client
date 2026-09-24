# DIH Client — Instruction Manual

> Version **5.0** · Minecraft **26.2** · Fabric Loader **0.19.3** · Java **25+**
> A packet / macro / duping toolkit for players who want direct control over what the server actually receives.

This manual is split into two halves:

1. **Using the client** — menus, keybinds, and every subsystem, written for players.
2. **Packet managers** — the part you asked to be documented most carefully: *where every packet control lives*, both in the in‑game UI **and** in the source tree. See **[§5 Packet Management](#5-packet-management-read-this-first)**.

---

## 1. Install & Build

DIH is a standard Fabric client mod.

**To run a release build:**
1. Install Fabric Loader `0.19.3+` for Minecraft `26.2` and the Fabric API.
2. Drop the `DIH Client-*.jar` into your `.minecraft/mods/` folder.
3. Launch the Fabric profile.

**To build from source:**
```bash
./gradlew build          # produces the client jar under build/libs/
./gradlew runClient      # launches a dev client
```
- Config lives in `config/` (see [§8](#8-file-locations)).
- Addon jars go in `mods/` alongside the client (see [§7](#7-addons)).

---

## 2. Opening the Client

| Action | Default key | Notes |
|---|---|---|
| **Module menu (ClickGUI)** | `Right Shift` | Main hub — toggle modules, open editors. |
| **Restore / Load stored GUI** | `V` | Re‑opens the last GUI you saved (see §5.2). |
| Commands | `.` prefix in chat | e.g. `.help`, `.modules`, `.macro`. |

Keybinds are configurable in **DihConfig** (`config/dih/config.json`). The full defaults are in [§9](#9-keybind-reference).

---

## 3. Modules

69 built‑in modules covering combat, movement, render/ESP, world, and utility. Toggle them from the module menu or with `.toggle <module>`.

- Registry: `modules/ModuleRegistry.java`, `modules/BuiltinModules.java`
- Categories: `modules/ModuleCategory.java`
- Base class: `modules/DihModule.java`

Highlights: `KillAuraModule`, `CrystalAuraModule`, `ScaffoldModule`, `BlinkModule`, `PhaseModule`, ESP family (`ModuleStorageEsp`, `ModuleBlockEsp`, `HoleEspModule`, `ModuleSpawnerEsp`), `WaypointsModule`, `PingSpoofModule`, `TrajectoriesModule`.

---

## 4. Macros

The macro system is the core automation engine — a scripted sequence of **actions** guarded by **conditions**, with variables and capture groups.

- Editor UI: `gui/macro/editor/` (open from the module menu).
- Runtime: `util/macro/MacroExecutor.java`, `util/DihMacroManager.java`.
- Actions live in `util/macro/*Action.java` (100+ actions).
- Conditions: `util/macro/MacroCondition*.java`.
- Variables & captures: `util/macro/MacroVariables.java`, `MacroCapturePattern.java`.
- Command entry point: `.macro`.

Macros are stored in `config/dih/dih_macros.nbt`.

The most relevant macro actions for packet work are listed in [§5.4](#54-packet-related-macro-actions).

---

## 5. Packet Management (read this first)

This is the client's headline feature and the part most worth understanding. There are **four distinct packet subsystems**. Keep them straight — they operate at different layers.

```
                 ┌─────────────────────────────────────────────┐
   your action → │  DihClientConnectionMixin.send()  (HOOK)  │ ← every outgoing packet passes here
                 └───────────────┬─────────────────────────────┘
                                 │ consults runtime flags:
        ┌────────────────────────┼──────────────────────────────┐
        │                        │                               │
  Send disabled?           Delay enabled?                 A Packet Gate active?
   (drop packet)        (enqueue → queue)                (cancel/delay/allow-only)
        │                        │                               │
        └──────→ DihSharedState (queue, flags, stored GUI) ←──┘
                                 │
                     DihGuiActions (save / load / desync / close GUI)
```

### 5.1 The runtime packet queue — Send / Delay / Flush / Clear

This is the "packet manager" in the everyday sense: a live queue of outgoing packets you can hold, edit, release, or drop.

**Where it lives in code:**

| Piece | File | Role |
|---|---|---|
| Interception hook | `mixin/DihClientConnectionMixin.java` | Hooks `ClientConnection.send(...)`. If **send** is disabled it cancels the packet; if **delay** is enabled it calls `shared.enqueuePacket(packet)`. This is the single choke point every C2S packet passes through. |
| Queue + flags | `util/DihSharedState.java` | Owns `delayedPackets` / `staggeredQueue`, `enqueuePacket(...)`, `flushDelayedPackets(...)`, `clearQueuedPackets()`, `setSendGuiPackets(bool)`, `setDelayGuiPackets(bool)`. **This is the actual manager object.** |
| Player‑facing toggles | `modules/DihModule.java` | `setSendGuiPackets`, `setDelayGuiPackets`, `flushDelayedPackets`, `clearQueuedPacketsUiBehavior` — the methods the UI/keybinds call. |
| Visual queue editor | `util/DihQueueEditorOverlay.java` | The overlay that lists queued packets and lets you reorder / retime / flush individual entries. |

**Where it lives in the UI / keybinds** (all default to *unbound* — set them in config):

| Control | Config keybind field | What it does |
|---|---|---|
| Toggle **Send** | `keybindToggleSend` | When off, outgoing packets are dropped instead of sent. |
| Toggle **Delay** | `keybindToggleDelay` | When on, outgoing packets are captured into the queue instead of sent. |
| **Flush** queue | `keybindFlushQueue` | Releases all queued packets to the server. |
| **Clear** queue | `keybindClearQueue` | Discards queued packets without sending. |
| Toggle **packet logger** | `keybindToggleLogger` | Shows/hides the live packet log overlay. |

### 5.2 GUI packet actions — Save / Load / Desync / Close

These manipulate container (inventory/GUI) packets specifically — the classic dupe primitives. **All four live in one file:** `util/DihGuiActions.java`.

| Action | Method | What actually happens on the wire |
|---|---|---|
| **Save GUI** | `saveCurrentGui(mc, notify)` | Stores the current `Screen` + `containerMenu` into `DihSharedState.storeScreen(...)`. **Client‑side only — no packet sent.** |
| **Load / Restore GUI** | `RestoreGuiAction` / keybind `V` (`keybindLoadGui`) | Re‑opens the stored screen and reassigns `player.containerMenu` from `DihSharedState.getStoredScreen()`. No packet sent. |
| **Desync** | `desyncCurrentScreen(mc, notify)` | Sends a `ServerboundContainerClosePacket` **while keeping the client screen open** — server thinks the container is closed, client keeps interacting. This is the desync primitive. |
| **Close GUI** | `closeCurrentScreen(mc, sendPacket, notify)` | Closes the screen; `sendPacket=false` closes locally without telling the server (suppresses the next container‑close packet via `DihSharedState.setSuppressNextContainerClosePacket`). |

Stored‑GUI state accessors live in `util/DihSharedState.java` (`storeScreen`, `getStoredScreen`, `getStoredAbstractContainerMenu`, `clearStoredScreen`).

### 5.3 Packet Gate — per‑macro packet filtering

A **gate** is a named, scoped rule that cancels / delays / allow‑only's specific packet types for a window of time. Used inside macros for precise wire control.

- Manager: **`util/macro/PacketGateManager.java`** — `install(...)`, `disable(id)`, `disableAndFlushConfigured(...)`, `clearAll()`, `handle(packet, direction)`, `hasActiveGates()`. Every packet is run through `PacketGateManager.handle(...)` from the connection mixin.
- Configure a gate: `util/macro/PacketGateAction.java`
  - `mode`: `CANCEL` / `DELAY` / `ALLOW_ONLY` / `DISABLE_GATE`
  - `direction`: `C2S` / `S2C` / `ANY`
  - `durationMode`: `UNTIL_DISABLED` / `TICKS` / `MS` / `UNTIL_PACKET` / `UNTIL_GUI` / `UNTIL_INVENTORY`
  - `packetNames`, `gateId`, `flushOnDisable`
- End a gate: `util/macro/EndPacketGateAction.java` → `PacketGateManager.disableAndFlushConfigured(gateId, ...)`.

Gates are cleared automatically on disconnect (`DihClientConnectionMixin` → `PacketGateManager.clearAll()`).

### 5.4 Packet‑related macro actions

All in `util/macro/`. These are how packet control is scripted inside a macro (`MacroActionType` enum lists them):

| Action file | Type | Purpose |
|---|---|---|
| `DesyncAction.java` | `DESYNC` | Fires the desync (§5.2). |
| `SaveGuiAction.java` | `SAVE_GUI` | Save current GUI; optional close / desync‑on‑close. |
| `RestoreGuiAction.java` | `RESTORE_GUI` | Restore the stored GUI. |
| `CloseGuiAction.java` | `CLOSE_GUI` | Close with or without a packet, with GUI/item filters. |
| `PacketGateAction.java` | `PACKET_GATE` | Install a gate (§5.3). |
| `EndPacketGateAction.java` | `END_PACKET_GATE` | Tear a gate down and flush. |
| `DelayPacketsAction.java` | `DELAY_PACKETS` | Toggle the delay queue from a macro. |
| `PacketBurstAction.java` | `PACKET_BURST` | Fire a burst of packets. |
| `SendPacketAction.java` | `SEND_PACKET` | Send a raw/custom packet. |
| `SendCommandPacketAction.java` | — | Send a command packet directly. |
| `PayloadAction.java` | — | Custom payload / plugin‑channel sender. |
| `WaitForPacketAction.java`, `WaitPacketMatchAction.java` | — | Block the macro until a matching packet arrives. |

### 5.5 Packet logger

Live view of packets flowing through the connection.

- Overlay: `util/DihPacketLoggerOverlay.java`
- Toggle: `keybindToggleLogger`

---

## 6. Commands

Chat commands (default prefix `.`, change with `.prefix`). Implementations in `commands/impl/`. A few useful ones:

`.help` · `.modules` · `.toggle <module>` · `.macro` · `.bind` / `.binds` · `.send` (raw packet) · `.delay` · `.nbt` · `.click-slot` / `.click-item` / `.change-slot` · `.xcarry` · `.hclip` / `.vclip` · `.give` / `.damage` · `.gamemode` / `.fakegm` · `.disconnect` · `.waypoints` · `.friend` · `.sync`.

Full list: `.commands`.

### 6.1 Full CLI — every subsystem from chat

The whole client is drivable from chat. The packet/dupe workflow and all module settings have first-class commands:

| Command | File | What it does |
|---|---|---|
| `.packet status` | `commands/impl/PacketCommand.java` | Show send/delay flags + queue size |
| `.packet send <on\|off\|toggle>` | ″ | Toggle sending outgoing packets |
| `.packet delay <on\|off\|toggle>` | ″ | Toggle the delay queue |
| `.packet flush` / `.packet clear` | ″ | Release / discard queued packets |
| `.gui save` | `commands/impl/GuiCommand.java` | Store the current screen handler (no packet) |
| `.gui desync` | ″ | Send close packet, keep the screen open |
| `.gui load` | ″ | Re-open the stored screen handler |
| `.gui close [packet\|nopacket]` | ″ | Close the screen, with or without the close packet |
| `.gate cancel\|delay\|allow <packet…>` | `commands/impl/GateCommand.java` | Install a packet gate (id `cli`) |
| `.gate end [id]` / `.gate clear` / `.gate status` | ″ | End one / all gates, or list active gates |
| `.setting <module>` | `commands/impl/SettingCommand.java` | List a module's settings + values |
| `.setting <module> <key> [value]` | ″ | Get or set any module setting (`reset` = default) |

**Scriptable desync dupe test**, end to end, without touching a GUI:
```
.gui save          # store the container handler
.gui desync        # server closes its container; screen stays open
.click-slot 0 1 5  # extraction clicks (ServerboundContainerClickPacket)
.gui load          # re-inject the stored handler
```
Wrap that in a macro (`.macro <name>`) to run it repeatedly against your own server (see §5).

---

## 7. Addons

DIH is expandable — addons can add modules, macro actions, conditions, presets, HUD elements, commands, events, and mixins.

- Loader: `addons/AddonManager.java` (scans the `mods/` folder).
- Templates: `addon-templates/` in the repo, plus the `addon templates` / `addon toolkit` Gradle tasks in `build.gradle.kts`.
- Presets: `util/DihPresetManager.java`.

---

## 8. File Locations

Base directory: **`config/dih/`** (created by `DihClientAddon.FOLDER`, i.e. `FabricLoader.getConfigDir()/dih`).

| File | Contents |
|---|---|
| `config.json` | Client + keybind config (`util/DihConfig.java`) |
| `dih_macros.nbt` | Saved macros (`util/DihMacroManager.java`) |
| `dih-accounts.nbt` | Accounts (`util/DihAccountManager.java`) |
| `dih-proxies.nbt` | Proxies (`util/DihProxyManager.java`) |
| `waypoints.json` | Waypoints |
| `dupedb-cache.json`, `dupedb-token.json` | Dupe Radar cache/token |
| `server-plugin-scans.json` | Server plugin scan cache |

---

## 9. Keybind Reference

Defaults from `util/DihConfig.java` (`-1` = unbound):

| Field | Default | Function |
|---|---|---|
| `keybindModuleMenu` | `Right Shift` | Open module menu / ClickGUI |
| `keybindLoadGui` | `V` | Restore stored GUI |
| `keybindToggleSend` | unbound | Toggle sending outgoing packets |
| `keybindToggleDelay` | unbound | Toggle the delay queue |
| `keybindFlushQueue` | unbound | Flush queued packets to server |
| `keybindClearQueue` | unbound | Discard queued packets |
| `keybindToggleLogger` | unbound | Toggle packet logger overlay |
| `keybindInsideGui` | `false` | Allow keybinds to fire while a GUI is open |

---

## 10. Source Map (developer quick reference)

```
src/main/java/dihclient/
├── DihClientMod.java          # Fabric entrypoint (onInitializeClient)
├── DihClientAddon.java        # config folder + logger + MOD_ID
├── modules/                      # 69 modules + registry
├── util/
│   ├── DihSharedState.java    # ★ packet queue, flags, stored-GUI state
│   ├── DihGuiActions.java     # ★ save / load / desync / close GUI
│   ├── DihQueueEditorOverlay.java   # visual packet queue editor
│   ├── DihPacketLoggerOverlay.java  # live packet log
│   ├── DihConfig.java         # config + keybinds
│   ├── DihMacroManager.java   # macro persistence
│   └── macro/                    # ★ all macro actions incl. PacketGateManager
│       ├── PacketGateManager.java
│       ├── PacketGateAction.java / EndPacketGateAction.java
│       ├── DesyncAction / SaveGuiAction / RestoreGuiAction / CloseGuiAction
│       └── DelayPacketsAction / PacketBurstAction / SendPacketAction ...
├── mixin/
│   └── DihClientConnectionMixin.java  # ★ the send() hook — every packet passes here
├── commands/impl/                # chat commands
├── gui/                          # ClickGUI, macro editor, HUD, menus
└── addons/AddonManager.java      # addon loading
```

★ = the four packet‑management touch points.

---

*Credits & licensing: see `CREDITS.md` and `LICENSE` (GPL‑3.0). DIH derives from Meteor, Wurst, OpSec, ExploitPreventer, Dupe Radar, and Better Storage ESP.*
