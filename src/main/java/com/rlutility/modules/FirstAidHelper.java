package com.rlutility.modules;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.CapabilityExtendedHealthSystem;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.items.FirstAidItems;
import ichttt.mods.firstaid.common.network.MessageApplyHealingItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class FirstAidHelper {

    private static boolean modLoaded = false;
    private int triageCooldown = 0;

    static {
        modLoaded = Loader.isModLoaded("firstaid");
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!modLoaded || !FeatureConfig.firstAidAutoHeal) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player) return;

        checkLimbDamageAndTriage(mc, player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!modLoaded || !FeatureConfig.firstAidAutoHeal || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        if (triageCooldown > 0) {
            triageCooldown--;
            return;
        }

        checkLimbDamageAndTriage(mc, player);
    }

    private void checkLimbDamageAndTriage(Minecraft mc, EntityPlayerSP player) {
        if (player.isDead || player.getHealth() <= 0.0f) return;

        try {
            if (CapabilityExtendedHealthSystem.INSTANCE == null) return;
            AbstractPlayerDamageModel damageModel = player.getCapability(CapabilityExtendedHealthSystem.INSTANCE, null);
            if (damageModel == null) return;

            // Critical Parts: HEAD and BODY can cause instant death if HP reaches 0
            AbstractDamageablePart head = damageModel.HEAD;
            AbstractDamageablePart body = damageModel.BODY;

            boolean headCritical = head != null && head.currentHealth <= 3.0f;
            boolean bodyCritical = body != null && body.currentHealth <= 4.0f;

            // If head or body takes critical lethal damage, emergency equip Totem to Offhand
            if (headCritical || bodyCritical || (head != null && head.currentHealth <= 2.0f)) {
                ensureTotemEquipped(mc, player);
            }

            // Auto apply Bandage or Plaster packet to HEAD or BODY if wounded and not already ticking healing
            EnumPlayerPart partToHeal = null;
            if (isPartDamagedAndIdle(head)) {
                partToHeal = EnumPlayerPart.HEAD;
            } else if (isPartDamagedAndIdle(body)) {
                partToHeal = EnumPlayerPart.BODY;
            } else if (isPartDamagedAndIdle(damageModel.LEFT_ARM)) {
                partToHeal = EnumPlayerPart.LEFT_ARM;
            } else if (isPartDamagedAndIdle(damageModel.RIGHT_ARM)) {
                partToHeal = EnumPlayerPart.RIGHT_ARM;
            } else if (isPartDamagedAndIdle(damageModel.LEFT_LEG)) {
                partToHeal = EnumPlayerPart.LEFT_LEG;
            } else if (isPartDamagedAndIdle(damageModel.RIGHT_LEG)) {
                partToHeal = EnumPlayerPart.RIGHT_LEG;
            } else if (isPartDamagedAndIdle(damageModel.LEFT_FOOT)) {
                partToHeal = EnumPlayerPart.LEFT_FOOT;
            } else if (isPartDamagedAndIdle(damageModel.RIGHT_FOOT)) {
                partToHeal = EnumPlayerPart.RIGHT_FOOT;
            }

            if (partToHeal != null) {
                applyHealingItemToPart(mc, player, partToHeal);
            }

        } catch (Throwable ignored) {
        }
    }

    private boolean isPartDamagedAndIdle(AbstractDamageablePart part) {
        if (part == null) return false;
        // If part already has an active healer ticking, do not waste another healing item!
        if (part.activeHealer != null) return false;
        // Require at least 0.95 HP missing to prevent float precision false positives on full health
        return part.currentHealth <= ((float) part.getMaxHealth() - 0.95f);
    }

    private void applyHealingItemToPart(Minecraft mc, EntityPlayerSP player, EnumPlayerPart part) {
        if (mc.playerController == null || mc.getConnection() == null) return;

        // 1. Check if already holding a healing item in mainhand or offhand
        EnumHand handToUse = null;
        if (isHealingItem(player.getHeldItemMainhand())) {
            handToUse = EnumHand.MAIN_HAND;
        } else if (isHealingItem(player.getHeldItemOffhand())) {
            handToUse = EnumHand.OFF_HAND;
        }

        // 2. If not holding, check hotbar (0 to 8) and switch server active slot
        int hotbarHealingSlot = -1;
        if (handToUse == null) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.inventory.getStackInSlot(i);
                if (isHealingItem(stack)) {
                    hotbarHealingSlot = i;
                    break;
                }
            }

            if (hotbarHealingSlot != -1) {
                player.inventory.currentItem = hotbarHealingSlot;
                mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketHeldItemChange(hotbarHealingSlot));
                handToUse = EnumHand.MAIN_HAND;
            }
        }

        // 3. If still not found in hotbar, check main inventory (slots 9 to 35) and quick-swap into current hotbar slot
        if (handToUse == null) {
            for (int i = 9; i <= 35; i++) {
                ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
                if (isHealingItem(stack)) {
                    int targetHotbarSlot = player.inventory.currentItem;
                    int targetContainerSlot = 36 + targetHotbarSlot;
                    // Swap inventory slot i with hotbar slot
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, targetContainerSlot, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    handToUse = EnumHand.MAIN_HAND;
                    break;
                }
            }
        }

        if (handToUse != null && FirstAid.NETWORKING != null) {
            FirstAid.NETWORKING.sendToServer(new MessageApplyHealingItem(part, handToUse));
            player.sendMessage(new TextComponentString("\u00A76[RLUtility] \u00A7aAuto-Applied FirstAid Healing to " + part.name() + "!"));
            triageCooldown = 25;
        }
    }

    private boolean isHealingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == FirstAidItems.BANDAGE || item == FirstAidItems.PLASTER || item == FirstAidItems.MORPHINE;
    }

    private void ensureTotemEquipped(Minecraft mc, EntityPlayerSP player) {
        ItemStack offhandStack = player.getHeldItemOffhand();
        if (!offhandStack.isEmpty() && offhandStack.getItem() == Items.TOTEM_OF_UNDYING) {
            return;
        }

        for (int i = 9; i <= 44; i++) {
            ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                int windowId = 0;
                mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(windowId, 45, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(windowId, i, 0, ClickType.PICKUP, player);
                player.sendMessage(new TextComponentString("\u00A76[RLUtility] \u00A7c[VITAL PROTECTION] Equipped Totem of Undying for Head/Chest protection!"));
                triageCooldown = 15;
                break;
            }
        }
    }
}
