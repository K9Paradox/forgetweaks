package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiCrafting;
import net.minecraft.client.gui.inventory.GuiFurnace;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.gui.inventory.GuiRepair;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Container stealer / auto loot.
 *
 * <p>Uses {@link ClickType#QUICK_MOVE} window clicks, i.e. exactly the packets a real shift-click
 * produces, so it is fully server-authoritative and works on any server - including RLCraft dungeon
 * chests, battle towers and shulkers.</p>
 */
public class AutoLootHandler {

    private int delay = 0;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!FeatureConfig.autoLoot) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.playerController == null) return;

        GuiScreen screen = mc.currentScreen;
        if (!isLootableScreen(screen)) {
            delay = 0;
            return;
        }

        Container container = mc.player.openContainer;
        if (container == null || container instanceof ContainerPlayer) return;

        if (delay > 0) {
            delay--;
            return;
        }

        for (Slot slot : container.inventorySlots) {
            if (!isLootableSlot(slot, mc)) continue;

            mc.playerController.windowClick(container.windowId, slot.slotNumber, 0,
                    ClickType.QUICK_MOVE, mc.player);
            delay = Math.max(0, FeatureConfig.autoLootDelay);
            return;
        }

        // Nothing transferable left.
        if (FeatureConfig.autoLootCloseWhenDone) {
            mc.player.closeScreen();
        }
    }

    private boolean isLootableSlot(Slot slot, Minecraft mc) {
        if (slot == null || !slot.getHasStack()) return false;
        if (slot.inventory instanceof InventoryPlayer) return false;
        // Never touch crafting grids or craft results - that would spam-craft.
        if (slot.inventory instanceof InventoryCrafting) return false;
        if (slot.inventory instanceof InventoryCraftResult) return false;
        return slot.canTakeStack(mc.player);
    }

    private boolean isLootableScreen(GuiScreen screen) {
        if (!(screen instanceof GuiContainer)) return false;
        if (screen instanceof GuiInventory) return false;
        if (screen instanceof GuiContainerCreative) return false;
        if (screen instanceof GuiCrafting) return false;
        if (screen instanceof GuiRepair) return false;
        if (screen instanceof GuiFurnace) return false;

        // Skip the mod GUIs the rest of this mod automates, so features don't fight each other.
        String name = screen.getClass().getName().toLowerCase();
        if (name.contains("reforg") || name.contains("lockpicking") || name.contains("anvil")
                || name.contains("enchant") || name.contains("beacon") || name.contains("quest")) {
            return false;
        }
        return true;
    }
}
