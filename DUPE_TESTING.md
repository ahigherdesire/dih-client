# AUTISM Client — Dupe Testing Playbook

> **Scope: testing a server you own or are authorized to test.**
> Every method below is a duplication *technique* the client can perform. Whether it actually
> duplicates depends entirely on the server — that is the point of the test. Run each, then check
> the result. A method that fails to dupe means the server handles that path correctly.

---

## 0. Prerequisites

1. Install the client jar (`build/libs/AUTISM Client-5.0-26.2-dev.jar`) into `.minecraft/mods/` and launch.
2. Learn the two facts that make everything below work:
   - **You can't type chat while a container/inventory screen is open.** So you trigger dupes with a **bound key**, not by typing. Set binds with the screen closed.
   - Command binds fire even inside a container screen. Bind syntax: `.bind <key> <command>` (the leading `.` is optional), list with `.binds`, remove with `.bind clear <key>`.
3. All macros below are authored from chat with the macro CLI:
   - `.macro new <name>` — create
   - `.macro add <name> <TYPE> [key=value …]` — append a step (tab-completes `TYPE`; `key=value` sets any field)
   - `.macro show <name>` — list steps
   - `.macro removeaction <name> <index>` — delete a step
   - `.macro delete <name>` — delete the macro
   - `.macro <name>` — run it

### Universal verification (do this for EVERY test)
1. **Baseline:** note the exact count of a disposable test item, and where it is (chest / your inventory / entity).
2. Run the dupe.
3. **Rejoin / reload the chunk / hop back** (whatever severs and restores state).
4. Compare totals:
   - **Source still holds the items AND you kept copies** → grand total went up → **VULNERABLE**.
   - **Source emptied, or your copies vanished** → total unchanged → **patched / not exploitable this way**.
5. If you can read the server files: compare the container's `region/*.mca` (or sync database) against `playerdata/<uuid>.dat`. Both holding the items = dupe.

> Use a **countable, disposable** item (e.g. 64 of one block). Re-note the baseline before **every** attempt or you'll lose track.

---

## Ranked by likelihood

| # | Method | Attacks | In client code |
|---|---|---|---|
| 1 | Kick / lag race | async save ordering (inventory vs chunk) | ✅ `DisconnectAction` KICK_DUPE |
| 2 | Server-switch / transfer | cross-server inventory sync | technique (`.server` + timing) |
| 3 | Ender chest cross-server | ender-chest sync across backends | technique |
| 4 | Bundle dupe | bundle select-packet validation | ✅ `BundleDupeV2Action` |
| 5 | Container desync | window-close / container lock | ✅ `AutismGuiActions` / `DesyncAction` |
| 6 | XCarry / crafting-grid disconnect | crafting/armor slot cleanup on disconnect | ✅ `XCarryAction` |
| 7 | Entity storage (chest boat/minecart) | entity save timing | ✅ lag methods `BOAT_NBT`/`ENTITY_NBT` |
| 8 | Rollback | inventory re-sync correctness | ✅ `RollbackAction` |
| 9 | Revision-sync | container state-id validation | ✅ `RevisionSyncAction` |

On a **BungeeCord / proxy** network start with **#2 and #3** (cross-server sync is the usual weak spot); on a single backend start with **#1**.

---

## 1. Kick / lag race dupe  ⭐ highest single-server chance

**Exploits:** the server credits your inventory from an extraction, but the container/chunk write loses a race when the connection is abruptly severed under packet flood.

