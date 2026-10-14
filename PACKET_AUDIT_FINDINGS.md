# RLCraft 2.9.3 packet audit — interim findings (post-ReC)

Hunt for more "server blindly trusts client" oversights like the Recurrent Complex one.
Method: clone each mod's exact RLCraft-era source, enumerate all Side.SERVER packet handlers,
read for missing validation. Status as of 2026-09-03.

## CONFIRMED EXPLOITABLE

### Ice and Fire — RLCraft actually pins **1.7.1** (CF file 2693547), not 1.8.4

> **1.7.1 correction (verified against the real source):** The first five modules shipped in
> 1.7.0 were audited against 1.8.4 and came out inert in-game. Re-auditing the build RLCraft
> 2.9.3 actually ships (IaF **1.7.1**, March 2019) showed: `MessageStartRidingMob` does not
> exist in 1.7.1 (mount hijack impossible — replaced by Dragon Smith), `MessagePlayerHitMultipart`
> carries only `creatureID` (no `extraData`), `MessageStoneStatue` checks no held item at all,
> and `MessageDragonArmor(dragonId, armor_index, armor_type)` exists with **no ownership check
> and no item consumed** (`setArmorInSlot` recomputes the ARMOR attribute server-side).
> Messages 1-3, 6, 7 below keep their conclusions in 1.7.1 (no range checks in the 1.7.1
> handlers either); 4, 5, 8, 9 were re-read in 1.8.4 only — treat their details as
> version-specific. Discriminator numbers are NOT stable across versions and must not be relied
> on; the shipped helper routes by message class through the mod's own wrapper.

Channel: LLibrary `@NetworkWrapper` on `IceAndFire.NETWORK_WRAPPER` (channel name `iceandfire`).
All messages below are C2S with NO permission check; most have NO range/ownership checks.

| # | Message | Vulnerability | Impact |
|---|---|---|---|
| 1 | `MessageMultipartInteract(int creatureID, float dmg)` | Server does `mob.attackEntityFrom(DamageSource.causeMobDamage(player), message.dmg)` for any `EntityLivingBase` within **100 blocks**; `dmg` is fully client-controlled (readFloat) | **Arbitrary ranged damage** — one-shot any player/boss/dragon remotely; kill credit goes to attacker |
| 2 | `MessagePlayerHitMultipart(creatureID, extraData)` | Server runs `player.attackTargetEntityWithCurrentItem(mob)` on any entity within **100 blocks** | **Ranged melee hit** — real weapon attack (damage + enchants + sweep) at 100 blocks, bypasses all reach |
| 3 | `MessageStoneStatue(entityId, isStone)` | Only requirement: gorgon head in main hand. Sets `StoneEntityProperties.isStone` on ANY entity by ID, any range | Remote petrify/un-petrify of players, bosses, mounts (statues can be mined/moved) |
| 4 | `MessageHippogryphArmor(dragonId, slot_index, armor_type)` | No ownership check: toggles saddle/chested/armor of ANY hippogryph/hippocampus by ID | Grief others' mounts; potentially grant armor states without items (`setArmor(int)` — needs verification whether it grants stats) |
| 5 | `MessageStartRidingMob(dragonId, ride)` | No ownership/tame check: `player.startRiding(entity)` on any `ISyncMount + EntityTameable` | Mount hijack attempts (vanilla `canBeRidden` may partially gate — needs per-entity check) |
| 6 | `MessageGetMyrmexHive(hive)` | Server finds hive by client UUID, then `serverHive.readVillageDataFromNBT(client tag)` | Overwrite/corrupt ANY Myrmex hive's data → hive griefing/destruction |
| 7 | `MessageSirenSong(sirenId, isSinging)` | Toggles singing of ANY siren by ID, any range | Defensive: silence sirens remotely (no more charm/drag); offensive: toggle chaos |
| 8 | `MessageDragonSyncFire` / `MessageDragonSetBurnBlock` | `stimulateFire(x,y,z)` / set `burningTarget` on ANY dragon by ID | Directed dragon fire at arbitrary position |
| 9 | `MessageAddChainedEntity` / `MessageRemoveChainedEntity` | Chain/unchain any two entities by ID | Chain griefing / weird movement interactions |

