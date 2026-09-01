package com.rlutility.modules;

import com.charles445.simpledifficulty.api.SDPotions;
import net.minecraft.client.Minecraft;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DebuffPurgerHandler {

    private static boolean sdLoaded = false;

    static {
        sdLoaded = Loader.isModLoaded("simpledifficulty");
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.clientDebuffNeutralizer || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        Collection<PotionEffect> activeEffects = mc.player.getActivePotionEffects();
        if (activeEffects == null || activeEffects.isEmpty()) return;

        List<Potion> debuffsToRemove = new ArrayList<Potion>();

        for (PotionEffect effect : activeEffects) {
            Potion potion = effect.getPotion();
            if (potion != null) {
                String name = potion.getRegistryName() != null ? potion.getRegistryName().toString().toLowerCase() : "";

                // Neutralize SimpleDifficulty Debuffs: parasites, thirsty, hypothermia, hyperthermia
                if (sdLoaded) {
                    if (potion == SDPotions.parasites || potion == SDPotions.thirsty ||
                        potion == SDPotions.hypothermia || potion == SDPotions.hyperthermia) {
                        debuffsToRemove.add(potion);
                        continue;
                    }
                }

                // Neutralize client disorientation, parasites, thirst, and camera shake debuffs
                if (potion.isBadEffect() ||
                    name.contains("parasite") ||
                    name.contains("thirst") ||
                    name.contains("hypothermia") ||
                    name.contains("hyperthermia") ||
                    name.contains("instability") ||
                    name.contains("fear") ||
                    name.contains("weight") ||
                    name.contains("paralysis") ||
                    name.contains("decay") ||
                    name.contains("curse") ||
                    name.contains("bleed") ||
                    name.contains("spin") ||
                    name.contains("smite") ||
                    name.contains("aphagia") ||
                    name.contains("dismount")) {
                    debuffsToRemove.add(potion);
                }
            }
        }

        for (Potion p : debuffsToRemove) {
            mc.player.removePotionEffect(p);
        }
    }
}
