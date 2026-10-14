package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Fast mining, two modes.
 *
 * <ul>
 * <li><b>Fast</b> - zeroes the vanilla 5-tick delay between breaking blocks and restores the
 *     mining speed NoTreePunching takes away (via the BreakSpeed event).</li>
 * <li><b>Instant</b> - additionally completes the block's damage progress every tick while you
 *     hold mine, so any block finishes in about one tick. The server receives the normal finish
 *     digging packet and never re-checks how fast you actually mined - vanilla trusts it. That is
 *     also why anti-cheats are the only thing that can object.</li>
 * </ul>
 *
 * <p>Fields are resolved by both their SRG and MCP names: {@code field_78781_i} is
 * {@code blockHitDelay} and {@code field_78770_f} is {@code curBlockDamageMP} in 1.12.2. An
 * earlier version used {@code field_78779_k} for the delay, which does not exist, so the whole
 * module silently did nothing at runtime - worth remembering before trusting a reflection cache.</p>
 */
public class FastMineHelper {

    public static final int MODE_FAST = 0;
    public static final int MODE_INSTANT = 1;

    private static final Field BLOCK_HIT_DELAY =
            findField(PlayerControllerMP.class, "field_78781_i", "blockHitDelay");
    private static final Field CUR_BLOCK_DAMAGE =
            findField(PlayerControllerMP.class, "field_78770_f", "curBlockDamageMP");

    private static Field findField(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {
                // try the next candidate
            }
        }
        return null;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.fastMine || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        PlayerControllerMP pc = mc.playerController;
        if (pc == null || mc.player == null) return;

        if (BLOCK_HIT_DELAY != null) {
            try {
                BLOCK_HIT_DELAY.setInt(pc, 0);
            } catch (Throwable ignored) {}
        }

        if (FeatureConfig.fastMineMode >= MODE_INSTANT
                && !pc.isInCreativeMode()
                && mc.currentScreen == null
                && mc.gameSettings.keyBindAttack.isKeyDown()
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK
                && CUR_BLOCK_DAMAGE != null) {
            try {
                CUR_BLOCK_DAMAGE.setFloat(pc, 1.0F);
            } catch (Throwable ignored) {}
        }
    }

    /** Counteracts NoTreePunching's 0.2x penalty and generally speeds breaking up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!FeatureConfig.fastMine) return;

        float speed = event.getNewSpeed();
        float orig = event.getOriginalSpeed();

        // If penalized below original speed (e.g. by NoTreePunching 0.2x), restore and amplify.
        if (speed < orig) {
            event.setNewSpeed(orig * 3.0F);
        } else {
            event.setNewSpeed(speed * 2.5F);
        }
    }
}
