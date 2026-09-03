package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;

/**
 * Removes the screen blur EnhancedVisuals applies when SimpleDifficulty thirst runs low.
 *
 * <h3>Where the blur actually comes from</h3>
 * SimpleDifficulty itself draws nothing when you are thirsty - the blurry vision is
 * EnhancedVisuals' {@code SimpleDifficultyAddon}. It registers a {@code ThirstHandler}
 * (id {@code enhancedvisuals:simple-thirst}) that ramps a {@code blobs2} post shader's
 * opacity as the thirst level falls into the configured {@code thirstLevel} span
 * (default 2..8, intensity up to 5). On RLCraft this means the screen smears badly exactly
 * when you most need to see.
 *
 * <h3>How it is suppressed</h3>
 * The handler keeps its tuning in two public fields that its own tick reads every frame:
 * {@code maxIntensity} (target opacity scale) and {@code thirstLevel} (the activation span).
 * We reflectively zero {@code maxIntensity} and collapse the span to -1, so the aimed opacity
 * is always 0 and the existing blur fades out within a couple of seconds via the handler's own
 * fade factor. Nothing is unregistered and no packet is involved - it is a purely client-side
 * visual change, which is all the effect ever was.
 *
 * <p>The fields have been named identically since the addon first shipped, but everything is
 * resolved by name and every failure is silent: on an EnhancedVisuals build without this
 * handler the module simply does nothing.</p>
 */
public class ThirstBlurSuppressor {

    private static final String ADDON_CLASS =
            "team.creative.enhancedvisuals.common.addon.simpledifficulty.SimpleDifficultyAddon";

    private static boolean evLoaded = false;

    static {
        evLoaded = Loader.isModLoaded("enhancedvisuals");
    }

    private boolean suppressed = false;
    private boolean gaveUp = false;
    private boolean notified = false;
    private int cooldown = 0;
    private int reassertTicks = 0;
    private int attempts = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (!evLoaded || !FeatureConfig.removeThirstBlur || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (suppressed) {
            // Re-assert periodically in case something (config reload) restores the values.
            if (++reassertTicks >= 200) {
                reassertTicks = 0;
                suppressed = neutralizeThirstHandler();
            }
            return;
        }
        if (gaveUp) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // The addon registers during EnhancedVisuals' init, which can land after ours - retry.
        if (neutralizeThirstHandler()) {
            suppressed = true;
            if (!notified) {
                notified = true;
                mc.player.sendMessage(new TextComponentString(
                        "\u00a76[RLUtility] \u00a7aThirst blur suppression active."));
            }
        } else if (++attempts >= 60) {
            gaveUp = true; // ~2 minutes of retries: this EV build has no such handler
        } else {
            cooldown = 100;
        }
    }

    /** Zeroes the handler's intensity and collapses its activation span. */
    private static boolean neutralizeThirstHandler() {
        try {
            Object handler = locateThirstHandler();
            if (handler == null) return false;

            boolean any = false;

            // maxIntensity = 0 -> the shader's target opacity is 0; existing blur fades out.
            Field maxIntensity = findField(handler.getClass(), "maxIntensity");
            if (maxIntensity != null) {
                maxIntensity.setAccessible(true);
                maxIntensity.setDouble(handler, 0.0D);
                any = true;
            }

            // Belt and braces: collapse the activation span so the "thirst is low" check can
            // never fire again, whatever maxIntensity ends up at.
            Field thirstLevel = findField(handler.getClass(), "thirstLevel");
            if (thirstLevel != null) {
                thirstLevel.setAccessible(true);
                Object range = thirstLevel.get(handler);
                if (range != null) {
                    Field minF = findField(range.getClass(), "min");
                    Field maxF = findField(range.getClass(), "max");
                    if (minF != null && maxF != null) {
                        minF.setAccessible(true);
                        maxF.setAccessible(true);
                        minF.setInt(range, -1);
                        maxF.setInt(range, -1);
                        any = true;
                    }
                }
            }
            return any;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Reads the handler instance from SimpleDifficultyAddon's static field. */
    private static Object locateThirstHandler() {
        try {
            Class<?> addon = Class.forName(ADDON_CLASS);
            Field f = addon.getField("thirst");
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Walks the class hierarchy for a field by name. */
    private static Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // keep walking
            }
        }
        return null;
    }
}
