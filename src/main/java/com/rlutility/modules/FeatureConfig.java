package com.rlutility.modules;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Properties;
import net.minecraft.client.Minecraft;

public class FeatureConfig {

    // Combat & Defense
    public static boolean autoCriticals = true;
    public static boolean noFall = true;
    public static boolean noSlowdown = true;
    public static boolean fastTriage = true;
    public static boolean firstAidAutoHeal = true;
    public static boolean autoTriggerbot = false;
    public static boolean levelDamageBypass = true;

    // Movement & Exploration
    public static boolean stepSpeed = true;
    public static boolean creativeFly = false;

    // RLCraft Specific Exploits
    public static boolean autoLockpick = true;
    public static boolean autoReforge = true;
    public static String targetQuality = "Godly / Legendary";
    public static int customLevelTarget = 10;
    public static boolean clientDebuffNeutralizer = true;
    public static boolean clientItemVacuum = false;
    public static boolean reskillableBypass = true;
    public static boolean fastMine = true;
    public static boolean simpleDifficultyAutoHydrate = true;

    // ESP / Visuals
    public static boolean espChests = true;
    public static boolean espSpawners = true;
    public static boolean espWaystones = true;
    public static boolean espDragons = true;

    private static File getConfigFile() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/rlutility_features.cfg");
    }

    public static void loadConfig() {
        try {
            File file = getConfigFile();
            if (!file.exists()) return;
            Properties props = new Properties();
            try (FileReader reader = new FileReader(file)) {
                props.load(reader);
            }
            autoCriticals = Boolean.parseBoolean(props.getProperty("autoCriticals", String.valueOf(autoCriticals)));
            noFall = Boolean.parseBoolean(props.getProperty("noFall", String.valueOf(noFall)));
            noSlowdown = Boolean.parseBoolean(props.getProperty("noSlowdown", String.valueOf(noSlowdown)));
            fastTriage = Boolean.parseBoolean(props.getProperty("fastTriage", String.valueOf(fastTriage)));
            firstAidAutoHeal = Boolean.parseBoolean(props.getProperty("firstAidAutoHeal", String.valueOf(firstAidAutoHeal)));
            autoTriggerbot = Boolean.parseBoolean(props.getProperty("autoTriggerbot", String.valueOf(autoTriggerbot)));
            levelDamageBypass = Boolean.parseBoolean(props.getProperty("levelDamageBypass", String.valueOf(levelDamageBypass)));
            stepSpeed = Boolean.parseBoolean(props.getProperty("stepSpeed", String.valueOf(stepSpeed)));
            creativeFly = Boolean.parseBoolean(props.getProperty("creativeFly", String.valueOf(creativeFly)));
            autoLockpick = Boolean.parseBoolean(props.getProperty("autoLockpick", String.valueOf(autoLockpick)));
            autoReforge = Boolean.parseBoolean(props.getProperty("autoReforge", String.valueOf(autoReforge)));
            targetQuality = props.getProperty("targetQuality", targetQuality);
            customLevelTarget = Integer.parseInt(props.getProperty("customLevelTarget", String.valueOf(customLevelTarget)));
            clientDebuffNeutralizer = Boolean.parseBoolean(props.getProperty("clientDebuffNeutralizer", String.valueOf(clientDebuffNeutralizer)));
            clientItemVacuum = Boolean.parseBoolean(props.getProperty("clientItemVacuum", String.valueOf(clientItemVacuum)));
            reskillableBypass = Boolean.parseBoolean(props.getProperty("reskillableBypass", String.valueOf(reskillableBypass)));
            fastMine = Boolean.parseBoolean(props.getProperty("fastMine", String.valueOf(fastMine)));
            simpleDifficultyAutoHydrate = Boolean.parseBoolean(props.getProperty("simpleDifficultyAutoHydrate", String.valueOf(simpleDifficultyAutoHydrate)));
            espChests = Boolean.parseBoolean(props.getProperty("espChests", String.valueOf(espChests)));
            espSpawners = Boolean.parseBoolean(props.getProperty("espSpawners", String.valueOf(espSpawners)));
            espWaystones = Boolean.parseBoolean(props.getProperty("espWaystones", String.valueOf(espWaystones)));
            espDragons = Boolean.parseBoolean(props.getProperty("espDragons", String.valueOf(espDragons)));
        } catch (Exception ignored) {}
    }

    public static void saveConfig() {
        try {
            File file = getConfigFile();
            file.getParentFile().mkdirs();
            Properties props = new Properties();
            props.setProperty("autoCriticals", String.valueOf(autoCriticals));
            props.setProperty("noFall", String.valueOf(noFall));
            props.setProperty("noSlowdown", String.valueOf(noSlowdown));
            props.setProperty("fastTriage", String.valueOf(fastTriage));
            props.setProperty("firstAidAutoHeal", String.valueOf(firstAidAutoHeal));
            props.setProperty("autoTriggerbot", String.valueOf(autoTriggerbot));
            props.setProperty("levelDamageBypass", String.valueOf(levelDamageBypass));
            props.setProperty("stepSpeed", String.valueOf(stepSpeed));
            props.setProperty("creativeFly", String.valueOf(creativeFly));
            props.setProperty("autoLockpick", String.valueOf(autoLockpick));
            props.setProperty("autoReforge", String.valueOf(autoReforge));
            props.setProperty("targetQuality", targetQuality);
            props.setProperty("customLevelTarget", String.valueOf(customLevelTarget));
            props.setProperty("clientDebuffNeutralizer", String.valueOf(clientDebuffNeutralizer));
            props.setProperty("clientItemVacuum", String.valueOf(clientItemVacuum));
            props.setProperty("reskillableBypass", String.valueOf(reskillableBypass));
            props.setProperty("fastMine", String.valueOf(fastMine));
            props.setProperty("simpleDifficultyAutoHydrate", String.valueOf(simpleDifficultyAutoHydrate));
            props.setProperty("espChests", String.valueOf(espChests));
            props.setProperty("espSpawners", String.valueOf(espSpawners));
            props.setProperty("espWaystones", String.valueOf(espWaystones));
            props.setProperty("espDragons", String.valueOf(espDragons));
            try (FileWriter writer = new FileWriter(file)) {
                props.store(writer, "RLUtility Config File");
            }
        } catch (Exception ignored) {}
    }
}
