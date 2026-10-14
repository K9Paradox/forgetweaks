package com.rlutility.modules;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayerAbilities;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Creative-style flight, kept alive across server ability resyncs - built in, not a setting.
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
 * the moment anything hits you (arrow hits included, since their resync can land a tick or two
 * after the damage itself).
 *
 * <h3>How a restore is told apart from a deliberate toggle</h3>
 * The double-jump toggle turns flight off <em>locally</em> and only then tells the server, so by
 * the time any confirmation packet arrives the client is already not flying. A damage resync is
 * the opposite: the packet arrives while the client still believes it is flying. This handler
 * taps the network pipeline and watches incoming {@code SPacketPlayerAbilities}: a "flying=false"
 * packet that lands while we are still flying is a server-side strip and triggers the restore;
 * anything else (toggles, landings) is left alone. That discriminator is why double-jump to stop
 * flying works again.
 *
 * <h3>What it cannot fix</h3>
 * If the strip packet also says {@code allowFlying=false}, the flight permission itself was
 * revoked server-side; vanilla rejects {@code flying=true} no matter how often we send it. That
 * case is reported once in chat, the client stops fighting it, and gravity (plus No Fall) takes
 * over normally.
 */
public class FlightHandler {

    private boolean forced = false;
    /** True while the player intends to be flying, so a server reset can be undone. */
    private boolean wantFlying = false;
    /** Ticks left in the restore retry window after a server-side strip. */
    private int retryTicks = 0;
    /** One chat warning per refusal episode instead of one per packet. */
    private boolean refusedWarned = false;
    /** Set by the netty tap when a strip packet arrives; consumed by the player tick. */
    private static volatile int revokeSignal = 0;
    /** Set by the netty tap when the strip also revoked the permission itself. */
    private static volatile boolean permissionStripped = false;

    public static int restores = 0;

    private static final int RETRY_WINDOW_TICKS = 60;
    private static final int RETRY_INTERVAL_TICKS = 8;
    private static final int SIGNAL_GRACE_TICKS = 8;

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        // Reset per-connection state, then tap the pipeline ahead of the vanilla packet handler so
        // we can inspect abilities packets before they overwrite the client's state.
        revokeSignal = 0;
        permissionStripped = false;
        wantFlying = false;
        retryTicks = 0;
        try {
            event.getManager().channel().pipeline().addBefore("packet_handler",
                    "rlutility_flight_watch", new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                            if (msg instanceof SPacketPlayerAbilities && FeatureConfig.creativeFly) {
                                SPacketPlayerAbilities packet = (SPacketPlayerAbilities) msg;
                                Minecraft mc = Minecraft.getMinecraft();
                                EntityPlayerSP player = mc.player;
                                if (player != null && !packet.isFlying() && player.capabilities.isFlying) {
                                    // Server stripped flight we still held -> not a local toggle.
                                    revokeSignal = SIGNAL_GRACE_TICKS;
                                    if (!packet.isAllowFlying()) permissionStripped = true;
                                }
                            }
                            ctx.fireChannelRead(msg);
                        }
                    });
        } catch (Throwable ignored) {
            // A pipeline we cannot tap simply means falling back to no restores.
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.player != player) return;

        if (FeatureConfig.creativeFly) {
            if (player.onGround) {
                // Clear stale client fall distance before the server processes a landing packet.
                player.fallDistance = 0.0F;
            }
            // The permission is ours; re-assert it every tick in case a resync stripped it.
            player.capabilities.allowFlying = true;
            forced = true;

            boolean flying = player.capabilities.isFlying;
            if (flying) {
                wantFlying = true;
                retryTicks = 0;
            } else if (player.onGround) {
                // Landing ends the episode cleanly - no restore, no retries.
                wantFlying = false;
                retryTicks = 0;
                refusedWarned = false;
                permissionStripped = false;
            } else if (player.isSneaking()) {
                // Sneak-to-descend is the deliberate "stop flying" input mid-air.
                wantFlying = false;
                retryTicks = 0;
            } else if (revokeSignal > 0 && wantFlying && !refusedWarned) {
                // A server-side strip just arrived. If the permission itself was revoked there is
                // nothing to restore - say so once and let gravity (and No Fall) take over.
                if (permissionStripped) {
                    refusedWarned = true;
                    permissionStripped = false;
                    player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7cFlight "
                            + "permission was revoked server-side (allowFlying=false). If your "
                            + "flight source is a mod ability, toggle it off/on or relog."));
                } else {
                    restoreFlight(player);
                    retryTicks = RETRY_WINDOW_TICKS;
                    flying = true;
                }
            }
            if (revokeSignal > 0) revokeSignal--;

            // Retry pass: the server may strip flight again right after we restored it.
            if (retryTicks > 0 && !player.capabilities.isFlying && !player.onGround && !refusedWarned) {
                if (retryTicks % RETRY_INTERVAL_TICKS == 0) {
                    player.capabilities.isFlying = true;
                    sendAbilities(player);
                    restores++;
                }
                retryTicks--;
            }

            // Keep the fly speed applied; the same resync resets it.
            if (flying) {
                player.capabilities.setFlySpeed((float) clamp(FeatureConfig.flySpeed, 0.01D, 1.0D));
            }
        } else if (forced) {
            forced = false;
            wantFlying = false;
            retryTicks = 0;
            refusedWarned = false;
            revokeSignal = 0;
            permissionStripped = false;
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
