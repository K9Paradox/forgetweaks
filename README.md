# ForgeTweaks — RLUtility

Client-side utility & exploit hub for **RLCraft 2.9.3** (Minecraft 1.12.2 / Forge 14.23.5.x).

Built for **multiplayer**: the majority of modules drive real client→server packets or real container
clicks, so the server applies them exactly as if you had done it by hand. Every module in the GUI
carries a compatibility tag so you always know what actually works on a server and what is just
paint on your own screen.

| Badge | Meaning |
|-------|---------|
| `SRV` | Server-authoritative. Vanilla C2S packets / window clicks — works on any server. |
| `MOD` | Uses a mod's own network channel. The server must run that mod (every RLCraft server does). |
| `CLI` | Client-side only. Cosmetic or prediction; server state is unchanged. |
| `!!`  | Client-side **and** easily rejected or flagged by a server. Use deliberately. |

Placebo features are deleted rather than shipped: if a module cannot change the server's mind it is
not in the list (that is why there is no kill aura, no weapon-lock packet bypass and no enchant
preview anymore — each was tested, found cosmetic or rejected, and removed).

---

## Controls

| Key | Action |
|-----|--------|
| `Right Shift` | Open the RLUtility hub |
| `R` | Toggle Click Aura |
| `H` | Toggle the HUD |

All three are rebindable in *Options → Controls → RLUtility*.

Commands: `/rlu` (diagnostics, xray, id lookup, unlock/buy, race, mods), `/rlu unlock`,
`/rlu buy <skill> [levels]`, `/rlu id`, `/rlu race [list|<race> [element]]`, `/dupe`,
`/bqexploit all`, `/skill`, `/unlockall`.

---

## Menu layout

The hub follows the usual utility-client convention: each tab is a category, each module is one
toggle row, and everything that belongs to a module — boolean options, value settings, editor
buttons — is nested directly underneath it. Values are click-stepped or typed (click the number).

### Combat
- **Auto Criticals** `SRV` — packet micro-hop before every swing so the server registers a 1.5× crit.
- **Click Aura** `SRV` — one swing hits every valid target in a sphere around you. Input-driven only;
  options for range, max targets per swing, cooldown respect and player/passive/tamed filters.
- **Anti-Knockback** `SRV` — damps the velocity the server pushes onto you, with separate
  horizontal/vertical factors.

### Movement
- **Flight** `!!` — creative-style flight with adjustable speed. *Persist Through Damage* re-asserts
  flight after the ability resync damage triggers **and re-sends the abilities packet to the
  server**, so both sides agree and you neither drop out of the sky nor rubber-band.
- **No Fall** `SRV` — spoofs the on-ground flag only once the drop would actually hurt.
- **Anti-Kinetic** `SRV` — stops wall-impact damage entirely, see below.
- **Step Assist** `SRV`, **Water Walk** `SRV`, **No Slowdown** `SRV`.
- **Timer** `!!` — client tick multiplier; fastest way to get flagged, auto-resets on disconnect.

### Survival
- **Auto Armor** `SRV`, **Auto Bandage** `MOD` (First Aid channel), **Auto Totem** `SRV`,
  **Auto Eat** `SRV` (configurable hunger threshold), **Auto Hydrate** `MOD` (SimpleDifficulty
  channel), **Auto Respawn** `SRV`.
- **Item Magnet** `MOD` — pulls nearby drops (authoritative with ItemPhysic), with radius/speed
  settings, whitelist/blacklist editors and an "only my drops" filter. Hard-stops while you are dead
  so it never drags loot into your corpse.
- **Debuff Neutralizer** `CLI` — strips screen-shake and nuisance debuff render.

### Exploits
- **Auto Lockpick** `MOD` — audio-pitch solver for Locks tumblers via the mod's own packets.
- **Auto Reforge** `MOD` — re-rolls QualityTools / Bountiful Baubles until the target quality lands.
- **Auto Loot** `SRV` — empties open containers with real shift-clicks; optional auto-close, delay setting.
- **Fast Mine** `SRV` — two modes: **Fast** (zero break delay, NoTreePunching penalty undone) and
  **Instant** (block progress completes every tick; vanilla servers trust the finish packet).
