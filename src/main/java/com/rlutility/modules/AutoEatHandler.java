package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto eat.
 *
 * <p>Drives the real "use item" key binding so the client emits the same packets a manual eat does
 * (held-item change, use-item, and the finish packet). Server-authoritative on any server.</p>
 */
public class AutoEatHandler {

    private boolean eating = false;
    private int previousSlot = -1;
    private int graceTicks = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;

        if (player == null || mc.world == null || !FeatureConfig.autoEat) {
            stop(mc);
            return;
        }
        if (mc.currentScreen != null || player.isDead) {
            stop(mc);
            return;
        }

        if (eating) {
            if (graceTicks > 0) {
                graceTicks--;
                return;
            }
            boolean full = player.getFoodStats().getFoodLevel() >= 20;
            if (!player.isHandActive() || full) {
                stop(mc);
            }
            return;
        }

        if (player.getFoodStats().getFoodLevel() > FeatureConfig.autoEatThreshold) return;
        if (player.isHandActive()) return; // busy blocking / drawing a bow

        int slot = findBestFoodSlot(player);
        if (slot < 0) return;

        previousSlot = player.inventory.currentItem;
        player.inventory.currentItem = slot;
        if (mc.getConnection() != null) {
            mc.getConnection().sendPacket(new CPacketHeldItemChange(slot));
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        eating = true;
        graceTicks = 3; // let the use-item actually start before we test isHandActive
    }

    private void stop(Minecraft mc) {
        if (!eating) return;
        eating = false;
        graceTicks = 0;

        try {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            if (mc.player != null && previousSlot >= 0 && previousSlot < 9) {
                mc.player.inventory.currentItem = previousSlot;
                if (mc.getConnection() != null) {
                    mc.getConnection().sendPacket(new CPacketHeldItemChange(previousSlot));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            previousSlot = -1;
        }
    }

    /** Highest-saturation safe food in the hotbar, or -1. */
    private int findBestFoodSlot(EntityPlayerSP player) {
        int best = -1;
        float bestValue = -1.0F;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemFood)) continue;
            if (isUnsafe(stack)) continue;

            ItemFood food = (ItemFood) stack.getItem();
            float value = food.getHealAmount(stack) + food.getSaturationModifier(stack) * 2.0F;
            if (value > bestValue) {
                bestValue = value;
                best = i;
            }
        }
        return best;
    }

    private boolean isUnsafe(ItemStack stack) {
        if (stack.getItem().getRegistryName() == null) return true;
        String id = stack.getItem().getRegistryName().toString().toLowerCase();
        return id.contains("rotten")
                || id.contains("spider_eye")
                || id.contains("poisonous")
                || id.contains("pufferfish")
                || id.contains("chorus")
                || id.contains("raw_")
                || id.endsWith(":chicken")
                || id.endsWith(":fish")
                || id.endsWith(":beef")
                || id.endsWith(":porkchop")
                || id.endsWith(":mutton")
                || id.endsWith(":rabbit");
    }
}
