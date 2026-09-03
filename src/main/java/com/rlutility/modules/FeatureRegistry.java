package com.rlutility.modules;

import com.rlutility.modules.Feature.Category;
import com.rlutility.modules.Feature.Compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Single source of truth for everything the GUI and the HUD display. Adding a module is one line
 * here plus the field in {@link FeatureConfig}.
 *
 * <h3>Layout convention</h3>
 * Every toggle is a module. Options that refine a module are declared with that module's name as
 * their {@code parent} (for boolean sub-features) or {@code group} (for value settings), and the
 * menu nests them directly underneath the module. Nothing floats around without an owner, which
 * is what used to make the tabs read as one wall of similarly named rows.
 */
public final class FeatureRegistry {

    private FeatureRegistry() {}

    private static final List<Feature> FEATURES = new ArrayList<>();
    private static final List<Setting> SETTINGS = new ArrayList<>();

    /** A numeric / cyclable option. {@code group} names the module it belongs to, or null. */
    public static class Setting {
        public final String name;
        public final String desc;
        public final Category category;
        public final String group;
        /** Declaration order within the registry; the menu uses it to interleave options naturally. */
        public int order;
        private final Supplier<String> valueSupplier;
        private final java.util.function.IntConsumer adjuster;
        /** Present only on numeric settings; enables typing a value directly. */
        private java.util.function.DoubleSupplier rawGet;
        private java.util.function.DoubleConsumer rawSet;
        private boolean integral;
        private double hardMin = -Double.MAX_VALUE;
        private double hardMax = Double.MAX_VALUE;

        public Setting(String name, String desc, Category category, String group,
                       Supplier<String> value, java.util.function.IntConsumer adjust) {
            this.name = name;
            this.desc = desc;
            this.category = category;
            this.group = group;
            this.valueSupplier = value;
            this.adjuster = adjust;
        }

        /** True when this setting accepts a typed value. */
        public boolean isTypable() {
            return rawSet != null;
        }

        public boolean isIntegral() {
            return integral;
        }

        public double rawValue() {
            return rawGet == null ? 0.0D : rawGet.getAsDouble();
        }

        /** Applies a typed value, clamped only by the safety limits, not the click step range. */
        public void setRaw(double v) {
            if (rawSet == null) return;
            if (v < hardMin) v = hardMin;
            if (v > hardMax) v = hardMax;
            rawSet.accept(integral ? Math.rint(v) : v);
            FeatureConfig.saveConfig();
        }

        public String value() {
            return valueSupplier.get();
        }

        /** direction is +1 (left click) or -1 (right click). */
        public void adjust(int direction) {
            adjuster.accept(direction);
            FeatureConfig.saveConfig();
        }
    }

    /** Global declaration counter so features and settings can be interleaved in one order. */
    private static int orderSeq = 0;

    private static void f(String name, String desc, Category c, Compat compat,
                          Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        Feature feature = new Feature(name, desc, c, compat, get, set);
        feature.order = orderSeq++;
        FEATURES.add(feature);
    }

    /** Sub-feature: a boolean option owned by another module, drawn nested under it. */
    private static void sub(String name, String desc, Category c, Compat compat, String parent,
                            Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        Feature feature = new Feature(name, desc, c, compat, parent, get, set);
        feature.order = orderSeq++;
        FEATURES.add(feature);
    }

    private static void s(String name, String desc, Category c, String group,
                          Supplier<String> value, java.util.function.IntConsumer adjust) {
        Setting setting = new Setting(name, desc, c, group, value, adjust);
        setting.order = orderSeq++;
        SETTINGS.add(setting);
    }

