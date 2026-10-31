# DIH Client modules

Every module in 5.1 beta 7g.3 and every setting it has. Generated from the source by `website/build_module_docs.py`.

## Module reference

All 105 modules, with their settings. Open the menu with Right Shift, or use `.toggle <module>` and `.setting <module> <key> <value>` with the ids below. Search finds settings too.

### Combat

#### AimAssist

Smoothly assists your aim toward configured entities.

`.toggle aim-assist`

##### Settings (23)

**General**

| Setting | Default | What it does |
|---|---|---|
| Priority `priority` | Direction | Who gets aimed at first. Choices: Direction, Type, Health, Distance, HurtTime, Age. |
| Axis `axis` | Both | Which way your view is turned. Choices: Both, Horizontal, Vertical. |
| Only On Click `only-on-click` | off | Only works while you hold the attack key. |
| Pause While Mining `pause-mining` | on | Stops while you break a block. |
| Held Items Only `held-items-only` | off | Only with listed items. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Range `range` | 4.5 | Farthest target to aim at, in blocks. 1.0 to 8.0. |
| FOV `fov` | 80 | How far off your crosshair a target may be, in degrees. 0 to 180. |
| Hurt Time `hurt-time` | 10 | Skips targets flashing from a hit for longer than this, in ticks. 0 to 10. |
| Switch Delay `switch-delay` | - | Ticks a better target must stay better before the aim moves to it. 0 to 20. |
| Through Walls `through-walls` | off | Aims at targets behind blocks too. |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities. |
| Ignore Naked `ignore-naked` | off | Skips players wearing no armor. |
| Ignore Named `ignore-named` | off | Skips mobs with a name tag. |
| Ignore Babies `ignore-babies` | off | Skips baby mobs. |
| Ignore Pets `ignore-pets` | Yours | Skips tamed animals: yours, anyone's, or none. Choices: Off, Yours, All. |
| Max Health `max-health` | 0 | Skips targets with more health than this; 0 is no limit. 0 to 100. |

**Aim**

| Setting | Default | What it does |
|---|---|---|
| Target Point `target-point` | Nearest | Where on the target to aim. Choices: Nearest, Center, Head, Body, Feet. |
| Safe Zone `safe-zone` | 5 | Deadzone around aim point. 0 to 100. |

**Smoothing**

| Setting | Default | What it does |
|---|---|---|
| Smooth `smooth-mode` | Interpolation | Interpolation eases toward the target; Instant snaps to it. Choices: Interpolation, Instant. |
| Horizontal `horizontal-speed` | 40 | How fast the view turns sideways. 1 to 100. |
| Vertical `vertical-speed` | 10 | How fast the view turns up and down. 1 to 100. |
| Direction Factor `direction-factor` | 100 | How much a target changing direction slows the turn. 0 to 100. |
| Midpoint `midpoint` | 0.35 | Where in each turn the view moves fastest. 0.0 to 1.0. |

#### AnchorAura

Blows targets up with respawn anchors.

**Speed** is **Instant** by default: it places, charges and detonates an anchor in one tick, with **Step Delay** in ticks. **Smooth** keeps human-like turning and millisecond delays. Instant flags anticheats.

`.toggle anchor-aura`

##### Settings (27)

**General**

| Setting | Default | What it does |
|---|---|---|
| Place `place` | on | Place new respawn anchors. |
| Charge `charge` | on | Charge anchors with glowstone. |
| Detonate `detonate` | on | Detonate charged anchors. |
| Pause Health `pause-health` | 0 HP | Stops while your health and absorption are at or below this; 0 never pauses. 0 to 36 HP. |
| Pause While Mining `pause-mining` | off | Stops while you break a block. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Target Range `target-range` | 8.0 | Enemy search radius. 1.0 to 16.0. |
| Targeting `targeting` | Distance | How the enemy is picked. Choices: Distance, HP, FOV. |
| Hurt Time `hurt-time` | 10 | Maximum hurt time. 0 to 10. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities. |
| Ignore Naked `ignore-naked` | off | Skip players wearing no armor. |

**Place**

| Setting | Default | What it does |
|---|---|---|
| Range `place-range` | 6.0 blocks | Farthest anchor to place, charge or detonate, capped by your reach. 1.0 to 6.0 blocks. |
| Switch Back `switch-back` | on | Return to your previous hotbar slot once idle. |
| Switch Back Delay `switch-back-delay` | 20 | Idle ticks before switching back. 1 to 100. |
| Only Above `only-above` | off | Place above target. |
| Force Engage `force-engage` | on | Engage anyway when nothing passes the damage gates. |

**Damage**

| Setting | Default | What it does |
|---|---|---|
| Min Target Damage `min-target-damage` | 5.0 | Minimum blast damage to target. 0.0 to 20.0. |
| Max Self Damage `max-self-damage` | 4.0 | Maximum blast damage to self. 0.0 to 20.0. |
| Efficient `efficient` | on | Require efficient trades. |
| Terrain `terrain` | on | Model terrain damage. |
| Glowstone Shield `glowstone-shield` | on | Shield yourself with glowstone when self damage is too high. |
| Face Place `face-place` | off | Accept weaker blasts against a target that is almost dead. |
| Face Place Health `face-place-health` | 8.0 | Target health and absorption at or below which Face Place starts. 1.0 to 36.0. |
| Face Place Damage `face-place-damage` | 2.0 | Minimum blast damage while face placing. 0.0 to 20.0. |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | Instant | Instant: snap the look and click at once; with no step delay a whole anchor goes off in one tick. Smooth: turn like a player between clicks (slower, quieter to anticheats). Choices: Instant, Smooth. |
| Step Delay `step-ticks` | 0 ticks | Ticks between two anchor clicks. 0 places, charges and detonates in the same tick. 0 to 20 ticks. |
| Step Delay `step-delay` | 75 ms | Floor between two anchor clicks. 0 to 1000 ms. |
| Step Jitter `step-jitter` | 20 ms | Random extra delay per click. 0 to 200 ms. |

#### ArrowDamage

Beta: 5.1 beta only, not in 5.0.1 stable.

Fires arrows at sniper speed so they one-shot players and mobs. Will flag anticheats.

Bow arrows fly at sniper speed and hit far harder. As you let go, the server is told you're moving fast along your aim, and the arrow takes that speed. Shots fly almost straight, so aim right at the target; Trajectories and BowAimAssist show and aim the boosted shot. Crossbows aren't affected. It flags anticheats and only shows its real effect on a server.

`.toggle arrow-damage`

##### Settings (8)

**General**

| Setting | Default | What it does |
|---|---|---|
| Boost `boost` | 19.0 | Blocks a tick added to each arrow (a full draw flies 3). Above 19 takes more packets and can rubber-band you on a laggy server. |
| Min Boost `min-boost` | - | Send the shot unboosted when the free room around you allows less than this. |
| Full Draw Only `full-draw` | on | Only boost arrows from a fully drawn bow. |
| Min Draw `min-draw` | 0 % | Only boost arrows drawn at least this far. 0 to 100 %. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Only At Targets `only-at-targets` | off | Only boost shots aimed at a target, so stray shots don't send the burst. |
| Targets `targets` | Players | What counts as a target. Choices: Players, Players + Hostile, All Living. |
| Target Angle `target-angle` | 10 deg | How far off your aim a target may be. 1 to 45 deg. |
| Target Range `target-range` | 120 blocks | How far away a target may be. 8 to 256 blocks. |

#### AutoArmor

Equips your best armor.

`.toggle auto-armor`

##### Settings (20)

**General**

| Setting | Default | What it does |
|---|---|---|
| Prefer Elytra `prefer-elytra` | off | Wear elytra over chestplate. |
| Allow Cursed `allow-cursed` | off | Equip curse-of-binding armor. |
| Expected Hit `expected-damage` | - | Hit size the armor is ranked for: bigger hits favor toughness over plain armor points. 1 to 30 HP. |
| Only In Inventory `inventory-only` | off | Only swap while your inventory screen is open. |

**Slots**

| Setting | Default | What it does |
|---|---|---|
| Helmet `helmet` | on | Manage the helmet slot. |
| Chestplate `chestplate` | on | Manage the chest slot. |
| Leggings `leggings` | on | Manage the leggings slot. |
| Boots `boots` | on | Manage the boots slot. |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Click Delay `click-delay` | - | Ticks between moves |
| Close Delay `close-delay` | - | Ticks before session close |
| Spacing `operation-delay` | - | Ticks between sessions |
| Reaction `reaction` | - | Ticks before first move |

**Constraints**

| Setting | Default | What it does |
|---|---|---|
| No Movement `no-movement` | on | Only swap while still. |
| Not Using Item `not-using-item` | on | Not while using item. |
| Pause Movement `pause-movement` | on | Hold your movement keys for a tick after each swap click. |

**Hotbar**

| Setting | Default | What it does |
|---|---|---|
| Hotbar `hotbar` | on | Equip via hotbar swap. |
| Can Swap Armor `can-swap-armor` | off | Direct armor swap. |

**Save Armor**

| Setting | Default | What it does |
|---|---|---|
| Save Armor `save-armor` | off | Swap before armor breaks. |
| Durability Threshold `durability-threshold` | 24 | Save below this durability. 0 to 100. |
| Auto Open Inventory `auto-open-inventory` | on | Open inventory to save. |

#### AutoClicker

Auto clicks or holds.

`.toggle auto-clicker`

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| CPS `tps` | - | Clicks per second, picked between these. |
| Mode `mode` | Constant | Click or hold. Choices: Constant, Hold Down, Hold. |
| Button `button` | Left | Mouse button. Choices: Left, Right. |

**Left Click**

| Setting | Default | What it does |
|---|---|---|
| Break Blocks `break-blocks` | off | Holds the button instead of clicking while you aim at a block, so it breaks. |
| Min Charge `min-charge` | 0 % | Skips clicks until the weapon cooldown is this full; 0 never waits. 0 to 100 %. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Held Items Only `held-items-only` | off | Only clicks while holding a listed item. |
| Pause While Using `pause-using` | off | Stops while you eat, drink, block or draw a bow. |

#### AutoMace

Beta: 5.1 beta only, not in 5.0.1 stable.

Swaps to your mace and smashes what you are falling onto.

**Nearby** aim smashes a target below you that's in reach and in sight, without turning your view. **Crosshair** only hits what you look at. Every drop counts from its own top, so it smashes again on the way back down after a Wind Burst launch.

`.toggle auto-mace`

##### Settings (17)

**General**

| Setting | Default | What it does |
|---|---|---|
| Aim `aim` | Nearby | Nearby: hit a target below you in reach and in sight. Crosshair: only what you look at. Choices: Nearby, Crosshair. |
| Min Fall `min-fall` | 2.0 blocks | Fall at least this far before smashing; more fall, more damage. 1.6 to 20.0 blocks. |
| Nearby Range `nearby-range` | 6.0 blocks | How far a Nearby target may be; never beyond your reach. 1.0 to 6.0 blocks. |
| Need Line Of Sight `need-sight` | on | Only pick Nearby targets you can see. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | Players | What to smash. Choices: Players, Players + Hostile, All Living. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities. |
| Ignore Naked `ignore-naked` | off | Skip players wearing no armor. |
| Skip Blocking `skip-blocking` | off | Save the smash when the target raises a shield, which stops it. |
| Skip Hurt Targets `skip-hurt` | off | Save the smash while the target still flashes from a hit and takes reduced damage. |

**Swap**

| Setting | Default | What it does |
|---|---|---|
| Swap To Mace `auto-swap` | on | Switch to a mace on your hotbar for the hit; off only smashes with a mace already in hand. |
| Prefer `prefer` | Density | Which mace to use when your hotbar holds more than one. Choices: Density, Breach, Wind Burst. |
| Swap Back `swap-back` | on | Go back to the item you held after the hit. |
| Swap Back Delay `swap-back-delay` | 2 ticks | Ticks to keep the mace out after the hit. 0 to 20 ticks. |

**Elytra**

| Setting | Default | What it does |
|---|---|---|
| Elytra Swap `elytra-swap` | off | Mid-flight, swap the elytra for a chestplate when a target is in your sights below. |
| Sight Range `elytra-range` | 32 blocks | How far ahead along your view a target counts. 8 to 96 blocks. |
| Min Height Above `elytra-drop` | 6 blocks | Only drop on targets at least this far below you. 2 to 40 blocks. |
| Re-equip Elytra `elytra-reequip` | After Hit | Put the elytra back on for the next run. Choices: Off, After Hit, On Landing. |

#### AutoTrap

Traps enemies in blocks.

`.toggle auto-trap`

##### Settings (19)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay `delay` | 20 ticks | Cooldown after a placed trap. 0 to 400 ticks. |
| Switch Back `switch-back` | on | Return to previous hotbar slot |
| Switch Back Delay `switch-back-delay` | - | Ticks to wait after the last trap before switching back. 1 to 20 ticks. |
| Wait For Hits `wait-for-hits` | on | Hold a trap while KillAura is about to swing, so the hit goes first |
| Range `range` | 6.0 blocks | Farthest trap placement, capped by your reach. 1.0 to 6.0 blocks. |
| Min Distance `min-distance` | 1.0 blocks | Never traps a target or block closer to your eyes than this. 0.0 to 3.0 blocks. |

**AutoWeb**

| Setting | Default | What it does |
|---|---|---|
| AutoWeb `web` | on | Place cobwebs; tried before Ignite |
| Web Then Ignite `web-then-ignite` | on | Right after a web, set the same target on fire |

**Ignite**

| Setting | Default | What it does |
|---|---|---|
| Ignite `ignite` | on | Place lava or set fire |
| Lava Buckets `use-lava` | on | Ignite by pouring lava |
| Flint And Steel `use-flint` | on | Ignite by lighting fire |
| Pick Up Lava `pickup-lava` | on | Scoop poured lava back up once the target burns |
| Pickup Delay `pickup-delay` | - | Longest wait for the target to catch fire before the lava comes back. 0 to 100 ticks. |

**Target**

| Setting | Default | What it does |
|---|---|---|
| Predict Landing `predict` | on | Trap the predicted landing spot |
| FOV `fov` | 180 degrees | Cone width around crosshair. 0 to 180 degrees. |
| Hurt Time `hurt-time` | 10 ticks | Maximum target hurt time. 0 to 10 ticks. |
| Priority `priority` | Type | Main target sort key. Choices: Type, Health, Distance. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities |
| Ignore Sleeping `ignore-sleeping` | on | Skip players in bed |

#### BlockHit

Beta: 5.1 beta only, not in 5.0.1 stable.

Blocks with your shield between hits.

`.toggle block-hit`

##### Settings (11)

**General**

| Setting | Default | What it does |
|---|---|---|
| Unblock At `unblock-at` | 0.85 | Lower the shield once the swing is this charged, ready for the next hit. 0.3 to 1.0. |
| Block Hand `block-hand` | Any | Which hand's blocking item to raise: Any tries the main hand, then the offhand. Choices: Any, Offhand, Main Hand. |
| Only After Hits `after-hits` | on | Block only in the fight you started, for a while after each hit. |
| After Hit Time `after-hit-time` | 30 ticks | How long after your last hit blocking stays allowed. 5 to 200 ticks. |
| Only On Ground `only-on-ground` | off | Don't block in the air, where a raised shield costs you speed. |

**Enemies**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | Players | What counts as an enemy worth blocking. Choices: Players, Players + Hostile, All Living. |
| Range `range` | 4.5 blocks | Only block with an enemy this close. 2.0 to 8.0 blocks. |
| FOV `fov` | 360 deg | Only enemies inside this cone in front of you; 360 counts everyone around. 30 to 360 deg. |
| Only When Targeted `only-targeted` | off | Only block an enemy that is looking at you. |
| Targeted Angle `targeted-angle` | 30 deg | How far off you an enemy's look may point and still count as aiming at you. 5 to 90 deg. |
| Ignore Invisible `ignore-invisible` | off | Don't block for invisible enemies. |

#### BowAimAssist

Beta: 5.1 beta only, not in 5.0.1 stable.

Aims bows, crossbows and tridents for you.

`.toggle bow-aim-assist`

##### Settings (17)

**General**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | Players | What to aim at. Choices: Players, Players + Hostile, All Living. |
| Speed `speed` | 40 % | How fast the view turns to the aim. 1 to 100 %. |
| Axis `axis` | Both | Which way the view is turned: Horizontal leaves your pitch to you. Choices: Both, Horizontal, Vertical. |
| Predict Movement `predict` | on | Lead moving targets by the flight time. |

**Aim**

| Setting | Default | What it does |
|---|---|---|
| Aim At `aim-at` | Body | Which part of the target the shot is aimed at. Choices: Head, Body, Feet. |
| Aim Offset `aim-offset` | 0.0 | Moves the aim up or down, as a share of the target's height. -0.5 to 0.5. |

**Weapons**

| Setting | Default | What it does |
|---|---|---|
| Bows `bows` | on | Aim while drawing a bow. |
| Min Draw `min-draw` | 0 % | Start aiming once the bow is drawn this far. 0 to 100 %. |
| Crossbows `crossbows` | on | Aim while holding a loaded crossbow. |
| Tridents `tridents` | on | Aim while readying a trident. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| FOV `fov` | 60 deg | Only targets this close to your crosshair. 5 to 180 deg. |
| Range `range` | 80 blocks | Farthest target to aim at. 8 to 160 blocks. |
| Priority `priority` | Crosshair | Which target wins when several are in the cone. Choices: Crosshair, Distance, Health. |
| Lock Target `lock-target` | off | Stay on the same target while it stays in range and in the cone. |
| Need Line Of Sight `need-sight` | off | Skip targets you can't see. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities. |
| Ignore Naked `ignore-naked` | off | Skip players wearing no armor. |

#### Criticals

Beta: 5.1 beta only, not in 5.0.1 stable.

Turns hits on the ground into critical hits. Packet mode will flag anticheats.

**Packet** crits every hit at once but anticheats flag it. **Jump** really jumps and hits on the way down, which is plain movement but slower.

`.toggle criticals`

##### Settings (6)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Packet | Packet: every hit crits at once (anticheats flag it). Jump: really jump and hit on the way down. Choices: Packet, Jump. |
| Max Hold `max-hold` | - | Let a held hit go after this long even if the jump never comes down. 4 to 20 ticks. |
| Only Full Hits `only-full-hits` | off | Leave weak hits alone: the server only crits hits charged past 90%. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | All Living | Which targets get critical hits. Choices: Players, Players + Hostile, All Living. |
| Skip Hurt Targets `skip-hurt` | off | Leave hits on a target still flashing from a hit alone; it only takes damage above the last hit. |
| Held Items Only `held-items-only` | off | Only while holding a listed item. |

#### CrystalAura

Places and detonates end crystals.

**Speed** is **Instant** by default: it looks at its spot and clicks in the same tick, with **Place Delay** and **Break Delay** in ticks. **Smooth** keeps human-like turning and millisecond delays. Instant flags anticheats.

`.toggle crystal-aura`

##### Settings (38)

**General**

