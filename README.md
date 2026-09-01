# ForgeTweaks — RLUtility

Client-side utility & exploit hub for **RLCraft 2.9.3** (Minecraft 1.12.2 / Forge 14.23.5.x).

Built for **multiplayer**: the majority of modules drive real client→server packets or real container
clicks, so the server applies them exactly as if you had done it by hand. Every module in the GUI
carries a compatibility badge so you always know what actually works on a server and what is just
paint on your own screen.

| Badge | Meaning |
|-------|---------|
| `SRV` | Server-authoritative. Vanilla C2S packets / window clicks — works on any server. |
| `MOD` | Uses a mod's own network channel. The server must run that mod (every RLCraft server does). |
| `CLI` | Client-side only. Cosmetic or prediction; server state is unchanged. |
| `!!`  | Client-side **and** easily rejected or flagged by a server. Use deliberately. |

---

## Controls

| Key | Action |
|-----|--------|
| `Right Shift` | Open the RLUtility hub |
| `R` | Toggle Kill Aura |
| `H` | Toggle the HUD |

All three are rebindable in *Options → Controls → RLUtility*.
Commands: `/rlu`, `/rlmenu`, `/rlgui`, `/rlu level <n>`, `/rlu tree <mining|crafting|combat> <n>`,
`/rlu safe`, `/rlu max`.

---

## Modules

### Combat
- **Auto Criticals** `SRV` — packet micro-hop before the swing so the server registers a 1.5× crit.
- **Kill Aura** `SRV` — closest-to-crosshair targeting, silent rotation packets, wall check, tamed-pet
  and villager protection, and a configurable range/CPS. Honours the vanilla attack cooldown so every
  hit lands at full RLCraft damage, and fires the crit hop when Auto Criticals is on.
- **Anti Knockback** `SRV` — damps the velocity the server pushes onto you (movement is
  client-authoritative, so this is real, not cosmetic). Separate horizontal/vertical strength.
- **Auto Totem** `SRV` — hot-swaps a Totem of Undying into the off-hand below 3.5 hearts.
- **Auto Armor** `SRV` — rates every armour piece (protection, toughness, enchants, durability) and
  equips the best one. Skips curse-of-binding and nearly-broken gear.
- **Auto Eat** `SRV` — eats real food at a configurable hunger level, never touches rotten flesh,
  spider eyes, pufferfish or raw meat, and restores your previous hotbar slot afterwards.
- **Auto Respawn** `SRV` — sends the respawn packet the moment the death screen appears.
- **FirstAid Auto-Triage** `MOD` — applies bandages/plasters to wounded limbs through FirstAid's channel.
- **Nunchaku Triggerbot / Level Damage Bypass** `MOD` — Better Survival spin loop plus the direct
  reach-attack packets of RLCombat, Spartan Weaponry, Ice & Fire and Trinkets & Baubles.

### Movement
- **No Fall** `SRV` — spoofs the on-ground flag *only once the drop would actually hurt*, and steps
  aside during a crit hop so it no longer cancels Auto Criticals.
- **Step Assist** `SRV`, **Water Walk** `SRV` (sneak to drop in), **No Slowdown** `SRV`.
- **Timer** `!!` — client tick multiplier (0.5×–3.0×). Fastest way to get flagged; capped and
  auto-reset on disconnect.
- **Creative Flight** `!!` — vanilla servers kick for this.

### Exploits
- **Level Up! 2 skill exploit** `MOD` — writes any skill level for free. The server handler
  (`SkillPacketHandler#handlePacket`) takes a client-supplied `levelSpend` and calls
  `properties.setSkillLevel(name, value)` — a raw `HashMap.put` with **no cost check and no level
  validation** — then syncs the result back to you. We send `button = -1`, `levelSpend = 0`.
  Includes a **free class change** (`levelupclasses` only charges `reclassCost` when the *client*
  asks it to, so we send `reclass = false`) and a post-send verification that tells you whether the
  server actually accepted.
- **Auto Lockpick** `MOD` — audio-pitch + minimax-entropy solver for `melonslise.locks` 3.0.0.
  Handles 11-pin master locks, replays known prefixes at one pin per tick, and deduces the final pin
  with zero guesses.
- **Auto Reforge** `MOD` — re-rolls QualityTools / Bountiful Baubles until the chosen quality lands.
- **Auto Loot** `SRV` — empties any chest, barrel, shulker or dungeon container using ordinary
  shift-click packets, with a configurable delay and optional auto-close. Crafting grids, furnaces,
  anvils and the mod GUIs this mod already automates are skipped.
- **Fast Mine** `SRV` — clears the block hit delay and undoes NoTreePunching's penalty.
- **Auto Hydrate** `MOD`, **Item Vacuum** `MOD` (authoritative only with ItemPhysic).
- **Debuff Neutralizer** `CLI`, **Reskillable Bypass** `CLI`.

