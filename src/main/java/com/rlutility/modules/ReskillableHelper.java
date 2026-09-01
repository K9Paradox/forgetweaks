package com.rlutility.modules;

import codersafterdark.reskillable.api.ReskillableRegistries;
import codersafterdark.reskillable.api.data.PlayerData;
import codersafterdark.reskillable.api.data.PlayerDataHandler;
import codersafterdark.reskillable.api.data.PlayerSkillInfo;
import codersafterdark.reskillable.api.requirement.RequirementCache;
import codersafterdark.reskillable.api.skill.Skill;
import codersafterdark.reskillable.api.unlockable.Unlockable;
import codersafterdark.reskillable.base.ToolTipHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

public class ReskillableHelper {

    private static boolean modLoaded = false;
    private static Field tooltipEnabledField = null;

    static {
        modLoaded = Loader.isModLoaded("reskillable");
        if (modLoaded) {
            try {
                tooltipEnabledField = ToolTipHandler.class.getDeclaredField("enabled");
                tooltipEnabledField.setAccessible(true);
            } catch (Throwable ignored) {
            }
        }
    }

    private int tickCounter = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!modLoaded || !FeatureConfig.reskillableBypass || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        tickCounter++;
        if (tickCounter % 20 == 0) {
            try {
                PlayerData data = PlayerDataHandler.get(player);
                if (data != null) {
                    if (ReskillableRegistries.SKILLS != null) {
                        for (Skill skill : ReskillableRegistries.SKILLS.getValuesCollection()) {
                            PlayerSkillInfo info = data.getSkillInfo(skill);
                            if (info != null && info.getLevel() < 32) {
                                info.setLevel(32);
                            }
                        }
                    }
                    if (ReskillableRegistries.UNLOCKABLES != null) {
                        for (Unlockable unlockable : ReskillableRegistries.UNLOCKABLES.getValuesCollection()) {
                            if (unlockable.getParentSkill() != null) {
                                PlayerSkillInfo info = data.getSkillInfo(unlockable.getParentSkill());
                                if (info != null && !info.isUnlocked(unlockable)) {
                                    info.unlock(unlockable, player);
                                }
                            }
                        }
                    }
                    RequirementCache cache = RequirementCache.getCache(player);
                    if (cache != null) {
                        cache.forceClear();
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // Un-cancel events intercepted by Reskillable's LevelLockHandler
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLivingAttack(LivingAttackEvent event) {
        if (modLoaded && FeatureConfig.reskillableBypass && event.isCanceled()) {
            event.setCanceled(false);
        }
    }
}
