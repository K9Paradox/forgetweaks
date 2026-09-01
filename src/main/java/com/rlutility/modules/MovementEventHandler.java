package com.rlutility.modules;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class MovementEventHandler {
    private boolean wasFlightForced = false;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EntityPlayer player = event.player;
        if (player == null) return;

        // Creative Flight Toggle: Only force flight if feature is explicitly ENABLED.
        if (FeatureConfig.creativeFly) {
            player.capabilities.allowFlying = true;
            wasFlightForced = true;
        } else if (wasFlightForced) {
            if (!player.isCreative() && !player.isSpectator()) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.sendPlayerAbilities();
            }
            wasFlightForced = false;
        }
    }
}
