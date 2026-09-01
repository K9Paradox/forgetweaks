package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/**
 * Server-authoritative kill aura.
 *
 * <p>Everything it does goes through the normal client-to-server pipeline: a look packet
 * ({@link CPacketPlayer.Rotation}) followed by the vanilla attack packet, which means the server
 * validates and applies the hit exactly like a manual swing. Vanilla attack cooldown is respected
 * so RLCraft/RLCombat damage scaling is never wasted.</p>
 */
public class KillAuraHandler {

    /** Current target, exposed for the HUD. */
    public static Entity currentTarget = null;

    private int attackCooldown = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (!FeatureConfig.killAura || player == null || mc.world == null || mc.playerController == null) {
            currentTarget = null;
            return;
        }
        if (mc.currentScreen != null || player.isDead || player.getHealth() <= 0.0F) {
            currentTarget = null;
            return;
        }

        if (attackCooldown > 0) attackCooldown--;

        Entity target = findBestTarget(mc, player);
        currentTarget = target;
        if (target == null) return;

        // Respect the vanilla cooldown so every swing lands at full damage.
        if (player.getCooledAttackStrength(0.0F) < 0.98F) return;
        if (attackCooldown > 0) return;

        // Automated swings should crit too.
        if (FeatureConfig.autoCriticals) {
            AutoCritHandler.triggerCritHop();
        }

        if (FeatureConfig.killAuraRotations) {
            float[] rot = rotationsTo(player, target);
            player.connection.sendPacket(new CPacketPlayer.Rotation(rot[0], rot[1], player.onGround));
        }

        if (FeatureConfig.levelDamageBypass) {
            TriggerbotHandler.dispatchDirectAttackPacket(player, target);
        } else {
            mc.playerController.attackEntity(player, target);
            player.swingArm(EnumHand.MAIN_HAND);
        }
        player.resetCooldown();

        int cps = FeatureConfig.killAuraCps <= 0 ? 1 : FeatureConfig.killAuraCps;
        attackCooldown = Math.max(1, 20 / cps);
    }

    private Entity findBestTarget(Minecraft mc, EntityPlayerSP player) {
        double range = FeatureConfig.killAuraRange;
        AxisAlignedBB box = player.getEntityBoundingBox().grow(range, range, range);
        List<Entity> candidates = mc.world.getEntitiesWithinAABBExcludingEntity(player, box);

        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : candidates) {
            if (!isValidTarget(player, e)) continue;

            double dist = Math.sqrt(player.getDistanceSq(e));
            if (dist > range) continue;
            if (FeatureConfig.killAuraWallCheck && !player.canEntityBeSeen(e)) continue;

            // Prefer whatever is closest to the crosshair, tie-broken by distance.
            double score = angleTo(player, e) * 0.5D + dist;
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    private boolean isValidTarget(EntityPlayerSP self, Entity e) {
        if (!(e instanceof EntityLivingBase) || e == self || e.isDead) return false;
        EntityLivingBase living = (EntityLivingBase) e;
        if (living.getHealth() <= 0.0F) return false;
        if (e instanceof EntityArmorStand) return false;

        if (e instanceof EntityPlayer) {
            EntityPlayer p = (EntityPlayer) e;
            return FeatureConfig.killAuraPlayers && !p.isSpectator() && !p.isCreative();
        }

        // Never turn on your own pets.
        if (e instanceof EntityTameable && ((EntityTameable) e).isTamed()) return false;
        if (isLycanitesPet(e)) return false;

        if (e instanceof IMob) return true;

        if (e instanceof EntityAnimal || e instanceof EntityAmbientCreature
                || e instanceof EntityWaterMob || e instanceof EntityVillager) {
            return FeatureConfig.killAuraAnimals;
        }

        // Unknown modded creature: treat as hostile only when animals are allowed too,
        // so a stray Lycanites summon never gets clipped by accident.
        return FeatureConfig.killAuraAnimals;
    }

    private boolean isLycanitesPet(Entity e) {
        try {
            // Lycanites tamed creatures carry an owner UUID in their entity data.
            return e.getClass().getName().startsWith("com.lycanitesmobs")
                    && e.getEntityData() != null
                    && e.getEntityData().hasKey("OwnerUUID");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static double angleTo(EntityPlayerSP player, Entity target) {
        Vec3d look = player.getLook(1.0F);
        Vec3d dir = target.getPositionVector().addVector(0, target.height / 2.0D, 0)
                .subtract(player.getPositionEyes(1.0F)).normalize();
        double dot = look.x * dir.x + look.y * dir.y + look.z * dir.z;
        return Math.acos(Math.max(-1.0D, Math.min(1.0D, dot)));
    }

    /** Yaw/pitch that point at the middle of the target's hitbox. */
    public static float[] rotationsTo(EntityPlayerSP player, Entity target) {
        double dx = target.posX - player.posX;
        double dz = target.posZ - player.posZ;
        double dy = (target.posY + target.height / 2.0D) - (player.posY + player.getEyeHeight());
        double horizontal = MathHelper.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return new float[]{yaw, pitch};
    }
}
