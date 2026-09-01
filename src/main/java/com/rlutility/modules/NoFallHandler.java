package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class NoFallHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!FeatureConfig.noFall) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getEntityLiving() != mc.player) return;

        DamageSource src = event.getSource();
        if (isFallOrKinetic(src)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingHurt(LivingHurtEvent event) {
        if (!FeatureConfig.noFall) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getEntityLiving() != mc.player) return;

        DamageSource src = event.getSource();
        if (isFallOrKinetic(src)) {
            event.setCanceled(true);
        }
    }

    private static boolean isFallOrKinetic(DamageSource src) {
        if (src == null) return false;
        if (src == DamageSource.FALL || src == DamageSource.FLY_INTO_WALL || src == DamageSource.ANVIL) return true;
        String type = src.getDamageType();
        if (type == null) return false;
        type = type.toLowerCase();
        return type.contains("fall") || type.contains("fly") || type.contains("wall") || type.contains("kinetic") || type.contains("crash") || type.contains("slam");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!FeatureConfig.noFall) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getEntityLiving() != mc.player) return;

        mc.player.fallDistance = 0.0f;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.noFall) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.connection == null) return;

        // Reset client fall distance without setting onGround (preserving 100% flight speed & inertia)
        player.fallDistance = 0.0f;

        // Prevent CollisionDamage mod from sending PacketCollisionS upon wall impact
        if (player.collidedHorizontally || player.collidedVertically) {
            player.getEntityData().setDouble("prevMotionCombined", 0.0);
            if (event.phase == TickEvent.Phase.START) {
                // Spoof grounded packet on collision to nullify server-side impact
                player.connection.sendPacket(new CPacketPlayer.Position(player.posX, player.posY, player.posZ, true));
            }
        }

        // Spoof grounded packet when falling to reset server-side fall distance
        if (event.phase == TickEvent.Phase.START && player.motionY < -0.3) {
            player.connection.sendPacket(new CPacketPlayer(true));
        }
    }
}
