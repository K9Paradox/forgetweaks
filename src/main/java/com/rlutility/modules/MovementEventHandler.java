package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Creative-style flight, kept alive across server ability resyncs.
 *
 * <h3>The bug this fixes</h3>
 * The old handler set {@code capabilities.allowFlying = true} every tick but never touched
 * {@code isFlying}. Taking damage causes the server to push a {@code SPacketPlayerAbilities}, and
 * {@code NetHandlerPlayClient#handlePlayerAbilities} overwrites <em>both</em> fields from it:
 *
 * <pre>
 * entityplayer.capabilities.allowFlying = packetIn.isAllowFlying();
 * entityplayer.capabilities.isFlying    = packetIn.isFlying();
 * </pre>
 *
 * So the next tick restored permission to fly but not the act of flying, and you dropped out of the
 * sky the moment anything hit you.
 *
 * <h3>Why it only restores after damage</h3>
 * Blindly re-asserting {@code isFlying} would fight you every time you deliberately toggled flight
 * off in mid-air. Instead the restore is gated on the player being inside a damage window
 * ({@code hurtTime} / {@code hurtResistantTime}), which is exactly when the unwanted resync happens.
 * A manual toggle outside that window is left alone.
 */
public class MovementEventHandler {

    private boolean wasFlightForced = false;
    /** True while the player intends to be flying, so a server reset can be undone. */
    private boolean wantFlying = false;
    private int restores = 0;

    public static int lastRestoreCount = 0;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        EntityPlayer player = event.player;
        if (player == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP self = mc.player;
        if (self == null || player != self) return;

        if (FeatureConfig.creativeFly) {
            player.capabilities.allowFlying = true;
            wasFlightForced = true;

            if (player.capabilities.isFlying) {
                wantFlying = true;
            } else if (player.onGround) {
                // Landing is a deliberate end to the flight.
                wantFlying = false;
            } else if (wantFlying && FeatureConfig.flyPersistThroughDamage && inDamageWindow(player)) {
                // The server just clobbered our abilities; put flight back.
                player.capabilities.isFlying = true;
                restores++;
                lastRestoreCount = restores;
            }

            // Keep the fly speed applied; the same resync resets it.
            if (player.capabilities.isFlying) {
                player.capabilities.setFlySpeed((float) Math.max(0.01D,
                        Math.min(1.0D, FeatureConfig.flySpeed)));
            }
        } else if (wasFlightForced) {
            wantFlying = false;
            if (!player.isCreative() && !player.isSpectator()) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.capabilities.setFlySpeed(0.05F);
                player.sendPlayerAbilities();
            }
            wasFlightForced = false;
        }
    }

    /** True for the brief period after taking damage, which is when the resync arrives. */
    private static boolean inDamageWindow(EntityPlayer player) {
        return player.hurtTime > 0 || player.hurtResistantTime > 0;
    }
}
