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
    public static boolean antiKnockback = false;
    public static double antiKnockbackHorizontal = 0.0D;
    public static double antiKnockbackVertical = 0.0D;
    public static boolean firstAidAutoHeal = true;
    public static boolean fastTriage = true;
    /** Totem hot-swap triggers at this much vanilla health (or earlier on First Aid criticals).
     *  Kept low on purpose: too high and the totem lives in the off-hand permanently. */
    public static double totemEquipAtHealth = 6.0D;
    public static boolean autoArmor = false;
    public static boolean autoEat = false;
    public static int autoEatThreshold = 12;
    public static boolean autoRespawn = false;
    /** Extend interaction and attack reach client-side. Vanilla servers trust the client here. */
    public static boolean reachEnabled = false;
    public static double reachBlocks = 5.0D;

    // ------------------------------------------------------------- Movement
    public static boolean noFall = true;
    public static boolean stepSpeed = true;
    public static boolean creativeFly = false;
    /** Fly speed re-applied each tick, since the resync also resets it. */
    public static double flySpeed = 0.05D;
    /** Bleed off elytra speed before a wall so kinetic damage stays under its threshold. */
    public static boolean noFallKinetic = true;
    public static boolean noSlowdown = true;
    public static boolean waterWalk = false;
    /** Counter Ice and Fire sirens: auto-equip earplugs, or auto-evade while charmed. */
    public static boolean sirenGuard = true;

    // -------------------------------------------------------------- Exploits
    public static boolean autoLockpick = true;
    public static boolean autoReforge = true;
    public static String targetQuality = "Godly / Legendary";
    public static int customLevelTarget = 10;
    public static boolean clientDebuffNeutralizer = true;
    public static boolean clientItemVacuum = false;
    public static boolean reskillableBypass = true;
    public static boolean fastMine = true;
    /** 0 = Fast (no break delay), 1 = Instant (block progress completes every tick). */
    public static int fastMineMode = 0;

    // ------------------------------------------------- Ice and Fire packets
    /** Real weapon attack on the crosshair target at long range. */
    public static boolean iafLongshot = false;
    public static double iafLongshotRange = 50.0D;
    /** Arbitrary-damage packet on the crosshair target. */
    public static boolean iafExecute = false;
    public static double iafExecuteDamage = 100.0D;
    public static int iafExecuteInterval = 20;
    public static double iafExecuteRange = 50.0D;
    /** Remote petrify - 1.7.1 applies the stone flag with no held item needed. */
    public static boolean iafGorgonGaze = false;
    /** Release statues instead of creating them. */
    public static boolean iafGorgonUnpetrify = false;
    public static double iafGorgonRange = 64.0D;
    /** Flip every nearby siren's singing flag off. */
    public static boolean iafSirenSilencer = false;
    public static double iafSirenRadius = 48.0D;
    /** Set/strip armor on any dragon on the crosshair (no ownership check server-side). */
    public static boolean iafDragonSmith = false;
    /** Armor grade applied by Dragon Smith: 0 none, 1 iron, 2 gold, 3 diamond. */
    public static int iafDragonSmithArmor = 3;
    public static double iafDragonSmithRange = 64.0D;

    public static boolean simpleDifficultyAutoHydrate = true;
    /** Never auto-drink from dirty sources except in a thirst emergency. */
    public static boolean simpleDifficultySafeWater = true;
    /** Suppress EnhancedVisuals' low-thirst screen blur. */
    public static boolean removeThirstBlur = true;
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
    /** Master toggles for the two custom lists; without these they could only be turned off by
     *  emptying the list, which loses the entries. */
    public static boolean espCustomEntitiesOn = true;
    public static boolean espCustomBlocksOn = true;

    /** Extra entity ids to highlight, comma separated. Supports "iceandfire:*" prefix matching. */
    public static String espCustomEntities = "iceandfire:dragon_fire,iceandfire:dragon_ice,iceandfire:cyclops,iceandfire:hydra,iceandfire:dread_lich,iceandfire:gorgon,*dragon_skull*,*dragonskull*,*dragon_skeleton*";
    /** Extra block ids to highlight as containers/points of interest. */
    public static String espCustomBlocks = "waystones:waystone,minecraft:end_portal_frame,minecraft:beacon,iceandfire:dragonforge_fire_core,iceandfire:dragonforge_ice_core,*dragon_skull*,*dragonskull*,*dragonegg*";

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
    /** The opposite of "Only My Drops": never pull items you dropped/threw yourself. */
    public static boolean magnetIgnoreMine = false;
    /** When set, only these item ids are pulled. Empty = pull everything. */
    public static String magnetWhitelist = "";
    /** Never pull these item ids. Applied after the whitelist. */
    public static String magnetBlacklist = "minecraft:rotten_flesh,minecraft:poisonous_potato";

    // ---------------------------------------------------------------- Locks


    // ------------------------------------------------------------- Click aura
    /** One swing hits everything around you. Input-driven, not autonomous. */
    public static boolean clickAura = false;
    public static double clickAuraRange = 4.5D;
    public static int clickAuraMaxTargets = 8;
    public static int clickAuraCooldown = 0;
    public static boolean clickAuraRespectCooldown = true;
    public static boolean clickAuraHitPlayers = false;
    public static boolean clickAuraHitPassive = false;
    public static boolean clickAuraHitTamed = false;

    // ------------------------------------------------------------- ESP style
    /**
     * Per-category render style, one entry per EspRenderHelper.Kind in ordinal order.
     * 0 = box, 1 = corner brackets, 2 = filled + outline, 3 = ground footprint,
     * 4 = outline (wireframe trace of the real model, living entities only).
     * Stored as a comma separated string so the reflective config writer can persist it.
     */
    // CHEST,SPAWNER,WAYSTONE,CONTAINER,CUSTOM_BLOCK,BOSS,HOSTILE,PLAYER,ITEM,MODDED,CUSTOM_ENTITY
    // Living kinds default to Outline(4); blocks and items to Box(0). ITEM was Footprint,
    // which is a 0.02-block-tall sliver and looked exactly like the feature being broken.
    public static String espStyles = "0,0,0,0,0,4,4,4,0,4,4";

    /** Parsed view of {@link #espStyles}, rebuilt only when the string changes. */
    private static String cachedStyleRaw = null;
    private static int[] cachedStyles = new int[0];

    public static int[] espStyleArray() {
        String raw = espStyles == null ? "" : espStyles;
        if (!raw.equals(cachedStyleRaw)) {
            String[] parts = raw.split(",");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    out[i] = Integer.parseInt(parts[i].trim());
                } catch (NumberFormatException e) {
                    out[i] = 0;
                }
            }
            cachedStyles = out;
            cachedStyleRaw = raw;
        }
        return cachedStyles;
    }

    /** Cycle one category's style, growing the list if a new Kind was added. */
    public static void cycleEspStyle(int index, int delta) {
        int[] cur = espStyleArray();
        int len = Math.max(cur.length, index + 1);
        int[] next = new int[len];
        System.arraycopy(cur, 0, next, 0, cur.length);
        next[index] = ((next[index] + delta) % 5 + 5) % 5;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            if (i > 0) sb.append(',');
            sb.append(next[i]);
        }
        espStyles = sb.toString();
    }
    public static double espLineWidth = 1.5D;
    /** Master switch for the wireframe model outline used by style 4. */
    public static boolean espModelOutline = true;
    /** Outline thickness in pixels. Real line rasterising, so this is exact. */
    public static double espOutlineWidth = 2.0D;
    /** Draw the model outline through terrain. */
    public static boolean espOutlineThroughWalls = true;

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

    /** Bumped when new default list entries are added; see migrate(). */
    public static int configVersion = 0;
    private static final int CURRENT_CONFIG_VERSION = 2;

    /**
     * Appends newly shipped default patterns to existing configs.
     *
     * <p>Defaults only apply to a config file that does not exist yet, so anyone who had already
     * saved kept the old ESP lists and never received later additions - which is why the dragon
     * skull patterns did not appear for existing users.</p>
     */
    private static void migrate() {
        if (configVersion >= CURRENT_CONFIG_VERSION) return;
        if (configVersion < 2) {
            for (String entry : new String[]{"*dragon_skull*", "*dragonskull*", "*dragon_head*",
                    "*dragon_skeleton*", "*dragonegg*", "*dragon_egg*"}) {
                espCustomBlocks = TargetList.add(espCustomBlocks, entry);
                espCustomEntities = TargetList.add(espCustomEntities, entry);
            }
        }
        configVersion = CURRENT_CONFIG_VERSION;
        saveConfig();
    }

    // ------------------------------------------------------------ Persistence
    private static File configFile;

    /** Called from pre-init with Forge's config directory (works on client AND dedicated server). */
    public static void init(File configDir) {
        configFile = new File(configDir, "rlutility_features.cfg");
        loadConfig();
        migrate();
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

    /** Absolute path of the last successful write, or the failure reason. */
    public static volatile String lastSaveResult = "not saved yet";

    public static boolean saveConfig() {
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
            // Previously this swallowed every exception, so a failed write looked identical to a
            // successful one. Report the real path and size instead.
            lastSaveResult = file.getAbsolutePath() + " (" + props.size() + " settings, "
                    + file.length() + " bytes)";
            return true;
        } catch (Exception e) {
            lastSaveResult = "FAILED: " + e;
            return false;
        }
    }
}
