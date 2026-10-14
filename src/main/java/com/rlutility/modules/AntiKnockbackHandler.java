package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Anti knockback.
 *
 * <p>Player movement in Minecraft is client-authoritative: the server sends a velocity packet and
 * trusts wherever the client says it ends up. Damping the motion right after a hit therefore works
 * on real servers, unlike purely visual "cancel the damage event" tricks.</p>
 */
public class AntiKnockbackHandler {

    private int lastHurtTime = 0;
    private int dampTicks = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.player != player) return;

        if (!FeatureConfig.antiKnockback) {
            lastHurtTime = player.hurtTime;
            dampTicks = 0;
            return;
        }

        // Rising edge of hurtTime == the tick the server's knockback landed on us.
        if (player.hurtTime > lastHurtTime) {
            dampTicks = 2;
        }
        lastHurtTime = player.hurtTime;

        if (dampTicks <= 0) return;
        dampTicks--;

        // Never interfere while riding or being carried - that desyncs badly.
        if (player.isRiding()) return;

        player.motionX *= FeatureConfig.antiKnockbackHorizontal;
        player.motionZ *= FeatureConfig.antiKnockbackHorizontal;
        if (player.motionY > 0.0D) {
            player.motionY *= FeatureConfig.antiKnockbackVertical;
        }
    }
}
