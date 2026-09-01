package com.rlutility.modules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Properties;

/**
 * Central feature/state holder.
 *
 * <p>Persistence is fully reflective: every {@code public static} non-final primitive or String
 * field declared here is written to / read from {@code config/rlutility_features.cfg}. Adding a new
 * option therefore only requires declaring the field (and registering it in {@link FeatureRegistry}
 * if it should appear in the GUI).</p>
 *
 * <p>IMPORTANT: this class must stay free of any {@code net.minecraft.client} reference so that it
 * can be safely touched from a dedicated server without a {@code NoClassDefFoundError}.</p>
 */
public final class FeatureConfig {

    private FeatureConfig() {}

    // ---------------------------------------------------------------- Combat
    public static boolean autoCriticals = true;
    public static boolean levelDamageBypass = true;
    public static boolean autoTriggerbot = false;
    public static boolean killAura = false;
    public static boolean killAuraPlayers = false;
    public static boolean killAuraAnimals = false;
    public static boolean killAuraWallCheck = true;
    public static boolean killAuraRotations = true;
    public static double killAuraRange = 4.2D;
    public static int killAuraCps = 8;
    public static boolean antiKnockback = false;
    public static double antiKnockbackHorizontal = 0.0D;
    public static double antiKnockbackVertical = 0.0D;
    public static boolean firstAidAutoHeal = true;
    public static boolean fastTriage = true;
    public static boolean autoArmor = false;
    public static boolean autoEat = false;
    public static int autoEatThreshold = 12;
    public static boolean autoRespawn = false;

    // ------------------------------------------------------------- Movement
    public static boolean noFall = true;
    public static boolean stepSpeed = true;
    public static boolean creativeFly = false;
    public static boolean noSlowdown = true;
    public static boolean waterWalk = false;
    public static boolean timerEnabled = false;
    public static double timerSpeed = 1.5D;

    // -------------------------------------------------------------- Exploits
    public static boolean autoLockpick = true;
    public static boolean autoReforge = true;
    public static String targetQuality = "Godly / Legendary";
    public static int customLevelTarget = 10;
    public static boolean clientDebuffNeutralizer = true;
    public static boolean clientItemVacuum = false;
    public static boolean reskillableBypass = true;
    public static boolean fastMine = true;
    public static boolean simpleDifficultyAutoHydrate = true;
    public static boolean autoLoot = false;
    public static int autoLootDelay = 2;
    public static boolean autoLootCloseWhenDone = true;
    public static boolean levelUpPreserveClass = true;
    public static boolean levelUpClampToMax = true;

    // --------------------------------------------------------------- Visuals
    public static boolean espChests = true;
    public static boolean espSpawners = true;
    public static boolean espWaystones = true;
    public static boolean espDragons = true;
    public static boolean espHostiles = false;
    public static boolean espPlayers = false;
    public static boolean espItems = false;
    public static boolean espTracers = false;
    public static int espRange = 64;

    /** Highlight every mob that is not from vanilla Minecraft. */
    public static boolean espModdedMobs = false;
    /** Highlight anything with an inventory, not just vanilla chests. */
    public static boolean espAllContainers = false;
    /** Extra entity ids to highlight, comma separated. Supports "iceandfire:*" prefix matching. */
    public static String espCustomEntities = "iceandfire:dragon_fire,iceandfire:dragon_ice,iceandfire:cyclops,iceandfire:hydra";
    /** Extra block ids to highlight as containers/points of interest. */
    public static String espCustomBlocks = "waystones:waystone,minecraft:end_portal_frame,minecraft:beacon";

    // ---------------------------------------------------------------- XRay
    public static boolean xrayEnabled = false;
    /** Blocks XRay looks for. Editable in game; supports "modid:*" prefix entries. */
    public static String xrayBlocks = "minecraft:diamond_ore,minecraft:emerald_ore,minecraft:gold_ore,"
            + "minecraft:iron_ore,minecraft:coal_ore,minecraft:lapis_ore,minecraft:redstone_ore,"
            + "minecraft:lit_redstone_ore,minecraft:quartz_ore,minecraft:mob_spawner,minecraft:chest,"
            + "minecraft:trapped_chest,minecraft:end_portal_frame,iceandfire:silver_ore,"
            + "iceandfire:sapphire_ore,iceandfire:copper_ore,iceandfire:gold_pile,iceandfire:silver_pile,"
            + "iceandfire:copper_pile,iceandfire:chunk_of_amythest";
    /** Scan radius in blocks. Cost grows with the cube of this, so keep it modest. */
    public static int xrayRange = 28;
    /** Ticks between rescans. Lower = more responsive, more CPU. */
    public static int xrayRescanTicks = 30;
    public static boolean xrayTracers = false;

