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
 * Found empirically: only the <em>nunchaku</em> branch of the triggerbot made locked weapons deal
 * damage. My first attempt credited the wrong code - the non-nunchaku branch sends raw mod packets
 * ({@code PacketMainhandAttack}, {@code PacketLongReachAttack}, {@code MessagePlayerHitMultipart})
 * and those do <b>not</b> work on their own. The nunchaku branch instead calls:
 *
 * <pre>
 * RLCombatCompat.attackEntityFromClient(new RayTraceResult(target), player);
 * </pre>
 *
 * <p>That is Better Survival's integration hook into RLCombat, and it drives RLCombat's full
 * client-side attack pipeline rather than firing a bare packet at the server. RLCombat only honours
 * an incoming attack when its own state machine says an attack is in progress, which is why the
 * loose packets were ignored while this call lands.</p>
 *
 * <p>It is a real server-side bypass, and it keeps enchantments and full damage because the weapon
 * genuinely stays in your hand. The raw packets are kept as an optional extra, off by default.</p>
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
        boolean any = false;
        StringBuilder how = new StringBuilder();

        // Primary: the exact call the working nunchaku path uses.
        if (FeatureConfig.bypassUseRlcombatHook && net.minecraftforge.fml.common.Loader.isModLoaded("rlcombat")) {
            try {
                com.mujmajnkraft.bettersurvival.integration.RLCombatCompat
                        .attackEntityFromClient(new RayTraceResult(target), player);
                how.append("rlcombat-hook ");
                any = true;
            } catch (Throwable t) {
                how.append("rlcombat-hook-FAILED(").append(t.getClass().getSimpleName()).append(") ");
            }
        }

        // Optional extra: the raw packets. Known not to work alone, but harmless to stack.
        if (FeatureConfig.bypassExtraPackets) {
            try {
                TriggerbotHandler.dispatchDirectAttackPacket(player, target);
                how.append("packets[").append(TriggerbotHandler.lastDispatched).append("] ");
                any = true;
            } catch (Throwable t) {
                how.append("packets-FAILED ");
            }
        }

        // Last resort so the module is never a no-op.
        if (!any) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.playerController != null) {
                mc.playerController.attackEntity(player, target);
                how.append("vanilla-fallback ");
            }
        }

        player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);
        attacks++;
        lastResult = how.toString().trim() + " via " + reason;
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
