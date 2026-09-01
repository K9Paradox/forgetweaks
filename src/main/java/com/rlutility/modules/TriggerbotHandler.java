package com.rlutility.modules;

import com.mujmajnkraft.bettersurvival.capabilities.nunchakucombo.INunchakuCombo;
import com.mujmajnkraft.bettersurvival.capabilities.nunchakucombo.NunchakuComboProvider;
import com.mujmajnkraft.bettersurvival.integration.RLCombatCompat;
import com.mujmajnkraft.bettersurvival.items.ItemNunchaku;
import com.mujmajnkraft.bettersurvival.packet.BetterSurvivalPacketHandler;
import com.mujmajnkraft.bettersurvival.packet.MessageNunchakuSpinClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Method;

public class TriggerbotHandler {

    private int attackCooldown = 0;
    private boolean wasSpinning = false;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || mc.playerController == null || mc.world == null) return;

        ItemStack heldItem = player.getHeldItemMainhand();
        boolean isNunchaku = !heldItem.isEmpty() && heldItem.getItem() instanceof ItemNunchaku;

        // Auto Triggerbot module: runs continuously when enabled
        if (FeatureConfig.autoTriggerbot) {
            if (isNunchaku) {
                // Keep nunchaku spinning continuously
                ensureSpinning(player, true);
                Entity target = getTargetEntity(mc, player);
                if (target != null && !target.isDead) {
                    if (player.getCooledAttackStrength(0.5F) >= 1.0F) {
                        RayTraceResult hitResult = new RayTraceResult(target);
                        if (ModCompat.hasRLCombatCompat()) {
                            RLCombatCompat.attackEntityFromClient(hitResult, player);
                        } else {
                            mc.playerController.attackEntity(player, target);
                        }
                    }
                }
            } else {
                stopSpinningIfNeeded(player);
                if (attackCooldown > 0) {
                    attackCooldown--;
                    return;
                }
                Entity target = getTargetEntity(mc, player);
                if (target != null && !target.isDead && player.getCooledAttackStrength(0.5F) >= 0.9F) {
                    if (FeatureConfig.levelDamageBypass) {
                        dispatchDirectAttackPacket(player, target);
                    } else {
                        mc.playerController.attackEntity(player, target);
                        player.swingArm(EnumHand.MAIN_HAND);
                    }
                    attackCooldown = 6;
                }
            }
            return;
        }

        // When autoTriggerbot is OFF, manage nunchaku spin state safely
        stopSpinningIfNeeded(player);

        // Level Damage Bypass for manual left-clicks
        if (FeatureConfig.levelDamageBypass && mc.gameSettings.keyBindAttack.isKeyDown()) {
            if (attackCooldown > 0) {
                attackCooldown--;
                return;
            }
            Entity target = getTargetEntity(mc, player);
            if (target != null && !target.isDead) {
                if (isNunchaku) {
                    ensureSpinning(player, true);
                    if (player.getCooledAttackStrength(0.5F) >= 1.0F) {
                        RayTraceResult hitResult = new RayTraceResult(target);
                        if (ModCompat.hasRLCombatCompat()) {
                            RLCombatCompat.attackEntityFromClient(hitResult, player);
                        } else {
                            mc.playerController.attackEntity(player, target);
                        }
                    }
                } else if (player.getCooledAttackStrength(0.5F) >= 0.85F) {
                    dispatchDirectAttackPacket(player, target);
                    attackCooldown = 6;
                }
            }
        }
    }

    private Entity getTargetEntity(Minecraft mc, EntityPlayerSP player) {
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY) {
            return mc.objectMouseOver.entityHit;
        }

        // Extended reach cone trace (up to 4.5 blocks) for reliable target acquisition
        double reach = 4.5;
        net.minecraft.util.math.Vec3d eyePos = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d lookVec = player.getLook(1.0F);
        net.minecraft.util.math.Vec3d reachVec = eyePos.addVector(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);

        Entity pointedEntity = null;
        java.util.List<Entity> list = mc.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().expand(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach).grow(1.0D, 1.0D, 1.0D));

        double closestDistance = reach;
        for (Entity entity : list) {
            if (entity.canBeCollidedWith() && entity instanceof net.minecraft.entity.EntityLivingBase && !entity.isDead) {
                net.minecraft.util.math.AxisAlignedBB aabb = entity.getEntityBoundingBox().grow(entity.getCollisionBorderSize());
                RayTraceResult intercept = aabb.calculateIntercept(eyePos, reachVec);
                if (intercept != null) {
                    double dist = eyePos.distanceTo(intercept.hitVec);
                    if (dist < closestDistance) {
                        pointedEntity = entity;
                        closestDistance = dist;
                    }
                }
            }
        }
        return pointedEntity;
    }

    private void ensureSpinning(EntityPlayerSP player, boolean spin) {
        try {
            INunchakuCombo combo = player.getCapability(NunchakuComboProvider.NUNCHAKUCOMBO_CAP, null);
            if (combo != null && combo.isSpinning() != spin) {
                BetterSurvivalPacketHandler.NETWORK.sendToServer(new MessageNunchakuSpinClient(spin));
                combo.setSpinning(spin);
                wasSpinning = spin;
            }
        } catch (Throwable ignored) {}
    }

    private void stopSpinningIfNeeded(EntityPlayerSP player) {
        if (wasSpinning && player != null) {
            ensureSpinning(player, false);
        }
    }

    /** Which packets actually went out last time - shown by /rlu diag. */
    public static volatile String lastDispatched = "none";

    public static void dispatchDirectAttackPacket(EntityPlayerSP player, Entity target) {
        if (target == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        StringBuilder sent = new StringBuilder();

        // Each of these is an independent server-side attack. Sending several at once stacks
        // damage (subject to hurtResistantTime), so they are individually switchable - that also
        // lets you isolate which one your server actually honours.

        // 1. Ice and Fire Multipart Attack Packet (Unchecked C2S entity attack)
        if (FeatureConfig.bypassPacketIaf && Loader.isModLoaded("iceandfire")) {
            try {
                Class<?> iafClass = Class.forName("com.github.alexthe666.iceandfire.IceAndFire");
                Object wrapper = iafClass.getField("NETWORK_WRAPPER").get(null);
                Class<?> msgClass = Class.forName("com.github.alexthe666.iceandfire.message.MessagePlayerHitMultipart");
                Object msg = msgClass.getConstructor(int.class).newInstance(target.getEntityId());
                Method sendToServer = wrapper.getClass().getMethod("sendToServer", net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
                sendToServer.invoke(wrapper, msg);
                sent.append("iaf ");
            } catch (Throwable ignored) {}
        }

        // 2. Spartan Weaponry Long Reach Attack Packet
        if (FeatureConfig.bypassPacketSpartan && ModCompat.spartanLongReachPacket() != null) {
            try {
                Class<?> spartanNet = ModCompat.spartanPacketHandler();
                Object instance = spartanNet.getField("instance").get(null);
                Class<?> packetClass = ModCompat.spartanLongReachPacket();
                Object packet = packetClass.getConstructor(int.class, float.class).newInstance(target.getEntityId(), 0.0F);
                Method sendToServer = instance.getClass().getMethod("sendToServer", net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
                sendToServer.invoke(instance, packet);
                sent.append("spartan ");
            } catch (Throwable ignored) {}
        }

        // 3. Trinkets & Baubles Increased Reach Packet
        if (FeatureConfig.bypassPacketTrinkets && Loader.isModLoaded("trinketsandbaubles")) {
            try {
                Class<?> packetClass = Class.forName("xzeroair.trinkets.network.IncreasedReachPacket");
                Object packet = packetClass.getConstructor(Entity.class).newInstance(target);
                xzeroair.trinkets.network.NetworkHandler.INSTANCE.sendToServer((xzeroair.trinkets.network.BasicPacket) packet);
                sent.append("trinkets ");
            } catch (Throwable ignored) {}
        }

        // 4. Direct RLCombat PacketMainhandAttack - the most likely one to be doing the work,
        //    since it runs RLCombat's own server-side attack routine.
        if (FeatureConfig.bypassPacketRlcombat && ModCompat.hasRLCombat()) {
            try {
                Class<?> packetHandlerClass = Class.forName("bettercombat.mod.network.PacketHandler");
                Object instance = packetHandlerClass.getField("instance").get(null);
                Class<?> packetClass = Class.forName("bettercombat.mod.network.PacketMainhandAttack");
                Object packet = packetClass.getConstructor(int.class).newInstance(target.getEntityId());
                Method sendToServer = instance.getClass().getMethod("sendToServer", net.minecraftforge.fml.common.network.simpleimpl.IMessage.class);
                sendToServer.invoke(instance, packet);
                sent.append("rlcombat ");
            } catch (Throwable ignored) {}
        } else if (FeatureConfig.bypassPacketVanilla) {
            // 5. Direct Vanilla Attack Packet via Connection
            if (mc.getConnection() != null) {
                mc.getConnection().sendPacket(new net.minecraft.network.play.client.CPacketUseEntity(target));
                sent.append("vanilla ");
            }
        }

        lastDispatched = sent.length() == 0 ? "none (all packet types disabled or absent)" : sent.toString().trim();
        player.swingArm(EnumHand.MAIN_HAND);
    }
}
