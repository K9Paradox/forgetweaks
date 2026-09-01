package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto respawn - fires the vanilla respawn request the moment the death screen appears.
 * Fully server-authoritative.
 */
public class AutoRespawnHandler {

    private int delay = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !FeatureConfig.autoRespawn) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        if (!(mc.currentScreen instanceof GuiGameOver)) {
            delay = 0;
            return;
        }

        // Tiny delay so death messages and the "you died" sound still land.
        if (delay++ < 5) return;
        delay = 0;

        try {
            mc.player.respawnPlayer();
            mc.displayGuiScreen(null);
        } catch (Throwable ignored) {
        }
    }
}
