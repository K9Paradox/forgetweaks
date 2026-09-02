package com.rlutility.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * No fall.
 *
 * <p>The part that actually matters on a server is the spoofed on-ground flag: the server resets
 * its own fall distance whenever the client reports it touched down. The previous implementation
 * sent that packet on <em>every</em> tick with {@code motionY &lt; -0.3}, which is both extremely
 * loud on the wire and broke Auto Criticals and elytra flight. It now only fires once the fall has
 * grown past the damage threshold, and it stays out of the way during a crit hop.</p>
 */
public class NoFallHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!FeatureConfig.noFall || event.phase != TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (player == null || player.connection == null || event.player != player) return;
        if (player.capabilities.isCreativeMode || player.isSpectator()) return;
        if (AutoCritHandler.critWindow > 0) return; // let the crit hop keep its fall distance
        if (player.isElytraFlying()) {
            dampBeforeImpact(player);
            return;
        }

        // Only lie about being grounded once the drop would actually hurt.
        if (!player.onGround && player.motionY < 0.0D && player.fallDistance > 2.0F) {
            player.connection.sendPacket(new CPacketPlayer(true));
            player.fallDistance = 0.0F;
        }

        // Kill the momentum snapshot CollisionDamage-style mods use for wall-impact damage.
        if (player.collidedHorizontally || player.collidedVertically) {
            player.getEntityData().setDouble("prevMotionCombined", 0.0D);
        }
    }

    /**
     * Kinetic ("flew into a wall") damage cannot be cancelled from the client.
     *
     * <p>It is applied inside {@code EntityLivingBase#travel} on the server:</p>
     *
     * <pre>
     * if (this.collidedHorizontally &amp;&amp; !this.world.isRemote) {
     *     double d13 = &lt;horizontal speed before&gt; - &lt;horizontal speed after&gt;;
     *     double d14 = d13 * 10.0D - 3.0D;
     *     if (d14 &gt; 0.0D) this.attackEntityFrom(DamageSource.FLY_INTO_WALL, (float) d14);
     * }
     * </pre>
     *
     * The damage is derived purely from how much horizontal speed the server saw you lose, and the
     * server derives that speed from the position packets we send. There is no flag to spoof - the
     * only lever is the motion itself. So rather than pretend, this bleeds off speed while a wall is
     * still ahead, keeping the delta under the {@code d13 * 10 - 3} threshold when the impact lands.
     *
     * <p>That makes it mitigation rather than immunity: a head-on hit at full elytra speed with no
     * warning distance can still hurt.</p>
     */
    private static void dampBeforeImpact(EntityPlayerSP player) {
        if (!FeatureConfig.noFallKinetic) return;

        double speed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);
        // Below the threshold the server's d14 is negative anyway, so leave flight alone.
        if (speed < 0.3D) return;

        double look = Math.max(1.5D, Math.min(6.0D, speed * 8.0D));
        net.minecraft.util.math.Vec3d from = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d dir = new net.minecraft.util.math.Vec3d(
                player.motionX, 0.0D, player.motionZ).normalize();
        net.minecraft.util.math.Vec3d to = from.addVector(dir.x * look, 0.0D, dir.z * look);

        net.minecraft.util.math.RayTraceResult hit = player.world.rayTraceBlocks(from, to, false, true, false);
        if (hit == null || hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) return;

        double distance = from.distanceTo(hit.hitVec);
        // Shed speed proportionally to how close the wall is; at contact we are near a standstill.
        double factor = Math.max(0.0D, Math.min(1.0D, (distance - 0.6D) / look));
        player.motionX *= factor;
        player.motionZ *= factor;
        dampened++;
    }

    private static int dampened = 0;

    public static int getDampenCount() {
        return dampened;
    }

    /**
     * Purely local: suppresses the flinch/red-flash for fall and kinetic damage. The server still
     * decides the real health value, so this is cosmetic in multiplayer.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (!FeatureConfig.noFall) return;
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
