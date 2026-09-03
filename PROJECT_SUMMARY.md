# RLUtility — Project State Summary

## 1. Overview
- **Mod id / name**: `rlutility` / RLUtility — version **1.7.0**
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
    ├── HudOverlay          watermark, module array list, stats, SD thirst/temp, target info
    ├── GuiEspConfig        per-category ESP colour + style matrix
    ├── GuiTargetListEditor generic add/remove list editor (ESP custom, magnet, XRay)
    └── GuiLevelUpConfig    per-skill Level Up! 2 editor
```

**Adding a module** is three steps:
1. add a `public static boolean` (and any numeric settings) to `FeatureConfig` — persistence is automatic;
2. add one `f(...)` line in `FeatureRegistry` with its `Compat` tag. Options owned by the module use
   `sub(..., parent, ...)` / `s(..., group, ...)` / `num(..., group, ...)` and the menu nests them
   under the module automatically (declaration order preserved via `FeatureRegistry.optionsOf`);
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

## 5. Level Up! 2 exploit (`LevelUpExploitHandler`)
Channel `levelupskills`, payload `byte button | int levelSpend | (UTF8 name, int level) * registrySize`.

- `button == -1` selects the server's "apply this skill map" branch.
- `levelSpend == 0` skips `player.removeExperienceLevel(...)` entirely (`if (levelSpend > 0)`).
- `IPlayerClass.setSkillLevel` is an unvalidated `HashMap.put`, so any level is accepted.
- `SkillRegistry.loadPlayer` then pushes the result back on `levelupinit`, which is how the
  post-send verification confirms whether the server accepted.

Channel `levelupclasses`, payload `byte specialization | boolean reclass`. `reclass` is client
supplied and gates the XP charge, so `false` is a free respec.

Hard constraints: the read loop is exactly `registry.size()` pairs, and every name must exist in the
registry or `PlayerExtension.saveNBTData` NPEs on the next save.

## 6. Notable changes in 1.7.0 — Ice and Fire packet attack surface

Research into the Recurrent Complex admin exploit (patched in ReC 1.4.8.7, CF file 8562927)
showed the bug class is "server trusts a client packet", and Ice and Fire **1.8.4 — the exact
build RLCraft 2.9.3 ships** — has several of the same. Audited against the `1.8.4-1.12.2`
source branch. All five new modules send real packets on Ice and Fire's own `iceandfire`
channel (LLibrary `@NetworkWrapper`, channel = mod id); `IceAndFireHelper` resolves the mod's
live `SimpleNetworkWrapper` and constructs its real message classes reflectively, so
`sendToServer` routes by class and no wire format is hand-rolled. Everything is
server-authoritative (`Compat.MODDED`), no Ice and Fire classes are referenced at compile time.

| Module | Packet (disc) | What the unvalidated handler does |
|---|---|---|
| IaF Longshot | `MessagePlayerHitMultipart` (13) | Server runs `attackTargetEntityWithCurrentItem` on the client-named entity within 100 blocks → real ranged melee hit (damage, enchants, sweep). We swing locally too so cooldown meters stay in step. |
| IaF Execute | `MessageMultipartInteract` (10) | Server applies `attackEntityFrom(causeMobDamage(player), dmg)` with `dmg` read straight from the packet → arbitrary damage at up to 100 blocks. |
| IaF Gorgon Gaze | `MessageStoneStatue` (3) | Server toggles petrification on any entity by id; the ONLY check is a gorgon head in the main hand — no distance limit. Unpetrify sub-mode included. |
| IaF Siren Silencer | `MessageSirenSong` (8) | Flips any siren's singing flag off, no range limit → sirens in radius stop charming everyone. Re-asserted every 2 s. |
| IaF Mount Hijack | `MessageStartRidingMob` (19) | `player.startRiding` on any `ISyncMount + EntityTameable` by id, no ownership check. Outcome still depends on the mob's own `canBeRidden` — labelled experimental. |

Supporting pieces: `IceAndFireHelper` also contains the 1.12 entity crosshair ray-cast
(`entityOnCrosshair`: look-vector ray vs border-grown entity AABBs with block occlusion, since
vanilla `objectMouseOver` only reaches ~4.5 blocks) and `nearbyByClassName` for the siren scan.

Other 1.8.4 messages audited and rejected: `MessageDragonControl` is owner-checked; pixie
house/jar/podium handlers are empty server-side; `MessageGetMyrmexHive` (client-writable hive
data) and hippogryph armor toggling are noted in `PACKET_AUDIT_FINDINGS.md` for a later round.

## 6a. Notable changes in 1.6.0
- **Auto Bandage fixed**: the client cannot see the server's `part.activeHealer`, so the old
  "is this part already treated" check always passed and a fresh `MessageApplyHealingItem` went
  out every cycle — each one *replacing* the server-side healer and resetting its heal timer to
  zero. Items were consumed, nothing ever healed. FirstAidHelper now keeps its own treatment
  ledger (45 s window per part, cleared early when synced health reaches max) and only re-applies
  when a healer genuinely expired. Morphine is no longer treated as a healer (it has none
  registered server-side).
- **Auto Totem fixed**: the old trigger was `health <= 7`, but First Aid kills via head/body part
  health while the averaged vanilla bar still shows plenty. Now: configurable threshold (default
  12) **or** a critical head/body wound (`FirstAidHelper.hasCriticalWound`), polled every tick
  plus on hurt events. Shared static `FastTriageHandler.tryEquipTotem` so the two handlers cannot
  click-fight.
- **Flight persist is built-in** (setting removed). A netty tap watches incoming
  `SPacketPlayerAbilities`: a "flying=false" packet that lands while the client is still flying is
  a server-side strip (damage/arrow resyncs) → restore + 3-second retry window. A double-jump
  toggle turns flight off locally first, so its confirmation packet is ignored and manual
  toggling works again. If the strip also says `allowFlying=false`, it is reported once in chat
  and the client stops fighting it (No Fall then covers the descent).
- **Auto Bandage also resyncs**: First Aid never syncs healer progress to the client (part updates
  only ship on damage), so the client HUD kept showing old low part hearts while the server healed
  — and the ledger thought parts were still wounded. The handler now sends First Aid's own
  `MessageClientRequest(REQUEST_REFRESH)` after each application and periodically during treatment.
- **Auto Hydrate rebuilt around the server's real trace**: the old client gate used
  `Entity#rayTrace`, which cannot see fluids (no collision box) — so it never drank. Now mirrors
  `world.rayTraceBlocks(..., stopOnLiquid=true)` at reach/2 plus the rain-drink check, and also
  auto-uses carried drinks (juice / purified bottles / canteens) via `CPacketPlayerTryUseItem`.
