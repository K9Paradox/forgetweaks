package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;

public class FastMineHelper {

    private static Field blockHitDelayField = null;

    static {
        try {
            // Obfuscated: field_78779_k, SRG: blockHitDelay
            blockHitDelayField = ReflectionHelper.findField(PlayerControllerMP.class, "field_78779_k", "blockHitDelay");
            blockHitDelayField.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.fastMine || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.playerController != null && blockHitDelayField != null) {
            try {
                blockHitDelayField.setInt(mc.playerController, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    // Counteract NoTreePunching 0.2x speed penalty and boost breaking speed
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!FeatureConfig.fastMine) return;

        float speed = event.getNewSpeed();
        float orig = event.getOriginalSpeed();

        // If penalized below original speed (e.g. by NoTreePunching 0.2x), restore and amplify
        if (speed < orig) {
            event.setNewSpeed(orig * 3.0f);
        } else {
            event.setNewSpeed(speed * 2.5f);
        }
    }
}