| Setting | Default | What it does |
|---|---|---|
| Place `place` | on | Place on obsidian and bedrock. |
| Destroy `destroy` | on | Attack crystals in range. |
| Place Obsidian `obsidian` | on | Build a support when none exists. |
| Auto Switch `auto-switch` | on | Switch to crystals and obsidian on your hotbar; off only uses what you hold. |
| Pause Health `pause-health` | 0 HP | Stops while your health and absorption are at or below this; 0 never pauses. 0 to 36 HP. |
| Pause While Mining `pause-mining` | off | Stops while you break a block. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Target Range `target-range` | 8.0 | Enemy search radius. 1.0 to 16.0. |
| Targeting `targeting` | Distance | How the enemy is picked. Choices: Distance, HP, FOV. |
| Hurt Time `hurt-time` | 10 | Maximum hurt time. 0 to 10. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities. |
| Ignore Naked `ignore-naked` | off | Skip players wearing no armor. |

**Place**

| Setting | Default | What it does |
|---|---|---|
| Place Range `place-range` | 6.0 blocks | Farthest crystal or obsidian placement, capped by your reach. 1.0 to 6.0 blocks. |
| Switch Back `switch-back` | on | Return to your previous hotbar slot once idle. |
| Switch Back Delay `switch-back-delay` | 20 | Idle ticks before switching back. 1 to 100. |
| Only Above `only-above` | off | Place above target. |
| Break Range `break-range` | 6.0 blocks | Farthest crystal to attack, capped by your reach. 1.0 to 6.0 blocks. |

**Damage**

| Setting | Default | What it does |
|---|---|---|
| Min Target Damage `min-target-damage` | 5.0 | Minimum blast damage to target. 0.0 to 20.0. |
| Max Self Damage `max-self-damage` | 4.0 | Maximum blast damage to self. 0.0 to 20.0. |
| Efficient `efficient` | on | Require efficient trades. |
| Terrain `terrain` | on | Model terrain damage. |
| Face Place `face-place` | off | Accept weaker blasts against a target that is almost dead. |
| Face Place Health `face-place-health` | 8.0 | Target health and absorption at or below which Face Place starts. 1.0 to 36.0. |
| Face Place Damage `face-place-damage` | 2.0 | Minimum blast damage while face placing. 0.0 to 20.0. |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | Instant | Instant: snap the look and click the same tick. Smooth: turn like a player first (slower, quieter to anticheats). Choices: Instant, Smooth. |
| Place Delay `place-ticks` | 0 ticks | Ticks between places. 0 to 20 ticks. |
| Break Delay `break-ticks` | 0 ticks | Ticks between breaks. 0 to 20 ticks. |
| Place Delay `place-delay` | 75 ms | Floor between places. 0 to 1000 ms. |
| Destroy Delay `destroy-delay` | 75 ms | Floor between breaks. 0 to 1000 ms. |
| Delay Jitter `jitter` | 20 ms | Random extra delay. 0 to 200 ms. |
| Spam `spam` | on | Run at the tick floor in a prime position. |
| Spam Delay `spam-delay` | 0 ms | Floor between actions while spamming. 0 to 200 ms. |
| Vulnerable Spam `vulnerable-spam` | on | Prime fast with the enemy trapped above; gates cadence and anchor priority. |

**Defend**

| Setting | Default | What it does |
|---|---|---|
| Defend `defend` | on | Block incoming crystals with obsidian. |

**CrystalView**

| Setting | Default | What it does |
|---|---|---|
| CrystalView `crystal-view` | off | Shrink and slow crystal models. |
| Size `view-size` | 0.3 | Crystal model scale. 0.1 to 1.5. |
| Y Translate `view-y` | -0.5 | Vertical offset in blocks. -2.0 to 2.0. |
| Spin Speed `view-spin` | 0.0 | Rotation speed. 0 freezes it. 0.0 to 5.0. |
| Bounce `view-bounce` | 0.25 | Bob height. Negative inverts it. -1.0 to 1.0. |

#### Danger

Beta: 5.1 beta only, not in 5.0.1 stable.

Warns about incoming projectiles and can hit fireballs back.

Warns about arrows, fireballs and thrown items that will hit you: time to impact and direction under the crosshair, a ping, and the path drawn in the world. **Deflect Fireballs** is off by default; with **Return To Sender** on, it turns to the fireball so it flies back. Deflecting turns your camera and can flag anticheats.

`.toggle danger`

##### Settings (27)

**General**

| Setting | Default | What it does |
|---|---|---|
| Range `range` | 32 | How far away to watch projectiles. 4 to 96. |
| Look Ahead `look-ahead` | 40 | Ticks of flight to predict. 5 to 120. |
| Hit Margin `margin` | 0.5 | Extra space around you that counts as a hit. 0.0 to 3.0. |
| Ignore Friends `ignore-friends` | off | Skip projectiles fired by friends and teammates. |

**Projectiles**

| Setting | Default | What it does |
|---|---|---|
| Arrows `arrows` | on | Arrows and tridents. |
| Fireballs `fireballs` | on | Fireballs, wither skulls and wind charges. |
| Thrown `thrown` | on | Snowballs, eggs, potions and pearls. |

**Alerts**

| Setting | Default | What it does |
|---|---|---|
| HUD Warning `hud` | on | Show incoming projectiles under the crosshair. |
| HUD Lines `hud-lines` | - | Most warnings listed at once, nearest first. 1 to 8. |
| HUD Offset `hud-offset` | - | Pixels below the crosshair the list starts; negative puts it above. -120 to 120. |
| Show Time `hud-time` | on | Seconds until the projectile reaches you. |
| Show Direction `hud-direction` | on | Arrow pointing to where the projectile is, relative to your view. |
| Urgent Time `urgent-time` | - | Flash the warning red when the hit is this close; 0 never flashes. 0 to 40 ticks. |
| Sound `sound` | on | Ping once when a new projectile is coming at you. |
| Sound Volume `sound-volume` | 25 % | How loud the ping is. 5 to 100 %. |
| Sound Pitch `sound-pitch` | 160 % | How high the ping is. 50 to 200 %. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Highlight `highlight` | on | Draw incoming projectiles and their predicted path. |
| Show Path `show-path` | on | Draw the predicted flight path, not just the projectile. |
| Line Width `line-width` | - | Thickness of the outline and path. 1.0 to 5.0. |
| Fill Opacity `fill-opacity` | - | How solid the box around the projectile is; 0 draws only the outline. 0 to 100 %. |

**Deflect**

| Setting | Default | What it does |
|---|---|---|
| Deflect Fireballs `deflect` | off | Hit incoming fireballs that come within reach. |
| Deflect Range `deflect-range` | 3.0 | How close a fireball must be (never beyond your reach). 1.0 to 6.0. |
| Return To Sender `rotate` | on | Look at the fireball before hitting it so it flies back the way it came. |
| Deflect Cooldown `deflect-cooldown` | - | Wait between deflecting hits. 0 to 20 ticks. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Arrow `color-arrow` | colour | Arrows and tridents. |
| Fireball `color-fireball` | colour | Fireballs and other hurting projectiles. |
| Thrown `color-thrown` | colour | Thrown items. |

#### KillAura

Automatically attacks configured entities.

It leaves your own tamed pets alone by default (**Ignore Pets: Yours**). Set that to Off for the old behaviour. **PerfectHit** times hits for when damage immunity ends.

`.toggle kill-aura`

##### Settings (34)

**General**

| Setting | Default | What it does |
|---|---|---|
| Attack Mode `attack-mode` | Real Input | How attacks are sent. Choices: Real Input, Packet. |
| Targeting `targeting` | FOV | Who gets hit first. Choices: Type, HP, Distance, FOV, Hurt Time, Age. |
| FOV `fov` | 180 | Attack cone in degrees. 10 to 360. |
| Miss Injection `miss-injection` | off | Adds human-like misses |
| Miss Chance `miss-chance` | 6 | Miss rate percent. 1 to 20. |
| Criticals `criticals` | off | Smart critical hits |
| Held Items Only `held-items-only` | off | Only with listed items |
| Auto Sword `auto-sword` | on | Switches to your best weapon before hitting |
| Shield Break `shield-break` | on | Switches to an axe when the target blocks |
| PerfectHit `perfect-hit` | off | Times attacks around damage immunity. |
| PerfectHit Lead `perfect-hit-lead` | 1 | Ticks before immunity ends to swing, to cover ping. 0 to 5. |
| Switch Back `switch-back` | on | Goes back to your old slot after the fight |
| Throwables `throwables` | MainHand | Hands checked for throwables. Choices: MainHand, BothHands. |
| Render `hit-marker` | on | Flashes the target's box when a hit lands |
| Hitsound `hitsound` | on | Plays a sound when a hit lands |
| Hitsound Volume `hitsound-volume` | 70 | Hitsound loudness in percent. 0 to 100. |
| Hitsound Pitch `hitsound-pitch` | 100 | Hitsound pitch in percent. 50 to 200. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities |
| Ignore Naked `ignore-naked` | off | Skips players wearing no armor |
| Ignore Named `ignore-named` | off | Skips mobs with a name tag |
| Ignore Babies `ignore-babies` | off | Skips baby mobs |
| Ignore Pets `ignore-pets` | Yours | Skips tamed animals: yours, anyone's, or none. Choices: Off, Yours, All. |
| Ignore Sleeping `ignore-sleeping` | on | Skips players in bed |
| Max Health `max-health` | 0 | Skips targets with more health than this; 0 is no limit. 0 to 100. |

**Range**

| Setting | Default | What it does |
|---|---|---|
| Range `range` | 6.0 | Farthest hit, capped by your reach; raise reach past 3 with the Reach module. 1.0 to 6.0. |
| Scan Range `scan-range` | - | How far past the range it starts turning toward a target |
| Through Walls `walls-range` | - | Hits through blocks within this distance; 0 needs a clear line. 0.0 to 6.0. |

**Aim**

| Setting | Default | What it does |
|---|---|---|
| Aim Height `aim-height` | Auto | Where on the target to aim. Choices: Auto, Head, Chest, Legs. |
| Aim Offset `aim-offset` | 0.0 | Moves the aim up or down, as a share of the target's height. -0.5 to 0.5. |
| Turn Speed `turn-speed` | - | Most degrees it turns per tick between attacks. 10 to 180. |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| CPS `cps` | - | Clicks per second, picked anew each second between these |
| Min Charge `min-charge` | 100 | Attacks once the weapon cooldown is this full, in percent. 50 to 100. |
| Only On Click `only-on-click` | off | Only works while you hold the attack key |
| Pause In GUI `pause-in-gui` | off | Stops while a screen such as your inventory is open |

#### MaceKill

Beta: 5.1 beta only, not in 5.0.1 stable.

Fakes a fall before each mace hit so it lands as a smash. Will flag anticheats.

Turns every mace hit into a smash by telling the server you fell first, up to 20 blocks and less under a roof. A smash the server turns down leaves you that fall, so by default it skips targets still flashing from a hit. It flags anticheats.

`.toggle mace-kill`

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| Fall Height `fall-height` | 22 | Most blocks of fall to fake; under a roof the lift stops below it. |
| Min Height `min-height` | - | Swing normally when a roof leaves less room than this: a short smash adds little. |
| Spam Packets `spam-packets` | 3 | Rotation packets sent first to raise the server's movement allowance. At 4, a normal move already sent that tick makes six and the server turns the lift down. |
| Only On Ground `only-on-ground` | off | Only fake the fall for hits made standing on the ground. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | All Living | Which targets get smash hits; others get a normal swing. Choices: Players, Players + Hostile, All Living. |
| Skip Hurt Targets `skip-hurt` | on | Swing normally at a target still recovering from a hit: a refused smash would leave you the fall. |
| Skip Blocking Targets `skip-blocking` | off | Swing normally at a target raising a shield, which stops the smash. |

#### Reach

Beta: 5.1 beta only, not in 5.0.1 stable.

Hit entities and reach blocks from further away. Will flag anticheats.

Raises entity reach up to 6 and block reach up to 5.5. A vanilla server allows a little over the normal range; anticheats check tighter.

`.toggle reach`

##### Settings (6)

**General**

| Setting | Default | What it does |
|---|---|---|
| Entity Reach `entity-reach` | on | Raise how far you can hit. |
| Block Reach `block-reach` | on | Raise how far you can break and place. |

**Entities**

| Setting | Default | What it does |
|---|---|---|
| Entity Range `entity-range` | 3.5 | How far you can hit, in blocks (vanilla is 3). Vanilla servers allow up to 6. 3.0 to 6.0. |
| Held Items Only `held-items-only` | off | Only reach further while holding a listed item. |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Block Range `block-range` | 4.5 | How far you can break and place, in blocks (vanilla is 4.5). Vanilla servers allow up to 5.5. 4.5 to 5.5. |
| Pause While Sneaking `pause-sneaking` | off | Use the normal block range while you sneak, for careful building. |

#### SpearKill

Beta: 5.1 beta only, not in 5.0.1 stable.

Makes a charging spear hit at full speed: Poke from where you stand, or Rush the target. Will flag anticheats.

**Poke**, the default, gives huge charge hits while you stand still or walk: each tick a target is in the spear's reach, the server is told you just charged at it. **Rush** flies you at the target. Both flag anticheats.

`.toggle spear-kill`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Poke | Poke: huge hits at your normal speed. Rush: really fly at the target. Choices: Poke, Rush. |
| Face Target `face` | on | Turn toward the target: the charge only hits what it points at. |

**Poke**

| Setting | Default | What it does |
|---|---|---|
| Boost `boost` | 9.5 | Blocks a tick the server thinks you charge at. Above 9.5 takes more packets and can miss on a laggy server. 1.0 to 22.0. |

**Rush**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | 3.0 | Blocks a tick to rush at the target. 0.5 to 9.5. |
| Stop At `stop-at` | 1.5 | Blocks short of the target to stop, inside the spear's reach. 0.5 to 3.0. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | All Living | What the charge may be aimed at. Choices: Players, Players + Hostile, All Living. |
| Range `range` | 12.0 | How far away a target can be picked. 2.0 to 32.0. |
| FOV `fov` | 90 | How far off the crosshair a target can be picked. 10 to 360. |
| Priority `priority` | Crosshair | Which target wins when several are in the cone. Choices: Crosshair, Distance, Health. |
| Crosshair First `crosshair-first` | on | Take the entity under your crosshair before anything else. |
| Lock Target `lock-target` | on | Keep charging the same target while it stays valid. |
| Need Line Of Sight `need-sight` | off | Skip targets behind blocks. |
| Ignore Invisible `ignore-invisible` | off | Skip invisible entities. |
| Ignore Sleeping `ignore-sleeping` | on | Skip players in bed. |

#### STap

Beta: 5.1 beta only, not in 5.0.1 stable.

Taps S after each hit to reset sprint and keep your distance.

`.toggle s-tap`

##### Settings (12)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay `delay` | 0 ticks | Wait after the hit before tapping. 0 to 5 ticks. |
| Hold `hold` | - | How long the tap lasts. 1 to 10 ticks. |
| Chance `chance` | 100 % | Share of hits that get a tap. 1 to 100 %. |
| Tap Cooldown `tap-cooldown` | 0 ticks | Ticks after a tap before another can start; 0 allows one per hit. 0 to 20 ticks. |
| Release W `release-forward` | off | Also let go of W during the tap, so you back off instead of stopping in place. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only Sprinting `only-sprinting` | on | Only after hits made while sprinting. |
| Only Full Hits `only-full-hits` | off | Only after hits charged past 90%: weaker hits keep your sprint anyway. |
| Only Players `only-players` | off | Only after hitting a player. |
| Only On Ground `only-on-ground` | off | Skip hits made in the air. |
| Only Moving Forward `only-moving` | off | Only while you hold the forward key. |
| Skip In Liquid `skip-liquid` | on | Skip hits made in water or lava, where sprint works differently. |
| Held Items Only `held-items-only` | off | Only while holding a listed item. |

#### TriggerBot

Attacks configured entities when your crosshair is on them.

`.toggle trigger-bot`

##### Settings (15)

**General**

| Setting | Default | What it does |
|---|---|---|
| Timing `timing` | Cooldown | Cooldown: hit when the swing is charged. CPS: click at a set rate, like AutoClicker. Choices: Cooldown, CPS. |
| Respect Cooldown `respect-cooldown` | on | Wait for full cooldown |
| Min Charge `min-charge` | 100 % | Hits once the weapon cooldown is this full. 50 to 100 %. |
| Randomize Delay `randomize-delay` | off | Small delay after cooldown. |
| CPS `cps` | - | Click rate; it drifts and hesitates within this band like AutoClicker. |
| Wait For Crits `wait-for-crit` | off | In the air, holds the hit until you fall, so it lands as a critical. |
| Pause While Using `pause-using` | on | Stops while you eat, drink, block or draw a bow. |
| Held Items Only `held-items-only` | off | Only with listed items. |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Hurt Time `hurt-time` | 10 | Skips targets flashing from a hit for longer than this, in ticks. 0 to 10. |
| Max Health `max-health` | 0 | Skips targets with more health than this; 0 is no limit. 0 to 100. |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities. |
| Ignore Naked `ignore-naked` | off | Skips players wearing no armor. |
| Ignore Named `ignore-named` | off | Skips mobs with a name tag. |
| Ignore Babies `ignore-babies` | off | Skips baby mobs. |
| Ignore Pets `ignore-pets` | Yours | Skips tamed animals: yours, anyone's, or none. Choices: Off, Yours, All. |

#### WTap

Beta: 5.1 beta only, not in 5.0.1 stable.

Lets go of W after each hit so every hit gets sprint knockback.

`.toggle w-tap`

##### Settings (12)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay `delay` | 0 ticks | Wait after the hit before tapping. 0 to 5 ticks. |
| Hold `hold` | - | How long the tap lasts. 1 to 10 ticks. |
| Chance `chance` | 100 % | Share of hits that get a tap. 1 to 100 %. |
| Tap Cooldown `tap-cooldown` | 0 ticks | Ticks after a tap before another can start; 0 allows one per hit. 0 to 20 ticks. |
| Mode `mode` | Legit | Legit: really release W. Packet: resend sprint before the hit (anticheats can notice). Choices: Legit, Packet. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only Sprinting `only-sprinting` | on | Only after hits made while sprinting. |
| Only Full Hits `only-full-hits` | off | Only after hits charged past 90%: weaker hits keep your sprint anyway. |
| Only Players `only-players` | off | Only after hitting a player. |
| Only On Ground `only-on-ground` | off | Skip hits made in the air. |
| Only Moving Forward `only-moving` | off | Only while you hold the forward key. |
| Skip In Liquid `skip-liquid` | on | Skip hits made in water or lava, where sprint works differently. |
| Held Items Only `held-items-only` | off | Only while holding a listed item. |

### Movement

#### AirJump

Jump in mid air.

`.toggle air-jump`

##### Settings (4)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | JumpFreely | JumpFreely: jump any time. DoubleJump: a set number per fall. GhostBlock: stand on air while holding jump. Choices: JumpFreely, DoubleJump, GhostBlock. |
| Air Jumps `air-jumps` | 1 | Jumps you get in the air before you have to land again. 1 to 10. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only Falling `only-falling` | off | Only jumps once you are on the way down |
| Pause Sneaking `pause-sneaking` | off | No air jumps while you sneak |

#### BoatFly

Lets ridden vehicles fly.

`.toggle boat-fly`

##### Settings (6)

**General**

| Setting | Default | What it does |
|---|---|---|
| Lock Yaw `lock-yaw` | off | Points the vehicle where you look |

**Horizontal**

| Setting | Default | What it does |
|---|---|---|
| Change Forward Speed `change-forward-speed` | off | Use custom forward speed. |
| Forward Speed `forward-speed` | 1.0 | Sets forward speed. 0.05 to 5.0. |

**Vertical**