- One-shot actions: **Quest Sweep** (BetterQuesting), **Trinkets: Set Race**, **Desync Dupe**.

### Skills
- **Client Lock Bypass** `CLI` — Reskillable runs on your client too and cancels mining/interaction
  locally before any packet leaves; this reverts that cancel so locked tools at least reach the
  server. Includes the **Unlock Held Item** one-click XP purchase.
- **Auto-Buy Levels** `MOD` — spends XP automatically on whatever the held item needs, with a reserve.
- **Level Up! 2** section — skill tree editor, free class change, target level, apply-all, safe preset,
  plus *Preserve Class* / *Clamp Levels* safety toggles.

### Visuals
Chest / spawner / waystone / boss / hostile / player / item / modded-mob / all-container ESP,
custom block & entity lists (dragon skulls included), tracers, per-category render styles, wireframe
**Model Outlines** with configurable thickness, and **XRay** with its own editable block list.
The *ESP Categories & Styles* table edits enable/style/colour for every category in one place.

### HUD
Master switch with watermark, active-module list, FPS/ping/coords line and a target-info readout.

### Tools
Manual save (reports the exact path it wrote) and a config-file locator.

---

## Anti-Kinetic: why this one is a real fix

RLCraft ships the **Collision Damage** mod. Reading its source
([fonnymunkey/CollisionDamage](https://github.com/fonnymunkey/CollisionDamage)) shows the entire
damage pipeline is client-reported:

```java
// client, every tick:
double accel = prevMotionCombined - curMotionCombined;
if (accel > 5 && player.collidedHorizontally)
    sendToServer(new PacketCollisionS(accel));   // "I hit a wall this hard"

// server, on that packet:
player.attackEntityFrom(flyIntoWall-or-fall, (accel - threshold) * 4 * multiplier);
```

The server never measures anything itself — it trusts the number you send. RLUtility keeps the mod's
own `prevMotionCombined` snapshot pinned to the current speed every tick, so the mod always computes
an acceleration of 0 and the packet never goes out. No packet, no damage, nothing to detect. This is
the same immunity the **Stone of Inertia Null** grants (its trinket ability cancels the resulting
`LivingFallEvent` server-side), minus needing the drop.

Vanilla elytra flight has a separate, genuinely server-computed impact damage based on how much
horizontal speed is lost inside a single tick. That one cannot be cancelled from the client, so for
it the module sweeps your bounding box along the flight path and brakes smoothly, arriving at the
wall below the loss threshold.

## The Reskillable sword problem

Mining tools work but swords do nothing, with a red warning on screen. That warning is the proof:
`LevelLockHandler.tellPlayer` is guarded by `if (player instanceof EntityPlayerMP)` and sends you a
`MessageLockedItem` packet **from the server**. Weapons are enforced through `LivingAttackEvent`
server-side, against the server's own copy of your skill levels. Every client-side packet path
tested against it (RLCombat hook, Spartan, Ice & Fire, raw `CPacketUseEntity`) was rejected — so
those "bypasses" were removed instead of being shipped.

**The real fix** is to raise the levels on the server with Reskillable's own `MessageLevelUp`
packet. The server validates the XP cost where we cannot reach it, so there is no free path — but
the packet carries nothing but a skill name, so we can drive it as fast as we like:

- `/rlu unlock` — buys the levels the item in your hand is missing
- `/rlu buy <skill> [levels]` — buys levels directly
- **Auto-Buy Levels** (Skills tab) — does it automatically when you hold a locked item
- **XP Reserve** — never spends below N levels

This costs real XP, but it is permanent and server-side: the sword actually works afterwards.

## Desync dupe

`/dupe` runs a guided three-step flow. It exploits the same `saveNBTData` NPE described below:
injecting a skill key that no skill is registered under makes `PlayerExtension.saveNBTData` throw
while serialising your capability, and vanilla's `SaveHandler.writePlayerData` catches `Exception`
and merely logs "Failed to save player data". Your player file freezes; chunk data (chests) keeps
saving.

The ordering matters, which is why the command insists on it:

1. `/dupe` tells you to relog. **This is the point of the step** — the rollback target is your last
   *successful* save, not the moment you armed. Relogging forces a clean one.
2. On rejoin it arms automatically, injecting only the poison key and leaving all 26 real skill
   levels untouched.
3. Bank your items in a chest, disconnect, rejoin.

There is no in-session disarm, and `/dupe cancel` says so honestly: the skill packet only ever
*writes* keys, never deletes them, and that key is what blocks the save. Rejoining is the cure,
because the corrupt map was never written to disk.

## XRay and ESP

XRay highlights blocks through terrain rather than hiding terrain — the visible result people
actually want, with no coremod, no mixin and no chunk re-mesh. It walks `ExtendedBlockStorage`
sections directly and skips empty ones, which is what makes a 28-block radius affordable; scans are
throttled and re-run when you cross a chunk border, and results are capped at 4000.

Every list is editable in game with the same picker (**Add looked-at**, **Browse all**, **Type an
id** with wildcards like `iceandfire:*`). The same editor drives ESP entities, ESP blocks, XRay
blocks, and the item magnet's whitelist/blacklist — each editor is nested under the module it
belongs to.

## Level Up! 2 exploit — correctness notes

Verified against [BeetoGuy/LevelUp2](https://github.com/BeetoGuy/LevelUp2) rather than guessed at:

1. **The server's read loop is fixed length.** It reads exactly
   `SkillRegistry.getSkillRegistry().size()` name/int pairs. We iterate the registry list itself so
   the count and order always line up.
2. **Unknown skill names corrupt your save.** `setSkillLevel` is a raw `map.put`, so a bogus name
   NPEs later in `PlayerExtension.saveNBTData`. Only registry-backed names are ever transmitted.
3. **Unconfigured skills defaulted to MAX.** The default is now your *current* level, so editing one
   skill no longer silently maxes the rest of the tree.

Two further footguns are options in the Skills tab: **Preserve Class** (the three `*_bonus` skills
double as the specialization flag) and **Clamp Levels** (some skills index tables by level).

---

## Building

### Windows (one command)

```bat
git clone -b arena/01a05a75-forgetweaks https://github.com/K9Paradox/forgetweaks.git && cd forgetweaks && build.bat
```

Already have the repo? Just `git pull && build.bat` inside it.

`build.bat` finds a JDK 8, downloads a private copy of Gradle 4.10.3 into
`%USERPROFILE%\.rlutility-build` and auto-detects your RLCraft mods folder. If it guesses wrong,
pass the folder explicitly:

```bat
build.bat "C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods"
```

Output: `build\libs\rlutility-1.5.0.jar` — drop it into that same mods folder.

**Requirements.** ForgeGradle 2.3 only runs on **JDK 8** (Java 9+ will fail with cryptic bytecode
errors) and Gradle 4.x. The script handles Gradle; if you have no JDK 8 it will tell you and stop:

```bat
winget install EclipseAdoptium.Temurin.8.JDK
```

The first build decompiles Minecraft and takes 5–15 minutes. Later builds take seconds.

### Manual / other platforms

```bash
gradle build -x test -PrlcraftMods="/path/to/RLCraft/minecraft/mods"
```

The RLCraft mod jars are compile-time only dependencies (this mod links against Level Up! 2,
Reskillable, Locks, First Aid and others). You can also drop them in `./libs` instead of passing the
flag. `gradle checkRlcraftDeps` lists which ones are missing.

---

## What changed in 1.5.0

- **Menu refactor.** Modules, their options and their editors are now nested instead of floating in
  flat walls of similarly named rows; every tab follows the same convention.
- **Anti-Kinetic is a real fix.** Suppresses the client-reported impact packet the Collision Damage
  mod trusts (the Stone of Inertia Null trick), and brakes elytra flights smoothly before walls.
- **Flight persist no longer jitters.** The restore now re-sends the abilities packet to the server
  instead of fighting it client-side only.
- **Fast Mine gained an Instant mode** — and the reflection now targets the correct field
  (`field_78781_i`), so the module actually works at runtime.
- **Removed:** enchantment preview, packet weapon-lock bypasses and every other toggle that testing
  proved cosmetic. Locked weapons are solved by buying levels, not by pretending.

---

## Disclaimer

This is a cheat client. Using it on a server you do not own will get you banned, and most of it is
against the rules of any public RLCraft server. Use it on your own worlds and your own servers.
