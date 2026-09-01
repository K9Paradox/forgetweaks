package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * No slowdown.
 *
 * <p>Movement input is client-authoritative, so undoing the 0.2x item-use multiplier here is
 * genuinely accepted by the server. The old version additionally wrote to the player's
 * MOVEMENT_SPEED attribute base value, which permanently clobbered the attribute (RLCraft, Level Up
 * and Reskillable all modify it) and desynced from the server's copy - that has been removed.</p>
 */
public class NoSlowdownHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onInputUpdate(InputUpdateEvent event) {
        if (!FeatureConfig.noSlowdown) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityPlayer() != player) return;
        if (!player.isHandActive() || player.isRiding()) return;

        // Vanilla multiplies the input by 0.2 while an item is in use - undo exactly that.
        event.getMovementInput().moveForward *= 5.0F;
        event.getMovementInput().moveStrafe *= 5.0F;
    }
}