| Setting | Default | What it does |
|---|---|---|
| Upward Speed `upward-speed` | 0.3 | Rise speed while you hold jump, in blocks per tick. 0.0 to 5.0. |
| Down Speed `down-speed` | 0.0 | Sink speed while you hold sprint; 0 lets the vehicle fall normally. 0.0 to 5.0. |
| Fall Speed `fall-speed` | 0.0 | Slow sink with no keys held; 0 hovers in place. 0.0 to 1.0. |

#### Clutch

Beta: 5.1 beta only, not in 5.0.1 stable.

Places blocks under you when you fall toward the void.

Places blocks back under you when you're knocked toward the void, using any full block in your hotbar. **Clutch With: Water** MLGs a water bucket; **Auto** uses water when it will catch you and blocks otherwise, and blocks in the Nether, where water would vanish. It turns your camera and can flag anticheats.

`.toggle clutch`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | - | Void: only when nothing is below. Big Falls: also when the ground is too far down. |
| Fall Height `fall-height` | 8 | Clutch when no ground is within this many blocks below. 3 to 64. |
| Clutch With `clutch-with` | - | Blocks: place blocks. Water: MLG a water bucket onto the ground below. Auto: water when it will catch you, blocks otherwise (and in the Nether). |

**Water**

| Setting | Default | What it does |
|---|---|---|
| Pick Up Water `water-pickup` | on | Scoop the water back into the bucket once you have landed. |

**Trigger**

| Setting | Default | What it does |
|---|---|---|
| Air Window `air-window` | 20 | Only clutch within this many ticks of leaving the ground. 2 to 100. |
| Only When Hit `only-hit` | off | Only clutch falls that start with you taking a hit (knockback). |
| Max Blocks `max-blocks` | 4 | Most blocks placed in one fall. 1 to 16. |
| Trigger Speed `trigger-speed` | - | Waits until you fall faster than this, in blocks per tick. 0.0 to 1.0. |

**Placing**

| Setting | Default | What it does |
|---|---|---|
| Rotate `rotate` | on | Turn to look at the face being clicked. |
| Switch Back `switch-back` | on | Return to the held slot after landing. |
| Prefer Held `prefer-held` | on | Uses the block in your hand before switching to a bigger stack |
| Search Radius `search-radius` | 2 blocks | How far sideways it looks for a cell to fill. 1 to 3 blocks. |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Filter `filter-mode` | Off | Limits which blocks it may use. Choices: Off, Whitelist, Blacklist. |

#### EntityControl

Controls selected rideable entities.

`.toggle entity-control`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Spoof Saddle `spoof-saddle` | on | Control without saddles. |
| Max Jump `max-jump` | on | Always charge fully. |
| Lock Yaw `lock-yaw` | on | Match your view. |
| Cancel Server Packets `cancel-server-packets` | on | Ignore server corrections. |

**Speed**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | off | Boost mount speed. |
| Horizontal Speed `horizontal-speed` | 10.0 | Sets horizontal speed. 0.0 to 100.0. |
| Only Ground `only-on-ground` | off | Require ground contact. |
| In Water `in-water` | on | Allow water speed. |

**Flight**

| Setting | Default | What it does |
|---|---|---|
| Fly `fly` | off | Enable mount flight. |
| Vertical Speed `vertical-speed` | 6.0 | Sets vertical speed. 0.0 to 100.0. |
| Fall Speed `fall-speed` | 0.0 | Sets downward drift. 0.0 to 100.0. |
| Anti Kick `anti-kick` | on | Reduce flight kicks. |
| Anti Kick Delay `anti-kick-delay` | 40 | Sets anti-kick interval. 1 to 80. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Disable On Dismount `disable-on-dismount` | off | Turns EntityControl off when you get off a controlled mount |

#### Flight

Client-side flight.

`.toggle flight`

##### Settings (16)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Abilities | Flight method. Choices: Abilities, Velocity, Vulcan. |
| Speed `speed` | 0.10 | Your speed when flying. 0.0 to 2.0. |
| Sprint Boost `sprint-boost` | 1.5 | Speed multiplier while sprinting. 1.0 to 3.0. |
| Vertical Speed Match `vertical-speed-match` | off | Rise and sink twice as fast, matching horizontal speed |
| Glide Speed `glide-speed` | 0.0 | Sinks this many blocks per tick when you press neither jump nor sneak. 0.0 to 1.0. |
| No Sneak `no-sneak` | off | Ignore sneak input. |
| Stop Instantly `stop-instantly` | off | Stops dead when you let go of the keys instead of drifting |
| AntiHunger `anti-hunger` | off | Hide sprint from server. |
| Stop On Disable `stop-on-disable` | off | Clears your momentum when Flight turns off |

**Vulcan**

| Setting | Default | What it does |
|---|---|---|
| Anti Break Elytra `anti-break-elytra` | on | Preserve low-durability elytra. |
| Min Durability `min-durability` | 40 | Swaps out an elytra with this little durability left (or under 10%). 1 to 200. |

**Anti Kick**

| Setting | Default | What it does |
|---|---|---|
| Anti Kick `anti-kick-mode` | Packet | Reduce floating kicks. Choices: Normal, Packet, None. |
| Delay `delay` | 20 | Ticks between anti-kick nudges. 1 to 200. |
| Off Time `off-time` | 1 | Ticks spent nudging down. 1 to 20. |
| Nudge Distance `nudge` | 0.0313 | How far each anti-kick packet drops you, in blocks. 0.01 to 0.2. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Disable On Setback `disable-on-setback` | off | Turns Flight off when the server teleports you back |

#### InvMove

Allows movement keys in normal container screens.

`.toggle inv-move`

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| Arrow Keys Look `arrow-look` | off | Turns your view with the arrow keys while a screen is open |
| Look Speed `look-speed` | 5 deg | Degrees turned per tick by the arrow keys. 1 to 20 deg. |

**Keys**

| Setting | Default | What it does |
|---|---|---|
| Jump `jump` | on | Lets the jump key work in screens |
| Sneak `sneak` | on | Lets the sneak key work in screens |
| Sprint `sprint` | on | Lets the sprint key work in screens |

**Screens**

| Setting | Default | What it does |
|---|---|---|
| Player Inventory `player-inventory` | on | Works in your own and the creative inventory |
| Containers `containers` | on | Works in chests, furnaces and other block screens |

#### NoClip

Move through blocks.

`.toggle no-clip`

##### Settings (5)

**General**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | 0.32 | Movement speed. 0.1 to 0.4. |
| Vertical Speed `vertical-speed` | 0.0 | Up and down speed; 0 uses Speed. 0.0 to 0.4. |
| Sprint Boost `sprint-boost` | 1.0 | Speed multiplier while you hold sprint. 1.0 to 3.0. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only In Vehicle `only-in-vehicle` | off | Only while riding. |
| Disable On Setback `disable-on-setback` | on | Disable on server setback. |

#### NoFall

Reduces fall damage.

`.toggle no-fall`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Packet | Fall protection method. Choices: Packet, AirPlace, Place. |
| Anti Bounce `anti-bounce` | on | Stop bounce effects. |
| Pause On Mace `pause-on-mace` | on | Pause holding mace |

**Packet**

| Setting | Default | What it does |
|---|---|---|
| Trigger Speed `trigger-speed` | 0.5 | Claims ground once you fall faster than this, in blocks per tick. 0.1 to 2.0. |
| With Flight `with-flight` | on | Always claims ground while Flight is on |

**Place**

| Setting | Default | What it does |
|---|---|---|
| Placed Item `placed-item` | Bucket | What it puts down to break the fall; Bucket uses powder snow in the Nether. Choices: Bucket, PowderSnow, HayBale, Cobweb, SlimeBlock. |
| Air Place Mode `air-place-mode` | BeforeDeath | Place before any damage, or only before a fall that would kill. Choices: BeforeDamage, BeforeDeath. |
| Place Height `place-height` | 3.0 | Places once you have fallen this many blocks. 2.0 to 20.0. |
| Reach Down `reach-down` | 5 blocks | How far below you it looks for ground to place on. 2 to 6 blocks. |
| Skip Above Water `skip-water` | on | Doesn't place when you will land in water anyway |
| Pick Up `pick-up` | on | Scoops the water or powder snow back into the bucket after landing |
| Anchor `anchor` | on | Slow before placing. |
| Client Rotation `client-rotate` | off | Also rotate camera client-side. |

#### Parkour

Jumps at block edges.

`.toggle parkour`

##### Settings (5)

**General**

| Setting | Default | What it does |
|---|---|---|
| Min Drop `min-drop` | 1 blocks | Only jumps off ledges at least this deep, so it walks down steps. 1 to 6 blocks. |
| Jump Early `lead-ticks` | 1 ticks | How many ticks ahead it looks for the edge. 1 to 3 ticks. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only Sprinting `only-sprinting` | off | Only jumps while you sprint |
| Only Forward `only-forward` | off | Only jumps while you hold the forward key |
| Min Speed `min-speed` | 0.0 b/s | Skips the jump when you move slower than this. 0.0 to 8.0 b/s. |

#### PearlCharge

Beta: 5.1 beta only, not in 5.0.1 stable.

Throws an ender pearl and a wind charge together to launch you. Bind a key to it.

Throws an ender pearl and pops a wind charge together to launch you, straight up or toward where you look. Bind a key and tap to fire. It needs an ender pearl and a wind charge on your hotbar.

`.toggle pearl-charge`

##### Settings (5)

| Setting | Default | What it does |
|---|---|---|
| Order `order` | - | Throw the pearl first, or pop the wind charge first. |
| Delay `delay` | 1 ticks | Ticks between the two throws; 0 fires them on the same tick. 0 to 10 ticks. |
| Launch Pitch `launch-pitch` | 90 ° | How far down the wind charge is aimed; 90 pops it at your feet for a straight-up launch. 0 to 90 °. |
| Pearl Where Looking `pearl-where-looking` | on | Throw the pearl where you are looking; off throws it straight up for height. |
| Switch Back `switch-back` | on | Return to the slot you were holding after launching. |

#### Phase

Phase through blocks.

`.toggle phase`

##### Settings (2)

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Clip | Phase method. Choices: Clip, Intave, Blink, Spider. |
| Maximum `maximum` | 120 ticks | Blink tick budget. 1 to 300 ticks. |

#### SafeWalk

Sneaks at ledges.

`.toggle safe-walk`

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| Look Down `look-down` | 25 deg | Minimum downward look. 0 to 90 deg. |
| Edge Distance `edge-distance` | 0.30 blocks | Ledge probe distance |
| Min Drop `min-drop` | 1 blocks | Only stops you at drops at least this deep, so you can walk down steps. 1 to 6 blocks. |
| Hold Time `hold-time` | - | Random sneak hold time |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Pause When Jumping `pause-when-jumping` | off | Lets go while you hold jump, so you can leap off the edge |
| Only Backwards `only-backwards` | off | Only while you walk backwards, as when bridging |
| Only With Blocks `only-with-blocks` | off | Only while you hold a block in either hand |

#### Scaffold

Places blocks beneath you.

`.toggle scaffold`

##### Settings (15)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Legit | Choose scaffold mode. Choices: Legit, Rage, Telly. |
| Remove Limits `remove-limits` | off |  |
| Switch Back `switch-back` | - |  |
| Blocks Ahead `rage-blocks` | - | Blocks built ahead. |
| Show Block Count `show-count` | off | Adds how many usable blocks you have to the module list |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Auto Switch `auto-switch` | on | Switches to a block in your hotbar; off only places what you hold |
| Block Pick `block-pick` | Smart | Smart prefers solid, normal blocks; or take the biggest stack or the leftmost slot. Choices: Smart, Biggest Stack, First Slot. |
| Filter `filter-mode` | Off | Choose filter mode. Choices: Off, Whitelist, Blacklist. |
| Low Block Warning `low-blocks` | 0 | Warns you once your usable blocks drop to this many; 0 is off. 0 to 64. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Disable Out Of Blocks `disable-no-blocks` | off | Turns Scaffold off when you have no usable blocks left |
| Disable On Setback `disable-on-setback` | off | Turns Scaffold off when the server teleports you back |
| Disable On Death `disable-on-death` | off | Turns Scaffold off when you die |

**Animation**

| Setting | Default | What it does |
|---|---|---|
| Place Animation `place-animation` | on | Placement ripple effect |
| Custom Color `animation-custom` | off | Custom ripple color |
| Color `animation-color` | colour | Ripple color |

#### Sneak

Keeps sneak held for you.

`.toggle sneak`

##### Settings (5)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Vanilla | Which method to sneak. Choices: Vanilla, Packet. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Pause In Air `pause-in-air` | off | Lets go of sneak while you are off the ground |
| Pause In Liquids `pause-in-liquids` | off | Lets go of sneak in water and lava so you don't sink |
| Pause On Ladders `pause-on-climbable` | off | Lets go of sneak on ladders, vines and scaffolding |
| Pause When Sprinting `pause-when-sprinting` | off | Lets go of sneak while you hold the sprint key |

#### Speed

Boosts movement speed.

`.toggle speed`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Vanilla | Speed method. Choices: Vanilla, Strafe. |
| Timer `timer` | 1.0 | Client timer speed. 0.01 to 10.0. |

**Vanilla**

| Setting | Default | What it does |
|---|---|---|
| Vanilla Speed `vanilla-speed` | 5.6 | Blocks per second. 0.0 to 20.0. |
| Speed Effect `speed-effect` | on | Adds the Speed potion bonus on top |
| Auto Jump `auto-jump` | off | Hops whenever you walk on the ground |

**Strafe**

| Setting | Default | What it does |
|---|---|---|
| Strafe Speed `strafe-speed` | 1.6 | Strafe speed multiplier. 0.0 to 3.0. |
| Hop Height `hop-height` | 0.40123128 | Upward speed of each strafe hop; vanilla jumps are 0.42. 0.2 to 0.6. |
| Speed Limit `speed-limit` | off | Limit max speed. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| In Liquids `in-liquids` | off | Keeps the boost in water and lava |
| When Sneaking `when-sneaking` | off | Keeps the boost while sneaking |
| Only Ground `only-on-ground` | off | Only boosts while your feet are on the ground |
| Pause When Hurt `pause-when-hurt` | off | Lets knockback play out instead of overwriting it |
| Pause While Using `pause-while-using` | off | No boost while you eat, drink, block or draw a bow |
| Disable On Setback `disable-on-setback` | off | Turns Speed off when the server teleports you back |

#### Sprint

Sprints automatically.

`.toggle sprint`

##### Settings (6)

**General**

| Setting | Default | What it does |
|---|---|---|
| Omnidirectional `omnidirectional` | off | Sprints in every direction, not only forward |
| Jump Toward Movement `omni-jump` | off | Sprint-jumps push you the way you walk, not the way you look |
| Pause In Liquids `pause-in-liquids` | off | Leaves sprinting to you while in water or lava |

**Ignore**

| Setting | Default | What it does |
|---|---|---|
| Blindness `ignore-blindness` | off | Sprint while blind |
| Collision `ignore-collision` | off | Sprint against walls |
| Item Use `ignore-item-use` | off | Keeps sprinting while you eat, drink or block |

### Player

#### AirPlace

The AirPlace module.

`.toggle air-place`

##### Settings (9)

**General**

| Setting | Default | What it does |
|---|---|---|
| Range `range` | 5.0 | 1.0 to 6.0. |
| Hand `hand` | Auto | Choices: Auto, Main Hand, Off Hand. |
| Place Delay `place-delay` | - | 0 to 20 ticks. |
| Repeat `repeat` | on |  |
| Sneak Only `sneak-only` | off |  |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Block Filter `block-filter` | Off | Choices: Off, Whitelist, Blacklist. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Guide `guide` | on |  |
| Fill `fill` | on |  |
| Guide Color `guide-color` | colour |  |

#### AntiAFK

Beta: 5.1 beta only, not in 5.0.1 stable.

Moves now and then so you are not kicked for idling.

`.toggle anti-afk`

##### Settings (18)

**General**

| Setting | Default | What it does |
|---|---|---|
| Idle Time `idle-time` | 30 s | Start only after this long without input; 0 acts all the time. 0 to 600 s. |
| Interval `interval` | - | Time between actions. |
| Mode `mode` | Random | Do one random action each time, or every action you turned on. Choices: Random, All. |
| Pause In Screens `pause-in-screens` | on | Stops while a screen such as chat or your inventory is open |

**Actions**

| Setting | Default | What it does |
|---|---|---|
| Rotate `rotate` | on | Turns your view a little |
| Swing `swing` | on | Swings your arm |
| Jump `jump` | off | Jumps once |
| Walk `walk` | off | Step a little in a random direction. |
| Sneak `sneak` | off | Crouches for a moment |
| Hotbar `hotbar` | off | Switch to a random hotbar slot. |
| Chat `chat` | off | Sends the message below; a leading / sends it as a command |
| Spin `spin` | off | Keeps turning in a circle the whole time you are idle |

**Tuning**

| Setting | Default | What it does |
|---|---|---|
| Turn Angle `turn-angle` | - | Largest left or right turn. 5 to 180 °. |
| Pitch Change `pitch-change` | - | Largest up or down look change. 0 to 45 °. |
| Walk Time `walk-time` | - | How long each step lasts |
| Sneak Time `sneak-time` | - | How long each crouch lasts |
| Message `message` | - | Chat line or /command to send |
| Spin Speed `spin-speed` | 5 °/tick | How fast Spin turns. 1 to 45 °/tick. |

#### AutoFarm

Harvests and replants crops.

`.toggle auto-farm`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Movement `movement` | off | Walk to distant crops |
| Search Range `search-range` | 16 | Pathfinding search radius. 4 to 16. |
| Reach `range` | 6.0 blocks | Farthest block it works on, capped by your reach. 1.0 to 6.0 blocks. |
| Pause While Sneaking `pause-sneaking` | off | Hands control back to you while you sneak |

**Actions**

| Setting | Default | What it does |
|---|---|---|
| Fully Grown Only `grown-only` | on | Harvest mature crops only |
| Replant `replant` | on | Replant after harvesting |
| Plant `plant` | on | Plant bare farmland |
| Bonemeal `bonemeal` | on | Fertilize crops to grow |
| Bonemeal Reserve `bonemeal-reserve` | 0 | Keeps this much bone meal and stops fertilizing below it. 0 to 64. |
| Till Dirt `till` | off | Hoe dirt into farmland |

**Tools**

| Setting | Default | What it does |
|---|---|---|
| Prefer Fortune `fortune` | on | Harvest with fortune tool |
| Switch Back `switch-back` | on | Return to previous slot |
| Switch Back Delay `switch-back-delay` | - | Idle ticks before returning to the previous slot. 0 to 40 ticks. |

#### AutoFish

Automatically reels and recasts fishing rods.

`.toggle auto-fish`

##### Settings (24)

**General**

| Setting | Default | What it does |
|---|---|---|
| Trigger `trigger-mode` | Sound | Reel in on the splash sound, or when the bobber dips. Choices: Sound, Bobber. |
| Sound Distance `sound-distance` | on | Require sound near player. |
| Max Distance `max-sound-distance` | 12.0 | Max player sound range. 1.0 to 128.0. |
| Click Mode `click-mode` | Real Input | Press the use key, or call the game's item use directly. Choices: Real Input, Interaction API. |
| Show Catches `show-catches` | off | Shows how many fish you caught next to the module name |
| Stop After `stop-after` | 0 | Turns off after this many catches; 0 never stops. 0 to 1000. |

**Casting**