Note: `MessageDragonControl` IS owner-checked (`isOwner`), pixie/podium/myrmex-null handlers are empty no-ops.

### ItemPhysic Full (CreativeMD)
| # | Packet | Vulnerability | Impact |
|---|---|---|---|
| 10 | `PickupPacket(UUID uuid, boolean rightClick)` | `executeServer`: looks up any `EntityItem` by UUID in player's world and picks it up — **no distance check** | Remote item pickup — steal drops anywhere in the loaded world (death drops, other players' items). Client learns UUIDs through the mod's normal sync |
| 11 | `DropPacket(float power)` | Sets server-global `EventHandler.Droppower` from client value | Minor: throw-force control (shared static, race with other players) |

### BountifulBaubles (CursedFlames, `forge-1.12.x`)
| # | Packet | Vulnerability | Impact |
|---|---|---|---|
| 12 | `NBTPacket` with `id=SYNC_SERVER_DATA` | `HandlerNBTPacket` dispatches by a client byte; server applies `Config.loadSyncTag(tag)` overwriting synced config properties — no perm check | Client-controlled mod config on the server (scope depends on which props are synced — TODO enumerate) |

TODO for BountifulBaubles: `HandlerReforge`, `HandlerWormhole`, `HandlerWormholeRequest`, `HandlerPrism` (teleport + reforge economy).

## VERIFIED CLEAN (dead ends, don't retry)
- **Waystones 1.12** (`TwelveIterations/Waystones` @ `1.12`): teleport handler validates mode-by-mode (scroll consumed, warp stone held + cooldown, source waystone must exist, XP charged, config gates); edit handler has distance check + owner-rename gate + `%RANDOM%` block; remove only affects own list. Solid.
- **Quark 1.12** (`VazkiiMods/Quark` @ `1.12`): management packets (sort/dropoff/delete/backpack/matrix enchanter/restock) all operate on `player.openContainer` / player's own inventory through server-side container code. No cross-inventory reach. (RLCraft likely disables most management features in quark.cfg anyway.)
- **Lycanites `MessageEntityGUICommand`**: `performGUICommand` checks `player != this.getOwner()` → gated.

## NOT YET AUDITED (queue, rough priority order)
1. Lycanites rest: `MessagePlayerControl`, `MessagePlayerAttack`, `MessagePetEntry(+Remove)`, `MessageSummonSet(+Selection)`, `MessageSummoningPedestalSummonSet`, `MessageTileEntityButton`, `MessageGUIRequest`
2. Bountiful (`1.12-legacy-forge`, Kotlin) — bounty fulfillment/reward claim validation
3. BountifulBaubles reforge/wormhole handlers
4. Mob Spawner Control (spawner-edit packets — check if op-gated; repo name TBD)
5. Charm 1.12 (crates etc. — repo TBD; svenhjol/Charm has no 1.12 branch visible)
6. Mowzie's Mobs (ability packets), Defiled Lands, Scape & Run: Parasites, Disenchanter, Fishing Made Better, Tool Belt, Comforts, Elenai Dodge, Switch-Bow, XP Tome, So Many Enchantments, AstikorCarts, Aquaculture 2, Advanced Hook Launchers
7. RLCraft closed-source editions with public repos: RLCombat / RLTweaker2 / RLMixins (fonnymunkey), ShieldBreak, Spartan and Fire RLCraft Edition
8. CarryOn — only C2S packet is `SyncKeybindPacket`; actual carrying rides vanilla interactions → dupe angles rather than packet angles (check pickup/drop race)

## Local clones available (sandbox /tmp — transient)
`iaf184` (IaF 1.8.4-1.12.2 exact), `quark`@1.12, `carryon`@1.12, `waystones`@1.12, `bountiful`@1.12-legacy-forge,
`itemphysic`@1.12, `bountifulbaubles`@forge-1.12.x, `lycanites`@master, `iaf` (CE fork, refactored — reference only),
`rcsrc` (ReC 1.4.8.6), `secfix.diff` (ReC 1.4.8.7 security port diff).
