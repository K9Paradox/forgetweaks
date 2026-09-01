package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Weapon lock bypass by routing attacks through a mod's own attack packet.
 *
 * <h3>Where this came from</h3>
 * Found empirically: enabling the nunchaku triggerbot made locked weapons deal damage. The reason is
 * that the triggerbot does not attack through the vanilla path at all - it calls
 * {@link TriggerbotHandler#dispatchDirectAttackPacket}, which sends mod attack packets directly:
 * RLCombat's {@code bettercombat.mod.network.PacketMainhandAttack}, Spartan Weaponry's
 * {@code PacketLongReachAttack}, Ice and Fire's {@code MessagePlayerHitMultipart} and the Trinkets
 * reach packet.
 *
 * <p>Those handlers run their own server-side attack routine rather than the vanilla
 * {@code CPacketUseEntity} -&gt; {@code processUseEntity} path, and Reskillable's lock does not stop
 * them. That makes it a genuine server-side bypass rather than a client-side illusion - and unlike
 * the equipment-desync approach it keeps enchantments and full damage, because the weapon really is
 * in your hand the whole time.</p>
 *
 * <p>That behaviour was previously reachable only as a side effect of the triggerbot, and only while
 * {@code levelDamageBypass} happened to be set. This module makes it a first-class feature that
 * works with ordinary left-clicking.</p>
 *
 * <h3>Avoiding double hits</h3>
 * Vanilla would otherwise <em>also</em> send its own attack for the same click. We cancel the
 * {@link MouseEvent} when the crosshair is on an entity, which stops {@code Minecraft.clickMouse()}
 * from running for that press, so exactly one attack is dispatched. Block breaking is untouched
 * because the cancel only happens for entity targets.
 */
public class WeaponLockBypassHandler {

    private int cooldown = 0;
    private static int attacks = 0;
    private static volatile String lastResult = "never triggered";

    public static int getAttackCount() {
        return attacks;
    }

    public static String getLastResult() {
        return lastResult;
    }

    // ------------------------------------------------------------- click path

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        if (!FeatureConfig.weaponPacketBypass) return;
        if (event.getButton() != 0 || !event.isButtonstate()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;

        Entity target = currentTarget(mc);
        if (target == null) return;

        // Do not fight the triggerbot; it already dispatches its own packets.
        if (FeatureConfig.autoTriggerbot) return;

        if (mc.player.getCooledAttackStrength(0.0F) < FeatureConfig.weaponBypassMinCharge) {
            return;
        }

        attack(mc.player, target, "click");
        // Suppress the vanilla attack for this press so the target is not hit twice.
        event.setCanceled(true);
    }

    /** Held-down attacking never re-fires MouseEvent, so drive it from the tick loop too. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (cooldown > 0) cooldown--;

        if (!FeatureConfig.weaponPacketBypass || !FeatureConfig.weaponBypassHeldAttack) return;
        if (FeatureConfig.autoTriggerbot) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || mc.player == null || mc.world == null) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (cooldown > 0) return;

        Entity target = currentTarget(mc);
        if (target == null) return;
        if (mc.player.getCooledAttackStrength(0.0F) < 1.0F) return;

        attack(mc.player, target, "held");
        cooldown = 4;
    }

    // ---------------------------------------------------------------- attack

    private static void attack(EntityPlayerSP player, Entity target, String reason) {
        try {
            TriggerbotHandler.dispatchDirectAttackPacket(player, target);
            attacks++;
            lastResult = "sent mod attack packets via " + reason;
        } catch (Throwable t) {
            lastResult = "failed: " + t;
            chat("\u00a7cWeapon bypass failed: " + t);
        }
    }

    private static Entity currentTarget(Minecraft mc) {
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.ENTITY) return null;
        Entity entity = hit.entityHit;
        if (entity == null || entity.isDead || entity == mc.player) return null;
        return entity;
    }

    private static void chat(String message) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null) {
            player.sendMessage(new TextComponentString("\u00a76[RLUtility] \u00a7r" + message));
        }
    }
}
