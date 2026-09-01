# RLUtility — Project State Summary

## 1. Overview
- **Mod id / name**: `rlutility` / RLUtility — version **1.4.0**
- **Target**: Minecraft 1.12.2, Forge 14.23.5.x, Java 8, RLCraft **2.9.3**
- **Scope**: client-side only (`clientSideOnly = true`, `acceptableRemoteVersions = "*"`), designed so
  that as much as possible is *server-authoritative* and therefore usable in multiplayer.

## 2. Architecture

```
com.rlutility
├── RLUtilityMod            @Mod entry point. preInit -> FeatureConfig.init(configDir)
├── proxy
│   ├── CommonProxy         no-op (dedicated server never loads a client class)
│   └── ClientProxy         keybinds, commands, and ALL event-handler registration
├── modules
│   ├── FeatureConfig       reflective persistence of every public static field
│   ├── Feature             {name, desc, Category, Compat, getter, setter}
│   ├── FeatureRegistry     single source of truth for the GUI + HUD
│   └── *Handler / *Helper  one module each
└── gui
    ├── GuiUtilityMenu      custom-drawn hub: rail + search + scroll list + settings
    ├── HudOverlay          watermark, module array list, stats, target info
    └── GuiLevelUpConfig    per-skill Level Up! 2 editor
```

**Adding a module** is three steps:
1. add a `public static boolean` (and any numeric settings) to `FeatureConfig` — persistence is automatic;
2. add one `f(...)` line (and optional `s(...)` setting) in `FeatureRegistry` with its `Compat` tag;
3. register the handler in `ClientProxy.init()`.

## 3. Compatibility taxonomy
`Feature.Compat` drives the badge shown in the GUI and HUD:

| Value    | Badge | Meaning |
|----------|-------|---------|
| `SERVER` | `SRV` | Vanilla C2S packets / window clicks — authoritative on any server |
| `MODDED` | `MOD` | Mod network channel — server must run that mod |
| `LOCAL`  | `CLI` | Client-only, cosmetic/prediction |
| `RISKY`  | `!!`  | Client-only and easily rejected/flagged |

## 4. Lock Solver (`LocksHelper`) — unchanged
Targets `melonslise.locks` 3.0.0.
1. Locks are strict permutations of `{0..N-1}`; master/diamond locks are 11 pins (11! ≈ 39.9M).
2. Audio feedback: `pin.match` (pitch 1.0) confirms a pin and removes it from every other slot's
   candidate set; `pin.fail` at pitch 1.25 means `|actual - guess| <= 1`, pitch 1.0 means distant.
3. Pick breaks send `CheckPinResultPacket(false, true)` with no fail sound — detected via a drop in
   the lifted-pin count.
4. Optimisations: known prefix replayed at one pin per tick, minimax entropy probe selection, and the
   final pin deduced with zero guesses.

## 5. Notable fixes in 1.4.0
- Dedicated-server crash paths removed (`Minecraft` reference in config, client handlers registered
  from the common entry point).
- `NoFall` no longer cancels `AutoCriticals` (shared `AutoCritHandler.critWindow`).
- Crit hop sent once, before the attack packet, instead of twice with half of it too late.
- `NoSlowdown` no longer overwrites the `MOVEMENT_SPEED` attribute base value.
- ESP snapshots the world lists (no more CME risk) and is distance-culled.
- `build.gradle` no longer hard-codes a Windows path for the RLCraft dependency jars.

## 6. Deployment
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
gradle build -x test -PrlcraftMods="C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods"
Copy-Item build\libs\rlutility-1.4.0.jar "$env:APPDATA\PrismLauncher\instances\RLCraft\minecraft\mods\" -Force
```
