package com.rlutility.modules;

import codersafterdark.reskillable.api.ReskillableRegistries;
import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.data.RequirementHolder;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.base.LevelLockHandler;
import codersafterdark.reskillable.network.MessageLevelUp;
import codersafterdark.reskillable.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reskillable level-lock handling.
 *
 * <h3>Why the old "bypass" could never work for weapons</h3>
 * The previous implementation did two client-side things: it wrote {@code info.setLevel(32)} into
 * the local {@link PlayerData}, and it re-enabled cancelled events at {@code LOWEST} priority. Both
 * are meaningless for combat.
 *
 * <p>{@code LevelLockHandler} enforces weapons through {@code LivingAttackEvent}, and its
 * {@code tellPlayer} helper is guarded by {@code if (player instanceof EntityPlayerMP)} - it sends a
 * {@code MessageLockedItem} packet <em>from the server to you</em>. So the red "you can't use this"
 * warning is proof that the server, holding its own copy of your real skill levels, cancelled the
 * hit. Nothing a client-only handler does can change that. Mining appeared to work because block
 * breaking is heavily client-predicted, so you see the block go even when the server disagrees.</p>
 *
 * <p>Worse, faking the level locally made the client believe the swing was legal, so it kept sending
 * attacks the server discarded - the exact "swing does nothing" symptom.</p>
 *
 * <h3>What actually works</h3>
 * Raise the real levels on the server using Reskillable's own level-up packet. {@code MessageLevelUp}
 * is validated server-side:
 *
 * <pre>
 * if (!info.isCapped()) {
 *     int cost = info.getLevelUpCost();
 *     if (player.experienceLevel &gt;= cost || player.isCreative()) { ...levelUp()... }
 * }
 * </pre>
 *
 * There is no free path - the cost is enforced where we cannot reach it - but the packet carries
 * nothing except a skill name, so we can drive it as fast as we like and buy exactly the levels the
 * held item requires. That is a permanent, server-side unlock rather than a placebo.
 */
public class ReskillableHelper {

    private static final boolean MOD_LOADED = Loader.isModLoaded("reskillable");

    /** Reflection fallback for reading a RequirementHolder's skill map across Reskillable builds. */
    private static Field skillLevelsField = null;
    private static boolean skillLevelsFieldResolved = false;

    private int tickCounter = 0;
    private int lastReportHash = 0;

    // ------------------------------------------------------------------ API

    public static boolean isModLoaded() {
        return MOD_LOADED;
    }