    /**
     * Declares a numeric setting that can be clicked to step OR typed into directly.
     *
     * <p>{@code step}, {@code softMin} and {@code softMax} bound click-stepping so the arrows stay
     * useful, while {@code hardMax} bounds what may be typed. Those are deliberately separate: the
     * old settings clamped everything to the click range, so an ESP range could never exceed 192
     * however you asked for it.</p>
     */
    private static void num(String name, String desc, Category c, String group, String unit,
                            java.util.function.DoubleSupplier get,
                            java.util.function.DoubleConsumer set,
                            double step, double softMin, double softMax,
                            double hardMin, double hardMax, boolean integral) {
        Setting setting = new Setting(name, desc, c, group,
                () -> integral
                        ? ((long) get.getAsDouble()) + unit
                        : String.format("%.2f", get.getAsDouble()) + unit,
                d -> {
                    double next = get.getAsDouble() + step * d;
                    // Stepping stays inside the soft range unless you have already typed past it,
                    // in which case stepping simply continues from where you are.
                    double lo = Math.min(softMin, get.getAsDouble());
                    double hi = Math.max(softMax, get.getAsDouble());
                    set.accept(integral ? Math.rint(clamp(next, lo, hi)) : clamp(next, lo, hi));
                });
        setting.rawGet = get;
        setting.rawSet = set;
        setting.integral = integral;
        setting.hardMin = hardMin;
        setting.hardMax = hardMax;
        setting.order = orderSeq++;
        SETTINGS.add(setting);
    }

