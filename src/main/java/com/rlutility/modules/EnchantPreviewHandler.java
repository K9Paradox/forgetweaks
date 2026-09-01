package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.enchantment.EnchantmentData;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.ContainerEnchantment;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Exact enchantment prediction for the enchanting table.
 *
 * <h3>Why this is possible at all</h3>
 * Enchanting is not rolled fresh when you click. {@code ContainerEnchantment} derives every outcome
 * from a single per-player value, {@code xpSeed}:
 *
 * <pre>
 * this.rand.setSeed((long) this.xpSeed);
 * for (int j = 0; j &lt; 3; ++j)
 *     this.enchantLevels[j] = EnchantmentHelper.calcItemStackEnchantability(this.rand, j, power, stack);
 *
 * private List&lt;EnchantmentData&gt; getEnchantmentList(ItemStack stack, int slot, int level) {
 *     this.rand.setSeed((long) (this.xpSeed + slot));
 *     return EnchantmentHelper.buildEnchantmentList(this.rand, stack, level, false);
 * }
 * </pre>
 *
 * <p>And {@code xpSeed} is <b>sent to the client</b> - vanilla ships it as window property 3 so the
 * GUI can show its one-enchantment "clue". The client therefore holds the exact seed the server will
 * use, which means we can reproduce {@code buildEnchantmentList} locally and read out the complete
 * result for all three slots, not just the teaser.</p>
 *
 * <h3>Notes</h3>
 * This is pure observation: no packets are sent and no state is changed, so there is nothing for a
 * server or anti-cheat to detect. The seed only changes when you actually enchant something
 * ({@code EntityPlayer.onEnchant} re-rolls it), so you cannot reroll for free - but you can swap
 * different items in and out and see exactly what each would get first, which is the part that
 * matters.
 */
public class EnchantPreviewHandler {

    private static Field xpSeedField = null;
    private static boolean resolved = false;

    @SubscribeEvent
    public void onDrawScreen(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!FeatureConfig.enchantPreview) return;
        if (!(event.getGui() instanceof GuiContainer)) return;

        GuiContainer gui = (GuiContainer) event.getGui();
        if (!(gui.inventorySlots instanceof ContainerEnchantment)) return;

        ContainerEnchantment container = (ContainerEnchantment) gui.inventorySlots;

        try {
            ItemStack stack = container.tableInventory.getStackInSlot(0);
            if (stack == null || stack.isEmpty()) return;

            int xpSeed = readXpSeed(container);
            Minecraft mc = Minecraft.getMinecraft();

            int x = 6;
            int y = 6;
            mc.fontRenderer.drawStringWithShadow("\u00a76\u00a7lEnchantment preview", x, y, 0xFFFFFF);
            y += 12;

            for (int slot = 0; slot < 3; slot++) {
                int level = container.enchantLevels[slot];
                if (level <= 0) {
                    mc.fontRenderer.drawStringWithShadow("\u00a78" + (slot + 1) + ". \u00a78(no offer)", x, y, 0xFFFFFF);
                    y += 10;
                    continue;
                }

                List<String> names = predict(stack, xpSeed, slot, level);
                String head = "\u00a7e" + (slot + 1) + ". \u00a77lvl " + level + " \u00a78- \u00a7f";
                if (names.isEmpty()) {
                    mc.fontRenderer.drawStringWithShadow(head + "\u00a78unknown", x, y, 0xFFFFFF);
                    y += 10;
                    continue;
                }

                mc.fontRenderer.drawStringWithShadow(head + names.get(0), x, y, 0xFFFFFF);
                y += 10;
                for (int i = 1; i < names.size(); i++) {
                    mc.fontRenderer.drawStringWithShadow("      \u00a7f" + names.get(i), x, y, 0xFFFFFF);
                    y += 10;
                }
                y += 2;
            }
        } catch (Throwable ignored) {
            // Never let a preview break the enchanting screen.
        }
    }

    /** Reproduces ContainerEnchantment#getEnchantmentList exactly. */
    private static List<String> predict(ItemStack stack, int xpSeed, int slot, int level) {
        List<String> out = new ArrayList<>();
        try {
            Random rand = new Random();
            rand.setSeed(xpSeed + slot);
            List<EnchantmentData> list = EnchantmentHelper.buildEnchantmentList(rand, stack, level, false);
            if (list == null) return out;
            for (EnchantmentData data : list) {
                if (data == null || data.enchantment == null) continue;
                out.add(data.enchantment.getTranslatedName(data.enchantmentLevel));
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static int readXpSeed(ContainerEnchantment container) throws Exception {
        if (!resolved) {
            resolved = true;
            try {
                // Deobfuscated name first, then the SRG name for a production environment.
                xpSeedField = ReflectionHelper.findField(ContainerEnchantment.class, "xpSeed", "field_178150_h");
                xpSeedField.setAccessible(true);
            } catch (Throwable ignored) {
                xpSeedField = null;
            }
        }
        if (xpSeedField == null) return 0;
        return xpSeedField.getInt(container);
    }
}