- **Auto Totem default threshold lowered to 6 HP** — 12 kept the totem parked in the off-hand
  permanently.
- **Auto-Buy Levels fixed**: requirements are read through the real API
  (`RequirementHolder.getRequirements()` → `SkillRequirement`); the old reflection looked for a
  Map field that does not exist, so it always reported "no requirements".
- **ESP model outlines fixed (white outlines)**: `doRender` resets GL colour internally, so
  colour-then-doRender always drew white. ModelOutlineHandler now renders
  `getMainModel().render(...)` directly with vanilla's exact transform sequence — including the
  `translate(0, -1.501F, 0)` from `prepareScale` that lifts flipped model space onto the entity's
  feet (missing it sat the outline ~1.5 blocks too low) — keeping our colour; entities without a
  readable model fall back to a coloured box, and style 4 with Model Outlines switched off now
  falls back to boxes instead of nothing.
- **No Fall gate widened**: the on-ground spoof no longer requires negative client velocity, so it
  still covers descents where the client and server disagree about flight.
- **Siren Guard (new)**: Ice and Fire sirens pull server-side, so they cannot be deleted
  client-side; the guard auto-equips earplugs (the real fix — server releases the charm) or
  auto-runs away from the siren when none are available (sprint speed beats the pull blend).
- **Reach (new)**: raises `PlayerControllerMP.blockReachDistance` (name + value-scan lookup,
  re-applied every tick, reset on disconnect). Vanilla 1.12 servers do not verify reach.
- **Item Magnet: "Ignore My Drops"** — inverse of "Only My Drops" via `EntityItem.getOwner()`.
- **Auto Hydrate: "Safe Water Only"** — mirrors the server ray-trace locally, only drinks dirty
  water in a thirst emergency (dirty = 75 % Thirsty + parasite roll, both server-side and
  unspoofable). HUD gained a colour-coded W/T readout of the synced thirst/temperature values.
- **Timer removed** (did not work; user does not want it).
- **Dead ends documented** (researched against pack sources, do not retry): QualityTools quality
  is rolled and stored server-side NBT — no quality packet to spoof; Trinkets & Baubles ring
  effects are server-applied potion/BreakSpeed — no C2S ring packets to edit; SimpleDifficulty
  temperature is computed and enforced server-side with no C2S channel — warmth cannot be
  spoofed; drinking contamination is server RNG.

## 6b. Notable changes in 1.5.0
- **Menu refactor**: modules own their options. Sub-features (`Feature.parent`), settings
  (`Setting.group`) and editor buttons nest under their module; standalone clusters (Level Up! 2,
  one-shot exploits) get explicit section rows.
- **Anti-Kinetic is authoritative**: RLCraft's Collision Damage mod deals its damage from a
  client-reported acceleration packet (`PacketCollisionS`); we pin its `prevMotionCombined`
  snapshot to the current speed every tick so the packet never goes out. Elytra impacts get a
  smooth rate-limited brake (arrive below the vanilla loss threshold).
- **Flight persist re-sends `CPacketPlayerAbilities`** so the server agrees flight is on — the old
  client-only re-assertion desynced and rubber-banded (the "jittery" bug).
- **Fast Mine modes**: Fast (delay 0 + BreakSpeed restore) and Instant (progress completed every
  tick). Reflection fixed to `field_78781_i` / `field_78770_f` — the old `field_78779_k` does not
  exist, so the module previously did nothing at runtime.
- **Removed as placebo**: enchantment preview, packet weapon-lock bypasses, level damage bypass.
  Click Aura now dispatches plain vanilla attacks.

## 6c. Notable fixes in 1.4.0
- Dedicated-server crash paths removed (`Minecraft` reference in config, client handlers registered
  from the common entry point).
- `NoFall` no longer cancels `AutoCriticals` (shared `AutoCritHandler.critWindow`).
- Crit hop sent once, before the attack packet, instead of twice with half of it too late.
- `NoSlowdown` no longer overwrites the `MOVEMENT_SPEED` attribute base value.
- ESP snapshots the world lists (no more CME risk) and is distance-culled.
- `build.gradle` no longer hard-codes a Windows path for the RLCraft dependency jars.

## 7. Deployment
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
gradle build -x test -PrlcraftMods="C:\Users\K9\AppData\Roaming\PrismLauncher\instances\RLCraft\minecraft\mods"
Copy-Item build\libs\rlutility-1.6.0.jar "$env:APPDATA\PrismLauncher\instances\RLCraft\minecraft\mods\" -Force
```
