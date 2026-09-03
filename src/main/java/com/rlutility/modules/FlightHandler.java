package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayerAbilities;
import net.minecraft.util.text.TextComponentString;
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
 * <h3>How the restore works</h3>
 * The restore is built in, not a setting: whenever flight drops off while you are airborne and
 * intend to fly, the handler re-asserts {@code isFlying} locally and re-sends
 * {@code CPacketPlayerAbilities}, so server and client agree again and nothing rubber-bands.
 * Because ability resyncs can arrive a tick or two after the damage packet (arrow hits, potion
 * ticks, mod ability refreshes), the restore keeps retrying for a couple of seconds instead of
 * firing exactly once - that single-shot edge was why some hits still knocked you out of the air.
 *
 * <h3>What it cannot fix</h3>
 * If the server revokes the flight <em>permission</em> itself ({@code allowFlying=false}, e.g. a
 * modded flight source disabling itself after damage), vanilla's packet handler rejects
 * {@code flying=true} no matter how often we send it. That case is reported once in chat instead
 * of silently retrying forever. Sneaking while falling counts as a deliberate descent and is left
 * alone, which is also the manual escape hatch if a mod re-grants flight on its own schedule.
 */
public class FlightHandler {

    private boolean forced = false;
    /** True while the player intends to be flying, so a server reset can be undone. */
    private boolean wantFlying = false;
    /** Flying state at the end of the previous tick, for edge detection. */
    private boolean wasFlying = false;
    /** Ticks left in the restore retry window after flight was lost mid-air. */
    private int retryTicks = 0;
    /** One chat warning per refusal episode instead of one per packet. */
    private boolean refusedWarned = false;

    public static int restores = 0;

    private static final int RETRY_WINDOW_TICKS = 60;
    private static final int RETRY_INTERVAL_TICKS = 8;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.player != player) return;

        if (FeatureConfig.creativeFly) {
            // The permission is ours; re-assert it every tick in case a resync stripped it.
            player.capabilities.allowFlying = true;
            forced = true;

            boolean flying = player.capabilities.isFlying;
            if (flying) {
                wantFlying = true;
                retryTicks = 0;
                refusedWarned = false;
            } else if (player.onGround) {
                // Landing ends the episode cleanly - no restore, no retries.
                wantFlying = false;
                retryTicks = 0;
            } else if (player.isSneaking()) {
                // Sneak-to-descend is the deliberate "stop flying" input mid-air.
                wantFlying = false;
                retryTicks = 0;
            } else if (wasFlying) {
                // True falling edge: flight was just stripped mid-air against our will. Restore
                // now and keep the retry window open in case another resync lands a moment later.
                restoreFlight(player);
                retryTicks = RETRY_WINDOW_TICKS;
                flying = true;
            }
            wasFlying = player.capabilities.isFlying;

            // Retry pass: the server may strip flight again right after we restored it.
            if (retryTicks > 0 && !player.capabilities.isFlying && !player.onGround) {
                if (retryTicks % RETRY_INTERVAL_TICKS == 0) {
                    if (player.capabilities.allowFlying) {
                        // Permission is still ours - the flying flag just got clobbered again.
                        player.capabilities.isFlying = true;
                    }
                    sendAbilities(player);
                    restores++;
                }
                retryTicks--;
                if (retryTicks == 0 && !player.capabilities.isFlying && !refusedWarned) {
                    refusedWarned = true;
                    player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7cFlight was "
                            + "revoked server-side and could not be restored. If your flight source "
                            + "is a mod ability, toggle it off/on or relog; vanilla servers need "
                            + "allowFlying granted first."));
                }
            }

            // Keep the fly speed applied; the same resync resets it.
            if (flying) {
                player.capabilities.setFlySpeed((float) clamp(FeatureConfig.flySpeed, 0.01D, 1.0D));
            }
        } else if (forced) {
            forced = false;
            wantFlying = false;
            wasFlying = false;
            retryTicks = 0;
            refusedWarned = false;
            if (!player.isCreative() && !player.isSpectator()) {
                player.capabilities.allowFlying = false;
                player.capabilities.isFlying = false;
                player.capabilities.setFlySpeed(0.05F);
                sendAbilities(player);
            }
        }
    }

    /** Re-enable flight locally AND tell the server, so both sides agree and nothing rubber-bands. */
    private static void restoreFlight(EntityPlayerSP player) {
        player.capabilities.isFlying = true;
        player.capabilities.allowFlying = true;
        sendAbilities(player);
        restores++;
    }

    private static void sendAbilities(EntityPlayerSP player) {
        if (player.connection != null) {
            player.connection.sendPacket(new CPacketPlayerAbilities(player.capabilities));
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
