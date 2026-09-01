package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Reskillable weapon-lock bypass via a one-tick equipment desync.
 *
 * <h3>The two facts this exploits</h3>
 * <ol>
 *   <li>Reskillable's weapon check reads the <em>current</em> main-hand stack:
 *   <pre>
 *   public static void livingAttack(LivingAttackEvent event) {
 *       ... genericEnforce(event, player, player.getHeldItemMainhand(), MSG_ITEM_LOCKED);
 *   }
 *   public static void genericEnforce(Event event, EntityPlayer player, ItemStack stack, String msg) {
 *       if (... || stack == null || stack.isEmpty() || ...) return;   // empty hand = no check at all
 *   </pre>
 *   An empty main hand takes the early return, so the lock is never evaluated.</li>
 *
 *   <li>Attack damage does <em>not</em> come from the held stack. It comes from the player's
 *   {@code ATTACK_DAMAGE} attribute, and that attribute is only recomputed once per tick inside
 *   {@code EntityLivingBase.onUpdate}, which diffs the equipment against last tick's copy and
 *   re-applies the item's modifiers.</li>
 * </ol>
 *
 * <h3>The gap</h3>
 * A server tick updates entities first and processes queued packets afterwards. So if the slot
 * change and the attack arrive in the same tick:
 *
 * <pre>
 * tick N  entity update : sword equipped -&gt; ATTACK_DAMAGE = sword
 *         network       : CPacketHeldItemChange(empty slot)   -&gt; currentItem = empty
 *                         CPacketUseEntity(ATTACK)
 *                           -&gt; damage = ATTACK_DAMAGE          (still the sword's, stale)
 *                           -&gt; LivingAttackEvent
 *                                getHeldItemMainhand() = empty -&gt; Reskillable returns early
 * </pre>
 *
 * The hit lands at the sword's full base damage with no level check performed.
 *
 * <h3>How we trigger it</h3>
 * No packet crafting is needed - vanilla does it for us. {@code PlayerControllerMP.attackEntity}
 * calls {@code syncCurrentPlayItem()} immediately before sending {@code CPacketUseEntity}, and that
 * helper emits a {@code CPacketHeldItemChange} whenever the client's {@code currentItem} differs
 * from the last value it sent. Changing {@code inventory.currentItem} in the {@link MouseEvent}
 * (which fires before {@code clickMouse()} in the same frame) therefore produces exactly the packet
 * pair above, in the right order. We restore the slot two ticks later.
 *
 * <h3>Known trade-offs</h3>
 * <ul>
 *   <li>Enchantment bonuses are lost: {@code EnchantmentHelper.getModifierForCreature} reads the
 *       live main-hand stack, which is empty. Base weapon damage still applies.</li>
 *   <li>The weapon takes no durability damage, since {@code hitEntity} runs on the empty stack.</li>
 *   <li>Your hand visibly flickers empty for two ticks per swing.</li>
 *   <li>The sword must be equipped at the <em>start</em> of the attack tick, so sustained swings
 *       work at most every other tick. RLCraft attack cooldowns are far slower than that.</li>
 * </ul>
 */
public class ReskillableAttackBypass {

    private static final boolean MOD_LOADED = Loader.isModLoaded("reskillable");

    private static int savedSlot = -1;
    private static int restoreDelay = 0;
    private static int warnCooldown = 0;
    private static int successes = 0;

    public static boolean isActive() {
        return savedSlot >= 0;
    }

    public static int getSuccessCount() {
        return successes;
    }

    // --------------------------------------------------------------- triggers

    /** Fires before {@code clickMouse()}, so the swap is in place when the attack packet goes out. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (!enabled()) return;
        if (event.getButton() != 0 || !event.isButtonstate()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) return;
        if (!lookingAtEntity(mc)) return;

        armSwap("click");
    }

    /**
     * Held-down attacking never re-fires {@link MouseEvent}, so cover that path from the tick loop:
     * arm again as soon as the attack cooldown is full and the crosshair is still on a target.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        if (restoreDelay > 0 && --restoreDelay == 0) restore();
        if (warnCooldown > 0) warnCooldown--;

        if (!enabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (!lookingAtEntity(mc)) return;
        if (mc.player.getCooledAttackStrength(0.0F) < 1.0F) return;

        armSwap("held");
    }

    // ----------------------------------------------------------------- swap

    private static boolean enabled() {
        return MOD_LOADED && FeatureConfig.reskillableAttackBypass;
    }

    private static boolean lookingAtEntity(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        return hit != null && hit.typeOfHit == RayTraceResult.Type.ENTITY && hit.entityHit != null;
    }

    private static void armSwap(String reason) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || savedSlot >= 0) return;

        // Nothing to do if the server would already accept this weapon.
        if (ReskillableHelper.canUseHeldItem()) return;

        int target = findBypassSlot(player);
        if (target < 0) {
            if (warnCooldown == 0) {
                warnCooldown = 100;
                chat("\u00a7cAttack bypass needs a free hotbar slot. Empty one and try again.");
            }
            return;
        }

        savedSlot = player.inventory.currentItem;
        player.inventory.currentItem = target;
        // Two ticks is enough for syncCurrentPlayItem to emit the change and the attack to follow.
        restoreDelay = 2;
        successes++;
    }

    private static void restore() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null && savedSlot >= 0 && savedSlot < 9) {
            mc.player.inventory.currentItem = savedSlot;
        }
        savedSlot = -1;
    }

    /** Prefers a genuinely empty hotbar slot; falls back to any slot Reskillable would allow. */
    private static int findBypassSlot(EntityPlayerSP player) {
        int current = player.inventory.currentItem;

        for (int slot = 0; slot < 9; slot++) {
            if (slot == current) continue;
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) return slot;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (slot == current) continue;
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && !stack.isEmpty() && ReskillableHelper.canUseStack(stack)) return slot;
        }
        return -1;
    }

    /** Called on disconnect so a swap can never be left applied. */
    public static void reset() {
        savedSlot = -1;
        restoreDelay = 0;
        warnCooldown = 0;
    }

    private static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
