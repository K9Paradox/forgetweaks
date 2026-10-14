package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Fall and kinetic damage control.
 *
 * <h3>Fall damage</h3>
 * The part that actually matters on a server is the spoofed on-ground flag: the server resets its
 * own fall distance whenever the client reports it touched down. This only fires once the fall has
 * grown past the damage threshold, and stays out of the way during a crit hop.
 *
 * <h3>Kinetic ("ran into a wall") damage - why this one is a real fix</h3>
 * RLCraft ships the <em>Collision Damage</em> mod. Its entire damage pipeline is:
 *
 * <pre>
 * // client, PlayerTickEvent END:
 * double accel = prevMotionCombined - curMotionCombined;
 * if (accel > 5 && player.collidedHorizontally)
 *     PacketHandler.INSTANCE.sendToServer(new PacketCollisionS(accel));
 *
 * // server, on that packet:
 * player.attackEntityFrom(flyIntoWall-or-fall, (accel - threshold) * 4 * multiplier);
 * </pre>
 *
 * The server never measures anything itself - it trusts the acceleration the client reports. So
 * the honest, authoritative fix is to never report one: we keep the {@code prevMotionCombined}
 * snapshot pinned to the current speed every tick, the mod always computes an acceleration of 0,
 * and the packet is never sent. That is exactly the immunity the Stone of Inertia Null grants,
 * just without needing the drop.
 *
 * <p>Vanilla elytra flight has a separate, truly server-computed impact damage based on how much
 * horizontal speed is lost inside a tick. That one cannot be cancelled from the client, so for it
 * we brake smoothly ahead of walls and arrive at impact below its loss threshold.</p>
 */
public class NoFallHandler {

    /** Impact speed under which vanilla elytra kinetic damage cannot trigger. */
    private static final double SAFE_IMPACT_SPEED = 0.25D;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.connection == null || event.player != player) return;

        if (FeatureConfig.noFallKinetic && player.isElytraFlying()) {
            brakeBeforeImpact(player);
        }

        // Creative-style flight can briefly report isFlying=false while the server resyncs
        // abilities after damage. Keep the fall guard active during that transition too; otherwise
        // touching down in the same tick can turn a harmless flight correction into lethal damage.
        boolean flightSafety = FeatureConfig.creativeFly && player.capabilities.allowFlying;
        if (!FeatureConfig.noFall && !flightSafety) return;
        if (player.capabilities.isCreativeMode || player.isSpectator()) return;
        if (AutoCritHandler.critWindow > 0 && !flightSafety) return; // let the crit hop keep its fall distance
        if (player.isElytraFlying()) return;

        // Only lie about being grounded once the drop would actually hurt. No motionY gate: when
        // the server disagrees about flight (ability revoked server-side) the client can be
        // "floating" with zero velocity while the server applies gravity - fallDistance still
        // climbs via the packets below, and this keeps covering that case too.
        if (!player.onGround && player.fallDistance > 2.0F) {
            player.connection.sendPacket(new CPacketPlayer(true));
            player.fallDistance = 0.0F;
        }
    }

    /**
     * Runs at the END of the player tick, before the Collision Damage mod's own END-phase handler
     * (we register at HIGHEST priority, it at NORMAL). It stores the current horizontal speed as
     * the "previous tick" snapshot, so the mod's acceleration is always current - current = 0 and
     * its impact packet never goes out. The snapshot key is theirs; nothing else reads it.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTickEnd(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!FeatureConfig.noFallKinetic) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || event.player != player) return;

        double mx = player.motionX;
        double mz = player.motionZ;
        // Same quantisation the mod uses, so the values compare exactly equal.
        double cur = ((double) ((int) (Math.sqrt(mx * mx + mz * mz) * 20 * 100))) / 100.0D;
        player.getEntityData().setDouble("prevMotionCombined", cur);
    }

    /**
     * Smooth braking for elytra flight. Sweeps the player's bounding box along the flight path to
     * find the impact distance, then sheds exactly enough speed per tick to arrive at the wall at
     * or below {@link #SAFE_IMPACT_SPEED}. Because the deceleration is spread over the ticks we
     * actually have, no single tick ever loses enough speed to register as kinetic damage.
     */
    private static void brakeBeforeImpact(EntityPlayerSP player) {
        double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        if (speed <= SAFE_IMPACT_SPEED + 0.1D) return;

        Vec3d motion = new Vec3d(player.motionX, player.motionY, player.motionZ);
        double len = motion.lengthVector();
        if (len < 1.0E-4D) return;

        // Look no further ahead than ~1.5 seconds of flight.
        double maxLook = Math.min(24.0D, len * 30.0D);
        AxisAlignedBB box = player.getEntityBoundingBox();
        double impact = -1.0D;
        double step = 0.25D;
        for (double d = step; d <= maxLook; d += step) {
            AxisAlignedBB at = box.offset(motion.x / len * d, motion.y / len * d, motion.z / len * d);
            if (player.world.collidesWithAnyBlock(at)) {
                impact = d;
                break;
            }
        }
        if (impact < 0.0D) return;

        double ticksToImpact = impact / len;
        // Keep the last tick in reserve: during it we travel the remaining distance at safe speed.
        double ticksLeft = Math.max(1.0D, ticksToImpact - 1.0D);
        double loss = (speed - SAFE_IMPACT_SPEED) / ticksLeft;
        loss = Math.min(speed - SAFE_IMPACT_SPEED, Math.max(0.0D, loss));
        if (loss <= 0.0D) return;

        double scale = (speed - loss) / speed;
        player.motionX *= scale;
        player.motionZ *= scale;
    }

    /**
     * Purely local: suppresses the flinch/red-flash for fall and kinetic damage. The server still
     * decides the real health value, so this is cosmetic in multiplayer.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!FeatureConfig.noFall && !FeatureConfig.noFallKinetic) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getEntityLiving() != mc.player) return;
        if (isFallOrKinetic(event.getSource())) event.setCanceled(true);
    }

    private static boolean isFallOrKinetic(DamageSource src) {
        if (src == null) return false;
        if (src == DamageSource.FALL || src == DamageSource.FLY_INTO_WALL) return true;
        String type = src.getDamageType();
        if (type == null) return false;
        type = type.toLowerCase();
        return type.contains("fall") || type.contains("fly") || type.contains("kinetic")
                || type.contains("crash") || type.contains("slam");
    }
}
