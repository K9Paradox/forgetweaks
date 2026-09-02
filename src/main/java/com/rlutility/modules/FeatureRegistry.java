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
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoArmor, v -> FeatureConfig.autoArmor = v);
        f("Auto Eat", "Eats real food (never rotten/poisonous) when hunger drops, then restores your slot.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoEat, v -> FeatureConfig.autoEat = v);
        f("Auto Respawn", "Instantly sends the respawn packet on the death screen.",
                Category.SURVIVAL, Compat.SERVER,
                () -> FeatureConfig.autoRespawn, v -> FeatureConfig.autoRespawn = v);

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
        f("Fly: Survive Damage", "Re-assert flight after the ability resync that damage triggers. "
                + "Without this you drop out of the sky whenever something hits you.",
                Category.MOVEMENT, Compat.LOCAL,
                () -> FeatureConfig.flyPersistThroughDamage, v -> FeatureConfig.flyPersistThroughDamage = v);
        s("Fly Speed", "Flight speed, re-applied every tick because the resync resets it.",
                Category.MOVEMENT,
                () -> String.format("%.2f", FeatureConfig.flySpeed),
                d -> FeatureConfig.flySpeed = clamp(FeatureConfig.flySpeed + 0.01 * d, 0.01, 1.0));
        f("Anti-Kinetic", "Bleed off elytra speed before a wall. Kinetic damage is computed on the "
                + "server from the speed you lose, so this is mitigation, not immunity.",
                Category.MOVEMENT, Compat.SERVER,
                () -> FeatureConfig.noFallKinetic, v -> FeatureConfig.noFallKinetic = v);

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
                Category.SURVIVAL, Compat.MODDED,
                () -> FeatureConfig.simpleDifficultyAutoHydrate, v -> FeatureConfig.simpleDifficultyAutoHydrate = v);
        f("Item Vacuum", "Pulls nearby drops in. Authoritative only when ItemPhysic is installed.",
                Category.EXPLOITS, Compat.MODDED,
                () -> FeatureConfig.clientItemVacuum, v -> FeatureConfig.clientItemVacuum = v);
        f("Debuff Neutralizer", "Strips screen-shake and nuisance debuffs from your client render.",
                Category.SURVIVAL, Compat.LOCAL,
                () -> FeatureConfig.clientDebuffNeutralizer, v -> FeatureConfig.clientDebuffNeutralizer = v);

        s("Loot Delay", "Ticks between each shift-click. Lower is faster but noisier.", Category.EXPLOITS,
                () -> FeatureConfig.autoLootDelay + "t",
                d -> FeatureConfig.autoLootDelay = (int) clamp(FeatureConfig.autoLootDelay + d, 0, 10));

        // -------------------------------------------------------------- TOOLS
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

        f("Client Lock Un-cancel", "REQUIRED for locked tools. Reskillable runs on your client too and "
                + "cancels mining/interaction locally, so the packet never even reaches the server. "
                + "This reverts that. The server still decides the outcome.",
                Category.SKILLS, Compat.LOCAL,
                () -> FeatureConfig.reskillableBypass, v -> FeatureConfig.reskillableBypass = v);

        f("Packet Attack Bypass", "THE weapon lock fix. Attacks via RLCombat/Spartan/Ice&Fire attack "
                + "packets instead of the vanilla path, which Reskillable's lock does not stop. Keeps "
                + "full damage and enchantments.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.weaponPacketBypass, v -> FeatureConfig.weaponPacketBypass = v);
        f("Bypass: Hold To Attack", "Keep attacking while the button is held down.",
                Category.COMBAT, Compat.LOCAL,
                () -> FeatureConfig.weaponBypassHeldAttack, v -> FeatureConfig.weaponBypassHeldAttack = v);
        f("Click Aura", "One swing hits every valid target around you - no hitbox lining up. "
                + "Input-driven: nothing happens unless you actually click.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.clickAura, v -> FeatureConfig.clickAura = v);
        s("Aura Range", "Sphere radius around you. The server still enforces its own reach.",
                Category.COMBAT,
                () -> String.format("%.1fm", FeatureConfig.clickAuraRange),
                d -> FeatureConfig.clickAuraRange = clamp(FeatureConfig.clickAuraRange + 0.5 * d, 1.0, 12.0));
        s("Aura Max Targets", "Cap on how many entities one swing hits.", Category.COMBAT,
                () -> String.valueOf(FeatureConfig.clickAuraMaxTargets),
                d -> FeatureConfig.clickAuraMaxTargets = (int) clamp(FeatureConfig.clickAuraMaxTargets + d, 1, 32));
        f("Aura: Respect Cooldown", "Only fire on a charged swing, matching vanilla attack speed.",
                Category.COMBAT, Compat.LOCAL,
                () -> FeatureConfig.clickAuraRespectCooldown, v -> FeatureConfig.clickAuraRespectCooldown = v);
        f("Aura: Hit Players", "Include other players.", Category.COMBAT, Compat.LOCAL,
                () -> FeatureConfig.clickAuraHitPlayers, v -> FeatureConfig.clickAuraHitPlayers = v);
        f("Aura: Hit Passive", "Include passive mobs.", Category.COMBAT, Compat.LOCAL,
                () -> FeatureConfig.clickAuraHitPassive, v -> FeatureConfig.clickAuraHitPassive = v);
        f("Aura: Hit Tamed", "Include your own tamed animals.", Category.COMBAT, Compat.LOCAL,
                () -> FeatureConfig.clickAuraHitTamed, v -> FeatureConfig.clickAuraHitTamed = v);

        f("Bypass: RLCombat Hook", "Attack through RLCombatCompat.attackEntityFromClient - the call the "
                + "working nunchaku path uses. This is the one that actually lands.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.bypassUseRlcombatHook, v -> FeatureConfig.bypassUseRlcombatHook = v);
        f("Bypass: Raw Packets", "Also send the raw mod attack packets. They do not work alone; only "
                + "useful for experimenting.",
                Category.COMBAT, Compat.SERVER,
                () -> FeatureConfig.bypassExtraPackets, v -> FeatureConfig.bypassExtraPackets = v);
        f("Reskillable Auto-Buy", "Automatically spend XP levels to unlock the item you are holding.",
                Category.SKILLS, Compat.MODDED,
                () -> FeatureConfig.reskillableAutoBuy, v -> FeatureConfig.reskillableAutoBuy = v);
        s("XP Reserve", "Never spend below this many XP levels when auto-buying.", Category.SKILLS,
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

        f("Model Outlines", "Trace the real model in wireframe for categories set to Outline. "
                + "Unlike the vanilla glow this is plain line rasterising, so thickness is exact "
                + "and there is no halo.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espModelOutline, v -> FeatureConfig.espModelOutline = v);
        s("Outline Thickness", "Model outline width in pixels.", Category.VISUALS,
                () -> String.format("%.1fpx", FeatureConfig.espOutlineWidth),
                d -> FeatureConfig.espOutlineWidth = clamp(FeatureConfig.espOutlineWidth + 0.5 * d, 0.5, 10.0));
        f("Outline Through Walls", "Draw model outlines through terrain.",
                Category.VISUALS, Compat.LOCAL,
                () -> FeatureConfig.espOutlineThroughWalls, v -> FeatureConfig.espOutlineThroughWalls = v);

        s("ESP Line Width", "Outline thickness.", Category.VISUALS,
                () -> String.format("%.1f", FeatureConfig.espLineWidth),
                d -> FeatureConfig.espLineWidth = clamp(FeatureConfig.espLineWidth + 0.5 * d, 0.5, 6.0));
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