    // ---------------------------------------------------------- Item magnet
    public static double magnetRadius = 6.0D;
    public static double magnetSpeed = 0.4D;
    /** Only pull items that are already flagged as yours (thrown/dropped by you). */
    public static boolean magnetOnlyMine = false;
    /** When set, only these item ids are pulled. Empty = pull everything. */
    public static String magnetWhitelist = "";
    /** Never pull these item ids. Applied after the whitelist. */
    public static String magnetBlacklist = "minecraft:rotten_flesh,minecraft:poisonous_potato";

    // ----------------------------------------------------------- Reskillable
    /** Automatically spend XP on Reskillable levels needed by the item you are holding. */
    public static boolean reskillableAutoBuy = false;
    /** Keep this many XP levels in reserve when auto-buying. */
    public static int reskillableXpReserve = 0;

    // ------------------------------------------------------------ Dupe/desync
    /** Set by /dupe between the "relog for a clean rollback point" step and re-joining. */
    public static boolean dupePendingArm = false;

    // ------------------------------------------------------------------- HUD
    public static boolean hudEnabled = true;
    public static boolean hudWatermark = true;
    public static boolean hudModuleList = true;
    public static boolean hudStats = true;
    public static boolean hudTargetInfo = true;

    // ------------------------------------------------------------ Persistence
    private static File configFile;

    /** Called from pre-init with Forge's config directory (works on client AND dedicated server). */
    public static void init(File configDir) {
        configFile = new File(configDir, "rlutility_features.cfg");
        loadConfig();
    }

    private static File getConfigFile() {
        if (configFile == null) {
            // Fallback for very early / test access.
            configFile = new File("config/rlutility_features.cfg");
        }
        return configFile;
    }

    private static Field[] persistedFields() {
        Field[] all = FeatureConfig.class.getDeclaredFields();
        int n = 0;
        for (Field f : all) if (isPersisted(f)) n++;
        Field[] out = new Field[n];
        int i = 0;
        for (Field f : all) if (isPersisted(f)) out[i++] = f;
        return out;
    }

    private static boolean isPersisted(Field f) {
        int m = f.getModifiers();
        if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || Modifier.isFinal(m)) return false;
        Class<?> t = f.getType();
        return t == boolean.class || t == int.class || t == double.class
                || t == float.class || t == long.class || t == String.class;
    }

    public static void loadConfig() {
        try {
            File file = getConfigFile();
            if (!file.exists()) return;
            Properties props = new Properties();
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            }
            for (Field f : persistedFields()) {
                String raw = props.getProperty(f.getName());
                if (raw == null) continue;
                try {
                    Class<?> t = f.getType();
                    if (t == boolean.class) f.setBoolean(null, Boolean.parseBoolean(raw));
                    else if (t == int.class) f.setInt(null, Integer.parseInt(raw.trim()));
                    else if (t == long.class) f.setLong(null, Long.parseLong(raw.trim()));
                    else if (t == double.class) f.setDouble(null, Double.parseDouble(raw.trim()));
                    else if (t == float.class) f.setFloat(null, Float.parseFloat(raw.trim()));
                    else f.set(null, raw);
                } catch (Exception ignored) {
                    // keep the default for this single field
                }
            }
        } catch (Exception ignored) {}
    }

    public static void saveConfig() {
        try {
            File file = getConfigFile();
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Properties props = new Properties();
            for (Field f : persistedFields()) {
                Object v = f.get(null);
                props.setProperty(f.getName(), v == null ? "" : String.valueOf(v));
            }
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "RLUtility feature configuration - RLCraft 2.9.3");
            }
        } catch (Exception ignored) {}
    }
}