| Setting | Default | What it does |
|---|---|---|
| Auto Cast `auto-cast` | on | Recast when missing |
| No Bite Retry `cast-timeout` | 60 s | 0 = wait forever. 0 to 300 s. |

**Delays**

| Setting | Default | What it does |
|---|---|---|
| Randomize Delays `randomize-delays` | on | Picks each delay between a min and a max |
| Catch Min `catch-min` | 2 ticks | Shortest wait before reeling in. 0 to 32 ticks. |
| Catch Max `catch-max` | 5 ticks | Longest wait before reeling in. 0 to 40 ticks. |
| Recast Min `recast-min` | 8 ticks | Shortest wait before casting again. 0 to 32 ticks. |
| Recast Max `recast-max` | 12 ticks | Longest wait before casting again. 0 to 40 ticks. |
| Catch Delay `catch-delay` | 3 ticks | Wait before reeling in. 0 to 40 ticks. |
| Recast Delay `recast-delay` | 10 ticks | Wait before casting again. 0 to 40 ticks. |

**Movement**

| Setting | Default | What it does |
|---|---|---|
| Steps `movement-steps` | 6 | Turns one step after each catch, sweeping up to this many steps each way; 0 stays still. 0 to 6. |
| Degrees / Step `degrees-per-step` | 5.0 | How far each turn goes. 0.1 to 20.0. |
| Duration `movement-duration` | 6 ticks | How long each turn takes. 1 to 30 ticks. |

**Rod Safety**

| Setting | Default | What it does |
|---|---|---|
| Anti Break `anti-break` | on | Stops before your rod breaks |
| Min Durability `min-durability` | 2 | Durability a rod must keep. 1 to 32. |
| Auto Select Best Rod `auto-select-rod` | on | Switches to your best usable rod |

**Auto Stop**

| Setting | Default | What it does |
|---|---|---|
| Auto Stop `stop-macro-enabled` | off | Silent conditional rule |
| Rule `stop-macro` | - |  |
| On Trigger `stop-macro-trigger` | Stop AutoFish | Action on condition. Choices: Stop AutoFish, Run Steps Only, Stop, Run, Resume. |

#### AutoLogin

Answers server login and register gates.

`.toggle auto-login`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Password `password` | - | Sent to login and register gates. |
| Only On Servers `servers` | - | Comma-separated server addresses to log in on; empty means every server |

**Prompts**

| Setting | Default | What it does |
|---|---|---|
| Chat Prompts `chat-prompts` | on | Answers /login and /register requests in chat |
| Answer Register `answer-register` | on | Also registers new accounts, not only logs in |
| Menu Prompts `menu-prompts` | on | Fills in and submits login dialog screens |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Chat Delay `chat-delay` | 2.0 s | Wait after spawning before sending. 0.0 to 10.0 s. |
| Window `window` | 10.0 s | Stop watching this long after joining. 3.0 to 60.0 s. |
| Resend Delay `resend-delay` | 2.5 s | Wait before trying again when the server asks a second time. 0.5 to 10.0 s. |
| Max Attempts `max-attempts` | 4 | Gives up after sending the password this many times. 1 to 10. |
| Menu Retry `menu-retry` | 1.0 s | Wait before retrying a login dialog that could not be submitted. 0.1 to 5.0 s. |
| Login After Register `register-follow-up` | 3.0 s | Sends /login this long after registering if the server stays quiet. 0.5 to 10.0 s. |

**Feedback**

| Setting | Default | What it does |
|---|---|---|
| Notify Success `notify-success` | off | Says in chat once the login went through |
| Warn On Failure `warn-failure` | on | Says in chat when it gives up or needs a password |

#### AutoRespawn

Beta: 5.1 beta only, not in 5.0.1 stable.

Respawns as soon as you die.

`.toggle auto-respawn`

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay `delay` | 0 ticks | Wait this long on the death screen. 0 to 100 ticks. |
| Skip Hardcore `skip-hardcore` | off | Leaves the death screen alone in hardcore worlds |
| Auto Disable `auto-disable` | off | Turns itself off after one respawn |

**After Respawn**

| Setting | Default | What it does |
|---|---|---|
| Print Coords `print-coords` | off | Says in chat where you died |
| Restore Slot `restore-slot` | off | Selects the hotbar slot you held when you died |
| Command `command` | - | Sent after respawning, e.g. /kit. Leave empty for none. |
| Command Delay `command-delay` | 20 ticks | Waits this long after respawning before sending the command. 0 to 200 ticks. |

#### AutoSign

Fills signs with configured text.

`.toggle auto-sign`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Line 1 `line-1` | - | Text for sign line one. |
| Line 2 `line-2` | - | Text for sign line two. |
| Line 3 `line-3` | - | Text for sign line three. |
| Line 4 `line-4` | - | Text for sign line four. |
| Placeholders `placeholders` | off | Replaces {player}, {date}, {time}, {x}, {y} and {z} in the lines |

**Signs**

| Setting | Default | What it does |
|---|---|---|
| Also When Editing `edit-existing` | off | Replace text when editing signs. |
| Blank Lines `blank-lines` | Clear | What an empty line above does to text already on the sign. Choices: Clear, Keep. |
| Sides `sides` | Both | Which side of the sign gets filled. Choices: Both, Front, Back. |
| Hanging Signs `hanging-signs` | on | Also fills hanging signs |
| Only While Sneaking `only-sneaking` | off | Fills only when you place or open the sign while sneaking |

**Finish**

| Setting | Default | What it does |
|---|---|---|
| Auto Done `auto-done` | on | Close editor after filling text. |
| Done Delay `done-delay` | 0 ticks | Waits this long before closing the editor. 0 to 40 ticks. |
| Notify `notify` | off | Says in chat when a sign was filled |

#### AutoTool

Switches to the best tool to mine a block.

`.toggle auto-tool`

##### Settings (9)

**General**

| Setting | Default | What it does |
|---|---|---|
| Switch Delay `switch-delay-ms` | 12 ms | Delay before switching. 0 to 1000 ms. |
| Require Sneaking `require-sneaking` | off | Only switches while you sneak |
| Off In Creative `off-in-creative` | off | Leaves your slot alone in creative, where every block breaks at once |

**Tools**

| Setting | Default | What it does |
|---|---|---|
| Consider Inventory `consider-inventory` | off | Pulls a better tool up from your main inventory |
| Ignore Durability `ignore-durability` | on | Also uses tools that are about to break |
| Prefer Silk Touch `prefer-silk-touch` | off | Picks a Silk Touch tool when one works on the block |
| Skip Instant Blocks `skip-instant` | off | Does not switch for blocks that break at once, like grass and torches |

**Switch Back**

| Setting | Default | What it does |
|---|---|---|
| Switch Back `switch-back` | off | Returns to your old slot when you stop mining |
| Restore Delay `switch-back-delay-ms` | 100 ms | Delay before restoring. 0 to 1000 ms. |

#### AutoTotem

Keeps a totem in offhand.

`.toggle auto-totem`

##### Settings (28)

**General**

| Setting | Default | What it does |
|---|---|---|
| Switch Mode `switch-mode` | Switch | How the totem is swapped. Choices: Smart, Switch, PickUp. |
| Switch Delay (ms) `switch-delay` | 0 | Delay between offhand swaps. 0 to 500. |
| Switch Back Delay (ms) `switch-back-delay` | 40 | Delay before switching back. 0 to 500. |
| Send Directly `send-directly` | off | Swap even with screens open. |
| Pause Movement `pause-movement` | on | Holds your movement keys for the tick a swap is sent |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Click Delay `click-delay` | - | Ticks between packets |
| Close Delay `close-delay` | - | Ticks before session close |
| Spacing `operation-delay` | - | Ticks between operations |
| Reaction `reaction` | - | Reaction ticks after pop |
| Settle `settle` | - | Ticks before first packet |

**Health**

| Setting | Default | What it does |
|---|---|---|
| Health `health` | on | Totem only when endangered. |
| Health Threshold `health-threshold` | 14 | Totem below this health. 0 to 20. |
| Safety `safety` | on | Stricter threshold inside holes. |
| Safe Health Threshold `safe-health-threshold` | 10 | Threshold while in holes. 0 to 20. |
| Subtract Calculated Damage `subtract-calculated-damage` | off | Subtract predicted damage too. |
| Predict Explosion Damage `predict-explosions` | on | Predict entity explosion damage. |
| Crystals `predict-crystals` | on | Counts end crystals as explosions |
| TNT `predict-tnt` | on | Counts lit TNT and TNT minecarts as explosions |
| Creepers `predict-creepers` | on | Counts creepers as explosions |
| Predict Block Explosions `predict-block-explosions` | off | Predict bed and anchor explosions. |
| Predict Fall Damage `predict-fall-damage` | on | Predict incoming fall damage. |
| Ignore Elytra `ignore-elytra` | off | Ignore fall damage while gliding. |
| Missing Armor `missing-armor` | on | Totem when armor missing. |
| While Gliding `while-gliding` | off | Always holds a totem while flying with an elytra |
| Switch Back `switch-back` | on | Restore item after healing. |

**Feedback**

| Setting | Default | What it does |
|---|---|---|
| No Render `no-render` | on | Hide the totem pop animation. |
| Notify Pops `notify-pops` | off | Says in chat when one of your totems pops and how many are left |
| Low Totem Warning `low-totems` | 0 | Warns in chat when you have this many totems or fewer; 0 is off. 0 to 10. |

#### BedDefender

Walls in a bed with your hardest blocks.

`.toggle bed-defender`

##### Settings (15)

**Bed**

| Setting | Default | What it does |
|---|---|---|
| Max Layers `max-layers` | 1 | Shell depth around the bed. 1 to 5. |
| Prefer Sides `prefer-sides` | on | Sides before the top |
| Defend Under `defend-under` | on | Also fill under the bed |
| Top Riser `top-riser` | on | Riser to reach the top |
| Require Sneak `require-sneak` | off | Only while holding shift |
| Only On Ground `only-on-ground` | off | Waits to place while you are jumping or falling |
| Disable When Done `disable-when-done` | off | Turns off once a bed is fully walled in |
| Rescan Interval `rescan-interval` | 10 ticks | Ticks between full bed sweeps. 1 to 40 ticks. |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Filter `filter-mode` | Whitelist | How the list is applied. Choices: Whitelist, Blacklist. |
| Allow Chests `allow-chests` | off | Also allow chests as material |

**Placing**

| Setting | Default | What it does |
|---|---|---|
| Sneak For Bed `sneak-for-bed` | on | Crouch to place on bed |
| Switch Back `switch-back` | on | Return to your old slot |
| Switch Back Delay `switch-back-delay` | - | Idle ticks before returning to your old slot. 0 to 40 ticks. |
| Turn Speed `turn-speed` | - | Most degrees it turns per tick toward the next face. 10 to 180 °/tick. |
| Trace Faces `trace-faces` | off | Show why faces were refused |

#### FastBreak

Speeds up block breaking.

`.toggle fast-break`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Damage | Break method. Choices: Normal, Haste, Damage, Packet. |
| No Break Delay `no-break-delay` | off | Skips the short wait between blocks; Packet mode always does |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Blocks Filter `blocks-filter` | Blacklist | List mode. Choices: Whitelist, Blacklist. |

**Normal**

| Setting | Default | What it does |
|---|---|---|
| Modifier `modifier` | 1.4 | Speed multiplier. 0.0 to 999.0. |
| No Air Penalty `no-air-penalty` | off | Mines as fast in the air as on the ground |
| No Water Penalty `no-water-penalty` | off | Mines as fast underwater as on land |
| No Mining Fatigue `no-fatigue` | off | Cancels the slowdown from Mining Fatigue |

**Haste**

| Setting | Default | What it does |
|---|---|---|
| Haste Amplifier `haste-amplifier` | 2 | Haste amplifier to apply. 1 to 10. |

**Damage**

| Setting | Default | What it does |
|---|---|---|
| Instamine `instamine` | on | Finish blocks early. |
| Finish At `finish-progress` | 0.7 | Breaks the block once mining is this far along. 0.5 to 1.0. |
| Instant Threshold `instant-threshold` | 0.5 | Breaks on the first click when one tick of mining does at least this much. 0.3 to 1.0. |
| Grim Bypass `grim-bypass` | off | Abort after stop. |

**Packet**

| Setting | Default | What it does |
|---|---|---|
| Activation Chance `activation-chance` | 100 % | % to fast-break. 0 to 100 %. |

#### FastUse

Speeds up item use.

`.toggle fast-use`

##### Settings (7)

**EXP**

| Setting | Default | What it does |
|---|---|---|
| Fast EXP `exp` | on | Speed experience bottles. |
| EXP CPS `exp-cps` | - | Throws per second while you hold use |
| EXP Pitch `exp-pitch` | 90 ° | How far down it aims the bottles; 90 is straight at your feet. 45 to 90 °. |
| Only When Damaged `exp-only-damaged` | off | Only speeds bottles up while something you wear or hold is damaged |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Fast Blocks `blocks` | on | Speed block use. |
| Blocks CPS `blocks-cps` | - | Placements per second while you hold use |
| Block Filter `block-filter` | Off | Limit which blocks get placed faster. Choices: Off, Whitelist, Blacklist. |

#### Flee

Disconnects, runs a command or stops Baritone when a trigger you turned on fires.

`.toggle flee`

##### Settings (17)

**When**

| Setting | Default | What it does |
|---|---|---|
| Check `when` | - | Baritone Busy: only while Baritone is mining, pathing or farming. |

**Triggers**

| Setting | Default | What it does |
|---|---|---|
| Player Nearby `player-nearby` | off | Another player comes within range. |
| Range `player-radius` | 32.0 blocks | 4.0 to 128.0 blocks. |
| Ignore Friends `ignore-friends` | on | Friends and teammates (Teams module) don't count. |
| Low Health `low-health` | off | Health drops to the threshold. |
| Hearts `health-hearts` | 5.0 hearts | 0.5 to 10.0 hearts. |
| Low Hunger `low-hunger` | off | Food drops to the threshold (20 = full). |
| Food `hunger-level` | 6 | 0 to 19. |
| Tool Worn `tool-worn` | off | The tool in your hand is about to break. |
| Durability Left `tool-durability` | 10 | 1 to 250. |
| No Pickaxe `no-pickaxe` | off | Baritone is mining and you have no pickaxe left. |
| Inventory Full `inventory-full` | off | No empty slot left. |

**Action**

| Setting | Default | What it does |
|---|---|---|
| Action `action` | - |  |
| Command `command` | /home | /server command, #baritone command or .dih command. |
| Stop Baritone `stop-baritone` | on | Cancel whatever Baritone is doing first. |
| Stay Disconnected `block-reconnect` | on | Skip AutoReconnect after fleeing. |
| Cooldown `cooldown` | 10.0 s | Wait this long before fleeing again. 0.0 to 120.0 s. |

#### GhostBlock

The GhostBlock module.

`.toggle ghostblock`

##### Settings (15)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Creative | Choices: Creative, Survival. |
| Place Delay `place-delay` | 4 ticks | 1 to 20 ticks. |
| Sneak Bypass `sneak-bypass` | on |  |
| Use Interactables `respect-interactables` | on |  |
| Local Pick Block `pick-block` | on |  |

**Ghosts**

| Setting | Default | What it does |
|---|---|---|
| Max Ghosts `max-ghosts` | - |  |
| Hold Against Server `hold-against-server` | on |  |
| Click Removes `break-ghosts` | on |  |
| Break Effects `break-effects` | on |  |
| Clear On Disable `clear-on-disable` | off |  |
| Clear Key `clear-key` | unbound |  |
| Clear Message `clear-message` | on |  |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Highlight `highlight` | on |  |
| Highlight Fill `highlight-fill` | on |  |
| Highlight Color `highlight-color` | colour |  |

#### InstantRebreak

Rebreaks the last block.

`.toggle instant-rebreak`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay `delay` | 0 ticks | Delay between rebreak packets. 0 to 20 ticks. |
| Rotate `rotate` | off | Face target first. |
| Max Rebreaks `max-rebreaks` | 0 | Forgets the block after breaking it this many times; 0 never stops. 0 to 100. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Only Pickaxe `only-pick` | on | Require pickaxe. |
| Same Block Only `same-block` | off | Only rebreaks when the same kind of block comes back |
| Range Check `range-check` | off | Waits while the block is farther than the range |
| Range `range` | 6.0 blocks | Farthest block it sends rebreaks for. 1.0 to 10.0 blocks. |
| Pause While Using `pause-while-using` | off | Waits while you eat, drink or block |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Render `render` | on | Show the rebreak target. |
| Color `color` | colour | Single outline color. |

#### InventoryTweaks

Inventory sorting, protection, auto-drop, and container steal/dump tools.

`.toggle inventory-tweaks`

##### Settings (26)

**General**

| Setting | Default | What it does |
|---|---|---|
| Shift Drag Move `mouse-drag-item-move` | on | Shift-drag transfers hovered stacks. |
| Uncap Bundle Scroll `uncap-bundle-scrolling` | on | Allow unrestricted bundle scrolling. |
| Shulker Preview `shulker-preview` | on | Preview container on hover |

**Sorting**

| Setting | Default | What it does |
|---|---|---|
| Sorting `sorting-enabled` | on | Enable inventory sorting. |
| Sort Key `sorting-key` | unbound | Sort key |
| Sort Delay `sorting-delay` | 1 | Ticks between sorting moves. 0 to 20. |
| Disable In Creative `disable-in-creative` | on | Skip creative screens |

**Anti Drop**

| Setting | Default | What it does |
|---|---|---|
| Frames / Pots `item-frames` | on | Block frame/pot placing |
| Override Key `override-bind` | unbound | Hold to bypass anti-drop. |

**Auto Drop**

| Setting | Default | What it does |
|---|---|---|
| Exclude Equipped `exclude-equipped` | on | Keep armor/offhand items. |
| Exclude Hotbar `exclude-hotbar` | off | Keep hotbar stacks |
| Only Full Stacks `only-full-stacks` | off | Only drop complete stacks. |
| Drop Delay `drop-delay` | 0 ticks | Wait between two auto drops. 0 to 40 ticks. |

**Steal / Dump**

| Setting | Default | What it does |
|---|---|---|
| Steal Screens `steal-screens` | - | Menu ids |
| Inventory Buttons `inventory-buttons` | on | Show steal/dump buttons |
| Steal Drop `steal-drop` | off | Drop instead of move |
| Drop Backwards `drop-backwards` | off | Turn while dropping |
| Dump Filter `dump-filter` | None | Filter dumped player items. Choices: None, Whitelist, Blacklist. |
| Steal Filter `steal-filter` | None | Filter stolen container items. Choices: None, Whitelist, Blacklist. |
| Stop When Full `stop-when-full` | off | Stops stealing once your inventory has no empty slot |

**Auto Steal**

| Setting | Default | What it does |
|---|---|---|
| Auto Steal `auto-steal` | off | Automatically steal supported containers. |
| Auto Dump `auto-dump` | off | Auto-dump supported containers. |
| Delay `delay-ms` | 0 | Move delay (ms). 0 to 1000. |
| Initial Delay `initial-delay-ms` | 0 | First-move delay (ms). 0 to 1000. |
| Random `random-ms` | 0 | Random extra delay. 0 to 1000. |
| Auto Close `auto-close` | off | Closes the container once an automatic steal or dump is done |

#### NoInteract

Cancels selected inputs.

`.toggle no-interact`

##### Settings (12)

**General**

