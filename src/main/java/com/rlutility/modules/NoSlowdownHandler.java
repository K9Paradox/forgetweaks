package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class NoSlowdownHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInputUpdate(InputUpdateEvent event) {
        if (!FeatureConfig.noSlowdown) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityPlayer() != player) return;

        // Restore movement inputs if slowed down by using items (bows, eating, blocking, charging weapons)
        if (player.isHandActive() && !player.isRiding()) {
            event.getMovementInput().moveForward *= 5.0F;
            event.getMovementInput().moveStrafe *= 5.0F;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!FeatureConfig.noSlowdown) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player) return;

        if (player.isHandActive() && !player.isRiding()) {
            IAttributeInstance moveSpeed = player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            if (moveSpeed != null && moveSpeed.getAttributeValue() < 0.1) {
                moveSpeed.setBaseValue(0.1D);
            }
        }
    }
}
