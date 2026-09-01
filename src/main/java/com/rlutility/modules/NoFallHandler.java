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
        if (player.isElytraFlying()) return;

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