| Setting | Default | What it does |
|---|---|---|
| Sneak Bypass `sneak-bypass` | off | Holding sneak lets every input through |
| Pause In Creative `pause-in-creative` | off | Lets every input through in creative mode |
| Notify `notify` | off | Says in chat when it cancels something, at most once a second |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Mine Mode `block-mine-mode` | BlackList | BlackList stops mining the listed blocks; WhiteList allows only them. Choices: WhiteList, BlackList. |
| Block Mode `block-interact-mode` | BlackList | BlackList stops using the listed blocks; WhiteList allows only them. Choices: WhiteList, BlackList. |
| Block Hand `block-interact-hand` | Both | Which hand the block-use rule applies to. Choices: Mainhand, Offhand, Both, None. |

**Entities**

| Setting | Default | What it does |
|---|---|---|
| Entity Mode `entity-interact-mode` | BlackList | BlackList stops using the listed entities; WhiteList allows only them. Choices: WhiteList, BlackList. |
| Entity Hand `entity-interact-hand` | Both | Which hand the entity-use rule applies to. Choices: Mainhand, Offhand, Both, None. |
| Friends `friends` | off | Also applies the entity rules to friends and teammates |
| Babies `babies` | off | Also applies the entity rules to baby mobs |
| Nametagged `nametagged` | off | Also applies the entity rules to mobs with a name tag |

**Attacks**

| Setting | Default | What it does |
|---|---|---|
| Hit Mode `entity-hit-mode` | WhiteList | WhiteList lets you hit only the listed entities; BlackList stops hitting them. Choices: WhiteList, BlackList. |

#### Nuker

Beta: 5.1 beta only, not in 5.0.1 stable.

Breaks blocks around you.

`.toggle nuker`

##### Settings (19)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Legit | Legit: one block at a time at normal speed. Instant: every block that breaks in one tick. Choices: Legit, Instant. |
| Range `range` | 4.5 blocks | Farthest block it breaks. 1.0 to 6.0 blocks. |
| Blocks Per Tick `per-tick` | 8 | Most blocks Instant breaks in one tick. 1 to 64. |
| Rotate `rotate` | off | Face each block for the server. |

**Area**

| Setting | Default | What it does |
|---|---|---|
| Area `area` | Sphere | Sphere: all around you. Flatten: only at or above your feet. Choices: Sphere, Flatten. |
| Shape `shape` | Sphere | Round reach, or a full box out to the range. Choices: Sphere, Cube. |
| Up Limit `up-limit` | 6 blocks | How far above your feet it reaches. 0 to 6 blocks. |
| Down Limit `down-limit` | 6 blocks | How far below your feet it reaches. 0 to 6 blocks. |
| Keep Floor `keep-floor` | on | Leaves the block you stand on until nothing else is left |

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Sort `sort` | Nearest | Which block it breaks first. Choices: Nearest, Farthest, Top Down, Softest. |
| Through Walls `through-walls` | on | Breaks blocks you cannot see; off needs a clear line to the block |
| Avoid Liquids `avoid-liquids` | off | Skips blocks next to water or lava so nothing floods |
| Only With Drops `only-drops` | off | Skips blocks your held tool would not get drops from |
| Filter `filter` | Off | Limit which blocks it breaks. Choices: Off, Whitelist, Blacklist. |

**Conditions**

| Setting | Default | What it does |
|---|---|---|
| Pause In Containers `pause-in-containers` | on | Stops while a chest or other container is open |
| Only On Ground `only-on-ground` | off | Waits while you are jumping or falling |
| Pause While Using `pause-while-using` | off | Waits while you eat, drink or block |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Render `render` | on | Outlines the blocks being broken |
| Color `color` | colour | Outline color |

#### OffhandInteract

Uses the offhand too.

`.toggle offhand-interact-placement`

##### Settings (5)

**General**

| Setting | Default | What it does |
|---|---|---|
| Cancel Mainhand `cancelMainhand` | off | Only the offhand acts; the main-hand click is not sent |
| Main Hand `main-hand` | Any | What the main hand may hold for the offhand to join in. Choices: Any, No Blocks, Empty. |
| Sneak Only `sneak-only` | off | Only while you sneak |
| Skip Interactables `skip-interactables` | off | Leaves chests, doors, buttons and levers to the main hand so they do not toggle twice |

**Items**

| Setting | Default | What it does |
|---|---|---|
| Offhand Items `offhand-items` | Any | Which offhand items get used. Choices: Any, Blocks Only, List. |

#### PacketMine

Beta: 5.1 beta only, not in 5.0.1 stable.

Click a block to mine it in the background.

`.toggle packet-mine`

##### Settings (16)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Normal | Normal: STOP when the block is done. Immediate: START and STOP at once, for servers that only check the time between them. Choices: Normal, Immediate. |
| Range `range` | 4.5 blocks | Mines only while the block is this close. 1.0 to 6.0 blocks. |
| Keep Range `keep-range` | 8.0 blocks | Drop the target past this distance. 1.0 to 64.0 blocks. |
| Rotate `rotate` | off | Face the block for the START and STOP packets. |

**Tool**

| Setting | Default | What it does |
|---|---|---|
| Switch `switch` | On Stop | When to hold the best tool: just for the STOP packet, the whole time, or never. Choices: On Stop, Always, Never. |
| Save Tools `save-tools` | on | Skips tools with 2 or less durability left |
| Silk Touch `silk-touch` | off | Only switches to Silk Touch tools |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Break Damage `break-damage` | 1.0 | Progress at which STOP is sent; vanilla servers accept from 0.7. 0.5 to 1.0. |
| Post Break Delay `post-break-delay` | 6 ticks | Wait before starting the next block (vanilla waits 5). 0 to 20 ticks. |
| Retry `retry` | on | Mines the block again if the server did not break it; off drops it |
| Confirm Time `confirm-ticks` | - | How long to wait for the server to remove a finished block. 2 to 40 ticks. |
| Pause While Using `pause-while-using` | on | Holds the STOP while you eat or drink so the tool swap does not cancel it |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Render `render` | on | Shows the block being mined |
| Grow Box `grow-box` | on | The filled box grows as the block breaks; off fills it fully |
| Color `color` | colour | Color while mining |
| Done Color `done-color` | colour | Color once the block is ready to break |

#### SmartEat

Beta: 5.1 beta only, not in 5.0.1 stable.

Eats when hungry and uses golden apples when hurt.

`.toggle smart-eat`

##### Settings (15)

**General**

| Setting | Default | What it does |
|---|---|---|
| Min Hunger `min-hunger` | 15 | Eat below this hunger (20 is full). 0 to 20. |
| Prefer `prefer` | Most Filling | Among plain foods, the most filling one or the one closest to your slot. Choices: Most Filling, Nearest Slot. |
| Switch Back `switch-back` | on | Return to the slot you had. |
| Save Golden Apples `save-golden-apples` | on | Keeps golden apples for low health instead of eating them as food |
| Avoid Bad Food `avoid-bad-food` | on | Never eats rotten flesh, spider eyes, raw chicken, chorus fruit and the like |

**Healing**

| Setting | Default | What it does |
|---|---|---|
| Golden Apples `golden-apples` | on | Eats golden apples when health is low |
| Golden Apple Health `gapple-health` | 9.0 | Eat golden apples at or below this health (20 is full). 0.0 to 20.0. |
| Notch Apples `notch-apples` | on | Eats enchanted golden apples when health is very low |
| Notch Apple Health `notch-health` | 4.0 | Eat enchanted golden apples at or below this health. 0.0 to 20.0. |
| Healing Potions `healing-potions` | on | Drinks Instant Health potions when health is low |
| Regeneration Potions `regen-potions` | off | Also drinks Regeneration potions at the same health |
| Potion Health `potion-health` | 8.0 | Drink healing potions at or below this health. 0.0 to 20.0. |
| Skip If Regenerating `skip-if-regenerating` | off | Saves apples and potions while you already have Regeneration |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Hurt Pause `hurt-pause` | 0 ticks | Don't start eating this soon after taking damage; 0 eats any time. 0 to 100 ticks. |
| Bite Timeout `bite-timeout` | - | Gives up on a bite that takes longer than this. 40 to 200 ticks. |

#### Surround

Places blocks around your feet.

`.toggle surround`

##### Settings (19)

**General**

| Setting | Default | What it does |
|---|---|---|
| Only On Ground `only-on-ground` | off | Waits to place while you are jumping or falling |
| Disable When Done `disable-when-done` | off | Turns off once every cell around you is filled |

**Features**

| Setting | Default | What it does |
|---|---|---|
| Down `down` | on | Fill under the surround floor |
| Ring `ring` | Center | Ring around center or hitbox. Choices: Center, Hitbox. |
| NoWaste `no-waste` | on | Skip extra cells inside holes |

**DisableOn**

| Setting | Default | What it does |
|---|---|---|
| YChange `disable-on-y-change` | on | Turn off on Y change |
| XZMove `disable-on-xz-move` | off | Turn off leaving start block |
| XZSpeed `disable-on-xz-speed` | off | Turn off when moving fast |
| Speed Limit `xz-speed-limit` | - | Sideways speed that turns it off. 0.1 to 2.0 blocks/tick. |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Filter `filter-mode` | Whitelist | How the list is applied. Choices: Whitelist, Blacklist. |

**Placing**

| Setting | Default | What it does |
|---|---|---|
| Aim Speed `aim-speed` | 3 | Turn and place speed. 1 to 5. |
| Switch Back `switch-back` | on | Return to your old slot |
| Switch Back Delay `switch-back-delay` | - | Idle ticks before returning to your old slot. 0 to 40 ticks. |

**Protect**

| Setting | Default | What it does |
|---|---|---|
| Protect `protect` | on | Refill blocks others are mining |
| Min Destroy Progress `min-destroy-progress` | 4 stage | Stage to start reacting at. 0 to 9 stage. |
| Extra Layer `extra-layer` | on | Second ring around mined cells |
| Corners `extra-layer-corners` | off | Also fill the diagonals |
| Force Extra Layer `force-extra-layer` | off | Extra layer on every cell |
| Breaker Timeout `breaker-timeout` | - | Forgets a mining player after this long without news. 0.5 to 10.0 s. |

#### TpClick

Teleport where you click.

`.toggle tp-click`

##### Settings (17)

**General**

| Setting | Default | What it does |
|---|---|---|
| Reach `reach` | 50 | Maximum click teleport distance. 10 to 250. |
| Click Key `click-key` | unbound | Button that teleports you. |
| Sneak Cancels `sneak-cancels` | on | Holding sneak turns click teleport off so you can click normally |
| Notify Errors `notify-errors` | on | Says in chat why a teleport could not start |

**Targeting**

| Setting | Default | What it does |
|---|---|---|
| Hit Fluids `fluids` | When Riding | Whether water and lava stop the aim ray. Choices: When Riding, Always, Never. |
| Max Climb `max-climb` | - | How far above the clicked block it looks for room to stand. 1 to 16 blocks. |
| Adjust Target `adjust-target` | on | Picks the nearest safe spot when the clicked one has no room |
| Search Radius `search-radius` | - | How far sideways it looks for a safe spot. 1 to 16 blocks. |
| Search Up `search-up` | - | How far up it looks for a safe spot. 0 to 16 blocks. |
| Search Down `search-down` | - | How far down it looks for a safe spot. 0 to 16 blocks. |

**Safety**

| Setting | Default | What it does |
|---|---|---|
| Block Movement `block-movement` | on | Freeze movement keys while teleporting. |
| No Fall `no-fall` | on | Prevent fall damage while teleporting. |
| Anti Kick `anti-kick` | on | Prevent floating kicks while airborne. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Highlight `highlight` | on | Outlines where you would land |
| Valid Color `valid-color` | colour | Box color when the clicked spot is safe |
| Adjusted Color `adjusted-color` | colour | Box color when a nearby spot was picked instead |
| Invalid Color `invalid-color` | colour | Box color when there is nowhere to stand |

#### XCarry

Keeps stored items in crafting / result / armor / offhand slots when closing inventory.

`.toggle xcarry`

##### Settings (7)

**Slots**

| Setting | Default | What it does |
|---|---|---|
| Use Crafting Grid `use-crafting` | on | Use all craft slots |
| Use Armor Slots `use-armor` | on | Use armor slots 5-8. |
| Use Offhand `use-offhand` | on | Use offhand slot. |
| Carry Cursor `carry-cursor` | on | Keep cursor stack. |

**General**

| Setting | Default | What it does |
|---|---|---|
| Notify `notify` | off | Says in chat how many stacks you carry when you close your inventory |
| Show Count `show-count` | on | Shows the carried crafting-grid stacks next to the module name |
| Empty On Disable `empty-on-disable` | off | Moves crafting-grid items back into your inventory when turned off |

### Misc

#### AdminTools

Creative/admin helpers.

`.toggle admin-tools`

##### Settings (57)

**Status**

| Setting | Default | What it does |
|---|---|---|
| Show Permission Status `admin-status` | button |  |
| Stop Queued Commands `admin-stop-queue` | button |  |

**Fireball Storm**

| Setting | Default | What it does |
|---|---|---|
| Count `fireball-count` | 12 | 1 to 256. |
| Spread `fireball-spread` | 8.0 | 0.0 to 90.0. |
| Speed `fireball-speed` | 1.8 | 0.1 to 10.0. |
| Power `fireball-power` | 3 | 0 to 127. |
| Delay `fireball-delay` | 1 | 0 to 20. |
| Spawn Dist `fireball-distance` | 2.0 | 0.5 to 16.0. |
| Aim `fireball-aim` | Cone | Choices: Look, Cone, Ring. |
| Randomize `fireball-randomize` | on |  |
| Stream Bind `fireball-stream-bind` | unbound |  |
| Firestorm Bind `firestorm-bind` | unbound |  |
| Firestorm Dist `firestorm-distance` | 8.0 | 2.0 to 48.0. |
| Firestorm Height `firestorm-height` | 2.0 | 0.0 to 32.0. |
| Preset Max Storm `fireball-preset-max` | button |  |
| Preview Command `fireball-preview` | button |  |
| Copy Command `fireball-copy` | button |  |
| Run Storm `fireball-storm` | button |  |

**General**

| Setting | Default | What it does |
|---|---|---|
| Stream Active `fireball-stream-active` | off |  |
| Firestorm Active `firestorm-active` | off |  |
| Imported Components `nbt-imported-components` | - |  |
| Imported Signature `nbt-imported-signature` | - |  |
| Full Item SNBT `nbt-item-stack` | - |  |
| Full Item Signature `nbt-item-stack-signature` | - |  |
| Fireball Power Legacy `fireballPower` | 3 |  |

**Airstrike**

| Setting | Default | What it does |
|---|---|---|
| Count `airstrike-count` | 24 | 1 to 256. |
| Radius `airstrike-radius` | 12.0 | 0.0 to 96.0. |
| Height `airstrike-height` | 48.0 | 8.0 to 256.0. |
| Fall Speed `airstrike-speed` | 2.0 | 0.1 to 10.0. |
| Power `airstrike-power` | 4 | 0 to 127. |
| Delay `airstrike-delay` | 1 | 0 to 20. |
| Target `airstrike-target` | Look | Choices: Look, Self. |
| Preset Tight Strike `airstrike-preset-small` | button |  |
| Preset Max Strike `airstrike-preset-max` | button |  |
| Run Airstrike `airstrike-run` | button |  |

**ForceOP**

| Setting | Default | What it does |
|---|---|---|
| Delay (ms) `forceop-delay` | 500 | 0 to 5000. |
| Wait for Chat `forceop-wait` | on |  |
| Min Length `forceop-min-length` | 0 | 0 to 32. |
| Max Length `forceop-max-length` | 0 | 0 to 32. |

**NBT Item Editor**

| Setting | Default | What it does |
|---|---|---|
| Item ID `nbt-item-id` | minecraft:stick |  |
| Count `nbt-item-count` | 1 | 1 to 99. |
| Display Name `nbt-item-name` | Custom Item |  |
| Lore Lines `nbt-item-lore` | - |  |
| Unbreakable `nbt-unbreakable` | off |  |
| Glint `nbt-glint` | Default | Choices: Default, On, Off. |
| Rarity `nbt-rarity` | Default | Choices: Default, Common, Uncommon, Rare, Epic. |
| Max Damage `nbt-max-damage` | 0 | 0 to 100000. |
| Damage `nbt-damage` | 0 | 0 to 100000. |
| Max Stack `nbt-max-stack` | 64 | 1 to 99. |
| Enchantments `nbt-enchants` | - |  |
| Attributes `nbt-attributes` | - |  |
| Custom Data `nbt-custom-data` | - |  |
| Embedded Command `nbt-command` | - |  |
| Raw Components `nbt-item-components` | - |  |
| Fill Held `fill-held-item` | button |  |
| Copy Target `copy-target-item` | button |  |
| Apply to Held `apply-held-item` | button |  |

#### AntiBot

Ignores fake/bot players.

`.toggle antibot`

##### Settings (9)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Conservative | Aggressive adds the flying-on-ground check, which can catch laggy real players. Choices: Conservative, Aggressive. |

**Checks**

| Setting | Default | What it does |
|---|---|---|
| Duplicate Names `check-duplicate-name` | on | Treat two players sharing a name with different UUIDs as bots. |
| Invalid Names `check-invalid-name` | on | Treat players whose name isn't a valid Minecraft username as bots. |
| Impossible Pitch `check-impossible-pitch` | on | Treat players looking past straight up or down as bots. |
| Pitch Limit `pitch-threshold` | 90 | Pitch past this many degrees counts as impossible. 60 to 90. |
| No Tab Entry `check-missing-profile` | on | Treat players with no matching tab-list profile as bots. |
| Bot Spots `check-bot-spots` | on | Treat still players standing exactly where a bot was as bots. |
| Flying On Ground `check-ground-flying` | on | Treat players marked on-ground while their height keeps changing as bots. |
| Ground Threshold `ground-violations` | 10 | Flying-on-ground ticks before a player counts as a bot. 1 to 40. |

#### AntiHunger

Prevents hunger from decreasing. Will flag anticheats.

`.toggle anti-hunger`

##### Settings (3)

**Sprint**

| Setting | Default | What it does |
|---|---|---|
| NoSprint `no-sprint` | on | Hide sprinting from the server so it doesn't drain hunger. |
| While Swimming `sprint-while-swimming` | off | Also hide sprinting while you swim. |

**Ground**

| Setting | Default | What it does |
|---|---|---|
| KeepFloating `keep-floating` | on | Tell the server you're never on the ground, which stops some hunger drain. |

#### AntiVanish

Detects vanished players.

`.toggle anti-vanish`

##### Settings (18)

**Detection**

| Setting | Default | What it does |
|---|---|---|
| Vanish Tracker `vanish-tracker` | on | Detect TAB disappearances. |
| Tab Probe `completion-probe` | on | Active tab probe. |
| Probe Command `probe-command` | minecraft:msg | Command whose tab-completion is used to list online players for the probe. |
| Gamemode Alerts `gamemode-alerts` | off | Warn when a player's gamemode changes, a common staff tell. |
| Player Filter `player-filter` | off | Only watch the players you list, ignoring everyone else. |
| Players `players` | - | Names watched when Player Filter is on. |

**Sensors**

