package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLiving;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Auto armor.
 *
 * <p>Rates every armour piece in the inventory and swaps in the best one using ordinary window
 * clicks on the player container, so the server applies the change itself. Works on any server.</p>
 */
public class AutoArmorHandler {

    private static final EntityEquipmentSlot[] ARMOR_SLOTS = {
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET
    };

    private int cooldown = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !FeatureConfig.autoArmor) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null) return;
        // Only act while no GUI is open, otherwise we fight with the player's own clicks.
        if (mc.currentScreen != null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        for (EntityEquipmentSlot armorSlot : ARMOR_SLOTS) {
            int containerSlot = 8 - armorSlot.getIndex();
            ItemStack equipped = player.inventoryContainer.getSlot(containerSlot).getStack();

            if (!equipped.isEmpty() && EnchantmentHelper.hasBindingCurse(equipped)) continue;

            double currentScore = rate(equipped);
            int bestInvSlot = -1;
            double bestScore = currentScore;

            for (int i = 9; i <= 44; i++) {
                ItemStack candidate = player.inventoryContainer.getSlot(i).getStack();
                if (candidate.isEmpty() || !(candidate.getItem() instanceof ItemArmor)) continue;
                if (EntityLiving.getSlotForItemStack(candidate) != armorSlot) continue;

                double score = rate(candidate);
                if (score > bestScore + 0.01D) {
                    bestScore = score;
                    bestInvSlot = i;
                }
            }

            if (bestInvSlot != -1) {
                swap(mc, player, bestInvSlot, containerSlot);
                cooldown = 8; // one piece per pass keeps the packet rate human
                return;
            }
        }
    }

    private void swap(Minecraft mc, EntityPlayerSP player, int fromSlot, int armorSlot) {
        try {
            mc.playerController.windowClick(0, fromSlot, 0, ClickType.PICKUP, player);
            mc.playerController.windowClick(0, armorSlot, 0, ClickType.PICKUP, player);
            // Put whatever we swapped out back into the now-free inventory slot.
            if (!player.inventory.getItemStack().isEmpty()) {
                mc.playerController.windowClick(0, fromSlot, 0, ClickType.PICKUP, player);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Higher is better. Considers protection, toughness, enchants and remaining durability. */
    private double rate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1.0D;
        if (!(stack.getItem() instanceof ItemArmor)) return -1.0D;

        ItemArmor armor = (ItemArmor) stack.getItem();
        double score = armor.damageReduceAmount * 1.0D + armor.toughness * 1.5D;

        try {
            score += EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.PROTECTION, stack) * 0.75D;
            score += EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.BLAST_PROTECTION, stack) * 0.25D;
            score += EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.PROJECTILE_PROTECTION, stack) * 0.25D;
            score += EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.FIRE_PROTECTION, stack) * 0.25D;
            score += EnchantmentHelper.getEnchantmentLevel(net.minecraft.init.Enchantments.THORNS, stack) * 0.20D;
        } catch (Throwable ignored) {
        }

        // Prefer gear that is not about to break.
        if (stack.isItemStackDamageable() && stack.getMaxDamage() > 0) {
            double remaining = 1.0D - ((double) stack.getItemDamage() / (double) stack.getMaxDamage());
            if (remaining < 0.08D) score -= 5.0D;
            score += remaining * 0.5D;
        }
        return score;
    }
}
