# DihScript automation APIs (5.1.5)

The editor and `.scripts functions` list these APIs. Existing scripts keep their syntax.
Objects use string indexing: `response["status"]`, `settings["enabled"] = true`.
Callbacks are named script functions, for example `on("damage", "handleDamage")`.

## Inventory and containers

Inventory slots are **1–36** (1–9 hotbar), plus **"offhand"**. GUI slot indices use
the existing raw container numbering and must not be confused with inventory numbers.

| Function | Result |
|---|---|
| `inventoryItem(slot)`, `inventoryCount(slot)` | Namespaced item ID and count, without opening a GUI; empty is `minecraft:air`/0. |
| `durability(slot)`, `maxDurability(slot)` | Remaining/max uses, or -1 for an empty/non-damageable item. |
| `hasEnchantment(slot, id)` | Boolean; accepts IDs such as `minecraft:mending`. |
| `swapInventory(a, b)` | Boolean confirmed exchange, including full stacks and offhand. |
| `moveInventory(from, to, amount)` | Confirmed item count moved; incompatible or full destination moves 0. |
| `dropInventory(slot, amount)` | Confirmed count dropped from that slot; remainder stays there. |
| `cursorItem()`, `cursorCount()` | Inspect carried items after an interrupted operation. |
| `slotLore(slot)` | List of plain lore strings from a raw GUI slot. |
| `clickSlotAction(slot, action, button)` | Boolean confirmed GUI click. Actions: `pickup`, `quick_move`, `swap`, `throw`. Swap buttons 0–8 select hotbar positions; 40 selects offhand. |

Transactions use normal clicks and an authoritative server inventory resync. Busy inventory,
an occupied cursor, a closed/replaced menu, or rejected/unconfirmed clicks report failure
instead of assuming success. Offhand changes require a menu with an offhand slot: close a
chest GUI first. Recovery never drops a cursor stack; inspect the cursor if recovery fails.
Inventory mutators share ownership across scripts and callbacks. Legacy inventory mutators
participate in the same guard. Counts represent confirmed progress, which may be partial.
Menu search ranks exact display name, substring, lore, then legacy item ID. Use an explicit
raw slot when the menu has duplicate names.

## Player actions and environment

`eat()` retains hotbar food selection; `eat("offhand")` eats offhand food. `usingItem()`
observes either hand, including Meteor eating. Eating refuses a competing Meteor Auto Eat
operation. `useItem("mainhand"|"offhand")` uses a selected bucket or other item.

`yaw()`, `pitch()`, and `look(yaw, pitch, sync)` read/restore rotation; `sync=true` sends it
before the next action. `attackCooldown()` returns 0–1; `attack()` performs the ordinary
crosshair attack. A mending script should require cooldown 1, then apply its configured
small random delay. `interactBlock(x,y,z,face,hand)` validates reach and the clicked face
of that exact block. `crosshairTarget()` captures the configured station directly.

`setKey(key, pressed)` supports forward/back/left/right/jump/sneak/sprint/use/attack.
Script-owned keys are released on stop and disconnect, restoring pre-script key state.
An item-use request during a new inventory transaction asks it to stop safely and returns
false; safety handlers can retry on the next tick after cursor recovery releases ownership.
`setPauseOnLostFocus(false)` temporarily disables focus pausing and restores the original
setting when all scripts owning that override stop.

| Function | Result |
|---|---|
| `alive()`, `onGround()`, `inWater()`, `submerged()`, `inLava()`, `onFire()` | Boolean observations. |
| `air()`, `fallDistance()` | Remaining air supply and accumulated falling distance. |
| `velocity()` | Object with `x`, `y`, `z` in blocks/tick. |
| `biome()` | Namespaced biome ID. |
| `groundDistance(maxDistance)` | Vertical collision distance below feet, or null for absent/unloaded ground; fluids are separate from collision ground. |
| `nearestThreat(radius)` | Entity identity/type/position/distance plus observed `targeting` and `attacking`. Damage owners and direct projectile sources count as observed attackers during hurt time; hostility alone does not imply attacking. |
| `blockInfo(x,y,z)` | `loaded`, `id`, `fluid`, `collision`, `fullBlockSupport`, `falling`; unloaded IDs are null, distinguishable from air. |
| `crosshairTarget()` | `type` = entity/block/miss, with entity identity or block coordinates and face. |

Targeting and attacks reflect information available to the client; vanilla servers do not
send every mob's AI target. Never interpret a false observation as proof a mob is harmless.
The creeper exception and safe-home policy belong in the script's settings.

## Optional Meteor integration

`modLoaded("meteor-client")`, `moduleEnabled("meteor", "auto-eat")`, and
`setModuleEnabled("meteor", "auto-log", false)` work without linking Meteor as a required
dependency. The setter compares state before toggling and verifies the result. Missing
providers/modules or failed changes return false. Keep Auto Eat configured for offhand
steak; disable Auto Log before returning home and enable it at the mining spot.

