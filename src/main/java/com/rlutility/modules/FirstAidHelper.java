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
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.EnumMap;

/**
 * Automatic First Aid wound treatment through the mod's own network channel.
 *
 * <h3>Why the old version consumed bandages without ever healing</h3>
 * The handler used {@code part.activeHealer != null} to tell whether a healer was already working,
 * but that field is only meaningful <em>server-side</em>. First Aid's damage model syncs part
 * health to the client, not the healer state - so on the client {@code activeHealer} was always
 * null, the "is this part already being treated" check always passed, and a fresh
 * {@code MessageApplyHealingItem} went out on every check cycle.
 *
 * <p>Server-side the packet handler simply assigns {@code part.activeHealer = newHealer}, replacing
 * whatever was there. A bandage heals 1 HP every several hundred ticks, so resetting the healer
 * every ~25 ticks meant the heal timer never reached its first tick: items were consumed, the
 * server accepted them, and nothing ever regenerated - exactly the reported symptom.</p>
 *
 * <h3>The fix</h3>
 * The client now keeps its own treatment ledger: after applying a healer to a part, that part is
 * considered busy for a long window ({@link #HEAL_WINDOW_TICKS}). The ledger entry clears early
 * once the part's synced health shows it healed. No packet is sent at a part that is already
 * recorded as being treated, so heal timers finally run to completion.
 */
public class FirstAidHelper {

    private static boolean modLoaded = false;
    private int checkCooldown = 0;

    /** Ticks a part is treated as "healer active" after we send one application packet. */
    private static final int HEAL_WINDOW_TICKS = 900; // 45 seconds

    /** Parts we believe currently have a healer working, with ticks left on the assumption. */
    private final EnumMap<EnumPlayerPart, Integer> treatingTicks = new EnumMap<>(EnumPlayerPart.class);

    static {
        modLoaded = Loader.isModLoaded("firstaid");
    }

    public static boolean isModLoaded() {
        return modLoaded;
    }

    /**
     * True when head or body is at a health where the next hit can be lethal. Used by
     * {@link FastTriageHandler} as its early totem trigger, since First Aid kills through part
     * health long before the averaged vanilla bar looks dangerous.
     */
    public static boolean hasCriticalWound(EntityPlayerSP player) {
        if (!modLoaded || player == null) return false;
        try {
            if (CapabilityExtendedHealthSystem.INSTANCE == null) return false;
            AbstractPlayerDamageModel model =
                    player.getCapability(CapabilityExtendedHealthSystem.INSTANCE, null);
            if (model == null) return false;
            AbstractDamageablePart head = model.HEAD;
            AbstractDamageablePart body = model.BODY;
            return (head != null && head.currentHealth <= 4.0f)
                    || (body != null && body.currentHealth <= 5.0f);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!modLoaded || !FeatureConfig.firstAidAutoHeal) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.getEntityLiving() != player) return;

        triage(mc, player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!modLoaded || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null) return;

        // Age the treatment ledger every tick, independent of the feature toggle, so stale
        // entries never survive a disable/enable cycle.
        if (!treatingTicks.isEmpty()) {
            treatingTicks.replaceAll((part, ticks) -> ticks - 1);
            treatingTicks.values().removeIf(t -> t <= 0);
        }

        if (!FeatureConfig.firstAidAutoHeal) return;

        if (checkCooldown > 0) {
            checkCooldown--;
            return;
        }
        checkCooldown = 20; // one decision per second; healing is slow anyway