    public static List<Skill> allSkills() {
        List<Skill> out = new ArrayList<>();
        try {
            if (ReskillableRegistries.SKILLS != null) {
                out.addAll(ReskillableRegistries.SKILLS.getValuesCollection());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public static Skill skillByName(String name) {
        if (name == null) return null;
        String key = name.toLowerCase().trim();
        if (key.indexOf(':') < 0) key = "reskillable:" + key;
        try {
            Skill skill = ReskillableRegistries.SKILLS.getValue(new ResourceLocation(key));
            if (skill != null) return skill;
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getLevel(Skill skill) {
        try {
            PlayerData data = PlayerDataHandler.get(Minecraft.getMinecraft().player);
            if (data == null) return 0;
            PlayerSkillInfo info = data.getSkillInfo(skill);
            return info == null ? 0 : info.getLevel();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static int getLevelUpCost(Skill skill) {
        try {
            PlayerData data = PlayerDataHandler.get(Minecraft.getMinecraft().player);
            if (data == null) return -1;
            PlayerSkillInfo info = data.getSkillInfo(skill);
            if (info == null || info.isCapped()) return -1;
            return info.getLevelUpCost();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static boolean isCapped(Skill skill) {
        try {
            PlayerData data = PlayerDataHandler.get(Minecraft.getMinecraft().player);
            PlayerSkillInfo info = data == null ? null : data.getSkillInfo(skill);
            return info == null || info.isCapped();
        } catch (Throwable ignored) {
            return true;
        }
    }

    /**
     * Buys up to {@code levels} levels of {@code skill} through Reskillable's own packet.
     *
     * <p>Each packet is validated independently server-side, so we simulate the same affordability
     * check locally to avoid firing packets that will simply be dropped. Returns how many were
     * actually requested.</p>
     */
    public static int buyLevels(Skill skill, int levels) {
        if (!MOD_LOADED || skill == null || levels <= 0) return 0;
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return 0;

        int bought = 0;
        try {
            PlayerData data = PlayerDataHandler.get(player);
            if (data == null) return 0;
            PlayerSkillInfo info = data.getSkillInfo(skill);
            if (info == null) return 0;

            // Local mirror of the server's own arithmetic so we stop when we genuinely run out.
            int simulatedLevel = info.getLevel();
            int budget = player.experienceLevel - Math.max(0, FeatureConfig.reskillableXpReserve);

            for (int i = 0; i < levels; i++) {
                if (info.isCapped()) break;
                int cost = info.getLevelUpCost();
                if (!player.capabilities.isCreativeMode) {
                    if (cost > budget) break;
                    budget -= cost;
                }
                PacketHandler.INSTANCE.sendToServer(new MessageLevelUp(skill.getRegistryName()));
                bought++;
                simulatedLevel++;
                // The server answers with a data sync; we cannot see the new cost until it lands,
                // so assume the cost climbs by at least one level to stay conservative.
                budget -= 1;
            }
        } catch (Throwable t) {
            chat("\u00a7cReskillable level-up failed: " + t);
        }
        return bought;
    }

    /** Requirements of the item in the main hand, as skill -> required level. */
    public static Map<Skill, Integer> requirementsForHeldItem() {
        Map<Skill, Integer> out = new LinkedHashMap<>();
        if (!MOD_LOADED) return out;
        try {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) return out;
            ItemStack stack = player.getHeldItemMainhand();
            if (stack == null || stack.isEmpty()) return out;

            RequirementHolder holder = LevelLockHandler.getSkillLock(stack);
            if (holder == null || holder.equals(LevelLockHandler.EMPTY_LOCK)) return out;

            // The field moved between Reskillable builds, so resolve it reflectively once.
            if (!skillLevelsFieldResolved) {
                skillLevelsFieldResolved = true;
                for (Field f : holder.getClass().getFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        skillLevelsField = f;
                        break;
                    }
                }
                if (skillLevelsField == null) {
                    for (Field f : holder.getClass().getDeclaredFields()) {
                        if (Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            skillLevelsField = f;
                            break;
                        }
                    }
                }
            }
            if (skillLevelsField == null) return out;

            Object value = skillLevelsField.get(holder);
            if (!(value instanceof Map)) return out;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                if (e.getKey() instanceof Skill && e.getValue() instanceof Integer) {
                    out.put((Skill) e.getKey(), (Integer) e.getValue());
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** True when the server would currently let us use this specific stack. */
    public static boolean canUseStack(ItemStack stack) {
        if (!MOD_LOADED) return true;
        try {
            if (stack == null || stack.isEmpty()) return true;
            PlayerData data = PlayerDataHandler.get(Minecraft.getMinecraft().player);
            if (data == null) return true;
            RequirementHolder holder = LevelLockHandler.getSkillLock(stack);
            return holder == null || data.matchStats(holder);
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Set when the lock check could not be evaluated, so diagnostics can say why. */
    public static volatile String lastLockError = null;
    /** True when the most recent check actually resolved rather than falling back. */
    public static volatile boolean lastLockResolved = false;

    /** True when the server would currently let us use the held item. */
    public static boolean canUseHeldItem() {
        lastLockError = null;
        lastLockResolved = false;
        if (!MOD_LOADED) {
            lastLockError = "reskillable not loaded";
            return true;
        }
        try {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) {
                lastLockError = "no player";
                return true;
            }
            ItemStack stack = player.getHeldItemMainhand();
            if (stack == null || stack.isEmpty()) {
                lastLockResolved = true;
                return true;
            }
            PlayerData data = PlayerDataHandler.get(player);
            if (data == null) {
                lastLockError = "PlayerDataHandler.get returned null";
                return true;
            }
            RequirementHolder holder = LevelLockHandler.getSkillLock(stack);
            if (holder == null) {
                lastLockResolved = true;
                return true;
            }
            lastLockResolved = true;
            return data.matchStats(holder);
        } catch (Throwable t) {
            lastLockError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return true;
        }
    }

    /** Full state dump so a failure can be diagnosed from chat instead of guessed at. */
    public static java.util.List<String> diagnose() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("\u00a76--- Reskillable diagnostics ---");
        out.add("  mod loaded: " + (MOD_LOADED ? "\u00a7ayes" : "\u00a7cno"));
        out.add("  client un-cancel: " + (FeatureConfig.reskillableBypass ? "\u00a7aon" : "\u00a7cOFF")
                + " \u00a77(reverted " + ReskillableClientUnlock.getUnCancelCount() + " cancels)");
        out.add("  packet bypass: " + (FeatureConfig.weaponPacketBypass ? "\u00a7aon" : "\u00a7cOFF")
                + " \u00a77(" + WeaponLockBypassHandler.getAttackCount() + " attacks, last sent: "
                + WeaponLockBypassHandler.getLastResult() + ")");

        if (!MOD_LOADED) return out;

        try {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
            if (player == null) {
                out.add("  \u00a7cno player");
                return out;
            }
            ItemStack stack = player.getHeldItemMainhand();
            String id = (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null)
                    ? "(empty)" : stack.getItem().getRegistryName().toString();
            out.add("  held item: \u00a7f" + id);

            boolean usable = canUseHeldItem();
            out.add("  lock check: " + (lastLockResolved
                    ? (usable ? "\u00a7aunlocked" : "\u00a7cLOCKED")
                    : "\u00a7eUNRESOLVED \u00a77- " + lastLockError));

            java.util.Map<Skill, Integer> req = requirementsForHeldItem();
            if (req.isEmpty()) {
                out.add("  requirements: \u00a77none readable");
            } else {
                for (java.util.Map.Entry<Skill, Integer> e : req.entrySet()) {
                    int have = getLevel(e.getKey());
                    out.add("    " + e.getKey().getName() + ": \u00a7f" + have + "\u00a78/\u00a7e" + e.getValue()
                            + (have >= e.getValue() ? " \u00a7aok" : " \u00a7cshort"));
                }
            }

            StringBuilder levels = new StringBuilder();
            for (Skill skill : allSkills()) {
                if (levels.length() > 0) levels.append("\u00a77, ");
                levels.append("\u00a7f").append(skill.getName()).append(" ").append(getLevel(skill));
            }
            out.add("  levels: " + levels);
            out.add("  xp levels: \u00a7f" + player.experienceLevel);
        } catch (Throwable t) {
            out.add("  \u00a7cdiagnostic failed: " + t);
        }
        return out;
    }

    /** Buys exactly the levels the held item is missing. Returns a human-readable summary. */
    public static String unlockHeldItem() {
        if (!MOD_LOADED) return "\u00a7cReskillable is not loaded.";
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return "\u00a7cNo player.";

        Map<Skill, Integer> required = requirementsForHeldItem();
        if (required.isEmpty()) {
            return canUseHeldItem()
                    ? "\u00a7aThat item has no level lock - you can already use it."
                    : "\u00a7eThat item is locked, but the requirement could not be read "
                        + "(it may be an advancement or a CompatSkills requirement, not a skill level).";
        }

        StringBuilder summary = new StringBuilder();
        int totalBought = 0;
        for (Map.Entry<Skill, Integer> e : required.entrySet()) {
            Skill skill = e.getKey();
            int need = e.getValue();
            int have = getLevel(skill);
            if (have >= need) continue;

            int bought = buyLevels(skill, need - have);
            totalBought += bought;
            if (summary.length() > 0) summary.append("\u00a77, ");
            summary.append("\u00a7f").append(skill.getName()).append(" \u00a77")
                   .append(have).append("\u00a78->\u00a7a").append(have + bought)
                   .append("\u00a78/").append(need);
        }

        if (totalBought == 0) {
            return "\u00a7cNot enough XP levels to buy anything. You have "
                    + player.experienceLevel + " levels.";
        }
        return "\u00a7aBought " + totalBought + " level(s): " + summary
                + "\u00a77 - re-check in a second, the server has to sync back.";
    }

    // ----------------------------------------------------------- auto-buy

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!MOD_LOADED || event.phase != TickEvent.Phase.START) return;
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player == null) return;

        if (++tickCounter % 20 != 0) return;

        if (FeatureConfig.reskillableAutoBuy && !canUseHeldItem()) {
            ItemStack held = player.getHeldItemMainhand();
            int hash = held == null || held.isEmpty() ? 0 : held.getItem().hashCode();
            if (hash != lastReportHash) {
                lastReportHash = hash;
                chat(unlockHeldItem());
            }
        } else if (canUseHeldItem()) {
            lastReportHash = 0;
        }
    }

    private static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