## Storage and leases

`readText(scope,path)`, `writeTextAtomic(scope,path,text)`, `appendText(scope,path,text)`,
and `listFiles(scope,directory)` operate on UTF-8 text. `instance` uses
`config/dih/script-data`. Configure `shared` locally in `config/dih/script-services.properties`:

```properties
sharedDirectory=C:/Minecraft/shared-bots
```

The `dih.script.sharedDirectory` JVM property overrides this setting. Paths stay inside
their scope. Traversal, symlinks/junctions and internal coordination files are rejected.
Atomic replacement requires filesystem support. Existing files must be read before
replacement; stale revisions raise a conflict rather than overwriting another client.
Append merges under the same cross-process coordination lock. External programs editing
these files must also coordinate; the APIs cannot make arbitrary editors honor a lock.

`tryLock(scope,name,leaseMilliseconds)` returns an opaque unique handle, or null while
another client owns the lease. `renewLock(handle)` extends its original duration;
`releaseLock(handle)` checks ownership again. `lockOwner(scope,name)` returns null or an
object with `token`, `expiresAt`, and `leaseMilliseconds`. Renew a sword lease of 180000ms
well before expiry, and a Discord poller lease of about 20000ms. Separate Minecraft
processes coordinate through OS file locks. An expired owner's handle cannot renew or
release a replacement. A crash releases the OS lock and the lease expires normally.

Malformed local service/secret properties retain the last valid configuration. For JSON
settings, validate a parsed candidate before replacing the active settings object.

## JSON, HTTP and secrets

`jsonParse(text, fallback)`, `jsonStringify(value)`, `object()`, and `keys(object)` support mutable
objects, arrays, null, booleans, strings and numbers. Invalid JSON returns the optional
fallback or raises an error when it is omitted. Use `settings = jsonParse(text, settings)`
to keep the last valid settings after a malformed edit, then validate the candidate schema.
Keep Discord IDs quoted as strings to preserve precision.

`httpRequest(method,url,headers,body,timeoutMilliseconds)` accepts an object of string
headers and a string/null body. The last three arguments are optional (default 15000ms).
It returns an object with numeric `status`, lower-case `headers` mapping to string lists,
and string `body`, including 429 responses and rate-limit headers. Network failures and
timeouts raise an error. Redirects are not followed. HTTP runs asynchronously and yields
to monitoring callbacks while pending.

`secret(name)` reads `DIH_SECRET_NAME` or the local
`config/dih/script-secrets.properties` file (`name=value`). Keep that file out of Git and
never print tokens or put them in scripts. A Discord bot must still validate authorized
users, retain string IDs, respect 429 limits, target the correct bot name, and use shared
leases for leader polling and queue distribution. A webhook cannot receive commands.

## Events, supervision and connection control

`on(event,handlerName)` accepts `tick`, `damage`, `death`, `join`, `disconnect`, `chat`,
and `resume`. The handler is a function taking zero or one argument. Damage/death and
disconnect carry captured position/environment/nearby-entity snapshots; damage adds
`damage` and `healthAfter`. Chat carries plain text. Tick carries time in milliseconds.
Callbacks run on the same interpreter worker at cooperative checkpoints, including
waits, walking and HTTP. Different handlers can interrupt a waiting callback on that
worker; the same handler never reenters itself. Keep tick handlers short. Tick events
coalesce and queues are bounded; safety events take priority over ordinary chat.

`registerClientCommand(name,handlerName)` registers a local DIH command and passes its
argument text to the callback. Names cannot replace built-in or other scripts' commands;
registrations are removed when the owning script stops.

`supervise(true)` keeps the worker alive after `main()` returns, including while outside
a server. Use a separate supervisor and mining script: `startScript(name)`,
`stopScript(name)`, `scriptRunning(name)` let Discord stop mining without stopping its
own control loop. Stopping the supervisor itself removes its commands and callbacks.

`disconnect(reason)`, `reconnectLastServer()`, and `respawn()` perform explicit normal
client actions and return false when unavailable. A join never starts a mining script.
An explicitly armed `armAutoLogResume(true)` plus API reconnect may emit a one-shot
`resume` callback; the script decides what to resume. Death cancels the grant.
Death Replay is still a separate mod.

```javascript
function monitor(event) {
    // Inspect the environment and run the script's bucket/safety policy here.
}
function handleDeath(snapshot) {
    stopScript("miner");
    appendText("instance", "deaths.jsonl", jsonStringify(snapshot) + "\n");
}
function main() {
    on("tick", "monitor");
    on("death", "handleDeath");
    setPauseOnLostFocus(false);
    supervise(true);
    // A separate polling script can use HTTP and a renewable shared poller lease.
}
```