        triage(mc, player);
    }

    private void triage(Minecraft mc, EntityPlayerSP player) {
        if (player.isDead || player.getHealth() <= 0.0f) return;

        try {
            if (CapabilityExtendedHealthSystem.INSTANCE == null) return;
            AbstractPlayerDamageModel damageModel = player.getCapability(CapabilityExtendedHealthSystem.INSTANCE, null);
            if (damageModel == null) return;

            // Head and body hitting zero is instant death: get a totem ready early.
            AbstractDamageablePart head = damageModel.HEAD;
            AbstractDamageablePart body = damageModel.BODY;
            boolean headCritical = head != null && head.currentHealth <= 3.0f;
            boolean bodyCritical = body != null && body.currentHealth <= 4.0f;
            if (headCritical || bodyCritical) {
                FastTriageHandler.tryEquipTotem(mc, player);
            }

            // Treat the most dangerous wounded part first, one application at a time.
            EnumPlayerPart partToHeal = null;
            if (needsTreatment(head)) partToHeal = EnumPlayerPart.HEAD;
            else if (needsTreatment(body)) partToHeal = EnumPlayerPart.BODY;
            else if (needsTreatment(damageModel.LEFT_ARM)) partToHeal = EnumPlayerPart.LEFT_ARM;
            else if (needsTreatment(damageModel.RIGHT_ARM)) partToHeal = EnumPlayerPart.RIGHT_ARM;
            else if (needsTreatment(damageModel.LEFT_LEG)) partToHeal = EnumPlayerPart.LEFT_LEG;
            else if (needsTreatment(damageModel.RIGHT_LEG)) partToHeal = EnumPlayerPart.RIGHT_LEG;
            else if (needsTreatment(damageModel.LEFT_FOOT)) partToHeal = EnumPlayerPart.LEFT_FOOT;
            else if (needsTreatment(damageModel.RIGHT_FOOT)) partToHeal = EnumPlayerPart.RIGHT_FOOT;

            if (partToHeal != null) {
                applyHealingItemToPart(mc, player, partToHeal);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Damaged enough to treat, and not already recorded as being treated. Note the deliberate
     * absence of any {@code activeHealer} check - see the class comment for why the client cannot
     * trust that field.
     */
    private boolean needsTreatment(AbstractDamageablePart part) {
        return part != null
                && part.currentHealth <= ((float) part.getMaxHealth() - 0.95f)
                && !isBeingTreated(part);
    }

    private boolean isBeingTreated(AbstractDamageablePart part) {
        EnumPlayerPart key = part.part;
        Integer ticks = treatingTicks.get(key);
        if (ticks == null) return false;
        // Synced health reaching max means the healer finished early; free the part.
        if (part.currentHealth >= (float) part.getMaxHealth() - 0.05f) {
            treatingTicks.remove(key);
            return false;
        }
        return true;
    }

    private void applyHealingItemToPart(Minecraft mc, EntityPlayerSP player, EnumPlayerPart part) {
        if (mc.playerController == null || mc.getConnection() == null) return;

        // 1. Already holding a healing item?
        EnumHand handToUse = null;
        if (isHealingItem(player.getHeldItemMainhand())) {
            handToUse = EnumHand.MAIN_HAND;
        } else if (isHealingItem(player.getHeldItemOffhand())) {
            handToUse = EnumHand.OFF_HAND;
        }

        // 2. Hotbar: switch the selected slot server-side.
        if (handToUse == null) {
            for (int i = 0; i < 9; i++) {
                if (isHealingItem(player.inventory.getStackInSlot(i))) {
                    player.inventory.currentItem = i;
                    mc.getConnection().sendPacket(
                            new net.minecraft.network.play.client.CPacketHeldItemChange(i));
                    handToUse = EnumHand.MAIN_HAND;
                    break;
                }
            }
        }

        // 3. Main inventory: swap the stack into the selected hotbar slot with real clicks.
        if (handToUse == null) {
            for (int i = 9; i <= 35; i++) {
                if (isHealingItem(player.inventoryContainer.getSlot(i).getStack())) {
                    int hotbarSlot = 36 + player.inventory.currentItem;
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, hotbarSlot, 0, ClickType.PICKUP, player);
                    mc.playerController.windowClick(0, i, 0, ClickType.PICKUP, player);
                    handToUse = EnumHand.MAIN_HAND;
                    break;
                }
            }
        }

        if (handToUse != null && FirstAid.NETWORKING != null) {
            FirstAid.NETWORKING.sendToServer(new MessageApplyHealingItem(part, handToUse));
            // Record the application so we leave this part's healer alone until it finishes
            // (or the window expires, at which point a re-application is legitimate).
            treatingTicks.put(part, HEAL_WINDOW_TICKS);
            checkCooldown = 25;
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7aApplied healing to "
                    + part.name() + " \u00a77- next application only if it does not finish."));
        }
    }

    /** Bandages and plasters only. Morphine is a debuff cleanser - it has no registered healer,
     *  so the server would reject and log it if we sent it. */
    private boolean isHealingItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item == FirstAidItems.BANDAGE || item == FirstAidItems.PLASTER;
    }
}
