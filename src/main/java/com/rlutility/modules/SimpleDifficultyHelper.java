package com.rlutility.modules;

import com.charles445.simpledifficulty.api.SDCapabilities;
import com.charles445.simpledifficulty.api.thirst.IThirstCapability;
import com.charles445.simpledifficulty.api.temperature.ITemperatureCapability;
import com.charles445.simpledifficulty.network.MessageDrinkWater;
import com.charles445.simpledifficulty.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class SimpleDifficultyHelper {

    private static boolean modLoaded = false;
    private int drinkCooldown = 0;

    static {
        modLoaded = Loader.isModLoaded("simpledifficulty");
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!modLoaded || !FeatureConfig.simpleDifficultyAutoHydrate || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        if (drinkCooldown > 0) {
            drinkCooldown--;
        }

        try {
            IThirstCapability thirst = SDCapabilities.getThirstData(player);
            if (thirst != null) {
                // If thirst drops below 14 (out of 20) and we have cooldown ready, send drink packet to server
                if (thirst.getThirstLevel() < 14 && drinkCooldown <= 0) {
                    if (PacketHandler.instance != null) {
                        PacketHandler.instance.sendToServer(new MessageDrinkWater());
                        drinkCooldown = 15;
                    }
                }
            }

            ITemperatureCapability temp = SDCapabilities.getTemperatureData(player);
            if (temp != null) {
                // Stabilize client temperature tracking
                if (temp.getTemperatureDamageCounter() > 0) {
                    temp.setTemperatureDamageCounter(0);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
