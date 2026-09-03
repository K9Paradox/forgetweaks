package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayerAbilities;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Creative-style flight, kept alive across server ability resyncs.
 *
 * <h3>The bug this fixes</h3>
 * Taking damage makes the server push a {@code SPacketPlayerAbilities}, and
 * {@code NetHandlerPlayClient#handlePlayerAbilities} overwrites <em>both</em> fields from it:
 *
 * <pre>
 * entityplayer.capabilities.allowFlying = packetIn.isAllowFlying();
 * entityplayer.capabilities.isFlying    = packetIn.isFlying();
 * </pre>
 *
 * So the permission to fly survives but the act of flying does not, and you drop out of the sky
 * the moment anything hits you.
 *
 * <h3>Why the old version felt jittery</h3>
 * It only restored {@code isFlying} on the client. The server had revoked flight, so the two
 * disagreed about whether gravity applied to you - the server kept snapping you down and the
 * client kept floating, which reads as violent rubber-banding. The restore now also re-sends
 * {@code CPacketPlayerAbilities}, so server and client agree again.
 *
 * <h3>Why it only restores after damage</h3>
 * Blindly re-asserting {@code isFlying} would fight you every time you deliberately toggled flight
 * off in mid-air. Instead the restore is edge-triggered on the flying->not-flying transition while
 * inside a damage window ({@code hurtTime} / {@code hurtResistantTime}), which is exactly when the
 * unwanted resync arrives. A manual toggle outside that window is left alone.
 */
public class FlightHandler {

    private boolean forced = false;
    /** True while the player intends to be flying, so a server reset can be undone. */
    private boolean wantFlying = false;
    /** Flying state at the end of the previous tick, for edge detection. */
    private boolean wasFlying = false;

    public static int restores = 0;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.player != player) return;

        if (FeatureConfig.creativeFly) {
            player.capabilities.allowFlying = true;
            forced = true;

            boolean flying = player.capabilities.isFlying;
            if (flying) {
                wantFlying = true;
            } else if (player.onGround) {
                // Landing is a deliberate end to the flight.
                wantFlying = false;
            } else if (wasFlying && wantFlying && FeatureConfig.flyPersistThroughDamage
                    && inDamageWindow(player)) {
                // Edge-triggered: flight was just taken away while we intended to keep it.
                restoreFlight(player);
                flying = true;
            }
            wasFlying = flying;

            // Keep the fly speed applied; the same resync resets it.
            if (flying) {
                player.capabilities.setFlySpeed((float) clamp(FeatureConfig.flySpeed, 0.01D, 1.0D));
            }
        } else if (forced) {
            forced = false;
            wantFlying = false;
            wasFlying = false;
            if (!player.isCreative() && !player.isSpectator()) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.capabilities.setFlySpeed(0.05F);
                if (player.connection != null) {
                    player.connection.sendPacket(new CPacketPlayerAbilities(player.capabilities));
                }
            }
        }
    }

    /** Re-enable flight locally AND tell the server, so both sides agree and nothing rubber-bands. */
    private static void restoreFlight(EntityPlayerSP player) {
        player.capabilities.isFlying = true;
        if (player.connection != null) {
            player.connection.sendPacket(new CPacketPlayerAbilities(player.capabilities));
        }
        restores++;
    }

    /** True for the brief period after taking damage, which is when the resync arrives. */
    private static boolean inDamageWindow(EntityPlayerSP player) {
        return player.hurtTime > 0 || player.hurtResistantTime > 0;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
