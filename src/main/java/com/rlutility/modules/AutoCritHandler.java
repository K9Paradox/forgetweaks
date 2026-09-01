package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto criticals.
 *
 * <p>The hop packets have to reach the server BEFORE the attack packet, so they are sent from
 * {@link MouseEvent} (which fires while the click is being processed) rather than from
 * {@code AttackEntityEvent}, which vanilla fires only after {@code CPacketUseEntity} has already
 * gone out. The old code did both, which sent the sequence twice per swing and put half of it in
 * the wrong order.</p>
 */
public class AutoCritHandler {

    /**
     * Ticks during which {@link NoFallHandler} must leave {@code fallDistance} alone. Without this
     * the No Fall module zeroed the fall distance every tick and quietly killed every crit.
     */
    public static int critWindow = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && critWindow > 0) critWindow--;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onCriticalHit(CriticalHitEvent event) {
        if (!FeatureConfig.autoCriticals) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        // Force the critical result for Forge, vanilla, RLCombat and Spartan Weaponry.
        if (event.getEntityPlayer() == mc.player) {
            event.setDamageModifier(1.5F);
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouseClick(MouseEvent event) {
        if (!FeatureConfig.autoCriticals) return;
        if (event.getButton() != 0 || !event.isButtonstate()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) return;

        // Only hop when we are actually about to hit something.
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.ENTITY) return;
        // A crit needs a full charge anyway - don't waste packets on a spam click.
        if (mc.player.getCooledAttackStrength(0.0F) < 0.9F) return;

        triggerCritHop();
    }

    /** Also used by the kill aura so automated swings crit too. */
    public static void triggerCritHop() {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.connection == null) return;

        if (!player.onGround || player.isOnLadder() || player.isInWater() || player.isRiding()) return;
        if (player.capabilities.isFlying) return;

        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;

        // Micro-hop packet sequence: the server sees us leave the ground and start descending.
        player.connection.sendPacket(new CPacketPlayer.Position(x, y + 0.11D, z, false));
        player.connection.sendPacket(new CPacketPlayer.Position(x, y + 0.1100013579D, z, false));
        player.connection.sendPacket(new CPacketPlayer.Position(x, y + 0.0000013579D, z, false));

        player.fallDistance = 0.5F;
        critWindow = 4;
    }
}