### Visuals
Chest / spawner / waystone / boss / hostile / player / item ESP, plus batched **tracers** and a
configurable draw distance. Rendering is distance-culled and snapshots the world lists, so it no
longer risks a `ConcurrentModificationException` on chunk load.

### HUD
Watermark, active-module array list (colour-coded by compatibility badge), FPS/ping/coords line and
a live Kill Aura target readout with a health bar.

### Tools
Reforge target cycle, Level Up! 2 target level, "apply to all skills", safe preset, per-skill tree
editor and a manual config save.

---

## Building

### Windows (one command)

```bat
git clone -b arena/01a05a75-forgetweaks https://github.com/K9Paradox/forgetweaks.git && cd forgetweaks && build.bat
```

`build.bat` finds a JDK 8, downloads a private copy of Gradle 4.10.3 into
`%USERPROFILE%\.rlutility-build` and auto-detects your RLCraft mods folder. If it guesses wrong,
pass the folder explicitly:

```bat
build.bat "C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods"
```

Output: `build\libs\rlutility-1.4.0.jar` — drop it into that same mods folder.

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

## Level Up! 2 exploit — correctness notes

Verified against [BeetoGuy/LevelUp2](https://github.com/BeetoGuy/LevelUp2) rather than guessed at.
Three constraints the previous implementation violated:

1. **The server's read loop is fixed length.** It reads exactly
   `SkillRegistry.getSkillRegistry().size()` name/int pairs — not "until the buffer runs out". We now
   iterate the registry list itself so the count and order always line up.
2. **Unknown skill names corrupt your save.** `setSkillLevel` is a raw `map.put`, so a bogus name is
   inserted happily — and then `PlayerExtension.saveNBTData` walks the key set and calls
   `skill.getSkillType()` on the `null` registry lookup, NPE-ing while saving your player data. Only
   registry-backed names are ever transmitted now.
3. **Unconfigured skills defaulted to MAX.** The old `getOrDefault(name, skill.getMaxLevel())` meant
   editing one skill silently maxed every other one — so `/skill mining_speed 5` maxed your whole
   tree. The default is now your *current* level, which makes the editor and the `/skill` command
   behave the way they read.

Two further footguns are now options (Tools tab):

- **Keep Class** (default on) — `levelup:mining_bonus` / `craft_bonus` / `combat_bonus` double as the
  specialization flag, so "max everything" used to set all three at once, a state the mod can never
  produce. Turn it off deliberately if you want all three XP bonuses simultaneously.
- **Cap** (default on) — several skills index tables by level, so overshooting `getMaxLevel()` can
  throw server-side.

## What changed in 1.4.0

- **Dedicated-server safety.** `FeatureConfig` no longer references `Minecraft`, the config path comes
  from Forge's config directory, and *all* handlers are registered from `ClientProxy`. The jar can no
  longer crash a server on class-load; it is also marked `clientSideOnly`.
- **Auto Criticals actually works again.** No Fall used to zero `fallDistance` every tick at
  `HIGHEST` priority, silently cancelling every crit. There is now a crit window both modules respect.
  The crit hop also fired twice per swing (once too late, from `AttackEntityEvent`); it is now sent
  once, from the mouse event, before the attack packet.
- **No Fall is far quieter.** It used to send a grounded packet on *every* tick with
  `motionY < -0.3`; it now only fires past the damage threshold and leaves elytra alone.
- **No Slowdown no longer clobbers your movement-speed attribute** (it was writing a base value that
  RLCraft, Level Up! and Reskillable all modify, causing a permanent desync).
- **Config is reflective.** Every public static field is persisted automatically — no more
  20-line copy-paste per option, and no more options silently missing from `saveConfig`.
- **New GUI**: category rail, live search, scrolling list, inline numeric settings, description
  footer and per-module server-compatibility badges.
- **Portable build**: RLCraft jars resolve from `./libs`, `-PrlcraftMods=...` or `$RLCRAFT_MODS`
  instead of a hard-coded Windows path.

---

## Build

Requirements: Java 8 JDK (Adoptium recommended), Gradle 4.9 / ForgeGradle 2.3.

Put the RLCraft mod jars listed in `build.gradle` into `./libs`, or point the build at your instance:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
gradle checkRlcraftDeps                      # report any missing dependency jars
gradle build -x test -PrlcraftMods="C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods"
```

Output: `build/libs/rlutility-1.4.0.jar`.

```powershell
Copy-Item build\libs\rlutility-1.4.0.jar `
  "$env:APPDATA\PrismLauncher\instances\RLCraft\minecraft\mods\" -Force
```

---

## Disclaimer

This is a cheat client. Using it on a server you do not own will get you banned, and most of it is
against the rules of any public RLCraft server. Use it on your own worlds and your own servers.
