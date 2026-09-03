package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Extended interaction and attack reach.
 *
 * <h3>Why this holds on real servers</h3>
 * {@code PlayerControllerMP#blockReachDistance} is the value the client uses to ray-trace block
 * targets and entity hits; the result of that ray trace is what gets put into the packets
 * ({@code CPacketPlayerDigging}, {@code CPacketUseEntity}, ...). The vanilla 1.12.2 server never
 * re-verifies the distance itself - it trusts that the client only sends interactions it could
 * see - which is why reach is a classic client-side feature. Strict anti-cheat plugins that add
 * their own server-side range check are the one thing that can flag it, hence the RISKY tag and
 * a sane default (5.0, vanilla creative reach) rather than something absurd.
 *
 * <h3>Field discovery</h3>
 * The field is located by name first, then by value ({@code 4.5F} in survival / {@code 5.0F} in
 * creative at boot), so mapping renames do not break it. It is re-applied every tick because
 * {@code PlayerControllerMP#setGameType} resets it (dimension changes, respawns).
 */
public class ReachHandler {

    private static Field reachField = null;
    private static boolean resolved = false;
    private boolean applied = false;

    private static void resolve(PlayerControllerMP controller) {
        if (resolved) return;
        resolved = true;
        try {
            // Known mappings first, then a value scan as fallback.
            for (String name : new String[]{"blockReachDistance", "field_78772_d"}) {
                try {
                    reachField = PlayerControllerMP.class.getDeclaredField(name);
                    if (reachField.getType() == float.class) {
                        reachField.setAccessible(true);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
            for (Field f : PlayerControllerMP.class.getDeclaredFields()) {
                if (f.getType() != float.class) continue;
                f.setAccessible(true);
                float v = f.getFloat(controller);
                if (Math.abs(v - 4.5F) < 0.001F || Math.abs(v - 5.0F) < 0.001F) {
                    reachField = f;
                    return;
                }
            }
        } catch (Throwable ignored) {
            reachField = null;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null) return;

        resolve(mc.playerController);
        if (reachField == null) return;

        try {
            if (FeatureConfig.reachEnabled) {
                float reach = (float) Math.max(4.5D, Math.min(32.0D, FeatureConfig.reachBlocks));
                reachField.setFloat(mc.playerController, reach);
                applied = true;
            } else if (applied) {
                // Back to the vanilla survival value once, so we leave no trace behind.
                reachField.setFloat(mc.playerController,
                        player.isCreative() ? 5.0F : 4.5F);
                applied = false;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Called on disconnect so a stale reach value never survives into another world. */
    public static void reset(PlayerControllerMP controller) {
        try {
            if (reachField != null && controller != null) {
                reachField.setFloat(controller, 4.5F);
            }
        } catch (Throwable ignored) {
        }
    }
}
