package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

/**
 * Timer - speeds up (or slows down) the client tick loop.
 *
 * <p>Because movement, mining progress and attack cooldowns are all driven client-side, a faster
 * tick loop translates into genuinely faster movement and mining on a real server. It is also the
 * single most detectable feature in this mod, which is why it is tagged RISKY in the GUI and capped
 * at 3.0x.</p>
 *
 * <p>The {@code tickLength} field is located by value (50.0F at boot) rather than by name so that
 * it keeps working across mapping/obfuscation differences.</p>
 */
public class TimerHandler {

    private static final float VANILLA_TICK_LENGTH = 50.0F;

    private static Object timerInstance = null;
    private static Field tickLengthField = null;
    private static boolean resolved = false;
    private static boolean applied = false;

    private static void resolve(Minecraft mc) {
        if (resolved) return;
        resolved = true;
        try {
            Field timerField = ReflectionHelper.findField(Minecraft.class, "field_71428_T", "timer");
            timerField.setAccessible(true);
            timerInstance = timerField.get(mc);
            if (timerInstance == null) return;

            for (Field f : timerInstance.getClass().getDeclaredFields()) {
                if (f.getType() != float.class) continue;
                f.setAccessible(true);
                if (Math.abs(f.getFloat(timerInstance) - VANILLA_TICK_LENGTH) < 0.001F) {
                    tickLengthField = f;
                    break;
                }
            }
        } catch (Throwable ignored) {
            timerInstance = null;
            tickLengthField = null;
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        resolve(mc);
        if (timerInstance == null || tickLengthField == null) return;

        boolean shouldRun = FeatureConfig.timerEnabled && mc.player != null && mc.world != null;

        try {
            if (shouldRun) {
                double speed = FeatureConfig.timerSpeed;
                if (speed < 0.1D) speed = 0.1D;
                if (speed > 3.0D) speed = 3.0D;
                tickLengthField.setFloat(timerInstance, (float) (VANILLA_TICK_LENGTH / speed));
                applied = true;
            } else if (applied) {
                tickLengthField.setFloat(timerInstance, VANILLA_TICK_LENGTH);
                applied = false;
            }
        } catch (Throwable ignored) {
        }
    }

    /** Called on disconnect / shutdown so the game never stays sped up. */
    public static void reset() {
        try {
            if (timerInstance != null && tickLengthField != null) {
                tickLengthField.setFloat(timerInstance, VANILLA_TICK_LENGTH);
            }
        } catch (Throwable ignored) {
        }
        applied = false;
    }
}
