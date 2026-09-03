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

/**
 * Totem of Undying hot-swap.
 *
 * <h3>Why it used to never fire</h3>
 * The trigger was {@code health <= 7} checked at the moment of taking damage. With First Aid the
 * vanilla health bar is an average over eight body parts, and death comes from the head or body
 * reaching zero - which can happen while the averaged bar still shows ten or more hearts. The old
 * threshold simply never crossed before you were dead.
 *
 * <p>The trigger is now two-fold: vanilla health dropping to a configurable threshold (default
 * 12 of 20, so there is actually time left), <em>or</em> - when First Aid is installed - a
 * critical head/body wound, which is what actually kills you in this pack. Both are also polled
 * once per second, not only on damage events, so a big hit that takes you straight from full to
 * zero still arms a totem for the follow-up hit.</p>
 *
 * <p>The swap itself is three real inventory clicks (pick up totem, swap with offhand slot 45,
 * park the previous offhand item back), which is exactly what a fast player does manually - the
 * server sees nothing it would not also see from hand clicks.</p>
 */
public class FastTriageHandler {

    /** Shared across this handler and FirstAidHelper so the two never click-fight. */
    private static int equipCooldown = 0;

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!FeatureConfig.fastTriage) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player) return;

        tryEquipTotem(mc, player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.fastTriage || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null) return;

        if (equipCooldown > 0) equipCooldown--;

        tryEquipTotem(mc, player);
    }

    /**
     * Equips a totem when the situation warrants it. Static so First Aid's critical-wound check
     * can route here as well; the cooldown keeps both callers from clicking at the same time.
     */
    public static void tryEquipTotem(Minecraft mc, EntityPlayerSP player) {
        if (!FeatureConfig.fastTriage) return;
        if (player.isDead || player.getHealth() <= 0.0f) return;
        if (equipCooldown > 0) return;

        double threshold = Math.max(1.0D, Math.min(20.0D, FeatureConfig.totemEquipAtHealth));
        boolean lowHealth = player.getHealth() <= threshold;
        boolean criticalWound = FirstAidHelper.hasCriticalWound(player);
        if (!lowHealth && !criticalWound) return;

        // Offhand already carries a totem - nothing to do.
        ItemStack offhandStack = player.getHeldItemOffhand();
        if (!offhandStack.isEmpty() && offhandStack.getItem() == Items.TOTEM_OF_UNDYING) {
            return;
        }
        if (mc.playerController == null) return;

        // ContainerPlayer: 9-35 main inventory, 36-44 hotbar, 45 offhand.
        for (int i = 9; i <= 44; i++) {
            ItemStack stack = player.inventoryContainer.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.TOTEM_OF_UNDYING) {
                mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(0, 45, 0, ClickType.PICKUP, player);
                mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);

                player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7aAuto Totem "
                        + (criticalWound ? "(critical wound)" : "(low health)")
                        + " moved a Totem of Undying to the off-hand."));
                equipCooldown = 20;
                return;
            }
        }
        // No totem found - do not spam clicks, but re-check reasonably soon.
        equipCooldown = 10;
    }
}
