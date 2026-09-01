package com.rlutility.modules;

import com.rlutility.modules.Feature.Category;
import com.rlutility.modules.Feature.Compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Single source of truth for everything the GUI and the HUD display. Adding a module is one line
 * here plus the field in {@link FeatureConfig}.
 */
public final class FeatureRegistry {

    private FeatureRegistry() {}

    private static final List<Feature> FEATURES = new ArrayList<>();
    private static final List<Setting> SETTINGS = new ArrayList<>();

    /** A numeric / cyclable option shown underneath the toggle list of a category. */
    public static class Setting {
        public final String name;
        public final String desc;
        public final Category category;
        private final Supplier<String> valueSupplier;
        private final java.util.function.IntConsumer adjuster;

        public Setting(String name, String desc, Category category,
                       Supplier<String> value, java.util.function.IntConsumer adjust) {
            this.name = name;
            this.desc = desc;
            this.category = category;
            this.valueSupplier = value;
            this.adjuster = adjust;
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

    private static void f(String name, String desc, Category c, Compat compat,
                          Supplier<Boolean> get, java.util.function.Consumer<Boolean> set) {
        FEATURES.add(new Feature(name, desc, c, compat, get, set));
    }

    private static void s(String name, String desc, Category c,
                          Supplier<String> value, java.util.function.IntConsumer adjust) {
        SETTINGS.add(new Setting(name, desc, c, value, adjust));
    }

    static {
        // ------------------------------------------------------------- COMBAT
        f("Auto Criticals", "Packet micro-hop before every swing so the server registers a 1.5x crit.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.autoCriticals, v -> FeatureConfig.autoCriticals = v);
        f("Kill Aura", "Auto-attacks the best target in range with rotation spoofing and real attack packets.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.killAura, v -> FeatureConfig.killAura = v);
        f("KA: Target Players", "Let Kill Aura hit other players (off by default so you don't grief allies).",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.killAuraPlayers, v -> FeatureConfig.killAuraPlayers = v);
        f("KA: Target Animals", "Let Kill Aura hit passive mobs. Tamed pets are always skipped.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.killAuraAnimals, v -> FeatureConfig.killAuraAnimals = v);
        f("KA: Wall Check", "Only swing when the target is actually visible - far less obvious to anti-cheat.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.killAuraWallCheck, v -> FeatureConfig.killAuraWallCheck = v);
        f("KA: Silent Rotations", "Send look packets at the target without turning your camera.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.killAuraRotations, v -> FeatureConfig.killAuraRotations = v);
        f("Nunchaku Triggerbot", "Keeps Better Survival nunchaku spinning and shreds whatever you look at.",
                Category.COMBAT, Compat.MODDED,
                () -> FeatureConfig.autoTriggerbot, v -> FeatureConfig.autoTriggerbot = v);
        f("Level Damage Bypass", "Fires the reach/attack packets of RLCombat, Spartan and I&F directly.",
                Category.COMBAT, Compat.MODDED,
                () -> FeatureConfig.levelDamageBypass, v -> FeatureConfig.levelDamageBypass = v);
        f("Anti Knockback", "Cancels the knockback the server pushes onto you - movement is client-driven.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.antiKnockback, v -> FeatureConfig.antiKnockback = v);
        f("Auto Totem", "Hot-swaps a Totem of Undying into your off-hand via real container clicks.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.fastTriage, v -> FeatureConfig.fastTriage = v);
        f("FirstAid Auto-Triage", "Applies bandages/plasters to wounded limbs through FirstAid's own channel.",
                Category.COMBAT, Compat.MODDED,
                () -> FeatureConfig.firstAidAutoHeal, v -> FeatureConfig.firstAidAutoHeal = v);
        f("Auto Armor", "Equips the strongest armour in your inventory with real inventory clicks.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.autoArmor, v -> FeatureConfig.autoArmor = v);
        f("Auto Eat", "Eats real food (never rotten/poisonous) when hunger drops, then restores your slot.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.autoEat, v -> FeatureConfig.autoEat = v);
        f("Auto Respawn", "Instantly sends the respawn packet on the death screen.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.autoRespawn, v -> FeatureConfig.autoRespawn = v);

        s("KA Range", "Kill Aura reach in blocks.", Category.COMBAT,
                () -> String.format("%.1f", FeatureConfig.killAuraRange),
                d -> FeatureConfig.killAuraRange = clamp(FeatureConfig.killAuraRange + 0.2D * d, 2.0D, 6.0D));
        s("KA Speed", "Attacks per second cap (the vanilla cooldown is still respected).", Category.COMBAT,
                () -> FeatureConfig.killAuraCps + " cps",
                d -> FeatureConfig.killAuraCps = (int) clamp(FeatureConfig.killAuraCps + d, 1, 20));
        s("Auto Eat At", "Hunger level that triggers Auto Eat.", Category.COMBAT,
                () -> FeatureConfig.autoEatThreshold + "/20",
                d -> FeatureConfig.autoEatThreshold = (int) clamp(FeatureConfig.autoEatThreshold + d, 1, 19));
        s("KB Horizontal", "Fraction of horizontal knockback kept (0.0 = immune).", Category.COMBAT,
                () -> String.format("%.2f", FeatureConfig.antiKnockbackHorizontal),
                d -> FeatureConfig.antiKnockbackHorizontal = clamp(FeatureConfig.antiKnockbackHorizontal + 0.05D * d, 0.0D, 1.0D));
        s("KB Vertical", "Fraction of vertical knockback kept (0.0 = immune).", Category.COMBAT,
                () -> String.format("%.2f", FeatureConfig.antiKnockbackVertical),
                d -> FeatureConfig.antiKnockbackVertical = clamp(FeatureConfig.antiKnockbackVertical + 0.05D * d, 0.0D, 1.0D));

        // ----------------------------------------------------------- MOVEMENT
        f("No Fall", "Spoofs the on-ground flag while falling so the server never applies fall damage.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noFall, v -> FeatureConfig.noFall = v);
        f("Step Assist", "Walk up full blocks. Sent as normal movement, so servers accept it.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.stepSpeed, v -> FeatureConfig.stepSpeed = v);
        f("Water Walk", "Jesus-walk across water and lava surfaces.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.waterWalk, v -> FeatureConfig.waterWalk = v);
        f("No Slowdown", "Full movement speed while eating, blocking or drawing a bow.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noSlowdown, v -> FeatureConfig.noSlowdown = v);
        f("Timer", "Speeds up your client tick loop - faster movement, mining and attacks.",
                Category.MOVEMENT, Compat.RISKY,
                () -> FeatureConfig.timerEnabled, v -> FeatureConfig.timerEnabled = v);
        f("Creative Flight", "Forces the flight capability. Vanilla servers kick for this.",
                Category.MOVEMENT, Compat.RISKY,
                () -> FeatureConfig.creativeFly, v -> FeatureConfig.creativeFly = v);

        s("Timer Speed", "Tick multiplier. 1.0 is vanilla; above ~2.0 gets you flagged fast.", Category.MOVEMENT,
                () -> String.format("%.2fx", FeatureConfig.timerSpeed),
                d -> FeatureConfig.timerSpeed = clamp(FeatureConfig.timerSpeed + 0.05D * d, 0.5D, 3.0D));

        // ----------------------------------------------------------- EXPLOITS
        f("Auto Lockpick", "Audio/entropy solver that opens Locks tumblers through the mod's own packets.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.autoLockpick, v -> FeatureConfig.autoLockpick = v);
        f("Auto Reforge", "Re-rolls QualityTools / Bountiful Baubles until the target quality lands.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.autoReforge, v -> FeatureConfig.autoReforge = v);
        f("Auto Loot", "Empties any open chest, barrel or dungeon container with real shift-click packets.",
                Category.EXPLOITS, Compat.SERVER,
                () -> FeatureConfig.autoLoot, v -> FeatureConfig.autoLoot = v);
        f("Auto Loot: Close", "Automatically closes the container once it has been emptied.",
                Category.EXPLOITS, Compat.SERVER,
                () -> FeatureConfig.autoLootCloseWhenDone, v -> FeatureConfig.autoLootCloseWhenDone = v);
        f("Fast Mine", "Removes the block hit delay and undoes NoTreePunching's speed penalty.",
                Category.EXPLOITS, Compat.SERVER,
                () -> FeatureConfig.fastMine, v -> FeatureConfig.fastMine = v);
        f("Auto Hydrate", "Drinks through SimpleDifficulty's channel before you ever go thirsty.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.simpleDifficultyAutoHydrate, v -> FeatureConfig.simpleDifficultyAutoHydrate = v);
        f("Item Vacuum", "Pulls nearby drops in. Authoritative only when ItemPhysic is installed.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.clientItemVacuum, v -> FeatureConfig.clientItemVacuum = v);
        f("Debuff Neutralizer", "Strips screen-shake and nuisance debuffs from your client render.",
                Category.EXPLOITS, Compat.LOCAL,
                () -> FeatureConfig.clientDebuffNeutralizer, v -> FeatureConfig.clientDebuffNeutralizer = v);

        s("Loot Delay", "Ticks between each shift-click. Lower is faster but noisier.", Category.EXPLOITS,
                () -> FeatureConfig.autoLootDelay + "t",
                d -> FeatureConfig.autoLootDelay = (int) clamp(FeatureConfig.autoLootDelay + d, 0, 10));

        // -------------------------------------------------------------- TOOLS
        f("Lock Auto-Solve", "Solves Locks lock-picking with zero risk: swaps your pick out so wrong "
                + "guesses cannot break it, then brute forces the permutation.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.locksAutoSolve, v -> FeatureConfig.locksAutoSolve = v);
        s("Lock Solve Delay", "Ticks between pin guesses. Raise it if a server throttles packets.",
                Category.EXPLOITS,
                () -> FeatureConfig.locksSolveDelay + "t",
                d -> FeatureConfig.locksSolveDelay = (int) clamp(FeatureConfig.locksSolveDelay + d, 1, 20));
        f("Enchant Preview", "Shows the exact enchantments all three table slots will give. Pure "
                + "observation - the server hands the client its xpSeed, so nothing is sent.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.enchantPreview, v -> FeatureConfig.enchantPreview = v);

        f("Magnet: Only My Drops", "Ignore items that another player dropped.",
                Category.TOOLS, Compat.LOCAL,
                () -> FeatureConfig.magnetOnlyMine, v -> FeatureConfig.magnetOnlyMine = v);
        s("Magnet Radius", "How far the item vacuum reaches.", Category.TOOLS,
                () -> String.format("%.1fm", FeatureConfig.magnetRadius),
                d -> FeatureConfig.magnetRadius = clamp(FeatureConfig.magnetRadius + 0.5 * d, 1.0, 32.0));
        s("Magnet Speed", "How hard items are pulled toward you.", Category.TOOLS,
                () -> String.format("%.2f", FeatureConfig.magnetSpeed),
                d -> FeatureConfig.magnetSpeed = clamp(FeatureConfig.magnetSpeed + 0.05 * d, 0.05, 2.0));

        f("Weapon Lock Bypass", "Beats Reskillable's level lock on weapons with a one-tick equipment "
                + "desync: the server still has the sword's damage attribute but sees an empty hand, "
                + "so the check is skipped. Loses enchant bonuses and durability wear.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.reskillableAttackBypass, v -> FeatureConfig.reskillableAttackBypass = v);

        f("Reskillable Auto-Buy", "Automatically spend XP levels to unlock the item you are holding.",
                Category.TOOLS, Compat.MODDED,
                () -> FeatureConfig.reskillableAutoBuy, v -> FeatureConfig.reskillableAutoBuy = v);
        s("XP Reserve", "Never spend below this many XP levels when auto-buying.", Category.TOOLS,
                () -> FeatureConfig.reskillableXpReserve + " lv",
                d -> FeatureConfig.reskillableXpReserve = (int) clamp(FeatureConfig.reskillableXpReserve + d, 0, 100));

        f("LU2: Preserve Class", "Keep exactly one Level Up! class marker set. Off = all three XP bonuses at once.",
                Category.TOOLS, Compat.MODDED,
                () -> FeatureConfig.levelUpPreserveClass, v -> FeatureConfig.levelUpPreserveClass = v);
        f("LU2: Clamp To Max", "Never send a skill level above its own cap. Overshooting can throw server-side.",
                Category.TOOLS, Compat.MODDED,
                () -> FeatureConfig.levelUpClampToMax, v -> FeatureConfig.levelUpClampToMax = v);

        // ------------------------------------------------------------ VISUALS
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
        f("Tracers", "Draws lines from your crosshair to every highlighted entity.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espTracers, v -> FeatureConfig.espTracers = v);

        f("Modded Mob ESP", "Outlines every living entity that is not from vanilla Minecraft.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espModdedMobs, v -> FeatureConfig.espModdedMobs = v);
        f("All Containers ESP", "Outlines anything with an inventory, including modded chests and barrels.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espAllContainers, v -> FeatureConfig.espAllContainers = v);

        s("ESP Range", "Maximum draw distance in blocks. Lower = better FPS.", Category.VISUALS,
                () -> FeatureConfig.espRange + "m",
                d -> FeatureConfig.espRange = (int) clamp(FeatureConfig.espRange + 8 * d, 16, 192));

        // ----------------------------------------------------------------- XRay
        f("XRay", "Highlights the blocks on your list through terrain. Edit the list in the Tools tab.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.xrayEnabled, v -> FeatureConfig.xrayEnabled = v);
        f("XRay Tracers", "Draws a line from your crosshair to every XRay hit.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.xrayTracers, v -> FeatureConfig.xrayTracers = v);
        s("XRay Range", "Scan radius. Cost grows with the cube of this - 28 is a good balance.",
                Category.VISUALS,
                () -> FeatureConfig.xrayRange + "m",
                d -> FeatureConfig.xrayRange = (int) clamp(FeatureConfig.xrayRange + 4 * d, 8, 64));
        s("XRay Rescan", "Ticks between scans. Lower reacts faster but costs more CPU.",
                Category.VISUALS,
                () -> FeatureConfig.xrayRescanTicks + "t",
                d -> FeatureConfig.xrayRescanTicks = (int) clamp(FeatureConfig.xrayRescanTicks + 5 * d, 5, 120));

        // ---------------------------------------------------------------- HUD
        f("HUD", "Master switch for the in-game overlay.",
                Category.HUD, Compat.LOCAL,
                () -> FeatureConfig.hudEnabled, v -> FeatureConfig.hudEnabled = v);
        f("Watermark", "Small RLUtility / RLCraft version tag in the corner.",
                Category.HUD, Compat.LOCAL,
                () -> FeatureConfig.hudWatermark, v -> FeatureConfig.hudWatermark = v);
        f("Module List", "Lists every active module down the right-hand side.",
                Category.HUD, Compat.LOCAL,
                () -> FeatureConfig.hudModuleList, v -> FeatureConfig.hudModuleList = v);
        f("Stats Line", "FPS, ping, coordinates and current dimension.",
                Category.HUD, Compat.LOCAL,
                () -> FeatureConfig.hudStats, v -> FeatureConfig.hudStats = v);
        f("Target Info", "Health bar and distance readout for your current Kill Aura target.",
                Category.HUD, Compat.LOCAL,
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

    public static List<Setting> settingsFor(Category c) {
        List<Setting> out = new ArrayList<>();
        for (Setting s : SETTINGS) if (s.category == c) out.add(s);
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

    /** Enabled modules worth showing in the HUD array list (HUD toggles themselves excluded). */
    public static List<Feature> activeForHud() {
        List<Feature> out = new ArrayList<>();
        for (Feature f : FEATURES) {
            if (f.category == Category.HUD || f.category == Category.TOOLS) continue;
            if (f.name.startsWith("KA: ") || f.name.startsWith("Auto Loot: ")) continue;
            if (f.isEnabled()) out.add(f);
        }
        return out;
    }
}