    static {
        // ================================================================ COMBAT
        f("Auto Criticals", "Packet micro-hop before every swing so the server registers a 1.5x crit.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.autoCriticals, v -> FeatureConfig.autoCriticals = v);

        f("Click Aura", "One swing hits every valid target around you - no hitbox lining up. "
                + "Input-driven: nothing happens unless you actually click.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.clickAura, v -> FeatureConfig.clickAura = v);
        num("Range", "Sphere radius around you. The server still enforces its own reach.",
                Category.COMBAT, "Click Aura", "m",
                () -> FeatureConfig.clickAuraRange, v -> FeatureConfig.clickAuraRange = v,
                0.5, 1, 12, 0.5, 256, false);
        num("Max Targets", "Cap on how many entities one swing hits.",
                Category.COMBAT, "Click Aura", "",
                () -> FeatureConfig.clickAuraMaxTargets, v -> FeatureConfig.clickAuraMaxTargets = (int) v,
                1, 1, 32, 1, 1024, true);
        sub("Respect Cooldown", "Only fire on a charged swing, matching vanilla attack speed.",
                Category.COMBAT, Compat.LOCAL, "Click Aura",
                () -> FeatureConfig.clickAuraRespectCooldown, v -> FeatureConfig.clickAuraRespectCooldown = v);
        sub("Hit Players", "Include other players.",
                Category.COMBAT, Compat.LOCAL, "Click Aura",
                () -> FeatureConfig.clickAuraHitPlayers, v -> FeatureConfig.clickAuraHitPlayers = v);
        sub("Hit Passive Mobs", "Include passive mobs.",
                Category.COMBAT, Compat.LOCAL, "Click Aura",
                () -> FeatureConfig.clickAuraHitPassive, v -> FeatureConfig.clickAuraHitPassive = v);
        sub("Hit Tamed Mobs", "Include your own tamed animals.",
                Category.COMBAT, Compat.LOCAL, "Click Aura",
                () -> FeatureConfig.clickAuraHitTamed, v -> FeatureConfig.clickAuraHitTamed = v);

        f("Reach", "Extends block interaction and attack reach. The vanilla 1.12 server never "
                + "verifies the client's reach value, so this holds on normal servers - strict "
                + "anti-cheats may flag it.",
                Category.COMBAT, Compat.RISKY,
                () -> FeatureConfig.reachEnabled, v -> FeatureConfig.reachEnabled = v);
        num("Reach Distance", "Interaction distance in blocks. Vanilla survival is 4.5, creative 5.0.",
                Category.COMBAT, "Reach", "m",
                () -> FeatureConfig.reachBlocks, v -> FeatureConfig.reachBlocks = v,
                0.5, 5, 8, 4.5, 32, false);

        f("Anti-Knockback", "Cancels the knockback the server pushes onto you - movement is client-driven.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.antiKnockback, v -> FeatureConfig.antiKnockback = v);
        num("Horizontal Factor", "Fraction of horizontal knockback kept (0.00 = immune).",
                Category.COMBAT, "Anti-Knockback", "",
                () -> FeatureConfig.antiKnockbackHorizontal, v -> FeatureConfig.antiKnockbackHorizontal = v,
                0.05, 0, 1, 0, 1, false);
        num("Vertical Factor", "Fraction of vertical knockback kept (0.00 = immune).",
                Category.COMBAT, "Anti-Knockback", "",
                () -> FeatureConfig.antiKnockbackVertical, v -> FeatureConfig.antiKnockbackVertical = v,
                0.05, 0, 1, 0, 1, false);

        // ============================================================== MOVEMENT
        f("Flight", "Creative-style flying. The server must allow flight for this to hold - on a "
                + "strict server you will rubber-band back to the ground. Flight is automatically "
                + "re-asserted whenever the server strips it mid-air (damage, ability resyncs).",
                Category.MOVEMENT, Compat.RISKY,
                () -> FeatureConfig.creativeFly, v -> FeatureConfig.creativeFly = v);
        num("Fly Speed", "Flight speed, re-applied every tick because server resyncs reset it.",
                Category.MOVEMENT, "Flight", "",
                () -> FeatureConfig.flySpeed, v -> FeatureConfig.flySpeed = v,
                0.01, 0.01, 1, 0.01, 10, false);

        f("No Fall", "Spoofs the on-ground flag while falling so the server never applies fall damage.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noFall, v -> FeatureConfig.noFall = v);

        f("Anti-Kinetic", "Stops wall-impact damage. RLCraft's Collision Damage mod asks the client "
                + "how hard it hit and trusts the answer - this reports nothing, and brakes elytra "
                + "flights before impact so vanilla can't compute a loss either. Same trick the "
                + "Stone of Inertia Null uses, minus needing the drop.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noFallKinetic, v -> FeatureConfig.noFallKinetic = v);

        f("Step Assist", "Walk up full blocks. Sent as normal movement, so servers accept it.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.stepSpeed, v -> FeatureConfig.stepSpeed = v);
        f("Water Walk", "Walk across water and lava surfaces.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.waterWalk, v -> FeatureConfig.waterWalk = v);
        f("No Slowdown", "Full movement speed while eating, blocking or drawing a bow.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noSlowdown, v -> FeatureConfig.noSlowdown = v);

        f("Siren Guard", "Counters Ice and Fire sirens. Auto-equips earplugs when you have them "
                + "(the real fix - the server then releases the charm); otherwise it fights the "
                + "pull and auto-runs you away from the singing siren.",
                Category.MOVEMENT, Compat.MODDED,
                () -> FeatureConfig.sirenGuard, v -> FeatureConfig.sirenGuard = v);

        // ============================================================== SURVIVAL
        f("Auto Armor", "Equips the strongest armour in your inventory with real inventory clicks.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoArmor, v -> FeatureConfig.autoArmor = v);
        f("Auto Bandage", "Applies bandages and plasters to wounded limbs through First Aid's own channel.",
                Category.SURVIVAL, Compat.MODDED,
                () -> FeatureConfig.firstAidAutoHeal, v -> FeatureConfig.firstAidAutoHeal = v);
        f("Auto Totem", "Hot-swaps a Totem of Undying into your off-hand via real container clicks. "
                + "With First Aid installed it also reacts to critical head/body wounds.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.fastTriage, v -> FeatureConfig.fastTriage = v);
        num("Equip At HP", "Swap a totem in when health drops to this value. First Aid critical "
                + "head/body wounds trigger it earlier.",
                Category.SURVIVAL, "Auto Totem", "hp",
                () -> FeatureConfig.totemEquipAtHealth, v -> FeatureConfig.totemEquipAtHealth = v,
                1, 2, 16, 1, 20, true);
        f("Auto Eat", "Eats real food (never rotten/poisonous) when hunger drops, then restores your slot.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoEat, v -> FeatureConfig.autoEat = v);
        s("Eat At", "Hunger level that triggers Auto Eat.",
                Category.SURVIVAL, "Auto Eat",
                () -> FeatureConfig.autoEatThreshold + "/20",
                d -> FeatureConfig.autoEatThreshold = (int) clamp(FeatureConfig.autoEatThreshold + d, 1, 19));
        f("Auto Hydrate", "Drinks through SimpleDifficulty only when thirst is low; it never clicks or "
                + "chooses a drink while you are comfortably hydrated.",
                Category.SURVIVAL, Compat.MODDED,
                () -> FeatureConfig.simpleDifficultyAutoHydrate, v -> FeatureConfig.simpleDifficultyAutoHydrate = v);
        num("Drink At", "Start drinking at or below this thirst level (0-20).",
                Category.SURVIVAL, "Auto Hydrate", " /20",
                () -> FeatureConfig.simpleDifficultyHydrateAt,
                v -> FeatureConfig.simpleDifficultyHydrateAt = (int) v,
                1, 2, 14, 1, 20, true);
        sub("Safe Water Only",  "Only drink from purified water unless thirst is critical. Dirty water "
                + "has a 75% Thirsty chance plus a parasite roll - the server applies both, so "
                + "choosing safe sources is the only real protection.",
                Category.SURVIVAL, Compat.MODDED, "Auto Hydrate",
                () -> FeatureConfig.simpleDifficultySafeWater, v -> FeatureConfig.simpleDifficultySafeWater = v);
        f("No Thirst Blur", "Removes the blurry-vision effect EnhancedVisuals applies when thirst runs "
                + "low. The blur is purely a client shader from EnhancedVisuals' SimpleDifficulty "
                + "addon - this zeroes its intensity at runtime so it fades out. Visual-only, safe "
                + "on any server.",
                Category.SURVIVAL, Compat.MODDED,
                () -> FeatureConfig.removeThirstBlur, v -> FeatureConfig.removeThirstBlur = v);
        f("Auto Respawn", "Instantly sends the respawn packet on the death screen.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoRespawn, v -> FeatureConfig.autoRespawn = v);

        f("Item Magnet", "Pulls nearby drops toward you. Authoritative only when ItemPhysic is "
                + "installed; otherwise it is a client-side convenience.",
                Category.SURVIVAL, Compat.MODDED,
                () -> FeatureConfig.clientItemVacuum, v -> FeatureConfig.clientItemVacuum = v);
        num("Magnet Radius", "How far the magnet reaches.",
                Category.SURVIVAL, "Item Magnet", "m",
                () -> FeatureConfig.magnetRadius, v -> FeatureConfig.magnetRadius = v,
                0.5, 1, 32, 0.5, 256, false);
        num("Magnet Speed", "How hard items are pulled toward you.",
                Category.SURVIVAL, "Item Magnet", "",
                () -> FeatureConfig.magnetSpeed, v -> FeatureConfig.magnetSpeed = v,
                0.05, 0.05, 2, 0.01, 20, false);
        sub("Only My Drops", "Ignore items that another player dropped.",
                Category.SURVIVAL, Compat.LOCAL, "Item Magnet",
                () -> FeatureConfig.magnetOnlyMine, v -> FeatureConfig.magnetOnlyMine = v);
        sub("Ignore My Drops", "The reverse: leave items you dropped or threw yourself where they "
                + "are, so the magnet only collects loot from kills and chests.",
                Category.SURVIVAL, Compat.LOCAL, "Item Magnet",
                () -> FeatureConfig.magnetIgnoreMine, v -> FeatureConfig.magnetIgnoreMine = v);

        f("Debuff Neutralizer", "Strips screen-shake and nuisance debuffs from your client render.",
                Category.SURVIVAL, Compat.LOCAL,
                () -> FeatureConfig.clientDebuffNeutralizer, v -> FeatureConfig.clientDebuffNeutralizer = v);

        // ============================================================== EXPLOITS
        f("Auto Lockpick", "Audio/entropy solver that opens Locks tumblers through the mod's own packets.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.autoLockpick, v -> FeatureConfig.autoLockpick = v);
        f("Auto Reforge", "Re-rolls QualityTools / Bountiful Baubles until the target quality lands.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.autoReforge, v -> FeatureConfig.autoReforge = v);
        f("Auto Loot", "Empties any open chest, barrel or dungeon container with real shift-click packets.",
                Category.EXPLOITS, Compat.SERVER,
                () -> FeatureConfig.autoLoot, v -> FeatureConfig.autoLoot = v);
        sub("Close When Done", "Automatically closes the container once it has been emptied.",
                Category.EXPLOITS, Compat.SERVER, "Auto Loot",
                () -> FeatureConfig.autoLootCloseWhenDone, v -> FeatureConfig.autoLootCloseWhenDone = v);
        s("Loot Delay", "Ticks between each shift-click. Lower is faster but noisier.",
                Category.EXPLOITS, "Auto Loot",
                () -> FeatureConfig.autoLootDelay + "t",
                d -> FeatureConfig.autoLootDelay = (int) clamp(FeatureConfig.autoLootDelay + d, 0, 10));

        f("Fast Mine", "Removes the vanilla break delay and undoes NoTreePunching's speed penalty. "
                + "Instant mode completes block progress in one tick - vanilla servers trust the "
                + "finish packet, anti-cheats may not.",
                Category.EXPLOITS, Compat.SERVER,
                () -> FeatureConfig.fastMine, v -> FeatureConfig.fastMine = v);
        s("Mine Mode", "Fast = no delay. Instant = blocks break in about one tick.",
                Category.EXPLOITS, "Fast Mine",
                () -> FeatureConfig.fastMineMode == 0 ? "Fast" : "Instant",
                d -> FeatureConfig.fastMineMode = ((FeatureConfig.fastMineMode + d) % 2 + 2) % 2);

        // Ice and Fire 1.7.1 (the build RLCraft 2.9.3 pins) ships several unvalidated
        // client-to-server packets (the same bug class as the Recurrent Complex admin exploit).
        // These modules ride the mod's own "iceandfire" channel - all effects happen server-side.
        f("IaF Longshot", "Ice and Fire: the server performs a real weapon attack on whatever you "
                + "aim at, up to ~100 blocks away (MessagePlayerHitMultipart is never validated). "
                + "Full damage, enchantments and sweep included.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.iafLongshot, v -> FeatureConfig.iafLongshot = v);
        num("Range", "How far the crosshair trace reaches. The server accepts up to 100 blocks.",
                Category.EXPLOITS, "IaF Longshot", "m",
                () -> FeatureConfig.iafLongshotRange, v -> FeatureConfig.iafLongshotRange = v,
                1, 8, 96, 4, 96, false);

        f("IaF Execute", "Ice and Fire: MessageMultipartInteract lets the client name a target AND "
                + "the damage number the server applies. Aim at anything and delete it.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.iafExecute, v -> FeatureConfig.iafExecute = v);
        num("Damage", "Damage applied per packet.",
                Category.EXPLOITS, "IaF Execute", " HP",
                () -> FeatureConfig.iafExecuteDamage, v -> FeatureConfig.iafExecuteDamage = v,
                10, 1, 1000, 0.5, 1000000, false);
        num("Interval", "Ticks between damage packets.",
                Category.EXPLOITS, "IaF Execute", "t",
                () -> FeatureConfig.iafExecuteInterval, v -> FeatureConfig.iafExecuteInterval = (int) v,
                5, 1, 100, 1, 1200, true);
        num("Range", "How far the crosshair trace reaches.",
                Category.EXPLOITS, "IaF Execute", "m",
                () -> FeatureConfig.iafExecuteRange, v -> FeatureConfig.iafExecuteRange = v,
                1, 8, 96, 4, 96, false);

        f("IaF Gorgon Gaze", "Ice and Fire: MessageStoneStatue petrifies (or frees) whatever you "
                + "aim at - the 1.7.1 server checks no distance and no held item, it just sets "
                + "the stone flag on the named entity. Works empty-handed. Hold aim to re-assert.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.iafGorgonGaze, v -> FeatureConfig.iafGorgonGaze = v);
        sub("Unpetrify Mode", "Send the release flag instead: turn stone statues back into mobs.",
                Category.EXPLOITS, Compat.MODDED, "IaF Gorgon Gaze",
                () -> FeatureConfig.iafGorgonUnpetrify, v -> FeatureConfig.iafGorgonUnpetrify = v);
        num("Range", "Crosshair trace range for this module only.",
                Category.EXPLOITS, "IaF Gorgon Gaze", "m",
                () -> FeatureConfig.iafGorgonRange, v -> FeatureConfig.iafGorgonRange = v,
                8, 8, 256, 4, 512, false);

        f("IaF Siren Silencer", "Ice and Fire: flips every siren's singing flag off server-side "
                + "(MessageSirenSong, unvalidated). Silent sirens charm nobody - the offensive "
                + "complement to Siren Guard's earplugs.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.iafSirenSilencer, v -> FeatureConfig.iafSirenSilencer = v);
        num("Radius", "Sirens inside this radius are silenced every 2 seconds.",
                Category.EXPLOITS, "IaF Siren Silencer", "m",
                () -> FeatureConfig.iafSirenRadius, v -> FeatureConfig.iafSirenRadius = v,
                8, 16, 128, 8, 256, false);

        f("IaF Dragon Smith", "Ice and Fire: MessageDragonArmor sets any dragon's armor slots "
                + "server-side with no ownership check - and no armor item is consumed. Aim at "
                + "any dragon to plate it (even someone else's tamed one), or set grade to 0 to "
                + "strip a dragon's armor bare.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.iafDragonSmith, v -> FeatureConfig.iafDragonSmith = v);
        num("Grade", "Armor grade applied: 0 = none (strip), 1 = iron, 2 = gold, 3 = diamond.",
                Category.EXPLOITS, "IaF Dragon Smith", "",
                () -> FeatureConfig.iafDragonSmithArmor, v -> FeatureConfig.iafDragonSmithArmor = (int) v,
                1, 0, 3, 0, 3, true);
        num("Range", "Crosshair trace range for this module only.",
                Category.EXPLOITS, "IaF Dragon Smith", "m",
                () -> FeatureConfig.iafDragonSmithRange, v -> FeatureConfig.iafDragonSmithRange = v,
                8, 8, 96, 4, 96, false);

        // ================================================================ SKILLS
        f("Client Lock Bypass", "REQUIRED for locked tools. Reskillable runs on your client too and "
                + "cancels mining/interaction locally, so the packet never even reaches the server. "
                + "This reverts that. The server still decides the outcome.",
                Category.SKILLS, Compat.LOCAL,
                () -> FeatureConfig.reskillableBypass, v -> FeatureConfig.reskillableBypass = v);
        f("Auto-Buy Levels", "Automatically spend XP levels to unlock the item you are holding.",
                Category.SKILLS, Compat.MODDED,
                () -> FeatureConfig.reskillableAutoBuy, v -> FeatureConfig.reskillableAutoBuy = v);
        s("XP Reserve", "Never spend below this many XP levels when auto-buying.",
                Category.SKILLS, "Auto-Buy Levels",
                () -> FeatureConfig.reskillableXpReserve + " lv",
                d -> FeatureConfig.reskillableXpReserve = (int) clamp(FeatureConfig.reskillableXpReserve + d, 0, 100));

        f("Preserve Class", "Level Up! 2: keep exactly one class marker set. Off = all three XP bonuses at once.",
                Category.SKILLS, Compat.MODDED,
                () -> FeatureConfig.levelUpPreserveClass, v -> FeatureConfig.levelUpPreserveClass = v);
        f("Clamp Levels", "Level Up! 2: never send a skill level above its own cap. Overshooting can throw server-side.",
                Category.SKILLS, Compat.MODDED,
                () -> FeatureConfig.levelUpClampToMax, v -> FeatureConfig.levelUpClampToMax = v);

        // =============================================================== VISUALS
        f("Chest ESP", "Outlines chests, ender chests and shulkers through walls.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espChests, v -> FeatureConfig.espChests = v);
        f("Spawner ESP", "Outlines dungeon and battle-tower spawners.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espSpawners, v -> FeatureConfig.espSpawners = v);
        f("Waystone ESP", "Outlines waystones so you never lose a fast-travel node.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espWaystones, v -> FeatureConfig.espWaystones = v);
        f("Boss ESP", "Outlines dragons, sea serpents, cyclopes and other Ice & Fire threats.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espDragons, v -> FeatureConfig.espDragons = v);
        f("Hostile ESP", "Outlines every hostile mob in range.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espHostiles, v -> FeatureConfig.espHostiles = v);
        f("Player ESP", "Outlines other players - essential on PvP servers.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espPlayers, v -> FeatureConfig.espPlayers = v);
        f("Item ESP", "Outlines dropped items so nothing gets lost in tall grass.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espItems, v -> FeatureConfig.espItems = v);
        f("Modded Mob ESP", "Outlines every living entity that is not from vanilla Minecraft.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espModdedMobs, v -> FeatureConfig.espModdedMobs = v);
        f("All Containers ESP", "Outlines anything with an inventory, including modded chests and barrels.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espAllContainers, v -> FeatureConfig.espAllContainers = v);
        f("Custom Entity ESP", "Highlight the entity ids on your custom list.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espCustomEntitiesOn, v -> FeatureConfig.espCustomEntitiesOn = v);
        f("Custom Block ESP", "Highlight the block ids on your custom list, including the dragon skull patterns.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espCustomBlocksOn, v -> FeatureConfig.espCustomBlocksOn = v);
        f("Tracers", "Draws lines from your crosshair to every highlighted entity.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espTracers, v -> FeatureConfig.espTracers = v);

        f("Model Outlines", "Trace the real model in wireframe for categories set to Outline. "
                + "Unlike the vanilla glow this is plain line rasterising, so thickness is exact "
                + "and there is no halo.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espModelOutline, v -> FeatureConfig.espModelOutline = v);
        num("Outline Thickness", "Model outline width in pixels.",
                Category.VISUALS, "Model Outlines", "px",
                () -> FeatureConfig.espOutlineWidth, v -> FeatureConfig.espOutlineWidth = v,
                0.5, 0.5, 10, 0.1, 64, false);
        sub("Outline Through Walls", "Draw model outlines through terrain.",
                Category.VISUALS, Compat.LOCAL, "Model Outlines",
                () -> FeatureConfig.espOutlineThroughWalls, v -> FeatureConfig.espOutlineThroughWalls = v);

        f("XRay", "Highlights the blocks on your list through terrain.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.xrayEnabled, v -> FeatureConfig.xrayEnabled = v);
        sub("XRay Tracers", "Draws a line from your crosshair to every XRay hit.",
                Category.VISUALS, Compat.LOCAL, "XRay",
                () -> FeatureConfig.xrayTracers, v -> FeatureConfig.xrayTracers = v);
        num("XRay Range", "Scan radius. Cost grows with the cube of this - 28 is a good balance.",
                Category.VISUALS, "XRay", "m",
                () -> FeatureConfig.xrayRange, v -> FeatureConfig.xrayRange = (int) v,
                4, 8, 64, 1, 256, true);
        s("XRay Rescan", "Ticks between scans. Lower reacts faster but costs more CPU.",
                Category.VISUALS, "XRay",
                () -> FeatureConfig.xrayRescanTicks + "t",
                d -> FeatureConfig.xrayRescanTicks = (int) clamp(FeatureConfig.xrayRescanTicks + 5 * d, 5, 120));

        num("ESP Range", "Maximum draw distance in blocks. Lower = better FPS.",
                Category.VISUALS, null, "m",
                () -> FeatureConfig.espRange, v -> FeatureConfig.espRange = (int) v,
                8, 16, 256, 1, 4096, true);
        num("ESP Line Width", "Outline thickness for box-style ESP.",
                Category.VISUALS, null, "px",
                () -> FeatureConfig.espLineWidth, v -> FeatureConfig.espLineWidth = v,
                0.5, 0.5, 6, 0.1, 64, false);

        // =================================================================== HUD
        f("HUD", "Master switch for the in-game overlay.",
                Category.HUD, Compat.LOCAL,
                () -> FeatureConfig.hudEnabled, v -> FeatureConfig.hudEnabled = v);
        sub("Watermark", "Small RLUtility / RLCraft version tag in the corner.",
                Category.HUD, Compat.LOCAL, "HUD",
                () -> FeatureConfig.hudWatermark, v -> FeatureConfig.hudWatermark = v);
        sub("Module List", "Lists every active module down the right-hand side.",
                Category.HUD, Compat.LOCAL, "HUD",
                () -> FeatureConfig.hudModuleList, v -> FeatureConfig.hudModuleList = v);
        sub("Stats Line", "FPS, ping, coordinates and current dimension.",
                Category.HUD, Compat.LOCAL, "HUD",
                () -> FeatureConfig.hudStats, v -> FeatureConfig.hudStats = v);
        sub("Target Info", "Health bar and distance readout for whatever you are looking at.",
                Category.HUD, Compat.LOCAL, "HUD",
                () -> FeatureConfig.hudTargetInfo, v -> FeatureConfig.hudTargetInfo = v);
    }

    private static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    public static List<Feature> all() {
        return FEATURES;
    }

    public static List<Feature> byCategory(Category c) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES) if (f.category == c) out.add(f);
        return out;
    }

    /** Top-level modules of a category, in declaration order. */
    public static List<Feature> modulesFor(Category c) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES) if (f.category == c && !f.isSubFeature()) out.add(f);
        return out;
    }

    /** Sub-features owned by the named module, in declaration order. */
    public static List<Feature> subFeaturesOf(Category c, String moduleName) {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES) {
            if (f.category == c && moduleName.equals(f.parent)) out.add(f);
        }
        return out;
    }

    public static List<Setting> settingsFor(Category c) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : SETTINGS) if (s.category == c) out.add(s);
        return out;
    }

    /** Settings owned by the named module. */
    public static List<Setting> settingsForGroup(Category c, String moduleName) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : SETTINGS) {
            if (s.category == c && moduleName.equals(s.group)) out.add(s);
        }
        return out;
    }

    /**
     * Everything owned by the named module - sub-features and settings interleaved in declaration
     * order, so a module's options read exactly the way they were written.
     */
    public static List<Object> optionsOf(Category c, String moduleName) {
        List<Object> out = new ArrayList<>();
        out.addAll(subFeaturesOf(c, moduleName));
        out.addAll(settingsForGroup(c, moduleName));
        out.sort(java.util.Comparator.comparingInt(o ->
                o instanceof Feature ? ((Feature) o).order : ((Setting) o).order));
        return out;
    }

    /** Settings with no owning module - rendered at the end of the tab. */
    public static List<Setting> standaloneSettings(Category c) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : SETTINGS) if (s.category == c && s.group == null) out.add(s);
        return out;
    }

    public static List<Feature> search(String query) {
        List<Feature> out = new ArrayList<>();
        String q = query.toLowerCase();
        for (Feature f : FEATURES) {
            if (f.name.toLowerCase().contains(q) || f.desc.toLowerCase().contains(q)) out.add(f);
        }
        return out;
    }

    /** Enabled modules worth showing in the HUD array list (options and HUD toggles excluded). */
    public static List<Feature> activeForHud() {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES) {
            if (f.category == Category.HUD || f.category == Category.TOOLS) continue;
            if (f.isSubFeature()) continue;
            if (f.isEnabled()) out.add(f);
        }
        return out;
    }
}
