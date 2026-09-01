package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AutoCritHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCriticalHit(CriticalHitEvent event) {
        if (!FeatureConfig.autoCriticals) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Force critical hit result for Forge, Vanilla, and RLCombat / Spartan Weaponry
        if (event.getEntityPlayer() == mc.player) {
            event.setDamageModifier(1.5F);
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttack(AttackEntityEvent event) {
        if (!FeatureConfig.autoCriticals) return;
        triggerCritHop();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClick(MouseEvent event) {
        if (!FeatureConfig.autoCriticals) return;
        // Button 0 is left click (Attack / RLCombat extended reach strike)
        if (event.getButton() == 0 && event.isButtonstate()) {
            triggerCritHop();
        }
    }

    private static void triggerCritHop() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (player == null || player.connection == null) return;

        if (player.onGround && !player.isOnLadder() && !player.isInWater() && !player.isRiding()) {
            double posX = player.posX;
            double posY = player.posY;
            double posZ = player.posZ;

            // Packet micro-hop sequence setting downwards falling state on server
            player.connection.sendPacket(new CPacketPlayer.Position(posX, posY + 0.11, posZ, false));
            player.connection.sendPacket(new CPacketPlayer.Position(posX, posY + 0.1100013579, posZ, false));
            player.connection.sendPacket(new CPacketPlayer.Position(posX, posY + 0.0000013579, posZ, false));

            player.fallDistance = 0.5F;
        }
    }
}