| Setting | Default | What it does |
|---|---|---|
| Environmental `environmental` | on | Master switch for the world sensors below. |
| Suspicious Sounds `sound-sensor` | on | Flag sounds with no visible source, like a hidden player's steps. |
| Ghost Particles `particle-sensor` | on | Flag particles that appear with nothing there to make them. |
| Block Updates `block-sensor` | on | Flag block places and breaks you never saw a player do. |
| Invisible Entities `invisible-sensor` | on | Flag players who are present but rendered invisible. |
| Camera Aberrations `camera-sensor` | on | Flag forced camera resets a spectator can cause. |
| Chunk Re-sends `chunk-sensor` | on | Flag nearby chunks the server quietly re-sends. |
| Detection Range `range` | 64 | How far out, in blocks, the sensors watch. 8 to 160. |

**Alerts**

| Setting | Default | What it does |
|---|---|---|
| Critical Alert `critical-alert` | on | Raise a louder alert when several signals line up at once. |
| Warning Sound `alert-sound` | on | Play a sound on a critical alert. |
| Vanish HUD `hud-list` | on | List suspected vanished players on the HUD. |
| Chat Alerts `chat-alerts` | on | Log each detection to your chat. |

#### AutoReconnect

Reconnects after disconnect.

`.toggle auto-reconnect`

##### Settings (4)

| Setting | Default | What it does |
|---|---|---|
| Delay Sec `delay` | 3.5 | Seconds to wait before reconnecting. 0.0 to 60.0. |
| Random Extra `randomize` | off | Add a random extra wait on top of the delay. |
| Max Extra Sec `random-max` | 2.0 | Largest random extra wait that can be added. 0.5 to 30.0. |
| Max Attempts `max-attempts` | 0 | Stop after this many reconnects without a successful join; 0 is unlimited. 0 to 50. |

#### AutoTrade

Beta: 5.1 beta only, not in 5.0.1 stable.

Buys villager trades that match a rule, or rerolls a villager until one does.

See the full guide under Guides: `AutoTrade`.

`.toggle auto-trade`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Trade | Trade: buy matching offers. Reroll: reroll the villager you look at until one matches, then lock it. Choices: Trade, Reroll. |
| Match `rule` | mending | mending, unbreaking3, sharpness 4-5, book, slot 2, item:bread or any. |
| Profession `profession` | - | Only villagers with this job, e.g. librarian. Empty: any. |

**Limits**

| Setting | Default | What it does |
|---|---|---|
| Max Price `max-price` | 0 | Most emeralds to pay per purchase. 0: no limit. 0 to 64. |
| Emerald Budget `budget` | 0 | Most emeralds to spend while enabled. 0: no limit. 0 to 2304. |
| Emerald Reserve `reserve` | 0 | Never spend below this many emeralds. 0 to 2304. |
| Per-Visit Cap `per-visit` | 0 | Purchases per villager visit. 0: no limit. 0 to 64. |

**Behavior**

| Setting | Default | What it does |
|---|---|---|
| Restock Wait (s) `restock-wait` | 300 | Trade mode: seconds before visiting the same villager again. 0 to 1800. |
| Walk To Villagers `walk` | on | Trade mode: walk to nearby villagers and open their trades. |
| Offer Labels `labels` | on | Show each villager's best remembered offer above it (works with the module off). |

#### BetterChat

Beta: 5.1 beta only, not in 5.0.1 stable.

Longer chat history, stacked repeats, filters and more.

`.toggle better-chat`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| History `history` | 1000 | Messages chat keeps (vanilla is 100). 100 to 20000. |
| Stack Repeats `stack` | on | Merge repeated messages into one with a count. |
| Count Color `stack-color` | Gray | Color of the [xN] repeat counter. Choices: Gray, Dark Gray, Yellow, Gold, Green, Aqua. |
| Filters `filters` | - | Hide server messages matching any of these regular expressions. |

**Timestamps**

| Setting | Default | What it does |
|---|---|---|
| Timestamps `timestamps` | off | Prefix each message with the time it arrived. |
| Format `timestamp-format` | HH:mm | Clock format for the timestamp. Choices: HH:mm, HH:mm:ss. |
| Color `timestamp-color` | Dark Gray | Color of the timestamp. Choices: Dark Gray, Gray, White, Yellow, Gold, Green, Aqua. |

**Outgoing**

| Setting | Default | What it does |
|---|---|---|
| Prefix `prefix` | - | Put before what you send. |
| Suffix `suffix` | - | Put after what you send. |
| Full-width `unicode` | off | Send letters and digits as full-width characters. |

#### Blink

Suspends packets to/from the server, released on disable.

`.toggle blink`

##### Settings (7)

| Setting | Default | What it does |
|---|---|---|
| Outgoing `outgoing` | on | Hold sent packets |
| Incoming `incoming` | off | Hold received packets |
| Hold Movement `hold-movement` | on | Delay movement packets |
| Hold Attack/Interact `hold-actions` | on | Delay action packets |
| Show Position `show-position` | on | Draw server position |
| Auto Reset `auto-reset` | off | Flush periodically |
| Reset After (ticks) `reset-after` | 50 | Ticks between flushes. 1 to 100000. |

#### BookBot

Writes into books.

`.toggle bookbot`

##### Settings (13)

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Random | Random or file text. Choices: Random, File. |
| Random Type `random-type` | Utf8 | Choices: Utf8, Ascii, PaperMC. |
| Pages `pages` | 100 | 1 to 100. |
| Chars/Page `characters` | - | 1 to 1024. |
| Delay `delay` | 20 | 1 to 200. |
| Sign `sign` | on |  |
| Name `name` | Dih |  |
| Append Count `append-count` | on |  |
| Word Wrap `word-wrap` | on |  |
| File `file-path` | - | Selected file. |
| Pick File `pick-file` | button | Choose text file. |
| Text `text` | - |  |
| Full Default Migrated `full-default-migrated` | off |  |

#### GoldenLever

Renames and recolors vanilla levers.

`.toggle golden-lever`

##### Settings (8)

**Lever**

| Setting | Default | What it does |
|---|---|---|
| Lever Name `lever-name` | Golden Lever | Name shown when you hover a lever. |
| Name Color `name-color` | Gold | Color of the lever name. Choices: Gold, Yellow, White, Red, Green, Aqua, Blue, Light Purple, Gray. |

**Appearance**

| Setting | Default | What it does |
|---|---|---|
| Female Body `female-body` | off | Shape player bodies. |
| Self Only `female-body-self-only` | on | Only affect you. |
| Only Others `female-body-only-others` | off | Only affect others. |
| Custom Players `female-body-custom-players` | off | Use selected players. |
| Include Self `female-body-custom-include-self` | on | Also include you. |
| Players `female-body-player-names` | - | Choose player names. |

#### HUD

Shows HUD elements.

`.toggle hud`

##### Settings (6)

**Editor**

| Setting | Default | What it does |
|---|---|---|
| Edit HUD `edit-hud` | button | Open HUD editor. |
| Editor Grid `editor-grid` | on | Show editor grid. |

**Visibility**

| Setting | Default | What it does |
|---|---|---|
| Hide In GUIs `hide-in-guis` | on | Hide in screens. |
| Show In Chat `show-in-chat` | off | Show in chat. |
| Show In Pause `show-in-pause` | off | Show in pause. |
| Show In HUD Editor `show-in-hud-editor` | on | Show while editing. |

#### NameCensor

Replaces player names with aliases.

`.toggle name-censor`

##### Settings (9)

**Names**

| Setting | Default | What it does |
|---|---|---|
| Censor Self `censor-self` | on | Hide your own name. |
| Censor Everyone `censor-everyone` | off | Hide every known player name. |
| Censor Everything `censor-everything` | off | Aggressively hide any username-shaped text, even unknown names. |
| Names `names` | - | Extra names to always hide. |

**Aliases**

| Setting | Default | What it does |
|---|---|---|
| Alias Style `alias-style` | Words | Words: themed words. Numbered: Player1, Player2. Hidden: a plain ***. Choices: Words, Numbered, Hidden. |
| Self Alias `self-alias` | - | A fixed alias for your own name. |
| Custom Aliases `custom-aliases` | - | name=alias pairs. |

**Skins**

| Setting | Default | What it does |
|---|---|---|
| Hide All Skins `hide-skins-all` | off | Show the default skin for everyone. |
| Hide My Skin `hide-skin-self` | off | Show the default skin for yourself. |

#### PacketCanceller

Cancels selected packets.

`.toggle packet-canceller`

##### Settings (2)

| Setting | Default | What it does |
|---|---|---|
| C2S Packets `C2S-packets` | - | Outgoing packet list. |
| S2C Packets `S2C-packets` | - | Incoming packet list. |

#### PanicMode

Temporarily hides client modules and UI.

`.toggle hide`

#### PayAll

Pay every listed player.

`.toggle pay-all`

##### Settings (9)

**Targets**

| Setting | Default | What it does |
|---|---|---|
| Players `players` | - | Who to pay. Empty means everyone visible. |
| Probe Hidden `probe` | on | When paying everyone, also scan for hidden players. |
| Shuffle Order `shuffle` | off | Pay in a random order instead of list order. |

**Payment**

| Setting | Default | What it does |
|---|---|---|
| Amount `amount` | 1000 | Amount per player. Supports K, M, B. |
| Split Total `divide` | off | Treat the amount as a total and split it across players. |
| Command `command` | /pay <player> <amount> | Command template with <player> and <amount>. |

**Timing**

| Setting | Default | What it does |
|---|---|---|
| Delay (ms) `delay` | 1000 | Gap between payments. 0 to 10000. |

**Advanced**

| Setting | Default | What it does |
|---|---|---|
| Dry Run `dry-run` | off | Log each command instead of sending it, to preview the run. |
| Confirm Macro `confirm-macro` | - | Run after every pay. |

#### PingSpoof

Adds latency to your ping by delaying keep-alives.

`.toggle ping-spoof`

##### Settings (3)

**General**

| Setting | Default | What it does |
|---|---|---|
| Delay (ms) `delay` | 250 | Latency added to ping. 0 to 500. |

**Real Latency**

| Setting | Default | What it does |
|---|---|---|
| Real: Incoming `real-incoming` | off | Delay packets you receive. |
| Real: Outgoing `real-outgoing` | off | Delay packets you send. |

#### Spam

Sends timed chat.

`.toggle spam`

##### Settings (13)

**General**

| Setting | Default | What it does |
|---|---|---|
| Messages `messages` | - | Messages separated by \|. |
| Delay `delay` | 20 | 0 to 200. |
| Randomise `randomise` | off |  |

**Safety**

| Setting | Default | What it does |
|---|---|---|
| Disable On Leave `disable-on-leave` | on |  |
| Disable On Disconnect `disable-on-disconnect` | on |  |

**Splitting**

| Setting | Default | What it does |
|---|---|---|
| Auto Split `auto-split-messages` | off |  |
| Split Length `split-length` | 256 | 8 to 256. |
| Split Delay `split-delay` | 20 | 0 to 200. |

**Bypass**

| Setting | Default | What it does |
|---|---|---|
| Bypass `bypass` | off |  |
| Uppercase `include-uppercase-characters` | off |  |
| Bypass Length `length` | 4 | 1 to 16. |
| Uppercase Legacy `uppercase` | off |  |
| Bypass Length Legacy `bypass-length` | 4 | 1 to 16. |

#### Teams

Friends and team detection.

This is the friends list `.friend` manages. It can also group players by name colour or armour dye.

`.toggle teams`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Friends `friends` | - | Players you treat as friends. |
| Auto Teams `auto-teams` | off | Also treat likely teammates as friends automatically. |
| Team By Name Color `team-by-color` | on | Count players whose name color matches yours as teammates. |
| Team By Armor Dye `team-by-armor` | on | Count players whose dyed helmet matches yours as teammates. |
| Multi Bots `multi-bots` | on | Count your own Multi bots as teammates. |
| Quick Add `quick-add` | on | Add or remove the player you look at with a key. |
| Quick Add Key `quick-add-bind` | unbound | Key that adds or removes the targeted player. |

**Visuals**

| Setting | Default | What it does |
|---|---|---|
| ESP `esp` | on | Draw boxes around friends. |
| Tracers `tracers` | on | Draw lines to friends. |
| Nametags `nametags` | on | Color friends' nametags. |
| Friends Color `friends-color` | colour | Color used for friend visuals. |

**Combat**

| Setting | Default | What it does |
|---|---|---|
| AimAssist `aimassist` | off | Let AimAssist target friends. |
| KillAura `killaura` | off | Let KillAura attack friends. |
| TriggerBot `triggerbot` | off | Let TriggerBot attack friends. |

### Render

#### BedESP

Beta: 5.1 beta only, not in 5.0.1 stable.

Shows bed defence layers and the fastest way through them.

