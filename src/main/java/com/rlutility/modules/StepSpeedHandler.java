package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class StepSpeedHandler {

    private boolean wasStepActive = false;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (FeatureConfig.stepSpeed) {
            // Set 1.25 block step height to walk up whole blocks and slabs smoothly
            mc.player.stepHeight = 1.25f;
            wasStepActive = true;

            // Legit ground speed boost
            if (mc.player.onGround && !mc.player.isInWater() && !mc.player.isOnLadder()) {
                if (mc.player.movementInput != null && (mc.player.movementInput.moveForward != 0 || mc.player.movementInput.moveStrafe != 0)) {
                    if (mc.player.isSprinting()) {
                        mc.player.motionX *= 1.08;
                        mc.player.motionZ *= 1.08;
                    }
                }
            }
        } else if (wasStepActive) {
            mc.player.stepHeight = 0.6f;
            wasStepActive = false;
        }
    }
}
