package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Click aura - one swing hits everything around you.
 *
 * <p>This is deliberately <em>not</em> a kill aura. Nothing happens unless you actually click, so
 * there is no autonomous attacking and no fixed timer pattern: the trigger is your real input. What
 * it removes is the need to line up a hitbox, which is the part RLCraft's cramped melee makes
 * miserable.</p>
 *
 * <p>On each swing it collects every valid living target inside {@link FeatureConfig#clickAuraRange}
 * in a full sphere - no facing requirement, hence "360" - sorts them nearest-first, and dispatches a
 * real attack at each up to a configurable cap.</p>
 *
 * <p>Every hit is dispatched through the vanilla {@code playerController.attackEntity} path, i.e.
 * a genuine server-side attack packet - the server still applies its own reach and damage rules,
 * so an absurd range simply gets ignored rather than silently doing nothing.</p>
 *
 * <p>Note on locked weapons: Reskillable's weapon lock is enforced by the server on the attack
 * event, and every client-side packet path tested against it was rejected, so this deliberately
 * does not pretend to bypass it. Locked weapons need their levels bought (see the Skills tab).</p>
 */
public class ClickAuraHandler {

    private static int swings = 0;
    private static int lastHitCount = 0;
    private int cooldown = 0;

    public static int getSwingCount() {
        return swings;
    }

    public static int getLastHitCount() {
        return lastHitCount;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onMouse(MouseEvent event) {
        if (!FeatureConfig.clickAura) return;
        if (event.getButton() != 0 || !event.isButtonstate()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;
        if (cooldown > 0) return;

        // Respect the vanilla swing timer so this cannot outpace legitimate attack speed.
        if (FeatureConfig.clickAuraRespectCooldown
                && mc.player.getCooledAttackStrength(0.0F) < 0.9F) {
            return;
        }

        List<EntityLivingBase> targets = findTargets(mc.player);
        if (targets.isEmpty()) return;

        int hit = 0;
        for (EntityLivingBase target : targets) {
            if (hit >= Math.max(1, FeatureConfig.clickAuraMaxTargets)) break;
            if (mc.playerController != null) {
                mc.playerController.attackEntity(mc.player, target);
            }
            hit++;
        }

        if (hit > 0) {
            mc.player.swingArm(EnumHand.MAIN_HAND);
            swings++;
            lastHitCount = hit;
            cooldown = Math.max(0, FeatureConfig.clickAuraCooldown);
        }
    }

    @SubscribeEvent
    public void onTick(net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.fml.common.gameevent.TickEvent.Phase.END && cooldown > 0) {
            cooldown--;
        }
    }

    /** Everything hittable in a sphere around the player, nearest first. */
    private static List<EntityLivingBase> findTargets(EntityPlayerSP player) {
        double range = Math.max(1.0D, Math.min(12.0D, FeatureConfig.clickAuraRange));
        AxisAlignedBB box = player.getEntityBoundingBox().grow(range);

        List<EntityLivingBase> out = new ArrayList<>();
        for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, box)) {
            if (!(entity instanceof EntityLivingBase) || entity.isDead) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.getHealth() <= 0.0F) continue;
            if (player.getDistance(living) > range) continue;   // sphere, not the AABB corner
            if (!isValidTarget(player, living)) continue;
            out.add(living);
        }
        out.sort(Comparator.comparingDouble(player::getDistance));
        return out;
    }

    private static boolean isValidTarget(EntityPlayerSP player, EntityLivingBase target) {
        if (target == player) return false;
        if (target instanceof EntityArmorStand) return false;
        if (target instanceof EntityPlayer && !FeatureConfig.clickAuraHitPlayers) return false;
        if (!FeatureConfig.clickAuraHitTamed && target instanceof EntityTameable
                && ((EntityTameable) target).isTamed()) {
            return false;
        }
        if (!FeatureConfig.clickAuraHitPassive && !(target instanceof net.minecraft.entity.monster.IMob)
                && !(target instanceof EntityPlayer)) {
            return false;
        }
        return true;
    }

    static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