Colours the defence around each nearby bed by layer. **Fastest Path** highlights the quickest way in, counting walking and mining time with the best tool in your hotbar (enchantments and effects aren't counted). **HUD Summary** shows the nearest bed above your hotbar, and each bed gets a floating label with the team, break time, distance and what the defence is made of.

`.toggle bed-esp`

##### Settings (30)

**General**

| Setting | Default | What it does |
|---|---|---|
| Range `range` | 48 | Horizontal distance to look for beds. 8 to 128. |
| Layers `layers` | 3 | How many defence layers to show. 1 to 6. |
| Max Beds `max-beds` | 8 | Most beds analysed at once (nearest first). 1 to 32. |
| Scan Interval `scan-interval` | - | How often beds and their defences are re-checked. 2 to 40 ticks. |
| Walk Cost `walk-cost` | - | Time one step counts for in the fastest path; higher prefers breaking straight through. 1 to 20 ticks. |

**Display**

| Setting | Default | What it does |
|---|---|---|
| Show Layers `show-layers` | on | Colour each defence block by its layer. |
| Fastest Path `show-path` | on | Highlight the blocks to break on the fastest way in. |
| HUD Summary `hud` | on | Show the nearest bed's layers and break time on screen. |
| Fill `fill` | on | Draw translucent faces. |
| Fill Strength `fill-strength` | 100 % | How solid the faces are. 10 to 300 %. |
| Outline `outline` | on | Draw edges. |
| Line Width `line-width` | - | Edge thickness of the bed and layers. 1.0 to 6.0. |
| Path Line Width `path-line-width` | - | Edge thickness of the path blocks. 1.0 to 6.0. |

**Label**

| Setting | Default | What it does |
|---|---|---|
| Labels `labels` | on | Float a label over each bed: its name, break time and defence blocks. |
| Scale `label-scale` | - | Label size. 0.25 to 3.0. |
| Distance Scaling `label-distance-scale` | on | Shrink labels with distance. |
| Height `label-height` | 1.0 blocks | How far above the bed the label floats. 0.0 to 4.0 blocks. |
| Break Time `label-time` | on | Time to break through to the bed. |
| Distance `label-distance` | off | Your distance to the bed. |
| Defence Blocks `label-materials` | on | Each defence block with its count. |
| Max Blocks `label-max-materials` | - | Most defence block kinds listed. 1 to 12. |
| Bed Colour Name `label-dye-color` | on | Writes the bed's name in its dye colour. |
| Name Colour `label-color` | colour | Name colour when not using the bed's dye. |
| Background `label-background` | on | Dark box behind the label. |
| Background Colour `label-background-color` | colour | Colour of the box. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Bed `color-bed` | colour | The bed itself. |
| Layer 1 `color-layer-1` | colour | Blocks touching the bed. |
| Layer 2 `color-layer-2` | colour | The second layer. |
| Layer 3+ `color-layer-3` | colour | The third layer and beyond. |
| Path `color-path` | colour | Blocks on the fastest way in. |

#### BetterTooltips

Adds item tooltip info.

`.toggle better-tooltips`

##### Settings (20)

**Info**

| Setting | Default | What it does |
|---|---|---|
| NBT Shortcut `nbt-shortcut` | Ctrl+Shift+Right-click |  |
| Block NBT Inspect `block-nbt-inspect` | off | Ctrl+Shift+Right-click blocks. |
| NBT Hint `nbt-hint` | on | Shows the NBT shortcut line. |
| Minecraft Name `minecraft-name` | on | The item's id, such as minecraft:stone. |
| Durability `durability` | on | Uses left out of the total. |
| Durability % `durability-percent` | off | Adds the uses left as a percentage. |
| Repair Cost `repair-cost` | off | Anvil repair cost in levels, when the item has one. |
| Food Info `food-info` | on | Hunger and saturation restored. |
| Byte Size `byte-size` | on | How big the item's data is. |
| Byte Format `byte-size-format` | Both | Bytes, KiB, or both. Choices: Raw, Rounded, Both. |

**Containers**

| Setting | Default | What it does |
|---|---|---|
| Open Contents `open-contents` | on | Preview only. |
| Containers `containers` | on | Stack count of shulkers and other containers. |
| Compact Shulker `compact-shulker-tooltip` | on | Compact counts. |
| Ender Chests `echests` | on | Server-side data. |

**Items**

| Setting | Default | What it does |
|---|---|---|
| Maps `maps` | on | Map id. |
| Map Scale `map-scale` | 1.0 | Map preview scale. 0.5 to 4.0. |
| Books `books` | on | Page count of books. |
| Banners `banners` | on | Banner patterns. |
| Buckets `buckets` | on | Marks buckets holding a mob. |
| Bundles `bundles` | on | Stack count of bundles. |

#### BlockESP

Highlights selected blocks.

`.toggle block-esp`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| Max Blocks `max-targets` | 1024 | Max rendered blocks. 64 to 8192. |
| Fill `fill` | off | Draw translucent filled boxes. |
| Fill Opacity `fill-opacity` | 30 % | How solid the fill is. 5 to 100 %. |
| Outline `outline` | on | Draw the box edges. |
| Line Width `line-width` | 1.5 | Edge thickness. 0.5 to 6.0. |
| Meshing `meshing` | on | Merge adjacent targets. |

**Tracers**

| Setting | Default | What it does |
|---|---|---|
| Tracers `tracers` | off | Line to each target |
| Tracer Width `tracer-width` | 2.0 | Line thickness. 1.0 to 6.0. |

**Blocks**

| Setting | Default | What it does |
|---|---|---|
| Barriers `barriers` | off | Show invisible barrier blocks. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Color `color` | colour | Colour for non-ore blocks. Ores are coloured by type. |

#### Breadcrumbs

Beta: 5.1 beta only, not in 5.0.1 stable.

Draws a trail behind you or other players.

`.toggle breadcrumbs`

##### Settings (19)

**General**

| Setting | Default | What it does |
|---|---|---|
| Only Own `only-own` | on | Trail only yourself, not every player. |
| Include Self `include-self` | on | Also trail yourself when trailing every player. |
| Ignore Friends `ignore-friends` | off | Leaves friends and teammates untrailed. |
| Ignore Bots `ignore-bots` | off | Skips players AntiBot flags as bots. |
| Pause In Air `pause-in-air` | off | Adds no points while the player is off the ground. |

**Trail**

| Setting | Default | What it does |
|---|---|---|
| Temporary `temporary` | on | Let old points expire. |
| Alive `alive` | 3000 ms | How long a point lasts. 100 to 60000 ms. |
| Fade `fade` | on | Fade points out as they expire. |
| Max Points `max-points` | 2000 | Most points kept per trail; the oldest go first. 50 to 20000. |
| Spacing `spacing` | 0.05 blocks | How far a player moves before a new point is added. 0.05 to 2.0 blocks. |
| Clear On Death `clear-on-death` | on | Drops your trail when you die and respawn. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Color `color` | colour | Trail colour. |
| Others Color `others-color` | colour | Colour for other players' trails. |
| Rainbow `rainbow` | off | Cycle the trail through the colours. |
| Rainbow Speed `rainbow-speed` | 4.0 s | Seconds for one full colour cycle. 0.5 to 20.0 s. |
| Height `height` | 0.0 | 0 draws a line; above 0 a ribbon this tall. 0.0 to 2.0. |
| Line Width `line-width` | - | Thickness of the line trail. 1.0 to 6.0. |
| Y Offset `y-offset` | 0.0 blocks | Raises or lowers the trail from the player's feet. -1.0 to 2.0 blocks. |
| Render Distance `render-distance` | 0 blocks | Hides points farther than this from the camera; 0 is no limit. 0 to 512 blocks. |

#### Chams

Entities through walls.

`.toggle chams`

##### Settings (15)

**General**

| Setting | Default | What it does |
|---|---|---|
| Style `style` | Colored | Colour or texture. Choices: Colored, Texture. |
| Max Distance `max-distance` | 128.0 | Range, 0 unlimited. 0.0 to 512.0. |
| Draw Armor `draw-armor` | on | Real armor on top. |
| Self `self` | off | Also on you in third person. |
| Ignore Friends `ignore-friends` | off | Leaves friends and teammates normal. |
| Ignore Invisible `ignore-invisible` | off | Leaves invisible entities hidden. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Visible `visible-color` | colour | Colour when visible. |
| Behind Walls `occluded-color` | colour | Colour behind walls. |
| Opacity `opacity` | 100 | Solid colour opacity. 0 to 100. |
| Friend Color `friend-color` | off | Friends in the Teams friend colour. |
| Rainbow `rainbow` | off | Cycle the colours over time. |
| Rainbow Speed `rainbow-speed` | 4.0 s | Seconds for one full cycle. 0.5 to 20.0 s. |

**Hit Color**

| Setting | Default | What it does |
|---|---|---|
| Hit Color `hit-color` | on | Flash on hit. |
| Color `hit-color-value` | colour | Flash colour. |
| Duration `hit-duration` | 500 ms | How long the flash lasts. 50 to 2000 ms. |

#### ESP

Highlights entities.

`.toggle esp`

##### Settings (22)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | 2D | Entity highlight style. Choices: Shader, 2D. |
| Max Distance `max-distance` | 0.0 | Farthest entity highlighted; 0 is no limit. 0.0 to 512.0. |
| Fade Distance `fade-distance` | 3.0 | Fades entities closer than this to the camera. 0.0 to 12.0. |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities. |

**2D**

| Setting | Default | What it does |
|---|---|---|
| Box Style `box-style` | Full | Whole box or only its corners. Choices: Full, Corners. |
| Fill `fill` | off | Shade inside the box. |
| Fill Alpha `fill-alpha` | 50 | Fill strength out of 255. 5 to 255. |
| Border `border` | on | Black edge around the box. |
| Health Bar `health-bar` | on | Health bar beside living entities. |
| Smooth `smooth` | on | Eases the box between frames. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Distance Color `distance-color` | off | Colour by distance, red near to green far. |
| Color Distance `color-distance` | 40 | Distance that counts as far. 8 to 256. |
| Players `players-color` | colour | Player colour. |
| Animals `animals-color` | colour | Passive mob colour. |
| Water Animals `water-animals-color` | colour | Water mob colour. |
| Monsters `monsters-color` | colour | Hostile mob colour. |
| Ambient `ambient-color` | colour | Bat and other ambient mob colour. |
| Misc `misc-color` | colour | Colour for everything else. |

**Skeleton**

| Setting | Default | What it does |
|---|---|---|
| Skeleton `skeleton` | off | Draw animated skeletons. |
| Color Mode `skeleton-color-mode` | Static | Skeleton color source. Choices: Static, Distance. |
| Color `skeleton-color` | colour | Skeleton colour. |
| Line Width `skeleton-width` | 2.0 | Skeleton line thickness. 1.0 to 6.0. |

#### FakeCoords

Spoofs displayed coordinates only.

`.toggle fake-coords`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Offset | Choose spoofing mode. Choices: Offset, Scaled, Rotated, Scrambled, Frozen, Custom. |
| Fake Y `fake-y` | off | Spoof vertical coordinate. |
| Debug Screen `affect-f3` | on | Spoof the coordinates on the F3 screen. |
| HUD `affect-hud` | on | Spoof the coordinates in the HUD lines. |
| Reroll `reroll` | button | Pick new random fake coordinates. |
| Reroll On Join `reroll-on-join` | off | Pick new fake coordinates every time you join a world. |

**Custom**

| Setting | Default | What it does |
|---|---|---|
| Custom X `custom-x` | 0 | Set displayed X. |
| Custom Y `custom-y` | 64 | Set displayed Y. |
| Custom Z `custom-z` | 0 | Set displayed Z. |
| Anchor `anchor` | button | Use current position. |

#### Freecam

Move the camera away from the player.

`.toggle freecam`

##### Settings (11)

**Movement**

| Setting | Default | What it does |
|---|---|---|
| Speed `speed` | 1.0 | Horizontal camera speed. 0.05 to 30.0. |
| Vertical Speed `vertical-speed` | 1.0 | Vertical camera speed. 0.05 to 30.0. |
| Walk Speed `walk-speed` | 50 % | Speed without sprint, as a share of the full speed. 5 to 100 %. |
| Follow Pitch `follow-pitch` | off | Forward moves where you look, up and down included. |
| Scroll Speed `scroll-speed` | on | Scroll the wheel to change the speed. |
| Scroll Step `scroll-step` | 0.5 | Speed change per scroll notch. 0.05 to 5.0. |
| Sensitivity `sensitivity` | 100 % | How fast the camera turns. 10 to 300 %. |

**General**

| Setting | Default | What it does |
|---|---|---|
| Reload Chunks `reload-chunks` | on | Refresh chunk culling. |
| Interact `interact` | on | Interact from freecam view |
| Disable On Damage `disable-on-damage` | off | Turns off when you take damage. |
| Disable On Death `disable-on-death` | on | Turns off when you die. |

#### FreeLook

Look around without rotating the player.

`.toggle free-look`

##### Settings (6)

**General**

| Setting | Default | What it does |
|---|---|---|
| Hold Key `hold` | on | Only while the key is held; off makes the key toggle it. |
| Perspective `perspective` | Back | Camera behind you or facing you. Choices: Back, Front. |
| Restore View `restore-perspective` | on | Goes back to your previous camera view when released. |

**Mouse**

| Setting | Default | What it does |
|---|---|---|
| Sensitivity `sensitivity` | 100 % | How fast the camera turns. 10 to 300 %. |
| Invert Y `invert-y` | off | Flips up and down for the free camera. |
| No Pitch Limit `no-pitch-limit` | on | Lets the camera look past straight up and down. |

#### Fullbright

Brightens the world.

`.toggle fullbright`

##### Settings (4)

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Gamma | How lighting is boosted. Choices: Gamma, Potion, Luminance. |
| Effect Icon `show-icon` | on | Shows the night vision icon. |
| Light Type `light-type` | BLOCK | Which light to raise. Choices: SKY, BLOCK. |
| Min Light `minimum-light-level` | 8 | Lowest light level anywhere. 0 to 15. |

#### GIF

Beta: 5.1 beta only, not in 5.0.1 stable.

Shows an animated GIF or image on the HUD, as your cape or on your shield.

Shows a GIF, PNG or JPEG on your HUD. **Image Path** is a full path or one inside `.minecraft`. **Cape** and **Shield** turn the image into an animated cape and shield on your own player. Leave **Cape Image** and **Shield Image** blank to use Image Path. Only you see them.

`.toggle gif`

##### Settings (18)

**Image**

| Setting | Default | What it does |
|---|---|---|
| Image Path `image` | - | GIF/PNG/JPEG path (absolute, or relative to the .minecraft folder). |
| Speed `speed` | 1.0 | Animation speed multiplier. 0.1 to 5.0. |
| Paused `paused` | off | Freezes the animation on the current frame. |
| Play Once `play-once` | off | Stops on the last frame instead of looping. |

**HUD**

| Setting | Default | What it does |
|---|---|---|
| HUD `hud` | on | Draw the image on the HUD. |
| Width `width` | 64 | On-screen width in pixels. 1 to 1024. |
| Keep Aspect `keep-aspect` | on | Scale height to match the image's aspect ratio. |
| Height `height` | 64 | On-screen height in pixels. 1 to 1024. |
| Opacity `opacity` | 100 | Opacity percent. 0 to 100. |
| Tint `tint` | colour | Colour multiplied over the image; white keeps it as is. |
| Hide In Menus `hide-in-screens` | off | Hides the HUD image while a menu or inventory is open. |

**Position**

| Setting | Default | What it does |
|---|---|---|
| Anchor `anchor` | Top Left | Screen corner the offsets are measured from. Choices: Top Left, Top Right, Bottom Left, Bottom Right, Center. |
| X Offset `x` | 4 | Horizontal offset from the anchor. 0 to 8000. |
| Y Offset `y` | 4 | Vertical offset from the anchor. 0 to 8000. |

**Cosmetics**

| Setting | Default | What it does |
|---|---|---|
| Cape `cape` | off | Wear the image as your cape. Only you see it; your skin settings must show capes. |
| Cape Image `cape-image` | - | Separate cape file; blank uses Image Path. A 2:1 image is used as a full cape texture, anything else covers the back of the cape. |
| Shield `shield` | off | Paint the image on the front of shields you hold (first person and your own player model). Only you see it; banner patterns are hidden. |
| Shield Image `shield-image` | - | Separate shield file; blank uses Image Path. |

#### HoleESP

Highlights safe holes.

`.toggle hole-esp`

##### Settings (20)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | - | Full box or glowing floor. |
| Horizontal Distance `horizontal-distance` | 32 | Scan radius on X/Z. 4 to 128. |
| Vertical Distance `vertical-distance` | 8 | Scan radius on Y. 4 to 64. |
| Max Holes `max-holes` | 512 | Most holes drawn at once. 16 to 4096. |
| Hide Own Hole `hide-own` | off | Doesn't draw the hole you are standing in. |

**Holes**

| Setting | Default | What it does |
|---|---|---|
| 1x1 Bedrock `show-1x1-bedrock` | on | Show one-block holes walled in bedrock. |
| 1x1 `show-1x1` | on | Show other one-block holes. |
| 1x2 `show-1x2` | on | Show two-block holes. |
| 2x2 `show-2x2` | on | Show four-block holes. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Fill `fill` | on | Draw the translucent face. |
| Fill Alpha `fill-alpha` | - | Fill strength out of 255. 5 to 255. |
| Outline `outline` | on | Draw the edges. |
| Outline Alpha `outline-alpha` | - | Edge strength out of 255. 5 to 255. |
| Line Width `line-width` | - | Edge thickness. 1.0 to 6.0. |
| Glow Height `glow-height` | 0.7 | Height of the fading walls. 0.0 to 1.0. |
| Distance Fade `distance-fade` | 0.3 | Fraction of radius that fades. 0.0 to 1.0. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| 1x1 Bedrock `color-1x1-bedrock` | colour | A 1x1 walled in bedrock. |
| 1x1 `color-1x1` | colour | A one-block hole. |
| 1x2 `color-1x2` | colour | A two-block hole. |
| 2x2 `color-2x2` | colour | A four-block hole. |

#### ItemESP

Outlines dropped items with the shader outline.

`.toggle item-esp`

##### Settings (10)

**General**

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Shader | Dropped item highlight style. Choices: Shader. |
| Items `items-mode` | All | Which items to outline. Choices: All, Some. |
| Min Stack `min-count` | 1 | Skips stacks smaller than this. 1 to 64. |
| Enchanted Only `enchanted-only` | off | Only enchanted items |
| Max Distance `max-distance` | 64.0 | 0 = unlimited. 0.0 to 256.0. |
| Fade Distance `fade-distance` | 3.0 | Fades items closer than this to the camera. 0.0 to 12.0. |

**Tracers**

| Setting | Default | What it does |
|---|---|---|
| Tracers `tracers` | off | Line to each outlined item |
| Tracer Width `tracer-width` | 2.0 | Line thickness. 1.0 to 6.0. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Color Mode `color-mode` | Dynamic | Dynamic colours each item by what it is. Choices: Dynamic, Static. |
| Color `color` | colour | Outline colour |

#### LogoffSpot

Beta: 5.1 beta only, not in 5.0.1 stable.

Marks where players logged off.

`.toggle logoff-spot`

##### Settings (18)

**General**

| Setting | Default | What it does |
|---|---|---|
| Detection `detection` | Tab List | Tab List: only players who also left the tab list. Any Removal: every player who disappears, as on servers without a tab list. Choices: Tab List, Any Removal. |
| Ignore Friends `ignore-friends` | off | Doesn't mark friends and teammates. |
| Expire After `expire` | 0 min | Removes a spot after this many minutes; 0 keeps it until they return. 0 to 120 min. |

**Chat**

| Setting | Default | What it does |
|---|---|---|
| Chat `chat` | on | Say in chat when someone logs off or comes back. |
| Coordinates `chat-coords` | on | Include where they logged off. |
| Returns `chat-return` | on | Also say when someone comes back. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Color `color` | colour | Box colour. |
| Fill `fill` | on | Shade the box. |
| Fill Opacity `fill-opacity` | 25 % | Fill strength, as a share of the colour's alpha. 5 to 100 %. |
| Outline `outline` | on | Draw the box edges. |
| Line Width `line-width` | - | Edge thickness. 1.0 to 6.0. |
| Max Distance `max-distance` | 256 blocks | Hides spots farther than this. 16 to 4096 blocks. |

**Label**

| Setting | Default | What it does |
|---|---|---|
| Label `label` | on | Show a label above the spot. |
| Health `label-health` | on | Health when they left. |
| Time `label-time` | on | How long ago they left. |
| Distance `label-distance` | off | Your distance to the spot. |
| Coordinates `label-coords` | off | Block coordinates of the spot. |
| Background `label-background` | on | Dark box behind the text. |

#### Nametags

Labels above entities.

`.toggle nametags`

##### Settings (26)

**General**

| Setting | Default | What it does |
|---|---|---|
| Players `players` | on | Tag players |
| Mobs `mobs` | off | Tag mobs |
| Items `items` | on | Tag dropped items |
| Self `self` | off | Tag yourself in third person |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities |

**Info**

| Setting | Default | What it does |
|---|---|---|
| Show Health `show-health` | on | Show health |
| Health Format `health-format` | HP | Health as points, hearts or percent. Choices: HP, Hearts, Percent. |
| Health Color `health-color` | off | Colour health from green to red |
| Show Distance `show-distance` | off | Show distance |
| Show Ping `show-ping` | off | Show players' ping |
| Show Game Mode `show-gamemode` | off | Show players' game mode, such as [C] for creative |

**Equipment**

| Setting | Default | What it does |
|---|---|---|
| Equipment `equipment` | on | Show armour and held items above the tag |
| Enchantments `equipment-enchants` | on | Short enchantment tags over each item |
| Max Enchantments `max-enchants` | - | Most enchantment tags shown per item. 1 to 10. |
| Durability `equipment-durability` | on | Durability bars and remaining uses |

**Display**

| Setting | Default | What it does |
|---|---|---|
| Scale `scale` | 1.0 | Label size. 0.5 to 4.0. |
| Distance Scaling `distance-scale` | on | Shrink with distance |
| Max Distance `max-distance` | 64.0 | 0 = unlimited. 0.0 to 256.0. |
| Height `y-offset` | 0.45 | How far above the head the tag sits. 0.0 to 2.0. |
| Background `background` | on | Dark box behind the text |
| Background Color `background-color` | colour | Colour of the box |
| Text Shadow `text-shadow` | on | Shadow under the text |
| Friend Color `friend-color` | on | Writes friends' names in the Teams friend colour |

**Items**

| Setting | Default | What it does |
|---|---|---|
| Group Items `group-items` | on | Merge nearby items |
| Group Radius `group-radius` | 5.0 | Item merge radius. 0.5 to 16.0. |
| Max Lines `max-item-lines` | 6 | Most item kinds listed in one tag. 1 to 20. |

#### NewChunks

Beta: 5.1 beta only, not in 5.0.1 stable.

Highlights chunks generated while you were near.

`.toggle new-chunks`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Render Distance `render-distance` | 32 chunks | Hides marks farther than this. 4 to 128 chunks. |
| Persist `persist` | on | Keep marks after chunks unload. |
| Clear Marks `clear` | button | Forgets every marked chunk. |

**Position**

| Setting | Default | What it does |
|---|---|---|
| Height `height-mode` | Fixed | Draw at a fixed Y, or relative to your feet. Choices: Fixed, Relative. |
| Y `y` | 0 | Height of the marks. -64 to 320. |
| Offset `offset` | -2 | Blocks above or below your feet. -128 to 128. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Show New `show-new` | on | Colour chunks that are new. |
| Show Old `show-old` | on | Also colour chunks with no sign of being new. |
| Smooth `smooth` | on | Tint old chunks next to new ones. |
| Fill `fill` | on | Shade the chunk. |
| Outline `outline` | on | Draw the chunk border. |
| Line Width `line-width` | - | Border thickness. 1.0 to 6.0. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| New Color `new-color` | colour | Colour of new chunks. |
| Old Color `old-color` | colour | Colour of old chunks. |

#### NoRender

Disables rendering of selected overlays, HUD elements, world features and entities.

`.toggle no-render`

##### Settings (52)

**Overlay**

| Setting | Default | What it does |
|---|---|---|
| Portal Overlay `portal-overlay` | off | Hide nether portal overlay |
| Spyglass Overlay `spyglass-overlay` | off | Hide spyglass overlay |
| Nausea `nausea` | off | Disable nausea/portal warp |
| Pumpkin Overlay `pumpkin-overlay` | off | Hide pumpkin overlay |
| Powdered Snow Overlay `powdered-snow-overlay` | off | Hide powder snow overlay |
| Fire Overlay `fire-overlay` | off | Hide fire overlay |
| Liquid Overlay `liquid-overlay` | off | Hide underwater overlay |
| In-Wall Overlay `in-wall-overlay` | off | Hide in-block overlay |
| Vignette `vignette` | off | Disable screen vignette |
| GUI Background `gui-background` | off | Hide GUI background dim |
| Totem Animation `totem-animation` | off | Hide totem animation |
| Eating Particles `eating-particles` | off | Hide eating particles |
| Enchantment Glint `enchantment-glint` | off | Hide enchantment glint |
| Hurt Cam `hurt-cam` | off | Disable hurt camera tilt and animation |

**HUD**

| Setting | Default | What it does |
|---|---|---|
| Boss Bar `boss-bar` | off | Hide boss bars |
| Scoreboard `scoreboard` | off | Hide scoreboard |
| Crosshair `crosshair` | off | Hide crosshair |
| Title `title` | off | Hide on-screen titles |
| Held Item Name `held-item-name` | off | Hide held item name |
| Obfuscation `obfuscation` | off | Show obfuscated text |
| Potion Icons `potion-icons` | off | Hide effect icons |

**World**

| Setting | Default | What it does |
|---|---|---|
| Weather `weather` | off | Hide rain and snow |
| World Border `world-border` | off | Hide world border |
| Blindness `blindness` | off | Disable blindness fog |
| Darkness `darkness` | off | Disable darkness fog |
| Fog `fog` | off | Disable fog |
| Enchant Table Book `enchantment-table-book` | off | Hide enchant table book |
| Sign Text `sign-text` | off | Hide sign text |
| Block Break Overlay `block-break-overlay` | off | Hide block crack overlay |
| Block Break Particles `block-break-particles` | off | Hide block break particles |
| Beacon Beams `beacon-beams` | off | Hide beacon beams |
| Falling Blocks `falling-blocks` | off | Hide falling blocks |
| Map Markers `map-markers` | off | Hide map markers |
| Map Contents `map-contents` | off | Hide map contents |
| Banners `banners` | off | Hide banners |
| Firework Explosions `firework-explosions` | off | Hide firework explosions |
| Hide All Particles `hide-all-particles` | off | Hide all particles |
| Texture Rotations `texture-rotations` | off | Constant texture rotations |

**Sky**

| Setting | Default | What it does |
|---|---|---|
| Sky `sky` | off | Hide the sky |
| Stars `stars` | off | Hide the stars |
| Sun `sun` | off | Hide the sun |
| Moon `moon` | off | Hide the moon |
| Sunset Glow `sunrise-glow` | off | Hide sunrise/sunset glow |
| Clouds `clouds` | off | Hide the clouds |

**Time & Weather**

| Setting | Default | What it does |
|---|---|---|
| Custom Time `custom-time` | 6000 | Custom time in ticks. 0 to 24000. |

**Entity**

| Setting | Default | What it does |
|---|---|---|
| Drop Spawn Packets `drop-spawn-packets` | off | Drop listed spawn packets |
| Armor `armor` | off | Hide entity armor |
| Invisibility `invisibility` | off | Show invisible entities |
| Glowing `glowing` | off | Disable glowing outline |
| Spawner Entities `spawner-entities` | off | Hide spawner mobs |
| Dead Entities `dead-entities` | off | Hide dead entities |
| Nametags `nametags` | off | Hide entity nametags |

#### Radar

Beta: 5.1 beta only, not in 5.0.1 stable.

Arrows around the crosshair pointing at nearby entities.

`.toggle radar`

##### Settings (18)

**General**

| Setting | Default | What it does |
|---|---|---|
| Targets `targets` | Players | Which entities get an arrow. Choices: Players, Players + Hostile, All Living. |
| Max Distance `max-distance` | 128 blocks | Farthest entity shown. 8 to 512 blocks. |
| Height Range `y-range` | 0 blocks | Skips entities this far above or below you; 0 is no limit. 0 to 256 blocks. |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities. |
| Ignore Friends `ignore-friends` | off | Skips friends and teammates. |

**Arrow**

| Setting | Default | What it does |
|---|---|---|
| Radius `radius` | 40 | Distance of the arrows from the crosshair. 10 to 200. |
| Width `width` | 8 | Arrow width in pixels. 2 to 40. |
| Height `height` | 10 | Arrow length in pixels. 2 to 40. |
| Notch `notch` | 2 | Depth of the cut in the arrow's tail. 0 to 20. |
| Distance Scale `distance-scale` | off | Shrinks arrows for far entities, down to half size. |
| Tilt `tilt` | By Pitch | Flatten the ring as if lying on the ground. Choices: None, Static, By Pitch. |
| Tilt Angle `tilt-angle` | 45 deg | How much the ring is flattened. 5 to 90 deg. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Color `color-mode` | Distance | What the arrow colour shows. Choices: Distance, Health, Static. |
| Static Color `color` | colour | Arrow colour. |
| Fade `fade` | on | Fade arrows for far entities. |
| Fade Strength `fade-strength` | 70 % | How faint the farthest arrows get. 10 to 100 %. |
| Friend Color `friend-color` | on | Draw Teams friends in their own colour. |
| Friends `friend-color-value` | colour | Colour for friends. |

#### Schematics

Beta: 5.1 beta only, not in 5.0.1 stable.

Places schematic files in the world and shows what is still to build.

See the full guide under `.schem`.

`.toggle schematics`

##### Settings (17)

**Render**

| Setting | Default | What it does |
|---|---|---|
| Ghost Blocks `ghost-blocks` | on | Draw the blocks still missing. |
| Ghost Alpha `ghost-alpha` | 150 | Ghost block strength out of 255. 20 to 255. |
| Overlay `overlay` | on | Tint every block that is not right yet. |
| Outline `outline` | on | Edge the tinted blocks. |
| See Through `see-through` | off | Draw the overlay through walls. |
| Render Range `render-range` | 96 | Blocks from the camera that are drawn. 16 to 512. |

**Layers**

| Setting | Default | What it does |
|---|---|---|
| Layers `layer-mode` | - | Draw all layers, one, or those below or above a height. |
| Layer Y `layer-y` | 64 | The height the layer mode uses. -64 to 320. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Missing `color-missing` | colour | A block not placed yet. |
| Extra `color-extra` | colour | A block where the schematic has air. |
| Wrong State `color-wrong-state` | colour | The right block, turned or set wrong. |
| Wrong Block `color-wrong-block` | colour | Another block than the schematic's. |

**Keys**

| Setting | Default | What it does |
|---|---|---|
| Menu Key `menu-key` | unbound | Opens the Schematics screen. |
| Open File Key `open-key` | unbound | Picks a schematic file from anywhere on your computer and places it. |
| Adjust Key `adjust-key` | unbound | Moves the selected placement with the mouse. |
| Rotate Key `rotate-key` | unbound | Turns the selected placement 90 degrees clockwise. |
| Mirror Key `mirror-key` | unbound | Cycles the selected placement's mirror. |

#### SpawnerESP

Highlights monster spawners by spawned mob.

`.toggle spawner-esp`

A few of its settings are made per item in the code and aren't listed here: a colour per target. The module menu shows them.

##### Settings (7)

**General**

| Setting | Default | What it does |
|---|---|---|
| Fill `fill` | off | Draw translucent filled boxes. |
| Fill Opacity `fill-opacity` | 30 % | How solid the fill is. 5 to 100 %. |
| Outline `outline` | on | Draw the box edges. |
| Line Width `line-width` | 1.5 | Edge thickness. 0.5 to 6.0. |
| Meshing `meshing` | on | Merge adjacent spawners. |

**Tracers**

| Setting | Default | What it does |
|---|---|---|
| Tracers `tracers` | off | Line to each spawner |
| Tracer Width `tracer-width` | 2.0 | Line thickness. 1.0 to 6.0. |

#### StorageESP

Highlights chests, shulkers, and other storage.

`.toggle storage-esp`

A few of its settings are made per item in the code and aren't listed here: a filter per structure. The module menu shows them.

##### Settings (17)

**General**

| Setting | Default | What it does |
|---|---|---|
| Fill `fill` | off | Draw translucent filled boxes. |
| Fill Opacity `fill-opacity` | 30 % | How solid the fill is. 5 to 100 %. |
| Outline `outline` | on | Draw the box edges. |
| Line Width `line-width` | 1.5 | Edge thickness. 0.5 to 6.0. |
| Meshing `meshing` | on | Merge adjacent storage. |

**Tracers**

| Setting | Default | What it does |
|---|---|---|
| Tracers `tracers` | off | Line to each target |
| Tracer Width `tracer-width` | 2.0 | Line thickness. 1.0 to 6.0. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Chest `chest-color` | colour | Chest + chest minecart/boat. |
| Trapped Chest `trapped-chest-color` | colour | Trapped chests. |
| Ender Chest `ender-chest-color` | colour | Ender chests. |
| Barrel `barrel-color` | colour | Barrels. |
| Shulker Box `shulker-color` | colour | Shulker boxes. |
| Hopper `hopper-color` | colour | Hopper + minecart. |
| Dispenser `dispenser-color` | colour | Dispenser + dropper. |
| Furnace `furnace-color` | colour | Furnaces + brewing stand. |
| Crafter `crafter-color` | colour | Crafter, pot, bookshelf |
| Other `other-color` | colour | Fallback. |

#### Tracers

Draws tracer lines to entities.

`.toggle tracers`

##### Settings (14)

**General**

| Setting | Default | What it does |
|---|---|---|
| Max Distance `max-distance` | 256 | Farthest entity traced; 0 is no limit. 0 to 512. |
| Ignore Invisible `ignore-invisible` | off | Skips invisible entities. |
| Target `target-point` | Feet | Where on the entity the line ends. Choices: Feet, Body, Head. |
| Line Width `line-width` | 2.0 | Tracer thickness. 2.0 to 6.0. |
| Height Line `height-line` | off | Vertical line up the entity. |

**Colors**

| Setting | Default | What it does |
|---|---|---|
| Opacity `opacity` | 100 % | Line opacity. 10 to 100 %. |
| Distance Color `distance-color` | off | Color by distance |
| Color Distance `color-distance` | 40 | Far color distance. 8 to 256. |
| Players `players-color` | colour | Player colour. |
| Animals `animals-color` | colour | Passive mob colour. |
| Water Animals `water-animals-color` | colour | Water mob colour. |
| Monsters `monsters-color` | colour | Hostile mob colour. |
| Ambient `ambient-color` | colour | Ambient mob colour. |
| Misc `misc-color` | colour | Colour for everything else. |

#### Trajectories

Shows where throws land.

`.toggle trajectories`

##### Settings (17)

**General**

| Setting | Default | What it does |
|---|---|---|
| Max Ticks `traj-ticks` | 120 | Simulation length. 20 to 1000. |
| Multishot `traj-multishot` | on | Show crossbow spread. |
| Always Show Bow `traj-always-bow` | off | Show before drawing. |
| Both Hands `traj-both-hands` | off | Show the off hand's throw as well as the main hand's. |

**Projectiles**

| Setting | Default | What it does |
|---|---|---|
| Bows `traj-bows` | on | Bows and crossbows. |
| Tridents `traj-tridents` | on | Thrown tridents. |
| Ender Pearls `traj-pearls` | on | Thrown ender pearls. |
| Throwables `traj-throwables` | on | Snowballs, eggs, bottles o' enchanting, fire and wind charges. |
| Potions `traj-potions` | on | Splash and lingering potions. |
| Fishing Rods `traj-fishing-rods` | on | Cast fishing bobbers. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Line Width `traj-line-width` | 1.0 | Trajectory line thickness. 1.0 to 8.0. |
| Color `traj-color` | colour | Trajectory line color. |

**Hit**

| Setting | Default | What it does |
|---|---|---|
| Hit Marker `traj-hit-marker` | on | Box at impact. |
| Marker Fill `traj-marker-fill` | 15 % | How solid the marker box is; 0 draws only its edges. 0 to 100 %. |
| Splash Targets `traj-splash-targets` | on | Also mark entities a splash potion would reach. |
| Hit Color `traj-hit-color` | colour | Block impact color. |
| Entity Color `traj-entity-color` | colour | Entity hit color. |

#### Viewmodel

Customize first-person hand position and animations.

**Z (Depth)** moves the item forward and back. **Size** scales it.

`.toggle viewmodel`

##### Settings (25)

**Main Hand**

| Setting | Default | What it does |
|---|---|---|
| Main Hand `main-hand` | off | Move the main hand item. |
| Z (Depth) `main-hand-scale` | 0.0 | Moves the item toward or away from the camera. -5.0 to 5.0. |
| X `main-hand-x` | 0.0 | Moves the item left or right. -5.0 to 5.0. |
| Y `main-hand-y` | 0.0 | Moves the item up or down. -5.0 to 5.0. |
| Rotation X `main-hand-rot-x` | 0.0 | Tilts the item forward or back. -50.0 to 50.0. |
| Rotation Y `main-hand-rot-y` | 0.0 | Turns the item left or right. -50.0 to 50.0. |
| Rotation Z `main-hand-rot-z` | 0.0 | Rolls the item sideways. -50.0 to 50.0. |
| Size `main-hand-size` | 1.0 | Makes the held item bigger or smaller. 0.1 to 3.0. |

**Off Hand**

| Setting | Default | What it does |
|---|---|---|
| Off Hand `off-hand` | off | Move the off hand item. |
| Z (Depth) `off-hand-scale` | 0.0 | Moves the item toward or away from the camera. -5.0 to 5.0. |
| X `off-hand-x` | 0.0 | Moves the item left or right. -1.0 to 1.0. |
| Y `off-hand-y` | 0.0 | Moves the item up or down. -1.0 to 1.0. |
| Rotation X `off-hand-rot-x` | 0.0 | Tilts the item forward or back. -50.0 to 50.0. |
| Rotation Y `off-hand-rot-y` | 0.0 | Turns the item left or right. -50.0 to 50.0. |
| Rotation Z `off-hand-rot-z` | 0.0 | Rolls the item sideways. -50.0 to 50.0. |
| Size `off-hand-size` | 1.0 | Makes the held item bigger or smaller. 0.1 to 3.0. |

**Animations**

| Setting | Default | What it does |
|---|---|---|
| Swing Duration `swing-duration` | 6 | Attack animation length (ticks). 1 to 20. |
| Air Walker `air-walker` | off | Keep the walk bob in the air. |

**Blocking Animation**

| Setting | Default | What it does |
|---|---|---|
| Blocking Animation `blocking-animation` | 1.7 | Sword animation while blocking; Off keeps vanilla. Choices: 1.7, Pushdown, Off. |
| 1.7 Y `one-seven-y` | 0.1 | How high the sword sits while blocking. 0.05 to 0.3. |
| 1.7 Swing Scale `one-seven-swing-scale` | 0.9 | How far the sword swings while blocking. 0.1 to 1.0. |

**Equip Offset**

| Setting | Default | What it does |
|---|---|---|
| Equip Offset `equip-offset` | on | Item lower/raise animation. |
| Ignore Blocking `ignore-blocking` | on | Skip the blocking offset. |
| Ignore Place `ignore-place` | on | Skip the place bump. |
| Ignore Amount `ignore-amount` | off | Skip on count change. |

#### Waypoints

Save and render named destinations.

`.toggle waypoints`

##### Settings (16)

**General**

| Setting | Default | What it does |
|---|---|---|
| Manage Waypoints `manage-waypoints` | button | Open the waypoint manager. |
| Default Color `default-color` | colour | Color for new waypoints. |

**Deaths**

| Setting | Default | What it does |
|---|---|---|
| Waypoint On Death `on-death` | on | Save a waypoint where you die. |
| Track Deaths `track-deaths` | on | Save deaths even when off. |
| Death Memory `death-memory` | 10 | Max deaths to keep. 1 to 50. |
| Death Color `death-color` | colour | Color for death waypoints. |
| Death Message `death-chat` | on | Say in chat where the death waypoint was saved. |
| Show Deaths `show-deaths` | on | Draw death waypoints in the world. |

**Render**

| Setting | Default | What it does |
|---|---|---|
| Dot `show-dot` | on | Draw the dot at each waypoint. |
| Dot Size `dot-size` | 3 | Waypoint dot size. 1 to 10. |
| Names `show-names` | on | Show each waypoint's name. |
| Show Distance `show-distance` | on | Show your distance below waypoints. |
| Text Scale `text-scale` | 1.0 | Size of the name and distance. 0.5 to 3.0. |
| Text Outline `text-outline` | on | Dark outline around the text. |
| Hide Within `hide-distance` | 2.5 blocks | Hides a waypoint once you are this close. 0.0 to 5.5 blocks. |
| Max Distance `max-distance` | 0 blocks | Hides waypoints farther than this; 0 is no limit. |

#### World

World render tweaks.

`.toggle world`

##### Settings (18)

**Block Tint**

| Setting | Default | What it does |
|---|---|---|
| Tint Blocks `darken-blocks` | on | Tint block textures. |
| Color `tint-color` | colour | Tint color. |
| Strength `darkness` | 50 | Tint strength, percent. 0 to 100. |
| Mode `mode` | Blacklist | List spares or targets. Choices: Blacklist, Whitelist. |

**Block Highlight**

| Setting | Default | What it does |
|---|---|---|
| Block Highlight `block-highlight` | off | Custom block outline. |
| Color `highlight-color` | colour | Outline color. |
| Line Width `highlight-width` | 2.0 | Outline thickness. 0.5 to 6.0. |
| Style `highlight-style` | Full | Full cube or corners. Choices: Full, Corners. |
| Fill `highlight-fill` | off | Fill the box faintly. |
| Fill Opacity `highlight-fill-opacity` | 25 % | How solid the fill is. 5 to 100 %. |
| Show Progress `highlight-progress` | on | Damage color shift. |
| Progress Start `progress-start-color` | colour | Colour when breaking starts. |
| Progress End `progress-end-color` | colour | Colour just before the block breaks. |

**Skybox**

| Setting | Default | What it does |
|---|---|---|
| Skybox `skybox` | off | Replace the sky with the DIH panorama. |
| Spin `skybox-spin` | on | Slowly spin the skybox panorama. |
| Spin Speed `skybox-speed` | 1.0 | Rotation speed. 0.5 to 3.0. |
| Theme Recolor `skybox-recolor` | off | Theme-recolor the skybox panorama. |
| Color `skybox-color` | colour | Custom panorama color. |

#### Xray

Shows selected blocks.

OreSim, its ore predictor, needs a world seed; `.oresim` explains what it's doing and `#seedinput` shares the seed with `#seedmap`.

`.toggle xray`

A few of its settings are made per item in the code and aren't listed here: a colour per ore. The module menu shows them.

##### Settings (10)

| Setting | Default | What it does |
|---|---|---|
| Mode `mode` | Normal | Real blocks or seed. Choices: Normal, OreSim. |
| Rendering Style `render-style` | Default | See-through or boxes. Choices: Default, ESP. |
| Opacity `opacity` | 25 | Hidden block alpha. 0 to 255. |
| Fluid Opacity `fluid-opacity` | Both | Visible fluids. Choices: None, Water, Lava, Both. |
| Exposed Only `exposed-only` | off | Only exposed ores. |
| Fill `fill` | on | Shade box interiors. |
| Fill Opacity `fill-opacity` | 30 % | How solid the box fill is. 5 to 100 %. |
| Line Width `line-width` | 1.5 | Box edge thickness. 0.5 to 6.0. |
| World Seed `oresim-seed` | - | Seed for this world |
| Simulation Radius `oresim-radius` | 2 | Chunk radius to simulate |

#### Zoom

Beta: 5.1 beta only, not in 5.0.1 stable.

Hold to zoom in.

`.toggle zoom`

##### Settings (12)

**General**

| Setting | Default | What it does |
|---|---|---|
| FOV `fov` | 30 deg | Field of view while zoomed. 1 to 110 deg. |
| Duration `duration` | 150 ms | Zoom-in transition time. 0 to 1000 ms. |
| Out Duration `out-duration` | 150 ms | Zoom-out transition time. 0 to 1000 ms. |
| Easing `easing` | Ease Out | Shape of the transition: fast then slow, steady, or slow at both ends. Choices: Ease Out, Linear, Smooth. |
| Hold Key `hold` | on | Only while the key is held; off makes the key toggle it. |
| Hide HUD `hide-hud` | off | Hides the HUD and hand while zoomed. |

**Scroll**

| Setting | Default | What it does |
|---|---|---|
| Scroll `scroll` | on | Scroll to zoom further in or out while held. |
| Scroll Step `scroll-step` | 3 deg | Degrees per scroll notch. 1 to 20 deg. |
| Min FOV `min-fov` | 1 deg | Narrowest view scrolling can reach. 1 to 60 deg. |
| Remember Scroll `remember-scroll` | off | Starts the next zoom at the level you scrolled to. |

**Mouse**

| Setting | Default | What it does |
|---|---|---|
| Scale Sensitivity `scale-sensitivity` | on | Slow the mouse down with the zoom. |
| Cinematic `cinematic` | off | Smooth camera while zoomed. |
