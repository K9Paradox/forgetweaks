package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class FastTriageHandler {

    private int cooldown = 0;

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!FeatureConfig.fastTriage) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player) return;

        // Perform instant triage swap when taking damage at critical HP
        checkAndEquipTotem(mc, player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.fastTriage || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        checkAndEquipTotem(mc, player);
    }

    private void checkAndEquipTotem(Minecraft mc, EntityPlayerSP player) {
        if (player.isDead || player.getHealth() <= 0.0f) return;

        // Check if health is under 7.0 HP (3.5 hearts)
        if (player.getHealth() <= 7.0f) {
            // Check if offhand already holds a Totem of Undying
            ItemStack offhandStack = player.getHeldItemOffhand();
            if (!offhandStack.isEmpty() && offhandStack.getItem() == Items.TOTEM_OF_UNDYING) {
                return;
            }

            // Search player inventory container for a Totem of Undying
            // ContainerPlayer: Slot 9-35 (Main Inventory), Slot 36-44 (Hotbar)
            for (int i = 9; i <= 44; i++) {
                ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
                if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                    // Fast swap: Pick up Totem from inventory -> Click Offhand (Slot 45) -> Return leftover to slot
                    int windowId = 0; // 0 is always the player inventory container
                    mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(windowId, 45, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, player);

                    player.sendMessage(new TextComponentString("\u00A76[RLUtility] \u00A7aAuto-Triage equipped Totem of Undying to off-hand!"));
                    cooldown = 10;
                    break;
                }
            }
        }
    }
}