**Setup**
```
.macro delete lagdupe
.macro new lagdupe
.macro add lagdupe SAVE_GUI
.macro add lagdupe DESYNC
.macro add lagdupe DISCONNECT mode=KICK_DUPE useNextAction=true lagMethod=CLICK_SLOT kickMethod=INVALID_SLOT packetCount=500
.macro add lagdupe CONTAINER_CLICK_SEQUENCE slotSource=RANGE startSlot=0 endSlot=26 containerInput=QUICK_MOVE
.macro show lagdupe
.bind C .macro lagdupe
```
- `endSlot=26` = single chest; `53` = double chest; `8` = dropper/dispenser.
- `lagMethod=CLICK_SLOT` needs no entity. `BOAT_NBT`/`ENTITY_NBT` hit harder but require a chest boat/minecart (see #7).
- The Kick Dupe step runs the click-sequence as its payload (lag → extract → kick → lag), so the click step **must** come after it.

**Perform**
1. Fill a chest with the test item; note the count. Empty that item from your inventory.
2. Stand somewhere safe (you'll get kicked and relog here).
3. **Right-click the chest** to open it.
4. **Press `C`.** You get disconnected — expected.
5. Rejoin → run universal verification.

**Tuning if it doesn't dupe:** raise `packetCount` (500→1000), try `kickMethod=HURT` / `CLIENT_SETTINGS`, pull fewer slots (`endSlot=8`), or switch to `BOAT_NBT` (#7).

---

## 2. Server-switch / transfer dupe  ⭐ highest proxy chance

**Exploits:** on a proxy, inventory is saved on the source server and loaded on the destination. Changing your inventory *during* a switch can save the pre-change state on one server and the post-change state on the other.

**Setup**
- Know your switch command (`.server <name>`, or your network's hub/warp command).
- Optionally bind it: `.bind H .server hub`.

**Perform**
1. Baseline your inventory count.
2. Start an inventory change (deposit into a chest, craft, or drop a stack).
3. **Immediately trigger the server switch** during/just after the change — the tighter the timing, the better.
4. Switch back to the origin server.
5. Verify on **both** servers' view of your inventory (and the chest, if used).

**Read:** items present on both sides, or origin chest full while destination inventory kept the deposit → vulnerable sync. Consistent single copy → patched.

**Tuning:** vary the delay between the change and the switch (try same-tick, +1 tick, +½s). Try switching *while a container is still open*. Try disconnecting from the backend (not the proxy) mid-change.

---

## 3. Ender chest cross-server dupe

**Exploits:** ender chests are frequently synced across backends separately from the main inventory, with their own (weaker) timing.

**Perform**
1. Baseline the ender-chest contents.
2. Put items into the ender chest on **server A**.
3. **Switch to server B before the ender-chest sync commits** (immediately after closing / while still open).
4. Check the ender chest on both A and B.

**Read:** items on both → vulnerable. Same single copy → patched.
**Tuning:** same timing sweep as #2; also try opening the ender chest, then hopping while it's still open.

---

## 4. Bundle dupe

**Exploits:** bundle item-selection packets (`ServerboundSelectBundleItemPacket`) — rapid selection to desync bundle contents from the server.

**Setup**
```
.macro delete bundledupe
.macro new bundledupe
.macro add bundledupe BUNDLE_DUPE_V2 hotbarSlot=0 bundlePacketCount=20 maxCycles=0
.bind B .macro bundledupe
```
- `hotbarSlot=0` = which hotbar slot holds the bundle. `maxCycles=0` = loop; set a number to bound it.
- Auto variant with kick: `DISCONNECT mode=KICK_DUPE useNextAction=false` runs a bundle dupe + kick.

**Perform**
1. Put a **bundle containing items** in hotbar slot 0. Baseline its contents.
2. Press `B`.
3. Verify.

**Tuning:** adjust `bundlePacketCount` (20→50), `delayAfterPickingUpMs` / `delayAfterPuttingBackMs`, `dropDelayMs`.

---

## 5. Container desync dupe

**Exploits:** the server drops its container lock on a close packet while the client keeps the screen open and interactable, so extraction clicks aren't committed to the container on disk.

**Setup**
```
.bind X .gui save
.bind V .gui desync
.bind Z .gui close nopacket
.bind N .gui load
```
(Or `.gui` commands directly — but you need the binds because chat is blocked with the chest open.)

**Perform**
1. Baseline a chest's contents.
2. **Open the chest.**
3. **Press `X`** (save the handler).
4. **Press `V`** (desync — sends the close packet; screen stays open).
5. **Shift-click items** out of the chest into your inventory.
6. **Press `Z`** (close without a packet, so the server can't resync).
7. For another pass: reopen the chest or press `N` (load), repeat 5–6.
8. Log out, rejoin → verify.

**Read:** if the shift-clicks after `V` **snap back / get rejected / the screen resyncs**, the server closed the window on the desync packet → **patched**, no tuning helps. If clicks land but rejoin empties the chest → it's a save-timing issue (use #1 instead).

---

## 6. XCarry / crafting-grid disconnect dupe

**Exploits:** items left in the crafting grid / armor / offhand on disconnect that the server both returns/drops **and** keeps.

**Setup**
```
.macro delete xdupe
.macro new xdupe
.macro add xdupe XCARRY mode=PUT_IN useCrafting=true useArmor=false useOffhand=false carryCursor=true
.macro add xdupe DISCONNECT mode=DISCONNECT
.bind K .macro xdupe
```

**Perform (manual is the clearest)**
1. Baseline a test stack (e.g. 64). Empty that item from your main inventory otherwise.
2. Press **E** to open your inventory.
3. Drag the stack into a **2×2 crafting-grid** slot.
4. Disconnect while the items are in the grid (quit to title / Alt-F4, or press `K` to let the macro do it).
5. Rejoin. Count **inventory + anything dropped at your feet.**

**Read:** total = 64 → normal (server returned/dropped once) → **patched**. Total > 64 (in inventory *and* on the ground) → **duped**.
**Variations:** `useArmor=true` / `useOffhand=true`; a 3×3 crafting table; replace the disconnect with `DISCONNECT mode=KICK_DUPE useNextAction=false kickMethod=INVALID_SLOT` for a hard sever.

---

## 7. Entity-storage (chest boat / minecart) dupe

**Exploits:** container entities (chest boat, chest minecart, donkey/mule) save on a different schedule than player data, so a lag-kick or hop mid-load can desync them. Also gives the strongest lag method for #1.

**Setup**
```
.macro delete entdupe
.macro new entdupe
.macro add entdupe DISCONNECT mode=KICK_DUPE useNextAction=false lagMethod=ENTITY_NBT kickMethod=INVALID_SLOT packetCount=500
.bind J .macro entdupe
```
- `ENTITY_NBT` requires you to be **looking at** a loaded chest boat/minecart.
- `BOAT_NBT` requires you to be **riding** one (place it, sit in it).

**Perform**
1. Place a **chest boat or chest minecart**, load it with the test item, note the count.
2. **Look at it** (for `ENTITY_NBT`) or **sit in it** (for `BOAT_NBT`).
3. Press `J` — lag flood + kick.
4. Rejoin, break the entity, check both the entity's contents and your inventory → verify.

---

## 8. Rollback dupe

**Exploits:** forcing the client's inventory to an earlier revision so the server re-sends items.

**Setup**
```
.macro delete rbdupe
.macro new rbdupe
.macro add rbdupe ROLLBACK scope=ALL_CONTAINER startSlot=0 endSlot=26
.bind R .macro rbdupe
```
- `scope`: `ALL_CONTAINER` / `CAPTURED_SLOTS` / `SLOT_RANGE`.

**Perform**
1. Open a container, baseline it.
2. Move items around, then press `R`.
3. Verify after the server re-syncs / rejoin.

---

## 9. Revision-sync dupe

**Exploits:** the container **state-id / revision** counter — sending clicks with a stale/forged revision the server should reject.

**Setup**
```
.macro delete revdupe
.macro new revdupe
.macro add revdupe SAVE_GUI
.macro add revdupe REVISION_SYNC revisionOffset=1 preGenCount=-1
.macro add revdupe CONTAINER_CLICK_SEQUENCE slotSource=RANGE startSlot=0 endSlot=26 containerInput=QUICK_MOVE
.bind M .macro revdupe
```

**Perform**
1. Open a chest, baseline it.
2. Press `M`.
3. Verify → stale clicks accepted = vulnerable revision validation; rejected = patched.
**Tuning:** try `revisionOffset` 1–5.

---

## Timing helpers (used by several methods)

- `.packet delay on` — queue outgoing packets instead of sending. Do your clicks, then `.packet flush` to release them as one burst.
- `.packet clear` — drop queued packets.
- `.gate cancel ServerboundContainerClosePacket` — stop a stray close packet from resyncing you mid-pull; `.gate end` when done.
- `.packet status` — show send/delay flags and queue size.

---

## What to do with a positive result

If a method dupes, the fix is **server-side**, matched to the vector:
- **Desync / revision:** reject or resync container clicks whose window id / state-id no longer matches an open handler.
- **Kick/lag race:** persist container/chunk state before (or atomically with) the inventory on disconnect; rate-limit the lag packets.
- **Server-switch / ender chest:** make cross-server inventory save/load atomic; lock the player during the hop; single source of truth (one database write, not per-server files).
- **XCarry:** clear crafting/armor/offhand server-side on disconnect before returning items.
- **Bundle:** validate bundle select packets against the actual bundle contents/rate.
- **Entity storage:** flush entity NBT on the same schedule as player data on disconnect.
